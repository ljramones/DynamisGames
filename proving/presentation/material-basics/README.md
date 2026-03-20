# Material Basics

Same mesh, different materials -- proving surface parameterisation.

## What it proves

- **SimpleMaterial** record -- canonical proving-level material definition
- **Blinn-Phong shading** -- ambient, diffuse, specular, shininess parameters
- **Material presets** -- Matte Red, Glossy Blue, Flat Green, Gold, Chrome, Debug Wire
- **Geometry/appearance separation** -- one MeshHandle drawn 6 times with 6 materials
- **ShadingMode** -- LIT (Blinn-Phong), UNLIT (flat color), WIREFRAME
- **Camera-relative specular** -- highlights shift as camera orbits

## Scene

6 tori arranged in a ring, each with a distinct material preset,
all sharing the same underlying mesh. Auto-rotating so specular
highlights are visible from different angles.

## Controls

| Key | Action |
|-----|--------|
| A/D or Left/Right | Orbit left/right |
| W/S or Up/Down | Orbit up/down |
| Q/E | Zoom in/out |
| R | Reset camera |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
