package dev.luminous.mod.modules.impl.nagisa.acpro.mode.BreakPage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class Breaks {
   public static BooleanSetting Break = new BooleanSetting("Break", true, () -> General.page.getValue() == ACEnum.Page.Break);
   public static SliderSetting breakMinDmg = new SliderSetting("Break-Min", 2.0, 0.0, 20.0, () -> General.page.getValue() == ACEnum.Page.Break);
   public static SliderSetting breakDelay = new SliderSetting("Break-Delay", 300, 0, 1000, () -> General.page.getValue() == ACEnum.Page.Break);
   public static BooleanSetting breakRemove = new BooleanSetting("Set-Dead", false, () -> General.page.getValue() == ACEnum.Page.Break);
   public static BooleanSetting instant = new BooleanSetting("Instant", true, () -> General.page.getValue() == ACEnum.Page.Break);
   public static BooleanSetting instantcalc = new BooleanSetting("Instant-Calculations", false, () -> General.page.getValue() == ACEnum.Page.Break);
   public static BooleanSetting alwayscalc = new BooleanSetting("Always-Calculations", false, () -> General.page.getValue() == ACEnum.Page.Break);
}
