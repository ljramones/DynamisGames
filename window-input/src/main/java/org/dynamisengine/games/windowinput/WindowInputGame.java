package org.dynamisengine.games.windowinput;

import org.dynamisengine.games.windowinput.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.windowinput.subsystem.WindowSubsystem;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.api.gesture.GestureFrame;
import org.dynamisengine.input.core.GestureRecognizer;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.stream.Collectors;

import static org.dynamisengine.games.windowinput.WindowInputBindings.*;

/**
 * Live keyboard/mouse input through a real GLFW window.
 *
 * Opens a window. Press keys. See actions and gestures fire in the console.
 * Press Escape or close the window to exit.
 */
public final class WindowInputGame implements WorldApplication {

    private final WindowSubsystem windowSubsystem;
    private final WindowInputSubsystem inputSubsystem;
    private GestureRecognizer recognizer;
    private float playerX = 0.0f;

    public WindowInputGame(WindowSubsystem windowSubsystem,
                           WindowInputSubsystem inputSubsystem) {
        this.windowSubsystem = windowSubsystem;
        this.inputSubsystem = inputSubsystem;
    }

    @Override
    public void initialize(GameContext context) {
        recognizer = WindowInputBindings.createRecognizer();

        System.out.println("=== Window Input Demo ===");
        System.out.println("A real GLFW window is open. Try these controls:");
        System.out.println("  A / Left Arrow  → Move left");
        System.out.println("  D / Right Arrow → Move right");
        System.out.println("  Space (tap)     → Quick jump");
        System.out.println("  Space (hold)    → Charge jump");
        System.out.println("  D (double-tap)  → Dash right");
        System.out.println("  Escape          → Quit");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        // Check window close
        if (windowSubsystem.isCloseRequested()) {
            System.out.println("[WindowInput] Window close requested.");
            context.requestStop();
            return;
        }

        // Get input frame from the subsystem
        InputFrame frame = inputSubsystem.lastFrame();
        if (frame == null) return;

        // Evaluate gestures
        GestureFrame gestures = recognizer.evaluateFrame(frame);

        // Check quit
        if (frame.pressed(QUIT)) {
            System.out.println("[WindowInput] Escape pressed. Exiting.");
            context.requestStop();
            return;
        }

        // Update player position
        float moveX = frame.axis(MOVE_X);
        playerX += moveX * deltaSeconds * 5.0f;

        // Display — only when something happens
        boolean hasInput = frame.actions().values().stream()
                .anyMatch(a -> a.pressed() || a.released());
        boolean hasGestures = gestures.anyFired();
        boolean hasMovement = moveX != 0.0f;

        if (hasInput || hasGestures) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Tick %4d | ", tick));

            // Pressed/released
            var pressed = frame.actions().entrySet().stream()
                    .filter(e -> e.getValue().pressed())
                    .map(e -> "+" + e.getKey().value())
                    .collect(Collectors.joining(", "));
            var released = frame.actions().entrySet().stream()
                    .filter(e -> e.getValue().released())
                    .map(e -> "-" + e.getKey().value())
                    .collect(Collectors.joining(", "));

            if (!pressed.isEmpty()) sb.append("Press: [").append(pressed).append("] ");
            if (!released.isEmpty()) sb.append("Release: [").append(released).append("] ");
            if (hasMovement) sb.append(String.format("MoveX=%.1f PlayerX=%.1f ", moveX, playerX));
            if (hasGestures) {
                var names = gestures.firedGestures().stream()
                        .map(g -> g.value()).collect(Collectors.joining(", "));
                sb.append(">>> GESTURE: [").append(names).append("] ");
            }
            if (!gestures.activeHolds().isEmpty()) {
                var holds = gestures.activeHolds().stream()
                        .map(g -> g.value()).collect(Collectors.joining(", "));
                sb.append("Holding: [").append(holds).append("] ");
            }

            System.out.println(sb.toString().trim());
        }

        // Periodic telemetry
        if (tick % 120 == 0 && tick > 0) {
            var t = context.telemetry();
            if (t != null) {
                System.out.println("[Telemetry] " + t.statusLine());
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
        System.out.println("[WindowInput] Shutdown. Final playerX: " + playerX);
    }
}
