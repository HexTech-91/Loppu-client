package dev.luminous.mod.modules.impl.player;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.util.Objects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class LowArmorAlert extends Module {
    public static LowArmorAlert INSTANCE;
    private final SliderSetting threshold;  // 阈值设置
    private final SliderSetting yOffset;    // Y偏移设置
    private long lastFlashTime;            // 最后闪烁时间

    public LowArmorAlert() {
        super("LowArmorAlert", Module.Category.Player);
        this.threshold = add(new SliderSetting("Threshold", 35.0d, 0.0d, 100.0d, 1.0d));
        this.yOffset = add(new SliderSetting("YOffset", -60.0d, 0.0d, 200.0d, 1.0d));
        setChinese("低盔甲耐久警报");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext drawContext, float tickDelta) {
        if (nullCheck()) {
            return;
        }
        double thresholdValue = this.threshold.getValue();
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int yOffset = (int) this.yOffset.getValue();
        boolean hasLowDurability = false;

        // 检查头盔
        ItemStack helmet = (ItemStack) mc.player.getInventory().armor.get(3);
        if (!helmet.isEmpty() && (helmet.getItem() instanceof ArmorItem)) {
            float durabilityPercent = getDurabilityPercent(helmet);
            if (durabilityPercent < thresholdValue) {
                hasLowDurability = true;
            }
        }

        // 检查胸甲
        ItemStack chestplate = (ItemStack) mc.player.getInventory().armor.get(2);
        if (!chestplate.isEmpty() && (chestplate.getItem() instanceof ArmorItem)) {
            float durabilityPercent = getDurabilityPercent(chestplate);
            if (durabilityPercent < thresholdValue) {
                hasLowDurability = true;
            }
        }

        // 检查护腿
        ItemStack leggings = (ItemStack) mc.player.getInventory().armor.get(1);
        if (!leggings.isEmpty() && (leggings.getItem() instanceof ArmorItem)) {
            float durabilityPercent = getDurabilityPercent(leggings);
            if (durabilityPercent < thresholdValue) {
                hasLowDurability = true;
            }
        }

        // 检查靴子
        ItemStack boots = (ItemStack) mc.player.getInventory().armor.get(0);
        if (!boots.isEmpty() && (boots.getItem() instanceof ArmorItem)) {
            float durabilityPercent = getDurabilityPercent(boots);
            if (durabilityPercent < thresholdValue) {
                hasLowDurability = true;
            }
        }

        if (hasLowDurability) {
            drawWarning(drawContext, "Your armor durability is low!", screenWidth, screenHeight, yOffset);
        }
    }

    private float getDurabilityPercent(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0f;
        }
        float maxDamage = stack.getMaxDamage();
        float currentDamage = stack.getDamage();
        float durability = maxDamage - currentDamage;
        return (durability / maxDamage) * 100.0f;
    }

    private void drawWarning(DrawContext drawContext, String text, int screenWidth, int screenHeight, int yOffset) {
        int textWidth = mc.textRenderer.getWidth(text);
        Objects.requireNonNull(mc.textRenderer);
        int x = (screenWidth - textWidth) / 2;
        int y = ((screenHeight / 2) - 100) + yOffset;
        long currentTime = System.currentTimeMillis();
        boolean shouldFlash = currentTime - this.lastFlashTime < 250;
        if (currentTime - this.lastFlashTime >= 500) {
            this.lastFlashTime = currentTime;
        }
        if (shouldFlash) {
            int left = x - 2;
            int top = y - 2;
            int right = x + textWidth + 2;
            int bottom = y + 9 + 2;
            // 绘制闪烁的红色边框
            drawContext.fill(left + 1, top, right - 1, top + 1, -2136342528);
            drawContext.fill(left + 1, bottom - 1, right - 1, bottom, -2136342528);
            drawContext.fill(left, top + 1, left + 1, bottom - 1, -2136342528);
            drawContext.fill(right - 1, top + 1, right, bottom - 1, -2136342528);
            drawContext.fill(left + 1, top + 1, right - 1, bottom - 1, -2136342528);
        }
        // 修复：使用新的方法签名，将字符串转换为Text对象
        drawContext.drawTextWithShadow(mc.textRenderer, Text.literal(text), x, y, -65536);
    }
}