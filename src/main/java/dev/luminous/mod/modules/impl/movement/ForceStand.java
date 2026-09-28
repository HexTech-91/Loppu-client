package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Box;

public class ForceStand extends Module {
    public static ForceStand INSTANCE;
    public final BooleanSetting antiSneak = this.add(new BooleanSetting("AntiSneak", false));
    // 新增：强制取消鞘翅滑翔，默认关闭
    public final BooleanSetting antiFallFly = this.add(new BooleanSetting("AntiFallFly", false));

    public ForceStand() {
        super("ForceStand", Module.Category.Movement);
        this.setChinese("强制站立");
        INSTANCE = this;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        EntityPose current = mc.player.getPose();
        if (current == EntityPose.STANDING) return;

        boolean force = false;
        if (current == EntityPose.SWIMMING) {
            force = true;
        } else if (this.antiSneak.getValue() && current == EntityPose.CROUCHING) {
            force = true;
        } else if (this.antiFallFly.getValue() && current == EntityPose.FALL_FLYING) {
            force = true;
        }

        if (force && canStandPhysically()) {
            mc.player.setPose(EntityPose.STANDING);
        }
    }

    public boolean shouldBlock(EntityPose pose) {
        if (!isOn()) return false;
        if (pose == EntityPose.SWIMMING) return true;
        if (this.antiSneak.getValue() && pose == EntityPose.CROUCHING) return true;
        if (this.antiFallFly.getValue() && pose == EntityPose.FALL_FLYING) return true;
        return false;
    }

    public static boolean canStandPhysically() {
        if (INSTANCE == null || !INSTANCE.isOn() || INSTANCE.mc.player == null) return false;
        Box standingBox = INSTANCE.mc.player.getDimensions(EntityPose.STANDING).getBoxAt(INSTANCE.mc.player.getPos());
        return INSTANCE.mc.world.isSpaceEmpty(INSTANCE.mc.player, standingBox);
    }
}