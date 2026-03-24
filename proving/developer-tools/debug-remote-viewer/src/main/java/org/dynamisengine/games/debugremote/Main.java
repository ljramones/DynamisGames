package org.dynamisengine.games.debugremote;

import org.dynamisengine.games.proving.OpenGlDebugOverlayRenderer;
import org.dynamisengine.games.proving.OpenGlTextRenderer;
import org.dynamisengine.games.proving.ProvingInputSubsystem;
import org.dynamisengine.games.proving.ProvingWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9876;

        var windowSub = new ProvingWindowSubsystem("Dynamis - Remote Debug Viewer [" + host + ":" + port + "]", 1024, 768);
        var processor = RemoteViewerGame.createProcessor();
        var inputSub = new ProvingInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new RemoteViewerGame(windowSub, inputSub, host, port))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
