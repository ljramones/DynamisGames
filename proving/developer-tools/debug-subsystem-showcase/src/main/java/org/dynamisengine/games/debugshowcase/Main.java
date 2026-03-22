package org.dynamisengine.games.debugshowcase;

import org.dynamisengine.worldengine.api.WorldEngine;

public final class Main {
    public static void main(String[] args) {
        var windowSub = new WindowSubsystem("Dynamis - Debug Subsystem Showcase", 1280, 800);
        var processor = ShowcaseGame.createProcessor();
        var inputSub = new WindowInputSubsystem(windowSub, processor);

        WorldEngine.builder()
                .application(new ShowcaseGame(windowSub, inputSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .run();
    }
}
