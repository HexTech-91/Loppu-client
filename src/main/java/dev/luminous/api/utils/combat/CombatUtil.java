
package dev.luminous.api.utils.combat;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class CombatUtil
        implements Wrapper {
    private static final LoggedOutEnemyManager loggedOutManager = new LoggedOutEnemyManager();
    public static final Timer breakTimer = new Timer();
    public static boolean terrainIgnore = false;
    public static BlockPos modifyPos;
    public static BlockState modifyBlockState;

    public static List<PlayerEntity> getEnemies(double range) {
        ArrayList<PlayerEntity> list = new ArrayList<PlayerEntity>();
        for (AbstractClientPlayerEntity player : Alien.THREAD.getPlayers()) {
            if (!CombatUtil.isValid((Entity)player, range)) continue;
            list.add((PlayerEntity)player);
        }
        return list;
    }

    public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
        CombatUtil.attackCrystal(new Box(pos), rotate, eatingPause);
    }

    public static void attackCrystal(Box box, boolean rotate, boolean eatingPause) {
        for (EndCrystalEntity entity : BlockUtil.getEndCrystals(box)) {
            CombatUtil.attackWithDelay((Entity)entity, rotate, eatingPause);
        }
    }

    public static void attackWithDelay(Entity entity, boolean rotate, boolean usingPause) {
        if (!breakTimer.passed((long)(AntiCheat.INSTANCE.attackDelay.getValue() * 1000.0))) {
            return;
        }
        if (usingPause && CombatUtil.mc.player.isUsingItem()) {
            return;
        }
        CombatUtil.attack(entity, rotate);
    }

    public static void attack(Entity entity, boolean rotate) {
        if (entity != null) {
            Vec3d attackVec = MathUtil.getClosestPointToBox(CombatUtil.mc.player.getEyePos(), entity.getBoundingBox());
            if (CombatUtil.mc.player.getEyePos().distanceTo(attackVec) > AntiCheat.INSTANCE.ieRange.getValue()) {
                return;
            }
            breakTimer.reset();
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue()) {
                Alien.ROTATION.lookAt(attackVec);
            }
            mc.getNetworkHandler().sendPacket((Packet)PlayerInteractEntityC2SPacket.attack((Entity)entity, (boolean)CombatUtil.mc.player.isSneaking()));
            CombatUtil.mc.player.resetLastAttackedTicks();
            EntityUtil.swingHand(Hand.MAIN_HAND, AntiCheat.INSTANCE.attackSwing.getValue());
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue()) {
                Alien.ROTATION.snapBack();
            }
        }
    }

    public static boolean isntValid(Entity entity, double range) {
        return !CombatUtil.isValid(entity, range);
    }

    public static boolean isValid(Entity entity, double range) {
        PlayerEntity player;
        boolean invalid = entity == null || !entity.isAlive() || entity.equals((Object)CombatUtil.mc.player) || entity instanceof PlayerEntity && Alien.FRIEND.isFriend(player = (PlayerEntity)entity) || CombatUtil.mc.player.getPos().distanceTo(entity.getPos()) > range;
        return !invalid;
    }

    public static boolean isValid(Entity entity) {
        PlayerEntity player;
        boolean invalid = entity == null || !entity.isAlive() || entity.equals((Object)CombatUtil.mc.player) || entity instanceof PlayerEntity && Alien.FRIEND.isFriend(player = (PlayerEntity)entity);
        return !invalid;
    }

    public static PlayerEntity getClosestEnemy(double getDistance) {
        PlayerEntity closest = null;
        for (PlayerEntity player : CombatUtil.getEnemies(getDistance)) {
            if (closest == null) {
                closest = player;
                continue;
            }
            if (!(CombatUtil.mc.player.squaredDistanceTo(player.getPos()) < CombatUtil.mc.player.squaredDistanceTo((Entity)closest))) continue;
            closest = player;
        }
        return closest;
    }

    static {
        modifyBlockState = Blocks.AIR.getDefaultState();
    }

    public static PlayerEntity getClosestLoggedOutEnemy(double value) {
        loggedOutManager.updatePlayerList();
        return loggedOutManager.getClosestLoggedOutEnemy(value);
    }

    public static List<PlayerEntity> getLoggedOutEnemies(double value) {
        loggedOutManager.updatePlayerList();
        return loggedOutManager.getLoggedOutEnemies(value);
    }

    public static boolean isLoggedOut(PlayerEntity player) {
        return loggedOutManager.isLoggedOut(player);
    }

    public static LoggedOutEnemyManager getLoggedOutManager() {
        return loggedOutManager;
    }

    /**
     * 获取实体预测位置（带碰撞检测）
     * @param entity 目标实体
     * @param ticks 预测tick数
     * @return 预测位置
     */
    public static Vec3d getEntityPosVec(PlayerEntity entity, int ticks) {
        if (ticks <= 0) {
            return entity.getPos();
        }
        return entity.getPos().add(getMotionVec(entity, ticks, true));
    }

    /**
     * 获取实体移动向量（带碰撞检测）
     * @param entity 目标实体
     * @param ticks 预测tick数
     * @param collision 是否检测碰撞
     * @return 移动向量
     */
    public static Vec3d getMotionVec(Entity entity, float ticks, boolean collision) {
        double dX = entity.getX() - entity.prevX;
        double dZ = entity.getZ() - entity.prevZ;
        double entityMotionPosX = 0;
        double entityMotionPosZ = 0;
        if (collision) {
            for (double i = 1; i <= ticks; i = i + 0.5) {
                if (!mc.world.canCollide(entity, entity.getBoundingBox().offset(new Vec3d(dX * i, 0, dZ * i)))) {
                    entityMotionPosX = dX * i;
                    entityMotionPosZ = dZ * i;
                } else {
                    break;
                }
            }
        } else {
            entityMotionPosX = dX * ticks;
            entityMotionPosZ = dZ * ticks;
        }
        return new Vec3d(entityMotionPosX, 0, entityMotionPosZ);
    }
}

