package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.mod.gui.mainmenu.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void replaceWithCustomMenu(CallbackInfo ci) {
        if (this.client == null) return;

        this.client.setScreen(new MainMenuScreen());
        ci.cancel();
    }
}
