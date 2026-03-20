package org.dynamisengine.games.mgi;

import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Lighting-aware mesh renderer.
 *
 * Extends material-basics with explicit directional + point light uniforms.
 * Blinn-Phong shading with proper per-light contribution.
 */
public final class SceneRenderer {

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

            // Material
            uniform vec3 uBaseColor;
            uniform float uAmbient;
            uniform float uDiffuse;
            uniform float uSpecular;
            uniform float uShininess;
            uniform int uShadingMode;
            uniform vec3 uCameraPos;

            // Scene ambient
            uniform vec3 uAmbientColor;

            // Directional light
            uniform int uDirEnabled;
            uniform vec3 uDirDirection;
            uniform vec3 uDirColor;
            uniform float uDirIntensity;

            // Point light
            uniform int uPointEnabled;
            uniform vec3 uPointPos;
            uniform vec3 uPointColor;
            uniform float uPointIntensity;
            uniform float uPointRadius;

            out vec4 fragColor;

            vec3 calcLight(vec3 lightDir, vec3 lightColor, float lightIntensity,
                           vec3 n, vec3 viewDir, vec3 baseColor,
                           float kd, float ks, float shininess) {
                float diff = max(dot(n, lightDir), 0.0);
                vec3 halfDir = normalize(lightDir + viewDir);
                float spec = pow(max(dot(n, halfDir), 0.0), shininess);
                return lightColor * lightIntensity * (kd * diff * baseColor + ks * spec * vec3(1.0));
            }

            void main() {
                if (uShadingMode == 1) {
                    fragColor = vec4(uBaseColor, 1.0);
                    return;
                }

                vec3 n = normalize(vNormal);
                vec3 viewDir = normalize(uCameraPos - vWorldPos);

                // Ambient
                vec3 color = uAmbient * uAmbientColor * uBaseColor;

                // Directional light
                if (uDirEnabled != 0) {
                    vec3 dirL = normalize(uDirDirection);
                    color += calcLight(dirL, uDirColor, uDirIntensity,
                                       n, viewDir, uBaseColor, uDiffuse, uSpecular, uShininess);
                }

                // Point light
                if (uPointEnabled != 0) {
                    vec3 toLight = uPointPos - vWorldPos;
                    float dist = length(toLight);
                    if (dist < uPointRadius) {
                        vec3 ptDir = toLight / dist;
                        float atten = 1.0 - smoothstep(0.0, uPointRadius, dist);
                        color += atten * calcLight(ptDir, uPointColor, uPointIntensity,
                                                    n, viewDir, uBaseColor, uDiffuse, uSpecular, uShininess);
                    }
                }

                fragColor = vec4(color, 1.0);
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
    // Material uniforms
    private int uMVP, uModel, uBaseColor, uAmbientU, uDiffuseU, uSpecularU, uShininessU, uShadingMode, uCameraPos;
    // Light uniforms
    private int uAmbientColor;
    private int uDirEnabled, uDirDirection, uDirColor, uDirIntensity;
    private int uPointEnabled, uPointPos, uPointColor, uPointIntensity, uPointRadius;
    // Text
    private int uTextProj, uTextColor;
    private int textVao, textVbo, textEbo;
    private ByteBuffer textBuffer;
    private IntBuffer textIndexBuffer;
    private boolean initialized;

    public void initialize() {
        GL.createCapabilities();
        prog = linkProgram(VERT, FRAG);
        uMVP = loc("uMVP"); uModel = loc("uModel");
        uBaseColor = loc("uBaseColor"); uAmbientU = loc("uAmbient");
        uDiffuseU = loc("uDiffuse"); uSpecularU = loc("uSpecular");
        uShininessU = loc("uShininess"); uShadingMode = loc("uShadingMode");
        uCameraPos = loc("uCameraPos");
        uAmbientColor = loc("uAmbientColor");
        uDirEnabled = loc("uDirEnabled"); uDirDirection = loc("uDirDirection");
        uDirColor = loc("uDirColor"); uDirIntensity = loc("uDirIntensity");
        uPointEnabled = loc("uPointEnabled"); uPointPos = loc("uPointPos");
        uPointColor = loc("uPointColor"); uPointIntensity = loc("uPointIntensity");
        uPointRadius = loc("uPointRadius");

        progText = linkProgram(VERT_TEXT, FRAG_TEXT);
        uTextProj = glGetUniformLocation(progText, "uProjection");
        uTextColor = glGetUniformLocation(progText, "uColor");

        textVao = glGenVertexArrays(); textVbo = glGenBuffers(); textEbo = glGenBuffers();
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(0);
        IntBuffer idx = memAllocInt(MAX_TEXT_QUADS * 6);
        for (int q = 0; q < MAX_TEXT_QUADS; q++) { int b=q*4; idx.put(b).put(b+1).put(b+2).put(b).put(b+2).put(b+3); }
        idx.flip(); textIndexBuffer = idx;
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, textEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, textIndexBuffer, GL_STATIC_DRAW);
        glBindVertexArray(0);
        textBuffer = memAlloc(MAX_TEXT_QUADS * 4 * 16);

        glEnable(GL_DEPTH_TEST);
        initialized = true;
    }

