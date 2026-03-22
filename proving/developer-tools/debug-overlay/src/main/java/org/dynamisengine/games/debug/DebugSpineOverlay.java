package org.dynamisengine.games.debug;

import org.dynamisengine.debug.api.DebugCategory;
import org.dynamisengine.debug.api.DebugSnapshot;
import org.dynamisengine.debug.api.DebugSeverity;
import org.dynamisengine.debug.api.WatchdogRule;
import org.dynamisengine.debug.api.event.DebugEvent;
import org.dynamisengine.debug.core.DebugSession;
import org.dynamisengine.ui.debug.builder.DebugOverlayBuilder;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
import org.dynamisengine.ui.debug.builder.DebugViewSnapshotMapper;
import org.dynamisengine.ui.debug.model.DebugOverlayPanel;
import org.dynamisengine.ui.debug.model.PanelRegion;
import org.dynamisengine.ui.debug.model.PanelSeverity;
import org.dynamisengine.ui.debug.model.RowSeverity;
import org.dynamisengine.worldengine.api.telemetry.EngineTelemetry;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.SubsystemTelemetry;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical wiring of the DynamisDebug spine into the overlay UI.
 *
 * <p>Demonstrates the production path:
 * <pre>
 *   WorldTelemetrySnapshot → DebugSession (snapshots) → DebugViewSnapshotMapper → DebugOverlayBuilder → panels
 * </pre>
 *
 * <p>This class owns no UI model types and no custom transformation logic.
 * It only wires DynamisDebug and DynamisUI together.
 */
final class DebugSpineOverlay {

    private final DebugSession session;
    private final DebugViewSnapshotMapper mapper;
    private final DebugOverlayBuilder builder;

    DebugSpineOverlay() {
        this.session = new DebugSession();
        this.mapper = new DebugViewSnapshotMapper(session);
        this.builder = new DebugOverlayBuilder();

        // Register sample watchdog rules
        session.watchdog().addRule(WatchdogRule.above(
            "engine.frameBudgetHigh", "worldengine", "budgetPercent",
            95.0, DebugSeverity.WARNING, "Frame budget > 95%"));
        session.watchdog().addRule(WatchdogRule.above(
            "ecs.entityCountHigh", "ecs", "entityCount",
            500.0, DebugSeverity.WARNING, "Entity count > 500"));
    }

    /**
     * Feeds the current frame's telemetry into the debug session and returns
     * ready-to-render overlay panels.
     *
     * @param telemetry the WorldEngine telemetry snapshot for this frame
     * @param tick      current engine tick
     * @param extraMetrics additional metrics to inject (e.g. particle count)
     * @return ordered list of overlay panels
     */
    List<DebugOverlayPanel> update(WorldTelemetrySnapshot telemetry, long tick,
                                    Map<String, Double> extraMetrics) {
        // Step 1: Convert WorldTelemetrySnapshot → DebugSnapshots
        Map<String, DebugSnapshot> frameSnapshots = convertToSnapshots(telemetry, tick, extraMetrics);

        // Step 2: Record in session history + evaluate watchdogs
        session.history().record(tick, frameSnapshots);
        List<DebugEvent> watchdogAlerts = session.watchdog().evaluate(tick, frameSnapshots);
        for (var alert : watchdogAlerts) {
            session.submit(alert);
        }

        // Step 3: Map to UI contract
        DebugViewSnapshot viewSnapshot = mapper.mapFromFrame(tick, frameSnapshots);

        // Step 4: Build overlay panels
        return builder.buildAll(viewSnapshot);
    }

    DebugSession session() { return session; }

    // --- WorldTelemetrySnapshot → DebugSnapshot conversion ---

    private Map<String, DebugSnapshot> convertToSnapshots(
            WorldTelemetrySnapshot telemetry, long tick,
            Map<String, Double> extraMetrics) {

        Map<String, DebugSnapshot> snapshots = new HashMap<>();
        long now = System.currentTimeMillis();

        // Engine telemetry
        EngineTelemetry eng = telemetry != null ? telemetry.engine() : null;
        if (eng != null) {
            snapshots.put("worldengine", new DebugSnapshot(
                tick, now, "worldengine", DebugCategory.ENGINE,
                Map.of(
                    "frameTimeMs", (double) eng.lastTickDurationMs(),
                    "avgFrameMs", (double) eng.avgTickDurationMs(),
                    "maxFrameMs", (double) eng.maxTickDurationMs(),
                    "budgetPercent", (double) eng.budgetPercent(),
                    "tickRate", (double) eng.tickRate(),
                    "uptimeSeconds", (double) eng.uptimeSeconds()
                ),
                Map.of(),
                eng.state().name()
            ));
        }

        // Subsystem health → snapshots
        if (telemetry != null) {
            for (var entry : telemetry.subsystems().entrySet()) {
                String name = entry.getKey();
                SubsystemTelemetry sub = entry.getValue();
                SubsystemHealth health = sub.health();

                DebugCategory category = categorizeSub(name);
                Map<String, Double> metrics = new HashMap<>();
                metrics.put("lastTick", (double) health.lastUpdateTick());

                Map<String, Boolean> flags = new HashMap<>();
                flags.put("healthy", health.state() == SubsystemHealth.HealthState.HEALTHY);
                if (health.state() == SubsystemHealth.HealthState.DEGRADED) flags.put("degraded", true);
                if (health.state() == SubsystemHealth.HealthState.FAULTED) flags.put("faulted", true);

                snapshots.put(name.toLowerCase(), new DebugSnapshot(
                    tick, now, name.toLowerCase(), category,
                    Map.copyOf(metrics), Map.copyOf(flags),
                    health.state().name()
                ));
            }
        }

        // Extra metrics (e.g. from ECS/game code)
        if (extraMetrics != null && !extraMetrics.isEmpty()) {
            snapshots.put("ecs", new DebugSnapshot(
                tick, now, "ecs", DebugCategory.ECS,
                Map.copyOf(extraMetrics), Map.of(), ""
            ));
        }

        return snapshots;
    }

    private static DebugCategory categorizeSub(String name) {
        return switch (name.toLowerCase()) {
            case "audio" -> DebugCategory.AUDIO;
            case "input" -> DebugCategory.INPUT;
            case "window" -> DebugCategory.UI;
            default -> DebugCategory.ENGINE;
        };
    }
}
