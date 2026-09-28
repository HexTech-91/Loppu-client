package dev.luminous.core.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.luminous.api.interfaces.IShaderEffectHook;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.render.ShaderModule;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.satin.api.managed.ManagedShaderEffect;
import org.ladysnake.satin.api.managed.ShaderEffectManager;
import org.slf4j.Logger;

public class ShaderManager implements Wrapper {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Timer timer = new Timer();
    private static final List<RenderTask> tasks = new ArrayList<>();

    // Shader效果实例
    public static ManagedShaderEffect DEFAULT_OUTLINE;
    public static ManagedShaderEffect PULSE_OUTLINE;
    public static ManagedShaderEffect SMOKE_OUTLINE;
    public static ManagedShaderEffect GRADIENT_OUTLINE;
    public static ManagedShaderEffect SNOW_OUTLINE;
    public static ManagedShaderEffect FLOW_OUTLINE;
    public static ManagedShaderEffect RAINBOW_OUTLINE;
    public static ManagedShaderEffect DEFAULT;
    public static ManagedShaderEffect PULSE;
    public static ManagedShaderEffect SMOKE;
    public static ManagedShaderEffect GRADIENT;
    public static ManagedShaderEffect SNOW;
    public static ManagedShaderEffect FLOW;
    public static ManagedShaderEffect RAINBOW;
    public static ManagedShaderEffect SKYBOX;

    public float time = 0.0f;
    private MyFramebuffer shaderBuffer;

    // 添加初始化方法，确保在合适时机调用
    public void init() {
        if (shaderBuffer == null) {
            reloadShaders();
        }
    }

    // 添加销毁方法，避免内存泄漏
    public void destroy() {
        if (shaderBuffer != null) {
            shaderBuffer.delete();
            shaderBuffer = null;
        }
    }

    public void renderShader(Runnable runnable, Shader mode) {
        tasks.add(new RenderTask(runnable, mode));
    }

    public void renderShaders() {
        tasks.forEach(t -> this.applyShader(t.task(), t.shader()));
        tasks.clear();
    }

