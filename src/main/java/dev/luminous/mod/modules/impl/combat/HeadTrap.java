package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.PredictUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.nagisa.HoleKick;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConcretePowderBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class HeadTrap extends Module {
    public static HeadTrap INSTANCE;

    // 基础设置
    public final SliderSetting delay = this.add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    public final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 4.0, 1.0, 6.0).setSuffix("m"));
    public final SliderSetting blocksPer = this.add(new SliderSetting("BlocksPer", 1, 1, 8));
    public final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 2.0, 0.0, 50.0, 1.0));
    public final SliderSetting range = this.add(new SliderSetting("Range", 5.0, 1.0, 8.0).setSuffix("m"));
    public final SliderSetting maxTargetSpeed = this.add(new SliderSetting("MaxTargetSpeed", 8, 0, 20).setSuffix("km/h"));

    // Y轴高度限制
    private final BooleanSetting yRestriction = this.add(new BooleanSetting("YRestriction", false));
    private final SliderSetting maxYOffset = this.add(new SliderSetting("MaxYOffset", 2, 1, 5,
            () -> yRestriction.getValue()).setSuffix("m"));

    // 功能开关
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting helper = this.add(new BooleanSetting("Helper", true));
    private final BooleanSetting detectMining = this.add(new BooleanSetting("DetectMining", false));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting breakCrystal = this.add(new BooleanSetting("Break", true));

    // 优先使用的头部方块类型（含哭泣黑曜石选项）
    private final EnumSetting<Mode> headMode = this.add(new EnumSetting<>("BlockForHead", Mode.Obsidian));

    private final Timer timer = new Timer();
    public PlayerEntity target;
    private int progress = 0;

    public HeadTrap() {
        super("HeadTrap", Category.Combat);
        setChinese("自动盖头");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        progress = 0;
        target = null;

        // 联动检查：当HoleKick工作时，HeadTrap暂停工作
        if (HoleKick.INSTANCE.isOn() && HoleKick.INSTANCE.currentTarget != null) return;

        // 基础状态检查
        if (!mc.player.isOnGround()) return;
        if (!inventory.getValue() || EntityUtil.inInventory()) {
            if (!Blink.INSTANCE.isOn() || !Blink.INSTANCE.pauseModule.getValue()) {
                if (!usingPause.getValue() || !mc.player.isUsingItem()) {
                    if (timer.passed((long) delay.getValue())) {
                        target = CombatUtil.getClosestEnemy(range.getValue());
                        if (target == null) {
                            if (autoDisable.getValue()) disable();
                            return;
                        }

                        // 检查目标速度
                        double targetSpeed = getPlayerSpeed(target);
                        if (targetSpeed > maxTargetSpeed.getValue()) {
                            return;
                        }

                        // Y轴高度限制检查
                        if (yRestriction.getValue()) {
                            int targetBlockY = target.getBlockPos().getY();
                            int playerBlockY = mc.player.getBlockPos().getY();
                            if (targetBlockY - playerBlockY > maxYOffset.getValue()) {
                                return;
                            }
                        }

                        // 预测目标位置
                        Vec3d predictedPos = predictTicks.getValue() > 0.0
                                ? PredictUtil.getPos(target, predictTicks.getValueInt())
                                : target.getPos();

                        BlockPosX targetPos = new BlockPosX(predictedPos.getX(), predictedPos.getY(), predictedPos.getZ());
                        placeHeadBlock(targetPos);
                    }
                }
            }
        }
    }

    private void placeHeadBlock(BlockPos pos) {
        if (pos == null) return;
        if (progress >= blocksPer.getValue()) return;

        int headOffset = target.isCrawling() ? 1 : 2;
        BlockPos headPos = pos.up(headOffset);

        tryPlaceBlock(headPos);
        if (helper.getValue() && BlockUtil.getPlaceSide(headPos) == null) {
            BlockPos helperPos = getHelper(headPos);
            if (helperPos != null) {
                tryPlaceBlock(helperPos);
            }
        }
    }

    private void tryPlaceBlock(BlockPos pos) {
        if (pos == null) return;
        if (progress >= blocksPer.getValue()) return;
        // 新增：DetectMining 检查
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;
        if (!BlockUtil.canPlace(pos, placeRange.getValue(), breakCrystal.getValue())) return;
        if (MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())) > placeRange.getValue()) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        int blockSlot = getBlockSlotForMode();

        if (blockSlot != -1) {
            CombatUtil.attackCrystal(pos, rotate.getValue(), usingPause.getValue());
            doSwap(blockSlot);
            BlockUtil.placeBlock(pos, rotate.getValue());
            if (inventory.getValue()) {
                doSwap(blockSlot);
                EntityUtil.syncInventory();
            } else {
                doSwap(oldSlot);
            }
            timer.reset();
            progress++;
        }
    }

    /**
     * 获取放置方块的槽位，优先级顺序：
     * 1. BlockForHead 选定的方块
     * 2. 普通黑曜石（最终备选）
     */
    private int getBlockSlotForMode() {
        boolean useInv = inventory.getValue();
        int slot = -1;

        // 优先级1：查找选定模式的方块
        switch (headMode.getValue()) {
            case Obsidian:
                slot = useInv ? InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN)
                        : InventoryUtil.findBlock(Blocks.OBSIDIAN);
                break;
            case CryingObsidian:
                slot = useInv ? InventoryUtil.findBlockInventorySlot(Blocks.CRYING_OBSIDIAN)
                        : InventoryUtil.findBlock(Blocks.CRYING_OBSIDIAN);
                break;
            case Anchor:
                slot = useInv ? InventoryUtil.findBlockInventorySlot(Blocks.RESPAWN_ANCHOR)
                        : InventoryUtil.findBlock(Blocks.RESPAWN_ANCHOR);
                break;
            case Web:
                slot = useInv ? InventoryUtil.findBlockInventorySlot(Blocks.COBWEB)
                        : InventoryUtil.findBlock(Blocks.COBWEB);
                break;
            case Concrete:
                slot = useInv ? InventoryUtil.findClassInventorySlot(ConcretePowderBlock.class)
                        : InventoryUtil.findClass(ConcretePowderBlock.class);
                break;
        }

        if (slot != -1) return slot;

        // 优先级2：最终回退到普通黑曜石（若选定模式本身就黑曜石且未找到，也会再次尝试）
        return useInv ? InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN)
                : InventoryUtil.findBlock(Blocks.OBSIDIAN);
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private BlockPos getHelper(BlockPos pos) {
        if (!helper.getValue()) return null;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN || dir == Direction.UP) continue;
            BlockPos neighbor = pos.offset(dir);
            // 新增：DetectMining 过滤正在被挖掘的邻居
            if (detectMining.getValue() && Alien.BREAK.isMining(neighbor)) continue;
            if (BlockUtil.isStrictDirection(neighbor, dir.getOpposite())
                    && BlockUtil.canPlace(neighbor, placeRange.getValue(), breakCrystal.getValue())) {
                return neighbor;
            }
        }
        return null;
    }

    private double getPlayerSpeed(PlayerEntity player) {
        double dx = player.getX() - player.prevX;
        double dz = player.getZ() - player.prevZ;
        double speedPerTick = Math.sqrt(dx * dx + dz * dz);
        return speedPerTick * 20.0 * 3.6;
    }

    @Override
    public String getInfo() {
        return target != null ? target.getName().getString() : null;
    }

    private enum Mode {
        Obsidian,
        CryingObsidian,
        Anchor,
        Web,
        Concrete
    }
}