package org.dynamisengine.games.lighting;

import org.dynamisengine.games.lighting.subsystem.*;
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
 * Lighting basics - directional + point light on proven meshes/materials.
 *
 * Proves: DirectionalLight/PointLight records, per-light shader binding,
 * Blinn-Phong with explicit light contributions, light toggles,
 * moving point light, light-material interaction.
 *
 * Scene: 5 tori with different materials under a directional sun
 * and an orbiting point light. Toggle each light to see its contribution.
 */
public final class LightingGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId TOGGLE_DIR = new ActionId("toggleDir");
    static final ActionId TOGGLE_POINT = new ActionId("togglePoint");
    static final ActionId TOGGLE_MOTION = new ActionId("toggleMotion");
    static final AxisId ORBIT_H = new AxisId("orbitH");
    static final AxisId ORBIT_V = new AxisId("orbitV");
    static final AxisId ZOOM = new AxisId("zoom");
    private static final ContextId CTX = new ContextId("lighting");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_1=49,KEY_2=50,KEY_3=51;
    private static final int KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final LightingRenderer renderer = new LightingRenderer();

    private MeshHandle torusHandle;
    private MeshHandle sphereHandle; // small sphere for point light indicator

    // Camera
    private float orbitYaw = 30f, orbitPitch = 30f, orbitDist = 10f;

    // Lights
    private boolean dirEnabled = true;
    private boolean pointEnabled = true;
    private boolean pointMoving = true;
    private float time = 0;

    // Materials for the 5 tori
    private static final SimpleMaterial[] MATS = {
            SimpleMaterial.MATTE_RED,
            SimpleMaterial.GLOSSY_BLUE,
            SimpleMaterial.GOLD,
            SimpleMaterial.CHROME,
            new SimpleMaterial(new float[]{0.6f, 0.3f, 0.8f}, 0.12f, 0.7f, 0.5f, 48f,
                    SimpleMaterial.ShadingMode.LIT) // purple
    };

    public LightingGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(QUIT, List.of(new KeyBinding(KEY_ESC, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        TOGGLE_DIR, List.of(new KeyBinding(KEY_1, 0)),
                        TOGGLE_POINT, List.of(new KeyBinding(KEY_2, 0)),
                        TOGGLE_MOTION, List.of(new KeyBinding(KEY_3, 0))),
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
        torusHandle = renderer.upload(SimpleMesh.torus(0.8f, 0.3f, 48, 24));
        sphereHandle = renderer.upload(SimpleMesh.sphere(0.5f, 12, 12));

        System.out.println("=== Lighting Basics ===");
        System.out.println("Directional sun + orbiting point light on 5 materials.");
        System.out.println("1=toggle sun, 2=toggle point, 3=toggle motion");
        System.out.println("A/D=orbit, W/S=pitch, Q/E=zoom, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) { orbitYaw=30; orbitPitch=30; orbitDist=10; time=0; dirEnabled=true; pointEnabled=true; pointMoving=true; }
        if (frame.pressed(TOGGLE_DIR)) dirEnabled = !dirEnabled;
        if (frame.pressed(TOGGLE_POINT)) pointEnabled = !pointEnabled;
        if (frame.pressed(TOGGLE_MOTION)) pointMoving = !pointMoving;

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 20f);

        if (pointMoving) time += dt;

        // Camera
        float yr = (float)Math.toRadians(orbitYaw), pr = (float)Math.toRadians(orbitPitch);
        float camX = orbitDist*(float)(Math.cos(pr)*Math.cos(yr));
        float camY = orbitDist*(float)Math.sin(pr);
        float camZ = orbitDist*(float)(Math.cos(pr)*Math.sin(yr));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float[] proj = LightingRenderer.perspective(60f, (float)w/h, 0.1f, 100f);
        float[] view = LightingRenderer.lookAt(camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        float[] vp = LightingRenderer.multiply(proj, view);

        // Point light orbits at y=1.5, radius=4
        float ptX = 4f * (float)Math.cos(time * 0.8);
        float ptY = 1.5f + 0.5f * (float)Math.sin(time * 1.3);
        float ptZ = 4f * (float)Math.sin(time * 0.8);
        PointLight pointLight = new PointLight(ptX, ptY, ptZ, 0.9f, 0.7f, 1.0f, 2.0f, 8f);

        // Render
        renderer.beginScene(w, h, 0.15f, 0.12f, 0.18f);
        renderer.setDirectionalLight(DirectionalLight.SUN, dirEnabled);
        renderer.setPointLight(pointLight, pointEnabled);

        // 5 tori in an arc
        for (int i = 0; i < MATS.length; i++) {
            float angle = (float)(Math.PI * (i - 2) / 4);
            float px = 3.5f * (float)Math.sin(angle);
            float pz = 3.5f * (float)Math.cos(angle) - 1f;
            float[] model = LightingRenderer.translate(LightingRenderer.identity(), px, 0, pz);
            model = LightingRenderer.rotateY(model, time * 0.3f + i);
            float[] mvp = LightingRenderer.multiply(vp, model);
            renderer.drawMesh(torusHandle, mvp, model, MATS[i], camX, camY, camZ);
        }

        // Point light indicator (small unlit sphere)
        if (pointEnabled) {
            renderer.drawLightIndicator(sphereHandle, vp, pointLight);
        }

        // HUD
        renderer.drawText("LIGHTING BASICS - Directional + Point Light", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Sun: %s  Point: %s  Motion: %s",
                dirEnabled ? "ON" : "OFF", pointEnabled ? "ON" : "OFF", pointMoving ? "ON" : "OFF"),
                10, 35, 2f, 0.7f, 0.8f, 0.7f, w, h);
        renderer.drawText(String.format("Point light: (%.1f, %.1f, %.1f)", ptX, ptY, ptZ),
                10, 55, 1.8f, 0.6f, 0.6f, 0.7f, w, h);
        renderer.drawText("1=sun  2=point  3=motion  A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusHandle);
        renderer.deleteMesh(sphereHandle);
        renderer.shutdown();
        System.out.println("[Lighting] Done.");
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
