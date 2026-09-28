package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;

/**
 * 头顶卡位锤 —— TP到玩家头顶第一个方块下方，下落累积摔落高度，回传原位攻击
 *
 * 工作流程：
 * 1. 确定目标玩家位置
 * 2. 扫描玩家头顶上方第一个实体方块（XZ不变）
 * 3. TP到方块Y-2位置
 * 4. Timer加速下落
 * 5. 达到摔落高度/时间后，TP回原位Y+1.8
 * 6. 发送攻击包
 * 7. 循环
 */
public class HitBoxMace extends Module {
    public static HitBoxMace INSTANCE;

    private final SliderSetting timerSpeed = add(new SliderSetting("TimerSpeed", 5.2, 1.0, 10.0, 0.1));
    private final SliderSetting fallTime = add(new SliderSetting("FallTime", 300.0, 100.0, 1000.0, 50.0));
    private final SliderSetting fallHeight = add(new SliderSetting("FallHeight", 30.0, 10.0, 200.0, 5.0));
    private final SliderSetting attackPackets = add(new SliderSetting("AttackPackets", 3.0, 1.0, 10.0, 1.0));
    private final SliderSetting cycleDelay = add(new SliderSetting("CycleDelay", 0.0, 0.0, 1000.0, 50.0));
    private final SliderSetting attackRange = add(new SliderSetting("AttackRange", 6.0, 1.0, 10.0, 0.1));
    private final SliderSetting maxScanHeight = add(new SliderSetting("MaxScanHeight", 64.0, 10.0, 256.0, 1.0));
    private final SliderSetting lowY = add(new SliderSetting("LowY", 3.0, 1.0, 20.0, 0.5));
    private final BooleanSetting attackDuringFall = add(new BooleanSetting("AttackDuringFall", true));
    private final BooleanSetting requireMace = add(new BooleanSetting("RequireMace", true));
    private final BooleanSetting autoSwap = add(new BooleanSetting("AutoSwap", true));
    private final BooleanSetting requireAura = add(new BooleanSetting("RequireAura", true));
    private final BooleanSetting noCrystal = add(new BooleanSetting("NoCrystal", false));
    private final BooleanSetting lockCamera = add(new BooleanSetting("LockCamera", false));

    private double originalX, originalY, originalZ;
    private double targetY;
    private boolean fallAttacked;
    private SpoofPhase phase = SpoofPhase.IDLE;
    private final Timer phaseTimer = new Timer();
    private final Timer cycleTimer = new Timer();

    public HitBoxMace() {
        super("HitBoxMace", Category.Mace);
        this.setChinese("卡位锤");
        INSTANCE = this;
    }

    public static boolean shouldDisableNoFall() {
        return INSTANCE != null && INSTANCE.isOn();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        resetState();
    }

    @Override
    public void onDisable() {
        if (phase != SpoofPhase.IDLE) {
            Alien.TIMER.reset();
        }
        resetState();
        LockCamera.unlock();
    }

    private void resetState() {
        phase = SpoofPhase.IDLE;
        phaseTimer.reset();
        cycleTimer.reset();
        originalX = 0;
        originalY = 0;
        originalZ = 0;
        targetY = 0;
        fallAttacked = false;
    }

