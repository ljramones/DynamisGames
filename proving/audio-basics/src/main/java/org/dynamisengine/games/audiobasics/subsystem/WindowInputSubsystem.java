package org.dynamisengine.games.audiobasics.subsystem;

import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.core.InputDeviceManager;
import org.dynamisengine.input.core.InputTelemetry;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.WindowEvents;
import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;
import org.dynamisengine.input.api.frame.InputFrame;

import java.util.Optional;
import java.util.Set;

/**
 * Input subsystem that reads real events from WindowSubsystem.
 *
 * Depends on WindowSubsystem (must tick after it to get fresh events).
 * Feeds real InputEvents into the InputProcessor each tick.
 */
public final class WindowInputSubsystem implements WorldSubsystem {

    private final WindowSubsystem windowSubsystem;
    private final DefaultInputProcessor processor;
    private final InputDeviceManager deviceManager;
    private volatile boolean initialized = false;
    private volatile long lastTick = -1;
    private volatile InputFrame lastFrame;

    public WindowInputSubsystem(WindowSubsystem windowSubsystem,
                                 DefaultInputProcessor processor) {
        this.windowSubsystem = windowSubsystem;
        this.processor = processor;
        this.deviceManager = new InputDeviceManager(4);
    }

    @Override public String name() { return WorldTelemetrySnapshot.INPUT; }
    @Override public Set<String> dependencies() { return Set.of("Window"); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        initialized = true;
    }

    @Override public void start() {}

    @Override
    public void tick(long tick, float deltaSeconds) throws DynamisTickException {
        lastTick = tick;
        deviceManager.beginTick();

        // Feed real events from the window
        WindowEvents events = windowSubsystem.lastEvents();
        for (InputEvent event : events.inputEvents()) {
            deviceManager.processEvent(event);
            processor.feed(event, tick);
        }

        // Produce the input frame
        lastFrame = processor.snapshot(tick);
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
        if (initialized) {
            deviceManager.recordSnapshot();
            return Optional.of(deviceManager.captureTelemetry(0));
        }
        return Optional.empty();
    }

    /** The most recent input frame. */
    public InputFrame lastFrame() { return lastFrame; }

    /** The input processor for gesture evaluation. */
    public DefaultInputProcessor processor() { return processor; }
}
