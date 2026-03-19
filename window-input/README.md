# Window Input

Demonstrates **live keyboard/mouse input** through a real GLFW window flowing into DynamisInput and WorldEngine.

This is the first Dynamis example with a real window. Unlike `input-basics` (synthetic events), this module captures actual keyboard events from the OS.

## Architecture

```
GLFW Window (DynamisWindow)
    → pollEvents() each tick
    → InputEvent objects (Key, MouseButton, CursorMoved)
    → InputProcessor.feed()
    → InputFrame (actions, axes)
    → GestureRecognizer
    → game logic
```

## Subsystems

- **WindowSubsystem** — owns GLFW window lifecycle, polls events
- **WindowInputSubsystem** — depends on Window, feeds events into InputProcessor

## Controls

| Key | Action |
|-----|--------|
| A / Left Arrow | Move left |
| D / Right Arrow | Move right |
| Space (tap) | Quick jump gesture |
| Space (hold 30+ ticks) | Charge jump gesture |
| D (double-tap) | Dash right gesture |
| Escape | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

**Important:** Run from a terminal (not background) so the window can receive keyboard focus.

macOS note: Uses `-XstartOnFirstThread` for GLFW compatibility.

## What to look for

- A real window opens on your desktop
- Press keys and see actions/gestures fire in the console
- Telemetry status line every 120 ticks
- Clean shutdown on Escape or window close