    @EventListener(priority = 250)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!nullCheck() && isOn()) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
                mc.player.setPosition(packet.getX(), packet.getY(), packet.getZ());
                if (phase != SpoofPhase.IDLE) {
                    Alien.TIMER.reset();
                    resetState();
                }
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!nullCheck() && event.isPre()) {
            // LockCamera
            if (lockCamera.getValue()) {
                if (LockCamera.isLocked()) {
                    LockCamera.onTick();
                }
            } else {
                LockCamera.unlock();
            }

            // AutoSwap
            if (!handleAutoSwap()) return;

            switch (phase) {
                case IDLE -> handleIdle();
                case FALLING -> handleFalling();
            }
        }
    }

    private void handleIdle() {
        if (!canStart()) return;
        if (!cycleTimer.passedMs((long) cycleDelay.getValue())) return;

        // 获取目标玩家位置
        Entity target = getTarget();
        if (target == null) return;

        // 记录目标玩家的原始位置（我们将会TP到目标位置）
        originalX = target.getX();
        originalY = target.getY();
        originalZ = target.getZ();

        // 寻找目标头顶上方的第一个实体方块
        double blockY = findBlockAboveHead(originalX, originalY, originalZ);
        if (blockY < 0) return; // 没找到方块，跳过

        // LowY检查：如果方块与玩家Y的差太小，跳过直到有足够高度的方块
        if (blockY - originalY < lowY.getValue()) return;

        // 计算TP位置：方块Y-2
        double tpY = blockY - 2.0;

        // 计算摔落高度
        double fallDist = tpY - (originalY + 1.8);
        if (fallDist < fallHeight.getValue()) return; // 摔落高度不够

        targetY = tpY;

        // LockCamera锁定目标位置（视觉上玩家不动）
        if (lockCamera.getValue()) {
            LockCamera.lock(originalX, originalY, originalZ);
        }

        // TP到目标头顶方块下方
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(originalX, tpY, originalZ, false)
        );
        mc.player.setPosition(originalX, tpY, originalZ);
        mc.player.fallDistance = 0;

        // 进入坠落阶段
        phase = SpoofPhase.FALLING;
        phaseTimer.reset();
    }

    private void handleFalling() {
        // 开启timer加速，让玩家快速下落
        Alien.TIMER.set(timerSpeed.getValueFloat());

        // 坠落过程中，如果攻击范围内有目标，也发送攻击包
        if (attackDuringFall.getValue() && !fallAttacked) {
            Entity fallTarget = getTarget();
            if (fallTarget != null) {
                double dist = mc.player.distanceTo(fallTarget);
                if (dist <= attackRange.getValue()) {
                    if (requireAura.getValue() && Aura.INSTANCE != null && Aura.INSTANCE.isOn()) {
                        Aura.INSTANCE.doMultiAttack(attackPackets.getValueInt());
                    } else {
                        for (int i = 0; i < attackPackets.getValueInt(); i++) {
                            mc.getNetworkHandler().sendPacket(
                                    PlayerInteractEntityC2SPacket.attack(fallTarget, mc.player.isSneaking())
                            );
                        }
                    }
                    fallAttacked = true;
                }
            }
        }

        // 检查是否已下落到足够高度，或超时
        double currentY = mc.player.getY();
        double returnY = originalY + 1.8;
        double fallenDistance = targetY - currentY;

        if (fallenDistance >= fallHeight.getValue() || phaseTimer.passedMs((long) fallTime.getValue())) {
            // 回传原位(Y+1.8)
            for (int i = 0; i < 2; i++) {
                mc.getNetworkHandler().sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(originalX, returnY, originalZ, false)
                );
            }
            mc.player.setPosition(originalX, returnY, originalZ);
            mc.player.fallDistance = 0.0f;

            // 发送攻击包（此时服务端fallDistance还在高位）
            Entity target = getTarget();
            if (target != null) {
                if (requireAura.getValue() && Aura.INSTANCE != null && Aura.INSTANCE.isOn()) {
                    Aura.INSTANCE.doMultiAttack(attackPackets.getValueInt());
                } else {
                    for (int i = 0; i < attackPackets.getValueInt(); i++) {
                        mc.getNetworkHandler().sendPacket(
                                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
                        );
                    }
                }
            }

            // 清理
            Alien.TIMER.reset();
            LockCamera.unlock();
            resetState();
            cycleTimer.reset();
        }
    }

    /**
     * 从目标头顶上方扫描，寻找第一个实体方块（Y坐标最小的方块）
     * 扫描范围：目标头顶（Y+2）到 Y+maxScanHeight
     *
     * @return 找到的方块Y坐标，-1表示未找到
     */
    private double findBlockAboveHead(double playerX, double playerY, double playerZ) {
        int startY = (int) Math.ceil(playerY + 1.8);
        int endY = startY + (int) maxScanHeight.getValue();

        for (int y = startY; y <= endY; y++) {
            BlockPos pos = BlockPos.ofFloored(playerX, y, playerZ);
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && state.isSolidBlock(mc.world, pos)) {
                return y;
            }
        }
        return -1;
    }

    private boolean handleAutoSwap() {
        if (!autoSwap.getValue()) return true;
        if (mc.player.getMainHandStack().getItem() == Items.MACE) return true;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) {
                mc.player.getInventory().selectedSlot = i;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
                return true;
            }
        }
        return false;
    }

    private boolean canStart() {
        if (requireMace.getValue() && mc.player.getMainHandStack().getItem() != Items.MACE) return false;
        Entity target = getTarget();
        if (target == null) return false;
        return mc.player.distanceTo(target) <= attackRange.getValue();
    }

    private Entity getTarget() {
        if (requireAura.getValue()) {
            if (Aura.INSTANCE != null && Aura.INSTANCE.isOn()) {
                Entity auraTarget = Aura.target;
                return isValidTarget(auraTarget) ? auraTarget : null;
            }
            return null;
        }
        PlayerEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (isValidTarget(player)) {
                double dist = mc.player.distanceTo(player);
                if (dist < minDist) {
                    minDist = dist;
                    closest = player;
                }
            }
        }
        return closest;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDead()) return false;
        if (entity instanceof PlayerEntity player && Alien.FRIEND.isFriend(player)) return false;
        return true;
    }

    public static boolean isNoCrystalActive() {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        if (!INSTANCE.noCrystal.getValue()) return false;
        if (mc.player == null) return false;
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return false;
        Entity target = INSTANCE.getTarget();
        if (target == null) return false;
        return mc.player.distanceTo(target) <= INSTANCE.attackRange.getValue();
    }

    @Override
    public String getInfo() {
        return phase.name();
    }

    private enum SpoofPhase {
        IDLE,
        FALLING
    }
}