package org.dynamisengine.games.debugdraw;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis — Debug Draw Basics", 1024, 768);
        var processor = DebugDrawBasicsGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new DebugDrawBasicsGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
