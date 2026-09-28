package dev.luminous.mod.modules.impl.client;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ModuleToggleEvent;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DynamicIsland extends Module {
    public static DynamicIsland INSTANCE;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

    // 用于显示功能切换的变量
    private String lastToggledModule = "";
    private boolean moduleToggled = false;
    private boolean moduleState = false; // 记录模块的启用状态
    private long toggleTime = 0;
    private final long DISPLAY_DURATION = 3000; // 3秒显示时间

    // 动画相关变量
    private final Animation toggleAnimation = new Animation();
    private final Animation expandAnimation = new Animation();
    private final Animation pulseAnimation = new Animation(); // 添加脉冲动画
    private boolean isExpanded = false;
    private long expandStartTime = 0;
    private final long EXPAND_DURATION = 500; // 增加展开动画持续时间，使动画更流畅
    private final long RETRACT_DURATION = 400; // 增加收缩动画持续时间
    private final long SLIDE_DURATION = 700; // 增加滑动动画持续时间
    private final long PULSE_DURATION = 1500; // 脉冲动画持续时间
    private long lastAnimationTime = 0; // 记录上次动画更新时间

    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting bgColor = add(new ColorSetting("BgColor", new Color(0, 0, 0, 180)));
    public final BooleanSetting shadow = add(new BooleanSetting("Shadow", true));
    public final BooleanSetting customFont = add(new BooleanSetting("CustomFont", true));
    public final SliderSetting xOffset = add(new SliderSetting("XOffset", 0.0, -200.0, 200.0, 1.0));
    public final SliderSetting yOffset = add(new SliderSetting("YOffset", 50.0, 0.0, 500.0, 1.0));
    public final SliderSetting rounding = add(new SliderSetting("Rounding", 15.0, 0.0, 30.0, 0.5));
    public final ColorSetting textColor = add(new ColorSetting("TextColor", new Color(255, 255, 255)));
    public final SliderSetting scale = add(new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
    public final SliderSetting animationSpeed = add(new SliderSetting("AnimationSpeed", 1.0, 0.1, 3.0, 0.1));
    public final BooleanSetting glowEffect = add(new BooleanSetting("GlowEffect", true));
    public final ColorSetting glowColor = add(new ColorSetting("GlowColor", new Color(255, 255, 255, 40))); // 白色泛光
    public final BooleanSetting constantGlow = add(new BooleanSetting("ConstantGlow", true));
    public final SliderSetting glowSize = add(new SliderSetting("GlowSize", 4.0, 1.0, 10.0, 0.5)); // 泛光大小
    public final SliderSetting glowIntensity = add(new SliderSetting("GlowIntensity", 0.3, 0.1, 1.0, 0.1)); // 泛光强度
    public final SliderSetting outerGlowSize = add(new SliderSetting("OuterGlowSize", 8.0, 1.0, 20.0, 0.5)); // 外层泛光大小
    public final SliderSetting outerGlowIntensity = add(new SliderSetting("OuterGlowIntensity", 0.2, 0.1, 1.0, 0.1)); // 外层泛光强度

    public DynamicIsland() {
        super("DynamicIsland", Module.Category.Client);
        this.setChinese("灵动岛");
        INSTANCE = this;
    }

    @EventListener
    public void onModuleToggle(ModuleToggleEvent event) {
        // 忽略DynamicIsland自身的切换
        if (event.getModule() == this) return;

        // 记录最后切换的模块
        lastToggledModule = event.getModule().getName();
        moduleToggled = true;
        moduleState = event.getState(); // 记录模块状态
        toggleTime = System.currentTimeMillis();

        // 触发展开动画
        isExpanded = true;
        expandStartTime = System.currentTimeMillis();
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;

        DrawContext context = event.drawContext;
        float scaledWidth = mc.getWindow().getScaledWidth();
        float scaledHeight = mc.getWindow().getScaledHeight();

        // 计算位置和尺寸
        float x = scaledWidth / 2.0f + xOffset.getValueFloat();
        float y = yOffset.getValueFloat();
        float scaleValue = scale.getValueFloat();

        // 检查是否需要显示模块切换信息
        boolean showModuleToggle = moduleToggled && (System.currentTimeMillis() - toggleTime < DISPLAY_DURATION);

        // 更新动画状态
        if (isExpanded && !showModuleToggle && (System.currentTimeMillis() - toggleTime > DISPLAY_DURATION)) {
            isExpanded = false;
            expandStartTime = System.currentTimeMillis();
            // 触发恢复到Zephyr界面的增长动画
            expandAnimation.from = 0.0;
            expandAnimation.to = 1.0;
        }

        // 计算动画进度 - 使用更平滑的缓动函数
        float expandProgress = 0.0f;
        long expandElapsedTime = System.currentTimeMillis() - expandStartTime;
        float expandDuration = isExpanded ? EXPAND_DURATION : RETRACT_DURATION;
        float expandTargetProgress = isExpanded ? 1.0f : 0.0f;
        expandProgress = Math.min(expandTargetProgress, expandElapsedTime / (float)expandDuration * animationSpeed.getValueFloat());
        expandProgress = (float) Easing.Expo.ease(expandProgress);

        float toggleProgress = (float) toggleAnimation.get(showModuleToggle ? 1.0 : 0.0, 250, Easing.Expo);

        // 计算脉冲动画进度
        float pulseProgress = (float) pulseAnimation.get(0.5f, PULSE_DURATION, Easing.SineInOut);
        float pulseScale = 1.0f + 0.02f * pulseProgress; // 减小脉冲幅度为2%

        float totalWidth;
        float height = 22.0f * scaleValue;

        // 计算默认信息宽度
        String time = timeFormat.format(new Date());
        String fps = "FPS：" + dev.luminous.Alien.FPS.getFps();

        String ping = "Ping：";
        if (mc.getNetworkHandler() != null && mc.player != null) {
            PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            ping += playerListEntry != null ? playerListEntry.getLatency() : "Unknown";
        } else {
            ping += "Unknown";
        }

        float seaWidth = getStringWidth("Zephyr");
        float timeWidth = getStringWidth(time);
        float fpsWidth = getStringWidth(fps);
        float pingWidth = getStringWidth(ping);
        float pipeWidth = getStringWidth("|");
        float padding = 4.0f * scaleValue;
        float spacing = 5.0f * scaleValue;
        float iconSize = getFontHeight(); // 提前定义iconSize
        String playerName = mc.player != null ? mc.player.getName().getString() : "Player";
        float playerNameWidth = getStringWidth(playerName);
        float timeTextWidth = getStringWidth("Time：" + time);
        float fpsTextWidth = getStringWidth("FPS：" + fps.substring(4));
        float pingTextWidth = getStringWidth("Ping：" + ping.substring(5));
        // 自动计算内容所需宽度，确保灵动岛能够完美包围内容
        float contentWidth = seaWidth + playerNameWidth + timeTextWidth + fpsTextWidth + pingTextWidth + getStringWidth("·") * 5 + iconSize * 5 + spacing * 6;
        // 动态调整灵动岛宽度，只在右侧添加额外的空间
        totalWidth = contentWidth + padding * 2 + 10 * scaleValue + 10 * scaleValue;
        // 确保最小宽度，避免内容过少时灵动岛太小
        totalWidth = Math.max(totalWidth, 120.0f * scaleValue);

        if (showModuleToggle || expandProgress > 0.0f) {
            // 显示模块切换信息
            String moduleName = lastToggledModule;
            float moduleWidth = getStringWidth(moduleName);
            float switchWidth = 34.0f * scaleValue; // 增加开关按钮宽度
            float togglePadding = 8.0f * scaleValue;
            float toggleSpacing = 15.0f * scaleValue;
            // 自动计算模块切换界面所需宽度
            float toggleContentWidth = moduleWidth + switchWidth + toggleSpacing;
            // 动态调整宽度，添加适当的内边距
            float toggleTotalWidth = toggleContentWidth + togglePadding * 2;
            // 确保最小宽度
            toggleTotalWidth = Math.max(toggleTotalWidth, 100.0f * scaleValue);

            // 使用更流畅的伸长动画
            float widthDiff = toggleTotalWidth - totalWidth;
            float animationProgress = 0.0f;

            // 合并的动画计算
            long widthElapsedTime = System.currentTimeMillis() - expandStartTime;
            float widthDuration = showModuleToggle ? EXPAND_DURATION : RETRACT_DURATION;
            float widthTargetProgress = showModuleToggle ? 1.0f : 0.0f;
            animationProgress = Math.min(widthTargetProgress, widthElapsedTime / (float)widthDuration * animationSpeed.getValueFloat());
            animationProgress = (float) Easing.Expo.ease(animationProgress);

            totalWidth = totalWidth + widthDiff * animationProgress;
        }

        // 绘制背景
        if (background.getValue()) {
            float halfWidth = totalWidth / 2.0f * pulseScale;

            // 绘制背景
            Render2DUtil.drawRoundedRect(context.getMatrices(),
                    x - halfWidth, y,
                    totalWidth * pulseScale, height * pulseScale,
                    rounding.getValueFloat() * pulseScale,
                    bgColor.getValue());
        }

        // 绘制泛光效果（在背景之后，文本之前）
        if (glowEffect.getValue()) {
            float halfWidth = totalWidth / 2.0f * pulseScale;

            // 绘制内层泛光
            if (glowEffect.getValue()) {
                float currentGlowSize = glowSize.getValueFloat() * scaleValue;
                // 根据是否开启constantGlow调整泛光大小
                float actualGlowSize = constantGlow.getValue() ? 
                        currentGlowSize * glowIntensity.getValueFloat() * 2.0f : // 开启constantGlow时，泛光范围扩大2倍
                        currentGlowSize * glowIntensity.getValueFloat();

                // 使用白色泛光，降低透明度确保文字可见
                Color actualGlowColor = new Color(255, 255, 255,
                        Math.min(80, (int)(glowColor.getValue().getAlpha() * glowIntensity.getValueFloat())));

                Render2DUtil.drawRoundedRect(context.getMatrices(),
                        x - halfWidth - actualGlowSize, y - actualGlowSize,
                        totalWidth * pulseScale + actualGlowSize * 2, height * pulseScale + actualGlowSize * 2,
                        rounding.getValueFloat() * pulseScale + actualGlowSize,
                        actualGlowColor);
            }
        }

        if (showModuleToggle || expandProgress > 0.0f) {
            // 显示模块切换信息
            String moduleName = lastToggledModule;
            float moduleWidth = getStringWidth(moduleName);
            float switchWidth = 34.0f * scaleValue; // 增加开关按钮宽度
            float togglePadding = 8.0f * scaleValue;
            float toggleSpacing = 15.0f * scaleValue;

            // 根据动画进度决定显示内容
            if (expandProgress > 0.5f && showModuleToggle) {
                // 显示模块切换信息
                float textY = y + (height * pulseScale - getFontHeight()) / 2.0f + 1.0f;

                // 计算模块名称和开关的总宽度
                float totalContentWidth = moduleWidth + switchWidth + 10.0f * scaleValue; // 模块名称宽度 + 开关宽度 + 间距
                float startX = x - totalContentWidth / 2.0f; // 居中起始位置

                // 模块名称
                drawString(context, lastToggledModule, startX, textY, textColor.getValue().getRGB());

                // iOS风格开关按钮
                float switchX = startX + moduleWidth + 5.0f * scaleValue; // 向右移动开关按钮
                float switchHeight = 12.0f * scaleValue; // 增加开关高度
                float switchY = y + (height * pulseScale - switchHeight) / 2.0f; // 调整Y坐标使开关与文本居中对齐

                // 颜色过渡
                float colorProgress = moduleState ? expandProgress : 1.0f - expandProgress;
                int r = (int)(244 + (76 - 244) * colorProgress);
                int g = (int)(67 + (175 - 67) * colorProgress);
                int b = (int)(54 + (80 - 54) * colorProgress);
                Color switchColor = new Color(r, g, b);

                // 绘制开关背景
                Render2DUtil.drawRoundedRect(context.getMatrices(),
                        switchX, switchY,
                        switchWidth, switchHeight,
                        switchHeight / 2.0f,
                        switchColor);

                // 绘制优化后的开关滑块
                float knobSize = 8.0f * scaleValue; // 调整滑块大小
                // 滑块位置直接根据模块状态显示，不使用动画
                float knobMinX = switchX + 3.0f * scaleValue; // 调整滑块左边距
                float knobMaxX = switchX + switchWidth - knobSize - 3.0f * scaleValue; // 调整滑块右边距
                float knobX = moduleState ? knobMaxX : knobMinX;
                float knobY = switchY + (switchHeight - knobSize) / 2.0f;

                Render2DUtil.drawRoundedRect(context.getMatrices(),
                        knobX, knobY,
                        knobSize, knobSize,
                        knobSize / 2.0f,
                        new Color(255, 255, 255));
            }
        } else {
            // 显示默认信息
            // 绘制文本
            float textY = y + (height * pulseScale - getFontHeight()) / 2.0f + 1.0f;
            float currentX = x - totalWidth / 2.0f + padding + 10.0f * scaleValue;

            // 添加左右增长动画效果
            if (!showModuleToggle && expandProgress > 0) {
                // 计算增长动画进度
                float growProgress = (float) expandAnimation.get(1.0, SLIDE_DURATION, Easing.Expo);
                // 计算文本裁剪宽度
                float clipWidth = totalWidth * growProgress;
                // 调整文本起始位置，实现从中心向两边增长的效果
                float centerOffset = (totalWidth - clipWidth) / 2.0f;
                currentX += centerOffset;

                // 设置裁剪区域
                float clipX = x - totalWidth / 2.0f + centerOffset;
                float clipY = y;
                context.enableScissor((int)clipX, (int)clipY, (int)(clipX + clipWidth), (int)(clipY + height * pulseScale));
            }

            // Zephyr (淡蓝色) + 锚图标 ⚓
            // iconSize已在前面定义
            drawString(context, "⚓", currentX, textY, new Color(142, 234, 246).getRGB());
            drawString(context, "Zephyr", currentX + iconSize + 2, textY, new Color(142, 234, 246).getRGB());
            currentX += iconSize + 2 + getStringWidth("Zephyr") + spacing;

            // 分隔点 (白色)
            drawString(context, "·", currentX, textY, new Color(255, 255, 255).getRGB());
            currentX += getStringWidth("·") + spacing;

            // 玩家名称 (绿色) + 人物图标 👤
            drawString(context, "👤", currentX, textY, new Color(85, 255, 85).getRGB());
            drawString(context, playerName, currentX + iconSize + 2, textY, new Color(85, 255, 85).getRGB());
            currentX += iconSize + 2 + getStringWidth(playerName) + spacing;

            // 分隔点 (白色)
            drawString(context, "·", currentX, textY, new Color(255, 255, 255).getRGB());
            currentX += getStringWidth("·") + spacing;

            // Time:时间 (白色) + 时钟图标 🕐
            drawString(context, "🕐", currentX, textY, new Color(255, 255, 255).getRGB());
            drawString(context, "Time:" + time, currentX + iconSize + 2, textY, new Color(255, 255, 255).getRGB());
            currentX += iconSize + 2 + getStringWidth("Time:" + time) + spacing;

            // 分隔点 (白色)
            drawString(context, "·", currentX, textY, new Color(255, 255, 255).getRGB());
            currentX += getStringWidth("·") + spacing;

            // FPS:帧率 (白色) + 图表图标 📊
            drawString(context, "📊", currentX, textY, new Color(255, 255, 255).getRGB());
            drawString(context, "FPS:" + fps.substring(4), currentX + iconSize + 2, textY, new Color(255, 255, 255).getRGB());
            currentX += iconSize + 2 + getStringWidth("FPS:" + fps.substring(4)) + spacing;

            // 分隔点 (白色)
            drawString(context, "·", currentX, textY, new Color(255, 255, 255).getRGB());
            currentX += getStringWidth("·") + spacing;

            // Ping:延迟 (白色) + 地球图标 🌐
            drawString(context, "🌐", currentX, textY, new Color(255, 255, 255).getRGB());
            drawString(context, "Ping:" + ping.substring(5), currentX + iconSize + 2, textY, new Color(255, 255, 255).getRGB());
            currentX += iconSize + 2 + getStringWidth("Ping:" + ping.substring(5)) + spacing;

            // 结束裁剪区域
            if (!showModuleToggle && expandProgress > 0) {
                context.disableScissor();
            }
        }
    }

    private float getStringWidth(String text) {
        // 使用原版字体
        return mc.textRenderer.getWidth(text) * scale.getValueFloat();
    }

    private float getFontHeight() {
        // 使用原版字体高度
        return mc.textRenderer.fontHeight * scale.getValueFloat();
    }

    private void drawString(DrawContext context, String text, float x, float y, int color) {
        // 使用原版字体
        context.drawText(mc.textRenderer, text, (int)x, (int)y, color, shadow.getValue());
    }
}
