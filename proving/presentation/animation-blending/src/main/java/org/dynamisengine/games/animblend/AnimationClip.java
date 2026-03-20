package org.dynamisengine.games.animblend;

import java.util.Map;

/**
 * Animation clip with named transform channels.
 */
public record AnimationClip(String name, float duration, Map<String, TransformChannel> channels) {

    public float sample(String channelName, float time) {
        TransformChannel ch = channels.get(channelName);
        return ch != null ? ch.sample(time) : 0;
    }

    /** Idle: gentle bob and slow rotation. */
    public static AnimationClip idle() {
        return new AnimationClip("idle", 4.0f, Map.of(
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 1.5f), new Keyframe(2, 1.8f), new Keyframe(4, 1.5f)),
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0), new Keyframe(4, (float)(Math.PI * 0.5))),
                "scale", new TransformChannel("scale",
                        new Keyframe(0, 1.0f), new Keyframe(2, 1.03f), new Keyframe(4, 1.0f)),
                "posX", new TransformChannel("posX",
                        new Keyframe(0, 0), new Keyframe(4, 0))
        ));
    }

    /** Active: energetic bounce, fast spin, scale pulse, side sway. */
    public static AnimationClip active() {
        return new AnimationClip("active", 2.0f, Map.of(
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 1.5f), new Keyframe(0.5f, 2.8f),
                        new Keyframe(1, 1.5f), new Keyframe(1.5f, 2.8f), new Keyframe(2, 1.5f)),
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0), new Keyframe(2, (float)(2 * Math.PI))),
                "scale", new TransformChannel("scale",
                        new Keyframe(0, 1.0f), new Keyframe(0.5f, 1.25f),
                        new Keyframe(1, 1.0f), new Keyframe(1.5f, 1.25f), new Keyframe(2, 1.0f)),
                "posX", new TransformChannel("posX",
                        new Keyframe(0, 0), new Keyframe(0.5f, 0.8f),
                        new Keyframe(1, 0), new Keyframe(1.5f, -0.8f), new Keyframe(2, 0))
        ));
    }
}
