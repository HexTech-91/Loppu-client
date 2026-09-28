package dev.luminous.api.utils.rotation;

import dev.luminous.Alien;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();


    public static Rotation calculate(final Vec3d from, final Vec3d to) {
        final Vec3d diff = to.subtract(from);
        final double distance = Math.hypot(diff.x, diff.z);
        final float yaw = (float)(Math.atan2(diff.z, diff.x) * 57.2957763671875) - 90.0f;
        final float pitch = (float)(-(Math.atan2(diff.y, distance) * 57.2957763671875));
        return new Rotation(yaw, pitch);
    }

    public static Rotation calculate(final Entity entity) {
        if (mc.player == null) {
            return new Rotation(0.0f, 0.0f);
        }
        double eyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double clamped = Math.max(0.0, Math.min(mc.player.getY() - entity.getY() + eyeHeight, (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.9));
        return calculate(entity.getPos().add(0.0, clamped, 0.0));
    }

    public static Rotation calculate(final Entity entity, final boolean adaptive, final double range) {
        Rotation normalRotations = RotationUtils.calculate(entity);

        HitResult result = RayCastUtil.rayCast(normalRotations, range, 0.0f);

        if (!adaptive || (result != null && result.getType() == HitResult.Type.ENTITY)) {
            return normalRotations;
        }

        Box bb = entity.getBoundingBox();
        double minX = bb.minX;
        double maxX = bb.maxX;
        double minY = bb.minY;
        double maxY = bb.maxY;
        double minZ = bb.minZ;
        double maxZ = bb.maxZ;

        Vec3d basePos = entity.getPos();

        for (double yPercent = 1; yPercent >= 0; yPercent -= 0.25 + Math.random() * 0.1) {
            for (double xPercent = 1; xPercent >= -0.5; xPercent -= 0.5) {
                for (double zPercent = 1; zPercent >= -0.5; zPercent -= 0.5) {

                    double offsetX = (maxX - minX) * xPercent;
                    double offsetY = (maxY - minY) * yPercent;
                    double offsetZ = (maxZ - minZ) * zPercent;

                    Vec3d targetPoint = basePos.add(offsetX, offsetY, offsetZ);

                    Rotation adaptiveRotations = RotationUtils.calculate(targetPoint);

                    HitResult rayCastResult = RayCastUtil.rayCast(adaptiveRotations, range, 0.0f);

                    if (rayCastResult != null && rayCastResult.getType() == HitResult.Type.ENTITY) {
                        return adaptiveRotations;
                    }
                }
            }
        }

        return normalRotations;
    }


    public static Rotation calculate(final BlockPos to) {
        return calculate(mc.player.getEyePos(), new Vec3d(to.getX(), to.getY(), to.getZ()).add(0.5, 0.5, 0.5));
    }

    public static Rotation calculate(final Vec3d to) {
        return calculate(mc.player.getEyePos(), to);
    }

    public static Rotation calculate(final Vec3d position, final Direction enumFacing) {
        double x = position.x + 0.5D;
        double y = position.y + 0.5D;
        double z = position.z + 0.5D;

        x += (double) enumFacing.getVector().getX() * 0.5D;
        y += (double) enumFacing.getVector().getY() * 0.5D;
        z += (double) enumFacing.getVector().getZ() * 0.5D;
        return calculate(new Vec3d(x, y, z));
    }


    public static Rotation calculate(final BlockPos position, final Direction enumFacing) {
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;

        x += (double) enumFacing.getVector().getX() * 0.5D;
        y += (double) enumFacing.getVector().getY() * 0.5D;
        z += (double) enumFacing.getVector().getZ() * 0.5D;
        return calculate(new Vec3d(x, y, z));
    }

    public static Rotation applySensitivityPatch(final Rotation rotation) {
        Rotation previousRotation = RotationManager.lastRotations;
        if (previousRotation == null) {
            previousRotation = new Rotation(mc.player.prevYaw, mc.player.prevPitch);
        }
        return applySensitivityPatch(rotation, previousRotation);
    }

    public static Rotation applySensitivityPatch(final Rotation rotation, final Rotation previousRotation) {
        final float mouseSensitivity = (float)((Double)mc.options.getMouseSensitivity().getValue() * 0.6f + 0.2f);
        final double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        final float yaw = previousRotation.getYaw() + (float) (Math.round((rotation.getYaw() - previousRotation.getYaw()) / multiplier) * multiplier);
        final float pitch = previousRotation.getPitch() + (float) (Math.round((rotation.getPitch() - previousRotation.getPitch()) / multiplier) * multiplier);
        return new Rotation(yaw, MathHelper.clamp(pitch, -90, 90));
    }

    public static Rotation relateToPlayerRotation(final Rotation rotation) {
        final Rotation previousRotation = new Rotation(mc.player.prevYaw, mc.player.prevPitch);
        final float yaw = previousRotation.getYaw() + MathHelper.wrapDegrees(rotation.getYaw() - previousRotation.getYaw());
        final float pitch = MathHelper.clamp(rotation.getPitch(), -90, 90);
        return new Rotation(yaw, pitch);
    }

    public static Rotation resetRotation(final Rotation rotation) {
        if (rotation == null) {
            return null;
        }

        final float yaw = rotation.getYaw() + MathHelper.wrapDegrees(mc.player.getYaw() - rotation.getYaw());
        final float pitch = mc.player.getPitch();
        return new Rotation(yaw, pitch);
    }

    public static Rotation move(final Rotation targetRotation, final double speed) {
        return move(RotationManager.lastRotations, targetRotation, speed);
    }

    public static Rotation move(final Rotation lastRotation, final Rotation targetRotation, double speed) {
        if (speed != 0) {

            double deltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - lastRotation.getYaw());
            final double deltaPitch = (targetRotation.getPitch() - lastRotation.getPitch());

            final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            final double distributionYaw = Math.abs(deltaYaw / distance);
            final double distributionPitch = Math.abs(deltaPitch / distance);

            final double maxYaw = speed * distributionYaw;
            final double maxPitch = speed * distributionPitch;

            final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
            final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

            return new Rotation(moveYaw, movePitch);
        }

        return new Rotation(0, 0);
    }

    public static Rotation smooth(final Rotation targetRotation, final double speed) {
        return smooth(RotationManager.lastRotations, targetRotation, speed);
    }

    public static Rotation smooth(final Rotation lastRotation, final Rotation targetRotation, final double speed) {
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        final float lastYaw = lastRotation.getYaw();
        final float lastPitch = lastRotation.getPitch();

        if (speed != 0) {
            Rotation move = move(targetRotation, speed);

            yaw = lastYaw + move.getYaw();
            pitch = lastPitch + move.getPitch();

            for (int i = 1; i <= (int)((float) Alien.FPS.getFps() / 20.0f + (float)Math.random() * 10.0f); ++i) {

                if (Math.abs(move.getYaw()) + Math.abs(move.getPitch()) > 0.0001) {
                    yaw += (Math.random() - 0.5) / 1000;
                    pitch -= Math.random() / 200;
                }

                /*
                 * Fixing GCD
                 */
                final Rotation rotations = new Rotation(yaw, pitch);
                final Rotation fixedRotations = applySensitivityPatch(rotations, lastRotation);

                /*
                 * Setting rotations
                 */
                yaw = shortestYaw(lastYaw, fixedRotations.getYaw());
                pitch = Math.max(-90, Math.min(90, fixedRotations.getPitch()));
            }
        }

        return new Rotation(yaw, pitch);
    }

    public static double getDistance(final Entity entity) {
        Vec3d eyes = mc.player.getEyePos();
        Box aabb = entity.getBoundingBox();

        double x = MathHelper.clamp(eyes.x, aabb.minX, aabb.maxX);
        double y = MathHelper.clamp(eyes.y, aabb.minY, aabb.maxY);
        double z = MathHelper.clamp(eyes.z, aabb.minZ, aabb.maxZ);

        return Math.sqrt(eyes.squaredDistanceTo(x, y, z));
    }

    public static double getDistance(PlayerEntity player, final Entity entity) {
        Vec3d eyes = player.getEyePos();
        Box aabb = entity.getBoundingBox();

        double x = MathHelper.clamp(eyes.x, aabb.minX, aabb.maxX);
        double y = MathHelper.clamp(eyes.y, aabb.minY, aabb.maxY);
        double z = MathHelper.clamp(eyes.z, aabb.minZ, aabb.maxZ);

        return Math.sqrt(eyes.squaredDistanceTo(x, y, z));
    }

    public static float shortestYaw(float from, float to) {
        return from + MathHelper.wrapDegrees(to - from);
    }
}
