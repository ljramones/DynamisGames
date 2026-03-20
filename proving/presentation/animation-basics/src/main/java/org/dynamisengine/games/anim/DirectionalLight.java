package org.dynamisengine.games.anim;

/**
 * Proving-level directional light (infinite distance, parallel rays).
 *
 * @param dirX     direction X (normalized, points toward light source)
 * @param dirY     direction Y
 * @param dirZ     direction Z
 * @param r        color red [0,1]
 * @param g        color green [0,1]
 * @param b        color blue [0,1]
 * @param intensity intensity multiplier
 */
public record DirectionalLight(float dirX, float dirY, float dirZ,
                                float r, float g, float b, float intensity) {

    public static final DirectionalLight SUN = new DirectionalLight(
            0.4f, 0.8f, 0.3f, 1.0f, 0.95f, 0.85f, 1.0f);
}
