package dev.luminous.mod.gui.nakano;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

/**
 * NanoVG GUI pipeline ported from Nakano's Opal-style NVGRenderer.
 */
public final class NvgRenderer {
    private static final GlState GL_STATE = new GlState();
    private static final NVGColor COLOR_A = NVGColor.create();
    private static final NVGColor COLOR_B = NVGColor.create();
    private static final NVGPaint PAINT = NVGPaint.create();
    private static final List<TextCommand> TEXT_COMMANDS = new ArrayList<>();

    private static long context;
    private static boolean frameStarted;
    private static float currentGlobalAlpha = 1.0F;
    private static TextClip currentTextClip;

    private NvgRenderer() {
    }

    public static long context() {
        RenderSystem.assertOnRenderThread();
        if (context == 0L) {
            context = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (context == 0L) {
                throw new IllegalStateException("Could not create the NanoVG OpenGL context");
            }
        }
        return context;
    }

    public static boolean beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (frameStarted) {
            return false;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        float pixelRatio = (float) window.getFramebufferWidth() / Math.max(1, window.getScaledWidth());

        TEXT_COMMANDS.clear();
        currentGlobalAlpha = 1.0F;
        currentTextClip = null;
        GL_STATE.capture();
        try {
            nvgBeginFrame(context(), window.getScaledWidth(), window.getScaledHeight(), pixelRatio);
            frameStarted = true;
            return true;
        } catch (RuntimeException | Error error) {
            GL_STATE.restore();
            throw error;
        }
    }

    public static void endFrame() {
        if (!frameStarted) {
            return;
        }

        try {
            RenderSystem.assertOnRenderThread();
            nvgEndFrame(context);
        } finally {
            GL_STATE.restore();
            frameStarted = false;
        }
    }

    public static void shutdown() {
        RenderSystem.assertOnRenderThread();
        if (context != 0L) {
            nvgDelete(context);
            context = 0L;
            frameStarted = false;
        }
    }

    static void queueText(String text, float x, float y, float size, int color, int alignment) {
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0F;
        int animatedColor = withAlpha(color, colorAlpha * currentGlobalAlpha);
        TEXT_COMMANDS.add(new TextCommand(text, x, y, size, animatedColor, alignment, currentTextClip));
    }

    static float drawFontText(String fontName, String text, float x, float y, float size, int color, int alignment) {
        if (containsNonAscii(text)) {
            queueText(text, x, y, size, color, alignment);
            return fontTextWidth(fontName, text, size);
        }
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0F;
        applyColor(withAlpha(color, colorAlpha * currentGlobalAlpha), COLOR_A);
        nvgFontFace(context(), fontName);
        nvgFontSize(context(), size);
        nvgTextAlign(context(), alignment);
        nvgFillColor(context(), COLOR_A);
        return nvgText(context(), x, y, text);
    }

