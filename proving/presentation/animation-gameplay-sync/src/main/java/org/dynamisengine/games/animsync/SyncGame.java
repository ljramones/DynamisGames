package org.dynamisengine.games.animsync;

import org.dynamisengine.animis.clip.AnimationEvent;
import org.dynamisengine.animis.runtime.transform.PropertyBlender;
import org.dynamisengine.animis.runtime.transform.PropertyPlayer;
import org.dynamisengine.animis.transform.PropertyClip;
import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.games.animsync.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Animation gameplay sync: input starts action, animation timing delivers consequence.
 *
 * Proves: gameplay consequence owned by animation event timing (not input timing),
 * strike clip with windup/hit/recover markers, target state management,
 * hit counter, blend from idle→strike→idle.
 *
 * Press Space to strike. The target only reacts when the animation
 * reaches the "hit" event at 0.5s - NOT when Space is pressed.
 */
public final class SyncGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId STRIKE = new ActionId("strike");
    static final ActionId PAUSE = new ActionId("pause");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("sync");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_SPACE=32,KEY_P=80;
    private static final int KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle torusMesh, sphereMesh, cubeMesh;

    // Animation
    private PropertyClip idleClip, strikeClip;
    private PropertyBlender heroBlender;
    private boolean striking = false;
    private float strikeTime = 0; // track strike clip time independently for non-looping

    // Use a separate PropertyPlayer for strike to get events
    private PropertyPlayer strikePlayer;

    // Target state
    private enum TargetState { ACTIVE, HIT, RECOVERING }
    private TargetState targetState = TargetState.ACTIVE;
    private float targetRecoverTimer = 0;
    private float targetFlash = 0;

    // Stats
    private int hitCount = 0;
    private int strikeCount = 0;
    private final Deque<String> eventLog = new ArrayDeque<>();
    private static final int MAX_LOG = 5;

    // Camera
    private float orbitYaw = 20f, orbitPitch = 20f, orbitDist = 8f;

    public SyncGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.ofEntries(
                        Map.entry(QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                        Map.entry(RESET, List.of(new KeyBinding(KEY_R, 0))),
                        Map.entry(STRIKE, List.of(new KeyBinding(KEY_SPACE, 0))),
                        Map.entry(PAUSE, List.of(new KeyBinding(KEY_P, 0)))),
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
        sphereMesh = renderer.upload(SimpleMesh.sphere(0.6f, 24, 24));
        cubeMesh = renderer.upload(generateCube());

        idleClip = DemoClips.idleClip();
        strikeClip = DemoClips.strikeClip();
        heroBlender = new PropertyBlender(idleClip);
        strikePlayer = new PropertyPlayer(strikeClip);
        strikePlayer.toggleLoop(); // non-looping for strike

        System.out.println("=== Animation Gameplay Sync ===");
        System.out.println("Input starts action; animation timing delivers consequence.");
        System.out.println("Space=strike  P=pause  R=reset  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(PAUSE)) heroBlender.togglePause();
        if (frame.pressed(RESET)) {
            heroBlender = new PropertyBlender(idleClip);
            striking = false; strikeTime = 0;
            targetState = TargetState.ACTIVE; targetFlash = 0; targetRecoverTimer = 0;
            hitCount = 0; strikeCount = 0; eventLog.clear();
            orbitYaw = 20; orbitPitch = 20; orbitDist = 8;
        }

        // Start strike
        if (frame.pressed(STRIKE) && !striking) {
            striking = true;
            strikeTime = 0;
            strikePlayer = new PropertyPlayer(strikeClip);
            strikePlayer.toggleLoop(); // non-looping
            strikeCount++;
            logEvent("INPUT: Strike started (#%d)", strikeCount);
        }

        // Camera
        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 3f, 15f);

        // Advance animation
        if (heroBlender.isPlaying()) {
            if (striking) {
                strikeTime += dt;
                strikePlayer.update(dt);

                // Process strike events - THE KEY PART
                for (AnimationEvent evt : strikePlayer.drainEvents()) {
                    float absTime = evt.normalizedTime() * strikeClip.duration();
                    switch (evt.name()) {
                        case "windup" -> {
                            logEvent("t=%.2f WINDUP - preparing", absTime);
                            playSound(330f, 0.08f, 0.001f, 0.02f);
                        }
                        case "hit" -> {
                            logEvent("t=%.2f HIT - consequence fires!", absTime);
                            // GAMEPLAY CONSEQUENCE: only here, not at input time
                            if (targetState == TargetState.ACTIVE) {
                                targetState = TargetState.HIT;
                                targetFlash = 0.4f;
                                targetRecoverTimer = 1.5f;
                                hitCount++;
                                playSound(880f, 0.25f, 0.001f, 0.08f);
                            }
                        }
                        case "recover" -> {
                            logEvent("t=%.2f RECOVER - returning", absTime);
                            playSound(220f, 0.06f, 0.002f, 0.03f);
                        }
                    }
                }

                // End strike when clip finishes
                if (strikeTime >= strikeClip.duration()) {
                    striking = false;
                }
            }
            heroBlender.update(dt);
        }

        // Target recovery
        if (targetState == TargetState.HIT) {
            targetRecoverTimer -= dt;
            if (targetRecoverTimer <= 0) {
                targetState = TargetState.ACTIVE;
                logEvent("Target recovered");
            }
        }
        if (targetFlash > 0) targetFlash = Math.max(0, targetFlash - dt);

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

        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(DirectionalLight.SUN, true);
        renderer.setPointLight(new PointLight(0, 3, 2, 0.8f, 0.7f, 1f, 2f, 8f), true);

        // Hero torus - animated
        {
            float posZ, rotX, rotY, scale;
            if (striking) {
                posZ = strikePlayer.sample("posZ");
                rotX = strikePlayer.sample("rotX");
                rotY = strikePlayer.sample("rotY");
                scale = strikePlayer.sample("scale");
            } else {
                posZ = heroBlender.sample("posZ");
                rotX = heroBlender.sample("rotX");
                rotY = heroBlender.sample("rotY");
                scale = heroBlender.sample("scale", 1f);
            }
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), 0, 1.2f, posZ - 1.5f);
            model = SceneRenderer.rotateY(model, rotY);
            // Apply rotX (pitch) via a simple rotation
            float[] rotXMat = SceneRenderer.identity();
            float cx = (float)Math.cos(rotX), sx = (float)Math.sin(rotX);
            rotXMat[5] = cx; rotXMat[6] = sx; rotXMat[9] = -sx; rotXMat[10] = cx;
            model = SceneRenderer.multiply(model, rotXMat);
            float[] s = SceneRenderer.identity(); s[0]=scale; s[5]=scale; s[10]=scale;
            model = SceneRenderer.multiply(model, s);
            float[] mvp = SceneRenderer.multiply(vp, model);

            float heroR = striking ? 1f : 0.4f;
            float heroG = striking ? 0.5f : 0.8f;
            float heroB = striking ? 0.2f : 1f;
            SimpleMaterial heroMat = new SimpleMaterial(new float[]{heroR, heroG, heroB},
                    0.12f, 0.7f, 0.6f, 32f, SimpleMaterial.ShadingMode.LIT);
            renderer.drawMesh(torusMesh, mvp, model, heroMat, camX, camY, camZ);
        }

        // Target sphere
        {
            float flash = targetFlash / 0.4f;
            float tr, tg, tb;
            switch (targetState) {
                case ACTIVE -> { tr = 0.9f; tg = 0.2f; tb = 0.15f; }
                case HIT -> { tr = 1f * (1-flash) + 1f * flash;
                    tg = 0.2f * (1-flash) + 1f * flash;
                    tb = 0.15f * (1-flash) + 1f * flash; }
                default -> { tr = 0.5f; tg = 0.5f; tb = 0.5f; }
            }
            float targetScale = targetState == TargetState.HIT ? 0.8f + flash * 0.3f : 1.0f;
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), 0, 1.2f, 1.5f);
            float[] s = SceneRenderer.identity(); s[0]=targetScale; s[5]=targetScale; s[10]=targetScale;
            model = SceneRenderer.multiply(model, s);
            float[] mvp = SceneRenderer.multiply(vp, model);
            SimpleMaterial targetMat = new SimpleMaterial(new float[]{tr, tg, tb},
                    0.15f, 0.7f, 0.3f, 16f, SimpleMaterial.ShadingMode.LIT);
            renderer.drawMesh(sphereMesh, mvp, model, targetMat, camX, camY, camZ);
        }

        // Floor
        SimpleMaterial floor = new SimpleMaterial(new float[]{0.35f,0.32f,0.3f},
                0.25f, 0.6f, 0.05f, 4f, SimpleMaterial.ShadingMode.LIT);
        for (int gx = -2; gx <= 2; gx++)
            for (int gz = -2; gz <= 2; gz++) {
                float[] model = SceneRenderer.translate(SceneRenderer.identity(), gx, -0.15f, gz);
                renderer.drawMesh(cubeMesh, SceneRenderer.multiply(vp, model), model, floor, camX, camY, camZ);
            }

        // HUD
        renderer.drawText("ANIMATION GAMEPLAY SYNC", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Hits: %d  Strikes: %d  Target: %s  %s",
                hitCount, strikeCount, targetState,
                striking ? String.format("STRIKING %.2f/%.2fs", strikeTime, strikeClip.duration()) : "IDLE"),
                10, 35, 1.8f, 0.7f, 0.8f, 0.7f, w, h);

        // Event log
        renderer.drawText("--- Event Log ---", w - 320, 10, 1.8f, 0.9f, 0.9f, 0.5f, w, h);
        int logY = 30;
        for (String line : eventLog) {
            renderer.drawText(line, w - 320, logY, 1.5f, 0.6f, 0.8f, 0.6f, w, h);
            logY += 13;
        }

        renderer.drawText("Space=strike  P=pause  R=reset  A/D/W/S=orbit  Q/E=zoom  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh); renderer.deleteMesh(sphereMesh); renderer.deleteMesh(cubeMesh);
        renderer.shutdown();
        System.out.printf("[Sync] Hits: %d  Strikes: %d%n", hitCount, strikeCount);
    }

    private void logEvent(String fmt, Object... args) {
        eventLog.addFirst(String.format(fmt, args));
        while (eventLog.size() > MAX_LOG) eventLog.removeLast();
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
