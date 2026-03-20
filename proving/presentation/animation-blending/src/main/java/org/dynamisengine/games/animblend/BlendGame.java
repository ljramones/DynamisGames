package org.dynamisengine.games.animblend;

import org.dynamisengine.animis.runtime.transform.PropertyBlender;
import org.dynamisengine.animis.transform.PropertyClip;
import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.games.animblend.subsystem.*;
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
 * Animation blending: smooth crossfade between two clips.
 *
 * Proves: PropertyBlender crossfade, weighted interpolation between
 * source/target clips, blend-weight progression, clean handoff
 * on completion. Two clips: idle (gentle) vs active (energetic).
 *
 * Press 1 for idle, 2 for active. Blend happens over 0.75s.
 */
public final class BlendGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId LOOP = new ActionId("loop");
    static final ActionId TO_IDLE = new ActionId("toIdle");
    static final ActionId TO_ACTIVE = new ActionId("toActive");
    static final ActionId SPEED_UP = new ActionId("speedUp");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("blend");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_SPACE=32,KEY_L=76;
    private static final int KEY_1=49,KEY_2=50,KEY_EQUAL=61;
    private static final int KEY_ESC=256,KEY_R=82;

    private static final float BLEND_DURATION = 0.75f;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle torusMesh, cubeMesh, sphereMesh;
    private PropertyClip idleClip, activeClip;
    private PropertyBlender blendState;

    private float orbitYaw = 30f, orbitPitch = 25f, orbitDist = 9f;
    private int transitionCount = 0;

    public BlendGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                        Map.entry(QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                        Map.entry(RESET, List.of(new KeyBinding(KEY_R, 0))),
                        Map.entry(PAUSE, List.of(new KeyBinding(KEY_SPACE, 0))),
                        Map.entry(LOOP, List.of(new KeyBinding(KEY_L, 0))),
                        Map.entry(TO_IDLE, List.of(new KeyBinding(KEY_1, 0))),
                        Map.entry(TO_ACTIVE, List.of(new KeyBinding(KEY_2, 0))),
                        Map.entry(SPEED_UP, List.of(new KeyBinding(KEY_EQUAL, 0)))),
                Map.of(ORBIT_H, List.of(
                                new AxisComposite2D(ORBIT_H, new AxisId("_"), KEY_A, KEY_D, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_H, new AxisId("_2"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f)),
                        ORBIT_V, List.of(
                                new AxisComposite2D(ORBIT_V, new AxisId("_3"), KEY_S, KEY_W, 0, 0, 1f),
                                new AxisComposite2D(ORBIT_V, new AxisId("_4"), KEY_DOWN, KEY_UP, 0, 0, 1f)),
                        ZOOM, List.of(
                                new AxisComposite2D(ZOOM, new AxisId("_5"), KEY_Q, KEY_E, 0, 0, 1f))),
                false);
        // TO_ACTIVE and SPEED_UP need to be checked via raw key since Map.of maxes at 5
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        renderer.initialize();
        torusMesh = renderer.upload(SimpleMesh.torus(1.0f, 0.35f, 48, 24));
        cubeMesh = renderer.upload(generateCube());
        sphereMesh = renderer.upload(SimpleMesh.sphere(0.5f, 12, 12));

        idleClip = DemoClips.idle();
        activeClip = DemoClips.active();
        blendState = new PropertyBlender(idleClip);

        System.out.println("=== Animation Blending ===");
        System.out.println("1=idle  2=active  (0.75s crossfade)");
        System.out.println("Space=pause  L=loop  R=reset  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) {
            blendState = new PropertyBlender(idleClip);
            orbitYaw=30; orbitPitch=25; orbitDist=9; transitionCount=0;
        }
        if (frame.pressed(PAUSE)) blendState.togglePause();
        if (frame.pressed(LOOP)) blendState.toggleLoop();

        if (frame.pressed(TO_IDLE)) {
            blendState.transitionTo(idleClip, BLEND_DURATION);
            transitionCount++;
            playSound(440f, 0.1f, 0.001f, 0.03f);
        }
        if (frame.pressed(TO_ACTIVE)) {
            blendState.transitionTo(activeClip, BLEND_DURATION);
            transitionCount++;
            playSound(660f, 0.12f, 0.001f, 0.03f);
        }

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 20f);

        blendState.update(dt);

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

        float heroY = blendState.sample("posY");
        PointLight pt = new PointLight(0, heroY + 2f, 0, 0.9f, 0.8f, 1.0f, 2.0f, 8f);

        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(DirectionalLight.SUN, true);
        renderer.setPointLight(pt, true);

        // Hero torus — blended animation
        {
            float posX = blendState.sample("posX");
            float posY = blendState.sample("posY");
            float rotY = blendState.sample("rotY");
            float scale = blendState.sample("scale");
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), posX, posY, 0);
            model = SceneRenderer.rotateY(model, rotY);
            float[] s = SceneRenderer.identity(); s[0]=scale; s[5]=scale; s[10]=scale;
            model = SceneRenderer.multiply(model, s);
            float[] mvp = SceneRenderer.multiply(vp, model);

            // Color shifts during blend
            float bw = blendState.blendWeight();
            boolean blending = blendState.isBlending();
            float cr = blending ? 1f * (1-bw) + 0.3f * bw : (blendState.targetClipName().equals("idle") ? 1f : 0.3f);
            float cg = blending ? 0.8f * (1-bw) + 0.9f * bw : (blendState.targetClipName().equals("idle") ? 0.8f : 0.9f);
            float cb = blending ? 0.3f * (1-bw) + 1f * bw : (blendState.targetClipName().equals("idle") ? 0.3f : 1f);
            SimpleMaterial mat = new SimpleMaterial(new float[]{cr, cg, cb}, 0.12f, 0.7f, 0.6f, 32f, SimpleMaterial.ShadingMode.LIT);
            renderer.drawMesh(torusMesh, mvp, model, mat, camX, camY, camZ);
        }

        // Floor
        SimpleMaterial floor = new SimpleMaterial(new float[]{0.35f,0.32f,0.3f}, 0.25f, 0.6f, 0.05f, 4f, SimpleMaterial.ShadingMode.LIT);
        for (int gx = -3; gx <= 3; gx += 2)
            for (int gz = -3; gz <= 3; gz += 2) {
                float[] model = SceneRenderer.translate(SceneRenderer.identity(), gx, -0.15f, gz);
                renderer.drawMesh(cubeMesh, SceneRenderer.multiply(vp, model), model, floor, camX, camY, camZ);
            }

        renderer.drawLightIndicator(sphereMesh, vp, pt);

        // HUD
        renderer.drawText("ANIMATION BLENDING - Crossfade", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);

        String blendStatus = blendState.isBlending()
                ? String.format("BLENDING %s -> %s  (%.0f%%)", blendState.sourceClipName(),
                    blendState.targetClipName(), blendState.blendWeight() * 100)
                : String.format("Playing: %s", blendState.targetClipName());
        renderer.drawText(blendStatus, 10, 35, 2f, 0.7f, 0.9f, 0.7f, w, h);

        renderer.drawText(String.format("posX=%.2f  posY=%.2f  rotY=%.2f  scale=%.2f",
                blendState.sample("posX"), blendState.sample("posY"),
                blendState.sample("rotY"), blendState.sample("scale")),
                10, 55, 1.8f, 0.6f, 0.7f, 0.6f, w, h);

        renderer.drawText(String.format("%s  Loop: %s  Speed: %.2fx  Transitions: %d",
                blendState.isPlaying() ? "PLAYING" : "PAUSED",
                blendState.isLooping() ? "ON" : "OFF",
                blendState.speed(), transitionCount),
                10, 75, 1.8f, 0.5f, 0.6f, 0.6f, w, h);

        // Blend weight bar
        if (blendState.isBlending()) {
            int barW = 200, barH = 8;
            int barX = 10, barY = 98;
            // Can't draw filled rects easily without scissor, just show text
            renderer.drawText(String.format("Blend: [%s]",
                    "#".repeat((int)(blendState.blendWeight() * 20)) +
                    "-".repeat(20 - (int)(blendState.blendWeight() * 20))),
                    10, 95, 1.8f, 0.8f, 0.8f, 0.3f, w, h);
        }

        renderer.drawText("1=idle  2=active  Space=pause  L=loop  R=reset  A/D/W/S=orbit  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh); renderer.deleteMesh(cubeMesh); renderer.deleteMesh(sphereMesh);
        renderer.shutdown();
        System.out.printf("[Blend] Transitions: %d%n", transitionCount);
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
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