    public void applyShader(Runnable runnable, Shader mode) {
        if (this.fullNullCheck()) {
            return;
        }

        RenderSystem.assertOnRenderThreadOrInit();

        // 安全获取Minecraft客户端实例
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        Framebuffer MCBuffer = client.getFramebuffer();
        if (MCBuffer == null || shaderBuffer == null) return;

        // 调整缓冲区大小，确保匹配
        if (shaderBuffer.textureWidth != MCBuffer.textureWidth || shaderBuffer.textureHeight != MCBuffer.textureHeight) {
            try {
                shaderBuffer.resize(MCBuffer.textureWidth, MCBuffer.textureHeight, false);
            } catch (Exception e) {
                LOGGER.error("Failed to resize shader buffer", e);
                return;
            }
        }

        try {
            // 绑定shader缓冲区
            GlStateManager._glBindFramebuffer(36009, shaderBuffer.fbo);
            shaderBuffer.beginWrite(true);
            runnable.run();
            shaderBuffer.endWrite();

            // 切换回主缓冲区
            GlStateManager._glBindFramebuffer(36009, MCBuffer.fbo);
            MCBuffer.beginWrite(false);

            // 获取shader效果
            ManagedShaderEffect shader = this.getShader(mode);
            if (shader == null) return;

            PostEffectProcessor effect = shader.getShaderEffect();
            if (effect == null) return;

            // 设置输入缓冲区
            ((IShaderEffectHook)effect).alienClient$addHook("bufIn", shaderBuffer);

            // 获取输出缓冲区并进行空检查
            Framebuffer outBuffer = effect.getSecondaryTarget("bufOut");
            if (outBuffer == null) {
                return;
            }

            // 设置shader参数
            this.setupShader(mode, shader);

            // 清理shader缓冲区
            shaderBuffer.clear(false);

            // 确保回到主缓冲区
            MCBuffer.beginWrite(false);

            // 开启混合并绘制
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SrcFactor.ZERO,
                    GlStateManager.DstFactor.ONE
            );

            RenderSystem.backupProjectionMatrix();
            outBuffer.draw(outBuffer.textureWidth, outBuffer.textureHeight, false);
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableBlend();

        } catch (Exception e) {
            LOGGER.error("Error applying shader " + mode, e);
            // 确保在异常后恢复到安全状态
            try {
                MCBuffer.beginWrite(false);
            } catch (Exception ex) {
                LOGGER.error("Failed to restore framebuffer after shader error", ex);
            }
        }
    }

    public ManagedShaderEffect getShader(@NotNull Shader mode) {
        // 使用枚举名称匹配，避免ordinal问题
        switch (mode) {
            case Pulse:
                return PULSE;
            case Smoke:
                return SMOKE;
            case Gradient:
                return GRADIENT;
            case Snow:
                return SNOW;
            case Flow:
                return FLOW;
            case Rainbow:
                return RAINBOW;
            case Solid:
            default:
                return DEFAULT;
        }
    }

    public ManagedShaderEffect getShaderOutline(@NotNull Shader mode) {
        switch (mode) {
            case Pulse:
                return PULSE_OUTLINE;
            case Smoke:
                return SMOKE_OUTLINE;
            case Gradient:
                return GRADIENT_OUTLINE;
            case Snow:
                return SNOW_OUTLINE;
            case Flow:
                return FLOW_OUTLINE;
            case Rainbow:
                return RAINBOW_OUTLINE;
            case Solid:
            default:
                return DEFAULT_OUTLINE;
        }
    }

    public void setupShader(Shader shader, ManagedShaderEffect effect) {
        if (effect == null) {
            LOGGER.warn("setupShader called with null effect");
            return;
        }

        ShaderModule module = ShaderModule.INSTANCE;
        if (module == null) {
            LOGGER.warn("ShaderModule is null");
            return;
        }

        Color color;
        try {
            color = module.fill.getValue();
            if (color == null) {
                color = Color.WHITE;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get fill color", e);
            color = Color.WHITE;
        }

        this.time = (float) timer.getMs() / 5.0f * module.speed.getValueFloat() * 0.004f;

        try {
            switch (shader) {
                case Rainbow:
                    setUniformSafe(effect, "alpha2", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    setUniformSafe(effect, "time", this.time);
                    break;

                case Gradient:
                    setUniformSafe(effect, "alpha2", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "rgb",
                            getColorFloat((Color) module.smoke1.getValue()),
                            getColorFloat((Color) module.smoke1.getValue()),
                            getColorFloat((Color) module.smoke1.getValue()));
                    setUniformSafe(effect, "rgb1",
                            getColorFloat((Color) module.smoke2.getValue()),
                            getColorFloat((Color) module.smoke2.getValue()),
                            getColorFloat((Color) module.smoke2.getValue()));
                    setUniformSafe(effect, "rgb2",
                            getColorFloat((Color) module.smoke3.getValue()),
                            getColorFloat((Color) module.smoke3.getValue()),
                            getColorFloat((Color) module.smoke3.getValue()));
                    setUniformSafe(effect, "rgb3",
                            getColorFloat((Color) module.smoke4.getValue()),
                            getColorFloat((Color) module.smoke4.getValue()),
                            getColorFloat((Color) module.smoke4.getValue()));
                    setUniformSafe(effect, "step", module.step.getValueFloat() * 300.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    setUniformSafe(effect, "time", this.time * 300.0f);
                    break;

                case Smoke:
                    setUniformSafe(effect, "alpha1", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "first",
                            getColorFloat((Color) module.smoke1.getValue()),
                            getColorFloat((Color) module.smoke1.getValue()),
                            getColorFloat((Color) module.smoke1.getValue()),
                            (float) module.smoke1.getValue().getAlpha() / 255.0f);
                    setUniformSafe(effect, "second",
                            getColorFloat((Color) module.smoke2.getValue()),
                            getColorFloat((Color) module.smoke2.getValue()),
                            getColorFloat((Color) module.smoke2.getValue()));
                    setUniformSafe(effect, "third",
                            getColorFloat((Color) module.smoke3.getValue()),
                            getColorFloat((Color) module.smoke3.getValue()),
                            getColorFloat((Color) module.smoke3.getValue()));
                    setUniformSafe(effect, "oct", (int) module.octaves.getValue());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    setUniformSafe(effect, "time", this.time);
                    break;

                case Solid:
                    setUniformSafe(effect, "mixFactor", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "minAlpha", module.alpha.getValueFloat() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "color",
                            (float) color.getRed() / 255.0f,
                            (float) color.getGreen() / 255.0f,
                            (float) color.getBlue() / 255.0f);
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    break;

                case Pulse:
                    setUniformSafe(effect, "mixFactor", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "minAlpha", module.alpha.getValueFloat() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "color",
                            (float) color.getRed() / 255.0f,
                            (float) color.getGreen() / 255.0f,
                            (float) color.getBlue() / 255.0f);
                    Color color2 = module.pulse.getValue();
                    setUniformSafe(effect, "color2",
                            (float) color2.getRed() / 255.0f,
                            (float) color2.getGreen() / 255.0f,
                            (float) color2.getBlue() / 255.0f);
                    setUniformSafe(effect, "time", this.time);
                    setUniformSafe(effect, "size", module.pulseSpeed.getValueFloat());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    break;

                case Snow:
                    setUniformSafe(effect, "color",
                            (float) color.getRed() / 255.0f,
                            (float) color.getGreen() / 255.0f,
                            (float) color.getBlue() / 255.0f,
                            (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    setUniformSafe(effect, "time", this.time);
                    break;

                case Flow:
                    setUniformSafe(effect, "mixFactor", (float) color.getAlpha() / 255.0f);
                    setUniformSafe(effect, "radius", module.radius.getValueFloat());
                    setUniformSafe(effect, "quality", module.smoothness.getValueFloat());
                    setUniformSafe(effect, "divider", module.divider.getValueFloat());
                    setUniformSafe(effect, "maxSample", module.maxSample.getValueFloat());
                    setUniformSafe(effect, "resolution",
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    setUniformSafe(effect, "time", this.time);
                    break;
            }

            // 渲染shader
            effect.render(mc.getRenderTickCounter().getTickDelta(true));

        } catch (Exception e) {
            LOGGER.error("Error setting up shader " + shader, e);
            try {
                // 尝试简单的渲染，避免崩溃
                effect.render(mc.getRenderTickCounter().getTickDelta(true));
            } catch (Exception ex) {
                LOGGER.error("Failed to render shader after setup error", ex);
            }
        }
    }

    // 安全设置uniform的辅助方法
    private void setUniformSafe(ManagedShaderEffect effect, String name, float value) {
        try {
            effect.setUniformValue(name, value);
        } catch (Exception e) {
            LOGGER.warn("Failed to set uniform {} = {}", name, value, e);
        }
    }

    private void setUniformSafe(ManagedShaderEffect effect, String name, float v1, float v2) {
        try {
            effect.setUniformValue(name, v1, v2);
        } catch (Exception e) {
            LOGGER.warn("Failed to set uniform {} = [{}, {}]", name, v1, v2, e);
        }
    }

    private void setUniformSafe(ManagedShaderEffect effect, String name, float v1, float v2, float v3) {
        try {
            effect.setUniformValue(name, v1, v2, v3);
        } catch (Exception e) {
            LOGGER.warn("Failed to set uniform {} = [{}, {}, {}]", name, v1, v2, v3, e);
        }
    }

    private void setUniformSafe(ManagedShaderEffect effect, String name, float v1, float v2, float v3, float v4) {
        try {
            effect.setUniformValue(name, v1, v2, v3, v4);
        } catch (Exception e) {
            LOGGER.warn("Failed to set uniform {} = [{}, {}, {}, {}]", name, v1, v2, v3, v4, e);
        }
    }

    // 颜色转换辅助方法
    private float getColorFloat(Color color) {
        if (color == null) return 1.0f;
        return (float) color.getRed() / 255.0f;
    }

    // 线程安全的shader重载（吸收代码2的优点）
    public void reloadShaders() {
        Runnable reloadTask = () -> {
            try {
                DEFAULT = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/outline.json"));
                SMOKE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/smoke.json"));
                GRADIENT = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/gradient.json"));
                SNOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/snow.json"));
                FLOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/flow.json"));
                RAINBOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/rainbow.json"));
                PULSE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/pulse.json"));

                // Skybox shader (全屏天空盒效果)
                SKYBOX = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/skybox.json"));

                // 创建outline shaders
                createOutlineShaders();

                // 初始化shader缓冲区
                if (shaderBuffer == null && mc.getFramebuffer() != null) {
                    shaderBuffer = new MyFramebuffer(mc.getFramebuffer().textureWidth, mc.getFramebuffer().textureHeight);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to reload shaders", e);
            }
        };

        // 确保在渲染线程执行
        if (RenderSystem.isOnRenderThread()) {
            reloadTask.run();
        } else {
            RenderSystem.recordRenderCall(reloadTask::run);
        }
    }

    private void createOutlineShaders() {
        // 为outline shaders添加回调，统一错误处理
        try {
            DEFAULT_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/outline.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            PULSE_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/pulse.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            SMOKE_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/smoke.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            GRADIENT_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/gradient.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            SNOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/snow.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            FLOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/flow.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

            RAINBOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/rainbow.json"), managedShaderEffect -> {
                PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
                if (effect != null && mc.worldRenderer != null) {
                    ((IShaderEffectHook)effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
                    ((IShaderEffectHook)effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
                }
            });

        } catch (Exception e) {
            LOGGER.error("Failed to create outline shaders", e);
        }
    }

    public boolean fullNullCheck() {
        // 更安全的空值检查，避免连锁崩溃
        if (mc == null || mc.getFramebuffer() == null) {
            return true;
        }

        // 检查所有必要的shader是否已加载
        if (DEFAULT == null || SMOKE == null || GRADIENT == null || SNOW == null ||
                FLOW == null || RAINBOW == null || PULSE == null) {
            this.reloadShaders();
            return true;
        }

        // 检查outline shaders
        if (DEFAULT_OUTLINE == null || PULSE_OUTLINE == null || SMOKE_OUTLINE == null ||
                GRADIENT_OUTLINE == null || SNOW_OUTLINE == null || FLOW_OUTLINE == null || RAINBOW_OUTLINE == null) {
            this.reloadShaders();
            return true;
        }

        // 检查shader缓冲区
        if (shaderBuffer == null) {
            try {
                shaderBuffer = new MyFramebuffer(mc.getFramebuffer().textureWidth, mc.getFramebuffer().textureHeight);
            } catch (Exception e) {
                LOGGER.error("Failed to create shader buffer", e);
                return true;
            }
        }

        return false;
    }

    public boolean skyboxNullCheck() {
        // 更安全的空值检查，避免连锁崩溃
        if (mc == null || mc.getFramebuffer() == null) {
            return true;
        }

        if (SKYBOX == null) {
            this.reloadShaders();
            return true;
        }

        return false;
    }

    public void renderSkyboxFullscreen(Color color, Color backgroundColor, float scale, float speed, float opacity) {
        if (this.skyboxNullCheck()) {
            return;
        }

        RenderSystem.assertOnRenderThreadOrInit();

        // 安全获取Minecraft客户端实例
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        Framebuffer MCBuffer = client.getFramebuffer();
        if (MCBuffer == null || shaderBuffer == null) return;

        // 调整缓冲区大小，确保匹配
        if (shaderBuffer.textureWidth != MCBuffer.textureWidth || shaderBuffer.textureHeight != MCBuffer.textureHeight) {
            try {
                shaderBuffer.resize(MCBuffer.textureWidth, MCBuffer.textureHeight, false);
            } catch (Exception e) {
                LOGGER.error("Failed to resize shader buffer", e);
                return;
            }
        }

        try {
            // 绑定shader缓冲区并清空（skybox为全屏程序化效果，无需输入内容）
            GlStateManager._glBindFramebuffer(36009, shaderBuffer.fbo);
            shaderBuffer.beginWrite(true);
            shaderBuffer.clear(false);
            shaderBuffer.endWrite();

            // 切换回主缓冲区
            GlStateManager._glBindFramebuffer(36009, MCBuffer.fbo);
            MCBuffer.beginWrite(false);

            // 获取shader效果
            PostEffectProcessor effect = SKYBOX.getShaderEffect();
            if (effect == null) return;

            // 设置输入缓冲区
            ((IShaderEffectHook)effect).alienClient$addHook("bufIn", shaderBuffer);

            // 获取输出缓冲区并进行空检查
            Framebuffer outBuffer = effect.getSecondaryTarget("bufOut");
            if (outBuffer == null) {
                return;
            }

            // 设置shader参数
            this.time = (float) timer.getMs() / 5.0f * speed * 0.004f;
            setUniformSafe(SKYBOX, "u_Color",
                    (float) color.getRed() / 255.0f,
                    (float) color.getGreen() / 255.0f,
                    (float) color.getBlue() / 255.0f,
                    (float) color.getAlpha() / 255.0f);
            setUniformSafe(SKYBOX, "u_BackgroundColor",
                    (float) backgroundColor.getRed() / 255.0f,
                    (float) backgroundColor.getGreen() / 255.0f,
                    (float) backgroundColor.getBlue() / 255.0f,
                    (float) backgroundColor.getAlpha() / 255.0f);
            setUniformSafe(SKYBOX, "u_Resolution",
                    mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight());
            setUniformSafe(SKYBOX, "u_Mouse", 0.0f, 0.0f);
            setUniformSafe(SKYBOX, "u_Scale", scale);
            setUniformSafe(SKYBOX, "u_Time", this.time);
            setUniformSafe(SKYBOX, "mixFactor", opacity);

            // 渲染shader
            SKYBOX.render(mc.getRenderTickCounter().getTickDelta(true));

            // 清理shader缓冲区
            shaderBuffer.clear(false);
            MCBuffer.beginWrite(false);

            // 开启混合并绘制
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SrcFactor.ZERO,
                    GlStateManager.DstFactor.ONE
            );

            RenderSystem.backupProjectionMatrix();
            outBuffer.draw(outBuffer.textureWidth, outBuffer.textureHeight, false);
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableBlend();

        } catch (Exception e) {
            LOGGER.error("Error applying skybox shader", e);
            // 确保在异常后恢复到安全状态
            try {
                MCBuffer.beginWrite(false);
            } catch (Exception ex) {
                LOGGER.error("Failed to restore framebuffer after skybox error", ex);
            }
        }
    }

    // 记录类
    public record RenderTask(Runnable task, Shader shader) {
    }

    // 枚举
    public enum Shader {
        Solid,
        Pulse,
        Smoke,
        Gradient,
        Snow,
        Flow,
        Rainbow
    }

    // 自定义Framebuffer类
    public static class MyFramebuffer extends Framebuffer {
        public MyFramebuffer(int width, int height) {
            super(false);
            RenderSystem.assertOnRenderThreadOrInit();
            try {
                this.resize(width, height, true);
                this.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            } catch (Exception e) {
                LOGGER.error("Failed to create MyFramebuffer, falling back to 1x1", e);
                this.resize(1, 1, true);
            }
        }

        @Override
        public void resize(int width, int height, boolean getError) {
            try {
                super.resize(width, height, getError);
            } catch (Exception e) {
                LOGGER.error("Failed to resize framebuffer to {}x{}, keeping previous size", width, height, e);
                // 如果调整大小失败，至少保证最小大小
                super.resize(Math.max(width, 1), Math.max(height, 1), getError);
            }
        }
    }
}