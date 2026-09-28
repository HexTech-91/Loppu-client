package dev.luminous.mod.gui.nakano.click;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.settings.Setting;

import java.util.List;

/** Small presentation adapter over the real module and setting model. Ported from Nakano. */
final class GuiModule {
    private final Module module;

    GuiModule(Module module) {
        this.module = module;
    }

    String name() {
        return module.getDisplayName();
    }

    boolean enabled() {
        return module.isOn();
    }

    void toggle() {
        module.toggle();
    }

    List<Setting> settings() {
        if (module instanceof ClickGui clickGui) {
            return List.of(
                    module.getBindSetting(), module.drawn, clickGui.style,
                    clickGui.autoSave, clickGui.font, clickGui.shadow,
                    clickGui.disableNotification, clickGui.sound,
                    clickGui.guiSound, clickGui.soundPitch,
                    clickGui.nakanoGlow, clickGui.nakanoGlowStrength
            );
        }
        return module.getSettings();
    }

    int boundKey() {
        return module.getBindSetting().getValue();
    }

    void setBoundKey(int boundKey) {
        module.getBindSetting().setValue(boundKey);
    }

    Module module() {
        return module;
    }
}