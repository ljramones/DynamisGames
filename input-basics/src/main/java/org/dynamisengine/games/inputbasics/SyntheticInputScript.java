package org.dynamisengine.games.inputbasics;

import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.InputEvent.InputAction;

import java.util.List;
import java.util.Map;

/**
 * Scripted synthetic input events for deterministic demonstration.
 *
 * Each tick has a list of events that simulate keyboard input.
 * This replaces a real window event loop for the headless proving module.
 */
final class SyntheticInputScript {

    private SyntheticInputScript() {}

    // GLFW key codes
    private static final int KEY_A     = 65;
    private static final int KEY_D     = 68;
    private static final int KEY_SPACE = 32;
    private static final int KEY_P     = 80;

    /**
     * Returns the scripted events for a given tick.
     * Demonstrates: tap, hold, double-tap, movement, and stop.
     */
    static List<InputEvent> eventsForTick(long tick) {
        return SCRIPT.getOrDefault(tick, List.of());
    }

    /** The last tick in the script. */
    static long lastTick() { return 100; }

    /**
     * Scripted input timeline:
     *
     * Tick  5: Press Space (start jump)
     * Tick  8: Release Space (quick tap → QUICK_JUMP gesture)
     * Tick 15: Press D (move right)
     * Tick 20: Release D
     * Tick 24: Press D (second tap → DASH_RIGHT gesture)
     * Tick 27: Release D
     * Tick 35: Press A (move left)
     * Tick 50: Release A (held 15 ticks)
     * Tick 55: Press Space (start long hold)
     * Tick 90: Release Space (held 35 ticks → CHARGE_JUMP gesture)
     * Tick 95: Press P (pause/quit signal)
     * Tick 96: Release P
     */
    private static final Map<Long, List<InputEvent>> SCRIPT = Map.ofEntries(
            // Quick jump tap
            Map.entry(5L,  List.of(key(KEY_SPACE, InputAction.PRESS))),
            Map.entry(8L,  List.of(key(KEY_SPACE, InputAction.RELEASE))),

            // Double-tap right (dash)
            Map.entry(15L, List.of(key(KEY_D, InputAction.PRESS))),
            Map.entry(20L, List.of(key(KEY_D, InputAction.RELEASE))),
            Map.entry(24L, List.of(key(KEY_D, InputAction.PRESS))),
            Map.entry(27L, List.of(key(KEY_D, InputAction.RELEASE))),

            // Move left (held)
            Map.entry(35L, List.of(key(KEY_A, InputAction.PRESS))),
            Map.entry(50L, List.of(key(KEY_A, InputAction.RELEASE))),

            // Charge jump (long hold)
            Map.entry(55L, List.of(key(KEY_SPACE, InputAction.PRESS))),
            Map.entry(90L, List.of(key(KEY_SPACE, InputAction.RELEASE))),

            // Pause/quit
            Map.entry(95L, List.of(key(KEY_P, InputAction.PRESS))),
            Map.entry(96L, List.of(key(KEY_P, InputAction.RELEASE)))
    );

    private static InputEvent.Key key(int keyCode, InputAction action) {
        return new InputEvent.Key(keyCode, keyCode, action, 0);
    }
}
