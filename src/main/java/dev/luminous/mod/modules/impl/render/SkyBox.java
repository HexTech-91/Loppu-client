package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.core.impl.ShaderManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;


public class SkyBox extends Module {
    public static SkyBox INSTANCE;
    public final ColorSetting color = this.add(new ColorSetting("Color", new Color(255, 255, 255, 255)));
    public final ColorSetting backgroundColor = this.add(new ColorSetting("BackgroundColor", new Color(0, 0, 0, 255)));
    public final SliderSetting scale = this.add(new SliderSetting("Scale", 1.0, 0.1, 5.0, 0.05));
    public final SliderSetting speed = this.add(new SliderSetting("Speed", 4.0, 0.0, 20.0, 0.1));
    public final SliderSetting opacity = this.add(new SliderSetting("Opacity", 1.0, 0.0, 1.0, 0.01));

    public SkyBox() {
        super("Skybox", Category.Render);
        this.setChinese("天空盒");
        INSTANCE = this;
    }

    public void renderSkybox() {
        if (!this.isOn()) return;
        ShaderManager sm = Alien.SHADER;
        if (sm == null || sm.skyboxNullCheck()) return;
        sm.renderSkyboxFullscreen(
            color.getValue(),
            backgroundColor.getValue(),
            scale.getValueFloat(),
            speed.getValueFloat(),
            opacity.getValueFloat()
        );
    }
}
