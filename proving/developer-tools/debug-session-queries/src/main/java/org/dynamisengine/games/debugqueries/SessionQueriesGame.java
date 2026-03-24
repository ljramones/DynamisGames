package org.dynamisengine.games.debugqueries;

import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;

import org.dynamisengine.debug.api.DebugCategory;
import org.dynamisengine.debug.api.DebugSeverity;
import org.dynamisengine.debug.api.DebugSnapshot;
import org.dynamisengine.debug.api.WatchdogRule;
import org.dynamisengine.debug.api.event.DebugEvent;
import org.dynamisengine.debug.core.DebugAnalytics;
import org.dynamisengine.debug.core.DebugSession;
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
import org.dynamisengine.ui.debug.runtime.DebugOverlayOptions;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Debug Session Queries — proves the debug spine is queryable as data.
 *
 * <p>Demonstrates 4 analytical query modes over accumulated history:
 * <ol>
 *   <li><b>Spike Analysis</b> — find frames where frameTimeMs exceeded threshold</li>
 *   <li><b>Noisy Rules</b> — rank watchdog rules by fire count</li>
 *   <li><b>Threshold Crossings</b> — count transitions above a threshold</li>
 *   <li><b>Correlated Window</b> — find frames where two conditions co-occur</li>
 * </ol>
 *
 * <p>Controls:
 * <pre>
 *   1   — Spike Analysis query
 *   2   — Noisy Rules query
 *   3   — Threshold Crossings query
 *   4   — Correlated Window query
 *   R   — Reset history
 *   P   — Pause/resume
 *   Tab — Toggle overlay
 *   Esc — Quit
 * </pre>
 */
public final class SessionQueriesGame implements WorldApplication {

    enum QueryMode { SPIKE_ANALYSIS, NOISY_RULES, THRESHOLD_CROSSINGS, CORRELATED_WINDOW }

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId Q1 = new ActionId("q1");
    static final ActionId Q2 = new ActionId("q2");
    static final ActionId Q3 = new ActionId("q3");
    static final ActionId Q4 = new ActionId("q4");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("queries");
    private static final int KEY_TAB = 258, KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52;
    private static final int KEY_R = 82, KEY_P = 80, KEY_ESC = 256;
    private static final int QUERY_WINDOW = 200;

    private static final DebugOverlayPanelId QUERY_PANEL_ID = new DebugOverlayPanelId("query", "results");

    private final GlfwWindowSubsystem windowSub;
    private final InputWorldSubsystem inputSub;
    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;

    private DebugSession session;
    private DebugAnalytics analytics;
    private DebugViewSnapshotMapper mapper;
    private DebugOverlayBuilder builder;

    private QueryMode queryMode = QueryMode.SPIKE_ANALYSIS;
    private float totalTime = 0f;
    private long tick = 0;
    private boolean overlayVisible = true;
    private boolean paused = false;

    // Simulated metrics with interesting patterns
    private float simFrameTimeMs = 3.0f;
    private float simEntityCount = 100f;
    private float simPhysicsStepMs = 1.0f;
    private int spikeTimer = 0;

