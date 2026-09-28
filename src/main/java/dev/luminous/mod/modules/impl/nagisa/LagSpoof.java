package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LagSpoof - 重锤欺骗模块
 * 通过发送虚假高度包，让服务器误以为你从极高处坠落，配合重锤打出瞬间秒杀伤害。
 * 同时包含自动索敌、快速下落、智能反击等全套辅助逻辑。
 */
public class LagSpoof extends Module {
    public static LagSpoof INSTANCE;

    // ======================== 设置项 ========================
    private final SliderSetting minFallHeight = add(new SliderSetting("MinFallHeight", 3.0, 1.0, 15.0, 0.1));
    private final SliderSetting activeHRange   = add(new SliderSetting("ActiveHRange", 20.0, 1.0, 50.0, 0.5));
    private final SliderSetting switchRange    = add(new SliderSetting("SwitchRange", 8.0, 1.0, 20.0, 0.5));
    private final SliderSetting attackRange    = add(new SliderSetting("AttackRange", 6.0, 1.0, 15.0, 0.1));
    private final SliderSetting attackAPS      = add(new SliderSetting("AttackAPS", 12.0, 1.0, 20.0, 1.0));
    private final BooleanSetting alwaysLag     = add(new BooleanSetting("AlwaysLag", true));
    private final BooleanSetting lagOnHit      = add(new BooleanSetting("LagOnHit", true));
    private final SliderSetting landDistance   = add(new SliderSetting("LandDistance", 2.5, 1.0, 5.0, 0.1));
    private final EnumSetting<LagMode> lagMode = add(new EnumSetting<>("LagMode", LagMode.Fly));
    private final SliderSetting smartXZ        = add(new SliderSetting("SmartXZ", 3.0, 0.0, 10.0, 0.1));
    private final SliderSetting smartUp        = add(new SliderSetting("SmartUp", 3.0, 0.0, 10.0, 0.1));
    private final SliderSetting smartDown      = add(new SliderSetting("SmartDown", 3.0, 0.0, 10.0, 0.1));
    private final SliderSetting smartDist      = add(new SliderSetting("SmartDist", 2.0, 0.0, 10.0, 0.1));
    private final EnumSetting<FastMode> fastMode = add(new EnumSetting<>("FastMode", FastMode.Timer));
    private final BooleanSetting smart         = add(new BooleanSetting("Smart", false));
    private final SliderSetting timerMultiplier = add(new SliderSetting("TimerMultiplier", 1.5, 1.0, 10.0, 0.1));
    private final SliderSetting smartH         = add(new SliderSetting("SmartH", 5.0, 0.0, 20.0, 0.1));
    private final SliderSetting smartV         = add(new SliderSetting("SmartV", 2.0, 0.0, 20.0, 0.1));
    private final SliderSetting velocitySpeed  = add(new SliderSetting("VelocitySpeed", 0.62, 0.1, 10.0, 0.01));
    private final BooleanSetting smartLag      = add(new BooleanSetting("SmartLag", false));
    private final SliderSetting smartDelay     = add(new SliderSetting("SmartDelay", 100.0, 0.0, 1000.0, 10.0));
    private final BooleanSetting antiNoFall    = add(new BooleanSetting("AntiNoFall", false));

    // ======================== 状态变量 ========================
    private boolean flag42;          // 是否已发送本次跳跃的假位置包
    private boolean flag183;         // 是否已激活Timer加速
    private boolean flag154;         // SmartLag检测到目标向上穿越
    private double  value155;        // 上一tick玩家的Y坐标
    private int     count186;        // SmartLag反击目标实体ID
    private PlayerEntity player2;    // 当前攻击目标
    private final Timer helper728 = new Timer(); // 攻击冷却计时器
    private final Timer helper73  = new Timer(); // SmartLag延迟计时器
    private final Map<Integer, Double> map30 = new HashMap<>(); // SmartLag：记录每个玩家上一tick的Y差值
    private int lastSlot = -1;       // 自动切锤前的物品槽，用于恢复

