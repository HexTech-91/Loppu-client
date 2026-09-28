package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.JelloUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public class Follower extends Module {
    public static Follower INSTANCE;

    private Entity target;
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.LowestDistance));
    private final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 5.0, 100.0));
    private final BooleanSetting onlyElytraFly = this.add(new BooleanSetting("OnlyElytraFly", true));
    private final EnumSetting<TargetESP> renderMode = this.add(new EnumSetting<>("RenderMode", TargetESP.Fill));
    private final SliderSetting animationTime = this.add(new SliderSetting("AnimationTime", 200.0, 0.0, 2000.0, 1.0));
    private final EnumSetting<Easing> ease = this.add(new EnumSetting<>("Ease", Easing.CubicInOut));
    private final ColorSetting color = this.add(new ColorSetting("Color", new Color(160, 0, 225, 50)));
    private final ColorSetting outlineColor = this.add(new ColorSetting("OutlineColor", new Color(255, 255, 255, 100)));
    private final ColorSetting hitColor = this.add(new ColorSetting("HitColor", new Color(255, 255, 255, 150)));
    private final ColorSetting hitOutlineColor = this.add(new ColorSetting("HitOutlineColor", new Color(255, 255, 255, 150)));
    private final Animation animation = new Animation();
    private final BooleanSetting render = this.add(new BooleanSetting("Render", true));
    private final SliderSetting rotationSpeed = this.add(new SliderSetting("RotationSpeed", 180.0, 10.0, 360.0));
    private final SliderSetting priority = this.add(new SliderSetting("Priority", 5.0, 0.0, 10.0));

    public Follower() {
        super("Follower", Category.Movement);
        setChinese("烟花追人-测试");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return target == null ? null : target.getName().getString();
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (event.isPost()) return;
        if (!nullCheck()) {
            if (onlyElytraFly.getValue() && !dev.luminous.mod.modules.impl.movement.ElytraFly.INSTANCE.isOn()) {
                target = null;
                return;
            }
            target = findTarget();
        }
    }

    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (!nullCheck() && target != null && (!onlyElytraFly.getValue() || dev.luminous.mod.modules.impl.movement.ElytraFly.INSTANCE.isOn())) {
            Vec3d hitVec = getHitVec(target);
            float[] rotations = getRotationsToVec(hitVec);
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);
        }
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (target != null && render.getValue()) {
            float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
            doRender(matrixStack, partialTicks, target);
        }
    }

    private void doRender(MatrixStack stack, float partialTicks, Entity entity) {
        Box bb = entity.getBoundingBox();
        Vec3d interpolated = new Vec3d(
                MathUtil.interpolate(entity.prevX, entity.getX(), partialTicks),
                MathUtil.interpolate(entity.prevY, entity.getY(), partialTicks),
                MathUtil.interpolate(entity.prevZ, entity.getZ(), partialTicks)
        );
        Box renderBox = bb.offset(interpolated.subtract(entity.getPos()));
        renderBox = renderBox.expand(0.0, 0.1, 0.0);

        switch (renderMode.getValue()) {
            case Fill:
                Render3DUtil.draw3DBox(stack, renderBox,
                        ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0.0, animationTime.getValueInt(), ease.getValue())),
                        ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0.0, animationTime.getValueInt(), ease.getValue())),
                        false, true);
                break;
            case Box:
                Render3DUtil.draw3DBox(stack, renderBox,
                        ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0.0, animationTime.getValueInt(), ease.getValue())),
                        ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0.0, animationTime.getValueInt(), ease.getValue())),
                        true, true);
                break;
            case Jello:
                JelloUtil.drawJello(stack, entity, color.getValue());
                break;
            case ThunderHack:
                Render3DUtil.drawTargetEsp(stack, target, color.getValue());
                break;
        }
    }

    private Entity findTarget() {
        Entity bestTarget = null;
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (!isValidTarget(entity)) continue;
            if (bestTarget == null) {
                bestTarget = entity;
                continue;
            }
            double distance = mc.player.getEyePos().distanceTo(entity.getPos());
            float health = EntityUtil.getHealth(entity);
            switch (mode.getValue()) {
                case LowestDistance:
                    double bestDistance = mc.player.getEyePos().distanceTo(bestTarget.getPos());
                    if (!(distance < bestDistance)) break;
                    bestTarget = entity;
                    break;
                case LowestHealth:
                    float bestHealth = EntityUtil.getHealth(bestTarget);
                    if (health < bestHealth) {
                        bestTarget = entity;
                        break;
                    }
                    if (health != bestHealth) break;
                    double currentTargetDistance = mc.player.getEyePos().distanceTo(bestTarget.getPos());
                    if (!(distance < currentTargetDistance)) break;
                    bestTarget = entity;
                    break;
            }
        }
        return bestTarget;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof PlayerEntity)) return false;
        PlayerEntity player = (PlayerEntity) entity;
        if (player == mc.player) return false;
        if (Alien.FRIEND.isFriend(player)) return false;
        if (!entity.isAlive()) return false;
        double distance = mc.player.getEyePos().distanceTo(entity.getPos());
        return !(distance > range.getValue());
    }

    private Vec3d getHitVec(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double targetCenterX = (box.minX + box.maxX) / 2.0;
        double targetCenterZ = (box.minZ + box.maxZ) / 2.0;
        double playerEyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double heightOffset = playerEyeHeight * 0.5;
        if (entity instanceof PlayerEntity) {
            heightOffset = (double) entity.getHeight() * 0.5;
        }
        double targetY = box.minY + heightOffset;
        double closestX = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
        double closestZ = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));
        return new Vec3d(closestX, targetY, closestZ);
    }

    private float[] getRotationsToVec(Vec3d vec) {
        Vec3d eyes = mc.player.getEyePos();
        double diffX = vec.x - eyes.x;
        double diffY = vec.y - eyes.y;
        double diffZ = vec.z - eyes.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        return new float[]{yaw, pitch};
    }

    public enum Mode {
        LowestDistance,
        LowestHealth
    }

    public enum TargetESP {
        Fill,
        Box,
        Jello,
        ThunderHack
    }
}