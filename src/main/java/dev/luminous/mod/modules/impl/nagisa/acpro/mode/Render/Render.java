package dev.luminous.mod.modules.impl.nagisa.acpro.mode.Render;

import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;

public class Render {
   public static EnumSetting<Aura.TargetESP> mode = new EnumSetting<>("TargetESP", Aura.TargetESP.Jello, () -> General.page.getValue() == ACEnum.Page.Render);
   public static ColorSetting color = new ColorSetting("TargetColor", new Color(255, 255, 255, 250), () -> General.page.getValue() == ACEnum.Page.Render);
   public static ColorSetting text = new ColorSetting("Text", new Color(-1), () -> General.page.getValue() == ACEnum.Page.Render).injectBoolean(false);
   public static ColorSetting misscatext = new ColorSetting("missText", new Color(-1), () -> General.page.getValue() == ACEnum.Page.Render).injectBoolean(true);
   public static ColorSetting nullpostext = new ColorSetting("nullposText", new Color(-1), () -> General.page.getValue() == ACEnum.Page.Render)
      .injectBoolean(true);
   public static ColorSetting spamtext = new ColorSetting("SpamText", new Color(-1), () -> General.page.getValue() == ACEnum.Page.Render).injectBoolean(true);
   public static BooleanSetting render = new BooleanSetting("Render", true, () -> General.page.getValue() == ACEnum.Page.Render);
   public static BooleanSetting shrink = new BooleanSetting("Shrink", true, () -> General.page.getValue() == ACEnum.Page.Render && render.getValue());
   public static ColorSetting box = new ColorSetting(
         "Box", new Color(255, 255, 255, 255), () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
      )
      .injectBoolean(true);
   public static SliderSetting lineWidth = new SliderSetting(
      "LineWidth", 1.5, 0.01, 3.0, 0.01, () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
   );
   public static ColorSetting fill = new ColorSetting(
         "Fill", new Color(255, 255, 255, 100), () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
      )
      .injectBoolean(true);
   public static SliderSetting sliderSpeed = new SliderSetting(
      "SliderSpeed", 0.5, 0.01, 1.0, 0.01, () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
   );
   public static SliderSetting startFadeTime = new SliderSetting(
         "StartFade", 0.3, 0.0, 5.0, 0.01, () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
      )
      .setSuffix("s");
   public static SliderSetting fadeSpeed = new SliderSetting(
      "FadeSpeed", 0.5, 0.01, 1.0, 0.01, () -> General.page.getValue() == ACEnum.Page.Render && render.getValue()
   );
   private static ACEnum.SwapMode SwapMode;
}
