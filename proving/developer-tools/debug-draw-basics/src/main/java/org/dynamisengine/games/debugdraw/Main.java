package org.dynamisengine.games.debugdraw;

import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis — Debug Draw Basics", 1024, 768);
        var processor = DebugDrawBasicsGame.createProcessor();
        var inputSub = new InputWorldSubsystem(windowSub::lastEvents, processor);

        WorldEngine.builder()
                .application(new DebugDrawBasicsGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
