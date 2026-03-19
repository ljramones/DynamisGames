package org.dynamisengine.games.interaction;

/**
 * A spawned object in the interaction sandbox.
 *
 * Pulses have a position, age, and lifetime. They update each tick
 * and expire when their age exceeds their lifetime.
 */
public final class Pulse {

    private static long nextId = 1;

    public final long id;
    public final float x;
    public final float y;
    public final float lifetimeSeconds;
    private float ageSeconds = 0f;
    private boolean active = true;

    public Pulse(float x, float y, float lifetimeSeconds) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    /** Advance age by deltaSeconds. Returns true if still active after update. */
    public boolean update(float deltaSeconds) {
        if (!active) return false;
        ageSeconds += deltaSeconds;
        if (ageSeconds >= lifetimeSeconds) {
            active = false;
        }
        return active;
    }

    public float ageSeconds() { return ageSeconds; }
    public boolean isActive() { return active; }
    public float progress() { return Math.min(ageSeconds / lifetimeSeconds, 1f); }
}
