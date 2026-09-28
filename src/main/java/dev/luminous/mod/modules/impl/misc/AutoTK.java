package dev.luminous.mod.modules.impl.misc;


import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.core.impl.CommandManager;

public class AutoTK extends Module {
    private final BooleanSetting notify = add(new BooleanSetting("Notify", true));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));

    // 静态标志位，表示是否忽略好友判断
    private static boolean ignoringFriends = false;

    public static boolean isIgnoringFriends() {
        return ignoringFriends;
    }

    public AutoTK() {
        super("AutoTK", Module.Category.Misc);
        this.setChinese("自动背刺");
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;

        ignoringFriends = true;

        if (notify.getValue()) {
            CommandManager.sendMessage("§aNow ignoring friends for team killing!");
        }
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        ignoringFriends = false;

        if (notify.getValue()) {
            CommandManager.sendMessage("§aRestored friend protection!");
        }
    }

    @Override
    public void onLogout() {
        if (autoDisable.getValue()) {
            disable();
        }
    }
}