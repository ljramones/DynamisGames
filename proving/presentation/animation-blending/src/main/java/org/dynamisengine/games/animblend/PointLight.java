package org.dynamisengine.games.animblend;

/**
 * Proving-level point light (position in world space, attenuates with distance).
 *
 * @param x         world position X
 * @param y         world position Y
 * @param z         world position Z
 * @param r         color red [0,1]
 * @param g         color green [0,1]
 * @param b         color blue [0,1]
 * @param intensity intensity multiplier
 * @param radius    effective radius (attenuation reaches ~0 at this distance)
 */
public record PointLight(float x, float y, float z,
                          float r, float g, float b,
                          float intensity, float radius) {}
