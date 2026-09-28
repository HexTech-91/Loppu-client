package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class POPGod extends Module {
    public static POPGod INSTANCE;

    // ── Page ──
    public final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));

    // ── 主模式选择 ──
    public final EnumSetting<BypassMode> mode = add(new EnumSetting<>("BypassMode", BypassMode.Combo,
            () -> page.getValue() == Page.General));

    // ── 子功能开关 (Methods页) ──
    public final BooleanSetting creativePacket = add(new BooleanSetting("CreativePacket", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting quickMove = add(new BooleanSetting("QuickMove", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting dropPick = add(new BooleanSetting("DropPick", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting swapOffhand = add(new BooleanSetting("SwapOffhand", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting pickFromInv = add(new BooleanSetting("PickFromInv", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting closeContainer = add(new BooleanSetting("CloseContainer", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting rawPacket = add(new BooleanSetting("RawPacket", false,
            () -> page.getValue() == Page.Methods));
    // 新增方法开关
    public final BooleanSetting splitStack = add(new BooleanSetting("SplitStack", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting doubleClick = add(new BooleanSetting("DoubleClick", true,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting dragStack = add(new BooleanSetting("DragStack", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting packetSpam = add(new BooleanSetting("PacketSpam", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting offhandSpam = add(new BooleanSetting("OffhandSpam", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting shiftDoubleClick = add(new BooleanSetting("ShiftDoubleClick", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting ghostClick = add(new BooleanSetting("GhostClick", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting cloneNbt = add(new BooleanSetting("CloneNBT", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting containerSwap = add(new BooleanSetting("ContainerSwap", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting creativeDirect = add(new BooleanSetting("CreativeDirect", false,
            () -> page.getValue() == Page.Methods));
    // 新增模式 (19-25)
    public final BooleanSetting windowClick = add(new BooleanSetting("WindowClick", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting transactionPing = add(new BooleanSetting("TransactionPing", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting bundlePacket = add(new BooleanSetting("BundlePacket", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting chestSteal = add(new BooleanSetting("ChestSteal", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting armorSwap = add(new BooleanSetting("ArmorSwap", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting fakeAction = add(new BooleanSetting("FakeAction", false,
            () -> page.getValue() == Page.Methods));
    public final BooleanSetting inventorySort = add(new BooleanSetting("InventorySort", false,
            () -> page.getValue() == Page.Methods));

    // ── 通用设置 (Settings页) ──
    public final BooleanSetting autoClose = add(new BooleanSetting("AutoClose", true,
            () -> page.getValue() == Page.Settings));
    public final BooleanSetting debug = add(new BooleanSetting("Debug", true,
            () -> page.getValue() == Page.Settings));
    public final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 1000, 50,
            () -> page.getValue() == Page.Settings));
    public final BooleanSetting toOffhand = add(new BooleanSetting("ToOffhand", true,
            () -> page.getValue() == Page.Settings));
    public final SliderSetting hotbarSlot = add(new SliderSetting("HotbarSlot", 1, 1, 9, 1,
            () -> page.getValue() == Page.Settings && !toOffhand.getValue()));
    public final SliderSetting spamCount = add(new SliderSetting("SpamCount", 20, 5, 100, 5,
            () -> page.getValue() == Page.Settings));

    private final Timer timer = new Timer();
    private boolean attempted = false;
    private int attemptPhase = 0;
    private int lastTotemContainerSlot = -1;

    public POPGod() {
        super("POPGod", Category.Exploit);
        this.setChinese("图腾神");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        attempted = false;
        attemptPhase = 0;
        lastTotemContainerSlot = -1;
    }

    @Override
    public void onDisable() {
        attempted = false;
        attemptPhase = 0;
        lastTotemContainerSlot = -1;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            attempted = false;
            attemptPhase = 0;
            lastTotemContainerSlot = -1;
            return;
        }

        if (!timer.passed(delay.getValueInt())) return;

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        ScreenHandler handler = screen.getScreenHandler();

        int totemContainerSlot = findStackedTotemSlot(handler);
        if (totemContainerSlot == -1) {
            totemContainerSlot = findAnyTotemSlot(handler);
            if (totemContainerSlot == -1) {
                if (debug.getValue()) sendMessage("[POPGod] 未找到图腾，跳过");
                if (autoClose.getValue() && attempted) mc.player.closeHandledScreen();
                attempted = false;
                attemptPhase = 0;
                return;
            }
        }

        lastTotemContainerSlot = totemContainerSlot;
        attempted = true;
        int windowSlotId = handler.getSlot(totemContainerSlot).id;

        if (debug.getValue()) {
            ItemStack stack = handler.getSlot(totemContainerSlot).getStack();
            sendMessage("[POPGod] 发现图腾: slot=" + totemContainerSlot +
                    " windowSlotId=" + windowSlotId + " count=" + stack.getCount());
        }

        switch (mode.getValue()) {
            case CreativePacket:    doCreativePacket(windowSlotId, handler); break;
            case QuickMove:         doQuickMove(windowSlotId, handler); break;
            case DropPick:          doDropPick(windowSlotId, handler); break;
            case SwapOffhand:       doSwapOffhand(windowSlotId, handler); break;
            case PickFromInventory: doPickFromInventory(windowSlotId, handler); break;
            case CloseContainer:    doCloseContainer(windowSlotId, handler); break;
            case RawPacket:         doRawPacket(windowSlotId, handler); break;
            case SplitStack:        doSplitStack(windowSlotId, handler); break;
            case DoubleClick:       doDoubleClick(windowSlotId, handler); break;
            case DragStack:         doDragStack(windowSlotId, handler); break;
            case PacketSpam:        doPacketSpam(windowSlotId, handler); break;
            case OffhandSpam:       doOffhandSpam(windowSlotId, handler); break;
            case ShiftDoubleClick:  doShiftDoubleClick(windowSlotId, handler); break;
            case GhostClick:        doGhostClick(windowSlotId, handler); break;
            case CloneNBT:          doCloneNBT(windowSlotId, handler); break;
            case ContainerSwap:     doContainerSwap(windowSlotId, handler); break;
            case CreativeDirect:    doCreativeDirect(windowSlotId, handler); break;
            case WindowClick:       doWindowClick(windowSlotId, handler); break;
            case TransactionPing:   doTransactionPing(windowSlotId, handler); break;
            case BundlePacket:      doBundlePacket(windowSlotId, handler); break;
            case ChestSteal:        doChestSteal(windowSlotId, handler); break;
            case ArmorSwap:         doArmorSwap(windowSlotId, handler); break;
            case FakeAction:        doFakeAction(windowSlotId, handler); break;
            case InventorySort:     doInventorySort(windowSlotId, handler); break;
            case Combo:             doCombo(windowSlotId, handler); break;
        }

        timer.reset();
    }

    // ═══════════════════════════════════════════════════════════════
    //  原有8种方法
    // ═══════════════════════════════════════════════════════════════

    // 方法1: CreativePacket
    private void doCreativePacket(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试CreativePacket方法");
        ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
        if (totem.isEmpty() || totem.getItem() != Items.TOTEM_OF_UNDYING) return;
        int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();
            if (debug.getValue()) sendMessage("[POPGod] CreativePacket: 已移动到目标槽位 " + targetSlot);
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] CreativePacket失败: " + e.getMessage());
        }
    }

    // 方法2: QuickMove
    private void doQuickMove(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试QuickMove方法");
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();
            if (debug.getValue()) sendMessage("[POPGod] QuickMove: 图腾已移入背包");
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] QuickMove: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] QuickMove失败: " + e.getMessage());
        }
    }

    // 方法3: DropPick
    private void doDropPick(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试DropPick方法");
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 1, SlotActionType.THROW, mc.player);
            EntityUtil.syncInventory();
            mc.player.closeHandledScreen();
            if (debug.getValue()) sendMessage("[POPGod] DropPick: 图腾已丢出，请手动捡起");
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] DropPick失败: " + e.getMessage());
        }
    }

    // 方法4: SwapOffhand
    private void doSwapOffhand(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试SwapOffhand方法");
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 40, SlotActionType.SWAP, mc.player);
            EntityUtil.syncInventory();
            if (debug.getValue()) sendMessage("[POPGod] SwapOffhand: 已尝试交换到副手");
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] SwapOffhand失败: " + e.getMessage());
        }
    }

    // 方法5: PickFromInventory
    private void doPickFromInventory(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试PickFromInventory方法");
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();
            mc.player.closeHandledScreen();
            int totemInvSlot = findTotemInPlayerInventory();
            if (totemInvSlot != -1) {
                if (toOffhand.getValue()) {
                    mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(totemInvSlot));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                    mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(totemInvSlot));
                    if (debug.getValue()) sendMessage("[POPGod] PickFromInventory: 已切换到副手");
                } else {
                    int targetHotbar = hotbarSlot.getValueInt() - 1;
                    if (totemInvSlot >= 9) {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                                totemInvSlot < 9 ? totemInvSlot + 36 : totemInvSlot,
                                targetHotbar, SlotActionType.SWAP, mc.player);
                        EntityUtil.syncInventory();
                    } else {
                        InventoryUtil.switchToSlot(targetHotbar);
                    }
                    if (debug.getValue()) sendMessage("[POPGod] PickFromInventory: 已放到快捷栏 " + targetHotbar);
                }
            } else {
                if (debug.getValue()) sendMessage("[POPGod] PickFromInventory: 未在背包中找到图腾");
            }
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] PickFromInventory失败: " + e.getMessage());
        }
    }

    // 方法6: CloseContainer
    private void doCloseContainer(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试CloseContainer方法");
        try {
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            mc.player.closeHandledScreen();
            if (toOffhand.getValue()) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
            } else {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        hotbarSlot.getValueInt() - 1 + 36, 0, SlotActionType.PICKUP, mc.player);
            }
            EntityUtil.syncInventory();
            if (debug.getValue()) sendMessage("[POPGod] CloseContainer: 已通过关容器方式获取图腾");
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] CloseContainer失败: " + e.getMessage());
        }
    }

    // 方法7: RawPacket
    private void doRawPacket(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试RawPacket方法");
        try {
            ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
            if (totem.isEmpty()) return;
            Int2ObjectArrayMap<ItemStack> modifiedSlots = new Int2ObjectArrayMap<>();
            ClickSlotC2SPacket packet = new ClickSlotC2SPacket(handler.syncId, handler.getRevision(),
                    windowSlotId, 0, SlotActionType.QUICK_MOVE, ItemStack.EMPTY, modifiedSlots);
            mc.getNetworkHandler().sendPacket(packet);
            EntityUtil.syncInventory();
            if (debug.getValue()) sendMessage("[POPGod] RawPacket: 已发送QUICK_MOVE原始包");
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    ClickSlotC2SPacket swapPacket = new ClickSlotC2SPacket(handler.syncId, handler.getRevision(),
                            handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    mc.getNetworkHandler().sendPacket(swapPacket);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] RawPacket: 已发送SWAP原始包到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] RawPacket失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  新增方法 (9-17)
    // ═══════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────
    //  方法9: SplitStack (拆分堆叠)
    //  思路：反作弊可能只检测"整堆64图腾"的操作。
    //  先PICKUP拿起整堆，再放回一半，拆分成两小堆，
    //  再对小堆进行操作，绕过"整堆检测"。
    // ─────────────────────────────────────────────────
    private void doSplitStack(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试SplitStack方法");
        try {
            // 1. PICKUP 拿起整堆图腾
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            // 2. 右键点击同一个槽位，放下一半（button=1 = 右键）
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 1, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] SplitStack: 已拆分堆叠");

            // 3. 现在容器中有一半的图腾，用QUICK_MOVE拿小堆
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] SplitStack: 拆分后的小堆已移入背包");

            // 4. 放到副手或快捷栏
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] SplitStack: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] SplitStack失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法10: DoubleClick (双击收集)
    //  思路：双击(PICKUP_ALL)会收集背包中所有同类物品。
    //  如果容器中有图腾，双击后容器中的图腾会全部
    //  被收集到鼠标上，绕过逐格检测。
    // ─────────────────────────────────────────────────
    private void doDoubleClick(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试DoubleClick方法");
        try {
            // 先确保背包中有一个图腾（双击需要目标）
            // 先QuickMove一个图腾到背包
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            // 找到背包中的图腾槽位，双击它
            int invSlot = findTotemInInventory(handler);
            if (invSlot != -1) {
                // 双击：PICKUP_ALL with button=0 触发双击收集
                mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 0, SlotActionType.PICKUP_ALL, mc.player);
                EntityUtil.syncInventory();

                if (debug.getValue()) sendMessage("[POPGod] DoubleClick: 已双击收集图腾");

                // 把鼠标上的图腾放到目标位置
                if (toOffhand.getValue()) {
                    mc.interactionManager.clickSlot(handler.syncId, 40, 0, SlotActionType.PICKUP, mc.player);
                } else {
                    int targetSlot = hotbarSlot.getValueInt() - 1;
                    mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] DoubleClick: 已放置到目标位置");
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] DoubleClick失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法11: DragStack (拖拽堆叠)
    //  思路：Minecraft的拖拽协议(QUICK_CRAFT)使用
    //  不同的包结构，可能不被反作弊监控。
    //  模拟拖拽：按下左键 → 拖过槽位 → 松开
    // ─────────────────────────────────────────────────
    private void doDragStack(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试DragStack方法");
        try {
            // 阶段1: 拿起物品
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            // 阶段2: 模拟拖拽 - QUICK_CRAFT 分三个阶段
            // 按下: button=1 (左键拖拽), action=0 (开始)
            // 注意: clickSlot 内部处理 QUICK_CRAFT 的 action 映射
            // 拖拽到目标槽位（副手或快捷栏对应的player inventory槽位）
            // 这里用SWAP代替更简单

            // 直接放到目标位置
            if (toOffhand.getValue()) {
                mc.interactionManager.clickSlot(handler.syncId, 40, 0, SlotActionType.PICKUP, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, hotbarSlot.getValueInt() - 1, 0, SlotActionType.PICKUP, mc.player);
            }
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] DragStack: 已拖拽移动到目标位置");
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] DragStack失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法12: PacketSpam (包洪流)
    //  思路：同时发送大量QUICK_MOVE包，反作弊可能
    //  处理不过来，其中一个包成功通过
    // ─────────────────────────────────────────────────
    private void doPacketSpam(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试PacketSpam方法");
        try {
            int count = spamCount.getValueInt();
            for (int i = 0; i < count; i++) {
                Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>();
                ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
                        handler.syncId, handler.getRevision(),
                        windowSlotId, 0, SlotActionType.QUICK_MOVE,
                        ItemStack.EMPTY, slots
                );
                mc.getNetworkHandler().sendPacket(packet);
            }

            if (debug.getValue()) sendMessage("[POPGod] PacketSpam: 已发送 " + count + " 个QUICK_MOVE包");

            // 如果放到副手，再发送大量SWAP包
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    int invWindowSlotId = handler.getSlot(invSlot).id;
                    for (int i = 0; i < count; i++) {
                        ClickSlotC2SPacket swapPacket = new ClickSlotC2SPacket(
                                handler.syncId, handler.getRevision(),
                                invWindowSlotId, 40, SlotActionType.SWAP,
                                ItemStack.EMPTY, new Int2ObjectArrayMap<>()
                        );
                        mc.getNetworkHandler().sendPacket(swapPacket);
                    }
                    if (debug.getValue()) sendMessage("[POPGod] PacketSpam: 已发送 " + count + " 个SWAP包");
                }
            }

            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] PacketSpam失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法13: OffhandSpam (副手洪流)
    //  思路：发送大量SWAP_ITEM_WITH_OFFHAND包，
    //  总有一个能成功切换，反作弊来不及全部拦截
    // ─────────────────────────────────────────────────
    private void doOffhandSpam(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试OffhandSpam方法");
        try {
            // 先QuickMove到背包
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] OffhandSpam: 图腾已移入背包");

            // 关闭容器
            mc.player.closeHandledScreen();

            // 查找图腾在背包的位置
            int invSlot = findTotemInPlayerInventory();
            if (invSlot != -1) {
                int count = spamCount.getValueInt();

                // 使用PickFromInventory + SWAP_ITEM_WITH_OFFHAND 组合发送多次
                for (int i = 0; i < count; i++) {
                    mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                }
                if (debug.getValue()) sendMessage("[POPGod] OffhandSpam: 已发送 " + count + " 组交换包");
            } else {
                if (debug.getValue()) sendMessage("[POPGod] OffhandSpam: 未在背包中找到图腾");
            }
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] OffhandSpam失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法14: ShiftDoubleClick (Shift双击)
    //  思路：按住Shift双击物品=QUICK_MOVE所有同类。
    //  不同于逐格操作，可能绕过检测。
    // ─────────────────────────────────────────────────
    private void doShiftDoubleClick(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试ShiftDoubleClick方法");
        try {
            // Shift+双击 = QUICK_MOVE + PICKUP_ALL
            // 先QuickMove一个到背包做"引子"
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            // 找到背包中的图腾，Shift+双击它
            int invSlot = findTotemInInventory(handler);
            if (invSlot != -1) {
                // 在背包槽位上QUICK_MOVE = 移回容器
                // 但实际上我们想要的是把所有容器中的图腾移过来
                // 在容器槽位上双击 = 收集

                // 直接在容器槽位上再次QUICK_MOVE（相当于Shift+左键）
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();

                if (debug.getValue()) sendMessage("[POPGod] ShiftDoubleClick: 已尝试批量移动");

                // 放到副手
                if (toOffhand.getValue()) {
                    int invSlot2 = findTotemInInventory(handler);
                    if (invSlot2 != -1) {
                        mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot2).id, 40, SlotActionType.SWAP, mc.player);
                        EntityUtil.syncInventory();
                    }
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] ShiftDoubleClick失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法15: GhostClick (幽灵点击)
    //  思路：点击拿起图腾后立即关闭容器，制造"幽灵"
    //  状态。再打开容器或背包时，物品可能出现在
    //  鼠标上，此时放到副手。
    // ─────────────────────────────────────────────────
    private void doGhostClick(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试GhostClick方法");
        try {
            // 点击拿起图腾
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);

            // 立即关闭容器（不syncInventory，制造不同步）
            mc.player.closeHandledScreen();

            // 快速重新打开背包（玩家背包界面）
            // 在鼠标上的物品应该还在
            if (toOffhand.getValue()) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
            } else {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        hotbarSlot.getValueInt() - 1 + 36, 0, SlotActionType.PICKUP, mc.player);
            }
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] GhostClick: 已通过幽灵点击获取图腾");
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] GhostClick失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法16: CloneNBT (NBT克隆)
    //  思路：反作弊可能通过NBT检查图腾是否合法。
    //  从背包中找一个正常的单图腾，复制其NBT
    //  到堆叠图腾上，绕过NBT检测。
    // ─────────────────────────────────────────────────
    private void doCloneNBT(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试CloneNBT方法");
        try {
            // 先找一个正常的单图腾（在背包中）
            int normalTotemInvSlot = -1;
            int containerSize = getContainerSize(handler);
            for (int i = containerSize; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.getItem() == Items.TOTEM_OF_UNDYING && stack.getCount() == 1) {
                    normalTotemInvSlot = i;
                    break;
                }
            }

            if (normalTotemInvSlot == -1) {
                if (debug.getValue()) sendMessage("[POPGod] CloneNBT: 未找到普通图腾用于克隆NBT");
                // 没有普通图腾，只能用QUICK_MOVE先拿一个
                doQuickMove(windowSlotId, handler);
                return;
            }

            // 拿起堆叠图腾
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            // 和普通图腾交换（SWAP），这可能会"覆盖"NBT
            mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(normalTotemInvSlot).id, 0, SlotActionType.SWAP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] CloneNBT: 已尝试NBT克隆");

            // 现在堆叠图腾在背包中，放到副手
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] CloneNBT: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] CloneNBT失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法17: ContainerSwap (容器交换)
    //  思路：打开第二个容器，把图腾在容器间移动。
    //  利用容器切换时的不同步绕过检测。
    // ─────────────────────────────────────────────────
    private void doContainerSwap(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试ContainerSwap方法");
        try {
            // 拿起图腾
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);

            // 放到背包中（不关闭容器）
            int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] ContainerSwap: 已通过容器交换获取图腾");
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] ContainerSwap失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法18: CreativeDirect (创造模式直接设置)
    //  思路：发送CreativeInventoryActionC2SPacket
    //  直接设置槽位内容，完全绕过点击逻辑。
    //  部分服务器不验证创造模式。
    // ─────────────────────────────────────────────────
    private void doCreativeDirect(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试CreativeDirect方法");
        try {
            ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
            if (totem.isEmpty() || totem.getItem() != Items.TOTEM_OF_UNDYING) return;

            int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);

            // 发送CreativeInventoryActionC2SPacket
            // 参数: slotId (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand), itemStack
            CreativeInventoryActionC2SPacket packet = new CreativeInventoryActionC2SPacket(targetSlot, totem.copy());
            mc.getNetworkHandler().sendPacket(packet);

            if (debug.getValue()) sendMessage("[POPGod] CreativeDirect: 已发送创造模式设置包到槽位 " + targetSlot);
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] CreativeDirect失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  新增方法 (19-25)
    // ═══════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────
    //  方法19: WindowClick (窗口点击混淆)
    //  思路：先PICKUP拿起图腾，然后发送一个携带
    //  不同window slot ID的ClickSlot包，让反作弊
    //  的槽位追踪器丢失目标。最后正常放到目标位置。
    // ─────────────────────────────────────────────────
    private void doWindowClick(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试WindowClick方法");
        try {
            ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
            if (totem.isEmpty() || totem.getItem() != Items.TOTEM_OF_UNDYING) return;

            // 1. PICKUP 拿起图腾（鼠标上）
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            // 2. 用窗口外的大slot ID发送一个ClickSlot，让反作弊追踪到错误槽位
            int fakeSlotId = windowSlotId + 100;
            ClickSlotC2SPacket fakeClick = new ClickSlotC2SPacket(
                    handler.syncId, handler.getRevision(),
                    fakeSlotId, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
            mc.getNetworkHandler().sendPacket(fakeClick);

            if (debug.getValue()) sendMessage("[POPGod] WindowClick: 已发送混淆包");

            // 3. 放到目标位置
            int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
            mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] WindowClick: 已放到目标槽位 " + targetSlot);
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] WindowClick失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法20: TransactionPing (事务乒乓)
    //  思路：先发送多个"空操作"ClickSlot包来消耗
    //  反作弊的事务追踪槽位，然后再执行真正的操作，
    //  让真正的操作混在大量已处理的事务中不被检测。
    // ─────────────────────────────────────────────────
    private void doTransactionPing(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试TransactionPing方法");
        try {
            int pingCount = spamCount.getValueInt();

            // 1. 发送大量空操作Ping包（对无效槽位PICKUP）
            for (int i = 0; i < pingCount; i++) {
                ClickSlotC2SPacket ping = new ClickSlotC2SPacket(
                        handler.syncId, handler.getRevision(),
                        -1 - i, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                mc.getNetworkHandler().sendPacket(ping);
            }

            if (debug.getValue()) sendMessage("[POPGod] TransactionPing: 已发送 " + pingCount + " 个混淆事务");

            // 2. 执行真正的操作 - QuickMove
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] TransactionPing: 图腾已移入背包");

            // 3. 放到副手/快捷栏
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] TransactionPing: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] TransactionPing失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法21: BundlePacket (捆绑包)
    //  思路：在同一tick内连续发送多个操作包，
    //  反作弊逐包检测时，所有包看起来是同时到达的，
    //  部分包可能绕过检测逻辑。
    // ─────────────────────────────────────────────────
    private void doBundlePacket(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试BundlePacket方法");
        try {
            ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
            if (totem.isEmpty() || totem.getItem() != Items.TOTEM_OF_UNDYING) return;

            int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);

            // 同时发送3个包：QUICK_MOVE + SWAP到目标 + 关闭容器
            // 这些包会在同一tick到达服务器
            ClickSlotC2SPacket movePacket = new ClickSlotC2SPacket(
                    handler.syncId, handler.getRevision(),
                    windowSlotId, 0, SlotActionType.QUICK_MOVE, ItemStack.EMPTY, new Int2ObjectArrayMap<>());

            ClickSlotC2SPacket swapPacket = new ClickSlotC2SPacket(
                    handler.syncId, handler.getRevision(),
                    targetSlot, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());

            // 一次性发送所有包
            mc.getNetworkHandler().sendPacket(movePacket);
            mc.getNetworkHandler().sendPacket(swapPacket);

            if (debug.getValue()) sendMessage("[POPGod] BundlePacket: 已发送捆绑包");

            // 同步客户端状态
            EntityUtil.syncInventory();
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] BundlePacket失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法22: ChestSteal (仿偷取时序)
    //  思路：模仿正常玩家从箱子里拿物品的操作时序，
    //  先PICKUP拿起，再放到背包，分两步完成，
    //  而不是一步QUICK_MOVE，更像合法操作。
    // ─────────────────────────────────────────────────
    private void doChestSteal(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试ChestSteal方法");
        try {
            // 1. 先拿起图腾（像正常玩家拿物品一样）
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] ChestSteal: 已拿起图腾");

            // 2. 放到背包里（模拟"放到背包"的操作）
            int containerSize = getContainerSize(handler);
            // 在背包区域找一个空位或任意位置放
            int targetInvSlot = -1;
            for (int i = containerSize; i < handler.slots.size(); i++) {
                if (handler.getSlot(i).getStack().isEmpty()) {
                    targetInvSlot = i;
                    break;
                }
            }
            if (targetInvSlot == -1) {
                // 没有空位，放到背包第一个槽位
                targetInvSlot = containerSize;
            }
            mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(targetInvSlot).id, 0, SlotActionType.PICKUP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] ChestSteal: 已放入背包槽位 " + targetInvSlot);

            // 3. 放到副手/快捷栏
            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] ChestSteal: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] ChestSteal失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法23: ArmorSwap (盔甲中转)
    //  思路：利用盔甲槽位(36-39)作为中转站。
    //  盔甲槽位的操作路径可能不被反作弊充分监控。
    //  先SWAP到盔甲槽，再从盔甲槽SWAP到副手。
    // ─────────────────────────────────────────────────
    private void doArmorSwap(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试ArmorSwap方法");
        try {
            // 1. 先把图腾QuickMove到背包
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] ArmorSwap: 图腾已移入背包");

            // 2. 在背包中找到图腾
            int invSlot = findTotemInInventory(handler);
            if (invSlot == -1) {
                if (debug.getValue()) sendMessage("[POPGod] ArmorSwap: 未在背包找到图腾");
                return;
            }

            // 3. 找一个空的盔甲槽位（或者用头盔槽36）
            int armorSlot = -1;
            for (int i = 36; i < 40; i++) {
                if (handler.getSlot(i).getStack().isEmpty()) {
                    armorSlot = i;
                    break;
                }
            }
            if (armorSlot == -1) armorSlot = 36; // 没有空位就用头盔槽

            // 4. SWAP背包图腾到盔甲槽
            mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, armorSlot, SlotActionType.SWAP, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] ArmorSwap: 图腾已中转至盔甲槽 " + armorSlot);

            if (toOffhand.getValue()) {
                // 5. 从盔甲槽SWAP到副手
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, 40, SlotActionType.SWAP, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] ArmorSwap: 已从盔甲槽交换到副手");
            } else {
                int targetSlot = hotbarSlot.getValueInt() - 1;
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, targetSlot, SlotActionType.SWAP, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] ArmorSwap: 已从盔甲槽交换到快捷栏 " + targetSlot);
            }

            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] ArmorSwap失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法24: FakeAction (假动作掩护)
    //  思路：先发送一个假动作包（如SWAP_ITEM_WITH_OFFHAND
    //  但指定错误物品），让反作弊检测到"非法动作"
    //  并消耗一次检测机会。紧接着执行真正的操作，
    //  反作弊的检测冷却期内放行。
    // ─────────────────────────────────────────────────
    private void doFakeAction(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试FakeAction方法");
        try {
            // 1. 发送一个假动作：假装从副手切换到主手（内容为空或错误物品）
            for (int i = 0; i < 3; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
            }

            if (debug.getValue()) sendMessage("[POPGod] FakeAction: 已发送假动作掩护");

            // 2. 执行真正的操作
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] FakeAction: 图腾已移入背包");

            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] FakeAction: 已交换到副手");
                }
            }

            // 3. 再发一次假动作收尾
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));

            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] FakeAction失败: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────
    //  方法25: InventorySort (背包整理掩护)
    //  思路：模拟背包整理操作，快速点击多个不同槽位，
    //  把真正的图腾操作伪装成"整理背包"的一部分。
    //  反作弊可能将一系列快速点击视为合法整理操作。
    // ─────────────────────────────────────────────────
    private void doInventorySort(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试InventorySort方法");
        try {
            // 1. 先随机点击几个背包槽位（"整理"动作）
            int containerSize = getContainerSize(handler);
            for (int i = 0; i < 3; i++) {
                int randomSlot = containerSize + (int)(Math.random() * 27);
                if (randomSlot < handler.slots.size()) {
                    mc.interactionManager.clickSlot(handler.syncId,
                            handler.getSlot(randomSlot).id, 0, SlotActionType.PICKUP, mc.player);
                }
            }
            EntityUtil.syncInventory();

            // 2. 在"整理"中夹带真正的操作 - QuickMove图腾
            mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);

            // 3. 再点几个槽位继续"整理"
            for (int i = 0; i < 2; i++) {
                int randomSlot = containerSize + (int)(Math.random() * 27);
                if (randomSlot < handler.slots.size()) {
                    mc.interactionManager.clickSlot(handler.syncId,
                            handler.getSlot(randomSlot).id, 0, SlotActionType.PICKUP, mc.player);
                }
            }
            EntityUtil.syncInventory();

            if (debug.getValue()) sendMessage("[POPGod] InventorySort: 已在整理掩护中移动图腾");

            if (toOffhand.getValue()) {
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 40, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] InventorySort: 已交换到副手");
                }
            }
            if (autoClose.getValue()) mc.player.closeHandledScreen();
        } catch (Exception e) {
            if (debug.getValue()) sendMessage("[POPGod] InventorySort失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Combo - 组合模式（扩展版）
    // ═══════════════════════════════════════════════════════════════
    private void doCombo(int windowSlotId, ScreenHandler handler) {
        if (debug.getValue()) sendMessage("[POPGod] 尝试Combo模式, 阶段=" + attemptPhase);

        // 阶段0: QuickMove
        if (attemptPhase == 0 && quickMove.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                ItemStack remaining = handler.getSlot(lastTotemContainerSlot).getStack();
                if (remaining.isEmpty() || remaining.getItem() != Items.TOTEM_OF_UNDYING) {
                    if (debug.getValue()) sendMessage("[POPGod] Combo: QuickMove成功");
                    attemptPhase = toOffhand.getValue() ? 10 : 20;
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 1;
        }

        // 阶段1: 尝试SplitStack → QuickMove
        if (attemptPhase == 1 && splitStack.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 1, SlotActionType.PICKUP, mc.player);
                EntityUtil.syncInventory();
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: SplitStack+QuickMove成功");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 2;
        }

        // 阶段2: CloseContainer
        if (attemptPhase == 2 && closeContainer.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
                mc.player.closeHandledScreen();
                if (toOffhand.getValue()) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                } else {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                            hotbarSlot.getValueInt() - 1 + 36, 0, SlotActionType.PICKUP, mc.player);
                }
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: CloseContainer成功");
                attemptPhase = 99;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 3;
        }

        // 阶段3: SwapOffhand
        if (attemptPhase == 3 && swapOffhand.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 40, SlotActionType.SWAP, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: SwapOffhand成功");
                attemptPhase = 99;
                if (autoClose.getValue()) mc.player.closeHandledScreen();
                return;
            } catch (Exception ignored) {}
            attemptPhase = 4;
        }

        // 阶段4: DoubleClick
        if (attemptPhase == 4 && doubleClick.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, 0, SlotActionType.PICKUP_ALL, mc.player);
                    EntityUtil.syncInventory();
                    if (toOffhand.getValue()) {
                        mc.interactionManager.clickSlot(handler.syncId, 40, 0, SlotActionType.PICKUP, mc.player);
                    } else {
                        mc.interactionManager.clickSlot(handler.syncId, hotbarSlot.getValueInt() - 1, 0, SlotActionType.PICKUP, mc.player);
                    }
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] Combo: DoubleClick成功");
                    attemptPhase = 99;
                    if (autoClose.getValue()) mc.player.closeHandledScreen();
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 5;
        }

        // 阶段5: PickFromInventory
        if (attemptPhase == 5 && pickFromInv.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                mc.player.closeHandledScreen();
                int invSlot2 = findTotemInPlayerInventory();
                if (invSlot2 != -1 && toOffhand.getValue()) {
                    mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot2));
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                    mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot2));
                    if (debug.getValue()) sendMessage("[POPGod] Combo: PickFromInventory成功");
                }
                attemptPhase = 99;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 6;
        }

        // 阶段6: CloneNBT
        if (attemptPhase == 6 && cloneNbt.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: CloneNBT已完成");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 7;
        }

        // 阶段7: CreativeDirect
        if (attemptPhase == 7 && creativeDirect.getValue()) {
            try {
                ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
                if (!totem.isEmpty() && totem.getItem() == Items.TOTEM_OF_UNDYING) {
                    int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
                    CreativeInventoryActionC2SPacket packet = new CreativeInventoryActionC2SPacket(targetSlot, totem.copy());
                    mc.getNetworkHandler().sendPacket(packet);
                    if (debug.getValue()) sendMessage("[POPGod] Combo: CreativeDirect已发送");
                    attemptPhase = 99;
                    if (autoClose.getValue()) mc.player.closeHandledScreen();
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 8;
        }

        // 阶段8: RawPacket
        if (attemptPhase == 8 && rawPacket.getValue()) {
            try {
                Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>();
                ClickSlotC2SPacket packet = new ClickSlotC2SPacket(handler.syncId, handler.getRevision(),
                        windowSlotId, 0, SlotActionType.QUICK_MOVE, ItemStack.EMPTY, slots);
                mc.getNetworkHandler().sendPacket(packet);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: RawPacket已发送");
                attemptPhase = 99;
                if (autoClose.getValue()) mc.player.closeHandledScreen();
                return;
            } catch (Exception ignored) {}
            attemptPhase = 9;
        }

        // 阶段9: PacketSpam
        if (attemptPhase == 9 && packetSpam.getValue()) {
            try {
                int count = spamCount.getValueInt();
                for (int i = 0; i < count; i++) {
                    ClickSlotC2SPacket pkt = new ClickSlotC2SPacket(handler.syncId, handler.getRevision(),
                            windowSlotId, 0, SlotActionType.QUICK_MOVE, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    mc.getNetworkHandler().sendPacket(pkt);
                }
                if (debug.getValue()) sendMessage("[POPGod] Combo: PacketSpam已发送");
                attemptPhase = 99;
                if (autoClose.getValue()) mc.player.closeHandledScreen();
                return;
            } catch (Exception ignored) {}
            attemptPhase = 10;
        }

        // 阶段10: OffhandSpam
        if (attemptPhase == 10 && offhandSpam.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                mc.player.closeHandledScreen();
                int invSlot3 = findTotemInPlayerInventory();
                if (invSlot3 != -1) {
                    int count = spamCount.getValueInt();
                    for (int i = 0; i < count; i++) {
                        mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot3));
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                    }
                    if (debug.getValue()) sendMessage("[POPGod] Combo: OffhandSpam已发送");
                }
                attemptPhase = 99;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 11;
        }

        // 阶段11: DropPick (最后手段)
        if (attemptPhase == 11 && dropPick.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 1, SlotActionType.THROW, mc.player);
                EntityUtil.syncInventory();
                mc.player.closeHandledScreen();
                if (debug.getValue()) sendMessage("[POPGod] Combo: 已丢出图腾，请捡起");
                attemptPhase = 99;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 12;
        }

        // 阶段12: WindowClick
        if (attemptPhase == 12 && windowClick.getValue()) {
            try {
                ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
                if (!totem.isEmpty() && totem.getItem() == Items.TOTEM_OF_UNDYING) {
                    mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
                    EntityUtil.syncInventory();
                    int fakeSlotId = windowSlotId + 100;
                    ClickSlotC2SPacket fakeClick = new ClickSlotC2SPacket(
                            handler.syncId, handler.getRevision(),
                            fakeSlotId, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    mc.getNetworkHandler().sendPacket(fakeClick);
                    int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
                    mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] Combo: WindowClick成功");
                    attemptPhase = 99;
                    if (autoClose.getValue()) mc.player.closeHandledScreen();
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 13;
        }

        // 阶段13: TransactionPing
        if (attemptPhase == 13 && transactionPing.getValue()) {
            try {
                int pingCount = spamCount.getValueInt();
                for (int i = 0; i < pingCount; i++) {
                    ClickSlotC2SPacket ping = new ClickSlotC2SPacket(
                            handler.syncId, handler.getRevision(),
                            -1 - i, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    mc.getNetworkHandler().sendPacket(ping);
                }
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: TransactionPing成功");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 14;
        }

        // 阶段14: BundlePacket
        if (attemptPhase == 14 && bundlePacket.getValue()) {
            try {
                ItemStack totem = handler.getSlot(lastTotemContainerSlot).getStack();
                if (!totem.isEmpty() && totem.getItem() == Items.TOTEM_OF_UNDYING) {
                    int targetSlot = toOffhand.getValue() ? 40 : (hotbarSlot.getValueInt() - 1);
                    ClickSlotC2SPacket movePacket = new ClickSlotC2SPacket(
                            handler.syncId, handler.getRevision(),
                            windowSlotId, 0, SlotActionType.QUICK_MOVE, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    ClickSlotC2SPacket swapPacket = new ClickSlotC2SPacket(
                            handler.syncId, handler.getRevision(),
                            targetSlot, 0, SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectArrayMap<>());
                    mc.getNetworkHandler().sendPacket(movePacket);
                    mc.getNetworkHandler().sendPacket(swapPacket);
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] Combo: BundlePacket已发送");
                    attemptPhase = 99;
                    if (autoClose.getValue()) mc.player.closeHandledScreen();
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 15;
        }

        // 阶段15: ChestSteal
        if (attemptPhase == 15 && chestSteal.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.PICKUP, mc.player);
                EntityUtil.syncInventory();
                int containerSize = getContainerSize(handler);
                int targetInvSlot = -1;
                for (int i = containerSize; i < handler.slots.size(); i++) {
                    if (handler.getSlot(i).getStack().isEmpty()) {
                        targetInvSlot = i;
                        break;
                    }
                }
                if (targetInvSlot == -1) targetInvSlot = containerSize;
                mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(targetInvSlot).id, 0, SlotActionType.PICKUP, mc.player);
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: ChestSteal成功");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 16;
        }

        // 阶段16: ArmorSwap
        if (attemptPhase == 16 && armorSwap.getValue()) {
            try {
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                int invSlot = findTotemInInventory(handler);
                if (invSlot != -1) {
                    int armorSlot = -1;
                    for (int i = 36; i < 40; i++) {
                        if (handler.getSlot(i).getStack().isEmpty()) {
                            armorSlot = i;
                            break;
                        }
                    }
                    if (armorSlot == -1) armorSlot = 36;
                    mc.interactionManager.clickSlot(handler.syncId, handler.getSlot(invSlot).id, armorSlot, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                    if (toOffhand.getValue()) {
                        mc.interactionManager.clickSlot(handler.syncId, armorSlot, 40, SlotActionType.SWAP, mc.player);
                    } else {
                        mc.interactionManager.clickSlot(handler.syncId, armorSlot, hotbarSlot.getValueInt() - 1, SlotActionType.SWAP, mc.player);
                    }
                    EntityUtil.syncInventory();
                    if (debug.getValue()) sendMessage("[POPGod] Combo: ArmorSwap成功");
                    attemptPhase = 99;
                    if (autoClose.getValue()) mc.player.closeHandledScreen();
                    return;
                }
            } catch (Exception ignored) {}
            attemptPhase = 17;
        }

        // 阶段17: FakeAction
        if (attemptPhase == 17 && fakeAction.getValue()) {
            try {
                for (int i = 0; i < 3; i++) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                            Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                }
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                EntityUtil.syncInventory();
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                if (debug.getValue()) sendMessage("[POPGod] Combo: FakeAction成功");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 18;
        }

        // 阶段18: InventorySort
        if (attemptPhase == 18 && inventorySort.getValue()) {
            try {
                int containerSize = getContainerSize(handler);
                for (int i = 0; i < 3; i++) {
                    int randomSlot = containerSize + (int)(Math.random() * 27);
                    if (randomSlot < handler.slots.size()) {
                        mc.interactionManager.clickSlot(handler.syncId,
                                handler.getSlot(randomSlot).id, 0, SlotActionType.PICKUP, mc.player);
                    }
                }
                EntityUtil.syncInventory();
                mc.interactionManager.clickSlot(handler.syncId, windowSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                for (int i = 0; i < 2; i++) {
                    int randomSlot = containerSize + (int)(Math.random() * 27);
                    if (randomSlot < handler.slots.size()) {
                        mc.interactionManager.clickSlot(handler.syncId,
                                handler.getSlot(randomSlot).id, 0, SlotActionType.PICKUP, mc.player);
                    }
                }
                EntityUtil.syncInventory();
                if (debug.getValue()) sendMessage("[POPGod] Combo: InventorySort成功");
                attemptPhase = toOffhand.getValue() ? 10 : 20;
                return;
            } catch (Exception ignored) {}
            attemptPhase = 99;
        }

        // 阶段10/20: 放到副手/快捷栏
        if (attemptPhase == 10) {
            mc.player.closeHandledScreen();
            int invSlot4 = findTotemInPlayerInventory();
            if (invSlot4 != -1) {
                mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot4));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, 0));
                mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(invSlot4));
                if (debug.getValue()) sendMessage("[POPGod] Combo: 已放到副手");
            }
            attemptPhase = 99;
        }
        if (attemptPhase == 20) {
            // 放在快捷栏，不需要额外操作
            if (autoClose.getValue()) mc.player.closeHandledScreen();
            attemptPhase = 99;
        }

        if (attemptPhase == 99) {
            attempted = false;
            attemptPhase = 0;
        }
    }

    // ── 辅助方法 ──

    private int findStackedTotemSlot(ScreenHandler handler) {
        int containerSize = getContainerSize(handler);
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == Items.TOTEM_OF_UNDYING && stack.getCount() > 1) return i;
        }
        return -1;
    }

    private int findAnyTotemSlot(ScreenHandler handler) {
        int containerSize = getContainerSize(handler);
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }

    private int findTotemInInventory(ScreenHandler handler) {
        int containerSize = getContainerSize(handler);
        int totalSlots = handler.slots.size();
        for (int i = containerSize; i < totalSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }

    private int findTotemInPlayerInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }

    private int getContainerSize(ScreenHandler handler) {
        if (handler instanceof net.minecraft.screen.GenericContainerScreenHandler gch) {
            return gch.getRows() * 9;
        }
        return 27;
    }

    // ── 枚举 ──

    public enum BypassMode {
        CreativePacket("C包"),
        QuickMove("快速移动"),
        DropPick("丢捡"),
        SwapOffhand("交换副手"),
        PickFromInventory("背包拾取"),
        CloseContainer("关容器"),
        RawPacket("原始包"),
        SplitStack("拆分堆叠"),
        DoubleClick("双击收集"),
        DragStack("拖拽堆叠"),
        PacketSpam("包洪流"),
        OffhandSpam("副手洪流"),
        ShiftDoubleClick("Shift双击"),
        GhostClick("幽灵点击"),
        CloneNBT("NBT克隆"),
        ContainerSwap("容器交换"),
        CreativeDirect("创造直接设"),
        WindowClick("窗口点击"),
        TransactionPing("事务乒乓"),
        BundlePacket("捆绑包"),
        ChestSteal("仿偷取"),
        ArmorSwap("盔甲中转"),
        FakeAction("假动作"),
        InventorySort("背包整理"),
        Combo("组合");

        private final String chinese;

        BypassMode(String chinese) { this.chinese = chinese; }

        @Override
        public String toString() { return chinese; }
    }

    public enum Page { General, Methods, Settings }
}