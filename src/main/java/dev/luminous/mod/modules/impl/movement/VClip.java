package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.Module.Category;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;

public class VClip extends Module {
    private final EnumSetting<Mode> mode = this.add(new EnumSetting("Mode", Mode.Jump));

    // Strict严格模式
    private final BooleanSetting strict = this.add(new BooleanSetting("Strict", false));

    // 安全设置（适用于Teleport、Teleport2、TeleportMax模式）
    private final BooleanSetting safety = this.add(new BooleanSetting("Safety", false,
            () -> mode.getValue() == Mode.Teleport ||
                    mode.getValue() == Mode.Teleport2 ||
                    mode.getValue() == Mode.TeleportMax));

    // TeleportMax的最大检测高度
    private final SliderSetting maxCheckHeight = this.add(new SliderSetting("MaxCheckHeight", 6, 3, 10,
            () -> mode.getValue() == Mode.TeleportMax));

    // 多层方块检测阈值
    private final SliderSetting blockThreshold = this.add(new SliderSetting("BlockThreshold", 2, 1, 5,
            () -> mode.getValue() == Mode.TeleportMax));

    // MaxUp - EscapeTrap模式的增强选项，开启后若无法逃生则尝试向上传送4格
    private final BooleanSetting maxUp = this.add(new BooleanSetting("MaxUp", false,
            () -> mode.getValue() == Mode.EscapeTrap));

    public VClip() {
        super("VClip", Module.Category.Movement);
        this.setChinese("纵向穿墙");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // Strict模式检查 - 如果是EscapeTrap模式则跳过
        if (strict.getValue() && mode.getValue() != Mode.EscapeTrap) {
            if (!checkStrictCondition()) {
                // 条件不满足，直接关闭模块
                this.disable();
                return;
            }
        }

        // 原逻辑：执行一次后自动关闭
        this.disable();
        switch ((Mode)this.mode.getValue()) {
            case Glitch:
                executeGlitch();
                break;
            case Teleport:
                executeTeleport(3);
                break;
            case Teleport2:
                executeTeleport(4);
                break;
            case Jump:
                executeJump();
                break;
            case TeleportMax:
                executeTeleportMax();
                break;
            case EscapeTrap:
                executeEscapeTrap(); // 使用增强后的版本
                break;
        }
    }

    // Strict模式条件检查
    private boolean checkStrictCondition() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        // 获取玩家当前位置
        BlockPos playerPos = mc.player.getBlockPos();

        // 检查玩家头顶方块（y+1）
        BlockPos headPos = playerPos.up(1);
        boolean hasHeadBlock = !isPositionSafe(headPos);

        // 检查玩家头顶再上一格（y+2）
        BlockPos headTopPos = playerPos.up(2);
        boolean hasHeadTopBlock = !isPositionSafe(headTopPos);

