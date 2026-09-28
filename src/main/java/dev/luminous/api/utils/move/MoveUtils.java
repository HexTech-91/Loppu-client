package dev.luminous.api.utils.move;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class MoveUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static float direction(float forward, float strafe) {
        float direction = mc.player.getYaw();
        boolean isMovingForward = forward > 0.0F;
        boolean isMovingBack = forward < 0.0F;
        boolean isMovingRight = strafe > 0.0F;
        boolean isMovingLeft = strafe < 0.0F;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;
        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0F;
            }

            if (isMovingForward && isMovingLeft) {
                return direction + 45.0F;
            }

            if (isMovingForward && isMovingRight) {
                return direction - 45.0F;
            }

            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0F;
            }

            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0F;
            }

            if (isMovingBack && isMovingLeft) {
                return direction + 135.0F;
            }

            if (isMovingBack) {
                return direction - 135.0F;
            }
        }

        return direction;
    }

    public static void fixMovement(float yaw) {
        if (mc.player == null) {
            return;
        }

        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        int angleUnit = 45;
        float angleTolerance = 22.5F;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = MathHelper.wrapDegrees(direction(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);
        forward = 0.0F;
        strafe = 0.0F;
        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;
        mc.player.input.movementForward = forward;
        mc.player.input.movementSideways = strafe;
    }

    public static boolean isMoving() {
        if (mc.player == null) {
            return false;
        }
        return mc.player.input.movementForward != 0.0F
                || mc.player.input.movementSideways != 0.0F
                || mc.options.jumpKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed()
                || mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed();
    }
}
