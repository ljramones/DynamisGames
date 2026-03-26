package org.dynamisengine.games.scriptbasics;

import java.util.Map;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the Scripting Basics exemplar.
 *
 * <p>Demonstrates the canonical DynamisScripting lifecycle:
 * trigger detection → context assembly → script evaluation → outcome application.
 *
 * <p>Controls:
 * <ul>
 *   <li>K — toggle "has key" state</li>
 *   <li>E — interact with the shrine</li>
 *   <li>ESC / close window — exit</li>
 * </ul>
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis - Scripting Basics", 640, 480);
        var ctx = new ContextId("game");
        var inputMap = new InputMap(ctx, Map.of(), Map.of(), false);
        var processor = new DefaultInputProcessor(Map.of(ctx, inputMap));
        processor.pushContext(ctx);
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new ScriptingBasicsGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
