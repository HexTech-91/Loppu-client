package dev.luminous.mod.modules.impl.nagisa.acpro.basic;

import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import net.minecraft.util.math.MathHelper;

public class InjectRotate {
   public static float[] injectStep(float[] angle, float steps) {
      if (angle == null) {
         return null;
      }

      if (steps < 0.01F) {
         steps = 0.01F;
      }

      if (steps > 1.0F) {
         steps = 1.0F;
      }

      if (steps < 1.0F) {
         float currentYaw = ACPro.lastYaw;
         float currentPitch = ACPro.lastPitch;
         float diff = MathHelper.wrapDegrees(angle[0] - currentYaw);
         if (Math.abs(diff) > 90.0F * steps) {
            angle[0] = currentYaw + diff * (90.0F * steps / Math.abs(diff));
         }

         float pitchDiff = angle[1] - currentPitch;
         if (Math.abs(pitchDiff) > 90.0F * steps) {
            angle[1] = currentPitch + pitchDiff * (90.0F * steps / Math.abs(pitchDiff));
         }
      }

      return new float[]{angle[0], angle[1]};
   }
}
