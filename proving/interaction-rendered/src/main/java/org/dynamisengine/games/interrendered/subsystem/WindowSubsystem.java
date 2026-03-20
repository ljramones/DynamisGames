package org.dynamisengine.games.interrendered.subsystem;

import org.dynamisengine.window.api.*;
import org.dynamisengine.window.glfw.GlfwWindowSystem;
import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;

import java.util.Optional;
import java.util.Set;

/**
 * Subsystem adapter for DynamisWindow (GLFW).
 *
 * Owns the GLFW window lifecycle. Polls events each tick and makes them
 * available for the InputSubsystem to consume.
 */
public final class WindowSubsystem implements WorldSubsystem {

    private GlfwWindowSystem windowSystem;
    private Window window;
    private volatile WindowEvents lastEvents = WindowEvents.empty();
    private volatile boolean closeRequested = false;
    private volatile boolean initialized = false;

    private final String title;
    private final int width;
    private final int height;

    public WindowSubsystem(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public WindowSubsystem() {
        this("Dynamis Window Input", 800, 600);
    }

    @Override public String name() { return "Window"; }
    @Override public Set<String> dependencies() { return Set.of(); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        windowSystem = new GlfwWindowSystem();
        window = windowSystem.create(new WindowConfig(
                title, width, height, false, true, BackendHint.OPENGL));
        initialized = true;
    }

    @Override public void start() {}

    @Override
    public void tick(long tick, float deltaSeconds) throws DynamisTickException {
        if (window != null) {
            lastEvents = window.pollEvents();
            if (window.shouldClose()) {
                closeRequested = true;
            }
        }
    }

    @Override
    public void stop() {}

    @Override
    public void shutdown() throws DynamisShutdownException {
        if (window != null) {
            window.close();
            window = null;
        }
        if (windowSystem != null) {
            windowSystem.shutdown();
            windowSystem = null;
        }
        initialized = false;
    }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        return SubsystemHealth.healthy(name(), 0);
    }

    @Override
    public Optional<Object> captureTelemetry() { return Optional.empty(); }

    /** Last polled events — consumed by InputSubsystem each tick. */
    public WindowEvents lastEvents() { return lastEvents; }

    /** True if the window close button was clicked. */
    public boolean isCloseRequested() { return closeRequested; }

    /** The window handle, for subsystems that need it. */
    public Window window() { return window; }
}
