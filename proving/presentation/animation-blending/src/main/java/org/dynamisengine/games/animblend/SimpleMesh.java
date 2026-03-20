package org.dynamisengine.games.animblend;

/**
 * Indexed triangle mesh — the canonical mesh data structure.
 *
 * Positions: interleaved [x,y,z, nx,ny,nz] per vertex (6 floats).
 * Indices: triangle list (3 ints per triangle).
 *
 * Normals are included because they cost nothing to generate and
 * will be needed immediately by material-basics/lighting-basics.
 */
public record SimpleMesh(float[] vertices, int[] indices, int vertexCount, int triangleCount) {

    /** Floats per vertex: position(3) + normal(3). */
    public static final int FLOATS_PER_VERTEX = 6;
    /** Bytes per vertex. */
    public static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;

    /**
     * Generate a torus — unmistakably 3D from any angle.
     *
     * @param majorRadius distance from center to tube center
     * @param minorRadius tube radius
     * @param majorSegments segments around the ring
     * @param minorSegments segments around the tube
     */
    public static SimpleMesh torus(float majorRadius, float minorRadius,
                                    int majorSegments, int minorSegments) {
        int vertCount = (majorSegments + 1) * (minorSegments + 1);
        int triCount = majorSegments * minorSegments * 2;
        float[] verts = new float[vertCount * FLOATS_PER_VERTEX];
        int[] idx = new int[triCount * 3];

        int vi = 0;
        for (int i = 0; i <= majorSegments; i++) {
            float u = (float) i / majorSegments * 2f * (float) Math.PI;
            float cu = (float) Math.cos(u), su = (float) Math.sin(u);

            for (int j = 0; j <= minorSegments; j++) {
                float v = (float) j / minorSegments * 2f * (float) Math.PI;
                float cv = (float) Math.cos(v), sv = (float) Math.sin(v);

                float x = (majorRadius + minorRadius * cv) * cu;
                float y = minorRadius * sv;
                float z = (majorRadius + minorRadius * cv) * su;

                // Normal: direction from tube center to surface point
                float nx = cv * cu;
                float ny = sv;
                float nz = cv * su;

                verts[vi++] = x;  verts[vi++] = y;  verts[vi++] = z;
                verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz;
            }
        }

        int ii = 0;
        int stride = minorSegments + 1;
        for (int i = 0; i < majorSegments; i++) {
            for (int j = 0; j < minorSegments; j++) {
                int a = i * stride + j;
                int b = a + stride;
                idx[ii++] = a;     idx[ii++] = b;     idx[ii++] = a + 1;
                idx[ii++] = a + 1; idx[ii++] = b;     idx[ii++] = b + 1;
            }
        }

        return new SimpleMesh(verts, idx, vertCount, triCount);
    }

    /**
     * Generate a UV sphere.
     */
    public static SimpleMesh sphere(float radius, int rings, int sectors) {
        int vertCount = (rings + 1) * (sectors + 1);
        int triCount = rings * sectors * 2;
        float[] verts = new float[vertCount * FLOATS_PER_VERTEX];
        int[] idx = new int[triCount * 3];

        int vi = 0;
        for (int r = 0; r <= rings; r++) {
            float phi = (float) Math.PI * r / rings;
            float sp = (float) Math.sin(phi), cp = (float) Math.cos(phi);

            for (int s = 0; s <= sectors; s++) {
                float theta = 2f * (float) Math.PI * s / sectors;
                float st = (float) Math.sin(theta), ct = (float) Math.cos(theta);

                float nx = sp * ct, ny = cp, nz = sp * st;
                verts[vi++] = radius * nx;
                verts[vi++] = radius * ny;
                verts[vi++] = radius * nz;
                verts[vi++] = nx;
                verts[vi++] = ny;
                verts[vi++] = nz;
            }
        }

        int ii = 0;
        int stride = sectors + 1;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                int a = r * stride + s;
                int b = a + stride;
                idx[ii++] = a;     idx[ii++] = b;     idx[ii++] = a + 1;
                idx[ii++] = a + 1; idx[ii++] = b;     idx[ii++] = b + 1;
            }
        }

        return new SimpleMesh(verts, idx, vertCount, triCount);
    }
}