    public LagSpoof() {
        super("LagSpoof", Category.Mace);
        INSTANCE = this;
    }

    /**
     * 供 NoFall 等模块调用，禁用防摔落伤害功能以避免冲突
     */
    public static boolean shouldDisableNoFall() {
        return INSTANCE != null && INSTANCE.isOn() && INSTANCE.antiNoFall.getValue();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        value155 = mc.player.getY();
        map30.clear();
        flag154 = false;
        count186 = -1;
        flag42 = false;
        flag183 = false;
        helper728.reset();
        helper73.reset();
        lastSlot = mc.player.getInventory().selectedSlot;
    }

    @Override
    public void onDisable() {
        resetState();                 // 清空攻击目标、假位置状态等
        if (flag183) {
            Alien.TIMER.reset();      // 恢复游戏速度
            flag183 = false;
        }
        // 恢复物品槽位
        if (lastSlot != -1 && mc.player.getInventory().selectedSlot != lastSlot) {
            InventoryUtil.switchToSlot(lastSlot);
        }
        lastSlot = -1;
    }

    /**
     * 重置所有与攻击流程相关的临时状态，但保留 SmartLag 的独立状态
     */
    private void resetState() {
        flag42 = false;
        player2 = null;
        // 注意：SmartLag 的 flag154 / count186 / map30 不在此重置，它们由 handleSmartLag 自行管理
    }

    /**
     * 接收数据包事件：当启用 LagOnHit 时，若我方攻击造成伤害，则自动触发假高度包
     */
    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (!lagOnHit.getValue()) return;

