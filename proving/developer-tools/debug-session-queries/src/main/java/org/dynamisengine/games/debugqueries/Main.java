package org.dynamisengine.games.debugqueries;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis - Debug Session Queries", 1024, 768);
        var processor = SessionQueriesGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new SessionQueriesGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
