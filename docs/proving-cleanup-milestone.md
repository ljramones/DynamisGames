# Proving Cleanup Milestone

**Date:** 2026-03-25
**Status:** Complete — 3 phases, 9,654 LOC net reduction

## Summary

Structural cleanup of `DynamisGames/proving` to eliminate infrastructure
duplication and establish proving modules as lean, canonical exemplars of
engine usage. All 41 modules compile green after cleanup.

## Phase Summary

| Phase | What | Files | Lines Deleted | Lines Added | Net |
|-------|------|-------|---------------|-------------|-----|
| **Phase 1** | Subsystem adapter dedup | 140 | 6,269 | 520 | -5,749 |
| **Phase 2a** | Shared record extraction | 78 | 1,775 | 145 | -1,630 |
| **Phase 2b** | SceneRenderer extraction | 18 | 2,312 | 37 | -2,275 |
| **Total** | | **236** | **10,356** | **702** | **-9,654** |

## What Moved to Base Repos

| Class | Copies Eliminated | Destination | Module |
|-------|-------------------|-------------|--------|
| WindowSubsystem | 23 → 0 | Already existed | `DynamisWindow/window-glfw` (GlfwWindowSubsystem) |
| AudioSubsystem | 23 → 1 | NEW module | `DynamisAudio/dynamis-audio-world-adapter` (AudioWorldSubsystem) |
| WindowInputSubsystem | 23 → 1 | NEW module | `DynamisInput/input-window-adapter` (WindowInputWorldSubsystem) |

## What Moved to Proving-Commons

| Item | Copies Eliminated | Package |
|------|-------------------|---------|
| SimpleMesh | 10 → 1 | `org.dynamisengine.games.commons.model` |
| SimpleMaterial | 9 → 1 | `org.dynamisengine.games.commons.model` |
| DirectionalLight | 8 → 1 | `org.dynamisengine.games.commons.model` |
| PointLight | 8 → 1 | `org.dynamisengine.games.commons.model` |
| MeshHandle | 10 → 1 | `org.dynamisengine.games.commons.model` |
| SceneRenderer | 8 → 1 | `org.dynamisengine.games.commons.render` |

## What Stayed in Proving (and Why)

| Item | Location | Why It Stays |
|------|----------|-------------|
| MeshRenderer | mesh-basics | Divergent shader (hemispheric, no lights) |
| MaterialRenderer | material-basics | Divergent shader (hardcoded light, material-only) |
| SceneRenderer (interaction) | interaction-rendered | Different technique (scissor-clear 2D) |
| InputSubsystem (2 copies) | hello-worldengine, input-basics | Demo-specific stubs without window dependency |
| All game classes (*Game.java) | Each module | Game-specific composition |
| All Main.java entry points | Each module | Per-demo wiring |
| All ECS Components/Systems | ecs-basics | Game-specific gameplay logic |
| All input bindings | window-input, input-basics | Game-specific action mappings |
| All DemoClips | animation modules | Demo-specific animation data |
| All debug proving modules | developer-tools/* | Prove subsystem capabilities |

## What Remains Intentionally Deferred

| Item | Reason |
|------|--------|
| Math utility extraction | OpenGL-specific matrix helpers embedded in renderers; needs stricter review before extraction to determine if they belong in Vectrix or proving-commons |
| DemoClips consolidation | 4 copies, but each is demo-specific content, not infrastructure |
| MeshRenderer/MaterialRenderer consolidation | Divergent enough that forced unification would hurt clarity |

## New Standard for Proving Modules

After this cleanup, proving modules should follow these rules:

1. **Use canonical base adapters** — GlfwWindowSubsystem, AudioWorldSubsystem, WindowInputWorldSubsystem
2. **Import shared types from proving-commons** — SimpleMesh, SimpleMaterial, DirectionalLight, PointLight, MeshHandle, SceneRenderer
3. **Do not copy infrastructure** — if a subsystem adapter or shared type is needed, add it to the appropriate base repo or proving-commons
4. **Keep only composition and demo-specific logic** — game classes, bindings, components, systems, entry points
5. **New renderers are OK** if they demonstrate a genuinely different technique; do not copy SceneRenderer for minor variations

## Architectural Lesson

> Proving modules are exemplars, not shadow infrastructure.

Any code that would be needed by a real engine user belongs in a base repo.
Any code that is shared across proving modules belongs in proving-commons.
Only game-specific composition and demo logic belongs in individual modules.

## Commits

| Repo | Commit | Description |
|------|--------|-------------|
| DynamisAudio | bb9580c | New `dynamis-audio-world-adapter` module |
| DynamisInput | 54d230d | New `input-window-adapter` module |
| DynamisGames | c789df9 | Phase 1: 69 subsystem adapters deleted |
| DynamisGames | 4f9851d | Phase 2a: 45 records extracted to proving-commons |
| DynamisGames | c3820d7 | Phase 2b: 8 SceneRenderers extracted to proving-commons |
