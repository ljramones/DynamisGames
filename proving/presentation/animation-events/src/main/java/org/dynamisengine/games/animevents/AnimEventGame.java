package org.dynamisengine.games.animevents;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.games.animevents.subsystem.*;
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
 * Animation events: timeline markers that trigger consequences.
 *
 * Proves: AnimationEvent markers on clips, crossing detection in
 * AnimationPlayer, one-shot dispatch with correct loop/pause/reset
 * behavior. Consequences: audio cue + color flash + event log.
 */
public final class AnimEventGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId PAUSE = new ActionId("pause");
    static final ActionId LOOP = new ActionId("loop");
    static final ActionId SPEED_UP = new ActionId("speedUp");
    static final ActionId SPEED_DOWN = new ActionId("speedDn");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("animevt");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_SPACE=32,KEY_L=76;
    private static final int KEY_EQUAL=61,KEY_MINUS=45;
    private static final int KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle torusMesh, sphereMesh, cubeMesh;
    private AnimationPlayer heroPlayer;
    private AnimationPlayer[] accentPlayers;

    // Camera
    private float orbitYaw = 30f, orbitPitch = 25f, orbitDist = 10f;

    // Event state
    private float flashTimer = 0;
    private float[] flashColor = {1, 1, 1};
    private int totalEventsFired = 0;
    private final Deque<String> eventLog = new ArrayDeque<>();
    private static final int MAX_LOG = 6;

    public AnimEventGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
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

        heroPlayer = new AnimationPlayer(AnimationClip.heroClipWithEvents());
        accentPlayers = new AnimationPlayer[4];
        for (int i = 0; i < 4; i++) {
            accentPlayers[i] = new AnimationPlayer(AnimationClip.orbitClip());
            for (int j = 0; j < i; j++) accentPlayers[i].update(1.5f);
        }

        System.out.println("=== Animation Events ===");
        System.out.println("Timeline markers trigger audio + flash + log.");
        System.out.println("Space=pause  L=loop  +=faster  R=reset  Esc=quit");
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
            totalEventsFired=0; eventLog.clear(); flashTimer=0;
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

        // Process events fired this frame
        for (AnimationEvent evt : heroPlayer.drainEvents()) {
            totalEventsFired++;
            String logLine = String.format("t=%.2f [%s] #%d", evt.time(), evt.name(), totalEventsFired);
            eventLog.addFirst(logLine);
            while (eventLog.size() > MAX_LOG) eventLog.removeLast();

            // Consequence: audio + flash
            switch (evt.name()) {
                case "pulse" -> {
                    playSound(660f, 0.15f, 0.001f, 0.04f);
                    flashColor = new float[]{0.3f, 0.9f, 1.0f};
                    flashTimer = 0.2f;
                }
                case "apex" -> {
                    playSound(880f, 0.2f, 0.001f, 0.06f);
                    flashColor = new float[]{1.0f, 1.0f, 0.3f};
                    flashTimer = 0.25f;
                }
                case "accent" -> {
                    playSound(440f, 0.12f, 0.002f, 0.05f);
                    flashColor = new float[]{1.0f, 0.4f, 0.8f};
                    flashTimer = 0.15f;
                }
            }
        }

        // Decay flash
        if (flashTimer > 0) flashTimer = Math.max(0, flashTimer - dt);

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

        float heroY = heroPlayer.sample("posY");
        PointLight pt = new PointLight(0, heroY + 2f, 0, 0.9f, 0.8f, 1.0f, 2.0f, 8f);

        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(DirectionalLight.SUN, true);
        renderer.setPointLight(pt, true);

        // Hero torus — material flashes on events
        {
            float posY = heroPlayer.sample("posY");
            float rotY = heroPlayer.sample("rotY");
            float scale = heroPlayer.sample("scale");
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), 0, posY, 0);
            model = SceneRenderer.rotateY(model, rotY);
            float[] s = SceneRenderer.identity(); s[0]=scale; s[5]=scale; s[10]=scale;
            model = SceneRenderer.multiply(model, s);
            float[] mvp = SceneRenderer.multiply(vp, model);

            // Blend material color with flash
            float flash = flashTimer > 0 ? flashTimer / 0.25f : 0;
            float br = 1.0f * (1-flash) + flashColor[0] * flash;
            float bg = 0.8f * (1-flash) + flashColor[1] * flash;
            float bb = 0.3f * (1-flash) + flashColor[2] * flash;
            SimpleMaterial heroMat = new SimpleMaterial(
                    new float[]{br, bg, bb}, 0.12f + flash * 0.3f, 0.7f, 0.6f, 32f,
                    SimpleMaterial.ShadingMode.LIT);
            renderer.drawMesh(torusMesh, mvp, model, heroMat, camX, camY, camZ);
        }

        // Accent tori
        SimpleMaterial[] accentMats = {SimpleMaterial.MATTE_RED, SimpleMaterial.GLOSSY_BLUE,
                SimpleMaterial.CHROME, new SimpleMaterial(new float[]{0.6f,0.3f,0.8f}, 0.12f, 0.7f, 0.5f, 48f, SimpleMaterial.ShadingMode.LIT)};
        for (int i = 0; i < 4; i++) {
            float baseAngle = (float)(Math.PI / 2 * i);
            float animRotY = accentPlayers[i].sample("rotY");
            float animPosY = accentPlayers[i].sample("posY");
            float angle = baseAngle + animRotY * 0.3f;
            float px = 4f * (float)Math.cos(angle), pz = 4f * (float)Math.sin(angle);
            float[] model = SceneRenderer.translate(SceneRenderer.identity(), px, animPosY, pz);
            model = SceneRenderer.rotateY(model, animRotY);
            float[] sm = SceneRenderer.identity(); sm[0]=0.5f; sm[5]=0.5f; sm[10]=0.5f;
            model = SceneRenderer.multiply(model, sm);
            renderer.drawMesh(torusMesh, SceneRenderer.multiply(vp, model), model, accentMats[i], camX, camY, camZ);
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
        renderer.drawText("ANIMATION EVENTS - Timeline Markers", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(String.format("Clip: %s  Time: %.2f/%.2fs  %s  Loop: %s  Speed: %.2fx  Events: %d",
                heroPlayer.clipName(), heroPlayer.currentTime(), heroPlayer.duration(),
                heroPlayer.isPlaying() ? "PLAYING" : "PAUSED",
                heroPlayer.isLooping() ? "ON" : "OFF",
                heroPlayer.speed(), totalEventsFired),
                10, 35, 1.8f, 0.7f, 0.8f, 0.7f, w, h);

        // Event log
        renderer.drawText("--- Event Log ---", w - 280, 10, 1.8f, 0.9f, 0.9f, 0.5f, w, h);
        int logY = 30;
        for (String line : eventLog) {
            renderer.drawText(line, w - 280, logY, 1.6f, 0.6f, 0.8f, 0.6f, w, h);
            logY += 14;
        }

        renderer.drawText("Space=pause  L=loop  +=faster  R=reset  A/D/W/S=orbit  Q/E=zoom  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(torusMesh); renderer.deleteMesh(sphereMesh); renderer.deleteMesh(cubeMesh);
        renderer.shutdown();
        System.out.printf("[AnimEvents] Total events fired: %d%n", totalEventsFired);
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
