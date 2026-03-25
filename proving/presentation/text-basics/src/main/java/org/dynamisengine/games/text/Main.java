package org.dynamisengine.games.text;

import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Text Basics", 800, 600);
        var processor = TextGame.createProcessor();
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new TextGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