    private int loc(String name) { return glGetUniformLocation(prog, name); }

    public MeshHandle upload(SimpleMesh mesh) {
        int vao = glGenVertexArrays(), vbo = glGenBuffers(), ebo = glGenBuffers();
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

    public void beginScene(int w, int h, float ambR, float ambG, float ambB) {
        glViewport(0, 0, w, h);
        glClearColor(0.03f, 0.03f, 0.06f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glUseProgram(prog);
        glUniform3f(uAmbientColor, ambR, ambG, ambB);
    }

    public void setDirectionalLight(DirectionalLight light, boolean enabled) {
        glUseProgram(prog);
        glUniform1i(uDirEnabled, enabled ? 1 : 0);
        if (enabled && light != null) {
            glUniform3f(uDirDirection, light.dirX(), light.dirY(), light.dirZ());
            glUniform3f(uDirColor, light.r(), light.g(), light.b());
            glUniform1f(uDirIntensity, light.intensity());
        }
    }

    public void setPointLight(PointLight light, boolean enabled) {
        glUseProgram(prog);
        glUniform1i(uPointEnabled, enabled ? 1 : 0);
        if (enabled && light != null) {
            glUniform3f(uPointPos, light.x(), light.y(), light.z());
            glUniform3f(uPointColor, light.r(), light.g(), light.b());
            glUniform1f(uPointIntensity, light.intensity());
            glUniform1f(uPointRadius, light.radius());
        }
    }

    public void drawMesh(MeshHandle h, float[] mvp, float[] model,
                         SimpleMaterial mat, float camX, float camY, float camZ) {
        if (!initialized) return;
        glUseProgram(prog);
        if (mat.shadingMode() == SimpleMaterial.ShadingMode.WIREFRAME)
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glBindVertexArray(h.vao());
        glUniformMatrix4fv(uMVP, false, mvp);
        glUniformMatrix4fv(uModel, false, model);
        glUniform3f(uBaseColor, mat.baseColor()[0], mat.baseColor()[1], mat.baseColor()[2]);
        glUniform1f(uAmbientU, mat.ambient());
        glUniform1f(uDiffuseU, mat.diffuse());
        glUniform1f(uSpecularU, mat.specular());
        glUniform1f(uShininessU, mat.shininess());
        glUniform1i(uShadingMode, mat.shadingMode() == SimpleMaterial.ShadingMode.UNLIT ? 1 : 0);
        glUniform3f(uCameraPos, camX, camY, camZ);
        glDrawElements(GL_TRIANGLES, h.indexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        if (mat.shadingMode() == SimpleMaterial.ShadingMode.WIREFRAME)
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }

    /** Draw a small indicator cube at the point light position. */
    public void drawLightIndicator(MeshHandle h, float[] vp, PointLight light) {
        float[] model = identity();
        model[12] = light.x(); model[13] = light.y(); model[14] = light.z();
        model[0] = 0.1f; model[5] = 0.1f; model[10] = 0.1f;
        float[] mvp = multiply(vp, model);
        // Draw as unlit with the light's color
        glUseProgram(prog);
        glBindVertexArray(h.vao());
        glUniformMatrix4fv(uMVP, false, mvp);
        glUniformMatrix4fv(uModel, false, model);
        glUniform3f(uBaseColor, light.r(), light.g(), light.b());
        glUniform1i(uShadingMode, 1); // unlit
        glDrawElements(GL_TRIANGLES, h.indexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void drawText(String text, float x, float y, float scale,
                         float r, float g, float b, int w, int h) {
        if (!initialized || text == null || text.isEmpty()) return;
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(progText); glDisableVertexAttribArray(1);
        textBuffer.clear();
        int nq = STBEasyFont.stb_easy_font_print(0, 0, text, null, textBuffer);
        if (nq <= 0 || nq > MAX_TEXT_QUADS) return;
        textBuffer.limit(nq * 4 * 16);
        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, textBuffer, GL_STREAM_DRAW);
        glUniform3f(uTextColor, r, g, b);
        float left=-x/scale, right=(w-x)/scale, top=-y/scale, bottom=(h-y)/scale;
        glUniformMatrix4fv(uTextProj, false, ortho(left, right, bottom, top, -1, 1));
        glDrawElements(GL_TRIANGLES, nq * 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glDisable(GL_BLEND); glEnable(GL_DEPTH_TEST);
    }

    public void deleteMesh(MeshHandle h) { glDeleteBuffers(h.vbo()); glDeleteBuffers(h.ebo()); glDeleteVertexArrays(h.vao()); }

    public void shutdown() {
        if (initialized) {
            glDeleteProgram(prog); glDeleteProgram(progText);
            glDeleteBuffers(textVbo); glDeleteBuffers(textEbo); glDeleteVertexArrays(textVao);
            if (textBuffer != null) memFree(textBuffer);
            if (textIndexBuffer != null) memFree(textIndexBuffer);
            initialized = false;
        }
    }

    // --- Math ---
    public static float[] perspective(float fov, float a, float n, float f) {
        float t=1f/(float)Math.tan(Math.toRadians(fov)/2); float[] m=new float[16];
        m[0]=t/a;m[5]=t;m[10]=(f+n)/(n-f);m[11]=-1;m[14]=(2*f*n)/(n-f);return m; }
    public static float[] lookAt(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz) {
        float fx=cx-ex,fy=cy-ey,fz=cz-ez;float fl=(float)Math.sqrt(fx*fx+fy*fy+fz*fz);fx/=fl;fy/=fl;fz/=fl;
        float sx=fy*uz-fz*uy,sy=fz*ux-fx*uz,sz=fx*uy-fy*ux;float sl=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);sx/=sl;sy/=sl;sz/=sl;
        float upx=sy*fz-sz*fy,upy=sz*fx-sx*fz,upz=sx*fy-sy*fx;float[] m=new float[16];
        m[0]=sx;m[1]=upx;m[2]=-fx;m[4]=sy;m[5]=upy;m[6]=-fy;m[8]=sz;m[9]=upz;m[10]=-fz;
        m[12]=-(sx*ex+sy*ey+sz*ez);m[13]=-(upx*ex+upy*ey+upz*ez);m[14]=(fx*ex+fy*ey+fz*ez);m[15]=1;return m;}
    public static float[] multiply(float[] a, float[] b) {
        float[] r=new float[16];for(int i=0;i<4;i++)for(int j=0;j<4;j++)
            r[i+j*4]=a[i]*b[j*4]+a[i+4]*b[j*4+1]+a[i+8]*b[j*4+2]+a[i+12]*b[j*4+3];return r;}
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
