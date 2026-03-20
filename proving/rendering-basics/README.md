# Rendering Basics

First **visual output** from the Dynamis engine.

Opens a real GLFW window with an OpenGL 4.1 context. Draws:
- Dark blue background (brightens with more active pulses)
- Yellow cursor square (moves with WASD)
- Audio feedback on spawn/expire

## What it demonstrates

```
Input → ECS state → visual rendering + audio feedback
```

This is the first module where you can SEE the game state changing.

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move yellow cursor |
| Space | Spawn pulse (brightens background) |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to observe

- Yellow square moves smoothly with WASD
- Background gets brighter blue as more pulses are active
- Background dims as pulses expire (2 second lifetime)
- Spawn → pop sound, expire → softer tone
- Smooth 60Hz update with vsync
