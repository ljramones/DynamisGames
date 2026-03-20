package org.dynamisengine.games.anim;

/**
 * Proving-level animation playback controller.
 *
 * Advances time, handles looping, pause, reset, speed.
 */
public final class AnimationPlayer {

    private final AnimationClip clip;
    private float currentTime = 0;
    private boolean playing = true;
    private boolean looping = true;
    private float speed = 1.0f;

    public AnimationPlayer(AnimationClip clip) {
        this.clip = clip;
    }

    public void update(float dt) {
        if (!playing) return;
        currentTime += dt * speed;
        if (looping) {
            while (currentTime >= clip.duration()) currentTime -= clip.duration();
            while (currentTime < 0) currentTime += clip.duration();
        } else {
            currentTime = Math.min(currentTime, clip.duration());
        }
    }

    public float sample(String channel) {
        return clip.sample(channel, currentTime);
    }

    public void togglePause() { playing = !playing; }
    public void reset() { currentTime = 0; }
    public void setSpeed(float s) { speed = s; }
    public void toggleLoop() { looping = !looping; }

    public boolean isPlaying() { return playing; }
    public boolean isLooping() { return looping; }
    public float speed() { return speed; }
    public float currentTime() { return currentTime; }
    public float duration() { return clip.duration(); }
    public String clipName() { return clip.name(); }
}
