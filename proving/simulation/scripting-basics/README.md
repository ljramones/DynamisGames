# Scripting Basics

Canonical exemplar for the DynamisScripting lifecycle: trigger, evaluate, commit, observe.

## What it proves

- **Scripting lifecycle** — full Canon/Oracle/Chronicler tick through gameplay interaction
- **Trigger → evaluate → apply** — player action triggers script evaluation, structured outcome applied as gameplay effect
- **Story nodes** — DSL predicate triggers (`hasKey == true && doorOpen == false`) with priority and cooldown
- **Canon log** — world events committed and queryable by causal link
- **Canonical dimension** — minimal `ShrineDimension` satisfying the runtime contract
- **Audio feedback** — procedural tone cues for success/failure/key toggle
- **Scripts decide, game systems apply** — clean separation between evaluation and effect

## Scenario: Shrine Door Trigger

A sealed door blocks the path. A shrine nearby evaluates whether the player holds the key.

1. Press **K** to pick up / drop the key
2. Press **E** to interact with the shrine
3. If the player has the key → door opens, success tone plays
4. If the player lacks the key → denied feedback, low tone plays
5. Console shows scripting lifecycle: proposed events, committed events, tick duration

## Architecture

```
Player input (E key)
  → ScriptingBasicsGame detects interaction
  → ShrineScriptFacade.evaluateInteraction(hasKey, doorOpen)
    → seeds gameplay state into canon log
    → ticks ScriptingRuntime (Chronicler evaluates story nodes)
    → Oracle commits matching world events
    → queries canon log for committed events
    → returns ShrineScriptOutcome (OPEN_DOOR / DENIED_NO_KEY / ALREADY_OPEN)
  → Game applies outcome: door state, audio cue, status message
```

## Controls

| Key | Action |
|-----|--------|
| K | Toggle key (pick up / drop) |
| E | Interact with shrine |
| Close window | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## Files

| File | Purpose |
|------|---------|
| `Main.java` | Bootstrap with canonical subsystem adapters |
| `ScriptingBasicsGame.java` | WorldApplication with full lifecycle |
| `ShrineScriptFacade.java` | Canonical seam between gameplay and scripting |
| `ShrineScriptOutcome.java` | Structured outcome enum |
| `ShrineDimension.java` | Minimal CanonDimensionProvider |
