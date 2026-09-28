package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.AutoMend;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class ArmorPreserve extends Module {
    private final SliderSetting threshold;

    public ArmorPreserve() {
        super("ArmorPreserve", Module.Category.Player);
        this.threshold = add(new SliderSetting("Threshold", 10, 1, 100));
        setChinese("脱甲修甲");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (AutoMend.INSTANCE.isOn()) {
            equipFromCrafting();
        } else {
            checkAndUnequip();
        }
    }

    private void checkAndUnequip() {
        // 盔甲槽位索引：5=头，6=胸，7=腿，8=脚
        for (int i = 5; i <= 8; i++) {
            ItemStack stack = ((Slot) mc.player.playerScreenHandler.slots.get(i)).getStack();
            if (!stack.isEmpty()) {
                boolean shouldProtect = false;
                if (stack.getItem() instanceof ArmorItem) {
                    int durability = EntityUtil.getDamagePercent(stack);
                    if (durability <= this.threshold.getValueInt()) {
                        shouldProtect = true;
                    }
                }
                if (shouldProtect) {
                    moveToCrafting(i);
                }
            }
        }
    }

    /**
     * 将指定盔甲槽位的物品移动到合成区或背包空位
     * @param armorSlot 盔甲槽索引 (5-8)
     */
    private void moveToCrafting(int armorSlot) {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        ClientPlayerInteractionManager interactionManager = mc.interactionManager;
        int syncId = handler.syncId;

        // 优先尝试移动到合成区槽位 (1-4)
        for (int i = 1; i <= 4; i++) {
            if (handler.slots.get(i).getStack().isEmpty()) {
                // 将盔甲快速移动到合成槽，再将合成槽的物品快速移动到原盔甲槽（实际上会交换）
                // 这里两次点击实现：先拿起盔甲，再放到空槽；原逻辑是两次QUICK_MOVE，可能利用服务端处理
                interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return;
            }
        }

        // 如果没有空闲合成槽，则尝试放入背包 (9-40)
        // 这里有一个奇怪的 if(0==0) 条件，原代码如此，可能是混淆遗留，实际总是执行
        if (0 == 0) {
            for (int i = 9; i <= 40; i++) {
                if (handler.slots.get(i).getStack().isEmpty()) {
                    interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
    }

    /**
     * 当自动修复模块开启时，将合成区中可装备的盔甲穿到身上
     */
    private void equipFromCrafting() {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        ClientPlayerInteractionManager interactionManager = mc.interactionManager;
        int syncId = handler.syncId;

        for (int i = 1; i <= 4; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            int targetSlot = -1;
            if (stack.getItem() instanceof ArmorItem) {
                ArmorItem armor = (ArmorItem) stack.getItem();
                EquipmentSlot slotType = armor.getSlotType();
                switch (slotType) {
                    case HEAD:
                        targetSlot = 5;
                        break;
                    case CHEST:
                        targetSlot = 6;
                        break;
                    case LEGS:
                        targetSlot = 7;
                        break;
                    case FEET:
                        targetSlot = 8;
                        break;
                }
            }

            if (targetSlot != -1) {
                ItemStack targetStack = handler.slots.get(targetSlot).getStack();
                // 如果目标槽为空，或者目标槽是胸甲且当前物品是鞘翅（允许替换？），则进行穿戴
                if (targetStack.isEmpty() || (targetSlot == 6 && (targetStack.getItem() instanceof ElytraItem))) {
                    if (!targetStack.isEmpty()) {
                        moveElytraToInventory(); // 将鞘翅移走，腾出胸甲槽
                    }
                    // 将合成区的盔甲快速移动到目标槽，同时可能将目标槽物品移回合成区
                    interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    interactionManager.clickSlot(syncId, targetSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
    }

    /**
     * 将胸甲槽（索引6）的鞘翅移动到背包空位
     */
    private void moveElytraToInventory() {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        ClientPlayerInteractionManager interactionManager = mc.interactionManager;
        int syncId = handler.syncId;

        for (int i = 9; i <= 40; i++) {
            if (handler.slots.get(i).getStack().isEmpty()) {
                interactionManager.clickSlot(syncId, 6, 0, SlotActionType.QUICK_MOVE, mc.player);
                interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return;
            }
        }
    }
}