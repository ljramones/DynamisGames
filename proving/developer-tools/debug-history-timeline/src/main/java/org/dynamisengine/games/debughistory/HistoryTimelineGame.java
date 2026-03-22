package org.dynamisengine.games.debughistory;

import org.dynamisengine.debug.api.DebugCategory;
import org.dynamisengine.debug.api.DebugSeverity;
import org.dynamisengine.debug.api.DebugSnapshot;
import org.dynamisengine.debug.api.WatchdogRule;
import org.dynamisengine.debug.api.event.DebugEvent;
import org.dynamisengine.debug.core.DebugSession;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.ui.debug.builder.DebugOverlayBuilder;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshotMapper;
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Debug History Timeline — proves time-aware diagnostics.
 *
 * <p>Simulates four phases of engine behavior to demonstrate how the debug
 * spine records history, generates timeline events, and drives trend
 * sparklines through real data accumulation:
 *
 * <ol>
 *   <li><b>NORMAL</b> — stable frame time, no alerts</li>
 *   <li><b>DEGRADING</b> — frame time drifts upward, warnings appear</li>
 *   <li><b>SPIKE</b> — sudden fault, error events emitted</li>
 *   <li><b>RECOVERY</b> — frame time returns to normal, alerts decay</li>
 * </ol>
 *
 * <p>Controls:
 * <pre>
 *   Space  — trigger immediate spike/fault
 *   R      — reset history and restart scenario
 *   P      — pause/resume simulation
 *   Tab    — toggle overlay
 *   Esc    — quit
 * </pre>
 */
public final class HistoryTimelineGame implements WorldApplication {

    enum Phase { NORMAL, DEGRADING, SPIKE, RECOVERY }

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId SPIKE_ACTION = new ActionId("spike");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("history");
    private static final int KEY_TAB = 258, KEY_SPACE = 32, KEY_R = 82, KEY_P = 80, KEY_ESC = 256;

    // Phase timing (seconds)
    private static final float NORMAL_DURATION = 5f;
    private static final float DEGRADING_DURATION = 5f;
    private static final float SPIKE_DURATION = 2f;
    private static final float RECOVERY_DURATION = 4f;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final TextRenderer textRenderer = new TextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;

    // Debug spine
    private DebugSession session;
    private DebugViewSnapshotMapper mapper;
    private DebugOverlayBuilder builder;

    // Simulation state
    private Phase phase = Phase.NORMAL;
    private float phaseTime = 0f;
    private float totalTime = 0f;
    private long tick = 0;
    private boolean overlayVisible = true;
    private boolean paused = false;

    // Simulated metrics
    private float simFrameTimeMs = 3.0f;
    private float simEntityCount = 100f;
    private float simPhysicsStepMs = 1.0f;
    private float simAudioVoices = 8f;

