package dev.luminous.mod.modules.impl.nagisa.acpro;

import dev.luminous.api.utils.math.ExplosionUtil;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.Calc.Calc;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class CrystalDamage {
   public static float calculateDamage(BlockPos pos, PlayerEntity player, PlayerEntity predict) {
      Vec3d vecPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
      return ExplosionUtil.calculateDamage(vecPos, player, predict, Calc.calcMode.getValue() == null ? 6.0F : 6.0F);
   }
}
