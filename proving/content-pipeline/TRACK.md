# Track: Content / Asset Pipeline

## Purpose

Prove that Dynamis can ingest, prepare, and consume runtime assets through a
single canonical pipeline, with MeshForge as the authoritative import/processing
system and MGI as the preferred runtime asset format.

## Core Doctrine

1. **MGI is the runtime format** -- interchange formats (glTF, OBJ, STL) are import-time only
2. **MeshForge owns asset truth** -- no per-backend reimplementation
3. **Proving modules validate integration, not performance** -- perf is MeshForge JMH benchmarks
4. **Extend MGI instead of preserving fallbacks** -- if runtime needs data, add it to MGI

## Proving Ladder

1. `mgi-basics` -- basic MGI load + render, no fallback
2. `mgi-runtime-integration` -- MeshForgeAssetService is used, MGI preferred
3. `mgi-materials` -- material data flows through MGI
4. `mgi-scene` -- full scene composition via MGI

## Optional

- `meshforge-import-basics` -- source -> MeshForge -> MGI pipeline
- `mgi-debug-inspection` -- runtime inspection of loaded MGI assets

## Position

After lightengine-presets-demo, before terrain/sky/large-scene modules.
