package org.dynamisengine.games.arena;

/**
 * Tiny game state machine for the arena minigame.
 * Lives outside ECS — game progression is not entity state.
 */
public enum GameMode {
    READY,       // Press Space to start
    PLAYING,     // Active wave
    WAVE_CLEAR,  // Brief pause between waves
    WON,         // All waves cleared
    LOST         // Too many misses
}
