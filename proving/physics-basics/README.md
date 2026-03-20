# Physics Basics

Projectile range sandbox proving DynamisPhysics (ODE4J) integration with ECS, rendering, input, and audio.

## What it proves

- **DynamisPhysics** — real ODE4J rigid-body simulation with gravity, collisions, contact events
- **Physics ↔ ECS sync** — physics body state synced to ECS positions each frame
- **Contact events** — `ContactEvent` from `drainEvents()` drives scoring and audio
- **Gameplay consequence** — projectile hits target → score, audio, visual feedback

## Controls

| Key | Action |
|-----|--------|
| W/S or Up/Down | Aim up/down |
| A/D or Left/Right | Move launcher |
| Space | Fire projectile |
| R | Reset |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
