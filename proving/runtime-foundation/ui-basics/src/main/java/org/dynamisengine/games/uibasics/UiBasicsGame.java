package org.dynamisengine.games.uibasics;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.query.QuerySpec;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.uibasics.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.*;

/**
 * UI flow proving module: title → play → pause → win/lose → restart.
 *
 * Gameplay is simplified arena logic. The focus is on UI state transitions
 * and how presentation state separates from gameplay state.
 */
public final class UiBasicsGame implements WorldApplication {

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

    static final ComponentKey<Position> POS = ComponentKey.of("ui.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("ui.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("ui.life", Lifetime.class);
    static final ComponentKey<Radius> RAD = ComponentKey.of("ui.rad", Radius.class);
    static final ComponentKey<PulseTag> PULSE = ComponentKey.of("ui.pulse", PulseTag.class);
    static final ComponentKey<TargetTag> TARGET = ComponentKey.of("ui.target", TargetTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("ui.player", PlayerTag.class);

    static final ActionId FIRE = new ActionId("fire");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("uibasics");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_SPACE=32,KEY_ENTER=257,KEY_P=80,KEY_R=82,KEY_ESC=256;
    private static final float SR = AcousticConstants.SAMPLE_RATE;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final UiRenderer renderer = new UiRenderer();

    private EntityId playerEntity;
    private UiMode uiMode = UiMode.TITLE;
    private int score = 0, wave = 0, misses = 0;
    private static final int MAX_MISSES = 5;
    private static final int TOTAL_WAVES = 3;
    private float waveClearTimer = 0;

    private final float[] pX=new float[256],pY=new float[256],pP=new float[256];
    private final float[] tX=new float[64],tY=new float[64],tR=new float[64];

    public UiBasicsGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(FIRE, List.of(new KeyBinding(KEY_SPACE, 0), new KeyBinding(KEY_ENTER, 0)),
                        PAUSE, List.of(new KeyBinding(KEY_P, 0)),
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

        System.out.println("=== UI Basics ===");
        System.out.println("Title → Play → Pause → Win/Lose → Restart");
        System.out.println("Space/Enter=start/fire, P=pause, R=restart, Esc=quit");
        System.out.println();
        System.out.println("  TITLE SCREEN — Press Space or Enter to start");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();
        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        if (frame.pressed(RESET)) {
            resetGame(world);
            return;
        }

        // Always allow cursor movement
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    clamp(p.x() + mx * 8f * dt, -9.5f, 9.5f),
                    clamp(p.y() + my * 8f * dt, -9.5f, 9.5f)));
        }

        var ws = windowSub.window().framebufferSize();

        switch (uiMode) {
            case TITLE -> {
                if (frame.pressed(FIRE)) {
                    uiMode = UiMode.PLAYING;
                    wave = 1; score = 0; misses = 0;
                    spawnWave(world, wave);
                    playSound(440f, 0.2f, 0.005f, 0.1f);
                    System.out.println("  PLAYING — Wave 1");
                }
                renderer.renderTitle(ws.width(), ws.height());
            }
            case PLAYING -> {
                if (frame.pressed(PAUSE)) {
                    uiMode = UiMode.PAUSED;
                    playSound(330f, 0.1f, 0.002f, 0.05f);
                    System.out.println("  PAUSED");
                } else {
                    updatePlaying(frame, world, dt);
                }
                if (uiMode == UiMode.PLAYING) {
                    renderPlaying(world, ws.width(), ws.height());
                } else if (uiMode == UiMode.WON || uiMode == UiMode.LOST) {
                    // State changed during update — render end screen
                    if (uiMode == UiMode.WON) renderer.renderWon(score, ws.width(), ws.height());
                    else renderer.renderLost(score, ws.width(), ws.height());
                }
            }
            case PAUSED -> {
                if (frame.pressed(PAUSE) || frame.pressed(FIRE)) {
                    uiMode = UiMode.PLAYING;
                    playSound(440f, 0.1f, 0.002f, 0.05f);
                    System.out.println("  RESUMED");
                }
                renderer.renderPaused(ws.width(), ws.height());
            }
            case WON -> renderer.renderWon(score, ws.width(), ws.height());
            case LOST -> renderer.renderLost(score, ws.width(), ws.height());
        }

