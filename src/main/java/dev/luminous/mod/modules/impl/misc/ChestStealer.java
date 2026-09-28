package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.Alien;
import dev.luminous.core.Manager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestStealer extends Module {

    private final BooleanSetting rotate;
    private final BooleanSetting autoDisable;
    private final SliderSetting disableTime;
    private final SliderSetting range;
    private final SliderSetting minRange;
    private final BooleanSetting inventory;
    private final BooleanSetting mine;
    private final BooleanSetting shulkerBox;
    private final BooleanSetting chest;
    private final BooleanSetting closeGUI;
    private final BindSetting placeKey;
    private final SliderSetting delay;
    private final EnumSetting<TakeMode> takeMode;

    private boolean wasPlaceKeyPressed;
    private final Timer timer = new Timer();
    private final Timer delayTimer = new Timer();
    private final Timer timeoutTimer = new Timer();
    private final Map<Integer, Item> desiredInventory = new HashMap<>();
    private final List<BlockPos> openList = new ArrayList<>();
    private BlockPos placePos;
    private BlockPos openPos;
    private boolean opened;
    private boolean wasInGui = false;

    public enum TakeMode {
        PickClick,
        Batch,
        Swap   // 新增强制替换模式（REKIT 逻辑）
    }

    public ChestStealer() {
        super("ChestStealer", Module.Category.Misc);
        this.takeMode = add(new EnumSetting<>("TakeMode", TakeMode.PickClick));
        this.autoDisable = add(new BooleanSetting("AutoDisable", false));
        this.disableTime = add(new SliderSetting("DisableTime", 500, 0, 1000));
        this.delay = add(new SliderSetting("Delay", 0, 0, 500, 10));
        this.range = add(new SliderSetting("Range", 4.0, 0.0, 6.0, 0.1));
        this.minRange = add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
        this.inventory = add(new BooleanSetting("InventorySwap", true));
        this.mine = add(new BooleanSetting("Mine", false));
        this.rotate = add(new BooleanSetting("Rotate", true));
        this.shulkerBox = add(new BooleanSetting("ShulkerBox", true));
        this.chest = add(new BooleanSetting("Chest", false));
        this.closeGUI = add(new BooleanSetting("CloseGUI", false));
        this.placeKey = add(new BindSetting("PlaceKey", -1));
        setChinese("智能补给");
    }

    @Override
    public void onEnable() {
        this.opened = false;
        this.openPos = null;
        this.placePos = null;
        this.timeoutTimer.reset();
        this.delayTimer.reset();
        this.desiredInventory.clear();
        this.openList.clear();

        try {
            loadKit();
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            sendMessage("§cFailed to load kit!");
            disable();
            return;
        }

        if (this.desiredInventory.isEmpty()) {
            String currentKit = Alien.KIT_CONFIG != null ? Alien.KIT_CONFIG.getCurrentKit() : "";
            if (currentKit.isEmpty()) {
                sendMessage("§cNo kit selected. Use /kit load <name> to load a kit first.");
            } else {
                sendMessage("§cKit [" + currentKit + "] is empty or not found!");
            }
            disable();
            return;
        }
    }

    @Override
    public void onDisable() {
        this.opened = false;
        if (this.mine.getValue() && this.placePos != null && this.closeGUI.getValue()) {
            PacketMine.INSTANCE.mine(this.placePos);
        }
        this.placePos = null;
        this.openPos = null;
        this.desiredInventory.clear();
        this.openList.clear();
    }

    public void reloadKit() {
        this.desiredInventory.clear();
        try {
            loadKit();
            String currentKit = Alien.KIT_CONFIG != null ? Alien.KIT_CONFIG.getCurrentKit() : "";
            if (!this.desiredInventory.isEmpty()) {
                sendMessage("§fKit [" + currentKit + "] reloaded");
            } else if (!currentKit.isEmpty()) {
                sendMessage("§cKit [" + currentKit + "] is empty or not found!");
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            sendMessage("§cFailed to reload kit!");
        }
    }

    private void loadKit() throws IOException, NumberFormatException {
        File file;
        try {
            String currentKit = Alien.KIT_CONFIG != null ? Alien.KIT_CONFIG.getCurrentKit() : "";
            if (currentKit.isEmpty()) {
                return;
            }
            File folder = Manager.getFile("kits");
            file = new File(folder, currentKit + ".kit");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    try {
                        int slot = Integer.parseInt(parts[0]);
                        String key = parts[1];
                        Item item = Items.AIR;

                        for (Item i : Registries.ITEM) {
                            if (i.getTranslationKey().equals(key) || i.toString().contains(key)) {
                                item = i;
                                break;
                            }
                        }

                        if (item != Items.AIR) {
                            this.desiredInventory.put(slot, item);
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }
    }

    // ========== 放置容器相关 ==========

    private void doPlace() {
        var player = mc.player;
        var world = mc.world;
        int oldSlot = player.getInventory().selectedSlot;
        double distance = 100.0;
        BlockPos bestPos = null;

        float rangeVal = (float) this.range.getValue();
        float minRangeVal = (float) this.minRange.getValue();

        for (BlockPos pos : BlockUtil.getSphere(rangeVal)) {
            if (!world.isAir(pos.up())) continue;

            BlockPos belowPos = pos.down();
            BlockState belowState = world.getBlockState(belowPos);

            if (belowState.isAir()) continue;
            if (!belowState.isSolidBlock(world, belowPos)) continue;
            if (MathHelper.sqrt((float) player.squaredDistanceTo(pos.toCenterPos())) < minRangeVal) continue;
            if (!BlockUtil.clientCanPlace(pos, false)) continue;
            if (!BlockUtil.isStrictDirection(belowPos, Direction.UP)) continue;
            if (!BlockUtil.canClick(belowPos)) continue;

            double currentDistance = MathHelper.sqrt((float) player.squaredDistanceTo(pos.toCenterPos()));
            if (bestPos == null || currentDistance < distance) {
                distance = currentDistance;
                bestPos = pos;
            }
        }

        if (bestPos != null) {
            int containerSlot = findContainer();
            if (containerSlot == -1) {
                sendMessage("§4No shulkerbox or enderchest found.");
                return;
            }

            if (this.inventory.getValue()) {
                InventoryUtil.inventorySwap(containerSlot, oldSlot);
                placeBlock(bestPos);
                this.placePos = bestPos;
                InventoryUtil.inventorySwap(containerSlot, oldSlot);
            } else {
                InventoryUtil.switchToSlot(containerSlot);
                placeBlock(bestPos);
                this.placePos = bestPos;
                InventoryUtil.switchToSlot(oldSlot);
            }
            this.timer.reset();
            return;
        }

        sendMessage("§4No place position found.");
    }

    private void placeBlock(BlockPos pos) {
        BlockUtil.clickBlock(pos.down(), Direction.UP, this.rotate.getValue());
    }

    private int findContainer() {
        var player = mc.player;
        var inventory = player.getInventory();
        boolean invMode = this.inventory.getValue();

        if (invMode) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    Item item = stack.getItem();
                    if (item instanceof net.minecraft.item.BlockItem blockItem) {
                        if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                            return i < 9 ? i + 36 : i;
                        }
                    }
                }
            }
        } else {
            int slot = InventoryUtil.findClass(ShulkerBoxBlock.class);
            if (slot != -1) return slot;
        }

        if (invMode) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty() && stack.getItem() == Items.ENDER_CHEST) {
                    return i < 9 ? i + 36 : i;
                }
            }
            return -1;
        }
        return InventoryUtil.findItem(Items.ENDER_CHEST);
    }

    // ========== 事件监听 ==========

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        var player = mc.player;
        var world = mc.world;

        openList.removeIf(pos -> {
            Block block = world.getBlockState(pos).getBlock();
            return !(block instanceof ShulkerBoxBlock) && block != Blocks.ENDER_CHEST;
        });

        boolean isInGui = mc.currentScreen != null;
        boolean isInShulkerGui = mc.currentScreen instanceof ShulkerBoxScreen;
        ScreenHandler currentHandler = player.currentScreenHandler;
        boolean isInChestGui = currentHandler instanceof GenericContainerScreenHandler;

        boolean justOpenedGui = isInGui && !this.wasInGui;
        this.wasInGui = isInGui;

        if (!isInGui || (!isInShulkerGui && !isInChestGui)) {
            if (this.opened) {
                this.opened = false;
                this.placePos = null;
                this.delayTimer.reset();
                if (this.mine.getValue() && this.openPos != null && this.closeGUI.getValue()) {
                    if (world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        PacketMine.INSTANCE.mine(this.openPos);
                    } else {
                        this.openPos = null;
                    }
                }
                if (this.autoDisable.getValue()) {
                    disable();
                }
                return;
            }

            if (this.placePos != null) {
                Block block = world.getBlockState(this.placePos).getBlock();
                if ((block instanceof ShulkerBoxBlock) || block == Blocks.ENDER_CHEST) {
                    this.openPos = this.placePos;
                    BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                }
                return;
            }

            boolean isPlaceKeyPressed = this.placeKey.isPressed();
            if (isPlaceKeyPressed && !this.wasPlaceKeyPressed && findContainer() != -1) {
                doPlace();
            }
            this.wasPlaceKeyPressed = isPlaceKeyPressed;
            return;
        }

        this.opened = true;
        if (this.openPos != null) {
            this.openList.add(this.openPos);
        }

        ScreenHandler handler = player.currentScreenHandler;

        if (handler instanceof ShulkerBoxScreenHandler && shulkerBox.getValue()) {
            processContainer(handler, handler.syncId);
            return;
        }

        if (handler instanceof GenericContainerScreenHandler && chest.getValue()) {
            processContainer(handler, handler.syncId);
        }
    }

    // ========== 分发处理 ==========

    private void processContainer(ScreenHandler handler, int syncId) {
        int delayMs = this.delay.getValueInt();
        if (delayMs > 0 && !this.delayTimer.passedMs(delayMs)) {
            return;
        }

        switch (takeMode.getValue()) {
            case PickClick -> performStealing(handler, syncId);
            case Batch     -> performStealingBatch(handler, syncId);
            case Swap      -> performStealingSwap(handler, syncId);
        }

        this.delayTimer.reset();
    }

    // ========== 序列发包实现 (Batch 模式) ==========

    private void performStealingBatch(ScreenHandler handler, int syncId) {
        var player = mc.player;
        if (player == null) return;

        // 1. 建立容器快照（仅关注前 27 格）
        List<ItemStack> containerSnapshot = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            containerSnapshot.add(handler.getSlot(i).getStack().copy());
        }

        // 2. 建立玩家背包快照
        var playerInv = player.getInventory();
        List<ItemStack> invSnapshot = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            invSnapshot.add(playerInv.getStack(i).copy());
        }

        // 3. 计算需要发送的包序列
        List<ClickSlotC2SPacket> packets = new ArrayList<>();
        int stateId = handler.getRevision();

        for (var entry : desiredInventory.entrySet()) {
            int targetSlot = entry.getKey();
            Item targetItem = entry.getValue();

            ItemStack current = invSnapshot.get(targetSlot);

            if (!current.isEmpty() && current.getItem() != targetItem) {
                continue;
            }

            int need = targetItem.getMaxCount() - current.getCount();
            if (need <= 0) continue;

            for (int contSlot = 0; contSlot < 27; contSlot++) {
                if (need <= 0) break;
                ItemStack contStack = containerSnapshot.get(contSlot);
                if (contStack.isEmpty() || contStack.getItem() != targetItem) continue;

                int take = Math.min(contStack.getCount(), need);
                int containerWindowId = handler.getSlot(contSlot).id;
                int invWindowId = getWindowSlotIdForInventory(handler, targetSlot);

                if (invWindowId == -1) continue;

                if (targetSlot >= 0 && targetSlot <= 8) {
                    packets.add(new ClickSlotC2SPacket(
                            syncId, stateId,
                            containerWindowId,
                            targetSlot,
                            SlotActionType.SWAP,
                            ItemStack.EMPTY,
                            new Int2ObjectArrayMap<>()
                    ));
                } else {
                    packets.add(new ClickSlotC2SPacket(
                            syncId, stateId,
                            containerWindowId, 0,
                            SlotActionType.PICKUP,
                            ItemStack.EMPTY,
                            new Int2ObjectArrayMap<>()
                    ));
                    packets.add(new ClickSlotC2SPacket(
                            syncId, stateId,
                            invWindowId, 0,
                            SlotActionType.PICKUP,
                            ItemStack.EMPTY,
                            new Int2ObjectArrayMap<>()
                    ));
                }

                contStack.decrement(take);
                if (current.isEmpty()) {
                    current = new ItemStack(targetItem, take);
                } else {
                    current.increment(take);
                }
                invSnapshot.set(targetSlot, current);
                need -= take;
            }
        }

        for (ClickSlotC2SPacket packet : packets) {
            mc.getNetworkHandler().sendPacket(packet);
        }

        if (isKitted() && closeGUI.getValue()) {
            player.closeHandledScreen();
        }
    }

    private int getWindowSlotIdForInventory(ScreenHandler handler, int invSlot) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == invSlot) {
                return slot.id;
            }
        }
        return -1;
    }

    // ========== PickClick 原有逻辑 ==========

    private void performStealing(ScreenHandler handler, int syncId) {
        var player = mc.player;
        var interactionManager = mc.interactionManager;
        var playerInventory = player.getInventory();

        try {
            int maxIterations = 100;
            int iteration = 0;
            boolean stoleInThisIteration;

            do {
                stoleInThisIteration = false;
                iteration++;
                if (iteration > maxIterations) break;
                if (mc.player == null || mc.player.currentScreenHandler != handler) break;

                for (Map.Entry<Integer, Item> entry : this.desiredInventory.entrySet()) {
                    if (mc.player == null || mc.player.currentScreenHandler != handler) break;

                    int targetSlot = entry.getKey();
                    Item targetItem = entry.getValue();
                    ItemStack playerStack = playerInventory.getStack(targetSlot);

                    if (mc.currentScreen instanceof ShulkerBoxScreen
                            && !playerStack.isEmpty()
                            && playerStack.getItem() instanceof net.minecraft.item.BlockItem blockItem
                            && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                        continue;
                    }

                    boolean needItem = playerStack.isEmpty()
                            || playerStack.getItem() != targetItem
                            || playerStack.getCount() < playerStack.getMaxCount();

                    if (needItem) {
                        for (int i = 0; i < 27; i++) {
                            Slot slot = handler.getSlot(i);
                            if (slot == null) continue;
                            ItemStack containerStack = slot.getStack();
                            if (containerStack.isEmpty() || containerStack.getItem() != targetItem) continue;

                            boolean canTake = true;
                            if (!playerStack.isEmpty() && playerStack.getItem() == targetItem) {
                                if (playerStack.getCount() >= playerStack.getMaxCount()) {
                                    canTake = false;
                                } else if (!ItemStack.areItemsAndComponentsEqual(playerStack, containerStack)) {
                                    canTake = false;
                                }
                            }
                            if (!canTake) continue;

                            int windowSlotId = getWindowSlotIdForInventory(handler, targetSlot);
                            interactionManager.clickSlot(syncId, slot.id, 0, SlotActionType.PICKUP, player);
                            interactionManager.clickSlot(syncId, windowSlotId, 0, SlotActionType.PICKUP, player);
                            if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                                interactionManager.clickSlot(syncId, slot.id, 0, SlotActionType.PICKUP, player);
                            }

                            stoleInThisIteration = true;
                            break;
                        }
                    }
                }
            } while (stoleInThisIteration && mc.player != null && mc.player.currentScreenHandler == handler);

            if (isKitted() && this.closeGUI.getValue() && mc.player != null) {
                mc.player.closeHandledScreen();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========== Swap 模式 (REKIT) 实现 ==========

    private void performStealingSwap(ScreenHandler handler, int syncId) {
        var player = mc.player;
        if (player == null) return;

        var interactionManager = mc.interactionManager;
        var playerInventory = player.getInventory();
        boolean isShulkerContainer = handler instanceof ShulkerBoxScreenHandler;

        for (var entry : desiredInventory.entrySet()) {
            int targetSlot = entry.getKey();
            Item targetItem = entry.getValue();
            ItemStack playerStack = playerInventory.getStack(targetSlot);

            // 已经正确且满 → 跳过
            if (!playerStack.isEmpty()
                    && playerStack.getItem() == targetItem
                    && playerStack.getCount() >= targetItem.getMaxCount()) {
                continue;
            }

            // 潜影盒保护：若容器是潜影盒，禁止放入任何潜影盒
            if (isShulkerContainer && isShulkerBoxItem(playerStack)) {
                continue; // 跳过，不在潜影盒容器内操作潜影盒
            }

            // 情况一：目标槽位是其它物品 → 先把垃圾放回容器空位
            if (!playerStack.isEmpty() && playerStack.getItem() != targetItem) {
                // 寻找容器空位（0-26）
                int emptyContainerSlot = -1;
                for (int i = 0; i < 27; i++) {
                    if (handler.getSlot(i).getStack().isEmpty()) {
                        emptyContainerSlot = i;
                        break;
                    }
                }
                if (emptyContainerSlot == -1) {
                    continue; // 容器满，无法交换，保留原物品
                }

                // 将垃圾物品移至容器空位
                interactionManager.clickSlot(
                        syncId,
                        getWindowSlotIdForInventory(handler, targetSlot),
                        0,
                        SlotActionType.PICKUP,
                        player
                );
                interactionManager.clickSlot(
                        syncId,
                        handler.getSlot(emptyContainerSlot).id,
                        0,
                        SlotActionType.PICKUP,
                        player
                );
                // 若光标还有残余，放回原槽位（应为空）
                if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                    interactionManager.clickSlot(
                            syncId,
                            getWindowSlotIdForInventory(handler, targetSlot),
                            0,
                            SlotActionType.PICKUP,
                            player
                    );
                }

                // 更新 playerStack 引用
                playerStack = playerInventory.getStack(targetSlot);
            }

            // 情况二：目标槽位空或已有目标物品但未满 → 从容器补充
            int need = targetItem.getMaxCount() - (playerStack.isEmpty() ? 0 : playerStack.getCount());
            if (need <= 0) continue;

            for (int contSlot = 0; contSlot < 27 && need > 0; contSlot++) {
                ItemStack contStack = handler.getSlot(contSlot).getStack();
                if (contStack.isEmpty() || contStack.getItem() != targetItem) continue;

                // 潜影盒保护：不从潜影盒容器拿出潜影盒（一般不会出现，但安全起见）
                if (isShulkerContainer && isShulkerBoxItem(contStack)) continue;

                int take = Math.min(contStack.getCount(), need);

                // 拿起容器物品
                interactionManager.clickSlot(
                        syncId,
                        handler.getSlot(contSlot).id,
                        0,
                        SlotActionType.PICKUP,
                        player
                );
                // 放入目标背包槽位
                interactionManager.clickSlot(
                        syncId,
                        getWindowSlotIdForInventory(handler, targetSlot),
                        0,
                        SlotActionType.PICKUP,
                        player
                );
                // 若光标还有残余，放回容器原槽位
                if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                    interactionManager.clickSlot(
                            syncId,
                            handler.getSlot(contSlot).id,
                            0,
                            SlotActionType.PICKUP,
                            player
                    );
                }

                need -= take;
            }
        }

        if (isKitted() && closeGUI.getValue()) {
            player.closeHandledScreen();
        }
    }

    private boolean isShulkerBoxItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    // ========== 工具方法 ==========

    private boolean isKitted() {
        if (mc.player == null) return false;
        var playerInventory = mc.player.getInventory();

        // Batch 和 Swap 模式均要求所有目标槽位满且物品匹配
        if (takeMode.getValue() == TakeMode.Batch || takeMode.getValue() == TakeMode.Swap) {
            for (Map.Entry<Integer, Item> entry : desiredInventory.entrySet()) {
                int slot = entry.getKey();
                Item item = entry.getValue();
                ItemStack stack = playerInventory.getStack(slot);
                if (stack.getItem() != item) return false;
                if (stack.getCount() < stack.getMaxCount()) return false;
            }
            return true;
        }

        // PickClick 原始判断
        for (Map.Entry<Integer, Item> entry : desiredInventory.entrySet()) {
            int slot = entry.getKey();
            Item item = entry.getValue();
            ItemStack stack = playerInventory.getStack(slot);
            if (stack.getItem() != item) return false;
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxCount()) return false;
        }
        return true;
    }
}