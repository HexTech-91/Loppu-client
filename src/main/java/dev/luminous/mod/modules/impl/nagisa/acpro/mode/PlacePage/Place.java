package dev.luminous.mod.modules.impl.nagisa.acpro.mode.PlacePage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class Place {
   public static SliderSetting minDamage = new SliderSetting("Min-Damage", 6.0, 0.0, 20.0, () -> General.page.getValue() == ACEnum.Page.Place);
   public static SliderSetting maxSelf = new SliderSetting("Max-SelfDmg", 12.0, 0.0, 20.0, () -> General.page.getValue() == ACEnum.Page.Place);
   public static SliderSetting range = new SliderSetting("Range", 5.0, 0.0, 6.0, () -> General.page.getValue() == ACEnum.Page.Place);
   public static SliderSetting noSuicide = new SliderSetting("NoSuicide", 3.0, 0.0, 10.0, () -> General.page.getValue() == ACEnum.Page.Place);
   public static BooleanSetting place = new BooleanSetting("Place", true, () -> General.page.getValue() == ACEnum.Page.Place);
   public static SliderSetting placeDelay = new SliderSetting("Place-Delay", 300, 0, 1000, () -> General.page.getValue() == ACEnum.Page.Place);
   public static SliderSetting ingionPiston = new SliderSetting("Ingion-Piston", 8.0, 0.0, 20.0, () -> General.page.getValue() == ACEnum.Page.Place);
}
