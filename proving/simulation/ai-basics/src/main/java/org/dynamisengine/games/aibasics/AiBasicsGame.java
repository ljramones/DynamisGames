package org.dynamisengine.games.aibasics;

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
 * Canonical AI exemplar: Shrine Guard.
 *
 * <p>One guard agent with three visible states (IDLE, INVESTIGATE, CHASE)
 * driven by player proximity. Demonstrates:
 * <ol>
 *   <li>Perception gathering (distance, visibility)</li>
 *   <li>Budget-governed AI evaluation via DynamisAI BudgetGovernor</li>
 *   <li>State machine decision with structured output</li>
 *   <li>Gameplay effect application (movement, audio, debug)</li>
 * </ol>
 *
 * <p>Controls: A/D = move player left/right, V = toggle visibility, Esc = quit
 */
public final class AiBasicsGame implements WorldApplication {

    private final GlfwWindowSubsystem windowSub;
    private final AudioWorldSubsystem audioSub;

    private GuardAiFacade guardAi;
    private boolean audioReady = false;

    // Player state (simulated as 1D position for simplicity)
    private float playerPosition = 10.0f;
    private boolean playerVisible = true;
    private long lastSeenTick = 0;

    // Track state transitions for audio cues
    private GuardState previousState = GuardState.IDLE;

    public AiBasicsGame(GlfwWindowSubsystem windowSub,
                        WindowInputWorldSubsystem inputSub,
                        AudioWorldSubsystem audioSub) {
        this.windowSub = windowSub;
        this.audioSub = audioSub;
    }

    @Override
    public void initialize(GameContext context) {
        guardAi = new GuardAiFacade();
        audioReady = audioSub.mixer() != null;
        System.out.println("=== AI Basics: Shrine Guard ===");
        System.out.println("A/D = move player, V = toggle visibility, Esc = quit");
        System.out.println("Guard is at position 0. Move close to trigger AI state changes.");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        // --- Input ---
        boolean escPressed = false;
        for (var event : windowSub.lastEvents().inputEvents()) {
            if (event instanceof InputEvent.Key key && key.action() == InputEvent.InputAction.PRESS) {
                if (key.keyCode() == 256) escPressed = true;       // ESC
                if (key.keyCode() == 86) {                          // V
                    playerVisible = !playerVisible;
                    System.out.printf("[tick=%d] Player visibility: %s%n", tick, playerVisible);
                }
            }
            if (event instanceof InputEvent.Key key && key.action() != InputEvent.InputAction.RELEASE) {
                if (key.keyCode() == 65) playerPosition -= 0.15f;  // A
                if (key.keyCode() == 68) playerPosition += 0.15f;  // D
            }
        }

        if (escPressed || windowSub.isCloseRequested()) {
            System.out.println("Exiting...");
            System.exit(0);
        }

        // --- Perception gathering (gameplay → AI boundary) ---
        float distance = Math.abs(playerPosition - guardAi.guardPosition());
        if (playerVisible) lastSeenTick = tick;
        long ticksSinceLastSeen = tick - lastSeenTick;

        var perception = new GuardPerception(distance, playerVisible, ticksSinceLastSeen);

        // --- AI evaluation (budget-governed) ---
        GuardDecision decision = guardAi.evaluate(tick, perception);

        // --- Apply decision (AI → gameplay boundary) ---
        if (decision.newState() != previousState) {
            System.out.printf("[tick=%d] GUARD: %s -> %s | %s%n",
                    tick, previousState, decision.newState(), decision.reason());

            // Audio cue on state transition
            switch (decision.newState()) {
                case INVESTIGATE -> playSound(440f, 0.15f);
                case CHASE -> playSound(880f, 0.25f);
                case IDLE -> playSound(220f, 0.1f);
            }
            previousState = decision.newState();
        }

        // --- Debug output every 120 ticks ---
        if (tick % 120 == 0) {
            var report = guardAi.lastFrameReport();
            System.out.printf("[tick=%d] player=%.1f guard=%.1f dist=%.1f visible=%s state=%s | %s%n",
                    tick, playerPosition, guardAi.guardPosition(), distance,
                    playerVisible, guardAi.currentState(), decision.reason());
            if (report != null) {
                System.out.printf("  AI budget: %dms frame, %d tasks, %d degraded, %d skipped%n",
                        report.frameElapsedMs(), report.taskRecords().size(),
                        report.degradedTaskCount(), report.skippedTaskCount());
            }
        }
    }

    @Override
    public void shutdown(GameContext context) {}

    private void playSound(float freqHz, float durationSec) {
        if (audioReady) {
            var osc = new SineOscillator(freqHz, 0.3f, 48000f);
            var env = new Envelope(0.01f, 0.05f, 0.6f, durationSec, 48000f);
            var synth = new SynthVoice(osc, env);
            QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
        }
    }
}
