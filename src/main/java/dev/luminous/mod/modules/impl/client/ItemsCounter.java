package dev.luminous.mod.modules.impl.client;

import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ItemsCounter extends Module {
    public static ItemsCounter INSTANCE;
    private final BooleanSetting hideEmpty = this.add(new BooleanSetting("HideEmpty", true));
    private final BooleanSetting crystal = this.add(new BooleanSetting("Crystal", true));
    private final BooleanSetting xp = this.add(new BooleanSetting("XP", true));
    private final BooleanSetting pearl = this.add(new BooleanSetting("Pearl", true));
    private final BooleanSetting obsidian = this.add(new BooleanSetting("Obsidian", true));
    private final BooleanSetting egApple = this.add(new BooleanSetting("E-GApple", true));
    private final BooleanSetting gApple = this.add(new BooleanSetting("GApple", true));
    private final BooleanSetting totem = this.add(new BooleanSetting("Totem", true));
    private final BooleanSetting web = this.add(new BooleanSetting("Web", true));
    private final BooleanSetting anchor = this.add(new BooleanSetting("Anchor", true));
    private final BooleanSetting glowstone = this.add(new BooleanSetting("Glowstone", true));
    private final BooleanSetting piston = this.add(new BooleanSetting("Piston", true));
    private final BooleanSetting redstone = this.add(new BooleanSetting("RedStone", true));
    private final BooleanSetting enderChest = this.add(new BooleanSetting("EnderChest", true));
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", true));
    private final BooleanSetting windCharge = this.add(new BooleanSetting("WindCharge", true));
    private final SliderSetting xOffset = this.add(new SliderSetting("X", 10, 0, 100));
    private final SliderSetting yOffset = this.add(new SliderSetting("Y", 10, 0, 100));
    private final SliderSetting offset = this.add(new SliderSetting("Offset", 18, 0, 30));

    public ItemsCounter() {
        super("Items", Module.Category.Client);
        this.setChinese("物品数量");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext drawContext, float tickDelta) {
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int baseX = (int) (screenWidth * (xOffset.getValue() / 100.0));
        int baseY = (int) (screenHeight * (yOffset.getValue() / 100.0));

        // 收集所有需要绘制的物品栈
        List<ItemStack> drawList = new ArrayList<>();

        if (crystal.getValue())      tryAddItem(drawList, Items.END_CRYSTAL);
        if (xp.getValue())           tryAddItem(drawList, Items.EXPERIENCE_BOTTLE);
        if (pearl.getValue())        tryAddItem(drawList, Items.ENDER_PEARL);
        if (obsidian.getValue())     tryAddItem(drawList, Items.OBSIDIAN);
        if (egApple.getValue())      tryAddItem(drawList, Items.ENCHANTED_GOLDEN_APPLE);
        if (gApple.getValue())       tryAddItem(drawList, Items.GOLDEN_APPLE);
        if (totem.getValue())        tryAddItem(drawList, Items.TOTEM_OF_UNDYING);
        if (web.getValue())          tryAddItem(drawList, Items.COBWEB);
        if (anchor.getValue())       tryAddItem(drawList, Items.RESPAWN_ANCHOR);
        if (glowstone.getValue())    tryAddItem(drawList, Items.GLOWSTONE);
        if (piston.getValue())       tryAddPiston(drawList);
        if (redstone.getValue())     tryAddItem(drawList, Items.REDSTONE_BLOCK);
        if (enderChest.getValue())   tryAddItem(drawList, Items.ENDER_CHEST);
        if (firework.getValue())     tryAddItem(drawList, Items.FIREWORK_ROCKET);
        if (windCharge.getValue())   tryAddItem(drawList, Items.WIND_CHARGE);

        int count = drawList.size();
        if (count == 0) return;

        int step = offset.getValueInt();
        int totalWidth = count * step;
        int startX = baseX - totalWidth / 2;
        int y = baseY;

        int x = startX;
        for (ItemStack stack : drawList) {
            drawContext.drawItem(stack, x, y);
            drawContext.drawItemInSlot(mc.textRenderer, stack, x, y);
            x += step;
        }
    }

    private void tryAddItem(List<ItemStack> list, Item item) {
        int amount = InventoryUtil.getItemCount(item);
        if (amount > 0 || !hideEmpty.getValue()) {
            list.add(new ItemStack(item, amount));
        }
    }

    private void tryAddPiston(List<ItemStack> list) {
        int amount = InventoryUtil.getItemCount(PistonBlock.class);
        if (amount > 0 || !hideEmpty.getValue()) {
            list.add(new ItemStack(Items.PISTON, amount));
        }
    }
}