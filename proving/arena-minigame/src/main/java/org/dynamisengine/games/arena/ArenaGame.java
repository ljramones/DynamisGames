package org.dynamisengine.games.arena;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.query.QuerySpec;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.arena.subsystem.*;
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
 * Arena Minigame — first playable micro-game in Dynamis.
 *
 * 3 waves of targets. Hit them with pulses. Score points.
 * Expired targets count as misses. 5 misses = game over.
 * Clear all 3 waves = win. Press R to restart.
 */
public final class ArenaGame implements WorldApplication {

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
    record TargetTag() {}
    record PlayerTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("ar.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("ar.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("ar.life", Lifetime.class);
    static final ComponentKey<Radius> RAD = ComponentKey.of("ar.rad", Radius.class);
    static final ComponentKey<PulseTag> PULSE = ComponentKey.of("ar.pulse", PulseTag.class);
    static final ComponentKey<TargetTag> TARGET = ComponentKey.of("ar.target", TargetTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("ar.player", PlayerTag.class);

    static final ActionId FIRE = new ActionId("fire");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("arena");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_SPACE=32,KEY_R=82,KEY_ESC=256;
    private static final float SR = AcousticConstants.SAMPLE_RATE;
    private static final int TOTAL_WAVES = 3;
    private static final int MAX_MISSES = 5;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final ArenaRenderer renderer = new ArenaRenderer();

    private EntityId playerEntity;
    private GameMode mode = GameMode.READY;
    private int wave = 0;
    private int score = 0;
    private int misses = 0;
    private float waveClearTimer = 0;

    private final float[] pX=new float[256],pY=new float[256],pP=new float[256];
    private final float[] tX=new float[64],tY=new float[64],tR=new float[64];

    public ArenaGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(FIRE, List.of(new KeyBinding(KEY_SPACE, 0)),
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

        System.out.println("=== Arena Minigame ===");
        System.out.println("3 waves. Hit targets. 5 misses = game over.");
        System.out.println("Space=fire/start, WASD=move, R=restart, Esc=quit");
        System.out.println();
        System.out.println("  Press SPACE to start!");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();
        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Restart from any end state or during play
        if (frame.pressed(RESET)) {
            restart(world);
            return;
        }

        switch (mode) {
            case READY -> {
                if (frame.pressed(FIRE)) {
                    mode = GameMode.PLAYING;
                    wave = 1;
                    score = 0;
                    misses = 0;
                    spawnWave(world, wave);
                    System.out.println("  [Wave 1] GO!");
                    playSound(440f, 0.2f, 0.005f, 0.1f);
                }
            }
            case PLAYING -> updatePlaying(context, frame, world, dt);
            case WAVE_CLEAR -> {
                waveClearTimer += dt;
                if (waveClearTimer > 1.5f) {
                    wave++;
                    if (wave > TOTAL_WAVES) {
                        mode = GameMode.WON;
                        System.out.printf("  [WIN] Final score: %d%n", score);
                        playSound(880f, 0.3f, 0.01f, 0.3f);
                        playSound(1320f, 0.2f, 0.1f, 0.4f);
                    } else {
                        mode = GameMode.PLAYING;
                        spawnWave(world, wave);
                        System.out.printf("  [Wave %d] GO!%n", wave);
                        playSound(440f, 0.2f, 0.005f, 0.1f);
                    }
                }
            }
            case WON, LOST -> {
                // Wait for restart
            }
        }

        // Always move cursor
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    clamp(p.x() + mx * 8f * dt, -9.5f, 9.5f),
                    clamp(p.y() + my * 8f * dt, -9.5f, 9.5f)));
        }

