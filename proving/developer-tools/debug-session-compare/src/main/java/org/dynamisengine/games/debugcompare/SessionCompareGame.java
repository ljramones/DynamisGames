package org.dynamisengine.games.debugcompare;

import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.ui.debug.builder.DebugOverlayBuilder;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
import org.dynamisengine.ui.debug.export.DebugSessionMetadata;
import org.dynamisengine.ui.debug.export.DebugSnapshotReplayLoader;
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Debug Session Compare — side-by-side comparison of two recorded sessions.
 *
 * <p>Loads two NDJSON session files and renders their overlays side-by-side
 * with synchronized navigation. Answers "what changed?" between runs.
 *
 * <p>Controls:
 * <pre>
 *   Space   — play/pause (synchronized)
 *   ,/.     — step backward/forward 1 frame
 *   Home/End — jump to first/last frame
 *   1/2/3   — playback speed
 *   Tab     — switch active side highlight
 *   Esc     — quit
 * </pre>
 */
public final class SessionCompareGame implements WorldApplication {

    static final ActionId PLAY_PAUSE = new ActionId("playPause");
    static final ActionId STEP_BACK = new ActionId("stepBack");
    static final ActionId STEP_FWD = new ActionId("stepFwd");
    static final ActionId JUMP_START = new ActionId("jumpStart");
    static final ActionId JUMP_END = new ActionId("jumpEnd");
    static final ActionId SPEED_SLOW = new ActionId("speedSlow");
    static final ActionId SPEED_NORMAL = new ActionId("speedNormal");
    static final ActionId SPEED_FAST = new ActionId("speedFast");
    static final ActionId SWITCH_SIDE = new ActionId("switchSide");
    static final ActionId NEXT_ANOMALY = new ActionId("nextAnomaly");
    static final ActionId PREV_ANOMALY = new ActionId("prevAnomaly");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("compare");
    private static final int KEY_SPACE = 32, KEY_COMMA = 44, KEY_PERIOD = 46;
    private static final int KEY_HOME = 268, KEY_END = 269, KEY_ESC = 256;
    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_TAB = 258;
    private static final int KEY_N = 78, KEY_B = 66;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final String leftFile, rightFile;

    private final TextRenderer textRenderer = new TextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;
    private final DebugOverlayBuilder builder = new DebugOverlayBuilder(
        new DebugOverlayOptions(false, true, true, 60, 14, 6, false));

    private List<DebugViewSnapshot> leftSnapshots, rightSnapshots;
    private DebugSessionMetadata leftMeta, rightMeta;
    private int currentIndex;
    private int maxIndex;
    private boolean playing;
    private float playbackSpeed = 1f;
    private float accumulator;
    private boolean activeSideLeft = true;

    // Precomputed anomaly data
    private float[] perFrameScores;
    private int[] anomalyPeakIndices; // frames where score is a local peak above threshold

    public SessionCompareGame(WindowSubsystem w, WindowInputSubsystem i, String left, String right) {
        this.windowSub = w;
        this.inputSub = i;
        this.leftFile = left;
        this.rightFile = right;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                    Map.entry(PLAY_PAUSE, List.of(new KeyBinding(KEY_SPACE, 0))),
                    Map.entry(STEP_BACK, List.of(new KeyBinding(KEY_COMMA, 0))),
                    Map.entry(STEP_FWD, List.of(new KeyBinding(KEY_PERIOD, 0))),
                    Map.entry(JUMP_START, List.of(new KeyBinding(KEY_HOME, 0))),
                    Map.entry(JUMP_END, List.of(new KeyBinding(KEY_END, 0))),
                    Map.entry(SPEED_SLOW, List.of(new KeyBinding(KEY_1, 0))),
                    Map.entry(SPEED_NORMAL, List.of(new KeyBinding(KEY_2, 0))),
                    Map.entry(SPEED_FAST, List.of(new KeyBinding(KEY_3, 0))),
                    Map.entry(SWITCH_SIDE, List.of(new KeyBinding(KEY_TAB, 0))),
                    Map.entry(NEXT_ANOMALY, List.of(new KeyBinding(KEY_N, 0))),
                    Map.entry(PREV_ANOMALY, List.of(new KeyBinding(KEY_B, 0))),
                    Map.entry(QUIT, List.of(new KeyBinding(KEY_ESC, 0)))),
                Map.of(),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        textRenderer.initialize();
        overlayRenderer = new OpenGlDebugOverlayRenderer(textRenderer);

        leftSnapshots = loadSession(leftFile, "LEFT");
        rightSnapshots = loadSession(rightFile, "RIGHT");
        leftMeta = loadMeta(leftFile);
        rightMeta = loadMeta(rightFile);

