# Mesh Basics

First real mesh rendering in the Dynamis proving ladder.

## What it proves

- **Indexed triangle mesh** -- SimpleMesh record with positions + normals + index buffer
- **GPU upload** -- VAO/VBO/EBO creation from mesh data (MeshHandle)
- **Normal-based shading** -- hemispheric + directional lighting in fragment shader
- **Model transforms** -- rotation, translation applied via uniform matrices
- **Wireframe toggle** -- glPolygonMode(GL_LINE) for mesh inspection
- **Mesh generation** -- procedural torus (48x24) and sphere (24x24)

## Scene

- Torus (center, auto-rotating) -- cyan, ~2304 triangles
- Sphere (offset right, counter-rotating) -- orange, ~1152 triangles
- Both rendered with normal-derived shading for clear 3D depth

## Controls

| Key | Action |
|-----|--------|
| A/D or Left/Right | Orbit left/right |
| W/S or Up/Down | Orbit up/down |
| Q/E | Zoom in/out |
| Tab | Toggle wireframe |
| R | Reset camera |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
