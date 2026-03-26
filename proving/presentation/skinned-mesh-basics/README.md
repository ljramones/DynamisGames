# Skinned Mesh Basics

Canonical exemplar for skeletal animation using the real DynamisLightEngine rendering pipeline.

## What it proves

- **End-to-end skinning pipeline** — GLB → MeshForge + Animis → LightEngine → GPU → screen
- **Animis animation loading** — GltfAnimationLoader extracts skeleton + clips from GLB
- **Per-frame animation sampling** — DefaultClipSampler interpolates keyframes into PoseBuffer
- **Skinning matrix computation** — DefaultSkinningComputer produces joint matrices
- **LightEngine integration** — EngineRuntime.updateSkinnedMesh() uploads joint data per frame
- **No proving-local rendering** — uses the real engine pipeline, not a custom OpenGL renderer

## Pipeline

```
CesiumMan.glb
  → GltfAnimationLoader.load() → Skeleton + Clips
  → GltfMeshLoader.load() (via LightEngine.loadScene) → skinned MeshData

Per frame:
  → DefaultClipSampler.sample(clip, skeleton, time) → PoseBuffer
  → PoseBuffer.toPose() → Pose
  → DefaultSkinningComputer.compute(skeleton, pose) → SkinningOutput
  → SkinningOutput.jointMatrices() → float[jointCount * 16]
  → EngineRuntime.updateSkinnedMesh(0, jointMatrices) → GPU upload
  → EngineRuntime.render() → skinned vertex shader transforms vertices
```

## Controls

| Key | Action |
|-----|--------|
| (runs for 10 seconds then exits) | |
| Esc | Quit early (when input integration available) |

## Build & Run

```bash
./build.sh
./run.sh
```

## Files

| File | Purpose |
|------|---------|
| `SkinnedMeshBasicsApp.java` | Main app: creates LightEngine runtime, loads scene, runs render loop |
| `SkinnedCharacterController.java` | Animation seam: load → sample → skin → joint matrices |
| `CesiumMan.glb` | Khronos sample skinned character (src/main/resources/models/) |

## Architecture Gaps Surfaced

This exemplar intentionally uses the real engine pipeline. Any friction encountered during
implementation represents genuine integration feedback:

- Mesh handle assignment is implicit (position in SceneDescriptor.meshes list)
- Input handling requires EngineInput integration (not GLFW directly)
- Backend discovery via ServiceLoader requires runtime classpath setup
