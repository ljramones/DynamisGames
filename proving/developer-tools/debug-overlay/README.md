# Debug Overlay

Live engine telemetry HUD proving GameContext.telemetry() integration.

## What it proves

- **WorldTelemetrySnapshot** — engine state, tick timing, budget utilization
- **SubsystemHealth** — per-subsystem health state with color coding (green/yellow/red)
- **AudioTelemetry** — DSP budget %, ring buffer fill, underrun/overrun counts
- **InputTelemetry** — connected devices, event counts, snapshot counts
- **ECS stats** — live entity count
- **JVM stats** — heap usage, processor count
- **Toggle overlay** — Tab key shows/hides (proves HUD can be layered)

## Controls

| Key | Action |
|-----|--------|
| Space | Spawn 20 particles (load generation) |
| Tab | Toggle overlay visibility |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
