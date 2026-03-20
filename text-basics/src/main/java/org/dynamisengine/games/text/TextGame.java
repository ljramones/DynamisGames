package org.dynamisengine.games.text;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.text.subsystem.*;
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

import static org.lwjgl.opengl.GL41C.*;

/**
 * Text rendering basics — first in-window text in the Dynamis proving ladder.
 *
 * Proves: OpenGL shader compilation, STBEasyFont text rendering, HUD overlay
 * on top of a simple interactive scene (cursor + spawned particles).
 *
 * The HUD shows: title, score, entity count, frame timing, controls.
 * This is the foundation for debug overlays and in-game UI.
 */
public final class TextGame implements WorldApplication {

    // ECS components
    record Position(float x, float y) {}
    record Velocity(float vx, float vy) {}
    record Lifetime(float age, float max) {
        boolean expired() { return age >= max; }
        Lifetime aged(float dt) { return new Lifetime(age + dt, max); }
        float progress() { return Math.min(age / max, 1f); }
    }
    record ParticleTag() {}
    record PlayerTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("txt.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("txt.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("txt.life", Lifetime.class);
    static final ComponentKey<ParticleTag> PARTICLE = ComponentKey.of("txt.part", ParticleTag.class);
    static final ComponentKey<PlayerTag> PLAYER = ComponentKey.of("txt.player", PlayerTag.class);

    // Input
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("text");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_SPACE=32, KEY_R=82, KEY_ESC=256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final TextRenderer textRenderer = new TextRenderer();

    private EntityId playerEntity;
    private int score = 0;
    private long totalSpawned = 0;
    private float fps = 0;
    private float fpsAccum = 0;
    private int fpsFrames = 0;

    // Render arrays
    private final float[] pX = new float[256], pY = new float[256], pP = new float[256];

    public TextGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
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
        textRenderer.initialize();
        World world = context.ecsWorld();
        playerEntity = world.createEntity();
        world.add(playerEntity, POS, new Position(0, 0));
        world.add(playerEntity, PLAYER, new PlayerTag());

        System.out.println("=== Text Basics ===");
        System.out.println("First in-window text rendering with OpenGL shaders.");
        System.out.println("WASD=move, Space=spawn, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // FPS tracking
        fpsAccum += dt;
        fpsFrames++;
        if (fpsAccum >= 0.5f) {
            fps = fpsFrames / fpsAccum;
            fpsAccum = 0;
            fpsFrames = 0;
        }

        // Reset
        if (frame.pressed(RESET)) {
            for (EntityId e : world.query(new QueryBuilder().allOf(PARTICLE).build()))
                world.destroyEntity(e);
            world.add(playerEntity, POS, new Position(0, 0));
            score = 0;
            totalSpawned = 0;
            playSound(440f, 0.15f, 0.001f, 0.02f);
        }

        // Move
        float mx = frame.axis(MOVE_X), my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            world.add(playerEntity, POS, new Position(
                    clamp(p.x() + mx * 8f * dt, -9.5f, 9.5f),
                    clamp(p.y() + my * 8f * dt, -9.5f, 9.5f)));
        }

        // Spawn
        if (frame.pressed(SPAWN)) {
            Position p = world.get(playerEntity, POS).orElse(new Position(0, 0));
            EntityId e = world.createEntity();
            world.add(e, POS, new Position(p.x(), p.y()));
            world.add(e, VEL, new Velocity(
                    (float)(Math.random() - 0.5) * 4f,
                    (float)(Math.random() - 0.5) * 4f));
            world.add(e, LIFE, new Lifetime(0, 3f));
            world.add(e, PARTICLE, new ParticleTag());
            totalSpawned++;
            score += 10;
            playSound(660f, 0.15f, 0.002f, 0.04f);
        }

        // Movement system
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * dt, p.y() + v.vy() * dt));
        }

        // Lifetime system
        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            Lifetime updated = lt.aged(dt);
            if (updated.expired()) expired.add(e);
            else world.add(e, LIFE, updated);
        }
        for (EntityId e : expired) world.destroyEntity(e);

        // Count entities
        int particleCount = 0;
        for (EntityId e : world.query(new QueryBuilder().allOf(PARTICLE, POS, LIFE).build())) {
            if (particleCount < pX.length) {
                Position p = world.get(e, POS).orElseThrow();
                Lifetime lt = world.get(e, LIFE).orElseThrow();
                pX[particleCount] = p.x();
                pY[particleCount] = p.y();
                pP[particleCount] = lt.progress();
                particleCount++;
            }
        }

        // === RENDER ===
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        Position playerPos = world.get(playerEntity, POS).orElse(new Position(0, 0));

        // Clear background
        glViewport(0, 0, w, h);
        float bg = Math.min(0.05f + particleCount * 0.01f, 0.2f);
        glClearColor(bg * 0.3f, bg * 0.3f, bg, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Draw scene with scissor rects (same as previous modules)
        glEnable(GL_SCISSOR_TEST);

        // Particles
        for (int i = 0; i < particleCount; i++) {
            float life = 1.0f - pP[i];
            float size = 0.04f * life + 0.01f;
            drawSceneRect(pX[i], pY[i], size, 0.2f * life, 0.8f * life, life, w, h);
        }

        // Cursor
        drawSceneRect(playerPos.x(), playerPos.y(), 0.04f, 1f, 1f, 0.3f, w, h);
        drawSceneRect(playerPos.x(), playerPos.y(), 0.015f, 1f, 0.8f, 0f, w, h);

        glDisable(GL_SCISSOR_TEST);

        // Draw HUD text overlay
        textRenderer.beginFrame(w, h);

        float scale = 2.5f;
        textRenderer.drawText("DYNAMIS TEXT BASICS", 20, 20, scale, 1.0f, 1.0f, 0.3f, w, h);
        textRenderer.drawText(String.format("Score: %d", score), 20, 50, scale, 0.9f, 0.9f, 0.9f, w, h);
        textRenderer.drawText(String.format("Particles: %d  Total: %d", particleCount, totalSpawned),
                20, 75, scale, 0.7f, 0.7f, 0.7f, w, h);
        textRenderer.drawText(String.format("FPS: %.0f  Tick: %d", fps, context.tick()),
                20, 100, scale, 0.5f, 0.8f, 0.5f, w, h);
        textRenderer.drawText(String.format("Entities: %d", world.entities().size()),
                20, 125, scale, 0.5f, 0.7f, 0.8f, w, h);
        textRenderer.drawText(String.format("Cursor: (%.1f, %.1f)", playerPos.x(), playerPos.y()),
                20, 150, scale, 0.6f, 0.6f, 0.6f, w, h);

        // Controls at bottom
        float bottomY = h - 30;
        textRenderer.drawText("WASD=move  Space=spawn  R=reset  Esc=quit", 20, bottomY, 2.0f, 0.4f, 0.4f, 0.5f, w, h);

        textRenderer.endFrame();

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
        System.out.printf("%n[Text] Score=%d Spawned=%d%n", score, totalSpawned);
    }

    private void drawSceneRect(float logX, float logY, float size,
                                float r, float g, float b, int w, int h) {
        int cx = (int) ((logX / 10f + 1f) * 0.5f * w);
        int cy = (int) ((logY / 10f + 1f) * 0.5f * h);
        int sz = Math.max(1, (int) (size * Math.min(w, h)));
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
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
