package dev.luminous.api.utils.rotation;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.SendMovementPacketsEvent;
import dev.luminous.api.utils.move.MoveUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.function.Function;

public class RotationManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Rotation offset = new Rotation(0, 0);
    public static Rotation rotations, lastRotations = new Rotation(0, 0), targetRotations, animationRotation, lastAnimationRotation;
    private static boolean active, smoothed;
    private static double rotationSpeed;
    private static Function<Rotation, Boolean> raycast;
    private static float randomAngle;
    private static boolean subscribed;

    public static void init() {
        if (subscribed) {
            return;
        }
        Alien.EVENT_BUS.subscribe(new RotationManager());
        subscribed = true;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        RotationManager.active = active;
    }

    public static boolean isSmoothed() {
        return smoothed;
    }

    public static void setRotations(final Rotation rotations, final double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null);
    }

    public static void setRotations(final Rotation rotations, final double rotationSpeed, final Function<Rotation, Boolean> raycast) {
        RotationManager.targetRotations = rotations;
        RotationManager.rotationSpeed = rotationSpeed;
        RotationManager.raycast = raycast;
        active = true;
        smoothed = false;
        smooth();
    }

    public static void smooth() {
        if (mc.player == null) {
            return;
        }
        if (!smoothed) {
            float targetYaw = targetRotations.getYaw();
            float targetPitch = targetRotations.getPitch();

            if (raycast != null && (Math.abs(targetYaw - rotations.getYaw()) > 5 || Math.abs(targetPitch - rotations.getPitch()) > 5)) {
                final Rotation trueTargetRotations = new Rotation(targetRotations.getYaw(), targetRotations.getPitch());

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.age / 10 % 2 == 0 ? -1 : 1));

                offset.setYaw((float) (offset.getYaw() + -MathHelper.sin((float) Math.toRadians(randomAngle)) * speed));
                offset.setPitch((float) (offset.getPitch() + MathHelper.cos((float) Math.toRadians(randomAngle)) * speed));

                targetYaw += offset.getYaw();
                targetPitch += offset.getPitch();

                if (!raycast.apply(new Rotation(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.getYaw() - targetYaw, targetPitch - trueTargetRotations.getPitch())) - 180;

                    targetYaw -= offset.getYaw();
                    targetPitch -= offset.getPitch();

                    offset.setYaw((float) (offset.getYaw() + -MathHelper.sin((float) Math.toRadians(randomAngle)) * speed));
                    offset.setPitch((float) (offset.getPitch() + MathHelper.cos((float) Math.toRadians(randomAngle)) * speed));

                    targetYaw = targetYaw + offset.getYaw();
                    targetPitch = targetPitch + offset.getPitch();
                }

                if (!raycast.apply(new Rotation(targetYaw, targetPitch))) {
                    offset.setYaw(0);
                    offset.setPitch(0);

                    targetYaw = (float) (targetRotations.getYaw() + Math.random() * 2);
                    targetPitch = (float) (targetRotations.getPitch() + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Rotation(targetYaw, targetPitch),
                    rotationSpeed + Math.random());

        }

        smoothed = true;
    }

    public static Rotation getRotation() {
        if (mc.player == null) {
            return new Rotation(0.0f, 0.0f);
        }
        return active && rotations != null ? rotations : new Rotation(mc.player.getYaw(), mc.player.getPitch());
    }

    @EventListener
    public void onLogout(GameLeftEvent e) {
        rotations = null;
        lastRotations = null;
        targetRotations = null;
        animationRotation = null;
        lastAnimationRotation = null;
        active = false;
        smoothed = false;
    }

    @EventListener(priority=4)
    public void onClientTick(ClientTickEvent e) {
        if (!e.isPre()) {
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }
        if (active) {
            smooth();
        }
    }

    @EventListener(priority=4)
    public void onSendMovementPackets(SendMovementPacketsEvent e) {
        if (mc.player == null) {
            return;
        }

        if (active && rotations != null) {
            float yaw = rotations.getYaw();
            float pitch = rotations.getPitch();
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                e.setYaw(yaw);
                e.setPitch(pitch);
            }

            if (Math.abs((rotations.getYaw() - mc.player.getYaw()) % 360.0f) < 1.0f && Math.abs(rotations.getPitch() - mc.player.getPitch()) < 1.0f) {
                active = false;
                this.correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rotation(e.getYaw(), e.getPitch());
        targetRotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        smoothed = false;
    }

    @EventListener(priority=100)
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.player == null) {
            return;
        }
        if (active && rotations != null) {
            MoveUtils.fixMovement(rotations.getYaw());
        }
    }

    private void correctDisabledRotations() {
        final Rotation rotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        final Rotation fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));

        mc.player.setYaw(fixedRotations.getYaw());
        mc.player.setPitch(fixedRotations.getPitch());
    }
}
