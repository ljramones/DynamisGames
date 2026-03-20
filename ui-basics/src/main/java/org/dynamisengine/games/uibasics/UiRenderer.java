package org.dynamisengine.games.uibasics;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL41C.*;

/**
 * UI renderer using colored panels to communicate game state.
 *
 * No text rendering — uses shapes, colors, and layout to convey:
 * - Title screen (large centered panel)
 * - HUD bars (score, health, wave indicators)
 * - Pause overlay (dimmed with center panel)
 * - Win/Lose overlays (tinted full screen)
 *
 * Console text supplements with exact values.
 */
public final class UiRenderer {

    private boolean initialized = false;

    public void initialize() {
        GL.createCapabilities();
        initialized = true;
    }

    public void renderTitle(int w, int h) {
        if (!initialized) return;
        glViewport(0, 0, w, h);
        glClearColor(0.02f, 0.03f, 0.1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Title banner — large bright panel in center
        drawPanel(w/2, h/2 + h/8, w/3, h/12, 0.1f, 0.3f, 0.8f, w, h);
        // "Press Enter" indicator — smaller panel below
        drawPanel(w/2, h/2 - h/8, w/4, h/20, 0.2f, 0.6f, 0.2f, w, h);

        glDisable(GL_SCISSOR_TEST);
    }

    public void renderPlaying(float cursorX, float cursorY,
                              float[] pulseX, float[] pulseY, float[] pulseP, int pulseCount,
                              float[] targetX, float[] targetY, float[] targetR, int targetCount,
                              int score, int wave, int misses, int maxMisses, int lives,
                              int w, int h) {
        if (!initialized) return;
        glViewport(0, 0, w, h);
        glClearColor(0.02f, 0.04f, 0.1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Targets
        for (int i = 0; i < targetCount; i++) {
            drawWorld(targetX[i], targetY[i], targetR[i] * 0.012f, 0.9f, 0.25f, 0.1f, w, h);
        }

        // Pulses
        for (int i = 0; i < pulseCount; i++) {
            float life = 1f - pulseP[i];
            drawWorld(pulseX[i], pulseY[i], 0.02f * life + 0.005f,
                    0.2f*life, 0.8f*life, 1f*life, w, h);
        }

        // Cursor
        drawWorld(cursorX, cursorY, 0.04f, 1f, 1f, 0.3f, w, h);

        // HUD: score bar (top-left) — length proportional to score
        int scoreWidth = Math.min(score * 15, w/3);
        drawPanel(scoreWidth/2 + 10, h - 20, scoreWidth/2, 8, 0.2f, 0.8f, 0.3f, w, h);

        // HUD: wave indicators (top-right) — filled blocks per wave
        for (int i = 0; i < 3; i++) {
            float brightness = (i < wave) ? 0.9f : 0.2f;
            drawPanel(w - 50 - i * 30, h - 20, 10, 8, brightness, brightness, 0.1f, w, h);
        }

        // HUD: miss indicators (bottom-left) — red dots
        for (int i = 0; i < misses; i++) {
            drawPanel(15 + i * 20, 15, 6, 6, 1f, 0.2f, 0.2f, w, h);
        }

        // HUD: lives (bottom-right) — green dots
        for (int i = 0; i < lives; i++) {
            drawPanel(w - 15 - i * 20, 15, 6, 6, 0.2f, 1f, 0.3f, w, h);
        }

        glDisable(GL_SCISSOR_TEST);
    }

    public void renderPaused(int w, int h) {
        if (!initialized) return;
        glViewport(0, 0, w, h);
        // Dim overlay
        glClearColor(0.05f, 0.05f, 0.15f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Pause panel
        drawPanel(w/2, h/2, w/5, h/10, 0.3f, 0.3f, 0.6f, w, h);
        // Resume hint
        drawPanel(w/2, h/2 - h/8, w/6, h/25, 0.2f, 0.5f, 0.2f, w, h);

        glDisable(GL_SCISSOR_TEST);
    }

    public void renderWon(int score, int w, int h) {
        if (!initialized) return;
        glViewport(0, 0, w, h);
        glClearColor(0.0f, 0.12f, 0.05f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Victory banner
        drawPanel(w/2, h/2 + h/8, w/3, h/10, 0.1f, 0.8f, 0.2f, w, h);
        // Score indicator — bar
        int scoreWidth = Math.min(score * 15, w/2);
        drawPanel(w/2, h/2 - h/10, scoreWidth/2, h/20, 0.9f, 0.9f, 0.2f, w, h);
        // Restart hint
        drawPanel(w/2, h/2 - h/4, w/6, h/25, 0.4f, 0.4f, 0.2f, w, h);

        glDisable(GL_SCISSOR_TEST);
    }

    public void renderLost(int score, int w, int h) {
        if (!initialized) return;
        glViewport(0, 0, w, h);
        glClearColor(0.12f, 0.02f, 0.02f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);

        // Defeat banner
        drawPanel(w/2, h/2 + h/8, w/3, h/10, 0.8f, 0.1f, 0.1f, w, h);
        // Score bar
        int scoreWidth = Math.min(score * 15, w/2);
        drawPanel(w/2, h/2 - h/10, scoreWidth/2, h/20, 0.6f, 0.6f, 0.2f, w, h);
        // Restart hint
        drawPanel(w/2, h/2 - h/4, w/6, h/25, 0.4f, 0.2f, 0.2f, w, h);

        glDisable(GL_SCISSOR_TEST);
    }

    private void drawPanel(int cx, int cy, int hw, int hh, float r, float g, float b, int w, int h) {
        glScissorIndexed(0, Math.max(0, cx - hw), Math.max(0, cy - hh), hw * 2, hh * 2);
        glClearColor(r, g, b, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private void drawWorld(float logX, float logY, float size, float r, float g, float b, int w, int h) {
        int cx = (int) ((logX / 10f + 1f) * 0.5f * w);
        int cy = (int) ((logY / 10f + 1f) * 0.5f * h);
        int sz = Math.max(1, (int) (size * Math.min(w, h)));
        glScissorIndexed(0, cx - sz, cy - sz, sz * 2, sz * 2);
        glClearColor(r, g, b, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
