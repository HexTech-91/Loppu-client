package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;

public class ExtraTab extends Module {
    public static ExtraTab INSTANCE;
    
    public final SliderSetting size = this.add(new SliderSetting("Size", 200.0, 80.0, 1000.0, 1.0));
    public final SliderSetting columns = this.add(new SliderSetting("Columns", 20.0, 1.0, 100.0, 1.0));
    
    public final BooleanSetting selfColor = this.add(new BooleanSetting("SelfColor", true).setParent());
    public final ColorSetting selfColorValue = this.add(new ColorSetting("SelfColorValue", new Color(255, 85, 85), this.selfColor::isOpen));
    
    public final BooleanSetting friendColor = this.add(new BooleanSetting("FriendColor", true).setParent());
    public final ColorSetting friendColorValue = this.add(new ColorSetting("FriendColorValue", new Color(85, 170, 255), this.friendColor::isOpen));

    public ExtraTab() {
        super("ExtraTab", Category.Client);
        this.setChinese("列表无上限");
        INSTANCE = this;
    }
    
    public static int getPlayerColor(String playerName, int originalColor) {
        if (INSTANCE == null || !INSTANCE.isOn()) {
            return originalColor;
        }
        
        if (mc.player != null && mc.player.getGameProfile().getName().equals(playerName)) {
            if (INSTANCE.selfColor.getValue()) {
                return INSTANCE.selfColorValue.getValue().getRGB();
            }
        }
        
        if (INSTANCE.friendColor.getValue() && Alien.FRIEND.isFriend(playerName)) {
            return INSTANCE.friendColorValue.getValue().getRGB();
        }
        
        return originalColor;
    }
    
    public static boolean shouldApplyColor() {
        return INSTANCE != null && INSTANCE.isOn();
    }
}
