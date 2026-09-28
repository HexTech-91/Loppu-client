package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.events.Event;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateWalkingPlayerEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class DesyncESP extends Module {
    public static DesyncESP INSTANCE;
    private final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255)));
    private final EnumSetting<Type> type = add(new EnumSetting<>("Type", Type.ServerSide));
    Model model;
    boolean update = true;
    float lastYaw;
    float lastPitch;

    public DesyncESP() {
        super("DesyncESP", Module.Category.Render);
        this.setChinese("反同步显示");
        INSTANCE = this;
    }

    private static void prepareScale(MatrixStack matrixStack) {
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        matrixStack.scale(1.6f, 1.8f, 1.6f);
        matrixStack.translate(0.0F, -1.501F, 0.0F);
    }

    @Override
    public void onLogin() {
        update = true;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (update) {
            model = new Model();
            update = false;
        }
    }

    @EventListener
    public void onUpdateWalkingPost(UpdateWalkingPlayerEvent event) {
        if (event.stage == Event.Stage.Post) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (nullCheck() || model == null) return;
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }
        if (Math.abs(lastYaw - Alien.ROTATION.lastYaw) < 1 && Math.abs(lastPitch - Alien.ROTATION.lastPitch) < 1)
            return;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 0, 1);
/*        model.modelPlayer.leftPants.visible = false;
        model.modelPlayer.rightPants.visible = false;
        model.modelPlayer.leftSleeve.visible = false;
        model.modelPlayer.rightSleeve.visible = false;
        model.modelPlayer.jacket.visible = false;
        model.modelPlayer.hat.visible = false;*/
        double tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        double x = mc.player.prevX + (mc.player.getX() - mc.player.prevX) * tickDelta - mc.getEntityRenderDispatcher().camera.getPos().getX();
        double y = mc.player.prevY + (mc.player.getY() - mc.player.prevY) * tickDelta - mc.getEntityRenderDispatcher().camera.getPos().getY();
        double z = mc.player.prevZ + (mc.player.getZ() - mc.player.prevZ) * tickDelta - mc.getEntityRenderDispatcher().camera.getPos().getZ();

        float bodyYaw = type.getValue() == Type.ServerSide ? RotationManager.getPrevRenderYawOffset() + (RotationManager.getRenderYawOffset() - RotationManager.getPrevRenderYawOffset()) * (float) tickDelta
                : mc.player.prevBodyYaw + (mc.player.bodyYaw - mc.player.prevBodyYaw) * (float) tickDelta;
        float headYaw = type.getValue() == Type.ServerSide ? RotationManager.getPrevRotationYawHead() + (RotationManager.getRotationYawHead() - RotationManager.getPrevRotationYawHead()) * (float) tickDelta
                : mc.player.prevHeadYaw + (mc.player.headYaw - mc.player.prevHeadYaw) * (float) tickDelta;
        float pitch = type.getValue() == Type.ServerSide ? RotationManager.getPrevRenderPitch() + (RotationManager.getRenderPitch() - RotationManager.getPrevRenderPitch()) * (float) tickDelta
                : mc.player.prevPitch + (mc.player.getPitch() - mc.player.prevPitch) * (float) tickDelta;
        matrixStack.push();
        matrixStack.translate((float) x, (float) y, (float) z);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtil.rad(180 - bodyYaw)));
        prepareScale(matrixStack);

        float tickDeltaF = (float) tickDelta;
        model.modelPlayer.animateModel(mc.player, mc.player.limbAnimator.getPos(tickDeltaF), mc.player.limbAnimator.getSpeed(tickDeltaF), tickDeltaF);
        model.modelPlayer.setAngles(mc.player, mc.player.limbAnimator.getPos(tickDeltaF), mc.player.limbAnimator.getSpeed(tickDeltaF), mc.player.age, headYaw - bodyYaw, pitch);

        RenderSystem.enableBlend();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Color c = color.getValue();
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float a = c.getAlpha() / 255f;
        RenderSystem.setShaderColor(r, g, b, a);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        model.modelPlayer.render(matrixStack, buffer, 10, 0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        matrixStack.pop();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    public enum Type {
        ClientSide,
        ServerSide
    }

    private static class Model {
        private final PlayerEntityModel<PlayerEntity> modelPlayer;

        public Model() {
            modelPlayer = new PlayerEntityModel<>(new EntityRendererFactory.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderManager(), mc.getEntityRenderDispatcher().getHeldItemRenderer(), mc.getResourceManager(), mc.getEntityModelLoader(), mc.textRenderer).getPart(EntityModelLayers.PLAYER), false);
            modelPlayer.getHead().scale(new Vector3f(-0.3f, -0.3f, -0.3f));
        }
    }
}