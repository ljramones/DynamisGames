package org.dynamisengine.games.material;

import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Material-aware mesh renderer.
 *
 * Extends the mesh-basics renderer with per-draw material parameters:
 * base color, ambient/diffuse/specular strengths, shininess, shading mode.
 *
 * Uses Blinn-Phong shading as the simplest model that shows clear
 * material differentiation (matte vs glossy vs unlit).
 */
public final class MaterialRenderer {

    private static final String VERT = """
            #version 410 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            out vec3 vNormal;
            out vec3 vWorldPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vNormal = normalize(mat3(uModel) * aNormal);
                vWorldPos = (uModel * vec4(aPos, 1.0)).xyz;
            }
            """;

    private static final String FRAG = """
            #version 410 core
            in vec3 vNormal;
            in vec3 vWorldPos;

            uniform vec3 uBaseColor;
            uniform float uAmbient;
            uniform float uDiffuse;
            uniform float uSpecular;
            uniform float uShininess;
            uniform int uShadingMode; // 0=lit, 1=unlit
            uniform vec3 uCameraPos;

            out vec4 fragColor;

            void main() {
                if (uShadingMode == 1) {
                    // Unlit: flat base color
                    fragColor = vec4(uBaseColor, 1.0);
                    return;
                }

                vec3 n = normalize(vNormal);
                vec3 lightDir = normalize(vec3(0.5, 0.8, 0.3));
                vec3 viewDir = normalize(uCameraPos - vWorldPos);
                vec3 halfDir = normalize(lightDir + viewDir);

                // Ambient
                vec3 ambient = uAmbient * uBaseColor;

                // Diffuse (Lambert)
                float diff = max(dot(n, lightDir), 0.0);
                vec3 diffuse = uDiffuse * diff * uBaseColor;

                // Specular (Blinn-Phong)
                float spec = pow(max(dot(n, halfDir), 0.0), uShininess);
                vec3 specular = uSpecular * spec * vec3(1.0);

                // Hemispheric fill (soft bottom light)
                float hemi = 0.5 + 0.5 * n.y;
                vec3 fill = 0.08 * hemi * uBaseColor;

                fragColor = vec4(ambient + diffuse + specular + fill, 1.0);
            }
            """;

    private static final String VERT_TEXT = """
            #version 410 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 uProjection;
            void main() { gl_Position = uProjection * vec4(aPos, 0.0, 1.0); }
            """;
    private static final String FRAG_TEXT = """
            #version 410 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() { fragColor = vec4(uColor, 1.0); }
            """;

    private static final int MAX_TEXT_QUADS = 2048;

    private int prog, progText;
    private int uMVP, uModel, uBaseColor, uAmbient, uDiffuse, uSpecular, uShininess, uShadingMode, uCameraPos;
    private int uTextProj, uTextColor;
    private int textVao, textVbo, textEbo;
    private ByteBuffer textBuffer;
    private IntBuffer textIndexBuffer;
    private boolean initialized;

