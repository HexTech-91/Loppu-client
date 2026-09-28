package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

import java.util.Random;

public class IQBoost extends Module {
    public static IQBoost INSTANCE;

    public enum IQMode {
        Fixed,
        Random,
        CrystalSpeed,
        PacketCount
    }

    public final EnumSetting<IQMode> iqMode = this.add(new EnumSetting<>("IQMode", IQMode.Fixed));

    public final SliderSetting fixedIqValue = this.add(new SliderSetting("FixedIQ", 100, -114514, 114514,
            1, () -> iqMode.getValue() == IQMode.Fixed));

    public final SliderSetting randomMinIq = this.add(new SliderSetting("RandomMin", 50, -114514, 114514,
            1, () -> iqMode.getValue() == IQMode.Random));
    public final SliderSetting randomMaxIq = this.add(new SliderSetting("RandomMax", 150, -114514, 114514,
            1, () -> iqMode.getValue() == IQMode.Random));

    public final SliderSetting crystalSpeedMultiplier = this.add(new SliderSetting("CrystalMultiplier", 10, 1, 100,
            0.1, () -> iqMode.getValue() == IQMode.CrystalSpeed));

    public final SliderSetting packetMultiplier = this.add(new SliderSetting("PacketMultiplier", 50, 1, 1000,
            1, () -> iqMode.getValue() == IQMode.PacketCount));

    private int currentIq = 100;
    private int packetCount = 0;
    private long lastUpdateTime = 0;
    private int crystalPlacements = 0;
    private long lastCrystalTime = 0;
    private final Random random = new Random();

    public IQBoost() {
        super("IQBoost",Category.Misc);
        this.setChinese("智商提升");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.currentIq = this.calculateIq();
        this.packetCount = 0;
        this.crystalPlacements = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        this.currentIq = 0;
        this.packetCount = 0;
        this.crystalPlacements = 0;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastUpdateTime >= 600) {
            this.currentIq = this.calculateIq();
            this.lastUpdateTime = currentTime;

            this.packetCount = 0;
            this.crystalPlacements = 0;
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (this.iqMode.getValue() == IQMode.PacketCount) {
            this.packetCount++;
        }

        if (this.iqMode.getValue() == IQMode.CrystalSpeed) {
            if (event.getPacket() instanceof PlayerInteractBlockC2SPacket) {
                this.crystalPlacements++;
            }
        }
    }

    private int calculateIq() {
        switch (this.iqMode.getValue()) {
            case Fixed:
                return (int) this.fixedIqValue.getValue();

            case Random:
                int min = (int) this.randomMinIq.getValue();
                int max = (int) this.randomMaxIq.getValue();
                return min + this.random.nextInt(Math.max(1, max - min + 1));

            case CrystalSpeed:
                double speed = this.crystalPlacements * this.crystalSpeedMultiplier.getValue();
                return (int) Math.round(speed);

            case PacketCount:
                double packets = this.packetCount * this.packetMultiplier.getValue();
                return (int) Math.round(packets);

            default:
                return 100;
        }
    }

    public int getCurrentIq() {
        return this.currentIq;
    }

    @Override
    public String getInfo() {
        return String.valueOf(this.currentIq);
    }
}