package dev.luminous.mod.modules.impl.nagisa.acpro.basic;

import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class WallCheck {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   public static boolean behindWall(BlockPos pos) {
      Vec3d WallVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5);
      BlockHitResult result = null;
      if (mc.world != null && mc.player != null) {
         result = mc.world.raycast(new RaycastContext(EntityUtil.getEyesPos(), WallVec, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
      }

      return result != null && result.getType() != Type.MISS
         ? mc.player.getEyePos().distanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0)) > ACPro.INSTANCE.wallRange.getValue()
         : false;
   }
}
