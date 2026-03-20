package org.dynamisengine.games.scene;

import org.dynamisengine.games.scene.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 capstone: a composed 3D scene from proven pieces.
 *
 * Gallery/plaza layout:
 * - Central hero torus (gold, large)
 * - 4 surrounding pedestals (spheres on pillars)
 * - Ring of accent tori at outer edge
 * - Floor grid of flat cubes
 * - Directional sun + orbiting point light
 * - All from reused SimpleMesh handles + SimpleMaterial presets
 *
 * Proves: multi-instance scene assembly, per-object transform/material,
 * shared GPU resources, coherent lighting across a composed space.
 */
public final class SceneGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId TOGGLE_DIR = new ActionId("tDir");
    static final ActionId TOGGLE_POINT = new ActionId("tPt");
    static final ActionId TOGGLE_ROTATE = new ActionId("tRot");
    static final ActionId TOGGLE_WIRE = new ActionId("tWire");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("scene");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_1=49,KEY_2=50,KEY_3=51,KEY_TAB=258;
    private static final int KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    // Shared GPU meshes
    private MeshHandle torusMesh;
    private MeshHandle sphereMesh;
    private MeshHandle cubeMesh;

    // Scene objects
    private final List<SceneObject> sceneObjects = new ArrayList<>();

    // Camera
    private float orbitYaw = 35f, orbitPitch = 30f, orbitDist = 14f;

    // State
    private float time = 0;
    private boolean dirEnabled = true, pointEnabled = true, autoRotate = true, wireframe = false;

    public SceneGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(QUIT, List.of(new KeyBinding(KEY_ESC, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        TOGGLE_DIR, List.of(new KeyBinding(KEY_1, 0)),
                        TOGGLE_POINT, List.of(new KeyBinding(KEY_2, 0)),
                        TOGGLE_ROTATE, List.of(new KeyBinding(KEY_3, 0))),
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

        // Upload shared meshes once
        torusMesh = renderer.upload(SimpleMesh.torus(1.0f, 0.35f, 48, 24));
        sphereMesh = renderer.upload(SimpleMesh.sphere(0.5f, 24, 24));
        cubeMesh = renderer.upload(SimpleMesh.sphere(0.5f, 4, 4)); // low-poly "cube-ish" for floor tiles
        // Actually generate a proper cube for floor
        cubeMesh = renderer.upload(generateCube());

        buildScene();

        System.out.println("=== Scene Basics - Phase 2 Capstone ===");
        System.out.printf("Scene: %d objects, 3 shared meshes%n", sceneObjects.size());
        System.out.println("1=sun  2=point  3=auto-rotate");
        System.out.println("A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit");
    }

    private void buildScene() {
        sceneObjects.clear();

        // === Central hero: large gold torus ===
        sceneObjects.add(new SceneObject(torusMesh, SimpleMaterial.GOLD,
                0, 1.5f, 0, 0, 1.5f, "Hero Torus"));

        // === 4 pedestals: sphere on a cube pillar ===
        float[][] pedestalPos = {{-4, 0, -4}, {4, 0, -4}, {-4, 0, 4}, {4, 0, 4}};
        SimpleMaterial[] pedestalMats = {
                SimpleMaterial.MATTE_RED, SimpleMaterial.GLOSSY_BLUE,
                SimpleMaterial.CHROME,
                new SimpleMaterial(new float[]{0.6f, 0.3f, 0.8f}, 0.12f, 0.7f, 0.5f, 48f,
                        SimpleMaterial.ShadingMode.LIT)
        };
        SimpleMaterial stone = new SimpleMaterial(
                new float[]{0.5f, 0.48f, 0.45f}, 0.2f, 0.7f, 0.1f, 8f,
                SimpleMaterial.ShadingMode.LIT);

        for (int i = 0; i < 4; i++) {
            float px = pedestalPos[i][0], pz = pedestalPos[i][2];
            // Pillar (tall cube)
            sceneObjects.add(new SceneObject(cubeMesh, stone,
                    px, 0.5f, pz, 0, 1.0f, "Pillar"));
            // Sphere on top
            sceneObjects.add(new SceneObject(sphereMesh, pedestalMats[i],
                    px, 1.5f, pz, 0, 0.7f, "Display Sphere"));
        }

        // === Outer ring: 8 small accent tori ===
        SimpleMaterial accent = new SimpleMaterial(
                new float[]{0.3f, 0.7f, 0.5f}, 0.1f, 0.6f, 0.4f, 24f,
                SimpleMaterial.ShadingMode.LIT);
        for (int i = 0; i < 8; i++) {
            float angle = (float)(2 * Math.PI * i / 8);
            float rx = 7f * (float) Math.cos(angle);
            float rz = 7f * (float) Math.sin(angle);
            sceneObjects.add(new SceneObject(torusMesh, accent,
                    rx, 0.5f, rz, angle, 0.5f, "Accent Torus"));
        }

        // === Floor grid ===
        SimpleMaterial floor = new SimpleMaterial(
                new float[]{0.35f, 0.32f, 0.3f}, 0.25f, 0.6f, 0.05f, 4f,
                SimpleMaterial.ShadingMode.LIT);
        for (int gx = -4; gx <= 4; gx += 2) {
            for (int gz = -4; gz <= 4; gz += 2) {
                sceneObjects.add(new SceneObject(cubeMesh, floor,
                        gx, -0.15f, gz, 0, 0.9f, "Floor"));
            }
        }
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) { orbitYaw=35; orbitPitch=30; orbitDist=14; time=0;
            dirEnabled=true; pointEnabled=true; autoRotate=true; }
        if (frame.pressed(TOGGLE_DIR)) dirEnabled = !dirEnabled;
        if (frame.pressed(TOGGLE_POINT)) pointEnabled = !pointEnabled;
        if (frame.pressed(TOGGLE_ROTATE)) autoRotate = !autoRotate;

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 6f * dt, 5f, 30f);

        if (autoRotate) time += dt;

        // Camera
        float yr = (float)Math.toRadians(orbitYaw), pr = (float)Math.toRadians(orbitPitch);
        float camX = orbitDist*(float)(Math.cos(pr)*Math.cos(yr));
        float camY = orbitDist*(float)Math.sin(pr);
        float camZ = orbitDist*(float)(Math.cos(pr)*Math.sin(yr));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float[] proj = SceneRenderer.perspective(60f, (float)w/h, 0.1f, 100f);
        float[] view = SceneRenderer.lookAt(camX, camY, camZ, 0, 1, 0, 0, 1, 0);
        float[] vp = SceneRenderer.multiply(proj, view);

        // Point light orbits
        float ptX = 5f * (float)Math.cos(time * 0.6);
        float ptY = 3f + (float)Math.sin(time * 0.9);
        float ptZ = 5f * (float)Math.sin(time * 0.6);
        PointLight pointLight = new PointLight(ptX, ptY, ptZ, 0.8f, 0.6f, 1.0f, 2.5f, 10f);

        // Render
        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(DirectionalLight.SUN, dirEnabled);
        renderer.setPointLight(pointLight, pointEnabled);

        // Draw all scene objects
        float heroRot = autoRotate ? time * 0.4f : 0;
        for (SceneObject obj : sceneObjects) {
            float extraRot = obj.label().equals("Hero Torus") ? heroRot : 0;
            float[] model = obj.buildModelMatrix(extraRot);
            float[] mvp = SceneRenderer.multiply(vp, model);

            if (wireframe) {
                // TODO: wireframe toggle if needed
            }
            renderer.drawMesh(obj.mesh(), mvp, model, obj.material(), camX, camY, camZ);
        }

        // Point light indicator
        if (pointEnabled) {
            renderer.drawLightIndicator(sphereMesh, vp, pointLight);
        }

        // HUD
        renderer.drawText("SCENE BASICS - Phase 2 Capstone", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Objects: %d  Meshes: 3 (shared)  Sun: %s  Point: %s  Rotate: %s",
                sceneObjects.size(), dirEnabled ? "ON" : "OFF",
                pointEnabled ? "ON" : "OFF", autoRotate ? "ON" : "OFF"),
                10, 35, 1.8f, 0.7f, 0.8f, 0.7f, w, h);
        renderer.drawText("1=sun  2=point  3=rotate  A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh);
        renderer.deleteMesh(sphereMesh);
        renderer.deleteMesh(cubeMesh);
        renderer.shutdown();
        System.out.printf("[Scene] Phase 2 complete. %d objects rendered.%n", sceneObjects.size());
    }

    /** Generate a simple axis-aligned cube (indexed, with normals). */
    private SimpleMesh generateCube() {
        // 24 verts (4 per face, unique normals), 36 indices
        float[] v = new float[24 * 6];
        int[] idx = new int[36];
        float[][] faceNormals = {{0,0,1},{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        float[][][] faceVerts = {
                {{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},    // front
                {{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}}, // back
                {{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}},     // right
                {{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}},  // left
                {{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}},      // top
                {{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}},  // bottom
        };
        int vi = 0, ii = 0;
        for (int f = 0; f < 6; f++) {
            for (int corner = 0; corner < 4; corner++) {
                v[vi++] = faceVerts[f][corner][0];
                v[vi++] = faceVerts[f][corner][1];
                v[vi++] = faceVerts[f][corner][2];
                v[vi++] = faceNormals[f][0];
                v[vi++] = faceNormals[f][1];
                v[vi++] = faceNormals[f][2];
            }
            int base = f * 4;
            idx[ii++] = base; idx[ii++] = base+1; idx[ii++] = base+2;
            idx[ii++] = base; idx[ii++] = base+2; idx[ii++] = base+3;
        }
        return new SimpleMesh(v, idx, 24, 12);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
