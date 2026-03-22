# Debug History Timeline

Proves the time-aware diagnostic capability of the DynamisDebug spine:
history accumulation, timeline events, trend evolution, and alert
transitions under changing simulated conditions.

## What it proves

- **History accumulation** — DebugSession records snapshots every frame into a ring buffer
- **Timeline events** — WARNING/ERROR/CRITICAL events appear in timeline order
- **Trend evolution** — mini-trend sparklines show metric behavior over time
- **Alert transitions** — watchdog rules fire as conditions change, then stop when resolved
- **Phase detection** — the overlay visually reflects normal/degrading/spike/recovery patterns

## Scenarios

The module auto-cycles through four phases:

| Phase | Duration | Behavior |
|-------|----------|----------|
| NORMAL | 5s | Stable ~3ms frame time, 100 entities, no alerts |
| DEGRADING | 5s | Frame time drifts 3ms -> 18ms, entities rise, warnings appear |
| SPIKE | 2s | Sudden fault: 25-40ms frames, 600+ entities, errors fire |
| RECOVERY | 4s | Frame time returns to normal, alerts decay |

## Controls

| Key | Action |
|-----|--------|
| Space | Trigger immediate spike/fault |
| R | Reset history and restart scenario |
| P | Pause/resume simulation |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

- Watch the Engine sparklines during DEGRADING — they visibly rise
- During SPIKE, the alert panel lights up and timeline events appear
- During RECOVERY, trends settle back and alerts stop firing
- Press Space to inject a manual spike at any time
- Press R to clear history and watch trends rebuild from scratch

## Canonical pattern

```java
// 1. Create session with watchdog rules
DebugSession session = new DebugSession();
session.watchdog().addRule(WatchdogRule.above(...));

// 2. Each frame: record snapshots, evaluate watchdogs
session.history().record(tick, frameSnapshots);
var alerts = session.watchdog().evaluate(tick, frameSnapshots);
for (var alert : alerts) session.submit(alert);

// 3. Map to UI contract
DebugViewSnapshot viewSnapshot = mapper.mapFromFrame(tick, frameSnapshots);

// 4. Build and render panels
List<DebugOverlayPanel> panels = builder.buildAll(viewSnapshot);
overlayRenderer.renderPanels(panels, w, h);
```

## Build & Run

```bash
./build.sh
./run.sh
```
