package dev.luminous.api.utils.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;

public final class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static HitResult rayCast(final Rotation rotation, final double range) {
        return rayCast(rotation, range, 0);
    }

    public static boolean inView(final Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        double dist = mc.player.distanceTo(entity);
        double range = Math.min(256.0D, dist + 2.0D);

        Rotation rotations = RotationUtils.calculate(entity);

        if (dist > 100 || !(entity instanceof PlayerEntity)) {
            HitResult result = RayCastUtil.rayCast(rotations, range, 0.2f);
            return result != null && result.getType() == HitResult.Type.ENTITY;
        } else {
            Box bb = entity.getBoundingBox();
            double width = bb.getLengthX();
            double height = bb.getLengthY();
            double depth = bb.getLengthZ();
            Vec3d basePos = entity.getPos();

            for (double yPercent = 1; yPercent >= -1; yPercent -= 0.5) {
                for (double xPercent = 1; xPercent >= -1; xPercent -= 1.0) {
                    for (double zPercent = 1; zPercent >= -1; zPercent -= 1.0) {

                        Vec3d targetPoint = basePos.add(width * xPercent, height * yPercent, depth * zPercent);
                        Rotation subRotations = RotationUtils.calculate(targetPoint);

                        HitResult result = RayCastUtil.rayCast(subRotations, range, 0.2f);
                        if (result != null && result.getType() == HitResult.Type.ENTITY) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static HitResult rayCast(final Rotation rotation, final double range, final float expand) {
        return rayCast(rotation, range, expand, mc.player);
    }

    public static HitResult rayCast(final Rotation rotation, final double range, final float expand, Entity entity) {
        if (mc.world == null || entity == null) return null;

        Vec3d eyePos = entity.getEyePos();
        Vec3d lookVec = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        Vec3d endVec = eyePos.add(lookVec.multiply(range));

        HitResult objectMouseOver = mc.world.raycast(new RaycastContext(
                eyePos,
                endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity
        ));

        double distToBlock = range;
        if (objectMouseOver != null && objectMouseOver.getType() != HitResult.Type.MISS) {
            distToBlock = objectMouseOver.getPos().distanceTo(eyePos);
        }

        Vec3d entitySearchEndVec = endVec;

        Entity pointedEntity = null;
        Vec3d hitVec = null;
        double currentDist = distToBlock;

        Box searchBox = entity.getBoundingBox().stretch(lookVec.multiply(range)).expand(1.0D);

        List<Entity> list = mc.world.getOtherEntities(entity, searchBox, e -> !e.isSpectator() && e.canHit());

        for (Entity candidate : list) {
            double collisionSize = candidate.getTargetingMargin() + expand;
            Box entityBox = candidate.getBoundingBox().expand(collisionSize);

            Optional<Vec3d> intercept = entityBox.raycast(eyePos, entitySearchEndVec);

            if (entityBox.contains(eyePos)) {
                if (currentDist >= 0.0D) {
                    pointedEntity = candidate;
                    hitVec = intercept.orElse(eyePos);
                    currentDist = 0.0D;
                }
            } else if (intercept.isPresent()) {
                Vec3d interceptVec = intercept.get();
                double d3 = eyePos.distanceTo(interceptVec);

                if (d3 < currentDist || currentDist == 0.0D) {
                    if (candidate.getRootVehicle() != entity.getRootVehicle() || currentDist == 0.0D) {
                        pointedEntity = candidate;
                        hitVec = interceptVec;
                        currentDist = d3;
                    }
                }
            }
        }

        if (pointedEntity != null && (currentDist < distToBlock || objectMouseOver == null || objectMouseOver.getType() == HitResult.Type.MISS)) {
            return new EntityHitResult(pointedEntity, hitVec);
        }

        return objectMouseOver;
    }

    public static boolean overBlock(final Rotation rotation, final Direction enumFacing, final BlockPos pos, final boolean strict) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d lookVec = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());

        Vec3d eyePos = mc.player.getEyePos();
        double reach = 4.5D;
        Vec3d endVec = eyePos.add(lookVec.multiply(reach));

        BlockHitResult result = mc.world.raycast(new RaycastContext(
                eyePos,
                endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (result.getType() == HitResult.Type.MISS) return false;

        return result.getBlockPos().equals(pos)
                && (!strict || result.getSide() == enumFacing);
    }

    public static boolean overBlock(final Direction enumFacing, final BlockPos pos, final boolean strict) {
        return overBlock(RotationManager.getRotation(), enumFacing, pos, strict);
    }

    public static Boolean overBlock(final Rotation rotation, final BlockPos pos) {
        return overBlock(rotation, Direction.UP, pos, false);
    }

    public static Boolean overBlock(final Rotation rotation, final BlockPos pos, final Direction enumFacing) {
        return overBlock(rotation, enumFacing, pos, true);
    }


}
