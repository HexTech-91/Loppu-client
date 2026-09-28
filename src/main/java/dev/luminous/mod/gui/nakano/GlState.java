package dev.luminous.mod.gui.nakano;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL20.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_MAJOR_VERSION;
import static org.lwjgl.opengl.GL30.GL_MINOR_VERSION;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;

/**
 * Saves and restores the OpenGL state touched by NanoVG.
 * Ported from Nakano's Opal-derived GLUtility.
 */
public final class GlState {
    private final int[] activeTexture = new int[1];
    private final int[] program = new int[1];
    private final int[] texture = new int[1];
    private final int[] sampler = new int[1];
    private final int[] arrayBuffer = new int[1];
    private final int[] vertexArray = new int[1];
    private final int[] polygonMode = new int[2];
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final int[] blendSrcRgb = new int[1];
    private final int[] blendDstRgb = new int[1];
    private final int[] blendSrcAlpha = new int[1];
    private final int[] blendDstAlpha = new int[1];
    private final int[] blendEquationRgb = new int[1];
    private final int[] blendEquationAlpha = new int[1];

    private boolean blend;
    private boolean cull;
    private boolean depth;
    private boolean stencil;
    private boolean scissor;
    private boolean primitiveRestart;
    private boolean depthMask;
    private int glVersion;

    public void capture() {
        int[] major = new int[1];
        int[] minor = new int[1];
        glGetIntegerv(GL_MAJOR_VERSION, major);
        glGetIntegerv(GL_MINOR_VERSION, minor);
        glVersion = major[0] * 100 + minor[0] * 10;

        glGetIntegerv(GL_ACTIVE_TEXTURE, activeTexture);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_CURRENT_PROGRAM, program);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, texture);

        if (supportsSamplerObjects()) {
            glGetIntegerv(GL_SAMPLER_BINDING, sampler);
        }

        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, arrayBuffer);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, vertexArray);
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
        glGetIntegerv(GL_BLEND_SRC_RGB, blendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, blendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, blendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, blendDstAlpha);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, blendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, blendEquationAlpha);

        blend = glIsEnabled(GL_BLEND);
        cull = glIsEnabled(GL_CULL_FACE);
        depth = glIsEnabled(GL_DEPTH_TEST);
        stencil = glIsEnabled(GL_STENCIL_TEST);
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        primitiveRestart = glVersion >= 310 && glIsEnabled(GL_PRIMITIVE_RESTART);
        depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    }

    public void restore() {
        glUseProgram(program[0]);
        glBindTexture(GL_TEXTURE_2D, texture[0]);
        if (supportsSamplerObjects()) {
            glBindSampler(0, sampler[0]);
        }
        glActiveTexture(activeTexture[0]);
        glBindVertexArray(vertexArray[0]);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer[0]);
        glBlendEquationSeparate(blendEquationRgb[0], blendEquationAlpha[0]);
        glBlendFuncSeparate(blendSrcRgb[0], blendDstRgb[0], blendSrcAlpha[0], blendDstAlpha[0]);

        setEnabled(GL_BLEND, blend);
        setEnabled(GL_CULL_FACE, cull);
        setEnabled(GL_DEPTH_TEST, depth);
        setEnabled(GL_STENCIL_TEST, stencil);
        setEnabled(GL_SCISSOR_TEST, scissor);
        if (glVersion >= 310) {
            setEnabled(GL_PRIMITIVE_RESTART, primitiveRestart);
        }

        glPolygonMode(GL_FRONT_AND_BACK, polygonMode[0]);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        glDepthMask(depthMask);
    }

    private boolean supportsSamplerObjects() {
        return glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects;
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }
}