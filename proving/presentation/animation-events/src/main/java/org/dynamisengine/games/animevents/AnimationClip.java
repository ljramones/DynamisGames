package org.dynamisengine.games.animevents;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Animation clip with transform channels AND event markers.
 */
public record AnimationClip(
        String name,
        float duration,
        Map<String, TransformChannel> channels,
        List<AnimationEvent> events
) {
    public float sample(String channelName, float time) {
        TransformChannel ch = channels.get(channelName);
        return ch != null ? ch.sample(time) : 0;
    }

    /** Hero clip with event markers at key moments. */
    public static AnimationClip heroClipWithEvents() {
        float dur = 4.0f;
        var channels = Map.of(
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 1.5f), new Keyframe(1, 2.5f),
                        new Keyframe(2, 1.5f), new Keyframe(3, 2.5f), new Keyframe(4, 1.5f)),
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0), new Keyframe(4, (float)(2 * Math.PI))),
                "scale", new TransformChannel("scale",
                        new Keyframe(0, 1.0f), new Keyframe(1, 1.15f),
                        new Keyframe(2, 1.0f), new Keyframe(3, 1.15f), new Keyframe(4, 1.0f))
        );
        var events = Arrays.asList(
                new AnimationEvent(0.5f, "pulse"),    // half-way up first bob
                new AnimationEvent(1.0f, "apex"),     // top of first bob
                new AnimationEvent(2.0f, "accent"),   // bottom of second bob
                new AnimationEvent(3.0f, "apex")      // top of second bob
        );
        return new AnimationClip("hero-events", dur, channels, events);
    }

    /** Simple orbit clip (no events). */
    public static AnimationClip orbitClip() {
        float dur = 6.0f;
        return new AnimationClip("orbit", dur, Map.of(
                "rotY", new TransformChannel("rotY",
                        new Keyframe(0, 0), new Keyframe(6, (float)(2 * Math.PI))),
                "posY", new TransformChannel("posY",
                        new Keyframe(0, 0.5f), new Keyframe(1.5f, 1.0f),
                        new Keyframe(3, 0.5f), new Keyframe(4.5f, 1.0f), new Keyframe(6, 0.5f))
        ), List.of());
    }
}
