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
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("compare");
    private static final int KEY_SPACE = 32, KEY_COMMA = 44, KEY_PERIOD = 46;
    private static final int KEY_HOME = 268, KEY_END = 269, KEY_ESC = 256;
    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_TAB = 258;

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
    private boolean activeSideLeft = true; // highlighted side

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

        System.out.println("=== Debug Session Compare ===");
        System.out.printf("Left:  %s (%d frames)%n", leftFile, leftSnapshots.size());
        System.out.printf("Right: %s (%d frames)%n", rightFile, rightSnapshots.size());
        if (leftMeta != null) System.out.printf("  Left:  %s / %s%n", leftMeta.scenario(), leftMeta.recordedAt());
        if (rightMeta != null) System.out.printf("  Right: %s / %s%n", rightMeta.scenario(), rightMeta.recordedAt());
        System.out.println("Space=play  ,/.=step  Tab=switch side  1/2/3=speed  Esc=quit");
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

        // Progress bar
        float barY = h - 55;
        float barW = w - 20;
        textRenderer.drawRect(10, barY, barW, 6, 0.2f, 0.2f, 0.2f, 0.8f, w, h);
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

    private static final float DELTA_THRESHOLD = 0.5f; // ignore deltas below this

    private void renderDiffSummary(DebugViewSnapshot left, DebugViewSnapshot right,
                                    int centerX, int w, int h) {
        float x = centerX + 6;
        float y = 20;
        float scale = 1.5f;
        float lineH = 12f;

        // Summary delta
        float leftFt = left.summary().frameTimeMs();
        float rightFt = right.summary().frameTimeMs();
        float ftDelta = rightFt - leftFt;
        if (Math.abs(ftDelta) > DELTA_THRESHOLD) {
            String sign = ftDelta > 0 ? "+" : "";
            float r = ftDelta > 0 ? 1f : 0.3f;
            float g = ftDelta > 0 ? 0.3f : 1f;
            textRenderer.drawText(String.format("frame: %s%.1fms", sign, ftDelta),
                x, y, scale, r, g, 0.3f, w, h);
            y += lineH;
        }

        float leftBudget = left.summary().budgetPercent();
        float rightBudget = right.summary().budgetPercent();
        float budgetDelta = rightBudget - leftBudget;
        if (Math.abs(budgetDelta) > 1f) {
            String sign = budgetDelta > 0 ? "+" : "";
            float r = budgetDelta > 0 ? 1f : 0.3f;
            float g = budgetDelta > 0 ? 0.3f : 1f;
            textRenderer.drawText(String.format("budget: %s%.0f%%", sign, budgetDelta),
                x, y, scale, r, g, 0.3f, w, h);
            y += lineH;
        }

        // Alert count delta
        int leftAlerts = left.alerts().size();
        int rightAlerts = right.alerts().size();
        int alertDelta = rightAlerts - leftAlerts;
        if (alertDelta != 0) {
            String sign = alertDelta > 0 ? "+" : "";
            float r = alertDelta > 0 ? 1f : 0.3f;
            float g = alertDelta > 0 ? 0.3f : 1f;
            textRenderer.drawText(String.format("alerts: %s%d", sign, alertDelta),
                x, y, scale, r, g, 0.3f, w, h);
            y += lineH;
        }

        // Per-category metric deltas
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
                        if (Math.abs(delta) > DELTA_THRESHOLD) {
                            String sign = delta > 0 ? "+" : "";
                            // For most metrics, higher = worse (red)
                            float r = delta > 0 ? 0.9f : 0.3f;
                            float g = delta > 0 ? 0.4f : 0.9f;
                            String name = metricEntry.getKey();
                            if (name.length() > 12) name = name.substring(0, 12);
                            textRenderer.drawText(String.format("%s: %s%.1f", name, sign, delta),
                                x, y, scale * 0.85f, r, g, 0.3f, w, h);
                            y += lineH;
                            if (y > h - 80) return; // stop if running out of space
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // New alerts in right (regression indicators)
        for (var rightAlert : right.alerts()) {
            boolean isNew = left.alerts().stream()
                .noneMatch(a -> a.ruleName().equals(rightAlert.ruleName()));
            if (isNew) {
                textRenderer.drawText("NEW: " + rightAlert.ruleName(),
                    x, y, scale * 0.85f, 1f, 0.2f, 0.2f, w, h);
                y += lineH;
                if (y > h - 80) return;
            }
        }

        // Resolved alerts (improvement indicators)
        for (var leftAlert : left.alerts()) {
            boolean resolved = right.alerts().stream()
                .noneMatch(a -> a.ruleName().equals(leftAlert.ruleName()));
            if (resolved) {
                textRenderer.drawText("RESOLVED: " + leftAlert.ruleName(),
                    x, y, scale * 0.85f, 0.2f, 1f, 0.3f, w, h);
                y += lineH;
                if (y > h - 80) return;
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
