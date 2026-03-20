package org.dynamisengine.games.text;

import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Minimal text renderer using STBEasyFont + OpenGL 4.1 core profile shaders.
 *
 * STBEasyFont generates quads (4 verts per quad) with 16 bytes per vertex:
 * [x:f32, y:f32, z:f32, color:u8x4]. We only use x,y.
 *
 * Since GL_QUADS is not available in core profile, we convert quads to
 * triangles via an index buffer (2 triangles per quad: 0,1,2 + 0,2,3).
 */
public final class TextRenderer {

    private static final String VERTEX_SHADER = """
            #version 410 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 uProjection;
            void main() {
                gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 410 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(uColor, 1.0);
            }
            """;

    private static final int MAX_QUADS = 4096;
    private static final int BYTES_PER_VERTEX = 16;

    private int shaderProgram;
    private int vao;
    private int vbo;
    private int ebo;
    private int uProjection;
    private int uColor;
    private boolean initialized;

    private ByteBuffer vertexBuffer;
    private IntBuffer indexBuffer;

    public void initialize() {
        GL.createCapabilities();

        // Compile shaders
        int vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link failed: " + glGetProgramInfoLog(shaderProgram));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);

        uProjection = glGetUniformLocation(shaderProgram, "uProjection");
        uColor = glGetUniformLocation(shaderProgram, "uColor");

        // Create VAO + VBO + EBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // x,y at offset 0, stride 16 bytes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, BYTES_PER_VERTEX, 0);
        glEnableVertexAttribArray(0);

        // Pre-build index buffer for quad→triangle conversion
        // For each quad (verts 0,1,2,3): two triangles (0,1,2) and (0,2,3)
        indexBuffer = memAllocInt(MAX_QUADS * 6);
        for (int q = 0; q < MAX_QUADS; q++) {
            int base = q * 4;
            indexBuffer.put(base).put(base + 1).put(base + 2);
            indexBuffer.put(base).put(base + 2).put(base + 3);
        }
        indexBuffer.flip();

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        glBindVertexArray(0);

        // Vertex data buffer
        vertexBuffer = memAlloc(MAX_QUADS * 4 * BYTES_PER_VERTEX);

        initialized = true;
    }

    public void beginFrame(int width, int height) {
        if (!initialized) return;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(shaderProgram);
    }

    /**
     * Draw text at pixel coordinates (0,0 = top-left).
     */
    public void drawText(String text, float x, float y, float scale,
                         float r, float g, float b, int screenW, int screenH) {
        if (!initialized || text == null || text.isEmpty()) return;

        vertexBuffer.clear();
        int numQuads = STBEasyFont.stb_easy_font_print(0, 0, text, null, vertexBuffer);
        if (numQuads <= 0) return;
        if (numQuads > MAX_QUADS) numQuads = MAX_QUADS;
        int numVertices = numQuads * 4;

        // Upload vertex data
        vertexBuffer.limit(numVertices * BYTES_PER_VERTEX);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STREAM_DRAW);

        glUniform3f(uColor, r, g, b);

        // Orthographic projection that maps STBEasyFont's (0,0) to screen pixel (x,y)
        // with each STB unit = `scale` pixels.
        // STB coord s maps to screen pixel: x + s*scale
        // Screen pixel p maps to NDC: (2*p/W - 1, 1 - 2*p/H)
        // Combined: NDC_x = 2*(x + s*scale)/W - 1 = (2*scale/W)*s + (2*x/W - 1)
        // This is an ortho projection with left=-x/scale, right=(W-x)/scale, etc.
        float left = -x / scale;
        float right = (screenW - x) / scale;
        float top = -y / scale;
        float bottom = (screenH - y) / scale;
        float[] proj = orthoMatrix(left, right, bottom, top, -1, 1);
        glUniformMatrix4fv(uProjection, false, proj);

        // Draw as triangles via index buffer
        int indexCount = numQuads * 6;
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
    }

    public void endFrame() {
        if (!initialized) return;
        glDisable(GL_BLEND);
        glUseProgram(0);
    }

    public void shutdown() {
        if (initialized) {
            glDeleteProgram(shaderProgram);
            glDeleteBuffers(vbo);
            glDeleteBuffers(ebo);
            glDeleteVertexArrays(vao);
            if (vertexBuffer != null) memFree(vertexBuffer);
            if (indexBuffer != null) memFree(indexBuffer);
            initialized = false;
        }
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static float[] orthoMatrix(float left, float right, float bottom, float top,
                                        float near, float far) {
        float[] m = new float[16];
        m[0]  = 2f / (right - left);
        m[5]  = 2f / (top - bottom);
        m[10] = -2f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        m[15] = 1f;
        return m;
    }
}
