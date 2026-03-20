package org.dynamisengine.games.renderbasics;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL41C.*;

/**
 * Minimal OpenGL renderer using raw LWJGL calls.
 *
 * No DynamisLightEngine — just direct GL to prove the rendering path works.
 * Draws a colored background and a simple cursor indicator.
 */
public final class SimpleRenderer {

    private boolean initialized = false;

    /** Call once after the GL context is current (after window creation). */
    public void initialize() {
        GL.createCapabilities();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        initialized = true;
    }

    /**
     * Render a frame. Call every tick.
     *
     * @param cursorX cursor X position [-10, 10] logical space
     * @param cursorY cursor Y position [-10, 10] logical space
     * @param pulseCount number of active pulses (affects background brightness)
     * @param width  framebuffer width
     * @param height framebuffer height
     */
    public void render(float cursorX, float cursorY, int pulseCount,
                       int width, int height) {
        if (!initialized) return;

        glViewport(0, 0, width, height);

        // Background color shifts based on pulse count
        float brightness = Math.min(0.05f + pulseCount * 0.02f, 0.3f);
        glClearColor(brightness * 0.3f, brightness * 0.5f, brightness, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Draw cursor as a small bright quad using immediate-mode-style approach
        // Map cursor from logical [-10,10] to NDC [-1,1]
        float ndcX = cursorX / 10.0f;
        float ndcY = cursorY / 10.0f;
        float size = 0.05f;

        // Use a simple fullscreen triangle technique with scissor to fake a quad
        // Actually, for GL 4.1 core profile we can't use immediate mode.
        // Use glScissor + glClear to draw rectangles (simplest no-shader approach):

        // Draw cursor rectangle
        int cx = (int) ((ndcX + 1.0f) * 0.5f * width);
        int cy = (int) ((ndcY + 1.0f) * 0.5f * height);
        int sz = (int) (size * Math.min(width, height));

        glEnable(GL_SCISSOR_TEST);
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(1.0f, 1.0f, 0.3f, 1.0f); // bright yellow cursor
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    public boolean isInitialized() { return initialized; }
}
