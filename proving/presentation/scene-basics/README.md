# Scene Basics - Phase 2 Capstone

First composed 3D scene from proven presentation pieces.

## What it proves

- **Scene composition** -- multiple mesh instances assembled into a coherent space
- **SceneObject** record -- mesh + material + transform per instance
- **Shared GPU resources** -- 3 meshes (torus, sphere, cube) reused across ~30 objects
- **Per-instance transforms** -- position, rotation, scale applied per object
- **Per-instance materials** -- Gold, Matte Red, Glossy Blue, Chrome, Purple, stone, floor
- **Coherent lighting** -- directional sun + orbiting point light across entire scene
- **Scene readability** -- gallery layout with hero object, pedestals, accent ring, floor grid

## Scene Layout

```
        [accent tori ring - radius 7]

    [pillar+sphere]          [pillar+sphere]

              [HERO TORUS]

    [pillar+sphere]          [pillar+sphere]

        [accent tori ring - radius 7]

    ========= floor grid =========
```

## Controls

| Key | Action |
|-----|--------|
| 1 | Toggle directional (sun) light |
| 2 | Toggle point light |
| 3 | Toggle auto-rotation |
| A/D or Left/Right | Orbit camera |
| W/S or Up/Down | Pitch camera |
| Q/E | Zoom |
| R | Reset |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## Phase 2 Complete

This module is the capstone of the presentation proving ladder:

1. text-basics -- in-window text
2. camera-basics -- spatial view
3. mesh-basics -- real geometry
4. material-basics -- surface definition
5. lighting-basics -- scene lighting
6. **scene-basics** -- composed scene
