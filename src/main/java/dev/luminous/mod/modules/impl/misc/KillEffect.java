package dev.luminous.mod.modules.impl.misc;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class KillEffect extends Module {

    private final EnumSetting<KillEffectMode> mode;
    private final BooleanSetting lightning;
    private final BooleanSetting levelUp;
    private final SliderSetting lMaxPitch;
    private final SliderSetting lMinPitch;
    private final BooleanSetting trident;
    private final SliderSetting tMaxPitch;
    private final SliderSetting tMinPitch;
    private final SliderSetting factor;
    private final SliderSetting duration;
    private final SliderSetting scale;
    private final BooleanSetting flash;

    private final List<DeathEffect> deathEffects = new ArrayList<>();

    public enum KillEffectMode {
        DEFAULT,
        HereHasALowiqDie
    }

    public KillEffect() {
        super("KillEffect", Category.Misc);

        this.mode = add(new EnumSetting<>("Mode", KillEffectMode.DEFAULT));

        this.lightning = add(new BooleanSetting("Lightning", true,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT));

        this.levelUp = add(new BooleanSetting("LevelUp", true,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT).setParent());

        this.lMaxPitch = add(new SliderSetting("LMaxPitch", 1.0, 0.0, 2.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT && this.levelUp.isOpen()));

        this.lMinPitch = add(new SliderSetting("LMinPitch", 1.0, 0.0, 2.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT && this.levelUp.isOpen()));

        this.trident = add(new BooleanSetting("Trident", false,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT).setParent());

        this.tMaxPitch = add(new SliderSetting("TMaxPitch", 1.0, 0.0, 2.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT && this.trident.isOpen()));

        this.tMinPitch = add(new SliderSetting("TMinPitch", 1.0, 0.0, 2.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT && this.trident.isOpen()));

        this.factor = add(new SliderSetting("Factor", 1.0, 1.0, 10.0, 1.0,
                () -> this.mode.getValue() == KillEffectMode.DEFAULT));

        this.duration = add(new SliderSetting("Duration", 3.0, 1.0, 10.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.HereHasALowiqDie));

        this.scale = add(new SliderSetting("Scale", 1.0, 0.1, 3.0, 0.1,
                () -> this.mode.getValue() == KillEffectMode.HereHasALowiqDie));

        this.flash = add(new BooleanSetting("Flash", true,
                () -> this.mode.getValue() == KillEffectMode.HereHasALowiqDie));

        setChinese("击杀标识");
    }

    @EventListener
    public void onPlayerDeath(DeathEvent event) {
        PlayerEntity player = event.getPlayer();
        if (nullCheck() || player == null) return;

        if (this.mode.getValue() == KillEffectMode.DEFAULT) {
            for (int i = 0; i < this.factor.getValue(); i++) {
                doEffect(player);
            }
        } else if (this.mode.getValue() == KillEffectMode.HereHasALowiqDie) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean exists = false;
            for (DeathEffect effect : this.deathEffects) {
                if (Math.abs(effect.x - x) < 0.5 &&
                        Math.abs(effect.y - y) < 0.5 &&
                        Math.abs(effect.z - z) < 0.5) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.deathEffects.add(new DeathEffect(x, y + 1.0, z, System.currentTimeMillis()));
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || this.mode.getValue() != KillEffectMode.HereHasALowiqDie) return;

        long now = System.currentTimeMillis();
        this.deathEffects.removeIf(effect ->
                (double)(now - effect.timestamp) > this.duration.getValue() * 1000.0
        );
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || this.mode.getValue() != KillEffectMode.HereHasALowiqDie) return;

        // 使用字段而非方法
        MatrixStack matrices = event.matrixStack;

        for (DeathEffect effect : this.deathEffects) {
            boolean shouldRender = !this.flash.getValue() || (System.currentTimeMillis() / 200) % 2 == 0;
            if (shouldRender) {
                drawEffect(matrices, effect.x, effect.y, effect.z, 1.0f);
            }
        }
    }

    private void doEffect(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        if (this.lightning.getValue()) {
            LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
            lightning.refreshPositionAfterTeleport(x, y, z);
            lightning.setPos(x, y, z);
            mc.world.addEntity(lightning);
        }

        if (this.levelUp.getValue()) {
            mc.world.playSound(mc.player, x, y, z,
                    SoundEvents.ENTITY_PLAYER_LEVELUP,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    100.0f,
                    MathUtil.random(this.lMinPitch.getValueFloat(), this.lMaxPitch.getValueFloat()));
        }

        if (this.trident.getValue()) {
            mc.world.playSound(mc.player, x, y, z,
                    SoundEvents.ITEM_TRIDENT_THUNDER,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    999.0f,
                    MathUtil.random(this.tMinPitch.getValueFloat(), this.tMaxPitch.getValueFloat()));
        }
    }

    private void drawEffect(MatrixStack matrixStack, double x, double y, double z, float alpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        matrixStack.push();
        matrixStack.translate(x - camX, y - camY, z - camZ);
        matrixStack.multiply(camera.getRotation());

        double scaleValue = 0.0245 * this.scale.getValue();
        matrixStack.scale((float) scaleValue, (float) scaleValue, (float) scaleValue);

        // 使用 Identifier.of
        Identifier circleTexture = Identifier.of("loppu", "textures/killeffect/yuanquan.png");
        RenderSystem.setShaderTexture(0, circleTexture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        drawTexture(matrixStack, -32.0f, -32.0f, 64.0f, 64.0f);

        Identifier arrowTexture = Identifier.of("loppu", "textures/killeffect/jiantou.png");
        RenderSystem.setShaderTexture(0, arrowTexture);
        drawTexture(matrixStack, -5.0f, -5.0f, 64.0f, 64.0f);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        matrixStack.pop();
    }

    private void drawTexture(MatrixStack matrices, float x, float y, float width, float height) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        // 1.21 新 API
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 不再需要 .next()
        buffer.vertex(matrix, x, y + height, 0.0f).texture(0.0f, 0.0f);
        buffer.vertex(matrix, x + width, y + height, 0.0f).texture(1.0f, 0.0f);
        buffer.vertex(matrix, x + width, y, 0.0f).texture(1.0f, 1.0f);
        buffer.vertex(matrix, x, y, 0.0f).texture(0.0f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static class DeathEffect {
        public final double x;
        public final double y;
        public final double z;
        public final long timestamp;

        public DeathEffect(double x, double y, double z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}