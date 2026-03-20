package org.dynamisengine.games.debug;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.dsp.device.AudioTelemetry;
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
import org.dynamisengine.input.core.InputTelemetry;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;
import org.dynamisengine.worldengine.api.telemetry.*;

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

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final TextRenderer textRenderer = new TextRenderer();

    private boolean overlayVisible = true;
    private int particlesSpawned = 0;

    public DebugOverlayGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
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
        System.out.println("=== Debug Overlay ===");
        System.out.println("Live engine telemetry HUD.");
        System.out.println("Space=spawn particles, Tab=toggle overlay, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;

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

        // Draw debug overlay
        if (overlayVisible) {
            textRenderer.beginFrame(w, h);
            float s = 2.0f; // scale
            float y = 10;
            float lineH = 18;

            textRenderer.drawText("=== DYNAMIS DEBUG OVERLAY ===", 10, y, s, 1f, 1f, 0.3f, w, h);
            y += lineH + 4;

            // Engine telemetry
            WorldTelemetrySnapshot snapshot = context.telemetry();
            if (snapshot != null) {
                EngineTelemetry eng = snapshot.engine();
                if (eng != null) {
                    textRenderer.drawText(String.format("Engine: %s  Tick: %d  Uptime: %.1fs",
                            eng.state(), eng.tick(), eng.uptimeSeconds()), 10, y, s, 0.8f, 0.9f, 0.8f, w, h);
                    y += lineH;
                    textRenderer.drawText(String.format("Frame: %.2fms (avg %.2f, max %.2f)  Budget: %.0f%%",
                            eng.lastTickDurationMs(), eng.avgTickDurationMs(), eng.maxTickDurationMs(),
                            eng.budgetPercent()), 10, y, s, 0.7f, 0.8f, 0.7f, w, h);
                    y += lineH;
                    textRenderer.drawText(String.format("Target: %dHz (%.2fms)",
                            eng.tickRate(), eng.targetTickMs()), 10, y, s, 0.6f, 0.7f, 0.6f, w, h);
                    y += lineH + 4;
                }

                // Subsystem health
                textRenderer.drawText("--- Subsystems ---", 10, y, s, 0.9f, 0.9f, 0.5f, w, h);
                y += lineH;
                for (var entry : snapshot.subsystems().entrySet()) {
                    SubsystemTelemetry sub = entry.getValue();
                    SubsystemHealth health = sub.health();
                    float r, g, b;
                    switch (health.state()) {
                        case HEALTHY -> { r = 0.3f; g = 0.9f; b = 0.3f; }
                        case DEGRADED -> { r = 0.9f; g = 0.7f; b = 0.2f; }
                        case FAULTED -> { r = 0.9f; g = 0.2f; b = 0.2f; }
                        default -> { r = 0.5f; g = 0.5f; b = 0.5f; }
                    }
                    String line = String.format("  %s: %s", health.name(), health.state());
                    if (health.lastError() != null) line += " — " + health.lastError();
                    textRenderer.drawText(line, 10, y, s, r, g, b, w, h);
                    y += lineH;

                    // Audio detail
                    if (sub.hasDetail()) {
                        Object detail = sub.detail();
                        if (detail instanceof AudioTelemetry audio) {
                            textRenderer.drawText(String.format("    %s | %s | DSP: %.0f%% | Ring: %.0f%% | U:%d O:%d",
                                    audio.backendName(), audio.deviceDescription(),
                                    audio.dspBudgetPercent(), audio.ringFillPercent(),
                                    audio.ringUnderruns(), audio.ringOverruns()),
                                    10, y, 1.8f, 0.6f, 0.7f, 0.8f, w, h);
                            y += lineH;
                        } else if (detail instanceof InputTelemetry input) {
                            textRenderer.drawText(String.format("    Devices: %d | Events: %d | Snapshots: %d",
                                    input.connectedDevices().size(),
                                    input.totalEventsProcessed(),
                                    input.totalSnapshots()),
                                    10, y, 1.8f, 0.6f, 0.7f, 0.8f, w, h);
                            y += lineH;
                        }
                    }
                }
                y += 4;

                // Summary
                textRenderer.drawText(String.format("Healthy: %d  Degraded: %d  Faulted: %d",
                        snapshot.healthyCount(), snapshot.degradedCount(), snapshot.faultedCount()),
                        10, y, s, 0.7f, 0.7f, 0.7f, w, h);
                y += lineH + 4;
            }

            // ECS stats
            int entityCount = world.entities().size();
            textRenderer.drawText(String.format("--- ECS ---  Entities: %d  Particles: %d (total spawned: %d)",
                    entityCount, particleCount, particlesSpawned), 10, y, s, 0.9f, 0.9f, 0.5f, w, h);
            y += lineH + 4;

            // Runtime
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long maxMB = rt.maxMemory() / (1024 * 1024);
            textRenderer.drawText(String.format("--- JVM ---  Heap: %dMB / %dMB  Processors: %d",
                    usedMB, maxMB, rt.availableProcessors()), 10, y, s, 0.9f, 0.9f, 0.5f, w, h);
            y += lineH + 4;

            // Controls
            textRenderer.drawText("Space=spawn 20 particles  Tab=toggle overlay  Esc=quit",
                    10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

            textRenderer.endFrame();
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
