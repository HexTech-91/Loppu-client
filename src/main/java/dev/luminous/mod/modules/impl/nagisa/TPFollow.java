package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.TPUtils;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

public class TPFollow extends Module {
    public static TPFollow INSTANCE;

    private final SliderSetting targetRange;
    private final SliderSetting tpDistance;
    private final BooleanSetting inventorySwap;
    private final BooleanSetting smartThrow;
    private final BooleanSetting onlyGround;

    private final Timer timer;

    private Optional<PlayerEntity> target;
    private boolean armed;

    public TPFollow() {
        super("TPFollow", Category.Movement);
        this.targetRange = add(new SliderSetting("TargetRange", 20.0, 0.0, 200.0, 0.1).setSuffix("m"));
        this.tpDistance = add(new SliderSetting("TPDistance", 20.0, 0.0, 100.0, 0.5).setSuffix("m"));
        this.inventorySwap = add(new BooleanSetting("InventorySwap", true));
        this.smartThrow = add(new BooleanSetting("SmartThrow", true));
        this.onlyGround = add(new BooleanSetting("OnlyGround", true, () -> !smartThrow.getValue()));

        this.timer = new Timer();
        this.armed = false;
        this.target = Optional.empty();
        setChinese("TP追人");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.target = Optional.empty();
        this.armed = false;
    }

    @Override
    public void onDisable() {
        this.target = Optional.empty();
        this.armed = false;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || mc.world == null) {
            return;
        }

        // 武装锁：等待爆炸响应，超时 1.5s 自动解除
        if (this.armed) {
            if (this.timer.passedMs(1500L)) {
                this.armed = false;
            } else {
                return;
            }
        }

        // 原版风弹冷却
        if (mc.player.getItemCooldownManager().isCoolingDown(Items.WIND_CHARGE)) {
            return;
        }

        this.target = pickTarget();
        if (!this.target.isPresent()) {
            return;
        }

        int windCharge = findItem(Items.WIND_CHARGE);
        if (windCharge == -1) {
            return;
        }

        Vec3d directionVec = getGroundHitVec();
        if (directionVec == null) {
            return;
        }