    public SessionQueriesGame(GlfwWindowSubsystem w, InputWorldSubsystem i) {
        this.windowSub = w;
        this.inputSub = i;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0)),
                        Q1, List.of(new KeyBinding(KEY_1, 0)),
                        Q2, List.of(new KeyBinding(KEY_2, 0)),
                        Q3, List.of(new KeyBinding(KEY_3, 0)),
                        Q4, List.of(new KeyBinding(KEY_4, 0)),
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

        System.out.println("=== Debug Session Queries ===");
        System.out.println("Proving: programmatic observability — the debug spine as queryable data");
        System.out.println("1=spike analysis  2=noisy rules  3=threshold crossings  4=correlation  R=reset  Esc=quit");
    }

    private void initSession() {
        session = new DebugSession();
        analytics = new DebugAnalytics(session);
        mapper = new DebugViewSnapshotMapper(session);
        builder = new DebugOverlayBuilder(new DebugOverlayOptions(true, true, true, 60, 16, 10, false));

        // Mix of good and bad rules for noise ranking
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameBudgetHigh", "engine", "frameTimeMs",
            8.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Frame time > 8ms", 30));
        session.watchdog().addRule(new WatchdogRule(
            "engine.frameCritical", "engine", "frameTimeMs",
            16.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.ERROR, "Frame time > 16ms", 30));
        session.watchdog().addRule(new WatchdogRule(
            "engine.noCooldown", "engine", "frameTimeMs",
            10.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Frame > 10ms (no cooldown)", 0));
        session.watchdog().addRule(new WatchdogRule(
            "ecs.entityHigh", "ecs", "entityCount",
            300.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Entities > 300", 60));
        session.watchdog().addRule(new WatchdogRule(
            "physics.stepHigh", "physics", "stepTimeMs",
            3.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "Physics step > 3ms", 60));

        totalTime = 0f;
        tick = 0;
        spikeTimer = 0;
        simFrameTimeMs = 3.0f;
        simEntityCount = 100f;
        simPhysicsStepMs = 1.0f;
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(PAUSE)) paused = !paused;
            if (frame.pressed(RESET)) { initSession(); }
            if (frame.pressed(Q1)) { queryMode = QueryMode.SPIKE_ANALYSIS; System.out.println("Query: Spike Analysis"); }
            if (frame.pressed(Q2)) { queryMode = QueryMode.NOISY_RULES; System.out.println("Query: Noisy Rules"); }
            if (frame.pressed(Q3)) { queryMode = QueryMode.THRESHOLD_CROSSINGS; System.out.println("Query: Threshold Crossings"); }
            if (frame.pressed(Q4)) { queryMode = QueryMode.CORRELATED_WINDOW; System.out.println("Query: Correlated Window"); }
        }

        if (!paused) {
            tick++;
            totalTime += dt;
            updateSimulation(dt);
        }

        // Record + evaluate
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
            List<DebugOverlayPanel> panels = new ArrayList<>(builder.buildAll(viewSnapshot));

            // Add query results panel
            panels.add(buildQueryResultsPanel());

            overlayRenderer.renderPanels(panels, w, h);

            // Status bar
            textRenderer.beginFrame(w, h);
            textRenderer.drawText(String.format(
                "Query: %s  Window: %d frames  Tick: %d  %s",
                queryMode, QUERY_WINDOW, tick, paused ? "[PAUSED]" : ""),
                10, h - 40, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText(
                "1=spikes  2=noisy rules  3=crossings  4=correlation  R=reset  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
    }

    // --- Simulation: creates interesting query-worthy patterns ---

    private void updateSimulation(float dt) {
        float noise = (float)(Math.random() - 0.5) * 0.5f;
        spikeTimer++;

        // Periodic spikes every ~120 frames (2 seconds)
        boolean isSpikeFrame = spikeTimer % 120 < 8;

        if (isSpikeFrame) {
            simFrameTimeMs = 15f + (float)(Math.random() * 15); // 15-30ms spikes
            simEntityCount = 350f + (float)(Math.random() * 200);
            simPhysicsStepMs = 4f + (float)(Math.random() * 3);
        } else {
            simFrameTimeMs = lerp(simFrameTimeMs, 4f + noise, dt * 5f);
            simEntityCount = lerp(simEntityCount, 120f + noise * 20, dt * 3f);
            simPhysicsStepMs = lerp(simPhysicsStepMs, 1.2f + noise * 0.3f, dt * 4f);
        }

        simFrameTimeMs = Math.max(1f, simFrameTimeMs);
        simEntityCount = Math.max(10f, simEntityCount);
        simPhysicsStepMs = Math.max(0.3f, simPhysicsStepMs);
    }

    private Map<String, DebugSnapshot> buildSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, DebugSnapshot> snapshots = new HashMap<>();

        snapshots.put("engine", new DebugSnapshot(tick, now, "engine", DebugCategory.ENGINE,
            Map.of("frameTimeMs", (double) simFrameTimeMs,
                   "budgetPercent", (double)(simFrameTimeMs / 16.667 * 100)),
            Map.of("healthy", simFrameTimeMs < 16),
            ""));

        snapshots.put("ecs", new DebugSnapshot(tick, now, "ecs", DebugCategory.ECS,
            Map.of("entityCount", (double) simEntityCount),
            Map.of(), ""));

        snapshots.put("physics", new DebugSnapshot(tick, now, "physics", DebugCategory.PHYSICS,
            Map.of("stepTimeMs", (double) simPhysicsStepMs),
            Map.of(), ""));

        return snapshots;
    }

    // --- Query Results Panel ---

    private DebugOverlayPanel buildQueryResultsPanel() {
        var panelBuilder = DebugOverlayPanel.builder(QUERY_PANEL_ID, "Query Results")
            .region(PanelRegion.GRID)
            .order(100); // after all category panels

        panelBuilder.row("mode", queryMode.name());
        panelBuilder.row("window", QUERY_WINDOW + " frames");

        switch (queryMode) {
            case SPIKE_ANALYSIS -> {
                var result = analytics.findSpikes("engine", "frameTimeMs", 10.0, QUERY_WINDOW);
                panelBuilder.row("spikeCount", String.valueOf(result.spikeCount()),
                    result.spikeCount() > 0 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                panelBuilder.row("lastSpikeFrame", result.lastSpikeFrame() >= 0
                    ? "T" + result.lastSpikeFrame() : "none");
                panelBuilder.row("maxFrameTimeMs", String.format("%.2f", result.maxValue()),
                    result.maxValue() > 16 ? RowSeverity.ERROR : RowSeverity.NORMAL);
            }

            case NOISY_RULES -> {
                var rules = analytics.rankNoisyRules(QUERY_WINDOW * 3);
                if (rules.isEmpty()) {
                    panelBuilder.row("status", "no rule fires in window");
                } else {
                    int rank = 1;
                    for (var rule : rules) {
                        RowSeverity sev = rule.fireCount() > 50 ? RowSeverity.ERROR
                            : rule.fireCount() > 10 ? RowSeverity.WARNING : RowSeverity.NORMAL;
                        panelBuilder.row(rank + ". " + rule.ruleName(),
                            rule.fireCount() + " fires (" + rule.severity() + ")", sev);
                        rank++;
                        if (rank > 8) break;
                    }
                }
            }

            case THRESHOLD_CROSSINGS -> {
                var result = analytics.analyzeThresholdCrossings("engine", "frameTimeMs", 10.0, QUERY_WINDOW);
                panelBuilder.row("threshold", "frameTimeMs > 10.0");
                panelBuilder.row("crossings", String.valueOf(result.crossings()),
                    result.crossings() > 5 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                panelBuilder.row("firstCrossing", result.firstCrossing() >= 0
                    ? "T" + result.firstCrossing() : "none");
                panelBuilder.row("lastCrossing", result.lastCrossing() >= 0
                    ? "T" + result.lastCrossing() : "none");
                panelBuilder.row("framesAbove", String.valueOf(result.framesAbove()),
                    result.framesAbove() > 20 ? RowSeverity.ERROR : RowSeverity.NORMAL);
            }

            case CORRELATED_WINDOW -> {
                var result = analytics.findCorrelatedFrames(
                    "ecs", "entityCount", 300.0,
                    "engine", "frameTimeMs", 10.0,
                    QUERY_WINDOW);
                panelBuilder.row("query", "ECS>300 AND frame>10ms");
                panelBuilder.row("matches", String.valueOf(result.matches()),
                    result.matches() > 10 ? RowSeverity.WARNING : RowSeverity.NORMAL);
                panelBuilder.row("firstMatch", result.firstMatch() >= 0
                    ? "T" + result.firstMatch() : "none");
                panelBuilder.row("lastMatch", result.lastMatch() >= 0
                    ? "T" + result.lastMatch() : "none");
            }
        }

        return panelBuilder.build();
    }

    private static float lerp(float current, float target, float rate) {
        return current + (target - current) * Math.min(rate, 1f);
    }
}
