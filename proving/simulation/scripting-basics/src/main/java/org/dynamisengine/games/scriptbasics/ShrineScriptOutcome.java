package org.dynamisengine.games.scriptbasics;

/**
 * Structured outcome from shrine script evaluation.
 *
 * <p>Scripts decide; game systems apply. This enum represents the
 * possible decisions the scripting layer can make about a shrine
 * interaction.
 */
public enum ShrineScriptOutcome {
    /** Player has the key — open the door. */
    OPEN_DOOR,
    /** Player lacks the key — show locked feedback. */
    DENIED_NO_KEY,
    /** Door is already open — no action needed. */
    ALREADY_OPEN
}
