package org.dynamisengine.games.animblend;

/**
 * Crossfade blend controller between two animation clips.
 *
 * Manages two independent clip playback times and a blend weight
 * that transitions from source (weight=0) to target (weight=1)
 * over a fixed duration.
 */
public final class BlendState {

    private AnimationClip sourceClip;
    private AnimationClip targetClip;
    private float sourceTime = 0;
    private float targetTime = 0;
    private float blendWeight = 0; // 0 = fully source, 1 = fully target
    private float blendDuration = 0;
    private float blendElapsed = 0;
    private boolean blending = false;
    private boolean playing = true;
    private boolean looping = true;
    private float speed = 1.0f;

    public BlendState(AnimationClip initialClip) {
        this.sourceClip = initialClip;
        this.targetClip = initialClip;
        this.blendWeight = 1.0f; // start fully on the initial clip
    }

    /**
     * Begin a crossfade to a new clip over the given duration.
     * The current blended state becomes the source.
     */
    public void transitionTo(AnimationClip newClip, float duration) {
        if (blending) {
            // Already blending — collapse current blend to source
            sourceClip = targetClip;
            sourceTime = targetTime;
        }
        targetClip = newClip;
        targetTime = 0;
        blendDuration = duration;
        blendElapsed = 0;
        blendWeight = 0;
        blending = true;
    }

    public void update(float dt) {
        if (!playing) return;
        float scaledDt = dt * speed;

        // Advance both clip times
        sourceTime += scaledDt;
        targetTime += scaledDt;

        if (looping) {
            if (sourceClip.duration() > 0)
                while (sourceTime >= sourceClip.duration()) sourceTime -= sourceClip.duration();
            if (targetClip.duration() > 0)
                while (targetTime >= targetClip.duration()) targetTime -= targetClip.duration();
        } else {
            sourceTime = Math.min(sourceTime, sourceClip.duration());
            targetTime = Math.min(targetTime, targetClip.duration());
        }

        // Advance blend
        if (blending) {
            blendElapsed += dt; // blend duration is real-time, not speed-scaled
            blendWeight = Math.min(blendElapsed / blendDuration, 1.0f);
            if (blendWeight >= 1.0f) {
                // Blend complete — target becomes authoritative
                sourceClip = targetClip;
                sourceTime = targetTime;
                blendWeight = 1.0f;
                blending = false;
            }
        }
    }

    /**
     * Sample a channel with crossfade blending.
     * Returns weighted interpolation between source and target clip values.
     */
    public float sample(String channel) {
        float sourceVal = sourceClip.sample(channel, sourceTime);
        float targetVal = targetClip.sample(channel, targetTime);

        if (!blending || blendWeight >= 1.0f) return targetVal;
        if (blendWeight <= 0f) return sourceVal;

        // Linear interpolation
        return sourceVal * (1f - blendWeight) + targetVal * blendWeight;
    }

    public void togglePause() { playing = !playing; }
    public void toggleLoop() { looping = !looping; }
    public void setSpeed(float s) { speed = s; }

    public void reset() {
        sourceTime = 0; targetTime = 0;
        blendWeight = 1.0f; blending = false;
        blendElapsed = 0;
    }

    public boolean isPlaying() { return playing; }
    public boolean isLooping() { return looping; }
    public boolean isBlending() { return blending; }
    public float speed() { return speed; }
    public float blendWeight() { return blendWeight; }
    public String sourceClipName() { return sourceClip.name(); }
    public String targetClipName() { return targetClip.name(); }
    public float sourceTime() { return sourceTime; }
    public float targetTime() { return targetTime; }
}
