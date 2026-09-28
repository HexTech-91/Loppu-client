package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AutoDick extends Module {
    public static AutoDick INSTANCE;

    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 50, 0, 500));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting inventorySwap = this.add(new BooleanSetting("InventorySwap", true));

    // 建筑方案：相对于原点的 [前移, 上移]
    private static final int[][] BUILD_PLAN = {
            {2, 0},   // ② 前端
            {1, 1},   // ③ 中部
            {1, 2}    // ④ 顶端
    };

    // 建筑方块优先级
    private static final Block[] BUILD_BLOCKS = {
            Blocks.OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.REDSTONE_BLOCK,
            Blocks.PISTON,
            Blocks.GLOWSTONE,
            Blocks.ENDER_CHEST
    };

    private BlockPos origin;
    private int buildIndex;
    private final Timer timer = new Timer();
    private Vec3d forwardDir;

    public AutoDick() {
        super("AutoDick", Module.Category.Combat);
        this.setChinese("自动迪克");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        origin = null;
        buildIndex = 0;
        forwardDir = null;
    }

    @Override
    public void onDisable() {
        origin = null;
        buildIndex = 0;
        forwardDir = null;
    }

    @Override
    public String getInfo() {
        if (origin == null) return "等待放置";
        if (buildIndex < BUILD_PLAN.length) return "建造中(" + (buildIndex + 1) + "/" + BUILD_PLAN.length + ")";
        return "完成";
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (origin != null) return;
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerInteractBlockC2SPacket packet)) return;

        BlockPos placedPos = BlockUtil.allowAirPlace()
                ? packet.getBlockHitResult().getBlockPos()
                : packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());

        if (!mc.world.isAir(placedPos) && !BlockUtil.canReplace(placedPos)) return;

        // 记录原点并计算玩家面向方向
        origin = placedPos;
        float yawRad = mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        forwardDir = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad)).normalize();
        buildIndex = 0;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (event.isPost()) return;
        if (origin == null || forwardDir == null) return;
        if (buildIndex >= BUILD_PLAN.length) return;
        if (!timer.passedMs(placeDelay.getValueInt())) return;

        int[] plan = BUILD_PLAN[buildIndex];
        // 计算目标世界坐标：origin + forward * plan[0] + up * plan[1]
        BlockPos targetPos = new BlockPos(
                MathHelper.floor(origin.getX() + forwardDir.x * plan[0] + 0.5),
                origin.getY() + plan[1],
                MathHelper.floor(origin.getZ() + forwardDir.z * plan[0] + 0.5)
        );

        // 位置已被占用则跳过
        if (!mc.world.isAir(targetPos) && !BlockUtil.canReplace(targetPos)) {
            buildIndex++;
            timer.reset();
            return;
        }

        // 查找可用建筑方块
        int slot = getBuildingSlot();
        if (slot == -1) {
            timer.reset();
            return;
        }

        // 尝试放置
        if (placeBlockSafe(targetPos, slot)) {
            buildIndex++;
            timer.reset();
        }
    }

    /**
     * 安全放置方块，自动避开末影箱作为支撑点（避免意外打开UI）
     */
    private boolean placeBlockSafe(BlockPos pos, int slot) {
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) {
            // 标准放置失败，尝试虚空放置
            if (BlockUtil.allowAirPlace()) {
                doSwap(slot);
                BlockUtil.airPlace(pos, rotate.getValue(), Hand.MAIN_HAND, true);
                if (inventorySwap.getValue()) {
                    doSwap(slot);
                    EntityUtil.syncInventory();
                }
                return true;
            }
            return false;
        }

        BlockPos supportPos = pos.offset(side);
        if (mc.world.getBlockState(supportPos).getBlock() instanceof EnderChestBlock) {
            // 支撑点是末影箱，寻找替代方向
            Direction altSide = findNonEnderChestSide(pos);
            if (altSide != null) {
                side = altSide;
                supportPos = pos.offset(side);
            } else if (BlockUtil.allowAirPlace()) {
                // 全部方向都是末影箱，虚空放置
                doSwap(slot);
                BlockUtil.airPlace(pos, rotate.getValue());
                if (inventorySwap.getValue()) {
                    doSwap(slot);
                    EntityUtil.syncInventory();
                }
                return true;
            } else {
                return false;
            }
        }

        // 标准放置
        int old = mc.player.getInventory().selectedSlot;
        doSwap(slot);
        BlockUtil.placeBlock(pos, rotate.getValue());
        if (inventorySwap.getValue()) {
            doSwap(slot);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
        return true;
    }

    /**
     * 寻找不涉及末影箱的支撑面方向
     */
    private Direction findNonEnderChestSide(BlockPos pos) {
        double minDist = Double.MAX_VALUE;
        Direction best = null;
        for (Direction dir : Direction.values()) {
            BlockPos supportPos = pos.offset(dir);
            if (!BlockUtil.canClick(supportPos)) continue;
            Block block = mc.world.getBlockState(supportPos).getBlock();
            if (block instanceof EnderChestBlock) continue;
            double dist = mc.player.getEyePos().squaredDistanceTo(supportPos.toCenterPos());
            if (dist < minDist) {
                minDist = dist;
                best = dir;
            }
        }
        return best;
    }

    /**
     * 按优先级查找背包中的建筑方块
     */
    private int getBuildingSlot() {
        for (Block block : BUILD_BLOCKS) {
            int slot = inventorySwap.getValue()
                    ? InventoryUtil.findBlockInventorySlot(block)
                    : InventoryUtil.findBlock(block);
            if (slot != -1) return slot;
        }
        return -1;
    }

    private void doSwap(int slot) {
        if (inventorySwap.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }
}