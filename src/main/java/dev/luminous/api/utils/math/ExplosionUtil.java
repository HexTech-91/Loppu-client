
package dev.luminous.api.utils.math;

import dev.luminous.api.utils.Wrapper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ExplosionUtil
implements Wrapper {
    public static float anchorDamage(BlockPos pos, LivingEntity target, LivingEntity predict) {
        return DamageUtils.anchorDamage(target, predict, pos.toCenterPos());
    }

    public static float calculateDamage(Vec3d pos, LivingEntity entity, LivingEntity predict, float power) {
        return DamageUtils.explosionDamage(entity, predict, pos, power * 2.0f);
    }

    public static float calculateDamage(BlockPos pos, LivingEntity entity) {
        return DamageUtils.calculateDamage(pos, entity);
    }

    public static float calculateDamage(Vec3d pos, LivingEntity entity, LivingEntity predict) {
        return DamageUtils.explosionDamage(entity, predict, pos, 12.0f);
    }
}

