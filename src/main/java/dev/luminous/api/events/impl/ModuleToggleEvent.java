package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import dev.luminous.mod.modules.Module;

public class ModuleToggleEvent extends Event {
    private final Module module;
    private final boolean state;

    public ModuleToggleEvent(Module module, boolean state) {
        this.module = module;
        this.state = state;
    }

    public Module getModule() {
        return this.module;
    }

    public boolean getState() {
        return this.state;
    }

    public boolean isEnabled() {
        return this.state;
    }
}
