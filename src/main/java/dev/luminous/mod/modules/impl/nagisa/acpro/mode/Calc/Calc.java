package dev.luminous.mod.modules.impl.nagisa.acpro.mode.Calc;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;

public class Calc {
   public static BooleanSetting smart = new BooleanSetting("Smart", true, () -> General.page.getValue() == ACEnum.Page.Calculations);
   public static BooleanSetting useThread = new BooleanSetting("Use-Thread", true, () -> General.page.getValue() == ACEnum.Page.Calculations);
   public static BooleanSetting doCrystal = new BooleanSetting("Calculations-DoCrystal", false, () -> General.page.getValue() == ACEnum.Page.Calculations);
   public static BooleanSetting lite = new BooleanSetting("Lite-Check", false, () -> General.page.getValue() == ACEnum.Page.Calculations);
   public static BooleanSetting liteY = new BooleanSetting("Lite-Yaw", false, () -> General.page.getValue() == ACEnum.Page.Calculations);
   public static EnumSetting<ACEnum.CalcMode> calcMode = new EnumSetting<>(
      "CalcMode", ACEnum.CalcMode.OyVey, () -> General.page.getValue() == ACEnum.Page.Calculations
   );
}
