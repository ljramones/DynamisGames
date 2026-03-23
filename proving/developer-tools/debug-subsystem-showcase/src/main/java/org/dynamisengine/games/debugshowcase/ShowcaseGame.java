package org.dynamisengine.games.debugshowcase;

import org.dynamisengine.debug.api.DebugCategory;
import org.dynamisengine.debug.api.DebugSeverity;
import org.dynamisengine.debug.api.DebugSnapshot;
import org.dynamisengine.debug.api.WatchdogRule;
import org.dynamisengine.debug.api.draw.DebugBoxCommand;
import org.dynamisengine.debug.api.draw.DebugLineCommand;
import org.dynamisengine.debug.api.draw.DepthMode;
import org.dynamisengine.debug.api.event.DebugEvent;
import org.dynamisengine.debug.core.DebugAnalytics;
import org.dynamisengine.debug.core.DebugSession;
import org.dynamisengine.debug.draw.DebugDrawQueue;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.ui.debug.builder.DebugOverlayBuilder;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshotMapper;
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.ui.debug.model.DebugOverlayPanelId;
import org.dynamisengine.ui.debug.model.PanelRegion;
import org.dynamisengine.ui.debug.model.RowSeverity;
import org.dynamisengine.ui.debug.render.DebugOverlayRenderer;
import org.dynamisengine.ui.debug.export.JsonFileExporter;
import org.dynamisengine.ui.debug.export.TcpExporter;
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.ui.debug.runtime.DebugOverlayState;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Debug Subsystem Showcase — capstone proving module.
 *
 * <p>Demonstrates the complete DynamisDebug observability stack in one
 * integrated runtime: overlay panels, debug draw, watchdogs, history/timeline,
 * sparkline trends, and analytical queries — all working together across
 * multiple simulated subsystems.
 *
 * <p>Four phases cycle automatically:
 * <ol>
 *   <li><b>IDLE</b> — stable baseline, all subsystems nominal</li>
 *   <li><b>COMBAT</b> — entity/physics load rises, debug draw entities increase</li>
 *   <li><b>STRESS</b> — spike in engine + audio, watchdogs fire, timeline fills</li>
 *   <li><b>RECOVERY</b> — load settles, alerts decay, trends normalize</li>
 * </ol>
 */
public final class ShowcaseGame implements WorldApplication {

    enum Phase { IDLE, COMBAT, STRESS, RECOVERY }

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId PHASE_1 = new ActionId("p1");
    static final ActionId PHASE_2 = new ActionId("p2");
    static final ActionId PHASE_3 = new ActionId("p3");
    static final ActionId PHASE_4 = new ActionId("p4");
    static final ActionId CYCLE_QUERY = new ActionId("cycleQ");
    static final ActionId INJECT_SPIKE = new ActionId("spike");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE_ACTION = new ActionId("pause");
    static final ActionId FOCUS = new ActionId("focus");
    static final ActionId FOCUS_NEXT = new ActionId("focusNext");
    static final ActionId FOCUS_PREV = new ActionId("focusPrev");
    static final ActionId REPLAY_TOGGLE = new ActionId("replayToggle");
    static final ActionId STEP_BACK = new ActionId("stepBack");
    static final ActionId STEP_FWD = new ActionId("stepFwd");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("showcase");
    private static final int KEY_TAB = 258, KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52;
    private static final int KEY_Q = 81, KEY_SPACE = 32, KEY_R = 82, KEY_P = 80, KEY_ESC = 256;
    private static final int KEY_F = 70, KEY_LEFT_BRACKET = 91, KEY_RIGHT_BRACKET = 93;
    private static final int KEY_T = 84, KEY_COMMA = 44, KEY_PERIOD = 46;

    private static final DebugOverlayPanelId QUERY_PANEL_ID = new DebugOverlayPanelId("query", "results");
    private static final float PHASE_DURATION = 5f;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;

    // Renderers
    private final TextRenderer textRenderer = new TextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;
    private DebugDrawRenderer debugDrawRenderer;

