package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;

public class PearlFollow extends Module {
    public static PearlFollow INSTANCE;
    private final SliderSetting pauseHealth;
    private final SliderSetting range;
    private final SliderSetting delay;
    private final Timer throwTimer;
    private Vec3d pendingLanding;
    private final Set<Integer> trackedPearls;

    public PearlFollow() {
        super("PearlFollow", Category.Movement);
        this.pauseHealth = add(new SliderSetting("PauseHealth", 10.0, 0.0, 36.0, 0.1).setSuffix("hp"));
        this.range = add(new SliderSetting("Range", 50.0, 0.0, 300.0, 0.1).setSuffix("m"));
        this.delay = add(new SliderSetting("Delay", 200, 0, 5000).setSuffix("ms"));
        this.throwTimer = new Timer();
        this.pendingLanding = null;
        this.trackedPearls = new HashSet<>();
        setChinese("珍珠跟随");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.pendingLanding = null;
        this.trackedPearls.clear();
    }

    @Override
    public void onDisable() {
        this.pendingLanding = null;
        this.trackedPearls.clear();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || mc.player.getHealth() <= this.pauseHealth.getValueFloat() || !this.throwTimer.passedMs(this.delay.getValueInt())) {
            return;
        }
        Vec3d playerPos = mc.player.getPos();
        double range = this.range.getValue();
        List<EnderPearlEntity> pearls = mc.world.getEntitiesByClass(EnderPearlEntity.class,
                new Box(playerPos.x - range, playerPos.y - range, playerPos.z - range,
                        playerPos.x + range, playerPos.y + range, playerPos.z + range),
                e -> e != null && e.isAlive() && !e.getVelocity().equals(Vec3d.ZERO));

        if (!pearls.isEmpty()) {
            for (EnderPearlEntity pearl : pearls) {
                if (!this.trackedPearls.contains(pearl.getId())) {
                    Entity owner = pearl.getOwner();
                    if (owner instanceof PlayerEntity && owner != mc.player
                            && !Alien.FRIEND.isFriend((PlayerEntity) owner)) {
                        Vec3d landing = predictPearlLanding(pearl);
                        if (landing != null && mc.player.squaredDistanceTo(landing) >= 1.0) {
                            this.pendingLanding = landing;
                            this.throwTimer.reset();
                            this.trackedPearls.add(pearl.getId());
                            break;
                        }
                    }
                }
            }
        }

        throwAtPending();

