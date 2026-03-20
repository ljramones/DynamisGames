package org.dynamisengine.games.hello.subsystem;

import org.dynamisengine.input.core.InputDeviceManager;
import org.dynamisengine.input.core.InputTelemetry;
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
 * Bridges DynamisInput's real runtime into the WorldEngine subsystem contract.
 *
 * Creates an InputDeviceManager with 4 player slots and provides real InputTelemetry.
 */
public final class InputSubsystem implements WorldSubsystem {

    private InputDeviceManager deviceManager;
    private volatile boolean initialized = false;
    private volatile long lastTick = -1;

    @Override public String name() { return WorldTelemetrySnapshot.INPUT; }
    @Override public Set<String> dependencies() { return Set.of(); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        this.deviceManager = new InputDeviceManager(4);
        initialized = true;
    }

    @Override public void start() {}

    @Override
    public void tick(long tick, float dt) {
        lastTick = tick;
        if (deviceManager != null) {
            deviceManager.beginTick();
        }
    }

    @Override public void stop() {}

    @Override
    public void shutdown() {
        initialized = false;
    }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        return SubsystemHealth.healthy(name(), lastTick);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        if (deviceManager != null) {
            deviceManager.recordSnapshot();
            return Optional.of(deviceManager.captureTelemetry(0));
        }
        return Optional.empty();
    }

    public InputDeviceManager deviceManager() { return deviceManager; }
}
