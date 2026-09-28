package dev.luminous.mod.modules.impl.nagisa;

import net.minecraft.client.MinecraftClient;

public class LockCamera {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean locked = false;
    private static double lockX, lockY, lockZ;

    public static void lock(double x, double y, double z) {
        lockX = x;
        lockY = y;
        lockZ = z;
        locked = true;
    }

    public static void unlock() {
        locked = false;
    }

    public static boolean isLocked() {
        return locked;
    }

    public static void onTick() {
        if (!locked || mc.player == null) return;
        mc.player.setPosition(lockX, lockY, lockZ);
        mc.player.setVelocity(0, 0, 0);
    }
}