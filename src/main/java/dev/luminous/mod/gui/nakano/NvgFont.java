package dev.luminous.mod.gui.nakano;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * In-memory NanoVG font, ported from Nakano's Opal-style NVGTextRenderer.
 */
public final class NvgFont implements AutoCloseable {
    private static final float DISPLAY_SCALE = 1.50F;
    private final String name;
    private final ByteBuffer data;

    public NvgFont(String name, InputStream input) throws IOException {
        this.name = name;
        byte[] bytes;
        try (input) {
            bytes = input.readAllBytes();
        }

        data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes).flip();
        int handle = nvgCreateFontMem(NvgRenderer.context(), name, data, false);
        if (handle == -1) {
            MemoryUtil.memFree(data);
            throw new IOException("NanoVG could not load font " + name);
        }
    }

    public float draw(String text, float x, float y, float size, int argb) {
        return draw(text, x, y, size, argb, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    public float draw(String text, float x, float y, float size, int argb, int alignment) {
        return NvgRenderer.drawFontText(name, text, x, y, size * DISPLAY_SCALE, argb, alignment);
    }

    public float width(String text, float size) {
        return NvgRenderer.fontTextWidth(name, text, size * DISPLAY_SCALE);
    }

    @Override
    public void close() {
        MemoryUtil.memFree(data);
    }
}