        windowSub.window().swapBuffers();
    }

    private void updatePlaying(InputFrame frame, World world, float dt) {
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

        // Movement
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x()+v.vx()*dt, p.y()+v.vy()*dt));
        }

        // Lifetime
        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            if (lt.aged(dt).expired()) expired.add(e);
            else world.add(e, LIFE, lt.aged(dt));
        }
        for (EntityId e : expired) {
            if (world.has(e, TARGET)) {
                misses++;
                playSound(220f, 0.15f, 0.01f, 0.15f);
                if (misses >= MAX_MISSES) {
                    uiMode = UiMode.LOST;
                    System.out.printf("  LOST — Score: %d%n", score);
                    playSound(110f, 0.3f, 0.02f, 0.5f);
                }
            }
            if (world.exists(e)) world.destroyEntity(e);
        }

        // Collision
        QuerySpec pq = new QueryBuilder().allOf(PULSE,POS,RAD).build();
        QuerySpec tq = new QueryBuilder().allOf(TARGET,POS,RAD).build();
        List<EntityId> hitT = new ArrayList<>(), hitP = new ArrayList<>();
        for (EntityId pulse : world.query(pq)) {
            Position pp = world.get(pulse, POS).orElseThrow();
            Radius pr = world.get(pulse, RAD).orElseThrow();
            for (EntityId target : world.query(tq)) {
                if (hitT.contains(target)) continue;
                Position tp = world.get(target, POS).orElseThrow();
                Radius tr = world.get(target, RAD).orElseThrow();
                float dx=pp.x()-tp.x(), dy=pp.y()-tp.y();
                if (dx*dx+dy*dy < (pr.r()+tr.r())*(pr.r()+tr.r())) {
                    hitT.add(target); hitP.add(pulse); score++;
                    playSound(1320f, 0.25f, 0.001f, 0.06f);
                }
            }
        }
        for (EntityId e : hitP) if (world.exists(e)) world.destroyEntity(e);
        for (EntityId e : hitT) if (world.exists(e)) world.destroyEntity(e);

        // Wave check
        int remaining = 0;
        for (EntityId ignored : world.query(tq)) remaining++;
        if (remaining == 0 && uiMode == UiMode.PLAYING) {
            wave++;
            if (wave > TOTAL_WAVES) {
                uiMode = UiMode.WON;
                System.out.printf("  WON — Score: %d%n", score);
                playSound(880f, 0.3f, 0.01f, 0.3f);
            } else {
                spawnWave(world, wave);
                playSound(660f, 0.2f, 0.005f, 0.15f);
                System.out.printf("  Wave %d%n", wave);
            }
        }
    }

    private void renderPlaying(World world, int w, int h) {
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));
        int pc = 0, tc = 0;
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE,POS).build())) {
            if (pc >= pX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Lifetime lt = world.get(e, LIFE).orElse(new Lifetime(0, 1));
            pX[pc]=p.x(); pY[pc]=p.y(); pP[pc]=lt.progress(); pc++;
        }
        for (EntityId e : world.query(new QueryBuilder().allOf(TARGET,POS,RAD).build())) {
            if (tc >= tX.length) break;
            Position p = world.get(e, POS).orElseThrow();
            Radius r = world.get(e, RAD).orElseThrow();
            tX[tc]=p.x(); tY[tc]=p.y(); tR[tc]=r.r(); tc++;
        }
        renderer.renderPlaying(playerPos.x(), playerPos.y(),
                pX, pY, pP, pc, tX, tY, tR, tc,
                score, wave, misses, MAX_MISSES, 0, w, h);
    }

    private void spawnWave(World world, int w) {
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build()))
            if (world.exists(e)) world.destroyEntity(e);
        int count = 2 + w * 2;
        float life = 6f - w * 0.5f;
        for (int i = 0; i < count; i++) {
            EntityId t = world.createEntity();
            float x = -4f + (8f * i / Math.max(1, count - 1));
            float y = 4f + (float)(Math.random() * 4);
            world.add(t, POS, new Position(x, y));
            world.add(t, RAD, new Radius(0.9f));
            world.add(t, LIFE, new Lifetime(0, life));
            world.add(t, TARGET, new TargetTag());
        }
    }

    private void resetGame(World world) {
        for (EntityId e : world.query(new QueryBuilder().allOf(PULSE).build()))
            if (world.exists(e)) world.destroyEntity(e);
        for (EntityId e : world.query(new QueryBuilder().allOf(TARGET).build()))
            if (world.exists(e)) world.destroyEntity(e);
        world.add(playerEntity, POS, new Position(0, -7));
        uiMode = UiMode.TITLE; wave = 0; score = 0; misses = 0;
        playSound(440f, 0.15f, 0.001f, 0.02f);
        System.out.println("  TITLE SCREEN — Press Space or Enter to start");
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("[UiBasics] mode=%s score=%d%n", uiMode, score);
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, SR);
        var env = new Envelope(atk, dec, 0f, 0.02f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