    public void initialize() {
        GL.createCapabilities();
        prog = linkProgram(VERT, FRAG);
        uMVP = glGetUniformLocation(prog, "uMVP");
        uModel = glGetUniformLocation(prog, "uModel");
        uBaseColor = glGetUniformLocation(prog, "uBaseColor");
        uAmbient = glGetUniformLocation(prog, "uAmbient");
        uDiffuse = glGetUniformLocation(prog, "uDiffuse");
        uSpecular = glGetUniformLocation(prog, "uSpecular");
        uShininess = glGetUniformLocation(prog, "uShininess");
        uShadingMode = glGetUniformLocation(prog, "uShadingMode");
        uCameraPos = glGetUniformLocation(prog, "uCameraPos");

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

    public MeshHandle upload(SimpleMesh mesh) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, SimpleMesh.BYTES_PER_VERTEX, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, SimpleMesh.BYTES_PER_VERTEX, 12);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);
        glBindVertexArray(0);
        return new MeshHandle(vao, vbo, ebo, mesh.triangleCount() * 3);
    }

    public void beginScene(int w, int h) {
        glViewport(0, 0, w, h);
        glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glUseProgram(prog);
    }

    public void drawMesh(MeshHandle handle, float[] mvp, float[] model,
                         SimpleMaterial mat, float camX, float camY, float camZ) {
        if (!initialized) return;
        glUseProgram(prog);

        if (mat.shadingMode() == SimpleMaterial.ShadingMode.WIREFRAME) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        }

        glBindVertexArray(handle.vao());
        glUniformMatrix4fv(uMVP, false, mvp);
        glUniformMatrix4fv(uModel, false, model);
        glUniform3f(uBaseColor, mat.baseColor()[0], mat.baseColor()[1], mat.baseColor()[2]);
        glUniform1f(uAmbient, mat.ambient());
        glUniform1f(uDiffuse, mat.diffuse());
        glUniform1f(uSpecular, mat.specular());
        glUniform1f(uShininess, mat.shininess());
        glUniform1i(uShadingMode, mat.shadingMode() == SimpleMaterial.ShadingMode.UNLIT ? 1 : 0);
        glUniform3f(uCameraPos, camX, camY, camZ);
        glDrawElements(GL_TRIANGLES, handle.indexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        if (mat.shadingMode() == SimpleMaterial.ShadingMode.WIREFRAME) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }
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
        float left = -x/scale, right = (w-x)/scale, top = -y/scale, bottom = (h-y)/scale;
        glUniformMatrix4fv(uTextProj, false, ortho(left, right, bottom, top, -1, 1));
        glDrawElements(GL_TRIANGLES, numQuads * 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public void deleteMesh(MeshHandle h) {
        glDeleteBuffers(h.vbo()); glDeleteBuffers(h.ebo()); glDeleteVertexArrays(h.vao());
    }

    public void shutdown() {
        if (initialized) {
            glDeleteProgram(prog); glDeleteProgram(progText);
            glDeleteBuffers(textVbo); glDeleteBuffers(textEbo); glDeleteVertexArrays(textVao);
            if (textBuffer != null) memFree(textBuffer);
            if (textIndexBuffer != null) memFree(textIndexBuffer);
            initialized = false;
        }
    }

    // --- Math (same as mesh-basics) ---
    public static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f/(float)Math.tan(Math.toRadians(fovDeg)/2);
        float[] m = new float[16]; m[0]=f/aspect; m[5]=f;
        m[10]=(far+near)/(near-far); m[11]=-1f; m[14]=(2*far*near)/(near-far); return m;
    }
    public static float[] lookAt(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz) {
        float fx=cx-ex,fy=cy-ey,fz=cz-ez;float fl=(float)Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=fl;fy/=fl;fz/=fl;
        float sx=fy*uz-fz*uy,sy=fz*ux-fx*uz,sz=fx*uy-fy*ux;float sl=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);sx/=sl;sy/=sl;sz/=sl;
        float upx=sy*fz-sz*fy,upy=sz*fx-sx*fz,upz=sx*fy-sy*fx;
        float[] m=new float[16];m[0]=sx;m[1]=upx;m[2]=-fx;m[4]=sy;m[5]=upy;m[6]=-fy;m[8]=sz;m[9]=upz;m[10]=-fz;
        m[12]=-(sx*ex+sy*ey+sz*ez);m[13]=-(upx*ex+upy*ey+upz*ez);m[14]=(fx*ex+fy*ey+fz*ez);m[15]=1;return m;
    }
    public static float[] multiply(float[] a, float[] b) {
        float[] r=new float[16];for(int i=0;i<4;i++)for(int j=0;j<4;j++)
            r[i+j*4]=a[i]*b[j*4]+a[i+4]*b[j*4+1]+a[i+8]*b[j*4+2]+a[i+12]*b[j*4+3];return r;
    }
    public static float[] identity(){float[] m=new float[16];m[0]=1;m[5]=1;m[10]=1;m[15]=1;return m;}
    public static float[] rotateY(float[] m,float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);
        float[] r=identity();r[0]=c;r[8]=s;r[2]=-s;r[10]=c;return multiply(m,r);}
    public static float[] translate(float[] m,float x,float y,float z){
        float[] t=identity();t[12]=x;t[13]=y;t[14]=z;return multiply(m,t);}
    private static float[] ortho(float l,float r,float b,float t,float n,float f){
        float[] m=new float[16];m[0]=2f/(r-l);m[5]=2f/(t-b);m[10]=-2f/(f-n);
        m[12]=-(r+l)/(r-l);m[13]=-(t+b)/(t-b);m[14]=-(f+n)/(f-n);m[15]=1;return m;}
    private static int linkProgram(String v,String f){int vs=compile(GL_VERTEX_SHADER,v);int fs=compile(GL_FRAGMENT_SHADER,f);
        int p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new RuntimeException("Link: "+glGetProgramInfoLog(p));
        glDeleteShader(vs);glDeleteShader(fs);return p;}
    private static int compile(int t,String s){int sh=glCreateShader(t);glShaderSource(sh,s);glCompileShader(sh);
        if(glGetShaderi(sh,GL_COMPILE_STATUS)==GL_FALSE)throw new RuntimeException("Compile: "+glGetShaderInfoLog(sh));return sh;}
}
