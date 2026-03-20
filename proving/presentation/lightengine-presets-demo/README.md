# LightEngine Presets Demo

Scene construction ergonomics via SceneBuilder and preset APIs.

## What it proves

- **SceneBuilder** -- fluent builder reduces SceneDescriptor boilerplate
- **ScenePresets** -- showcase, studio, night, debug (return SceneBuilder for customization)
- **CameraPresets** -- orbit, editorPerspective, showcase, topDown
- **LightPresets** -- directionalSun, directionalMoon, pointAccent, studioKey/Fill
- **EnvironmentPresets** -- defaultAmbient, outdoorBright, indoorDim, night
- **Real SceneDescriptor** -- presets produce actual LightEngine API types
- **Preset-driven rendering** -- lights/ambient extracted from SceneDescriptor

## The key contrast

Scene construction goes from ~30 lines of raw record construction to:

```java
SceneDescriptor scene = ScenePresets.showcase().build();
```

Or with customization:

```java
SceneDescriptor scene = ScenePresets.nightScene()
    .light(LightPresets.pointAccent(2, 1.5f, 0))
    .build();
```

## Controls

| Key | Action |
|-----|--------|
| 1 | Showcase preset |
| 2 | Studio preset |
| 3 | Night preset |
| 4 | Debug preset |
| A/D/W/S | Orbit camera |
| Q/E | Zoom |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
