package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.client.SkinChanger;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class MixinAbstractClientPlayerEntity {

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        if (SkinChanger.INSTANCE != null && SkinChanger.INSTANCE.isOn()) {
            AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
            SkinTextures result = SkinChanger.INSTANCE.getCustomSkinTextures(player);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }
}