package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.nagisa.*;
import dev.luminous.mod.modules.impl.nagisa.GroundMace;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Burrow extends Module {
    public static Burrow INSTANCE;
    private final EnumSetting<RotateMode> rotate = this.add(new EnumSetting<>("RotateMode", RotateMode.Bypass));
    private final EnumSetting<LagBackMode> lagMode = this.add(new EnumSetting<>("LagMode", LagBackMode.TrollHack));
    private final EnumSetting<LagBackMode> aboveLagMode = this.add(new EnumSetting<>("MoveLagMode", LagBackMode.Smart));
    private final List<BlockPos> placePos = new ArrayList<>();
    private final Timer timer = new Timer();
    private final Timer webTimer = new Timer();
    private final BooleanSetting disable = this.add(new BooleanSetting("Disable", true));
    private final BooleanSetting jumpDisable = this.add(new BooleanSetting("JumpDisable", true, () -> !this.disable.getValue()));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 500, 0, 1000, () -> !this.disable.getValue()));
    private final SliderSetting webTime = this.add(new SliderSetting("WebTime", 0, 0, 500));
    private final BooleanSetting enderChest = this.add(new BooleanSetting("EnderChest", true));
    private final BooleanSetting antiLag = this.add(new BooleanSetting("AntiLag", false));
    private final BooleanSetting single = this.add(new BooleanSetting("SingleBlock", false));
    private final BooleanSetting detectMine = this.add(new BooleanSetting("DetectMining", false));
    private final BooleanSetting headFill = this.add(new BooleanSetting("HeadFill", false));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", false));
    private final BooleanSetting down = this.add(new BooleanSetting("Down", true));
    private final BooleanSetting noSelfPos = this.add(new BooleanSetting("NoSelfPos", false));
    private final BooleanSetting packetPlace = this.add(new BooleanSetting("PacketPlace", true));
    private final BooleanSetting sound = this.add(new BooleanSetting("Sound", true));
    private final SliderSetting blocksPer = this.add(new SliderSetting("BlocksPer", 4.0, 1.0, 4.0, 1.0));
    private final BooleanSetting breakCrystal = this.add(new BooleanSetting("Break", true));
    private final BooleanSetting wait = this.add(new BooleanSetting("Wait", true, this.disable::getValue));
    private final BooleanSetting fakeMove = this.add(new BooleanSetting("FakeMove", true).setParent());
    private final BooleanSetting center = this.add(new BooleanSetting("Center", true, this.fakeMove::isOpen)); // 原有的 Center，允许移动到空气
    private final BooleanSetting allowCenter = this.add(new BooleanSetting("AllowCenter", false, this.fakeMove::isOpen)); // 新增：使用简化逻辑
    private final SliderSetting preCorrect = this.add(new SliderSetting("PreCorrect", 0.25, 0.0, 1.0, 0.001, this.fakeMove::isOpen));
    private final SliderSetting moveDis = this.add(new SliderSetting("MoveDis", 0.25, 0.0, 1.0, 0.001, this.fakeMove::isOpen));
    private final SliderSetting moveDis2 = this.add(new SliderSetting("MoveDis2", 0.25, 0.0, 1.0, 0.001, this.fakeMove::isOpen));
    private final SliderSetting moveCorrect2 = this.add(new SliderSetting("Correct", 0.25, 0.0, 1.0, 0.001, this.fakeMove::isOpen));
    private final SliderSetting yOffset = this.add(new SliderSetting("YOffset", 0.01, 0.0, 1.0, 0.001, this.fakeMove::isOpen));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final SliderSetting smartX = this.add(new SliderSetting("SmartXZ", 3.0, 0.0, 10.0, 0.1,
            () -> this.lagMode.getValue() == LagBackMode.Smart || this.aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartUp = this.add(new SliderSetting("SmartUp", 3.0, 0.0, 10.0, 0.1,
            () -> this.lagMode.getValue() == LagBackMode.Smart || this.aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartDown = this.add(new SliderSetting("SmartDown", 3.0, 0.0, 10.0, 0.1,
            () -> this.lagMode.getValue() == LagBackMode.Smart || this.aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartDistance = this.add(new SliderSetting("SmartDistance", 2.0, 0.0, 10.0, 0.1,
            () -> this.lagMode.getValue() == LagBackMode.Smart || this.aboveLagMode.getValue() == LagBackMode.Smart));
    private final BooleanSetting yawStep = this.add(new BooleanSetting("YawStep", false).setParent());
    private final SliderSetting steps = this.add(new SliderSetting("Steps", 1.0, 0.0, 1.0, 0.01, this.yawStep::isOpen));
    private final SliderSetting priority = this.add(new SliderSetting("Priority", 50, 1, 100, this.yawStep::isOpen));
    private int progress = 0;
    private Vec3d currentPos;
    public Vec3d directionVec = null;
    private boolean isPlacing = false;

    public Burrow() {
        super("Burrow", Module.Category.Combat);
        this.setChinese("卡黑曜石");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        this.directionVec = null;
        this.currentPos = null;
        this.placePos.clear();
        this.progress = 0;
        this.isPlacing = false;
    }

    public boolean isPlacing() {
        return this.isPlacing;
    }

    private boolean isMaceActive() {
        return (GroundMace.INSTANCE != null && GroundMace.INSTANCE.isOn())
                || (TPMace.INSTANCE != null && TPMace.INSTANCE.isOn())
                || (VoidMace.INSTANCE != null && VoidMace.INSTANCE.isOn())
                || (MaceAura.INSTANCE != null && MaceAura.INSTANCE.isOn());
    }

    @EventListener
    public void onUpdate(ClientTickEvent event) {
        if (!this.inventory.getValue() || EntityUtil.inInventory()) {
            if (event.isPost()) {
                if (isMaceActive()) {
                    return;
                }
                if (!this.disable.getValue() && this.jumpDisable.getValue() && mc.player.input.jumping) {
                    this.disable();
                    return;
                }
                if (Alien.PLAYER.isInWeb(mc.player)) {
                    this.webTimer.reset();
                    return;
                }
                if (this.usingPause.getValue() && mc.player.isUsingItem()) {
                    return;
                }
                if (!this.webTimer.passedMs(this.webTime.getValue())) {
                    return;
                }
                if (!this.disable.getValue() && !this.timer.passedMs(this.delay.getValue())) {
                    return;
                }
                if (!mc.player.isOnGround()) {
                    return;
                }
                if (this.antiLag.getValue()
                        && !BlockUtil.canCollide(mc.player, new Box(EntityUtil.getPlayerPos(true).down()))) {
                    return;
                }
                if (this.single.getValue() && EntityUtil.isInsideBlock()) {
                    if (this.disable.getValue()) {
                        this.disable();
                    }
                    return;
                }
                if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
                    return;
                }

                int oldSlot = mc.player.getInventory().selectedSlot;
                int block;
                if ((block = this.getBlock()) == -1) {
                    CommandManager.sendMessageId("§4No block found.", this.hashCode() - 1);
                    this.disable();
                    return;
                }

                this.progress = 0;
                this.placePos.clear();
                double offset = this.single.getValue() ? 0.0 : AntiCheat.getOffset();
                BlockPos pos1 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 0.5, mc.player.getZ() + offset);
                BlockPos pos2 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 0.5, mc.player.getZ() + offset);
                BlockPos pos3 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 0.5, mc.player.getZ() - offset);
                BlockPos pos4 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 0.5, mc.player.getZ() - offset);
                BlockPos pos5 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 1.5, mc.player.getZ() + offset);
                BlockPos pos6 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 1.5, mc.player.getZ() + offset);
                BlockPos pos7 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 1.5, mc.player.getZ() - offset);
                BlockPos pos8 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 1.5, mc.player.getZ() - offset);
                BlockPos pos9 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() - 1.0, mc.player.getZ() + offset);
                BlockPos pos10 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() - 1.0, mc.player.getZ() + offset);
                BlockPos pos11 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() - 1.0, mc.player.getZ() - offset);
                BlockPos pos12 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() - 1.0, mc.player.getZ() - offset);
                BlockPos playerPos = EntityUtil.getPlayerPos(true);
                boolean headFill = false;
                if (!this.canPlace(pos1) && !this.canPlace(pos2) && !this.canPlace(pos3) && !this.canPlace(pos4)) {
                    boolean cantHeadFill = !this.headFill.getValue()
                            || !this.canPlace(pos5) && !this.canPlace(pos6) && !this.canPlace(pos7) && !this.canPlace(pos8);
                    boolean cantDown = !this.down.getValue()
                            || !this.canPlace(pos9) && !this.canPlace(pos10) && !this.canPlace(pos11) && !this.canPlace(pos12);
                    if (cantHeadFill) {
                        if (cantDown) {
                            if (!this.wait.getValue() && this.disable.getValue()) {
                                this.disable();
                            }
                            return;
                        }
                    } else {
                        headFill = true;
                    }
                }

                boolean above = false;
                BlockPos headPos = EntityUtil.getPlayerPos(true).up(2);
                boolean rotate = this.rotate.getValue() == RotateMode.Normal;
                CombatUtil.attackCrystal(pos1, rotate, false);
                CombatUtil.attackCrystal(pos2, rotate, false);
                CombatUtil.attackCrystal(pos3, rotate, false);
                CombatUtil.attackCrystal(pos4, rotate, false);

                // 头顶被堵或被困住
                if (headFill || mc.player.isCrawling() || this.trapped(headPos)
                        || this.trapped(headPos.add(1, 0, 0)) || this.trapped(headPos.add(-1, 0, 0))
                        || this.trapped(headPos.add(0, 0, 1)) || this.trapped(headPos.add(0, 0, -1))
                        || this.trapped(headPos.add(1, 0, -1)) || this.trapped(headPos.add(-1, 0, -1))
                        || this.trapped(headPos.add(1, 0, 1)) || this.trapped(headPos.add(-1, 0, 1))) {
                    above = true;
                    if (!this.fakeMove.getValue()) {
                        if (!this.wait.getValue() && this.disable.getValue()) {
                            this.disable();
                        }
                        return;
                    }

                    // 根据 AllowCenter 选择使用简化逻辑还是原有多步移动
                    if (this.allowCenter.getValue()) {
                        boolean moved = false;
                        BlockPos offPos = playerPos;
                        // 优先级1：玩家碰撞箱所在的不可替换方块
                        if (this.checkSelf(offPos) && !BlockUtil.canReplace(offPos)
                                && (!this.headFill.getValue() || !BlockUtil.canReplace(offPos.up()))) {
                            this.simpleGotoPos(offPos);
                            moved = true;
                        } else {
                            for (Direction facing : Direction.values()) {
                                if (facing == Direction.UP || facing == Direction.DOWN) continue;
                                offPos = playerPos.offset(facing);
                                if (this.checkSelf(offPos) && !BlockUtil.canReplace(offPos)
                                        && (!this.headFill.getValue() || !BlockUtil.canReplace(offPos.up()))) {
                                    this.simpleGotoPos(offPos);
                                    moved = true;
                                    break;
                                }
                            }
                        }
                        // 优先级2：只要碰撞箱相交
                        if (!moved) {
                            for (Direction facing : Direction.values()) {
                                if (facing == Direction.UP || facing == Direction.DOWN) continue;
                                offPos = playerPos.offset(facing);
                                if (this.checkSelf(offPos)) {
                                    this.simpleGotoPos(offPos);
                                    moved = true;
                                    break;
                                }
                            }
                        }
                        // 优先级3：移动到空气方块（需 Center 也开启）
                        if (!moved) {
                            if (!this.center.getValue()) {
                                if (!this.wait.getValue() && this.disable.getValue()) {
                                    this.disable();
                                }
                                return;
                            }
                            for (Direction facing : Direction.values()) {
                                if (facing == Direction.UP || facing == Direction.DOWN) continue;
                                offPos = playerPos.offset(facing);
                                if (this.canMove(offPos)) {
                                    this.simpleGotoPos(offPos);
                                    moved = true;
                                    break;
                                }
                            }
                            if (!moved) {
                                if (!this.wait.getValue() && this.disable.getValue()) {
                                    this.disable();
                                }
                                return;
                            }
                        }
                    } else {
                        // 原多步假移动
                        if (!this.fakeMove()) {
                            return;
                        }
                    }
                } else {
                    // 头顶有空间，正常向上假跳
                    mc.getNetworkHandler().sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false));
                    mc.getNetworkHandler().sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false));
                    mc.getNetworkHandler().sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.9999957640154541, mc.player.getZ(), false));
                    mc.getNetworkHandler().sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ(), false));
                    this.currentPos = new Vec3d(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ());
                }

                this.timer.reset();
                this.doSwap(block);
                this.isPlacing = true;
                if (this.rotate.getValue() == RotateMode.Bypass) {
                    if (above) {
                        // P3: 当VoidMace处于FALLING阶段时，发送旋转包会导致强制低头卡视角
                        if (VoidMace.INSTANCE != null && VoidMace.INSTANCE.isOn()) {
                            // 不计算旋转，直接使用当前玩家视角（不强制低头）
                            if (this.yawStep.getValue()) {
                                this.directionVec = mc.player.getEyePos().add(0, -1, 0);
                            } else {
                                this.sendRotationPacket(mc.player.getYaw(), mc.player.getPitch());
                            }
                        } else {
                            // 修复：getRotation(from, to) 需要从玩家眼睛位置指向目标位置
                            float[] angle = RotationManager.getRotation(
                                    mc.player.getEyePos(),
                                    this.currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0));
                            if (this.yawStep.getValue()) {
                                this.directionVec = this.currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
                            } else {
                                // 只发旋转包，不修改渲染视角（不强制扭头）
                                this.sendRotationPacket(angle[0], angle[1]);
                            }
                        }
                    } else {
                        if (this.yawStep.getValue()) {
                            this.directionVec = mc.player.getEyePos().add(0, -1, 0);
                        } else {
                            // 不再硬编码pitch=90（不强制低头），保持当前pitch
                            this.sendRotationPacket(Alien.ROTATION.rotationYaw, Alien.ROTATION.rotationPitch);
                        }
                    }
                }

                this.placeBlock(playerPos, rotate);
                this.placeBlock(pos1, rotate);
                this.placeBlock(pos2, rotate);
                this.placeBlock(pos3, rotate);
                this.placeBlock(pos4, rotate);
                if (this.down.getValue()) {
                    this.placeBlock(pos9, rotate);
                    this.placeBlock(pos10, rotate);
                    this.placeBlock(pos11, rotate);
                    this.placeBlock(pos12, rotate);
                }
                if (this.headFill.getValue() && above) {
                    this.placeBlock(pos5, rotate);
                    this.placeBlock(pos6, rotate);
                    this.placeBlock(pos7, rotate);
                    this.placeBlock(pos8, rotate);
                }
                this.isPlacing = false;

                if (this.inventory.getValue()) {
                    this.doSwap(block);
                    EntityUtil.syncInventory();
                } else {
                    this.doSwap(oldSlot);
                }

                switch (above ? this.aboveLagMode.getValue() : this.lagMode.getValue()) {
                    case Smart -> {
                        ArrayList<BlockPos> list = new ArrayList<>();
                        for (double x = mc.player.getPos().getX() - this.smartX.getValue(); x < mc.player.getPos().getX()
                                + this.smartX.getValue(); x++) {
                            for (double z = mc.player.getPos().getZ() - this.smartX.getValue(); z < mc.player.getPos()
                                    .getZ() + this.smartX.getValue(); z++) {
                                for (double y = mc.player.getPos().getY() - this.smartDown.getValue(); y < mc.player
                                        .getPos().getY() + this.smartUp.getValue(); y++) {
                                    list.add(new BlockPosX(x, y, z));
                                }
                            }
                        }
                        double distance = 0.0;
                        BlockPos bestPos = null;
                        for (BlockPos pos : list) {
                            if (this.canMove(pos)
                                    && !(MathHelper.sqrt((float) mc.player
                                    .squaredDistanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0))) < this.smartDistance
                                    .getValue())
                                    && (bestPos == null
                                    || mc.player.squaredDistanceTo(pos.toCenterPos()) < distance)) {
                                bestPos = pos;
                                distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                            }
                        }
                        if (bestPos != null) {
                            mc.getNetworkHandler().sendPacket(
                                    new PositionAndOnGround(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false));
                        }
                    }
                    case Invalid -> {
                        for (int i = 0; i < 20; i++) {
                            mc.getNetworkHandler().sendPacket(
                                    new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1337.0, mc.player.getZ(), false));
                        }
                    }
                    case TrollHack ->
                            mc.getNetworkHandler().sendPacket(
                                    new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(),
                                            false));
                    case ToVoid ->
                            mc.getNetworkHandler()
                                    .sendPacket(new PositionAndOnGround(mc.player.getX(), -70.0, mc.player.getZ(), false));
                    case ToVoid2 ->
                            mc.getNetworkHandler()
                                    .sendPacket(new PositionAndOnGround(mc.player.getX(), -7.0, mc.player.getZ(), false));
                    case Normal ->
                            mc.getNetworkHandler().sendPacket(
                                    new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ(), false));
                    case Rotation -> {
                        // 修复：-180/180极端yaw会强制转头180°，完全不扭头：保持玩家真实yaw/pitch，仅做普通Look包
                        this.sendRotationPacket(mc.player.getYaw(), mc.player.getPitch());
                    }
                    case Fly -> {
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.170005801788139, mc.player.getZ(),
                                        false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.2426308013947485, mc.player.getZ(),
                                        false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(),
                                        false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.640088003576279, mc.player.getZ(),
                                        false));
                    }
                    case Glide -> {
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false));
                        mc.getNetworkHandler().sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false));
                    }
                }

                if (this.disable.getValue()) {
                    this.disable();
                }
            }
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        // RotateMode=None 或 YawStep=false 完全不做任何渐进旋转，避免强制扭头
        if (this.rotate.getValue() == RotateMode.None || !this.yawStep.getValue() || this.directionVec == null) {
            return;
        }
        event.setTarget(this.directionVec, this.steps.getValueFloat(), this.priority.getValueFloat());
    }

    // === 第二段简化 FakeMove 的单包瞬移方法 ===
    private void simpleGotoPos(BlockPos offPos) {
        double x = offPos.getX() + 0.5;
        double y = mc.player.getY() + 0.1;
        double z = offPos.getZ() + 0.5;
        this.currentPos = new Vec3d(x, y, z); // 更新当前位置，供后续旋转使用

        if (this.rotate.getValue() == RotateMode.None) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
        } else {
            float realYaw = mc.player.getYaw();
            float realPitch = mc.player.getPitch();
            // 修复：不硬编码pitch=90（低头最大），使用玩家当前pitch保持自然视角
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.Full(x, y, z, Alien.ROTATION.rotationYaw, Alien.ROTATION.rotationPitch, false));
            // 立即恢复 lastYaw/lastPitch 到玩家真实视角，避免渲染被Full包的旋转信息覆盖（不强制扭头）
            Alien.ROTATION.setLastYaw(realYaw);
            Alien.ROTATION.setLastPitch(realPitch);
        }
    }

    // 原有的多步假移动 fakeMove
    private boolean fakeMove() {
        double[] offsets = new double[]{1.0, 0.0, -1.0};
        List<BlockPos> offList = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();

        for (double x : offsets) {
            for (double z : offsets) {
                offList.add(new BlockPosX(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z));
            }
        }

        for (BlockPos offPos : offList) {
            if (this.checkSelf(offPos) && !BlockUtil.canReplace(offPos)
                    && (!this.headFill.getValue() || !BlockUtil.canReplace(offPos.up()))) {
                this.gotoPos(offPos);
                return true;
            }
        }

        List<BlockPos> pos = new ArrayList<>();
        for (BlockPos offPosx : offList) {
            if (!playerPos.equals(offPosx) && this.checkSelf(offPosx) && this.canMove(offPosx)) {
                pos.add(offPosx);
            }
        }

        if (!pos.isEmpty()) {
            double dis = 10.0;
            BlockPos target = null;
            for (BlockPos p : pos) {
                double distance = mc.player.getPos().distanceTo(p.toCenterPos().add(0.0, -0.5, 0.0));
                if (distance < dis || target == null) {
                    target = p;
                    dis = distance;
                }
            }
            this.gotoPos(target);
            return true;
        } else {
            for (BlockPos offPosxx : offList) {
                if (!playerPos.equals(offPosxx) && this.checkSelf(offPosxx)) {
                    pos.add(offPosxx);
                }
            }

            if (!pos.isEmpty()) {
                double dis = 10.0;
                BlockPos target = null;
                for (BlockPos px : pos) {
                    double distance = mc.player.getPos().distanceTo(px.toCenterPos().add(0.0, -0.5, 0.0));
                    if (distance < dis || target == null) {
                        target = px;
                        dis = distance;
                    }
                }
                this.gotoPos(target);
                return true;
            } else if (!this.center.getValue()) {
                return false;
            } else {
                offList.clear();
                offList.add(new BlockPosX(mc.player.getX() + 1.0, mc.player.getY(), mc.player.getZ()));
                offList.add(new BlockPosX(mc.player.getX() - 1.0, mc.player.getY(), mc.player.getZ()));
                offList.add(new BlockPosX(mc.player.getX(), mc.player.getY(), mc.player.getZ() - 1.0));
                offList.add(new BlockPosX(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 1.0));

                for (BlockPos offPosxxx : offList) {
                    if (this.canMove(offPosxxx)) {
                        this.gotoPos(offPosxxx);
                        return true;
                    }
                }

                if (!this.wait.getValue() && this.disable.getValue()) {
                    this.disable();
                }
                return false;
            }
        }
    }

    // 原有的多步 gotoPos
    private void gotoPos(BlockPos offPos) {
        double targetX = offPos.getX() + 0.5;
        double targetZ = offPos.getZ() + 0.5;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = mc.player.getY() + this.yOffset.getValue();
        double xDiff = Math.abs(x - targetX);
        double zDiff = Math.abs(z - targetZ);
        double moveDis = this.preCorrect.getValue();
        if (moveDis > 0.0) {
            if (xDiff >= moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
            }
            if (zDiff >= moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
            }
        }

        xDiff = Math.abs(x - targetX);
        zDiff = Math.abs(z - targetZ);
        moveDis = this.moveDis.getValue();
        if (moveDis > 0.0) {
            while (xDiff > moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
                zDiff = Math.abs(z - targetZ);
            }
        }

        moveDis = this.moveDis2.getValue();
        if (moveDis > 0.0) {
            while (xDiff > moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
                zDiff = Math.abs(z - targetZ);
            }
        }

        moveDis = this.moveCorrect2.getValue();
        if (moveDis > 0.0) {
            if (xDiff >= moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
            }
            if (zDiff >= moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.networkHandler.sendPacket(new PositionAndOnGround(x, y, z, false));
            }
        }

        this.currentPos = new Vec3d(x, y, z);
    }

    private void placeBlock(BlockPos pos, boolean rotate) {
        if (this.canPlace(pos) && !this.placePos.contains(pos) && this.progress < this.blocksPer.getValueInt()) {
            this.placePos.add(pos);
            if (BlockUtil.allowAirPlace()) {
                this.progress++;
                BlockUtil.placedPos.add(pos);
                if (this.sound.getValue()) {
                    mc.world.playSound(mc.player, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
                }
                this.clickBlock(pos, Direction.DOWN, rotate, Hand.MAIN_HAND, this.packetPlace.getValue());
            }

            Direction side;
            if ((side = BlockUtil.getPlaceSide(pos)) == null) {
                return;
            }
            this.progress++;
            BlockUtil.placedPos.add(pos);
            if (this.sound.getValue()) {
                mc.world.playSound(mc.player, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
            }
            this.clickBlock(pos.offset(side), side.getOpposite(), rotate, Hand.MAIN_HAND, this.packetPlace.getValue());
        }
    }

    public void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand, boolean packet) {
        Vec3d clickVec = new Vec3d(
                pos.getX() + 0.5 + side.getVector().getX() * 0.5,
                pos.getY() + 0.5 + side.getVector().getY() * 0.5,
                pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        if (rotate) {
            if (this.yawStep.getValue()) {
                this.directionVec = clickVec;
            } else {
                // 修复：from应该是玩家眼睛位置（mc.player.getEyePos()），不是currentPos；currentPos只是假移动坐标
                float[] angle = RotationManager.getRotation(
                        this.currentPos != null
                                ? this.currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0)
                                : mc.player.getEyePos(),
                        clickVec);
                // 只发旋转包，不修改渲染视角（不强制扭头）
                this.sendRotationPacket(angle[0], angle[1]);
            }
        }

        EntityUtil.swingHand(hand, (SwingSide) AntiCheat.INSTANCE.interactSwing.getValue());
        BlockHitResult result = new BlockHitResult(clickVec, side, pos, false);
        if (packet) {
            Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(hand, result, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }

        // 不再调用 Alien.ROTATION.snapBack()，避免触发回扭渲染视角
    }

    /**
     * 只向服务器发送旋转包，不修改玩家本地渲染视角（不会强制扭头）。
     * 思路：
     * 1. 保存玩家真实 yaw/pitch（用于恢复）。
     * 2. 发送 LookAndOnGround / Full（按 AntiCheat.grimRotation）包。
     * 3. 包发出后 onPacketSend 会把 lastYaw/lastPitch 改成刚才发的值，
     *    立刻调用 Alien.ROTATION.setLastYaw / setLastPitch 覆盖回玩家真实值，
     *    确保后续 onUpdateWalkingPost 用真实视角刷新渲染，不发生扭头。
     */
    private void sendRotationPacket(float yaw, float pitch) {
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();

        if (AntiCheat.INSTANCE.grimRotation.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    yaw, pitch, mc.player.isOnGround()));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch, mc.player.isOnGround()));
        }

        // 立即把 lastYaw/lastPitch 恢复成玩家真实视角，防止 RotationManager 后续基于发包值刷新渲染
        Alien.ROTATION.setLastYaw(realYaw);
        Alien.ROTATION.setLastPitch(realPitch);
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private boolean canMove(BlockPos pos) {
        return mc.world.isAir(pos) && mc.world.isAir(pos.up());
    }

    private boolean canPlace(BlockPos pos) {
        if (this.noSelfPos.getValue() && pos.equals(EntityUtil.getPlayerPos(true))) {
            return false;
        } else if (!BlockUtil.allowAirPlace() && BlockUtil.getPlaceSide(pos) == null) {
            return false;
        } else if (!BlockUtil.canReplace(pos)) {
            return false;
        } else {
            return this.detectMine.getValue() && Alien.BREAK.isMining(pos) ? false : !this.hasEntity(pos);
        }
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (entity != mc.player
                    && entity.isAlive()
                    && !(entity instanceof ItemEntity)
                    && !(entity instanceof ExperienceOrbEntity)
                    && !(entity instanceof ExperienceBottleEntity)
                    && !(entity instanceof ArrowEntity)
                    && (!(entity instanceof EndCrystalEntity) || !this.breakCrystal.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSelf(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new Box(pos));
    }

    private boolean trapped(BlockPos pos) {
        return (BlockUtil.canCollide(mc.player, new Box(pos)) || BlockUtil.getBlock(pos) == Blocks.COBWEB) && this.checkSelf(pos.down(2));
    }

    private int getBlock() {
        if (this.inventory.getValue()) {
            int cryingObsidianSlot = InventoryUtil.findBlockInventorySlot(Blocks.CRYING_OBSIDIAN);
            if (cryingObsidianSlot != -1) {
                return cryingObsidianSlot;
            }
            int obsidianSlot = InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
            if (obsidianSlot != -1) {
                return obsidianSlot;
            }
            return this.enderChest.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST) : -1;
        } else {
            int cryingObsidian = InventoryUtil.findBlock(Blocks.CRYING_OBSIDIAN);
            if (cryingObsidian != -1) {
                return cryingObsidian;
            }
            int obsidian = InventoryUtil.findBlock(Blocks.OBSIDIAN);
            if (obsidian != -1) {
                return obsidian;
            }
            return this.enderChest.getValue() ? InventoryUtil.findBlock(Blocks.ENDER_CHEST) : -1;
        }
    }

    private enum LagBackMode {
        Smart, Invalid, TrollHack, ToVoid, ToVoid2, Normal, Rotation, Fly, Glide
    }

    private enum RotateMode {
        Bypass, Normal, None
    }
}