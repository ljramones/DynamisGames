package org.dynamisengine.games.debugplayer;

import org.dynamisengine.games.proving.OpenGlDebugOverlayRenderer;
import org.dynamisengine.games.proving.OpenGlTextRenderer;
import org.dynamisengine.games.proving.ProvingInputSubsystem;
import org.dynamisengine.games.proving.ProvingWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: debug-session-player <session.ndjson>");
            System.exit(1);
        }

        var windowSub = new ProvingWindowSubsystem("Dynamis - Session Player [" + args[0] + "]", 1024, 768);
        var processor = SessionPlayerGame.createProcessor();
        var inputSub = new ProvingInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new SessionPlayerGame(windowSub, inputSub, args[0]))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
