package org.dynamisengine.games.debugcorrelation;

import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;

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
 * Debug Correlation Basics — proves cross-subsystem diagnostic reasoning.
 *
 * <p>Three scenarios with distinct causal patterns:
 * <ol>
 *   <li><b>ECS Overload</b> — entity flood drives physics, then engine budget</li>
 *   <li><b>Physics Spike</b> — physics complexity jumps independently of entity count</li>
 *   <li><b>Audio Pressure</b> — audio load rises without affecting frame budget</li>
 * </ol>
 *
 * <p>Each scenario produces a different multi-panel signature. The overlay
 * should let a developer distinguish which subsystem is the root cause by
 * reading the pattern of trends and alert timing.
 *
 * <p>Controls:
 * <pre>
 *   1     — ECS overload scenario
 *   2     — Physics spike scenario
 *   3     — Audio pressure scenario
 *   R     — Reset to baseline
 *   P     — Pause/resume
 *   Tab   — Toggle overlay
 *   Esc   — Quit
 * </pre>
 */
public final class CorrelationGame implements WorldApplication {

    enum Scenario { BASELINE, ECS_OVERLOAD, PHYSICS_SPIKE, AUDIO_PRESSURE }

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId SCENARIO_1 = new ActionId("ecs");
    static final ActionId SCENARIO_2 = new ActionId("physics");
    static final ActionId SCENARIO_3 = new ActionId("audio");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("correlation");
    private static final int KEY_TAB = 258, KEY_1 = 49, KEY_2 = 50, KEY_3 = 51;
    private static final int KEY_R = 82, KEY_P = 80, KEY_ESC = 256;

    private final GlfwWindowSubsystem windowSub;
    private final InputWorldSubsystem inputSub;
    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;

    // Debug spine
    private DebugSession session;
    private DebugViewSnapshotMapper mapper;
    private DebugOverlayBuilder builder;

    // State
    private Scenario scenario = Scenario.BASELINE;
    private float scenarioTime = 0f;
    private float totalTime = 0f;
    private long tick = 0;
    private boolean overlayVisible = true;
    private boolean paused = false;

    // Simulated metrics — baseline values
    private float simFrameTimeMs = 3.0f;
    private float simEntityCount = 100f;
    private float simPhysicsStepMs = 1.0f;
    private float simPhysicsContacts = 10f;
    private float simAudioVoices = 8f;
    private float simAudioDspBudget = 25f;

