# Debug Session Compare

Side-by-side comparison of two recorded debug sessions for regression
analysis. Answers "what changed?" between runs.

## Usage

```bash
# Record two sessions (different scenarios or code versions)
cd debug-subsystem-showcase
./build.sh && ./run.sh    # record first session
mv debug-session.ndjson baseline.ndjson
mv debug-session.meta.json baseline.meta.json
./run.sh                  # record second session
mv debug-session.ndjson regression.ndjson

# Compare them
cd ../debug-session-compare
./build.sh
./run.sh ../debug-subsystem-showcase/baseline.ndjson ../debug-subsystem-showcase/regression.ndjson
```

## Controls

| Key | Action |
|-----|--------|
| Space | Play/pause (synchronized) |
| , / . | Step backward/forward 1 frame |
| Home/End | Jump to first/last frame |
| 1/2/3 | Playback speed (0.25x/1x/2x) |
| Tab | Switch active side highlight |
| Esc | Quit |

## What to observe

- Both sessions advance together (frame-synchronized)
- Side labels show scenario names from metadata
- Green divider line separates the two sessions
- Progress bar spans both sessions
- Look for:
  - different alert patterns between left and right
  - different trend shapes (one session degrading, other stable)
  - different subsystem panel contents
  - timing differences in event occurrence

## Build & Run

```bash
./build.sh
./run.sh <baseline.ndjson> <regression.ndjson>
```
