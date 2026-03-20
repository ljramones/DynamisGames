package org.dynamisengine.games.mesh;

import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Renders indexed triangle meshes with normal-based coloring.
 *
 * Shader uses normals to derive color (hemispheric lighting trick):
 * brighter where normal faces up/camera, darker underneath.
 * This gives a clear sense of 3D shape without a full lighting system.
 *
 * Also includes HUD text rendering (same STBEasyFont approach).
 */
public final class MeshRenderer {

    private static final String VERT_MESH = """
            #version 410 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec3 vWorldPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vNormal = mat3(uModel) * aNormal;
                vWorldPos = (uModel * vec4(aPos, 1.0)).xyz;
            }
            """;

    private static final String FRAG_MESH = """
            #version 410 core
            in vec3 vNormal;
            in vec3 vWorldPos;
            uniform vec3 uBaseColor;
            out vec4 fragColor;
            void main() {
                vec3 n = normalize(vNormal);
                // Simple hemispheric shading: up = bright, down = dark
                float hemi = 0.5 + 0.5 * n.y;
                // Fake directional from upper-right
                vec3 lightDir = normalize(vec3(0.5, 0.8, 0.3));
                float diffuse = max(dot(n, lightDir), 0.0);
                float light = 0.15 + 0.45 * hemi + 0.4 * diffuse;
                fragColor = vec4(uBaseColor * light, 1.0);
            }
            """;

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

    private static final int MAX_TEXT_QUADS = 2048;

    private int progMesh, progText;
    private int uMVP, uModel, uBaseColor;
    private int uTextProj, uTextColor;
    private int textVao, textVbo, textEbo;
    private ByteBuffer textBuffer;
    private IntBuffer textIndexBuffer;
    private boolean initialized;

    public void initialize() {
        GL.createCapabilities();

        progMesh = linkProgram(VERT_MESH, FRAG_MESH);
        uMVP = glGetUniformLocation(progMesh, "uMVP");
        uModel = glGetUniformLocation(progMesh, "uModel");
        uBaseColor = glGetUniformLocation(progMesh, "uBaseColor");

        progText = linkProgram(VERT_TEXT, FRAG_TEXT);
        uTextProj = glGetUniformLocation(progText, "uProjection");
        uTextColor = glGetUniformLocation(progText, "uColor");

        // Text VAO
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
     * Upload a SimpleMesh to the GPU. Returns a handle for drawing.
     */
    public MeshHandle upload(SimpleMesh mesh) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);

        // position at location 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, SimpleMesh.BYTES_PER_VERTEX, 0);
        glEnableVertexAttribArray(0);
        // normal at location 1
        glVertexAttribPointer(1, 3, GL_FLOAT, false, SimpleMesh.BYTES_PER_VERTEX, 12);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);

        glBindVertexArray(0);

        return new MeshHandle(vao, vbo, ebo, mesh.triangleCount() * 3);
    }

    public void beginScene(int w, int h) {
        glViewport(0, 0, w, h);
        glClearColor(0.06f, 0.06f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(progMesh);
    }

    /**
     * Draw an uploaded mesh with the given MVP, model matrix, and base color.
     */
    public void drawMesh(MeshHandle handle, float[] mvp, float[] model,
                         float r, float g, float b) {
        if (!initialized) return;
        glUseProgram(progMesh);
        glBindVertexArray(handle.vao());
        glUniformMatrix4fv(uMVP, false, mvp);
        glUniformMatrix4fv(uModel, false, model);
        glUniform3f(uBaseColor, r, g, b);
        glDrawElements(GL_TRIANGLES, handle.indexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    /**
     * Draw mesh in wireframe mode.
     */
    public void drawMeshWireframe(MeshHandle handle, float[] mvp, float[] model,
                                   float r, float g, float b) {
        if (!initialized) return;
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        drawMesh(handle, mvp, model, r, g, b);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }

    public void drawText(String text, float x, float y, float scale,
                         float r, float g, float b, int w, int h) {
        if (!initialized || text == null || text.isEmpty()) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(progText);
        glDisableVertexAttribArray(1);

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
    }

    public void deleteMesh(MeshHandle handle) {
        glDeleteBuffers(handle.vbo());
        glDeleteBuffers(handle.ebo());
        glDeleteVertexArrays(handle.vao());
    }

    public void shutdown() {
        if (initialized) {
            glDeleteProgram(progMesh);
            glDeleteProgram(progText);
            glDeleteBuffers(textVbo);
            glDeleteBuffers(textEbo);
            glDeleteVertexArrays(textVao);
            if (textBuffer != null) memFree(textBuffer);
            if (textIndexBuffer != null) memFree(textIndexBuffer);
            initialized = false;
        }
    }

    // --- Math ---
    public static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float) Math.tan(Math.toRadians(fovDeg) / 2);
        float[] m = new float[16];
        m[0] = f / aspect; m[5] = f;
        m[10] = (far + near) / (near - far); m[11] = -1f;
        m[14] = (2 * far * near) / (near - far);
        return m;
    }

    public static float[] lookAt(float ex, float ey, float ez,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz) {
        float fx = cx-ex, fy = cy-ey, fz = cz-ez;
        float fl = (float)Math.sqrt(fx*fx+fy*fy+fz*fz);
        fx/=fl; fy/=fl; fz/=fl;
        float sx = fy*uz-fz*uy, sy = fz*ux-fx*uz, sz = fx*uy-fy*ux;
        float sl = (float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        sx/=sl; sy/=sl; sz/=sl;
        float upx = sy*fz-sz*fy, upy = sz*fx-sx*fz, upz = sx*fy-sy*fx;
        float[] m = new float[16];
        m[0]=sx; m[1]=upx; m[2]=-fx; m[4]=sy; m[5]=upy; m[6]=-fy;
        m[8]=sz; m[9]=upz; m[10]=-fz;
        m[12]=-(sx*ex+sy*ey+sz*ez);
        m[13]=-(upx*ex+upy*ey+upz*ez);
        m[14]=(fx*ex+fy*ey+fz*ez);
        m[15]=1;
        return m;
    }

    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                r[i+j*4] = a[i]*b[j*4] + a[i+4]*b[j*4+1] + a[i+8]*b[j*4+2] + a[i+12]*b[j*4+3];
        return r;
    }

    public static float[] identity() {
        float[] m = new float[16]; m[0]=1; m[5]=1; m[10]=1; m[15]=1; return m;
    }

    public static float[] rotateY(float[] m, float angle) {
        float c = (float)Math.cos(angle), s = (float)Math.sin(angle);
        float[] rot = identity();
        rot[0] = c; rot[8] = s; rot[2] = -s; rot[10] = c;
        return multiply(m, rot);
    }

    public static float[] translate(float[] m, float x, float y, float z) {
        float[] t = identity();
        t[12] = x; t[13] = y; t[14] = z;
        return multiply(m, t);
    }

    private static float[] ortho(float l, float r, float b, float t, float n, float f) {
        float[] m = new float[16];
        m[0]=2f/(r-l); m[5]=2f/(t-b); m[10]=-2f/(f-n);
        m[12]=-(r+l)/(r-l); m[13]=-(t+b)/(t-b); m[14]=-(f+n)/(f-n); m[15]=1;
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
}
