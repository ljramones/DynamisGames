package org.dynamisengine.games.scene;

/**
 * Proving-level material definition.
 *
 * Captures the minimal surface parameters needed to show visibly
 * distinct appearances on the same geometry.
 *
 * @param baseColor    RGB base color [0,1]
 * @param ambient      ambient light contribution [0,1]
 * @param diffuse      diffuse response strength [0,1]
 * @param specular     specular highlight strength [0,1]
 * @param shininess    specular exponent (higher = tighter highlight)
 * @param shadingMode  how the surface is shaded
 */
public record SimpleMaterial(
        float[] baseColor,
        float ambient,
        float diffuse,
        float specular,
        float shininess,
        ShadingMode shadingMode
) {
    public enum ShadingMode {
        LIT,        // normal-based lighting with specular
        UNLIT,      // flat base color, no lighting
        WIREFRAME   // wireframe rendering
    }

    // --- Preset materials ---

    public static final SimpleMaterial MATTE_RED = new SimpleMaterial(
            new float[]{0.9f, 0.2f, 0.15f}, 0.15f, 0.85f, 0.05f, 8f, ShadingMode.LIT);

    public static final SimpleMaterial GLOSSY_BLUE = new SimpleMaterial(
            new float[]{0.2f, 0.4f, 0.95f}, 0.1f, 0.6f, 0.8f, 64f, ShadingMode.LIT);

    public static final SimpleMaterial FLAT_GREEN = new SimpleMaterial(
            new float[]{0.3f, 0.85f, 0.3f}, 1.0f, 0.0f, 0.0f, 1f, ShadingMode.UNLIT);

    public static final SimpleMaterial GOLD = new SimpleMaterial(
            new float[]{1.0f, 0.8f, 0.3f}, 0.12f, 0.7f, 0.6f, 32f, ShadingMode.LIT);

    public static final SimpleMaterial CHROME = new SimpleMaterial(
            new float[]{0.8f, 0.8f, 0.85f}, 0.08f, 0.4f, 0.95f, 128f, ShadingMode.LIT);

    public static final SimpleMaterial DEBUG_WIRE = new SimpleMaterial(
            new float[]{0.5f, 1.0f, 0.5f}, 1.0f, 0.0f, 0.0f, 1f, ShadingMode.WIREFRAME);

    public static final SimpleMaterial[] PRESETS = {
            MATTE_RED, GLOSSY_BLUE, FLAT_GREEN, GOLD, CHROME, DEBUG_WIRE
    };

    public static final String[] PRESET_NAMES = {
            "Matte Red", "Glossy Blue", "Flat Green", "Gold", "Chrome", "Debug Wire"
    };
}
