# Debug Session Queries

Proves that the DynamisDebug spine is not just visual — it is queryable
as data. The debug system becomes a programmatic observability platform,
not just a dashboard.

## What it proves

- **Spike detection** — find when metrics exceeded thresholds in recent history
- **Rule noise ranking** — identify which watchdog rules fire most (good vs bad design)
- **Threshold crossing analysis** — count transitions, find first/last crossing, frames above
- **Correlated window queries** — find frames where two conditions co-occurred

## Query modes

### 1. Spike Analysis (key 1)

Finds frames where `engine.frameTimeMs > 10` in the last 200 frames.

Shows: spike count, last spike frame, max value.

### 2. Noisy Rules (key 2)

Ranks all watchdog rules by fire count. The `engine.noCooldown` rule
(0-frame cooldown) will vastly outnumber properly-cooled rules.

Shows: ranked list with fire counts and severity.

### 3. Threshold Crossings (key 3)

Analyzes `engine.frameTimeMs > 10` crossings: how many times the metric
transitioned from below to above, first/last crossing frame, total
frames above threshold.

Shows: crossings, first/last frame, frames above count.

### 4. Correlated Window (key 4)

Finds frames where `ecs.entityCount > 300` AND `engine.frameTimeMs > 10`
are both true simultaneously.

Shows: match count, first/last match frame.

## Simulation

The module runs periodic spike patterns (~2 second intervals) where
frame time, entity count, and physics step time all jump together.
Between spikes, metrics are stable. This creates meaningful results
for all four query modes.

## Controls

| Key | Action |
|-----|--------|
| 1   | Spike Analysis query |
| 2   | Noisy Rules query |
| 3   | Threshold Crossings query |
| 4   | Correlated Window query |
| R   | Reset history |
| P   | Pause/resume |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

- Spike Analysis: watch the spike count grow over time as periodic spikes accumulate
- Noisy Rules: compare noCooldown (hundreds of fires) vs cooled rules (few fires)
- Threshold Crossings: count increases with each spike cycle
- Correlated Window: matches appear only during spikes (when both conditions are true)

## Canonical pattern

```java
DebugAnalytics analytics = new DebugAnalytics(session);

// Spike detection
var spikes = analytics.findSpikes("engine", "frameTimeMs", 10.0, 200);

// Rule noise ranking
var noisy = analytics.rankNoisyRules(200);

// Threshold analysis
var crossings = analytics.analyzeThresholdCrossings("engine", "frameTimeMs", 10.0, 200);

// Correlated query
var correlated = analytics.findCorrelatedFrames(
    "ecs", "entityCount", 300.0,
    "engine", "frameTimeMs", 10.0, 200);
```

## Build & Run

```bash
./build.sh
./run.sh
```
