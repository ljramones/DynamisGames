package org.dynamisengine.games.camera;

import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * First 3D renderer in the proving ladder.
 *
 * Proves: perspective projection, view matrix (lookAt), model transforms,
 * vertex buffers, depth testing, colored geometry.
 *
 * Renders colored cubes using a simple 3D shader with per-vertex color.
 * Also renders HUD text via STBEasyFont (same technique as text-basics).
 */
public final class Scene3DRenderer {

    // 3D shader: position + color, transformed by MVP
    private static final String VERT_3D = """
            #version 410 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aColor;
            uniform mat4 uMVP;
            out vec3 vColor;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vColor = aColor;
            }
            """;

    private static final String FRAG_3D = """
            #version 410 core
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, 1.0);
            }
            """;

    // 2D text shader (same as text-basics)
    private static final String VERT_TEXT = """
            #version 410 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 uProjection;
            void main() {
                gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String FRAG_TEXT = """
            #version 410 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(uColor, 1.0);
            }
            """;

    // Cube geometry: 36 vertices (6 faces * 2 triangles * 3 verts)
    // Each vertex: x,y,z, r,g,b
    private static final float[] CUBE_VERTS = buildCubeVertices();

    private int prog3D, progText;
    private int uMVP, uTextProj, uTextColor;
    private int cubeVao, cubeVbo;
    private int textVao, textVbo, textEbo;
    private boolean initialized;
    private ByteBuffer textBuffer;
    private IntBuffer textIndexBuffer;

    private static final int MAX_TEXT_QUADS = 2048;

    public void initialize() {
        GL.createCapabilities();

        // 3D program
        prog3D = linkProgram(VERT_3D, FRAG_3D);
        uMVP = glGetUniformLocation(prog3D, "uMVP");

        // Cube VAO
        cubeVao = glGenVertexArrays();
        cubeVbo = glGenBuffers();
        glBindVertexArray(cubeVao);
        glBindBuffer(GL_ARRAY_BUFFER, cubeVbo);
        glBufferData(GL_ARRAY_BUFFER, CUBE_VERTS, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);   // position
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 24, 12);  // color
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);

        // Text program
        progText = linkProgram(VERT_TEXT, FRAG_TEXT);
        uTextProj = glGetUniformLocation(progText, "uProjection");
        uTextColor = glGetUniformLocation(progText, "uColor");

        textVao = glGenVertexArrays();
        textVbo = glGenBuffers();
        textEbo = glGenBuffers();
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(0);

        IntBuffer idx = memAllocInt(MAX_TEXT_QUADS * 6);
        for (int q = 0; q < MAX_TEXT_QUADS; q++) {
            int b = q * 4;
            idx.put(b).put(b+1).put(b+2).put(b).put(b+2).put(b+3);
        }
        idx.flip();
        textIndexBuffer = idx;
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, textEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, textIndexBuffer, GL_STATIC_DRAW);
        glBindVertexArray(0);

        textBuffer = memAlloc(MAX_TEXT_QUADS * 4 * 16);

        glEnable(GL_DEPTH_TEST);
        initialized = true;
    }

    /**
     * Draw a cube at a given position with a scale, using the provided view-projection matrix.
     */
    public void beginScene(int w, int h) {
        glViewport(0, 0, w, h);
        glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog3D);
    }

    public void drawCube(float[] viewProj, float x, float y, float z, float scale) {
        if (!initialized) return;
        // Build model matrix: translate + scale
        float[] model = identity();
        model[12] = x; model[13] = y; model[14] = z;
        model[0] = scale; model[5] = scale; model[10] = scale;

        // MVP = viewProj * model
        float[] mvp = multiply(viewProj, model);
        glBindVertexArray(cubeVao);
        glUniformMatrix4fv(uMVP, false, mvp);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
    }

    public void drawText(String text, float x, float y, float scale,
                         float r, float g, float b, int w, int h) {
        if (!initialized || text == null || text.isEmpty()) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(progText);

        textBuffer.clear();
        int numQuads = STBEasyFont.stb_easy_font_print(0, 0, text, null, textBuffer);
        if (numQuads <= 0 || numQuads > MAX_TEXT_QUADS) return;
        textBuffer.limit(numQuads * 4 * 16);

        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, textBuffer, GL_STREAM_DRAW);
        glUniform3f(uTextColor, r, g, b);

        float left = -x / scale, right = (w - x) / scale;
        float top = -y / scale, bottom = (h - y) / scale;
        glUniformMatrix4fv(uTextProj, false, ortho(left, right, bottom, top, -1, 1));
        glDrawElements(GL_TRIANGLES, numQuads * 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog3D);
    }

    public void shutdown() {
        if (initialized) {
            glDeleteProgram(prog3D);
            glDeleteProgram(progText);
            glDeleteBuffers(cubeVbo);
            glDeleteVertexArrays(cubeVao);
            glDeleteBuffers(textVbo);
            glDeleteBuffers(textEbo);
            glDeleteVertexArrays(textVao);
            if (textBuffer != null) memFree(textBuffer);
            if (textIndexBuffer != null) memFree(textIndexBuffer);
            initialized = false;
        }
    }

    // --- Math utilities ---

    public static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float) Math.tan(Math.toRadians(fovDeg) / 2);
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2 * far * near) / (near - far);
        return m;
    }

    public static float[] lookAt(float eyeX, float eyeY, float eyeZ,
                                  float centerX, float centerY, float centerZ,
                                  float upX, float upY, float upZ) {
        float fx = centerX - eyeX, fy = centerY - eyeY, fz = centerZ - eyeZ;
        float fLen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= fLen; fy /= fLen; fz /= fLen;

        float sx = fy * upZ - fz * upY, sy = fz * upX - fx * upZ, sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx*sx + sy*sy + sz*sz);
        sx /= sLen; sy /= sLen; sz /= sLen;

        float ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;

        float[] m = new float[16];
        m[0] = sx;  m[1] = ux;  m[2] = -fx;  m[3] = 0;
        m[4] = sy;  m[5] = uy;  m[6] = -fy;  m[7] = 0;
        m[8] = sz;  m[9] = uz;  m[10] = -fz; m[11] = 0;
        m[12] = -(sx*eyeX + sy*eyeY + sz*eyeZ);
        m[13] = -(ux*eyeX + uy*eyeY + uz*eyeZ);
        m[14] = -(-fx*eyeX + -fy*eyeY + -fz*eyeZ);
        m[15] = 1;
        return m;
    }

    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                r[i + j*4] = a[i]*b[j*4] + a[i+4]*b[j*4+1] + a[i+8]*b[j*4+2] + a[i+12]*b[j*4+3];
        return r;
    }

    private static float[] identity() {
        float[] m = new float[16];
        m[0] = 1; m[5] = 1; m[10] = 1; m[15] = 1;
        return m;
    }

    private static float[] ortho(float l, float r, float b, float t, float n, float f) {
        float[] m = new float[16];
        m[0] = 2f/(r-l); m[5] = 2f/(t-b); m[10] = -2f/(f-n);
        m[12] = -(r+l)/(r-l); m[13] = -(t+b)/(t-b); m[14] = -(f+n)/(f-n); m[15] = 1;
        return m;
    }

    private static int linkProgram(String vertSrc, String fragSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragSrc);
        int prog = glCreateProgram();
        glAttachShader(prog, vs); glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Link: " + glGetProgramInfoLog(prog));
        glDeleteShader(vs); glDeleteShader(fs);
        return prog;
    }

    private static int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src); glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Compile: " + glGetShaderInfoLog(s));
        return s;
    }

    private static float[] buildCubeVertices() {
        // Unit cube centered at origin, 6 faces with distinct colors
        float[][] faces = {
            // Front (z=+0.5) — cyan
            {-0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f},
            // Back (z=-0.5) — magenta
            {0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f},
            // Right (x=+0.5) — red
            {0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f},
            // Left (x=-0.5) — green
            {-0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f},
            // Top (y=+0.5) — yellow
            {-0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f},
            // Bottom (y=-0.5) — blue
            {-0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f},
        };
        float[][] colors = {
            {0.2f,0.8f,1f}, {0.9f,0.2f,0.9f}, {0.9f,0.2f,0.2f},
            {0.2f,0.9f,0.2f}, {0.9f,0.9f,0.2f}, {0.2f,0.2f,0.9f}
        };
        float[] verts = new float[36 * 6]; // 36 vertices * (3 pos + 3 color)
        int vi = 0;
        for (int f = 0; f < 6; f++) {
            for (int v = 0; v < 6; v++) {
                verts[vi++] = faces[f][v*3];
                verts[vi++] = faces[f][v*3+1];
                verts[vi++] = faces[f][v*3+2];
                verts[vi++] = colors[f][0];
                verts[vi++] = colors[f][1];
                verts[vi++] = colors[f][2];
            }
        }
        return verts;
    }
}
