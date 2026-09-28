package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class AutoRegear extends Module {
    public static AutoRegear INSTANCE;

    public enum PlaceMode {
        Container, // 原版：使用外置 findPlacePositionForContainer 平躺放置
        Generic, // 自己遍历地面放置
        Advanced // 支持横放、侧贴，部分反作弊无法绕过
    }

    private final EnumSetting<PlaceMode> placeMode = add(new EnumSetting<>("PlaceMode", PlaceMode.Container));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = add(new SliderSetting("DisableTime", 500, 0, 1000));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting place = add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting open = add(new BooleanSetting("Open", true));
    private final SliderSetting range = add(new SliderSetting("Range", 4.0f, 0.0f, 6f, .1));
    private final SliderSetting minRange = add(new SliderSetting("MinRange", 1.0f, 0.0f, 3f, .1));
    private final BooleanSetting mine = add(new BooleanSetting("Mine", false));
    private final BooleanSetting debug = add(new BooleanSetting("Debug", false));
    private final BooleanSetting enderChest = add(new BooleanSetting("EnderChest", false));
    private final BooleanSetting detectMining = add(new BooleanSetting("DetectMining", false));

    private final BooleanSetting take = add(new BooleanSetting("Take", false));
    private final BooleanSetting smart = add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final BooleanSetting forceMove = add(new BooleanSetting("ForceQuickMove", true, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting crystal = add(new SliderSetting("Crystal", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting exp = add(new SliderSetting("Exp", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting totem = add(new SliderSetting("Totem", 6, 0, 36, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting gapple = add(new SliderSetting("Gapple", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting obsidian = add(new SliderSetting("Obsidian", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting web = add(new SliderSetting("Web", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting glowstone = add(new SliderSetting("Glowstone", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting anchor = add(new SliderSetting("Anchor", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting pearl = add(new SliderSetting("Pearl", 16, 0, 64, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting piston = add(new SliderSetting("Piston", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting redstone = add(new SliderSetting("RedStone", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting bed = add(new SliderSetting("Bed", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting speed = add(new SliderSetting("Speed", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting resistance = add(new SliderSetting("Resistance", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting strength = add(new SliderSetting("Strength", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));

    private final int[] stealCountList = new int[15];

    private final Timer timer = new Timer();
    public BlockPos placePos = null;
    private final Timer disableTimer = new Timer();
    BlockPos openPos;
    boolean opend = false;

    public AutoRegear() {
        super("AutoRegear", Category.Misc);
        setChinese("补给放置");
        INSTANCE = this;
    }

    public int findShulker() {
        if (inventory.getValue()) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem &&
                        blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    return i < 9 ? i + 36 : i;
                }
            }
            return -1;
        }
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    public int findEnderChest() {
        if (inventory.getValue()) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == Items.ENDER_CHEST) {
                    return i < 9 ? i + 36 : i;
                }
            }
            return -1;
        }
        return InventoryUtil.findItem(Items.ENDER_CHEST);
    }

    @Override
    public void onEnable() {
        opend = false;
        openPos = null;
        disableTimer.reset();
        placePos = null;
        if (nullCheck()) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        if (!this.place.getValue()) return;

        if (placeMode.getValue() == PlaceMode.Container) {
            BlockPos bestPos = findPlacePosition();
            if (bestPos != null) {
                if (detectMining.getValue() && Alien.BREAK.isMining(bestPos)) {
                    sendMessage("§cPlacing position is being mined, aborted.");
                    return;
                }
                int shulkerSlot = findShulker();
                int containerSlot = shulkerSlot != -1 ? shulkerSlot : (enderChest.getValue() ? findEnderChest() : -1);
                if (containerSlot == -1) {
                    sendMessage(enderChest.getValue() ? "§cNo ender chest found" : "§cNo shulkerbox found");
                    return;
                }
                if (inventory.getValue()) {
                    InventoryUtil.inventorySwap(containerSlot, oldSlot);
                    BlockUtil.placeContainer(bestPos, rotate.getValue());
                    placePos = bestPos;
                    InventoryUtil.inventorySwap(containerSlot, oldSlot);
                } else {
                    InventoryUtil.switchToSlot(containerSlot);
                    BlockUtil.placeContainer(bestPos, rotate.getValue());
                    placePos = bestPos;
                    InventoryUtil.switchToSlot(oldSlot);
                }
                timer.reset();
                if (debug.getValue()) sendMessage("§7[Debug] Placed container at " + bestPos);
            } else {
                sendMessage("§4No place position found.");
            }
        } else if (placeMode.getValue() == PlaceMode.Generic) {
            doGenericPlace();
        } else if (placeMode.getValue() == PlaceMode.Advanced) {
            doAdvancedPlace();
        }
    }

    @Override
    public void onDisable() {
        opend = false;
        if (mine.getValue() && placePos != null) {
            PacketMine.INSTANCE.mine(placePos);
            if (debug.getValue()) sendMessage("§7[Debug] Mined container at " + placePos);
        }
    }

    private void doGenericPlace() {
        double bestDistance = 100.0;
        BlockPos bestPos = null;
        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            BlockState posState = mc.world.getBlockState(pos);
            if (!posState.isAir() && posState.getBlock() != Blocks.WATER) continue;
            BlockState upState = mc.world.getBlockState(pos.up());
            if (!upState.isAir() && upState.getBlock() != Blocks.WATER) continue;

            double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
            if (dist < this.minRange.getValue()) continue;

            BlockPos downPos = pos.down();
            BlockState downState = mc.world.getBlockState(downPos);
            if (!downState.isSolid()) continue;
            if (downState.getBlock() instanceof ShulkerBoxBlock) continue;
            if (!BlockUtil.canClick(downPos)) continue;

            if (detectMining.getValue() && Alien.BREAK.isMining(pos)) continue;

            if (bestPos != null && dist >= bestDistance) continue;
            bestDistance = dist;
            bestPos = pos;
        }
        if (bestPos == null) {
            sendMessage("§4No suitable placement position found.");
            return;
        }

        int shulkerSlot = findShulker();
        int containerSlot = shulkerSlot != -1 ? shulkerSlot : (enderChest.getValue() ? findEnderChest() : -1);
        if (containerSlot == -1) {
            sendMessage(enderChest.getValue() ? "§cNo ender chest found" : "§cNo shulkerbox found");
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(containerSlot, oldSlot);
            BlockUtil.placeContainer(bestPos, rotate.getValue());
            placePos = bestPos;
            InventoryUtil.inventorySwap(containerSlot, oldSlot);
        } else {
            InventoryUtil.switchToSlot(containerSlot);
            BlockUtil.placeContainer(bestPos, rotate.getValue());
            placePos = bestPos;
            InventoryUtil.switchToSlot(oldSlot);
        }
        timer.reset();
        if (debug.getValue()) sendMessage("§7[Debug] (Generic) Placed container at " + bestPos);
    }

    private void doAdvancedPlace() {
        double bestDistance = 100.0;
        BlockPos bestPos = null;
        Direction bestPlaceSide = null;

        int shulkerSlot = findShulker();
        int containerSlot = shulkerSlot != -1 ? shulkerSlot : (enderChest.getValue() ? findEnderChest() : -1);
        if (containerSlot == -1) {
            sendMessage(enderChest.getValue() ? "§cNo ender chest found" : "§cNo shulkerbox found");
            return;
        }
        boolean usingEnderChest = (shulkerSlot == -1 && enderChest.getValue());

        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos()));
            if (dist < this.minRange.getValue()) continue;

            Direction placeSide = BlockUtil.getPlaceSide(pos, this.range.getValue());
            if (placeSide == null) continue;
            if (!BlockUtil.clientCanPlace(pos, false)) continue;

            if (detectMining.getValue() && Alien.BREAK.isMining(pos)) continue;

            if (usingEnderChest) {
                if (!canOpenEnderChestAfterPlace(pos)) continue;
            } else {
                if (!canOpenShulkerAfterPlace(pos, placeSide)) continue;
            }

            if (bestPos == null || dist < bestDistance) {
                bestDistance = dist;
                bestPos = pos;
                bestPlaceSide = placeSide;
            }
        }

        if (bestPos == null) {
            sendMessage("§4No valid placement position found (Advanced).");
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(containerSlot, oldSlot);
            BlockUtil.placeBlock(bestPos, rotate.getValue(), false);
            placePos = bestPos;
            InventoryUtil.inventorySwap(containerSlot, oldSlot);
        } else {
            InventoryUtil.switchToSlot(containerSlot);
            BlockUtil.placeBlock(bestPos, rotate.getValue(), false);
            placePos = bestPos;
            InventoryUtil.switchToSlot(oldSlot);
        }
        timer.reset();
        if (debug.getValue()) sendMessage("§7[Debug] (Advanced) Placed container at " + bestPos + " side=" + bestPlaceSide);
    }

    private boolean canOpenShulkerAfterPlace(BlockPos pos, Direction placeSide) {
        Direction facing = BlockUtil.allowAirPlace() ? BlockUtil.getClickSide(pos) : placeSide.getOpposite();
        BlockPos front = pos.offset(facing);
        return mc.world.getBlockState(front).getCollisionShape(mc.world, front).isEmpty();
    }

    private boolean canOpenEnderChestAfterPlace(BlockPos pos) {
        BlockPos above = pos.up();
        return mc.world.getBlockState(above).getCollisionShape(mc.world, above).isEmpty();
    }

    private boolean canOpenShulker(BlockPos pos) {
        if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) return false;
        Direction facing = mc.world.getBlockState(pos).get(ShulkerBoxBlock.FACING);
        BlockPos front = pos.offset(facing);
        return mc.world.getBlockState(front).getCollisionShape(mc.world, front).isEmpty();
    }

    private BlockPos findPlacePosition() {
        return BlockUtil.findPlacePositionForContainer((float) range.getValue(), (float) minRange.getValue(), false);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (enderChest.getValue() && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            if (autoDisable.getValue()) timeoutToDisable();
            return;
        }

        if (smart.getValue() && take.getValue() && mc.currentScreen instanceof ShulkerBoxScreen) {
            updateStealCount();
        }

        if (!(mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (opend) {
                opend = false;
                if (autoDisable.getValue()) timeoutToDisable();
                if (mine.getValue() && openPos != null) {
                    if (mc.world.getBlockState(openPos).getBlock() instanceof ShulkerBoxBlock) {
                        PacketMine.INSTANCE.mine(openPos);
                        if (debug.getValue()) sendMessage("§7[Debug] Mined opened container at " + openPos);
                    } else {
                        openPos = null;
                    }
                }
                return;
            }

            if (open.getValue() && placePos != null) {
                double dist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(placePos.toCenterPos()));
                if (dist > range.getValue()) {
                    if (autoDisable.getValue()) timeoutToDisable();
                    return;
                }

                boolean isShulker = mc.world.getBlockState(placePos).getBlock() instanceof ShulkerBoxBlock;
                boolean isEnder = enderChest.getValue() && mc.world.getBlockState(placePos).getBlock() == Blocks.ENDER_CHEST;

                if (isShulker) {
                    if (!canOpenShulker(placePos)) {
                        if (autoDisable.getValue()) timeoutToDisable();
                        return;
                    }
                }

                if (isShulker || isEnder) {
                    openPos = placePos;
                    BlockUtil.clickBlock(placePos, BlockUtil.getClickSide(placePos), rotate.getValue());
                    timer.reset();
                    if (debug.getValue()) sendMessage("§7[Debug] Opening container at " + placePos);
                } else if (!timer.passedMs(500)) {
                    return;
                } else {
                    if (autoDisable.getValue()) timeoutToDisable();
                    return;
                }
            } else if (autoDisable.getValue()) {
                timeoutToDisable();
            }
            return;
        }

        opend = true;
        if (!take.getValue()) {
            if (autoDisable.getValue()) timeoutToDisable();
            return;
        }

        boolean took = false;
        ScreenHandler screenHandler = mc.player.currentScreenHandler;
        if (screenHandler instanceof ShulkerBoxScreenHandler shulker) {
            for (Slot slot : shulker.slots) {
                if (slot.id >= 27 || slot.getStack().isEmpty()) continue;
                ItemStack stk = slot.getStack();
                int needAmount = getStealAmount(stk);
                if (needAmount <= 0) continue;

                if ((smart.getValue() && needAmount >= stk.getCount()) || forceMove.getValue()) {
                    mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                    deductStealCount(stk, stk.getCount());
                    took = true;
                    continue;
                }

                for (int slot1 = 0; slot1 < 36; ++slot1) {
                    ItemStack invStack = mc.player.getInventory().getStack(slot1);
                    if (invStack.isEmpty() || !invStack.isStackable() || invStack.getItem() != stk.getItem() || invStack.getCount() >= invStack.getMaxCount())
                        continue;
                    int targetSyncId = slot1 + 27;
                    mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(shulker.syncId, targetSyncId, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                    deductStealCount(stk, needAmount);
                    took = true;
                    break;
                }
            }
        }
        if (autoDisable.getValue() && !took) timeoutToDisable();
    }

    private void timeoutToDisable() {
        if (disableTimer.passed(disableTime.getValueInt())) {
            disable();
        }
    }

    private void updateStealCount() {
        stealCountList[0] = (int) (crystal.getValue() - InventoryUtil.getItemCount(Items.END_CRYSTAL));
        stealCountList[1] = (int) (exp.getValue() - InventoryUtil.getItemCount(Items.EXPERIENCE_BOTTLE));
        stealCountList[2] = (int) (totem.getValue() - InventoryUtil.getItemCount(Items.TOTEM_OF_UNDYING));
        stealCountList[3] = (int) (gapple.getValue() - InventoryUtil.getItemCount(Items.ENCHANTED_GOLDEN_APPLE));
        stealCountList[4] = (int) (obsidian.getValue() - InventoryUtil.getItemCount(Blocks.OBSIDIAN.asItem()));
        stealCountList[5] = (int) (web.getValue() - InventoryUtil.getItemCount(Blocks.COBWEB.asItem()));
        stealCountList[6] = (int) (glowstone.getValue() - InventoryUtil.getItemCount(Blocks.GLOWSTONE.asItem()));
        stealCountList[7] = (int) (anchor.getValue() - InventoryUtil.getItemCount(Blocks.RESPAWN_ANCHOR.asItem()));
        stealCountList[8] = (int) (pearl.getValue() - InventoryUtil.getItemCount(Items.ENDER_PEARL));
        stealCountList[9] = (int) (piston.getValue() - InventoryUtil.getItemCount(Blocks.PISTON.asItem()) - InventoryUtil.getItemCount(Blocks.STICKY_PISTON.asItem()));
        stealCountList[10] = (int) (redstone.getValue() - InventoryUtil.getItemCount(Blocks.REDSTONE_BLOCK.asItem()));
        stealCountList[11] = (int) (bed.getValue() - InventoryUtil.getItemCount(BedBlock.class));
        stealCountList[12] = (int) (speed.getValue() - InventoryUtil.getPotionCount(StatusEffects.SPEED.value()));
        stealCountList[13] = (int) (resistance.getValue() - InventoryUtil.getPotionCount(StatusEffects.RESISTANCE.value()));
        stealCountList[14] = (int) (strength.getValue() - InventoryUtil.getPotionCount(StatusEffects.STRENGTH.value()));
    }

    private int getStealAmount(ItemStack stack) {
        int needed = 0;
        if (stack.getItem() == Items.END_CRYSTAL) needed = stealCountList[0];
        else if (stack.getItem() == Items.EXPERIENCE_BOTTLE) needed = stealCountList[1];
        else if (stack.getItem() == Items.TOTEM_OF_UNDYING) needed = stealCountList[2];
        else if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) needed = stealCountList[3];
        else if (stack.getItem() == Blocks.OBSIDIAN.asItem()) needed = stealCountList[4];
        else if (stack.getItem() == Blocks.COBWEB.asItem()) needed = stealCountList[5];
        else if (stack.getItem() == Blocks.GLOWSTONE.asItem()) needed = stealCountList[6];
        else if (stack.getItem() == Blocks.RESPAWN_ANCHOR.asItem()) needed = stealCountList[7];
        else if (stack.getItem() == Items.ENDER_PEARL) needed = stealCountList[8];
        else if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof PistonBlock) needed = stealCountList[9];
        else if (stack.getItem() == Blocks.REDSTONE_BLOCK.asItem()) needed = stealCountList[10];
        else if (stack.getItem() instanceof BlockItem bi2 && bi2.getBlock() instanceof BedBlock) needed = stealCountList[11];
        else if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            PotionContentsComponent potion = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potion.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value() && stealCountList[12] > 0) needed = 1;
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value() && stealCountList[13] > 0) needed = 1;
                if (effect.getEffectType().value() == StatusEffects.STRENGTH.value() && stealCountList[14] > 0) needed = 1;
                if (needed > 0) break;
            }
        }
        if (needed <= 0) return 0;
        return Math.min(needed, stack.getCount());
    }

    private void deductStealCount(ItemStack stack, int amount) {
        if (stack.getItem() == Items.END_CRYSTAL) stealCountList[0] -= amount;
        else if (stack.getItem() == Items.EXPERIENCE_BOTTLE) stealCountList[1] -= amount;
        else if (stack.getItem() == Items.TOTEM_OF_UNDYING) stealCountList[2] -= amount;
        else if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) stealCountList[3] -= amount;
        else if (stack.getItem() == Blocks.OBSIDIAN.asItem()) stealCountList[4] -= amount;
        else if (stack.getItem() == Blocks.COBWEB.asItem()) stealCountList[5] -= amount;
        else if (stack.getItem() == Blocks.GLOWSTONE.asItem()) stealCountList[6] -= amount;
        else if (stack.getItem() == Blocks.RESPAWN_ANCHOR.asItem()) stealCountList[7] -= amount;
        else if (stack.getItem() == Items.ENDER_PEARL) stealCountList[8] -= amount;
        else if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof PistonBlock) stealCountList[9] -= amount;
        else if (stack.getItem() == Blocks.REDSTONE_BLOCK.asItem()) stealCountList[10] -= amount;
        else if (stack.getItem() instanceof BlockItem bi2 && bi2.getBlock() instanceof BedBlock) stealCountList[11] -= amount;
        else if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            PotionContentsComponent potion = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potion.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value()) stealCountList[12] -= amount;
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value()) stealCountList[13] -= amount;
                if (effect.getEffectType().value() == StatusEffects.STRENGTH.value()) stealCountList[14] -= amount;
            }
        }
    }
}