package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import dev.luminous.Alien;

public class AntiSlowFalling extends Module {
    public static AntiSlowFalling INSTANCE;

    // 原设置
    private final BooleanSetting bypassSlowFalling = this.add(new BooleanSetting("SlowFallingCheck", false));
    private final SliderSetting y = this.add(new SliderSetting("YMove", 0.01, 0.0, 1.0, 0.01, () -> bypassSlowFalling.getValue()));
    private final SliderSetting jumpDelay = this.add(new SliderSetting("JumpTick", 0, 0, 500, 1, () -> bypassSlowFalling.getValue()));
    private final BooleanSetting excludeWater = this.add(new BooleanSetting("ExcludeWater", true, () -> bypassSlowFalling.getValue()));
    private final BooleanSetting noFallFlying = this.add(new BooleanSetting("NoFallFlying", true, () -> bypassSlowFalling.getValue()));
    private final BooleanSetting noWeb = this.add(new BooleanSetting("NoWeb", true, () -> bypassSlowFalling.getValue()));

    // SpringBack 新设置（作为 SlowFallingCheck 的子设置）
    private final BooleanSetting springBack = this.add(new BooleanSetting("SpringBack", false, () -> bypassSlowFalling.getValue()));
    private final SliderSetting backDelay = this.add(new SliderSetting("BackDelay", 1, 1, 20, 1, () -> bypassSlowFalling.getValue() && springBack.getValue()));

    // SpringBack 防误触发设置（参考代码2）
    private final SliderSetting springBackThreshold = this.add(new SliderSetting("SBThreshold", 2, 1, 10, 1, () -> bypassSlowFalling.getValue() && springBack.getValue()));
    private final SliderSetting springBackWindow = this.add(new SliderSetting("SBWindow", 1.0, 0.5, 5.0, 0.1, () -> bypassSlowFalling.getValue() && springBack.getValue()));

    private final Timer jumpTimer = new Timer();

    // SpringBack 相关变量
    private final Timer springBackPacketTimer = new Timer();
    private final Timer sneakTimer = new Timer();
    private int springBackPacketCount = 0;
    private boolean isSneakingForSpringBack = false;
    private boolean shouldSneakForSpringBack = false;

    // 状态枚举（参考代码3）
    private enum SpringBackState {
        IDLE,
        SNEAKING,
        RELEASING
    }
    private SpringBackState springBackState = SpringBackState.IDLE;

    // 用于记录下蹲开始的tick
    private int sneakStartTick = 0;

