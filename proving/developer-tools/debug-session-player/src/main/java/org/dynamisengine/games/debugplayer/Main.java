package org.dynamisengine.games.debugplayer;

import org.dynamisengine.light.impl.opengl.OpenGlDebugOverlayRenderer;
import org.dynamisengine.light.impl.opengl.OpenGlTextRenderer;
import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: debug-session-player <session.ndjson>");
            System.exit(1);
        }

        var windowSub = new GlfwWindowSubsystem("Dynamis - Session Player [" + args[0] + "]", 1024, 768);
        var processor = SessionPlayerGame.createProcessor();
        var inputSub = new InputWorldSubsystem(windowSub::lastEvents, processor);

        WorldEngine.builder()
                .application(new SessionPlayerGame(windowSub, inputSub, args[0]))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
