package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TravelEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.rotation.Rotation;
import dev.luminous.api.utils.rotation.RotationUtils;
import dev.luminous.asm.accessors.IFireworkRocketEntity;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class ElytraFly
        extends Module {
    public static ElytraFly INSTANCE;
    public final EnumSetting<Mode> mode = this.add(new EnumSetting<Mode>("Mode", Mode.Control));
    public final BooleanSetting infiniteDura = this.add(new BooleanSetting("InfiniteDura", false));
    public final BooleanSetting packet = this.add(new BooleanSetting("Packet", false).setParent());
    private final SliderSetting packetDelay = this.add(new SliderSetting("PacketDelay", 0.0, 0.0, 20.0, 1.0, this.packet::isOpen));
    private final BooleanSetting setFlag = this.add(new BooleanSetting("SetFlag", false, () -> !this.mode.is(Mode.Bounce)));
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", false).setParent());
    public final BindSetting fireWork = this.add(new BindSetting("FireWorkBind", -1, this.firework::isOpen));
    public final BooleanSetting packetInteract = this.add(new BooleanSetting("PacketInteract", true, this.firework::isOpen));
    public final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true, this.firework::isOpen));
    public final BooleanSetting onlyOne = this.add(new BooleanSetting("OnlyOne", true, this.firework::isOpen));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true, this.firework::isOpen));
    public final BooleanSetting autoJump = this.add(new BooleanSetting("AutoJump", true, () -> this.mode.is(Mode.Bounce)));
    public final SliderSetting upPitch = this.add(new SliderSetting("UpPitch", 0.0, 0.0, 90.0, () -> this.mode.getValue() == Mode.Control));
    public final SliderSetting upFactor = this.add(new SliderSetting("UpFactor", 1.0, 0.0, 10.0, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    public final SliderSetting downFactor = this.add(new SliderSetting("FallSpeed", 1.0, 0.0, 10.0, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    public final SliderSetting speed = this.add(new SliderSetting("Speed", 1.0, 0.0, 10.0, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    
    // SpeedRamp 速度渐进
    public final BooleanSetting speedRamp = this.add(new BooleanSetting("SpeedRamp", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)).setParent());
    public final SliderSetting rampStartSpeed = this.add(new SliderSetting("RampStartSpeed", 0.7, 0.0, 10.0, 0.01, () -> this.speedRamp.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final SliderSetting rampIncreasePerSecond = this.add(new SliderSetting("RampPerSecond", 0.5, 0.0, 10.0, 0.01, () -> this.speedRamp.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final SliderSetting rampTargetSpeed = this.add(new SliderSetting("RampTargetSpeed", 2.5, 0.0, 10.0, 0.01, () -> this.speedRamp.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final BooleanSetting onlyResistance = this.add(new BooleanSetting("OnlyResistance", false, () -> this.speedRamp.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    
    // TurtleSpeed 神龟速度
    public final BooleanSetting turtleSpeedToggle = this.add(new BooleanSetting("TurtleSpeed", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)).setParent());
    public final SliderSetting turtleSpeed = this.add(new SliderSetting("TurtleHelmetSpeed", 0.7, 0.0, 10.0, () -> this.turtleSpeedToggle.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final SliderSetting turtleNormalSpeed = this.add(new SliderSetting("TurtleNormalSpeed", 3.0, 0.0, 10.0, () -> this.turtleSpeedToggle.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    
    // ResistanceAdapt 抗性适应
    public final BooleanSetting resistanceAdapt = this.add(new BooleanSetting("ResistanceAdapt", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)).setParent());
    public final SliderSetting resistanceStrength = this.add(new SliderSetting("ResistanceStrength", 0.7, 0.0, 1.0, 0.01, () -> this.resistanceAdapt.isOpen() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    
    public final BooleanSetting speedLimit = this.add(new BooleanSetting("SpeedLimit", true, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    public final SliderSetting maxSpeed = this.add(new SliderSetting("MaxSpeed", 2.5, 0.1, 10.0, () -> this.speedLimit.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final BooleanSetting noDrag = this.add(new BooleanSetting("NoDrag", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    public final BooleanSetting followPlayer = this.add(new BooleanSetting("FollowPlayer", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    public final SliderSetting range = this.add(new SliderSetting("Range", 64.0, 1.0, 256.0, 1.0, () -> this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final SliderSetting fov = this.add(new SliderSetting("Fov", 180.0, 0.0, 360.0, 1.0, () -> this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    public final SliderSetting count = this.add(new SliderSetting("Count", 1, 1, 8, () -> this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))));
    private final BooleanSetting targetSwitch = this.add(new BooleanSetting("TargetSwitch", false, () -> this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))).setParent());
    public final BindSetting nextTarget = this.add(new BindSetting("NextTarget", -1, this.targetSwitch::isOpen));
    private final SliderSetting switchDelay = this.add(new SliderSetting("SwitchDelay", 0.0, 0.0, 5000.0, 50.0, this.targetSwitch::isOpen));
    private final BooleanSetting avoid = this.add(new BooleanSetting("Avoid", true, () -> this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))).setParent());
    private final SliderSetting avoidRange = this.add(new SliderSetting("AvoidRange", 4.0, 0.5, 12.0, 0.5, this.avoid::isOpen));
    private final SliderSetting avoidUp = this.add(new SliderSetting("AvoidUp", 1.2, 0.0, 5.0, 0.1, this.avoid::isOpen));
    private final SliderSetting avoidSide = this.add(new SliderSetting("AvoidSide", 0.8, 0.0, 3.0, 0.1, this.avoid::isOpen));
    // 抗性4限速
    private final BooleanSetting resistanceLimit = this.add(new BooleanSetting("ResistanceLimit", false, () -> this.mode.is(Mode.Control)).setParent());
    private final BooleanSetting smartLimit = this.add(new BooleanSetting("SmartLimit", false, this.resistanceLimit::isOpen));

    // SmartSpeed 神龟加速
    private final BooleanSetting smartSpeed = this.add(new BooleanSetting("SmartSpeed", false, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)).setParent());
    private final SliderSetting glideTime = this.add(new SliderSetting("GlideTime", 5.0, 1.0, 30.0, 0.5, this.smartSpeed::isOpen).setSuffix("s"));

    public final Timer fireworkTimer = new Timer();
    private final BooleanSetting autoStop = this.add(new BooleanSetting("AutoStop", true));
    private final BooleanSetting sprint = this.add(new BooleanSetting("Sprint", true, () -> this.mode.is(Mode.Bounce)));
    private final SliderSetting pitch = this.add(new SliderSetting("Pitch", 88.0, -90.0, 90.0, 0.1, () -> this.mode.is(Mode.Bounce)));
    private final BooleanSetting instantFly = this.add(new BooleanSetting("AutoStart", true, () -> !this.mode.is(Mode.Bounce)));
    private final BooleanSetting checkSpeed = this.add(new BooleanSetting("CheckSpeed", false, () -> !this.mode.is(Mode.Bounce)));
    public final SliderSetting minSpeed = this.add(new SliderSetting("MinSpeed", 70.0, 0.1, 200.0, () -> !this.mode.is(Mode.Bounce)));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 1000.0, 0.0, 20000.0, 50.0, () -> !this.mode.is(Mode.Bounce)));
    private final SliderSetting timeout = this.add(new SliderSetting("Timeout", 0.0, 0.1, 1.0, 0.1, () -> !this.mode.is(Mode.Bounce)));
    private final SliderSetting sneakDownSpeed = this.add(new SliderSetting("DownSpeed", 1.0, 0.1, 10.0, () -> this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)));
    private final SliderSetting boost = this.add(new SliderSetting("Boost", 1.0, 0.1, 4.0, () -> this.mode.getValue() == Mode.Boost));
    private final BooleanSetting freeze = this.add(new BooleanSetting("Freeze", false, () -> this.mode.is(Mode.Rotation)));
    private final BooleanSetting motionStop = this.add(new BooleanSetting("MotionStop", false, () -> this.mode.is(Mode.Rotation)));
    private final SliderSetting infiniteMaxSpeed = this.add(new SliderSetting("InfiniteMaxSpeed", 150.0, 50.0, 170.0, () -> this.mode.getValue() == Mode.Pitch));
    private final SliderSetting infiniteMinSpeed = this.add(new SliderSetting("InfiniteMinSpeed", 25.0, 10.0, 70.0, () -> this.mode.getValue() == Mode.Pitch));
    private final SliderSetting infiniteMaxHeight = this.add(new SliderSetting("InfiniteMaxHeight", 200, -50, 360, () -> this.mode.getValue() == Mode.Pitch));
    public final BooleanSetting releaseSneak = this.add(new BooleanSetting("ReleaseSneak", false));
    private final Timer instantFlyTimer = new Timer();
    private final Timer followSwitchTimer = new Timer();
    private final Timer smartSpeedTimer = new Timer();
    private final Timer speedRampTimer = new Timer();
    private boolean wasSmartSpeedActive = false;
    private boolean nextTargetPressed;
    private int followSwitchIndex;
    private double currentRampSpeed;
    private boolean rampInitialized;
    boolean prev;
    float prePitch;
    private boolean hasElytra = false;
    float yaw = 0.0f;
    float rotationPitch = 0.0f;
    boolean flying = false;
    int packetDelayInt = 0;
    private boolean down;
    private float lastInfinitePitch;
    private float infinitePitch;

    public ElytraFly() {
        super("ElytraFly", Module.Category.Movement);
        this.setChinese("鞘翅飞行");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new FireWorkTweak());
    }

    public void off() {
        int firework;
        if (this.inventory.getValue() && !EntityUtil.inInventory()) return;
        if (this.onlyOne.getValue() && this.isFireworkActive()) return;
        ElytraFly.INSTANCE.fireworkTimer.reset();
        if (ElytraFly.mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
            if (this.packetInteract.getValue()) {
                ElytraFly.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            } else {
                ElytraFly.mc.interactionManager.interactItem((PlayerEntity)ElytraFly.mc.player, Hand.MAIN_HAND);
            }
        } else if (this.inventory.getValue() && (firework = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)) != -1) {
            InventoryUtil.inventorySwap(firework, ElytraFly.mc.player.getInventory().selectedSlot);
            if (this.packetInteract.getValue()) {
                ElytraFly.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            } else {
                ElytraFly.mc.interactionManager.interactItem((PlayerEntity)ElytraFly.mc.player, Hand.MAIN_HAND);
            }
            InventoryUtil.inventorySwap(firework, ElytraFly.mc.player.getInventory().selectedSlot);
            EntityUtil.syncInventory();
        } else {
            firework = InventoryUtil.findItem(Items.FIREWORK_ROCKET);
            if (firework != -1) {
                int old = ElytraFly.mc.player.getInventory().selectedSlot;
                InventoryUtil.switchToSlot(firework);
                if (this.packetInteract.getValue()) {
                    ElytraFly.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                } else {
                    ElytraFly.mc.interactionManager.interactItem((PlayerEntity)ElytraFly.mc.player, Hand.MAIN_HAND);
                }
                InventoryUtil.switchToSlot(old);
            }
        }
    }

    private boolean isFireworkActive() {
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (entity instanceof FireworkRocketEntity) {
                IFireworkRocketEntity fireworkEntity = (IFireworkRocketEntity) entity;
                if (fireworkEntity.getShooter() == ElytraFly.mc.player) return true;
            }
        }
        return false;
    }

    public static boolean recastElytra(ClientPlayerEntity player) {
        if (ElytraFly.checkConditions(player) && ElytraFly.ignoreGround(player)) {
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            if (ElytraFly.INSTANCE.setFlag.getValue()) ElytraFly.mc.player.startFallFlying();
            return true;
        }
        return false;
    }

    public static boolean checkConditions(ClientPlayerEntity player) {
        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
        return !player.getAbilities().flying && !player.hasVehicle() && !player.isClimbing() && itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack);
    }

    private static boolean ignoreGround(ClientPlayerEntity player) {
        if (!player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                player.startFallFlying();
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (ElytraFly.nullCheck()) return;
        this.hasElytra = false;
        this.yaw = ElytraFly.mc.player.getYaw();
        this.rotationPitch = ElytraFly.mc.player.getPitch();
        this.smartSpeedTimer.reset();
        this.resetSpeedRamp();
    }

    @Override
    public void onDisable() {
        this.resetSpeedRamp();
        if (ElytraFly.nullCheck()) return;
        if (this.releaseSneak.getValue()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        }
    }

    private void boost() {
        if (this.hasElytra) {
            if (!this.isFallFlying()) return;
            float yaw = (float)Math.toRadians(ElytraFly.mc.player.getYaw());
            if (ElytraFly.mc.options.forwardKey.isPressed()) {
                ElytraFly.mc.player.addVelocity(-MathHelper.sin(yaw) * this.boost.getValueFloat() / 10.0f, 0.0, MathHelper.cos(yaw) * this.boost.getValueFloat() / 10.0f);
            }
        }
    }

    @EventListener(priority=-9999)
    public void onRotation(UpdateRotateEvent event) {
        if (ElytraFly.nullCheck()) return;
        if ((this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) && this.followPlayer.getValue() && this.hasElytra && this.isFallFlying()) {
            Vec3d targetPos = this.getFollowTargetPos();
            if (targetPos != null) {
                Rotation rot = RotationUtils.calculate(ElytraFly.mc.player.getEyePos(), targetPos);
                event.setYaw(rot.getYaw());
                event.setPitch(rot.getPitch());
            }
        }
        if (this.mode.is(Mode.Rotation)) {
            if (this.isFallFlying()) {
                if (MovementUtil.isMoving()) {
                    if (ElytraFly.mc.options.jumpKey.isPressed()) {
                        this.rotationPitch = -45.0f;
                    } else if (ElytraFly.mc.options.sneakKey.isPressed()) {
                        this.rotationPitch = 45.0f;
                    } else {
                        this.rotationPitch = -1.9f;
                        if (this.motionStop.getValue()) this.setY(0.0);
                    }
                } else if (ElytraFly.mc.options.jumpKey.isPressed()) {
                    this.rotationPitch = -89.0f;
                } else if (ElytraFly.mc.options.sneakKey.isPressed()) {
                    this.rotationPitch = 89.0f;
                } else if (this.motionStop.getValue()) {
                    this.setY(0.0);
                }
                if (ElytraFly.mc.options.forwardKey.isPressed() || ElytraFly.mc.options.backKey.isPressed() || ElytraFly.mc.options.leftKey.isPressed() || ElytraFly.mc.options.rightKey.isPressed()) {
                    this.yaw = Sprint.getSprintYaw(ElytraFly.mc.player.getYaw());
                } else if (this.motionStop.getValue()) {
                    this.setX(0.0);
                    this.setZ(0.0);
                }
                event.setYaw(this.yaw);
                event.setPitch(this.rotationPitch);
            }
        } else if (this.mode.is(Mode.Pitch)) {
            if (this.isFallFlying()) event.setPitch(this.infinitePitch);
        } else if (this.mode.is(Mode.Bounce) && this.isFallFlying()) {
            event.setPitch(this.pitch.getValueFloat());
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // SmartSpeed: 检测神龟效果状态变化并管理计时器
        boolean isNowSmartSpeedActive = isSmartSpeedActive();
        if (isNowSmartSpeedActive && !wasSmartSpeedActive) {
            // 神龟效果激活，开始平滑加速计时
            smartSpeedTimer.reset();
        } else if (!isNowSmartSpeedActive && wasSmartSpeedActive) {
            // 神龟效果消失，重置计时器
            smartSpeedTimer.reset();
        }
        wasSmartSpeedActive = isNowSmartSpeedActive;

        this.getInfinitePitch();
        this.flying = false;
        if (this.packet.getValue()) {
            this.hasElytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA) != -1;
        } else {
            this.hasElytra = false;
            for (ItemStack is : ElytraFly.mc.player.getArmorItems()) {
                if (is.getItem() instanceof ElytraItem) {
                    this.hasElytra = true;
                    break;
                }
            }
            if (this.infiniteDura.getValue() && !ElytraFly.mc.player.isOnGround() && this.hasElytra) {
                this.flying = true;
                ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (this.setFlag.getValue()) ElytraFly.mc.player.startFallFlying();
            }
            if (this.mode.is(Mode.Bounce)) {
                ((ILivingEntity)ElytraFly.mc.player).setLastJumpCooldown(0);
                return;
            }
        }

        boolean followActive = this.followPlayer.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) && this.hasElytra && this.isFallFlying();
        if (!followActive || !this.targetSwitch.getValue()) {
            this.followSwitchIndex = 0;
            this.nextTargetPressed = false;
            this.followSwitchTimer.reset();
        } else {
            boolean pressed = this.nextTarget.isPressed();
            if (pressed && !this.nextTargetPressed) {
                ++this.followSwitchIndex;
                this.followSwitchTimer.reset();
            }
            this.nextTargetPressed = pressed;
            int delayMs = this.switchDelay.getValueInt();
            if (delayMs > 0 && this.followSwitchTimer.passed(delayMs)) {
                ++this.followSwitchIndex;
                this.followSwitchTimer.reset();
            }
        }

        double x = ElytraFly.mc.player.getX() - ElytraFly.mc.player.prevX;
        double y = ElytraFly.mc.player.getY() - ElytraFly.mc.player.prevY;
        double z = ElytraFly.mc.player.getZ() - ElytraFly.mc.player.prevZ;
        double dist = Math.sqrt(x * x + z * z + y * y) / 1000.0;
        double div = 1.388888888888889E-5;
        float timer = Alien.TIMER.get();
        double currentSpeed = dist / div * timer;
        if (this.mode.getValue() == Mode.Boost) this.boost();

        // SmartLimit 烟花逻辑
        if (this.resistanceLimit.getValue() && this.smartLimit.getValue()
                && mc.player.hasStatusEffect(StatusEffects.RESISTANCE)
                && mc.player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() >= 3
                && MovementUtil.isMoving()
                && InventoryUtil.findItem(Items.FIREWORK_ROCKET) != -1
                && !this.isFireworkActive()
                && this.fireworkTimer.passed(this.delay.getValueInt())) {
            this.off();
        }

        if (this.packet.getValue()) {
            if (ElytraFly.mc.player.isOnGround()) return;
            ++this.packetDelayInt;
            if ((double)this.packetDelayInt <= this.packetDelay.getValue()) return;
            int elytra = InventoryUtil.findItem(Items.ELYTRA);
            if (elytra != -1) {
                ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, ElytraFly.mc.player);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                ElytraFly.mc.player.startFallFlying();
                if ((!this.checkSpeed.getValue() || currentSpeed <= this.minSpeed.getValue()) && this.firework.getValue() && this.fireworkTimer.passed(this.delay.getValueInt()) && (MovementUtil.isMoving() || this.mode.is(Mode.Rotation) && ElytraFly.mc.options.jumpKey.isPressed()) && (!ElytraFly.mc.player.isUsingItem() || !this.usingPause.getValue()) && this.isFallFlying()) {
                    this.off();
                    this.fireworkTimer.reset();
                }
                ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, ElytraFly.mc.player);
                this.packetDelayInt = 0;
            } else {
                elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
                if (elytra != -1) {
                    ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                    ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    ElytraFly.mc.player.startFallFlying();
                    if ((!this.checkSpeed.getValue() || currentSpeed <= this.minSpeed.getValue()) && this.firework.getValue() && this.fireworkTimer.passed(this.delay.getValueInt()) && (MovementUtil.isMoving() || this.mode.is(Mode.Rotation) && ElytraFly.mc.options.jumpKey.isPressed()) && (!ElytraFly.mc.player.isUsingItem() || !this.usingPause.getValue()) && this.isFallFlying()) {
                        this.off();
                        this.fireworkTimer.reset();
                    }
                    ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                    ElytraFly.mc.interactionManager.clickSlot(ElytraFly.mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, ElytraFly.mc.player);
                    this.packetDelayInt = 0;
                }
            }
            return;
        }
        if ((!this.checkSpeed.getValue() || currentSpeed <= this.minSpeed.getValue()) && this.firework.getValue() && this.fireworkTimer.passed(this.delay.getValueInt()) && (MovementUtil.isMoving() || this.mode.is(Mode.Rotation) && ElytraFly.mc.options.jumpKey.isPressed()) && (!ElytraFly.mc.player.isUsingItem() || !this.usingPause.getValue()) && this.isFallFlying()) {
            this.off();
            this.fireworkTimer.reset();
        }
        if (!this.isFallFlying() && this.hasElytra) {
            this.fireworkTimer.setMs(99999999L);
            if (!ElytraFly.mc.player.isOnGround() && this.instantFly.getValue() && ElytraFly.mc.player.getVelocity().getY() < 0.0 && !this.infiniteDura.getValue()) {
                if (!this.instantFlyTimer.passed((long)(1000.0 * this.timeout.getValue()))) return;
                this.instantFlyTimer.reset();
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (this.setFlag.getValue()) ElytraFly.mc.player.startFallFlying();
            }
        }
    }

    protected final Vec3d getRotationVector(float pitch, float yaw) {
        float f = pitch * ((float)Math.PI / 180);
        float g = -yaw * ((float)Math.PI / 180);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    public Vec3d getRotationVec(float tickDelta) {
        return this.getRotationVector(-this.upPitch.getValueFloat(), ElytraFly.mc.player.getYaw(tickDelta));
    }

    @EventListener
    private void onPlayerMove(MoveEvent event) {
        if (this.autoStop.getValue() && this.isFallFlying()) {
            int chunkX = (int)(ElytraFly.mc.player.getX() / 16.0);
            int chunkZ = (int)(ElytraFly.mc.player.getZ() / 16.0);
            if (!ElytraFly.mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) event.cancel();
        }
    }

    @EventListener
    private void onTick(ClientTickEvent event) {
        if (ElytraFly.nullCheck()) return;
        if (this.mode.is(Mode.Bounce) && this.hasElytra) {
            if (this.autoJump.getValue()) ElytraFly.mc.options.jumpKey.setPressed(true);
            if (event.isPost()) {
                if (!this.isFallFlying()) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(ElytraFly.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                if (ElytraFly.checkConditions(ElytraFly.mc.player) && !this.sprint.getValue()) {
                    if (this.isFallFlying()) ElytraFly.mc.player.setSprinting(ElytraFly.mc.player.isOnGround());
                    else ElytraFly.mc.player.setSprinting(true);
                }
            } else if (ElytraFly.checkConditions(ElytraFly.mc.player) && this.sprint.getValue()) {
                ElytraFly.mc.player.setSprinting(true);
            }
        }
    }

    @EventListener
    private void onPacketSend(PacketEvent.Send event) {
        if (ElytraFly.nullCheck()) return;
        if (this.mode.is(Mode.Bounce) && this.hasElytra && event.getPacket() instanceof ClientCommandC2SPacket && ((ClientCommandC2SPacket)event.getPacket()).getMode().equals(ClientCommandC2SPacket.Mode.START_FALL_FLYING) && !this.sprint.getValue()) {
            ElytraFly.mc.player.setSprinting(true);
        }
    }

    @EventListener
    private void onPacketReceive(PacketEvent.Receive event) {
        if (ElytraFly.nullCheck()) return;
        // SpeedRamp: 服务器位置校正时重置
        if (this.speedRamp.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.resetSpeedRamp();
        }
        if (this.mode.is(Mode.Bounce) && this.hasElytra && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            ElytraFly.mc.player.stopFallFlying();
        }
    }

    @EventListener
    public void travel(TravelEvent event) {
        if (ElytraFly.nullCheck()) return;
        if (!AntiCheat.INSTANCE.movementSync()) {
            if (this.mode.is(Mode.Bounce) && this.hasElytra) {
                if (event.isPre()) {
                    this.prev = true;
                    this.prePitch = ElytraFly.mc.player.getPitch();
                    ElytraFly.mc.player.setPitch(this.pitch.getValueFloat());
                } else if (this.prev) {
                    this.prev = false;
                    ElytraFly.mc.player.setPitch(this.prePitch);
                }
            } else if (this.mode.is(Mode.Pitch) && this.isFallFlying()) {
                if (event.isPre()) {
                    this.prev = true;
                    this.prePitch = ElytraFly.mc.player.getPitch();
                    ElytraFly.mc.player.setPitch(this.lastInfinitePitch);
                } else if (this.prev) {
                    this.prev = false;
                    ElytraFly.mc.player.setPitch(this.prePitch);
                }
            }
            
            // SpeedRamp: 碰撞时重置
            if (this.speedRamp.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) && this.isFallFlying()) {
                if (ElytraFly.mc.player.horizontalCollision || ElytraFly.mc.player.verticalCollision || ElytraFly.mc.player.isInsideWall()) {
                    this.resetSpeedRamp();
                }
            }
        }
    }

    private double getEffectiveSpeed() {
        // TurtleSpeed 神龟速度：根据是否有神龟效果调整速度
        if (this.turtleSpeedToggle.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))) {
            if (ElytraFly.mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                return this.turtleSpeed.getValue();
            } else {
                return this.turtleNormalSpeed.getValue();
            }
        }
        
        // SpeedRamp 速度渐进：从rampStartSpeed平滑加速到rampTargetSpeed
        if (this.speedRamp.getValue() && (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze))) {
            // OnlyResistance: 只在有神龟效果时才启用SpeedRamp
            if (this.onlyResistance.getValue()) {
                if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                    return this.getRampedSpeed();
                } else {
                    return this.speed.getValue();
                }
            } else {
                return this.getRampedSpeed();
            }
        }
        
        if (isResistanceLimited()) {
            if (smartLimit.getValue() && InventoryUtil.findItem(Items.FIREWORK_ROCKET) != -1) return this.speed.getValue();
            return 0.7;
        }
        // SmartSpeed 神龟加速：从0平滑加速到speed
        if (isSmartSpeedActive()) {
            double targetSpeed = this.speed.getValue();
            double glideTimeSec = glideTime.getValue();
            long elapsedMs = smartSpeedTimer.getMs();
            double elapsedSec = elapsedMs / 1000.0;

            if (elapsedSec >= glideTimeSec) {
                return targetSpeed;
            } else {
                // 从0平滑加速到speed
                double progress = elapsedSec / glideTimeSec;
                // 使用 ease-out 缓动
                progress = 1 - Math.pow(1 - progress, 3);
                return targetSpeed * progress;
            }
        }
        return this.speed.getValue();
    }

    private boolean isResistanceLimited() {
        return resistanceLimit.getValue()
                && mc.player.hasStatusEffect(StatusEffects.RESISTANCE)
                && mc.player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() >= 3;
    }

    private boolean isSmartSpeedActive() {
        return smartSpeed.getValue()
                && (mode.is(Mode.Control) || mode.is(Mode.Freeze))
                && mc.player.hasStatusEffect(StatusEffects.RESISTANCE)
                && mc.player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() >= 3
                && mc.player.hasStatusEffect(StatusEffects.SLOWNESS);
    }
    
    /**
     * SpeedRamp: 获取渐进速度
     */
    private double getRampedSpeed() {
        if (!MovementUtil.isMoving()) {
            this.currentRampSpeed = this.rampStartSpeed.getValue();
            this.speedRampTimer.reset();
            this.rampInitialized = true;
            return this.currentRampSpeed;
        }
        
        if (!this.rampInitialized) {
            this.currentRampSpeed = this.rampStartSpeed.getValue();
            this.speedRampTimer.reset();
            this.rampInitialized = true;
            return this.currentRampSpeed;
        }
        
        double elapsedSeconds = this.speedRampTimer.getMs() / 1000.0;
        if (elapsedSeconds > 0.0) {
            this.currentRampSpeed = Math.min(
                    this.rampTargetSpeed.getValue(),
                    this.currentRampSpeed + elapsedSeconds * this.rampIncreasePerSecond.getValue()
            );
            this.speedRampTimer.reset();
        }
        
        return this.currentRampSpeed;
    }
    
    /**
     * SpeedRamp: 重置渐进速度
     */
    private void resetSpeedRamp() {
        this.currentRampSpeed = this.rampStartSpeed.getValue();
        this.rampInitialized = false;
        this.speedRampTimer.reset();
    }
    
    /**
     * ResistanceAdapt: 应用抗性适应阻力
     */
    private void applyResistanceAdapt() {
        double motionX = this.getX();
        double motionY = this.getY();
        double motionZ = this.getZ();
        double horizontalSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double descendBoost = MathHelper.clamp(-motionY * 2.0, 0.0, 1.0);
        double ascendPenalty = MathHelper.clamp(motionY * 2.0, 0.0, 1.0);
        double pitchWeight = Math.abs(ElytraFly.mc.player.getPitch()) / 90.0;
        double speedWeight = this.speedLimit.getValue()
                ? MathHelper.clamp(horizontalSpeed / Math.max(0.1, this.maxSpeed.getValue()), 0.0, 1.0)
                : MathHelper.clamp(horizontalSpeed / Math.max(0.1, this.speed.getValue()), 0.0, 1.0);
        double targetHorizontalDrag = MathHelper.clamp(0.91 + descendBoost * 0.04 + speedWeight * 0.02 - ascendPenalty * 0.03 + pitchWeight * 0.01, 0.88, 0.995);
        double targetVerticalDrag = MathHelper.clamp(0.90 + descendBoost * 0.06 + pitchWeight * 0.03 - ascendPenalty * 0.05, 0.85, 0.995);
        double strength = this.resistanceStrength.getValue();
        double horizontalDrag = this.lerp(0.985, targetHorizontalDrag, strength);
        double verticalDrag = this.lerp(0.99, targetVerticalDrag, strength);
        this.setX(motionX * horizontalDrag);
        this.setY(motionY * verticalDrag);
        this.setZ(motionZ * horizontalDrag);
    }
    
    private double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    @EventListener
    public void onMove(TravelEvent event) {
        if (ElytraFly.nullCheck() || !this.hasElytra || !this.isFallFlying() || event.isPost()) return;

        if ((this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) && this.followPlayer.getValue()) {
            Vec3d targetPos = this.getFollowTargetPos();
            if (targetPos != null) {
                this.applyFollowMotion(targetPos);
                if (this.mode.is(Mode.Control) || this.mode.is(Mode.Freeze)) {
                    event.cancel();
                    ElytraFly.mc.player.move(MovementType.SELF, ElytraFly.mc.player.getVelocity());
                }
                return;
            }
        }

        if ((this.mode.is(Mode.Freeze) || this.mode.is(Mode.Rotation) && this.freeze.getValue()) && !MovementUtil.isMoving() && !ElytraFly.mc.options.jumpKey.isPressed() && !ElytraFly.mc.options.sneakKey.isPressed()) {
            event.cancel();
            return;
        }

        if (this.mode.getValue() == Mode.Control) {
            if (this.firework.getValue()) {
                if (!ElytraFly.mc.options.sneakKey.isPressed() || !ElytraFly.mc.player.input.jumping) {
                    if (ElytraFly.mc.options.sneakKey.isPressed()) {
                        this.setY(-this.sneakDownSpeed.getValue());
                    } else if (ElytraFly.mc.player.input.jumping) {
                        this.setY(this.upFactor.getValue());
                    } else {
                        this.setY(-3.0E-11 * this.downFactor.getValue());
                    }
                } else {
                    this.setY(0.0);
                }
                double[] dir = MovementUtil.directionSpeed(getEffectiveSpeed());
                this.setX(dir[0]);
                this.setZ(dir[1]);
            } else {
                Vec3d lookVec = this.getRotationVec(mc.getRenderTickCounter().getTickDelta(true));
                double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
                double motionDist = Math.sqrt(this.getX() * this.getX() + this.getZ() * this.getZ());
                if (ElytraFly.mc.player.input.sneaking) {
                    this.setY(-this.sneakDownSpeed.getValue());
                } else if (!ElytraFly.mc.player.input.jumping) {
                    this.setY(-3.0E-11 * this.downFactor.getValue());
                }
                if (ElytraFly.mc.player.input.jumping) {
                    if (motionDist > this.upFactor.getValue() / this.upFactor.getMax()) {
                        double rawUpSpeed = motionDist * 0.01325;
                        this.setY(this.getY() + rawUpSpeed * 3.2);
                        this.setX(this.getX() - lookVec.x * rawUpSpeed / lookDist);
                        this.setZ(this.getZ() - lookVec.z * rawUpSpeed / lookDist);
                    } else {
                        double[] dir = MovementUtil.directionSpeed(getEffectiveSpeed());
                        this.setX(dir[0]);
                        this.setZ(dir[1]);
                    }
                }
                if (lookDist > 0.0) {
                    this.setX(this.getX() + (lookVec.x / lookDist * motionDist - this.getX()) * 0.1);
                    this.setZ(this.getZ() + (lookVec.z / lookDist * motionDist - this.getZ()) * 0.1);
                }
                if (!ElytraFly.mc.player.input.jumping) {
                    double[] dir = MovementUtil.directionSpeed(getEffectiveSpeed());
                    this.setX(dir[0]);
                    this.setZ(dir[1]);
                }
                if (!this.noDrag.getValue()) {
                    // ResistanceAdapt: 抗性适应阻力
                    if (this.resistanceAdapt.getValue()) {
                        this.applyResistanceAdapt();
                    } else {
                        this.setY(this.getY() * 0.99);
                        this.setX(this.getX() * 0.98);
                        this.setZ(this.getZ() * 0.99);
                    }
                }
                double finalDist = Math.sqrt(this.getX() * this.getX() + this.getZ() * this.getZ());
                if (this.speedLimit.getValue() && finalDist > this.maxSpeed.getValue()) {
                    this.setX(this.getX() * this.maxSpeed.getValue() / finalDist);
                    this.setZ(this.getZ() * this.maxSpeed.getValue() / finalDist);
                }
                event.cancel();
                ElytraFly.mc.player.move(MovementType.SELF, ElytraFly.mc.player.getVelocity());
            }
        }
    }

    private void applyFollowMotion(Vec3d targetPos) {
        Vec3d delta = targetPos.subtract(ElytraFly.mc.player.getPos());
        double distXZ = Math.hypot(delta.x, delta.z);
        double dy = targetPos.y - ElytraFly.mc.player.getEyeY();

        if (ElytraFly.mc.player.input.sneaking) {
            this.setY(-this.sneakDownSpeed.getValue());
        } else if (ElytraFly.mc.player.input.jumping) {
            this.setY(this.upFactor.getValue());
        } else {
            if (dy > 0.35) {
                this.setY(Math.min(this.upFactor.getValue(), 0.15 + dy * 0.35));
            } else if (dy < -1.5) {
                this.setY(-this.sneakDownSpeed.getValue());
            } else {
                this.setY(-3.0E-11 * this.downFactor.getValue());
            }
        }

        if (distXZ > 1.0E-4) {
            double spd = getEffectiveSpeed();
            double slowRange = 3.0;
            double factor = MathHelper.clamp(distXZ / slowRange, 0.0, 1.0);
            if (distXZ < 1.0) factor = 0.0;
            spd *= factor;
            this.setX(delta.x / distXZ * spd);
            this.setZ(delta.z / distXZ * spd);
        } else {
            this.setX(0.0);
            this.setZ(0.0);
        }

        if (this.avoid.getValue() && ElytraFly.mc.world != null && distXZ > 1.0E-4) {
            Vec3d start = ElytraFly.mc.player.getPos().add(0.0, ElytraFly.mc.player.getHeight() * 0.5, 0.0);
            Vec3d forward = new Vec3d(delta.x / distXZ, 0.0, delta.z / distXZ);
            double checkDist = this.avoidRange.getValue();
            double forwardHitDist = this.raycastHitDist(start, forward, checkDist);
            if (forwardHitDist <= checkDist) {
                Vec3d left = new Vec3d(-forward.z, 0.0, forward.x);
                Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
                Vec3d leftDir = forward.add(left.multiply(0.75));
                Vec3d rightDir = forward.add(right.multiply(0.75));
                if (leftDir.lengthSquared() > 1.0E-8) leftDir = leftDir.normalize();
                else leftDir = forward;
                if (rightDir.lengthSquared() > 1.0E-8) rightDir = rightDir.normalize();
                else rightDir = forward;
                double leftHitDist = this.raycastHitDist(start, leftDir, checkDist);
                double rightHitDist = this.raycastHitDist(start, rightDir, checkDist);
                Vec3d side = leftHitDist > rightHitDist ? left : right;
                double sidePush = this.avoidSide.getValue();
                this.setX(this.getX() + side.x * sidePush);
                this.setZ(this.getZ() + side.z * sidePush);
                if (!ElytraFly.mc.player.input.jumping && !ElytraFly.mc.player.input.sneaking) {
                    this.setY(Math.max(this.getY(), this.avoidUp.getValue()));
                }
            }
        }
        
        if (!this.noDrag.getValue()) {
            // ResistanceAdapt: 抗性适应阻力
            if (this.resistanceAdapt.getValue()) {
                this.applyResistanceAdapt();
            } else {
                this.setY(this.getY() * 0.99);
                this.setX(this.getX() * 0.98);
                this.setZ(this.getZ() * 0.99);
            }
        }
        double finalDist = Math.sqrt(this.getX() * this.getX() + this.getZ() * this.getZ());
        if (this.speedLimit.getValue() && finalDist > this.maxSpeed.getValue()) {
            this.setX(this.getX() * this.maxSpeed.getValue() / finalDist);
            this.setZ(this.getZ() * this.maxSpeed.getValue() / finalDist);
        }
    }

    private double getX() { return MovementUtil.getMotionX(); }
    private void setX(double f) { MovementUtil.setMotionX(f); }
    private double getY() { return MovementUtil.getMotionY(); }
    private void setY(double f) { MovementUtil.setMotionY(f); }
    private double getZ() { return MovementUtil.getMotionZ(); }
    private void setZ(double f) { MovementUtil.setMotionZ(f); }

    private void getInfinitePitch() {
        this.lastInfinitePitch = this.infinitePitch;
        double currentPlayerSpeed = Math.hypot(ElytraFly.mc.player.getX() - ElytraFly.mc.player.prevX, ElytraFly.mc.player.getZ() - ElytraFly.mc.player.prevZ);
        if (ElytraFly.mc.player.getY() < this.infiniteMaxHeight.getValue()) {
            if (currentPlayerSpeed * 72.0 < this.infiniteMinSpeed.getValue() && !this.down) this.down = true;
            if (currentPlayerSpeed * 72.0 > this.infiniteMaxSpeed.getValue() && this.down) this.down = false;
        } else this.down = true;
        this.infinitePitch = this.down ? (this.infinitePitch += 3.0f) : (this.infinitePitch -= 3.0f);
        this.infinitePitch = MathUtil.clamp(this.infinitePitch, -40.0f, 40.0f);
    }

    public boolean isFallFlying() {
        return ElytraFly.mc.player.isFallFlying() || this.packet.getValue() && this.hasElytra && !ElytraFly.mc.player.isOnGround() || this.flying;
    }

    private Vec3d getFollowTargetPos() {
        if (ElytraFly.mc.world == null || ElytraFly.mc.player == null) return null;
        PlayerEntity self = ElytraFly.mc.player;
        double maxRange = this.range.getValue();
        double maxRangeSq = maxRange * maxRange;
        float maxFov = this.fov.getValueFloat();
        int maxCount = Math.max(1, this.count.getValueInt());
        ArrayList<PlayerEntity> candidates = new ArrayList<>();
        for (PlayerEntity p : ElytraFly.mc.world.getPlayers()) {
            if (p == null || p == self || p.isRemoved() || !p.isAlive() || p.isSpectator() || Alien.FRIEND.isFriend(p)) continue;
            double d = self.squaredDistanceTo(p);
            if (d > maxRangeSq) continue;
            Vec3d pos = p.getPos().add(0.0, p.getHeight() * 0.5, 0.0);
            if (!Alien.ROTATION.inFov(pos, maxFov)) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(self::squaredDistanceTo));
        int n = Math.min(maxCount, candidates.size());
        int startIndex = this.targetSwitch.getValue() ? Math.floorMod(this.followSwitchIndex, candidates.size()) : 0;
        double sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < n; ++i) {
            PlayerEntity p = candidates.get((startIndex + i) % candidates.size());
            Vec3d pos = p.getPos().add(0.0, p.getHeight() * 0.5, 0.0);
            sx += pos.x;
            sy += pos.y;
            sz += pos.z;
        }
        return new Vec3d(sx / n, sy / n, sz / n);
    }

    private double raycastHitDist(Vec3d start, Vec3d dir, double distance) {
        if (ElytraFly.mc.world == null || ElytraFly.mc.player == null) return distance + 1.0;
        Vec3d end = start.add(dir.multiply(distance));
        HitResult hit = ElytraFly.mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, ElytraFly.mc.player));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return distance + 1.0;
        return hit.getPos().distanceTo(start);
    }

    public static enum Mode {
        Control, Boost, Bounce, Freeze, None, Rotation, Pitch;
    }

    public class FireWorkTweak {
        boolean press;
        @EventListener
        public void onTick(ClientTickEvent event) {
            if (Module.nullCheck() || event.isPost()) return;
            if (ElytraFly.this.inventory.getValue() && !EntityUtil.inInventory()) return;
            if (Wrapper.mc.currentScreen == null) {
                if (ElytraFly.this.fireWork.isPressed()) {
                    if (!(this.press || !ElytraFly.this.fireworkTimer.passed(ElytraFly.this.delay.getValueInt()) || Wrapper.mc.player.isUsingItem() && ElytraFly.this.usingPause.getValue() || !ElytraFly.this.isFallFlying())) {
                        ElytraFly.this.off();
                        ElytraFly.this.fireworkTimer.reset();
                    }
                    this.press = true;
                } else {
                    this.press = false;
                }
            } else {
                this.press = false;
            }
        }
    }
}