        int select = mc.player.getInventory().selectedSlot;
        Alien.ROTATION.lookAt(directionVec);
        swap(windCharge, select);
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.lastYaw, Alien.ROTATION.lastPitch));
        swap(windCharge, select);

        this.armed = true;
        this.timer.reset();
    }

    @EventListener
    public void onWindBurst(PacketEvent.Receive event) {
        if (nullCheck() || mc.world == null || !this.armed) {
            return;
        }
        if (!(event.getPacket() instanceof ExplosionS2CPacket)) {
            return;
        }
        ExplosionS2CPacket packet = (ExplosionS2CPacket) event.getPacket();
        if (packet.getSoundEvent() != SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST || mc.player.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) > 5.0) {
            return;
        }

        this.armed = false;

        if (!this.target.isPresent() || !this.target.get().isAlive()) {
            return;
        }
        performTeleport(this.target.get());
        this.timer.reset();
    }

    private Optional<PlayerEntity> pickTarget() {
        List<PlayerEntity> enemies = CombatUtil.getEnemies(this.targetRange.getValue());
        if (enemies.isEmpty()) {
            return Optional.empty();
        }
        PlayerEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (PlayerEntity player : enemies) {
            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = player;
            }
        }
        return Optional.ofNullable(closest);
    }

    private void performTeleport(PlayerEntity target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        double dist = playerPos.distanceTo(targetPos);

        if (dist > this.tpDistance.getValue()) {
            teleportTowards(targetPos);
            return;
        }

        Vec3d center = getTargetBlockCenter(target);
        if (center != null && isSafeStandingPosition(center)) {
            TPUtils.newTeleport(center);
            mc.player.setPosition(center.x, center.y, center.z);
        } else {
            teleportTowards(targetPos);
        }
    }

    private void teleportTowards(Vec3d targetPos) {
        Vec3d playerPos = mc.player.getPos();
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double horizLen = Math.sqrt((dx * dx) + (dz * dz));
        if (horizLen < 0.001) {
            return;
        }
        double move = Math.min(this.tpDistance.getValue(), horizLen);
        double ux = dx / horizLen;
        double uz = dz / horizLen;
        double newX = playerPos.x + (ux * move);
        double newZ = playerPos.z + (uz * move);

        double dy = targetPos.y - playerPos.y;
        double maxYDiff = 4.0;
        double originalY = playerPos.y;
        double baseY = originalY + Math.max(-maxYDiff, Math.min(maxYDiff, dy));

        BlockPos block = BlockPos.ofFloored(newX, baseY, newZ);
        newX = block.getX() + 0.5;
        newZ = block.getZ() + 0.5;

        double finalY = findValidStandingY(newX, newZ, originalY, maxYDiff, baseY);
        if (Double.isNaN(finalY)) {
            return;
        }

        Vec3d finalPos = new Vec3d(newX, finalY, newZ);
        TPUtils.newTeleport(finalPos);
        mc.player.setPosition(finalPos.x, finalPos.y, finalPos.z);
    }

    private double findValidStandingY(double x, double z, double originalY, double maxYDiff, double preferredY) {
        double minY = originalY - maxYDiff;
        double maxY = originalY + maxYDiff;
        double searchY = Math.max(minY, Math.min(maxY, preferredY));

        for (double yOffset = 0; yOffset <= maxYDiff; yOffset += 0.5) {
            double testY = searchY + yOffset;
            if (testY > maxY) break;
            Vec3d pos = new Vec3d(x, testY, z);
            if (isSafeStandingPosition(pos)) {
                return testY;
            }
        }
        for (double yOffset = -0.5; yOffset >= -maxYDiff; yOffset -= 0.5) {
            double testY = searchY + yOffset;
            if (testY < minY) break;
            Vec3d pos = new Vec3d(x, testY, z);
            if (isSafeStandingPosition(pos)) {
                return testY;
            }
        }
        return Double.NaN;
    }

    private boolean isSafeStandingPosition(Vec3d pos) {
        if (mc.world == null) return false;
        Box box = mc.player.getBoundingBox().offset(pos.x - mc.player.getX(), pos.y - mc.player.getY(), pos.z - mc.player.getZ());
        if (!mc.world.isSpaceEmpty(box)) {
            return false;
        }
        // 已删除脚下 0.01 格碰撞检测
        BlockPos below = BlockPos.ofFloored(pos.x, pos.y - 0.01, pos.z);
        if (!mc.world.getBlockState(below).isSolid()) {
            return false;
        }
        return true;
    }

    private Vec3d getTargetBlockCenter(PlayerEntity target) {
        BlockPos block;
        if (mc.world == null) {
            return null;
        }
        BlockPos feet = target.getBlockPos();
        boolean buried = !mc.world.isAir(feet);
        if (buried) {
            return new Vec3d(target.getX(), target.getY() + 1.0, target.getZ());
        }
        BlockPos below = target.getBlockPos().down();
        BlockPos pos = below;
        while (true) {
            block = pos;
            if (block.getY() <= mc.world.getBottomY() || !mc.world.isAir(block)) {
                break;
            }
            pos = block.down();
        }
        if (block.getY() <= mc.world.getBottomY() || mc.world.isAir(block)) {
            return null;
        }
        return new Vec3d(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    private static Vec3d getClosestPointToSide(Vec3d eyePos, BlockPos pos, Direction side) {
        double qx, qy, qz;
        double px = eyePos.x;
        double py = eyePos.y;
        double pz = eyePos.z;
        double minX = pos.getX();
        double maxX = pos.getX() + 1;
        double minY = pos.getY();
        double maxY = pos.getY() + 1;
        double minZ = pos.getZ();
        double maxZ = pos.getZ() + 1;
        switch (side) {
            case DOWN:
                qx = clamp(px, minX, maxX);
                qz = clamp(pz, minZ, maxZ);
                qy = minY;
                break;
            case UP:
                qx = clamp(px, minX, maxX);
                qz = clamp(pz, minZ, maxZ);
                qy = maxY;
                break;
            case NORTH:
                qx = clamp(px, minX, maxX);
                qy = clamp(py, minY, maxY);
                qz = minZ;
                break;
            case SOUTH:
                qx = clamp(px, minX, maxX);
                qy = clamp(py, minY, maxY);
                qz = maxZ;
                break;
            case WEST:
                qy = clamp(py, minY, maxY);
                qz = clamp(pz, minZ, maxZ);
                qx = minX;
                break;
            case EAST:
                qy = clamp(py, minY, maxY);
                qz = clamp(pz, minZ, maxZ);
                qx = maxX;
                break;
            default:
                return eyePos;
        }
        return new Vec3d(qx, qy, qz);
    }

    private Vec3d getGroundHitVec() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos feet = playerPos.down();

        if (smartThrow.getValue()) {
            if (!mc.world.isAir(feet) && mc.world.getBlockState(feet).isFullCube(mc.world, feet)) {
                return getClosestPointToSide(mc.player.getEyePos(), feet, Direction.DOWN);
            }
            if (!mc.world.isAir(playerPos) && mc.world.getBlockState(playerPos).isFullCube(mc.world, playerPos)) {
                return getClosestPointToSide(mc.player.getEyePos(), playerPos, Direction.DOWN);
            }
            Vec3d directionVec = null;
            for (Direction dir : Direction.values()) {
                BlockPos pos = playerPos.offset(dir);
                if (!mc.world.isAir(pos) && mc.world.getBlockState(pos).isFullCube(mc.world, pos)) {
                    directionVec = getClosestPointToSide(mc.player.getEyePos(), pos, dir);
                }
            }
            if (directionVec == null) {
                for (Direction dir1 : Direction.values()) {
                    for (Direction dir2 : Direction.values()) {
                        BlockPos pos2 = playerPos.offset(dir1).offset(dir2);
                        if (!mc.world.isAir(pos2) && mc.world.getBlockState(pos2).isFullCube(mc.world, pos2)) {
                            directionVec = getClosestPointToSide(mc.player.getEyePos(), pos2, dir2);
                        }
                    }
                }
            }
            return directionVec;
        }

        if (onlyGround.getValue()) {
            if (!mc.world.isAir(feet) && mc.world.getBlockState(feet).isFullCube(mc.world, feet)) {
                return getClosestPointToSide(mc.player.getEyePos(), feet, Direction.DOWN);
            }
            return null;
        } else {
            return new Vec3d(mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ());
        }
    }

    private int findItem(Item item) {
        if (this.inventorySwap.getValue()) {
            return InventoryUtil.findItemInventorySlot(item);
        }
        return InventoryUtil.findItem(item);
    }

    private void swap(int slot, int select) {
        if (this.inventorySwap.getValue()) {
            InventoryUtil.inventorySwap(slot, select);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }
}