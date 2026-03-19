package org.dynamisengine.games.ecsbasics;

import org.dynamisengine.ecs.api.component.ComponentKey;

/**
 * Component definitions for the ECS basics example.
 *
 * Components are plain records. ComponentKeys are the typed handles
 * used to add/get/query components on entities.
 */
public final class Components {

    private Components() {}

    // -- Component records ---------------------------------------------------

    /** 2D position in logical space. */
    public record Position(float x, float y) {}

    /** 2D velocity in units per second. */
    public record Velocity(float vx, float vy) {}

    /** Tracks age and maximum lifetime for expiring entities. */
    public record Lifetime(float ageSeconds, float maxAgeSeconds) {
        public Lifetime withAge(float newAge) {
            return new Lifetime(newAge, maxAgeSeconds);
        }
        public boolean isExpired() {
            return ageSeconds >= maxAgeSeconds;
        }
        public float progress() {
            return Math.min(ageSeconds / maxAgeSeconds, 1f);
        }
    }

    /** Marker: this entity is the player cursor. */
    public record PlayerTag() {}

    /** Marker: this entity is a spawned pulse. */
    public record PulseTag(long spawnTick) {}

    // -- Component keys ------------------------------------------------------

    public static final ComponentKey<Position> POSITION =
            ComponentKey.of("ecsbasics.position", Position.class);

    public static final ComponentKey<Velocity> VELOCITY =
            ComponentKey.of("ecsbasics.velocity", Velocity.class);

    public static final ComponentKey<Lifetime> LIFETIME =
            ComponentKey.of("ecsbasics.lifetime", Lifetime.class);

    public static final ComponentKey<PlayerTag> PLAYER =
            ComponentKey.of("ecsbasics.player", PlayerTag.class);

    public static final ComponentKey<PulseTag> PULSE =
            ComponentKey.of("ecsbasics.pulse", PulseTag.class);
}
