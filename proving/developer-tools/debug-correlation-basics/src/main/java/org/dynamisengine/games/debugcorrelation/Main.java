package org.dynamisengine.games.debugcorrelation;

import org.dynamisengine.games.proving.OpenGlDebugOverlayRenderer;
import org.dynamisengine.games.proving.OpenGlTextRenderer;
import org.dynamisengine.games.proving.ProvingInputSubsystem;
import org.dynamisengine.games.proving.ProvingWindowSubsystem;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new ProvingWindowSubsystem("Dynamis - Debug Correlation Basics", 1024, 768);
        var processor = CorrelationGame.createProcessor();
        var inputSub = new ProvingInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new CorrelationGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
