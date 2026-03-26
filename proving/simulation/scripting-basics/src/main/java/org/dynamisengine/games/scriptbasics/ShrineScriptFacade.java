package org.dynamisengine.games.scriptbasics;

import java.util.List;
import org.dynamisengine.scripting.api.value.CanonEvent;
import org.dynamisengine.scripting.chronicler.StoryNode;
import org.dynamisengine.scripting.runtime.RuntimeBuilder;
import org.dynamisengine.scripting.runtime.RuntimeConfiguration;
import org.dynamisengine.scripting.runtime.RuntimeTickResult;
import org.dynamisengine.scripting.runtime.ScriptingRuntime;

/**
 * Facade between gameplay and the scripting subsystem.
 *
 * <p>This is the canonical seam: gameplay code calls {@link #evaluateInteraction}
 * with current world state, and the scripting layer returns a structured
 * {@link ShrineScriptOutcome} that gameplay code applies as a visible effect.
 *
 * <p>Internally, the facade:
 * <ol>
 *   <li>Seeds the canon with current gameplay state (hasKey, doorOpen)</li>
 *   <li>Ticks the scripting runtime so the Chronicler can evaluate triggers</li>
 *   <li>Reads the canon log to determine what happened</li>
 *   <li>Returns a structured outcome for the game to apply</li>
 * </ol>
 */
public final class ShrineScriptFacade {

    private final ScriptingRuntime runtime;
    private RuntimeTickResult lastTickResult;

    public ShrineScriptFacade() {
        // Assemble the scripting runtime with two story nodes:
        // 1. "shrine.unlock" fires when player has key and door is closed
        // 2. "shrine.deny"   fires when player lacks key
        this.runtime = RuntimeBuilder.create()
                .withConfiguration(RuntimeConfiguration.defaults())
                .withStoryNode(StoryNode.of(
                        "shrine.unlock",
                        "authored",
                        "hasKey == true && doorOpen == false",
                        List.of(),
                        100,
                        false,
                        0L))
                .withStoryNode(StoryNode.of(
                        "shrine.deny",
                        "authored",
                        "hasKey == false && doorOpen == false",
                        List.of(),
                        50,
                        true,    // repeatable
                        10L))   // cooldown ticks
                .build();
        runtime.start();
    }

    /**
     * Evaluate a shrine interaction using the scripting lifecycle.
     *
     * <p>Flow: seed state → tick runtime → read canon → return outcome.
     *
     * @param hasKey   whether the player currently holds the key
     * @param doorOpen whether the door is already open
     * @return structured outcome for gameplay to apply
     */
    public ShrineScriptOutcome evaluateInteraction(boolean hasKey, boolean doorOpen) {
        if (doorOpen) {
            return ShrineScriptOutcome.ALREADY_OPEN;
        }

        // Seed current gameplay state into the canon so predicates can read it
        long nextId = runtime.canonLog().latestCommitId() + 1;
        runtime.seedEvent(CanonEvent.of(
                nextId,
                runtime.currentTime(),
                "gameplay:shrine_interact",
                "hasKey=" + hasKey + ",doorOpen=" + doorOpen));

        // Tick the scripting runtime:
        //   Chronicler evaluates story nodes against canon predicates
        //   Oracle commits matching world events to the canon log
        lastTickResult = runtime.tick();

        // Read the outcome by querying canon for world events from this tick
        var unlockEvents = runtime.canonLog().queryByCausalLink("worldevent:shrine.unlock");
        if (!unlockEvents.isEmpty()) {
            return ShrineScriptOutcome.OPEN_DOOR;
        }
        var denyEvents = runtime.canonLog().queryByCausalLink("worldevent:shrine.deny");
        if (!denyEvents.isEmpty()) {
            return ShrineScriptOutcome.DENIED_NO_KEY;
        }

        // No script matched — likely already handled or between cooldowns
        return hasKey ? ShrineScriptOutcome.OPEN_DOOR : ShrineScriptOutcome.DENIED_NO_KEY;
    }

    /** Last tick result for telemetry/debug display. */
    public RuntimeTickResult lastTickResult() { return lastTickResult; }

    /** The scripting runtime for direct inspection. */
    public ScriptingRuntime runtime() { return runtime; }

    public void shutdown() { runtime.stop(); }
}
