package org.dynamisengine.games.debugdraw;

import org.dynamisengine.collision.bounds.Aabb;
import org.dynamisengine.collision.contact.ContactManifold3D;
import org.dynamisengine.collision.contact.ContactPoint3D;
import org.dynamisengine.collision.debug.CollisionDebugSnapshot3D;
import org.dynamisengine.collision.events.CollisionEvent;
import org.dynamisengine.collision.events.CollisionEventType;
import org.dynamisengine.collision.narrowphase.CollisionManifold3D;
import org.dynamisengine.collision.pipeline.CollisionPair;
import org.dynamisengine.debug.api.draw.DebugLineCommand;
import org.dynamisengine.debug.api.draw.DepthMode;
import org.dynamisengine.debug.bridge.draw.CollisionDebugDraw;
import org.dynamisengine.debug.bridge.draw.SceneGraphDebugDraw;
import org.dynamisengine.debug.bridge.draw.TerrainDebugDraw;
import org.dynamisengine.debug.draw.DebugDrawQueue;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Debug Draw Basics — proves the full debug draw pipeline end-to-end.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>DebugDrawQueue with convenience methods and bridge helpers</li>
 *   <li>CollisionDebugDraw — AABBs, contacts, normals</li>
 *   <li>TerrainDebugDraw — chunk bounds with LOD color coding</li>
 *   <li>SceneGraphDebugDraw — node bounds</li>
 *   <li>Depth mode toggle (TESTED vs ALWAYS_VISIBLE)</li>
 *   <li>Per-frame queue lifecycle (no frame bleed)</li>
 * </ul>
 *
 * <p>Controls:
 * <pre>
 *   1 — toggle collision debug draw
 *   2 — toggle terrain debug draw
 *   3 — toggle scene graph debug draw
 *   4 — toggle depth mode (TESTED / ALWAYS_VISIBLE)
 *   Tab — toggle all debug draw
 *   Esc — quit
 * </pre>
 */
public final class DebugDrawBasicsGame implements WorldApplication {

    // Input actions
    static final ActionId TOGGLE_COLLISION = new ActionId("toggleCollision");
    static final ActionId TOGGLE_TERRAIN = new ActionId("toggleTerrain");
    static final ActionId TOGGLE_SCENEGRAPH = new ActionId("toggleScenegraph");
    static final ActionId TOGGLE_DEPTH = new ActionId("toggleDepth");
    static final ActionId TOGGLE_ALL = new ActionId("toggleAll");
    static final ActionId QUIT = new ActionId("quit");
    private static final ContextId CTX = new ContextId("debugdraw");

    private static final int KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52;
    private static final int KEY_TAB = 258, KEY_ESC = 256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final DebugDrawRenderer renderer = new DebugDrawRenderer();
    private final DebugDrawQueue queue = new DebugDrawQueue();

    // Toggle state
    private boolean showCollision = true;
    private boolean showTerrain = true;
    private boolean showSceneGraph = true;
    private boolean debugEnabled = true;
    private boolean depthTested = true; // true = TESTED, false = ALWAYS_VISIBLE

    // Camera orbit
    private float cameraAngle = 0f;

    // Synthetic scene data
    private float time = 0f;

