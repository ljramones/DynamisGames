package org.dynamisengine.games.ecsbasics;

import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;

import java.util.ArrayList;
import java.util.List;

import static org.dynamisengine.games.ecsbasics.Components.*;

/**
 * Simple ECS systems for the basics example.
 *
 * Each system is a static method that reads/writes components on the world.
 * This is the canonical pattern: systems are functions over the world, not objects.
 */
public final class Systems {

    private Systems() {}

    /**
     * Movement system: applies velocity to position for all entities
     * that have both Position and Velocity.
     */
    public static void movement(World world, float dt) {
        var query = world.query(new QueryBuilder().allOf(POSITION, VELOCITY).build());
        for (EntityId entity : query) {
            Position pos = world.get(entity, POSITION).orElseThrow();
            Velocity vel = world.get(entity, VELOCITY).orElseThrow();
            world.add(entity, POSITION, new Position(
                    pos.x() + vel.vx() * dt,
                    pos.y() + vel.vy() * dt));
        }
    }

    /**
     * Lifetime system: ages all entities with Lifetime component.
     * Returns the list of entities that expired this tick (for audio/cleanup).
     */
    public static List<EntityId> lifetime(World world, float dt) {
        var query = world.query(new QueryBuilder().allOf(LIFETIME).build());
        List<EntityId> expired = new ArrayList<>();
        for (EntityId entity : query) {
            Lifetime lt = world.get(entity, LIFETIME).orElseThrow();
            Lifetime updated = lt.withAge(lt.ageSeconds() + dt);
            if (updated.isExpired()) {
                expired.add(entity);
            } else {
                world.add(entity, LIFETIME, updated);
            }
        }
        return expired;
    }

    /**
     * Cleanup system: destroys all entities in the expired list.
     */
    public static void cleanup(World world, List<EntityId> expired) {
        for (EntityId entity : expired) {
            world.destroyEntity(entity);
        }
    }

    /**
     * Spawn system: creates a pulse entity at the given position.
     */
    public static EntityId spawnPulse(World world, float x, float y, long tick) {
        EntityId entity = world.createEntity();
        world.add(entity, POSITION, new Position(x, y));
        world.add(entity, VELOCITY, new Velocity(
                (float) (Math.random() - 0.5) * 2f,  // slight random drift
                (float) (Math.random() - 0.5) * 2f));
        world.add(entity, LIFETIME, new Lifetime(0f, 2.0f));
        world.add(entity, PULSE, new PulseTag(tick));
        return entity;
    }

    /**
     * Count entities matching a query.
     */
    public static int count(World world, org.dynamisengine.ecs.api.query.QuerySpec spec) {
        int n = 0;
        for (EntityId ignored : world.query(spec)) n++;
        return n;
    }
}
