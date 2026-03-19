# ECS Basics

DynamisECS as the **canonical gameplay state model**.

Same gameplay concept as the `interaction` module — cursor movement, pulse spawning,
lifetime expiry, audio feedback — but all state lives in ECS entities and components.

## What it demonstrates

- Entity creation and destruction through the ECS World
- Typed components: Position, Velocity, Lifetime, PlayerTag, PulseTag
- Systems as functions: movement, lifetime aging, cleanup
- Queries: `allOf(POSITION, VELOCITY)`, `allOf(PULSE)`, etc.
- Input → ECS mutation → system update → audio feedback
- WorldEngine tick integration (ECS world from GameContext)

## Controls

| Key | Action |
|-----|--------|
| WASD / Arrows | Move player entity |
| Space | Spawn pulse entity at player position |
| R | Reset (destroy all pulses, reset position) |
| Esc | Quit |

## Build & Run

```bash
./build.sh
./run.sh
```

## What to observe

- Player entity moves via Position component updates
- Pulse entities spawn with Position, Velocity, Lifetime components
- Pulses drift slightly (random velocity) and expire after 2 seconds
- Spawn → pop sound (660 Hz), expire → softer tone (330 Hz)
- Entity counts in status line show creation/destruction cycle

## Key ECS patterns shown

```java
// Define components
static final ComponentKey<Position> POSITION = ComponentKey.of("position", Position.class);

// Create entity
EntityId entity = world.createEntity();
world.add(entity, POSITION, new Position(x, y));

// Query entities
var query = world.query(new QueryBuilder().allOf(POSITION, VELOCITY).build());
for (EntityId e : query) { ... }

// Destroy entity
world.destroyEntity(entity);
```

## Comparison with `interaction`

| Aspect | interaction | ecs-basics |
|--------|-------------|------------|
| State model | Hand-managed `Pulse` list | ECS entities + components |
| Object lifecycle | `ArrayList` add/remove | `createEntity` / `destroyEntity` |
| Queries | Linear iteration | `QueryBuilder.allOf().build()` |
| Scalability | Manual | Engine-supported |
