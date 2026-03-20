package org.dynamisengine.games.text.subsystem;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.core.AcousticEventQueueImpl;
import org.dynamisengine.audio.core.AcousticSnapshotManager;
import org.dynamisengine.audio.designer.MixSnapshotManager;
import org.dynamisengine.audio.dsp.SoftwareMixer;
import org.dynamisengine.audio.dsp.device.AudioDeviceManager;
import org.dynamisengine.audio.dsp.device.NullAudioDevice;
import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;

import java.util.Optional;
import java.util.Set;

public final class AudioSubsystem implements WorldSubsystem {

    private SoftwareMixer mixer;
    private AudioDeviceManager deviceManager;
    private volatile boolean initialized = false;
    private volatile String lastError = null;

    @Override public String name() { return WorldTelemetrySnapshot.AUDIO; }
    @Override public Set<String> dependencies() { return Set.of(); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        try {
            AcousticSnapshotManager snapshotMgr = new AcousticSnapshotManager();
            AcousticEventQueueImpl eventQueue = new AcousticEventQueueImpl();
            MixSnapshotManager mixSnapshotMgr = new MixSnapshotManager();
            this.mixer = new SoftwareMixer(snapshotMgr, eventQueue,
                    new NullAudioDevice(), mixSnapshotMgr);
            this.deviceManager = new AudioDeviceManager();
            deviceManager.initialize(mixer);
            initialized = true;
        } catch (Exception e) {
            lastError = e.getMessage();
            initialized = true;
        }
    }

    @Override
    public void start() throws DynamisInitException {
        if (initialized && deviceManager != null) {
            try { deviceManager.start(); }
            catch (Exception e) { lastError = "Audio start failed: " + e.getMessage(); }
        }
    }

    @Override public void tick(long tick, float dt) throws DynamisTickException {}

    @Override
    public void stop() throws DynamisShutdownException {
        if (deviceManager != null) deviceManager.stop();
    }

    @Override
    public void shutdown() throws DynamisShutdownException {
        if (deviceManager != null) deviceManager.shutdown();
        initialized = false;
    }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        if (lastError != null) return SubsystemHealth.degraded(name(), lastError, 0);
        if (deviceManager != null && deviceManager.isRunning())
            return SubsystemHealth.healthy(name(), 0);
        return SubsystemHealth.degraded(name(), "device manager not running", 0);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        if (deviceManager != null) return Optional.of(deviceManager.captureTelemetry());
        return Optional.empty();
    }

    public SoftwareMixer mixer() { return mixer; }
}
