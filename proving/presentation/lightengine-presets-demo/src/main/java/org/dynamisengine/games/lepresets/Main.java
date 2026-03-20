package org.dynamisengine.games.lepresets;

import org.dynamisengine.games.lepresets.subsystem.*;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis - LightEngine Presets Demo", 900, 650);
        var processor = PresetsGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new PresetsGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
