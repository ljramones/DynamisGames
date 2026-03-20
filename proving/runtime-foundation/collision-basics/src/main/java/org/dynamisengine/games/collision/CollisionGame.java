package org.dynamisengine.games.collision;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.query.QuerySpec;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.collision.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * First game rule: pulse-to-target collision.
 *
 * Targets spawn in the arena. Press Space to fire pulses.
 * When a pulse overlaps a target, the target is destroyed with a hit sound
 * and visual flash. Score increments. Targets respawn after a delay.
 */
public final class CollisionGame implements WorldApplication {

    // Components
    record Position(float x, float y) {}
    record Velocity(float vx, float vy) {}
    record Lifetime(float age, float max) {
        boolean expired() { return age >= max; }
        Lifetime aged(float dt) { return new Lifetime(age + dt, max); }
        float progress() { return Math.min(age / max, 1f); }
    }
    record Radius(float r) {}
    record PulseTag() {}
    record TargetTag(float flashTimer) {
        TargetTag flashed() { return new TargetTag(0.3f); }
        TargetTag cooled(float dt) { return new TargetTag(Math.max(0, flashTimer - dt)); }
    }
    record PlayerTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("col.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("col.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("col.life", Lifetime.class);
    static final ComponentKey<Radius> RAD = ComponentKey.of("col.rad", Radius.class);
    static final ComponentKey<PulseTag> PULSE = ComponentKey.of("col.pulse", PulseTag.class);
    static final ComponentKey<TargetTag> TARGET = ComponentKey.of("col.target", TargetTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("col.player", PlayerTag.class);

    // Input
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("collision");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_SPACE=32,KEY_R=82,KEY_ESC=256;
    private static final float SR = AcousticConstants.SAMPLE_RATE;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final CollisionRenderer renderer = new CollisionRenderer();

    private EntityId playerEntity;
    private int score = 0;
    private float respawnTimer = 0;

    // Render arrays (pre-allocated)
    private final float[] pX=new float[256], pY=new float[256], pP=new float[256];
    private final float[] tX=new float[64], tY=new float[64], tR=new float[64], tF=new float[64];

    public CollisionGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
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
        world.add(playerEntity, POS, new Position(0, -7));
        world.add(playerEntity, PLAYER, new PlayerTag());

        spawnTargets(world);

        System.out.println("=== Collision Basics ===");
        System.out.println("Shoot pulses at targets. Score points on hits.");
        System.out.println("WASD=move, Space=fire, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Reset
        if (frame.pressed(RESET)) {
            for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build())) world.destroyEntity(e);
            for (EntityId e : world.query(new QueryBuilder().allOf(TARGET).build())) world.destroyEntity(e);
            world.add(playerEntity, POS, new Position(0, -7));
            score = 0;
            spawnTargets(world);
            playSound(440f, 0.15f, 0.001f, 0.02f);
            System.out.println("  [Reset] Score=0");
        }

        // Move player
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    clamp(p.x() + mx * 8f * dt, -9.5f, 9.5f),
                    clamp(p.y() + my * 8f * dt, -9.5f, 9.5f)));
        }

        // Fire pulse upward from cursor
        if (frame.pressed(SPAWN)) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            EntityId e = world.createEntity();
            world.add(e, POS, new Position(p.x(), p.y()));
            world.add(e, VEL, new Velocity(0, 12f)); // fires upward
            world.add(e, LIFE, new Lifetime(0, 2f));
            world.add(e, RAD, new Radius(0.8f));
            world.add(e, PULSE, new PulseTag());
            playSound(880f, 0.15f, 0.002f, 0.04f);
        }

        // System: movement
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * dt, p.y() + v.vy() * dt));
        }

        // System: lifetime
        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            Lifetime updated = lt.aged(dt);
            if (updated.expired()) expired.add(e);
            else world.add(e, LIFE, updated);
        }
        for (EntityId e : expired) world.destroyEntity(e);

        // System: collision (pulse vs target)
        List<EntityId> hitTargets = new ArrayList<>();
        List<EntityId> hitPulses = new ArrayList<>();
        QuerySpec pulseSpec = new QueryBuilder().allOf(PULSE, POS, RAD).build();
        QuerySpec targetSpec = new QueryBuilder().allOf(TARGET, POS, RAD).build();

        for (EntityId pulse : world.query(pulseSpec)) {
            Position pp = world.get(pulse, POS).orElseThrow();
            Radius pr = world.get(pulse, RAD).orElseThrow();
            for (EntityId target : world.query(targetSpec)) {
                if (hitTargets.contains(target)) continue;
                Position tp = world.get(target, POS).orElseThrow();
                Radius tr = world.get(target, RAD).orElseThrow();

                float dx = pp.x() - tp.x();
                float dy = pp.y() - tp.y();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float minDist = pr.r() + tr.r();

                if (dist < minDist) {
                    hitTargets.add(target);
                    hitPulses.add(pulse);
                    score++;
                    playSound(1320f, 0.25f, 0.001f, 0.08f); // hit sound
                    System.out.printf("  [HIT] Score=%d%n", score);
                }
            }
        }
        for (EntityId e : hitPulses) if (world.exists(e)) world.destroyEntity(e);
        for (EntityId e : hitTargets) if (world.exists(e)) world.destroyEntity(e);

        // System: target flash cooldown
        for (EntityId e : world.query(new QueryBuilder().allOf(TARGET).build())) {
            TargetTag tag = world.get(e, TARGET).orElseThrow();
            if (tag.flashTimer() > 0) {
                world.add(e, TARGET, tag.cooled(dt));
            }
        }

        // Respawn targets if all destroyed
        int targetCount = 0;
        for (EntityId ignored : world.query(targetSpec)) targetCount++;
        if (targetCount == 0) {
            respawnTimer += dt;
            if (respawnTimer > 1.0f) {
                spawnTargets(world);
                respawnTimer = 0;
                System.out.println("  [Wave] New targets spawned");
            }
        }

        // Render
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));

        int pc = 0;
        for (EntityId e : world.query(pulseSpec)) {
            if (pc >= pX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Lifetime lt = world.get(e, LIFE).orElse(new Lifetime(0, 1));
            pX[pc] = p.x(); pY[pc] = p.y(); pP[pc] = lt.progress(); pc++;
        }

        int tc = 0;
        for (EntityId e : world.query(targetSpec)) {
            if (tc >= tX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Radius r = world.get(e, RAD).orElseThrow();
            TargetTag tag = world.get(e, TARGET).orElse(new TargetTag(0));
            tX[tc] = p.x(); tY[tc] = p.y(); tR[tc] = r.r(); tF[tc] = tag.flashTimer(); tc++;
        }

        var ws = windowSub.window().framebufferSize();
        renderer.render(playerPos.x(), playerPos.y(),
                pX, pY, pP, pc,
                tX, tY, tR, tF, tc,
                score, ws.width(), ws.height());
        windowSub.window().swapBuffers();

        // Status
        if (context.tick() % 180 == 0 && context.tick() > 0) {
            System.out.printf("  [Status] Score=%d Targets=%d Pulses=%d%n", score, tc, pc);
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("%n[Collision] Final score: %d%n", score);
    }

    private void spawnTargets(World world) {
        float[][] positions = {
                {-6, 5}, {-3, 6}, {0, 7}, {3, 6}, {6, 5},
                {-4, 3}, {0, 4}, {4, 3}
        };
        for (float[] pos : positions) {
            EntityId t = world.createEntity();
            world.add(t, POS, new Position(pos[0], pos[1]));
            world.add(t, RAD, new Radius(1.0f));
            world.add(t, TARGET, new TargetTag(0));
        }
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, SR);
        var env = new Envelope(atk, dec, 0f, 0.02f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
