package org.dynamisengine.games.physics;

import org.dynamisengine.audio.api.AcousticConstants;
import org.dynamisengine.audio.procedural.*;
import org.dynamisengine.collision.shapes.CollisionShape;
import org.dynamisengine.core.entity.EntityId;
import org.dynamisengine.ecs.api.component.ComponentKey;
import org.dynamisengine.ecs.api.query.QueryBuilder;
import org.dynamisengine.ecs.api.world.World;
import org.dynamisengine.games.physics.subsystem.*;
import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.physics.api.PhysicsWorldFactory;
import org.dynamisengine.physics.api.body.*;
import org.dynamisengine.physics.api.config.PhysicsBackend;
import org.dynamisengine.physics.api.config.PhysicsWorldConfig;
import org.dynamisengine.physics.api.event.*;
import org.dynamisengine.physics.api.material.PhysicsMaterial;
import org.dynamisengine.physics.api.world.PhysicsWorld;
import org.dynamisengine.physics.ode4j.Ode4jBackendRegistrar;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.worldengine.api.GameContext;
import org.dynamisengine.worldengine.api.WorldApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Projectile range sandbox proving DynamisPhysics integration.
 *
 * A launcher at the bottom fires spheres at adjustable angles.
 * Targets sit on a ground plane. Real ODE4J physics handles gravity,
 * trajectories, and collisions. Contact events trigger audio and scoring.
 *
 * WASD=aim/move, Space=fire, R=reset, Esc=quit
 */
public final class PhysicsGame implements WorldApplication {

    // ECS components — track which physics bodies belong to which entities
    record PhysicsBody(RigidBodyHandle handle) {}
    record ProjectileTag() {}
    record TargetTag(boolean hit) {}
    record EcsPosition(float x, float y) {}
    record EcsRadius(float r) {}

    static final ComponentKey<PhysicsBody> BODY = ComponentKey.of("phys.body", PhysicsBody.class);
    static final ComponentKey<ProjectileTag> PROJ = ComponentKey.of("phys.proj", ProjectileTag.class);
    static final ComponentKey<TargetTag> TARG = ComponentKey.of("phys.targ", TargetTag.class);
    static final ComponentKey<EcsPosition> POS = ComponentKey.of("phys.pos", EcsPosition.class);
    static final ComponentKey<EcsRadius> RAD = ComponentKey.of("phys.rad", EcsRadius.class);

    // Input
    static final ActionId FIRE = new ActionId("fire");
    static final ActionId RESET = new ActionId("reset");
    static final ActionId QUIT = new ActionId("quit");
    static final AxisId AIM = new AxisId("aim");
    static final AxisId MOVE = new AxisId("move");
    private static final ContextId CTX = new ContextId("physics");
    private static final int KEY_W = 87, KEY_A = 65, KEY_S = 83, KEY_D = 68;
    private static final int KEY_UP = 265, KEY_DOWN = 264, KEY_LEFT = 263, KEY_RIGHT = 262;
    private static final int KEY_SPACE = 32, KEY_R = 82, KEY_ESC = 256;

    private final WindowSubsystem windowSub;
    private final WindowInputSubsystem inputSub;
    private final AudioSubsystem audioSub;
    private final PhysicsRenderer renderer = new PhysicsRenderer();

    // Physics world
    private PhysicsWorld physicsWorld;
    private RigidBodyHandle groundBody;

    // Game state
    private float launchX = 0f;
    private float launchY = 1.5f;
    private float aimAngle = 60f; // degrees from horizontal
    private int score = 0;
    private int shotsFired = 0;

    // Render arrays (pre-allocated)
    private final float[] projX = new float[128], projY = new float[128], projR = new float[128];
    private final float[] targX = new float[32], targY = new float[32], targR = new float[32];
    private final boolean[] targHit = new boolean[32];

    public PhysicsGame(WindowSubsystem w, WindowInputSubsystem i, AudioSubsystem a) {
        this.windowSub = w; this.inputSub = i; this.audioSub = a;
    }

    static DefaultInputProcessor createProcessor() {
        InputMap map = new InputMap(CTX,
                Map.of(FIRE, List.of(new KeyBinding(KEY_SPACE, 0)),
                        RESET, List.of(new KeyBinding(KEY_R, 0)),
                        QUIT, List.of(new KeyBinding(KEY_ESC, 0))),
                Map.of(AIM, List.of(
                                new AxisComposite2D(AIM, new AxisId("_"), KEY_DOWN, KEY_UP, 0, 0, 1f),
                                new AxisComposite2D(AIM, new AxisId("_2"), KEY_S, KEY_W, 0, 0, 1f)),
                        MOVE, List.of(
                                new AxisComposite2D(MOVE, new AxisId("_3"), KEY_LEFT, KEY_RIGHT, 0, 0, 1f),
                                new AxisComposite2D(MOVE, new AxisId("_4"), KEY_A, KEY_D, 0, 0, 1f))),
                false);
        var proc = new DefaultInputProcessor(Map.of(CTX, map));
        proc.pushContext(CTX);
        return proc;
    }

