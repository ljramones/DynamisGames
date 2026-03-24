package org.dynamisengine.games.debugplayer;

import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.games.proving.ProvingInputSubsystem;
import org.dynamisengine.games.proving.ProvingWindowSubsystem;

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
import org.dynamisengine.ui.debug.render.DebugOverlayRenderer;
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.ui.debug.runtime.DebugOverlayState;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Debug Session Player — loads a recorded NDJSON session file and provides
 * full offline replay with the same overlay, focus mode, and controls as
 * live debugging.
 *
 * <p>Controls:
 * <pre>
 *   Space   — play/pause auto-advance
 *   ,/.     — step backward/forward 1 frame
 *   Shift   — step 10 frames (hold with ,/.)
 *   Home    — jump to first frame
 *   End     — jump to last frame
 *   F       — toggle focus mode
 *   [/]     — cycle panels in focus mode
 *   1/2/3   — playback speed (0.25x / 1x / 2x)
 *   Tab     — toggle overlay
 *   Esc     — quit
 * </pre>
 */
public final class SessionPlayerGame implements WorldApplication {

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId FOCUS = new ActionId("focus");
    static final ActionId FOCUS_NEXT = new ActionId("focusNext");
    static final ActionId FOCUS_PREV = new ActionId("focusPrev");
    static final ActionId PLAY_PAUSE = new ActionId("playPause");
    static final ActionId STEP_BACK = new ActionId("stepBack");
    static final ActionId STEP_FWD = new ActionId("stepFwd");
    static final ActionId JUMP_START = new ActionId("jumpStart");
    static final ActionId JUMP_END = new ActionId("jumpEnd");
    static final ActionId SPEED_SLOW = new ActionId("speedSlow");
    static final ActionId SPEED_NORMAL = new ActionId("speedNormal");
    static final ActionId SPEED_FAST = new ActionId("speedFast");
    static final ActionId NEXT_EVENT = new ActionId("nextEvent");
    static final ActionId PREV_EVENT = new ActionId("prevEvent");
    static final ActionId BOOKMARK = new ActionId("bookmark");
    static final ActionId NEXT_BOOKMARK = new ActionId("nextBookmark");
    static final ActionId LABEL_BOOKMARK = new ActionId("labelBookmark");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("player");
    private static final int KEY_TAB = 258, KEY_F = 70, KEY_SPACE = 32;
    private static final int KEY_LEFT_BRACKET = 91, KEY_RIGHT_BRACKET = 93;
    private static final int KEY_COMMA = 44, KEY_PERIOD = 46, KEY_ESC = 256;
    private static final int KEY_HOME = 268, KEY_END = 269;
    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51;
    private static final int KEY_N = 78, KEY_B = 66, KEY_M = 77, KEY_J = 74, KEY_L = 76;

    private final ProvingWindowSubsystem windowSub;
    private final ProvingInputSubsystem inputSub;
    private final String sessionFile;

    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;
    private final DebugOverlayBuilder builder = new DebugOverlayBuilder(
        new DebugOverlayOptions(true, true, true, 60, 16, 8, false));
    private final DebugOverlayState state = new DebugOverlayState();

    private List<DebugViewSnapshot> snapshots;
    private int currentIndex;
    private boolean playing;
    private float playbackSpeed = 1.0f;
    private float accumulator;
    private boolean overlayVisible = true;
    private int lastPanelCount;

    private int[] eventFrameIndices;
    private final java.util.List<DebugSessionMetadata.Bookmark> bookmarks = new java.util.ArrayList<>();
    private DebugSessionMetadata metadata;
    private Path metadataPath;

    // Preset labels for quick labeling (cycle with L)
    private static final String[] PRESET_LABELS = {
        "spike", "recovery", "degradation start", "alert onset",
        "physics issue", "audio issue", "ECS burst", "interesting",
        "regression", "good baseline"
    };

