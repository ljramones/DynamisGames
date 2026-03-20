package org.dynamisengine.games.interaction;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.dsp.SoftwareMixer;
import org.dynamisengine.audio.procedural.*;

/**
 * Audio feedback for interaction events.
 * Uses the canonical path: SynthVoice → ProceduralAudioAsset → QuickPlayback.
 */
public final class InteractionAudio {

    private static final float SR = AcousticConstants.SAMPLE_RATE;
    private final SoftwareMixer mixer;

    public InteractionAudio(SoftwareMixer mixer) {
        this.mixer = mixer;
    }

    /** Bright pop on spawn — 660 Hz, short. */
    public void onSpawn() {
        var osc = new SineOscillator(660f, 0.2f, SR);
        var env = new Envelope(0.003f, 0.06f, 0f, 0.02f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(660f, 0.2f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
    }

    /** Soft descending tone on expire — 330 Hz, gentle. */
    public void onExpire() {
        var osc = new SineOscillator(330f, 0.1f, SR);
        var env = new Envelope(0.01f, 0.1f, 0f, 0.05f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(330f, 0.1f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
    }

    /** Subtle tick when cursor starts moving — 1200 Hz, very short. */
    public void onMove() {
        var osc = new SineOscillator(1200f, 0.08f, SR);
        var env = new Envelope(0.001f, 0.015f, 0f, 0.005f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(1200f, 0.08f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
    }

    /** Short click on reset. */
    public void onReset() {
        var osc = new SineOscillator(440f, 0.15f, SR);
        var env = new Envelope(0.001f, 0.02f, 0f, 0.01f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(440f, 0.15f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
    }
}
