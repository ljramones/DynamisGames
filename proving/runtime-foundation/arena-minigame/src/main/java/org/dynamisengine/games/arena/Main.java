package org.dynamisengine.games.arena;

import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Arena Minigame", 800, 600);
        var processor = ArenaGame.createProcessor();
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new ArenaGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
