package org.dynamisengine.games.audiobasics;

import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Audio Basics example.
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Audio Basics", 640, 480);
        var processor = AudioBasicsGame.createProcessor();
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new AudioBasicsGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
