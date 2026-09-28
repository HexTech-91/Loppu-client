package dev.luminous.mod.modules.impl.player;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

//当玩家自身血量低于设定阈值时，在屏幕上显示 "Your body Health is low!" 警告

public class LowHealthAlert extends Module {
    public static LowHealthAlert INSTANCE;

    private final SliderSetting threshold;  // 血量阈值（绝对值，例如 10.0 代表 10 点生命值）
    private final SliderSetting yOffset;    // 垂直偏移量，用于调整文字显示位置
    private long lastFlashTime;             // 上次闪烁时间，用于实现闪烁效果

    public LowHealthAlert() {
        super("LowHealthAlert", Category.Player);
        // 阈值：确切生命值，范围 0~36（覆盖常规最大生命值及模组加成），默认 10.0，步进 0.5
        this.threshold = add(new SliderSetting("Threshold", 10.0d, 0.0d, 36.0d, 0.5d));
        // Y轴偏移，默认 -60（相对于屏幕中心偏上），范围 0~200
        this.yOffset = add(new SliderSetting("YOffset", -60.0d, 0.0d, 200.0d, 1.0d));
        setChinese("低血量警报");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext drawContext, float tickDelta) {
        // 基础空值检查（世界/玩家不存在时跳过）
        if (nullCheck()) {
            return;
        }

        double thresholdValue = this.threshold.getValue();
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int yOffset = (int) this.yOffset.getValue();

        // 获取玩家当前生命值（浮点数）
        float currentHealth = mc.player.getHealth();

        // 如果当前生命值低于阈值，则绘制警告
        if (currentHealth < thresholdValue) {
            drawWarning(drawContext, "Your body Health is low!", screenWidth, screenHeight, yOffset);
        }
    }

    /**
     * 绘制闪烁警告文本
     *
     * @param drawContext  绘图上下文
     * @param text         要显示的文本
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param yOffset      Y轴偏移量
     */
    private void drawWarning(DrawContext drawContext, String text, int screenWidth, int screenHeight, int yOffset) {
        int textWidth = mc.textRenderer.getWidth(text);
        // 计算文字居中位置（X居中，Y在屏幕中心偏上 + 偏移）
        int x = (screenWidth - textWidth) / 2;
        int y = ((screenHeight / 2) - 100) + yOffset;

        long currentTime = System.currentTimeMillis();
        // 闪烁逻辑：每500ms一个周期，其中前250ms显示边框，后250ms隐藏边框
        boolean shouldFlash = currentTime - this.lastFlashTime < 250;
        if (currentTime - this.lastFlashTime >= 500) {
            this.lastFlashTime = currentTime;
        }

        if (shouldFlash) {
            // 绘制红色半透明边框（与 LowArmorAlert 相同样式）
            int left = x - 2;
            int top = y - 2;
            int right = x + textWidth + 2;
            int bottom = y + 9 + 2;

            // 上边框
            drawContext.fill(left + 1, top, right - 1, top + 1, -2136342528);
            // 下边框
            drawContext.fill(left + 1, bottom - 1, right - 1, bottom, -2136342528);
            // 左边框
            drawContext.fill(left, top + 1, left + 1, bottom - 1, -2136342528);
            // 右边框
            drawContext.fill(right - 1, top + 1, right, bottom - 1, -2136342528);
            // 填充背景
            drawContext.fill(left + 1, top + 1, right - 1, bottom - 1, -2136342528);
        }

        // 绘制文字（红色）
        drawContext.drawTextWithShadow(mc.textRenderer, Text.literal(text), x, y, -65536);
    }
}