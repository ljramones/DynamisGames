package org.dynamisengine.games.collision;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL41C.*;

/**
 * Renders cursor, pulses, targets, and hit flashes.
 */
public final class CollisionRenderer {

    private boolean initialized = false;

    public void initialize() {
        GL.createCapabilities();
        initialized = true;
    }

    public void render(float cursorX, float cursorY,
                       float[] pulseX, float[] pulseY, float[] pulseProgress, int pulseCount,
                       float[] targetX, float[] targetY, float[] targetRadius,
                       float[] targetFlash, int targetCount,
                       int score, int width, int height) {
        if (!initialized) return;

        glViewport(0, 0, width, height);
        glClearColor(0.02f, 0.05f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Draw targets — red/orange, flash white on hit
        for (int i = 0; i < targetCount; i++) {
            float flash = targetFlash[i];
            float r = 0.9f + flash * 0.1f;
            float g = 0.3f + flash * 0.7f;
            float b = 0.1f + flash * 0.9f;
            drawRect(targetX[i], targetY[i], targetRadius[i] * 0.01f, r, g, b, width, height);
        }

        // Draw pulses — cyan, shrinking
        for (int i = 0; i < pulseCount; i++) {
            float life = 1.0f - pulseProgress[i];
            float size = 0.025f * life + 0.008f;
            drawRect(pulseX[i], pulseY[i], size, 0.3f * life, 0.9f * life, 1.0f * life, width, height);
        }

        // Draw cursor — yellow with crosshair
        drawRect(cursorX, cursorY, 0.04f, 1.0f, 1.0f, 0.3f, width, height);
        drawRect(cursorX, cursorY, 0.015f, 1.0f, 0.8f, 0.0f, width, height);

        glDisable(GL_SCISSOR_TEST);
    }

    private void drawRect(float logX, float logY, float size,
                          float r, float g, float b, int w, int h) {
        int cx = (int) ((logX / 10f + 1f) * 0.5f * w);
        int cy = (int) ((logY / 10f + 1f) * 0.5f * h);
        int sz = Math.max(1, (int) (size * Math.min(w, h)));
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
