# Animation Basics - Phase 3 Opener

First time-based animation in the Dynamis proving ladder.

## What it proves

- **AnimationClip** -- named channels with keyframe data and duration
- **TransformChannel** -- per-channel keyframes with linear interpolation
- **Keyframe** -- (time, value) pairs sampled at runtime
- **AnimationPlayer** -- playback controller: play/pause/reset/loop/speed
- **Animation-driven transforms** -- posY, rotY, scale sampled each frame
- **Scene integration** -- animated objects coexist with static scene, lighting, camera
- **HUD feedback** -- live clip time, channel values, playback state

## Scene

- Central hero torus: bobs up/down, rotates, pulses scale (4s clip)
- 4 accent tori: gentle orbit + bob (6s clip, staggered starts)
- Floor grid, directional sun, point light tracking hero
- Static elements prove animation doesn't break non-animated objects

## Controls

| Key | Action |
|-----|--------|
| Space | Pause/resume animation |
| L | Toggle looping |
| + | Increase speed (+0.25x) |
| R | Reset animation + camera |
| A/D or Left/Right | Orbit camera |
| W/S or Up/Down | Pitch camera |
| Q/E | Zoom |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
