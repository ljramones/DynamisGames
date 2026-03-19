package org.dynamisengine.games.audiobasics;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.dsp.SoftwareMixer;
import org.dynamisengine.audio.procedural.*;

/**
 * Produces distinct procedural sounds for the audio basics demo.
 *
 * Each sound creates a new SynthVoice with its own oscillator and envelope,
 * wraps it in a ProceduralAudioAsset, and plays through QuickPlayback.
 * Every playback instance has its own state — no shared phase.
 */
public final class DemoSoundBank {

    private static final float SR = AcousticConstants.SAMPLE_RATE;

    private final SoftwareMixer mixer;
    private SynthVoice droneVoice;
    private boolean droneActive = false;

    public DemoSoundBank(SoftwareMixer mixer) {
        this.mixer = mixer;
    }

    /** Short bright blip at 880 Hz. Quick attack, fast decay, no sustain. */
    public void playBlip() {
        var osc = new SineOscillator(880f, 0.25f, SR);
        var env = new Envelope(0.005f, 0.08f, 0f, 0.02f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(880f, 0.25f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
        System.out.println("  [Sound] Blip (880 Hz)");
    }

    /** Low thump at 110 Hz. Punchy attack, medium decay, some sustain. */
    public void playThump() {
        var osc = new SineOscillator(110f, 0.4f, SR);
        var env = new Envelope(0.002f, 0.15f, 0.1f, 0.2f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(110f, 0.4f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
        System.out.println("  [Sound] Thump (110 Hz)");
    }

    /** Bright chirp at 1320 Hz. Very short, sparkly. */
    public void playChirp() {
        var osc = new SineOscillator(1320f, 0.15f, SR);
        var env = new Envelope(0.001f, 0.04f, 0f, 0.01f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(1320f, 0.15f);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
        System.out.println("  [Sound] Chirp (1320 Hz)");
    }

    /** Toggle a sustained 220 Hz drone on/off. */
    public void toggleDrone() {
        if (droneActive && droneVoice != null) {
            droneVoice.noteOff();
            droneActive = false;
            System.out.println("  [Sound] Drone OFF");
        } else {
            var osc = new SineOscillator(220f, 0.2f, SR);
            var env = new Envelope(0.1f, 0.2f, 0.8f, 1.0f, SR);
            droneVoice = new SynthVoice(osc, env);
            droneVoice.noteOn(220f, 0.2f);
            QuickPlayback.play(mixer, new ProceduralAudioAsset(droneVoice));
            droneActive = true;
            System.out.println("  [Sound] Drone ON (220 Hz)");
        }
    }

    public boolean isDroneActive() { return droneActive; }
}
