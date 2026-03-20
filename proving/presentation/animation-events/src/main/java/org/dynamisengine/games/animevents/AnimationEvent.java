package org.dynamisengine.games.animevents;

/**
 * A time-stamped event marker on an animation timeline.
 *
 * @param time      time in seconds from clip start
 * @param name      event name (e.g. "pulse", "accent", "flash")
 */
public record AnimationEvent(float time, String name) implements Comparable<AnimationEvent> {
    @Override
    public int compareTo(AnimationEvent o) {
        return Float.compare(this.time, o.time);
    }
}
