package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class TPVoid extends Module {
    public static TPVoid INSTANCE;

    private final SliderSetting baseY = this.add(
            new SliderSetting("BaseY", -60.0, -100.0, -50.0, 1.0)
    );
    private final BooleanSetting onTick = this.add(
            new BooleanSetting("OnTick", false)
    );
    private final BooleanSetting tickBypass = this.add(
            new BooleanSetting("TickBypass", true)
    );

    private boolean hasTp = false;
    private double returnY = 0;

    public TPVoid() {
        super("TPVoid", Category.Player);
        this.setChinese("虚空传送");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            return;
        }
        this.hasTp = false;
        this.returnY = 0;
        // 立即发送TP包
        this.sendTpPacket();
    }

    @Override
    public void onDisable() {
        this.hasTp = false;
        this.returnY = 0;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || !this.isOn()) {
            return;
        }
        if (!event.isPre()) {
            return;
        }

        // 如果开启OnTick模式，每tick发送TP包
        if (this.onTick.getValue()) {
            this.sendTpPacket();
        } else if (!this.hasTp) {
            // 非OnTick模式：只发送一次
            this.sendTpPacket();
            this.hasTp = true;
        }
    }

    private void sendTpPacket() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }

        double targetY = this.baseY.getValue();
        double x = mc.player.getX();
        double z = mc.player.getZ();

        // 包1: 发送到虚空位置 — 让服务器计算坠落距离
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, targetY, z, false
                )
        );

        if (this.tickBypass.getValue()) {
            // 包2: 立即发送回当前Y — 同一个tick内回到地面
            // 服务端在同一tick处理完两个包后，tick边界检查时玩家Y已在-63以上
            // 虚空tick计数器不会触发，不会爆图腾
            double returnPosY = (this.returnY != 0) ? this.returnY : mc.player.getY();
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            x, returnPosY, z, true
                    )
            );
            // 只在首次启用时记录returnY
            if (this.returnY == 0) {
                this.returnY = mc.player.getY();
            }
            // 本地位置保持在returnY，不留在虚空
            mc.player.setPosition(x, returnPosY, z);
            mc.player.fallDistance = (float) (returnPosY - targetY);
        } else {
            // 旧模式：停留在虚空
            mc.player.setPosition(x, targetY, z);
        }
    }

    @Override
    public String getInfo() {
        return "BaseY: " + (int)this.baseY.getValue();
    }
}