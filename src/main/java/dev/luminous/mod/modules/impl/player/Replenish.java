package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.Module.Category;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class Replenish extends Module {
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.QuickMove));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 2.0, 0.0, 5.0, 0.01).setSuffix("s"));
    private final SliderSetting min = this.add(new SliderSetting("Min", 50, 1, 100).setSuffix("%"));
    private final SliderSetting forceDelay = this.add(new SliderSetting("ForceDelay", 0.2, 0.0, 4.0, 0.01).setSuffix("s"));
    private final SliderSetting forceMin = this.add(new SliderSetting("ForceMin", 16, 1, 100).setSuffix("%"));
    // 新增第三重保险
    private final SliderSetting topMin = this.add(new SliderSetting("TopMin", 10, 1, 100).setSuffix("%"));
    private final SliderSetting topDelay = this.add(new SliderSetting("TopDelay", 0.0, 0.0, 4.0, 0.01).setSuffix("s"));

    private final Timer timer = new Timer();

    public Replenish() {
        super("Replenish", Category.Player);
        this.setChinese("物品栏补充");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        for (int i = 0; i < 9; i++) {
            if (this.replenish(i)) {
                this.timer.reset();
                return;
            }
        }
    }

    private boolean replenish(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) {
            return false;
        } else if (!stack.isStackable()) {
            return false;
        } else {
            int percent = (int) ((double) stack.getCount() / stack.getMaxCount() * 100.0);
            if (percent > this.min.getValue()) {
                return false;
            } else {
                for (int i = 9; i < 36; i++) {
                    ItemStack item = mc.player.getInventory().getStack(i);
                    if (!item.isEmpty() && Sorter.canMerge(stack, item)) {
                        // 三层延迟判断
                        float p = percent;
                        float forceMinVal = this.forceMin.getValueFloat();
                        float topMinVal = this.topMin.getValueFloat();

                        if (p > forceMinVal) {
                            // 第一层：常规补货（物品量在 Min 和 ForceMin 之间）
                            if (!this.timer.passedS(this.delay.getValue())) {
                                return false;
                            }
                        } else if (p > topMinVal) {
                            // 第二层：紧急补货（物品量在 ForceMin 和 TopMin 之间）
                            if (!this.timer.passedS(this.forceDelay.getValue())) {
                                return false;
                            }
                        } else {
                            // 第三层：极危补货（物品量低于 TopMin）
                            if (!this.timer.passedS(this.topDelay.getValue())) {
                                return false;
                            }
                        }

                        // 执行补货操作
                        switch (this.mode.getValue()) {
                            case QuickMove:
                                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                                break;
                            case ClickSlot:
                                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot + 36, 0, SlotActionType.PICKUP, mc.player);
                                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                                break;
                        }
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public enum Mode {
        QuickMove,
        ClickSlot
    }
}