        if (event.getPacket() instanceof EntityDamageS2CPacket packet) {
            // 必须是我们当前锁定的目标
            if (packet.entityId() != (player2 != null ? player2.getId() : -1)) return;

            // 检查伤害是否由我方造成
            boolean causedByMe = packet.sourceCauseId() == mc.player.getId()
                    || packet.sourceDirectId() == mc.player.getId();
            // 如果本次跳跃尚未发送假位置包，且玩家真实坠落高度达标，则立即发送
            if (causedByMe && !flag42 && mc.player.fallDistance >= minFallHeight.getValue()) {
                performLag();
                flag42 = true;
            }
        }
    }

    /**
     * 客户端 Tick 事件（Pre）：核心逻辑循环
     */
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || event.isPost()) return;

        // 1. 处理快速下落模式（Timer / Velocity）
        handleFastMode();

        // 2. 处理智能反击（SmartLag）
        handleSmartLag();

        // 3. 更新 Y 轴状态：若上升或落地，允许下次触发假位置
        double currentY = mc.player.getY();
        if (currentY > value155 || mc.player.isOnGround()) {
            flag42 = false;
        }
        value155 = currentY;

        // 4. 若玩家已落地，清除攻击目标并恢复物品栏
        if (mc.player.isOnGround()) {
            resetAttackState();
            return;
        }

        // 5. 寻找攻击目标
        player2 = findTarget();
        if (player2 == null) {
            resetAttackState();
            handleAlwaysLag(); // 没有目标时仍然检测 AlwaysLag
            return;
        }

        boolean fallEnough = mc.player.fallDistance >= minFallHeight.getValue();
        double dist = mc.player.distanceTo(player2);

        // 6. 若坠落距离足够且目标在切换范围内，自动切重锤并准备攻击
        if (fallEnough && dist <= switchRange.getValue()) {
            int maceSlot = InventoryUtil.findItem(Items.MACE);
            if (maceSlot != -1) {
                // 自动切换到重锤
                if (mc.player.getInventory().selectedSlot != maceSlot) {
                    lastSlot = mc.player.getInventory().selectedSlot;
                    InventoryUtil.switchToSlot(maceSlot);
                }
                // 攻击冷却与距离判断
                if (dist <= attackRange.getValue() && helper728.passedMs((long)(1000.0 / attackAPS.getValue()))) {
                    mc.interactionManager.attackEntity(mc.player, player2);
                    helper728.reset();
                }
            }
        } else {
            // 不满足条件时恢复物品栏
            resetAttackState();
        }

        // 7. 无论有无目标，处理常驻假位置（AlwaysLag）
        handleAlwaysLag();
    }

    /**
     * 清除当前攻击目标并恢复物品栏
     */
    private void resetAttackState() {
        if (lastSlot != -1 && mc.player.getInventory().selectedSlot != lastSlot) {
            InventoryUtil.switchToSlot(lastSlot);
            lastSlot = -1;
        }
        player2 = null;
    }

    /**
     * AlwaysLag：在满足条件时自动触发假高度包（无需攻击命中）
     */
    private void handleAlwaysLag() {
        if (!alwaysLag.getValue()) return;
        if (flag42) return; // 本次跳跃已触发过
        if (mc.player.fallDistance < minFallHeight.getValue()) return;
        if (isNearGround(landDistance.getValue())) {
            performLag();
            flag42 = true;
        }
    }

    /**
     * 检查玩家脚底与下方实体方块/液体表面的距离是否 ≤ maxDist
     */
    private boolean isNearGround(double maxDist) {
        BlockPos feet = mc.player.getBlockPos();
        int checks = (int) Math.ceil(maxDist) + 1;
        for (int i = 1; i <= checks; i++) {
            BlockPos pos = feet.down(i);
            VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
            boolean solid = !shape.isEmpty();
            boolean liquid = !mc.world.getBlockState(pos).getFluidState().isEmpty();
            if (!solid && !liquid) continue;

            double surfaceY = solid ? pos.getY() + shape.getMax(Direction.Axis.Y)
                    : pos.getY() + mc.world.getBlockState(pos).getFluidState().getHeight(mc.world, pos);
            double dist = mc.player.getY() - surfaceY;
            if (dist <= maxDist && dist >= 0.0) return true;
        }
        return false;
    }

    /**
     * 根据选定的 LagMode 发送虚假位置包
     */
    private void performLag() {
        switch (lagMode.getValue()) {
            case Smart -> smartLagPosition();
            case Invalid -> {
                for (int i = 0; i < 20; i++)
                    sendPos(mc.player.getX(), mc.player.getY() + 1337.0, mc.player.getZ(), false);
            }
            case TrollHack -> sendPos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false);
            case ToVoid    -> sendPos(mc.player.getX(), -70.0, mc.player.getZ(), false);
            case ToVoid2   -> sendPos(mc.player.getX(), -7.0, mc.player.getZ(), false);
            case Normal    -> sendPos(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ(), false);
            case Rotation  -> {
                sendLook(-180, -90);
                sendLook(180, 90);
            }
            case Fly -> {
                sendPos(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.170005801788139, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.2426308013947485, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 2.640088003576279, mc.player.getZ(), false);
            }
            case Glide -> {
                sendPos(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false);
            }
        }
    }

    /**
     * Smart 模式：扫描周围安全空气方块并传送到最合适的位置
     */
    private void smartLagPosition() {
        double bestDist = Double.MAX_VALUE;
        BlockPos bestPos = null;
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        for (double dx = x - smartXZ.getValue(); dx < x + smartXZ.getValue(); dx++) {
            for (double dz = z - smartXZ.getValue(); dz < z + smartXZ.getValue(); dz++) {
                for (double dy = y - smartDown.getValue(); dy < y + smartUp.getValue(); dy++) {
                    BlockPos pos = BlockPos.ofFloored(dx, dy, dz);
                    if (isAir(pos) && isAir(pos.up())) {
                        double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        if (dist > smartDist.getValue() * smartDist.getValue() && dist < bestDist) {
                            bestDist = dist;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        if (bestPos != null) {
            sendPos(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false);
        }
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
        }
    }

    private void sendLook(float yaw, float pitch) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, false));
        }
    }

    /**
     * 智能反击系统：监测敌人从下方跃过玩家，并在延迟后自动假位置反击
     */
    private void handleSmartLag() {
        if (!smartLag.getValue()) {
            flag154 = false;
            count186 = -1;
            map30.clear();
            return;
        }

        // 已检测到穿越事件，等待延迟到期后执行反击
        if (flag154) {
            Entity target = mc.world.getEntityById(count186);
            if (target instanceof PlayerEntity && isValidTarget((PlayerEntity) target)) {
                if (helper73.passedMs((long) smartDelay.getValue())) {
                    performLag();
                    flag42 = true;        // 标记已发送，防止重复
                    flag154 = false;      // 重置监测状态
                    count186 = -1;
                }
            } else {
                flag154 = false;
                count186 = -1;
            }
            return;
        }

        // 实时计算每个玩家的 Y 差值，检测从负到正的变化
        List<Integer> currentIds = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            int id = player.getId();
            currentIds.add(id);
            double currentDiff = player.getY() - mc.player.getY();
            Double prevDiff = map30.put(id, currentDiff);
            if (!flag154 && prevDiff != null) {
                if (prevDiff < 0 && currentDiff > 0) {
                    // 目标从下方穿越到了上方，准备反击
                    flag154 = true;
                    count186 = id;
                    helper73.reset();
                }
            }
        }
        // 清理已离开世界的玩家记录，防止 Map 膨胀
        map30.keySet().removeIf(id -> !currentIds.contains(id));
    }

    // ======================== 快速下落模式处理 ========================
    private void handleFastMode() {
        boolean applyTimer = false;
        switch (fastMode.getValue()) {
            case Timer  -> applyTimer = shouldTimer();
            case Velocity -> applyVelocity();
            case Both -> {
                applyTimer = shouldTimer();
                applyVelocity();
            }
            case None -> resetTimer();
        }
        if (applyTimer) {
            if (!flag183) {
                Alien.TIMER.set(timerMultiplier.getValueFloat());
                flag183 = true;
            }
        } else {
            resetTimer();
        }
    }

    private boolean shouldTimer() {
        if (mc.player == null) return false;
        if (hasElytra()) return false;
        if (mc.player.getVelocity().y >= 0) return false;
        if (smart.getValue() && !nearbyEnemyWithinSmartRange()) return false;
        return true;
    }

    private void applyVelocity() {
        if (mc.player == null) return;
        double speed = velocitySpeed.getValue();
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(
                    mc.player.getVelocity().x,
                    mc.player.getVelocity().y - speed,
                    mc.player.getVelocity().z
            );
        } else if (mc.player.getVelocity().y < 0) {
            mc.player.setVelocity(
                    mc.player.getVelocity().x,
                    mc.player.getVelocity().y - speed * 0.3,
                    mc.player.getVelocity().z
            );
        }
    }

    private void resetTimer() {
        if (flag183) {
            Alien.TIMER.reset();
            flag183 = false;
        }
    }

    private boolean nearbyEnemyWithinSmartRange() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            double hDist = horizontalDist(player);
            double vDiff = mc.player.getY() - player.getY();
            if (hDist < smartH.getValue() && vDiff > 0 && vDiff <= smartV.getValue()) {
                return true;
            }
        }
        return false;
    }

    // ======================== 目标选择 ========================
    private PlayerEntity findTarget() {
        PlayerEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            if (horizontalDist(player) > activeHRange.getValue()) continue;
            double dist = mc.player.distanceTo(player);
            if (dist < minDist) {
                minDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        return !Alien.FRIEND.isFriend(player);
    }

    private double horizontalDist(PlayerEntity player) {
        double dx = mc.player.getX() - player.getX();
        double dz = mc.player.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean hasElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean isAir(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    // ======================== 内部枚举定义 ========================
    public enum LagMode { Smart, Invalid, TrollHack, ToVoid, ToVoid2, Normal, Rotation, Fly, Glide }
    public enum FastMode { Timer, Velocity, Both, None }
}