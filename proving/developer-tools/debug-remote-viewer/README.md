# Debug Remote Viewer

Connects to a running engine process via TCP and renders the debug overlay
from streamed `DebugViewSnapshot` data — without running the engine locally.

## What it proves

- **External observability** — the debug spine is observable from outside the engine
- **Reuse** — same overlay builder, renderer, and controls as the in-engine version
- **Replay from stream** — received snapshots accumulate in a ring buffer for replay
- **No engine coupling** — only needs a TCP connection and the UI libraries

## Architecture

```
Engine (debug-subsystem-showcase)
  → TcpExporter (port 9876)
  → length-prefixed JSON frames

Remote Viewer
  → TcpSnapshotClient (background reader thread)
  → DebugViewSnapshot ring buffer (300 frames)
  → DebugOverlayBuilder → OpenGlDebugOverlayRenderer → screen
```

## Usage

### 1. Start the engine with TCP export

```bash
cd debug-subsystem-showcase
./build.sh && ./run.sh
# Starts TCP telemetry server on port 9876
```

### 2. Start the remote viewer

```bash
cd debug-remote-viewer
./build.sh && ./run.sh
# Connects to localhost:9876 by default
```

Or specify host/port:

```bash
./run.sh 192.168.1.50 9876
```

## Controls

| Key | Action |
|-----|--------|
| T   | Toggle live/replay mode |
| ,   | Step backward 10 frames (replay) |
| .   | Step forward 10 frames (replay) |
| F   | Toggle focus mode |
| [ ] | Cycle panels in focus mode |
| Tab | Toggle overlay |
| Esc | Quit |

## What to observe

- Status bar shows connection state and received frame count
- Overlay updates in real time as the engine runs
- Press T to freeze and step through received history
- Focus mode works identically to the in-engine version
- Disconnect/reconnect is handled gracefully (auto-retry every 2s)

## Build & Run

```bash
./build.sh
./run.sh [host] [port]
```
