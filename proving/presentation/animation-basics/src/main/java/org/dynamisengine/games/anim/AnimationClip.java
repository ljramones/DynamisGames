package org.dynamisengine.games.anim;

import java.util.Map;

/**
 * A proving-level animation clip: named channels sampled over a duration.
 *
 * @param name     clip name
 * @param duration total duration in seconds
 * @param channels named transform channels (e.g. "posY", "rotY", "scale")
 */
public record AnimationClip(String name, float duration, Map<String, TransformChannel> channels) {

    public float sample(String channelName, float time) {
        TransformChannel ch = channels.get(channelName);
        return ch != null ? ch.sample(time) : 0;
    }

    /** Build a demo clip: bob up/down, rotate, pulse scale. */
    public static AnimationClip heroClip() {
        float dur = 4.0f;
        return new AnimationClip("hero", dur, Map.of(
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 1.5f),
                        new Keyframe(1, 2.5f),
                        new Keyframe(2, 1.5f),
                        new Keyframe(3, 2.5f),
                        new Keyframe(4, 1.5f)),
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0),
                        new Keyframe(4, (float)(2 * Math.PI))),
                "scale", new TransformChannel("scale",
                        new Keyframe(0, 1.0f),
                        new Keyframe(1, 1.15f),
                        new Keyframe(2, 1.0f),
                        new Keyframe(3, 1.15f),
                        new Keyframe(4, 1.0f))
        ));
    }

    /** Build a gentle orbit clip for accent objects. */
    public static AnimationClip orbitClip() {
        float dur = 6.0f;
        return new AnimationClip("orbit", dur, Map.of(
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0),
                        new Keyframe(6, (float)(2 * Math.PI))),
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 0.5f),
                        new Keyframe(1.5f, 1.0f),
                        new Keyframe(3, 0.5f),
                        new Keyframe(4.5f, 1.0f),
                        new Keyframe(6, 0.5f))
        ));
    }
}
