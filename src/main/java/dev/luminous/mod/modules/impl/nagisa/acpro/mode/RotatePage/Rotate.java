package dev.luminous.mod.modules.impl.nagisa.acpro.mode.RotatePage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class Rotate {
   public static BooleanSetting rotate = new BooleanSetting("Rotation", true, () -> General.page.getValue() == ACEnum.Page.Rotation);
   public static BooleanSetting onBreak = new BooleanSetting("OnBreak", false, () -> General.page.getValue() == ACEnum.Page.Rotation);
   public static BooleanSetting offTackStep = new BooleanSetting("OffTack", false, () -> General.page.getValue() == ACEnum.Page.Rotation);
   public static BooleanSetting checkLook = new BooleanSetting("CheckLook", true, () -> General.page.getValue() == ACEnum.Page.Rotation);
   public static SliderSetting fov = new SliderSetting("Fov", 30.0, 0.0, 90.0, () -> General.page.getValue() == ACEnum.Page.Rotation && checkLook.getValue());
}
