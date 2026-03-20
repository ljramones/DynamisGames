package org.dynamisengine.games.mgi;

import org.dynamisengine.games.mgi.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.mgi.MgiStaticMesh;
import org.dynamisengine.meshforge.mgi.MgiStaticMeshCodec;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * MGI basics -- proves canonical MGI runtime loading.
 *
 * Loads a .mgi file directly via MeshForge's MgiStaticMeshCodec,
 * converts to MeshData, packs, and renders. This is the first
 * module in the content-pipeline track.
 *
 * Key proof: .mgi loads, deserializes, renders correctly.
 */
public final class MgiGame implements WorldApplication {

    static final ActionId QUIT = new ActionId("quit");
    static final ActionId RESET = new ActionId("reset");
    static final AxisId ORBIT_H = new AxisId("oh");
    static final AxisId ORBIT_V = new AxisId("ov");
    static final AxisId ZOOM = new AxisId("zm");
    private static final ContextId CTX = new ContextId("mgi");
    private static final int KEY_W=87,KEY_A=65,KEY_S=83,KEY_D=68;
    private static final int KEY_UP=265,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262;
    private static final int KEY_Q=81,KEY_E=69,KEY_ESC=256,KEY_R=82;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final SceneRenderer renderer = new SceneRenderer();

    private MeshHandle mgiMeshHandle;
    private String loadStatus = "loading...";
    private int vertexCount = 0, indexCount = 0;

    private float orbitYaw = 30f, orbitPitch = 25f, orbitDist = 5f;
    private float time = 0;

    public MgiGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
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

        // Load .mgi directly via MeshForge canonical path
        Path mgiPath = Path.of("src/main/resources/assets/cube.mgi");
        try {
            byte[] bytes = Files.readAllBytes(mgiPath);
            MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
            MgiStaticMesh mgi = codec.read(bytes);
            MeshData meshData = MgiMeshDataCodec.toMeshData(mgi);

            // Pack for GPU
            PackedMesh packed = MeshPacker.pack(meshData, Packers.realtimeFast());
            RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);

            mgiMeshHandle = convertPayloadToHandle(payload);
            vertexCount = payload.vertexCount();
            indexCount = payload.indexCount();
            loadStatus = String.format("MGI loaded: cube.mgi (%d verts, %d indices)", vertexCount, indexCount);
        } catch (Exception e) {
            loadStatus = "FAILED: " + e.getMessage();
            System.err.println(loadStatus);
            e.printStackTrace();
            // Fallback so module is still runnable
            mgiMeshHandle = renderer.upload(SimpleMesh.sphere(0.8f, 24, 24));
        }

        System.out.println("=== MGI Basics ===");
        System.out.println(loadStatus);
        System.out.println("A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        if (frame.pressed(QUIT)) { context.requestStop(); return; }
        if (frame.pressed(RESET)) { orbitYaw=30; orbitPitch=25; orbitDist=5; time=0; }

        float oh = frame.axis(ORBIT_H), ov = frame.axis(ORBIT_V), zm = frame.axis(ZOOM);
        if (oh != 0) orbitYaw += oh * 90f * dt;
        if (ov != 0) orbitPitch = clamp(orbitPitch + ov * 60f * dt, 5f, 85f);
        if (zm != 0) orbitDist = clamp(orbitDist + zm * 5f * dt, 2f, 15f);
        time += dt;

        float yr = (float)Math.toRadians(orbitYaw), pr = (float)Math.toRadians(orbitPitch);
        float camX = orbitDist*(float)(Math.cos(pr)*Math.cos(yr));
        float camY = orbitDist*(float)Math.sin(pr);
        float camZ = orbitDist*(float)(Math.cos(pr)*Math.sin(yr));

        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        float[] proj = SceneRenderer.perspective(60f, (float)w/h, 0.1f, 100f);
        float[] view = SceneRenderer.lookAt(camX, camY, camZ, 0, 0, 0, 0, 1, 0);
        float[] vp = SceneRenderer.multiply(proj, view);

        renderer.beginScene(w, h, 0.12f, 0.1f, 0.14f);
        renderer.setDirectionalLight(new DirectionalLight(0.4f, 0.8f, 0.3f, 1f, 0.95f, 0.85f, 1f), true);
        renderer.setPointLight(new PointLight(2*(float)Math.cos(time), 2, 2*(float)Math.sin(time),
                0.8f, 0.6f, 1f, 2f, 8f), true);

        float[] model = SceneRenderer.rotateY(SceneRenderer.identity(), time * 0.4f);
        float[] mvp = SceneRenderer.multiply(vp, model);
        renderer.drawMesh(mgiMeshHandle, mvp, model, SimpleMaterial.GOLD, camX, camY, camZ);

        renderer.drawText("MGI BASICS - Content Pipeline Track", 10, 10, 2.2f, 1f, 1f, 0.3f, w, h);
        renderer.drawText(loadStatus, 10, 35, 1.8f, 0.3f, 0.9f, 0.3f, w, h);
        renderer.drawText("A/D=orbit  W/S=pitch  Q/E=zoom  R=reset  Esc=quit",
                10, h - 25, 1.8f, 0.4f, 0.4f, 0.5f, w, h);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.deleteMesh(mgiMeshHandle);
        renderer.shutdown();
        System.out.println("[MGI] Done.");
    }

    private MeshHandle convertPayloadToHandle(RuntimeGeometryPayload payload) {
        VertexLayout layout = payload.layout();
        ByteBuffer vb = payload.vertexBytes().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int vertCount = payload.vertexCount();
        int stride = layout.strideBytes();

        int posOff = -1, normOff = -1;
        for (var entry : layout.entries().values()) {
            String sem = entry.key().semantic().name();
            if ("POSITION".equals(sem)) posOff = entry.offsetBytes();
            else if ("NORMAL".equals(sem)) normOff = entry.offsetBytes();
        }

        float[] verts = new float[vertCount * 6];
        for (int v = 0; v < vertCount; v++) {
            int base = v * stride;
            int out = v * 6;
            if (posOff >= 0) {
                verts[out] = vb.getFloat(base + posOff);
                verts[out+1] = vb.getFloat(base + posOff + 4);
                verts[out+2] = vb.getFloat(base + posOff + 8);
            }
            if (normOff >= 0) {
                verts[out+3] = vb.getFloat(base + normOff);
                verts[out+4] = vb.getFloat(base + normOff + 4);
                verts[out+5] = vb.getFloat(base + normOff + 8);
            } else {
                verts[out+4] = 1f;
            }
        }

        int[] indices;
        if (payload.indexBytes() != null && payload.indexCount() > 0) {
            ByteBuffer ib = payload.indexBytes().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            indices = new int[payload.indexCount()];
            var indexType = payload.indexType();
            if (indexType != null && "UINT16".equals(indexType.name())) {
                for (int i = 0; i < indices.length; i++) indices[i] = Short.toUnsignedInt(ib.getShort(i*2));
            } else {
                for (int i = 0; i < indices.length; i++) indices[i] = ib.getInt(i*4);
            }
        } else {
            indices = new int[vertCount];
            for (int i = 0; i < vertCount; i++) indices[i] = i;
        }

        SimpleMesh mesh = new SimpleMesh(verts, indices, vertCount, indices.length / 3);
        return renderer.upload(mesh);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
