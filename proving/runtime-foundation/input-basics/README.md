# Input Basics

Demonstrates the **DynamisInput processing pipeline** using **synthetic input events**.

This example is intentionally headless — it does not open a real window.
A later example will demonstrate full DynamisWindow integration with live keyboard/mouse input.

## What it demonstrates

- Action bindings (MOVE_LEFT, MOVE_RIGHT, JUMP, PAUSE)
- Axis composites (MoveX from WASD keys)
- Gesture grammar:
  - **QuickJump** — tap Space within 8 ticks
  - **ChargeJump** — hold Space for 30+ ticks
  - **DashRight** — double-tap D within window
- Per-tick action state (pressed, down, released)
- Gesture firing and hold state tracking
- WorldEngine subsystem integration
- Telemetry output

## Controls (scripted)

| Input | Action |
|-------|--------|
| A / Left | MOVE_LEFT |
| D / Right | MOVE_RIGHT |
| Space | JUMP |
| P | PAUSE |
| Escape | QUIT |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to look for

- `>>> GESTURE: [quickJump]` — fires on Space tap release
- `>>> GESTURE: [chargeJump]` — fires after 30 ticks of holding Space
- `>>> GESTURE: [dashRight]` — fires on second D release (double-tap)
- `Holding: [chargeJump]` — sustained hold state
- `MoveX: -1.0` — axis value from A key
- Telemetry status line at end
