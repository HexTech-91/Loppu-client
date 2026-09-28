package dev.luminous.api.utils.combat;

import dev.luminous.api.utils.Wrapper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class LoggedOutEnemyManager implements Wrapper {
    private static final long LOGOUT_EXPIRE_TIME = 30000L;
    private final Map<UUID, LoggedOutEnemy> loggedOutEnemies = new HashMap<>();
    private final List<UUID> previousOnlinePlayers = new ArrayList<>();

    private static class LoggedOutEnemy {
        final Vec3d lastPosition;
        final long logoutTime;

        LoggedOutEnemy(Vec3d lastPosition, long logoutTime) {
            this.lastPosition = lastPosition;
            this.logoutTime = logoutTime;
        }
    }

    public void updatePlayerList() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        List<AbstractClientPlayerEntity> currentPlayers = new ArrayList<>(mc.world.getPlayers());
        List<UUID> currentUuids = new ArrayList<>();

        for (AbstractClientPlayerEntity player : currentPlayers) {
            currentUuids.add(player.getUuid());
        }

        for (UUID uuid : previousOnlinePlayers) {
            if (!currentUuids.contains(uuid)) {
                LoggedOutEnemy existingEnemy = loggedOutEnemies.get(uuid);
                Vec3d position = null;

                if (existingEnemy != null) {
                    position = existingEnemy.lastPosition;
                } else {
                    continue;
                }

                if (position != null) {
                    loggedOutEnemies.put(uuid, new LoggedOutEnemy(position, System.currentTimeMillis()));
                }
            }
        }

        previousOnlinePlayers.clear();
        previousOnlinePlayers.addAll(currentUuids);

        for (UUID uuid : new ArrayList<>(loggedOutEnemies.keySet())) {
            if (currentUuids.contains(uuid)) {
                loggedOutEnemies.remove(uuid);
            }
        }

        long currentTime = System.currentTimeMillis();
        loggedOutEnemies.entrySet().removeIf(entry ->
                currentTime - entry.getValue().logoutTime > LOGOUT_EXPIRE_TIME);
    }

    public List<PlayerEntity> getLoggedOutEnemies(double range) {
        List<PlayerEntity> enemies = new ArrayList<>();
        Vec3d playerPos = mc.player.getPos();

        for (LoggedOutEnemy enemy : loggedOutEnemies.values()) {
            if (playerPos.distanceTo(enemy.lastPosition) <= range) {
                enemies.add(new DummyPlayerEntity(enemy.lastPosition));
            }
        }

        return enemies;
    }

    public boolean isLoggedOut(PlayerEntity player) {
        if (player == null) return false;

        if (player instanceof DummyPlayerEntity) {
            return true;
        }

        return loggedOutEnemies.containsKey(player.getUuid());
    }

    public PlayerEntity getClosestLoggedOutEnemy(double range) {
        PlayerEntity closest = null;
        Vec3d playerPos = mc.player.getPos();

        for (LoggedOutEnemy enemy : loggedOutEnemies.values()) {
            if (playerPos.distanceTo(enemy.lastPosition) <= range) {
                PlayerEntity dummyPlayer = new DummyPlayerEntity(enemy.lastPosition);
                if (closest == null || playerPos.distanceTo(dummyPlayer.getPos()) < playerPos.distanceTo(closest.getPos())) {
                    closest = dummyPlayer;
                }
            }
        }

        return closest;
    }

    public void recordLoggedOutPlayer(UUID uuid, Vec3d position) {
        if (uuid == null || position == null) {
            return;
        }
        loggedOutEnemies.put(uuid, new LoggedOutEnemy(position, System.currentTimeMillis()));
    }

    private static class DummyPlayerEntity extends PlayerEntity {
        public DummyPlayerEntity(Vec3d position) {
            super(mc.world, new net.minecraft.util.math.BlockPos((int)position.x, (int)position.y, (int)position.z),
                    0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DummyPlayer"));
            this.setPosition(position.x, position.y, position.z);
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }
    }
}
