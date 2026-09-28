package dev.luminous.mod.modules.impl.nagisa.acpro.mode.AntiSurroundPage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class AntiSurround {
   public static BooleanSetting antiSurround = new BooleanSetting("Anti-Surround", true, () -> General.page.getValue() == ACEnum.Page.AntiSurround);
   public static SliderSetting antiSurroundMax = new SliderSetting("When-Lower", 5.0, 0.0, 36.0, () -> General.page.getValue() == ACEnum.Page.AntiSurround);
   public static SliderSetting antiSurrounPprogress = new SliderSetting("Progress", 0.8, 0.0, 1.0, () -> General.page.getValue() == ACEnum.Page.AntiSurround);
}
