package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class CrystalPlaceESP extends Module {
    public static CrystalPlaceESP INSTANCE;

    private final ConcurrentHashMap<EndCrystalEntity, RenderInfo> cryList = new ConcurrentHashMap<>();
    private final Timer timer = new Timer();
    BooleanSetting range = add(new BooleanSetting("Check Range", true)).setParent();
    SliderSetting rangeValue = add(new SliderSetting("Range", 12, 0, 256, () -> range.getValue()));
    SliderSetting animationTime = add(new SliderSetting("AnimationTime ", 500, 0, 1500));
    SliderSetting fadeSpeed = add(new SliderSetting("FadeSpeed", 500, 0, 1500, 0.1));
    EnumSetting<Mode> mode = add(new EnumSetting<Mode>("Mode", Mode.Normal));
    SliderSetting pointsNew = add(new SliderSetting("Points", 3, 1, 10, () -> mode.getValue() == Mode.Normal));
    SliderSetting interval = add(new SliderSetting("Interval ", 2, 1, 100, () -> mode.getValue() == Mode.New));
    ColorSetting attackColor = add(new ColorSetting("AttackColor", new Color(255, 255, 255, 150)).injectBoolean(true));
    ColorSetting defendColor = add(new ColorSetting("DefendColor", new Color(255, 255, 255, 150)).injectBoolean(true));

    public CrystalPlaceESP() {
        super("CrystalPlaceESP", Module.Category.Render);
        this.setChinese("水晶放置ESP");
        INSTANCE = this;
    }

    public static void drawCircle3D(MatrixStack stack, Entity ent, float radius, float height, Color color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        GL11.glLineWidth(2);
        double x = ent.prevX + (ent.getX() - ent.prevX) * mc.getRenderTickCounter().getTickDelta(false) - mc.getEntityRenderDispatcher().camera.getPos().getX();
        double y = ent.prevY + (ent.getY() - ent.prevY) * mc.getRenderTickCounter().getTickDelta(false) - mc.getEntityRenderDispatcher().camera.getPos().getY();
        double z = ent.prevZ + (ent.getZ() - ent.prevZ) * mc.getRenderTickCounter().getTickDelta(false) - mc.getEntityRenderDispatcher().camera.getPos().getZ();
        stack.push();
        stack.translate(x, y, z);

        float r = (float) color.getRed() / 255.0f;
        float g = (float) color.getGreen() / 255.0f;
        float b = (float) color.getBlue() / 255.0f;
        float a = (float) color.getAlpha() / 255.0f;
        Matrix4f matrix = stack.peek().getPositionMatrix();
        for (int i = 0; i <= 180; i++) {
            bufferBuilder.vertex(matrix, (float) (radius * Math.cos(i * 6.28 / 45)), 0f, (float) (radius * Math.sin(i * 6.28 / 45))).color(r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
        stack.translate(-x, -y + height, -z);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        stack.pop();
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        for (Entity e : new Iterable<Entity>() {
            @Override
            public Iterator<Entity> iterator() {
                return mc.world.getEntities().iterator();
            }
        }) {
            if (!(e instanceof EndCrystalEntity)) continue;
            if (range.getValue() && mc.player.distanceTo(e) > rangeValue.getValue()) continue;
            if (!cryList.containsKey(e)) {
                cryList.put((EndCrystalEntity) e, new RenderInfo((EndCrystalEntity) e, System.currentTimeMillis()));
            }
        }

        if (mode.getValue().equals(Mode.Normal)) {
            cryList.forEach((e, renderInfo) -> draw(matrixStack, renderInfo.entity, renderInfo.time, renderInfo.time));
        } else if (mode.getValue().equals(Mode.New)) {
            var time = 0;
            for (int i = 0; i < pointsNew.getValue(); i++) {
                if (timer.passedMs(500)) {
                    int finalTime = time;
                    cryList.forEach((e, renderInfo) ->
                            draw(matrixStack, renderInfo.entity, renderInfo.time - finalTime, renderInfo.time - finalTime)
                    );
                }
                time += interval.getValue();
            }
        }

        cryList.forEach((e, renderInfo) -> {
            if (((System.currentTimeMillis() - renderInfo.time) > animationTime.getValue()) && !e.isAlive()) {
                cryList.remove(e);
            }
            if (((System.currentTimeMillis() - renderInfo.time) > animationTime.getValue()) && mc.player.distanceTo(e) > rangeValue.getValue()) {
                cryList.remove(e);
            }
        });
    }

    private void draw(MatrixStack matrixStack, EndCrystalEntity entity, long radTime, long heightTime) {
        var rad = System.currentTimeMillis() - radTime;
        var height = System.currentTimeMillis() - heightTime;
        if (rad <= animationTime.getValue()) {
            if (attackColor.booleanValue) {
                drawCircle3D(matrixStack, entity, rad / fadeSpeed.getValueFloat(), height / 1000F, attackColor.getValue());
            }
            if (defendColor.booleanValue) {
                drawCircle3D(matrixStack, entity, rad / fadeSpeed.getValueFloat(), height / 1000F, defendColor.getValue());
            }
        }
    }

    @Override
    public void onDisable() {
        cryList.clear();
    }

    public enum Mode {
        Normal,
        New
    }

    record RenderInfo(EndCrystalEntity entity, long time) {
    }
}