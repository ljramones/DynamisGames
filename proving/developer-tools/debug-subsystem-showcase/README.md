# Debug Subsystem Showcase

Capstone proving module for the DynamisDebug observability stack.
Demonstrates the complete system in one integrated runtime: overlay panels,
world-space debug draw, watchdogs, history/timeline, sparkline trends,
and analytical queries — all working together across multiple subsystems.

## What it proves

This is not another isolated feature demo. It proves:

- **All debug capabilities working together** in one coherent runtime
- **Overlay + debug draw coexistence** — 2D panels and 3D wireframes render together
- **Multi-subsystem observability** — engine, ECS, physics, audio, GPU all active
- **Real diagnostic patterns** — phase transitions, correlation signatures, query results
- **The debug spine is a complete diagnostic instrument**, not just a collection of features

## Active subsystems

| Subsystem | Metrics | Watchdog rules |
|-----------|---------|----------------|
| Engine | frameTimeMs, budgetPercent, tickRate, uptime | frameBudgetHigh, frameCritical |
| ECS | entityCount | entityFlood |
| Physics | stepTimeMs, contacts, bodies | stepHigh |
| Audio | voices, dspBudget | dspHigh |
| GPU | drawCalls, backlog | — |

## Phases (auto-cycling, 5s each)

| Phase | Behavior |
|-------|----------|
| IDLE | Stable baseline, all nominal, few debug draw entities |
| COMBAT | Entity/physics load rises, more wireframe boxes appear, audio increases |
| STRESS | All subsystems under pressure, watchdogs fire, timeline fills |
| RECOVERY | Load settles, alerts decay, trends normalize |

## Debug draw

World-space 3D scene with orbiting camera:
- **Ground grid** — 10x10 reference grid
- **Coordinate axes** — RGB XYZ lines (always visible)
- **Entity boxes** — wireframe cubes that scale with entity count, colored by phase
- **Contact lines** — yellow lines from origin representing physics contacts

## Query modes (press Q to cycle)

1. **Spikes** — frame time spike count, last spike frame, max value
2. **NoisyRules** — ranked watchdog rules by fire count
3. **Correlation** — frames where ECS > 300 AND frameTimeMs > 8ms

## Controls

| Key | Action |
|-----|--------|
| 1-4 | Set phase (IDLE/COMBAT/STRESS/RECOVERY) |
| Q | Cycle query mode |
| Space | Inject stress spike |
| R | Reset history + scenario |
| P | Pause/resume |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

1. **During IDLE** — overlay shows stable metrics, green sparklines, no alerts, few 3D boxes
2. **During COMBAT** — entity boxes multiply and turn yellow, ECS/physics sparklines rise, warnings may appear
3. **During STRESS** — boxes turn red, alerts fire, timeline fills with events, query results become interesting
4. **During RECOVERY** — boxes turn cyan and shrink, sparklines settle, alerts stop
5. **Query panel** — shows live analytical results that change with accumulated history
6. **Debug draw + overlay** — both render cleanly together without interference

## Build & Run

```bash
./build.sh
./run.sh
```
