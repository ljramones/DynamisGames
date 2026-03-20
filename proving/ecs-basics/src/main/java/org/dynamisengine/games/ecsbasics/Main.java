package org.dynamisengine.games.ecsbasics;

import org.dynamisengine.games.ecsbasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.ecsbasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.ecsbasics.subsystem.WindowSubsystem;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — ECS Basics", 640, 480);
        var processor = EcsBasicsGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new EcsBasicsGame(windowSub, inputSub, audioSub, processor))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
