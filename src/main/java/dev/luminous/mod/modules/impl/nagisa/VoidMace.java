package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class VoidMace extends Module {
    public static VoidMace INSTANCE;

    // ==================== 模式切换 ====================
    public enum Mode {
        DoublePacket,   // 同Tick双包法：虚空→回传(onGround=false)→攻击
        Legacy          // 原等待模式（已废弃，仅保留兼容）
    }
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.DoublePacket));

    // ==================== 核心参数 ====================
    private final SliderSetting voidY = this.add(new SliderSetting("VoidY", -60.0, -100.0, -50.0, 1.0));
    private final SliderSetting attackPackets = this.add(new SliderSetting("AttackPackets", 5.0, 1.0, 10.0, 1.0));
    private final SliderSetting cycleDelay = this.add(new SliderSetting("CycleDelay", 0.0, 0.0, 1000.0, 50.0));
    private final SliderSetting fallHeight = this.add(new SliderSetting("FallHeight", 40.0, 20.0, 500.0, 10.0));
    private final SliderSetting attackRange = this.add(new SliderSetting("AttackRange", 6.0, 1.0, 10.0, 0.1));
    private final SliderSetting wallRange = this.add(new SliderSetting("WallRange", 5.5, 0.0, 8.0, 0.1));

    private final BooleanSetting requireMace = this.add(new BooleanSetting("RequireMace", true));
    private final BooleanSetting requireAura = this.add(new BooleanSetting("RequireAura", true));
    private final BooleanSetting swing = this.add(new BooleanSetting("Swing", true));

    // ==================== 双包模式专属增强 ====================
    private final BooleanSetting antiWeb = this.add(new BooleanSetting("AntiWeb", true, () -> mode.is(Mode.DoublePacket)));
    private final BooleanSetting burrowBypass = this.add(new BooleanSetting("BurrowBypass", false, () -> mode.is(Mode.DoublePacket)));
    private final SliderSetting burrowLift = this.add(new SliderSetting("BurrowLift", 1.5, 1.0, 3.0, 0.1,
            () -> mode.is(Mode.DoublePacket) && burrowBypass.getValue()));
    private final BooleanSetting autoReburrow = this.add(new BooleanSetting("AutoReburrow", false,
            () -> mode.is(Mode.DoublePacket) && burrowBypass.getValue()));
    private final BooleanSetting viewLock = this.add(new BooleanSetting("ViewLock", false,
            () -> mode.is(Mode.DoublePacket)));

    // ==================== Legacy 模式专属（保留但不推荐） ====================
    private final SliderSetting timerSpeed = this.add(new SliderSetting("TimerSpeed", 5.2, 1.0, 10.0, 0.1,
            () -> mode.is(Mode.Legacy)));
    private final SliderSetting fallTime = this.add(new SliderSetting("FallTime", 300.0, 50.0, 1000.0, 50.0,
            () -> mode.is(Mode.Legacy)));

    // ==================== 状态变量 ====================
    private final Timer cycleTimer = new Timer();
    private final Timer phaseTimer = new Timer();
    private boolean timerActive = false;
    private VoidPhase voidPhase = VoidPhase.IDLE;
    private double originalX, originalY, originalZ;

    public VoidMace() {
        super("VoidMace", Category.Mace);
        this.setChinese("虚空重锤");
        INSTANCE = this;
    }

    public static boolean shouldDisableNoFall() {
        return INSTANCE != null && INSTANCE.isOn();
    }

    public static boolean isNoCrystalActive() {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        if (!INSTANCE.requireMace.getValue()) return false;
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.MACE;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        this.resetState();
        this.cycleTimer.reset();
    }

    @Override
    public void onDisable() {
        if (this.timerActive) {
            Alien.TIMER.reset();
            this.timerActive = false;
        }
        this.resetState();
    }

    private void resetState() {
        this.voidPhase = VoidPhase.IDLE;
        this.phaseTimer.reset();
        this.cycleTimer.reset();
        this.originalX = 0.0;
        this.originalY = 0.0;
        this.originalZ = 0.0;
    }

    @EventListener(priority = 250)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!nullCheck() && this.isOn()) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
                mc.player.setPosition(packet.getX(), packet.getY(), packet.getZ());
                mc.player.setYaw(packet.getYaw());
                mc.player.setPitch(packet.getPitch());
                mc.player.fallDistance = 0.0F;
                this.resetState();
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!nullCheck() || !event.isPre()) return;

        if (this.requireMace.getValue() && mc.player.getMainHandStack().getItem() != Items.MACE) {
            if (this.timerActive) {
                Alien.TIMER.reset();
                this.timerActive = false;
            }
            return;
        }

        Entity target = getTarget();
        if (target == null) {
            if (this.timerActive) {
                Alien.TIMER.reset();
                this.timerActive = false;
            }
            this.resetState();
            return;
        }

        if (mode.is(Mode.DoublePacket)) {
            handleDoublePacket();
        } else {
            handleLegacyFlow();
        }
    }

    // ============================================================
    //  ★★★ DoublePacket 模式：同 Tick 双包法 ★★★
    //  发送顺序：出洞/反网 → 虚空包 → 回传包(onGround=false) → 攻击包
    // ============================================================
    private void handleDoublePacket() {
        if (!this.cycleTimer.passedMs((long) this.cycleDelay.getValue())) return;
        this.cycleTimer.reset();

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double origY = y;
        boolean wasBurrowed = this.burrowBypass.getValue() && isPlayerBurrowed();

        // 1. BurrowBypass：出洞抬升
        if (wasBurrowed) {
            double liftY = y + this.burrowLift.getValue();
            mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, liftY, z, false));
            if (!this.viewLock.getValue()) mc.player.setPosition(x, liftY, z);
            y = liftY;
        }

        // 2. AntiWeb：脱离蜘蛛网
        if (this.antiWeb.getValue() && isPlayerInWeb()) {
            double webY = y + 1.1;
            mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, webY, z, false));
            if (!this.viewLock.getValue()) mc.player.setPosition(x, webY, z);
            y = webY;
        }

        // 3. 核心双包：去虚空 → 回地面（onGround=false 保留 fallDistance）
        double voidY = this.voidY.getValue();
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, voidY, z, false));
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, y + 1.0, z, false));

        // 更新本地位置（除非 ViewLock）
        if (!this.viewLock.getValue()) {
            mc.player.setPosition(x, y + 1.0, z);
            mc.player.fallDistance = (float) this.fallHeight.getValue();
        } else {
            mc.player.fallDistance = 0.0F;
        }

        // 4. 攻击包（在回传包之后发送，此时 fallDistance 仍保留）
        tryAttack();

        // 5. AutoReburrow：回洞
        if (this.autoReburrow.getValue() && wasBurrowed) {
            mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, origY, z, false));
            if (!this.viewLock.getValue()) mc.player.setPosition(x, origY, z);
        }
    }

    // ============================================================
    //  Legacy 模式（保留但不推荐使用）
    // ============================================================
    private void handleLegacyFlow() {
        switch (this.voidPhase) {
            case IDLE:
                if (!this.cycleTimer.passedMs((long) this.cycleDelay.getValue())) return;
                this.originalX = mc.player.getX();
                this.originalY = mc.player.getY();
                this.originalZ = mc.player.getZ();
                double targetY = this.voidY.getValue();
                mc.getNetworkHandler().sendPacket(new PositionAndOnGround(this.originalX, targetY, this.originalZ, false));
                mc.player.setPosition(this.originalX, targetY, this.originalZ);
                mc.player.fallDistance = (float) this.fallHeight.getValue();
                Alien.TIMER.set(this.timerSpeed.getValueFloat());
                this.timerActive = true;
                this.voidPhase = VoidPhase.FALLING;
                this.phaseTimer.reset();
                break;

            case FALLING:
                if (!this.phaseTimer.passedMs((long) this.fallTime.getValue())) return;
                double returnY = this.originalY + 1.0;
                mc.getNetworkHandler().sendPacket(new PositionAndOnGround(this.originalX, returnY, this.originalZ, false));
                mc.player.setPosition(this.originalX, returnY, this.originalZ);
                if (this.timerActive) {
                    Alien.TIMER.reset();
                    this.timerActive = false;
                }
                this.tryAttack();
                this.voidPhase = VoidPhase.IDLE;
                this.cycleTimer.reset();
                break;
        }
    }

    // ============================================================
    //  攻击执行
    // ============================================================
    private void tryAttack() {
        Entity target = getTarget();
        if (target == null) return;

        Vec3d targetPos = MathUtil.getClosestPointToBox(mc.player.getEyePos(), target.getBoundingBox());
        double distance = mc.player.getEyePos().distanceTo(targetPos);
        boolean canSee = mc.player.canSee(target);
        if (canSee) {
            if (distance > this.attackRange.getValue()) return;
        } else {
            if (distance > this.wallRange.getValue()) return;
        }

        int packets = this.attackPackets.getValueInt();
        if (this.requireAura.getValue() && Aura.INSTANCE != null && Aura.INSTANCE.isOn() && Aura.target != null) {
            Aura.INSTANCE.doMultiAttack(packets);
        } else {
            for (int i = 0; i < packets; i++) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
            }
            if (this.swing.getValue()) {
                EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
            }
        }
    }

    // ============================================================
    //  辅助方法
    // ============================================================
    private Entity getTarget() {
        if (this.requireAura.getValue()) {
            if (Aura.INSTANCE != null && Aura.INSTANCE.isOn()) {
                Entity auraTarget = Aura.target;
                return isValidTarget(auraTarget) ? auraTarget : null;
            }
            return null;
        } else {
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
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isDead()) return false;
        if (entity instanceof PlayerEntity player && Alien.FRIEND.isFriend(player)) return false;
        return true;
    }

    private boolean isPlayerInWeb() {
        BlockPos pos = mc.player.getBlockPos();
        return mc.world.getBlockState(pos).isOf(Blocks.COBWEB)
                || mc.world.getBlockState(pos.up()).isOf(Blocks.COBWEB);
    }

    private boolean isPlayerBurrowed() {
        BlockPos pos = mc.player.getBlockPos();
        return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    private enum VoidPhase {
        IDLE, FALLING
    }
}