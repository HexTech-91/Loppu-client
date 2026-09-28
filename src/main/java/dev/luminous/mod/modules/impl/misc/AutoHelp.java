package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;

import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.Vec3d;

public class AutoHelp extends Module {
    private final Timer timer = new Timer();
    private final List<String> onlineFriends = new ArrayList();
    private final Set<String> sentFriends = new HashSet();
    private final StringSetting prefixText = this.add(new StringSetting("PrefixText", "Helpme!"));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 3.0, 1.0, 10.0, 1.0).setSuffix("s"));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));

    public AutoHelp() {
        super("AutoHelp", Module.Category.Misc);
        this.setChinese("自动求助");
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            this.disable();
        } else {
            this.sentFriends.clear();
            this.refreshOnlineFriends();
            if (this.onlineFriends.isEmpty()) {
                this.sendMessage("没有在线好友");
                if (this.autoDisable.getValue()) {
                    this.disable();
                }
            } else {
                this.timer.reset();
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        double distanceFromSpawn = mc.player.getPos().distanceTo(new Vec3d(0.0, 0.0, 0.0));
        if (distanceFromSpawn > 4000.0) {
            this.sendMessage("距离出生点超过4000格，自动关闭");
            this.disable();
        } else if (this.timer.passedS(this.delay.getValue())) {
            this.timer.reset();
            this.refreshOnlineFriends();
            if (this.onlineFriends.isEmpty()) {
                if (this.autoDisable.getValue()) {
                    this.disable();
                }
            } else {
                String coordinates = String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                String message = this.prefixText.getValue() + " 当前坐标 " + coordinates;
                String friendName = (String)this.onlineFriends.get(0);
                mc.getNetworkHandler().sendChatCommand("tell " + friendName + " " + message);
                this.sentFriends.add(friendName);
            }
        }
    }

    private void refreshOnlineFriends() {
        this.onlineFriends.clear();
        if (mc.getNetworkHandler() != null && mc.player != null) {
            String selfName = mc.player.getGameProfile().getName();

            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().getName();
                if (name != null && !name.equals(selfName) && Alien.FRIEND.isFriend(name) && !this.sentFriends.contains(name)) {
                    this.onlineFriends.add(name);
                }
            }
        }
    }
}