    static float fontTextWidth(String fontName, String text, float size) {
        if (containsNonAscii(text)) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            float scale = size / Math.max(1.0F, renderer.fontHeight);
            return renderer.getWidth(text) * scale;
        }
        nvgFontFace(context(), fontName);
        nvgFontSize(context(), size);
        return nvgTextBounds(context(), 0.0F, 0.0F, text, (float[]) null);
    }

    public static void flushText(DrawContext context) {
        if (TEXT_COMMANDS.isEmpty()) {
            return;
        }

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        for (TextCommand command : TEXT_COMMANDS) {
            if (command.clip != null) {
                context.enableScissor(
                        (int) Math.floor(command.clip.x),
                        (int) Math.floor(command.clip.y),
                        (int) Math.ceil(command.clip.right()),
                        (int) Math.ceil(command.clip.bottom())
                );
            }

            float scale = command.size / font.fontHeight;
            int textWidth = font.getWidth(command.text);
            float localX;
            if ((command.alignment & NVG_ALIGN_RIGHT) != 0) {
                localX = -textWidth;
            } else if ((command.alignment & NVG_ALIGN_CENTER) != 0) {
                localX = -textWidth * 0.5F;
            } else {
                localX = 0;
            }

            float localY;
            if ((command.alignment & NVG_ALIGN_BOTTOM) != 0) {
                localY = -font.fontHeight;
            } else if ((command.alignment & NVG_ALIGN_MIDDLE) != 0) {
                localY = -font.fontHeight * 0.5F;
            } else {
                localY = 0;
            }

            try {
                MatrixStack matrices = context.getMatrices();
                matrices.push();
                try {
                    matrices.translate(command.x, command.y, 0.0F);
                    matrices.scale(scale, scale, 1.0F);
                    context.drawText(font, command.text, Math.round(localX), Math.round(localY), command.color, false);
                } finally {
                    matrices.pop();
                }
            } finally {
                if (command.clip != null) {
                    context.disableScissor();
                }
            }
        }
        TEXT_COMMANDS.clear();
    }

    public static void globalAlpha(float alpha) {
        currentGlobalAlpha = clamp01(alpha);
        nvgGlobalAlpha(context(), currentGlobalAlpha);
    }

    public static void withGlobalAlpha(float multiplier, Runnable drawing) {
        float previousAlpha = currentGlobalAlpha;
        globalAlpha(previousAlpha * clamp01(multiplier));
        try {
            drawing.run();
        } finally {
            globalAlpha(previousAlpha);
        }
    }

    public static void rect(float x, float y, float width, float height, int color) {
        beginPath(color);
        nvgRect(context, x, y, width, height);
        finishPath();
    }

    public static void roundedRect(float x, float y, float width, float height, float radius, int color) {
        beginPath(color);
        nvgRoundedRect(context, x, y, width, height, radius);
        finishPath();
    }

    public static void roundedRectVarying(
            float x, float y, float width, float height,
            float topLeft, float topRight, float bottomRight, float bottomLeft,
            int color
    ) {
        beginPath(color);
        nvgRoundedRectVarying(context, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
        finishPath();
    }

    public static void roundedGradient(
            float x, float y, float width, float height, float radius,
            int colorA, int colorB, float angleDegrees
    ) {
        applyColor(colorA, COLOR_A);
        applyColor(colorB, COLOR_B);

        float angle = (float) Math.toRadians(angleDegrees);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        nvgLinearGradient(
                context(),
                x + width * 0.5F - dx * width * 0.5F,
                y + height * 0.5F - dy * height * 0.5F,
                x + width * 0.5F + dx * width * 0.5F,
                y + height * 0.5F + dy * height * 0.5F,
                COLOR_A, COLOR_B, PAINT
        );

        nvgBeginPath(context);
        nvgFillPaint(context, PAINT);
        nvgRoundedRect(context, x, y, width, height, radius);
        nvgFill(context);
        nvgClosePath(context);
    }

    public static void roundedGradientVarying(
            float x, float y, float width, float height,
            float topLeft, float topRight, float bottomRight, float bottomLeft,
            int colorA, int colorB, float angleDegrees
    ) {
        applyColor(colorA, COLOR_A);
        applyColor(colorB, COLOR_B);

        float angle = (float) Math.toRadians(angleDegrees);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        nvgLinearGradient(
                context(),
                x + width * 0.5F - dx * width * 0.5F,
                y + height * 0.5F - dy * height * 0.5F,
                x + width * 0.5F + dx * width * 0.5F,
                y + height * 0.5F + dy * height * 0.5F,
                COLOR_A, COLOR_B, PAINT
        );

        nvgBeginPath(context);
        nvgFillPaint(context, PAINT);
        nvgRoundedRectVarying(context, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
        nvgFill(context);
        nvgClosePath(context);
    }

    public static void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        applyColor(color, COLOR_A);
        nvgBeginPath(context());
        nvgStrokeColor(context, COLOR_A);
        nvgStrokeWidth(context, thickness);
        nvgRoundedRect(context, x, y, width, height, radius);
        nvgStroke(context);
        nvgClosePath(context);
    }

    public static void gradientGlowOutline(
            float x, float y, float width, float height, float radius,
            int colorA, int colorB, float strength
    ) {
        float intensity = clamp01(strength);
        if (intensity <= 0.001F || width <= 0.0F || height <= 0.0F) {
            return;
        }

        for (int layer = 6; layer >= 1; layer--) {
            float layerProgress = layer / 6.0F;
            float alpha = intensity * (0.025F + (1.0F - layerProgress) * 0.075F);
            applyColor(withAlpha(colorA, alpha), COLOR_A);
            applyColor(withAlpha(colorB, alpha), COLOR_B);
            nvgLinearGradient(context(), x, y, x + width, y + height, COLOR_A, COLOR_B, PAINT);

            nvgBeginPath(context);
            nvgStrokePaint(context, PAINT);
            nvgStrokeWidth(context, 1.0F + layer * intensity * 1.65F);
            nvgRoundedRect(context, x, y, width, height, radius);
            nvgStroke(context);
            nvgClosePath(context);
        }

        applyColor(withAlpha(colorA, 0.35F + intensity * 0.55F), COLOR_A);
        applyColor(withAlpha(colorB, 0.35F + intensity * 0.55F), COLOR_B);
        nvgLinearGradient(context(), x, y, x + width, y + height, COLOR_A, COLOR_B, PAINT);
        nvgBeginPath(context);
        nvgStrokePaint(context, PAINT);
        nvgStrokeWidth(context, 0.75F + intensity * 0.9F);
        nvgRoundedRect(context, x, y, width, height, radius);
        nvgStroke(context);
        nvgClosePath(context);
    }

    public static void scissor(float x, float y, float width, float height, Runnable drawing) {
        TextClip previousTextClip = currentTextClip;
        TextClip requestedClip = new TextClip(x, y, Math.max(0.0F, width), Math.max(0.0F, height));
        currentTextClip = previousTextClip == null
                ? requestedClip
                : previousTextClip.intersect(requestedClip);
        nvgSave(context());
        try {
            nvgIntersectScissor(context, x, y, Math.max(0, width), Math.max(0, height));
            drawing.run();
        } finally {
            nvgRestore(context);
            currentTextClip = previousTextClip;
        }
    }

    public static void applyColor(int argb, NVGColor target) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        float red = ((argb >>> 16) & 0xFF) / 255.0F;
        float green = ((argb >>> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        nvgRGBAf(red, green, blue, alpha, target);
    }

    public static int withAlpha(int rgb, float alpha) {
        return ((int) (clamp01(alpha) * 255.0F) << 24) | (rgb & 0x00FFFFFF);
    }

    public static int mix(int first, int second, float progress) {
        float t = clamp01(progress);
        int a = mixChannel(first >>> 24, second >>> 24, t);
        int r = mixChannel(first >>> 16, second >>> 16, t);
        int g = mixChannel(first >>> 8, second >>> 8, t);
        int b = mixChannel(first, second, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void beginPath(int color) {
        applyColor(color, COLOR_A);
        nvgBeginPath(context());
        nvgFillColor(context, COLOR_A);
    }

    private static void finishPath() {
        nvgFill(context);
        nvgClosePath(context);
    }

    private static int mixChannel(int first, int second, float progress) {
        return Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * progress);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean containsNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) return true;
        }
        return false;
    }

    private record TextClip(float x, float y, float width, float height) {
        float right() {
            return x + width;
        }

        float bottom() {
            return y + height;
        }

        TextClip intersect(TextClip other) {
            float left = Math.max(x, other.x);
            float top = Math.max(y, other.y);
            float right = Math.min(right(), other.right());
            float bottom = Math.min(bottom(), other.bottom());
            return new TextClip(left, top, Math.max(0.0F, right - left), Math.max(0.0F, bottom - top));
        }
    }

    private record TextCommand(
            String text, float x, float y, float size, int color, int alignment, TextClip clip
    ) {
    }
}