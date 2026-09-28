package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.gui.KitMgrScreen;
import dev.luminous.mod.modules.Module;

public class KitMgr extends Module {
    public static KitMgr INSTANCE;

    public KitMgr() {
        super("KitMgr", "Kit管理GUI", Category.Client);
        this.setChinese("Kit管理");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        mc.setScreen(new KitMgrScreen());
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof KitMgrScreen) {
            mc.setScreen(null);
        }
    }
}