        maxIndex = Math.max(leftSnapshots.size(), rightSnapshots.size()) - 1;

        // Precompute per-frame regression scores and find peaks
        buildAnomalyIndex();

        System.out.println("=== Debug Session Compare ===");
        System.out.printf("Left:  %s (%d frames)%n", leftFile, leftSnapshots.size());
        System.out.printf("Right: %s (%d frames)%n", rightFile, rightSnapshots.size());
        System.out.printf("Anomaly peaks: %d (score >= 10)%n", anomalyPeakIndices.length);
        if (leftMeta != null) System.out.printf("  Left:  %s / %s%n", leftMeta.scenario(), leftMeta.recordedAt());
        if (rightMeta != null) System.out.printf("  Right: %s / %s%n", rightMeta.scenario(), rightMeta.recordedAt());
        System.out.println("Space=play  ,/.=step  N/B=anomaly  Tab=switch  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(PLAY_PAUSE)) playing = !playing;
            if (frame.pressed(STEP_BACK)) currentIndex = Math.max(0, currentIndex - 1);
            if (frame.pressed(STEP_FWD)) currentIndex = Math.min(maxIndex, currentIndex + 1);
            if (frame.pressed(JUMP_START)) currentIndex = 0;
            if (frame.pressed(JUMP_END)) currentIndex = maxIndex;
            if (frame.pressed(SPEED_SLOW)) playbackSpeed = 0.25f;
            if (frame.pressed(SPEED_NORMAL)) playbackSpeed = 1f;
            if (frame.pressed(SPEED_FAST)) playbackSpeed = 2f;
            if (frame.pressed(SWITCH_SIDE)) activeSideLeft = !activeSideLeft;
            if (frame.pressed(NEXT_ANOMALY)) jumpToNextAnomaly();
            if (frame.pressed(PREV_ANOMALY)) jumpToPrevAnomaly();
        }

        // Auto-advance
        if (playing) {
            accumulator += dt * playbackSpeed;
            float interval = 1f / 60f;
            while (accumulator >= interval && currentIndex < maxIndex) {
                currentIndex++;
                accumulator -= interval;
            }
            if (currentIndex >= maxIndex) playing = false;
        }

        // Get snapshots for current index
        var leftSnap = getFrame(leftSnapshots, currentIndex);
        var rightSnap = getFrame(rightSnapshots, currentIndex);

