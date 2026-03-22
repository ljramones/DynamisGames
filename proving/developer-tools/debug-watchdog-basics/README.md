# Debug Watchdog Basics

Proves watchdog rule behavior: threshold crossing, cooldowns, escalation,
and flapping. Uses a single observable metric with four distinct patterns
to isolate and teach watchdog design.

## What it proves

- **Threshold breach** — warning/error boundaries fire in sequence, then clear on recovery
- **Cooldown / anti-spam** — sustained breach does not flood alerts; cooldown paces firing
- **Escalation** — prolonged worsening crosses WARNING -> ERROR -> CRITICAL thresholds
- **Flapping** — metric oscillating around threshold shows why cooldown matters

## Rule set

All rules watch the same metric (`test.value`):

| Rule | Threshold | Severity | Cooldown |
|------|-----------|----------|----------|
| `test.warning` | > 10 | WARNING | 30 frames (0.5s) |
| `test.error` | > 20 | ERROR | 30 frames |
| `test.critical` | > 30 | CRITICAL | 30 frames |
| `test.noCooldown` | > 15 | WARNING | 0 frames (fires every frame!) |

The `noCooldown` rule is intentionally bad design — it demonstrates why
cooldowns are essential. Compare its alert count vs the cooldown-protected rules.

## Patterns

### 1. Threshold Breach (key 1)

Metric ramps 5 -> 25 over 3s, holds 2s, recovers over 3s.

**What to watch:**
- `test.warning` fires when metric crosses 10
- `test.error` fires when metric crosses 20
- Both stop firing during recovery below thresholds
- Timeline shows clear breach/recovery cycle

### 2. Sustained Breach (key 2)

Metric ramps to 18 and holds for 10 seconds.

**What to watch:**
- `test.warning` fires, then respects 30-frame cooldown
- `test.noCooldown` fires every single frame (floods alerts)
- Grouped alert counts show the difference: noCooldown has far more firings
- This teaches why cooldown > 0 is essential

### 3. Escalation (key 3)

Metric climbs steadily from 5 to 35 over 9s (crossing all three thresholds),
holds 3s at 35, then recovers over 4s.

**What to watch:**
- WARNING appears first at ~10
- ERROR appears later at ~20
- CRITICAL appears last at ~30
- Timeline shows clear escalation sequence
- Alert panel severity changes from WARNING -> ERROR as escalation progresses

### 4. Flapping (key 4)

Metric oscillates between 5 and 15 (2-second sine wave centered on
the warning threshold of 10).

**What to watch:**
- `test.warning` fires intermittently, paced by cooldown
- `test.noCooldown` fires on every frame the metric is above 15
- Trend sparkline shows the sine wave clearly
- Alert count demonstrates cooldown value: the cooled rule fires far less

## Controls

| Key | Action |
|-----|--------|
| 1   | Threshold breach pattern |
| 2   | Sustained breach pattern |
| 3   | Escalation pattern |
| 4   | Flapping pattern |
| R   | Reset to baseline |
| P   | Pause/resume |
| Tab | Toggle overlay |
| Esc | Quit |

## What good watchdog design looks like

From this module, a developer should learn:

1. **Always set a cooldown** — 0-cooldown rules spam alerts uselessly
2. **Use tiered severity** — WARNING/ERROR/CRITICAL thresholds at different levels
3. **Cooldown should match the diagnostic value** — too short = noise, too long = missed events
4. **Grouped alerts make cooldown gaps tolerable** — the builder collapses repeats with count

## Build & Run

```bash
./build.sh
./run.sh
```
