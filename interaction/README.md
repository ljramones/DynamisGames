# Interaction Sandbox

The first real **gameplay loop** in Dynamis.

Combines live input, mutable game state, object lifecycle, and audio feedback
into a single interactive example.

## What it demonstrates

```
Input → gameplay intent → mutate state → update objects → trigger audio → report
```

- Cursor movement from keyboard
- Object spawn/update/despawn lifecycle
- Procedural audio on spawn and expiry
- Stable update loop with status reporting

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move cursor |
| Space | Spawn pulse at cursor |
| R | Reset state |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to observe

- Move the cursor, then press Space to spawn pulses
- Each spawn triggers a bright pop sound (660 Hz)
- Pulses live for 2 seconds, then expire with a softer tone (330 Hz)
- Status prints every 2 seconds: cursor position, active/spawned/expired counts
- Clean shutdown on Esc or window close

## Architecture

This is the canonical example of how interactive game logic should be structured:

- **InteractionState** — plain state object (cursor + pulse list)
- **InteractionAudio** — audio feedback helper using QuickPlayback
- **InteractionGame** — WorldApplication that reads input, mutates state, triggers audio
- Three subsystems: Window, Input, Audio — all managed by WorldEngine
