package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.Module.Category;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

/**
 * InteractTweaks 模块
 * 功能：交互调整 + 快速使用物品
 * 
 * 原有功能：
 * - NoEntityTrace（穿透实体挖掘）
 * - MultiTask（多任务处理）
 * - Respawn（自动重生）
 * - GhostHand（幽灵手）
 * - SwitchEat（切换吃金苹果）
 * - Reach调整（范围调整）
 * 
 * 新增功能（来自 FastUse）：
 * - MultiUse（多次使用物品）
 * - FastUseDelay（使用延迟控制）
 * - FastBow（弓箭快速释放）
 * - 物品类型过滤（方块、经验瓶、水晶、烟花、药水）
 */
public class InteractTweaks extends Module {
    public static InteractTweaks INSTANCE;
    
    // ========== 原有设置项 ==========
    public final BooleanSetting noEntityTrace = this.add(new BooleanSetting("NoEntityTrace", true).setParent());
    public final BooleanSetting onlyPickaxe = this.add(new BooleanSetting("OnlyPickaxe", true, this.noEntityTrace::isOpen));
    public final BooleanSetting multiTask = this.add(new BooleanSetting("MultiTask", true));
    public final BooleanSetting respawn = this.add(new BooleanSetting("Respawn", true));
    public final BooleanSetting ghostHand = this.add(new BooleanSetting("IgnoreBedrock", false));
    private final BooleanSetting noAbort = this.add(new BooleanSetting("NoMineAbort", false));
    private final BooleanSetting noReset = this.add(new BooleanSetting("NoMineReset", false));
    private final BooleanSetting noDelay = this.add(new BooleanSetting("NoMineDelay", false));
    private final BooleanSetting noInteract = this.add(new BooleanSetting("NoInteract", false));
    private final BooleanSetting pickaxeSwitch = this.add(new BooleanSetting("SwitchEat", false).setParent());
    private final BooleanSetting allowSword = this.add(new BooleanSetting("Sword", true, this.pickaxeSwitch::isOpen));
    private final BooleanSetting allowPickaxe = this.add(new BooleanSetting("Pickaxe", true, this.pickaxeSwitch::isOpen));
    private final BooleanSetting allowCrystal = this.add(new BooleanSetting("Crystal", false, this.pickaxeSwitch::isOpen));
    private final BooleanSetting allowMace = this.add(new BooleanSetting("Mace", false, this.pickaxeSwitch::isOpen));
    private final BooleanSetting reach = this.add(new BooleanSetting("Reach", false));
    public final SliderSetting blockRange = this.add(new SliderSetting("BlockRange", 5.0, 0.0, 15.0, 0.1, this.reach::getValue));
    public final SliderSetting entityRange = this.add(new SliderSetting("EntityRange", 5.0, 0.0, 15.0, 0.1, this.reach::getValue));
    private final SliderSetting delay = this.add(new SliderSetting("UseDelay", 4.0, 0.0, 4.0, 1.0));
    
    // ========== 新增：FastUse 功能设置 ==========
    private final BooleanSetting fastUse = this.add(new BooleanSetting("FastUse", false).setParent());
    private final SliderSetting multiUse = this.add(new SliderSetting("MultiUse", 1.0, 1.0, 10.0, 1.0, this.fastUse::isOpen));
    private final SliderSetting fastUseDelay = this.add(new SliderSetting("FastUseDelay", 0.0, 0.0, 10.0, 1.0, this.fastUse::isOpen));
    
    // 物品类型过滤设置
    private final BooleanSetting fastBlocks = this.add(new BooleanSetting("FastBlocks", false, this.fastUse::isOpen));
    private final BooleanSetting fastAllItems = this.add(new BooleanSetting("FastAllItems", false, this.fastUse::isOpen));
    private final BooleanSetting fastExpBottles = this.add(new BooleanSetting("FastExpBottles", true, this.fastUse::isOpen));
    private final BooleanSetting fastEndCrystals = this.add(new BooleanSetting("FastEndCrystals", true, this.fastUse::isOpen));
    private final BooleanSetting fastFireworks = this.add(new BooleanSetting("FastFireworks", false, this.fastUse::isOpen));
    private final BooleanSetting fastPotions = this.add(new BooleanSetting("FastPotions", false, this.fastUse::isOpen));
    
