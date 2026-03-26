package org.dynamisengine.games.aibasics;

import org.dynamisengine.ai.core.AITaskNode;
import org.dynamisengine.ai.core.DefaultBudgetGovernor;
import org.dynamisengine.ai.core.DegradeMode;
import org.dynamisengine.ai.core.FrameBudgetReport;
import org.dynamisengine.ai.core.Priority;
import org.dynamisengine.core.entity.EntityId;

/**
 * Canonical AI seam: perception in, decision out.
 *
 * <p>Uses the real DynamisAI {@link DefaultBudgetGovernor} to execute the
 * guard's perception+decision task within the AI frame budget. The governor
 * tracks execution time, enforces budget limits, and provides frame reports
 * for telemetry.
 *
 * <p>The teaching arc:
 * <ol>
 *   <li>Gameplay gathers {@link GuardPerception} from the world</li>
 *   <li>{@link #evaluate} passes perception into the budget-governed task</li>
 *   <li>The task evaluates state transitions based on perception</li>
 *   <li>{@link GuardDecision} is returned for gameplay to apply</li>
 * </ol>
 */
public final class GuardAiFacade {

    private static final float AWARENESS_RANGE = 5.0f;
    private static final float CHASE_RANGE = 3.0f;
    private static final long INVESTIGATE_TIMEOUT_TICKS = 180; // ~3 seconds at 60Hz

    private final DefaultBudgetGovernor governor;
    private final EntityId guardId = EntityId.of(1L);

    private GuardState currentState = GuardState.IDLE;
    private GuardDecision lastDecision = GuardDecision.stay(GuardState.IDLE, "initial");
    private GuardPerception lastPerception;
    private float guardPosition = 0f;

    public GuardAiFacade() {
        this.governor = new DefaultBudgetGovernor(8); // 8ms AI budget

        // Register the guard's think task with the budget governor.
        // This is the canonical pattern: every agent registers a task
        // with priority, budget, degrade mode, and a mandatory fallback.
        governor.register(new AITaskNode(
                "guard.think",
                2,                        // 2ms max budget for this task
                Priority.NORMAL,
                DegradeMode.CACHED,       // if over budget, use last decision
                this::think,              // full evaluation
                this::fallback,           // degraded: reuse last decision
                guardId
        ));
    }

    /**
     * Run one AI frame: gather perception, evaluate under budget, return decision.
     *
     * <p>The BudgetGovernor decides whether to run the full think task or
     * the fallback, based on frame budget remaining. This is how DynamisAI
     * handles many agents without blowing the frame budget.
     */
    public GuardDecision evaluate(long tick, GuardPerception perception) {
        this.lastPerception = perception;

        // Run the budget governor — it executes registered tasks within budget
        governor.runFrame(tick, null);

        return lastDecision;
    }

    /** Full perception → decision evaluation (runs when budget allows). */
    private void think() {
        if (lastPerception == null) return;

        GuardState newState = currentState;
        String reason = "no change";
        float moveTo = guardPosition;

        switch (currentState) {
            case IDLE -> {
                if (lastPerception.playerVisible() && lastPerception.distanceToPlayer() < AWARENESS_RANGE) {
                    newState = GuardState.INVESTIGATE;
                    reason = "player detected at distance " + String.format("%.1f", lastPerception.distanceToPlayer());
                }
            }
            case INVESTIGATE -> {
                if (lastPerception.playerVisible() && lastPerception.distanceToPlayer() < CHASE_RANGE) {
                    newState = GuardState.CHASE;
                    reason = "player confirmed at close range";
                } else if (lastPerception.ticksSinceLastSeen() > INVESTIGATE_TIMEOUT_TICKS) {
                    newState = GuardState.IDLE;
                    reason = "lost contact, returning to idle";
                } else {
                    reason = "investigating, scanning...";
                }
            }
            case CHASE -> {
                if (!lastPerception.playerVisible() || lastPerception.distanceToPlayer() > AWARENESS_RANGE) {
                    newState = GuardState.INVESTIGATE;
                    reason = "target lost, investigating last position";
                } else {
                    moveTo = lastPerception.distanceToPlayer() > 0.5f ? guardPosition + 0.1f : guardPosition;
                    reason = "pursuing at distance " + String.format("%.1f", lastPerception.distanceToPlayer());
                }
            }
        }

        if (newState != currentState) {
            currentState = newState;
        }
        guardPosition = moveTo;
        lastDecision = new GuardDecision(currentState, reason, moveTo);
    }

    /** Fallback: reuse last decision (runs when budget is exceeded). */
    private void fallback() {
        // BudgetGovernor calls this when the frame budget is exhausted.
        // The agent continues with its last decision — no new evaluation.
    }

    public GuardState currentState() { return currentState; }
    public GuardDecision lastDecision() { return lastDecision; }
    public float guardPosition() { return guardPosition; }
    public FrameBudgetReport lastFrameReport() { return governor.getLastFrameReport(); }
}
