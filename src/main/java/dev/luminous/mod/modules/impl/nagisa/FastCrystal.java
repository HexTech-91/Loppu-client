package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static dev.luminous.api.utils.Wrapper.mc;

public class FastCrystal extends Module {
    public static FastCrystal INSTANCE;

    private final SliderSetting placeDelay = add(new SliderSetting("PlaceDelay", 20, 20, 120, 1));
    private final SliderSetting breakDelay = add(new SliderSetting("BreakDelay", 20, 20, 120, 1));

    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();

    private BlockPos targetPos;

    public FastCrystal() {
        super("FastCrystal", Category.Player);
        setChinese("快速水晶");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        if (!isHoldingCrystal()) return;

        BlockHitResult hitResult = getBlockHitResult();
        if (hitResult == null) return;

        BlockPos pos = hitResult.getBlockPos();
        if (!isObsidianOrBedrock(pos)) return;

        if (!isHoldingDown()) return;

        targetPos = pos;

        if (placeTimer.passedMs((long) placeDelay.getValue())) {
            placeCrystal(pos, hitResult.getSide());
            placeTimer.reset();
        }

        if (breakTimer.passedMs((long) breakDelay.getValue())) {
            breakCrystal();
            breakTimer.reset();
        }
    }

    private boolean isHoldingCrystal() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        return mainHand.getItem() == Items.END_CRYSTAL || offHand.getItem() == Items.END_CRYSTAL;
    }

    private BlockHitResult getBlockHitResult() {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            return (BlockHitResult) hitResult;
        }
        return null;
    }

    private boolean isObsidianOrBedrock(BlockPos pos) {
        if (pos == null) return false;
        return BlockUtil.getBlock(pos) == Blocks.OBSIDIAN || BlockUtil.getBlock(pos) == Blocks.BEDROCK;
    }

    private boolean isHoldingDown() {
        return mc.options.attackKey.isPressed() || mc.options.useKey.isPressed();
    }

    private void placeCrystal(BlockPos pos, Direction side) {
        if (pos == null) return;

        Direction placeSide = BlockUtil.getPlaceSide(pos);
        if (placeSide == null) return;

        Vec3d directionVec = new Vec3d(
            pos.getX() + 0.5 + placeSide.getVector().getX() * 0.5,
            pos.getY() + 0.5 + placeSide.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + placeSide.getVector().getZ() * 0.5
        );

        if (!BlockUtil.canPlace(pos, 6.0)) return;

        BlockUtil.clickBlock(pos, Direction.DOWN, false, Hand.MAIN_HAND);
    }

    private void breakCrystal() {
        Box box = new Box(mc.player.getBlockPos()).expand(6.0);
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, entity -> true);
        if (crystals.isEmpty()) return;

        EndCrystalEntity nearestCrystal = null;
        double nearestDistance = Double.MAX_VALUE;

        for (EndCrystalEntity crystal : crystals) {
            double distance = crystal.getPos().distanceTo(mc.player.getPos());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestCrystal = crystal;
            }
        }

        if (nearestCrystal != null && nearestDistance <= 6.0) {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(nearestCrystal, mc.player.isSneaking()));
            mc.player.resetLastAttackedTicks();
            EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
        }
    }

    @Override
    public void onDisable() {
        targetPos = null;
    }
}