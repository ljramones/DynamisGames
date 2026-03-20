package org.dynamisengine.games.lepresets;

import org.dynamisengine.games.lepresets.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.light.api.scene.*;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.Map;

/**
 * LightEngine presets demo - proves first-use ergonomics.
 *
 * Shows 4 scene presets (showcase, studio, night, debug) that produce
 * real SceneDescriptor instances via the new SceneBuilder/preset API.
 * Cycles between them with 1-4 keys. Each preset is rendered through
 * the proven SceneRenderer path, demonstrating that presets produce
 * coherent, usable scene configurations with minimal code.
 *
 * The HUD shows the preset name and the SceneDescriptor details
 * (camera, lights, environment) to prove the presets are real.
 */
public final class PresetsGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId PRESET_1 = new ActionId("p1");
    static final ActionId PRESET_2 = new ActionId("p2");
    static final ActionId PRESET_3 = new ActionId("p3");
    static final ActionId PRESET_4 = new ActionId("p4");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("presets");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69;
    private static final int KEY_1=49,KEY_2=50,KEY_3=51,KEY_4=52;
    private static final int KEY_ESC=256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle torusMesh, sphereMesh, cubeMesh;

    // Current scene descriptor (from presets)
    private SceneDescriptor currentScene;
    private String currentPresetName = "showcase";
    private int currentPresetIndex = 0;

    // Camera overrides (orbit on top of preset camera)
    private float orbitYaw, orbitPitch, orbitDist;
    private float time = 0;

    private static final String[] PRESET_NAMES = {"Showcase", "Studio", "Night", "Debug"};

    public PresetsGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                        Map.entry(QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                        Map.entry(PRESET_1, List.of(new KeyBinding(KEY_1, 0))),
                        Map.entry(PRESET_2, List.of(new KeyBinding(KEY_2, 0))),
                        Map.entry(PRESET_3, List.of(new KeyBinding(KEY_3, 0))),
                        Map.entry(PRESET_4, List.of(new KeyBinding(KEY_4, 0)))),
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
        torusMesh = renderer.upload(SimpleMesh.torus(0.8f, 0.3f, 48, 24));
        sphereMesh = renderer.upload(SimpleMesh.sphere(0.5f, 24, 24));
        cubeMesh = renderer.upload(generateCube());

        applyPreset(0);

        System.out.println("=== LightEngine Presets Demo ===");
        System.out.println("1=Showcase  2=Studio  3=Night  4=Debug");
        System.out.println("A/D=orbit  W/S=pitch  Q/E=zoom  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(PRESET_1)) applyPreset(0);
        if (frame.pressed(PRESET_2)) applyPreset(1);
        if (frame.pressed(PRESET_3)) applyPreset(2);
        if (frame.pressed(PRESET_4)) applyPreset(3);

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 20f);

        time += dt;

        // Camera from orbit
        float yr = (float)Math.toRadians(orbitYaw), pr = (float)Math.toRadians(orbitPitch);
        float camX = orbitDist*(float)(Math.cos(pr)*Math.cos(yr));
        float camY = orbitDist*(float)Math.sin(pr);
        float camZ = orbitDist*(float)(Math.cos(pr)*Math.sin(yr));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float[] proj = SceneRenderer.perspective(60f, (float)w/h, 0.1f, 100f);
        float[] view = SceneRenderer.lookAt(camX, camY, camZ, 0, 1, 0, 0, 1, 0);
        float[] vp = SceneRenderer.multiply(proj, view);

        // Extract lighting from SceneDescriptor
        EnvironmentDesc env = currentScene.environment();
        float ambR = env != null ? env.ambientColor().x() : 0.12f;
        float ambG = env != null ? env.ambientColor().y() : 0.1f;
        float ambB = env != null ? env.ambientColor().z() : 0.14f;

        renderer.beginScene(w, h, ambR, ambG, ambB);

        // Apply lights from SceneDescriptor
        boolean dirSet = false, ptSet = false;
        for (LightDesc light : currentScene.lights()) {
            if (light.type() == LightType.DIRECTIONAL && !dirSet) {
                renderer.setDirectionalLight(new DirectionalLight(
                        light.direction().x(), light.direction().y(), light.direction().z(),
                        light.color().x(), light.color().y(), light.color().z(),
                        light.intensity()), true);
                dirSet = true;
            } else if (light.type() == LightType.POINT && !ptSet) {
                renderer.setPointLight(new PointLight(
                        light.position().x(), light.position().y(), light.position().z(),
                        light.color().x(), light.color().y(), light.color().z(),
                        light.intensity(), light.range()), true);
                ptSet = true;
            }
        }
        if (!dirSet) renderer.setDirectionalLight(null, false);
        if (!ptSet) renderer.setPointLight(null, false);

        // Draw gallery scene
        drawGalleryScene(vp, camX, camY, camZ);

        // HUD
        renderer.drawText("LIGHTENGINE PRESETS DEMO", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Active: [%d] %s", currentPresetIndex + 1, currentPresetName),
                10, 35, 2f, 0.9f, 0.9f, 0.5f, w, h);

        // Show SceneDescriptor details
        renderer.drawText(String.format("Scene: \"%s\"  Cameras: %d  Lights: %d",
                currentScene.sceneName(), currentScene.cameras().size(), currentScene.lights().size()),
                10, 55, 1.8f, 0.6f, 0.7f, 0.6f, w, h);
        if (!currentScene.cameras().isEmpty()) {
            CameraDesc cam = currentScene.cameras().get(0);
            renderer.drawText(String.format("Camera: %s  pos=(%.1f,%.1f,%.1f)  fov=%.0f",
                    cam.id(), cam.position().x(), cam.position().y(), cam.position().z(), cam.fovDegrees()),
                    10, 72, 1.6f, 0.5f, 0.6f, 0.5f, w, h);
        }
        if (env != null) {
            renderer.drawText(String.format("Ambient: (%.2f,%.2f,%.2f) x%.1f",
                    env.ambientColor().x(), env.ambientColor().y(), env.ambientColor().z(),
                    env.ambientIntensity()),
                    10, 88, 1.6f, 0.5f, 0.6f, 0.5f, w, h);
        }

        renderer.drawText("1=Showcase  2=Studio  3=Night  4=Debug  A/D/W/S=orbit  Q/E=zoom  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    private void applyPreset(int index) {
        currentPresetIndex = index;
        currentPresetName = PRESET_NAMES[index];

        // Build scene from presets - THIS IS THE KEY PART
        currentScene = switch (index) {
            case 0 -> ScenePresets.showcase().build();
            case 1 -> ScenePresets.studio().build();
            case 2 -> ScenePresets.nightScene()
                    .light(LightPresets.pointAccent(2, 1.5f, 0))
                    .build();
            case 3 -> ScenePresets.debug().build();
            default -> ScenePresets.showcase().build();
        };

        // Reset camera from preset
        if (!currentScene.cameras().isEmpty()) {
            CameraDesc cam = currentScene.cameras().get(0);
            // Derive orbit params from camera position
            float cx = cam.position().x(), cy = cam.position().y(), cz = cam.position().z();
            orbitDist = (float)Math.sqrt(cx*cx + cy*cy + cz*cz);
            orbitPitch = (float)Math.toDegrees(Math.asin(cy / orbitDist));
            orbitYaw = (float)Math.toDegrees(Math.atan2(cz, cx));
        }
    }

    private void drawGalleryScene(float[] vp, float camX, float camY, float camZ) {
        // Central hero torus
        float[] model = SceneRenderer.translate(SceneRenderer.identity(), 0, 1.2f, 0);
        model = SceneRenderer.rotateY(model, time * 0.3f);
        renderer.drawMesh(torusMesh, SceneRenderer.multiply(vp, model), model,
                SimpleMaterial.GOLD, camX, camY, camZ);

        // 4 display spheres
        SimpleMaterial[] mats = {SimpleMaterial.MATTE_RED, SimpleMaterial.GLOSSY_BLUE,
                SimpleMaterial.CHROME, new SimpleMaterial(new float[]{0.6f,0.3f,0.8f},
                0.12f, 0.7f, 0.5f, 48f, SimpleMaterial.ShadingMode.LIT)};
        for (int i = 0; i < 4; i++) {
            float angle = (float)(Math.PI / 2 * i) + 0.4f;
            float px = 3f * (float)Math.cos(angle);
            float pz = 3f * (float)Math.sin(angle);
            float[] m = SceneRenderer.translate(SceneRenderer.identity(), px, 0.8f, pz);
            renderer.drawMesh(sphereMesh, SceneRenderer.multiply(vp, m), m,
                    mats[i], camX, camY, camZ);
        }

        // Floor
        SimpleMaterial floor = new SimpleMaterial(new float[]{0.35f,0.32f,0.3f},
                0.25f, 0.6f, 0.05f, 4f, SimpleMaterial.ShadingMode.LIT);
        for (int gx = -3; gx <= 3; gx += 2)
            for (int gz = -3; gz <= 3; gz += 2) {
                float[] m = SceneRenderer.translate(SceneRenderer.identity(), gx, -0.15f, gz);
                renderer.drawMesh(cubeMesh, SceneRenderer.multiply(vp, m), m,
                        floor, camX, camY, camZ);
            }
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh); renderer.deleteMesh(sphereMesh); renderer.deleteMesh(cubeMesh);
        renderer.shutdown();
        System.out.println("[Presets] Done.");
    }

    private SimpleMesh generateCube() {
        float[] v = new float[24*6]; int[] idx = new int[36];
        float[][] fn={{0,0,1},{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        float[][][] fv={
            {{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},
            {{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}},
            {{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}},
            {{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}},
            {{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}},
            {{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}}};
        int vi=0,ii=0;
        for(int f=0;f<6;f++){for(int c=0;c<4;c++){
            v[vi++]=fv[f][c][0];v[vi++]=fv[f][c][1];v[vi++]=fv[f][c][2];
            v[vi++]=fn[f][0];v[vi++]=fn[f][1];v[vi++]=fn[f][2];}
            int b=f*4;idx[ii++]=b;idx[ii++]=b+1;idx[ii++]=b+2;idx[ii++]=b;idx[ii++]=b+2;idx[ii++]=b+3;}
        return new SimpleMesh(v,idx,24,12);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
