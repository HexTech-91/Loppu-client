package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public class AutoVclip extends Module {
    public static AutoVclip INSTANCE;

    private final BooleanSetting movePause = this.add(new BooleanSetting("MovePause", true));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 1.0, 0.1, 5.0, 0.1).setSuffix("s"));
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Jump));
    private final BooleanSetting strict = this.add(new BooleanSetting("Strict", false));
    private final BooleanSetting safety = this.add(new BooleanSetting("Safety", false,
            () -> mode.getValue() == Mode.Teleport ||
                    mode.getValue() == Mode.Teleport2 ||
                    mode.getValue() == Mode.TeleportMax));
    private final SliderSetting maxCheckHeight = this.add(new SliderSetting("MaxCheckHeight", 6, 3, 10,
            () -> mode.getValue() == Mode.TeleportMax));
    private final SliderSetting blockThreshold = this.add(new SliderSetting("BlockThreshold", 2, 1, 5,
            () -> mode.getValue() == Mode.TeleportMax));
    private final BooleanSetting maxUp = this.add(new BooleanSetting("MaxUp", false,
            () -> mode.getValue() == Mode.EscapeTrap));

    private final Timer timer = new Timer();
    private Vec3d lastPos = Vec3d.ZERO;

    public AutoVclip() {
        super("AutoVclip", Category.Movement);
        this.setChinese("自动纵向穿墙");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        timer.reset();
        lastPos = mc.player != null ? mc.player.getPos() : Vec3d.ZERO;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (event.isPost() || nullCheck()) return;

        // MovePause：玩家移动时不检测目标方块
        if (movePause.getValue()) {
            Vec3d currentPos = mc.player.getPos();
            double speed = currentPos.squaredDistanceTo(lastPos);
            lastPos = currentPos;
            if (speed > 0.0001) {
                timer.reset();
                return;
            }
        }

        // Delay 控制检查间隔
        if (!timer.passed((long) (delay.getValue() * 1000))) {
            return;
        }
        timer.reset();

        // 检测目标方块：以玩家下半身为原点，目标位置 (0, 2)
        if (hasBlockAtTarget()) {
            executeVClip();
        }
    }

    private boolean hasBlockAtTarget() {
        double playerY = mc.player.getY();
        double[] xOffsets = new double[]{0, 0.3, -0.3};
        double[] zOffsets = new double[]{0, 0.3, -0.3};

        for (double xOff : xOffsets) {
            for (double zOff : zOffsets) {
                BlockPos targetPos = new BlockPos(
                    (int) Math.floor(mc.player.getX() + xOff),
                    (int) Math.floor(playerY + 2),
                    (int) Math.floor(mc.player.getZ() + zOff)
                );
                if (mc.world.getBlockState(targetPos).getBlock() != Blocks.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    private void executeVClip() {
        switch (mode.getValue()) {
            case Glitch:
                executeGlitch();
                break;
            case Teleport:
                if (!strict.getValue() || checkStrictCondition()) {
                    executeTeleport(3);
                }
                break;
            case Teleport2:
                if (!strict.getValue() || checkStrictCondition()) {
                    executeTeleport(4);
                }
                break;
            case Jump:
                executeJump();
                break;
            case TeleportMax:
                executeTeleportMax();
                break;
            case EscapeTrap:
                executeEscapeTrap();
                break;
        }
    }

    private boolean checkStrictCondition() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos headPos = playerPos.up(1);
        BlockPos headTopPos = playerPos.up(2);
        return !isPositionSafe(headPos) || !isPositionSafe(headTopPos);
    }

    private void executeTeleport(int distance) {
        if (safety.getValue() && !isTeleportSafe(distance)) return;
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
    }

    private void executeTeleportMax() {
        TeleportDecision decision = analyzeTeleportSituation();
        switch (decision.type) {
            case USE_TELEPORT:
                if (!safety.getValue() || isTeleportSafe(3)) {
                    executeTeleport(3);
                }
                break;
            case USE_TELEPORT2:
                if (!safety.getValue() || isTeleportSafe(4)) {
                    executeTeleport(4);
                }
                break;
            case ABORT_TRAP:
            case ABORT_UNSAFE:
                break;
        }
    }

    private TeleportDecision analyzeTeleportSituation() {
        if (mc.player == null || mc.world == null) {
            return new TeleportDecision(DecisionType.ABORT_UNSAFE, "玩家或世界为空");
        }
        BlockAnalysis analysis = analyzeBlocksAbove();
        if (analysis.consecutiveBlocks >= blockThreshold.getValueInt()) {
            return new TeleportDecision(DecisionType.ABORT_TRAP, "检测到多层方块陷阱");
        }
        boolean teleportSafe = !safety.getValue() || isTeleportSafe(3);
        boolean teleport2Safe = !safety.getValue() || isTeleportSafe(4);
        if (analysis.hasAntiStep) {
            if (teleport2Safe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT2, "检测到AntiStep方块");
            } else if (teleportSafe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT, "Teleport2不安全");
            } else {
                return new TeleportDecision(DecisionType.ABORT_UNSAFE, "两种模式都不安全");
            }
        } else {
            if (teleportSafe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT, "无AntiStep");
            } else if (teleport2Safe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT2, "Teleport不安全");
            } else {
                return new TeleportDecision(DecisionType.ABORT_UNSAFE, "两种模式都不安全");
            }
        }
    }

    private BlockAnalysis analyzeBlocksAbove() {
        int playerX = mc.player.getBlockX();
        int playerY = mc.player.getBlockY();
        int playerZ = mc.player.getBlockZ();
        int consecutiveBlocks = 0;
        boolean hasAntiStep = false;
        int maxHeight = maxCheckHeight.getValueInt();
        for (int offset = 1; offset <= maxHeight; offset++) {
            BlockPos pos = new BlockPos(playerX, playerY + offset, playerZ);
            BlockState state = mc.world.getBlockState(pos);
            if (!isPositionSafe(pos)) {
                consecutiveBlocks++;
                if (offset == 3) hasAntiStep = true;
            } else {
                consecutiveBlocks = 0;
            }
        }
        return new BlockAnalysis(consecutiveBlocks, hasAntiStep);
    }

    private boolean isTeleportSafe(int distance) {
        if (mc.player == null || mc.world == null) return false;
        int targetX = mc.player.getBlockX();
        int targetY = (int) Math.floor(mc.player.getY() + distance);
        int targetZ = mc.player.getBlockZ();
        BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
        return isPositionSafe(targetPos);
    }

    private boolean isPositionSafe(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(mc.world, pos) == null || state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void executeJump() {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false));
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
    }

    private void executeGlitch() {
        double posX = mc.player.getX();
        double posY = Math.round(mc.player.getY());
        double posZ = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY, posZ, onGround));
        double halfY = 0.005;
        posY -= halfY;
        mc.player.setPosition(posX, posY, posZ);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY, posZ, onGround));
        posY -= halfY * 300.0;
        mc.player.setPosition(posX, posY, posZ);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(posX, posY, posZ, onGround));
    }

    private void executeEscapeTrap() {
        if (mc.player == null || mc.world == null) return;
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos headPos = playerPos.up(2);
        ArrayList<BlockPos> candidatePositions = new ArrayList<>();
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (x == 0 && z == 0) continue;
                BlockPos checkPos = headPos.add(x, 0, z);
                if (hasBlock(checkPos)) candidatePositions.add(checkPos.up());
            }
        }
        if (hasBlock(headPos)) candidatePositions.add(headPos.up());
        for (BlockPos pos : candidatePositions) {
            if (hasBlock(pos) || hasBlock(pos.up()) || hasBlock(pos.up(2))) continue;
            teleport(pos);
            return;
        }
        if (maxUp.getValue()) {
            if (tryTeleportUp(4)) return;
        }
    }

    private boolean tryTeleportUp(int distance) {
        if (mc.player == null || mc.world == null) return false;
        if (strict.getValue() && !checkStrictCondition()) return false;
        if (safety.getValue() && !isTeleportSafe(distance)) return false;
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
        return true;
    }

    private boolean hasBlock(BlockPos pos) {
        return mc.world != null && !mc.world.isAir(pos);
    }

    private void teleport(BlockPos pos) {
        if (hasBlock(pos) || hasBlock(pos.up()) || hasBlock(pos.up(2))) return;
        double x = (double) pos.getX() + 0.5;
        double y = pos.getY();
        double z = (double) pos.getZ() + 0.5;
        mc.player.setPosition(x, y, z);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
    }

    public enum Mode {
        Glitch,
        Teleport,
        Teleport2,
        Jump,
        TeleportMax,
        EscapeTrap
    }

    private enum DecisionType {
        USE_TELEPORT,
        USE_TELEPORT2,
        ABORT_TRAP,
        ABORT_UNSAFE
    }

    private static class TeleportDecision {
        final DecisionType type;
        final String reason;
        TeleportDecision(DecisionType type, String reason) {
            this.type = type;
            this.reason = reason;
        }
    }

    private static class BlockAnalysis {
        final int consecutiveBlocks;
        final boolean hasAntiStep;
        BlockAnalysis(int consecutiveBlocks, boolean hasAntiStep) {
            this.consecutiveBlocks = consecutiveBlocks;
            this.hasAntiStep = hasAntiStep;
        }
    }
}