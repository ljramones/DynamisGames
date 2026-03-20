package org.dynamisengine.games.camera;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.games.camera.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.Map;

/**
 * First 3D camera proving module.
 *
 * Proves: perspective projection, lookAt view matrix, orbit camera,
 * input-driven rotation/zoom, depth testing, colored cube geometry,
 * 3D shader pipeline (vertex color + MVP uniform).
 *
 * The scene contains a grid of colored cubes. The camera orbits around
 * the center, controlled by input. HUD text shows camera parameters.
 */
public final class CameraGame implements WorldApplication {

    // Input
    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final AxisId ORBIT_H = new AxisId("orbitH");
    static final AxisId ORBIT_V = new AxisId("orbitV");
    static final AxisId ZOOM = new AxisId("zoom");
    private static final ContextId CTX = new ContextId("camera");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_Q=81, KEY_E=69;
    private static final int KEY_ESC=256, KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final Scene3DRenderer renderer = new Scene3DRenderer();

    // Camera state
    private float orbitYaw = 45f;    // degrees
    private float orbitPitch = 30f;  // degrees
    private float orbitDist = 8f;    // distance from center

    public CameraGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(QUIT, List.of(new KeyBinding(KEY_ESC, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0))),
                Map.of(ORBIT_H, List.of(
                                new AxisComposite2D(ORBIT_H, new AxisId("_"), KEY_A, KEY_D, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_H, new AxisId("_2"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f)),
                        ORBIT_V, List.of(
                                new AxisComposite2D(ORBIT_V, new AxisId("_3"), KEY_S, KEY_W, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_V, new AxisId("_4"), KEY_DOWN, KEY_UP, 0, 0, 1f)),
                        ZOOM, List.of(
                                new AxisComposite2D(ZOOM, new AxisId("_5"), KEY_Q, KEY_E, 0, 0, 1f))),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        renderer.initialize();
        System.out.println("=== Camera Basics ===");
        System.out.println("First 3D camera with perspective projection and orbit control.");
        System.out.println("A/D=orbit left/right, W/S=orbit up/down, Q/E=zoom, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) {
            orbitYaw = 45f; orbitPitch = 30f; orbitDist = 8f;
            playSound(440f, 0.1f, 0.001f, 0.02f);
        }

        // Update camera
        float oh = frame.axis(ORBIT_H);
        float ov = frame.axis(ORBIT_V);
        float zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 20f);

        // Compute camera position from spherical coordinates
        float yawRad = (float) Math.toRadians(orbitYaw);
        float pitchRad = (float) Math.toRadians(orbitPitch);
        float camX = orbitDist * (float)(Math.cos(pitchRad) * Math.cos(yawRad));
        float camY = orbitDist * (float) Math.sin(pitchRad);
        float camZ = orbitDist * (float)(Math.cos(pitchRad) * Math.sin(yawRad));

        // Build view-projection
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float aspect = (float) w / h;

        float[] proj = Scene3DRenderer.perspective(60f, aspect, 0.1f, 100f);
        float[] view = Scene3DRenderer.lookAt(camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        float[] vp = Scene3DRenderer.multiply(proj, view);

        // Render scene
        renderer.beginScene(w, h);

        // Ground grid of cubes
        for (int gx = -3; gx <= 3; gx++) {
            for (int gz = -3; gz <= 3; gz++) {
                float scale = 0.4f;
                renderer.drawCube(vp, gx * 1.2f, -0.5f, gz * 1.2f, scale);
            }
        }

        // Center pillar
        renderer.drawCube(vp, 0, 0.5f, 0, 0.8f);

        // Corner pillars
        renderer.drawCube(vp, 3f, 0.3f, 3f, 0.5f);
        renderer.drawCube(vp, -3f, 0.3f, -3f, 0.5f);
        renderer.drawCube(vp, 3f, 0.3f, -3f, 0.5f);
        renderer.drawCube(vp, -3f, 0.3f, 3f, 0.5f);

        // HUD text
        renderer.drawText("CAMERA BASICS - First 3D", 10, 10, 2.5f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Yaw: %.0f  Pitch: %.0f  Dist: %.1f", orbitYaw, orbitPitch, orbitDist),
                10, 35, 2f, 0.7f, 0.8f, 0.7f, w, h);
        renderer.drawText(String.format("Eye: (%.1f, %.1f, %.1f)", camX, camY, camZ),
                10, 55, 2f, 0.6f, 0.7f, 0.6f, w, h);
        renderer.drawText("A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.shutdown();
        System.out.println("[Camera] Done.");
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
