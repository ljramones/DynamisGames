package org.dynamisengine.games.hello;

import org.dynamisengine.games.hello.subsystem.AudioSubsystem;
import org.dynamisengine.games.hello.subsystem.InputSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point. Registers Audio and Input subsystems, then runs the game.
 */
public final class Main {

    public static void main(String[] args) {
        WorldEngine.builder()
                .application(new HelloWorldGame())
                .subsystem(new AudioSubsystem())
                .subsystem(new InputSubsystem())
                .run();
    }
}
