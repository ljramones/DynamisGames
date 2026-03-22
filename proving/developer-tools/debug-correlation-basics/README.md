# Debug Correlation Basics

Proves cross-subsystem diagnostic reasoning: multiple subsystems
producing correlated signals with distinct causal patterns that a
developer must read across panels to identify root cause.

## What it proves

- **Causal ordering** — some metrics lead, others lag
- **Alert timing** — watchdogs fire after their root cause, not before
- **Independent faults** — not all alerts share the same root cause
- **Multi-panel reading** — the overlay pattern differs per fault class

## Scenarios

### 1. ECS Overload (press 1)

Entity count rises first. Physics contacts and step time follow with
a ~15% lag. Engine frame time follows physics with a ~30% lag. Audio
remains unaffected.

**Root-cause signature:** ECS entityCount sparkline rises before physics
stepTimeMs rises before engine frameTimeMs rises. Audio trends are flat.

**Expected alerts (in order):**
1. `ecs.entityFlood` (first)
2. `physics.stepHigh` (lagged)
3. `engine.frameBudgetHigh` (further lagged)

### 2. Physics Spike (press 2)

Entity count stays stable. Physics step time and contact count jump
due to collision complexity (not entity growth). Engine frame time
follows physics.

**Root-cause signature:** ECS entityCount sparkline is flat. Physics
stepTimeMs sparkline rises. Engine frameTimeMs follows. Audio flat.

**Expected alerts (in order):**
1. `physics.stepHigh` (first)
2. `engine.frameBudgetHigh` (lagged)
3. No `ecs.entityFlood`

### 3. Audio Pressure (press 3)

Gameplay load stays normal. Audio voices and DSP budget rise
independently. Engine frame time is mostly unaffected.

**Root-cause signature:** Physics/ECS/Engine sparklines are flat.
Audio dspBudget and voices sparklines rise. Only audio alerts fire.

**Expected alerts:**
1. `audio.dspHigh`
2. `audio.voiceOverload`
3. No engine or physics alerts

## Controls

| Key | Action |
|-----|--------|
| 1   | ECS overload scenario |
| 2   | Physics spike scenario |
| 3   | Audio pressure scenario |
| R   | Reset to baseline |
| P   | Pause/resume |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

- Watch which sparklines rise first after pressing a scenario key
- Note which alerts appear first in the Alerts panel
- Compare the timeline events at the bottom — the order reveals causality
- Press different scenarios and compare the multi-panel signatures

## Canonical pattern

A developer should learn to read the overlay by asking:
1. Which panel spiked first?
2. Which alerts fired first?
3. Which panels stayed flat?
4. Does the timeline show the same ordering?

## Build & Run

```bash
./build.sh
./run.sh
```