    public CorrelationGame(GlfwWindowSubsystem w, InputWorldSubsystem i) {
        this.windowSub = w;
        this.inputSub = i;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0)),
                        SCENARIO_1, List.of(new KeyBinding(KEY_1, 0)),
                        SCENARIO_2, List.of(new KeyBinding(KEY_2, 0)),
                        SCENARIO_3, List.of(new KeyBinding(KEY_3, 0)),
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

        System.out.println("=== Debug Correlation Basics ===");
        System.out.println("Proving: cross-subsystem correlation patterns");
        System.out.println("1=ECS overload  2=Physics spike  3=Audio pressure  R=reset  P=pause  Esc=quit");
    }

    private void initSession() {
        session = new DebugSession();
        mapper = new DebugViewSnapshotMapper(session);
        builder = new DebugOverlayBuilder(new DebugOverlayOptions(true, true, true, 60, 16, 8, false));

        // Watchdog rules across multiple subsystems
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameBudgetHigh", "engine", "frameTimeMs",
            8.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Frame time > 8ms", 60));
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameCritical", "engine", "frameTimeMs",
            16.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.ERROR, "Frame time > 16ms", 30));
        session.watchdog().addRule(new WatchdogRule(
            "physics.stepHigh", "physics", "stepTimeMs",
            4.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Physics step > 4ms", 60));
        session.watchdog().addRule(new WatchdogRule(
            "ecs.entityFlood", "ecs", "entityCount",
            400.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Entity count > 400", 90));
        session.watchdog().addRule(new WatchdogRule(
            "audio.dspHigh", "audio", "dspBudget",
            70.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Audio DSP budget > 70%", 60));
        session.watchdog().addRule(new WatchdogRule(
            "audio.voiceOverload", "audio", "voices",
            24.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.ERROR, "Audio voices > 24", 30));

        resetMetrics();
    }

    private void resetMetrics() {
        scenario = Scenario.BASELINE;
        scenarioTime = 0f;
        totalTime = 0f;
        tick = 0;
        simFrameTimeMs = 3.0f;
        simEntityCount = 100f;
        simPhysicsStepMs = 1.0f;
        simPhysicsContacts = 10f;
        simAudioVoices = 8f;
        simAudioDspBudget = 25f;
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(PAUSE)) { paused = !paused; }
            if (frame.pressed(RESET)) { initSession(); System.out.println("Reset to baseline."); }
            if (frame.pressed(SCENARIO_1)) startScenario(Scenario.ECS_OVERLOAD);
            if (frame.pressed(SCENARIO_2)) startScenario(Scenario.PHYSICS_SPIKE);
            if (frame.pressed(SCENARIO_3)) startScenario(Scenario.AUDIO_PRESSURE);
        }

        if (!paused) {
            tick++;
            totalTime += dt;
            scenarioTime += dt;
            updateMetrics(dt);
        }

        // Feed debug session
        Map<String, DebugSnapshot> frameSnapshots = buildSnapshots();
        session.history().record(tick, frameSnapshots);
        var alerts = session.watchdog().evaluate(tick, frameSnapshots);
        for (var a : alerts) session.submit(a);

        // Render
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);
        glClearColor(0.04f, 0.05f, 0.08f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        if (overlayVisible) {
            var viewSnapshot = mapper.mapFromFrame(tick, frameSnapshots);
            List<DebugOverlayPanel> panels = builder.buildAll(viewSnapshot);
            overlayRenderer.renderPanels(panels, w, h);

            // Status bar
            textRenderer.beginFrame(w, h);
            String status = String.format("Scenario: %s  Time: %.1fs  Tick: %d  %s",
                scenario, totalTime, tick, paused ? "[PAUSED]" : "");
            textRenderer.drawText(status, 10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText("1=ECS overload  2=Physics spike  3=Audio pressure  R=reset  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
    }

    // --- Scenario management ---

    private void startScenario(Scenario s) {
        // Return to baseline first, then inject
        simFrameTimeMs = 3.0f;
        simEntityCount = 100f;
        simPhysicsStepMs = 1.0f;
        simPhysicsContacts = 10f;
        simAudioVoices = 8f;
        simAudioDspBudget = 25f;
        scenario = s;
        scenarioTime = 0f;

        String desc = switch (s) {
            case ECS_OVERLOAD -> "ECS entity flood -> physics pressure -> engine budget failure";
            case PHYSICS_SPIKE -> "Physics complexity spike (stable entities) -> engine budget failure";
            case AUDIO_PRESSURE -> "Audio voice/DSP overload (independent of frame budget)";
            case BASELINE -> "Stable baseline";
        };

        System.out.printf("[%.1fs] Scenario: %s -- %s%n", totalTime, s, desc);
        session.submit(new DebugEvent(tick, System.currentTimeMillis(),
            "scenario", DebugCategory.ENGINE, DebugSeverity.INFO,
            "scenario.started", s + ": " + desc));
    }

    // --- Metric simulation ---

    private void updateMetrics(float dt) {
        float noise = (float)(Math.random() - 0.5) * 0.3f;
        float progress = Math.min(scenarioTime / 6f, 1f); // 6s ramp

        switch (scenario) {
            case BASELINE -> {
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + noise, dt * 3f);
                simEntityCount = lerp(simEntityCount, 100f, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + noise * 0.2f, dt * 3f);
                simPhysicsContacts = lerp(simPhysicsContacts, 10f, dt * 2f);
                simAudioVoices = lerp(simAudioVoices, 8f, dt * 2f);
                simAudioDspBudget = lerp(simAudioDspBudget, 25f, dt * 2f);
            }

            case ECS_OVERLOAD -> {
                // CAUSE: entity count rises first
                float entityTarget = 100f + progress * 800f;
                simEntityCount = lerp(simEntityCount, entityTarget + noise * 20, dt * 4f);

                // EFFECT 1: physics contacts/step time follow entity count (lagged)
                float physicsLag = Math.max(0, progress - 0.15f); // 15% lag
                simPhysicsContacts = simEntityCount * 0.15f + noise * 5;
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + physicsLag * 8f + noise, dt * 2f);

                // EFFECT 2: frame time follows physics (further lagged)
                float frameLag = Math.max(0, progress - 0.3f); // 30% lag
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + frameLag * 20f + noise * 2, dt * 2f);

                // Audio unaffected
                simAudioVoices = lerp(simAudioVoices, 8f + noise, dt * 3f);
                simAudioDspBudget = lerp(simAudioDspBudget, 25f + noise * 2, dt * 3f);
            }

            case PHYSICS_SPIKE -> {
                // Entity count stays stable
                simEntityCount = lerp(simEntityCount, 100f + noise * 10, dt * 3f);

                // CAUSE: physics step time jumps (collision complexity, not entity count)
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + progress * 10f + noise, dt * 3f);
                simPhysicsContacts = lerp(simPhysicsContacts, 10f + progress * 40f, dt * 2f);

                // EFFECT: frame time follows physics (lagged)
                float frameLag = Math.max(0, progress - 0.2f);
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + frameLag * 15f + noise * 2, dt * 2f);

                // Audio unaffected
                simAudioVoices = lerp(simAudioVoices, 8f + noise, dt * 3f);
                simAudioDspBudget = lerp(simAudioDspBudget, 25f + noise * 2, dt * 3f);
            }

            case AUDIO_PRESSURE -> {
                // Gameplay load stays normal
                simEntityCount = lerp(simEntityCount, 100f + noise * 10, dt * 3f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + noise * 0.2f, dt * 3f);
                simPhysicsContacts = lerp(simPhysicsContacts, 10f + noise * 2, dt * 3f);
                simFrameTimeMs = lerp(simFrameTimeMs, 3.5f + noise, dt * 3f);

                // CAUSE: audio voice/DSP pressure rises independently
                simAudioVoices = lerp(simAudioVoices, 8f + progress * 24f + noise * 2, dt * 3f);
                simAudioDspBudget = lerp(simAudioDspBudget, 25f + progress * 65f + noise * 3, dt * 3f);
            }
        }

        // Clamp
        simFrameTimeMs = Math.max(1f, simFrameTimeMs);
        simEntityCount = Math.max(10f, simEntityCount);
        simPhysicsStepMs = Math.max(0.2f, simPhysicsStepMs);
        simPhysicsContacts = Math.max(0f, simPhysicsContacts);
        simAudioVoices = Math.max(1f, simAudioVoices);
        simAudioDspBudget = Math.max(5f, simAudioDspBudget);
    }

    // --- Snapshot generation ---

    private Map<String, DebugSnapshot> buildSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, DebugSnapshot> snapshots = new HashMap<>();

        snapshots.put("engine", new DebugSnapshot(tick, now, "engine", DebugCategory.ENGINE,
            Map.of(
                "frameTimeMs", (double) simFrameTimeMs,
                "budgetPercent", (double)(simFrameTimeMs / 16.667 * 100),
                "tickRate", 60.0,
                "uptimeSeconds", (double) totalTime
            ),
            Map.of("healthy", simFrameTimeMs < 16f),
            scenario.name()
        ));

        snapshots.put("physics", new DebugSnapshot(tick, now, "physics", DebugCategory.PHYSICS,
            Map.of(
                "stepTimeMs", (double) simPhysicsStepMs,
                "contacts", (double) simPhysicsContacts,
                "bodies", (double)(int)(simEntityCount * 0.3f)
            ),
            Map.of("hasContacts", simPhysicsContacts > 5),
            ""
        ));

        snapshots.put("ecs", new DebugSnapshot(tick, now, "ecs", DebugCategory.ECS,
            Map.of("entityCount", (double) simEntityCount),
            Map.of(),
            ""
        ));

        snapshots.put("audio", new DebugSnapshot(tick, now, "audio", DebugCategory.AUDIO,
            Map.of(
                "voices", (double) simAudioVoices,
                "dspBudget", (double) simAudioDspBudget
            ),
            Map.of("underrunActive", simAudioDspBudget > 80),
            ""
        ));

        return snapshots;
    }

    private static float lerp(float current, float target, float rate) {
        return current + (target - current) * Math.min(rate, 1f);
    }
}