    // Debug spine
    private DebugSession session;
    private DebugAnalytics analytics;
    private DebugViewSnapshotMapper mapper;
    private DebugOverlayBuilder builder;
    private DebugDrawQueue drawQueue;
    private TcpExporter tcpExporter;
    private JsonFileExporter fileExporter;
    private final DebugOverlayState overlayState = new DebugOverlayState();

    // State
    private Phase phase = Phase.IDLE;
    private float phaseTime = 0f;
    private float totalTime = 0f;
    private long tick = 0;
    private boolean overlayVisible = true;
    private boolean paused = false;
    private int queryMode = 0; // 0=spikes, 1=noisy, 2=correlation
    private int lastPanelCount = 0;
    private float cameraAngle = 0f;

    // Simulated subsystem metrics
    private float simFrameTimeMs = 3f;
    private float simEntityCount = 80f;
    private float simPhysicsStepMs = 1f;
    private float simPhysicsContacts = 8f;
    private float simAudioVoices = 6f;
    private float simAudioDspBudget = 20f;
    private float simGpuDrawCalls = 50f;

    public ShowcaseGame(WindowSubsystem w, WindowInputSubsystem i) {
        this.windowSub = w;
        this.inputSub = i;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                    Map.entry(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0))),
                    Map.entry(PHASE_1, List.of(new KeyBinding(KEY_1, 0))),
                    Map.entry(PHASE_2, List.of(new KeyBinding(KEY_2, 0))),
                    Map.entry(PHASE_3, List.of(new KeyBinding(KEY_3, 0))),
                    Map.entry(PHASE_4, List.of(new KeyBinding(KEY_4, 0))),
                    Map.entry(CYCLE_QUERY, List.of(new KeyBinding(KEY_Q, 0))),
                    Map.entry(INJECT_SPIKE, List.of(new KeyBinding(KEY_SPACE, 0))),
                    Map.entry(RESET, List.of(new KeyBinding(KEY_R, 0))),
                    Map.entry(PAUSE_ACTION, List.of(new KeyBinding(KEY_P, 0))),
                    Map.entry(FOCUS, List.of(new KeyBinding(KEY_F, 0))),
                    Map.entry(FOCUS_NEXT, List.of(new KeyBinding(KEY_RIGHT_BRACKET, 0))),
                    Map.entry(FOCUS_PREV, List.of(new KeyBinding(KEY_LEFT_BRACKET, 0))),
                    Map.entry(REPLAY_TOGGLE, List.of(new KeyBinding(KEY_T, 0))),
                    Map.entry(STEP_BACK, List.of(new KeyBinding(KEY_COMMA, 0))),
                    Map.entry(STEP_FWD, List.of(new KeyBinding(KEY_PERIOD, 0))),
                    Map.entry(QUIT, List.of(new KeyBinding(KEY_ESC, 0)))),
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
        debugDrawRenderer = new DebugDrawRenderer();
        debugDrawRenderer.initialize();
        initSession();

        // Start exporters
        tcpExporter = new TcpExporter(9876);
        try { tcpExporter.start(); } catch (IOException e) {
            System.err.println("TCP exporter failed to start: " + e.getMessage());
        }
        fileExporter = new JsonFileExporter(java.nio.file.Path.of("debug-session.ndjson"));
        try { fileExporter.open(); } catch (IOException e) {
            System.err.println("File exporter failed to open: " + e.getMessage());
        }

        System.out.println("=== Debug Subsystem Showcase ===");
        System.out.println("Capstone: full debug spine with overlay + debug draw + queries + timeline");
        System.out.println("TCP telemetry server on port 9876 (connect with debug-remote-viewer)");
        System.out.println("1-4=phases  Q=cycle query  Space=spike  R=reset  P=pause  Tab=overlay  Esc=quit");
    }

    private void initSession() {
        session = new DebugSession();
        analytics = new DebugAnalytics(session);
        mapper = new DebugViewSnapshotMapper(session);
        builder = new DebugOverlayBuilder(new DebugOverlayOptions(true, true, true, 60, 16, 8, false));
        drawQueue = new DebugDrawQueue();

        // Multi-subsystem watchdog rules
        session.watchdog().addRule(new WatchdogRule("engine.frameBudgetHigh", "engine", "frameTimeMs",
            8.0, WatchdogRule.Comparison.GREATER_THAN, DebugSeverity.WARNING, "Frame > 8ms", 60));
        session.watchdog().addRule(new WatchdogRule("engine.frameCritical", "engine", "frameTimeMs",
            16.0, WatchdogRule.Comparison.GREATER_THAN, DebugSeverity.ERROR, "Frame > 16ms", 30));
        session.watchdog().addRule(new WatchdogRule("physics.stepHigh", "physics", "stepTimeMs",
            4.0, WatchdogRule.Comparison.GREATER_THAN, DebugSeverity.WARNING, "Physics step > 4ms", 60));
        session.watchdog().addRule(new WatchdogRule("ecs.entityFlood", "ecs", "entityCount",
            400.0, WatchdogRule.Comparison.GREATER_THAN, DebugSeverity.WARNING, "Entities > 400", 90));
        session.watchdog().addRule(new WatchdogRule("audio.dspHigh", "audio", "dspBudget",
            70.0, WatchdogRule.Comparison.GREATER_THAN, DebugSeverity.WARNING, "DSP > 70%", 60));

        phase = Phase.IDLE;
        phaseTime = 0f;
        totalTime = 0f;
        tick = 0;
        resetMetrics();
    }

    private void resetMetrics() {
        simFrameTimeMs = 3f; simEntityCount = 80f; simPhysicsStepMs = 1f;
        simPhysicsContacts = 8f; simAudioVoices = 6f; simAudioDspBudget = 20f;
        simGpuDrawCalls = 50f;
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(PAUSE_ACTION)) paused = !paused;
            if (frame.pressed(RESET)) { initSession(); }
            if (frame.pressed(PHASE_1)) setPhase(Phase.IDLE);
            if (frame.pressed(PHASE_2)) setPhase(Phase.COMBAT);
            if (frame.pressed(PHASE_3)) setPhase(Phase.STRESS);
            if (frame.pressed(PHASE_4)) setPhase(Phase.RECOVERY);
            if (frame.pressed(CYCLE_QUERY)) { queryMode = (queryMode + 1) % 3; }
            if (frame.pressed(INJECT_SPIKE)) injectSpike();
            if (frame.pressed(FOCUS)) overlayState.toggleFocus();
            if (frame.pressed(FOCUS_NEXT)) overlayState.nextPanel(lastPanelCount);
            if (frame.pressed(FOCUS_PREV)) overlayState.previousPanel(lastPanelCount);
            if (frame.pressed(REPLAY_TOGGLE)) overlayState.toggleReplay(tick);
            if (frame.pressed(STEP_BACK)) {
                long oldest = session.history().oldestFrameNumber();
                overlayState.stepBackward(10, Math.max(0, oldest));
            }
            if (frame.pressed(STEP_FWD)) {
                overlayState.stepForward(10, session.history().newestFrameNumber());
            }
        }

        if (!paused) {
            tick++;
            totalTime += dt;
            phaseTime += dt;
            cameraAngle += dt * 0.2f;
            advancePhase();
            updateMetrics(dt);
        }

        // === Debug spine: record + evaluate ===
        Map<String, DebugSnapshot> frameSnapshots = buildSnapshots();
        session.history().record(tick, frameSnapshots);
        var alerts = session.watchdog().evaluate(tick, frameSnapshots);
        for (var a : alerts) session.submit(a);

        // === Debug draw: world-space indicators ===
        buildDebugDraw();

        // === Render ===
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);

        float[] bg = phaseBackground();
        glClearColor(bg[0], bg[1], bg[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        // Render debug draw in 3D
        float aspect = (float) w / Math.max(h, 1);
        float[] viewProj = buildViewProjMatrix(aspect);
        var commands = drawQueue.activeCommands();
        if (!commands.isEmpty()) {
            debugDrawRenderer.render(commands, viewProj);
        }
        drawQueue.endFrame(dt);

        // Render overlay on top
        if (overlayVisible) {
            // Choose snapshot source: live or historical
            var viewSnapshot = overlayState.isReplayMode()
                ? mapper.mapHistoricalFrame(overlayState.selectedFrameNumber())
                : mapper.mapFromFrame(tick, frameSnapshots);

            // Export for remote viewer + file recording
            if (!overlayState.isReplayMode()) {
                if (tcpExporter != null) tcpExporter.export(viewSnapshot);
                if (fileExporter != null) fileExporter.export(viewSnapshot);
            }

            List<DebugOverlayPanel> panels = new ArrayList<>(builder.buildAll(viewSnapshot));
            panels.add(buildQueryPanel());
            lastPanelCount = panels.size();

            String modeLabel = overlayState.modeLabel(tick);

            if (overlayState.isFocusMode()) {
                // Focus mode: render single panel fullscreen
                var focused = overlayState.focusedPanel(panels);
                if (focused != null) {
                    // Filter timeline events by panel category
                    String category = focused.id().category();
                    var categoryEvents = viewSnapshot.timelineEvents().stream()
                        .filter(e -> e.source().contains(category) ||
                                     category.equals("engine") && e.source().equals("scenario") ||
                                     category.equals("query"))
                        .toList();

                    overlayRenderer.renderFocus(focused,
                        new DebugOverlayRenderer.LayoutBox(0, 0, w, h), categoryEvents);
                }

                // Focus status bar
                textRenderer.beginFrame(w, h);
                textRenderer.drawText(String.format(
                    "FOCUS [%d/%d]  %s  Phase: %s  Tick: %d  %s",
                    overlayState.focusedPanelIndex() + 1, lastPanelCount,
                    modeLabel, phase, tick, paused ? "[PAUSED]" : ""),
                    10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
                textRenderer.drawText(
                    "F=exit  [/]=cycle  T=replay  ,/.=step  Esc=quit",
                    10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
                textRenderer.endFrame();
            } else {
                // Normal overlay mode
                overlayRenderer.renderPanels(panels, w, h);

                textRenderer.beginFrame(w, h);
                textRenderer.drawText(String.format("%s  Phase: %s  Tick: %d  Query: %s  %s",
                    modeLabel, phase, tick, queryModeName(), paused ? "[PAUSED]" : ""),
                    10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
                textRenderer.drawText(
                    "F=focus  T=replay  ,/.=step  1-4=phase  Q=query  Space=spike  Esc=quit",
                    10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
                textRenderer.endFrame();
            }
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        if (tcpExporter != null) tcpExporter.close();
        if (fileExporter != null) fileExporter.close();
        debugDrawRenderer.shutdown();
        textRenderer.shutdown();
        System.out.printf("[Showcase] %d ticks, %.1fs, phase=%s%n", tick, totalTime, phase);
    }

    // --- Phase management ---

    private void setPhase(Phase p) {
        phase = p;
        phaseTime = 0f;
        session.submit(new DebugEvent(tick, System.currentTimeMillis(),
            "scenario", DebugCategory.ENGINE, DebugSeverity.INFO,
            "phase.set", p.name()));
    }

    private void advancePhase() {
        if (phaseTime >= PHASE_DURATION) {
            phaseTime = 0f;
            Phase old = phase;
            phase = switch (phase) {
                case IDLE -> Phase.COMBAT;
                case COMBAT -> Phase.STRESS;
                case STRESS -> Phase.RECOVERY;
                case RECOVERY -> Phase.IDLE;
            };
            session.submit(new DebugEvent(tick, System.currentTimeMillis(),
                "scenario", DebugCategory.ENGINE, DebugSeverity.INFO,
                "phase.auto", old + " -> " + phase));
        }
    }

    private void injectSpike() {
        simFrameTimeMs = 20f + (float)(Math.random() * 15);
        simPhysicsStepMs = 6f + (float)(Math.random() * 4);
        simEntityCount = 500f + (float)(Math.random() * 200);
        simAudioVoices = 20f + (float)(Math.random() * 10);
        session.submit(new DebugEvent(tick, System.currentTimeMillis(),
            "user", DebugCategory.ENGINE, DebugSeverity.CRITICAL,
            "user.spike", String.format("Manual spike: %.1fms", simFrameTimeMs)));
    }

    // --- Metric simulation ---

    private void updateMetrics(float dt) {
        float n = (float)(Math.random() - 0.5) * 0.4f;
        float progress = Math.min(phaseTime / PHASE_DURATION, 1f);

        switch (phase) {
            case IDLE -> {
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + n, dt * 3f);
                simEntityCount = lerp(simEntityCount, 80f + n * 10, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + n * 0.2f, dt * 3f);
                simPhysicsContacts = lerp(simPhysicsContacts, 8f, dt * 2f);
                simAudioVoices = lerp(simAudioVoices, 6f + n, dt * 2f);
                simAudioDspBudget = lerp(simAudioDspBudget, 20f + n * 2, dt * 2f);
                simGpuDrawCalls = lerp(simGpuDrawCalls, 50f + n * 5, dt * 2f);
            }
            case COMBAT -> {
                simEntityCount = lerp(simEntityCount, 80f + progress * 350f, dt * 3f);
                simPhysicsContacts = simEntityCount * 0.12f;
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + progress * 4f + n, dt * 2f);
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + progress * 8f + n, dt * 2f);
                simAudioVoices = lerp(simAudioVoices, 6f + progress * 8f, dt * 2f);
                simAudioDspBudget = lerp(simAudioDspBudget, 20f + progress * 30f, dt * 2f);
                simGpuDrawCalls = lerp(simGpuDrawCalls, 50f + progress * 80f, dt * 2f);
            }
            case STRESS -> {
                simFrameTimeMs = lerp(simFrameTimeMs, 18f + n * 5, dt * 2f);
                simEntityCount = lerp(simEntityCount, 500f + n * 30, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 6f + n * 2, dt * 2f);
                simPhysicsContacts = simEntityCount * 0.15f;
                simAudioVoices = lerp(simAudioVoices, 25f + n * 3, dt * 2f);
                simAudioDspBudget = lerp(simAudioDspBudget, 75f + n * 5, dt * 2f);
                simGpuDrawCalls = lerp(simGpuDrawCalls, 150f + n * 10, dt * 2f);
            }
            case RECOVERY -> {
                float recov = 1f - progress;
                simFrameTimeMs = lerp(simFrameTimeMs, 3f + recov * 10f + n, dt * 3f);
                simEntityCount = lerp(simEntityCount, 80f + recov * 300f, dt * 2f);
                simPhysicsStepMs = lerp(simPhysicsStepMs, 1f + recov * 3f + n * 0.3f, dt * 3f);
                simPhysicsContacts = simEntityCount * 0.1f;
                simAudioVoices = lerp(simAudioVoices, 6f + recov * 15f, dt * 2f);
                simAudioDspBudget = lerp(simAudioDspBudget, 20f + recov * 45f, dt * 2f);
                simGpuDrawCalls = lerp(simGpuDrawCalls, 50f + recov * 80f, dt * 2f);
            }
        }

        // Clamp
        simFrameTimeMs = Math.max(1f, simFrameTimeMs);
        simEntityCount = Math.max(10f, simEntityCount);
        simPhysicsStepMs = Math.max(0.2f, simPhysicsStepMs);
        simPhysicsContacts = Math.max(0f, simPhysicsContacts);
        simAudioVoices = Math.max(1f, simAudioVoices);
        simAudioDspBudget = Math.max(5f, simAudioDspBudget);
        simGpuDrawCalls = Math.max(10f, simGpuDrawCalls);
    }

    // --- Debug draw: world-space visualization ---

    private void buildDebugDraw() {
        // Coordinate axes (always visible)
        drawQueue.submit(DebugLineCommand.of(0, 0, 0, 3, 0, 0, 1, 0, 0, DepthMode.ALWAYS_VISIBLE));
        drawQueue.submit(DebugLineCommand.of(0, 0, 0, 0, 3, 0, 0, 1, 0, DepthMode.ALWAYS_VISIBLE));
        drawQueue.submit(DebugLineCommand.of(0, 0, 0, 0, 0, 3, 0, 0, 1, DepthMode.ALWAYS_VISIBLE));

        // Entity representation: boxes that scale with entity count
        int entityBoxes = Math.min((int)(simEntityCount / 40), 15);
        for (int i = 0; i < entityBoxes; i++) {
            float angle = (float)(i * Math.PI * 2 / entityBoxes) + totalTime * 0.3f;
            float radius = 3f + (float) Math.sin(totalTime + i) * 0.5f;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float y = 0.5f + (float) Math.sin(totalTime * 2 + i * 0.7f) * 0.3f;

            // Color by phase: green=idle, yellow=combat, red=stress, cyan=recovery
            float r, g, b;
            switch (phase) {
                case IDLE -> { r = 0.2f; g = 0.8f; b = 0.3f; }
                case COMBAT -> { r = 0.9f; g = 0.7f; b = 0.1f; }
                case STRESS -> { r = 1f; g = 0.2f; b = 0.2f; }
                case RECOVERY -> { r = 0.2f; g = 0.7f; b = 0.9f; }
                default -> { r = 0.5f; g = 0.5f; b = 0.5f; }
            }

            drawQueue.submit(DebugBoxCommand.of(x, y, z, 0.3f, 0.3f, 0.3f, r, g, b, DepthMode.TESTED));
        }

        // Physics contacts: lines from origin toward entity boxes
        int contactLines = Math.min((int)(simPhysicsContacts / 5), 10);
        for (int i = 0; i < contactLines; i++) {
            float angle = (float)(i * Math.PI * 2 / contactLines) + totalTime * 0.5f;
            float x = (float) Math.cos(angle) * 2f;
            float z = (float) Math.sin(angle) * 2f;
            drawQueue.submit(DebugLineCommand.of(0, 0.1f, 0, x, 0.3f, z, 1f, 1f, 0f, DepthMode.TESTED));
        }

        // Ground grid
        for (int i = -5; i <= 5; i++) {
            drawQueue.submit(DebugLineCommand.of(i, 0, -5, i, 0, 5, 0.15f, 0.15f, 0.2f, DepthMode.TESTED));
            drawQueue.submit(DebugLineCommand.of(-5, 0, i, 5, 0, i, 0.15f, 0.15f, 0.2f, DepthMode.TESTED));
        }
    }

    // --- Snapshots ---

    private Map<String, DebugSnapshot> buildSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, DebugSnapshot> s = new HashMap<>();

        s.put("engine", new DebugSnapshot(tick, now, "engine", DebugCategory.ENGINE,
            Map.of("frameTimeMs", (double) simFrameTimeMs,
                   "budgetPercent", (double)(simFrameTimeMs / 16.667 * 100),
                   "tickRate", 60.0, "uptimeSeconds", (double) totalTime),
            Map.of("healthy", simFrameTimeMs < 16), phase.name()));

        s.put("ecs", new DebugSnapshot(tick, now, "ecs", DebugCategory.ECS,
            Map.of("entityCount", (double) simEntityCount),
            Map.of(), ""));

        s.put("physics", new DebugSnapshot(tick, now, "physics", DebugCategory.PHYSICS,
            Map.of("stepTimeMs", (double) simPhysicsStepMs,
                   "contacts", (double) simPhysicsContacts,
                   "bodies", (double)(int)(simEntityCount * 0.3)),
            Map.of("hasContacts", simPhysicsContacts > 5), ""));

        s.put("audio", new DebugSnapshot(tick, now, "audio", DebugCategory.AUDIO,
            Map.of("voices", (double) simAudioVoices,
                   "dspBudget", (double) simAudioDspBudget),
            Map.of("underrunActive", simAudioDspBudget > 80), ""));

        s.put("gpu", new DebugSnapshot(tick, now, "gpu", DebugCategory.RENDERING,
            Map.of("drawCalls", (double) simGpuDrawCalls,
                   "backlog", (double) Math.max(0, simGpuDrawCalls - 100)),
            Map.of(), ""));

        return s;
    }

    // --- Query panel ---

    private DebugOverlayPanel buildQueryPanel() {
        var pb = DebugOverlayPanel.builder(QUERY_PANEL_ID, "Query: " + queryModeName())
            .region(PanelRegion.GRID).order(100);

        switch (queryMode) {
            case 0 -> { // Spike analysis
                var r = analytics.findSpikes("engine", "frameTimeMs", 10.0, 200);
                pb.row("spikeCount", String.valueOf(r.spikeCount()),
                    r.spikeCount() > 5 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                pb.row("lastSpike", r.lastSpikeFrame() >= 0 ? "T" + r.lastSpikeFrame() : "none");
                pb.row("maxMs", String.format("%.1f", r.maxValue()),
                    r.maxValue() > 16 ? RowSeverity.ERROR : RowSeverity.NORMAL);
            }
            case 1 -> { // Noisy rules
                var rules = analytics.rankNoisyRules(300);
                int rank = 1;
                for (var rule : rules) {
                    pb.row(rank + ". " + rule.ruleName(), rule.fireCount() + " fires",
                        rule.fireCount() > 20 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                    if (++rank > 5) break;
                }
                if (rules.isEmpty()) pb.row("status", "no fires");
            }
            case 2 -> { // Correlation
                var r = analytics.findCorrelatedFrames(
                    "ecs", "entityCount", 300.0,
                    "engine", "frameTimeMs", 8.0, 200);
                pb.row("query", "ECS>300 AND frame>8ms");
                pb.row("matches", String.valueOf(r.matches()),
                    r.matches() > 10 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                pb.row("range", r.firstMatch() >= 0
                    ? "T" + r.firstMatch() + "-T" + r.lastMatch() : "none");
            }
        }

        return pb.build();
    }

    private String queryModeName() {
        return switch (queryMode) { case 0 -> "Spikes"; case 1 -> "NoisyRules"; default -> "Correlation"; };
    }

    // --- Camera ---

    private float[] buildViewProjMatrix(float aspect) {
        float fov = (float) Math.toRadians(55);
        float near = 0.1f, far = 100f;
        float f = 1f / (float) Math.tan(fov / 2f);
        float[] proj = new float[16];
        proj[0] = f / aspect; proj[5] = f;
        proj[10] = (far + near) / (near - far); proj[11] = -1f;
        proj[14] = (2f * far * near) / (near - far);

        float dist = 10f;
        float eyeX = (float) Math.sin(cameraAngle) * dist;
        float eyeZ = (float) Math.cos(cameraAngle) * dist;
        float[] view = lookAt(eyeX, 5f, eyeZ, 0, 0, 0);

        return multiply(proj, view);
    }

    private static float[] lookAt(float ex, float ey, float ez, float cx, float cy, float cz) {
        float fx = cx-ex, fy = cy-ey, fz = cz-ez;
        float fl = (float)Math.sqrt(fx*fx+fy*fy+fz*fz); fx/=fl; fy/=fl; fz/=fl;
        float sx = fy*0-fz*1, sy = fz*0-fx*0, sz = fx*1-fy*0;
        float sl = (float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        if (sl > 0.001f) { sx/=sl; sy/=sl; sz/=sl; }
        float ux = sy*fz-sz*fy, uy = sz*fx-sx*fz, uz = sx*fy-sy*fx;
        float[] m = new float[16];
        m[0]=sx; m[4]=sy; m[8]=sz; m[1]=ux; m[5]=uy; m[9]=uz;
        m[2]=-fx; m[6]=-fy; m[10]=-fz;
        m[12]=-(sx*ex+sy*ey+sz*ez); m[13]=-(ux*ex+uy*ey+uz*ez);
        m[14]=(fx*ex+fy*ey+fz*ez); m[15]=1;
        return m;
    }

    private static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i=0;i<4;i++) for (int j=0;j<4;j++) for (int k=0;k<4;k++) r[j*4+i]+=a[k*4+i]*b[j*4+k];
        return r;
    }

    private float[] phaseBackground() {
        return switch (phase) {
            case IDLE -> new float[]{0.04f, 0.05f, 0.08f};
            case COMBAT -> new float[]{0.06f, 0.05f, 0.04f};
            case STRESS -> new float[]{0.1f, 0.04f, 0.04f};
            case RECOVERY -> new float[]{0.04f, 0.06f, 0.05f};
        };
    }

    private static float lerp(float c, float t, float r) { return c + (t - c) * Math.min(r, 1f); }
}