        // 任意一个有方块即可满足条件
        return hasHeadBlock || hasHeadTopBlock;
    }

    // Teleport执行方法（带参数）
    private void executeTeleport(int distance) {
        // 安全检查
        if (safety.getValue() && !isTeleportSafe(distance)) {
            return;
        }

        // 执行传送
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
    }

    // TeleportMax执行方法
    private void executeTeleportMax() {
        // 智能检测并执行传送
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
                // 不执行传送
                return;
        }
    }

    // 智能分析传送情况
    private TeleportDecision analyzeTeleportSituation() {
        if (mc.player == null || mc.world == null) {
            return new TeleportDecision(DecisionType.ABORT_UNSAFE, "玩家或世界为空");
        }

        // 检测头顶方块情况
        BlockAnalysis analysis = analyzeBlocksAbove();

        // 如果检测到多层方块陷阱
        if (analysis.consecutiveBlocks >= blockThreshold.getValueInt()) {
            return new TeleportDecision(DecisionType.ABORT_TRAP,
                    "检测到多层方块陷阱 (" + analysis.consecutiveBlocks + "层连续方块)");
        }

        // 检查Teleport模式是否安全
        boolean teleportSafe = !safety.getValue() || isTeleportSafe(3);
        boolean teleport2Safe = !safety.getValue() || isTeleportSafe(4);

        // 智能决策逻辑
        if (analysis.hasAntiStep) {
            // 有AntiStep方块，优先使用Teleport2
            if (teleport2Safe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT2, "检测到AntiStep方块");
            } else if (teleportSafe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT, "Teleport2不安全，回退到Teleport");
            } else {
                return new TeleportDecision(DecisionType.ABORT_UNSAFE, "两种模式都不安全");
            }
        } else {
            // 没有AntiStep，根据安全情况选择
            if (teleportSafe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT, "无AntiStep，使用Teleport");
            } else if (teleport2Safe) {
                return new TeleportDecision(DecisionType.USE_TELEPORT2, "Teleport不安全，使用Teleport2");
            } else {
                return new TeleportDecision(DecisionType.ABORT_UNSAFE, "两种模式都不安全");
            }
        }
    }

    // 头顶方块分析
    private BlockAnalysis analyzeBlocksAbove() {
        int playerX = mc.player.getBlockX();
        int playerY = mc.player.getBlockY();
        int playerZ = mc.player.getBlockZ();

        int consecutiveBlocks = 0;
        boolean hasAntiStep = false;
        int maxHeight = maxCheckHeight.getValueInt();

        // 从玩家头顶开始向上检测
        for (int offset = 1; offset <= maxHeight; offset++) {
            BlockPos pos = new BlockPos(playerX, playerY + offset, playerZ);
            BlockState state = mc.world.getBlockState(pos);

            if (!isPositionSafe(pos)) {
                consecutiveBlocks++;

                // 检查是否是AntiStep位置（头上第3格）
                if (offset == 3) {
                    hasAntiStep = true;
                }
            } else {
                // 遇到空气，重置连续方块计数
                consecutiveBlocks = 0;
            }
        }

        return new BlockAnalysis(consecutiveBlocks, hasAntiStep);
    }

    /**
     * 简化安全检查，只检查目标位置是否安全
     * 移除了对目标位置上方1格和2格的检查
     * @param distance 传送距离（格数）
     * @return 是否安全
     */
    private boolean isTeleportSafe(int distance) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        // 计算目标位置（玩家当前位置向上distance格）
        int targetX = mc.player.getBlockX();
        int targetY = (int) Math.floor(mc.player.getY() + distance);
        int targetZ = mc.player.getBlockZ();

        // 只检查目标位置是否安全，不再检查上方格子
        BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
        return isPositionSafe(targetPos);
    }

    /**
     * 检查指定位置是否安全（是否可通行）
     */
    private boolean isPositionSafe(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() ||
                state.getCollisionShape(mc.world, pos) == null ||
                state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private void executeJump() {
        mc.getNetworkHandler()
                .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false));
        mc.getNetworkHandler()
                .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false));
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
    }

    private void executeGlitch() {
        double posX = mc.player.getX();
        double posY = Math.round(mc.player.getY());
        double posZ = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(posX, posY, posZ, onGround));
        double halfY = 0.005;
        posY -= halfY;
        mc.player.setPosition(posX, posY, posZ);
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(posX, posY, posZ, onGround));
        posY -= halfY * 300.0;
        mc.player.setPosition(posX, posY, posZ);
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(posX, posY, posZ, onGround));
    }

    /**
     * EscapeTrap模式的核心逻辑
     * 主候选位置搜索不受任何干扰（包括Strict）
     * 若主搜索失败且MaxUp开启，则进行Strict和Safety检查后尝试向上传送4格
     */
    private void executeEscapeTrap() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos headPos = playerPos.up(2); // 玩家头顶第2格
        ArrayList<BlockPos> candidatePositions = new ArrayList<>();

        // 检查以headPos为中心的3x3区域（除了正下方）
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                // 跳过正下方（x=0, z=0）
                if (x == 0 && z == 0) continue;

                BlockPos checkPos = headPos.add(x, 0, z);
                // 如果该位置有方块，则将其上方一格加入候选列表
                if (hasBlock(checkPos)) {
                    candidatePositions.add(checkPos.up());
                }
            }
        }

        // 如果头顶位置有方块，也将其上方一格加入候选列表
        if (hasBlock(headPos)) {
            candidatePositions.add(headPos.up());
        }

        // 遍历候选位置，寻找安全位置
        for (BlockPos pos : candidatePositions) {
            // 检查候选位置、其上方一格、上方两格是否都没有方块
            if (hasBlock(pos) || hasBlock(pos.up()) || hasBlock(pos.up(2))) {
                continue; // 不安全，跳过
            }

            // 找到安全位置，传送到该位置
            teleport(pos);
            return; // 成功传送，返回
        }

        // 主搜索失败，若MaxUp开启则尝试向上传送4格（需要满足Strict和Safety）
        if (maxUp.getValue()) {
            if (tryTeleportUp(4)) {
                return; // 传送成功
            }
        }
    }

    /**
     * 尝试向上传送指定距离，并执行Strict和Safety检查
     * @param distance 传送距离（格数）
     * @return 是否成功传送
     */
    private boolean tryTeleportUp(int distance) {
        if (mc.player == null || mc.world == null) return false;

        // Strict检查：头顶1格或2格必须有方块
        if (strict.getValue() && !checkStrictCondition()) return false;

        // Safety检查：目标位置必须安全
        if (safety.getValue() && !isTeleportSafe(distance)) return false;

        // 执行传送
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
        return true;
    }

    /**
     * 检查指定位置是否有方块
     * @param pos 位置
     * @return 是否有方块
     */
    private boolean hasBlock(BlockPos pos) {
        return mc.world != null && !mc.world.isAir(pos);
    }

    /**
     * 传送到指定位置
     * @param pos 目标位置
     */
    private void teleport(BlockPos pos) {
        // 再次安全检查（冗余但安全）
        if (hasBlock(pos) || hasBlock(pos.up()) || hasBlock(pos.up(2))) {
            return;
        }

        // 传送到方块中心
        double x = (double)pos.getX() + 0.5;
        double y = pos.getY();
        double z = (double)pos.getZ() + 0.5;

        mc.player.setPosition(x, y, z);
        mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, y, z, true));
    }

    // 模式枚举
    public static enum Mode {
        Glitch,
        Teleport,
        Teleport2,
        Jump,
        TeleportMax,
        EscapeTrap;
    }

    // 传送决策类型
    private enum DecisionType {
        USE_TELEPORT,      // 使用3格传送
        USE_TELEPORT2,     // 使用4格传送
        ABORT_TRAP,        // 多层方块陷阱，放弃传送
        ABORT_UNSAFE       // 不安全，放弃传送
    }

    // 传送决策
    private static class TeleportDecision {
        final DecisionType type;
        final String reason;

        TeleportDecision(DecisionType type, String reason) {
            this.type = type;
            this.reason = reason;
        }
    }

    // 方块分析结果
    private static class BlockAnalysis {
        final int consecutiveBlocks;  // 连续方块数量
        final boolean hasAntiStep;    // 是否有AntiStep方块

        BlockAnalysis(int consecutiveBlocks, boolean hasAntiStep) {
            this.consecutiveBlocks = consecutiveBlocks;
            this.hasAntiStep = hasAntiStep;
        }
    }
}