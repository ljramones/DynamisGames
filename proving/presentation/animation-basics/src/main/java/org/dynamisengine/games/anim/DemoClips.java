package org.dynamisengine.games.anim;

import org.dynamisengine.animis.transform.*;
import java.util.Map;

public final class DemoClips {
    private DemoClips() {}

    public static PropertyClip heroClip() {
        float dur = 4.0f;
        return new PropertyClip("hero", dur, Map.of(
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
