package dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class General {
   public static EnumSetting<ACEnum.Page> page = new EnumSetting<>("Page", ACEnum.Page.General);
   public static BooleanSetting preferAnchor = new BooleanSetting("Prefer-Anchor", true, () -> page.getValue() == ACEnum.Page.General);
   public static BooleanSetting preferpiston = new BooleanSetting("Prefer-Piston", true, () -> page.getValue() == ACEnum.Page.General);
   public static EnumSetting<SwingSide> swingMode = new EnumSetting<>("Swing", SwingSide.Server, () -> page.getValue() == ACEnum.Page.General);
   public static BooleanSetting eatingPause = new BooleanSetting("Eating-Pause", true, () -> page.getValue() == ACEnum.Page.General);
   public static BooleanSetting breakOnlyHasCrystal = new BooleanSetting("OnlyHoldItem", true, () -> page.getValue() == ACEnum.Page.General);
   public static EnumSetting<ACEnum.SwapMode> autoSwap = new EnumSetting<>("Auto-Swap", ACEnum.SwapMode.Off, () -> page.getValue() == ACEnum.Page.General);
   public static SliderSetting switchCooldown = new SliderSetting("Switch-Pause", 100, 0, 1000, () -> page.getValue() == ACEnum.Page.General);
   public static SliderSetting targetRange = new SliderSetting("Target-Range", 12.0, 0.0, 20.0, () -> page.getValue() == ACEnum.Page.General);
   public static SliderSetting updateDelay = new SliderSetting("Update-Delay", 50, 0, 1000, () -> page.getValue() == ACEnum.Page.General);
   public static SliderSetting wallRange = new SliderSetting("Wall-Range", 6.0, 0.0, 6.0, () -> page.getValue() == ACEnum.Page.General);
   public static SliderSetting spamDelay = new SliderSetting("Spam-Delay", 50, 0, 1000, () -> page.getValue() == ACEnum.Page.General);
}
