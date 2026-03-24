package org.dynamisengine.games.debugshowcase;

import org.dynamisengine.input.runtime.InputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis - Debug Subsystem Showcase", 1280, 800);
        var processor = ShowcaseGame.createProcessor();
        var inputSub = new InputWorldSubsystem(windowSub::lastEvents, processor);

        WorldEngine.builder()
                .application(new ShowcaseGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
