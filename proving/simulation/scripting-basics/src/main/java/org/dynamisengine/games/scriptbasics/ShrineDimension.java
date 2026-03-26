package org.dynamisengine.games.scriptbasics;

import java.util.List;
import org.dynamisengine.scripting.api.value.WorldPatch;
import org.dynamisengine.scripting.spi.CanonDimensionProvider;

/**
 * Minimal canonical dimension for the shrine scenario.
 *
 * <p>Every ScriptingRuntime requires at least one CanonDimensionProvider.
 * Dimensions represent orthogonal aspects of world state that the scripting
 * layer can reason about (e.g., economics, politics, ecology).
 *
 * <p>For this exemplar, the dimension is intentionally minimal.
 */
public final class ShrineDimension implements CanonDimensionProvider {

    @Override public String dimensionId() { return "shrine"; }
    @Override public String dimensionName() { return "Shrine"; }
    @Override public List<String> canonicalObjectTypes() { return List.of("shrine.state"); }

    @Override
    public void onWorldPatchApplied(WorldPatch patch) {
        // Shrine state is managed in gameplay code for this exemplar.
    }
}
