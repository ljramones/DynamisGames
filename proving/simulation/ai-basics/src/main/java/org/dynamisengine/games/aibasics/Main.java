package org.dynamisengine.games.aibasics;

import java.util.Map;
import org.dynamisengine.audio.world.AudioWorldSubsystem;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.window.WindowInputWorldSubsystem;
import org.dynamisengine.window.glfw.GlfwWindowSubsystem;
import org.dynamisengine.worldengine.api.WorldEngine;

/**
 * Entry point for the AI Basics exemplar.
 *
 * <p>Demonstrates budget-governed AI with a shrine guard agent.
 * Perception, decision, and action are clearly separated.
 *
 * <p>Controls: A/D = move player, V = toggle visibility, Esc = quit
 */
public final class Main {

    public static void main(String[] args) {
        var windowSub = new GlfwWindowSubsystem("Dynamis - AI Basics", 640, 480);
        var ctx = new ContextId("game");
        var inputMap = new InputMap(ctx, Map.of(), Map.of(), false);
        var processor = new DefaultInputProcessor(Map.of(ctx, inputMap));
        processor.pushContext(ctx);
        var inputSub = new WindowInputWorldSubsystem(windowSub, processor);
        var audioSub = new AudioWorldSubsystem();

        WorldEngine.builder()
                .application(new AiBasicsGame(windowSub, inputSub, audioSub))
                .subsystem(windowSub)
                .subsystem(inputSub)
                .subsystem(audioSub)
                .run();
    }
}