    public DebugDrawBasicsGame(WindowSubsystem windowSub, WindowInputSubsystem inputSub) {
        this.windowSub = windowSub;
        this.inputSub = inputSub;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(TOGGLE_COLLISION, List.of(new KeyBinding(KEY_1, 0)),
                        TOGGLE_TERRAIN, List.of(new KeyBinding(KEY_2, 0)),
                        TOGGLE_SCENEGRAPH, List.of(new KeyBinding(KEY_3, 0)),
                        TOGGLE_DEPTH, List.of(new KeyBinding(KEY_4, 0)),
                        TOGGLE_ALL, List.of(new KeyBinding(KEY_TAB, 0)),
                        QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        renderer.initialize();
        System.out.println("=== Debug Draw Basics ===");
        System.out.println("Proving: DebugDrawQueue + CollisionDebugDraw + TerrainDebugDraw + SceneGraphDebugDraw");
        System.out.println("1=collision  2=terrain  3=scenegraph  4=depth mode  Tab=all  Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame != null) {
            if (frame.pressed(QUIT)) { context.requestStop(); return; }
            if (frame.pressed(TOGGLE_COLLISION)) showCollision = !showCollision;
            if (frame.pressed(TOGGLE_TERRAIN)) showTerrain = !showTerrain;
            if (frame.pressed(TOGGLE_SCENEGRAPH)) showSceneGraph = !showSceneGraph;
            if (frame.pressed(TOGGLE_DEPTH)) {
                depthTested = !depthTested;
                System.out.println("Depth mode: " + (depthTested ? "TESTED" : "ALWAYS_VISIBLE"));
            }
            if (frame.pressed(TOGGLE_ALL)) {
                debugEnabled = !debugEnabled;
                System.out.println("Debug draw: " + (debugEnabled ? "ON" : "OFF"));
            }
        }

        time += dt;
        cameraAngle += dt * 0.3f;

        // === Build debug draw commands ===
        if (debugEnabled) {
            if (showCollision) buildCollisionDebugDraw();
            if (showTerrain) buildTerrainDebugDraw();
            if (showSceneGraph) buildSceneGraphDebugDraw();

            // Direct API: draw coordinate axes (always visible)
            float axisLen = 3f;
            queue.submit(DebugLineCommand.of(0, 0, 0, axisLen, 0, 0, 1, 0, 0, DepthMode.ALWAYS_VISIBLE)); // X red
            queue.submit(DebugLineCommand.of(0, 0, 0, 0, axisLen, 0, 0, 1, 0, DepthMode.ALWAYS_VISIBLE)); // Y green
            queue.submit(DebugLineCommand.of(0, 0, 0, 0, 0, axisLen, 0, 0, 1, DepthMode.ALWAYS_VISIBLE)); // Z blue
        }

        // === Render ===
        var ws = windowSub.window().framebufferSize();
        int w = ws.width(), h = ws.height();
        glViewport(0, 0, w, h);
        glClearColor(0.08f, 0.08f, 0.12f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        // Simple perspective + orbit camera
        float aspect = (float) w / Math.max(h, 1);
        float[] viewProj = buildViewProjMatrix(aspect);

        // Render debug draw commands
        var commands = queue.activeCommands();
        if (!commands.isEmpty()) {
            renderer.render(commands, viewProj);
        }

        // Clear queue for next frame (fire-and-forget contract)
        queue.endFrame(dt);

        windowSub.window().swapBuffers();
    }

    @Override
    public void shutdown(GameContext context) {
        renderer.shutdown();
        System.out.println("[DebugDrawBasics] Shutdown complete.");
    }

    // --- Synthetic debug data generators ---

    private void buildCollisionDebugDraw() {
        // Two boxes that oscillate toward/away from each other
        float offset = (float) Math.sin(time * 1.5) * 0.8f;

        String objA = "boxA";
        String objB = "boxB";

        var itemA = new CollisionDebugSnapshot3D.ItemBounds<>(objA,
                new Aabb(-2 - offset, -0.5, -0.5, -1 - offset, 0.5, 0.5));
        var itemB = new CollisionDebugSnapshot3D.ItemBounds<>(objB,
                new Aabb(1 + offset, -0.5, -0.5, 2 + offset, 0.5, 0.5));

        // Generate contacts when close enough
        List<CollisionDebugSnapshot3D.Contact<String>> contacts;
        if (Math.abs(offset) < 0.3f) {
            var pair = new CollisionPair<>(objA, objB);
            var manifold = new CollisionManifold3D(1, 0, 0, 0.1);
            CollisionEventType eventType = offset > 0
                    ? CollisionEventType.EXIT
                    : (Math.abs(offset) < 0.1f ? CollisionEventType.STAY : CollisionEventType.ENTER);
            contacts = List.of(
                    new CollisionDebugSnapshot3D.Contact<>(pair, eventType, true,
                            manifold, new ContactPoint3D(0, 0, 0))
            );
        } else {
            contacts = List.of();
        }

        var snapshot = new CollisionDebugSnapshot3D<>(List.of(itemA, itemB), contacts);
        CollisionDebugDraw.draw(snapshot, queue);
    }

    private void buildTerrainDebugDraw() {
        // 3×3 grid of terrain chunks at different LOD levels
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int lod = Math.abs(x) + Math.abs(z); // center = LOD 0, corners = LOD 2
                TerrainDebugDraw.drawChunkBounds(queue,
                        x * 4f, -2f, z * 4f - 8f,
                        1.8f, 0.3f, 1.8f,
                        lod);
            }
        }
    }

    private void buildSceneGraphDebugDraw() {
        // A few scene nodes with bounds
        float bob = (float) Math.sin(time * 2f) * 0.3f;
        SceneGraphDebugDraw.drawNodeBounds(queue, 0, 3 + bob, 0, 0.8f, 0.8f, 0.8f);
        SceneGraphDebugDraw.drawNodeBounds(queue, 3, 2, -3, 0.5f, 1.2f, 0.5f);
        SceneGraphDebugDraw.drawNodeBounds(queue, -3, 1.5f, -2, 1f, 0.6f, 0.4f);
    }

    // --- Camera math ---

    private float[] buildViewProjMatrix(float aspect) {
        // Perspective projection
        float fov = (float) Math.toRadians(60);
        float near = 0.1f, far = 100f;
        float f = 1f / (float) Math.tan(fov / 2f);
        float[] proj = new float[16];
        proj[0] = f / aspect;
        proj[5] = f;
        proj[10] = (far + near) / (near - far);
        proj[11] = -1f;
        proj[14] = (2f * far * near) / (near - far);

        // Orbit camera looking at origin
        float dist = 12f;
        float eyeX = (float) Math.sin(cameraAngle) * dist;
        float eyeZ = (float) Math.cos(cameraAngle) * dist;
        float eyeY = 5f;
        float[] view = lookAt(eyeX, eyeY, eyeZ, 0, 0, -3);

        // viewProj = proj × view
        return multiply(proj, view);
    }

    private static float[] lookAt(float eyeX, float eyeY, float eyeZ,
                                   float centerX, float centerY, float centerZ) {
        float upX = 0, upY = 1, upZ = 0;
        float fx = centerX - eyeX, fy = centerY - eyeY, fz = centerZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fLen; fy /= fLen; fz /= fLen;
        float sx = fy * upZ - fz * upY, sy = fz * upX - fx * upZ, sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sLen; sy /= sLen; sz /= sLen;
        float ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;

        float[] m = new float[16];
        m[0] = sx; m[4] = sy; m[8] = sz;
        m[1] = ux; m[5] = uy; m[9] = uz;
        m[2] = -fx; m[6] = -fy; m[10] = -fz;
        m[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ);
        m[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ);
        m[14] = (fx * eyeX + fy * eyeY + fz * eyeZ);
        m[15] = 1;
        return m;
    }

    private static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                for (int k = 0; k < 4; k++)
                    r[j * 4 + i] += a[k * 4 + i] * b[j * 4 + k];
        return r;
    }
}
