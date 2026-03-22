# Debug Draw Basics

Proves the full debug draw pipeline end-to-end: `DebugDrawQueue` command accumulation,
bridge helpers (`CollisionDebugDraw`, `TerrainDebugDraw`, `SceneGraphDebugDraw`), and
OpenGL wireframe rendering with depth mode support.

## What it proves

- **DebugDrawQueue** — per-frame command accumulation and clear lifecycle
- **CollisionDebugDraw** — AABB bounds (cyan), contact points (green/yellow/red by lifecycle), normals (white)
- **TerrainDebugDraw** — chunk bounds with LOD color coding (green→red)
- **SceneGraphDebugDraw** — node bounds (light blue)
- **Direct API** — coordinate axes drawn via `DebugLineCommand` convenience
- **Depth modes** — TESTED (occluded by geometry) vs ALWAYS_VISIBLE (x-ray)
- **Frame lifecycle** — queue clears every frame, no ghost lines

## Controls

| Key | Action |
|-----|--------|
| 1   | Toggle collision debug draw |
| 2   | Toggle terrain debug draw |
| 3   | Toggle scene graph debug draw |
| 4   | Toggle depth mode (TESTED / ALWAYS_VISIBLE) |
| Tab | Toggle all debug draw on/off |
| Esc | Quit |

## v1 scope

OpenGL debug draw v1 supports lines and boxes. Spheres are deferred
(`DebugSphereCommand` exists in the API but is not yet rendered).
World-space text remains a UI-layer responsibility.

## Build & Run

```bash
./build.sh
./run.sh
```
