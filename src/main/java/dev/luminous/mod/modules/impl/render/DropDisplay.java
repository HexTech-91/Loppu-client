package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.mod.modules.Module;

import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;

public class DropDisplay extends Module {
    public static DropDisplay INSTANCE;
    private final DecimalFormat df = new DecimalFormat("0.0");
    private final SliderSetting scale = this.add(new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1));
    private final SliderSetting x = this.add(new SliderSetting("X", 1, 0, 100));
    private final SliderSetting y = this.add(new SliderSetting("Y", 1, 0, 100));
    private final SliderSetting spacing = this.add(new SliderSetting("Spacing", 10, 0, 50));
    private final ColorSetting textColor = this.add(new ColorSetting("TextColor", new Color(255, 255, 255)));
    private final BooleanSetting showCount = this.add(new BooleanSetting("ShowCount", true));
    private final BooleanSetting showDistance = this.add(new BooleanSetting("ShowDistance", true));
    private final SliderSetting maxDistance = this.add(new SliderSetting("MaxDistance", 50.0, 5.0, 100.0, 1.0));
    private final BooleanSetting shadow = this.add(new BooleanSetting("Shadow", true));

    public DropDisplay() {
        super("DropDisplay", Module.Category.Render);
        this.setChinese("掉落显示");
        INSTANCE = this;
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (!nullCheck()) {
            DrawContext drawContext = event.drawContext;
            List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(this.maxDistance.getValue()), entity -> true);
            items.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));
            // 百分比定位：X/Y 为屏幕宽度/高度的百分比（与 BackPackHUD 一致）
            int currentY = (int) (mc.getWindow().getScaledHeight() * (this.y.getValue() / 100.0));
            int currentX = (int) (mc.getWindow().getScaledWidth() * (this.x.getValue() / 100.0));
            int spacing = this.spacing.getValueInt();
            float textScale = (float)this.scale.getValue();

            for (ItemEntity item : items) {
                ItemStack stack = item.getStack();
                if (!stack.isEmpty()) {
                    double distance = mc.player.distanceTo(item);
                    StringBuilder text = new StringBuilder();
                    if (this.showCount.getValue()) {
                        text.append(stack.getCount()).append("*");
                    }

                    text.append(stack.getName().getString());
                    if (this.showDistance.getValue()) {
                        text.append(" [").append(this.df.format(distance)).append("]");
                    }

                    if (text.length() != 0) {
                        int colorValue = this.textColor.getValue().getRGB();
                        String textStr = text.toString();
                        drawContext.getMatrices().push();
                        drawContext.getMatrices().scale(textScale, textScale, 1.0F);
                        drawContext.drawText(mc.textRenderer, textStr, (int)(currentX / textScale), (int)(currentY / textScale), colorValue, this.shadow.getValue());
                        drawContext.getMatrices().pop();
                        currentY += (int)(spacing * textScale);
                    }
                }
            }
        }
    }
}
