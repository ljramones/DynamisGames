package org.dynamisengine.games.inputbasics;

import org.dynamisengine.games.inputbasics.subsystem.InputSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Input Basics example.
 */
public final class Main {

    public static void main(String[] args) {
        WorldEngine.builder()
                .application(new InputBasicsGame())
                .subsystem(new InputSubsystem())
                .run();
    }
}
