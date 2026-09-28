package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.OpenScreenEvent;
import dev.luminous.mod.modules.Module;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    public static AutoRespawn INSTANCE;
    
    public AutoRespawn() {
        super("AutoRespawn", Category.Misc);
        this.setChinese("自动重生");
        INSTANCE = this;
    }
    
    @EventListener
    public void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof DeathScreen)) return;
        mc.player.requestRespawn();
        event.cancel();
    }
}
