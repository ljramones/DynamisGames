package org.dynamisengine.games.renderbasics;

import org.dynamisengine.games.renderbasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.renderbasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.renderbasics.subsystem.WindowSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — Rendering Basics", 800, 600);
        var processor = RenderBasicsGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new RenderBasicsGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
