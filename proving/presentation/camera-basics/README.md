# Camera Basics

First 3D rendering in the Dynamis proving ladder.

## What it proves

- **Perspective projection** — proper FOV, aspect ratio, near/far planes
- **View matrix (lookAt)** — eye position, center target, up vector
- **Orbit camera** — spherical coordinates (yaw, pitch, distance)
- **Input-driven 3D navigation** — smooth orbit rotation and zoom
- **Depth testing** — cubes occlude each other correctly
- **Per-vertex color** — 3D shader with vertex color interpolation
- **3D + 2D coexistence** — 3D scene + 2D text HUD in same frame

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
