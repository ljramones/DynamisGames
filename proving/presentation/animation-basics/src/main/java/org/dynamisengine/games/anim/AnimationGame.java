package org.dynamisengine.games.anim;

import org.dynamisengine.animis.runtime.transform.PropertyPlayer;
import org.dynamisengine.animis.transform.PropertyClip;
import org.dynamisengine.games.anim.subsystem.*;
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
 * Phase 3 opener: time-based animation in a composed scene.
 *
 * Proves: AnimationClip, TransformChannel, Keyframe, AnimationPlayer,
 * linear interpolation, loop/pause/reset/speed, animation-driven transforms
 * rendered through the proven scene stack.
 *
 * Scene: animated hero torus (bobs, rotates, pulses) surrounded by
 * 4 gently orbiting accent tori, over a static floor with lighting.
 */
public final class AnimationGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId LOOP = new ActionId("loop");
    static final ActionId SPEED_UP = new ActionId("speedUp");
    static final ActionId SPEED_DOWN = new ActionId("speedDn");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("anim");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_SPACE=32,KEY_L=76;
    private static final int KEY_MINUS=45,KEY_EQUAL=61;
    private static final int KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle torusMesh, sphereMesh, cubeMesh;

    // Animation
    private PropertyPlayer heroPlayer;
    private PropertyPlayer[] accentPlayers;

    // Camera
    private float orbitYaw = 30f, orbitPitch = 25f, orbitDist = 10f;

    public AnimationGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(QUIT, List.of(new KeyBinding(KEY_ESC, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        PAUSE, List.of(new KeyBinding(KEY_SPACE, 0)),
                        LOOP, List.of(new KeyBinding(KEY_L, 0)),
                        SPEED_UP, List.of(new KeyBinding(KEY_EQUAL, 0))),
                Map.of(ORBIT_H, List.of(
                                new AxisComposite2D(ORBIT_H, new AxisId("_"), KEY_A, KEY_D, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_H, new AxisId("_2"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f)),
                        ORBIT_V, List.of(
                                new AxisComposite2D(ORBIT_V, new AxisId("_3"), KEY_S, KEY_W, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_V, new AxisId("_4"), KEY_DOWN, KEY_UP, 0, 0, 1f)),
                        ZOOM, List.of(
                                new AxisComposite2D(ZOOM, new AxisId("_5"), KEY_Q, KEY_E, 0, 0, 1f))),
                false);
        // SPEED_DOWN needs separate binding since Map.of() maxes at 5
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        renderer.initialize();
        torusMesh = renderer.upload(SimpleMesh.torus(1.0f, 0.35f, 48, 24));
        sphereMesh = renderer.upload(SimpleMesh.sphere(0.5f, 12, 12));
        cubeMesh = renderer.upload(generateCube());

        heroPlayer = new PropertyPlayer(DemoClips.heroClip());

        accentPlayers = new PropertyPlayer[4];
        for (int i = 0; i < 4; i++) {
            accentPlayers[i] = new PropertyPlayer(DemoClips.orbitClip());
            // Stagger start times
            for (int j = 0; j < i; j++) accentPlayers[i].update(1.5f);
        }

        System.out.println("=== Animation Basics - Phase 3 Opener ===");
        System.out.println("Space=pause  L=loop  +/-=speed  R=reset  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) {
            heroPlayer.reset(); for (var p : accentPlayers) p.reset();
            orbitYaw=30; orbitPitch=25; orbitDist=10;
        }
        if (frame.pressed(PAUSE)) heroPlayer.togglePause();
        if (frame.pressed(LOOP)) heroPlayer.toggleLoop();
        if (frame.pressed(SPEED_UP)) heroPlayer.setSpeed(Math.min(heroPlayer.speed() + 0.25f, 4f));

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 20f);

        // Advance animations
        heroPlayer.update(dt);
        for (var p : accentPlayers) p.update(dt);

        // Camera
        float yr = (float)Math.toRadians(orbitYaw), pr = (float)Math.toRadians(orbitPitch);
        float camX = orbitDist*(float)(Math.cos(pr)*Math.cos(yr));
        float camY = orbitDist*(float)Math.sin(pr);
        float camZ = orbitDist*(float)(Math.cos(pr)*Math.sin(yr));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float[] proj = SceneRenderer.perspective(60f, (float)w/h, 0.1f, 100f);
        float[] view = SceneRenderer.lookAt(camX, camY, camZ, 0, 1.5f, 0, 0, 1, 0);
        float[] vp = SceneRenderer.multiply(proj, view);

        // Point light follows hero
        float heroY = heroPlayer.sample("posY");
        PointLight pt = new PointLight(0, heroY + 2f, 0, 0.9f, 0.8f, 1.0f, 2.0f, 8f);

        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(DirectionalLight.SUN, true);
        renderer.setPointLight(pt, true);

        // Hero torus — animated by clip
        {
            float posY = heroPlayer.sample("posY");
            float rotY = heroPlayer.sample("rotY");
            float scale = heroPlayer.sample("scale");
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), 0, posY, 0);
            model = SceneRenderer.rotateY(model, rotY);
            float[] s = SceneRenderer.identity(); s[0]=scale; s[5]=scale; s[10]=scale;
            model = SceneRenderer.multiply(model, s);
            float[] mvp = SceneRenderer.multiply(vp, model);
            renderer.drawMesh(torusMesh, mvp, model, SimpleMaterial.GOLD, camX, camY, camZ);
        }

        // 4 accent tori orbiting at radius 4
        float[][] accentAngles = {{0}, {(float)(Math.PI/2)}, {(float)Math.PI}, {(float)(3*Math.PI/2)}};
        SimpleMaterial[] accentMats = {SimpleMaterial.MATTE_RED, SimpleMaterial.GLOSSY_BLUE,
                SimpleMaterial.CHROME, new SimpleMaterial(new float[]{0.6f,0.3f,0.8f}, 0.12f, 0.7f, 0.5f, 48f, SimpleMaterial.ShadingMode.LIT)};
        for (int i = 0; i < 4; i++) {
            float baseAngle = accentAngles[i][0];
            float animRotY = accentPlayers[i].sample("rotY");
            float animPosY = accentPlayers[i].sample("posY");
            float angle = baseAngle + animRotY * 0.3f;
            float px = 4f * (float)Math.cos(angle);
            float pz = 4f * (float)Math.sin(angle);
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), px, animPosY, pz);
            model = SceneRenderer.rotateY(model, animRotY);
            float[] sm = SceneRenderer.identity(); sm[0]=0.5f; sm[5]=0.5f; sm[10]=0.5f;
            model = SceneRenderer.multiply(model, sm);
            float[] mvp = SceneRenderer.multiply(vp, model);
            renderer.drawMesh(torusMesh, mvp, model, accentMats[i], camX, camY, camZ);
        }

        // Floor
        SimpleMaterial floor = new SimpleMaterial(new float[]{0.35f,0.32f,0.3f}, 0.25f, 0.6f, 0.05f, 4f, SimpleMaterial.ShadingMode.LIT);
        for (int gx = -3; gx <= 3; gx += 2) {
            for (int gz = -3; gz <= 3; gz += 2) {
                float[] model = SceneRenderer.translate(SceneRenderer.identity(), gx, -0.15f, gz);
                float[] mvp = SceneRenderer.multiply(vp, model);
                renderer.drawMesh(cubeMesh, mvp, model, floor, camX, camY, camZ);
            }
        }

        // Light indicator
        renderer.drawLightIndicator(sphereMesh, vp, pt);

        // HUD
        renderer.drawText("ANIMATION BASICS - Phase 3 Opener", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Clip: %s  Time: %.2f/%.2fs  %s  Loop: %s  Speed: %.2fx",
                heroPlayer.clipName(), heroPlayer.currentTime(), heroPlayer.duration(),
                heroPlayer.isPlaying() ? "PLAYING" : "PAUSED",
                heroPlayer.isLooping() ? "ON" : "OFF",
                heroPlayer.speed()),
                10, 35, 1.8f, 0.7f, 0.8f, 0.7f, w, h);
        renderer.drawText(String.format("posY=%.2f  rotY=%.2f  scale=%.2f",
                heroPlayer.sample("posY"), heroPlayer.sample("rotY"), heroPlayer.sample("scale")),
                10, 55, 1.8f, 0.6f, 0.7f, 0.6f, w, h);
        renderer.drawText("Space=pause  L=loop  +=faster  R=reset  A/D/W/S=orbit  Q/E=zoom  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh);
        renderer.deleteMesh(sphereMesh);
        renderer.deleteMesh(cubeMesh);
        renderer.shutdown();
        System.out.println("[Animation] Phase 3 started.");
    }

    private SimpleMesh generateCube() {
        float[] v = new float[24 * 6]; int[] idx = new int[36];
        float[][] fn = {{0,0,1},{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        float[][][] fv = {
                {{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}},
                {{.5f,-.5f,-.5f},{-.5f,-.5f,-.5f},{-.5f,.5f,-.5f},{.5f,.5f,-.5f}},
                {{.5f,-.5f,.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{.5f,.5f,.5f}},
                {{-.5f,-.5f,-.5f},{-.5f,-.5f,.5f},{-.5f,.5f,.5f},{-.5f,.5f,-.5f}},
                {{-.5f,.5f,.5f},{.5f,.5f,.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f}},
                {{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,-.5f,.5f},{-.5f,-.5f,.5f}},
        };
        int vi=0, ii=0;
        for (int f=0;f<6;f++){for(int c=0;c<4;c++){
            v[vi++]=fv[f][c][0];v[vi++]=fv[f][c][1];v[vi++]=fv[f][c][2];
            v[vi++]=fn[f][0];v[vi++]=fn[f][1];v[vi++]=fn[f][2];}
            int b=f*4;idx[ii++]=b;idx[ii++]=b+1;idx[ii++]=b+2;idx[ii++]=b;idx[ii++]=b+2;idx[ii++]=b+3;}
        return new SimpleMesh(v, idx, 24, 12);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