        this.trackedPearls.removeIf(id -> {
            Entity e = mc.world.getEntityById(id);
            return e == null || !e.isAlive();
        });
    }

    private void throwAtPending() {
        if (nullCheck() || this.pendingLanding == null) return;
        Vec3d landing = this.pendingLanding;
        this.pendingLanding = null;
        if (mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL
                && mc.player.getOffHandStack().getItem() != Items.ENDER_PEARL
                && InventoryUtil.findItemInventorySlot(Items.ENDER_PEARL) == -1) {
            return;
        }
        throwAt(landing);
    }

    private Vec3d predictPearlLanding(EnderPearlEntity pearl) {
        Vec3d v = pearl.getVelocity();
        if (v == null) return null;
        double mx = v.x, my = v.y, mz = v.z;
        if (mx == 0.0 && my == 0.0 && mz == 0.0) return null;
        double x = pearl.getX(), y = pearl.getY(), z = pearl.getZ();
        for (int i = 0; i < 600; i++) { // 改为 600 步
            mx *= 0.99;
            mz *= 0.99;
            my = my * 0.99 - 0.03;
            Vec3d last = new Vec3d(x, y, z);
            x += mx;
            y += my;
            z += mz;
            Vec3d pos = new Vec3d(x, y, z);
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(last, pos,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, pearl));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                return bhr.getPos();
            }
            if (y <= mc.world.getBottomY()) {
                return pos;
            }
        }
        return new Vec3d(x, y, z);
    }

    private void throwAt(Vec3d landing) {
        Vec3d eye = mc.player.getEyePos();
        double dx = landing.x - eye.x;
        double dz = landing.z - eye.z;
        float baseYaw = (float) (MathHelper.atan2(-dx, dz) * 180.0 / Math.PI);
        float[] straight = solveStraightPitch(eye, baseYaw, landing);
        float yaw, pitch;
        if (straight[1] <= 16.0) {
            yaw = baseYaw;
            pitch = straight[0];
        } else {
            float[] result = solveAngles(eye, baseYaw, landing);
            if (result[2] > 10000.0) return;
            yaw = result[0];
            pitch = result[1];
        }
        ThrowResult res = simulateThrow(eye, yaw, pitch);
        // 硬编码最大误差为 4.0 格（距离平方 > 16.0 则放弃）
        if (res.landing.squaredDistanceTo(landing) > 16.0) return;
        throwPearl(yaw, pitch);
    }

    private float[] solveStraightPitch(Vec3d eye, float yaw, Vec3d landing) {
        double bestPitch = 20.0;
        double bestErr = Double.MAX_VALUE;
        for (float p = -20.0f; p <= 90.0f; p += 2.0f) {
            ThrowResult res = simulateThrow(eye, yaw, p);
            double err = res.landing.squaredDistanceTo(landing);
            if (err < bestErr) {
                bestErr = err;
                bestPitch = p;
            }
        }
        float lo = (float) Math.max(-20.0, bestPitch - 3.0);
        float hi = (float) Math.min(90.0, bestPitch + 3.0);
        for (float p = lo; p <= hi; p += 0.1f) {
            ThrowResult res = simulateThrow(eye, yaw, p);
            double err = res.landing.squaredDistanceTo(landing);
            if (err < bestErr) {
                bestErr = err;
                bestPitch = p;
            }
        }
        return new float[]{(float) bestPitch, (float) bestErr};
    }

    private float[] solveAngles(Vec3d eye, float baseYaw, Vec3d landing) {
        double bestYaw = baseYaw;
        double bestPitch = 20.0;
        double bestErr = Double.MAX_VALUE;
        for (float y = baseYaw - 25.0f; y <= baseYaw + 25.0f; y += 2.0f) {
            for (float p = -20.0f; p <= 90.0f; p += 2.0f) {
                ThrowResult res = simulateThrow(eye, y, p);
                double err = res.landing.squaredDistanceTo(landing);
                if (err < bestErr) {
                    bestYaw = y;
                    bestPitch = p;
                    bestErr = err;
                }
            }
        }
        float yLo = (float) Math.max(baseYaw - 25.0, bestYaw - 3.0);
        float yHi = (float) Math.min(baseYaw + 25.0, bestYaw + 3.0);
        float pLo = (float) Math.max(-20.0, bestPitch - 3.0);
        float pHi = (float) Math.min(90.0, bestPitch + 3.0);
        for (float y = yLo; y <= yHi; y += 0.5f) {
            for (float p = pLo; p <= pHi; p += 0.5f) {
                ThrowResult res = simulateThrow(eye, y, p);
                double err = res.landing.squaredDistanceTo(landing);
                if (err < bestErr) {
                    bestYaw = y;
                    bestPitch = p;
                    bestErr = err;
                }
            }
        }
        return new float[]{(float) bestYaw, (float) bestPitch, (float) bestErr};
    }

    private ThrowResult simulateThrow(Vec3d start, float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double mx = -Math.sin(yawRad) * Math.cos(pitchRad) * 1.5;
        double my = -Math.sin(pitchRad) * 1.5;
        double mz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.5;
        double x = start.x, y = start.y, z = start.z;
        for (int i = 0; i < 600; i++) { // 改为 600 步
            mx *= 0.99;
            mz *= 0.99;
            my = my * 0.99 - 0.03;
            Vec3d last = new Vec3d(x, y, z);
            x += mx;
            y += my;
            z += mz;
            Vec3d pos = new Vec3d(x, y, z);
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(last, pos,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                return new ThrowResult(bhr.getPos(), true);
            }
            if (y <= mc.world.getBottomY()) {
                return new ThrowResult(pos, false);
            }
        }
        return new ThrowResult(new Vec3d(x, y, z), false);
    }

    private static final class ThrowResult {
        final Vec3d landing;
        final boolean blocked;
        ThrowResult(Vec3d landing, boolean blocked) {
            this.landing = landing;
            this.blocked = blocked;
        }
    }

    private void throwPearl(float yaw, float pitch) {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
            Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            return;
        }
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) {
            Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, yaw, pitch));
            return;
        }
        int pearl = InventoryUtil.findItemInventorySlot(Items.ENDER_PEARL);
        if (pearl != -1 && EntityUtil.inInventory()) {
            InventoryUtil.inventorySwap(pearl, mc.player.getInventory().selectedSlot);
            Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.inventorySwap(pearl, mc.player.getInventory().selectedSlot);
            EntityUtil.syncInventory();
            return;
        }
        int pearl2 = InventoryUtil.findItem(Items.ENDER_PEARL);
        if (pearl2 != -1) {
            int old = mc.player.getInventory().selectedSlot;
            InventoryUtil.switchToSlot(pearl2);
            Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.switchToSlot(old);
        }
    }
}