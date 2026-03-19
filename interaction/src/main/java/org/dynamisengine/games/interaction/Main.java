package org.dynamisengine.games.interaction;

import org.dynamisengine.games.interaction.subsystem.AudioSubsystem;
import org.dynamisengine.games.interaction.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.interaction.subsystem.WindowSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Interaction Sandbox.
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — Interaction", 640, 480);
        var processor = InteractionGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new InteractionGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
