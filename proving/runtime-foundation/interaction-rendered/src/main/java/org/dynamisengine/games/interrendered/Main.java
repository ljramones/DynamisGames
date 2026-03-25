package org.dynamisengine.games.interrendered;

import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Interaction Rendered", 800, 600);
        var processor = InteractionRenderedGame.createProcessor();
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new InteractionRenderedGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
