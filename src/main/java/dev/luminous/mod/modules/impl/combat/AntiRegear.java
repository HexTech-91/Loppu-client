package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PlaceBlockEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.misc.AutoRegear;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntiRegear extends Module {
    public static AntiRegear INSTANCE;
    public final List<BlockPos> safe = new ArrayList<>();

    public enum Mode { Mine, Drop, MineAndDrop }

    // ---------- 模式选择 ----------
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Mine));

    // ---------- 保护相关设置 ----------
    private final SliderSetting safeRange = this.add(new SliderSetting("SafeRange", 2.0, 0.0, 8.0, 0.1));
    private final SliderSetting detectRange = this.add(new SliderSetting("DetectRange", 5.0, 0.0, 8.0, 0.1));  // 发现范围
    private final SliderSetting takeRange = this.add(new SliderSetting("TakeRange", 5.0, 0.0, 8.0, 0.1));       // 操控范围
    private final BooleanSetting checkSelf = this.add(new BooleanSetting("CheckSelf", true));
    private final SliderSetting safeSize = this.add(new SliderSetting("SafeSize", 1, 1, 5, 1, this.checkSelf::getValue));

    // ---------- 丢弃相关设置（Drop / MineAndDrop 可见）----------
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.mode.getValue() == Mode.Drop || this.mode.getValue() == Mode.MineAndDrop));
    private final SliderSetting dropDelay = this.add(new SliderSetting("DropDelay", 0, 0, 500, 10, () -> (this.mode.getValue() == Mode.Drop || this.mode.getValue() == Mode.MineAndDrop) && !this.instantDrop.getValue()));
    private final BooleanSetting instantDrop = this.add(new BooleanSetting("InstantDrop", false, () -> this.mode.getValue() == Mode.Drop || this.mode.getValue() == Mode.MineAndDrop));
    private final SliderSetting timeOut = this.add(new SliderSetting("TimeOut", 1000, 0, 5000, 100, () -> this.mode.getValue() == Mode.Drop || this.mode.getValue() == Mode.MineAndDrop).setSuffix("ms"));

    // ---------- 状态变量 ----------
    private BlockPos dropOpenPos = null;
    private boolean dropOpened = false;
    private final Timer dropTimer = new Timer();
    private final Timer openTimer = new Timer();
    private final Set<BlockPos> processed = new HashSet<>();        // 已完全处理的盒子
    private BlockPos pendingMinePos = null;                          // 等待挖掘的盒子（Drop 超时 / MineAndDrop 丢弃完成）

    public AntiRegear() {
        super("AntiRegear", Module.Category.Combat);
        this.setChinese("反补给");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        dropOpenPos = null;
        dropOpened = false;
        pendingMinePos = null;
        dropTimer.reset();
        openTimer.reset();
    }

    @Override
    public void onDisable() {
        dropOpenPos = null;
        dropOpened = false;
        pendingMinePos = null;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 清理所有临时列表中的无效坐标
        safe.removeIf(pos -> !(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        trimSafeList();
        processed.removeIf(pos -> !(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));

        // 清理待挖掘的无效坐标
        if (pendingMinePos != null && !(mc.world.getBlockState(pendingMinePos).getBlock() instanceof ShulkerBoxBlock)) {
            processed.add(pendingMinePos);
            pendingMinePos = null;
        }

        // 统一处理待挖掘队列（适用于 Drop 超时 / MineAndDrop 丢弃完成）
        if (pendingMinePos != null && mode.getValue() != Mode.Mine) {
            handlePendingMine();
        }

        // 根据模式分发
        switch (mode.getValue()) {
            case Mine -> handleMine();
            case Drop -> handleDrop();
            case MineAndDrop -> handleMineAndDrop();
        }
    }

    // ==================== 统一挖掘待处理盒子 ====================
    private void handlePendingMine() {
        if (pendingMinePos == null) return;
        if (!(mc.world.getBlockState(pendingMinePos).getBlock() instanceof ShulkerBoxBlock)) {
            processed.add(pendingMinePos);
            pendingMinePos = null;
            return;
        }

        double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pendingMinePos.toCenterPos()));
        if (dist > takeRange.getValue()) {
            return; // 超出操控距离，不执行
        }

        // 如果已有挖掘目标且不是当前盒子，不重复下达指令（PacketMine 会处理冲突）
        BlockPos currentMine = PacketMine.getBreakPos();
        if (currentMine == null || !currentMine.equals(pendingMinePos)) {
            PacketMine.INSTANCE.mine(pendingMinePos);
        }
    }

    // ==================== 挖掘模式 ====================
    private void handleMine() {
        if (PacketMine.getBreakPos() == null || !(mc.world.getBlockState(PacketMine.getBreakPos()).getBlock() instanceof ShulkerBoxBlock)) {
            ShulkerBoxBlockEntity entity = getBlock();
            if (entity != null) {
                double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(entity.getPos().toCenterPos()));
                if (dist <= takeRange.getValue()) {
                    PacketMine.INSTANCE.mine(entity.getPos());
                }
            }
        }
    }

    // ==================== 丢弃模式 ====================
    private void handleDrop() {
        // 校验当前打开目标
        if (dropOpenPos != null && !(mc.world.getBlockState(dropOpenPos).getBlock() instanceof ShulkerBoxBlock)) {
            dropOpenPos = null;
            dropOpened = false;
        }

        if (mc.currentScreen instanceof ShulkerBoxScreen && dropOpenPos != null) {
            dropOpened = true;
            if (instantDrop.getValue()) {
                instantDropAll();
            } else {
                gradualDrop();
            }
        } else {
            // 不在 GUI 中
            if (dropOpened) {
                dropOpened = false;
                dropOpenPos = null;
            }

            // 如果有一个等待打开的盒子，检查超时
            if (dropOpenPos != null) {
                if (openTimer.passedMs(timeOut.getValueInt())) {
                    // 超时未打开 → 直接转入挖掘队列
                    pendingMinePos = dropOpenPos;
                    dropOpenPos = null;
                }
            } else {
                // 寻找下一个盒子
                ShulkerBoxBlockEntity entity = getBlock();
                if (entity != null) {
                    openBox(entity.getPos());
                }
            }
        }
    }

    // ==================== 挖掘 + 丢弃组合模式 ====================
    private void handleMineAndDrop() {
        // 如果有等待挖掘的盒子，优先处理挖掘（已在 onUpdate 中统一处理，这里仅作防护）
        if (pendingMinePos != null) {
            return;
        }

        // 校验当前丢弃目标
        if (dropOpenPos != null && !(mc.world.getBlockState(dropOpenPos).getBlock() instanceof ShulkerBoxBlock)) {
            dropOpenPos = null;
            dropOpened = false;
        }

        if (mc.currentScreen instanceof ShulkerBoxScreen && dropOpenPos != null) {
            dropOpened = true;
            if (instantDrop.getValue()) {
                instantDropAll();
            } else {
                gradualDrop();
            }
        } else {
            // 不在 GUI 中
            if (dropOpened) {
                dropOpened = false;
                dropOpenPos = null;
            }

            if (dropOpenPos != null) {
                if (openTimer.passedMs(timeOut.getValueInt())) {
                    // 超时 → 直接挖
                    pendingMinePos = dropOpenPos;
                    dropOpenPos = null;
                }
            } else {
                ShulkerBoxBlockEntity entity = getBlock();
                if (entity != null) {
                    openBox(entity.getPos());
                }
            }
        }
    }

    // ==================== 丢弃辅助方法 ====================
    private void instantDropAll() {
        ShulkerBoxScreenHandler handler = (ShulkerBoxScreenHandler) mc.player.currentScreenHandler;
        for (Slot slot : handler.slots) {
            if (slot.id >= 27) break;
            if (!slot.getStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
            }
        }
        mc.player.closeHandledScreen();
        onBoxEmptied();
    }

    private void gradualDrop() {
        if (dropTimer.passedMs(dropDelay.getValueInt())) {
            ShulkerBoxScreenHandler handler = (ShulkerBoxScreenHandler) mc.player.currentScreenHandler;
            boolean anyDropped = false;
            for (Slot slot : handler.slots) {
                if (slot.id >= 27) break;
                if (!slot.getStack().isEmpty()) {
                    mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
                    anyDropped = true;
                    break;
                }
            }
            if (!anyDropped) {
                mc.player.closeHandledScreen();
                onBoxEmptied();
            }
            dropTimer.reset();
        }
    }

    /**
     * 当一个盒子被完全清空并关闭界面后调用
     */
    private void onBoxEmptied() {
        BlockPos boxPos = dropOpenPos;
        dropOpenPos = null;
        dropOpened = false;

        if (boxPos == null) return;

        if (mode.getValue() == Mode.MineAndDrop) {
            // 组合模式：交给挖掘队列
            pendingMinePos = boxPos;
        } else {
            // 纯丢弃模式：直接标记为已处理
            processed.add(boxPos);
        }
    }

    private void openBox(BlockPos pos) {
        double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
        if (dist > takeRange.getValue()) {
            return;
        }
        BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), rotate.getValue());
        dropOpenPos = pos;
        dropTimer.reset();
        openTimer.reset();
    }

    // ==================== 辅助方法 ====================
    @EventListener
    public void onPlace(PlaceBlockEvent event) {
        if (event.block instanceof ShulkerBoxBlock) {
            safe.add(event.blockPos);
            trimSafeList();
        }
    }

    private void trimSafeList() {
        int max = (int) safeSize.getValue();
        while (safe.size() > max) {
            safe.remove(0);
        }
    }

    /**
     * 获取一个符合条件的潜影盒实体（使用 DetectRange 发现）
     */
    private ShulkerBoxBlockEntity getBlock() {
        for (BlockEntity entity : BlockUtil.getTileEntities()) {
            if (entity instanceof ShulkerBoxBlockEntity shulker) {
                BlockPos pos = shulker.getPos();
                double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
                if (dist <= this.safeRange.getValue()) continue;
                if (dist > this.detectRange.getValue()) continue;  // 使用检测距离

                if (this.checkSelf.getValue()) {
                    if (this.safe.contains(pos)) continue;
                    if (AutoRegear.INSTANCE != null && pos.equals(AutoRegear.INSTANCE.placePos)) continue;
                }

                // 排除已处理、待挖掘、当前打开的盒子
                if (processed.contains(pos)) continue;
                if (pendingMinePos != null && pendingMinePos.equals(pos)) continue;
                if (dropOpenPos != null && dropOpenPos.equals(pos)) continue;

                return shulker;
            }
        }
        return null;
    }
}