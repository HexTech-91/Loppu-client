package dev.luminous.mod.modules.impl.nagisa.acpro.mode.ForcePlace;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class ForcePlace {
   public static BooleanSetting slowPlace = new BooleanSetting("Face-Place", true, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static SliderSetting slowDelay = new SliderSetting("FacePlace-Delay", 600, 0, 2000, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static SliderSetting slowMinDamage = new SliderSetting("Face-Min", 1.5, 0.0, 36.0, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static BooleanSetting forcePlace = new BooleanSetting("Health-Place", true, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static SliderSetting forceMaxHealth = new SliderSetting("Max-Health", 7, 0, 36, () -> General.page.getValue() == ACEnum.Page.ForcePlace)
      .setSuffix("health");
   public static SliderSetting forceMin = new SliderSetting("Health-Min", 1.5, 0.0, 36.0, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static BooleanSetting armorBreaker = new BooleanSetting("ArmorBreaker", true, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static SliderSetting maxDurable = new SliderSetting("Max-Durable", 8, 0, 100, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
   public static SliderSetting armorBreakerDamage = new SliderSetting("Min-Durable", 3.0, 0.0, 36.0, () -> General.page.getValue() == ACEnum.Page.ForcePlace);
}
