# Interaction Rendered

The first module where you can **see the gameplay happening**.

Combines everything proven so far: live input, ECS state, object lifecycle,
procedural audio, and visible rendering — all through WorldEngine.

## What it demonstrates

- Yellow cursor moves with WASD
- Space spawns bright cyan pulse entities at cursor position
- Pulses drift with random velocity
- Pulses shrink and fade over 3 seconds
- Pulses disappear on expiry
- Background brightens with more active pulses
- Spawn pop (660 Hz) and expire tone (330 Hz) through voice pipeline

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move cursor |
| Space | Spawn visible pulse |
| R | Reset scene |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## Architecture

Rendering reflects ECS state — it does NOT become the state:

```
Input → ECS mutation → Systems (movement, lifetime) → Render from ECS → Audio on events
```

Each frame, the renderer reads Position and Lifetime from pulse entities
and draws them at the correct size, position, and brightness.
