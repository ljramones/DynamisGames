package org.dynamisengine.games.skinned;

import org.dynamisengine.animis.clip.Clip;
import org.dynamisengine.animis.loader.AnimationLoadResult;
import org.dynamisengine.animis.loader.GltfAnimationLoader;
import org.dynamisengine.animis.runtime.pose.Pose;
import org.dynamisengine.animis.runtime.pose.PoseBuffer;
import org.dynamisengine.animis.runtime.sampling.ClipSampleResult;
import org.dynamisengine.animis.runtime.sampling.DefaultClipSampler;
import org.dynamisengine.animis.runtime.skinning.DefaultSkinningComputer;
import org.dynamisengine.animis.runtime.skinning.SkinningOutput;
import org.dynamisengine.animis.skeleton.Skeleton;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Canonical seam for skeletal animation: load, sample, skin.
 *
 * <p>Loads a GLB file via Animis, extracts skeleton and clips, and provides
 * per-frame joint matrix updates for the DynamisLightEngine skinned mesh pipeline.
 *
 * <p>Teaching flow:
 * <ol>
 *   <li>Load: GLB → Skeleton + Clips via {@link GltfAnimationLoader}</li>
 *   <li>Sample: advance time → {@link DefaultClipSampler} → PoseBuffer</li>
 *   <li>Skin: PoseBuffer → {@link DefaultSkinningComputer} → float[] jointMatrices</li>
 *   <li>Upload: pass jointMatrices to EngineRuntime.updateSkinnedMesh()</li>
 * </ol>
 */
public final class SkinnedCharacterController {

    private final Skeleton skeleton;
    private final List<Clip> clips;
    private final DefaultClipSampler sampler = new DefaultClipSampler();
    private final DefaultSkinningComputer skinner = new DefaultSkinningComputer();
    private final PoseBuffer poseBuffer;

    private int currentClipIndex = 0;
    private float animationTime = 0f;
    private float previousTime = 0f;
    private boolean paused = false;
    private float playbackSpeed = 1.0f;

    public SkinnedCharacterController(Path glbPath) throws IOException {
        AnimationLoadResult result = new GltfAnimationLoader().load(glbPath);
        if (result.skeletons().isEmpty()) {
            throw new IOException("GLB contains no skeletons: " + glbPath);
        }
        if (result.clips().isEmpty()) {
            throw new IOException("GLB contains no animation clips: " + glbPath);
        }
        this.skeleton = result.skeletons().getFirst();
        this.clips = List.copyOf(result.clips());
        this.poseBuffer = new PoseBuffer(skeleton.joints().size());
    }

    /**
     * Advance animation and compute skinning matrices for this frame.
     *
     * @param deltaSeconds frame delta time
     * @return joint matrices (jointCount * 16 floats) ready for GPU upload
     */
    public float[] update(float deltaSeconds) {
        if (!paused) {
            previousTime = animationTime;
            animationTime += deltaSeconds * playbackSpeed;
        }

        Clip clip = clips.get(currentClipIndex);

        // Sample the clip at current time → fills PoseBuffer with local joint transforms
        ClipSampleResult sampleResult = sampler.sample(
                clip, skeleton, animationTime, previousTime, true, poseBuffer);

        // Convert local pose to skinning matrices (world * inverseBindMatrix per joint)
        Pose pose = poseBuffer.toPose();
        SkinningOutput skinOutput = skinner.compute(skeleton, pose);

        return skinOutput.jointMatrices();
    }

    public void togglePause() { paused = !paused; }
    public void nextClip() {
        currentClipIndex = (currentClipIndex + 1) % clips.size();
        animationTime = 0f;
        previousTime = 0f;
    }

    public int jointCount() { return skeleton.joints().size(); }
    public String currentClipName() { return clips.get(currentClipIndex).name(); }
    public int clipCount() { return clips.size(); }
    public float animationTime() { return animationTime; }
    public float clipDuration() { return clips.get(currentClipIndex).durationSeconds(); }
    public boolean isPaused() { return paused; }
}
