package org.dynamisengine.games.mesh;

import org.dynamisengine.games.mesh.subsystem.*;
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
 * First real mesh rendering in the Dynamis proving ladder.
 *
 * Proves: indexed triangle mesh generation, GPU upload (VAO/VBO/EBO),
 * normal-based shading, model transforms, orbit camera, wireframe toggle.
 *
 * Scene: a torus (unmistakably 3D) with slow auto-rotation,
 * plus a smaller sphere, both with hemispheric + directional shading.
 */
public final class MeshGame implements WorldApplication {

    // Input
    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId WIREFRAME = new ActionId("wireframe");
    static final AxisId ORBIT_H = new AxisId("orbitH");
    static final AxisId ORBIT_V = new AxisId("orbitV");
    static final AxisId ZOOM = new AxisId("zoom");
    private static final ContextId CTX = new ContextId("mesh");
    private static final int KEY_W=87, KEY_A=65, KEY_S=83, KEY_D=68;
    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262;
    private static final int KEY_Q=81, KEY_E=69, KEY_TAB=258;
    private static final int KEY_ESC=256, KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final MeshRenderer renderer = new MeshRenderer();

    // Meshes
    private MeshHandle torusHandle;
    private MeshHandle sphereHandle;
    private SimpleMesh torusMesh;
    private SimpleMesh sphereMesh;

    // Camera
    private float orbitYaw = 30f;
    private float orbitPitch = 25f;
    private float orbitDist = 6f;

    // State
    private float rotationAngle = 0f;
    private boolean wireframe = false;

    public MeshGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(QUIT, List.of(new KeyBinding(KEY_ESC, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        WIREFRAME, List.of(new KeyBinding(KEY_TAB, 0))),
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

        // Generate meshes
        torusMesh = SimpleMesh.torus(1.0f, 0.4f, 48, 24);
        sphereMesh = SimpleMesh.sphere(0.5f, 24, 24);

        // Upload to GPU
        torusHandle = renderer.upload(torusMesh);
        sphereHandle = renderer.upload(sphereMesh);

        System.out.println("=== Mesh Basics ===");
        System.out.printf("Torus: %d verts, %d tris%n", torusMesh.vertexCount(), torusMesh.triangleCount());
        System.out.printf("Sphere: %d verts, %d tris%n", sphereMesh.vertexCount(), sphereMesh.triangleCount());
        System.out.println("A/D=orbit, W/S=pitch, Q/E=zoom, Tab=wireframe, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(WIREFRAME)) wireframe = !wireframe;
        if (frame.pressed(RESET)) {
            orbitYaw = 30f; orbitPitch = 25f; orbitDist = 6f; rotationAngle = 0;
        }

        // Camera control
        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 2f, 15f);

        // Auto-rotate
        rotationAngle += dt * 0.5f;

        // Camera position
        float yawRad = (float) Math.toRadians(orbitYaw);
        float pitchRad = (float) Math.toRadians(orbitPitch);
        float camX = orbitDist * (float)(Math.cos(pitchRad) * Math.cos(yawRad));
        float camY = orbitDist * (float) Math.sin(pitchRad);
        float camZ = orbitDist * (float)(Math.cos(pitchRad) * Math.sin(yawRad));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float aspect = (float) w / h;

        float[] proj = MeshRenderer.perspective(60f, aspect, 0.1f, 100f);
        float[] view = MeshRenderer.lookAt(camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        float[] vp = MeshRenderer.multiply(proj, view);

        // Render
        renderer.beginScene(w, h);

        // Torus: centered, auto-rotating
        float[] torusModel = MeshRenderer.rotateY(MeshRenderer.identity(), rotationAngle);
        float[] torusMVP = MeshRenderer.multiply(vp, torusModel);

        if (wireframe) {
            renderer.drawMeshWireframe(torusHandle, torusMVP, torusModel, 0.4f, 0.8f, 1.0f);
        } else {
            renderer.drawMesh(torusHandle, torusMVP, torusModel, 0.4f, 0.8f, 1.0f);
        }

        // Sphere: offset to the right, counter-rotating
        float[] sphereModel = MeshRenderer.translate(MeshRenderer.identity(), 2.5f, 0, 0);
        sphereModel = MeshRenderer.rotateY(sphereModel, -rotationAngle * 2);
        float[] sphereMVP = MeshRenderer.multiply(vp, sphereModel);

        if (wireframe) {
            renderer.drawMeshWireframe(sphereHandle, sphereMVP, sphereModel, 1.0f, 0.6f, 0.3f);
        } else {
            renderer.drawMesh(sphereHandle, sphereMVP, sphereModel, 1.0f, 0.6f, 0.3f);
        }

        // HUD
        renderer.drawText("MESH BASICS - First Real Geometry", 10, 10, 2.5f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Torus: %d tris  Sphere: %d tris  %s",
                torusMesh.triangleCount(), sphereMesh.triangleCount(),
                wireframe ? "[WIREFRAME]" : "[SOLID]"),
                10, 35, 2f, 0.7f, 0.8f, 0.7f, w, h);
        renderer.drawText(String.format("Camera: yaw=%.0f pitch=%.0f dist=%.1f",
                orbitYaw, orbitPitch, orbitDist),
                10, 55, 2f, 0.6f, 0.7f, 0.6f, w, h);
        renderer.drawText("A/D=orbit  W/S=pitch  Q/E=zoom  Tab=wireframe  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusHandle);
        renderer.deleteMesh(sphereHandle);
        renderer.shutdown();
        System.out.println("[Mesh] Done.");
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
