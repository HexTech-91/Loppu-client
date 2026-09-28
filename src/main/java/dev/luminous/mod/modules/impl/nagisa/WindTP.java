package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.TPUtils;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class WindTP extends Module {
    public static WindTP INSTANCE;

    private final SliderSetting tpDistance = add(new SliderSetting("Distance", 15, 0, 100, 0.5).setSuffix("m"));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final BooleanSetting disableOnRubberband = add(new BooleanSetting("DisableOnRubberband", true));

    public WindTP() {
        super("WindTP", Category.Movement);
        setChinese("风弹传送（改良）");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        // 无需重置状态（无状态锁）
    }

    // ========== 冷却检测 + 投掷逻辑 ==========
    @EventListener
    public void onUpdate() {
        if (nullCheck() || mc.world == null) return;

        // ★ 加入原版冷却检测（来自 WindTP）
        if (mc.player.getItemCooldownManager().isCoolingDown(Items.WIND_CHARGE)) return;

        int windCharge = InventoryUtil.findItemInventorySlot(Items.WIND_CHARGE);
        if (windCharge == -1) return;

        // 计算投掷目标点（沿用 LongJump 的遍历方式，确保总能找到可砸的方块）
        Vec3d aimPos = getAimPosition();
        if (aimPos == null) return;

        int originalSlot = mc.player.getInventory().selectedSlot;
        Alien.ROTATION.snapAt(aimPos);

        // 直接调用发送，不加状态锁
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id,
                Alien.ROTATION.lastYaw, Alien.ROTATION.lastPitch));

        // 切回原位（如果交换了）
        InventoryUtil.inventorySwap(windCharge, originalSlot);
        // 注意：没有 armed 锁，允许连续投掷，但冷却会阻止频繁发包
    }

    // ========== 爆炸监听 + 光线追踪传送 ==========
    @EventListener
    public void onWindBurst(PacketEvent.Receive event) {
        if (nullCheck() || mc.world == null) return;
        if (!(event.getPacket() instanceof ExplosionS2CPacket packet)) return;

        // 必须是风弹爆炸，且在玩家附近
        if (packet.getSoundEvent() != SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST
                || mc.player.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) > 5.0) {
            return;
        }

        // ★ 采用 LongJump 的光线追踪落点计算（精准且防撞墙）
        Vec3d direction = mc.cameraEntity.getRotationVec(1.0f);
        RaycastContext ctx = new RaycastContext(
                mc.player.getEyePos(),
                mc.player.getEyePos().add(direction.multiply(tpDistance.getValue())),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult bhr = mc.world.raycast(ctx);
        BlockPos topY;

        if (bhr.getType() == HitResult.Type.MISS) {
            Vec3d end = mc.player.getEyePos().add(direction.multiply(tpDistance.getValue()));
            if (end.getY() <= mc.player.getY()) return; // 不传向下方
            topY = BlockPos.ofFloored(end);
        } else {
            topY = bhr.getBlockPos();
        }

        // 向下找地面
        while (topY.getY() > mc.world.getBottomY() && mc.world.isAir(topY)) {
            topY = topY.down();
        }
        // 确保地面存在
        if (mc.world.isAir(topY)) return;

        double x = topY.getX() + 0.5;
        double y = topY.getY() + 1.0;
        double z = topY.getZ() + 0.5;
        Vec3d target = new Vec3d(x, y, z);

        TPUtils.newTeleport(target);
        mc.player.setPosition(target.x, target.y, target.z);

        if (autoDisable.getValue()) {
            disable();
        }
    }

    // ========== 拉回检测（来自 LongJump） ==========
    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (disableOnRubberband.getValue() && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            disable();
        }
    }

    // ========== 投掷瞄准点计算（沿用 LongJump 的遍历逻辑） ==========
    private Vec3d getAimPosition() {
        BlockPos playerPos = mc.player.getBlockPos();
        // 先检查相邻六个方向
        for (Direction dir : Direction.values()) {
            BlockPos pos = playerPos.offset(dir);
            if (!mc.world.isAir(pos) && mc.world.getBlockState(pos).isFullCube(mc.world, pos)) {
                return getClosestPointToSide(mc.player.getEyePos(), pos, dir);
            }
        }
        // 再检查对角线方向（两个方向偏移）
        for (Direction dir1 : Direction.values()) {
            for (Direction dir2 : Direction.values()) {
                BlockPos pos = playerPos.offset(dir1).offset(dir2);
                if (!mc.world.isAir(pos) && mc.world.getBlockState(pos).isFullCube(mc.world, pos)) {
                    return getClosestPointToSide(mc.player.getEyePos(), pos, dir2);
                }
            }
        }
        return null;
    }

    // ========== 辅助：点到方块表面的最近点（复制自 WindTP 同名方法） ==========
    private static Vec3d getClosestPointToSide(Vec3d eyePos, BlockPos pos, Direction side) {
        double px = eyePos.x, py = eyePos.y, pz = eyePos.z;
        double minX = pos.getX(), maxX = pos.getX() + 1;
        double minY = pos.getY(), maxY = pos.getY() + 1;
        double minZ = pos.getZ(), maxZ = pos.getZ() + 1;
        double qx, qy, qz;
        switch (side) {
            case DOWN:  qx = clamp(px, minX, maxX); qz = clamp(pz, minZ, maxZ); qy = minY; break;
            case UP:    qx = clamp(px, minX, maxX); qz = clamp(pz, minZ, maxZ); qy = maxY; break;
            case NORTH: qx = clamp(px, minX, maxX); qy = clamp(py, minY, maxY); qz = minZ; break;
            case SOUTH: qx = clamp(px, minX, maxX); qy = clamp(py, minY, maxY); qz = maxZ; break;
            case WEST:  qy = clamp(py, minY, maxY); qz = clamp(pz, minZ, maxZ); qx = minX; break;
            case EAST:  qy = clamp(py, minY, maxY); qz = clamp(pz, minZ, maxZ); qx = maxX; break;
            default: return eyePos;
        }
        return new Vec3d(qx, qy, qz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}