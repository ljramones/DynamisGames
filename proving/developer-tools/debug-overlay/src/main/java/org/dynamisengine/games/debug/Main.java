package org.dynamisengine.games.debug;

import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Debug Overlay", 900, 700);
        var processor = DebugOverlayGame.createProcessor();
        var inputSub = new InputWorldSubsystem(windowSub::lastEvents, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new DebugOverlayGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
