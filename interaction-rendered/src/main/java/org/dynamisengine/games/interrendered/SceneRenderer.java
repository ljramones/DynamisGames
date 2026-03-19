package org.dynamisengine.games.interrendered;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Simple OpenGL renderer that draws the cursor and visible pulse entities.
 *
 * Uses scissor-clear trick for rectangles (no shaders needed).
 * Each pulse is drawn as a colored square that shrinks and dims over its lifetime.
 */
public final class SceneRenderer {

    private boolean initialized = false;

    public void initialize() {
        GL.createCapabilities();
        initialized = true;
    }

    /**
     * Render a full frame.
     *
     * @param cursor     cursor position in logical space [-10, 10]
     * @param pulses     array of visible pulses (x, y, progress 0-1)
     * @param pulseCount number of valid entries in pulses array
     * @param width      framebuffer width
     * @param height     framebuffer height
     */
    public void render(float cursorX, float cursorY,
                       float[] pulseX, float[] pulseY, float[] pulseProgress,
                       int pulseCount,
                       int width, int height) {
        if (!initialized) return;

        glViewport(0, 0, width, height);

        // Background — dark blue, slightly brighter with more pulses
        float bg = Math.min(0.05f + pulseCount * 0.015f, 0.25f);
        glClearColor(bg * 0.2f, bg * 0.4f, bg, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glEnable(GL_SCISSOR_TEST);

        // Draw pulses — bright colored squares that shrink and dim
        for (int i = 0; i < pulseCount; i++) {
            float progress = pulseProgress[i]; // 0 = just spawned, 1 = about to expire
            float life = 1.0f - progress; // 1 = full life, 0 = dead

            // Color: starts bright cyan, fades to dark
            float r = 0.2f * life;
            float g = 0.8f * life;
            float b = 1.0f * life;

            // Size: starts large, shrinks
            float size = 0.04f * life + 0.01f;

            drawRect(pulseX[i], pulseY[i], size, r, g, b, width, height);
        }

        // Draw cursor — bright yellow, always on top
        drawRect(cursorX, cursorY, 0.04f, 1.0f, 1.0f, 0.3f, width, height);

        // Draw cursor crosshair (smaller inner square)
        drawRect(cursorX, cursorY, 0.015f, 1.0f, 0.8f, 0.0f, width, height);

        glDisable(GL_SCISSOR_TEST);
    }

    private void drawRect(float logX, float logY, float size,
                          float r, float g, float b,
                          int width, int height) {
        // Map from logical [-10, 10] to pixel coordinates
        float ndcX = logX / 10.0f;
        float ndcY = logY / 10.0f;
        int cx = (int) ((ndcX + 1.0f) * 0.5f * width);
        int cy = (int) ((ndcY + 1.0f) * 0.5f * height);
        int sz = (int) (size * Math.min(width, height));
        if (sz < 1) sz = 1;

        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
