package org.dynamisengine.games.scriptbasics;

import org.dynamisengine.audio.procedural.Envelope;
import org.dynamisengine.audio.procedural.ProceduralAudioAsset;
import org.dynamisengine.audio.procedural.QuickPlayback;
import org.dynamisengine.audio.procedural.SineOscillator;
import org.dynamisengine.audio.procedural.SynthVoice;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

/**
 * Canonical scripting lifecycle exemplar: Shrine Door Trigger.
 *
 * <p>Demonstrates the full DynamisScripting lifecycle:
 * <ol>
 *   <li>Player presses E to interact with the shrine</li>
 *   <li>Game assembles context (hasKey, doorOpen) for the scripting layer</li>
 *   <li>Scripting evaluates: Chronicler checks triggers, Oracle commits events</li>
 *   <li>Game reads the structured outcome and applies visible effects</li>
 *   <li>Console output shows the scripting lifecycle in action</li>
 * </ol>
 *
 * <p>Controls: K = toggle key, E = interact with shrine
 */
public final class ScriptingBasicsGame implements WorldApplication {

    private final GlfwWindowSubsystem windowSub;
    private final WindowInputWorldSubsystem inputSub;
    private final AudioWorldSubsystem audioSub;

    private ShrineScriptFacade scriptFacade;
    private boolean audioReady = false;

    // Game state
    private boolean hasKey = false;
    private boolean doorOpen = false;
    private ShrineScriptOutcome lastOutcome = null;
    private String statusMessage = "Press K to pick up key, E to interact with shrine";
    private long lastInteractTick = -100;

    public ScriptingBasicsGame(GlfwWindowSubsystem windowSub,
                               WindowInputWorldSubsystem inputSub,
                               AudioWorldSubsystem audioSub) {
        this.windowSub = windowSub;
        this.inputSub = inputSub;
        this.audioSub = audioSub;
    }

    @Override
    public void initialize(GameContext context) {
        scriptFacade = new ShrineScriptFacade();
        audioReady = audioSub.mixer() != null;
        System.out.println("=== Scripting Basics ===");
        System.out.println("Press K to toggle key, E to interact with shrine");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        if (windowSub.isCloseRequested()) return;

        // Read input events
        boolean ePressed = false;
        boolean kPressed = false;
        for (var event : windowSub.lastEvents().inputEvents()) {
            if (event instanceof InputEvent.Key key && key.action() == InputEvent.InputAction.PRESS) {
                if (key.keyCode() == 69) ePressed = true;  // E
                if (key.keyCode() == 75) kPressed = true;  // K
            }
        }

        // Toggle key state
        if (kPressed) {
            hasKey = !hasKey;
            statusMessage = hasKey ? "Key acquired! Press E at the shrine." : "Key dropped.";
            playSound(hasKey ? 880f : 220f, 0.1f);
            System.out.printf("[tick=%d] %s%n", tick, statusMessage);
        }

        // Interact with shrine - THE CANONICAL SCRIPTING SEAM
        if (ePressed && tick - lastInteractTick > 10) {
            lastInteractTick = tick;

            // === THE SCRIPTING LIFECYCLE ===
            // 1. Gameplay state (hasKey, doorOpen) is the context
            // 2. ShrineScriptFacade seeds state into canon and ticks runtime
            // 3. Chronicler evaluates trigger predicates against canon state
            // 4. Oracle commits matching world events to the canon log
            // 5. Facade reads committed events, returns structured outcome
            // 6. Game applies the outcome as visible effects below
            lastOutcome = scriptFacade.evaluateInteraction(hasKey, doorOpen);

            // Scripts decide; game systems apply
            switch (lastOutcome) {
                case OPEN_DOOR -> {
                    doorOpen = true;
                    statusMessage = "The shrine glows! Door opens.";
                    playSound(660f, 0.3f);
                }
                case DENIED_NO_KEY -> {
                    statusMessage = "The shrine is silent. You need the key.";
                    playSound(110f, 0.15f);
                }
                case ALREADY_OPEN -> {
                    statusMessage = "The door is already open.";
                }
            }

            // Debug: show the scripting lifecycle result
            var tickResult = scriptFacade.lastTickResult();
            System.out.printf("[tick=%d] INTERACT -> %s | %s%n", tick, lastOutcome, statusMessage);
            if (tickResult != null) {
                System.out.printf("  scripting: proposed=%d committed=%d duration=%.2fms canonTick=%d canonLog=%d%n",
                        tickResult.worldEventsProposed(),
                        tickResult.worldEventsCommitted(),
                        tickResult.tickDurationNanos() / 1_000_000.0,
                        tickResult.canonTime().tick(),
                        scriptFacade.canonLogSize());
            }
        }
    }

    @Override
    public void shutdown(GameContext context) {
        if (scriptFacade != null) scriptFacade.shutdown();
    }

    private void playSound(float freqHz, float durationSec) {
        if (audioReady) {
            var osc = new SineOscillator(freqHz, 0.3f, 48000f);
            var env = new Envelope(0.01f, 0.05f, 0.6f, durationSec, 48000f);
            var synth = new SynthVoice(osc, env);
            QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
        }
    }
}
