package org.dynamisengine.games.mgi;

import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.mgi.MgiStaticMesh;
import org.dynamisengine.meshforge.mgi.MgiStaticMeshCodec;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates test MGI assets from MeshForge procedural geometry.
 *
 * Run this once to create the .mgi files in src/main/resources/assets/.
 * After that, the runtime loads them directly via MeshForgeAssetService.
 *
 * This demonstrates the canonical pipeline:
 * procedural/imported mesh -> MeshForge process -> MGI bake -> file
 */
public final class MgiAssetGenerator {

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of("src/main/resources/assets");
        Files.createDirectories(outDir);

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();

        // Generate a cube
        MeshData cube = Meshes.cube(1.0f);
        cube = MeshPipeline.run(cube, Ops.validate(), Ops.normals(60f), Ops.bounds());
        MgiStaticMesh cubePayload = MgiMeshDataCodec.toMgiStaticMesh(cube);
        Files.write(outDir.resolve("cube.mgi"), codec.write(cubePayload));
        System.out.printf("Wrote cube.mgi (%d verts, %d indices)%n",
                cubePayload.positions().length / 3, cubePayload.indices().length);

        System.out.println("MGI assets generated in " + outDir);
    }
}
