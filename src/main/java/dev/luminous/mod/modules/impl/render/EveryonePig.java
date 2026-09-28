package dev.luminous.mod.modules.impl.render;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class EveryonePig extends Module {
    public static EveryonePig INSTANCE;

    public enum Target {
        Self,
        Others,
        Both
    }

    public final EnumSetting<Target> target = add(new EnumSetting<>("Target", Target.Both));
    public final BooleanSetting firstPersonSelf = add(new BooleanSetting("FirstPersonSelf", false));

    private final Map<UUID, PigEntity> pigs = new HashMap<>();

    public EveryonePig() {
        super("EveryonePig", Category.Render);
        setChinese("全员变猪");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        pigs.clear();
    }

    public boolean shouldReplace(PlayerEntity player) {
        if (player == mc.player) {
            return target.getValue() == Target.Self || target.getValue() == Target.Both;
        }
        return target.getValue() == Target.Others || target.getValue() == Target.Both;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (nullCheck()) return;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        // 遍历所有实体（包括FakePlayer），找出PlayerEntity
        List<UUID> activeUuids = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            activeUuids.add(player.getUuid());
            if (player == null || player.isDead() || !player.isAlive()) continue;
            if (!shouldReplace(player)) continue;

            // Skip self in first person unless FirstPersonSelf is enabled
            if (player == mc.player && mc.options.getPerspective().isFirstPerson() && !firstPersonSelf.getValue()) {
                continue;
            }

            PigEntity pig = getPig(player);

            double x = MathHelper.lerp(tickDelta, player.prevX, player.getX());
            double y = MathHelper.lerp(tickDelta, player.prevY, player.getY());
            double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
            float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);

            pig.refreshPositionAndAngles(x, y, z, player.getYaw(), player.getPitch());
            pig.prevX = pig.getX();
            pig.prevY = pig.getY();
            pig.prevZ = pig.getZ();
            pig.bodyYaw = bodyYaw;
            pig.prevBodyYaw = bodyYaw;
            pig.headYaw = player.headYaw;
            pig.prevHeadYaw = player.prevHeadYaw;
            pig.limbAnimator.setSpeed(player.limbAnimator.getSpeed());
            pig.limbAnimator.pos = player.limbAnimator.getPos();
            pig.age = player.age;
            pig.setSneaking(player.isSneaking());
            pig.setPose(player.getPose());

            double camX = mc.getEntityRenderDispatcher().camera.getPos().getX();
            double camY = mc.getEntityRenderDispatcher().camera.getPos().getY();
            double camZ = mc.getEntityRenderDispatcher().camera.getPos().getZ();

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            GL11.glDisable(32823);
            GL11.glPolygonOffset(0.0f, 0.0f);

            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            mc.getEntityRenderDispatcher().render(pig,
                    x - camX, y - camY, z - camZ,
                    bodyYaw, tickDelta, matrixStack, immediate,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
            immediate.draw();

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }

        // 清理已不存在的玩家对应的猪实体
        pigs.keySet().removeIf(key -> !activeUuids.contains(key));
    }

    private PigEntity getPig(PlayerEntity player) {
        UUID uuid = player.getUuid();
        return pigs.computeIfAbsent(uuid, key -> {
            PigEntity pig = new PigEntity(EntityType.PIG, mc.world);
            pig.setId(-Math.abs(uuid.hashCode()));
            pig.setSilent(true);
            pig.setNoGravity(true);
            return pig;
        });
    }
}