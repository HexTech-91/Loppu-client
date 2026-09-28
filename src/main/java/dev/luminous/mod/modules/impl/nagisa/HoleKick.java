package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.AutoTrap;
import dev.luminous.mod.modules.impl.player.AutoPot;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class HoleKick extends Module {
    public static HoleKick INSTANCE;

    private enum Page { General, Rotate, Check }
    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));

    // ========== General 页面 ==========
    private final BooleanSetting torch = add(new BooleanSetting("Torch", false, () -> page.getValue() == Page.General));
    private final BooleanSetting pistonPacket = add(new BooleanSetting("PistonPacket", true, () -> page.getValue() == Page.General));
    private final BooleanSetting powerPacket = add(new BooleanSetting("PowerPacket", true, () -> page.getValue() == Page.General));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true, () -> page.getValue() == Page.General));
    private final BooleanSetting helper = add(new BooleanSetting("Helper", true, () -> page.getValue() == Page.General));
    private final BooleanSetting mine = add(new BooleanSetting("Mine", true, () -> page.getValue() == Page.General));
    private final BooleanSetting pauseCity = add(new BooleanSetting("PauseCity", true, () -> mine.isOpen() && page.getValue() == Page.General));
    private final SliderSetting updateDelay = add(new SliderSetting("Delay", 100, 0, 1000, () -> page.getValue() == Page.General));
    private final SliderSetting range = add(new SliderSetting("Range", 5.0, 0.0, 6.0, () -> page.getValue() == Page.General));
    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0, () -> page.getValue() == Page.General));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true, () -> page.getValue() == Page.General));
    private final BooleanSetting preferPot = add(new BooleanSetting("PreferPot", true, () -> page.getValue() == Page.General));
    private final BooleanSetting preferTrap = add(new BooleanSetting("PreferTrap", true, () -> page.getValue() == Page.General));
    private final BooleanSetting syncCrystal = add(new BooleanSetting("SyncCrystal", true, () -> page.getValue() == Page.General));
    private final BooleanSetting detectMining = add(new BooleanSetting("DetectMining", false, () -> page.getValue() == Page.General));
    private final SliderSetting predictTicks = add(new SliderSetting("PredictTicks", 0.0, 0.0, 20.0, 1.0, () -> page.getValue() == Page.General));
    private final BooleanSetting force = add(new BooleanSetting("Force", false, () -> page.getValue() == Page.General));
    private final BooleanSetting face = add(new BooleanSetting("Face", true, () -> page.getValue() == Page.General));
    private final BooleanSetting feet = add(new BooleanSetting("Feet", true, () -> page.getValue() == Page.General));

    // ========== Rotate 页面 ==========
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true, () -> page.getValue() == Page.Rotate));
    private final BooleanSetting yawDeceive = add(new BooleanSetting("YawDeceive", true, () -> page.getValue() == Page.Rotate));

    // ========== Check 页面 ==========
    private final BooleanSetting noEating = add(new BooleanSetting("EatingPause", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting selfGround = add(new BooleanSetting("SelfGround", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", false, () -> page.getValue() == Page.Check));
    private final BooleanSetting allowWeb = add(new BooleanSetting("AllowWeb", true, () -> page.getValue() == Page.Check));
    public final BindSetting allowKey = add(new BindSetting("AllowKey", -1, () -> !allowWeb.getValue() && page.getValue() == Page.Check));
    private final BooleanSetting onlyNoInside = add(new BooleanSetting("OnlyNoInside", false, () -> page.getValue() == Page.Check));
    private final BooleanSetting onlyInside = add(new BooleanSetting("OnlyInside", true, () -> page.getValue() == Page.Check));
    private final SliderSetting surroundCheck = add(new SliderSetting("SurroundCheck", 2, 0, 4, () -> page.getValue() == Page.Check));
    private final BooleanSetting movePause = add(new BooleanSetting("MovePause", false, () -> page.getValue() == Page.Check));
    private final BooleanSetting ignoreCrawling = add(new BooleanSetting("IgnoreCrawling", false, () -> page.getValue() == Page.Check));

    private enum BurrowMode { Strict, Loose, Always }
    private final EnumSetting<BurrowMode> burrowMode = add(new EnumSetting<>("BurrowMode", BurrowMode.Loose, () -> page.getValue() == Page.Check));

    private static final float[] OFFSETS = {-0.25f, 0.0f, 0.25f};
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final Timer timer = new Timer();
    public PlayerEntity currentTarget = null;
    public BlockPos currentTargetPos = null;

    public HoleKick() {
        super("HoleKick", Category.Combat);
        setChinese("智能坑推");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (AutoCrystal.INSTANCE != null) AutoCrystal.INSTANCE.lastBreakTimer.reset();
        timer.reset();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (movePause.getValue() && isPlayerMoving()) { timer.reset(); return; }
        if (preferPot.getValue() && AutoPot.INSTANCE != null && AutoPot.INSTANCE.shouldThrowNow()) { timer.reset(); return; }
        if (preferTrap.getValue() && AutoTrap.INSTANCE != null && AutoTrap.INSTANCE.isOn() && AutoTrap.INSTANCE.target != null) { timer.reset(); return; }

        if (!timer.passedMs(updateDelay.getValue())) return;

        if (selfGround.getValue() && !mc.player.isOnGround()) return;
        Block blockType = getBlockType();
        if (findBlock(blockType) == -1 || findClass(PistonBlock.class) == -1) {
            if (autoDisable.getValue()) disable();
            return;
        }
        if (noEating.getValue() && mc.player.isUsingItem()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (syncCrystal.getValue() && AutoCrystal.INSTANCE != null && AutoCrystal.INSTANCE.crystalPos != null) return;

        List<PlayerEntity> enemies = CombatUtil.getEnemies(range.getValue());
        if (enemies.isEmpty()) { currentTarget = null; currentTargetPos = null; return; }

        for (PlayerEntity player : enemies) {
            currentTarget = player;
            currentTargetPos = player.getBlockPos();
            if (!canPush(player)) continue;

            Vec3d targetPos = player.getPos();
            Box targetBox = player.getBoundingBox();

            if (predictTicks.getValue() > 0) {
                Vec3d vel = player.getVelocity();
                targetPos = targetPos.add(vel.x * predictTicks.getValue(), 0.0, vel.z * predictTicks.getValue());
                targetBox = targetBox.offset(vel.x * predictTicks.getValue(), 0.0, vel.z * predictTicks.getValue());
            }

            if (tryPushFourBlockBurrowCenter(player, targetPos, targetBox)) { timer.reset(); return; }
            if (tryPushPlayer(player, targetPos, targetBox)) { timer.reset(); return; }
        }
        currentTarget = null;
        currentTargetPos = null;
    }

    private boolean isPlayerMoving() {
        return mc.player.input.pressingForward || mc.player.input.pressingBack ||
                mc.player.input.pressingLeft || mc.player.input.pressingRight;
    }

    private boolean tryPushPlayer(PlayerEntity player, Vec3d targetPos, Box targetBox) {
        boolean forceMode = force.getValue();

        for (float x : OFFSETS) {
            for (float z : OFFSETS) {
                BlockPosX playerPos = new BlockPosX(targetPos.x + x, targetPos.y + 0.5, targetPos.z + z);
                for (Direction dir : HORIZONTAL_DIRECTIONS) {
                    BlockPos pos = playerPos.offset(dir);
                    if (!isTargetHere(pos, targetBox)) continue;
                    if (!forceMode && !mc.world.canCollide(player, new Box(pos))) continue;
                    if (forceMode && !canSideEject(targetBox, dir)) continue;

                    if (tryPushAtHeight(playerPos.offset(dir.getOpposite()), dir, player, 0)) return true;
                    if (tryPushAtHeight(playerPos.offset(dir.getOpposite()).up(), dir, player, 1)) return true;
                }
            }
        }

        if (!mc.world.canCollide(player, new Box(new BlockPosX(targetPos.x, targetPos.y + 2.5, targetPos.z)))) {
            BlockPos playerBasePos = new BlockPosX(targetPos.x, targetPos.y, targetPos.z);
            for (Direction dir : HORIZONTAL_DIRECTIONS) {
                BlockPos pos = playerBasePos.offset(dir);
                Box box = targetBox.offset(new Vec3d(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()));
                if (getBlock(pos.up()) == Blocks.PISTON_HEAD) continue;
                if (mc.world.canCollide(player, box.offset(0, 1, 0))) continue;
                if (isTargetHere(pos, targetBox)) continue;
                if (forceMode && !canSideEject(targetBox, dir)) continue;

                if (tryPushAtHeight(playerBasePos.offset(dir.getOpposite()).up(), dir, player, 1)) return true;
                if (tryPushAtHeight(playerBasePos.offset(dir.getOpposite()), dir, player, 0)) return true;
            }
        }

        for (float x : OFFSETS) {
            for (float z : OFFSETS) {
                BlockPosX playerPos = new BlockPosX(targetPos.x + x, targetPos.y + 0.5, targetPos.z + z);
                for (Direction dir : HORIZONTAL_DIRECTIONS) {
                    BlockPos pos = playerPos.offset(dir);
                    if (!isTargetHere(pos, targetBox)) continue;
                    if (forceMode && !canSideEject(targetBox, dir)) continue;

                    if (tryPushAtHeight(playerPos.offset(dir.getOpposite()).up(), dir, player, 1)) return true;
                    if (tryPushAtHeight(playerPos.offset(dir.getOpposite()), dir, player, 0)) return true;
                }
            }
        }

        return false;
    }

    private boolean tryPushAtHeight(BlockPos pistonPos, Direction dir, PlayerEntity player, int heightFlag) {
        if ((heightFlag == 0 && !feet.getValue()) || (heightFlag == 1 && !face.getValue())) return false;
        return tryPush(pistonPos, dir);
    }

    private boolean canSideEject(Box targetBox, Direction dir) {
        Vec3d delta = new Vec3d(dir.getOffsetX(), 0.0, dir.getOffsetZ());
        return !mc.world.canCollide(null, targetBox.offset(delta));
    }

    private boolean tryPushFourBlockBurrowCenter(PlayerEntity player, Vec3d targetPos, Box targetBox) {
        double x = targetPos.x, z = targetPos.z;
        double fx = x - Math.floor(x);
        double fz = z - Math.floor(z);
        double dx = Math.min(fx, 1.0 - fx);
        double dz = Math.min(fz, 1.0 - fz);
        if (dx > 0.15 || dz > 0.15) return false;

        int cornerX = (int) Math.round(x);
        int cornerZ = (int) Math.round(z);
        int yFeet = new BlockPosX(targetPos.x, targetPos.y + 0.5, targetPos.z).getY();
        BlockPos p1 = new BlockPos(cornerX, yFeet, cornerZ);
        BlockPos p2 = new BlockPos(cornerX - 1, yFeet, cornerZ);
        BlockPos p3 = new BlockPos(cornerX, yFeet, cornerZ - 1);
        BlockPos p4 = new BlockPos(cornerX - 1, yFeet, cornerZ - 1);

        int inside = 0;
        if (isTargetHere(p1, targetBox) && isCollidableBlock(p1)) inside++;
        if (isTargetHere(p2, targetBox) && isCollidableBlock(p2)) inside++;
        if (isTargetHere(p3, targetBox) && isCollidableBlock(p3)) inside++;
        if (isTargetHere(p4, targetBox) && isCollidableBlock(p4)) inside++;
        if (inside < 3) return false;

        BlockPos[] bases = {p1, p2, p3, p4};
        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            if (!canSideEject(targetBox, dir)) continue;
            for (BlockPos base : bases) {
                BlockPos targetUp = base.up();
                if (isTargetHere(targetUp, targetBox) && mc.world.isAir(targetUp)) {
                    BlockPos pistonPos = targetUp.offset(dir.getOpposite());
                    if (tryPushAtHeight(pistonPos, dir, player, 1)) return true;
                    if (tryPushAtHeight(pistonPos.up(), dir, player, 1)) return true;
                }
                BlockPos targetUp2 = base.up(2);
                if (isTargetHere(targetUp2, targetBox) && mc.world.isAir(targetUp2)) {
                    BlockPos pistonPos = targetUp2.offset(dir.getOpposite());
                    if (tryPushAtHeight(pistonPos, dir, player, 1)) return true;
                    if (tryPushAtHeight(pistonPos.up(), dir, player, 1)) return true;
                }
            }
        }
        return false;
    }

    private boolean tryPush(BlockPos piston, Direction direction) {
        if (!mc.world.isAir(piston.offset(direction))) return false;

        if (detectMining.getValue() && Alien.BREAK.isMining(piston)) return false;

        boolean canPlace = BlockUtil.clientCanPlace(piston, false);
        if (!canPlace && helper.getValue()) {
            BlockPos helperPos = getHelper(piston);
            if (helperPos != null) {
                if (detectMining.getValue() && Alien.BREAK.isMining(helperPos)) return false;
                tryPlaceObsidian(helperPos);
                canPlace = BlockUtil.clientCanPlace(piston, false);
            }
        }

        if (isTrueFacing(piston, direction) && facingCheck(piston) && canPlace) {
            Block blockType = getBlockType();
            boolean hasPower = false;
            for (Direction d : Direction.values()) {
                BlockPos powerCandidate = piston.offset(d);
                if (getBlock(powerCandidate) == blockType) {
                    if (detectMining.getValue() && Alien.BREAK.isMining(powerCandidate)) return false;
                    hasPower = true;
                    break;
                }
            }

            if (hasPower) {
                return placePistonAndPower(piston, direction, true);
            } else {
                Direction powerSide = findPowerSide(piston, direction, blockType);
                if (powerSide != null) {
                    return placePowerThenPiston(piston, direction, powerSide);
                }
            }
        }

        BlockState state = mc.world.getBlockState(piston);
        if (state.getBlock() instanceof PistonBlock && state.get(FacingBlock.FACING) == direction) {
            return activateExistingPiston(piston, direction);
        }

        return false;
    }

    private Direction findPowerSide(BlockPos piston, Direction pushDir, Block blockType) {
        for (Direction d : Direction.values()) {
            if (d == pushDir || (d == Direction.UP && torch.getValue())) continue;
            if (BlockUtil.canPlace(piston.offset(d), placeRange.getValue())) return d;
        }
        Direction opposite = pushDir.getOpposite();
        if (opposite != Direction.UP || !torch.getValue()) {
            if (BlockUtil.canPlace(piston.offset(opposite), placeRange.getValue())) return opposite;
        }
        return null;
    }

    private boolean placePistonAndPower(BlockPos piston, Direction direction, boolean alreadyPowered) {
        if (detectMining.getValue() && Alien.BREAK.isMining(piston)) return false;

        int pistonSlot = findClass(PistonBlock.class);
        Direction side = BlockUtil.getPlaceSide(piston);
        if (side == null) return false;

        if (rotate.getValue()) Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
        if (yawDeceive.getValue()) pistonFacing(direction.getOpposite());

        int old = mc.player.getInventory().selectedSlot;
        doSwap(pistonSlot);
        BlockUtil.placeBlock(piston, false, pistonPacket.getValue());
        if (inventory.getValue()) { doSwap(pistonSlot); EntityUtil.syncInventory(); } else doSwap(old);

        if (rotate.getValue() && yawDeceive.getValue()) Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
        if (rotate.getValue()) Alien.ROTATION.snapBack();

        if (alreadyPowered) {
            if (shouldMine()) {
                for (Direction d : Direction.values()) {
                    if (getBlock(piston.offset(d)) == getBlockType()) PacketMine.INSTANCE.mine(piston.offset(d));
                }
            }
            if (autoDisable.getValue()) disable();
            return true;
        }

        Direction powerSide = findPowerSide(piston, direction, getBlockType());
        if (powerSide != null) {
            BlockPos powerPos = piston.offset(powerSide);
            if (detectMining.getValue() && Alien.BREAK.isMining(powerPos)) return false;

            int old2 = mc.player.getInventory().selectedSlot;
            int powerSlot = findBlock(getBlockType());
            doSwap(powerSlot);
            BlockUtil.placeBlock(powerPos, rotate.getValue(), powerPacket.getValue());
            if (inventory.getValue()) { doSwap(powerSlot); EntityUtil.syncInventory(); } else doSwap(old2);
            if (shouldMine()) PacketMine.INSTANCE.mine(powerPos);
            return true;
        }
        return true;
    }

    private boolean placePowerThenPiston(BlockPos piston, Direction direction, Direction powerSide) {
        BlockPos powerPos = piston.offset(powerSide);
        if (detectMining.getValue() && (Alien.BREAK.isMining(powerPos) || Alien.BREAK.isMining(piston))) return false;

        int old = mc.player.getInventory().selectedSlot;
        int powerSlot = findBlock(getBlockType());
        doSwap(powerSlot);
        BlockUtil.placeBlock(powerPos, rotate.getValue(), powerPacket.getValue());
        if (inventory.getValue()) { doSwap(powerSlot); EntityUtil.syncInventory(); } else doSwap(old);

        int pistonSlot = findClass(PistonBlock.class);
        Direction side = BlockUtil.getPlaceSide(piston);
        if (side == null) return false;

        if (rotate.getValue()) Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
        if (yawDeceive.getValue()) pistonFacing(direction.getOpposite());

        int old2 = mc.player.getInventory().selectedSlot;
        doSwap(pistonSlot);
        BlockUtil.placeBlock(piston, false, pistonPacket.getValue());
        if (inventory.getValue()) { doSwap(pistonSlot); EntityUtil.syncInventory(); } else doSwap(old2);

        if (rotate.getValue() && yawDeceive.getValue()) Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
        if (rotate.getValue()) Alien.ROTATION.snapBack();

        if (shouldMine()) PacketMine.INSTANCE.mine(powerPos);
        return true;
    }

    private boolean activateExistingPiston(BlockPos piston, Direction direction) {
        Block blockType = getBlockType();
        for (Direction d : Direction.values()) {
            if (getBlock(piston.offset(d)) == blockType) {
                if (detectMining.getValue() && Alien.BREAK.isMining(piston.offset(d))) return false;
                if (autoDisable.getValue()) disable();
                return true;
            }
        }
        for (Direction d : Direction.values()) {
            if ((d != Direction.UP || !torch.getValue()) && BlockUtil.canPlace(piston.offset(d), placeRange.getValue())) {
                BlockPos powerPos = piston.offset(d);
                if (detectMining.getValue() && Alien.BREAK.isMining(powerPos)) continue;
                int old = mc.player.getInventory().selectedSlot;
                int powerSlot = findBlock(blockType);
                doSwap(powerSlot);
                BlockUtil.placeBlock(powerPos, rotate.getValue(), powerPacket.getValue());
                if (inventory.getValue()) { doSwap(powerSlot); EntityUtil.syncInventory(); } else doSwap(old);
                return true;
            }
        }
        if (helper.getValue()) {
            BlockPos helperPos = getHelper(piston);
            if (helperPos != null) {
                if (detectMining.getValue() && Alien.BREAK.isMining(helperPos)) return false;
                int old = mc.player.getInventory().selectedSlot;
                int powerSlot = findBlock(blockType);
                doSwap(powerSlot);
                BlockUtil.placeBlock(helperPos, rotate.getValue(), powerPacket.getValue());
                if (inventory.getValue()) { doSwap(powerSlot); EntityUtil.syncInventory(); } else doSwap(old);
                return true;
            }
        }
        return false;
    }

    private BlockPos getHelper(BlockPos pos) {
        if (!helper.getValue()) return null;
        for (Direction d : Direction.values()) {
            if (detectMining.getValue() && Alien.BREAK.isMining(pos.offset(d))) continue;
            if (BlockUtil.isStrictDirection(pos.offset(d), d.getOpposite()) &&
                    BlockUtil.canPlace(pos.offset(d), placeRange.getValue())) {
                return pos.offset(d);
            }
        }
        return null;
    }

    private void tryPlaceObsidian(BlockPos pos) {
        if (pos == null) return;
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;
        if (!BlockUtil.canPlace(pos, 6.0, false)) return;
        if (MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())) > placeRange.getValue()) return;

        int old = mc.player.getInventory().selectedSlot;
        int block = findBlock(Blocks.OBSIDIAN);
        if (block == -1) block = findBlock(Blocks.CRYING_OBSIDIAN);
        if (block == -1) return;
        doSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue());
        if (inventory.getValue()) { doSwap(block); EntityUtil.syncInventory(); } else doSwap(old);
    }

    private boolean shouldMine() {
        if (!mine.getValue()) return false;
        if (!pauseCity.getValue()) return true;
        BlockPos breakPos = PacketMine.getBreakPos();
        return breakPos != null && breakPos.equals(PacketMine.secondPos);
    }

    private boolean isTrueFacing(BlockPos pos, Direction facing) {
        if (yawDeceive.getValue()) return true;
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) return false;
        Vec3d hitVec = pos.offset(side.getOpposite()).toCenterPos()
                .add(new Vec3d(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5));
        float[] rot = RotationManager.getRotation(hitVec);
        return MathUtil.getFacingOrder(rot[0], rot[1]).getOpposite() == facing;
    }

    private boolean facingCheck(BlockPos pos) {
        if (ClientSetting.INSTANCE.lowVersion.getValue()) {
            Direction direction = MathUtil.getDirectionFromEntityLiving(pos, mc.player);
            return direction != Direction.UP && direction != Direction.DOWN;
        }
        return true;
    }

    public static void pistonFacing(Direction i) {
        if (i == Direction.EAST) Alien.ROTATION.snapAt(-90.0f, 5.0f);
        else if (i == Direction.WEST) Alien.ROTATION.snapAt(90.0f, 5.0f);
        else if (i == Direction.NORTH) Alien.ROTATION.snapAt(180.0f, 5.0f);
        else if (i == Direction.SOUTH) Alien.ROTATION.snapAt(0.0f, 5.0f);
    }

    private boolean canPush(PlayerEntity player) {
        if (ignoreCrawling.getValue() && player.isCrawling()) return false;
        if (onlyGround.getValue() && !player.isOnGround()) return false;
        if (!allowWeb.getValue() && Alien.PLAYER.isInWeb(player) && !allowKey.isPressed()) return false;
        if (onlyNoInside.getValue() && isPlayerBurrowed(player)) return false;
        if (onlyInside.getValue() && !isPlayerBurrowed(player)) return false;

        int progress = 0;
        if (mc.world.canCollide(player, new Box(new BlockPosX(player.getX() + 1, player.getY() + 0.5, player.getZ()))))
            progress++;
        if (mc.world.canCollide(player, new Box(new BlockPosX(player.getX() - 1, player.getY() + 0.5, player.getZ()))))
            progress++;
        if (mc.world.canCollide(player, new Box(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() + 1))))
            progress++;
        if (mc.world.canCollide(player, new Box(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() - 1))))
            progress++;

        for (float x : OFFSETS) {
            for (float z : OFFSETS) {
                BlockPosX pp = new BlockPosX(player.getX() + x, player.getY() + 0.5, player.getZ() + z);
                for (Direction d : HORIZONTAL_DIRECTIONS) {
                    BlockPos pos = pp.offset(d);
                    if (isTargetHere(pos, player.getBoundingBox())) {
                        if (mc.world.canCollide(player, new Box(pos))) return true;
                        if (progress > surroundCheck.getValue() - 1) return true;
                    }
                }
            }
        }

        if (!mc.world.canCollide(player, new Box(new BlockPosX(player.getX(), player.getY() + 2.5, player.getZ())))) {
            BlockPos base = EntityUtil.getEntityPos(player);
            for (Direction d : HORIZONTAL_DIRECTIONS) {
                BlockPos pos = base.offset(d);
                Box box = player.getBoundingBox().offset(new Vec3d(d.getOffsetX(), d.getOffsetY(), d.getOffsetZ()));
                if (getBlock(pos.up()) == Blocks.PISTON_HEAD) continue;
                if (mc.world.canCollide(player, box.offset(0, 1, 0))) continue;
                if (isTargetHere(pos, player.getBoundingBox())) continue;
                if (mc.world.canCollide(player, new Box(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ()))))
                    return true;
            }
        }

        return progress > surroundCheck.getValue() - 1 ||
                Alien.HOLE.isHard(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ()));
    }

    private boolean isPlayerBurrowed(PlayerEntity player) {
        switch (burrowMode.getValue()) {
            case Strict: return isPlayerBurrowedStrict(player);
            case Loose: return isPlayerBurrowedLoose(player);
            case Always: return isPlayerBurrowedStrict(player) || isPlayerBurrowedLoose(player);
            default: return false;
        }
    }

    private boolean isPlayerBurrowedStrict(PlayerEntity player) {
        Vec3d pos = player.getPos();
        double x = pos.x, z = pos.z;
        double fx = x - Math.floor(x);
        double fz = z - Math.floor(z);
        double dx = Math.min(fx, 1.0 - fx);
        double dz = Math.min(fz, 1.0 - fz);
        if (dx > 0.15 || dz > 0.15) return false;

        int yFeet = EntityUtil.getEntityPos(player).getY();
        int cornerX = (int) Math.round(x);
        int cornerZ = (int) Math.round(z);
        BlockPos p1 = new BlockPos(cornerX, yFeet, cornerZ);
        BlockPos p2 = new BlockPos(cornerX - 1, yFeet, cornerZ);
        BlockPos p3 = new BlockPos(cornerX, yFeet, cornerZ - 1);
        BlockPos p4 = new BlockPos(cornerX - 1, yFeet, cornerZ - 1);

        Box targetBox = player.getBoundingBox();
        int inside = 0;
        if (isTargetHere(p1, targetBox) && isCollidableBlock(p1)) inside++;
        if (isTargetHere(p2, targetBox) && isCollidableBlock(p2)) inside++;
        if (isTargetHere(p3, targetBox) && isCollidableBlock(p3)) inside++;
        if (isTargetHere(p4, targetBox) && isCollidableBlock(p4)) inside++;
        return inside >= 3;
    }

    private boolean isPlayerBurrowedLoose(PlayerEntity player) {
        double[] yOffsets = {0.3, 1.1, 2.3};
        double[] xzOffsets = {0.3, -0.3};
        for (double y : yOffsets) {
            for (double x : xzOffsets) {
                for (double z : xzOffsets) {
                    BlockPos pos = new BlockPosX(player.getX() + x, player.getY() + y, player.getZ() + z);
                    if (!mc.world.isAir(pos) && isCollidableBlock(pos)) return true;
                }
            }
        }
        return false;
    }

    private boolean isTargetHere(BlockPos pos, Box box) {
        return box.intersects(new Box(pos));
    }

    private boolean isCollidableBlock(BlockPos pos) {
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    private Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    private Block getBlockType() {
        return torch.getValue() ? Blocks.REDSTONE_TORCH : Blocks.REDSTONE_BLOCK;
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        else InventoryUtil.switchToSlot(slot);
    }

    public int findBlock(Block block) {
        return inventory.getValue() ? InventoryUtil.findBlockInventorySlot(block) : InventoryUtil.findBlock(block);
    }

    public int findClass(Class<?> clazz) {
        return inventory.getValue() ? InventoryUtil.findClassInventorySlot(clazz) : InventoryUtil.findClass(clazz);
    }

    @Override
    public String getInfo() {
        if (syncCrystal.getValue() && AutoCrystal.INSTANCE != null && AutoCrystal.INSTANCE.crystalPos != null) {
            return "WaitSync";
        }
        if (currentTarget != null) {
            return currentTarget.getName().getString();
        }
        return null;
    }
}