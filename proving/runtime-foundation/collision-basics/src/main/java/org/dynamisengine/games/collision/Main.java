package org.dynamisengine.games.collision;

import org.dynamisengine.games.collision.subsystem.*;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — Collision Basics", 800, 600);
        var processor = CollisionGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);
        var audioSub = new AudioSubsystem();

        WorldEngine.builder()
                .application(new CollisionGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
