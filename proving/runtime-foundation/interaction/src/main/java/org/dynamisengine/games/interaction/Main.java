package org.dynamisengine.games.interaction;

import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Interaction Sandbox.
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Interaction", 640, 480);
        var processor = InteractionGame.createProcessor();
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new InteractionGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
