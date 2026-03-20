# Animation Gameplay Sync

Animation timing owns gameplay consequence, not raw input.

## The key lesson

When you press Space, the strike animation starts. But the target is NOT hit immediately.
The target only reacts when the animation reaches the "hit" event at 0.5s into the 1.2s clip.

```
Input → start animation → time advances → "hit" event crossed → gameplay consequence fires
```

This is how real games work: attack windows, footsteps, damage frames.

## What it proves

- **Animation-timed consequence** -- gameplay effect fires at event time, not input time
- **Strike clip with markers** -- windup (0.1s), hit (0.5s), recover (0.9s)
- **Target state management** -- ACTIVE → HIT → RECOVERING cycle
- **One-shot dispatch** -- hit fires once per strike, not continuously
- **Event log** -- shows INPUT vs WINDUP vs HIT vs RECOVER timing
- **Pause safety** -- paused animation prevents event firing
- **Uses Animis PropertyPlayer** -- canonical animation subsystem

## Controls

| Key | Action |
|-----|--------|
| Space | Strike (starts action animation) |
| P | Pause/resume |
| R | Reset scene |
| A/D/W/S | Orbit camera |
| Q/E | Zoom |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