    // 弓箭快速释放
    private final BooleanSetting fastBow = this.add(new BooleanSetting("FastBow", true, this.fastUse::isOpen));
    private final SliderSetting bowCharge = this.add(new SliderSetting("BowCharge", 3.0, 0.0, 20.0, 1.0, this.fastUse::isOpen));
    
    // ========== 状态变量 ==========
    public boolean isActive;
    boolean swapped = false;
    int lastSlot = 0;
    private Hand currentHand = Hand.MAIN_HAND; // 追踪当前使用的手
    
    public InteractTweaks() {
        super("InteractTweaks", Module.Category.Player);
        this.setChinese("交互调整");
        INSTANCE = this;
    }
    
    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        
        // 原有功能：自动重生
        if (this.respawn.getValue() && mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
        
        // 原有功能：SwitchEat（切换吃金苹果）
        handleSwitchEat();
        
        // 新增功能：FastUse（快速使用物品，会覆盖原有的 UseDelay）
        handleFastUse();
        
        // 原有功能：使用延迟控制（仅在 FastUse 未生效时运行）
        if (!isFastUseActive()) {
            if (mc.itemUseCooldown <= 4 - this.delay.getValueInt()) {
                mc.itemUseCooldown = 0;
            }
        }
    }
    
    /**
     * 检查 FastUse 是否正在生效
     * 用于避免 UseDelay 和 FastUseDelay 的冲突
     */
    private boolean isFastUseActive() {
        if (!this.fastUse.getValue() || mc.interactionManager == null) {
            return false;
        }
        
        ItemStack currentStack = mc.player.getStackInHand(this.currentHand);
        return !currentStack.isEmpty() && shouldFastUse(currentStack);
    }
    
