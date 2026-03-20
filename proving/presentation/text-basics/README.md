# Text Basics

First in-window text rendering in the Dynamis proving ladder.

## What it proves

- **OpenGL shaders** — first shader compilation (vertex + fragment) in the game ladder
- **STBEasyFont** — geometry-based text (no font files, no texture atlas)
- **HUD overlay** — score, entity count, FPS, cursor position, controls
- **Shader + scissor coexistence** — scene uses scissor-clear, HUD uses shader pipeline

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move cursor |
| Space | Spawn particle (+10 score) |
| R | Reset |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
