package org.dynamisengine.games.material;

import org.dynamisengine.games.material.subsystem.*;
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
 * Material basics - same mesh, different materials.
 *
 * Proves: SimpleMaterial record, shader parameter binding (Blinn-Phong),
 * visible differentiation (matte/glossy/unlit/metallic/wireframe),
 * material presets, geometry/appearance separation.
 *
 * Scene: 6 tori in a ring, each with a different material preset,
 * all using the exact same SimpleMesh and MeshHandle.
 */
public final class MaterialGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final AxisId ORBIT_H = new AxisId("orbitH");
    static final AxisId ORBIT_V = new AxisId("orbitV");
    static final AxisId ZOOM = new AxisId("zoom");
    private static final ContextId CTX = new ContextId("material");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_Q=81, KEY_E=69;
    private static final int KEY_ESC=256, KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final MaterialRenderer renderer = new MaterialRenderer();

    private MeshHandle torusHandle;
    private float orbitYaw = 20f, orbitPitch = 25f, orbitDist = 10f;
    private float rotAngle = 0f;

    public MaterialGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
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

        // One mesh, uploaded once, drawn 6 times with different materials
        SimpleMesh torus = SimpleMesh.torus(0.8f, 0.3f, 48, 24);
        torusHandle = renderer.upload(torus);

        System.out.println("=== Material Basics ===");
        System.out.println("Same mesh, 6 different materials (Blinn-Phong).");
        System.out.printf("Torus: %d verts, %d tris%n", torus.vertexCount(), torus.triangleCount());
        System.out.println("A/D=orbit, W/S=pitch, Q/E=zoom, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) { orbitYaw = 20f; orbitPitch = 25f; orbitDist = 10f; rotAngle = 0; }

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 4f, 20f);

        rotAngle += dt * 0.3f;

        float yawRad = (float) Math.toRadians(orbitYaw);
        float pitchRad = (float) Math.toRadians(orbitPitch);
        float camX = orbitDist * (float)(Math.cos(pitchRad) * Math.cos(yawRad));
        float camY = orbitDist * (float) Math.sin(pitchRad);
        float camZ = orbitDist * (float)(Math.cos(pitchRad) * Math.sin(yawRad));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float aspect = (float) w / h;

        float[] proj = MaterialRenderer.perspective(60f, aspect, 0.1f, 100f);
        float[] view = MaterialRenderer.lookAt(camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        float[] vp = MaterialRenderer.multiply(proj, view);

        renderer.beginScene(w, h);

        // Draw 6 tori in a ring, each with a different material
        SimpleMaterial[] mats = SimpleMaterial.PRESETS;
        int count = mats.length;
        float ringRadius = 3.5f;

        for (int i = 0; i < count; i++) {
            float angle = (float)(2 * Math.PI * i / count) + rotAngle;
            float px = ringRadius * (float) Math.cos(angle);
            float pz = ringRadius * (float) Math.sin(angle);

            // Each torus tilted slightly and spinning on its own axis
            float[] model = MaterialRenderer.translate(MaterialRenderer.identity(), px, 0, pz);
            model = MaterialRenderer.rotateY(model, angle + rotAngle * 2);

            float[] mvp = MaterialRenderer.multiply(vp, model);
            renderer.drawMesh(torusHandle, mvp, model, mats[i], camX, camY, camZ);
        }

        // HUD
        renderer.drawText("MATERIAL BASICS - Same Mesh, Different Surfaces", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);

        // Label each material
        for (int i = 0; i < count; i++) {
            float[] bc = mats[i].baseColor();
            renderer.drawText(String.format("%d: %s", i + 1, SimpleMaterial.PRESET_NAMES[i]),
                    10, 35 + i * 16, 1.8f, bc[0], bc[1], bc[2], w, h);
        }

        renderer.drawText("A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusHandle);
        renderer.shutdown();
        System.out.println("[Material] Done.");
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
