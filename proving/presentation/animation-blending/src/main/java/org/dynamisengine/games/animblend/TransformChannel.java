package org.dynamisengine.games.animblend;

/**
 * A single animation channel driving one float value over time.
 * Linearly interpolates between keyframes.
 */
public final class TransformChannel {

    private final String name;
    private final Keyframe[] keyframes;

    public TransformChannel(String name, Keyframe... keyframes) {
        this.name = name;
        this.keyframes = keyframes;
    }

    public String name() { return name; }

    /**
     * Sample the channel at a given time. Linearly interpolates between keyframes.
     * Clamps to first/last keyframe outside range.
     */
    public float sample(float time) {
        if (keyframes.length == 0) return 0;
        if (keyframes.length == 1 || time <= keyframes[0].time()) return keyframes[0].value();
        if (time >= keyframes[keyframes.length - 1].time()) return keyframes[keyframes.length - 1].value();

        for (int i = 0; i < keyframes.length - 1; i++) {
            Keyframe a = keyframes[i], b = keyframes[i + 1];
            if (time >= a.time() && time <= b.time()) {
                float t = (time - a.time()) / (b.time() - a.time());
                return a.value() + t * (b.value() - a.value());
            }
        }
        return keyframes[keyframes.length - 1].value();
    }
}
