# Audio Basics

Interactive audio playback through the full Dynamis engine pipeline.

Opens a real GLFW window. Press keys to trigger procedural sounds that flow through:
```
SynthVoice → ProceduralAudioAsset → VoicePool → SoftwareMixer → CoreAudio → speakers
```

## Controls

| Key | Sound |
|-----|-------|
| 1 | Blip (880 Hz, short bright beep) |
| 2 | Thump (110 Hz, punchy bass) |
| 3 | Chirp (1320 Hz, sparkly) |
| 4 | Toggle drone (220 Hz, sustained) |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

Focus the window, then press keys. Sound plays through speakers. Telemetry prints every 5 seconds.

macOS note: Uses `-XstartOnFirstThread` for GLFW.

## What to observe

- Distinct sounds per key (different frequencies and envelopes)
- Drone toggles on/off with key 4
- Audio telemetry: callbacks, underruns, backend state
- Clean shutdown on Esc or window close

## Architecture

Three WorldEngine subsystems registered:
- **WindowSubsystem** — GLFW window, polls keyboard events
- **WindowInputSubsystem** — feeds events into InputProcessor, produces InputFrame
- **AudioSubsystem** — real SoftwareMixer + AudioDeviceManager with CoreAudio backend

Sound playback uses `QuickPlayback.play(mixer, new ProceduralAudioAsset(synthVoice))`.
