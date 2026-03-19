package org.dynamisengine.games.hello;

import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point. This is the entire launcher.
 */
public final class Main {

    public static void main(String[] args) {
        WorldEngine.run(new HelloWorldGame());
    }
}
