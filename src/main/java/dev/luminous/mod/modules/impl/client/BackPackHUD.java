package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.utils.render.Render2DUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import java.awt.Color;

public class BackPackHUD extends Module {
    public static BackPackHUD INSTANCE;

    private final BooleanSetting enable = add(new BooleanSetting("Enable", true));
    private final SliderSetting x = add(new SliderSetting("X", 10, 0, 100, 1));
    private final SliderSetting y = add(new SliderSetting("Y", 50, 0, 100, 1));
    private final SliderSetting scale = add(new SliderSetting("Scale", 1.0, 0.1, 3.0, 0.1));
    private final BooleanSetting showCount = add(new BooleanSetting("ShowCount", true));

    // 新增背景色设置
    private final ColorSetting bgColor = add(new ColorSetting("Background", new Color(0, 0, 0, 120)));

    private final BooleanSetting borderEnable = add(new BooleanSetting("Border", true));
    private final ColorSetting borderColor = add(new ColorSetting("BorderColor", new Color(255, 255, 255, 200)));
    private final SliderSetting borderPadding = add(new SliderSetting("BorderPadding", 2, 0, 10, 1));

    // 移除 BorderThickness，因为 Render2DUtil.drawRoundedStroke 只画 1 像素边框
    // 若需要粗细，需自行扩展 Render2DUtil

    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int BASE_SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 1;
    private static final int BACKPACK_BASE_WIDTH = COLUMNS * BASE_SLOT_SIZE + (COLUMNS - 1) * SLOT_SPACING;
    private static final int BACKPACK_BASE_HEIGHT = ROWS * BASE_SLOT_SIZE + (ROWS - 1) * SLOT_SPACING;

    public BackPackHUD() {
        super("BackPackHUD", Category.Client);
        setChinese("背包显示");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!enable.getValue() || mc.player == null) return;

        float currentScale = (float) scale.getValue();
        if (currentScale <= 0.001f) currentScale = 0.1f;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int baseX = (int) (screenWidth * (x.getValue() / 100.0));
        int baseY = (int) (screenHeight * (y.getValue() / 100.0));

        ItemStack[] backpackItems = getBackpackItems();

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(baseX, baseY, 0);
        matrices.scale(currentScale, currentScale, 1.0f);

        // 绘制背景和边框（使用 Render2DUtil 保证精准对齐）
        drawBackpackBackgroundAndBorder(matrices);

        // 绘制物品
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int itemIndex = row * COLUMNS + col;
                int slotX = col * (BASE_SLOT_SIZE + SLOT_SPACING);
                int slotY = row * (BASE_SLOT_SIZE + SLOT_SPACING);
                renderSingleItem(context, backpackItems[itemIndex], slotX, slotY);
            }
        }

        matrices.pop();
    }

    private void drawBackpackBackgroundAndBorder(MatrixStack matrices) {
        float padding = borderPadding.getValueFloat();
        float bgX = -padding;
        float bgY = -padding;
        float bgWidth = BACKPACK_BASE_WIDTH + 2 * padding;
        float bgHeight = BACKPACK_BASE_HEIGHT + 2 * padding;

        // 绘制半透明背景
        Render2DUtil.drawRoundedRect(matrices, bgX, bgY, bgWidth, bgHeight, 0, bgColor.getValue());

        // 绘制 1 像素宽的边框（分段数 32 保证圆角平滑，此处无圆角所以线段数不影响直线）
        if (borderEnable.getValue()) {
            Render2DUtil.drawRoundedStroke(matrices, bgX, bgY, bgWidth, bgHeight, 0, borderColor.getValue(), 32);
        }
    }

    private ItemStack[] getBackpackItems() {
        ItemStack[] items = new ItemStack[27];
        if (mc.player == null) return items;
        for (int i = 0; i < 27; i++) {
            items[i] = mc.player.getInventory().getStack(9 + i);
        }
        return items;
    }

    private void renderSingleItem(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        context.drawItem(stack, x, y);
        if (showCount.getValue()) {
            context.drawItemInSlot(mc.textRenderer, stack, x, y);
        }
    }

    // 外部依赖的 getter 方法
    public boolean isEnabled() { return enable.getValue(); }
    public float getScale() {
        float s = (float) scale.getValue();
        return s <= 0.001f ? 0.1f : s;
    }
    public int getX() { return (int) x.getValue(); }
    public int getY() { return (int) y.getValue(); }
    public boolean isShowCount() { return showCount.getValue(); }
    public boolean isBorderEnable() { return borderEnable.getValue(); }
    public Color getBorderColor() { return borderColor.getValue(); }
    public int getBorderPadding() { return (int) borderPadding.getValue(); }
}