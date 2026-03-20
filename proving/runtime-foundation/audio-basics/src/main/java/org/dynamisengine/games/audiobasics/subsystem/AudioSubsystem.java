package org.dynamisengine.games.audiobasics.subsystem;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.core.AcousticEventQueueImpl;
import org.dynamisengine.audio.core.AcousticSnapshotManager;
import org.dynamisengine.audio.designer.MixSnapshotManager;
import org.dynamisengine.audio.dsp.SoftwareMixer;
import org.dynamisengine.audio.dsp.device.AudioDeviceManager;
import org.dynamisengine.audio.dsp.device.AudioTelemetry;
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

/**
 * Bridges DynamisAudio's real runtime into the WorldEngine subsystem contract.
 *
 * Creates a SoftwareMixer and AudioDeviceManager, discovers the platform
 * backend (CoreAudio on macOS), and provides real AudioTelemetry.
 */
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
            // Create the real audio pipeline
            AcousticSnapshotManager snapshotMgr = new AcousticSnapshotManager();
            AcousticEventQueueImpl eventQueue = new AcousticEventQueueImpl();
            MixSnapshotManager mixSnapshotMgr = new MixSnapshotManager();

            // SoftwareMixer with NullAudioDevice for the legacy push path
            // (the real output goes through AudioDeviceManager's pull model)
            this.mixer = new SoftwareMixer(snapshotMgr, eventQueue,
                    new NullAudioDevice(), mixSnapshotMgr);

            // AudioDeviceManager discovers platform backend (CoreAudio on macOS)
            this.deviceManager = new AudioDeviceManager();
            deviceManager.initialize(mixer);

            initialized = true;
        } catch (Exception e) {
            lastError = e.getMessage();
            // Non-fatal — engine can run without audio
            initialized = true;
        }
    }

    @Override
    public void start() throws DynamisInitException {
        if (initialized && deviceManager != null) {
            try {
                deviceManager.start();
            } catch (Exception e) {
                lastError = "Audio start failed: " + e.getMessage();
            }
        }
    }

    @Override
    public void tick(long tick, float dt) throws DynamisTickException {
        // Audio runs on its own threads — no per-tick work here
    }

    @Override
    public void stop() throws DynamisShutdownException {
        if (deviceManager != null) {
            deviceManager.stop();
        }
    }

    @Override
    public void shutdown() throws DynamisShutdownException {
        if (deviceManager != null) {
            deviceManager.shutdown();
        }
        initialized = false;
    }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        if (lastError != null) return SubsystemHealth.degraded(name(), lastError, 0);
        if (deviceManager != null && deviceManager.isRunning()) {
            return SubsystemHealth.healthy(name(), 0);
        }
        return SubsystemHealth.degraded(name(), "device manager not running", 0);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        if (deviceManager != null) {
            return Optional.of(deviceManager.captureTelemetry());
        }
        return Optional.empty();
    }

    public AudioDeviceManager deviceManager() { return deviceManager; }
    public SoftwareMixer mixer() { return mixer; }
}
