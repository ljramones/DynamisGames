# MGI Basics

First content-pipeline proof: runtime MGI loading via MeshForgeAssetService.

## What it proves

- **MGI runtime loading** -- .mgi file loaded via MeshForgeAssetService (fast binary path)
- **MeshForge canonical pipeline** -- MgiStaticMeshCodec -> MgiMeshDataCodec -> MeshPacker -> RuntimeGeometryPayload
- **No fallback required** -- MGI path is primary, HUD shows green "MGI" when used
- **Asset generation** -- MgiAssetGenerator demonstrates procedural -> MeshForge -> MGI bake

## Pipeline

```
generate-assets.sh: Meshes.cube() -> MeshPipeline -> MgiMeshDataCodec -> cube.mgi
run.sh:             cube.mgi -> MeshForgeAssetService -> RuntimeGeometryPayload -> render
```

## Setup

```bash
./build.sh
./generate-assets.sh   # creates src/main/resources/assets/cube.mgi
./run.sh
```

## Controls

| Key | Action |
|-----|--------|
| A/D or Left/Right | Orbit |
| W/S or Up/Down | Pitch |
| Q/E | Zoom |
| R | Reset |
| Esc | Quit |