        // Render
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);
        glClearColor(0.03f, 0.04f, 0.06f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        int halfW = w / 2;
        int panelAreaH = h - 70; // leave room for status bar

        // Render left side
        glViewport(0, 0, halfW, h);
        glScissor(0, 0, halfW, h);
        glEnable(GL_SCISSOR_TEST);
        var leftPanels = builder.buildAll(leftSnap);
        overlayRenderer.renderPanels(leftPanels, halfW, panelAreaH);
        glDisable(GL_SCISSOR_TEST);

        // Render right side
        glViewport(halfW, 0, halfW, h);
        glScissor(halfW, 0, halfW, h);
        glEnable(GL_SCISSOR_TEST);
        var rightPanels = builder.buildAll(rightSnap);
        overlayRenderer.renderPanels(rightPanels, halfW, panelAreaH);
        glDisable(GL_SCISSOR_TEST);

        // Render status bar (full width)
        glViewport(0, 0, w, h);
        textRenderer.beginFrame(w, h);

        // Side labels
        int activeColor = activeSideLeft ? 0 : 1;
        float lAlpha = activeSideLeft ? 1f : 0.5f;
        float rAlpha = activeSideLeft ? 0.5f : 1f;
        String leftLabel = leftMeta != null ? leftMeta.scenario() : "LEFT";
        String rightLabel = rightMeta != null ? rightMeta.scenario() : "RIGHT";
        textRenderer.drawText("< " + leftLabel + " >", 10, 2, 2.0f,
            0.3f * lAlpha, 1f * lAlpha, 0.6f * lAlpha, w, h);
        textRenderer.drawText("< " + rightLabel + " >", halfW + 10, 2, 2.0f,
            0.3f * rAlpha, 1f * rAlpha, 0.6f * rAlpha, w, h);

        // Divider line
        textRenderer.drawRect(halfW - 1, 0, 2, h - 60, 0.4f, 0.4f, 0.4f, 0.8f, w, h);

        // Diff summary at center
        renderDiffSummary(leftSnap, rightSnap, halfW, w, h);

        // Progress bar with anomaly markers
        float barY = h - 55;
        float barW = w - 20;
        textRenderer.drawRect(10, barY, barW, 6, 0.2f, 0.2f, 0.2f, 0.8f, w, h);

        // Score heatmap on progress bar (faint background colored by score)
        if (perFrameScores != null && maxIndex > 0) {
            int segments = Math.min(200, maxIndex + 1);
            float segW = barW / segments;
            for (int s = 0; s < segments; s++) {
                int frameIdx = (int)((float) s / segments * maxIndex);
                if (frameIdx < perFrameScores.length) {
                    float score = perFrameScores[frameIdx];
                    if (score > 5f) {
                        float intensity = Math.min(1f, score / 60f);
                        textRenderer.drawRect(10 + s * segW, barY - 1, segW + 1, 8,
                            intensity, 0.1f, 0.05f, 0.6f, w, h);
                    }
                }
            }

            // Anomaly peak ticks
            for (int pi : anomalyPeakIndices) {
                float px = 10 + barW * ((float) pi / maxIndex);
                float score = perFrameScores[pi];
                float r = score >= 60 ? 1f : (score >= 30 ? 1f : 0.9f);
                float g = score >= 60 ? 0.15f : (score >= 30 ? 0.3f : 0.6f);
                textRenderer.drawRect(px, barY - 4, 2, 14, r, g, 0.1f, 0.9f, w, h);
            }
        }
        if (maxIndex > 0) {
            float progress = (float) currentIndex / maxIndex;
            textRenderer.drawRect(10, barY, barW * progress, 6, 0.3f, 1f, 0.5f, 0.9f, w, h);
        }

        // Status
        String playState = playing ? "PLAYING " + playbackSpeed + "x" : "PAUSED";
        textRenderer.drawText(String.format("[%s]  Frame %d/%d  Left tick: %d  Right tick: %d",
            playState, currentIndex + 1, maxIndex + 1, leftSnap.tick(), rightSnap.tick()),
            10, h - 42, 1.8f, 0.8f, 0.8f, 0.4f, w, h);
        textRenderer.drawText(
            "Space=play  ,/.=step  Tab=switch  Home/End=jump  1/2/3=speed  Esc=quit",
            10, h - 20, 1.5f, 0.4f, 0.4f, 0.5f, w, h);

        textRenderer.endFrame();
        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
        System.out.printf("[Compare] Viewed %d/%d frames%n", currentIndex + 1, maxIndex + 1);
    }

    private static DebugViewSnapshot getFrame(List<DebugViewSnapshot> snapshots, int index) {
        if (index < 0 || index >= snapshots.size()) return DebugViewSnapshot.EMPTY;
        return snapshots.get(index);
    }

    private static List<DebugViewSnapshot> loadSession(String file, String label) {
        try {
            var snapshots = DebugSnapshotReplayLoader.load(Path.of(file));
            System.out.printf("[%s] Loaded %d frames from %s%n", label, snapshots.size(), file);
            return snapshots;
        } catch (IOException e) {
            System.err.printf("[%s] Failed to load: %s%n", label, e.getMessage());
            return List.of();
        }
    }

    // --- Diff rendering ---

    private static final float DELTA_THRESHOLD = 0.5f;

    // Regression score weights
    private static final float W_FRAME_TIME = 3f;    // per ms delta
    private static final float W_BUDGET = 0.5f;      // per % delta
    private static final float W_ALERT_COUNT = 5f;    // per alert delta
    private static final float W_NEW_ERROR = 15f;     // per new ERROR/CRITICAL alert
    private static final float W_NEW_WARNING = 8f;    // per new WARNING alert
    private static final float W_METRIC = 1f;         // per unit of subsystem metric delta

    private void renderDiffSummary(DebugViewSnapshot left, DebugViewSnapshot right,
                                    int centerX, int w, int h) {
        float x = centerX + 6;
        float y = 20;
        float scale = 1.5f;
        float lineH = 12f;

        // --- Compute regression score ---
        float score = 0f;
        var contributors = new java.util.ArrayList<ScoreContributor>();

        // Frame time
        float ftDelta = right.summary().frameTimeMs() - left.summary().frameTimeMs();
        if (ftDelta > DELTA_THRESHOLD) {
            float contrib = ftDelta * W_FRAME_TIME;
            score += contrib;
            contributors.add(new ScoreContributor("frameTimeMs", contrib, ftDelta));
        }

        // Budget
        float budgetDelta = right.summary().budgetPercent() - left.summary().budgetPercent();
        if (budgetDelta > 1f) {
            float contrib = budgetDelta * W_BUDGET;
            score += contrib;
            contributors.add(new ScoreContributor("budget%", contrib, budgetDelta));
        }

        // Alert count
        int alertDelta = right.alerts().size() - left.alerts().size();
        if (alertDelta > 0) {
            float contrib = alertDelta * W_ALERT_COUNT;
            score += contrib;
            contributors.add(new ScoreContributor("alerts", contrib, alertDelta));
        }

        // New alerts by severity
        int newErrors = 0, newWarnings = 0;
        for (var ra : right.alerts()) {
            boolean isNew = left.alerts().stream().noneMatch(a -> a.ruleName().equals(ra.ruleName()));
            if (isNew) {
                if ("ERROR".equals(ra.severity())) newErrors++;
                else newWarnings++;
            }
        }
        if (newErrors > 0) {
            float contrib = newErrors * W_NEW_ERROR;
            score += contrib;
            contributors.add(new ScoreContributor("new ERRORs", contrib, newErrors));
        }
        if (newWarnings > 0) {
            float contrib = newWarnings * W_NEW_WARNING;
            score += contrib;
            contributors.add(new ScoreContributor("new WARNINGs", contrib, newWarnings));
        }

        // Subsystem metric deltas
        for (var catKey : left.categories().keySet()) {
            var leftCat = left.categories().get(catKey);
            var rightCat = right.categories().get(catKey);
            if (rightCat == null) continue;
            for (var srcEntry : leftCat.sources().entrySet()) {
                var rightSrc = rightCat.sources().get(srcEntry.getKey());
                if (rightSrc == null) continue;
                for (var metricEntry : srcEntry.getValue().entrySet()) {
                    String rightVal = rightSrc.get(metricEntry.getKey());
                    if (rightVal == null) continue;
                    try {
                        double lv = Double.parseDouble(metricEntry.getValue());
                        double rv = Double.parseDouble(rightVal);
                        double delta = rv - lv;
                        if (delta > DELTA_THRESHOLD) {
                            float contrib = (float)(delta * W_METRIC);
                            score += contrib;
                            String name = metricEntry.getKey();
                            if (name.length() > 15) name = name.substring(0, 15);
                            contributors.add(new ScoreContributor(name, contrib, delta));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Negative score = improvement
        int resolvedCount = 0;
        for (var la : left.alerts()) {
            if (right.alerts().stream().noneMatch(a -> a.ruleName().equals(la.ruleName()))) {
                resolvedCount++;
            }
        }

        // Sort contributors by contribution (descending)
        contributors.sort((a, b) -> Float.compare(b.contribution, a.contribution));

        // --- Render score ---
        int scoreInt = Math.round(score);
        float sr, sg;
        String verdict;
        if (scoreInt == 0) { sr = 0.7f; sg = 0.7f; verdict = "NEUTRAL"; }
        else if (scoreInt < 10) { sr = 0.9f; sg = 0.7f; verdict = "MINOR"; }
        else if (scoreInt < 30) { sr = 1f; sg = 0.5f; verdict = "MODERATE"; }
        else if (scoreInt < 60) { sr = 1f; sg = 0.3f; verdict = "SIGNIFICANT"; }
        else { sr = 1f; sg = 0.15f; verdict = "SEVERE"; }

        // Score background
        textRenderer.drawRect(x - 4, y - 2, 120, 18, 0.1f, 0.1f, 0.1f, 0.85f, w, h);
        textRenderer.drawText(String.format("SCORE: %d [%s]", scoreInt, verdict),
            x, y, 1.8f, sr, sg, 0.2f, w, h);
        y += 18;

        if (resolvedCount > 0) {
            textRenderer.drawText(resolvedCount + " alerts resolved",
                x, y, scale * 0.85f, 0.2f, 1f, 0.3f, w, h);
            y += lineH;
        }

        y += 4; // gap before contributors

        // --- Render top contributors ---
        int shown = 0;
        for (var c : contributors) {
            if (shown >= 8 || y > h - 80) break;
            String sign = c.delta > 0 ? "+" : "";
            textRenderer.drawText(String.format("%.0f  %s %s%.1f",
                c.contribution, c.name, sign, c.delta),
                x, y, scale * 0.8f, sr * 0.8f, sg * 0.8f, 0.3f, w, h);
            y += lineH;
            shown++;
        }

        // --- Render NEW / RESOLVED ---
        y += 4;
        for (var ra : right.alerts()) {
            if (y > h - 80) break;
            boolean isNew = left.alerts().stream().noneMatch(a -> a.ruleName().equals(ra.ruleName()));
            if (isNew) {
                textRenderer.drawText("NEW: " + truncate(ra.ruleName(), 20),
                    x, y, scale * 0.85f, 1f, 0.2f, 0.2f, w, h);
                y += lineH;
            }
        }
        for (var la : left.alerts()) {
            if (y > h - 80) break;
            boolean resolved = right.alerts().stream().noneMatch(a -> a.ruleName().equals(la.ruleName()));
            if (resolved) {
                textRenderer.drawText("OK: " + truncate(la.ruleName(), 20),
                    x, y, scale * 0.85f, 0.2f, 1f, 0.3f, w, h);
                y += lineH;
            }
        }
    }

    private record ScoreContributor(String name, float contribution, double delta) {}

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    // --- Anomaly index ---

    private void buildAnomalyIndex() {
        int totalFrames = maxIndex + 1;
        perFrameScores = new float[totalFrames];

        // Compute score for every frame
        for (int i = 0; i < totalFrames; i++) {
            var left = getFrame(leftSnapshots, i);
            var right = getFrame(rightSnapshots, i);
            perFrameScores[i] = computeScore(left, right);
        }

        // Find local peaks (score > neighbors and above threshold)
        var peaks = new java.util.ArrayList<Integer>();
        float peakThreshold = 10f;
        for (int i = 1; i < totalFrames - 1; i++) {
            if (perFrameScores[i] >= peakThreshold
                && perFrameScores[i] >= perFrameScores[i - 1]
                && perFrameScores[i] >= perFrameScores[i + 1]) {
                // Deduplicate: skip if too close to last peak
                if (peaks.isEmpty() || i - peaks.getLast() > 10) {
                    peaks.add(i);
                }
            }
        }
        anomalyPeakIndices = peaks.stream().mapToInt(Integer::intValue).toArray();
    }

    private float computeScore(DebugViewSnapshot left, DebugViewSnapshot right) {
        float score = 0f;

        float ftDelta = right.summary().frameTimeMs() - left.summary().frameTimeMs();
        if (ftDelta > DELTA_THRESHOLD) score += ftDelta * W_FRAME_TIME;

        float budgetDelta = right.summary().budgetPercent() - left.summary().budgetPercent();
        if (budgetDelta > 1f) score += budgetDelta * W_BUDGET;

        int alertDelta = right.alerts().size() - left.alerts().size();
        if (alertDelta > 0) score += alertDelta * W_ALERT_COUNT;

        for (var ra : right.alerts()) {
            boolean isNew = left.alerts().stream().noneMatch(a -> a.ruleName().equals(ra.ruleName()));
            if (isNew) score += "ERROR".equals(ra.severity()) ? W_NEW_ERROR : W_NEW_WARNING;
        }

        for (var catKey : left.categories().keySet()) {
            var leftCat = left.categories().get(catKey);
            var rightCat = right.categories().get(catKey);
            if (rightCat == null) continue;
            for (var srcEntry : leftCat.sources().entrySet()) {
                var rightSrc = rightCat.sources().get(srcEntry.getKey());
                if (rightSrc == null) continue;
                for (var metricEntry : srcEntry.getValue().entrySet()) {
                    String rightVal = rightSrc.get(metricEntry.getKey());
                    if (rightVal == null) continue;
                    try {
                        double delta = Double.parseDouble(rightVal) - Double.parseDouble(metricEntry.getValue());
                        if (delta > DELTA_THRESHOLD) score += (float)(delta * W_METRIC);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return score;
    }

    private void jumpToNextAnomaly() {
        for (int pi : anomalyPeakIndices) {
            if (pi > currentIndex) {
                currentIndex = pi;
                playing = false;
                System.out.printf("-> Anomaly peak: frame %d (score: %.0f)%n",
                    currentIndex + 1, perFrameScores[currentIndex]);
                return;
            }
        }
    }

    private void jumpToPrevAnomaly() {
        for (int i = anomalyPeakIndices.length - 1; i >= 0; i--) {
            if (anomalyPeakIndices[i] < currentIndex) {
                currentIndex = anomalyPeakIndices[i];
                playing = false;
                System.out.printf("-> Anomaly peak: frame %d (score: %.0f)%n",
                    currentIndex + 1, perFrameScores[currentIndex]);
                return;
            }
        }
    }

    private static DebugSessionMetadata loadMeta(String ndjsonFile) {
        Path metaPath = Path.of(ndjsonFile.replace(".ndjson", ".meta.json"));
        try {
            if (Files.exists(metaPath)) {
                return DebugSessionMetadata.fromJson(Files.readString(metaPath));
            }
        } catch (IOException ignored) {}
        return null;
    }
}
