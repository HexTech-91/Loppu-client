package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.entity.PlayerEntityPredict;
import dev.luminous.api.utils.math.AnimateUtil;
import dev.luminous.api.utils.math.ExplosionUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.impl.player.AirPlace;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class AAPro extends Module {
    public static AAPro INSTANCE;
    public static BlockPos currentAnchorPos;
    static Vec3d placeVec3d;
    static Vec3d curVec3d;
    public final EnumSetting<Page> page = this.add(new EnumSetting<>("Page", Page.General));
    public final SliderSetting range = this.add(new SliderSetting("Range", 5.0, 0.0, 6.0, 0.1, () -> this.page.getValue() == Page.General).setSuffix("m"));
    public final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 8.0, 0.1, 12.0, 0.1, () -> this.page.getValue() == Page.General).setSuffix("m"));
    public final SliderSetting minDamage = this.add(new SliderSetting("Min", 4.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Interact).setSuffix("dmg"));
    public final SliderSetting breakMin = this.add(new SliderSetting("ExplosionMin", 4.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Interact).setSuffix("dmg"));
    public final SliderSetting headDamage = this.add(new SliderSetting("ForceHead", 7.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Interact).setSuffix("dmg"));
    private final SliderSetting selfPredict = this.add(new SliderSetting("SelfPredict", 4, 0, 10, () -> this.page.getValue() == Page.Predict).setSuffix("ticks"));
    private final SliderSetting predictTicks = this.add(new SliderSetting("Predict", 4, 0, 10, () -> this.page.getValue() == Page.Predict).setSuffix("ticks"));
    private final SliderSetting simulation = this.add(new SliderSetting("Simulation", 5.0, 0.0, 20.0, 1.0, () -> this.page.getValue() == Page.Predict));
    private final SliderSetting maxMotionY = this.add(new SliderSetting("MaxMotionY", 0.34, 0.0, 2.0, 0.01, () -> this.page.getValue() == Page.Predict));
    private final BooleanSetting step = this.add(new BooleanSetting("Step", false, () -> this.page.getValue() == Page.Predict));
    private final BooleanSetting doubleStep = this.add(new BooleanSetting("DoubleStep", false, () -> this.page.getValue() == Page.Predict));
    private final BooleanSetting jump = this.add(new BooleanSetting("Jump", false, () -> this.page.getValue() == Page.Predict));
    private final BooleanSetting inBlockPause = this.add(new BooleanSetting("InBlockPause", true, () -> this.page.getValue() == Page.Predict));
    final ArrayList<BlockPos> chargeList = new ArrayList<>();
    private final BooleanSetting assist = this.add(new BooleanSetting("Assist", true, () -> this.page.getValue() == Page.Assist));
    private final BooleanSetting obsidian = this.add(new BooleanSetting("Obsidian", true, () -> this.page.getValue() == Page.Assist));
    private final BooleanSetting checkMine = this.add(new BooleanSetting("DetectMining", false, () -> this.page.getValue() == Page.Assist));
    private final SliderSetting assistRange = this.add(new SliderSetting("AssistRange", 5.0, 0.0, 6.0, 0.1, () -> this.page.getValue() == Page.Assist).setSuffix("m"));
    private final SliderSetting assistDamage = this.add(new SliderSetting("AssistDamage", 6.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Assist).setSuffix("h"));
    private final SliderSetting delay = this.add(new SliderSetting("AssistDelay", 0.1, 0.0, 1.0, 0.01, () -> this.page.getValue() == Page.Assist).setSuffix("s"));
    private final BooleanSetting preferCrystal = this.add(new BooleanSetting("PreferCrystal", false, () -> this.page.getValue() == Page.General));
    private final BooleanSetting thread = this.add(new BooleanSetting("Thread", false, () -> this.page.getValue() == Page.General));
    private final BooleanSetting light = this.add(new BooleanSetting("LessCPU", true, () -> this.page.getValue() == Page.General));
    private final BooleanSetting inventorySwap = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.getValue() == Page.General));
    private final BooleanSetting breakCrystal = this.add(new BooleanSetting("BreakCrystal", true, () -> this.page.getValue() == Page.General).setParent());
    private final BooleanSetting spam = this.add(new BooleanSetting("Spam", true, () -> this.page.getValue() == Page.General).setParent());
    private final BooleanSetting mineSpam = this.add(new BooleanSetting("OnlyMining", true, () -> this.page.getValue() == Page.General && this.spam.isOpen()));
    private final BooleanSetting spamPlace = this.add(new BooleanSetting("Fast", true, () -> this.page.getValue() == Page.General).setParent());
    private final BooleanSetting inSpam = this.add(new BooleanSetting("WhenSpamming", true, () -> this.page.getValue() == Page.General && this.spamPlace.isOpen()));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true, () -> this.page.getValue() == Page.General));
    private final EnumSetting<SwingSide> swingMode = this.add(new EnumSetting<>("Swing", SwingSide.All, () -> this.page.getValue() == Page.General));
    private final EnumSetting<Timing> timing = this.add(new EnumSetting<>("Timing", Timing.All, () -> this.page.getValue() == Page.General));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 100.0, 0.0, 500.0, 1.0, () -> this.page.getValue() == Page.General).setSuffix("ms"));
    private final SliderSetting fillDelay = this.add(new SliderSetting("FillDelay", 100.0, 0.0, 500.0, 1.0, () -> this.page.getValue() == Page.General).setSuffix("ms"));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 100.0, 0.0, 500.0, 1.0, () -> this.page.getValue() == Page.General).setSuffix("ms"));
    private final SliderSetting spamDelay = this.add(new SliderSetting("SpamDelay", 200.0, 0.0, 1000.0, 1.0, () -> this.page.getValue() == Page.General).setSuffix("ms"));
    private final SliderSetting updateDelay = this.add(new SliderSetting("UpdateDelay", 200.0, 0.0, 1000.0, 1.0, () -> this.page.getValue() == Page.General).setSuffix("ms"));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.getValue() == Page.Rotate).setParent());
    private final BooleanSetting yawStep = this.add(new BooleanSetting("YawStep", true, () -> this.rotate.isOpen() && this.page.getValue() == Page.Rotate).setParent());
    private final BooleanSetting whenElytra = this.add(new BooleanSetting("FallFlying", true, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.page.getValue() == Page.Rotate));
    private final SliderSetting steps = this.add(new SliderSetting("Steps", 0.05, 0.0, 1.0, 0.01, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.page.getValue() == Page.Rotate));
    private final BooleanSetting checkFov = this.add(new BooleanSetting("OnlyLooking", true, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.page.getValue() == Page.Rotate));
    private final SliderSetting fov = this.add(new SliderSetting("Fov", 20.0, 0.0, 360.0, 0.1, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.checkFov.getValue() && this.page.getValue() == Page.Rotate));
    private final SliderSetting priority = this.add(new SliderSetting("Priority", 10, 0, 100, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.page.getValue() == Page.Rotate));
    private final BooleanSetting noSuicide = this.add(new BooleanSetting("NoSuicide", true, () -> this.page.getValue() == Page.Interact));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, () -> this.page.getValue() == Page.Interact));
    private final BooleanSetting terrainIgnore = this.add(new BooleanSetting("TerrainIgnore", true, () -> this.page.getValue() == Page.Interact));
    private final SliderSetting minPrefer = this.add(new SliderSetting("Prefer", 7.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Interact).setSuffix("dmg"));
    private final SliderSetting maxSelfDamage = this.add(new SliderSetting("MaxSelf", 8.0, 0.0, 36.0, 0.1, () -> this.page.getValue() == Page.Interact).setSuffix("dmg"));
    private final EnumSetting<Aura.TargetESP> mode = this.add(new EnumSetting<>("TargetESP", Aura.TargetESP.Jello, () -> this.page.getValue() == Page.Render));
    private final ColorSetting color = this.add(new ColorSetting("TargetColor", new Color(255, 255, 255, 250), () -> this.page.getValue() == Page.Render));
    private final ColorSetting outlineColor = this.add(new ColorSetting("TargetOutlineColor", new Color(255, 255, 255, 250), () -> this.page.getValue() == Page.Render));
    private final BooleanSetting render = this.add(new BooleanSetting("Render", true, () -> this.page.getValue() == Page.Render));
    private final BooleanSetting shrink = this.add(new BooleanSetting("Shrink", true, () -> this.page.getValue() == Page.Render && this.render.getValue()));
    private final ColorSetting box = this.add(new ColorSetting("Box", new Color(255, 255, 255, 255), () -> this.page.getValue() == Page.Render && this.render.getValue()).injectBoolean(true));
    private final ColorSetting fill = this.add(new ColorSetting("Fill", new Color(255, 255, 255, 100), () -> this.page.getValue() == Page.Render && this.render.getValue()).injectBoolean(true));
    private final SliderSetting sliderSpeed = this.add(new SliderSetting("SliderSpeed", 0.2, 0.0, 1.0, 0.01, () -> this.page.getValue() == Page.Render && this.render.getValue()));
    private final SliderSetting startFadeTime = this.add(new SliderSetting("StartFade", 0.3, 0.0, 2.0, 0.01, () -> this.page.getValue() == Page.Render && this.render.getValue()).setSuffix("s"));
    private final SliderSetting fadeSpeed = this.add(new SliderSetting("FadeSpeed", 0.2, 0.01, 1.0, 0.01, () -> this.page.getValue() == Page.Render && this.render.getValue()));
    private final Timer delayTimer = new Timer();
    private final Timer calcTimer = new Timer();
    private final Timer noPosTimer = new Timer();
    private final Timer assistTimer = new Timer();
    public Vec3d directionVec = null;
    public PlayerEntity displayTarget;
    public BlockPos currentPos;
    public BlockPos tempPos;
    public double lastDamage;
    double fade = 0.0;
    BlockPos assistPos;

    public AAPro() {

        super("AAPro", Module.Category.Combat);

        this.setChinese("随心锚点");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new AnchorRender());
    }

    /**
     * 检查指定位置是否与其他 nagisa 功能冲突
     */
    public static boolean isConflictWithOthers(BlockPos pos) {
        if (SmartDigger.INSTANCE.isOn() && SmartDigger.INSTANCE.currentMineTarget != null
                && pos.equals(SmartDigger.INSTANCE.currentMineTarget)) {
            return true;
        }
        if (HoleKick.INSTANCE.isOn() && HoleKick.INSTANCE.currentTargetPos != null
                && pos.equals(HoleKick.INSTANCE.currentTargetPos)) {
            return true;
        }
        if (ACPro.tempPos != null && pos.equals(ACPro.tempPos)) {
            return true;
        }
        if (ACPro.breakPos != null && pos.equals(ACPro.breakPos)) {
            return true;
        }
        return false;
    }

    public static boolean isMaceActive() {
        return (GroundMace.INSTANCE != null && GroundMace.INSTANCE.isOn())
                || (TPMace.INSTANCE != null && TPMace.INSTANCE.isOn())
                || (VoidMace.INSTANCE != null && VoidMace.INSTANCE.isOn())
                || (MaceAura.INSTANCE != null && MaceAura.INSTANCE.isOn());
    }

    public static boolean canSee(Vec3d from, Vec3d to) {
        BlockHitResult result = AAPro.mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, AAPro.mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    @Override
    public String getInfo() {
        if (this.displayTarget != null && this.currentPos != null) {
            return this.displayTarget.getName().getString();
        }
        return null;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (this.displayTarget != null && this.currentPos != null) {
            Aura.doRender(matrixStack, mc.getRenderTickCounter().getTickDelta(true), this.displayTarget, this.color.getValue(), this.outlineColor.getValue(), this.mode.getValue());
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (this.currentPos != null && this.rotate.getValue() && this.shouldYawStep() && this.directionVec != null) {
            event.setTarget(this.directionVec, this.steps.getValueFloat(), this.priority.getValueFloat());
        }
    }

    @Override
    public void onDisable() {
        this.tempPos = null;
        this.currentPos = null;
        currentAnchorPos = null;
    }

    public void onThread() {
        if (this.isOff() || AAPro.nullCheck()) {
            return;
        }
        // 下界重生锚只能设置重生点，无法引爆，模块自动失效
        if (mc.world.getRegistryKey() == World.NETHER) {
            return;
        }
        if (this.thread.getValue()) {
            if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            if (AutoCrystal.INSTANCE.isOn() && AutoCrystal.INSTANCE.crystalPos != null && this.preferCrystal.getValue()) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            int anchor = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.RESPAWN_ANCHOR) : InventoryUtil.findBlock(Blocks.RESPAWN_ANCHOR);
            int glowstone = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.GLOWSTONE) : InventoryUtil.findBlock(Blocks.GLOWSTONE);
            int unBlock = this.inventorySwap.getValue() ? anchor : InventoryUtil.findUnBlock();
            if (anchor == -1) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            if (glowstone == -1) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            if (unBlock == -1) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            if (AAPro.mc.player.isSneaking()) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            if (this.usingPause.getValue() && AAPro.mc.player.isUsingItem()) {
                this.currentPos = null;
                currentAnchorPos = null;
                return;
            }
            this.calc();
        }
    }

    private boolean shouldYawStep() {
        if (!this.whenElytra.getValue() && (AAPro.mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying())) {
            return false;
        }
        return this.yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        BlockPos pos;
        if (AAPro.nullCheck()) {
            return;
        }
        // 下界重生锚只能设置重生点，无法引爆，模块自动失效
        if (mc.world.getRegistryKey() == World.NETHER) {
            return;
        }
        if (isMaceActive()) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (this.timing.is(Timing.Pre) && event.isPost() || this.timing.is(Timing.Post) && event.isPre()) {
            return;
        }
        int anchor = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.RESPAWN_ANCHOR) : InventoryUtil.findBlock(Blocks.RESPAWN_ANCHOR);
        int glowstone = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.GLOWSTONE) : InventoryUtil.findBlock(Blocks.GLOWSTONE);
        int unBlock = this.inventorySwap.getValue() ? anchor : InventoryUtil.findUnBlock();
        int old = AAPro.mc.player.getInventory().selectedSlot;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (AutoCrystal.INSTANCE.isOn() && AutoCrystal.INSTANCE.crystalPos != null) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (anchor == -1) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (glowstone == -1) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (unBlock == -1) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (AAPro.mc.player.isSneaking()) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (this.usingPause.getValue() && AAPro.mc.player.isUsingItem()) {
            this.currentPos = null;
            currentAnchorPos = null;
            return;
        }
        if (this.inventorySwap.getValue() && !EntityUtil.inInventory()) {
            return;
        }
        if (this.assist.getValue()) {
            this.onAssist();
        }
        if (!this.thread.getValue()) {
            this.calc();
        }
        if ((pos = this.currentPos) != null) {
            boolean shouldSpam;
            if (this.breakCrystal.getValue()) {
                CombatUtil.attackCrystal(new BlockPos(pos), this.rotate.getValue(), false);
            }
            boolean bl = shouldSpam = this.spam.getValue() && (!this.mineSpam.getValue() || Alien.BREAK.isMining(pos));
            if (shouldSpam) {
                if (!this.delayTimer.passed((long) this.spamDelay.getValueFloat())) {
                    return;
                }
                this.delayTimer.reset();
                if (BlockUtil.canPlace(pos, this.range.getValue(), this.breakCrystal.getValue())) {
                    this.placeBlock(pos, this.rotate.getValue(), anchor);
                }
                if (!this.chargeList.contains(pos)) {
                    this.delayTimer.reset();
                    this.clickBlock(pos, BlockUtil.getClickSide(pos), this.rotate.getValue(), glowstone);
                    this.chargeList.add(pos);
                }
                this.chargeList.remove(pos);
                this.clickBlock(pos, BlockUtil.getClickSide(pos), this.rotate.getValue(), unBlock);
                if (this.spamPlace.getValue() && this.inSpam.getValue()) {
                    if (this.shouldYawStep() && this.checkFov.getValue()) {
                        Direction side = BlockUtil.getClickSide(pos);
                        Vec3d directionVec = new Vec3d((double) pos.getX() + 0.5 + (double) side.getVector().getX() * 0.5, (double) pos.getY() + 0.5 + (double) side.getVector().getY() * 0.5, (double) pos.getZ() + 0.5 + (double) side.getVector().getZ() * 0.5);
                        if (Alien.ROTATION.inFov(directionVec, this.fov.getValueFloat())) {
                            CombatUtil.modifyPos = pos;
                            CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                            this.placeBlock(pos, this.rotate.getValue(), anchor);
                            CombatUtil.modifyPos = null;
                        }
                    } else {
                        CombatUtil.modifyPos = pos;
                        CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                        this.placeBlock(pos, this.rotate.getValue(), anchor);
                        CombatUtil.modifyPos = null;
                    }
                }
            } else if (BlockUtil.canPlace(pos, this.range.getValue(), this.breakCrystal.getValue())) {
                if (!this.delayTimer.passed((long) this.placeDelay.getValueFloat())) {
                    return;
                }
                this.delayTimer.reset();
                this.placeBlock(pos, this.rotate.getValue(), anchor);
            } else if (BlockUtil.getBlock(pos) == Blocks.RESPAWN_ANCHOR) {
                if (!this.chargeList.contains(pos)) {
                    if (!this.delayTimer.passed((long) this.fillDelay.getValueFloat())) {
                        return;
                    }
                    this.delayTimer.reset();
                    this.clickBlock(pos, BlockUtil.getClickSide(pos), this.rotate.getValue(), glowstone);
                    this.chargeList.add(pos);
                } else {
                    if (!this.delayTimer.passed((long) this.breakDelay.getValueFloat())) {
                        return;
                    }
                    this.delayTimer.reset();
                    this.chargeList.remove(pos);
                    this.clickBlock(pos, BlockUtil.getClickSide(pos), this.rotate.getValue(), unBlock);
                    if (this.spamPlace.getValue()) {
                        if (this.shouldYawStep() && this.checkFov.getValue()) {
                            Direction side = BlockUtil.getClickSide(pos);
                            Vec3d directionVec = new Vec3d((double) pos.getX() + 0.5 + (double) side.getVector().getX() * 0.5, (double) pos.getY() + 0.5 + (double) side.getVector().getY() * 0.5, (double) pos.getZ() + 0.5 + (double) side.getVector().getZ() * 0.5);
                            if (Alien.ROTATION.inFov(directionVec, this.fov.getValueFloat())) {
                                CombatUtil.modifyPos = pos;
                                CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                                this.placeBlock(pos, this.rotate.getValue(), anchor);
                                CombatUtil.modifyPos = null;
                            }
                        } else {
                            CombatUtil.modifyPos = pos;
                            CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                            this.placeBlock(pos, this.rotate.getValue(), anchor);
                            CombatUtil.modifyPos = null;
                        }
                    }
                }
            }
            if (!this.inventorySwap.getValue()) {
                this.doSwap(old);
            }
        }
    }

    private void calc() {
        if (AAPro.nullCheck()) {
            return;
        }
        if (this.calcTimer.passed((long) this.updateDelay.getValueFloat())) {
            double damage;
            this.calcTimer.reset();
            PlayerEntityPredict selfPredict = new PlayerEntityPredict(AAPro.mc.player, this.maxMotionY.getValue(), this.selfPredict.getValueInt(), this.simulation.getValueInt(), this.step.getValue(), this.doubleStep.getValue(), this.jump.getValue(), this.inBlockPause.getValue());
            this.tempPos = null;
            double placeDamage = this.minDamage.getValue();
            double breakDamage = this.breakMin.getValue();
            boolean anchorFound = false;
            List<PlayerEntity> enemies = CombatUtil.getEnemies(this.targetRange.getValue());
            ArrayList<PlayerEntityPredict> list = new ArrayList<>();
            for (PlayerEntity player : enemies) {
                list.add(new PlayerEntityPredict(player, this.maxMotionY.getValue(), this.predictTicks.getValueInt(), this.simulation.getValueInt(), this.step.getValue(), this.doubleStep.getValue(), this.jump.getValue(), this.inBlockPause.getValue()));
            }
            for (PlayerEntityPredict pap : list) {
                double selfDamage;
                BlockPos pos = EntityUtil.getEntityPos(pap.player, true).up(2);
                if (!BlockUtil.canPlace(pos, this.range.getValue(), this.breakCrystal.getValue()) && (BlockUtil.getBlock(pos) != Blocks.RESPAWN_ANCHOR || BlockUtil.getClickSideStrict(pos) == null) || (selfDamage = this.getAnchorDamage(pos, selfPredict.player, selfPredict.predict)) > this.maxSelfDamage.getValue() || this.noSuicide.getValue() && selfDamage > (double) (AAPro.mc.player.getHealth() + AAPro.mc.player.getAbsorptionAmount())) continue;
                damage = this.getAnchorDamage(pos, pap.player, pap.predict);
                if (!(damage > (double) this.headDamage.getValueFloat()) || this.smart.getValue() && selfDamage > damage) continue;
                this.lastDamage = damage;
                this.displayTarget = pap.player;
                this.tempPos = pos;
                break;
            }
            if (this.tempPos == null) {
                for (BlockPos pos : BlockUtil.getSphere(this.range.getValueFloat() + 1.0f, AAPro.mc.player.getEyePos())) {
                    for (PlayerEntityPredict pap : list) {
                        double selfDamage;
                        boolean skip;
                        if (this.light.getValue()) {
                            CombatUtil.modifyPos = pos;
                            CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                            skip = !AAPro.canSee(pos.toCenterPos(), pap.predict.getPos());
                            CombatUtil.modifyPos = null;
                            if (skip) continue;
                        }
                        if (isConflictWithOthers(pos)) {
                            continue;
                        }
                        if (BlockUtil.getBlock(pos) != Blocks.RESPAWN_ANCHOR) {
                            double selfDamage2;
                            if (anchorFound || !BlockUtil.canPlace(pos, this.range.getValue(), this.breakCrystal.getValue())) continue;
                            CombatUtil.modifyPos = pos;
                            CombatUtil.modifyBlockState = Blocks.OBSIDIAN.getDefaultState();
                            skip = BlockUtil.getClickSideStrict(pos) == null;
                            CombatUtil.modifyPos = null;
                            if (skip || !((damage = this.getAnchorDamage(pos, pap.player, pap.predict)) >= placeDamage) || AutoCrystal.INSTANCE.crystalPos != null && !AutoCrystal.INSTANCE.isOff() && !((double) AutoCrystal.INSTANCE.lastDamage < damage) || (selfDamage2 = this.getAnchorDamage(pos, selfPredict.player, selfPredict.predict)) > this.maxSelfDamage.getValue() || this.noSuicide.getValue() && selfDamage2 > (double) (AAPro.mc.player.getHealth() + AAPro.mc.player.getAbsorptionAmount()) || this.smart.getValue() && selfDamage2 > damage) continue;
                            this.lastDamage = damage;
                            this.displayTarget = pap.player;
                            placeDamage = damage;
                            this.tempPos = pos;
                            continue;
                        }
                        double damage2 = this.getAnchorDamage(pos, pap.player, pap.predict);
                        if (BlockUtil.getClickSideStrict(pos) == null || !(damage2 >= breakDamage)) continue;
                        if (damage2 >= this.minPrefer.getValue()) {
                            anchorFound = true;
                        }
                        if (!anchorFound && damage2 < placeDamage || AutoCrystal.INSTANCE.crystalPos != null && !AutoCrystal.INSTANCE.isOff() && !((double) AutoCrystal.INSTANCE.lastDamage < damage2) || (selfDamage = this.getAnchorDamage(pos, selfPredict.player, selfPredict.predict)) > this.maxSelfDamage.getValue() || this.noSuicide.getValue() && selfDamage > (double) (AAPro.mc.player.getHealth() + AAPro.mc.player.getAbsorptionAmount()) || this.smart.getValue() && selfDamage > damage2) continue;
                        this.lastDamage = damage2;
                        this.displayTarget = pap.player;
                        breakDamage = damage2;
                        this.tempPos = pos;
                    }
                }
            }
        }
        this.currentPos = this.tempPos;
        currentAnchorPos = this.currentPos;
    }

    public double getAnchorDamage(BlockPos anchorPos, PlayerEntity target, PlayerEntity predict) {
        if (this.terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        double damage = ExplosionUtil.anchorDamage(anchorPos, target, predict);
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    public void placeBlock(BlockPos pos, boolean rotate, int slot) {
        if (BlockUtil.allowAirPlace()) {
            this.airPlace(pos, rotate, slot);
            return;
        }
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) {
            return;
        }
        this.clickBlock(pos.offset(side), side.getOpposite(), rotate, slot);
    }

    public void clickBlock(BlockPos pos, Direction side, boolean rotate, int slot) {
        if (pos == null) {
            return;
        }
        Vec3d directionVec = new Vec3d((double) pos.getX() + 0.5 + (double) side.getVector().getX() * 0.5, (double) pos.getY() + 0.5 + (double) side.getVector().getY() * 0.5, (double) pos.getZ() + 0.5 + (double) side.getVector().getZ() * 0.5);
        if (rotate && !this.faceVector(directionVec)) {
            return;
        }
        this.doSwap(slot);
        EntityUtil.swingHand(Hand.MAIN_HAND, this.swingMode.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, id));
        if (this.inventorySwap.getValue()) {
            this.doSwap(slot);
        }
        if (rotate && !this.shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    public void airPlace(BlockPos pos, boolean rotate, int slot) {
        if (pos == null) {
            return;
        }
        Direction side = BlockUtil.getClickSide(pos);
        Vec3d directionVec = new Vec3d((double) pos.getX() + 0.5 + (double) side.getVector().getX() * 0.5, (double) pos.getY() + 0.5 + (double) side.getVector().getY() * 0.5, (double) pos.getZ() + 0.5 + (double) side.getVector().getZ() * 0.5);
        if (rotate && !this.faceVector(directionVec)) {
            return;
        }
        this.doSwap(slot);
        boolean bypass = AirPlace.INSTANCE.grimBypass.getValue();
        if (bypass) {
            mc.getNetworkHandler().sendPacket((Packet)new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0), Direction.DOWN));
        }
        EntityUtil.swingHand(Hand.MAIN_HAND, this.swingMode.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(bypass ? Hand.OFF_HAND : Hand.MAIN_HAND, result, id));
        if (bypass) {
            mc.getNetworkHandler().sendPacket((Packet)new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0), Direction.DOWN));
        }
        if (this.inventorySwap.getValue()) {
            this.doSwap(slot);
        }
        if (rotate && !this.shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doSwap(int slot) {
        if (this.inventorySwap.getValue()) {
            InventoryUtil.inventorySwap(slot, AAPro.mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    public boolean faceVector(Vec3d directionVec) {
        if (!this.shouldYawStep()) {
            Alien.ROTATION.lookAt(directionVec);
            return true;
        }
        this.directionVec = directionVec;
        if (Alien.ROTATION.inFov(directionVec, this.fov.getValueFloat())) {
            return true;
        }
        return !this.checkFov.getValue();
    }

    public void onAssist() {
        BlockPos placePos;
        this.assistPos = null;
        int anchor = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.RESPAWN_ANCHOR) : InventoryUtil.findBlock(Blocks.RESPAWN_ANCHOR);
        int glowstone = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.GLOWSTONE) : InventoryUtil.findBlock(Blocks.GLOWSTONE);
        int old = AAPro.mc.player.getInventory().selectedSlot;
        if (anchor == -1) {
            return;
        }
        if (this.obsidian.getValue()) {
            anchor = this.inventorySwap.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN) : InventoryUtil.findBlock(Blocks.OBSIDIAN);
            if (anchor == -1) {
                return;
            }
        }
        if (glowstone == -1) {
            return;
        }
        if (AAPro.mc.player.isSneaking()) {
            return;
        }
        if (this.usingPause.getValue() && AAPro.mc.player.isUsingItem()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        if (!this.assistTimer.passed((long) (this.delay.getValueFloat() * 1000.0f))) {
            return;
        }
        this.assistTimer.reset();
        ArrayList<PlayerEntityPredict> list = new ArrayList<>();
        for (PlayerEntity player : CombatUtil.getEnemies(this.assistRange.getValue())) {
            list.add(new PlayerEntityPredict(player, this.maxMotionY.getValue(), this.predictTicks.getValueInt(), this.simulation.getValueInt(), this.step.getValue(), this.doubleStep.getValue(), this.jump.getValue(), this.inBlockPause.getValue()));
        }
        double bestDamage = this.assistDamage.getValue();
        for (PlayerEntityPredict pap : list) {
            double damage;
            BlockPos pos = EntityUtil.getEntityPos(pap.player, true).up(2);
            if (AAPro.mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR) {
                return;
            }
            if (BlockUtil.clientCanPlace(pos, false) && (damage = this.getAnchorDamage(pos, pap.player, pap.predict)) >= bestDamage) {
                bestDamage = damage;
                this.assistPos = pos;
            }
            for (Direction i : Direction.values()) {
                double damage2;
                if (i == Direction.UP || i == Direction.DOWN || !BlockUtil.clientCanPlace(pos.offset(i), false) || !((damage2 = this.getAnchorDamage(pos.offset(i), pap.player, pap.predict)) >= bestDamage)) continue;
                bestDamage = damage2;
                this.assistPos = pos.offset(i);
            }
        }
        if (this.assistPos != null && BlockUtil.getPlaceSide(this.assistPos, this.range.getValue()) == null && (placePos = this.getHelper(this.assistPos)) != null) {
            this.doSwap(anchor);
            BlockUtil.placeBlock(placePos, this.rotate.getValue());
            if (this.inventorySwap.getValue()) {
                this.doSwap(anchor);
            } else {
                this.doSwap(old);
            }
        }
    }

    public BlockPos getHelper(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (this.checkMine.getValue() && Alien.BREAK.isMining(pos.offset(i)) || !BlockUtil.isStrictDirection(pos.offset(i), i.getOpposite()) || !BlockUtil.canPlace(pos.offset(i))) continue;
            return pos.offset(i);
        }
        return null;
    }

    public enum Page {
        General,
        Interact,
        Predict,
        Rotate,
        Assist,
        Render
    }

    public class AnchorRender {
        @EventListener
        public void onRender3D(Render3DEvent event) {
            BlockPos currentPos = AAPro.INSTANCE.currentPos;
            if (currentPos != null) {
                AAPro.this.noPosTimer.reset();
                placeVec3d = currentPos.toCenterPos();
            }
            if (placeVec3d == null) {
                return;
            }
            double target = AAPro.this.noPosTimer.passed((long) (AAPro.this.startFadeTime.getValue() * 1000.0)) ? 0.0 : 1.0;
            AAPro.this.fade = AAPro.this.fadeSpeed.getValue() >= 1.0 ? target : AnimateUtil.animate(AAPro.this.fade, target, AAPro.this.fadeSpeed.getValue() / 10.0);
            if (AAPro.this.fade < 0.01) {
                curVec3d = null;
                return;
            }
            curVec3d = curVec3d == null || AAPro.this.sliderSpeed.getValue() >= 1.0 ? placeVec3d : new Vec3d(AnimateUtil.animate(curVec3d.x, placeVec3d.x, AAPro.this.sliderSpeed.getValue() / 10.0), AnimateUtil.animate(curVec3d.y, placeVec3d.y, AAPro.this.sliderSpeed.getValue() / 10.0), AnimateUtil.animate(curVec3d.z, placeVec3d.z, AAPro.this.sliderSpeed.getValue() / 10.0));
            if (AAPro.this.render.getValue()) {
                Box cbox;
                if (AAPro.this.shrink.getValue()) {
                    double expand = 0.5 * AAPro.this.fade;
                    cbox = new Box(curVec3d.x - expand, curVec3d.y - expand, curVec3d.z - expand, curVec3d.x + expand, curVec3d.y + expand, curVec3d.z + expand);
                } else {
                    cbox = new Box(curVec3d.x - 0.5, curVec3d.y - 0.5, curVec3d.z - 0.5, curVec3d.x + 0.5, curVec3d.y + 0.5, curVec3d.z + 0.5);
                }
                if (AAPro.this.fill.booleanValue) {
                    event.drawFill(cbox, ColorUtil.injectAlpha(AAPro.this.fill.getValue(), (int) ((double) AAPro.this.fill.getValue().getAlpha() * AAPro.this.fade)));
                }
                if (AAPro.this.box.booleanValue) {
                    event.drawBox(cbox, ColorUtil.injectAlpha(AAPro.this.box.getValue(), (int) ((double) AAPro.this.box.getValue().getAlpha() * AAPro.this.fade)));
                }
            }
        }
    }
}