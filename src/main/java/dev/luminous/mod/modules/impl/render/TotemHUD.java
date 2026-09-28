package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * TotemHUD - 不死图腾计数器 HUD 模块
 */
public class TotemHUD extends Module {
    private final SliderSetting x = this.add(new SliderSetting("X", 1, 0, 100, 1));
    private final SliderSetting y = this.add(new SliderSetting("Y", 1, 0, 100, 1));
    private final ItemStack totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);

    public TotemHUD() {
        super("TotemHUD", Module.Category.Render);
        this.setChinese("图腾显示");
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;

        int totemCount = getTotemCount();
        if (totemCount > 0) {
            totemStack.setCount(totemCount);
            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();
            int drawX = (int) (screenWidth * (x.getValue() / 100.0));
            int drawY = (int) (screenHeight * (y.getValue() / 100.0));
            drawTotem(event.drawContext, drawX, drawY);
        }
    }

    private int getTotemCount() {
        int count = 0;

        // 主手
        if (mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            count += mc.player.getMainHandStack().getCount();
        }

        // 副手
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            count += mc.player.getOffHandStack().getCount();
        }

        // 背包（不包括快捷栏）
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                count += stack.getCount();
            }
        }

        // 快捷栏
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private void drawTotem(DrawContext context, int x, int y) {
        context.drawItem(totemStack, x, y);
        context.drawItemInSlot(mc.textRenderer, totemStack, x, y);
    }
}