package org.dynamisengine.games.interaction;

import org.dynamisengine.games.interaction.subsystem.AudioSubsystem;
import org.dynamisengine.games.interaction.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.interaction.subsystem.WindowSubsystem;
import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.AxisId;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.bind.AxisComposite2D;
import org.dynamisengine.input.api.bind.KeyBinding;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.Map;

/**
 * Gameplay interaction sandbox.
 *
 * Demonstrates the canonical Dynamis gameplay loop:
 *   Input → gameplay intent → mutate state → update objects → trigger audio → report
 *
 * Move a cursor with WASD/arrows. Space spawns pulses that live briefly and expire.
 * Spawn and expiry trigger distinct audio cues through the real voice pipeline.
 */
public final class InteractionGame implements WorldApplication {

    // Actions
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT  = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("interaction");

    // GLFW keys
    private static final int KEY_W = 87, KEY_A = 65, KEY_S = 83, KEY_D = 68;
    private static final int KEY_UP = 265, KEY_DOWN = 264, KEY_LEFT = 263, KEY_RIGHT = 262;
    private static final int KEY_SPACE = 32, KEY_R = 82, KEY_ESC = 256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final DefaultInputProcessor processor;

    private InteractionState state;
    private InteractionAudio audio;
    private static final float CURSOR_SPEED = 8.0f;
    private static final float PULSE_LIFETIME = 2.0f;

    public InteractionGame(WindowSubsystem windowSub, WindowInputSubsystem inputSub,
                           AudioSubsystem audioSub, DefaultInputProcessor processor) {
        this.windowSub = windowSub;
        this.inputSub = inputSub;
        this.audioSub = audioSub;
        this.processor = processor;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(
                        SPAWN, List.of(new KeyBinding(KEY_SPACE, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        QUIT,  List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(
                        MOVE_X, List.of(new AxisComposite2D(
                                MOVE_X, new AxisId("_unused"),
                                KEY_A, KEY_D, 0, 0, 1.0f),
                                new AxisComposite2D(
                                MOVE_X, new AxisId("_unused2"),
                                KEY_LEFT, KEY_RIGHT, 0, 0, 1.0f)),
                        MOVE_Y, List.of(new AxisComposite2D(
                                new AxisId("_unused3"), MOVE_Y,
                                0, 0, KEY_S, KEY_W, 1.0f),
                                new AxisComposite2D(
                                new AxisId("_unused4"), MOVE_Y,
                                0, 0, KEY_DOWN, KEY_UP, 1.0f))),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        state = new InteractionState();
        audio = new InteractionAudio(audioSub.mixer());

        System.out.println("=== Interaction Sandbox ===");
        System.out.println("Move cursor, spawn pulses, hear audio feedback.");
        System.out.println();
        System.out.println("Controls:");
        System.out.println("  WASD / Arrows → Move cursor");
        System.out.println("  Space         → Spawn pulse");
        System.out.println("  R             → Reset");
        System.out.println("  Esc           → Quit");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Reset
        if (frame.pressed(RESET)) {
            state.reset();
            audio.onReset();
            System.out.println("  [Reset] State cleared");
        }

        // Move cursor
        float mx = frame.axis(MOVE_X);
        float my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            state.moveCursor(mx * CURSOR_SPEED * deltaSeconds, my * CURSOR_SPEED * deltaSeconds);
        }

        // Spawn
        if (frame.pressed(SPAWN)) {
            Pulse p = state.spawn(PULSE_LIFETIME);
            audio.onSpawn();
            System.out.printf("  [Spawn] Pulse #%d at (%.1f, %.1f) lifetime=%.1fs%n",
                    p.id, p.x, p.y, p.lifetimeSeconds);
        }

        // Update objects
        List<Pulse> expired = state.update(deltaSeconds);
        for (Pulse p : expired) {
            audio.onExpire();
            System.out.printf("  [Expire] Pulse #%d at (%.1f, %.1f) lived %.2fs%n",
                    p.id, p.x, p.y, p.ageSeconds());
        }

        // Status every 2 seconds
        if (context.tick() % 120 == 0 && context.tick() > 0) {
            System.out.printf("  [Status] cursor=(%.1f,%.1f) active=%d spawned=%d expired=%d%n",
                    state.cursorX, state.cursorY,
                    state.activeCount(), state.totalSpawned(), state.totalExpired());
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("%n[Interaction] Final: spawned=%d expired=%d%n",
                state.totalSpawned(), state.totalExpired());
        var t = context.telemetry();
        if (t != null) System.out.println(t.statusLine());
        System.out.println("[Interaction] Shutdown.");
    }
}
