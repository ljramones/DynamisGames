package org.dynamisengine.games.debugshowcase;

import org.dynamisengine.debug.api.draw.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Minimal self-contained debug draw renderer for the proving module.
 *
 * <p>Mirrors the production {@code OpenGlDebugDrawRenderer} contract:
 * lines and wireframe boxes, two-pass depth mode, per-frame rebuild.
 */
final class DebugDrawRenderer {

    private static final int FLOATS_PER_VERTEX = 6; // x,y,z,r,g,b
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int BOX_VERTICES = 24; // 12 edges × 2

    private int programId;
    private int vpLocation;
    private int vaoId;
    private int vboId;
    private int vboCapacityBytes;

    void initialize() {
        GL.createCapabilities();
        programId = createShaderProgram();
        vpLocation = glGetUniformLocation(programId, "u_viewProj");

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        vboCapacityBytes = 4096 * BYTES_PER_VERTEX;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vboCapacityBytes, GL_DYNAMIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_PER_VERTEX, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, BYTES_PER_VERTEX, 3L * Float.BYTES);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void shutdown() {
        if (vboId != 0) { glDeleteBuffers(vboId); vboId = 0; }
        if (vaoId != 0) { glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (programId != 0) { glDeleteProgram(programId); programId = 0; }
    }

    void render(List<DebugDrawCommand> commands, float[] viewProj) {
        if (commands.isEmpty()) return;
        renderBatch(commands, viewProj, DepthMode.TESTED, true);
        renderBatch(commands, viewProj, DepthMode.ALWAYS_VISIBLE, false);
    }

    private void renderBatch(List<DebugDrawCommand> commands, float[] viewProj,
                              DepthMode mode, boolean depthTest) {
        int vertexCount = 0;
        for (var cmd : commands) {
            if (depthMode(cmd) != mode) continue;
            vertexCount += verticesFor(cmd);
        }
        if (vertexCount == 0) return;

        ByteBuffer buf = ByteBuffer.allocateDirect(vertexCount * BYTES_PER_VERTEX)
                .order(ByteOrder.nativeOrder());
        for (var cmd : commands) {
            if (depthMode(cmd) != mode) continue;
            writeCommand(cmd, buf);
        }
        buf.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int needed = vertexCount * BYTES_PER_VERTEX;
        if (needed > vboCapacityBytes) {
            vboCapacityBytes = needed * 2;
            glBufferData(GL_ARRAY_BUFFER, vboCapacityBytes, GL_DYNAMIC_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glUseProgram(programId);
        glUniformMatrix4fv(vpLocation, false, viewProj);

        if (depthTest) glEnable(GL_DEPTH_TEST);
        else glDisable(GL_DEPTH_TEST);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
        glUseProgram(0);
    }

    private int verticesFor(DebugDrawCommand cmd) {
        return switch (cmd) {
            case DebugLineCommand _ -> 2;
            case DebugBoxCommand _ -> BOX_VERTICES;
            case DebugSphereCommand _ -> 0;
            case DebugTextCommand _ -> 0;
        };
    }

    private void writeCommand(DebugDrawCommand cmd, ByteBuffer buf) {
        switch (cmd) {
            case DebugLineCommand l -> {
                putVertex(buf, l.x1(), l.y1(), l.z1(), l.r(), l.g(), l.b());
                putVertex(buf, l.x2(), l.y2(), l.z2(), l.r(), l.g(), l.b());
            }
            case DebugBoxCommand b -> writeBox(b, buf);
            case DebugSphereCommand _ -> {}
            case DebugTextCommand _ -> {}
        }
    }

    private void writeBox(DebugBoxCommand b, ByteBuffer buf) {
        float x0 = b.cx() - b.halfX(), y0 = b.cy() - b.halfY(), z0 = b.cz() - b.halfZ();
        float x1 = b.cx() + b.halfX(), y1 = b.cy() + b.halfY(), z1 = b.cz() + b.halfZ();
        float r = b.r(), g = b.g(), bl = b.b();
        // Bottom
        edge(buf, x0,y0,z0, x1,y0,z0, r,g,bl); edge(buf, x1,y0,z0, x1,y0,z1, r,g,bl);
        edge(buf, x1,y0,z1, x0,y0,z1, r,g,bl); edge(buf, x0,y0,z1, x0,y0,z0, r,g,bl);
        // Top
        edge(buf, x0,y1,z0, x1,y1,z0, r,g,bl); edge(buf, x1,y1,z0, x1,y1,z1, r,g,bl);
        edge(buf, x1,y1,z1, x0,y1,z1, r,g,bl); edge(buf, x0,y1,z1, x0,y1,z0, r,g,bl);
        // Verticals
        edge(buf, x0,y0,z0, x0,y1,z0, r,g,bl); edge(buf, x1,y0,z0, x1,y1,z0, r,g,bl);
        edge(buf, x1,y0,z1, x1,y1,z1, r,g,bl); edge(buf, x0,y0,z1, x0,y1,z1, r,g,bl);
    }

    private void edge(ByteBuffer buf, float x0, float y0, float z0,
                       float x1, float y1, float z1, float r, float g, float b) {
        putVertex(buf, x0, y0, z0, r, g, b);
        putVertex(buf, x1, y1, z1, r, g, b);
    }

    private void putVertex(ByteBuffer buf, float x, float y, float z,
                            float r, float g, float b) {
        buf.putFloat(x).putFloat(y).putFloat(z).putFloat(r).putFloat(g).putFloat(b);
    }

    private DepthMode depthMode(DebugDrawCommand cmd) {
        return switch (cmd) {
            case DebugLineCommand l -> l.depthMode();
            case DebugBoxCommand b -> b.depthMode();
            case DebugSphereCommand s -> s.depthMode();
            case DebugTextCommand t -> t.depthMode();
        };
    }

    private static int createShaderProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, VERT);
        int fs = compileShader(GL_FRAGMENT_SHADER, FRAG);
        int prog = glCreateProgram();
        glAttachShader(prog, vs); glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Debug shader link: " + glGetProgramInfoLog(prog));
        glDeleteShader(vs); glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src); glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Debug shader: " + glGetShaderInfoLog(id));
        return id;
    }

    private static final String VERT = """
            #version 330 core
            layout(location=0) in vec3 a_pos;
            layout(location=1) in vec3 a_col;
            uniform mat4 u_viewProj;
            out vec3 v_col;
            void main() { v_col = a_col; gl_Position = u_viewProj * vec4(a_pos, 1.0); }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec3 v_col;
            out vec4 fragColor;
            void main() { fragColor = vec4(v_col, 1.0); }
            """;
}
