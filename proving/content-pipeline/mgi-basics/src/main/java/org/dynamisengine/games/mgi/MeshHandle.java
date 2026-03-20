package org.dynamisengine.games.mgi;

/**
 * GPU handle for an uploaded mesh. Holds VAO, VBO, EBO, and index count.
 */
public record MeshHandle(int vao, int vbo, int ebo, int indexCount) {}
