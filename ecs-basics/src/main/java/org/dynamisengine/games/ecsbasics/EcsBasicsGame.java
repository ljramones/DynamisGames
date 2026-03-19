package org.dynamisengine.games.ecsbasics;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.dsp.SoftwareMixer;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.ecsbasics.subsystem.AudioSubsystem;
import org.dynamisengine.games.ecsbasics.subsystem.WindowInputSubsystem;
import org.dynamisengine.games.ecsbasics.subsystem.WindowSubsystem;
import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.AxisId;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.bind.AxisComposite2D;
import org.dynamisengine.input.api.bind.KeyBinding;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.List;
import java.util.Map;

import static org.dynamisengine.games.ecsbasics.Components.*;

/**
 * DynamisECS as the canonical gameplay state model.
 *
 * Same concept as the interaction sandbox, but all state lives in ECS:
 * - Player entity has Position (moved by input)
 * - Pulse entities have Position, Velocity, Lifetime, PulseTag
 * - Systems: movement, lifetime aging, cleanup
 * - Audio triggers on spawn/expire
 */
public final class EcsBasicsGame implements WorldApplication {

    static final ActionId SPAWN = new ActionId("spawn");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT  = new ActionId("quit");
    static final AxisId MOVE_X = new AxisId("moveX");
    static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId CTX = new ContextId("ecsbasics");

    private static final int KEY_W = 87, KEY_A = 65, KEY_S = 83, KEY_D = 68;
    private static final int KEY_UP = 265, KEY_DOWN = 264, KEY_LEFT = 263, KEY_RIGHT = 262;
    private static final int KEY_SPACE = 32, KEY_R = 82, KEY_ESC = 256;
    private static final float CURSOR_SPEED = 8.0f;
    private static final float SR = AcousticConstants.SAMPLE_RATE;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final DefaultInputProcessor processor;

    private EntityId playerEntity;
    private long totalSpawned = 0;
    private long totalExpired = 0;
    private boolean wasMoving = false;

    public EcsBasicsGame(WindowSubsystem w, WindowInputSubsystem i,
                         AudioSubsystem a, DefaultInputProcessor p) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a; this.processor = p;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(SPAWN, List.of(new KeyBinding(KEY_SPACE, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(MOVE_X, List.of(
                                new AxisComposite2D(MOVE_X, new AxisId("_u1"), KEY_A, KEY_D, 0, 0, 1f),
                                new AxisComposite2D(MOVE_X, new AxisId("_u2"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f)),
                        MOVE_Y, List.of(
                                new AxisComposite2D(new AxisId("_u3"), MOVE_Y, 0, 0, KEY_S, KEY_W, 1f),
                                new AxisComposite2D(new AxisId("_u4"), MOVE_Y, 0, 0, KEY_DOWN, KEY_UP, 1f))),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        World world = context.ecsWorld();

        // Create player entity with position
        playerEntity = world.createEntity();
        world.add(playerEntity, POSITION, new Position(0f, 0f));
        world.add(playerEntity, PLAYER, new PlayerTag());

        System.out.println("=== ECS Basics ===");
        System.out.println("DynamisECS as the canonical gameplay state model.");
        System.out.println("Same gameplay as 'interaction' — but all state is in ECS.");
        System.out.println();
        System.out.println("Controls: WASD/Arrows=move, Space=spawn, R=reset, Esc=quit");
        System.out.println();
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }

        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;

        World world = context.ecsWorld();
        SoftwareMixer mixer = audioSub.mixer();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Reset: destroy all pulses, reset player position
        if (frame.pressed(RESET)) {
            var pulses = world.query(new QueryBuilder().allOf(PULSE).build());
            for (EntityId e : pulses) world.destroyEntity(e);
            world.add(playerEntity, POSITION, new Position(0f, 0f));
            totalSpawned = 0; totalExpired = 0;
            playSound(mixer, 440f, 0.15f, 0.001f, 0.02f);
            System.out.println("  [Reset]");
        }

        // Move player entity
        float mx = frame.axis(MOVE_X);
        float my = frame.axis(MOVE_Y);
        if (mx != 0 || my != 0) {
            Position pos = world.get(playerEntity, POSITION).orElse(new Position(0, 0));
            world.add(playerEntity, POSITION, new Position(
                    pos.x() + mx * CURSOR_SPEED * deltaSeconds,
                    pos.y() + my * CURSOR_SPEED * deltaSeconds));
            if (!wasMoving) {
                playSound(mixer, 1200f, 0.08f, 0.001f, 0.015f);
            }
            wasMoving = true;
            if (context.tick() % 10 == 0) {
                Position p = world.get(playerEntity, POSITION).orElseThrow();
                int pulseCount = Systems.count(world,
                        new QueryBuilder().allOf(PULSE).build());
                System.out.printf("  [Player] (%.1f, %.1f) pulses=%d%n", p.x(), p.y(), pulseCount);
            }
        } else {
            wasMoving = false;
        }

        // Spawn pulse at player position
        if (frame.pressed(SPAWN)) {
            Position pos = world.get(playerEntity, POSITION).orElse(new Position(0, 0));
            EntityId pulse = Systems.spawnPulse(world, pos.x(), pos.y(), context.tick());
            totalSpawned++;
            playSound(mixer, 660f, 0.2f, 0.003f, 0.06f);
            System.out.printf("  [Spawn] Pulse entity %d at (%.1f, %.1f)%n",
                    pulse.id(), pos.x(), pos.y());
        }

        // Run ECS systems
        Systems.movement(world, deltaSeconds);
        List<EntityId> expired = Systems.lifetime(world, deltaSeconds);

        // Handle expiry: audio + cleanup
        for (EntityId e : expired) {
            Position pos = world.get(e, POSITION).orElse(new Position(0, 0));
            totalExpired++;
            playSound(mixer, 330f, 0.1f, 0.01f, 0.1f);
            System.out.printf("  [Expire] Entity %d at (%.1f, %.1f)%n", e.id(), pos.x(), pos.y());
        }
        Systems.cleanup(world, expired);

        // Periodic status
        if (context.tick() % 120 == 0 && context.tick() > 0) {
            int entities = world.entities().size();
            int pulses = Systems.count(world, new QueryBuilder().allOf(PULSE).build());
            System.out.printf("  [Status] entities=%d pulses=%d spawned=%d expired=%d%n",
                    entities, pulses, totalSpawned, totalExpired);
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("%n[EcsBasics] Final: spawned=%d expired=%d entities=%d%n",
                totalSpawned, totalExpired, context.ecsWorld().entities().size());
        var t = context.telemetry();
        if (t != null) System.out.println(t.statusLine());
        System.out.println("[EcsBasics] Shutdown.");
    }

    private void playSound(SoftwareMixer mixer, float freq, float amp, float attack, float decay) {
        var osc = new SineOscillator(freq, amp, SR);
        var env = new Envelope(attack, decay, 0f, 0.02f, SR);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(mixer, new ProceduralAudioAsset(synth));
    }
}
