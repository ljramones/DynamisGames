package org.dynamisengine.games.hello;

import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

/**
 * The simplest possible Dynamis game.
 *
 * Demonstrates the WorldEngine facade: initialize, tick, shutdown.
 * This is what a game developer writes. Everything else is handled
 * by the engine.
 */
public final class HelloWorldGame implements WorldApplication {

    @Override
    public void initialize(GameContext context) {
        System.out.println("[HelloWorld] Initialized!");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        if (tick % 20 == 0) {
            System.out.printf("[HelloWorld] Tick %d | elapsed=%.2fs | dt=%.4fs%n",
                    tick, context.elapsedSeconds(), deltaSeconds);
        }

        if (tick >= 120) {
            System.out.println("[HelloWorld] Reached tick 120. Stopping.");
            context.requestStop();
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[HelloWorld] Shutdown complete. Final tick: " + context.tick());
    }
}
