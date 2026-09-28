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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

/**
 * 天空锤 —— TP到Y=369, 实际下落到Y=330, 回传原位, 攻击
 *
 * 利用实际坠落让服务端累计fallDistance(369-330=39), 回传后攻击包在entity tick之前处理, fallDistance仍在高位
 * 全程Y>0, 无虚空伤害, 不爆图腾
 */
public class SkyMace extends Module {
    public static SkyMace INSTANCE;
    private final SliderSetting skyY = this.add(new SliderSetting("SkyY", 369.0, 320.0, 369.0, 1.0));
    private final SliderSetting fallToY = this.add(new SliderSetting("FallToY", 330.0, 300.0, 368.0, 1.0));
    private final SliderSetting timerSpeed = this.add(new SliderSetting("TimerSpeed", 5.2, 1.0, 10.0, 0.1));
    private final SliderSetting fallTime = this.add(new SliderSetting("FallTime", 300.0, 100.0, 1000.0, 50.0));
    private final SliderSetting attackPackets = this.add(new SliderSetting("AttackPackets", 3.0, 1.0, 10.0, 1.0));
    private final SliderSetting cycleDelay = this.add(new SliderSetting("CycleDelay", 0.0, 0.0, 1000.0, 50.0));
    private final SliderSetting fallHeight = this.add(new SliderSetting("FallHeight", 100.0, 20.0, 500.0, 10.0));
    private final SliderSetting attackRange = this.add(new SliderSetting("AttackRange", 6.0, 1.0, 10.0, 0.1));
    private final BooleanSetting requireMace = this.add(new BooleanSetting("RequireMace", true));
    private final BooleanSetting autoSwap = this.add(new BooleanSetting("AutoSwap", true));
    private final BooleanSetting requireAura = this.add(new BooleanSetting("RequireAura", true));
    private final BooleanSetting noCrystal = this.add(new BooleanSetting("NoCrystal", false));
    private final BooleanSetting lockCamera = this.add(new BooleanSetting("LockCamera", false));

    private double originalX, originalY, originalZ;
    private SkyPhase phase = SkyPhase.IDLE;
    private final Timer phaseTimer = new Timer();
    private final Timer cycleTimer = new Timer();

    public SkyMace() {
        super("SkyMace", Category.Mace);
        this.setChinese("天空锤");
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
        if (phase != SkyPhase.IDLE) {
            Alien.TIMER.reset();
        }
        resetState();
        LockCamera.unlock();
    }

    private void resetState() {
        phase = SkyPhase.IDLE;
        phaseTimer.reset();
        cycleTimer.reset();
        originalX = 0;
        originalY = 0;
        originalZ = 0;
    }

    @EventListener(priority = 250)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!nullCheck() && isOn()) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
                mc.player.setPosition(packet.getX(), packet.getY(), packet.getZ());
                if (phase != SkyPhase.IDLE) {
                    Alien.TIMER.reset();
                    resetState();
                }
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!nullCheck() && event.isPre()) {
            // LockCamera — 动态锁定，仅在canStart时锁定
            if (this.lockCamera.getValue()) {
                boolean canLock = canStart();
                if (canLock) {
                    if (!LockCamera.isLocked()) {
                        LockCamera.lock(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    }
                    LockCamera.onTick();
                } else {
                    LockCamera.unlock();
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

        // 记录原始位置
        originalX = mc.player.getX();
        originalY = mc.player.getY();
        originalZ = mc.player.getZ();

        // TP到天空Y
        double targetY = skyY.getValue();
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(originalX, targetY, originalZ, false)
        );
        mc.player.setPosition(originalX, targetY, originalZ);
        mc.player.fallDistance = (float) fallHeight.getValue();

        // 进入坠落阶段
        phase = SkyPhase.FALLING;
        phaseTimer.reset();
    }

    private void handleFalling() {
        // 开启timer加速，让玩家快速下落
        Alien.TIMER.set(timerSpeed.getValueFloat());

        // 检查是否已下落到目标Y，或超时
        if (mc.player.getY() <= fallToY.getValue() || phaseTimer.passedMs((long) fallTime.getValue())) {
            // 阶段1: 回传原位(Y+1.8)
            double returnY = originalY + 1.8;
            for (int i = 0; i < 2; i++) {
                mc.getNetworkHandler().sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(originalX, returnY, originalZ, false)
                );
            }
            mc.player.setPosition(originalX, returnY, originalZ);
            mc.player.fallDistance = 0.0f;

            // 阶段2: 发送攻击包（此时服务端fallDistance还在高位，entity tick还没重置）
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

    private enum SkyPhase {
        IDLE,
        FALLING
    }
}