    public HistoryTimelineGame(WindowSubsystem w, WindowInputSubsystem i) {
        this.windowSub = w;
        this.inputSub = i;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0)),
                        SPIKE_ACTION, List.of(new KeyBinding(KEY_SPACE, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        PAUSE, List.of(new KeyBinding(KEY_P, 0)),
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
        initSession();

        System.out.println("=== Debug History Timeline ===");
        System.out.println("Proving: history accumulation, timeline events, trend evolution");
        System.out.println("Phases: NORMAL -> DEGRADING -> SPIKE -> RECOVERY (auto-cycling)");
        System.out.println("Space=spike  R=reset  P=pause  Tab=overlay  Esc=quit");
    }

    private void initSession() {
        session = new DebugSession();
        mapper = new DebugViewSnapshotMapper(session);
        var options = new DebugOverlayOptions(true, true, true, 60, 16, 8, false);
        builder = new DebugOverlayBuilder(options);

        // Watchdog rules with meaningful cooldowns
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameBudgetHigh", "engine", "frameTimeMs",
            8.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Frame time > 8ms", 60));
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameCritical", "engine", "frameTimeMs",
            16.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.ERROR, "Frame time > 16ms (missed frame)", 30));
        session.watchdog().addRule(new WatchdogRule(
            "physics.stepHigh", "physics", "stepTimeMs",
            4.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Physics step > 4ms", 90));
        session.watchdog().addRule(new WatchdogRule(
            "ecs.churnHigh", "ecs", "entityCount",
            500.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Entity count > 500", 120));

        phase = Phase.NORMAL;
        phaseTime = 0f;
        totalTime = 0f;
        tick = 0;
        simFrameTimeMs = 3.0f;
        simEntityCount = 100f;
        simPhysicsStepMs = 1.0f;
        simAudioVoices = 8f;
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(PAUSE)) {
                paused = !paused;
                System.out.println("Simulation: " + (paused ? "PAUSED" : "RUNNING"));
            }
            if (frame.pressed(RESET)) {
                initSession();
                System.out.println("History reset. Restarting scenario.");
            }
            if (frame.pressed(SPIKE_ACTION)) {
                triggerSpike();
            }
        }

        // Advance simulation
        if (!paused) {
            tick++;
            totalTime += dt;
            phaseTime += dt;
            advancePhase();
            updateSimulatedMetrics(dt);
        }

        // Feed debug session
        Map<String, DebugSnapshot> frameSnapshots = buildFrameSnapshots();
        session.history().record(tick, frameSnapshots);
        var alerts = session.watchdog().evaluate(tick, frameSnapshots);
        for (var alert : alerts) session.submit(alert);

        // Render
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);

        // Background color reflects phase
        float[] bgColor = phaseBackground();
        glClearColor(bgColor[0], bgColor[1], bgColor[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Overlay
        if (overlayVisible) {
            var viewSnapshot = mapper.mapFromFrame(tick, frameSnapshots);
            List<DebugOverlayPanel> panels = builder.buildAll(viewSnapshot);
            overlayRenderer.renderPanels(panels, w, h);

            // Phase indicator at bottom
            textRenderer.beginFrame(w, h);
            String status = String.format("Phase: %s  Time: %.1fs  Tick: %d  %s",
                phase, totalTime, tick, paused ? "[PAUSED]" : "");
            textRenderer.drawText(status, 10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText("Space=spike  R=reset  P=pause  Tab=overlay  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
        System.out.printf("[HistoryTimeline] Final: %d ticks, %.1fs, phase=%s%n",
            tick, totalTime, phase);
    }

    // --- Phase management ---

    private void advancePhase() {
        float duration = switch (phase) {
            case NORMAL -> NORMAL_DURATION;
            case DEGRADING -> DEGRADING_DURATION;
            case SPIKE -> SPIKE_DURATION;
            case RECOVERY -> RECOVERY_DURATION;
        };

        if (phaseTime >= duration) {
            phaseTime = 0f;
            Phase oldPhase = phase;
            phase = switch (phase) {
                case NORMAL -> Phase.DEGRADING;
                case DEGRADING -> Phase.SPIKE;
                case SPIKE -> Phase.RECOVERY;
                case RECOVERY -> Phase.NORMAL;
            };
            System.out.printf("[%.1fs] Phase: %s -> %s%n", totalTime, oldPhase, phase);

            // Emit phase transition event
            session.submit(new DebugEvent(tick, System.currentTimeMillis(),
                "scenario", DebugCategory.ENGINE, DebugSeverity.INFO,
                "phase.transition", oldPhase + " -> " + phase));

            if (phase == Phase.SPIKE) {
                session.submit(new DebugEvent(tick, System.currentTimeMillis(),
                    "scenario", DebugCategory.ENGINE, DebugSeverity.ERROR,
                    "scenario.faultInjected", "Simulated fault spike begins"));
            }
        }
    }

    private void triggerSpike() {
        phase = Phase.SPIKE;
        phaseTime = 0f;
        simFrameTimeMs = 25f + (float)(Math.random() * 15);
        simPhysicsStepMs = 8f + (float)(Math.random() * 4);
        simEntityCount = 600f + (float)(Math.random() * 200);
        System.out.printf("[%.1fs] Manual spike triggered! frameTime=%.1fms%n",
            totalTime, simFrameTimeMs);

        session.submit(new DebugEvent(tick, System.currentTimeMillis(),
            "user", DebugCategory.ENGINE, DebugSeverity.CRITICAL,
            "user.manualSpike", String.format("Manual spike: %.1fms", simFrameTimeMs)));
    }

    // --- Simulated metric evolution ---

    private void updateSimulatedMetrics(float dt) {
        float noise = (float)(Math.random() - 0.5) * 0.4f;

        switch (phase) {
            case NORMAL -> {
                simFrameTimeMs = lerp(simFrameTimeMs, 3.0f + noise, dt * 2f);
                simEntityCount = lerp(simEntityCount, 100f + noise * 20, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1.0f + noise * 0.2f, dt * 2f);
                simAudioVoices = lerp(simAudioVoices, 8f + noise * 2, dt * 2f);
            }
            case DEGRADING -> {
                float progress = phaseTime / DEGRADING_DURATION;
                float target = 3f + progress * 15f; // 3ms -> 18ms
                simFrameTimeMs = lerp(simFrameTimeMs, target + noise * 2, dt * 3f);
                simEntityCount = lerp(simEntityCount, 100f + progress * 400f, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + progress * 5f, dt * 2f);
                simAudioVoices = lerp(simAudioVoices, 8f + progress * 20f, dt * 2f);
            }
            case SPIKE -> {
                simFrameTimeMs = Math.max(simFrameTimeMs + noise * 5, 15f);
                simEntityCount = Math.max(simEntityCount + noise * 30, 400f);
                simPhysicsStepMs = Math.max(simPhysicsStepMs + noise * 2, 5f);
                simAudioVoices = Math.max(simAudioVoices + noise * 3, 20f);
            }
            case RECOVERY -> {
                float progress = phaseTime / RECOVERY_DURATION;
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + (1f - progress) * 5f + noise, dt * 4f);
                simEntityCount = lerp(simEntityCount, 100f + (1f - progress) * 200f, dt * 3f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + (1f - progress) * 2f + noise * 0.3f, dt * 3f);
                simAudioVoices = lerp(simAudioVoices, 8f + (1f - progress) * 5f, dt * 3f);
            }
        }

        // Clamp
        simFrameTimeMs = Math.max(1f, simFrameTimeMs);
        simEntityCount = Math.max(10f, simEntityCount);
        simPhysicsStepMs = Math.max(0.2f, simPhysicsStepMs);
        simAudioVoices = Math.max(1f, simAudioVoices);
    }

    // --- DebugSnapshot generation ---

    private Map<String, DebugSnapshot> buildFrameSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, DebugSnapshot> snapshots = new HashMap<>();

        snapshots.put("engine", new DebugSnapshot(tick, now, "engine", DebugCategory.ENGINE,
            Map.of(
                "frameTimeMs", (double) simFrameTimeMs,
                "budgetPercent", (double) (simFrameTimeMs / 16.667 * 100),
                "tickRate", 60.0,
                "uptimeSeconds", (double) totalTime
            ),
            Map.of("healthy", simFrameTimeMs < 16f),
            phase.name()
        ));

        snapshots.put("physics", new DebugSnapshot(tick, now, "physics", DebugCategory.PHYSICS,
            Map.of(
                "stepTimeMs", (double) simPhysicsStepMs,
                "bodies", (double)(int)(simEntityCount * 0.3f),
                "contacts", (double)(int)(simEntityCount * 0.1f)
            ),
            Map.of("hasContacts", simEntityCount > 50),
            ""
        ));

        snapshots.put("ecs", new DebugSnapshot(tick, now, "ecs", DebugCategory.ECS,
            Map.of(
                "entityCount", (double) simEntityCount,
                "createdThisFrame", (double)(int)(Math.random() * 3),
                "destroyedThisFrame", (double)(int)(Math.random() * 2)
            ),
            Map.of(),
            ""
        ));

        snapshots.put("audio", new DebugSnapshot(tick, now, "audio", DebugCategory.AUDIO,
            Map.of(
                "voices", (double) simAudioVoices,
                "dspBudget", (double)(simAudioVoices / 32.0 * 100)
            ),
            Map.of("underrunActive", simAudioVoices > 24),
            ""
        ));

        return snapshots;
    }

    private float[] phaseBackground() {
        return switch (phase) {
            case NORMAL -> new float[]{0.04f, 0.05f, 0.08f};
            case DEGRADING -> new float[]{0.06f, 0.05f, 0.04f};
            case SPIKE -> new float[]{0.12f, 0.04f, 0.04f};
            case RECOVERY -> new float[]{0.04f, 0.06f, 0.05f};
        };
    }

    private static float lerp(float current, float target, float rate) {
        return current + (target - current) * Math.min(rate, 1f);
    }
}
