package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class GrimLowHop
        extends Module {
    public static GrimLowHop INSTANCE;
    private final BooleanSetting logging = this.add(new BooleanSetting("Logging", false));
    private final SliderSetting startTicks = this.add(new SliderSetting("StartTick", 2, 0, 10));
    private final SliderSetting skipTicks = this.add(new SliderSetting("SkipTicks", 2, 1, 10));
    private final SliderSetting ticks = this.add(new SliderSetting("Ticks", 3, 1, 10));
    private int airTicks;
    private boolean canTimer;
    private boolean timer;

    public GrimLowHop() {
        super("GrimLowHop", Module.Category.Movement);
        this.setChinese("Grim低跳");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return this.skipTicks.getValueInt() + "+" + this.ticks.getValueInt();
    }

    @Override
    public void onEnable() {
        this.airTicks = 0;
        this.canTimer = false;
        this.timer = false;
        Alien.TIMER.clearSkipTicks();
    }

    @Override
    public void onDisable() {
        this.airTicks = 0;
        this.canTimer = false;
        this.timer = false;
        Alien.TIMER.clearSkipTicks();
    }

    private void log(String message) {
        if (this.logging.getValue()) {
            CommandManager.sendMessage(message);
        }
    }

    @EventListener
    public void onPreTick(ClientTickEvent event) {
        if (event.isPost() || GrimLowHop.nullCheck()) {
            return;
        }
        if (this.airTicks >= this.startTicks.getValueInt() && !this.canTimer) {
            Alien.TIMER.addSkipTicks(this.skipTicks.getValueInt());
            this.canTimer = true;
            this.timer = false;
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (GrimLowHop.nullCheck()) {
            return;
        }

        if (GrimLowHop.mc.player.isOnGround()) {
            this.airTicks = 0;
            this.canTimer = false;
            this.timer = false;
            return;
        }

        ++this.airTicks;
        if (this.canTimer && !this.timer) {
            this.timer = true;
            this.log("GrimLowHop trigger: airTicks=" + this.airTicks + " skipTicks=" + this.skipTicks.getValueInt() + " ticks=" + this.ticks.getValueInt());
            int count = this.ticks.getValueInt();
            for (int i = 0; i < count; ++i) {
                GrimLowHop.mc.player.tick();
            }
            this.log("GrimLowHop done");
        }
    }

    @EventListener(priority=-200)
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (GrimLowHop.nullCheck()) {
            return;
        }
        GrimLowHop.mc.player.input.jumping = true;
    }
}
