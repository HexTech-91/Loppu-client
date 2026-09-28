package dev.luminous.asm.mixins;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.render.EveryonePig;
import dev.luminous.mod.modules.impl.render.NoRender;
import dev.luminous.mod.modules.impl.render.PlayerScale;
import dev.luminous.mod.modules.impl.render.RotationVisual;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {
    @Unique
    private LivingEntity lastEntity;
    @Unique
    private float originalYaw;
    @Unique
    private float originalHeadYaw;
    @Unique
    private float originalBodyYaw;
    @Unique
    private float originalPitch;
    @Unique
    private float originalPrevYaw;
    @Unique
    private float originalPrevHeadYaw;
    @Unique
    private float originalPrevBodyYaw;
    @Unique
    private float rotationVisualYawOffset;
    @Unique
    private float rotationVisualPitchOffset;

    @Inject(method = {"render*"}, at = @At("HEAD"), cancellable = true)
    public void onRenderPre(T livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        // EveryonePig: cancel player rendering
        if (EveryonePig.INSTANCE != null && EveryonePig.INSTANCE.isOn() && livingEntity instanceof PlayerEntity player) {
            if (EveryonePig.INSTANCE.shouldReplace(player)) {
                ci.cancel();
                return;
            }
        }

        if (MinecraftClient.getInstance().player != null && livingEntity == MinecraftClient.getInstance().player && ClientSetting.INSTANCE.rotations.getValue()) {
            this.originalYaw = livingEntity.getYaw();
            this.originalHeadYaw = livingEntity.headYaw;
            this.originalBodyYaw = livingEntity.bodyYaw;
            this.originalPitch = livingEntity.getPitch();
            this.originalPrevYaw = livingEntity.prevYaw;
            this.originalPrevHeadYaw = livingEntity.prevHeadYaw;
            this.originalPrevBodyYaw = livingEntity.prevBodyYaw;
            livingEntity.setYaw(RotationManager.getRenderYawOffset());
            livingEntity.headYaw = RotationManager.getRotationYawHead();
            livingEntity.bodyYaw = RotationManager.getRenderYawOffset();
            livingEntity.setPitch(RotationManager.getRenderPitch());
            livingEntity.prevYaw = RotationManager.getPrevRenderYawOffset();
            livingEntity.prevHeadYaw = RotationManager.getPrevRotationYawHead();
            livingEntity.prevBodyYaw = RotationManager.getPrevRenderYawOffset();
            livingEntity.prevPitch = RotationManager.getPrevRenderPitch();
        }

        if (RotationVisual.INSTANCE != null && RotationVisual.INSTANCE.isOn()) {
            this.rotationVisualYawOffset = RotationVisual.INSTANCE.yaw.getValueFloat();
            this.rotationVisualPitchOffset = RotationVisual.INSTANCE.pitch.getValueFloat();
            boolean shouldRotate = false;
            if (livingEntity == MinecraftClient.getInstance().player && RotationVisual.INSTANCE.self.getValue()) {
                shouldRotate = true;
            } else if (livingEntity != MinecraftClient.getInstance().player && RotationVisual.INSTANCE.others.getValue()) {
                shouldRotate = true;
            }
            if (shouldRotate) {
                matrixStack.push();
                matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(this.rotationVisualYawOffset));
                matrixStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(this.rotationVisualPitchOffset));
            }
        }

        // PlayerScale: scale player model locally
        if (PlayerScale.INSTANCE != null && PlayerScale.INSTANCE.isOn() && livingEntity instanceof PlayerEntity player) {
            if (PlayerScale.INSTANCE.shouldScale(player)) {
                matrixStack.push();
                float s = PlayerScale.INSTANCE.scale.getValueFloat();
                matrixStack.scale(s, s, s);
            }
        }

        this.lastEntity = livingEntity;
    }

    @Inject(method = {"render*"}, at = @At("TAIL"))
    public void onRenderPost(T livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        if (MinecraftClient.getInstance().player != null && livingEntity == MinecraftClient.getInstance().player && ClientSetting.INSTANCE.rotations.getValue()) {
            livingEntity.setYaw(this.originalYaw);
            livingEntity.headYaw = this.originalHeadYaw;
            livingEntity.bodyYaw = this.originalBodyYaw;
            livingEntity.setPitch(this.originalPitch);
            livingEntity.prevYaw = this.originalPrevYaw;
            livingEntity.prevHeadYaw = this.originalPrevHeadYaw;
            livingEntity.prevBodyYaw = this.originalPrevBodyYaw;
            livingEntity.prevPitch = this.originalPitch;
        }

        if (RotationVisual.INSTANCE != null && RotationVisual.INSTANCE.isOn()) {
            boolean shouldRotate = false;
            if (livingEntity == MinecraftClient.getInstance().player && RotationVisual.INSTANCE.self.getValue()) {
                shouldRotate = true;
            } else if (livingEntity != MinecraftClient.getInstance().player && RotationVisual.INSTANCE.others.getValue()) {
                shouldRotate = true;
            }
            if (shouldRotate) {
                matrixStack.pop();
            }
        }

        // PlayerScale: pop scale matrix
        if (PlayerScale.INSTANCE != null && PlayerScale.INSTANCE.isOn() && livingEntity instanceof PlayerEntity player) {
            if (PlayerScale.INSTANCE.shouldScale(player)) {
                matrixStack.pop();
            }
        }
    }

    @ModifyArgs(method = {"render*"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void renderHook(Args args) {
        PlayerEntity pl;
        LivingEntity livingEntity;
        float alpha = -1.0f;
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.antiPlayerCollision.getValue() && this.lastEntity != Wrapper.mc.player && (livingEntity = this.lastEntity) instanceof PlayerEntity && !(pl = (PlayerEntity)livingEntity).isInvisible()) {
            alpha = MathUtil.clamp((float)(Wrapper.mc.player.squaredDistanceTo(this.lastEntity.getPos()) / 3.0) + 0.2f, 0.0f, 1.0f);
        }
        if (alpha != -1.0f) {
            args.set(4, (Object)this.applyOpacity(0x26FFFFFF, alpha));
        }
    }

    @Unique
    int applyOpacity(int color_int, float opacity) {
        opacity = Math.min(1.0f, Math.max(0.0f, opacity));
        Color color = new Color(color_int);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)((float)color.getAlpha() * opacity)).getRGB();
    }
}