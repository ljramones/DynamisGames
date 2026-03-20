package org.dynamisengine.games.hello;

import org.dynamisengine.audio.dsp.device.AudioTelemetry;
import org.dynamisengine.input.core.InputTelemetry;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;
import org.dynamisengine.worldengine.api.telemetry.SubsystemTelemetry;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;

/**
 * Dynamis game with real Audio and Input subsystems.
 */
public final class HelloWorldGame implements WorldApplication {

    @Override
    public void initialize(GameContext context) {
        System.out.println("[HelloWorld] Initialized!");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        long tick = context.tick();

        if (tick % 30 == 0) {
            WorldTelemetrySnapshot t = context.telemetry();
            if (t != null) {
                System.out.println("[Telemetry] " + t.statusLine());
            }
        }

        if (tick >= 120) {
            WorldTelemetrySnapshot t = context.telemetry();
            if (t != null) {
                System.out.println();
                System.out.println(t.detailedReport());

                // Print Audio detail if available
                SubsystemTelemetry audioSlot = t.subsystem("Audio");
                if (audioSlot != null && audioSlot.hasDetail()) {
                    AudioTelemetry audio = audioSlot.detailAs(AudioTelemetry.class);
                    if (audio != null) {
                        System.out.println("--- Audio Detail ---");
                        System.out.println("  State:       " + audio.state());
                        System.out.println("  Backend:     " + audio.backendName());
                        System.out.println("  Callbacks:   " + audio.callbackCount());
                        System.out.println("  Underruns:   " + audio.ringUnderruns());
                        System.out.println("  Swap gen:    " + audio.swapGeneration());
                    }
                }

                // Print Input detail if available
                SubsystemTelemetry inputSlot = t.subsystem("Input");
                if (inputSlot != null && inputSlot.hasDetail()) {
                    InputTelemetry input = inputSlot.detailAs(InputTelemetry.class);
                    if (input != null) {
                        System.out.println("--- Input Detail ---");
                        System.out.println("  Devices:     " + input.connectedDevices().size());
                        System.out.println("  Events:      " + input.totalEventsProcessed());
                        System.out.println("  " + input.statusLine());
                    }
                }
            }
            System.out.println();
            System.out.println("[HelloWorld] Reached tick 120. Stopping.");
            context.requestStop();
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[HelloWorld] Shutdown complete.");
    }
}
