package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.PredictUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.AutoTrap;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class HolePush
        extends Module {
    public static HolePush INSTANCE;
    private static final float[] OFFSETS = new float[]{-0.25f, 0.0f, 0.25f};
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final EnumSetting<Page> page = this.add(new EnumSetting<Page>("Page", Page.General));
    private final BooleanSetting torch = this.add(new BooleanSetting("Torch", false, () -> this.page.is(Page.General)));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.is(Page.Rotate)));
    private final BooleanSetting yawDeceive = this.add(new BooleanSetting("YawDeceive", true, () -> this.page.is(Page.Rotate)));
    private final BooleanSetting allowWeb = this.add(new BooleanSetting("AllowWeb", true, () -> this.page.is(Page.Check)));
    public final BindSetting allowKey = this.add(new BindSetting("AllowKey", -1, () -> !this.allowWeb.getValue() && this.page.is(Page.Check)));
    private final BooleanSetting noEating = this.add(new BooleanSetting("EatingPause", true, () -> this.page.is(Page.Check)));
    private final BooleanSetting selfGround = this.add(new BooleanSetting("SelfGround", true, () -> this.page.is(Page.Check)));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", false, () -> this.page.is(Page.Check)));
    private final BooleanSetting onlyNoInside = this.add(new BooleanSetting("OnlyNoInside", false, () -> this.page.is(Page.Check)));
    private final BooleanSetting onlyInside = this.add(new BooleanSetting("OnlyInside", true, () -> this.page.is(Page.Check)));
    private final SliderSetting surroundCheck = this.add(new SliderSetting("SurroundCheck", 0, 0, 4, () -> this.page.is(Page.Check)));
    private final BooleanSetting syncCrystal = this.add(new BooleanSetting("SyncCrystal", true, () -> this.page.is(Page.Check)));
    private final BooleanSetting preferTrap = this.add(new BooleanSetting("PreferTrap", true, () -> this.page.is(Page.Check)));
    private final BooleanSetting pistonPacket = this.add(new BooleanSetting("PistonPacket", true, () -> this.page.is(Page.General)));
    private final BooleanSetting powerPacket = this.add(new BooleanSetting("PowerPacket", true, () -> this.page.is(Page.General)));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true, () -> this.page.is(Page.General)));
    private final BooleanSetting pauseCity = this.add(new BooleanSetting("PauseCity", true, () -> this.mine.isOpen() && this.page.is(Page.General)));
    private final SliderSetting updateDelay = this.add(new SliderSetting("Delay", 200, 0, 1000, () -> this.page.is(Page.General)));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true, () -> this.page.is(Page.General)));
    private final SliderSetting range = this.add(new SliderSetting("Range", 4.7, 0.0, 6.0, () -> this.page.is(Page.General)));
    private final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 4.7, 0.0, 6.0, () -> this.page.is(Page.General)));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.is(Page.General)));

    // ========== 新增 Helper 设置 ==========
    private final BooleanSetting helper = this.add(new BooleanSetting("Helper", true, () -> this.page.is(Page.General)));
    // ======================================

    private final Timer timer = new Timer();

    public HolePush() {
        super("HolePush", Module.Category.Combat);
        this.setChinese("坑推");
        INSTANCE = this;
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

    @Override
    public void onEnable() {
        if (AutoCrystal.INSTANCE != null) {
            AutoCrystal.INSTANCE.lastBreakTimer.reset();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!this.timer.passedMs(this.updateDelay.getValue())) {
            return;
        }
        if (this.selfGround.getValue() && !HolePush.mc.player.isOnGround()) {
            return;
        }
        Block blockType = this.getBlockType();
        int powerSlot = this.findBlock(blockType);
        int pistonSlot = this.findClass(PistonBlock.class);
        if (powerSlot == -1 || pistonSlot == -1) {
            if (this.autoDisable.getValue()) {
                this.disable();
            }
            return;
        }
        if (this.syncCrystal.getValue() && AutoCrystal.INSTANCE != null && AutoCrystal.INSTANCE.crystalPos != null) {
            return;
        }
        if (this.preferTrap.getValue() && AutoTrap.INSTANCE != null && AutoTrap.INSTANCE.isOn() && AutoTrap.INSTANCE.target != null) {
            this.timer.reset();
            return;
        }
        if (this.noEating.getValue() && HolePush.mc.player.isUsingItem()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        for (PlayerEntity player : CombatUtil.getEnemies(this.range.getValue())) {
            BlockPos pos;
            BlockPosX playerPos;
            if (!this.canPush(player).booleanValue()) continue;
            BlockPos basePos = EntityUtil.getEntityPos((Entity)player);
            Vec3d targetPos = player.getPos();
            Box targetBox = player.getBoundingBox();

            boolean pushed = this.tryPushFourBlockBurrowCenter(player, targetPos, targetBox);
            if (pushed) {
                this.timer.reset();
                return;
            }

            for (Direction i : HORIZONTAL_DIRECTIONS) {
                BlockPos pos2;
                if (!this.isTargetHere(pos2 = basePos.offset(i), (Entity)player) || !HolePush.mc.world.canCollide((Entity)player, new Box(pos2))) continue;
                if (this.tryPush(basePos.offset(i.getOpposite()), i)) {
                    this.timer.reset();
                    return;
                }
                if (!this.tryPush(basePos.offset(i.getOpposite()).up(), i)) continue;
                this.timer.reset();
                return;
            }
            for (float x : OFFSETS) {
                for (float z : OFFSETS) {
                    playerPos = new BlockPosX(player.getX() + (double)x, player.getY() + 0.5, player.getZ() + (double)z);
                    for (Direction i : HORIZONTAL_DIRECTIONS) {
                        if (!this.isTargetHere(pos = playerPos.offset(i), (Entity)player) || !HolePush.mc.world.canCollide((Entity)player, new Box(pos))) continue;
                        if (this.tryPush(playerPos.offset(i.getOpposite()), i)) {
                            this.timer.reset();
                            return;
                        }
                        if (!this.tryPush(playerPos.offset(i.getOpposite()).up(), i)) continue;
                        this.timer.reset();
                        return;
                    }
                }
            }
            if (!HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX(), player.getY() + 2.5, player.getZ())))) {
                for (Direction i : HORIZONTAL_DIRECTIONS) {
                    BlockPos pos3 = basePos.offset(i);
                    Box box = player.getBoundingBox().offset(new Vec3d((double)i.getOffsetX(), (double)i.getOffsetY(), (double)i.getOffsetZ()));
                    if (this.getBlock(pos3.up()) == Blocks.PISTON_HEAD || HolePush.mc.world.canCollide((Entity)player, box.offset(0.0, 1.0, 0.0)) || this.isTargetHere(pos3, (Entity)player)) continue;
                    if (this.tryPush(basePos.offset(i.getOpposite()).up(), i)) {
                        this.timer.reset();
                        return;
                    }
                    if (!this.tryPush(basePos.offset(i.getOpposite()), i)) continue;
                    this.timer.reset();
                    return;
                }
            }
            for (float x : OFFSETS) {
                for (float z : OFFSETS) {
                    playerPos = new BlockPosX(player.getX() + (double)x, player.getY() + 0.5, player.getZ() + (double)z);
                    for (Direction i : HORIZONTAL_DIRECTIONS) {
                        if (!this.isTargetHere(pos = playerPos.offset(i), (Entity)player)) continue;
                        if (this.tryPush(playerPos.offset(i.getOpposite()).up(), i)) {
                            this.timer.reset();
                            return;
                        }
                        if (!this.tryPush(playerPos.offset(i.getOpposite()), i)) continue;
                        this.timer.reset();
                        return;
                    }
                }
            }
        }
    }

    // ========== 新增 Helper 方法 ==========
    private BlockPos getHelper(BlockPos pos) {
        if (!this.helper.getValue()) {
            return null;
        }
        for (Direction i : Direction.values()) {
            if (!BlockUtil.isStrictDirection(pos.offset(i), i.getOpposite()) || !BlockUtil.canPlace(pos.offset(i), this.placeRange.getValue())) continue;
            return pos.offset(i);
        }
        return null;
    }

    private void tryPlaceObsidian(BlockPos pos) {
        if (pos != null) {
            int obsidian;
            int old = HolePush.mc.player.getInventory().selectedSlot;
            if ((obsidian = this.findBlock(Blocks.OBSIDIAN)) == -1) {
                obsidian = this.findBlock(Blocks.NETHERITE_BLOCK);
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
    // ======================================

    private boolean tryPush(BlockPos piston, Direction direction) {
        BlockState state;
        Block blockType = this.getBlockType();
        if (!HolePush.mc.world.isAir(piston.offset(direction))) {
            return false;
        }

        // ========== 新增 Helper 逻辑 ==========
        boolean canPlace = BlockUtil.clientCanPlace(piston, false);
        if (!canPlace && this.helper.getValue()) {
            BlockPos helperPos = this.getHelper(piston);
            if (helperPos != null) {
                this.tryPlaceObsidian(helperPos);
                canPlace = true; // 假设放置辅助块后即可放置活塞
            }
        }
        // ====================================

        if (this.isTrueFacing(piston, direction) && this.facingCheck(piston) && canPlace) {
            boolean canPower = false;
            if (BlockUtil.getPlaceSide(piston, this.placeRange.getValue()) != null) {
                CombatUtil.modifyPos = piston;
                CombatUtil.modifyBlockState = Blocks.PISTON.getDefaultState();
                for (Direction direction2 : Direction.values()) {
                    if (this.getBlock(piston.offset(direction2)) != blockType) continue;
                    canPower = true;
                    break;
                }
                for (Direction direction3 : Direction.values()) {
                    if (canPower) break;
                    if (!BlockUtil.canPlace(piston.offset(direction3), this.placeRange.getValue())) continue;
                    canPower = true;
                }
                CombatUtil.modifyPos = null;
                if (canPower) {
                    int pistonSlot = this.findClass(PistonBlock.class);
                    Direction side = BlockUtil.getPlaceSide(piston, this.placeRange.getValue());
                    if (side != null) {
                        if (this.rotate.getValue()) {
                            Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
                        }
                        if (this.yawDeceive.getValue()) {
                            HolePush.pistonFacing(direction.getOpposite());
                        }
                        int old = HolePush.mc.player.getInventory().selectedSlot;
                        this.doSwap(pistonSlot);
                        BlockUtil.placeBlock(piston, false, this.pistonPacket.getValue());
                        if (this.inventory.getValue()) {
                            this.doSwap(pistonSlot);
                            EntityUtil.syncInventory();
                        } else {
                            this.doSwap(old);
                        }
                        if (this.rotate.getValue() && this.yawDeceive.getValue()) {
                            Alien.ROTATION.lookAt(piston.offset(side), side.getOpposite());
                        }
                        if (this.rotate.getValue()) {
                            Alien.ROTATION.snapBack();
                        }
                        for (Direction i : Direction.values()) {
                            if (this.getBlock(piston.offset(i)) != blockType) continue;
                            if (this.shouldMine()) {
                                PacketMine.INSTANCE.mine(piston.offset(i));
                            }
                            if (this.autoDisable.getValue()) {
                                this.disable();
                            }
                            return true;
                        }
                        for (Direction i : Direction.values()) {
                            if (i == Direction.UP && this.torch.getValue() || !BlockUtil.canPlace(piston.offset(i), this.placeRange.getValue())) continue;
                            int oldSlot = HolePush.mc.player.getInventory().selectedSlot;
                            int powerSlot = this.findBlock(blockType);
                            this.doSwap(powerSlot);
                            BlockUtil.placeBlock(piston.offset(i), this.rotate.getValue(), this.powerPacket.getValue());
                            if (this.inventory.getValue()) {
                                this.doSwap(powerSlot);
                                EntityUtil.syncInventory();
                            } else {
                                this.doSwap(oldSlot);
                            }
                            if (this.shouldMine()) {
                                PacketMine.INSTANCE.mine(piston.offset(i));
                            }
                            return true;
                        }
                        return true;
                    }
                }
            } else {
                Direction powerFacing = null;
                for (Direction i : Direction.values()) {
                    if (i == Direction.UP && this.torch.getValue()) continue;
                    if (powerFacing != null) break;
                    CombatUtil.modifyPos = piston.offset(i);
                    CombatUtil.modifyBlockState = blockType.getDefaultState();
                    if (BlockUtil.getPlaceSide(piston, this.placeRange.getValue()) != null) {
                        powerFacing = i;
                    }
                    CombatUtil.modifyPos = null;
                    if (powerFacing != null && !BlockUtil.canPlace(piston.offset(powerFacing))) {
                        powerFacing = null;
                    }
                }
                if (powerFacing != null) {
                    int oldSlot = HolePush.mc.player.getInventory().selectedSlot;
                    int powerSlot = this.findBlock(blockType);
                    this.doSwap(powerSlot);
                    BlockUtil.placeBlock(piston.offset(powerFacing), this.rotate.getValue(), this.powerPacket.getValue());
                    if (this.inventory.getValue()) {
                        this.doSwap(powerSlot);
                        EntityUtil.syncInventory();
                    } else {
                        this.doSwap(oldSlot);
                    }
                    CombatUtil.modifyPos = piston.offset(powerFacing);
                    CombatUtil.modifyBlockState = blockType.getDefaultState();
                    int pistonSlot = this.findClass(PistonBlock.class);
                    Direction side2 = BlockUtil.getPlaceSide(piston, this.placeRange.getValue());
                    if (side2 != null) {
                        if (this.rotate.getValue()) {
                            Alien.ROTATION.lookAt(piston.offset(side2), side2.getOpposite());
                        }
                        if (this.yawDeceive.getValue()) {
                            HolePush.pistonFacing(direction.getOpposite());
                        }
                        int old2 = HolePush.mc.player.getInventory().selectedSlot;
                        this.doSwap(pistonSlot);
                        BlockUtil.placeBlock(piston, false, this.pistonPacket.getValue());
                        if (this.inventory.getValue()) {
                            this.doSwap(pistonSlot);
                            EntityUtil.syncInventory();
                        } else {
                            this.doSwap(old2);
                        }
                        if (this.rotate.getValue() && this.yawDeceive.getValue()) {
                            Alien.ROTATION.lookAt(piston.offset(side2), side2.getOpposite());
                        }
                        if (this.rotate.getValue()) {
                            Alien.ROTATION.snapBack();
                        }
                    }
                    CombatUtil.modifyPos = null;
                    return true;
                }
            }
        }
        if ((state = HolePush.mc.world.getBlockState(piston)).getBlock() instanceof PistonBlock && this.getBlockState(piston).get((Property)FacingBlock.FACING) == direction) {
            for (Direction direction4 : Direction.values()) {
                if (this.getBlock(piston.offset(direction4)) != blockType) continue;
                if (this.autoDisable.getValue()) {
                    this.disable();
                    return true;
                }
                return false;
            }
            for (Direction direction5 : Direction.values()) {
                if (direction5 == Direction.UP && this.torch.getValue() || !BlockUtil.canPlace(piston.offset(direction5), this.placeRange.getValue())) continue;
                int oldSlot = HolePush.mc.player.getInventory().selectedSlot;
                int powerSlot = this.findBlock(blockType);
                this.doSwap(powerSlot);
                BlockUtil.placeBlock(piston.offset(direction5), this.rotate.getValue(), this.powerPacket.getValue());
                if (this.inventory.getValue()) {
                    this.doSwap(powerSlot);
                    EntityUtil.syncInventory();
                } else {
                    this.doSwap(oldSlot);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public String getInfo() {
        if (this.syncCrystal.getValue() && AutoCrystal.INSTANCE != null && AutoCrystal.INSTANCE.crystalPos != null) {
            return "WaitSync";
        }
        if (this.allowWeb.getValue() || this.allowKey.isPressed()) {
            return "CrazyDog";
        }
        return "SmartPush";
    }

    private boolean shouldMine() {
        if (!this.mine.getValue()) {
            return false;
        }
        if (!this.pauseCity.getValue()) {
            return true;
        }
        BlockPos breakPos = PacketMine.getBreakPos();
        return breakPos != null && breakPos.equals((Object)PacketMine.secondPos);
    }

    private boolean facingCheck(BlockPos pos) {
        if (ClientSetting.INSTANCE.lowVersion.getValue()) {
            Direction direction = MathUtil.getDirectionFromEntityLiving(pos, (LivingEntity)HolePush.mc.player);
            return direction != Direction.UP && direction != Direction.DOWN;
        }
        return true;
    }

    private boolean isTrueFacing(BlockPos pos, Direction facing) {
        if (this.yawDeceive.getValue()) {
            return true;
        }
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) {
            return false;
        }
        Vec3d directionVec = new Vec3d((double)pos.getX() + 0.5 + (double)side.getVector().getX() * 0.5, (double)pos.getY() + 0.5 + (double)side.getVector().getY() * 0.5, (double)pos.getZ() + 0.5 + (double)side.getVector().getZ() * 0.5);
        float[] rotation = RotationManager.getRotation(directionVec);
        return MathUtil.getFacingOrder(rotation[0], rotation[1]).getOpposite() == facing;
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, HolePush.mc.player.getInventory().selectedSlot);
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

    private boolean burrowUpdate(PlayerEntity player) {
        for (float x : new float[]{0.0f, 0.3f, -0.3f}) {
            for (float z : new float[]{0.0f, 0.3f, -0.3f}) {
                BlockPosX pos = new BlockPosX(player.getX() + (double)x, player.getY() + 1.5, player.getZ() + (double)z);
                Block block = HolePush.mc.world.getBlockState((BlockPos)pos).getBlock();
                if (!new Box(pos).intersects(player.getBoundingBox()) || block != Blocks.OBSIDIAN && block != Blocks.RESPAWN_ANCHOR && block != Blocks.BEDROCK && block != Blocks.ENDER_CHEST) continue;
                return true;
            }
        }
        return false;
    }

    private Boolean canPush(PlayerEntity player) {
        if (this.onlyGround.getValue() && !player.isOnGround()) {
            return false;
        }
        if (!this.allowWeb.getValue() && Alien.PLAYER.isInWeb(player) && !this.allowKey.isPressed()) {
            return false;
        }
        if (this.onlyNoInside.getValue() && this.burrowUpdate(player)) {
            return false;
        }
        if (this.onlyInside.getValue() && !this.burrowUpdate(player)) {
            return false;
        }
        int progress = 0;
        if (HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX() + 1.0, player.getY() + 0.5, player.getZ())))) {
            ++progress;
        }
        if (HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX() - 1.0, player.getY() + 0.5, player.getZ())))) {
            ++progress;
        }
        if (HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() + 1.0)))) {
            ++progress;
        }
        if (HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() - 1.0)))) {
            ++progress;
        }
        Box baseBox = new Box((BlockPos)new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ()));
        for (float x : OFFSETS) {
            for (float z : OFFSETS) {
                BlockPosX playerPos = new BlockPosX(player.getX() + (double)x, player.getY() + 0.5, player.getZ() + (double)z);
                for (Direction i : HORIZONTAL_DIRECTIONS) {
                    BlockPos pos;
                    if (!this.isTargetHere(pos = playerPos.offset(i), (Entity)player)) continue;
                    if (HolePush.mc.world.canCollide((Entity)player, new Box(pos))) {
                        return true;
                    }
                    if (!((double)progress > this.surroundCheck.getValue() - 1.0)) continue;
                    return true;
                }
            }
        }
        if (!HolePush.mc.world.canCollide((Entity)player, new Box((BlockPos)new BlockPosX(player.getX(), player.getY() + 2.5, player.getZ())))) {
            BlockPos playerBasePos = EntityUtil.getEntityPos((Entity)player);
            for (Direction i : HORIZONTAL_DIRECTIONS) {
                BlockPos pos = playerBasePos.offset(i);
                Box box = player.getBoundingBox().offset(new Vec3d((double)i.getOffsetX(), (double)i.getOffsetY(), (double)i.getOffsetZ()));
                if (this.getBlock(pos.up()) == Blocks.PISTON_HEAD || HolePush.mc.world.canCollide((Entity)player, box.offset(0.0, 1.0, 0.0)) || this.isTargetHere(pos, (Entity)player) || !HolePush.mc.world.canCollide((Entity)player, baseBox)) continue;
                return true;
            }
        }
        return (double)progress > this.surroundCheck.getValue() - 1.0 || Alien.HOLE.isHard(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ()));
    }

    private boolean isTargetHere(BlockPos pos, Entity target) {
        return new Box(pos).intersects(target.getBoundingBox());
    }

    private boolean isTargetHere(BlockPos pos, Box targetBox) {
        double minX = (double)pos.getX();
        double minY = (double)pos.getY();
        double minZ = (double)pos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;
        return targetBox.maxX > minX && targetBox.minX < maxX && targetBox.maxY > minY && targetBox.minY < maxY && targetBox.maxZ > minZ && targetBox.minZ < maxZ;
    }

    private boolean canSideEject(PlayerEntity player, Box targetBox, Direction dir) {
        Vec3d delta = new Vec3d((double)dir.getOffsetX(), 0.0, (double)dir.getOffsetZ());
        return !HolePush.mc.world.canCollide((Entity)player, targetBox.offset(delta));
    }

    private boolean isCollidableBlock(BlockPos pos) {
        return !HolePush.mc.world.getBlockState(pos).getCollisionShape(HolePush.mc.world, pos).isEmpty();
    }

    private boolean tryPushFourBlockBurrowCenter(PlayerEntity player, Vec3d targetPos, Box targetBox) {
        double x = targetPos.getX();
        double z = targetPos.getZ();
        double fx = x - Math.floor(x);
        double fz = z - Math.floor(z);
        double dx = Math.min(fx, 1.0 - fx);
        double dz = Math.min(fz, 1.0 - fz);
        if (dx > 0.15 || dz > 0.15) {
            return false;
        }
        int cornerX = (int)Math.round(x);
        int cornerZ = (int)Math.round(z);
        int yFeet = new BlockPosX(targetPos.getX(), targetPos.getY() + 0.5, targetPos.getZ()).getY();
        BlockPos p1 = new BlockPos(cornerX, yFeet, cornerZ);
        BlockPos p2 = new BlockPos(cornerX - 1, yFeet, cornerZ);
        BlockPos p3 = new BlockPos(cornerX, yFeet, cornerZ - 1);
        BlockPos p4 = new BlockPos(cornerX - 1, yFeet, cornerZ - 1);
        int inside = 0;
        if (this.isTargetHere(p1, targetBox) && this.isCollidableBlock(p1)) {
            ++inside;
        }
        if (this.isTargetHere(p2, targetBox) && this.isCollidableBlock(p2)) {
            ++inside;
        }
        if (this.isTargetHere(p3, targetBox) && this.isCollidableBlock(p3)) {
            ++inside;
        }
        if (this.isTargetHere(p4, targetBox) && this.isCollidableBlock(p4)) {
            ++inside;
        }
        if (inside < 3) {
            return false;
        }
        BlockPos[] bases = new BlockPos[]{p1, p2, p3, p4};

        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            if (!this.canSideEject(player, targetBox, dir)) {
                continue;
            }
            for (BlockPos base : bases) {
                BlockPos targetUp = base.up();
                if (this.isTargetHere(targetUp, targetBox) && HolePush.mc.world.isAir(targetUp)) {
                    BlockPos pistonPos = targetUp.offset(dir.getOpposite());
                    if (this.tryPush(pistonPos, dir)) {
                        return true;
                    }
                    if (this.tryPush(pistonPos.up(), dir)) {
                        return true;
                    }
                }
                BlockPos targetUp2 = base.up(2);
                if (this.isTargetHere(targetUp2, targetBox) && HolePush.mc.world.isAir(targetUp2)) {
                    BlockPos pistonPos = targetUp2.offset(dir.getOpposite());
                    if (this.tryPush(pistonPos, dir)) {
                        return true;
                    }
                    if (this.tryPush(pistonPos.up(), dir)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Block getBlock(BlockPos pos) {
        return HolePush.mc.world.getBlockState(pos).getBlock();
    }

    private Block getBlockType() {
        if (this.torch.getValue()) {
            return Blocks.REDSTONE_TORCH;
        }
        return Blocks.REDSTONE_BLOCK;
    }

    private BlockState getBlockState(BlockPos pos) {
        return HolePush.mc.world.getBlockState(pos);
    }

    public enum Page {
        General,
        Rotate,
        Check;
    }
}