    public AntiSlowFalling() {
        super("AntiSlowFalling", Module.Category.Movement);
        this.setChinese("反缓降");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 修复：使用 Module.nullCheck() 方法
        if (Module.nullCheck() || !bypassSlowFalling.getValue()) return;

        // SpringBack 状态处理（类似代码3的状态机）
        handleSpringBackState();

        if (!jumpTimer.passedMs((long) jumpDelay.getValue())) return;
        if (!mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return;
        if (mc.player.isSpectator()) return;
        if (!EntityUtil.isInsideBlock()) return;

        // 排除是水  以防羊癫疯
        if (excludeWater.getValue() && mc.player.isTouchingWater()) {
            return;
        }

        // 如果穿着鞘翅或在空中飞 则不能弹跳
        if (noFallFlying.getValue()) {
            boolean wearingElytra = false;
            for (ItemStack is : mc.player.getArmorItems()) {
                if (is.getItem() == Items.ELYTRA) {
                    wearingElytra = true;
                    break;
                }
            }
            if (mc.player.isFallFlying() || wearingElytra) {
                return;
            }
        }

        // 在蜘蛛网中不能弹跳 不然会直接卡死
        if (noWeb.getValue()) {
            boolean inWeb = Alien.PLAYER.isInWeb(mc.player)
                    || mc.world.getBlockState(EntityUtil.getPlayerPos(true)).getBlock() == Blocks.COBWEB;
            if (inWeb) {
                return;
            }
        }

        if (mc.player.isOnGround() && mc.player.fallDistance <= 0) {
            MovementUtil.setMotionY(y.getValue());
            jumpTimer.reset();
        }
    }

    /**
     * 监听位置纠正包（使用 PacketEvent.Receive）
     */
    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        // 检查是否是PlayerPositionLookS2CPacket
        if (!bypassSlowFalling.getValue() || !springBack.getValue()) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            handlePositionCorrection();
        }
    }

    /**
     * 处理位置纠正包（参考代码2的防误触发逻辑）
     */
    private void handlePositionCorrection() {
        // 检查游戏环境
        if (mc.player == null || mc.world == null) return;

        // 只有在方块内部且有缓降buff时才处理
        if (!EntityUtil.isInsideBlock() || !mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            springBackPacketCount = 0;
            return;
        }

        // 安全排除（参考原代码逻辑）
        if (excludeWater.getValue() && mc.player.isTouchingWater()) return;
        if (noFallFlying.getValue() && mc.player.isFallFlying()) return;
        if (noWeb.getValue()) {
            boolean inWeb = Alien.PLAYER.isInWeb(mc.player)
                    || mc.world.getBlockState(EntityUtil.getPlayerPos(true)).getBlock() == Blocks.COBWEB;
            if (inWeb) return;
        }

        // 记录收到数据包的时间
        if (springBackPacketCount == 0) {
            springBackPacketTimer.reset();
        }

        springBackPacketCount++;

        // 检查是否达到阈值且在时间窗口内（防误触发）
        // 注意：时间窗口内是指还没有超过时间窗口
        if (springBackPacketCount >= springBackThreshold.getValueInt() &&
                !springBackPacketTimer.passedMs((long)(springBackWindow.getValue() * 1000))) {
            // 触发下蹲
            shouldSneakForSpringBack = true;
            // 重置计数
            springBackPacketCount = 0;
        }
    }

    /**
     * 处理SpringBack状态（参考代码3的状态机设计）
     */
    private void handleSpringBackState() {
        // 如果不是SpringBack模式，重置所有状态
        if (!bypassSlowFalling.getValue() || !springBack.getValue()) {
            resetSpringBackState();
            return;
        }

        // 如果没有玩家对象，重置状态
        if (mc.player == null) {
            resetSpringBackState();
            return;
        }

        switch (springBackState) {
            case IDLE:
                // 等待触发下蹲信号
                if (shouldSneakForSpringBack) {
                    // 开始下蹲（参考代码3的潜行方式）
                    mc.options.sneakKey.setPressed(true);
                    isSneakingForSpringBack = true;
                    sneakStartTick = mc.player.age;
                    sneakTimer.reset();
                    springBackState = SpringBackState.SNEAKING;
                    shouldSneakForSpringBack = false;
                }
                break;

            case SNEAKING:
                // 检查下蹲时间是否达到BackDelay
                // 使用tick计数而不是时间，因为tick更可靠
                // 1 tick = 50ms，但我们使用tick计数更准确
                int currentTick = mc.player.age;
                int ticksSneaking = currentTick - sneakStartTick;

                if (ticksSneaking >= backDelay.getValue()) {
                    // 释放潜行键
                    mc.options.sneakKey.setPressed(false);
                    isSneakingForSpringBack = false;
                    springBackState = SpringBackState.RELEASING;
                    sneakTimer.reset();
                }
                break;

            case RELEASING:
                // 短暂延迟后返回IDLE状态（100ms延迟）
                if (sneakTimer.passedMs(100)) {
                    springBackState = SpringBackState.IDLE;
                }
                break;
        }

        // 时间窗口过期，重置计数
        if (springBackPacketCount > 0 &&
                springBackPacketTimer.passedMs((long)(springBackWindow.getValue() * 1000))) {
            springBackPacketCount = 0;
        }
    }

    /**
     * 重置SpringBack状态
     */
    private void resetSpringBackState() {
        if (isSneakingForSpringBack) {
            mc.options.sneakKey.setPressed(false);
        }
        springBackState = SpringBackState.IDLE;
        isSneakingForSpringBack = false;
        shouldSneakForSpringBack = false;
        springBackPacketCount = 0;
    }

    /**
     * 模块禁用时清理状态
     */
    @Override
    public void onDisable() {
        super.onDisable();
        resetSpringBackState();
    }

    /**
     * 模块启用时重置状态
     */
    @Override
    public void onEnable() {
        super.onEnable();
        resetSpringBackState();
    }
}