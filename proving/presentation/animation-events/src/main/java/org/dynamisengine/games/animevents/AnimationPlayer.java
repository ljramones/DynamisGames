package org.dynamisengine.games.animevents;

import java.util.ArrayList;
import java.util.List;

/**
 * Animation playback controller with event crossing detection.
 *
 * Key behavior:
 * - Events fire once when playback time crosses their timestamp
 * - Same event does not refire until the next loop
 * - On loop wrap, all events become eligible again
 * - On reset, event state clears
 * - Paused animation does not emit events
 */
public final class AnimationPlayer {

    private final AnimationClip clip;
    private float currentTime = 0;
    private float previousTime = 0;
    private boolean playing = true;
    private boolean looping = true;
    private float speed = 1.0f;

    // Track which events have fired in the current loop pass
    private final boolean[] eventFired;

    // Events fired this frame (cleared each update)
    private final List<AnimationEvent> pendingEvents = new ArrayList<>();

    public AnimationPlayer(AnimationClip clip) {
        this.clip = clip;
        this.eventFired = new boolean[clip.events().size()];
    }

    /**
     * Advance time and detect event crossings. Call once per frame.
     * After calling, use {@link #drainEvents()} to consume fired events.
     */
    public void update(float dt) {
        pendingEvents.clear();
        if (!playing) return;

        previousTime = currentTime;
        currentTime += dt * speed;

        boolean wrapped = false;
        if (looping) {
            if (currentTime >= clip.duration()) {
                currentTime -= clip.duration();
                wrapped = true;
            }
            while (currentTime < 0) currentTime += clip.duration();
        } else {
            currentTime = Math.min(currentTime, clip.duration());
        }

        // Reset fired flags on loop wrap
        if (wrapped) {
            for (int i = 0; i < eventFired.length; i++) eventFired[i] = false;
        }

        // Detect crossings
        List<AnimationEvent> events = clip.events();
        for (int i = 0; i < events.size(); i++) {
            if (eventFired[i]) continue;
            AnimationEvent evt = events.get(i);

            boolean crossed;
            if (wrapped) {
                // Wrapped: event fires if it's after previousTime OR before currentTime
                crossed = evt.time() > previousTime || evt.time() <= currentTime;
            } else {
                // Normal: event fires if it's in the (previousTime, currentTime] range
                crossed = evt.time() > previousTime && evt.time() <= currentTime;
            }

            if (crossed) {
                eventFired[i] = true;
                pendingEvents.add(evt);
            }
        }
    }

    /** Drain events fired this frame. */
    public List<AnimationEvent> drainEvents() {
        return List.copyOf(pendingEvents);
    }

    public float sample(String channel) { return clip.sample(channel, currentTime); }

    public void togglePause() { playing = !playing; }
    public void toggleLoop() { looping = !looping; }
    public void setSpeed(float s) { speed = s; }

    public void reset() {
        currentTime = 0;
        previousTime = 0;
        pendingEvents.clear();
        for (int i = 0; i < eventFired.length; i++) eventFired[i] = false;
    }

    public boolean isPlaying() { return playing; }
    public boolean isLooping() { return looping; }
    public float speed() { return speed; }
    public float currentTime() { return currentTime; }
    public float duration() { return clip.duration(); }
    public String clipName() { return clip.name(); }
}
