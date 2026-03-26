package org.dynamisengine.games.skinned;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamisengine.light.api.config.EngineConfig;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.api.input.EngineInput;
import org.dynamisengine.light.api.input.KeyCode;
import org.dynamisengine.light.api.logging.LogMessage;
import org.dynamisengine.light.api.runtime.EngineFrameResult;
import org.dynamisengine.light.api.runtime.EngineHostCallbacks;
import org.dynamisengine.light.api.runtime.EngineRuntime;
import org.dynamisengine.light.api.runtime.EngineStats;
import org.dynamisengine.light.api.scene.CameraDesc;
import org.dynamisengine.light.api.scene.EnvironmentDesc;
import org.dynamisengine.light.api.scene.FogDesc;
import org.dynamisengine.light.api.scene.FogMode;
import org.dynamisengine.light.api.scene.LightDesc;
import org.dynamisengine.light.api.scene.MaterialDesc;
import org.dynamisengine.light.api.scene.MeshDesc;
import org.dynamisengine.light.api.scene.PostProcessDesc;
import org.dynamisengine.light.api.scene.SceneDescriptor;
import org.dynamisengine.light.api.scene.SmokeEmitterDesc;
import org.dynamisengine.light.api.scene.TransformDesc;
import org.dynamisengine.light.api.scene.Vec3;
import org.dynamisengine.light.spi.EngineBackendProvider;
import org.dynamisengine.light.spi.registry.BackendRegistry;

/**
 * Canonical skinned mesh exemplar using the real DynamisLightEngine pipeline.
 *
 * <p>Pipeline: GLB → MeshForge + Animis → LightEngine.loadScene() →
 * per-frame Animis sample/skin → updateSkinnedMesh() → render()
 *
 * <p>This proves the end-to-end skinning pipeline across MeshForge, Animis,
 * and DynamisLightEngine without any proving-local rendering code.
 *
 * <p>Controls: Space = pause/play, N = next clip, Esc = quit
 */
public final class SkinnedMeshBasicsApp {

    private static final String BACKEND = System.getProperty("dle.backend", "vulkan");
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    public static void main(String[] args) throws Exception {
        Path glbPath = Path.of("src/main/resources/models/CesiumMan.glb");
        if (!glbPath.toFile().exists()) {
            // Try from module root
            glbPath = Path.of("proving/presentation/skinned-mesh-basics/src/main/resources/models/CesiumMan.glb");
        }
        if (!glbPath.toFile().exists()) {
            System.err.println("CesiumMan.glb not found. Place it in src/main/resources/models/");
            System.exit(1);
        }

        System.out.println("=== Skinned Mesh Basics ===");
        System.out.println("Loading: " + glbPath);

        // --- Load animation data via Animis ---
        SkinnedCharacterController character = new SkinnedCharacterController(glbPath);
        System.out.printf("Skeleton: %d joints, %d clips%n",
                character.jointCount(), character.clipCount());
        System.out.printf("Clip: %s (%.2fs)%n",
                character.currentClipName(), character.clipDuration());
        System.out.println("Space = pause/play, N = next clip, Esc = quit");
        System.out.println();

        // --- Create LightEngine runtime ---
        EngineBackendProvider provider = BackendRegistry.discover()
                .providers()
                .stream()
                .filter(p -> p.backendId().equalsIgnoreCase(BACKEND))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Backend not found: " + BACKEND));

        EngineConfig config = new EngineConfig(
                BACKEND, "skinned-mesh-basics", WIDTH, HEIGHT, 1.0f, true, 60,
                QualityTier.MEDIUM, Path.of("."),
                Map.of(
                    BACKEND + ".mockContext", "false",
                    BACKEND + ".windowVisible", "true"
                ));

        SceneDescriptor scene = buildScene(glbPath.toString());

        try (EngineRuntime runtime = provider.createRuntime()) {
            runtime.initialize(config, new MinimalCallbacks());
            runtime.loadScene(scene);

            System.out.println("Rendering with " + provider.backendId() + " backend...");

            // --- Main loop ---
            long frameCount = 0;
            long startNanos = System.nanoTime();
            float dt = 1.0f / 60.0f;

            while (true) {
                // Update animation and compute joint matrices
                float[] jointMatrices = character.update(dt);

                // Upload joint matrices to the engine's skinned mesh pipeline
                try {
                    runtime.updateSkinnedMesh(0, jointMatrices);
                } catch (EngineException e) {
                    if (frameCount == 0) {
                        System.err.println("updateSkinnedMesh failed: " + e.getMessage());
                        System.err.println("(Mesh may not be detected as skinned by the loader)");
                    }
                }

                // Engine update + render
                EngineFrameResult updateResult = runtime.update(dt, new EngineInput(0, 0, 0, 0, false, false, Set.of(), 0));
                EngineFrameResult renderResult = runtime.render();

                frameCount++;

                // Debug output every 120 frames
                if (frameCount % 120 == 0) {
                    EngineStats stats = runtime.getStats();
                    System.out.printf("[frame=%d] clip=%s time=%.2f/%.2fs %s | " +
                                    "fps=%.0f cpu=%.1fms skinned=%d draws=%d%n",
                            frameCount,
                            character.currentClipName(),
                            character.animationTime(),
                            character.clipDuration(),
                            character.isPaused() ? "(PAUSED)" : "",
                            stats.fps(), stats.cpuFrameMs(),
                            stats.skinnedDraws(), stats.drawCalls());
                }

                // TODO: input handling for Space/N/Esc requires EngineInput
                // integration which depends on the backend's window event loop.
                // For now, runs until max frames or external termination.
                if (frameCount >= 600) break; // 10 seconds at 60fps
            }

            double totalMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            System.out.printf("%nCompleted %d frames in %.0fms (%.1f fps avg)%n",
                    frameCount, totalMs, frameCount / (totalMs / 1000.0));
        }
    }

    private static SceneDescriptor buildScene(String meshPath) {
        var camera = new CameraDesc("main-cam",
                new Vec3(0, 1.5f, 3), new Vec3(0, 0.8f, 0), 45f, 0.1f, 100f);
        var transform = new TransformDesc("root",
                new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        var mesh = new MeshDesc("character", "root", "mat-1", meshPath);
        var material = new MaterialDesc("mat-1",
                new Vec3(0.8f, 0.8f, 0.8f), 0.15f, 0.7f,
                null, null, null, null, 0.5f, true, true);
        var light = new LightDesc("sun",
                new Vec3(3, 5, 3), new Vec3(1, 0.95f, 0.9f), 1.0f, 50f, false, null);
        var env = new EnvironmentDesc(new Vec3(0.15f, 0.15f, 0.2f), 0.3f, null);
        var fog = new FogDesc(false, FogMode.NONE, new Vec3(0, 0, 0), 0, 0, 0, 0, 0, 0);
        var post = new PostProcessDesc(false, false, 1f, 2.2f,
                false, 0, 0, false, 0, 0, 0, 0, false, 0, false, 0, false, null);

        return new SceneDescriptor("skinned-basics",
                List.of(camera), camera.id(),
                List.of(transform), List.of(mesh), List.of(material), List.of(light),
                env, fog, List.of(), post);
    }

    private static class MinimalCallbacks implements EngineHostCallbacks {
        @Override public void onEvent(org.dynamisengine.light.api.event.EngineEvent event) {}
        @Override public void onLog(LogMessage message) {}
        @Override public void onError(org.dynamisengine.light.api.error.EngineErrorReport report) {
            System.err.println("Engine error: " + report);
        }
    }
}
