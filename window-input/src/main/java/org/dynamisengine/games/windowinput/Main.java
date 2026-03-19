package org.dynamisengine.games.windowinput;

import org.dynamisengine.games.windowinput.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.windowinput.subsystem.WindowSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Window Input example.
 *
 * Opens a real GLFW window and demonstrates live keyboard/mouse input
 * flowing through DynamisWindow → DynamisInput → WorldEngine.
 */
public final class Main {

    public static void main(String[] args) {
        var windowSubsystem = new WindowSubsystem("Dynamis — Input Demo", 800, 600);
        DefaultInputProcessor processor = WindowInputBindings.createProcessor();
        var inputSubsystem = new WindowInputSubsystem(windowSubsystem, processor);

        WorldEngine.builder()
                .application(new WindowInputGame(windowSubsystem, inputSubsystem))
                .subsystem(windowSubsystem)
                .subsystem(inputSubsystem)
                .run();
    }
}
