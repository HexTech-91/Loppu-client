package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
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
import dev.luminous.mod.modules.impl.exploit.AntiPacket;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
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

public class BurrowSkill extends Module {
    public static BurrowSkill INSTANCE;

    // 页面设置
    public final EnumSetting<BurrowSkill.Page> page = this.add(new EnumSetting("Page", BurrowSkill.Page.General));

    // General页面设置（已删除Disable, JumpDisable, HeadFill）
    private final EnumSetting<BurrowSkill.RotateMode> rotate = this.add(new EnumSetting("RotateMode", BurrowSkill.RotateMode.Bypass, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final EnumSetting<BurrowSkill.LagBackMode> lagMode = this.add(new EnumSetting("LagMode", BurrowSkill.LagBackMode.TrollHack, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final EnumSetting<BurrowSkill.LagBackMode> aboveLagMode = this.add(new EnumSetting("MoveLagMode", BurrowSkill.LagBackMode.Smart, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final List<BlockPos> placePos = new ArrayList();
    private final Timer timer = new Timer();
    private final Timer webTimer = new Timer();

    // 删除Disable和JumpDisable设置，保留Delay
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 500, 0, 1000, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting webTime = this.add(new SliderSetting("WebTime", 0, 0, 500, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting antiLag = this.add(new BooleanSetting("AntiLag", false, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting single = this.add(new BooleanSetting("SingleBlock", false, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting detectMine = this.add(new BooleanSetting("DetectMining", false, () -> this.page.getValue() == BurrowSkill.Page.General));

    // 删除HeadFill设置，保留usingPause
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", false, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting down = this.add(new BooleanSetting("Down", true, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting noSelfPos = this.add(new BooleanSetting("NoSelfPos", false, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting packetPlace = this.add(new BooleanSetting("PacketPlace", true, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting sound = this.add(new BooleanSetting("Sound", true, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting blocksPer = this.add(new SliderSetting("BlocksPer", 4.0, 1.0, 4.0, 1.0, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting breakCrystal = this.add(new BooleanSetting("Break", true, () -> this.page.getValue() == BurrowSkill.Page.General));

    // 删除wait设置（它依赖disable）
    private final BooleanSetting fakeMove = this.add(new BooleanSetting("FakeMove", true, () -> this.page.getValue() == BurrowSkill.Page.General).setParent());
    private final BooleanSetting center = this.add(new BooleanSetting("AllowCenter", true, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting preCorrect = this.add(new SliderSetting("PreCorrect", 0.25, 0.0, 1.0, 0.001, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting moveDis = this.add(new SliderSetting("MoveMoveDis", 0.25, 0.0, 1.0, 0.001, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting moveDis2 = this.add(new SliderSetting("MoveDis2", 0.25, 0.0, 1.0, 0.001, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting moveCorrect2 = this.add(new SliderSetting("Correct", 0.25, 0.0, 1.0, 0.001, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting yOffset = this.add(new SliderSetting("YOffset", 0.01, 0.0, 1.0, 0.001, () -> this.fakeMove.isOpen() && this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final SliderSetting smartX = this.add(
            new SliderSetting(
                    "SmartXZ", 3.0, 0.0, 10.0, 0.1, () -> (this.lagMode.getValue() == BurrowSkill.LagBackMode.Smart || this.aboveLagMode.getValue() == BurrowSkill.LagBackMode.Smart) && this.page.getValue() == BurrowSkill.Page.General
            )
    );
    private final SliderSetting smartUp = this.add(
            new SliderSetting(
                    "SmartUp", 3.0, 0.0, 10.0, 0.1, () -> (this.lagMode.getValue() == BurrowSkill.LagBackMode.Smart || this.aboveLagMode.getValue() == BurrowSkill.LagBackMode.Smart) && this.page.getValue() == BurrowSkill.Page.General
            )
    );
    private final SliderSetting smartDown = this.add(
            new SliderSetting(
                    "SmartDown",
                    3.0,
                    0.0,
                    10.0,
                    0.1,
                    () -> (this.lagMode.getValue() == BurrowSkill.LagBackMode.Smart || this.aboveLagMode.getValue() == BurrowSkill.LagBackMode.Smart) && this.page.getValue() == BurrowSkill.Page.General
            )
    );
    private final SliderSetting smartDistance = this.add(
            new SliderSetting(
                    "SmartDistance",
                    2.0,
                    0.0,
                    10.0,
                    0.1,
                    () -> (this.lagMode.getValue() == BurrowSkill.LagBackMode.Smart || this.aboveLagMode.getValue() == BurrowSkill.LagBackMode.Smart) && this.page.getValue() == BurrowSkill.Page.General
            )
    );

    // 缓降检测相关设置
    private final BooleanSetting slowFallingCheck = this.add(new BooleanSetting("SlowFallingCheck", false, () -> this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting mode2 = this.add(new BooleanSetting("Mode2", false, () -> this.slowFallingCheck.getValue() && this.page.getValue() == BurrowSkill.Page.General));
    private final BooleanSetting antiAntiPacket = this.add(new BooleanSetting("AntiAntiPacket", false, () -> this.mode2.getValue() && this.page.getValue() == BurrowSkill.Page.General));

    // Blocks页面设置
    private final BooleanSetting iceEnabled = this.add(new BooleanSetting("Ice", true, () -> this.page.getValue() == BurrowSkill.Page.Blocks));
    private final BindSetting iceKey = this.add(new BindSetting("IceKey", -1, () -> this.page.getValue() == BurrowSkill.Page.Blocks && iceEnabled.getValue()));
    private final BooleanSetting cryingObsidianEnabled = this.add(new BooleanSetting("CryingObsidian", true, () -> this.page.getValue() == BurrowSkill.Page.Blocks));
    private final BindSetting cryingObsidianKey = this.add(new BindSetting("CryingObsidianKey", -1, () -> this.page.getValue() == BurrowSkill.Page.Blocks && cryingObsidianEnabled.getValue()));
    private final BooleanSetting ancientDebrisEnabled = this.add(new BooleanSetting("AncientDebris", true, () -> this.page.getValue() == BurrowSkill.Page.Blocks));
    private final BindSetting ancientDebrisKey = this.add(new BindSetting("AncientDebrisKey", -1, () -> this.page.getValue() == BurrowSkill.Page.Blocks && ancientDebrisEnabled.getValue()));

    // 按键状态跟踪
    private boolean wasIceKeyPressed = false;
    private boolean wasCryingObsidianKeyPressed = false;
    private boolean wasAncientDebrisKeyPressed = false;

    private int progress = 0;
    private Vec3d currentPos;
    private RotateMode originalRotate;
    private LagBackMode originalLagMode;
    private LagBackMode originalAboveLagMode;
    private Boolean originalAntiPacketS2CRotate;

    // 当前选中的方块类型
    private BlockType currentBlockType = null;

    // 方块类型枚举
    private enum BlockType {
        ICE,
        CRYING_OBSIDIAN,
        ANCIENT_DEBRIS
    }

    public BurrowSkill() {
        super("BurrowSkill", Module.Category.Combat);
        this.setChinese("埋身技能");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // 重置按键状态
        wasIceKeyPressed = false;
        wasCryingObsidianKeyPressed = false;
        wasAncientDebrisKeyPressed = false;
        currentBlockType = null;
    }

    @EventListener
    public void onUpdate(ClientTickEvent event) {
        if (!this.inventory.getValue() || EntityUtil.inInventory()) {
            if (event.isPost()) {
                // 检测按键输入
                boolean isIceKeyPressed = iceKey.isPressed();
                boolean isCryingObsidianKeyPressed = cryingObsidianKey.isPressed();
                boolean isAncientDebrisKeyPressed = ancientDebrisKey.isPressed();

                // 检查冰按键
                if (isIceKeyPressed && !wasIceKeyPressed && iceEnabled.getValue()) {
                    currentBlockType = BlockType.ICE;
                    executeBurrow();
                }

                // 检查哭泣黑曜石按键
                if (isCryingObsidianKeyPressed && !wasCryingObsidianKeyPressed && cryingObsidianEnabled.getValue()) {
                    currentBlockType = BlockType.CRYING_OBSIDIAN;
                    executeBurrow();
                }

                // 检查下界残骸按键
                if (isAncientDebrisKeyPressed && !wasAncientDebrisKeyPressed && ancientDebrisEnabled.getValue()) {
                    currentBlockType = BlockType.ANCIENT_DEBRIS;
                    executeBurrow();
                }

                // 更新按键状态
                wasIceKeyPressed = isIceKeyPressed;
                wasCryingObsidianKeyPressed = isCryingObsidianKeyPressed;
                wasAncientDebrisKeyPressed = isAncientDebrisKeyPressed;
            }
        }
    }

    private void executeBurrow() {
        if (currentBlockType == null) {
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

        // Disable为false时的逻辑：不检查timer.passedMs(this.delay.getValue())？
        // 不对，即使Disable为false，原代码仍会检查delay
        if (!this.timer.passedMs(this.delay.getValue())) {
            return;
        }

        if (!mc.player.isOnGround()) {
            return;
        }

        // JumpDisable为false时的逻辑：不检查跳跃
        // 原代码：if (!this.disable.getValue() && this.jumpDisable.getValue() && mc.player.input.jumping)
        // 当JumpDisable为false时，即使跳跃也不会禁用，所以这里不需要任何检查

        // 缓降检测和处理
        boolean hasSlowFalling = mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
        if (this.slowFallingCheck.getValue() && hasSlowFalling && !this.mode2.getValue()) {
            return;
        }

        // 缓降模式切换
        if (this.mode2.getValue()) {
            if (hasSlowFalling) {
                if (this.originalRotate == null) {
                    this.originalRotate = this.rotate.getValue();
                    this.originalLagMode = this.lagMode.getValue();
                    this.originalAboveLagMode = this.aboveLagMode.getValue();
                }
                this.rotate.setValue(RotateMode.None);
                this.lagMode.setValue(LagBackMode.Invalid);
                this.aboveLagMode.setValue(LagBackMode.Smart);
            } else if (this.originalRotate != null) {
                this.rotate.setValue(this.originalRotate);
                this.lagMode.setValue(this.originalLagMode);
                this.aboveLagMode.setValue(this.originalAboveLagMode);
                this.originalRotate = null;
                this.originalLagMode = null;
                this.originalAboveLagMode = null;
            }
        }

        // AntiPacket模块兼容
        if (this.antiAntiPacket.getValue()) {
            if (hasSlowFalling) {
                if (this.originalAntiPacketS2CRotate == null && AntiPacket.INSTANCE != null) {
                    this.originalAntiPacketS2CRotate = AntiPacket.INSTANCE.s2CRotate.getValue();
                    AntiPacket.INSTANCE.s2CRotate.setValue(false);
                }
            } else if (this.originalAntiPacketS2CRotate != null && AntiPacket.INSTANCE != null) {
                AntiPacket.INSTANCE.s2CRotate.setValue(this.originalAntiPacketS2CRotate);
                this.originalAntiPacketS2CRotate = null;
            }
        }

        if (this.antiLag.getValue() && !BlockUtil.canCollide(mc.player, new Box(EntityUtil.getPlayerPos(true).down()))) {
            return;
        }

        if (this.single.getValue() && EntityUtil.isInsideBlock()) {
            // Disable为false时的逻辑：不自动禁用模块，只是return
            return;
        }

        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        int block;
        if ((block = this.getBlock()) == -1) {
            CommandManager.sendMessageId("§4No block found.", this.hashCode() - 1);
            // Disable为false时的逻辑：找不到方块时也不禁用模块
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

        // HeadFill为false时的逻辑：不进行头部填充
        boolean headFill = false; // 始终为false，因为HeadFill设置被删除了
        if (!this.canPlace(pos1) && !this.canPlace(pos2) && !this.canPlace(pos3) && !this.canPlace(pos4)) {
            boolean cantDown = !this.down.getValue() || !this.canPlace(pos9) && !this.canPlace(pos10) && !this.canPlace(pos11) && !this.canPlace(pos12);
            if (cantDown) {
                // Wait为true且Disable为false时的逻辑：只是return，不禁用
                return;
            }
        }

        boolean above = false;
        BlockPos headPos = EntityUtil.getPlayerPos(true).up(2);
        boolean rotate = this.rotate.getValue() == BurrowSkill.RotateMode.Normal;
        CombatUtil.attackCrystal(pos1, rotate, false);
        CombatUtil.attackCrystal(pos2, rotate, false);
        CombatUtil.attackCrystal(pos3, rotate, false);
        CombatUtil.attackCrystal(pos4, rotate, false);
        if (!headFill
                && !mc.player.isCrawling()
                && !this.trapped(headPos)
                && !this.trapped(headPos.add(1, 0, 0))
                && !this.trapped(headPos.add(-1, 0, 0))
                && !this.trapped(headPos.add(0, 0, 1))
                && !this.trapped(headPos.add(0, 0, -1))
                && !this.trapped(headPos.add(1, 0, -1))
                && !this.trapped(headPos.add(-1, 0, -1))
                && !this.trapped(headPos.add(1, 0, 1))
                && !this.trapped(headPos.add(-1, 0, 1))) {
            mc.getNetworkHandler()
                    .sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false)
                    );
            mc.getNetworkHandler()
                    .sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false)
                    );
            mc.getNetworkHandler()
                    .sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.9999957640154541, mc.player.getZ(), false)
                    );
            mc.getNetworkHandler()
                    .sendPacket(
                            new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ(), false)
                    );
            this.currentPos = new Vec3d(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ());
        } else {
            above = true;
            if (!this.fakeMove.getValue()) {
                return;
            }

            if (!this.fakeMove()) {
                return;
            }
        }

        this.timer.reset();
        this.doSwap(block);
        if (this.rotate.getValue() == BurrowSkill.RotateMode.Bypass) {
            if (above) {
                float[] angle = RotationManager.getRotation(
                        this.currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), mc.player.getPos()
                );
                Alien.ROTATION.snapAt(angle[0], angle[1]);
            } else {
                Alien.ROTATION.snapAt(Alien.ROTATION.rotationYaw, 90.0F);
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

        // HeadFill为false时的逻辑：不进行头部填充（不放置pos5-pos8）

        if (this.inventory.getValue()) {
            this.doSwap(block);
            EntityUtil.syncInventory();
        } else {
            this.doSwap(oldSlot);
        }

        switch (above ? (BurrowSkill.LagBackMode)this.aboveLagMode.getValue() : (BurrowSkill.LagBackMode)this.lagMode.getValue()) {
            case Smart:
                ArrayList<BlockPos> list = new ArrayList();

                for (double x = mc.player.getPos().getX() - this.smartX.getValue();
                     x < mc.player.getPos().getX() + this.smartX.getValue();
                     x++
                ) {
                    for (double z = mc.player.getPos().getZ() - this.smartX.getValue();
                         z < mc.player.getPos().getZ() + this.smartX.getValue();
                         z++
                    ) {
                        for (double y = mc.player.getPos().getY() - this.smartDown.getValue();
                             y < mc.player.getPos().getY() + this.smartUp.getValue();
                             y++
                        ) {
                            list.add(new BlockPosX(x, y, z));
                        }
                    }
                }

                double distance = 0.0;
                BlockPos bestPos = null;

                for (BlockPos pos : list) {
                    if (this.canMove(pos)
                            && !(MathHelper.sqrt((float)mc.player.squaredDistanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0))) < this.smartDistance.getValue())
                            && (bestPos == null || mc.player.squaredDistanceTo(pos.toCenterPos()) < distance)) {
                        bestPos = pos;
                        distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                    }
                }

                if (bestPos != null) {
                    mc.getNetworkHandler()
                            .sendPacket(new PositionAndOnGround(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false));
                }
                break;
            case Invalid:
                for (int i = 0; i < 20; i++) {
                    mc.getNetworkHandler()
                            .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1337.0, mc.player.getZ(), false));
                }
                break;
            case TrollHack:
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false)
                        );
                break;
            case ToVoid:
                mc.getNetworkHandler().sendPacket(new PositionAndOnGround(mc.player.getX(), -70.0, mc.player.getZ(), false));
                break;
            case ToVoid2:
                mc.getNetworkHandler().sendPacket(new PositionAndOnGround(mc.player.getX(), -7.0, mc.player.getZ(), false));
                break;
            case Normal:
                mc.getNetworkHandler()
                        .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ(), false));
                break;
            case Rotation:
                mc.getNetworkHandler().sendPacket(new LookAndOnGround(-180.0F, -90.0F, false));
                mc.getNetworkHandler().sendPacket(new LookAndOnGround(180.0F, 90.0F, false));
                break;
            case Fly:
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), false)
                        );
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.170005801788139, mc.player.getZ(), false)
                        );
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.2426308013947485, mc.player.getZ(), false)
                        );
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false)
                        );
                mc.getNetworkHandler()
                        .sendPacket(
                                new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.640088003576279, mc.player.getZ(), false)
                        );
                break;
            case Glide:
                mc.getNetworkHandler()
                        .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false));
                mc.getNetworkHandler()
                        .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false));
                mc.getNetworkHandler()
                        .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false));
                mc.getNetworkHandler()
                        .sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false));
        }

        // Disable为false时的逻辑：执行完毕后不自动禁用模块
    }

    private boolean fakeMove() {
        double[] offsets = new double[]{1.0, 0.0, -1.0};
        List<BlockPos> offList = new ArrayList();
        BlockPos playerPos = mc.player.getBlockPos();

        for (double x : offsets) {
            for (double z : offsets) {
                offList.add(new BlockPosX(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z));
            }
        }

        for (BlockPos offPos : offList) {
            // HeadFill为false时的逻辑：只检查!BlockUtil.canReplace(offPos)
            if (this.checkSelf(offPos) && !BlockUtil.canReplace(offPos)) {
                this.gotoPos(offPos);
                return true;
            }
        }

        List<BlockPos> pos = new ArrayList();

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
                offList.add(new BlockPosX(mc.player.getX(), mc.player.getZ(), mc.player.getZ() + 1.0));

                for (BlockPos offPosxxx : offList) {
                    if (this.canMove(offPosxxx)) {
                        this.gotoPos(offPosxxx);
                        return true;
                    }
                }

                return false;
            }
        }
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
        Vec3d directionVec = new Vec3d(
                pos.getX() + 0.5 + side.getVector().getX() * 0.5,
                pos.getY() + 0.5 + side.getVector().getY() * 0.5,
                pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        if (rotate) {
            float[] angle = RotationManager.getRotation(this.currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), directionVec);
            Alien.ROTATION.snapAt(angle[0], angle[1]);
        }

        EntityUtil.swingHand(hand, (SwingSide)AntiCheat.INSTANCE.interactSwing.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(hand, result, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }

        if (rotate) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

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
        if (currentBlockType == null) {
            return -1;
        }

        Block targetBlock = null;

        switch (currentBlockType) {
            case ICE:
                targetBlock = Blocks.ICE;
                break;
            case CRYING_OBSIDIAN:
                targetBlock = Blocks.CRYING_OBSIDIAN;
                break;
            case ANCIENT_DEBRIS:
                targetBlock = Blocks.ANCIENT_DEBRIS;
                break;
        }

        if (targetBlock == null) {
            return -1;
        }

        if (this.inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(targetBlock);
        } else {
            return InventoryUtil.findBlock(targetBlock);
        }
    }

    // 页面枚举
    public static enum Page {
        General,
        Blocks;
    }

    // 其他枚举保持不变
    private static enum LagBackMode {
        Smart,
        Invalid,
        TrollHack,
        ToVoid,
        ToVoid2,
        Normal,
        Rotation,
        Fly,
        Glide;
    }

    private static enum RotateMode {
        Bypass,
        Normal,
        None;
    }
}