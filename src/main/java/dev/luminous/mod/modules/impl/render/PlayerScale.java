package dev.luminous.mod.modules.impl.render;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerScale extends Module {
    public static PlayerScale INSTANCE;

    public final SliderSetting scale = this.add(new SliderSetting("Scale", 1.5, 0.1, 5.0, 0.01));

    public PlayerScale() {
        super("PlayerScale", Module.Category.Render);
        this.setChinese("玩家模型放大");
        INSTANCE = this;
    }

    public boolean shouldScale(PlayerEntity player) {
        return player != null && !player.isInvisible();
    }
}