package org.dynamisengine.games.debugremote;

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
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.ui.debug.render.DebugOverlayRenderer;
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.ui.debug.runtime.DebugOverlayState;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Remote Debug Viewer — connects to a running engine via TCP and renders
 * the debug overlay from streamed {@link DebugViewSnapshot} data.
 *
 * <p>Reuses the exact same overlay pipeline as the in-engine version:
 * same builder, same renderer, same controls (focus, replay, panel cycling).
 * No engine runtime required — just a window + TCP stream.
 *
 * <p>Controls:
 * <pre>
 *   T     — toggle live/replay mode
 *   ,/.   — step backward/forward through received history
 *   F     — toggle focus mode
 *   [/]   — cycle panels in focus mode
 *   Tab   — toggle overlay
 *   Esc   — quit
 * </pre>
 */
public final class RemoteViewerGame implements WorldApplication {

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId FOCUS = new ActionId("focus");
    static final ActionId FOCUS_NEXT = new ActionId("focusNext");
    static final ActionId FOCUS_PREV = new ActionId("focusPrev");
    static final ActionId REPLAY_TOGGLE = new ActionId("replay");
    static final ActionId STEP_BACK = new ActionId("stepBack");
    static final ActionId STEP_FWD = new ActionId("stepFwd");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("remote");
    private static final int KEY_TAB = 258, KEY_F = 70, KEY_T = 84;
    private static final int KEY_LEFT_BRACKET = 91, KEY_RIGHT_BRACKET = 93;
    private static final int KEY_COMMA = 44, KEY_PERIOD = 46, KEY_ESC = 256;

    private final ProvingWindowSubsystem windowSub;
    private final ProvingInputSubsystem inputSub;
    private final String host;
    private final int port;

    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;
    private final DebugOverlayBuilder builder = new DebugOverlayBuilder(
        new DebugOverlayOptions(true, true, true, 60, 16, 8, false));
    private final DebugOverlayState state = new DebugOverlayState();

    private TcpSnapshotClient client;
    private boolean overlayVisible = true;
    private int lastPanelCount;
    private int replayIndex; // index into received history for replay

    public RemoteViewerGame(ProvingWindowSubsystem w, ProvingInputSubsystem i, String host, int port) {
        this.windowSub = w;
        this.inputSub = i;
        this.host = host;
        this.port = port;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                    Map.entry(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0))),
                    Map.entry(FOCUS, List.of(new KeyBinding(KEY_F, 0))),
                    Map.entry(FOCUS_NEXT, List.of(new KeyBinding(KEY_RIGHT_BRACKET, 0))),
                    Map.entry(FOCUS_PREV, List.of(new KeyBinding(KEY_LEFT_BRACKET, 0))),
                    Map.entry(REPLAY_TOGGLE, List.of(new KeyBinding(KEY_T, 0))),
                    Map.entry(STEP_BACK, List.of(new KeyBinding(KEY_COMMA, 0))),
                    Map.entry(STEP_FWD, List.of(new KeyBinding(KEY_PERIOD, 0))),
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

        client = new TcpSnapshotClient(host, port);
        client.start();

        System.out.println("=== Debug Remote Viewer ===");
        System.out.println("Connecting to " + host + ":" + port);
        System.out.println("T=replay  ,/.=step  F=focus  [/]=cycle  Tab=overlay  Esc=quit");
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
            if (frame.pressed(REPLAY_TOGGLE)) {
                if (state.isReplayMode()) {
                    state.exitReplay();
                } else {
                    replayIndex = Math.max(0, client.historySize() - 1);
                    state.toggleReplay(replayIndex);
                }
            }
            if (frame.pressed(STEP_BACK) && state.isReplayMode()) {
                replayIndex = Math.max(0, replayIndex - 10);
            }
            if (frame.pressed(STEP_FWD) && state.isReplayMode()) {
                replayIndex = Math.min(client.historySize() - 1, replayIndex + 10);
            }
        }

        // Get snapshot: live or replay
        DebugViewSnapshot snapshot;
        if (state.isReplayMode()) {
            snapshot = client.frame(replayIndex);
        } else {
            snapshot = client.latest();
        }

        // Render
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);
        glClearColor(0.04f, 0.05f, 0.08f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        if (overlayVisible) {
            List<DebugOverlayPanel> panels = new ArrayList<>(builder.buildAll(snapshot));
            lastPanelCount = panels.size();

            if (state.isFocusMode()) {
                var focused = state.focusedPanel(panels);
                if (focused != null) {
                    String category = focused.id().category();
                    var categoryEvents = snapshot.timelineEvents().stream()
                        .filter(e -> e.source().contains(category))
                        .toList();
                    glDisable(GL_DEPTH_TEST);
                    overlayRenderer.renderFocus(focused,
                        new DebugOverlayRenderer.LayoutBox(0, 0, w, h), categoryEvents);
                }
            } else {
                glDisable(GL_DEPTH_TEST);
                overlayRenderer.renderPanels(panels, w, h);
            }

            // Status bar
            textRenderer.beginFrame(w, h);
            String connStatus = client.isConnected()
                ? "CONNECTED (" + client.receivedCount() + " frames)"
                : "DISCONNECTED";
            String modeLabel = state.isReplayMode()
                ? String.format("[ REPLAY %d/%d ]", replayIndex + 1, client.historySize())
                : "[ LIVE ]";
            textRenderer.drawText(String.format("%s  %s  Tick: %d  History: %d",
                modeLabel, connStatus, snapshot.tick(), client.historySize()),
                10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText(
                "T=replay  ,/.=step  F=focus  [/]=cycle  Tab=overlay  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        client.stop();
        textRenderer.shutdown();
        System.out.println("[RemoteViewer] Shutdown. Received " + client.receivedCount() + " frames.");
    }
}
