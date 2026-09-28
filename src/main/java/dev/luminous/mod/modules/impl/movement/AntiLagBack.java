package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer; // 导入 Timer 工具类
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class AntiLagBack extends Module {
    // 原有设置
    private final BooleanSetting ignoreEffect;  // 未使用，保留
    private final BooleanSetting debug;
    private final SliderSetting sneakTime;

    // 新增设置
    private final BooleanSetting antiGround;          // 只在悬空时触发
    private final BooleanSetting onlySlowFalling;     // 只在缓降时触发
    private final SliderSetting triggerThreshold;     // 防误触：需要收到的包数量
    private final SliderSetting triggerWindow;        // 防误触：时间窗口（秒）

    // 原有状态变量
    private boolean shouldSneak;
    private long sneakStartTime;

    // 新增防误触计数变量
    private int packetCount;
    private final Timer packetTimer = new Timer();

    // 构造函数
    public AntiLagBack() {
        super("AntiLagBack", Module.Category.Movement);
        // 原有设置
        this.sneakTime = add(new SliderSetting("SneakTime", 200.0d, 1.0d, 1000.0d, 1.0d).setSuffix("ms"));
        this.debug = add(new BooleanSetting("Debug", false));
        this.ignoreEffect = add(new BooleanSetting("IgnoreEffect", false));

        // 新增设置
        this.antiGround = add(new BooleanSetting("AntiGround", false));                 // 默认关闭
        this.onlySlowFalling = add(new BooleanSetting("OnlySlowFalling", true));        // 默认开启
        this.triggerThreshold = add(new SliderSetting("TriggerThreshold", 2, 1, 10, 1)); // 触发所需包数量
        this.triggerWindow = add(new SliderSetting("TriggerWindow", 1.0, 0.5, 5.0, 0.1).setSuffix("s")); // 时间窗口

        // 初始化状态
        this.shouldSneak = false;
        this.sneakStartTime = 0L;
        this.packetCount = 0;
        setChinese("反回弹下蹲");
    }

    @Override
    public void onEnable() {
        // 启用时重置计数
        packetCount = 0;
        packetTimer.reset();
    }

    @Override
    public void onDisable() {
        // 清除潜行状态和计数
        this.shouldSneak = false;
        this.sneakStartTime = 0L;
        this.packetCount = 0;
        if (!nullCheck()) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        // 处理潜行持续时间
        if (this.shouldSneak) {
            long dur = this.sneakTime.getValueInt();
            if (System.currentTimeMillis() - this.sneakStartTime <= dur) {
                mc.options.sneakKey.setPressed(true);
            } else {
                mc.options.sneakKey.setPressed(false);
                this.shouldSneak = false;
            }
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return; // 确保 mc.player 存在

        // 只处理 PlayerPositionLookS2CPacket
        if (!(event.getPacket() instanceof PlayerPositionLookS2CPacket)) return;

        // 条件检查：OnlySlowFalling
        if (onlySlowFalling.getValue() && !mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            return;
        }

        // 条件检查：AntiGround（只在悬空时触发）
        if (antiGround.getValue() && mc.player.isOnGround()) {
            return;
        }

        // 防误触计数逻辑
        int threshold = triggerThreshold.getValueInt();
        double windowSec = triggerWindow.getValue();

        // 如果阈值<=1，直接触发（无需计数）
        if (threshold <= 1) {
            triggerSneak("S2C PositionLook (immediate)");
            return;
        }

        // 如果是第一个包，重置计时器
        if (packetCount == 0) {
            packetTimer.reset();
        }

        packetCount++;

        // 判断是否达到阈值且仍在时间窗口内
        if (packetCount >= threshold && !packetTimer.passedMs((long) (windowSec * 1000))) {
            triggerSneak("S2C PositionLook (threshold met)");
            packetCount = 0; // 触发后重置计数
        }

        // 如果超过时间窗口，重置计数（防止累积）
        if (packetTimer.passedMs((long) (windowSec * 1000))) {
            packetCount = 0;
        }
    }

    /**
     * 触发潜行，并记录开始时间
     */
    private void triggerSneak(String reason) {
        this.shouldSneak = true;
        this.sneakStartTime = System.currentTimeMillis();
        if (this.debug.getValue()) {
            sendMessage("AntiLagBack: " + reason + " -> sneak " + this.sneakTime.getValueInt() + this.sneakTime.getSuffix());
        }
    }
}