    // ========== Packet 事件监听 ==========
    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        
        // 原有功能：NoInteract（阻止交互）
        if (this.noInteract.getValue() && event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
            Block block = mc.world.getBlockState(packet.getBlockHitResult().getBlockPos()).getBlock();
            if (!mc.player.isSneaking() && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AnvilBlock)) {
                event.cancel();
            }
        }
        
        // 新增功能：追踪当前使用的 Hand
        if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            this.currentHand = packet.getHand();
        } else if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
            this.currentHand = packet.getHand();
        }
    }
    
    // ========== SwitchEat 功能实现 ==========
    private void handleSwitchEat() {
        if (!this.pickaxeSwitch.getValue()) return;
        
        // 检查当前手持物品是否符合切换条件
        if ((!(mc.player.getMainHandStack().getItem() instanceof PickaxeItem) || !this.allowPickaxe.getValue())
                && (!(mc.player.getMainHandStack().getItem() instanceof SwordItem) || !this.allowSword.getValue())
                && (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL || !this.allowCrystal.getValue())
                && (mc.player.getMainHandStack().getItem() != Items.MACE || !this.allowMace.getValue())
                && mc.player.getMainHandStack().getItem() != Items.ENCHANTED_GOLDEN_APPLE
                && mc.player.getMainHandStack().getItem() != Items.GOLDEN_APPLE) {
            this.swapped = false;
            return;
        }
        
        // 查找金苹果
        int gapple = InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
        if (gapple == -1) {
            gapple = InventoryUtil.findItem(Items.GOLDEN_APPLE);
        }
        
        if (gapple == -1) {
            if (this.swapped) {
                InventoryUtil.switchToSlot(this.lastSlot);
                this.swapped = false;
            }
            return;
        }
        
        // 执行切换逻辑
        if (mc.options.useKey.isPressed()) {
            if ((mc.player.getMainHandStack().getItem() instanceof PickaxeItem && this.allowPickaxe.getValue()
                    || mc.player.getMainHandStack().getItem() instanceof SwordItem && this.allowSword.getValue()
                    || mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL && this.allowCrystal.getValue()
                    || mc.player.getMainHandStack().getItem() == Items.MACE && this.allowMace.getValue())
                    && mc.player.getOffHandStack().getItem() != Items.ENCHANTED_GOLDEN_APPLE
                    && mc.player.getMainHandStack().getItem() != Items.GOLDEN_APPLE) {
                this.lastSlot = mc.player.getInventory().selectedSlot;
                InventoryUtil.switchToSlot(gapple);
                this.swapped = true;
            }
        } else if (this.swapped) {
            InventoryUtil.switchToSlot(this.lastSlot);
            this.swapped = false;
        }
    }
    
    // ========== FastUse 功能实现 ==========
    private void handleFastUse() {
        if (!this.fastUse.getValue() || mc.interactionManager == null) return;
        
        ItemStack currentStack = mc.player.getStackInHand(this.currentHand);
        
        // 检查物品是否符合加速条件
        if (currentStack.isEmpty() || !shouldFastUse(currentStack)) {
            return;
        }
        
        // 设置使用延迟
        mc.itemUseCooldown = this.fastUseDelay.getValueInt();
        
        // 弓箭快速释放
        if (this.fastBow.getValue() 
                && currentStack.getItem() instanceof BowItem 
                && mc.player.isUsingItem() 
                && mc.player.getItemUseTime() >= this.bowCharge.getValueInt()) {
            mc.interactionManager.stopUsingItem(mc.player);
            mc.player.clearActiveItem();
            return;
        }
        
        // MultiUse：多次使用物品
        if (!mc.options.useKey.isPressed() || this.multiUse.getValueInt() <= 1) {
            return;
        }
        
        // 循环使用物品（核心逻辑）
        for (int i = 1; i < this.multiUse.getValueInt(); i++) {
            mc.interactionManager.interactItem(mc.player, this.currentHand);
        }
    }
    
    // ========== 物品类型判断（FastUse 核心） ==========
    private boolean shouldFastUse(ItemStack stack) {
        // AllItems 模式：对所有物品生效（除了方块需要额外检查）
        if (this.fastAllItems.getValue()) {
            if (!(stack.getItem() instanceof BlockItem)) return true;
            return this.fastBlocks.getValue();
        }
        
        // 方块
        if (this.fastBlocks.getValue() && stack.getItem() instanceof BlockItem) {
            return true;
        }
        
        // 经验瓶
        if (this.fastExpBottles.getValue() && stack.getItem() instanceof ExperienceBottleItem) {
            return true;
        }
        
        // 末地水晶
        if (this.fastEndCrystals.getValue() && stack.getItem() instanceof EndCrystalItem) {
            return true;
        }
        
        // 烟花
        if (this.fastFireworks.getValue() && stack.getItem() instanceof FireworkRocketItem) {
            return true;
        }
        
        // 投掷药水
        if (this.fastPotions.getValue() && stack.getItem() instanceof ThrowablePotionItem) {
            return true;
        }
        
        // 弓箭
        if (this.fastBow.getValue() && stack.getItem() instanceof BowItem) {
            return true;
        }
        
        return false;
    }
    
    // ========== 公共接口方法 ==========
    @Override
    public void onDisable() {
        this.isActive = false;
    }
    
    public boolean reach() {
        return this.isOn() && this.reach.getValue();
    }
    
    public boolean noAbort() {
        return this.isOn() && this.noAbort.getValue() && !mc.options.useKey.isPressed();
    }
    
    public boolean noReset() {
        return this.isOn() && this.noReset.getValue();
    }
    
    public boolean noDelay() {
        return this.isOn() && this.noDelay.getValue();
    }
    
    public boolean multiTask() {
        return this.isOn() && this.multiTask.getValue();
    }
    
    public boolean noEntityTrace() {
        if (this.isOff() || !this.noEntityTrace.getValue()) {
            return false;
        } else {
            return !this.onlyPickaxe.getValue()
                    ? true
                    : mc.player.getMainHandStack().getItem() instanceof PickaxeItem 
                        || mc.player.isUsingItem() && !(mc.player.getMainHandStack().getItem() instanceof SwordItem);
        }
    }
    
    public boolean ghostHand() {
        return this.isOn() && this.ghostHand.getValue() && !mc.options.useKey.isPressed() && !mc.options.sneakKey.isPressed();
    }
}