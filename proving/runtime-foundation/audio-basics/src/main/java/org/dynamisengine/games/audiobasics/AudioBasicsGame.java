package org.dynamisengine.games.audiobasics;

import org.dynamisengine.games.audiobasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.audiobasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.audiobasics.subsystem.WindowSubsystem;
import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.AxisId;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.bind.KeyBinding;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;
import org.dynamisengine.worldengine.api.telemetry.SubsystemTelemetry;
import org.dynamisengine.audio.dsp.device.AudioTelemetry;

import java.util.List;
import java.util.Map;

/**
 * Interactive audio playback demo.
 *
 * Opens a window. Press keys to trigger procedural sounds through the
 * full engine audio pipeline: SynthVoice → ProceduralAudioAsset →
 * VoicePool → SoftwareMixer → CoreAudio.
 */
public final class AudioBasicsGame implements WorldApplication {

    // Actions
    private static final ActionId BLIP   = new ActionId("blip");
    private static final ActionId THUMP  = new ActionId("thump");
    private static final ActionId CHIRP  = new ActionId("chirp");
    private static final ActionId DRONE  = new ActionId("drone");
    private static final ActionId QUIT   = new ActionId("quit");
    private static final ContextId CTX   = new ContextId("audio");

    // GLFW key codes
    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52;
    private static final int KEY_ESC = 256;

    private final WindowSubsystem windowSubsystem;
    private final WindowInputSubsystem inputSubsystem;
    private final AudioSubsystem audioSubsystem;
    private final DefaultInputProcessor processor;
    private DemoSoundBank soundBank;

    public AudioBasicsGame(WindowSubsystem windowSub, WindowInputSubsystem inputSub,
                           AudioSubsystem audioSub, DefaultInputProcessor processor) {
        this.windowSubsystem = windowSub;
        this.inputSubsystem = inputSub;
        this.audioSubsystem = audioSub;
        this.processor = processor;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(
                        BLIP,  List.of(new KeyBinding(KEY_1, 0)),
                        THUMP, List.of(new KeyBinding(KEY_2, 0)),
                        CHIRP, List.of(new KeyBinding(KEY_3, 0)),
                        DRONE, List.of(new KeyBinding(KEY_4, 0)),
                        QUIT,  List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        soundBank = new DemoSoundBank(audioSubsystem.mixer());

        System.out.println("=== DynamisAudio Basics ===");
        System.out.println("Real procedural audio through the full voice pipeline.");
        System.out.println();
        System.out.println("Controls (focus the window first):");
        System.out.println("  1 → Blip (880 Hz, short)");
        System.out.println("  2 → Thump (110 Hz, punchy)");
        System.out.println("  3 → Chirp (1320 Hz, sparkly)");
        System.out.println("  4 → Toggle drone (220 Hz, sustained)");
        System.out.println("  Esc → Quit");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (windowSubsystem.isCloseRequested()) {
            context.requestStop();
            return;
        }

        InputFrame frame = inputSubsystem.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT))  { context.requestStop(); return; }
        if (frame.pressed(BLIP))  soundBank.playBlip();
        if (frame.pressed(THUMP)) soundBank.playThump();
        if (frame.pressed(CHIRP)) soundBank.playChirp();
        if (frame.pressed(DRONE)) soundBank.toggleDrone();

        // Telemetry every 5 seconds
        if (context.tick() % 300 == 0 && context.tick() > 0) {
            var t = context.telemetry();
            if (t != null) {
                SubsystemTelemetry audioSlot = t.subsystem("Audio");
                if (audioSlot != null && audioSlot.hasDetail()) {
                    AudioTelemetry at = audioSlot.detailAs(AudioTelemetry.class);
                    if (at != null) {
                        System.out.printf("[Audio] %s | callbacks=%d underruns=%d | drone=%s%n",
                                at.state(), at.callbackCount(), at.ringUnderruns(),
                                soundBank.isDroneActive() ? "ON" : "OFF");
                    }
                }
            }
        }
    }

    @Override
    public void shutdown(GameContext context) {
        var t = context.telemetry();
        if (t != null) {
            System.out.println();
            System.out.println(t.detailedReport());
        }
        System.out.println("[AudioBasics] Shutdown.");
    }
}
