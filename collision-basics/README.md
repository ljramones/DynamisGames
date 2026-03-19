# Collision Basics

The first **game rule** in Dynamis: pulse-to-target collision.

Targets are arranged in the arena. Move the cursor, fire pulses upward.
When a pulse overlaps a target, the target is destroyed with a distinct hit sound
and the score increments. Targets respawn in waves after being cleared.

## What it demonstrates

```
Input → fire pulse → pulse moves → overlaps target → collision detected →
  target destroyed + hit sound + score increment
```

This is the first module where entities **matter to each other** through rules.

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move cursor |
| Space | Fire pulse upward |
| R | Reset (score=0, targets respawn) |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to observe

- Yellow cursor at bottom, red/orange targets at top
- Space fires cyan pulses upward
- Pulse hits target → target disappears, bright hit sound (1320 Hz)
- All targets cleared → new wave spawns after 1 second
- Score accumulates in console
- Collision is simple circle-circle distance check

## Collision model

```java
float dist = sqrt(dx*dx + dy*dy);
if (dist < pulse.radius + target.radius) → HIT
```

No physics engine. Just the simplest possible game rule.
