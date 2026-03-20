# Lighting Basics

Directional + point light on proven meshes and materials.

## What it proves

- **DirectionalLight** record -- direction, color, intensity (sun-like)
- **PointLight** record -- position, color, intensity, radius with attenuation
- **Per-light shader binding** -- explicit enable/disable per light type
- **Light-material interaction** -- matte vs glossy respond differently to same lights
- **Moving point light** -- orbiting light with visible indicator sphere
- **Light toggles** -- 1/2/3 keys show each light's individual contribution
- **Blinn-Phong with explicit lights** -- ambient + directional + point contributions

## Scene

5 tori in an arc with different materials (Matte Red, Glossy Blue, Gold, Chrome, Purple),
lit by a warm directional sun and a cool orbiting point light.
Small unlit sphere shows point light position.

## Controls

| Key | Action |
|-----|--------|
| 1 | Toggle directional (sun) light |
| 2 | Toggle point light |
| 3 | Toggle point light motion |
| A/D or Left/Right | Orbit camera |
| W/S or Up/Down | Pitch camera |
| Q/E | Zoom |
| R | Reset |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```
