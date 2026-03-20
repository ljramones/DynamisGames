package org.dynamisengine.games.arena;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL41C.*;

/**
 * Arena renderer — same scissor-clear technique, with mode-dependent visuals.
 */
public final class ArenaRenderer {

    private boolean initialized = false;

    public void initialize() {
        GL.createCapabilities();
        initialized = true;
    }

    public void render(GameMode mode, float cursorX, float cursorY,
                       float[] pulseX, float[] pulseY, float[] pulseP, int pulseCount,
                       float[] targetX, float[] targetY, float[] targetR, int targetCount,
                       int wave, int score, int misses, int maxMisses,
                       int width, int height) {
        if (!initialized) return;

        glViewport(0, 0, width, height);

        // Background color based on mode
        switch (mode) {
            case READY -> glClearColor(0.02f, 0.02f, 0.08f, 1f);
            case PLAYING -> glClearColor(0.02f, 0.04f, 0.1f, 1f);
            case WAVE_CLEAR -> glClearColor(0.02f, 0.08f, 0.15f, 1f);
            case WON -> glClearColor(0.0f, 0.15f, 0.05f, 1f);
            case LOST -> glClearColor(0.15f, 0.02f, 0.02f, 1f);
        }
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Targets — red, slightly larger for later waves
        for (int i = 0; i < targetCount; i++) {
            float sz = targetR[i] * 0.012f;
            drawRect(targetX[i], targetY[i], sz, 0.9f, 0.25f, 0.1f, width, height);
        }

        // Pulses — bright cyan
        for (int i = 0; i < pulseCount; i++) {
            float life = 1f - pulseP[i];
            float sz = 0.02f * life + 0.005f;
            drawRect(pulseX[i], pulseY[i], sz, 0.2f*life, 0.8f*life, 1f*life, width, height);
        }

        // Cursor — yellow
        if (mode == GameMode.PLAYING || mode == GameMode.READY) {
            drawRect(cursorX, cursorY, 0.04f, 1f, 1f, 0.3f, width, height);
            drawRect(cursorX, cursorY, 0.015f, 1f, 0.8f, 0f, width, height);
        }

        // Miss indicator — red dots at bottom
        for (int i = 0; i < misses; i++) {
            float x = -9f + i * 1.5f;
            drawRect(x, -9f, 0.015f, 1f, 0.2f, 0.2f, width, height);
        }

        glDisable(GL_SCISSOR_TEST);
    }

    private void drawRect(float logX, float logY, float size,
                          float r, float g, float b, int w, int h) {
        int cx = (int) ((logX / 10f + 1f) * 0.5f * w);
        int cy = (int) ((logY / 10f + 1f) * 0.5f * h);
        int sz = Math.max(1, (int) (size * Math.min(w, h)));
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
