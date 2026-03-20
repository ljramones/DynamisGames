# Arena Minigame

The first **playable micro-game** in Dynamis.

3 waves of targets. Hit them with pulses. Expired targets count as misses.
5 misses = game over. Clear all 3 waves = win.

## Game rules

- **Wave 1:** 4 targets, 5.5s lifetime
- **Wave 2:** 6 targets, 5.0s lifetime
- **Wave 3:** 8 targets, 4.5s lifetime
- Hit target: score +1, bright hit sound
- Target expires: miss +1, low miss sound
- 5 misses: GAME OVER (red background)
- All 3 waves cleared: WIN (green background)

## Controls

| Key | Action |
|-----|--------|
| Space | Fire pulse / Start game |
| WASD / Arrows | Move cursor |
| R | Restart |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to observe

- Press Space to start — targets appear at the top
- Fire pulses upward to hit targets before they expire
- Red dots at bottom show miss count
- Background changes color for game state:
  - Dark blue = playing
  - Bright blue = wave clear
  - Green = won
  - Red = lost
- Distinct sounds: fire, hit, miss, wave clear, win, lose

## Architecture

Game mode state machine (READY → PLAYING → WAVE_CLEAR → WON/LOST) lives
outside ECS. World entities (player, pulses, targets) live in ECS.
This separation is the canonical pattern for game progression + world state.
