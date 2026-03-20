package org.dynamisengine.games.physics;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL41C.*;

/**
 * Simple renderer for the physics sandbox.
 *
 * Draws: ground line, launcher, projectiles (from physics state),
 * targets, and a simple HUD (score + angle).
 * Uses scissor-clear technique (no shaders).
 */
public final class PhysicsRenderer {

    private boolean initialized = false;

    public void initialize() {
        GL.createCapabilities();
        initialized = true;
    }

    /**
     * Render the physics scene.
     *
     * Coordinate space: X [-10, 10], Y [0, 20] (ground at Y=0).
     * We map to screen with Y-up.
     */
    public void render(float launchX, float launchY, float aimAngleDeg,
                       float[] projX, float[] projY, float[] projR, int projCount,
                       float[] targX, float[] targY, float[] targR, boolean[] targHit, int targCount,
                       int score, int width, int height) {
        if (!initialized) return;

        glViewport(0, 0, width, height);
        glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Draw ground — brown line at Y=0
        for (int i = -10; i <= 10; i++) {
            drawRect(i, 0, 0.012f, 0.4f, 0.25f, 0.1f, width, height);
        }

        // Draw targets — red, flash white when hit
        for (int i = 0; i < targCount; i++) {
            float r, g, b;
            if (targHit[i]) { r = 1f; g = 1f; b = 1f; }
            else { r = 0.9f; g = 0.2f; b = 0.1f; }
            float sz = targR[i] * 0.005f;
            drawRect(targX[i], targY[i], Math.max(sz, 0.015f), r, g, b, width, height);
        }

        // Draw projectiles — bright orange spheres
        for (int i = 0; i < projCount; i++) {
            float sz = projR[i] * 0.005f;
            drawRect(projX[i], projY[i], Math.max(sz, 0.012f), 1.0f, 0.7f, 0.2f, width, height);
        }

        // Draw launcher — green
        drawRect(launchX, launchY, 0.035f, 0.3f, 0.9f, 0.3f, width, height);

        // Draw aim direction indicator
        float rad = (float) Math.toRadians(aimAngleDeg);
        float tipX = launchX + (float) Math.cos(rad) * 1.5f;
        float tipY = launchY + (float) Math.sin(rad) * 1.5f;
        for (int i = 1; i <= 5; i++) {
            float t = i / 5f;
            float dotX = launchX + (tipX - launchX) * t;
            float dotY = launchY + (tipY - launchY) * t;
            drawRect(dotX, dotY, 0.008f, 0.5f, 1.0f, 0.5f, width, height);
        }

        glDisable(GL_SCISSOR_TEST);
    }

    private void drawRect(float logX, float logY, float size,
                          float r, float g, float b, int w, int h) {
        // Map X from [-10, 10] to [0, w], Y from [0, 20] to [0, h]
        int cx = (int) ((logX + 10f) / 20f * w);
        int cy = (int) (logY / 20f * h);
        int sz = Math.max(1, (int) (size * Math.min(w, h)));
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
