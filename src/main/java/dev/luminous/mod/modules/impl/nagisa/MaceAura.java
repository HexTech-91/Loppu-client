package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.NoFall;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class MaceAura extends Module {
    public static MaceAura INSTANCE;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Bounce));

    private final SliderSetting range = add(new SliderSetting("Range", 6.0, 1.0, 10.0, 0.1));
    private final SliderSetting delay = add(new SliderSetting("Delay", 0, 0, 500, 10));
    private final SliderSetting multiAttack = add(new SliderSetting("MultiAttack", 1, 1, 10, 1));
    private final BooleanSetting autoSwitch = add(new BooleanSetting("AutoSwitch", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    private final BooleanSetting playerOnly = add(new BooleanSetting("PlayerOnly", true));
    private final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", true));
    private final BooleanSetting autoDisableNoFall = add(new BooleanSetting("AutoDisableNoFall", true));
    private final BooleanSetting pauseElytra = add(new BooleanSetting("PauseElytra", true));
    private final BooleanSetting noCrystal = add(new BooleanSetting("NoCrystal", true));

    private final SliderSetting height = add(new SliderSetting("Height", 15.0, 3.0, 100.0, 0.5));

    // JumpSpoof specific (方案五)
    private final SliderSetting jumpBase = add(new SliderSetting("JumpBase", 2.4, 1.1, 10.0, 0.01,
            () -> mode.is(Mode.JumpSpoof)));
    private final SliderSetting jumpSteps = add(new SliderSetting("JumpSteps", 0.5, 0.1, 5.0, 0.01,
            () -> mode.is(Mode.JumpSpoof)));

    private final Timer timer = new Timer();
    private boolean noFallWasOn = false;

    public MaceAura() {
        super("MaceAura", Category.Mace);
        this.setChinese("重锤光环");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (autoDisableNoFall.getValue()) {
            NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
            noFallWasOn = noFall != null && noFall.isOn();
            if (noFallWasOn && noFall != null) {
                noFall.disable();
            }
        }
        timer.reset();
    }

    @Override
    public void onDisable() {
        if (autoDisableNoFall.getValue() && noFallWasOn) {
            NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
            if (noFall != null && !noFall.isOn()) {
                noFall.enable();
            }
            noFallWasOn = false;
        }
    }

    @EventListener
    public void onClientTick(ClientTickEvent event) {
        if (nullCheck()) return;

        if (!timer.passedMs(delay.getValueInt())) return;

        if (mc.player.getMainHandStack().getItem() != Items.MACE) {
            if (autoSwitch.getValue()) {
                int slot = InventoryUtil.findItem(Items.MACE);
                if (slot == -1) return;
                InventoryUtil.switchToSlot(slot);
            } else {
                return;
            }
        }

        if (pauseElytra.getValue() && mc.player.isFallFlying()) return;
        if (onlyGround.getValue() && !mc.player.isOnGround()) return;

        Entity target = findTarget();
        if (target == null) return;

        if (rotate.getValue()) {
            Alien.ROTATION.lookAt(target.getBoundingBox().getCenter());
        }

        switch (mode.getValue()) {
            case Bounce -> doBounce();
            case JumpSpoof -> doJumpSpoof();
        }

        for (int i = 0; i < multiAttack.getValueInt(); i++) {
            mc.getNetworkHandler().sendPacket(
                    PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
            );
        }

        mc.player.swingHand(Hand.MAIN_HAND);
        timer.reset();
    }

    /**
     * 方案一：位置包回弹法 (Position Bounce)
     * 发阶梯式下降位置包，模拟自然坠落过程
     * 发包: Y+height → Y+height*0.7 → Y+height*0.3 → Y(onGround=true)
     */
    private void doBounce() {
        double h = height.getValue();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        if (h < 1.5) return;

        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + h, z, false)
        );
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + h * 0.7, z, false)
        );
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + h * 0.3, z, false)
        );
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true)
        );
    }

    /**
     * 方案五：优化版 JumpSpoof
     * 固定步长递进，比 GroundMace 的指数循环包更少，不卡脚
     * 发包: height*0.8 → height*0.5 → height*0.2 → 0
     */
    private void doJumpSpoof() {
        double targetHeight = height.getValue();
        if (targetHeight < 1.5) return;

        double baseVal = MathHelper.clamp(jumpBase.getValue(), 1.1, 1000.0);
        double stepVal = MathHelper.clamp(jumpSteps.getValue(), 0.1, 5.0);

        for (double i = 0.0; Math.pow(baseVal, i) < targetHeight; i += stepVal) {
            double y = mc.player.getY() + Math.min(targetHeight, Math.pow(baseVal, i));
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), y, mc.player.getZ(), false
                    )
            );
        }

        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(), true
                )
        );
    }

    /**
     * 判断给定玩家是否为好友（使用 Alien.FRIEND 系统）
     */
    private boolean isFriend(PlayerEntity player) {
        if (Alien.FRIEND != null) {
            try {
                return Alien.FRIEND.isFriend(player);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private Entity findTarget() {
        Entity closest = null;
        double minDist = Double.MAX_VALUE;
        double maxRange = range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (playerOnly.getValue() && !(entity instanceof PlayerEntity)) continue;
            if (noCrystal.getValue() && entity instanceof EndCrystalEntity) continue;

            // 如果实体是玩家且为好友，则跳过（永远不攻击好友）
            if (entity instanceof PlayerEntity && isFriend((PlayerEntity) entity)) {
                continue;
            }

            double dist = mc.player.distanceTo(entity);
            if (dist < minDist && dist <= maxRange) {
                minDist = dist;
                closest = entity;
            }
        }

        return closest;
    }

    private enum Mode {
        Bounce,
        JumpSpoof
    }
}