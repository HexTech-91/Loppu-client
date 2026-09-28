package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.impl.player.AutoPot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * PotHelper - 自动药水辅助
 * 当玩家一次性受伤大于设定阈值时，自动启动AutoPot
 */
public class PotHelper extends Module {
    public static PotHelper INSTANCE;

    // ========== 设置 ==========
    private final SliderSetting healthThreshold = this.add(new SliderSetting("HealthThreshold", 6.0, 1.0, 20.0, 1.0).setSuffix("hp"));
    private final SliderSetting cooldown = this.add(new SliderSetting("Cooldown", 3.0, 0.0, 10.0, 0.5).setSuffix("s"));
    private final BooleanSetting includeAbsorption = this.add(new BooleanSetting("IncludeAbsorption", true));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", false));

    // ========== 状态 ==========
    private float lastHealth = 20.0f;
    private float lastAbsorption = 0.0f;
    private long lastTriggerTime = 0L;
    private boolean wasTriggered = false;

    public PotHelper() {

        super("PotHelper", Category.Combat);

        this.setChinese("药水帮助者");

        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            lastHealth = mc.player.getHealth();
            lastAbsorption = mc.player.getAbsorptionAmount();
        }
        wasTriggered = false;
    }

    @Override
    public void onDisable() {
        wasTriggered = false;
    }

    @Override
    public String getInfo() {
        return "Threshold: " + (int) healthThreshold.getValue() + "hp";
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!event.isPost()) return;
        if (mc.player == null || mc.world == null) return;

        // 获取当前血量
        float currentHealth = mc.player.getHealth();
        float currentAbsorption = mc.player.getAbsorptionAmount();
        float currentTotal = includeAbsorption.getValue() ? currentHealth + currentAbsorption : currentHealth;
        float lastTotal = includeAbsorption.getValue() ? lastHealth + lastAbsorption : lastHealth;

        // 计算伤害量（血量减少量）
        float damageTaken = lastTotal - currentTotal;

        // 检测是否受到大于阈值的伤害
        if (damageTaken >= healthThreshold.getValue()) {
            // 检查冷却时间
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTriggerTime >= (long)(cooldown.getValue() * 1000)) {
                triggerAutoPot();
                lastTriggerTime = currentTime;
            }
        }

        // 更新上次血量
        lastHealth = currentHealth;
        lastAbsorption = currentAbsorption;

        // 检查AutoPot状态，如果已关闭则重置触发状态
        if (wasTriggered && !AutoPot.INSTANCE.isOn()) {
            wasTriggered = false;
        }
    }

    /**
     * 触发AutoPot
     */
    private void triggerAutoPot() {
        // 检查地面条件
        if (onlyGround.getValue() && !mc.player.isOnGround()) {
            return;
        }

        // 如果AutoPot已经开启且有神龟Buff，返回重新循环检查
        if (AutoPot.INSTANCE.isOn()) {
            StatusEffectInstance resistance = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
            // 有神龟Buff（Resistance II或更高），不需要重新触发
            if (resistance != null && resistance.getAmplifier() >= 2) {
                return;
            }
            // AutoPot已开启但神龟Buff已消失或不足，继续执行重新触发
        }

        // 启动AutoPot
        AutoPot.INSTANCE.enable();
        wasTriggered = true;

        // 发送消息提示
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§c[PotHelper] §f检测到大量伤害，自动启动AutoPot"), false);
        }
    }

    /**
     * 获取上次受到的伤害量
     */
    public float getLastDamage() {
        float lastTotal = includeAbsorption.getValue() ? lastHealth + lastAbsorption : lastHealth;
        float currentTotal = includeAbsorption.getValue() ? mc.player.getHealth() + mc.player.getAbsorptionAmount() : mc.player.getHealth();
        return Math.max(0, lastTotal - currentTotal);
    }

    /**
     * 是否刚刚触发了AutoPot
     */
    public boolean wasJustTriggered() {
        return wasTriggered;
    }
}