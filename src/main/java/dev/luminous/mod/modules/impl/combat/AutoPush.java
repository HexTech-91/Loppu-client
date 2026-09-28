package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;

public class AutoPush extends Module {
    public static AutoPush INSTANCE;

    private final BooleanSetting general = add(new BooleanSetting("General", false).setParent().injectTask(this::generalPage));
    private final BooleanSetting pistonPacket = add(new BooleanSetting("PistonPacket", false, this.general::isOpen));
    private final BooleanSetting redStonePacket = add(new BooleanSetting("PowerPacket", true, this.general::isOpen));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true, this.general::isOpen));
    private final BooleanSetting helper = this.add(new BooleanSetting("Helper", true, this.general::isOpen));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotation", true, this.general::isOpen));
    private final BooleanSetting yawDeceive = add(new BooleanSetting("YawDeceive", true, this.general::isOpen));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true, this.general::isOpen));
    private final SliderSetting range = add(new SliderSetting("Range", 5.0, 0.0, 6.0, this.general::isOpen));
    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0, this.general::isOpen));
    private final SliderSetting updateDelay = add(new SliderSetting("Delay", 100, 0, 1000, this.general::isOpen));

    private final BooleanSetting check = add(new BooleanSetting("Check", false).setParent().injectTask(this::checkPage));
    private final BooleanSetting nomove = this.add(new BooleanSetting("MovePause", false, this.check::isOpen));
    private final BooleanSetting noEating = add(new BooleanSetting("EatingPause", true, this.check::isOpen));
    private final BooleanSetting allowWeb = add(new BooleanSetting("AllowWeb", true, this.check::isOpen));
    private final BooleanSetting selfGround = add(new BooleanSetting("SelfGround", true, this.check::isOpen));
    private final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", false, this.check::isOpen));
    private final SliderSetting surroundCheck = add(new SliderSetting("SurroundCheck", 2, 0, 4, this.check::isOpen));

    private final Timer timer = new Timer();
    private PlayerEntity displayTarget = null;
    public Vec3d directionVec = null;

    public AutoPush() {
        super("AutoPush", Category.Combat);
        setChinese("更好的活塞推人");
        INSTANCE = this;
    }

    public void generalPage() {
        this.general.setValueWithoutTask(false);
        this.general.setOpen(!this.general.isOpen());
    }

    public void checkPage() {
        this.check.setValueWithoutTask(false);
        this.check.setOpen(!this.check.isOpen());
    }

    public static boolean isTargetHere(BlockPos pos, Entity target) {
        return new Box(pos).intersects(target.getBoundingBox());
    }

    public boolean check(boolean onlyStatic) {
        return onlyStatic
                && mc.player != null
                && (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0);
    }

    public static boolean isInWeb(PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        for (float x : new float[] {0.0f, 0.3f, -0.3f}) {
            for (float z : new float[] {0.0f, 0.3f, -0.3f}) {
                for (float y : new float[] {0.0f, 1.0f, -1.0f}) {
                    BlockPos pos =
                            new BlockPos(
                                    (int)(playerPos.getX() + (double) x),
                                    (int)(playerPos.getY() + (double) y),
                                    (int)(playerPos.getZ() + (double) z));
                    if (!AutoPush.isTargetHere(pos, player)
                            || AutoPush.mc.world.getBlockState(pos).getBlock() != Blocks.COBWEB) continue;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
    }

    public static void pistonFacing(Direction i) {
        if (i == Direction.EAST) {
            Alien.ROTATION.snapAt(-90.0f, 5.0f);
        } else if (i == Direction.WEST) {
            Alien.ROTATION.snapAt(90.0f, 5.0f);
        } else if (i == Direction.NORTH) {
            Alien.ROTATION.snapAt(180.0f, 5.0f);
        } else if (i == Direction.SOUTH) {
            Alien.ROTATION.snapAt(0.0f, 5.0f);
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        boolean hasTarget = false;
        if (!timer.passedMs(updateDelay.getValue())) return;
        if (selfGround.getValue() && !mc.player.isOnGround()) {
            if (autoDisable.getValue()) {
                disable();
            }
            return;
        }
        if (check(nomove.getValue())) {
            return;
        }
        if (findBlock(getBlockType()) == -1 || findClass(PistonBlock.class) == -1) {
            if (autoDisable.getValue()) disable();
            return;
        }
        if (noEating.getValue() && mc.player.isUsingItem()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;

        for (PlayerEntity player : CombatUtil.getEnemies(range.getValue())) {
            if (player.isDead() || player.getHealth() <= 0) continue;
            this.displayTarget = player;
            hasTarget = true;
            if (!canPush(player)) continue;
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                BlockPos pos = EntityUtil.getEntityPos(player).offset(i);
                if (isTargetHere(pos, player)) {
                    if (mc.world.canCollide(player, new Box(pos))) {
                        if (this.doPush(EntityUtil.getEntityPos(player).offset(i.getOpposite()), i)) {
                            timer.reset();
                            return;
                        }
                        if (this.doPush(EntityUtil.getEntityPos(player).offset(i.getOpposite()).up(), i)) {
                            timer.reset();
                            return;
                        }
                    }
                }
            }

            float[] offset = new float[] {-0.25f, 0f, 0.25f};
            for (float x : offset) {
                for (float z : offset) {
                    BlockPos playerPos =
                            new BlockPos((int)(player.getX() + x), (int)(player.getY() + 0.5), (int)(player.getZ() + z));
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP || i == Direction.DOWN) continue;
                        BlockPos pos = playerPos.offset(i);
                        if (isTargetHere(pos, player)) {
                            if (mc.world.canCollide(player, new Box(pos))) {
                                if (this.doPush(playerPos.offset(i.getOpposite()), i)) {
                                    timer.reset();
                                    return;
                                }
                                if (this.doPush(playerPos.offset(i.getOpposite()).up(), i)) {
                                    timer.reset();
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (!mc.world.canCollide(
                    player, new Box(new BlockPos((int)player.getX(), (int)(player.getY() + 2.5), (int)player.getZ())))) {
                for (Direction i : Direction.values()) {
                    if (i == Direction.UP || i == Direction.DOWN) continue;
                    BlockPos pos = EntityUtil.getEntityPos(player).offset(i);
                    Box box =
                            player
                                    .getBoundingBox()
                                    .offset(new Vec3d(i.getOffsetX(), i.getOffsetY(), i.getOffsetZ()));
                    if (getBlock(pos.up()) != Blocks.PISTON_HEAD
                            && !mc.world.canCollide(player, box.offset(0.0, 1.0, 0.0))
                            && !isTargetHere(pos, player)) {
                        if (this.doPush(EntityUtil.getEntityPos(player).offset(i.getOpposite()).up(), i)) {
                            timer.reset();
                            return;
                        }
                        if (this.doPush(EntityUtil.getEntityPos(player).offset(i.getOpposite()), i)) {
                            timer.reset();
                            return;
                        }
                    }
                }
            }

            for (float x : offset) {
                for (float z : offset) {
                    BlockPos playerPos =
                            new BlockPos((int)(player.getX() + x), (int)(player.getY() + 0.5), (int)(player.getZ() + z));
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP || i == Direction.DOWN) continue;
                        BlockPos pos = playerPos.offset(i);
                        if (isTargetHere(pos, player)) {
                            if (this.doPush(playerPos.offset(i.getOpposite()).up(), i)) {
                                timer.reset();
                                return;
                            }
                            if (this.doPush(playerPos.offset(i.getOpposite()), i)) {
                                timer.reset();
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (!hasTarget) {
            this.displayTarget = null;
        }
    }

    private boolean doPush(BlockPos pos, Direction direction) {
        if (!mc.world.isAir(pos.offset(direction))) return false;
        if (this.isTrueFacing(pos, direction) && this.facingCheck(pos)) {
            boolean canPlace = BlockUtil.canPlace(pos, this.placeRange.getValue());
            if (!canPlace && this.helper.getValue()) {
                BlockPos helperPos = this.getHelper(pos);
                if (helperPos != null) {
                    this.tryPlaceObsidian(helperPos);
                    canPlace = true;
                }
            }
            if (canPlace) {
                boolean canPower = false;
                for (Direction i : Direction.values()) {
                    if (this.getBlock(pos.offset(i)) == Blocks.REDSTONE_BLOCK) {
                        canPower = true;
                        break;
                    }
                }
                if (canPower) {
                    this.doPiston(direction, pos, true);
                    return true;
                }
                for (Direction i : Direction.values()) {
                    if (BlockUtil.canPlace(pos.offset(i), this.placeRange.getValue())) {
                        canPower = true;
                        break;
                    }
                }
                if (canPower) {
                    this.doPiston(direction, pos, false);
                    return true;
                }
                this.downPower(pos);
                this.doPiston(direction, pos, false);
                return true;
            }
        }
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof PistonBlock
                && getBlockState(pos).get(FacingBlock.FACING) == direction) {
            for (Direction i : Direction.values()) {
                if (this.getBlock(pos.offset(i)) == Blocks.REDSTONE_BLOCK) {
                    if (this.autoDisable.getValue()) {
                        this.disable();
                        return true;
                    }
                    return false;
                }
            }
            for (Direction i : Direction.values()) {
                if (BlockUtil.canPlace(pos.offset(i), this.placeRange.getValue())) {
                    this.doPower(pos, i);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean facingCheck(BlockPos pos) {
        return true;
    }

    private boolean isTrueFacing(BlockPos pos, Direction facing) {
        if (this.yawDeceive.getValue()) return true;
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) return false;
        return true;
    }

    private boolean doPower(BlockPos pos, Direction i2) {
        if (!BlockUtil.canPlace(pos.offset(i2), this.placeRange.getValue())) {
            return true;
        }
        int old = 0;
        if (mc.player != null) {
            old = mc.player.getInventory().selectedSlot;
        }
        int power = this.findBlock(Blocks.REDSTONE_BLOCK);
        if (power == -1) return true;
        this.doSwap(power);
        BlockUtil.placeBlock(pos.offset(i2), this.rotate.getValue(), this.redStonePacket.getValue());
        if (this.inventory.getValue()) {
            this.doSwap(power);
            EntityUtil.syncInventory();
        } else {
            this.doSwap(old);
        }
        return false;
    }

    private boolean doPower(BlockPos pos) {
        Direction facing = BlockUtil.getBestNeighboring(pos, null);
        if (facing != null) {
            if (!this.doPower(pos, facing)) {
                return true;
            }
        }
        for (Direction i2 : Direction.values()) {
            if (this.doPower(pos, i2)) continue;
            return true;
        }
        return false;
    }

    private boolean downPower(BlockPos pos) {
        if (BlockUtil.getPlaceSide(pos) == null) {
            boolean noPower = true;
            for (Direction i2 : Direction.values()) {
                if (this.getBlock(pos.offset(i2)) == Blocks.REDSTONE_BLOCK) {
                    noPower = false;
                    break;
                }
            }
            if (noPower) {
                if (!BlockUtil.canPlace(pos.add(0, -1, 0), this.placeRange.getValue())) {
                    return true;
                }
                int old = 0;
                if (mc.player != null) {
                    old = mc.player.getInventory().selectedSlot;
                }
                int power = this.findBlock(Blocks.REDSTONE_BLOCK);
                this.doSwap(power);
                BlockUtil.placeBlock(
                        pos.add(0, -1, 0), this.rotate.getValue(), this.redStonePacket.getValue());
                if (this.inventory.getValue()) {
                    this.doSwap(power);
                    EntityUtil.syncInventory();
                } else {
                    this.doSwap(old);
                }
            }
        }
        return false;
    }

    private void doPiston(Direction i, BlockPos pos, boolean mine) {
        if (BlockUtil.canPlace(pos, this.placeRange.getValue())) {
            int piston = this.findClass(PistonBlock.class);
            Direction side = BlockUtil.getPlaceSide(pos);
            if (this.rotate.getValue()) {
                Alien.ROTATION.lookAt(pos.offset(side), side.getOpposite());
            }
            if (this.yawDeceive.getValue()) {
                AutoPush.pistonFacing(i.getOpposite());
            }
            int old = 0;
            if (mc.player != null) {
                old = mc.player.getInventory().selectedSlot;
            }
            this.doSwap(piston);
            BlockUtil.placeBlock(pos, false, this.pistonPacket.getValue());
            if (this.inventory.getValue()) {
                this.doSwap(piston);
                EntityUtil.syncInventory();
            } else {
                this.doSwap(old);
            }
            if (this.rotate.getValue()) {
                Alien.ROTATION.lookAt(pos.offset(side), side.getOpposite());
            }
            for (Direction i2 : Direction.values()) {
                if (this.getBlock(pos.offset(i2)) != Blocks.REDSTONE_BLOCK) continue;
                if (mine) {
                    this.mine(pos.offset(i2));
                }
                if (this.autoDisable.getValue()) {
                    this.disable();
                }
                return;
            }
            this.doPower(pos);
        }
    }

    @Override
    public String getInfo() {
        if (this.displayTarget != null) {
            return this.displayTarget.getName().getString();
        }
        return null;
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            if (mc.player != null) {
                InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
            }
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    public int findBlock(Block blockIn) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(blockIn);
        }
        return InventoryUtil.findBlock(blockIn);
    }

    public int findClass(Class<?> clazz) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(clazz);
        }
        return InventoryUtil.findClass(clazz);
    }

    public BlockPos getHelper(BlockPos pos) {
        if (!this.helper.getValue()) {
            return null;
        } else {
            for (Direction i : Direction.values()) {
                if (BlockUtil.isStrictDirection(pos.offset(i), i.getOpposite()) && BlockUtil.canPlace(pos.offset(i), this.placeRange.getValue())) {
                    return pos.offset(i);
                }
            }
            return null;
        }
    }

    private void tryPlaceObsidian(BlockPos pos) {
        if (pos != null) {
            int old = 0;
            if (mc.player != null) {
                old = mc.player.getInventory().selectedSlot;
            }
            int obsidian = this.findBlock(Blocks.OBSIDIAN);
            if (obsidian == -1) {
                obsidian = this.findBlock(Blocks.CRYING_OBSIDIAN);
            }
            if (obsidian != -1) {
                this.doSwap(obsidian);
                BlockUtil.placeBlock(pos, this.rotate.getValue(), false);
                if (this.inventory.getValue()) {
                    this.doSwap(obsidian);
                    EntityUtil.syncInventory();
                } else {
                    this.doSwap(old);
                }
            }
        }
    }

    private void mine(BlockPos pos) {
        PacketMine.INSTANCE.mine(pos);
    }

    private Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    private BlockState getBlockState(BlockPos pos) {
        if (mc.world != null) {
            return mc.world.getBlockState(pos);
        }
        return null;
    }

    private Boolean canPush(PlayerEntity player) {
        if (this.onlyGround.getValue() && !player.isOnGround()) {
            return false;
        }
        if (!this.allowWeb.getValue() && isInWeb(player)) {
            return false;
        }
        int progress = 0;
        if (!mc.world.isAir(new BlockPos((int)(player.getX() + 1.0), (int)(player.getY() + 0.5), (int)player.getZ()))) {
            ++progress;
        }
        if (!mc.world.isAir(new BlockPos((int)(player.getX() - 1.0), (int)(player.getY() + 0.5), (int)player.getZ()))) {
            ++progress;
        }
        if (!mc.world.isAir(new BlockPos((int)player.getX(), (int)(player.getY() + 0.5), (int)(player.getZ() + 1.0)))) {
            ++progress;
        }
        if (!mc.world.isAir(new BlockPos((int)player.getX(), (int)(player.getY() + 0.5), (int)(player.getZ() - 1.0)))) {
            ++progress;
        }
        if (!mc.world.isAir(new BlockPos((int)player.getX(), (int)(player.getY() + 2.5), (int)player.getZ()))) {
            for (Direction i : Direction.values()) {
                BlockPos pos;
                if (i == Direction.UP
                        || i == Direction.DOWN
                        || (!mc.world.isAir(pos = EntityUtil.getEntityPos(player).offset(i))
                        || !mc.world.isAir(pos.up()))
                        && !isTargetHere(pos, player)) continue;
                if (!mc.world.isAir(new BlockPos((int)player.getX(), (int)(player.getY() + 0.5), (int)player.getZ()))) {
                    return true;
                }
                return (double) progress > this.surroundCheck.getValue() - 1.0;
            }
            return false;
        }
        if (!mc.world.canCollide(
                player, new Box(new BlockPos((int)player.getX(), (int)(player.getY() + 2.5), (int)player.getZ())))) {
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                BlockPos pos = EntityUtil.getEntityPos(player).offset(i);
                Box box =
                        player
                                .getBoundingBox()
                                .offset(new Vec3d(i.getOffsetX(), i.getOffsetY(), i.getOffsetZ()));
                if (this.getBlock(pos.up()) == Blocks.PISTON_HEAD
                        || mc.world.canCollide(player, box.offset(0.0, 1.0, 0.0))
                        || isTargetHere(pos, player)
                        || !mc.world.canCollide(
                        player, new Box(new BlockPos((int)player.getX(), (int)(player.getY() + 0.5), (int)player.getZ()))))
                    continue;
                return true;
            }
        }
        return (double) progress > this.surroundCheck.getValue() - 1.0;
    }

    private Block getBlockType() {
        return Blocks.REDSTONE_BLOCK;
    }
}