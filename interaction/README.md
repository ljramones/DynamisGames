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

## Facade effectiveness

This module compiles to a **16 KB JAR** with **358 lines of game-specific code** across 5 classes:

| File | Lines | Role |
|------|-------|------|
| `InteractionGame.java` | 168 | WorldApplication: input reading, state mutation, audio triggers |
| `InteractionState.java` | 68 | Game state: cursor position + pulse list |
| `InteractionAudio.java` | 55 | Audio feedback: spawn pop, expire tone, movement tick |
| `Pulse.java` | 40 | Spawned object: position, age, lifetime |
| `Main.java` | 27 | Entry point: register subsystems, run |

The game module is tiny because the engine carries the operational complexity:
Panama FFM CoreAudio, wait-free ring buffer, voice/mixer pipeline, GLFW window,
input processor, subsystem coordinator, telemetry — all invisible to the 358 lines above.

A proving module with real interactive behaviour should remain extremely small,
because engine complexity belongs in Dynamis subsystems, not in game-facing modules.
