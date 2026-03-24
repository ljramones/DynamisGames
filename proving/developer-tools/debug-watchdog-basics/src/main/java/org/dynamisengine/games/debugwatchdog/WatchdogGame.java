package org.dynamisengine.games.debugwatchdog;

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
 * Debug Watchdog Basics — proves rule behavior, cooldowns, escalation, and flapping.
 *
 * <p>Four focused patterns demonstrate how watchdog rules behave under
 * different metric conditions:
 *
 * <ol>
 *   <li><b>Threshold Breach</b> — metric crosses warning, then error, then recovers</li>
 *   <li><b>Sustained Breach</b> — condition stays bad for many frames; cooldown prevents flood</li>
 *   <li><b>Escalation</b> — prolonged breach escalates WARNING -> ERROR -> CRITICAL</li>
 *   <li><b>Flapping</b> — metric oscillates around threshold, showing why cooldown matters</li>
 * </ol>
 *
 * <p>Controls:
 * <pre>
 *   1     — Threshold breach pattern
 *   2     — Sustained breach pattern
 *   3     — Escalation pattern
 *   4     — Flapping pattern
 *   R     — Reset to baseline
 *   P     — Pause/resume
 *   Tab   — Toggle overlay
 *   Esc   — Quit
 * </pre>
 */
public final class WatchdogGame implements WorldApplication {

    enum Pattern { BASELINE, THRESHOLD_BREACH, SUSTAINED_BREACH, ESCALATION, FLAPPING }

    // Input
    static final ActionId TOGGLE = new ActionId("toggle");
    static final ActionId PATTERN_1 = new ActionId("p1");
    static final ActionId PATTERN_2 = new ActionId("p2");
    static final ActionId PATTERN_3 = new ActionId("p3");
    static final ActionId PATTERN_4 = new ActionId("p4");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("watchdog");
    private static final int KEY_TAB = 258, KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52;
    private static final int KEY_R = 82, KEY_P = 80, KEY_ESC = 256;

    private final GlfwWindowSubsystem windowSub;
    private final InputWorldSubsystem inputSub;
    private final OpenGlTextRenderer textRenderer = new OpenGlTextRenderer();
    private OpenGlDebugOverlayRenderer overlayRenderer;

    private DebugSession session;
    private DebugViewSnapshotMapper mapper;
    private DebugOverlayBuilder builder;

    private Pattern pattern = Pattern.BASELINE;
    private float patternTime = 0f;
    private float totalTime = 0f;
    private long tick = 0;
    private boolean overlayVisible = true;
    private boolean paused = false;

    // The single metric under test
    private float testMetric = 5.0f;

