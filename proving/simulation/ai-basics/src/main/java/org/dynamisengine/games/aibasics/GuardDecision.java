package org.dynamisengine.games.aibasics;

/**
 * Structured decision output from the guard AI.
 *
 * <p>AI decides; game systems apply. This record carries the decision
 * and the reason, so gameplay code can apply effects and debug output
 * can explain why.
 *
 * @param newState   the state the guard should transition to
 * @param reason     human-readable explanation for debug display
 * @param moveToward target position to move toward (0 = stay)
 */
public record GuardDecision(
        GuardState newState,
        String reason,
        float moveToward
) {
    public static GuardDecision stay(GuardState state, String reason) {
        return new GuardDecision(state, reason, 0);
    }

    public static GuardDecision moveTo(GuardState state, String reason, float target) {
        return new GuardDecision(state, reason, target);
    }
}
