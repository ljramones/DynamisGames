package org.dynamisengine.games.scriptbasics;

import java.util.List;
import org.dynamisengine.scripting.api.value.CanonEvent;
import org.dynamisengine.scripting.api.value.CanonTime;
import org.dynamisengine.scripting.chronicler.StoryNode;
import org.dynamisengine.scripting.runtime.RuntimeBuilder;
import org.dynamisengine.scripting.runtime.RuntimeConfiguration;
import org.dynamisengine.scripting.runtime.RuntimeTickResult;
import org.dynamisengine.scripting.runtime.ScriptingRuntime;

/**
 * Facade between gameplay and the scripting subsystem.
 *
 * <p>This is the canonical seam: gameplay code calls {@link #evaluateInteraction}
 * with current world state, and the scripting layer commits the decision to the
 * canon log as an auditable, queryable world event.
 *
 * <p>The lifecycle demonstrated:
 * <ol>
 *   <li>Gameplay evaluates conditions (hasKey, doorOpen)</li>
 *   <li>Facade creates the appropriate world event</li>
 *   <li>ScriptingRuntime ticks — Chronicler fires matching story nodes</li>
 *   <li>Oracle commits the event to the canon log</li>
 *   <li>Facade returns a structured outcome for the game to apply</li>
 * </ol>
 *
 * <p>The Chronicler story nodes use {@code canonTime}-based predicates
 * (the only variables the DSL currently resolves). Gameplay condition
 * logic stays in the facade, which is the correct separation: the
 * scripting layer owns commit ordering and causal tracking, not
 * gameplay rule evaluation.
 */
public final class ShrineScriptFacade {

    private final ScriptingRuntime runtime;
    private RuntimeTickResult lastTickResult;

    public ShrineScriptFacade() {
        // Story nodes fire on canon time — they represent world reactions
        // that the Chronicler proposes and Oracle commits
        this.runtime = RuntimeBuilder.create()
                .withConfiguration(RuntimeConfiguration.defaults())
                .withDimension(new ShrineDimension())
                .withStoryNode(StoryNode.of(
                        "shrine.world_reaction",
                        "authored",
                        "canonTime > 0",
                        List.of(),
                        100,
                        true,
                        5L))
                .build();
        runtime.start();
    }

    /**
     * Evaluate a shrine interaction using the scripting lifecycle.
     *
     * <p>Gameplay decides the condition; scripting commits the event
     * to the canon log with full causal tracking and audit trail.
     *
     * @param hasKey   whether the player currently holds the key
     * @param doorOpen whether the door is already open
     * @return structured outcome for gameplay to apply
     */
    public ShrineScriptOutcome evaluateInteraction(boolean hasKey, boolean doorOpen) {
        if (doorOpen) {
            return ShrineScriptOutcome.ALREADY_OPEN;
        }

        // Determine the outcome
        ShrineScriptOutcome outcome = hasKey
                ? ShrineScriptOutcome.OPEN_DOOR
                : ShrineScriptOutcome.DENIED_NO_KEY;

        // Commit the interaction as a canonical world event
        // This is the scripting lifecycle: the event enters the canon log
        // with a causal link, timestamp, and commit ordering
        long nextId = runtime.canonLog().latestCommitId() + 1;
        runtime.seedEvent(CanonEvent.of(
                nextId,
                runtime.currentTime(),
                "shrine:" + outcome.name().toLowerCase(),
                "ShrineInteract(hasKey=" + hasKey + ", result=" + outcome + ")"));

        // Tick the runtime — Chronicler evaluates story nodes,
        // Oracle commits world events, canon log advances
        lastTickResult = runtime.tick();

        return outcome;
    }

    /** Last tick result for telemetry/debug display. */
    public RuntimeTickResult lastTickResult() { return lastTickResult; }

    /** The scripting runtime for direct inspection. */
    public ScriptingRuntime runtime() { return runtime; }

    /** Number of events in the canon log. */
    public long canonLogSize() { return runtime.canonLog().latestCommitId(); }

    public void shutdown() { runtime.stop(); }
}
