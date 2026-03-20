package org.dynamisengine.games.animevents;

/**
 * A single keyframe in a transform animation channel.
 *
 * @param time  time in seconds from clip start
 * @param value the value at this keyframe
 */
public record Keyframe(float time, float value) {}
