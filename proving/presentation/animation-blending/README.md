# Animation Blending

Smooth crossfade between two animation clips.

## What it proves

- **BlendState** -- crossfade controller with source/target clips and blend weight
- **Weighted interpolation** -- per-channel lerp between two clips
- **Clean handoff** -- target becomes authoritative when blend completes
- **Mid-blend retarget** -- can trigger new blend while one is in progress
- **Two distinct clips** -- idle (gentle bob) vs active (energetic bounce + sway)
- **Visual blend feedback** -- color shifts during transition, text blend bar

## Blend rules

1. `transitionTo(clip, duration)` starts a crossfade
2. Both clips advance independently during blend
3. Output = source * (1-weight) + target * weight
4. Weight progresses 0->1 over blend duration (real-time, not speed-scaled)
5. On completion, target becomes the sole authoritative clip
6. Mid-blend transitions collapse current state to source

## Controls

| Key | Action |
|-----|--------|
| 1 | Transition to Idle |
| 2 | Transition to Active |
| Space | Pause/resume |
| L | Toggle looping |
| R | Reset |
| A/D/W/S | Orbit camera |
| Q/E | Zoom |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
