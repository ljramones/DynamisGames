package org.dynamisengine.games.windowinput;

import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.gesture.GestureId;
import org.dynamisengine.input.api.gesture.InputGesture;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.core.GestureRecognizer;

import java.util.List;
import java.util.Map;

/**
 * Input bindings for the window-input example.
 */
public final class WindowInputBindings {

    private WindowInputBindings() {}

    // Actions
    public static final ActionId MOVE_LEFT  = new ActionId("moveLeft");
    public static final ActionId MOVE_RIGHT = new ActionId("moveRight");
    public static final ActionId JUMP       = new ActionId("jump");
    public static final ActionId QUIT       = new ActionId("quit");

    // Axes
    public static final AxisId MOVE_X = new AxisId("moveX");

    // Gestures
    public static final GestureId QUICK_JUMP  = new GestureId("quickJump");
    public static final GestureId CHARGE_JUMP = new GestureId("chargeJump");
    public static final GestureId DASH_RIGHT  = new GestureId("dashRight");

    // Context
    public static final ContextId GAMEPLAY = new ContextId("gameplay");

    // GLFW key codes
    private static final int KEY_A      = 65;
    private static final int KEY_D      = 68;
    private static final int KEY_SPACE  = 32;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_LEFT   = 263;
    private static final int KEY_RIGHT  = 262;

    public static DefaultInputProcessor createProcessor() {
        InputMap gameplay = new InputMap(GAMEPLAY,
                Map.of(
                        MOVE_LEFT,  List.of(new KeyBinding(KEY_A, 0), new KeyBinding(KEY_LEFT, 0)),
                        MOVE_RIGHT, List.of(new KeyBinding(KEY_D, 0), new KeyBinding(KEY_RIGHT, 0)),
                        JUMP,       List.of(new KeyBinding(KEY_SPACE, 0)),
                        QUIT,       List.of(new KeyBinding(KEY_ESCAPE, 0))),
                Map.of(
                        MOVE_X, List.of(new AxisComposite2D(
                                MOVE_X, new AxisId("unused"),
                                KEY_A, KEY_D, 0, 0, 1.0f))),
                false);

        var processor = new DefaultInputProcessor(Map.of(GAMEPLAY, gameplay));
        processor.pushContext(GAMEPLAY);
        return processor;
    }

    public static GestureRecognizer createRecognizer() {
        var recognizer = new GestureRecognizer();
        recognizer.register(QUICK_JUMP,  new InputGesture.Tap(JUMP, 10));
        recognizer.register(CHARGE_JUMP, new InputGesture.Hold(JUMP, 30));
        recognizer.register(DASH_RIGHT,  new InputGesture.DoubleTap(MOVE_RIGHT, 10, 15));
        return recognizer;
    }
}
