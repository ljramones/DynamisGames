package org.dynamisengine.games.animblend;

import org.dynamisengine.animis.transform.*;
import java.util.Map;

public final class DemoClips {
    private DemoClips() {}

    public static PropertyClip idle() {
        return new PropertyClip("idle", 4.0f, Map.of(
                "posY", new PropertyTrack("posY",
                        new PropertyKeyframe(0, 1.5f), new PropertyKeyframe(2, 1.8f),
                        new PropertyKeyframe(4, 1.5f)),
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(4, (float)(Math.PI * 0.5))),
                "scale", new PropertyTrack("scale",
                        new PropertyKeyframe(0, 1.0f), new PropertyKeyframe(2, 1.03f),
                        new PropertyKeyframe(4, 1.0f)),
                "posX", new PropertyTrack("posX",
                        new PropertyKeyframe(0, 0), new PropertyKeyframe(4, 0))
        ));
    }

    public static PropertyClip active() {
        return new PropertyClip("active", 2.0f, Map.of(
                "posY", new PropertyTrack("posY",
                        new PropertyKeyframe(0, 1.5f), new PropertyKeyframe(0.5f, 2.8f),
                        new PropertyKeyframe(1, 1.5f), new PropertyKeyframe(1.5f, 2.8f),
                        new PropertyKeyframe(2, 1.5f)),
                "rotY", new PropertyTrack("rotY",
                        new PropertyKeyframe(0, 0),
                        new PropertyKeyframe(2, (float)(2 * Math.PI))),
                "scale", new PropertyTrack("scale",
                        new PropertyKeyframe(0, 1.0f), new PropertyKeyframe(0.5f, 1.25f),
                        new PropertyKeyframe(1, 1.0f), new PropertyKeyframe(1.5f, 1.25f),
                        new PropertyKeyframe(2, 1.0f)),
                "posX", new PropertyTrack("posX",
                        new PropertyKeyframe(0, 0), new PropertyKeyframe(0.5f, 0.8f),
                        new PropertyKeyframe(1, 0), new PropertyKeyframe(1.5f, -0.8f),
                        new PropertyKeyframe(2, 0))
        ));
    }
}
