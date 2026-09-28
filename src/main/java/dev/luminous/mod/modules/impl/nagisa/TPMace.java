package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import dev.luminous.mod.modules.impl.nagisa.acpro.BasePlacer;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TPMace extends Module {
    public static TPMace INSTANCE;

    private final SliderSetting fallHeight = add(new SliderSetting("FallHeight", 4.0, 0.5, 5.0, 0.1));
    private final SliderSetting segments = add(new SliderSetting("Segments", 3.0, 2.0, 10.0, 1.0));
    private final SliderSetting resetDistance = add(new SliderSetting("ResetDistance", 0.1, 0.05, 0.5, 0.05));
    private final BooleanSetting requireMace = add(new BooleanSetting("RequireMace", false));
    private final BooleanSetting autoSwap = add(new BooleanSetting("AutoSwap", true));
    private final BooleanSetting timer = add(new BooleanSetting("Timer", true));
    private final SliderSetting timerMultiplier = add(new SliderSetting("TimerMultiplier", 1.5, 1.0, 5.0, 0.1,
            () -> timer.getValue()));
    private final SliderSetting attackPackets = add(new SliderSetting("PacketA", 1.0, 1.0, 10.0, 1.0));
    private final BooleanSetting noCrystal = add(new BooleanSetting("NoCrystal", false));
    private final BooleanSetting autoAttack = add(new BooleanSetting("AutoAttack", true));
    private final BooleanSetting requireAura = add(new BooleanSetting("RequireAura", true,
            () -> autoAttack.getValue()));
    private final SliderSetting attackRange = add(new SliderSetting("AttackRange", 6.0, 1.0, 10.0, 0.1,
            () -> autoAttack.getValue()));
    private final SliderSetting wallRange = add(new SliderSetting("WallRange", 5.5, 0.0, 8.0, 0.1,
            () -> autoAttack.getValue()));
    private final SliderSetting attackDelay = add(new SliderSetting("AttackDelay", 50.0, 0.0, 500.0, 10.0,
            () -> autoAttack.getValue()));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true,
            () -> autoAttack.getValue()));

    private double groundY;
    private boolean timerActive = false;
    private final Timer attackTimer = new Timer();

    public TPMace() {
        super("TPMace", Category.Mace);
        this.setChinese("TP锤");
        INSTANCE = this;
    }

    public static boolean shouldDisableNoFall() {
        return INSTANCE != null && INSTANCE.isOn();
    }

    @Override
    public void onEnable() {
        if (!nullCheck()) {
            groundY = findGroundY();
            timerActive = false;
            if (noCrystal.getValue()) {
                if (ACPro.INSTANCE != null && ACPro.INSTANCE.isOn()) {
                    ACPro.INSTANCE.disable();
                }
                if (BasePlacer.INSTANCE != null && BasePlacer.INSTANCE.isOn()) {
                    BasePlacer.INSTANCE.disable();
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (timerActive) {
            Alien.TIMER.reset();
            timerActive = false;
        }
        if (noCrystal.getValue()) {
            if (ACPro.INSTANCE != null && !ACPro.INSTANCE.isOn()) {
                ACPro.INSTANCE.enable();
            }
            if (BasePlacer.INSTANCE != null && !BasePlacer.INSTANCE.isOn()) {
                BasePlacer.INSTANCE.enable();
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || !event.isPre()) return;

        // AutoSwap：检查快捷栏是否有锤子，自动切换
        if (!handleAutoSwap()) return;

        if (requireMace.getValue() && mc.player.getMainHandStack().getItem() != Items.MACE) {
            if (timerActive) {
                Alien.TIMER.reset();
                timerActive = false;
            }
            return;
        }

        // Timer mode: accelerate ticks when falling to trigger mace damage faster
        if (timer.getValue()) {
            if (mc.player.getVelocity().y < 0.0 && !mc.player.isFallFlying()) {
                Alien.TIMER.set(timerMultiplier.getValueFloat());
                timerActive = true;
            } else if (timerActive) {
                Alien.TIMER.reset();
                timerActive = false;
            }
        }

        // Teleport up when too close to ground
        double fallTrigger = groundY + resetDistance.getValue();
        if (mc.player.getY() <= fallTrigger) {
            teleportUpInSegments();
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
        }

        // Auto attack
        if (autoAttack.getValue()) {
            Entity target = getTarget();
            if (target != null) {
                tryAttack(target);
            }
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

    private void tryAttack(Entity target) {
        if (!attackTimer.passedMs(attackDelay.getValueInt())) return;

        Vec3d targetPos = MathUtil.getClosestPointToBox(mc.player.getEyePos(), target.getBoundingBox());
        double distance = mc.player.getEyePos().distanceTo(targetPos);
        boolean canSee = mc.player.canSee(target);
        if (canSee) {
            if (distance > attackRange.getValue()) return;
        } else if (distance > wallRange.getValue()) {
            return;
        }

        if (requireAura.getValue() && Aura.INSTANCE != null && Aura.INSTANCE.isOn() && Aura.target != null) {
            Aura.INSTANCE.doMultiAttack(attackPackets.getValueInt());
        } else {
            for (int i = 0; i < attackPackets.getValueInt(); i++) {
                mc.getNetworkHandler().sendPacket(
                        PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
                );
            }
            if (swing.getValue()) {
                EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
            }
        }
        attackTimer.reset();
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
        return !(entity instanceof PlayerEntity player && Alien.FRIEND.isFriend(player));
    }

    private void teleportUpInSegments() {
        double x = mc.player.getX();
        double startY = mc.player.getY();
        double z = mc.player.getZ();
        double segmentHeight = fallHeight.getValue();
        int segmentCount = segments.getValueInt();
        double targetY = startY + segmentHeight * segmentCount;

        for (int step = 1; step <= segmentCount; step++) {
            double packetY = startY + segmentHeight * step;
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, packetY, z, false)
            );
        }

        mc.player.setPosition(x, targetY, z);
        mc.player.fallDistance = (float) (segmentHeight * segmentCount);
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
        return fallHeight.getValueInt() + "\u00d7" + segments.getValueInt();
    }

    private double findGroundY() {
        BlockPos pos = mc.player.getBlockPos();
        int worldBottom = mc.world.getBottomY();
        while (pos.getY() > worldBottom) {
            BlockPos below = pos.down();
            if (mc.world.getBlockState(below).blocksMovement()) {
                return below.getY() + 1.0;
            }
            pos = below;
        }
        return mc.player.getY();
    }

    public void updateGround() {
        groundY = findGroundY();
    }
}