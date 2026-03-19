package org.dynamisengine.games.interrendered;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.interrendered.subsystem.AudioSubsystem;
import org.dynamisengine.games.interrendered.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.interrendered.subsystem.WindowSubsystem;
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
 * Full interactive gameplay with visible rendering.
 *
 * Cursor moves, pulses spawn and drift, you can SEE them animate and expire.
 * Audio fires on spawn and expire. ECS is the authoritative state model.
 * Rendering reads from ECS — it does not become the state.
 */
public final class InteractionRenderedGame implements WorldApplication {

    // Components
    record Position(float x, float y) {}
    record Velocity(float vx, float vy) {}
    record Lifetime(float age, float max) {
        boolean expired() { return age >= max; }
        Lifetime aged(float dt) { return new Lifetime(age + dt, max); }
        float progress() { return Math.min(age / max, 1f); }
    }
    record PulseTag() {}
    record PlayerTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("ir.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("ir.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("ir.life", Lifetime.class);
    static final ComponentKey<PulseTag> PULSE = ComponentKey.of("ir.pulse", PulseTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("ir.player", PlayerTag.class);

    // Input
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("interrendered");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_SPACE=32, KEY_R=82, KEY_ESC=256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final DefaultInputProcessor processor;
    private final SceneRenderer renderer = new SceneRenderer();

    // Pre-allocated render arrays to avoid per-frame allocation
    private final float[] renderX = new float[256];
    private final float[] renderY = new float[256];
    private final float[] renderP = new float[256];

    private EntityId playerEntity;
    private long totalSpawned = 0;

    public InteractionRenderedGame(WindowSubsystem w, WindowInputSubsystem i,
                                   AudioSubsystem a, DefaultInputProcessor p) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a; this.processor = p;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(SPAWN, List.of(new KeyBinding(KEY_SPACE, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
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
        renderer.initialize();
        World world = context.ecsWorld();
        playerEntity = world.createEntity();
        world.add(playerEntity, POS, new Position(0, 0));
        world.add(playerEntity, PLAYER, new PlayerTag());

        System.out.println("=== Interaction Rendered ===");
        System.out.println("See pulses spawn, drift, shrink, and expire.");
        System.out.println("WASD=move, Space=spawn, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Reset
        if (frame.pressed(RESET)) {
            for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build()))
                world.destroyEntity(e);
            world.add(playerEntity, POS, new Position(0, 0));
            totalSpawned = 0;
            playSound(440f, 0.15f, 0.001f, 0.02f);
        }

        // Move player
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    clamp(p.x() + mx * 8f * deltaSeconds, -9.5f, 9.5f),
                    clamp(p.y() + my * 8f * deltaSeconds, -9.5f, 9.5f)));
        }

        // Spawn
        if (frame.pressed(SPAWN)) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            EntityId e = world.createEntity();
            world.add(e, POS, new Position(p.x(), p.y()));
            world.add(e, VEL, new Velocity(
                    (float)(Math.random() - 0.5) * 3f,
                    (float)(Math.random() - 0.5) * 3f));
            world.add(e, LIFE, new Lifetime(0, 3f)); // 3 second lifetime
            world.add(e, PULSE, new PulseTag());
            totalSpawned++;
            playSound(660f, 0.2f, 0.003f, 0.06f);
        }

        // Systems: movement
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * deltaSeconds,
                                            p.y() + v.vy() * deltaSeconds));
        }

        // Systems: lifetime + expire
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

        // Render: read ECS state into render arrays
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));
        int count = 0;
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE, POS, LIFE).build())) {
            if (count >= renderX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            renderX[count] = p.x();
            renderY[count] = p.y();
            renderP[count] = lt.progress();
            count++;
        }

        var ws = windowSub.window().framebufferSize();
        renderer.render(playerPos.x(), playerPos.y(),
                renderX, renderY, renderP, count,
                ws.width(), ws.height());
        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("[InteractionRendered] Spawned=%d Entities=%d%n",
                totalSpawned, context.ecsWorld().entities().size());
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
