package org.dynamisengine.games.inputbasics;

import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.api.gesture.GestureFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.core.GestureRecognizer;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.stream.Collectors;

import static org.dynamisengine.games.inputbasics.InputBindings.*;

/**
 * Demonstrates the DynamisInput processing pipeline using synthetic events.
 *
 * Each tick:
 * 1. Feed scripted events into the InputProcessor
 * 2. Produce an InputFrame (action states, axis values)
 * 3. Evaluate gestures against the frame
 * 4. Print meaningful output
 */
public final class InputBasicsGame implements WorldApplication {

    private DefaultInputProcessor processor;
    private GestureRecognizer recognizer;

    @Override
    public void initialize(GameContext context) {
        processor = InputBindings.createProcessor();
        recognizer = InputBindings.createRecognizer();

        System.out.println("=== DynamisInput Basics ===");
        System.out.println("Synthetic input demo — scripted events over ~100 ticks");
        System.out.println();
        System.out.println("Actions: MOVE_LEFT(A), MOVE_RIGHT(D), JUMP(Space), PAUSE(P), QUIT(Esc)");
        System.out.println("Gestures: QuickJump(tap space), ChargeJump(hold space 30+), DashRight(double-tap D)");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        // 1. Feed synthetic events for this tick
        List<InputEvent> events = SyntheticInputScript.eventsForTick(tick);
        for (InputEvent event : events) {
            processor.feed(event, tick);
        }

        // 2. Produce input frame
        InputFrame frame = processor.snapshot(tick);

        // 3. Evaluate gestures
        GestureFrame gestures = recognizer.evaluateFrame(frame);

        // 4. Display — only print when something interesting happens
        boolean hasActions = frame.actions().values().stream()
                .anyMatch(a -> a.pressed() || a.released() || a.down());
        boolean hasGestures = gestures.anyFired();
        boolean hasHolds = !gestures.activeHolds().isEmpty();

        if (hasActions || hasGestures || !events.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Tick %3d | ", tick));

            // Active actions
            var activeActions = frame.actions().entrySet().stream()
                    .filter(e -> e.getValue().down())
                    .map(e -> e.getKey().value())
                    .collect(Collectors.joining(", "));
            if (!activeActions.isEmpty()) {
                sb.append("Actions: [").append(activeActions).append("] ");
            }

            // Pressed this tick
            var pressed = frame.actions().entrySet().stream()
                    .filter(e -> e.getValue().pressed())
                    .map(e -> "+" + e.getKey().value())
                    .collect(Collectors.joining(", "));
            if (!pressed.isEmpty()) {
                sb.append("Pressed: [").append(pressed).append("] ");
            }

            // Released this tick
            var released = frame.actions().entrySet().stream()
                    .filter(e -> e.getValue().released())
                    .map(e -> "-" + e.getKey().value())
                    .collect(Collectors.joining(", "));
            if (!released.isEmpty()) {
                sb.append("Released: [").append(released).append("] ");
            }

            // Axes
            float moveX = frame.axis(MOVE_X);
            if (moveX != 0.0f) {
                sb.append(String.format("MoveX: %.1f ", moveX));
            }

            // Gestures fired
            if (hasGestures) {
                var gestureNames = gestures.firedGestures().stream()
                        .map(g -> g.value())
                        .collect(Collectors.joining(", "));
                sb.append(">>> GESTURE: [").append(gestureNames).append("] ");
            }

            // Active holds
            if (hasHolds) {
                var holdNames = gestures.activeHolds().stream()
                        .map(g -> g.value())
                        .collect(Collectors.joining(", "));
                sb.append("Holding: [").append(holdNames).append("] ");
            }

            System.out.println(sb.toString().trim());
        }

        // Stop after script ends
        if (tick > SyntheticInputScript.lastTick()) {
            System.out.println();
            System.out.println("--- Script complete ---");
            var t = context.telemetry();
            if (t != null) {
                System.out.println(t.statusLine());
            }
            context.requestStop();
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[InputBasics] Shutdown.");
    }
}
