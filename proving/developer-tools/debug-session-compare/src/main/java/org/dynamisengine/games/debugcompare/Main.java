package org.dynamisengine.games.debugcompare;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: debug-session-compare <baseline.ndjson> <regression.ndjson>");
            System.exit(1);
        }

        var windowSub = new WindowSubsystem("Dynamis - Session Compare", 1400, 800);
        var processor = SessionCompareGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new SessionCompareGame(windowSub, inputSub, args[0], args[1]))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
