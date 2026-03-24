package org.dynamisengine.games.debug;

import org.dynamisengine.games.proving.OpenGlDebugOverlayRenderer;
import org.dynamisengine.games.proving.OpenGlTextRenderer;
import org.dynamisengine.games.proving.ProvingInputSubsystem;
import org.dynamisengine.games.proving.ProvingWindowSubsystem;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.debug.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Debug overlay — live engine telemetry HUD.
 *
 * Proves: GameContext.telemetry(), WorldTelemetrySnapshot, EngineTelemetry,
 * AudioTelemetry, InputTelemetry, SubsystemHealth — all rendered as a
 * real-time text overlay on top of a simple interactive scene.
 *
 * Toggle overlay with Tab. Spawn particles to generate load.
 */
public final class DebugOverlayGame implements WorldApplication {

    // Simple scene components
    record Position(float x, float y) {}
    record Velocity(float vx, float vy) {}
    record Lifetime(float age, float max) {
        boolean expired() { return age >= max; }
        Lifetime aged(float dt) { return new Lifetime(age + dt, max); }
    }
    record ParticleTag() {}

    static final ComponentKey<Position> POS = ComponentKey.of("dbg.pos", Position.class);
    static final ComponentKey<Velocity> VEL = ComponentKey.of("dbg.vel", Velocity.class);
    static final ComponentKey<Lifetime> LIFE = ComponentKey.of("dbg.life", Lifetime.class);
    static final ComponentKey<ParticleTag> PART = ComponentKey.of("dbg.part", ParticleTag.class);

    // Input
    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("debug");
    private static final int KEY_SPACE = 32, KEY_TAB = 258, KEY_ESC = 256;

    private final ProvingWindowSubsystem windowSub;
    private final ProvingInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private final DebugSpineOverlay spineOverlay = new DebugSpineOverlay();
    private OpenGlDebugOverlayRenderer overlayRenderer;

    private boolean overlayVisible = true;
    private int particlesSpawned = 0;

    public DebugOverlayGame(ProvingWindowSubsystem w, ProvingInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(SPAWN, List.of(new KeyBinding(KEY_SPACE, 0)),
                        TOGGLE, List.of(new KeyBinding(KEY_TAB, 0)),
                        QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
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
        System.out.println("=== Debug Overlay ===");
        System.out.println("Live engine telemetry HUD.");
        System.out.println("Space=spawn particles  Tab=toggle overlay  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(TOGGLE)) {
            overlayVisible = !overlayVisible;
            System.out.println("Overlay: " + (overlayVisible ? "ON" : "OFF"));
        }

        // Spawn burst of particles for load generation
        if (frame.pressed(SPAWN)) {
            for (int i = 0; i < 20; i++) {
                EntityId e = world.createEntity();
                world.add(e, POS, new Position(
                        (float)(Math.random() * 18 - 9),
                        (float)(Math.random() * 18 - 9)));
                world.add(e, VEL, new Velocity(
                        (float)(Math.random() - 0.5) * 6f,
                        (float)(Math.random() - 0.5) * 6f));
                world.add(e, LIFE, new Lifetime(0, 2f + (float)(Math.random() * 3)));
                world.add(e, PART, new ParticleTag());
                particlesSpawned++;
            }
            playSound(440f, 0.1f, 0.001f, 0.02f);
        }

        // Systems
        for (EntityId e : world.query(new QueryBuilder().allOf(POS, VEL).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Velocity v = world.get(e, VEL).orElseThrow();
            world.add(e, POS, new Position(p.x() + v.vx() * dt, p.y() + v.vy() * dt));
        }

        List<EntityId> expired = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(LIFE).build())) {
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            Lifetime updated = lt.aged(dt);
            if (updated.expired()) expired.add(e);
            else world.add(e, LIFE, updated);
        }
        for (EntityId e : expired) world.destroyEntity(e);

        // Count live particles
        int particleCount = 0;
        for (EntityId ignored : world.query(new QueryBuilder().allOf(PART).build()))
            particleCount++;

        // === RENDER ===
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();

        glViewport(0, 0, w, h);
        float bg = Math.min(0.04f + particleCount * 0.005f, 0.15f);
        glClearColor(bg * 0.3f, bg * 0.4f, bg, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Draw particles as colored dots
        glEnable(GL_SCISSOR_TEST);
        for (EntityId e : world.query(new QueryBuilder().allOf(PART, POS, LIFE).build())) {
            Position p = world.get(e, POS).orElseThrow();
            Lifetime lt = world.get(e, LIFE).orElseThrow();
            float life = 1f - Math.min(lt.age() / lt.max(), 1f);
            int cx = (int) ((p.x() / 10f + 1f) * 0.5f * w);
            int cy = (int) ((p.y() / 10f + 1f) * 0.5f * h);
            int sz = Math.max(2, (int) (life * 6));
            glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
            glClearColor(0.3f * life, 0.7f * life, life, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        glDisable(GL_SCISSOR_TEST);

        // === Debug Overlay (sole rendering path) ===
        if (overlayVisible) {
            WorldTelemetrySnapshot snapshot = context.telemetry();
            long tick = snapshot != null && snapshot.engine() != null ? snapshot.engine().tick() : 0;

            Map<String, Double> ecsMetrics = Map.of(
                "entityCount", (double) world.entities().size(),
                "particleCount", (double) particleCount,
                "totalSpawned", (double) particlesSpawned
            );

            List<DebugOverlayPanel> panels = spineOverlay.update(snapshot, tick, ecsMetrics);
            overlayRenderer.renderPanels(panels, w, h);
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
        System.out.printf("%n[Debug] Particles spawned: %d%n", particlesSpawned);
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }
}
