package org.dynamisengine.games.animevents;

import org.dynamisengine.games.animevents.subsystem.*;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis - Animation Events", 900, 650);
        var processor = AnimEventGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new AnimEventGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
