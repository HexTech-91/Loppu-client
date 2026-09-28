package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.HUD;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class Particles extends Module {

    public static final Identifier firefly = Identifier.of("textures/targeresp/firefly.png");
    public static final Identifier star = Identifier.of("textures/targeresp/star.png");
    public static final Identifier heart = Identifier.of("textures/targeresp/heart.png");
    public static final Identifier dollar = Identifier.of("textures/targeresp/dollar.png");
    public static final Identifier snowflake = Identifier.of("textures/targeresp/snowflake.png");
    public final BooleanSetting FireFlies = add(new BooleanSetting("FireFlies", true));
    public final SliderSetting ffcount = add(new SliderSetting("FFCount", 30, 20, 200, 1, () -> FireFlies.getValue()));
    public final SliderSetting ffsize = add(new SliderSetting("FFSize", 1f, 0.1f, 2f, 0.1f, () -> FireFlies.getValue()));
    public final EnumSetting mode = add(new EnumSetting("Mode", Mode.SnowFlake));
    public final SliderSetting count = add(new SliderSetting("Count", 100, 20, 800, 1));
    public final SliderSetting size = add(new SliderSetting("Size", 1f, 0.1f, 6f, 0.1f));
    public final EnumSetting lmode = add(new EnumSetting("ColorMode", ColorMode.Sync));
    public final ColorSetting color = add(new ColorSetting("Color", new Color(3649978),
            () -> lmode.getValue() == ColorMode.Custom));
    public final EnumSetting physics = add(new EnumSetting("Physics", Physics.Fly,
            () -> mode.getValue() != Mode.Off));
    private final ArrayList<ParticleBase> fireFlies = new ArrayList<>();
    private final ArrayList<ParticleBase> particles = new ArrayList<>();

    public Particles() {
        super("Particles", Category.Render);
        setChinese("粒子效果");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        fireFlies.removeIf(ParticleBase::tick);
        particles.removeIf(ParticleBase::tick);

        for (int i = fireFlies.size(); i < ffcount.getValue(); i++) {
            if (FireFlies.getValue())
                fireFlies.add(new FireFly(
                        (float) (mc.player.getX() + MathUtil.random(-25f, 25f)),
                        (float) (mc.player.getY() + MathUtil.random(2f, 15f)),
                        (float) (mc.player.getZ() + MathUtil.random(-25f, 25f)),
                        MathUtil.random(-0.2f, 0.2f),
                        MathUtil.random(-0.1f, 0.1f),
                        MathUtil.random(-0.2f, 0.2f)));
        }

        for (int j = particles.size(); j < count.getValue(); j++) {
            boolean drop = physics.getValue() == Physics.Drop;
            if (mode.getValue() != Mode.Off)
                particles.add(new ParticleBase(
                        (float) (mc.player.getX() + MathUtil.random(-48f, 48f)),
                        (float) (mc.player.getY() + MathUtil.random(2, 48f)),
                        (float) (mc.player.getZ() + MathUtil.random(-48f, 48f)),
                        drop ? 0 : MathUtil.random(-0.4f, 0.4f),
                        drop ? MathUtil.random(-0.2f, -0.05f) : MathUtil.random(-0.1f, 0.1f),
                        drop ? 0 : MathUtil.random(-0.4f, 0.4f)));
        }
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (FireFlies.getValue()) {
            RenderSystem.setShaderTexture(0, firefly);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            fireFlies.forEach(p -> p.render(bufferBuilder));
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
            RenderSystem.depthMask(true);
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
        }

        if (mode.getValue() != Mode.Off) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            particles.forEach(p -> p.render(bufferBuilder));
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
            RenderSystem.depthMask(true);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    public enum ColorMode {
        Custom, Sync
    }

    public enum Mode {
        Off, SnowFlake, Stars, Hearts, Dollars, Bloom
    }

    public enum Physics {
        Drop, Fly
    }

    public static class Trail {
        private final Vec3d from;
        private final Vec3d to;
        private final Color color;
        private int ticks, prevTicks;

        public Trail(Vec3d from, Vec3d to, Color color) {
            this.from = from;
            this.to = to;
            this.ticks = 10;
            this.color = color;
        }

        public Vec3d interpolate(float pt) {
            double x = from.x + ((to.x - from.x) * pt) - mc.getEntityRenderDispatcher().camera.getPos().getX();
            double y = from.y + ((to.y - from.y) * pt) - mc.getEntityRenderDispatcher().camera.getPos().getY();
            double z = from.z + ((to.z - from.z) * pt) - mc.getEntityRenderDispatcher().camera.getPos().getZ();
            return new Vec3d(x, y, z);
        }

        public double animation(float pt) {
            return (this.prevTicks + (this.ticks - this.prevTicks) * pt) / 10.;
        }

        public boolean update() {
            this.prevTicks = this.ticks;
            return this.ticks-- <= 0;
        }

        public Color color() {
            return color;
        }
    }

    public class FireFly extends ParticleBase {
        private final List<Trail> trails = new ArrayList<>();


        public FireFly(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            super(posX, posY, posZ, motionX, motionY, motionZ);
        }

        @Override
        public boolean tick() {

            if (mc.player.squaredDistanceTo(posX, posY, posZ) > 100) age -= 4;
            else if (!mc.world.getBlockState(new BlockPos((int) posX, (int) posY, (int) posZ)).isAir()) age -= 8;
            else age--;

            if (age < 0)
                return true;

            trails.removeIf(Trail::update);

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            Color trailColor = lmode.getValue() == ColorMode.Sync && HUD.INSTANCE != null
                    ? getRainbowColor(age * 10)
                    : color.getValue();
            trails.add(new Trail(new Vec3d(prevposX, prevposY, prevposZ), new Vec3d(posX, posY, posZ), trailColor));
            motionX *= 0.99f;
            motionY *= 0.99f;
            motionZ *= 0.99f;

            return false;
        }

        @Override
        public void render(BufferBuilder bufferBuilder) {
            RenderSystem.setShaderTexture(0, firefly);
            if (!trails.isEmpty()) {
                Camera camera = mc.gameRenderer.getCamera();
                for (Trail ctx : trails) {
                    Vec3d pos = ctx.interpolate(1f);
                    MatrixStack matrices = new MatrixStack();
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
                    matrices.translate(pos.x, pos.y, pos.z);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
                    float anim = (float) ctx.animation(tickDelta);
                    int alpha = (int) (255 * ((float) age / (float) maxAge) * anim);
                    bufferBuilder.vertex(matrix, (float) 0, (float) -ffsize.getValue(), 0).texture(0f, 1f).color(ColorUtil.injectAlpha(ctx.color(), alpha).getRGB());
                    bufferBuilder.vertex(matrix, (float) -ffsize.getValue(), (float) -ffsize.getValue(), 0).texture(1f, 1f).color(ColorUtil.injectAlpha(ctx.color(), alpha).getRGB());
                    bufferBuilder.vertex(matrix, (float) -ffsize.getValue(), 0, 0).texture(1f, 0).color(ColorUtil.injectAlpha(ctx.color(), alpha).getRGB());
                    bufferBuilder.vertex(matrix, 0, 0, 0).texture(0, 0).color(ColorUtil.injectAlpha(ctx.color(), alpha).getRGB());
                }
            }
        }

        private Color getRainbowColor(int offset) {
            float hue = (offset % 360) / 360f;
            return Color.getHSBColor(hue, 0.8f, 1f);
        }
    }


    public class ParticleBase {

        protected float prevposX, prevposY, prevposZ, posX, posY, posZ, motionX, motionY, motionZ;
        protected int age, maxAge;

        public ParticleBase(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            age = (int) MathUtil.random(100, 300);
            maxAge = age;
        }

        public boolean tick() {
            if (mc.player.squaredDistanceTo(posX, posY, posZ) > 4096) age -= 8;
            else age--;

            if (age < 0)
                return true;

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            motionX *= 0.9f;
            if (physics.getValue() == Physics.Fly)
                motionY *= 0.9f;
            motionZ *= 0.9f;

            motionY -= 0.001f;

            return false;
        }

        public void render(BufferBuilder bufferBuilder) {
            Mode m = (Mode) mode.getValue();
            if (m == Mode.Bloom) {
                RenderSystem.setShaderTexture(0, firefly);
            } else if (m == Mode.SnowFlake) {
                RenderSystem.setShaderTexture(0, snowflake);
            } else if (m == Mode.Dollars) {
                RenderSystem.setShaderTexture(0, dollar);
            } else if (m == Mode.Hearts) {
                RenderSystem.setShaderTexture(0, heart);
            } else if (m == Mode.Stars) {
                RenderSystem.setShaderTexture(0, star);
            }


            Camera camera = mc.gameRenderer.getCamera();
            Color color1;
            if (lmode.getValue() == ColorMode.Sync && HUD.INSTANCE != null) {
                color1 = ColorUtil.pulseColor(color.getValue(), age * 2, 10, 1.0);
            } else {
                color1 = color.getValue();
            }
            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            Vec3d pos = new Vec3d(
                    prevposX + (posX - prevposX) * tickDelta,
                    prevposY + (posY - prevposY) * tickDelta,
                    prevposZ + (posZ - prevposZ) * tickDelta
            );

            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            matrices.translate(pos.x, pos.y, pos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            Matrix4f matrix1 = matrices.peek().getPositionMatrix();
            int alpha = (int) (255 * ((float) age / (float) maxAge));
            bufferBuilder.vertex(matrix1, (float) 0, (float) -size.getValue(), 0).texture(0f, 1f).color(ColorUtil.injectAlpha(color1, alpha).getRGB());
            bufferBuilder.vertex(matrix1, (float) -size.getValue(), (float) -size.getValue(), 0).texture(1f, 1f).color(ColorUtil.injectAlpha(color1, alpha).getRGB());
            bufferBuilder.vertex(matrix1, (float) -size.getValue(), 0, 0).texture(1f, 0).color(ColorUtil.injectAlpha(color1, alpha).getRGB());
            bufferBuilder.vertex(matrix1, 0, 0, 0).texture(0, 0).color(ColorUtil.injectAlpha(color1, alpha).getRGB());
        }
    }
}