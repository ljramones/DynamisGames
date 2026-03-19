package org.dynamisengine.games.hello.subsystem;

import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;

import java.util.Optional;
import java.util.Set;

/**
 * Adapts DynamisInput to the WorldSubsystem contract.
 *
 * For the proving module, Input initializes in basic mode (keyboard + mouse).
 * Full integration will wire InputRuntime and InputDeviceManager.
 */
public final class InputSubsystem implements WorldSubsystem {

    private volatile boolean initialized = false;
    private volatile long lastTick = -1;

    @Override public String name() { return WorldTelemetrySnapshot.INPUT; }
    @Override public Set<String> dependencies() { return Set.of(); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        initialized = true;
    }

    @Override public void start() {}

    @Override
    public void tick(long tick, float dt) {
        lastTick = tick;
    }

    @Override public void stop() {}
    @Override public void shutdown() { initialized = false; }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        return SubsystemHealth.healthy(name(), lastTick);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        // Will return InputTelemetry when fully wired
        return Optional.empty();
    }
}
