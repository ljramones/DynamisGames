# AI Basics

Canonical exemplar for DynamisAI gameplay integration: perception, decision, action.

## What it proves

- **Budget-governed AI** — real DynamisAI `DefaultBudgetGovernor` with `AITaskNode` registration
- **Perception → decision → action** — clean separation at each boundary
- **State machine agent** — guard with IDLE, INVESTIGATE, CHASE states
- **Frame budget tracking** — per-task execution time, degraded/skipped counts
- **Mandatory fallback** — every AI task ships with a degraded fallback
- **AI decides, game systems apply** — canonical seam pattern

## Scenario: Shrine Guard

A guard agent stands at position 0. The player starts far away.

1. **IDLE** — guard is stationary, player is distant
2. **INVESTIGATE** — player enters awareness range (5 units), guard becomes alert
3. **CHASE** — player enters close range (3 units) while visible, guard pursues
4. **De-escalate** — player moves away or toggles visibility off

The guard's decision is driven by perception (distance + visibility) and
evaluated under the AI frame budget (8ms). If the budget is exceeded, the
fallback reuses the last decision instead of evaluating fresh.

## Architecture

```
World state (player position, visibility)
  → GuardPerception record (distance, visible, ticksSinceLastSeen)
  → GuardAiFacade.evaluate(tick, perception)
    → DefaultBudgetGovernor.runFrame(tick, snapshot)
      → AITaskNode "guard.think" executes within budget
      → State machine: IDLE ↔ INVESTIGATE ↔ CHASE
    → GuardDecision (newState, reason, moveToward)
  → AiBasicsGame applies: movement, audio cue, debug output
```

## Controls

| Key | Action |
|-----|--------|
| A/D | Move player left/right |
| V | Toggle player visibility |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## Files

| File | Purpose |
|------|---------|
| `Main.java` | Bootstrap with canonical subsystem adapters |
| `AiBasicsGame.java` | WorldApplication with perception/decision/action loop |
| `GuardAiFacade.java` | Canonical AI seam using BudgetGovernor + AITaskNode |
| `GuardState.java` | Agent behavioral state enum |
| `GuardPerception.java` | Perception input record |
| `GuardDecision.java` | Structured decision output record |
