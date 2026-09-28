/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.Entity
 *  net.minecraft.DonkeyEntity
 *  net.minecraft.PlayerEntity
 *  net.minecraft.ChunkPos
 *  net.minecraft.BlockPos
 *  net.minecraft.Vec3i
 *  net.minecraft.BlockEntity
 *  net.minecraft.ChestBlockEntity
 *  net.minecraft.Packet
 *  net.minecraft.SignBlockEntity
 *  net.minecraft.PlayerPositionLookS2CPacket
 *  net.minecraft.WorldChunk
 */
package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.misc.Friend;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.events.eventbus.EventListener;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.WorldChunk;

public class AntiBaseKill
        extends Module {
    public static AntiBaseKill INSTANCE;
    private final SliderSetting checkRange = this.add(new SliderSetting("CheckRange", 30.0, 10.0, 100.0, 1.0));
    private final BooleanSetting chest = this.add(new BooleanSetting("Chest", true).setParent());
    private final SliderSetting chestCount = this.add(new SliderSetting("ChestCount", 50.0, 1.0, 500.0, 1.0, () -> this.chest.isOpen()));
    private final BooleanSetting sign = this.add(new BooleanSetting("Sign", true).setParent());
    private final SliderSetting signCount = this.add(new SliderSetting("SignCount", 10.0, 1.0, 100.0, 1.0, () -> this.sign.isOpen()));
    private final BooleanSetting donkey = this.add(new BooleanSetting("Donkey", true).setParent());
    private final SliderSetting donkeyCount = this.add(new SliderSetting("DonkeyCount", 5.0, 1.0, 50.0, 1.0, () -> this.donkey.isOpen()));
    private final BooleanSetting c03Check = this.add(new BooleanSetting("C03Check", true).setParent());
    private final SliderSetting c03Range = this.add(new SliderSetting("C03Range", 1000.0, 1000.0, 5000.0, 100.0, () -> this.c03Check.isOpen()));
    private final Set<String> tempFriends = new HashSet<String>();
    private boolean isCurrentlyInBase = false;
    private int safeguardTicks = 0;

    public AntiBaseKill() {
        super("AntiBaseKill", "Auto friend players when in base", Category.Misc);
        this.setChinese("\u9632\u62c6\u5bb6");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.isCurrentlyInBase = false;
        this.tempFriends.clear();
        this.safeguardTicks = 0;
        this.ensureFriendModuleEnabled();
    }

    @Override
    public void onDisable() {
        this.clearTempFriends();
        this.isCurrentlyInBase = false;
        this.safeguardTicks = 0;
    }

    private void ensureFriendModuleEnabled() {
        if (Friend.INSTANCE != null && !Friend.INSTANCE.isOn()) {
            Friend.INSTANCE.toggle();
            CommandManager.sendMessage("§e\u81ea\u52a8\u5f00\u542f\u597d\u53cb\u6a21\u5757\u3002");
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> class_25962;
        if (AntiBaseKill.nullCheck() || AntiBaseKill.mc.player == null) {
            return;
        }
        if (this.c03Check.getValue() && (class_25962 = event.getPacket()) instanceof PlayerPositionLookS2CPacket) {
            double deltaZ;
            PlayerPositionLookS2CPacket packet = (PlayerPositionLookS2CPacket)class_25962;
            double packetX = packet.getX();
            double packetZ = packet.getZ();
            double deltaX = packetX - AntiBaseKill.mc.player.getX();
            double distXZ = Math.sqrt(deltaX * deltaX + (deltaZ = packetZ - AntiBaseKill.mc.player.getZ()) * deltaZ);
            if (distXZ >= this.c03Range.getValue()) {
                this.safeguardTicks = 30;
                CommandManager.sendMessage(" \u00a76\u68c0\u6d4b\u5230\u8d85\u7a7f (" + (int)distXZ + "m)\uff0c\u8fdb\u5165\u514d\u6218\u9501\u3002");
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (AntiBaseKill.nullCheck()) {
            return;
        }
        if (AntiBaseKill.mc.player.age % 20 == 0) {
            this.ensureFriendModuleEnabled();
        }
        boolean isBase = false;
        if (this.safeguardTicks > 0) {
            --this.safeguardTicks;
            isBase = true;
        } else {
            if (!(this.chest.getValue() || this.sign.getValue() || this.donkey.getValue())) {
                this.handleBaseState(false);
                return;
            }
            int currentChests = 0;
            int currentSigns = 0;
            int currentDonkeys = 0;
            double range = this.checkRange.getValue();
            double rangeSq = range * range;
            if (this.donkey.getValue()) {
                for (Entity entity : AntiBaseKill.mc.world.getEntities()) {
                    if (!(entity instanceof DonkeyEntity) || !(AntiBaseKill.mc.player.squaredDistanceTo(entity) <= rangeSq)) continue;
                    ++currentDonkeys;
                }
            }
            if (this.chest.getValue() || this.sign.getValue()) {
                BlockPos playerPos = AntiBaseKill.mc.player.getBlockPos();
                int r = (int)range;
                ChunkPos minChunk = new ChunkPos(playerPos.add(-r, 0, -r));
                ChunkPos maxChunk = new ChunkPos(playerPos.add(r, 0, r));
                for (int cx = minChunk.x; cx <= maxChunk.x; ++cx) {
                    for (int cz = minChunk.z; cz <= maxChunk.z; ++cz) {
                        if (!AntiBaseKill.mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                        WorldChunk chunk = AntiBaseKill.mc.world.getChunk(cx, cz);
                        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                            if (!(blockEntity.getPos().getSquaredDistance((Vec3i)playerPos) <= rangeSq)) continue;
                            if (this.chest.getValue() && blockEntity instanceof ChestBlockEntity) {
                                ++currentChests;
                                continue;
                            }
                            if (!this.sign.getValue() || !(blockEntity instanceof SignBlockEntity)) continue;
                            ++currentSigns;
                        }
                    }
                }
            }
            isBase = true;
            if (this.chest.getValue() && currentChests < this.chestCount.getValueInt()) {
                isBase = false;
            }
            if (this.sign.getValue() && currentSigns < this.signCount.getValueInt()) {
                isBase = false;
            }
            if (this.donkey.getValue() && currentDonkeys < this.donkeyCount.getValueInt()) {
                isBase = false;
            }
        }
        this.handleBaseState(isBase);
    }

    private void handleBaseState(boolean isBase) {
        if (isBase) {
            if (!this.isCurrentlyInBase) {
                CommandManager.sendMessage("\u00a77[AntiSB] \u00a7a\u514d\u6218\u4fdd\u62a4\u5df2\u5f00\u542f\u3002");
                this.isCurrentlyInBase = true;
            }
            this.addNearbyPlayersToTempFriends();
        } else if (this.isCurrentlyInBase) {
            CommandManager.sendMessage("\u00a77[AntiSB] \u00a7c\u514d\u6218\u4fdd\u62a4\u5df2\u5173\u95ed\u3002");
            this.isCurrentlyInBase = false;
            this.clearTempFriends();
        }
    }

    private void addNearbyPlayersToTempFriends() {
        double rangeSq = this.checkRange.getValue() * this.checkRange.getValue();
        for (PlayerEntity player : AntiBaseKill.mc.world.getPlayers()) {
            String playerName;
            if (player == AntiBaseKill.mc.player || !(AntiBaseKill.mc.player.squaredDistanceTo((Entity)player) <= rangeSq) || Alien.FRIEND.isFriend(playerName = player.getGameProfile().getName()) || this.tempFriends.contains(playerName)) continue;
            Alien.FRIEND.add(playerName);
            this.tempFriends.add(playerName);
            CommandManager.sendMessage("\u00a77[AntiSB] \u00a7a\u6dfb\u52a0\u4e34\u65f6\u597d\u53cb: \u00a7f" + playerName);
        }
    }

    private void clearTempFriends() {
        if (this.tempFriends.isEmpty()) {
            return;
        }
        for (String playerName : this.tempFriends) {
            Alien.FRIEND.remove(playerName);
            CommandManager.sendMessage("\u00a77[AntiSB] \u00a7c\u79fb\u9664\u4e34\u65f6\u597d\u53cb: \u00a7f" + playerName);
        }
        this.tempFriends.clear();
    }
}

