package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.gui.FriendMgrScreen;
import dev.luminous.mod.modules.Module;

public class FriendMgr extends Module {
    public static FriendMgr INSTANCE;

    public FriendMgr() {
        super("FriendMgr", "好友管理GUI", Category.Client);
        this.setChinese("好友管理");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        mc.setScreen(new FriendMgrScreen());
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof FriendMgrScreen) {
            mc.setScreen(null);
        }
    }
}