package org.dynamisengine.games.animsync;

import org.dynamisengine.animis.clip.AnimationEvent;
import org.dynamisengine.animis.transform.*;

import java.util.List;
import java.util.Map;

/**
 * Demo clips for the gameplay-sync module.
 */
public final class DemoClips {
    private DemoClips() {}

    /**
     * Strike clip: windup → hit → recover.
     * Duration 1.2s. Non-looping action.
     *
     * Events at key gameplay moments:
     * - windup at 0.1s (normalized 0.083)
     * - hit at 0.5s (normalized 0.417) — THE gameplay moment
     * - recover at 0.9s (normalized 0.75)
     */
    public static PropertyClip strikeClip() {
        float dur = 1.2f;
        return new PropertyClip("strike", dur, Map.of(
                // Pull back, lunge forward, return
                "posZ", new PropertyTrack("posZ",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(0.2f, -0.5f),  // windup pullback
                        new PropertyKeyframe(0.5f, 1.2f),   // strike forward
                        new PropertyKeyframe(0.8f, 0.3f),   // overshoot
                        new PropertyKeyframe(1.2f, 0)),      // return
                // Tilt forward during strike
                "rotX", new PropertyTrack("rotX",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(0.2f, -0.3f),   // lean back
                        new PropertyKeyframe(0.5f, 0.5f),    // lean forward
                        new PropertyKeyframe(1.2f, 0)),       // return
                // Scale pulse on hit
                "scale", new PropertyTrack("scale",
                        new PropertyKeyframe(0, 1.0f),
                        new PropertyKeyframe(0.4f, 0.9f),    // compress
                        new PropertyKeyframe(0.5f, 1.3f),    // expand on hit
                        new PropertyKeyframe(0.7f, 1.0f),
                        new PropertyKeyframe(1.2f, 1.0f)),
                // Rotation flourish
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(0.5f, 0.8f),
                        new PropertyKeyframe(1.2f, 0))
        ), List.of(
                new AnimationEvent("windup", 0.1f / dur),
                new AnimationEvent("hit", 0.5f / dur),
                new AnimationEvent("recover", 0.9f / dur)
        ));
    }

    /** Idle: gentle bob while waiting. */
    public static PropertyClip idleClip() {
        return new PropertyClip("idle", 3.0f, Map.of(
                "posZ", new PropertyTrack("posZ",
                        new PropertyKeyframe(0, 0), new PropertyKeyframe(3, 0)),
                "rotX", new PropertyTrack("rotX",
                        new PropertyKeyframe(0, 0), new PropertyKeyframe(3, 0)),
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(3, (float)(Math.PI * 0.3))),
                "scale", new PropertyTrack("scale",
                        new PropertyKeyframe(0, 1.0f), new PropertyKeyframe(1.5f, 1.05f),
                        new PropertyKeyframe(3, 1.0f))
        ));
    }
}
