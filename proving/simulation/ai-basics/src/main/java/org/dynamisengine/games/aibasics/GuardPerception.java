package org.dynamisengine.games.aibasics;

/**
 * Perception snapshot for the guard agent.
 *
 * <p>Gameplay gathers these facts from the world. The AI layer consumes
 * them without knowing how they were produced. This is the canonical
 * perception boundary.
 *
 * @param distanceToPlayer  distance in world units
 * @param playerVisible     true if line-of-sight is clear
 * @param ticksSinceLastSeen ticks since player was last visible
 */
public record GuardPerception(
        float distanceToPlayer,
        boolean playerVisible,
        long ticksSinceLastSeen
) {}