    public WatchdogGame(GlfwWindowSubsystem w, InputWorldSubsystem i) {
        this.windowSub = w;
        this.inputSub = i;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(TOGGLE, List.of(new KeyBinding(KEY_TAB, 0)),
                        PATTERN_1, List.of(new KeyBinding(KEY_1, 0)),
                        PATTERN_2, List.of(new KeyBinding(KEY_2, 0)),
                        PATTERN_3, List.of(new KeyBinding(KEY_3, 0)),
                        PATTERN_4, List.of(new KeyBinding(KEY_4, 0)),
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

        System.out.println("=== Debug Watchdog Basics ===");
        System.out.println("Proving: rule behavior, cooldowns, escalation, flapping");
        System.out.println("1=threshold  2=sustained  3=escalation  4=flapping  R=reset  Esc=quit");
    }

    private void initSession() {
        session = new DebugSession();
        mapper = new DebugViewSnapshotMapper(session);
        builder = new DebugOverlayBuilder(new DebugOverlayOptions(true, true, true, 60, 16, 10, false));

        // Rule set designed to show different watchdog behaviors
        // Warning at 10, Error at 20, Critical at 30
        session.watchdog().addRule(new WatchdogRule(
            "test.warning", "test", "value",
            10.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "value > 10 (warning threshold)", 30));

        session.watchdog().addRule(new WatchdogRule(
            "test.error", "test", "value",
            20.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.ERROR, "value > 20 (error threshold)", 30));

        session.watchdog().addRule(new WatchdogRule(
            "test.critical", "test", "value",
            30.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.CRITICAL, "value > 30 (critical threshold)", 30));

        // A "no cooldown" rule to show what happens without anti-spam
        session.watchdog().addRule(new WatchdogRule(
            "test.noCooldown", "test", "value",
            15.0, WatchdogRule.Comparison.GREATER_THAN,
            DebugSeverity.WARNING, "value > 15 (NO cooldown!)", 0));

        pattern = Pattern.BASELINE;
        patternTime = 0f;
        totalTime = 0f;
        tick = 0;
        testMetric = 5.0f;
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE)) overlayVisible = !overlayVisible;
            if (frame.pressed(PAUSE)) paused = !paused;
            if (frame.pressed(RESET)) { initSession(); System.out.println("Reset."); }
            if (frame.pressed(PATTERN_1)) startPattern(Pattern.THRESHOLD_BREACH);
            if (frame.pressed(PATTERN_2)) startPattern(Pattern.SUSTAINED_BREACH);
            if (frame.pressed(PATTERN_3)) startPattern(Pattern.ESCALATION);
            if (frame.pressed(PATTERN_4)) startPattern(Pattern.FLAPPING);
        }

        if (!paused) {
            tick++;
            totalTime += dt;
            patternTime += dt;
            updateMetric(dt);
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

            // Status + threshold reference
            textRenderer.beginFrame(w, h);
            textRenderer.drawText(String.format(
                "Pattern: %s  Metric: %.1f  Time: %.1fs  Tick: %d  %s",
                pattern, testMetric, totalTime, tick, paused ? "[PAUSED]" : ""),
                10, h - 55, 2.0f, 0.8f, 0.8f, 0.4f, w, h);
            textRenderer.drawText(
                "Thresholds: WARNING>10  ERROR>20  CRITICAL>30  (noCooldown>15)",
                10, h - 38, 1.6f, 0.6f, 0.5f, 0.3f, w, h);
            textRenderer.drawText(
                "1=threshold  2=sustained  3=escalation  4=flapping  R=reset  Esc=quit",
                10, h - 20, 1.6f, 0.4f, 0.4f, 0.5f, w, h);
            textRenderer.endFrame();
        }

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        textRenderer.shutdown();
    }

    private void startPattern(Pattern p) {
        pattern = p;
        patternTime = 0f;
        testMetric = 5.0f;
        System.out.printf("[%.1fs] Pattern: %s%n", totalTime, p);
        session.submit(new DebugEvent(tick, System.currentTimeMillis(),
            "scenario", DebugCategory.ENGINE, DebugSeverity.INFO,
            "pattern.started", p.name()));
    }

    // --- Metric patterns ---

    private void updateMetric(float dt) {
        float noise = (float)(Math.random() - 0.5) * 0.3f;

        switch (pattern) {
            case BASELINE -> {
                testMetric = lerp(testMetric, 5f + noise, dt * 3f);
            }

            case THRESHOLD_BREACH -> {
                // Phase 1 (0-3s): ramp from 5 to 25 (crosses warning at 10, error at 20)
                // Phase 2 (3-5s): hold at ~25
                // Phase 3 (5-8s): recover back to 5
                if (patternTime < 3f) {
                    float target = 5f + (patternTime / 3f) * 20f;
                    testMetric = lerp(testMetric, target + noise, dt * 4f);
                } else if (patternTime < 5f) {
                    testMetric = lerp(testMetric, 25f + noise * 2, dt * 3f);
                } else if (patternTime < 8f) {
                    float recovery = (patternTime - 5f) / 3f;
                    testMetric = lerp(testMetric, 25f - recovery * 20f + noise, dt * 4f);
                } else {
                    testMetric = lerp(testMetric, 5f + noise, dt * 3f);
                    if (patternTime > 10f) {
                        pattern = Pattern.BASELINE;
                        System.out.printf("[%.1fs] Threshold breach complete, returning to baseline%n", totalTime);
                    }
                }
            }

            case SUSTAINED_BREACH -> {
                // Ramp to 18 (above warning, below error) and hold for 10 seconds
                // Shows cooldown preventing flood despite sustained breach
                if (patternTime < 2f) {
                    testMetric = lerp(testMetric, 5f + (patternTime / 2f) * 13f, dt * 4f);
                } else if (patternTime < 12f) {
                    // Hold at ~18 for 10 seconds — well above warning (10) and noCooldown (15)
                    testMetric = lerp(testMetric, 18f + noise * 2, dt * 3f);
                } else {
                    testMetric = lerp(testMetric, 5f + noise, dt * 3f);
                    if (patternTime > 15f) {
                        pattern = Pattern.BASELINE;
                        System.out.printf("[%.1fs] Sustained breach complete%n", totalTime);
                    }
                }
            }

            case ESCALATION -> {
                // Steadily climb through all three thresholds over 9 seconds
                // 0-3s: cross warning (10)
                // 3-6s: cross error (20)
                // 6-9s: cross critical (30)
                // 9-12s: hold at 35
                // 12-16s: recover
                if (patternTime < 9f) {
                    float target = 5f + (patternTime / 9f) * 30f; // 5 -> 35
                    testMetric = lerp(testMetric, target + noise, dt * 4f);
                } else if (patternTime < 12f) {
                    testMetric = lerp(testMetric, 35f + noise * 3, dt * 2f);
                } else if (patternTime < 16f) {
                    float recovery = (patternTime - 12f) / 4f;
                    testMetric = lerp(testMetric, 35f - recovery * 30f + noise, dt * 3f);
                } else {
                    testMetric = lerp(testMetric, 5f + noise, dt * 3f);
                    if (patternTime > 18f) {
                        pattern = Pattern.BASELINE;
                        System.out.printf("[%.1fs] Escalation complete%n", totalTime);
                    }
                }
            }

            case FLAPPING -> {
                // Oscillate around the warning threshold (10) with a 2-second period
                // This shows repeated crossing and how cooldown prevents excessive alerts
                float amplitude = 5f; // swings from 5 to 15
                float frequency = 0.5f; // 2-second period
                float center = 10f; // right at threshold
                testMetric = center + amplitude * (float) Math.sin(patternTime * frequency * 2 * Math.PI) + noise;

                if (patternTime > 15f) {
                    pattern = Pattern.BASELINE;
                    testMetric = 5f;
                    System.out.printf("[%.1fs] Flapping complete%n", totalTime);
                }
            }
        }

        testMetric = Math.max(0f, testMetric);
    }

    private Map<String, DebugSnapshot> buildSnapshots() {
        long now = System.currentTimeMillis();
        Map<String, DebugSnapshot> snapshots = new HashMap<>();

        // The test source with the single metric under observation
        snapshots.put("test", new DebugSnapshot(tick, now, "test", DebugCategory.CUSTOM,
            Map.of("value", (double) testMetric),
            Map.of(
                "aboveWarning", testMetric > 10,
                "aboveError", testMetric > 20,
                "aboveCritical", testMetric > 30
            ),
            pattern.name()
        ));

        return snapshots;
    }

    private static float lerp(float current, float target, float rate) {
        return current + (target - current) * Math.min(rate, 1f);
    }
}
