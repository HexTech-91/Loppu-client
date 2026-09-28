package dev.luminous.mod.modules.impl.nagisa.acpro.mode.SyncPage;

import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class Sync {
   public static SliderSetting HurtTime = new SliderSetting("Sync-Time", 10.0, 0.0, 10.0, 0.1, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting SyncTime = new SliderSetting("Last-Sync", 8.0, 0.0, 10.0, 0.1, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting WebSync = new BooleanSetting("Web-Damage", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting WebMinDamage = new SliderSetting("Web-MinDmg", 5.0, 0.0, 20.0, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting SyncTerrainIgnore = new BooleanSetting("TerrainIgnore", true, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting WebdeSync = new BooleanSetting("WebDeSync", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting breaktime = new BooleanSetting("BreakTime-Sync", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting posSync = new BooleanSetting("Pos-Sync", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting forceWeb = new BooleanSetting("SyncTick", true, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting waitsyncupdate = new BooleanSetting("WaitSync-Update", true, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting waitsync = new BooleanSetting("WaitSync-Place", true, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting waitsyncbreak = new BooleanSetting("WaitSync-Break", true, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting waitSyncTime = new SliderSetting("WaitSync-Time", 0.0, 0.0, 2.0, 0.01, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting OnlySync = new BooleanSetting("Only-Sync", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting OnlySyncTime = new SliderSetting("OnlySync-Time", 10.0, 0.0, 10.0, 0.1, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting SpamSyncTime = new SliderSetting(
      "SpamSync-Time", 100.0, 0.0, 1000.0, 1.0, () -> General.page.getValue() == ACEnum.Page.SyncHronize
   );
   public static BooleanSetting syncdebug = new BooleanSetting("Sync-Debug", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static BooleanSetting cancelPushSync = new BooleanSetting("CancelPushSync", false, () -> General.page.getValue() == ACEnum.Page.SyncHronize);
   public static SliderSetting cancelPushSyncDmg = new SliderSetting(
      "CancelPushSync-Dmg", 10.0, 0.0, 36.0, () -> General.page.getValue() == ACEnum.Page.SyncHronize && cancelPushSync.getValue()
   );
}
