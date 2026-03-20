package org.dynamisengine.games.renderbasics;

import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.renderbasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.renderbasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.renderbasics.subsystem.WindowSubsystem;
import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * First visual output from the Dynamis engine.
 *
 * Opens a GLFW window with real OpenGL context. Draws:
 * - Dark blue background that brightens with more active pulses
 * - Yellow cursor square that moves with WASD
 * - Audio feedback on spawn/expire
 *
 * Proves: game state → visible result + audio through the canonical path.
 */
public final class RenderBasicsGame implements WorldApplication {

    // -- Components (inline for simplicity) ---
    record Position(float x, float y) {}
    record Velocity(float vx, float vy) {}
    record Lifetime(float age, float max) {
        boolean expired() { return age >= max; }
        Lifetime aged(float dt) { return new Lifetime(age + dt, max); }
    }
    record PulseTag() {}
    record PlayerTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("rb.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("rb.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("rb.life", Lifetime.class);
    static final ComponentKey<PulseTag> PULSE = ComponentKey.of("rb.pulse", PulseTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("rb.player", PlayerTag.class);

    // -- Input ---
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("renderbasics");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_SPACE=32, KEY_ESC=256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final DefaultInputProcessor processor;
    private final SimpleRenderer renderer = new SimpleRenderer();

    private EntityId playerEntity;

    public RenderBasicsGame(WindowSubsystem w, WindowInputSubsystem i,
                            AudioSubsystem a, DefaultInputProcessor p) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a; this.processor = p;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(SPAWN, List.of(new KeyBinding(KEY_SPACE, 0)),
                        QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(MOVE_X, List.of(
                                new AxisComposite2D(MOVE_X, new AxisId("_"), KEY_A, KEY_D, 0, 0, 1f),
                                new AxisComposite2D(MOVE_X, new AxisId("_2"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f)),
                        MOVE_Y, List.of(
                                new AxisComposite2D(new AxisId("_3"), MOVE_Y, 0, 0, KEY_S, KEY_W, 1f),
                                new AxisComposite2D(new AxisId("_4"), MOVE_Y, 0, 0, KEY_DOWN, KEY_UP, 1f))),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        // Initialize OpenGL (context is current on this thread from GlfwWindow)
        renderer.initialize();

        World world = context.ecsWorld();
        playerEntity = world.createEntity();
        world.add(playerEntity, POS, new Position(0, 0));
        world.add(playerEntity, PLAYER, new PlayerTag());

        System.out.println("=== Rendering Basics ===");
        System.out.println("First visual output. Yellow cursor, blue background.");
        System.out.println("WASD=move, Space=spawn pulses, Esc=quit");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Move player
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    Math.max(-9, Math.min(9, p.x() + mx * 8f * deltaSeconds)),
                    Math.max(-9, Math.min(9, p.y() + my * 8f * deltaSeconds))));
        }

        // Spawn
        if (frame.pressed(SPAWN)) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            EntityId e = world.createEntity();
            world.add(e, POS, new Position(p.x(), p.y()));
            world.add(e, VEL, new Velocity(
                    (float)(Math.random() - 0.5) * 4f,
                    (float)(Math.random() - 0.5) * 4f));
            world.add(e, LIFE, new Lifetime(0, 2f));
            world.add(e, PULSE, new PulseTag());
            playSound(660f, 0.2f, 0.003f, 0.06f);
        }

        // Systems: movement + lifetime
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * deltaSeconds,
                                            p.y() + v.vy() * deltaSeconds));
        }

        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            Lifetime updated = lt.aged(deltaSeconds);
            if (updated.expired()) expired.add(e);
            else world.add(e, LIFE, updated);
        }
        for (EntityId e : expired) {
            world.destroyEntity(e);
            playSound(330f, 0.1f, 0.01f, 0.1f);
        }

        // Render
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));
        int pulseCount = 0;
        for (EntityId ignored : world.query(new QueryBuilder().allOf(PULSE).build())) pulseCount++;

        var ws = windowSub.window().framebufferSize();
        renderer.render(playerPos.x(), playerPos.y(), pulseCount, ws.width(), ws.height());
        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[RenderBasics] Shutdown.");
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }
}