    public SessionPlayerGame(ProvingWindowSubsystem w, ProvingInputSubsystem i, String sessionFile) {
        this.windowSub = w;
        this.inputSub = i;
        this.sessionFile = sessionFile;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                    Map.entry(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0))),
                    Map.entry(FOCUS, List.of(new KeyBinding(KEY_F, 0))),
                    Map.entry(FOCUS_NEXT, List.of(new KeyBinding(KEY_RIGHT_BRACKET, 0))),
                    Map.entry(FOCUS_PREV, List.of(new KeyBinding(KEY_LEFT_BRACKET, 0))),
                    Map.entry(PLAY_PAUSE, List.of(new KeyBinding(KEY_SPACE, 0))),
                    Map.entry(STEP_BACK, List.of(new KeyBinding(KEY_COMMA, 0))),
                    Map.entry(STEP_FWD, List.of(new KeyBinding(KEY_PERIOD, 0))),
                    Map.entry(JUMP_START, List.of(new KeyBinding(KEY_HOME, 0))),
                    Map.entry(JUMP_END, List.of(new KeyBinding(KEY_END, 0))),
                    Map.entry(SPEED_SLOW, List.of(new KeyBinding(KEY_1, 0))),
                    Map.entry(SPEED_NORMAL, List.of(new KeyBinding(KEY_2, 0))),
                    Map.entry(SPEED_FAST, List.of(new KeyBinding(KEY_3, 0))),
                    Map.entry(NEXT_EVENT, List.of(new KeyBinding(KEY_N, 0))),
                    Map.entry(PREV_EVENT, List.of(new KeyBinding(KEY_B, 0))),
                    Map.entry(BOOKMARK, List.of(new KeyBinding(KEY_M, 0))),
                    Map.entry(NEXT_BOOKMARK, List.of(new KeyBinding(KEY_J, 0))),
                    Map.entry(LABEL_BOOKMARK, List.of(new KeyBinding(KEY_L, 0))),
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

        try {
            snapshots = DebugSnapshotReplayLoader.load(Path.of(sessionFile));
            System.out.println("=== Debug Session Player ===");
            System.out.println("Loaded " + snapshots.size() + " frames from " + sessionFile);
            if (!snapshots.isEmpty()) {
                long firstTick = snapshots.getFirst().tick();
                long lastTick = snapshots.getLast().tick();
                System.out.printf("Range: T%d - T%d (%.1fs at 60Hz)%n",
                    firstTick, lastTick, (lastTick - firstTick) / 60.0);
            }
        } catch (IOException e) {
            System.err.println("Failed to load session: " + e.getMessage());
            snapshots = List.of();
        }

        buildEventIndex();

        // Load session metadata sidecar if available
        metadataPath = Path.of(sessionFile.replace(".ndjson", ".meta.json"));
        try {
            if (java.nio.file.Files.exists(metadataPath)) {
                metadata = DebugSessionMetadata.fromJson(java.nio.file.Files.readString(metadataPath));
                System.out.printf("Session: %s  Backend: %s  Scenario: %s  Recorded: %s%n",
                    metadata.engineVersion(), metadata.backend(),
                    metadata.scenario(), metadata.recordedAt());
                // Restore bookmarks from metadata
                for (var bm : metadata.bookmarks()) {
                    if (bookmarks.stream().noneMatch(b -> b.frameIndex() == bm.frameIndex())) {
                        bookmarks.add(bm);
                    }
                }
                bookmarks.sort(java.util.Comparator.comparingInt(DebugSessionMetadata.Bookmark::frameIndex));
                if (!bookmarks.isEmpty()) {
                    System.out.println("Restored " + bookmarks.size() + " bookmarks:");
                    for (var bm : bookmarks) {
                        String label = bm.label().isEmpty() ? "(unlabeled)" : bm.label();
                        System.out.printf("  Frame %d: %s%n", bm.frameIndex() + 1, label);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("No metadata sidecar found (optional)");
        }

        currentIndex = 0;
        playing = false;

        System.out.println("Space=play  ,/.=step  N/B=event  M=bookmark  J=next bookmark  F=focus  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(FOCUS)) state.toggleFocus();
            if (frame.pressed(FOCUS_NEXT)) state.nextPanel(lastPanelCount);
            if (frame.pressed(FOCUS_PREV)) state.previousPanel(lastPanelCount);
            if (frame.pressed(PLAY_PAUSE)) playing = !playing;
            if (frame.pressed(STEP_BACK)) currentIndex = Math.max(0, currentIndex - 1);
            if (frame.pressed(STEP_FWD)) currentIndex = Math.min(snapshots.size() - 1, currentIndex + 1);
            if (frame.pressed(JUMP_START)) currentIndex = 0;
            if (frame.pressed(JUMP_END)) currentIndex = Math.max(0, snapshots.size() - 1);
            if (frame.pressed(SPEED_SLOW)) { playbackSpeed = 0.25f; }
            if (frame.pressed(SPEED_NORMAL)) { playbackSpeed = 1.0f; }
            if (frame.pressed(SPEED_FAST)) { playbackSpeed = 2.0f; }
            if (frame.pressed(NEXT_EVENT)) jumpToNextEvent();
            if (frame.pressed(PREV_EVENT)) jumpToPrevEvent();
            if (frame.pressed(BOOKMARK)) addBookmark();
            if (frame.pressed(NEXT_BOOKMARK)) jumpToNextBookmark();
            if (frame.pressed(LABEL_BOOKMARK)) cycleLabelOnCurrentBookmark();
        }

        // Auto-advance when playing
        if (playing && !snapshots.isEmpty()) {
            accumulator += dt * playbackSpeed;
            float frameInterval = 1f / 60f; // assume 60Hz recording
            while (accumulator >= frameInterval && currentIndex < snapshots.size() - 1) {
                currentIndex++;
                accumulator -= frameInterval;
            }
            if (currentIndex >= snapshots.size() - 1) {
                playing = false; // stop at end
            }
        }

        // Get current snapshot
        DebugViewSnapshot snapshot = snapshots.isEmpty()
            ? DebugViewSnapshot.EMPTY
            : snapshots.get(currentIndex);

        // Render
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);
        glClearColor(0.03f, 0.04f, 0.06f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        if (overlayVisible) {
            List<DebugOverlayPanel> panels = new ArrayList<>(builder.buildAll(snapshot));
            lastPanelCount = panels.size();

            glDisable(GL_DEPTH_TEST);

            if (state.isFocusMode()) {
                var focused = state.focusedPanel(panels);
                if (focused != null) {
                    var categoryEvents = snapshot.timelineEvents().stream()
                        .filter(e -> e.source().contains(focused.id().category()))
                        .toList();
                    overlayRenderer.renderFocus(focused,
                        new DebugOverlayRenderer.LayoutBox(0, 0, w, h), categoryEvents);
                }
            } else {
                overlayRenderer.renderPanels(panels, w, h);
            }

            // Progress bar
            textRenderer.beginFrame(w, h);

            // Draw progress bar background
            float barY = h - 60;
            float barW = w - 20;
            textRenderer.drawRect(10, barY, barW, 8, 0.2f, 0.2f, 0.2f, 0.8f, w, h);

            // Draw event markers on progress bar
            if (!snapshots.isEmpty()) {
                int totalFrames = snapshots.size();
                for (int ei : eventFrameIndices) {
                    float ex = 10 + barW * ((float) ei / Math.max(1, totalFrames - 1));
                    textRenderer.drawRect(ex, barY - 2, 2, 12, 1f, 0.3f, 0.2f, 0.8f, w, h);
                }
                // Draw bookmark markers (blue ticks, with labels)
                for (var bm : bookmarks) {
                    float bx = 10 + barW * ((float) bm.frameIndex() / Math.max(1, totalFrames - 1));
                    textRenderer.drawRect(bx, barY - 3, 2, 14, 0.3f, 0.7f, 1f, 0.9f, w, h);
                }
                // Draw progress position
                float progress = (float) currentIndex / Math.max(1, totalFrames - 1);
                textRenderer.drawRect(10, barY, barW * progress, 8, 0.3f, 1f, 0.5f, 0.9f, w, h);
            }

            // Status
            String playState = playing ? "PLAYING " + playbackSpeed + "x" : "PAUSED";
            // Show bookmark label if current frame is bookmarked
            String bmLabel = bookmarks.stream()
                .filter(b -> b.frameIndex() == currentIndex)
                .findFirst()
                .map(b -> b.label().isEmpty() ? "  [bookmarked]" : "  [" + b.label() + "]")
                .orElse("");
            String bmInfo = bookmarks.isEmpty() ? "" : "  BM:" + bookmarks.size() + bmLabel;
            textRenderer.drawText(String.format("[%s]  Frame %d/%d  Tick: %d  Events: %d%s",
                playState, currentIndex + 1, snapshots.size(), snapshot.tick(),
                eventFrameIndices.length, bmInfo),
                10, h - 45, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText(
                "Space=play  ,/.=step  N/B=event  M=bookmark  J=next bookmark  F=focus  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        // Save bookmarks to metadata sidecar
        if (!bookmarks.isEmpty() && metadataPath != null) {
            if (metadata == null) {
                metadata = new DebugSessionMetadata();
                metadata.setFrameCount(snapshots.size());
                if (!snapshots.isEmpty()) {
                    metadata.setFirstTick(snapshots.getFirst().tick());
                    metadata.setLastTick(snapshots.getLast().tick());
                }
            }
            metadata.bookmarks().clear();
            for (var bm : bookmarks) {
                metadata.addBookmark(bm.frameIndex(), bm.label());
            }
            try {
                java.nio.file.Files.writeString(metadataPath, metadata.toJson());
                System.out.println("Bookmarks saved to " + metadataPath);
            } catch (IOException e) {
                System.err.println("Failed to save bookmarks: " + e.getMessage());
            }
        }

        textRenderer.shutdown();
        System.out.printf("[SessionPlayer] Viewed %d/%d frames, %d bookmarks%n",
            currentIndex + 1, snapshots.size(), bookmarks.size());
    }

    // --- Event index + navigation ---

    private void buildEventIndex() {
        var indices = new java.util.ArrayList<Integer>();
        for (int i = 0; i < snapshots.size(); i++) {
            var snap = snapshots.get(i);
            // Significant = has WARNING/ERROR/CRITICAL timeline events
            if (!snap.timelineEvents().isEmpty()) {
                boolean significant = snap.timelineEvents().stream()
                    .anyMatch(e -> "WARNING".equals(e.severity()) ||
                                   "ERROR".equals(e.severity()) ||
                                   "CRITICAL".equals(e.severity()));
                if (significant) indices.add(i);
            }
            // Also significant if alerts changed from previous frame
            if (i > 0 && snap.alerts().size() != snapshots.get(i - 1).alerts().size()) {
                if (!indices.contains(i)) indices.add(i);
            }
        }
        eventFrameIndices = indices.stream().mapToInt(Integer::intValue).toArray();
        System.out.println("Event index: " + eventFrameIndices.length + " significant frames");
    }

    private void jumpToNextEvent() {
        for (int ei : eventFrameIndices) {
            if (ei > currentIndex) {
                currentIndex = ei;
                playing = false;
                return;
            }
        }
    }

    private void jumpToPrevEvent() {
        for (int i = eventFrameIndices.length - 1; i >= 0; i--) {
            if (eventFrameIndices[i] < currentIndex) {
                currentIndex = eventFrameIndices[i];
                playing = false;
                return;
            }
        }
    }

    private void addBookmark() {
        if (bookmarks.stream().anyMatch(b -> b.frameIndex() == currentIndex)) return;

        // Auto-generate label from current frame state
        var snap = snapshots.get(currentIndex);
        String autoLabel = generateAutoLabel(snap);

        bookmarks.add(new DebugSessionMetadata.Bookmark(currentIndex, autoLabel));
        bookmarks.sort(java.util.Comparator.comparingInt(DebugSessionMetadata.Bookmark::frameIndex));
        System.out.printf("Bookmark: frame %d [%s]%n", currentIndex + 1, autoLabel);
    }

    private void cycleLabelOnCurrentBookmark() {
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).frameIndex() == currentIndex) {
                String currentLabel = bookmarks.get(i).label();
                // Find current label in presets and cycle to next
                int presetIdx = -1;
                for (int j = 0; j < PRESET_LABELS.length; j++) {
                    if (PRESET_LABELS[j].equals(currentLabel)) { presetIdx = j; break; }
                }
                String newLabel = PRESET_LABELS[(presetIdx + 1) % PRESET_LABELS.length];
                bookmarks.set(i, new DebugSessionMetadata.Bookmark(currentIndex, newLabel));
                System.out.printf("Bookmark relabeled: frame %d [%s]%n", currentIndex + 1, newLabel);
                return;
            }
        }
        // No bookmark at current frame — create one first
        addBookmark();
    }

    private String generateAutoLabel(DebugViewSnapshot snap) {
        if (!snap.alerts().isEmpty()) {
            var first = snap.alerts().getFirst();
            return first.ruleName();
        }
        if (!snap.timelineEvents().isEmpty()) {
            var last = snap.timelineEvents().getLast();
            return last.name();
        }
        return "T" + snap.tick();
    }

    private void jumpToNextBookmark() {
        if (bookmarks.isEmpty()) return;
        for (var bm : bookmarks) {
            if (bm.frameIndex() > currentIndex) {
                currentIndex = bm.frameIndex();
                playing = false;
                System.out.printf("-> Bookmark: frame %d [%s]%n", currentIndex + 1, bm.label());
                return;
            }
        }
        // Wrap to first
        var first = bookmarks.getFirst();
        currentIndex = first.frameIndex();
        playing = false;
        System.out.printf("-> Bookmark: frame %d [%s]%n", currentIndex + 1, first.label());
    }
}
