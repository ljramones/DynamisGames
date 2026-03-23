package org.dynamisengine.games.debugplayer;

import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.ui.debug.builder.DebugOverlayBuilder;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
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
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("player");
    private static final int KEY_TAB = 258, KEY_F = 70, KEY_SPACE = 32;
    private static final int KEY_LEFT_BRACKET = 91, KEY_RIGHT_BRACKET = 93;
    private static final int KEY_COMMA = 44, KEY_PERIOD = 46, KEY_ESC = 256;
    private static final int KEY_HOME = 268, KEY_END = 269;
    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final String sessionFile;

    private final TextRenderer textRenderer = new TextRenderer();
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

    public SessionPlayerGame(WindowSubsystem w, WindowInputSubsystem i, String sessionFile) {
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

        currentIndex = 0;
        playing = false;

        System.out.println("Space=play/pause  ,/.=step  Home/End=jump  1/2/3=speed  F=focus  Esc=quit");
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
            if (frame.pressed(SPEED_SLOW)) { playbackSpeed = 0.25f; System.out.println("Speed: 0.25x"); }
            if (frame.pressed(SPEED_NORMAL)) { playbackSpeed = 1.0f; System.out.println("Speed: 1x"); }
            if (frame.pressed(SPEED_FAST)) { playbackSpeed = 2.0f; System.out.println("Speed: 2x"); }
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

            // Draw progress position
            if (!snapshots.isEmpty()) {
                float progress = (float) currentIndex / Math.max(1, snapshots.size() - 1);
                textRenderer.drawRect(10, barY, barW * progress, 8, 0.3f, 1f, 0.5f, 0.9f, w, h);
            }

            // Status
            String playState = playing ? "PLAYING " + playbackSpeed + "x" : "PAUSED";
            textRenderer.drawText(String.format("[%s]  Frame %d/%d  Tick: %d",
                playState, currentIndex + 1, snapshots.size(), snapshot.tick()),
                10, h - 45, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText(
                "Space=play/pause  ,/.=step  Home/End=jump  1/2/3=speed  F=focus  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
        System.out.printf("[SessionPlayer] Viewed %d/%d frames%n",
            currentIndex + 1, snapshots.size());
    }
}
