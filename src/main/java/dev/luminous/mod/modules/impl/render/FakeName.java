package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class FakeName extends Module {
    public static FakeName INSTANCE;
    public final StringSetting fakeName;
    public final BooleanSetting applyToSelf;
    public final BooleanSetting applyToFriends;
    public final BooleanSetting applyToNameTags;
    public final BooleanSetting applyToChat;
    public final BooleanSetting applyToHUD;

    public FakeName() {
        super("FakeName", Module.Category.Render);
        this.fakeName = add(new StringSetting("FakeName", "FakePlayer"));
        this.applyToSelf = add(new BooleanSetting("ApplyToSelf", true));
        this.applyToFriends = add(new BooleanSetting("ApplyToFriends", false));
        this.applyToNameTags = add(new BooleanSetting("ApplyToNameTags", true));
        this.applyToChat = add(new BooleanSetting("ApplyToChat", true));
        this.applyToHUD = add(new BooleanSetting("ApplyToHUD", true));
        setChinese("伪名字");
        INSTANCE = this;
    }

    public String getDisplayName(PlayerEntity player) {
        if (!isOn()) {
            return player.getName().getString();
        }
        if (this.applyToSelf.getValue() && player != null && mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
            return this.fakeName.getValue();
        }
        if (this.applyToFriends.getValue() && player != null && Alien.FRIEND != null && Alien.FRIEND.isFriend(player)) {
            return this.fakeName.getValue();
        }
        return player.getName().getString();
    }

    public String getNameTagsName(PlayerEntity player) {
        if (!isOn() || !this.applyToNameTags.getValue()) {
            return player.getName().getString();
        }
        if (this.applyToSelf.getValue() && player != null && mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
            return this.fakeName.getValue();
        }
        if (this.applyToFriends.getValue() && player != null && Alien.FRIEND != null && Alien.FRIEND.isFriend(player)) {
            return this.fakeName.getValue();
        }
        return player.getName().getString();
    }

    public Text getChatName(PlayerEntity player) {
        if (!isOn() || !this.applyToChat.getValue()) {
            return player.getName();
        }
        if (this.applyToSelf.getValue() && player != null && mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
            return Text.literal(this.fakeName.getValue());
        }
        if (this.applyToFriends.getValue() && player != null && Alien.FRIEND != null && Alien.FRIEND.isFriend(player)) {
            return Text.literal(this.fakeName.getValue());
        }
        return player.getName();
    }

    public String getHUDName(PlayerEntity player) {
        if (!isOn() || !this.applyToHUD.getValue()) {
            return player.getName().getString();
        }
        if (this.applyToSelf.getValue() && player != null && mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
            return this.fakeName.getValue();
        }
        if (this.applyToFriends.getValue() && player != null && Alien.FRIEND != null && Alien.FRIEND.isFriend(player)) {
            return this.fakeName.getValue();
        }
        return player.getName().getString();
    }
}
