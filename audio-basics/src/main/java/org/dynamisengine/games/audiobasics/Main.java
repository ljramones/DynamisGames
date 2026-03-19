package org.dynamisengine.games.audiobasics;

import org.dynamisengine.games.audiobasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.audiobasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.audiobasics.subsystem.WindowSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Audio Basics example.
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — Audio Basics", 640, 480);
        var processor = AudioBasicsGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new AudioBasicsGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
