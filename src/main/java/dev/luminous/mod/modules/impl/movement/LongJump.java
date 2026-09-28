package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.MovedEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class LongJump extends Module {
    public static LongJump INSTANCE;

    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Strafe));

    // ========== Strafe 模式设置 ==========
    private final SliderSetting strafeSpeed = this.add(new SliderSetting("Speed", 0.2873, 0.0, 1.0, 1.0E-4,
            () -> mode.is(Mode.Strafe)));
    private final BooleanSetting jump = this.add(new BooleanSetting("Jump", true,
            () -> mode.is(Mode.Strafe)));
    private final SliderSetting lagTime = this.add(new SliderSetting("LagTime", 500.0, 0.0, 1000.0, 1.0,
            () -> mode.is(Mode.Strafe) || mode.is(Mode.WindCharge)));
    private final BooleanSetting airStop = this.add(new BooleanSetting("AirStop", false,
            () -> mode.is(Mode.Strafe) || mode.is(Mode.WindCharge)));

    // ========== WindCharge 模式设置 ==========
    private final SliderSetting boostSpeed = this.add(new SliderSetting("BoostSpeed", 1.5, 0.5, 10.0, 0.1,
            () -> mode.is(Mode.WindCharge)));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true,
            () -> mode.is(Mode.WindCharge)));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 300, 0, 1000, 1,
            () -> mode.is(Mode.WindCharge)));
    private final BooleanSetting autoThrow = this.add(new BooleanSetting("AutoThrow", true,
            () -> mode.is(Mode.WindCharge)));
    private final BooleanSetting rotation = this.add(new BooleanSetting("Rotation", true,
            () -> mode.is(Mode.WindCharge)));
    private final BooleanSetting inventorySwap = this.add(new BooleanSetting("InventorySwap", false,
            () -> mode.is(Mode.WindCharge)));
    private final EnumSetting<SwingSide> swing = this.add(new EnumSetting<>("Swing", SwingSide.All,
            () -> mode.is(Mode.WindCharge)));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true,
            () -> mode.is(Mode.WindCharge)));
    private final SliderSetting activeTime = this.add(new SliderSetting("ActiveTime", 200, 1, 1000, 1,
            () -> mode.is(Mode.WindCharge)));

    // Strafe 内部状态
    private final Timer lagTimer = new Timer();
    private boolean stop;
    private double distance;
    private double speed;
    private int stage;
    private boolean boost;

    // WindCharge 内部状态
    private final Timer jumpTimer = new Timer();
    private final Timer phaseTimer = new Timer();
    private boolean shouldJump = false;
    private boolean isThrowing = false;
    private int throwCheckTick = -1;
    private int preThrowCount = -1;
    private enum Phase { IDLE, ACTIVE }
    private Phase phase = Phase.IDLE;

    public LongJump() {
        super("LongJump", Module.Category.Movement);
        this.setChinese("长跳");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        lagTimer.reset();

        if (mode.is(Mode.Strafe)) {
            speed = MovementUtil.getSpeed(false);
            distance = MovementUtil.getDistance2D();
            stage = 4;
            stop = false;
            boost = false;
        } else if (mode.is(Mode.WindCharge)) {
            shouldJump = false;
            isThrowing = false;
            throwCheckTick = -1;
            preThrowCount = -1;
            phase = Phase.IDLE;
        }
    }

    @Override
    public void onDisable() {
        if (mode.is(Mode.WindCharge)) {
            phase = Phase.IDLE;
        }
    }

    @EventListener(priority = 100)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lagTimer.reset();
            if (mode.is(Mode.Strafe)) {
                resetStrafe();
            } else if (mode.is(Mode.WindCharge)) {
                shouldJump = false;
                isThrowing = false;
                throwCheckTick = -1;
                phase = Phase.IDLE;
            }
        }
    }

    @EventListener
    public void onMoved(MovedEvent event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Strafe)) {
            double dx = mc.player.getX() - mc.player.prevX;
            double dz = mc.player.getZ() - mc.player.prevZ;
            this.distance = Math.sqrt(dx * dx + dz * dz);
        }
    }

    @EventListener(priority = -50)
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;
        if (mc.player.isRiding() || mc.player.isHoldingOntoLadder()
                || mc.player.getAbilities().flying || mc.player.isFallFlying()) {
            return;
        }

        if (mode.is(Mode.Strafe)) {
            handleStrafeMove(event);
        } else if (mode.is(Mode.WindCharge)) {
            if (!MovementUtil.isMoving() && airStop.getValue()) {
                event.setX(0.0);
                event.setZ(0.0);
                return;
            }
            handleWindChargeMove(event);
        }
    }

    // ========== Strafe 逻辑 ==========
    private void handleStrafeMove(MoveEvent event) {
        if (!MovementUtil.isMoving() && airStop.getValue()) {
            MovementUtil.setMotionX(0.0);
            MovementUtil.setMotionZ(0.0);
        }
        if (!MovementUtil.isMoving()) {
            resetStrafe();
            stop = true;
            return;
        }
        if (stop) {
            stop = false;
        } else if (lagTimer.passed(lagTime.getValueInt())) {
            if (stage == 1) {
                speed = 1.35 * MovementUtil.getSpeed(false, strafeSpeed.getValue()) - 0.01;
            } else if (stage == 2 && mc.player.isOnGround() && (mc.options.jumpKey.isPressed() || jump.getValue())) {
                double yMotion = 0.3999 + MovementUtil.getJumpSpeed();
                MovementUtil.setMotionY(yMotion);
                event.setY(yMotion);
                speed = speed * (boost ? 1.6835 : 1.395);
            } else if (stage == 3) {
                speed = distance - 0.66 * (distance - MovementUtil.getSpeed(false, strafeSpeed.getValue()));
                boost = !boost;
            } else {
                if ((BlockUtil.canCollide(null, mc.player.getBoundingBox().offset(0.0, MovementUtil.getMotionY(), 0.0)) || mc.player.collidedSoftly) && stage > 0) {
                    stage = 1;
                }
                speed = distance - distance / 159.0;
            }
            speed = Math.min(speed, 10.0);
            speed = Math.max(speed, MovementUtil.getSpeed(false, strafeSpeed.getValue()));

            double forward = mc.player.input.movementForward;
            double strafe = mc.player.input.movementSideways;
            double yaw = mc.player.getYaw();
            if (forward == 0.0 && strafe == 0.0) {
                event.setX(0.0);
                event.setZ(0.0);
            } else if (forward != 0.0 && strafe != 0.0) {
                forward *= Math.sin(Math.PI / 4);
                strafe *= Math.cos(Math.PI / 4);
            }
            event.setX((forward * speed * -Math.sin(Math.toRadians(yaw)) + strafe * speed * Math.cos(Math.toRadians(yaw))) * 0.99);
            event.setZ((forward * speed * Math.cos(Math.toRadians(yaw)) - strafe * speed * -Math.sin(Math.toRadians(yaw))) * 0.99);
            stage++;
        }
    }

    private void resetStrafe() {
        stage = 4;
        speed = 0.0;
        distance = 0.0;
        stop = false;
        boost = false;
    }

    // ========== WindCharge 逻辑 ==========
    @EventListener
    public void onClientTick(ClientTickEvent event) {
        if (nullCheck() || mode.getValue() != Mode.WindCharge) return;

        if (onlyGround.getValue() && autoDisable.getValue() && !checkGroundCondition() && phase == Phase.IDLE) {
            this.disable();
            return;
        }

        if (autoThrow.getValue()) {
            if (isThrowing && throwCheckTick > 0) {
                throwCheckTick--;
                if (throwCheckTick == 0) {
                    if (checkThrowSuccess()) {
                        shouldJump = true;
                        jumpTimer.reset();
                    }
                    isThrowing = false;
                }
            }
            if (!isThrowing && !shouldJump && phase == Phase.IDLE) {
                if (!checkGroundCondition()) {
                    if (autoDisable.getValue()) this.disable();
                    return;
                }
                if (!lagTimer.passed(lagTime.getValueInt())) return;
                tryThrowWindCharge();
            }
        }

        if (shouldJump && jumpTimer.passedMs((long) delay.getValue()) && phase == Phase.IDLE) {
            phase = Phase.ACTIVE;
            phaseTimer.reset();
            shouldJump = false;
        }

        if (phase == Phase.ACTIVE && phaseTimer.passedMs((long) activeTime.getValue())) {
            phase = Phase.IDLE;
            if (autoDisable.getValue()) {
                this.disable();
            }
        }
    }

    private void handleWindChargeMove(MoveEvent event) {
        if (phase != Phase.ACTIVE) return;

        float yaw = mc.player.getYaw();
        double targetSpeed = boostSpeed.getValue();
        double x = -MathHelper.sin((float) Math.toRadians(yaw)) * targetSpeed;
        double z = MathHelper.cos((float) Math.toRadians(yaw)) * targetSpeed;
        event.setX(x);
        event.setZ(z);
    }

    @EventListener(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || mode.getValue() != Mode.WindCharge) return;

        if (!autoThrow.getValue() && event.getPacket() instanceof PlayerInteractItemC2SPacket) {
            if (!isHoldingWindCharge()) return;
            if (!checkGroundCondition()) {
                if (autoDisable.getValue()) this.disable();
                return;
            }
            shouldJump = true;
            jumpTimer.reset();
        }
    }

    private boolean checkGroundCondition() {
        if (!onlyGround.getValue()) return true;
        if (mc.player.isOnGround() || Alien.PLAYER.isInWeb(mc.player)) {
            BlockPos downPos = new BlockPosX(mc.player.getPos()).down();
            return !mc.world.isAir(downPos);
        }
        return false;
    }

    private void tryThrowWindCharge() {
        if (!hasWindCharge()) {
            if (autoDisable.getValue()) {
                CommandManager.sendMessageId("§4No Wind Charge found.", this.hashCode() - 1);
                this.disable();
            }
            return;
        }
        if (mc.player.getItemCooldownManager().isCoolingDown(Items.WIND_CHARGE)) {
            if (autoDisable.getValue()) this.disable();
            return;
        }

        int originalSlot = mc.player.getInventory().selectedSlot;
        int targetSlot = -1;
        boolean needSwitch = false;
        if (mc.player.getMainHandStack().getItem() != Items.WIND_CHARGE) {
            if (inventorySwap.getValue()) {
                targetSlot = InventoryUtil.findItemInventorySlotFromZero(Items.WIND_CHARGE);
                if (targetSlot == -1) return;
                needSwitch = true;
            } else {
                targetSlot = InventoryUtil.findItem(Items.WIND_CHARGE);
                if (targetSlot == -1) return;
                needSwitch = true;
            }
        }
        if (needSwitch) {
            if (inventorySwap.getValue()) {
                InventoryUtil.inventorySwap(targetSlot, originalSlot);
            } else {
                InventoryUtil.switchToSlot(targetSlot);
            }
        }
        ItemStack mainStack = mc.player.getMainHandStack();
        if (mainStack.getItem() != Items.WIND_CHARGE) {
            if (needSwitch) restoreSlot(originalSlot, targetSlot);
            return;
        }
        preThrowCount = mainStack.getCount();

        float yaw = mc.player.getYaw();
        float pitch = 90f;
        if (rotation.getValue()) Alien.ROTATION.snapAt(yaw, pitch);
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
        EntityUtil.swingHand(Hand.MAIN_HAND, swing.getValue());
        if (rotation.getValue()) Alien.ROTATION.snapBack();

        if (needSwitch) restoreSlot(originalSlot, targetSlot);
        isThrowing = true;
        throwCheckTick = 2;
    }

    private boolean checkThrowSuccess() {
        ItemStack mainStack = mc.player.getMainHandStack();
        return mainStack.getItem() != Items.WIND_CHARGE || mainStack.getCount() < preThrowCount;
    }

    private void restoreSlot(int originalSlot, int targetSlot) {
        if (inventorySwap.getValue()) {
            InventoryUtil.inventorySwap(targetSlot, originalSlot);
        } else {
            InventoryUtil.switchToSlot(originalSlot);
        }
    }

    private boolean isHoldingWindCharge() {
        return mc.player.getMainHandStack().getItem() == Items.WIND_CHARGE
                || mc.player.getOffHandStack().getItem() == Items.WIND_CHARGE;
    }

    private boolean hasWindCharge() {
        if (isHoldingWindCharge()) return true;
        if (inventorySwap.getValue()) {
            return InventoryUtil.findItemInventorySlotFromZero(Items.WIND_CHARGE) != -1;
        }
        return InventoryUtil.findItem(Items.WIND_CHARGE) != -1;
    }

    public enum Mode {
        Strafe,
        WindCharge
    }
}