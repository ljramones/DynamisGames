package org.dynamisengine.games.uibasics;

/** UI state machine — separate from gameplay state. */
public enum UiMode {
    TITLE,      // Title screen: "Press Enter to Start"
    PLAYING,    // Active gameplay with HUD
    PAUSED,     // Pause overlay
    WON,        // Victory overlay
    LOST        // Defeat overlay
}
