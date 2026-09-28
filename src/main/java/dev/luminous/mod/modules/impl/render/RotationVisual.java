package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;

import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class RotationVisual extends Module {
    public static RotationVisual INSTANCE;
    public final BooleanSetting self = this.add(new BooleanSetting("Self", true));
    public final BooleanSetting others = this.add(new BooleanSetting("Others", true));
    public final SliderSetting pitch = this.add(new SliderSetting("Pitch", 0.0, -90.0, 90.0));
    public final SliderSetting yaw = this.add(new SliderSetting("Yaw", 0.0, -180.0, 180.0));
    public final SliderSetting autoRotateSpeed = this.add(new SliderSetting("AutoRotateSpeed", 0.0, 0.0, 10.0, 0.1F));
    private float currentYaw = 0.0F;
    private float currentPitch = 0.0F;

    public RotationVisual() {
        super("RotationVisual", Module.Category.Render);
        this.setChinese("原地旋转");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mc.player != null) {
            if (this.autoRotateSpeed.getValue() > 0.0) {
                this.currentYaw = (float)(this.currentYaw + this.autoRotateSpeed.getValue());
                if (this.currentYaw > 180.0F) {
                    this.currentYaw -= 360.0F;
                }

                if (this.currentYaw < -180.0F) {
                    this.currentYaw += 360.0F;
                }

                this.yaw.setValue(this.currentYaw);
            }
        }
    }
}
