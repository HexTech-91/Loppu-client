package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.JumpEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TimerEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class HighJump extends Module {
    public static HighJump INSTANCE;

    // 模式选择
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Strafe));

    // ========== Strafe 模式设置 ==========
    private final SliderSetting multiplier = this.add(new SliderSetting("Multiplier", 1.0, 0.0, 5.0, 0.1,
            () -> mode.getValue() == Mode.Strafe));

    // ========== WindCharge 模式设置 ==========
    private final SliderSetting height = this.add(new SliderSetting("Height", 1.5, 0.5, 10.0, 0.1,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting addVelocity = this.add(new BooleanSetting("AddVelocity", true,
            () -> mode.getValue() == Mode.WindCharge));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 300, 0, 1000, 1,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting autoThrow = this.add(new BooleanSetting("AutoThrow", true,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting rotation = this.add(new BooleanSetting("Rotation", true,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", false,
            () -> mode.getValue() == Mode.WindCharge));
    private final EnumSetting<SwingSide> swing = this.add(new EnumSetting<>("Swing", SwingSide.All,
            () -> mode.getValue() == Mode.WindCharge));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true,
            () -> mode.getValue() == Mode.WindCharge));

    // ========== Timer 相关设置（WindCharge 模式） ==========
    private final BooleanSetting useTimer = this.add(new BooleanSetting("UseTimer", false,
            () -> mode.getValue() == Mode.WindCharge));
    private final SliderSetting timerSpeed = this.add(new SliderSetting("Timer", 1.5, 1.0, 8.0, 0.1,
            () -> mode.getValue() == Mode.WindCharge && useTimer.getValue()));
    private final EnumSetting<TimerMode> timerMode = this.add(new EnumSetting<>("TimerMode", TimerMode.Down,
            () -> mode.getValue() == Mode.WindCharge && useTimer.getValue()));

    // WindCharge 内部状态
    private final Timer jumpTimer = new Timer();
    private boolean shouldJump = false;
    private boolean isThrowing = false;
    private int throwCheckTick = -1;
    private int preThrowCount = -1;
    private boolean timerActive = false; // 控制 Timer 加速是否启用

    public HighJump() {
        super("HighJump", Category.Movement);
        this.setChinese("高跳");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (mode.getValue() == Mode.WindCharge) {
            return "Wind " + height.getValue();
        }
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        shouldJump = false;
        isThrowing = false;
        throwCheckTick = -1;
        preThrowCount = -1;
        timerActive = false;
    }

    @Override
    public void onDisable() {
        timerActive = false; // 关闭时确保 Timer 恢复
    }

    // ========== Strafe 模式：跳跃倍率 ==========
    @EventListener
    public void onJump(JumpEvent event) {
        if (nullCheck() || mode.getValue() != Mode.Strafe || !event.isPost()) return;

        mc.player.setVelocity(
                mc.player.getVelocity().x,
                mc.player.getVelocity().y * multiplier.getValue(),
                mc.player.getVelocity().z
        );
    }

    // ========== WindCharge 模式：手动触发监听 ==========
    @EventListener(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || mode.getValue() != Mode.WindCharge) return;
        if (autoThrow.getValue()) return;

        if (event.getPacket() instanceof PlayerInteractItemC2SPacket) {
            if (!isHoldingWindCharge()) return;
            if (!checkGroundCondition()) {
                if (autoDisable.getValue()) this.disable();
                return;
            }
            shouldJump = true;
            jumpTimer.reset();
        }
    }

    // ========== WindCharge 模式：主循环 ==========
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || mode.getValue() != Mode.WindCharge) return;

        // 若 OnlyGround + AutoDisable 开启且不满足地面条件，立刻关闭模块
        if (onlyGround.getValue() && autoDisable.getValue() && !checkGroundCondition()) {
            this.disable();
            return;
        }

        if (autoThrow.getValue()) {
            // 投掷成功检测
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

            // 尝试自动投掷
            if (!isThrowing && !shouldJump) {
                if (!checkGroundCondition()) {
                    if (autoDisable.getValue()) this.disable();
                    return;
                }
                tryThrowWindCharge();
            }
        }

        // 执行跳跃
        if (shouldJump && jumpTimer.passed((long) delay.getValue())) {
            if (addVelocity.getValue()) {
                mc.player.addVelocity(0, height.getValue(), 0);
            } else {
                mc.player.setVelocity(mc.player.getVelocity().x, height.getValue(), mc.player.getVelocity().z);
            }
            shouldJump = false;

            // 激活 Timer 加速（如果开启）
            if (useTimer.getValue()) {
                timerActive = true;
            }

            if (autoDisable.getValue()) {
                // 如果自动关闭，timerActive 会在 onDisable 中重置，此处直接关闭
                this.disable();
            }
        }

        // 若 Timer 处于激活状态，一旦落地就关闭（因为跳跃已完成）
        if (timerActive && mc.player.isOnGround()) {
            timerActive = false;
        }
    }

    // ========== Timer 加速控制 ==========
    @EventListener
    public void onTimer(TimerEvent event) {
        if (nullCheck() || mode.getValue() != Mode.WindCharge || !timerActive || !useTimer.getValue()) return;

        // 根据 TimerMode 和当前垂直速度方向决定是否加速
        double motionY = mc.player.getVelocity().y;
        boolean shouldSpeed = false;

        switch (timerMode.getValue()) {
            case Up -> shouldSpeed = motionY > 0;
            case Down -> shouldSpeed = motionY < 0;
            case Both -> shouldSpeed = motionY != 0; // 只要在空中就加速（垂直速度不为0）
        }

        if (shouldSpeed) {
            event.set(timerSpeed.getValueFloat());
        }
    }

    // ==================== 地面条件判断（内置 StrictGround 逻辑） ====================
    private boolean checkGroundCondition() {
        if (!onlyGround.getValue()) {
            return true;
        }
        if ((mc.player.isOnGround() || Alien.PLAYER.isInWeb(mc.player))) {
            BlockPos downPos = new BlockPosX(mc.player.getPos()).down();
            if (!mc.world.isAir(downPos)) {
                return true;
            }
        }
        return false;
    }

    // ==================== WindCharge 工具方法 ====================
    private void tryThrowWindCharge() {
        if (!hasWindCharge()) {
            if (autoDisable.getValue()) {
                CommandManager.sendMessageId("§4No Wind Charge found.", this.hashCode() - 1);
                this.disable();
            }
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(Items.WIND_CHARGE)) {
            if (autoDisable.getValue()) {
                this.disable();
            }
            return;
        }

        int originalSlot = mc.player.getInventory().selectedSlot;
        int targetSlot = -1;
        boolean needSwitch = false;

        if (mc.player.getMainHandStack().getItem() != Items.WIND_CHARGE) {
            if (inventory.getValue()) {
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
            if (inventory.getValue()) {
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
        if (rotation.getValue()) {
            Alien.ROTATION.snapAt(yaw, pitch);
        }

        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
        EntityUtil.swingHand(Hand.MAIN_HAND, swing.getValue());

        if (rotation.getValue()) {
            Alien.ROTATION.snapBack();
        }

        if (needSwitch) {
            restoreSlot(originalSlot, targetSlot);
        }

        isThrowing = true;
        throwCheckTick = 2;
    }

    private boolean checkThrowSuccess() {
        ItemStack mainStack = mc.player.getMainHandStack();
        if (mainStack.getItem() == Items.WIND_CHARGE) {
            return mainStack.getCount() < preThrowCount;
        }
        return true;
    }

    private void restoreSlot(int originalSlot, int targetSlot) {
        if (inventory.getValue()) {
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
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlotFromZero(Items.WIND_CHARGE) != -1;
        } else {
            return InventoryUtil.findItem(Items.WIND_CHARGE) != -1;
        }
    }

    // ==================== 模式枚举 ====================
    public enum Mode {
        Strafe,
        WindCharge
    }

    public enum TimerMode {
        Up,
        Down,
        Both
    }
}