package dev.luminous.mod.modules.impl.nagisa.acpro.basic;

import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.client.MinecraftClient;

public class CanPlaceCrystal {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public static boolean canPlaceCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
      BlockPos obsPos = pos.down();
      BlockPos boost = obsPos.up();
      BlockPos boost2 = boost.up();
      return (BlockUtil.getBlock(obsPos) == Blocks.BEDROCK || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN)
         && BlockUtil.getClickSideStrict(obsPos) != null
         && hasEntityBlockCrystal(boost, ignoreCrystal, ignoreItem)
         && hasEntityBlockCrystal(boost2, ignoreCrystal, ignoreItem)
         && (mc.world.isAir(boost) || BlockUtil.hasCrystal(boost) && BlockUtil.getBlock(boost) == Blocks.FIRE);
   }

   private static boolean hasEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
      if (mc.world != null) {
         for (Entity entity : mc.world.getNonSpectatingEntities(Entity.class, new Box(pos))) {
            if (entity.isAlive() && (!ignoreItem || !(entity instanceof ItemEntity)) && !(entity instanceof ArmorStandEntity)) {
               if (!(entity instanceof EndCrystalEntity)) {
                  return false;
               }

               if (!ignoreCrystal) {
                  return false;
               }

               if (mc.player == null
                  || !mc.player.canSee(entity)
                     && !(mc.player.getEyePos().distanceTo(entity.getPos()) <= ACPro.INSTANCE.wallRange.getValue())) {
                  return false;
               }
            }
         }
      }

      return true;
   }
}
