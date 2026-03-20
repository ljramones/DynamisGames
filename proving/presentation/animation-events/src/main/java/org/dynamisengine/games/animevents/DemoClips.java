package org.dynamisengine.games.animevents;

import org.dynamisengine.animis.clip.AnimationEvent;
import org.dynamisengine.animis.transform.*;
import java.util.List;
import java.util.Map;

public final class DemoClips {
    private DemoClips() {}

    public static PropertyClip heroClipWithEvents() {
        float dur = 4.0f;
        return new PropertyClip("hero-events", dur, Map.of(
                "posY", new PropertyTrack("posY",
                        new PropertyKeyframe(0, 1.5f), new PropertyKeyframe(1, 2.5f),
                        new PropertyKeyframe(2, 1.5f), new PropertyKeyframe(3, 2.5f),
                        new PropertyKeyframe(4, 1.5f)),
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(4, (float)(2 * Math.PI))),
                "scale", new PropertyTrack("scale",
                        new PropertyKeyframe(0, 1.0f), new PropertyKeyframe(1, 1.15f),
                        new PropertyKeyframe(2, 1.0f), new PropertyKeyframe(3, 1.15f),
                        new PropertyKeyframe(4, 1.0f))
        ), List.of(
                new AnimationEvent("pulse", 0.5f / dur),
                new AnimationEvent("apex", 1.0f / dur),
                new AnimationEvent("accent", 2.0f / dur),
                new AnimationEvent("apex", 3.0f / dur)
        ));
    }

    public static PropertyClip orbitClip() {
        float dur = 6.0f;
        return new PropertyClip("orbit", dur, Map.of(
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(6, (float)(2 * Math.PI))),
                "posY", new PropertyTrack("posY",
                        new PropertyKeyframe(0, 0.5f), new PropertyKeyframe(1.5f, 1.0f),
                        new PropertyKeyframe(3, 0.5f), new PropertyKeyframe(4.5f, 1.0f),
                        new PropertyKeyframe(6, 0.5f))
        ));
    }
}
