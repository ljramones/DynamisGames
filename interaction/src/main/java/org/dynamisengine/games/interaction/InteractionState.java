package org.dynamisengine.games.interaction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simple game state for the interaction sandbox.
 *
 * Tracks cursor position and active pulses. This is the canonical example
 * of how gameplay state should be structured in Dynamis: a plain object
 * that the update loop mutates deterministically.
 */
public final class InteractionState {

    public float cursorX = 0f;
    public float cursorY = 0f;

    private final List<Pulse> pulses = new ArrayList<>();
    private final List<Pulse> justExpired = new ArrayList<>();
    private long totalSpawned = 0;
    private long totalExpired = 0;

    /** Move cursor by delta. */
    public void moveCursor(float dx, float dy) {
        cursorX += dx;
        cursorY += dy;
    }

    /** Spawn a pulse at the current cursor position. */
    public Pulse spawn(float lifetimeSeconds) {
        Pulse p = new Pulse(cursorX, cursorY, lifetimeSeconds);
        pulses.add(p);
        totalSpawned++;
        return p;
    }

    /** Update all pulses. Returns list of pulses that expired THIS tick. */
    public List<Pulse> update(float deltaSeconds) {
        justExpired.clear();
        Iterator<Pulse> it = pulses.iterator();
        while (it.hasNext()) {
            Pulse p = it.next();
            boolean alive = p.update(deltaSeconds);
            if (!alive) {
                justExpired.add(p);
                it.remove();
                totalExpired++;
            }
        }
        return justExpired;
    }

    /** Reset to initial state. */
    public void reset() {
        cursorX = 0f;
        cursorY = 0f;
        pulses.clear();
        justExpired.clear();
        totalSpawned = 0;
        totalExpired = 0;
    }

    public int activeCount() { return pulses.size(); }
    public long totalSpawned() { return totalSpawned; }
    public long totalExpired() { return totalExpired; }
    public List<Pulse> activePulses() { return pulses; }
}
