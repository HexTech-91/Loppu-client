package dev.luminous.mod.modules.impl.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;

public class PearlIntercept extends Module {
    public static PearlIntercept INSTANCE;

    // 基础拦截设置
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 4.0, 1.0, 6.0).setSuffix("m"));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting usingPause = add(new BooleanSetting("UsingPause", true));

    // 拦截距离限制
    private final SliderSetting minDistance = add(new SliderSetting("MinDistance", 1.0, 0.0, 5.0, 0.1).setSuffix("m"));
    private final SliderSetting minLength = add(new SliderSetting("MinLength", 5.0, 0.0, 20.0, 0.1).setSuffix("m"));

    // Helper 辅助放置
    private final BooleanSetting helper = add(new BooleanSetting("Helper", true));
    // 新增：DetectMining 设置
    private final BooleanSetting detectMining = add(new BooleanSetting("DetectMining", false));

    // 轨迹渲染设置
    private final BooleanSetting render = add(new BooleanSetting("Render", true));
    private final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255, 255), this.render::getValue));
    private final BooleanSetting showAll = add(new BooleanSetting("ShowAll", false, this.render::getValue));

    private final Timer timer = new Timer();
    private EnderPearlEntity currentPearl;

    public PearlIntercept() {
        super("PearlIntercept", Category.Combat);
        this.setChinese("珍珠拦截");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentPearl = null;
        timer.reset();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (!inventory.getValue() || EntityUtil.inInventory()) {
            if (usingPause.getValue() && mc.player.isUsingItem()) return;
            if (!timer.passed((long) delay.getValue())) return;

            EnderPearlEntity targetPearl = findInterceptablePearl();
            if (targetPearl == null) {
                if (autoDisable.getValue()) disable();
                currentPearl = null;
                return;
            }

            BlockPos placePos = findPlacePos(targetPearl);
            if (placePos != null) {
                placeObsidian(placePos);
                currentPearl = targetPearl;
                timer.reset();
            }
        }
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (!render.getValue()) return;

        RenderSystem.disableDepthTest();
        if (showAll.getValue()) {
            // 渲染所有珍珠轨迹（包括自己和好友）
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl) {
                    drawPearlTrajectory(pearl, color.getValue(), matrixStack);
                }
            }
        } else {
            // 仅渲染当前拦截目标
            if (currentPearl != null && currentPearl.isAlive()) {
                drawPearlTrajectory(currentPearl, color.getValue(), matrixStack);
            }
        }
        RenderSystem.enableDepthTest();
    }

    private void drawPearlTrajectory(EnderPearlEntity pearl, Color color, MatrixStack matrixStack) {
        double motionX = pearl.getVelocity().x;
        double motionY = pearl.getVelocity().y;
        double motionZ = pearl.getVelocity().z;
        if (motionX == 0.0 && motionY == 0.0 && motionZ == 0.0) return;

        double x = pearl.getX();
        double y = pearl.getY();
        double z = pearl.getZ();

        for (int i = 0; i < 300; i++) {
            Vec3d lastPos = new Vec3d(x, y, z);

            BlockPos bp = new BlockPos((int) (x + motionX), (int) (y + motionY), (int) (z + motionZ));
            if (mc.world.getBlockState(bp).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }
            motionY -= 0.03;

            x += motionX;
            y += motionY;
            z += motionZ;
            Vec3d pos = new Vec3d(x, y, z);

            // 实体碰撞检测
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ArrowEntity || entity.equals(mc.player)) continue;
                Box pearlBox = new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3);
                if (entity.getBoundingBox().intersects(pearlBox)) {
                    Render3DUtil.drawBox(matrixStack, entity.getBoundingBox(), color);
                    return;
                }
            }

            // 方块碰撞检测
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                Render3DUtil.drawBox(matrixStack, new Box(bhr.getBlockPos()), color);
                break;
            }

            if (y <= -65.0) break;
            if (motionX == 0.0 && motionY == 0.0 && motionZ == 0.0) continue;

            Render3DUtil.drawLine(lastPos, pos, color);
        }
    }

    private EnderPearlEntity findInterceptablePearl() {
        EnderPearlEntity best = null;
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            // 忽略自己的珍珠
            if (pearl.getOwner() != null && pearl.getOwner().equals(mc.player)) continue;
            // 始终忽略好友的珍珠（不拦截）
            if (isFriendPearl(pearl)) continue;

            if (best == null || pearl.distanceTo(mc.player) < best.distanceTo(mc.player)) {
                best = pearl;
            }
        }
        return best;
    }

    private boolean isFriendPearl(EnderPearlEntity pearl) {
        Entity owner = pearl.getOwner();
        if (owner instanceof PlayerEntity playerOwner) {
            if (Alien.FRIEND != null && Alien.FRIEND.isFriend(playerOwner)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findPlacePos(EnderPearlEntity pearl) {
        double motionX = pearl.getVelocity().x;
        double motionY = pearl.getVelocity().y;
        double motionZ = pearl.getVelocity().z;
        if (motionX == 0.0 && motionY == 0.0 && motionZ == 0.0) return null;

        double x = pearl.getX();
        double y = pearl.getY();
        double z = pearl.getZ();
        double travelled = 0.0;

        for (int i = 0; i < 300; i++) {
            Vec3d lastPos = new Vec3d(x, y, z);

            if (mc.world.getBlockState(new BlockPos((int) (x + motionX), (int) (y + motionY), (int) (z + motionZ))).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }
            motionY -= 0.03;

            x += motionX;
            y += motionY;
            z += motionZ;
            Vec3d pos = new Vec3d(x, y, z);
            travelled += pos.distanceTo(lastPos);
            BlockPos bp = new BlockPosX((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));

            // 与投掷者距离限制
            Entity owner = pearl.getOwner();
            if (owner instanceof PlayerEntity ownerPlayer) {
                if (ownerPlayer.getPos().distanceTo(Vec3d.ofCenter(bp)) < minDistance.getValue()) {
                    continue;
                }
            }

            // 放置范围 + 可放置检查
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(bp)) < placeRange.getValue() * placeRange.getValue()) {
                if (pearl.getPos().squaredDistanceTo(Vec3d.ofCenter(bp)) > 0.5) {
                    if (travelled >= minLength.getValue()) {
                        return bp;
                    }
                }
            }

            RaycastContext ctx = new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player);
            if (mc.world.raycast(ctx) != null &&
                    (mc.world.raycast(ctx).getType() == HitResult.Type.ENTITY ||
                            mc.world.raycast(ctx).getType() == HitResult.Type.BLOCK)) {
                break;
            }
            if (y <= -65.0) break;
        }
        return null;
    }

    private void placeObsidian(BlockPos pos) {
        if (pos == null) return;
        if (BlockUtil.clientCanPlace(pos, false)) {
            doPlace(pos);
            return;
        }
        if (helper.getValue()) {
            BlockPos helperPos = getHelper(pos);
            if (helperPos != null) {
                doPlace(helperPos);
            }
        }
    }

    private void doPlace(BlockPos pos) {
        // 新增：DetectMining 检查，若目标方块正被挖掘则放弃放置
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;

        int obsidianSlot = getObsidianSlot();
        if (obsidianSlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        boolean useInv = inventory.getValue();

        if (useInv) {
            InventoryUtil.inventorySwap(obsidianSlot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(obsidianSlot);
        }

        BlockUtil.placeBlock(pos, rotate.getValue());

        if (useInv) {
            InventoryUtil.inventorySwap(obsidianSlot, mc.player.getInventory().selectedSlot);
            EntityUtil.syncInventory();
        } else {
            InventoryUtil.switchToSlot(oldSlot);
        }
    }

    private int getObsidianSlot() {
        if (inventory.getValue()) {
            int crying = InventoryUtil.findBlockInventorySlot(Blocks.CRYING_OBSIDIAN);
            if (crying != -1) return crying;
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            int crying = InventoryUtil.findBlock(Blocks.CRYING_OBSIDIAN);
            if (crying != -1) return crying;
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }

    private BlockPos getHelper(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            // 新增：跳过正被挖掘的邻居
            if (detectMining.getValue() && Alien.BREAK.isMining(neighbor)) continue;
            if (BlockUtil.isStrictDirection(neighbor, dir.getOpposite())
                    && BlockUtil.canPlace(neighbor, placeRange.getValue(), false)) {
                return neighbor;
            }
        }
        return null;
    }

    @Override
    public String getInfo() {
        if (currentPearl != null && currentPearl.isAlive()) {
            return "Intercepting...";
        }
        return null;
    }
}