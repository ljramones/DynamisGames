# Animation Events

Timeline event markers triggering one-shot consequences.

## What it proves

- **AnimationEvent** record -- (time, name) markers on a clip timeline
- **Crossing detection** -- events fire when playback crosses their timestamp
- **One-shot dispatch** -- each event fires once per loop pass
- **Loop-safe** -- events re-arm on loop wrap
- **Pause-safe** -- no events fire while paused
- **Reset-safe** -- event state clears on reset
- **Consequences** -- audio cue + color flash + scrolling event log
- **Event types** -- "pulse" (cyan, 660Hz), "apex" (yellow, 880Hz), "accent" (pink, 440Hz)

## Event dispatch rules

1. Event fires when playback time crosses its timestamp
2. Same event does not refire until next loop
3. On loop wrap, all events become eligible again
4. On reset, event state clears completely
5. Paused animation does not emit events

## Controls

| Key | Action |
|-----|--------|
| Space | Pause/resume |
| L | Toggle looping |
| + | Increase speed |
| R | Reset animation + camera |
| A/D/W/S | Orbit camera |
| Q/E | Zoom |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
