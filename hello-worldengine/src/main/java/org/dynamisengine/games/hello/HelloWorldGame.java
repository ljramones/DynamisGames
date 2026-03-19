package org.dynamisengine.games.hello;

import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;

/**
 * The simplest possible Dynamis game.
 *
 * Demonstrates WorldEngine with Audio and Input subsystems registered.
 */
public final class HelloWorldGame implements WorldApplication {

    @Override
    public void initialize(GameContext context) {
        System.out.println("[HelloWorld] Initialized!");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        if (tick % 30 == 0) {
            WorldTelemetrySnapshot t = context.telemetry();
            if (t != null) {
                System.out.println("[Telemetry] " + t.statusLine());
            }
        }

        if (tick >= 120) {
            WorldTelemetrySnapshot t = context.telemetry();
            if (t != null) {
                System.out.println();
                System.out.println(t.detailedReport());
            }
            System.out.println("[HelloWorld] Reached tick 120. Stopping.");
            context.requestStop();
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[HelloWorld] Shutdown complete.");
    }
}
