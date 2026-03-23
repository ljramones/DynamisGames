# Debug Session Player

Loads a recorded NDJSON debug session file and provides full offline
replay with the same overlay, focus mode, and playback controls.

## What it proves

- **Offline debugging** — inspect recorded sessions without running the engine
- **Session sharing** — recorded files can be shared for collaborative debugging
- **Full fidelity** — same overlay builder, renderer, and controls as live debugging
- **Playback control** — play/pause, speed control, frame stepping, progress bar

## Usage

### 1. Record a session

Run the showcase (it auto-records to `debug-session.ndjson`):

```bash
cd debug-subsystem-showcase
./build.sh && ./run.sh
# Let it run through some phases, then Esc to quit
# File: debug-subsystem-showcase/debug-session.ndjson
```

### 2. Play it back

```bash
cd debug-session-player
./build.sh
./run.sh ../debug-subsystem-showcase/debug-session.ndjson
```

## Controls

| Key | Action |
|-----|--------|
| Space | Play/pause auto-advance |
| , | Step backward 1 frame |
| . | Step forward 1 frame |
| Home | Jump to first frame |
| End | Jump to last frame |
| 1 | Playback speed 0.25x |
| 2 | Playback speed 1x |
| 3 | Playback speed 2x |
| F | Toggle focus mode |
| [ ] | Cycle panels in focus mode |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

- Green progress bar at bottom shows position in recording
- Status bar shows PLAYING/PAUSED, frame count, and tick number
- Overlay panels update as you step through frames
- Trends reflect the historical data at each frame
- Focus mode works for deep inspection at any point in the recording
- Play at 0.25x to watch phase transitions in slow motion

## Build & Run

```bash
./build.sh
./run.sh <path-to-session.ndjson>
```
