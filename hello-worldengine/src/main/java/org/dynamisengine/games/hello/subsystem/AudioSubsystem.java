package org.dynamisengine.games.hello.subsystem;

import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;

import java.util.Optional;
import java.util.Set;

/**
 * Adapts DynamisAudio to the WorldSubsystem contract.
 *
 * For the proving module, Audio initializes in degraded mode (no mixer wired).
 * Full integration requires SoftwareMixer construction, which will come
 * when WorldEngine manages the full audio pipeline.
 */
public final class AudioSubsystem implements WorldSubsystem {

    private volatile boolean initialized = false;

    @Override public String name() { return WorldTelemetrySnapshot.AUDIO; }
    @Override public Set<String> dependencies() { return Set.of(); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        // Audio backend discovery happens here in full integration.
        // For proving: mark as initialized (degraded — no mixer).
        initialized = true;
    }

    @Override public void start() {}
    @Override public void tick(long tick, float dt) {}
    @Override public void stop() {}
    @Override public void shutdown() { initialized = false; }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        return SubsystemHealth.degraded(name(), "no mixer wired (proving mode)", 0);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        // Will return AudioTelemetry when fully wired
        return Optional.empty();
    }
}