    @Override
    public void initialize(GameContext context) {
        renderer.initialize();

        // Register ODE4J backend (static initializer does the registration)
        new Ode4jBackendRegistrar();

        // Create physics world with gravity
        PhysicsWorldConfig config = PhysicsWorldConfig.defaults(PhysicsBackend.ODE4J);
        physicsWorld = PhysicsWorldFactory.create(config);

        // Create ground plane (static, at Y=0)
        groundBody = physicsWorld.spawnRigidBody(
                RigidBodyConfig.builder(CollisionShape.plane(0, 1, 0, 0), 0)
                        .mode(BodyMode.STATIC)
                        .material(PhysicsMaterial.ROCK)
                        .build());

        // Spawn targets
        spawnTargets(context.ecsWorld());

        System.out.println("=== Physics Basics ===");
        System.out.println("Projectile range with real ODE4J physics.");
        System.out.println("W/S=aim up/down, A/D=move launcher, Space=fire, R=reset, Esc=quit");
    }

    @Override
    public void update(GameContext context, float dt) {
        if (windowSub.isCloseRequested()) { context.requestStop(); return; }
        InputFrame frame = inputSub.lastFrame();
        if (frame == null) return;
        World world = context.ecsWorld();

        if (frame.pressed(QUIT)) { context.requestStop(); return; }

        // Reset
        if (frame.pressed(RESET)) {
            // Destroy all projectiles
            for (EntityId e : world.query(new QueryBuilder().allOf(PROJ, BODY).build())) {
                PhysicsBody pb = world.get(e, BODY).orElseThrow();
                if (pb.handle().isAlive()) physicsWorld.destroyRigidBody(pb.handle());
                world.destroyEntity(e);
            }
            // Destroy and respawn targets
            for (EntityId e : world.query(new QueryBuilder().allOf(TARG, BODY).build())) {
                PhysicsBody pb = world.get(e, BODY).orElseThrow();
                if (pb.handle().isAlive()) physicsWorld.destroyRigidBody(pb.handle());
                world.destroyEntity(e);
            }
            spawnTargets(world);
            score = 0;
            shotsFired = 0;
            launchX = 0f;
            aimAngle = 60f;
            playSound(440f, 0.15f, 0.001f, 0.02f);
            System.out.println("  [Reset]");
        }

        // Aim (W/S or Up/Down adjusts angle)
        float aimInput = frame.axis(AIM);
        if (aimInput != 0) {
            aimAngle = clamp(aimAngle + aimInput * 90f * dt, 10f, 85f);
        }

        // Move launcher (A/D or Left/Right)
        float moveInput = frame.axis(MOVE);
        if (moveInput != 0) {
            launchX = clamp(launchX + moveInput * 6f * dt, -8f, 8f);
        }

        // Fire projectile
        if (frame.pressed(FIRE)) {
            float rad = (float) Math.toRadians(aimAngle);
            float speed = 15f; // m/s launch speed
            float vx = (float) Math.cos(rad) * speed;
            float vy = (float) Math.sin(rad) * speed;

            // Create physics body — sphere with mass 1kg
            RigidBodyHandle handle = physicsWorld.spawnRigidBody(
                    RigidBodyConfig.builder(CollisionShape.sphere(0.3f), 1.0f)
                            .worldTransform(new Matrix4f().identity().translate(launchX, launchY, 0))
                            .linearVelocity(new Vector3f(vx, vy, 0))
                            .material(PhysicsMaterial.METAL)
                            .mode(BodyMode.DYNAMIC)
                            .build());

            // Create ECS entity linked to physics body
            EntityId e = world.createEntity();
            world.add(e, BODY, new PhysicsBody(handle));
            world.add(e, PROJ, new ProjectileTag());
            world.add(e, POS, new EcsPosition(launchX, launchY));
            world.add(e, RAD, new EcsRadius(0.3f));

            shotsFired++;
            playSound(220f, 0.2f, 0.001f, 0.03f); // launch thump
        }

        // Step physics simulation
        physicsWorld.step(dt);

        // Drain contact events
        List<PhysicsEvent> events = physicsWorld.drainEvents();
        for (PhysicsEvent event : events) {
            if (event instanceof ContactEvent contact) {
                handleContact(contact, world);
            }
        }

        // Sync physics state → ECS positions
        for (EntityId e : world.query(new QueryBuilder().allOf(BODY, POS).build())) {
            PhysicsBody pb = world.get(e, BODY).orElseThrow();
            if (!pb.handle().isAlive()) continue;
            BodyState state = physicsWorld.getBodyState(pb.handle());
            world.add(e, POS, new EcsPosition(state.position().x(), state.position().y()));
        }

        // Clean up projectiles that fell below ground or went out of bounds
        List<EntityId> toDestroy = new ArrayList<>();
        for (EntityId e : world.query(new QueryBuilder().allOf(PROJ, POS, BODY).build())) {
            EcsPosition pos = world.get(e, POS).orElseThrow();
            if (pos.y() < -2f || Math.abs(pos.x()) > 15f) {
                toDestroy.add(e);
            }
        }
        for (EntityId e : toDestroy) {
            PhysicsBody pb = world.get(e, BODY).orElseThrow();
            if (pb.handle().isAlive()) physicsWorld.destroyRigidBody(pb.handle());
            world.destroyEntity(e);
        }

        // Render: gather ECS state into arrays
        int pc = 0;
        for (EntityId e : world.query(new QueryBuilder().allOf(PROJ, POS, RAD).build())) {
            if (pc >= projX.length) break;
            EcsPosition p = world.get(e, POS).orElseThrow();
            EcsRadius r = world.get(e, RAD).orElseThrow();
            projX[pc] = p.x(); projY[pc] = p.y(); projR[pc] = r.r(); pc++;
        }

        int tc = 0;
        for (EntityId e : world.query(new QueryBuilder().allOf(TARG, POS, RAD).build())) {
            if (tc >= targX.length) break;
            EcsPosition p = world.get(e, POS).orElseThrow();
            EcsRadius r = world.get(e, RAD).orElseThrow();
            TargetTag tag = world.get(e, TARG).orElseThrow();
            targX[tc] = p.x(); targY[tc] = p.y(); targR[tc] = r.r();
            targHit[tc] = tag.hit();
            tc++;
        }

        var ws = windowSub.window().framebufferSize();
        renderer.render(launchX, launchY, aimAngle,
                projX, projY, projR, pc,
                targX, targY, targR, targHit, tc,
                score, ws.width(), ws.height());
        windowSub.window().swapBuffers();

        // Periodic status
        if (context.tick() % 180 == 0 && context.tick() > 0) {
            System.out.printf("  [Status] Score=%d Shots=%d Angle=%.0f%n", score, shotsFired, aimAngle);
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.printf("%n[Physics] Final: Score=%d Shots=%d%n", score, shotsFired);
        if (physicsWorld != null) {
            physicsWorld.destroy();
        }
    }

    private void handleContact(ContactEvent contact, World world) {
        // Check if a projectile hit a target
        RigidBodyHandle a = contact.bodyA();
        RigidBodyHandle b = contact.bodyB();

        EntityId projEntity = findEntityByHandle(world, PROJ, a);
        if (projEntity == null) projEntity = findEntityByHandle(world, PROJ, b);

        EntityId targEntity = findEntityByHandle(world, TARG, a);
        if (targEntity == null) targEntity = findEntityByHandle(world, TARG, b);

        if (projEntity != null && targEntity != null) {
            // Projectile hit target!
            TargetTag tag = world.get(targEntity, TARG).orElse(new TargetTag(false));
            if (!tag.hit()) {
                world.add(targEntity, TARG, new TargetTag(true));
                score++;
                // Pitch varies with impact strength
                float freq = 880f + Math.min(contact.totalImpulse() * 50f, 500f);
                playSound(freq, 0.25f, 0.001f, 0.08f);
                System.out.printf("  [HIT] Score=%d impulse=%.1f%n", score, contact.totalImpulse());
            }
        } else if (projEntity != null && contact.totalImpulse() > 0.5f) {
            // Projectile hit ground — bounce sound
            float freq = 110f + contact.totalImpulse() * 20f;
            playSound(Math.min(freq, 400f), 0.1f, 0.001f, 0.03f);
        }
    }

    private EntityId findEntityByHandle(World world, ComponentKey<?> tag, RigidBodyHandle handle) {
        for (EntityId e : world.query(new QueryBuilder().allOf(tag, BODY).build())) {
            PhysicsBody pb = world.get(e, BODY).orElse(null);
            if (pb != null && pb.handle() == handle) return e;
        }
        return null;
    }

    private void spawnTargets(World world) {
        float[][] positions = {
                {4, 1.0f, 0.8f},   // x, y, radius
                {6, 1.0f, 0.6f},
                {8, 1.0f, 0.5f},
                {5, 3.0f, 0.7f},
                {7, 3.0f, 0.5f},
                {6, 5.0f, 0.6f},
        };
        for (float[] t : positions) {
            float x = t[0], y = t[1], r = t[2];

            // Static physics body for the target
            RigidBodyHandle handle = physicsWorld.spawnRigidBody(
                    RigidBodyConfig.builder(CollisionShape.sphere(r), 0)
                            .worldTransform(new Matrix4f().identity().translate(x, y, 0))
                            .mode(BodyMode.STATIC)
                            .material(PhysicsMaterial.METAL)
                            .build());

            EntityId e = world.createEntity();
            world.add(e, BODY, new PhysicsBody(handle));
            world.add(e, TARG, new TargetTag(false));
            world.add(e, POS, new EcsPosition(x, y));
            world.add(e, RAD, new EcsRadius(r));
        }
    }

    private void playSound(float freq, float amp, float atk, float dec) {
        var osc = new SineOscillator(freq, amp, AcousticConstants.SAMPLE_RATE);
        var env = new Envelope(atk, dec, 0f, 0.02f, AcousticConstants.SAMPLE_RATE);
        var synth = new SynthVoice(osc, env);
        synth.noteOn(freq, amp);
        QuickPlayback.play(audioSub.mixer(), new ProceduralAudioAsset(synth));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
