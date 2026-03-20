# UI Basics

Proves **UI flow and presentation state** in Dynamis.

Title screen → gameplay HUD → pause → win/lose → restart. All state
transitions are visible through colored panels and overlays.

## UI Screens

| Screen | Visual | Trigger |
|--------|--------|---------|
| Title | Dark blue + centered panels | Start state, after R |
| Playing | Game scene + HUD bars | Space/Enter from title |
| Paused | Dimmed purple overlay | P during play |
| Won | Green background + score bar | Clear wave 3 |
| Lost | Red background + score bar | 5 misses |

## HUD elements (during play)

- **Score bar** (top-left): green bar grows with score
- **Wave indicators** (top-right): bright blocks for completed waves
- **Miss dots** (bottom-left): red dots accumulate
- **Cursor** (yellow): player position

## Controls

| Key | Action |
|-----|--------|
| Space / Enter | Start game / Fire pulse |
| P | Pause / Resume |
| R | Restart to title |
| WASD / Arrows | Move cursor |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## Architecture

UI state (`UiMode`) and gameplay state (score/wave/misses) are separate:
- `UiMode` controls which screen renders
- Gameplay state lives in ECS + simple counters
- Renderer has per-mode methods (renderTitle, renderPlaying, renderPaused, etc.)
- No text rendering — colored panels communicate state visually
- Console text supplements with exact values
