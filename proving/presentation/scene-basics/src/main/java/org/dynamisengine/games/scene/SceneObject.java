package org.dynamisengine.games.scene;

/**
 * Proving-level scene object — a mesh instance with transform and material.
 *
 * @param mesh      GPU mesh handle (shared across instances)
 * @param material  surface appearance
 * @param x         world position X
 * @param y         world position Y
 * @param z         world position Z
 * @param rotY      rotation around Y axis (radians)
 * @param scale     uniform scale
 * @param label     display name for HUD
 */
public record SceneObject(
        MeshHandle mesh,
        SimpleMaterial material,
        float x, float y, float z,
        float rotY,
        float scale,
        String label
) {
    public float[] buildModelMatrix(float extraRotY) {
        float[] m = SceneRenderer.identity();
        m = SceneRenderer.translate(m, x, y, z);
        m = SceneRenderer.rotateY(m, rotY + extraRotY);
        // apply uniform scale
        float[] s = SceneRenderer.identity();
        s[0] = scale; s[5] = scale; s[10] = scale;
        return SceneRenderer.multiply(m, s);
    }
}