        // Always render
        renderFrame(world);
    }

    private void updatePlaying(GameContext context, InputFrame frame, World world, float dt) {
        // Fire
        if (frame.pressed(FIRE)) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            EntityId e = world.createEntity();
            world.add(e, POS, new Position(p.x(), p.y()));
            world.add(e, VEL, new Velocity(0, 14f));
            world.add(e, LIFE, new Lifetime(0, 1.5f));
            world.add(e, RAD, new Radius(0.7f));
            world.add(e, PULSE, new PulseTag());
            playSound(880f, 0.12f, 0.002f, 0.03f);
        }

        // Movement system
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * dt, p.y() + v.vy() * dt));
        }

        // Lifetime + expiry
        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            Lifetime updated = lt.aged(dt);
            if (updated.expired()) expired.add(e);
            else world.add(e, LIFE, updated);
        }

        // Check expired targets = misses
        for (EntityId e : expired) {
            if (world.has(e, TARGET)) {
                misses++;
                playSound(220f, 0.15f, 0.01f, 0.15f); // miss sound
                System.out.printf("  [MISS] misses=%d/%d%n", misses, MAX_MISSES);
                if (misses >= MAX_MISSES) {
                    mode = GameMode.LOST;
                    System.out.printf("  [LOST] Score: %d%n", score);
                    playSound(110f, 0.3f, 0.02f, 0.5f);
                }
            }
            if (world.exists(e)) world.destroyEntity(e);
        }

        // Collision: pulse vs target
        List<EntityId> hitTargets = new ArrayList<>();
        List<EntityId> hitPulses = new ArrayList<>();
        QuerySpec pulseQ = new QueryBuilder().allOf(PULSE, POS, RAD).build();
        QuerySpec targetQ = new QueryBuilder().allOf(TARGET, POS, RAD).build();

        for (EntityId pulse : world.query(pulseQ)) {
            Position pp = world.get(pulse, POS).orElseThrow();
            Radius pr = world.get(pulse, RAD).orElseThrow();
            for (EntityId target : world.query(targetQ)) {
                if (hitTargets.contains(target)) continue;
                Position tp = world.get(target, POS).orElseThrow();
                Radius tr = world.get(target, RAD).orElseThrow();
                float dx = pp.x() - tp.x(), dy = pp.y() - tp.y();
                if (dx*dx + dy*dy < (pr.r() + tr.r()) * (pr.r() + tr.r())) {
                    hitTargets.add(target);
                    hitPulses.add(pulse);
                    score++;
                    playSound(1320f, 0.25f, 0.001f, 0.06f);
                }
            }
        }
        for (EntityId e : hitPulses) if (world.exists(e)) world.destroyEntity(e);
        for (EntityId e : hitTargets) if (world.exists(e)) world.destroyEntity(e);

        // Check wave clear
        int remaining = 0;
        for (EntityId ignored : world.query(targetQ)) remaining++;
        if (remaining == 0 && mode == GameMode.PLAYING) {
            mode = GameMode.WAVE_CLEAR;
            waveClearTimer = 0;
            System.out.printf("  [CLEAR] Wave %d cleared! Score=%d%n", wave, score);
            playSound(660f, 0.2f, 0.005f, 0.15f);
        }
    }

    private void spawnWave(World world, int waveNum) {
        // Clear leftover pulses
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build()))
            if (world.exists(e)) world.destroyEntity(e);

        int count = 2 + waveNum * 2; // wave 1=4, 2=6, 3=8
        float targetLife = 6f - waveNum * 0.5f; // shorter lifetime in later waves
        float spread = 8f;

        for (int i = 0; i < count; i++) {
            EntityId t = world.createEntity();
            float x = -spread/2 + (spread * i / (count - 1));
            float y = 4f + (float)(Math.random() * 4);
            world.add(t, POS, new Position(x, y));
            world.add(t, RAD, new Radius(0.9f));
            world.add(t, LIFE, new Lifetime(0, targetLife));
            world.add(t, TARGET, new TargetTag());
        }
    }

    private void restart(World world) {
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build()))
            if (world.exists(e)) world.destroyEntity(e);
        for (EntityId e : world.query(new QueryBuilder().allOf(TARGET).build()))
            if (world.exists(e)) world.destroyEntity(e);
        world.add(playerEntity, POS, new Position(0, -7));
        mode = GameMode.READY;
        wave = 0; score = 0; misses = 0;
        playSound(440f, 0.15f, 0.001f, 0.02f);
        System.out.println("  [Restart] Press SPACE to start!");
    }

    private void renderFrame(World world) {
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));
        QuerySpec pulseQ = new QueryBuilder().allOf(PULSE, POS).build();
        QuerySpec targetQ = new QueryBuilder().allOf(TARGET, POS, RAD).build();

        int pc = 0;
        for (EntityId e : world.query(pulseQ)) {
            if (pc >= pX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Lifetime lt = world.get(e, LIFE).orElse(new Lifetime(0, 1));
            pX[pc] = p.x(); pY[pc] = p.y(); pP[pc] = lt.progress(); pc++;
        }

        int tc = 0;
        for (EntityId e : world.query(targetQ)) {
            if (tc >= tX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Radius r = world.get(e, RAD).orElseThrow();
            tX[tc] = p.x(); tY[tc] = p.y(); tR[tc] = r.r(); tc++;
        }

        var ws = windowSub.window().framebufferSize();
        renderer.render(mode, playerPos.x(), playerPos.y(),
                pX, pY, pP, pc, tX, tY, tR, tc,
                wave, score, misses, MAX_MISSES,
                ws.width(), ws.height());
        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("%n[Arena] Final: score=%d waves=%d mode=%s%n", score, wave, mode);
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
