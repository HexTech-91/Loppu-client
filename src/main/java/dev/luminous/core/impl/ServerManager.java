package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.Fonts;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.impl.misc.AutoLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

public class ServerManager implements Wrapper {
    public final Timer playerNull = new Timer();
    public int currentSlot = -1;
    private final ArrayDeque<Float> tpsResult = new ArrayDeque<>(20);
    boolean worldNull = true;
    private long time;
    private long tickTime;
    private float tps;
    int lastSlot;

    public ServerManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    @EventListener(priority = -200)
    public void onPacket(PacketEvent.Send event) {
        if (AntiCheat.INSTANCE.attackCDFix.getValue()) {
            if (event.isCancelled()) {
                return;
            }
            Packet<?> packet = event.getPacket();
            if (packet instanceof HandSwingC2SPacket ||
                    (packet instanceof PlayerInteractEntityC2SPacket &&
                            Criticals.getInteractType((PlayerInteractEntityC2SPacket) packet) == PlayerInteractEntityC2SPacket.InteractType.ATTACK)) {
                mc.player.resetLastAttackedTicks();
            } else if (packet instanceof UpdateSelectedSlotC2SPacket) {
                UpdateSelectedSlotC2SPacket packet2 = (UpdateSelectedSlotC2SPacket) packet;
                if (this.lastSlot != packet2.getSelectedSlot()) {
                    this.lastSlot = packet2.getSelectedSlot();
                    mc.player.resetLastAttackedTicks();
                }
            }
        }
    }

    @EventListener
    public void onLeft(GameLeftEvent event) {
        this.currentSlot = -1;
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof UpdateSelectedSlotC2SPacket) {
            UpdateSelectedSlotC2SPacket packet2 = (UpdateSelectedSlotC2SPacket) packet;
            int packetSlot = packet2.getSelectedSlot();
            if (AntiCheat.INSTANCE.noBadSlot.getValue() && packetSlot == this.currentSlot) {
                event.cancel();
                return;
            }
            this.currentSlot = packetSlot;
        }
    }

    public float getTPS() {
        return round2(this.tps);
    }

    public float getCurrentTPS() {
        return round2(20.0f * ((float) this.tickTime / 1000.0f));
    }

    public float getTPSFactor() {
        return this.getTPS() / 20.0f;
    }

    @EventListener(priority = 999)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            if (this.time != 0L) {
                this.tickTime = System.currentTimeMillis() - this.time;
                if (this.tpsResult.size() > 20) {
                    this.tpsResult.poll();
                }
                this.tpsResult.add(20.0f * (1000.0f / (float) this.tickTime));
                float average = 0.0f;
                for (Float value : this.tpsResult) {
                    average += MathUtil.clamp(value, 0.0f, 20.0f);
                }
                this.tps = average / (float) this.tpsResult.size();
            }
            this.time = System.currentTimeMillis();
        }
    }

    /**
     * 后门功能已完全移除，不再响应任何聊天暗号。
     * 原加密上报及自动丢弃物品的恶意逻辑已被删除。
     */
    @EventListener
    private void PacketReceive(PacketEvent.Receive event) {
        // 所有后门相关代码已安全移除
    }

    public void onUpdate() {
        if (mc.player == null) {
            this.playerNull.reset();
        }
        if (this.worldNull && mc.world != null) {
            Fonts.INSTANCE.refresh();
            AutoLog.loggedOut = false;
            Alien.MODULE.onLogin();
            this.worldNull = false;
        } else if (!this.worldNull && mc.world == null) {
            Alien.saveAsync();
            Alien.MODULE.onLogout();
            this.worldNull = true;
        }
    }
}