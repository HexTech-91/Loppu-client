package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AutoAim extends Module {
    public static AutoAim INSTANCE;

    // ===== 页面分组 =====
    public enum Page {
        General,
        Aim,
        Bow,
        Rotation,
        Render
    }
    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));

    // ===== General 页面 =====
    private final BooleanSetting enabled = add(new BooleanSetting("Enabled", true, () -> this.page.is(Page.General)));

    // ===== Aim 页面 =====
    private final SliderSetting range = add(new SliderSetting("Range", 5.0, 5.0, 100.0, 0.1, () -> this.page.is(Page.Aim)).setSuffix("m"));
    private final BooleanSetting ignoreFriends = add(new BooleanSetting("IgnoreFriends", true, () -> this.page.is(Page.Aim)));

    // 瞄准修正
    private final SliderSetting horizontalOffset = add(new SliderSetting("HOffset", 0.0, -10.0, 10.0, 0.1, () -> this.page.is(Page.Aim)).setSuffix("°"));
    private final SliderSetting verticalOffset = add(new SliderSetting("VOffset", 0.0, -10.0, 10.0, 0.1, () -> this.page.is(Page.Aim)).setSuffix("°"));

    // 预测功能
    private final BooleanSetting enablePrediction = add(new BooleanSetting("Prediction", true, () -> this.page.is(Page.Aim)).setParent());
    private final SliderSetting predictionTicks = add(new SliderSetting("PredictionTicks", 3.0, 0.0, 10.0, 0.1, () -> this.enablePrediction.isOpen() && this.page.is(Page.Aim)));
    private final SliderSetting predictionMultiplier = add(new SliderSetting("PredictionMulti", 0.5, 0.1, 2.0, 0.1, () -> this.enablePrediction.isOpen() && this.page.is(Page.Aim)));

    // 弹道补偿
    private final BooleanSetting ballisticCompensation = add(new BooleanSetting("BallisticComp", true, () -> this.page.is(Page.Aim)).setParent());
    private final SliderSetting ballisticFactor = add(new SliderSetting("BallisticFactor", 1.0, 0.5, 3.0, 0.1, () -> this.ballisticCompensation.isOpen() && this.page.is(Page.Aim)));

    // 墙壁检测
    private final BooleanSetting wallCheck = add(new BooleanSetting("WallCheck", true, () -> this.page.is(Page.Aim)).setParent());
    private final SliderSetting wallCheckRange = add(new SliderSetting("WallCheckRange", 3.0, 0.0, 10.0, 0.1, () -> this.wallCheck.isOpen() && this.page.is(Page.Aim)).setSuffix("m"));

    // ===== Render 页面 =====
    private final BooleanSetting showLine = add(new BooleanSetting("ShowLine", true, () -> this.page.is(Page.Render)).setParent());
    private final ColorSetting lineColor = add(new ColorSetting("LineColor", new Color(255, 0, 0, 200), () -> this.showLine.isOpen() && this.page.is(Page.Render)));

    // 好友线颜色
    private final BooleanSetting friendLineColor = add(new BooleanSetting("FriendLineColor", true, () -> this.page.is(Page.Render)).setParent());
    private final ColorSetting friendLineColorValue = add(new ColorSetting("FriendColor", new Color(0, 255, 0, 200), () -> this.friendLineColor.isOpen() && this.page.is(Page.Render)));

    // 强制客户端视角
    private final BooleanSetting forceClientView = add(new BooleanSetting("ForceClientView", true, () -> this.page.is(Page.Render)));

    // ===== Bow 页面 =====
    private final BooleanSetting autoSwitch = add(new BooleanSetting("AutoSwitch", false, () -> this.page.is(Page.Bow)).setParent());
    private final BindSetting eventKey = add(new BindSetting("EventKey", -1, () -> this.autoSwitch.isOpen() && this.page.is(Page.Bow)));
    private final SliderSetting switchTime = add(new SliderSetting("SwitchTime", 0.11f, 0.0f, 1.0f, 0.01f, () -> this.autoSwitch.isOpen() && this.page.is(Page.Bow)));
    private final BooleanSetting instantRotate = add(new BooleanSetting("InstantRotate", true, () -> this.autoSwitch.isOpen() && this.page.is(Page.Bow)));
    private final BooleanSetting onlyPress = add(new BooleanSetting("OnlyPress", false, () -> this.autoSwitch.isOpen() && this.page.is(Page.Bow)));

    private final BooleanSetting farBow = add(new BooleanSetting("FarBow", false, () -> this.page.is(Page.Bow)).setParent());
    private final SliderSetting maxRange = add(new SliderSetting("MaxRange", 20.0, 5.0, 300.0, 0.1, () -> this.farBow.isOpen() && this.page.is(Page.Bow)).setSuffix("m"));

    // ===== Rotation 页面 =====
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true, () -> this.page.is(Page.Rotation)).setParent());
    private final BooleanSetting smoothRotation = add(new BooleanSetting("SmoothRotation", false, () -> this.rotate.isOpen() && this.page.is(Page.Rotation)));
    private final SliderSetting rotationSpeed = add(new SliderSetting("RotationSpeed", 0.3f, 0.1f, 1.0f, 0.01f, () -> this.rotate.isOpen() && this.page.is(Page.Rotation)));
    private final SliderSetting rotationPriority = add(new SliderSetting("RotationPriority", 10.0f, 0.0f, 100.0f, 1.0f, () -> this.rotate.isOpen() && this.page.is(Page.Rotation)));

    // ===== 状态变量 =====
    private final Timer targetUpdateTimer = new Timer();
    private Entity currentTarget = null;
    private boolean wasAiming = false;
    private Vec3d lastTargetPos = null;
    private Vec3d lastTargetVelocity = null;

    // AutoSwitch 状态变量
    private boolean switching = false;
    private int startSlot = 0;
    private boolean eventKeyPressed = false;
    private boolean bowActive = false;

    // 旋转相关
    private Vec3d directionVec = null;

    public AutoAim() {
        super("AutoAim", Category.Combat);
        setChinese("弓自瞄");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentTarget = null;
        wasAiming = false;
        lastTargetPos = null;
        lastTargetVelocity = null;
        directionVec = null;
        targetUpdateTimer.reset();

        switching = false;
        eventKeyPressed = false;
        bowActive = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        wasAiming = false;
        lastTargetPos = null;
        lastTargetVelocity = null;
        directionVec = null;

        if (switching) {
            InventoryUtil.switchToSlot(startSlot);
        }
        switching = false;
        eventKeyPressed = false;
        bowActive = false;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (!enabled.getValue() || mc.player == null || mc.world == null) {
            return;
        }

        handleAutoSwitch();

        if (shouldUpdateTarget()) {
            updateTarget();
        }
    }

    private void handleAutoSwitch() {
        if (!autoSwitch.getValue()) {
            if (switching) {
                InventoryUtil.switchToSlot(startSlot);
                switching = false;
                eventKeyPressed = false;
            }
            return;
        }

        if (eventKey.isPressed()) {
            if (!eventKeyPressed && !switching) {
                int bowSlot = findBowSlot();
                if (bowSlot != -1) {
                    startSlot = mc.player.getInventory().selectedSlot;
                    InventoryUtil.switchToSlot(bowSlot);
                    setUseKeyPressed(true);
                    switching = true;
                    eventKeyPressed = true;
                }
            }
        } else {
            eventKeyPressed = false;
        }

        if (switching) {
            boolean shouldStop = (!mc.options.useKey.isPressed() && !onlyPress.getValue()) ||
                    (mc.player.isUsingItem() && !isActiveItemBow());

            if (shouldStop) {
                InventoryUtil.switchToSlot(startSlot);
                setUseKeyPressed(false);
                switching = false;
                bowActive = false;
                return;
            }

            bowActive = isActiveItemBow();

            if (bowActive && (!onlyPress.getValue() || switching)) {
                float pullProgress = getBowPullProgress();
                if (pullProgress >= switchTime.getValue()) {
                    setUseKeyPressed(false);
                    mc.interactionManager.stopUsingItem(mc.player);
                    InventoryUtil.switchToSlot(startSlot);
                    switching = false;
                    bowActive = false;
                }
            }
        }
    }

    private float getBowPullProgress() {
        if (mc.player.isUsingItem() && isActiveItemBow()) {
            return BowItem.getPullProgress(mc.player.getItemUseTime());
        }
        return 0.0f;
    }

    private boolean isActiveItemBow() {
        return mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem;
    }

    private int findBowSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BowItem) {
                return i;
            }
        }
        return -1;
    }

    private void setUseKeyPressed(boolean pressed) {
        if (mc.options == null) return;
        mc.options.useKey.setPressed(pressed);
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (!enabled.getValue() || !rotate.getValue() || mc.player == null || mc.world == null) {
            wasAiming = false;
            directionVec = null;
            return;
        }

        boolean weaponReady;
        double targetDistance = 0.0;
        boolean farBowMode = false;
        if (currentTarget != null) {
            targetDistance = mc.player.distanceTo(currentTarget);
            farBowMode = farBow.getValue() && maxRange.getValue() > range.getValue()
                    && targetDistance > range.getValue()
                    && targetDistance <= maxRange.getValue();
        }
        if (farBowMode) {
            weaponReady = isBowWeapon() && isFullBowDrawn();
        } else {
            weaponReady = isWeaponReady();
        }

        if (!weaponReady) {
            wasAiming = false;
            directionVec = null;
            if (currentTarget != null) {
                currentTarget = null;
                lastTargetPos = null;
                lastTargetVelocity = null;
            }
            return;
        }

        if (currentTarget != null && currentTarget.isAlive()) {
            Vec3d targetPos = calculatePredictedPosition();
            float[] rotations = calculateRotations(targetPos);

            if (rotations != null) {
                if (isBowWeapon() && (ballisticCompensation.getValue() || farBowMode)) {
                    rotations = applyBallisticCompensation(rotations, targetPos);
                }

                rotations[0] += (float) horizontalOffset.getValue();
                rotations[1] += (float) verticalOffset.getValue();

                if (smoothRotation.getValue()) {
                    directionVec = getVectorForRotation(rotations[1], rotations[0]);
                    event.setTarget(directionVec, rotationSpeed.getValueFloat(), rotationPriority.getValueFloat());
                } else {
                    event.setRotation(rotations[0], rotations[1], rotationSpeed.getValueFloat(), rotationPriority.getValueFloat());
                }

                if (forceClientView.getValue()) {
                    forceClientRotation(rotations[0], rotations[1]);
                }

                wasAiming = true;
            } else {
                wasAiming = false;
                directionVec = null;
            }
        } else {
            wasAiming = false;
            directionVec = null;
        }

        if (autoSwitch.getValue() && instantRotate.getValue() && bowActive) {
            event.setRotation(mc.player.getYaw(), -90.0f, rotationSpeed.getValueFloat(), rotationPriority.getValueFloat() + 5);
        }
    }

    private Vec3d getVectorForRotation(float pitch, float yaw) {
        float pitchRad = pitch * 0.017453292F;
        float yawRad = -yaw * 0.017453292F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private Vec3d calculatePredictedPosition() {
        if (currentTarget == null || !currentTarget.isAlive()) return null;
        Vec3d basePos = currentTarget.getEyePos();
        if (!enablePrediction.getValue() || !(currentTarget instanceof PlayerEntity)) {
            return basePos;
        }
        Vec3d currentVelocity = calculateTargetVelocity();
        if (currentVelocity == null || currentVelocity.lengthSquared() < 0.001) {
            return basePos;
        }
        double predictTicks = predictionTicks.getValue();
        double multiplier = predictionMultiplier.getValue();
        return basePos.add(
                currentVelocity.x * predictTicks * multiplier,
                currentVelocity.y * predictTicks * multiplier,
                currentVelocity.z * predictTicks * multiplier
        );
    }

    private Vec3d calculateTargetVelocity() {
        if (currentTarget == null) return null;
        if (lastTargetPos == null) {
            lastTargetPos = currentTarget.getEyePos();
            lastTargetVelocity = Vec3d.ZERO;
            return Vec3d.ZERO;
        }
        Vec3d currentPos = currentTarget.getEyePos();
        Vec3d velocity = currentPos.subtract(lastTargetPos);
        lastTargetPos = currentPos;
        lastTargetVelocity = velocity;
        return velocity;
    }

    private float[] applyBallisticCompensation(float[] rotations, Vec3d targetPos) {
        if (!isBowWeapon() || targetPos == null) return rotations;
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(targetPos);
        double compensationAngle = distance * 0.015 * ballisticFactor.getValue();
        rotations[1] -= (float) compensationAngle;
        rotations[1] = MathHelper.clamp(rotations[1], -90.0f, 90.0f);
        return rotations;
    }

    private boolean isBowWeapon() {
        return mc.player.getActiveItem().getItem() instanceof BowItem;
    }

    private void forceClientRotation(float yaw, float pitch) {
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private float[] calculateRotations(Vec3d targetPos) {
        if (targetPos == null) return null;
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = targetPos.x - eyePos.x;
        double diffY = targetPos.y - eyePos.y;
        double diffZ = targetPos.z - eyePos.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        return new float[]{yaw, pitch};
    }

    private boolean isWeaponReady() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        if (mainHand.getItem() instanceof BowItem) {
            if (!mc.player.isUsingItem()) return false;
            return mc.player.getItemUseTime() > 3;
        }
        if (offHand.getItem() instanceof BowItem) {
            if (!mc.player.isUsingItem()) return false;
            return mc.player.getItemUseTime() > 3;
        }
        return false;
    }

    private boolean isFullBowDrawn() {
        if (!mc.player.isUsingItem()) return false;
        float progress = BowItem.getPullProgress(mc.player.getItemUseTime());
        return progress >= 1.0f;
    }

    private boolean shouldUpdateTarget() {
        return currentTarget == null ||
                !currentTarget.isAlive() ||
                targetUpdateTimer.passedMs(300) ||
                (currentTarget != null && mc.player.distanceTo(currentTarget) > range.getValue());
    }

    private void updateTarget() {
        List<PlayerEntity> players = getNearbyPlayers();
        if (players.isEmpty()) {
            currentTarget = null;
            lastTargetPos = null;
            lastTargetVelocity = null;
            return;
        }
        for (PlayerEntity player : players) {
            if (isValidTarget(player)) {
                currentTarget = player;
                lastTargetPos = player.getEyePos();
                lastTargetVelocity = Vec3d.ZERO;
                targetUpdateTimer.reset();
                return;
            }
        }
        currentTarget = null;
        lastTargetPos = null;
        lastTargetVelocity = null;
    }

    private List<PlayerEntity> getNearbyPlayers() {
        double searchRange = (farBow.getValue() && maxRange.getValue() > range.getValue()) ? maxRange.getValue() : range.getValue();
        Box searchBox = new Box(
                mc.player.getX() - searchRange,
                mc.player.getY() - searchRange,
                mc.player.getZ() - searchRange,
                mc.player.getX() + searchRange,
                mc.player.getY() + searchRange,
                mc.player.getZ() + searchRange
        );
        return mc.world.getPlayers()
                .stream()
                .filter(p -> p != mc.player)
                .filter(p -> p.getBoundingBox().intersects(searchBox))
                .sorted(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
                .collect(Collectors.toList());
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == null || !player.isAlive()) return false;
        double distance = mc.player.distanceTo(player);
        if (farBow.getValue() && maxRange.getValue() > range.getValue()) {
            if (distance <= range.getValue()) return false;
            if (distance > maxRange.getValue()) return false;
        } else {
            if (distance > range.getValue()) return false;
        }
        if (ignoreFriends.getValue() && isFriend(player)) {
            return false;
        }
        if (wallCheck.getValue()) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d targetPos = player.getEyePos();
            HitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    eyePos,
                    targetPos,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    mc.player
            ));
            if (hit.getType() == HitResult.Type.BLOCK) {
                double wallDistance = eyePos.distanceTo(hit.getPos());
                double entityDistance = eyePos.distanceTo(targetPos);
                return wallDistance > entityDistance - wallCheckRange.getValue();
            }
        }
        return true;
    }

    private boolean isFriend(PlayerEntity player) {
        if (Alien.FRIEND != null) {
            try {
                return Alien.FRIEND.isFriend(player);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onRender3D(net.minecraft.client.util.math.MatrixStack matrixStack) {
        if (!showLine.getValue() || currentTarget == null || mc.player == null) return;
        if (currentTarget.isAlive()) {
            Vec3d start = mc.player.getEyePos();
            Vec3d end;
            if (enablePrediction.getValue() && currentTarget instanceof PlayerEntity) {
                end = calculatePredictedPosition();
                if (end == null) end = currentTarget.getEyePos();
            } else {
                end = currentTarget.getEyePos();
            }
            Color lineColorToUse = lineColor.getValue();
            if (friendLineColor.getValue() && currentTarget instanceof PlayerEntity) {
                if (isFriend((PlayerEntity) currentTarget)) {
                    lineColorToUse = friendLineColorValue.getValue();
                }
            }
            dev.luminous.api.utils.render.Render3DUtil.drawLine(start, end, lineColorToUse);
        }
    }

    @Override
    public String getInfo() {
        if (currentTarget != null && currentTarget.isAlive()) {
            double distance = mc.player.distanceTo(currentTarget);
            String friendIcon = "";
            if (currentTarget instanceof PlayerEntity) {
                if (isFriend((PlayerEntity) currentTarget)) {
                    friendIcon = "👫";
                }
            }
            String predictionIcon = enablePrediction.getValue() ? "⚡" : "";
            return String.format("%s%s%.1fm", friendIcon, predictionIcon, distance);
        }
        if (autoSwitch.getValue() && switching) {
            return "QuickShot";
        }
        return null;
    }
}