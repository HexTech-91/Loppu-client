package dev.luminous.asm.accessors;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={LivingEntity.class})
public interface ILivingEntity {
    @Accessor(value="lastAttackedTicks")
    int getLastAttackedTicks();

    @Accessor(value="jumpingCooldown")
    int getLastJumpCooldown();

    @Accessor(value="jumpingCooldown")
    void setLastJumpCooldown(int var1);

    @Accessor(value="leaningPitch")
    float getLeaningPitch();

    @Accessor(value="leaningPitch")
    void setLeaningPitch(float var1);

    @Accessor(value="lastLeaningPitch")
    void setLastLeaningPitch(float var1);
}

