package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.Event;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.DoAttackEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.*;

public class MaceEffect extends Module {

    public static MaceEffect INSTANCE;

    // ============ Settings ============
    private final BooleanSetting killEffect = add(new BooleanSetting("KillEffect", true));
    private final BooleanSetting killSound = add(new BooleanSetting("KillSound", true));
    private final EnumSetting<ParticleStyle> particleStyle = add(new EnumSetting<>("ParticleStyle", ParticleStyle.EXPLOSION));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", true));

    private final BooleanSetting hitEffect = add(new BooleanSetting("HitEffect", true));
    private final BooleanSetting shockwave = add(new BooleanSetting("Shockwave", true));
    private final BooleanSetting crater = add(new BooleanSetting("Crater", true));
    private final BooleanSetting lightning = add(new BooleanSetting("Lightning", true));
    private final BooleanSetting highQuality = add(new BooleanSetting("HighQuality", true));

    private final BooleanSetting fallTrail = add(new BooleanSetting("FallTrail", true));
    private final BooleanSetting slamEffect = add(new BooleanSetting("SlamEffect", true));
    private final BooleanSetting slamSound = add(new BooleanSetting("SlamSound", true));

    // ============ State ============
    private final Map<Entity, Float> lastHealthMap = new HashMap<>();
    private final Map<Entity, Boolean> soundPlayedMap = new HashMap<>();
    private final Random random = new Random();
    private boolean wasFalling = false;
    private double lastFallSpeed = 0.0;
    private int trailTick = 0;

    // ============ Theme ============
    private enum Theme {
        NEUTRAL(0.88F, 0.94F, 1.0F, 0.42F, 0.5F, 0.64F),
        FIRE(1.0F, 0.34F, 0.04F, 1.0F, 0.78F, 0.08F),
        CURSE(0.72F, 0.18F, 1.0F, 0.16F, 0.02F, 0.34F),
        FROST(0.35F, 0.88F, 1.0F, 0.86F, 1.0F, 1.0F),
        POISON(0.38F, 1.0F, 0.18F, 0.03F, 0.25F, 0.02F),
        WIND(0.62F, 1.0F, 0.92F, 0.25F, 0.76F, 1.0F),
        DENSITY(1.0F, 0.74F, 0.22F, 0.65F, 0.34F, 0.08F),
        BREACH(0.28F, 1.0F, 0.86F, 0.04F, 0.3F, 0.42F),
        ENDER(0.12F, 0.55F, 0.55F, 0.55F, 0.12F, 0.55F);

        final Vector3f dustColor;
        final Vector3f transitionColor;

        Theme(float dr, float dg, float db, float tr, float tg, float tb) {
            this.dustColor = new Vector3f(dr, dg, db);
            this.transitionColor = new Vector3f(tr, tg, tb);
        }

        static Theme fromStack(ItemStack stack) {
            if (stack.isEmpty()) return NEUTRAL;
            var enchantments = EnchantmentHelper.getEnchantments(stack);
            if (enchantments == null) return NEUTRAL;
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                if (entry.matchesKey(Enchantments.FIRE_ASPECT)) return FIRE;
                if (entry.matchesKey(Enchantments.SHARPNESS) || entry.matchesKey(Enchantments.SMITE)) return CURSE;
                if (entry.matchesKey(Enchantments.FROST_WALKER)) return FROST;
                if (entry.matchesKey(Enchantments.WIND_BURST)) return WIND;
                if (entry.matchesKey(Enchantments.DENSITY)) return DENSITY;
                if (entry.matchesKey(Enchantments.BREACH)) return BREACH;
                Optional<RegistryKey<Enchantment>> keyOpt = entry.getKey();
                if (keyOpt.isPresent()) {
                    String path = keyOpt.get().getValue().getPath();
                    if (path.contains("fire") || path.contains("flame")) return FIRE;
                    if (path.contains("curse") || path.contains("void") || path.contains("hex")) return CURSE;
                    if (path.contains("frost") || path.contains("freeze") || path.contains("ice")) return FROST;
                    if (path.contains("poison") || path.contains("venom") || path.contains("toxic")) return POISON;
                    if (path.contains("ender") || path.contains("warp") || path.contains("teleport")) return ENDER;
                }
            }
            return NEUTRAL;
        }
    }

    public enum ParticleStyle {
        EXPLOSION, STARS, SNOWBALL, LIGHTNING_RING, RANDOM
    }

    public MaceEffect() {
        super("MaceEffect", Category.Render);
        setChinese("棒槌特效");
        INSTANCE = this;
    }

    // ============ Events ============

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        // --- Kill detection ---
        if (killEffect.getValue()) {
            World world = mc.world;
            List<Entity> entities = world.getEntitiesByClass(Entity.class,
                    mc.player.getBoundingBox().expand(100.0),
                    e -> e instanceof PlayerEntity || (mobs.getValue() && e instanceof MobEntity));

            for (Entity entity : entities) {
                if (entity == mc.player) continue;
                float currentHealth = getHealth(entity);
                float previousHealth = lastHealthMap.getOrDefault(entity, currentHealth);
                if (previousHealth > 0.0F && currentHealth <= 0.0F || !entity.isAlive() && lastHealthMap.containsKey(entity)) {
                    if (killSound.getValue() && !soundPlayedMap.containsKey(entity)) {
                        world.playSound(mc.player, entity.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
                        soundPlayedMap.put(entity, true);
                    }
                    spawnKillParticles(world, entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
                    lastHealthMap.remove(entity);
                } else {
                    lastHealthMap.put(entity, currentHealth);
                }
            }
            lastHealthMap.entrySet().removeIf(e -> e.getKey().isRemoved());
            soundPlayedMap.entrySet().removeIf(e -> e.getKey().isRemoved());
        }

        // --- Fall trail ---
        if (fallTrail.getValue() && mc.world != null) {
            ItemStack stack = mc.player.getMainHandStack();
            boolean holdingMace = stack.isOf(Items.MACE);
            if (holdingMace && !mc.player.isOnGround() && mc.player.getVelocity().y < -0.08 && mc.player.fallDistance >= 2.0F) {
                trailTick++;
                if (trailTick % (highQuality.getValue() ? 1 : 2) == 0) {
                    spawnFallTrail(mc.world, mc.player, Theme.fromStack(stack), mc.player.fallDistance);
                }
            }
        }

        // --- Mace slam detection ---
        boolean holdingMace = mc.player.getMainHandStack().isOf(Items.MACE);
        double fallSpeed = mc.player.getVelocity().y;
        boolean currentlyFalling = fallSpeed < -0.5 && !mc.player.isOnGround() && holdingMace;
        if (wasFalling && mc.player.isOnGround() && holdingMace && lastFallSpeed < -1.0 && slamEffect.getValue()) {
            onMaceSlam(mc.world);
        }
        if (currentlyFalling) {
            lastFallSpeed = fallSpeed;
        }
        wasFalling = currentlyFalling;
        if (mc.player.isOnGround()) {
            lastFallSpeed = 0.0;
        }
    }

    @EventListener
    public void onAttack(DoAttackEvent event) {
        if (event.stage != Event.Stage.Pre) return;
        if (!hitEffect.getValue() || mc.player == null || mc.world == null) return;
        ItemStack stack = mc.player.getMainHandStack();
        if (!stack.isOf(Items.MACE)) return;
        Entity target = mc.targetedEntity;
        if (target == null) return;
        float fallDistance = Math.max(mc.player.fallDistance, 3.0F);
        Theme theme = Theme.fromStack(stack);
        spawnImpact(mc.world, target, mc.player, theme, fallDistance);
    }

    // ============ Mace Slam ============

    private void onMaceSlam(World world) {
        Theme theme = Theme.fromStack(mc.player.getMainHandStack());
        spawnKillParticles(world, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (slamSound.getValue()) {
            world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5F, 1.8F);
            world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 2.0F, 0.8F);
        }
    }

    // ============ Kill Particles ============

    private void spawnKillParticles(World world, double x, double y, double z) {
        ParticleStyle style = particleStyle.getValue();
        if (style == ParticleStyle.RANDOM) {
            ParticleStyle[] options = {ParticleStyle.EXPLOSION, ParticleStyle.STARS, ParticleStyle.SNOWBALL, ParticleStyle.LIGHTNING_RING};
            style = options[random.nextInt(options.length)];
        }
        Vec3d pos = new Vec3d(x, y, z);
        switch (style) {
            case EXPLOSION -> spawnExplosionParticles(world, pos);
            case STARS -> spawnStarParticles(world, pos);
            case SNOWBALL -> spawnSnowballParticles(world, pos);
            case LIGHTNING_RING -> spawnLightningRingParticles(world, pos);
        }
    }

    private void spawnExplosionParticles(World world, Vec3d pos) {
        for (int i = 0; i < 8; i++) {
            world.addParticle(ParticleTypes.EXPLOSION,
                    pos.x + (random.nextDouble() - 0.5) * 2.5,
                    pos.y + random.nextDouble() * 1.8,
                    pos.z + (random.nextDouble() - 0.5) * 2.5,
                    0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double elevation = (random.nextDouble() - 0.3) * Math.PI / 2.0;
            double speed = 0.3 + random.nextDouble() * 0.5;
            world.addParticle(ParticleTypes.FLAME,
                    pos.x, pos.y + 0.2, pos.z,
                    Math.cos(angle) * Math.cos(elevation) * speed,
                    Math.sin(elevation) * speed + 0.2,
                    Math.sin(angle) * Math.cos(elevation) * speed);
        }
    }

    private void spawnStarParticles(World world, Vec3d pos) {
        for (int i = 0; i < 60; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double elevation = (random.nextDouble() - 0.2) * Math.PI / 2.0;
            double speed = 0.2 + random.nextDouble() * 0.4;
            world.addParticle(ParticleTypes.CLOUD,
                    pos.x, pos.y + 0.2, pos.z,
                    Math.cos(angle) * Math.cos(elevation) * speed,
                    Math.sin(elevation) * speed + 0.25,
                    Math.sin(angle) * Math.cos(elevation) * speed);
        }
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.15 + random.nextDouble() * 0.25;
            world.addParticle(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.3, pos.z,
                    Math.cos(angle) * speed,
                    0.2 + random.nextDouble() * 0.2,
                    Math.sin(angle) * speed);
        }
    }

    private void spawnSnowballParticles(World world, Vec3d pos) {
        for (int i = 0; i < 50; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.1 + random.nextDouble() * 0.3;
            world.addParticle(ParticleTypes.ITEM_SNOWBALL,
                    pos.x, pos.y + 0.5, pos.z,
                    Math.cos(angle) * speed,
                    0.1 + random.nextDouble() * 0.2,
                    Math.sin(angle) * speed);
        }
        for (int i = 0; i < 30; i++) {
            world.addParticle(ParticleTypes.SNOWFLAKE,
                    pos.x + (random.nextDouble() - 0.5) * 2.0,
                    pos.y + random.nextDouble() * 2.0,
                    pos.z + (random.nextDouble() - 0.5) * 2.0,
                    0.0, -0.05, 0.0);
        }
    }

    private void spawnLightningRingParticles(World world, Vec3d pos) {
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.5 + ring * 0.8;
            int particles = 40 + ring * 10;
            for (int i = 0; i < particles; i++) {
                double angle = (Math.PI * 2) * i / particles;
                double x = pos.x + Math.cos(angle) * radius;
                double z = pos.z + Math.sin(angle) * radius;
                double y = pos.y + 0.1 + random.nextDouble() * 0.3;
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.05,
                        0.1 + random.nextDouble() * 0.2,
                        (random.nextDouble() - 0.5) * 0.05);
            }
        }
        for (int i = 0; i < 25; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * 2.5;
            world.addParticle(ParticleTypes.ENCHANT,
                    pos.x + Math.cos(angle) * radius,
                    pos.y + 0.1,
                    pos.z + Math.sin(angle) * radius,
                    0.0, 0.05 + random.nextDouble() * 0.1, 0.0);
        }
    }

    // ============ Fall Trail ============

    private void spawnFallTrail(World world, PlayerEntity player, Theme theme, float fallDistance) {
        float strength = MathHelper.clamp(fallDistance / 28.0F, 0.18F, 1.0F);
        int count = highQuality.getValue() ? 9 + MathHelper.floor(strength * 13.0F) : 4 + MathHelper.floor(strength * 6.0F);
        Vec3d motion = player.getVelocity();
        Vec3d base = player.getPos().add(0.0, player.getHeight() * 0.58, 0.0);
        DustColorTransitionParticleEffect dust = new DustColorTransitionParticleEffect(theme.dustColor, theme.transitionColor, 0.85F + strength * 0.55F);

        for (int i = 0; i < count; i++) {
            double drift = 0.16 + strength * 0.36;
            double x = base.x - motion.x * (2.5 + random.nextDouble() * 5.0) + gaussian(drift);
            double y = base.y - motion.y * (1.2 + random.nextDouble() * 3.0) + gaussian(drift * 0.65);
            double z = base.z - motion.z * (2.5 + random.nextDouble() * 5.0) + gaussian(drift);
            world.addParticle(dust, x, y, z,
                    -motion.x * 0.05 + gaussian(0.012),
                    0.018 + random.nextDouble() * 0.018,
                    -motion.z * 0.05 + gaussian(0.012));
            if (i % 3 == 0) {
                world.addParticle(accentParticle(theme),
                        x + gaussian(0.08), y + gaussian(0.08), z + gaussian(0.08),
                        gaussian(0.018), 0.012 + random.nextDouble() * 0.03, gaussian(0.018));
            }
        }
        if (highQuality.getValue() && random.nextFloat() < strength * 0.45F) {
            world.addParticle(highlightParticle(theme),
                    base.x + gaussian(0.22), base.y + gaussian(0.18), base.z + gaussian(0.22),
                    gaussian(0.02), gaussian(0.02), gaussian(0.02));
        }
    }

    // ============ Impact Effects ============

    private void spawnImpact(World world, Entity target, PlayerEntity player, Theme theme, float fallDistance) {
        Vec3d center = target.getPos().add(0.0, Math.max(0.12, target.getHeight() * 0.08), 0.0);
        float strength = MathHelper.clamp(fallDistance / 30.0F, 0.28F, 1.35F);

        if (shockwave.getValue()) spawnShockwave(world, center, theme, strength);
        if (crater.getValue()) spawnCrater(world, center, theme, strength);
        if (lightning.getValue() && fallDistance >= 15.0F) spawnLightning(world, center, theme, strength);
        spawnStarburst(world, center, player, theme, strength);
        spawnThemeSignature(world, center, theme, strength);
    }

    private void spawnShockwave(World world, Vec3d center, Theme theme, float strength) {
        int rings = highQuality.getValue() ? 4 : 2;
        int points = highQuality.getValue() ? 88 : 44;
        double maxRadius = 2.3 + strength * 5.2;

        for (int ring = 0; ring < rings; ring++) {
            double radius = maxRadius * (ring + 1.0) / rings;
            ParticleEffect particle = (ring % 2 == 0)
                    ? new DustColorTransitionParticleEffect(theme.dustColor, theme.transitionColor, 1.2F)
                    : accentParticle(theme);

            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2) * i / points;
                double wobble = 0.88 + random.nextDouble() * 0.25;
                double x = center.x + Math.cos(angle) * radius * wobble;
                double z = center.z + Math.sin(angle) * radius * wobble;
                double speed = 0.035 + strength * 0.035;
                world.addParticle(particle, x, center.y + 0.06 + random.nextDouble() * 0.08, z,
                        Math.cos(angle) * speed, 0.018, Math.sin(angle) * speed);
            }
        }
    }

    private void spawnCrater(World world, Vec3d center, Theme theme, float strength) {
        int cracks = highQuality.getValue() ? 18 : 10;
        int segments = highQuality.getValue() ? 9 : 5;
        double radius = 1.7 + strength * 3.8;

        for (int crack = 0; crack < cracks; crack++) {
            double angle = (Math.PI * 2) * crack / cracks + gaussian(0.11);
            double length = radius * (0.45 + random.nextDouble() * 0.65);
            for (int segment = 1; segment <= segments; segment++) {
                double progress = (double) segment / segments;
                double bend = gaussian(0.18) * progress;
                double x = center.x + Math.cos(angle + bend) * length * progress;
                double z = center.z + Math.sin(angle + bend) * length * progress;
                world.addParticle(new DustParticleEffect(theme.dustColor, 0.78F),
                        x, center.y + 0.025, z, gaussian(0.006), 0.005, gaussian(0.006));
                if (random.nextFloat() < 0.38F) {
                    world.addParticle(ParticleTypes.ASH, x, center.y + 0.02, z,
                            gaussian(0.025), 0.025 + random.nextDouble() * 0.035, gaussian(0.025));
                }
                if (segment == segments && random.nextFloat() < 0.42F) {
                    world.addParticle(accentParticle(theme), x, center.y + 0.04, z,
                            gaussian(0.018), 0.02 + random.nextDouble() * 0.05, gaussian(0.018));
                }
            }
        }
    }

    private void spawnLightning(World world, Vec3d center, Theme theme, float strength) {
        int bolts = highQuality.getValue() ? 14 : 8;
        int segments = highQuality.getValue() ? 8 : 5;
        double radius = 2.2 + strength * 5.8;

        for (int bolt = 0; bolt < bolts; bolt++) {
            double angle = (Math.PI * 2) * bolt / bolts + gaussian(0.2);
            Vec3d previous = center.add(0.0, 0.25 + random.nextDouble() * 0.35, 0.0);
            for (int segment = 1; segment <= segments; segment++) {
                double progress = (double) segment / segments;
                double arcAngle = angle + gaussian(0.18) * progress;
                Vec3d next = center.add(
                        Math.cos(arcAngle) * radius * progress,
                        0.12 + random.nextDouble() * 0.65,
                        Math.sin(arcAngle) * radius * progress);
                spawnLine(world, previous, next, new DustColorTransitionParticleEffect(theme.dustColor, theme.transitionColor, 1.35F),
                        highQuality.getValue() ? 4 : 2);
                previous = next;
            }
        }
    }

    private void spawnStarburst(World world, Vec3d center, PlayerEntity player, Theme theme, float strength) {
        int rays = highQuality.getValue() ? 38 : 20;
        ParticleEffect dust = new DustColorTransitionParticleEffect(theme.dustColor, theme.transitionColor, 1.45F);
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2) * i / rays;
            double speed = 0.18 + random.nextDouble() * (0.18 + strength * 0.1);
            world.addParticle(dust,
                    center.x + gaussian(0.12), center.y + 0.55 + gaussian(0.2), center.z + gaussian(0.12),
                    Math.cos(angle) * speed, 0.04 + random.nextDouble() * 0.18, Math.sin(angle) * speed);
        }
        Vec3d fromPlayer = center.subtract(player.getPos()).normalize();
        int hearts = highQuality.getValue() ? 18 : 9;
        for (int i = 0; i < hearts; i++) {
            world.addParticle(ParticleTypes.HEART,
                    center.x + fromPlayer.x * 0.5 + gaussian(0.25),
                    center.y + 0.65 + gaussian(0.2),
                    center.z + fromPlayer.z * 0.5 + gaussian(0.25),
                    0.0, 0.0, 0.0);
        }
    }

    // ============ Theme Signatures ============

    private void spawnThemeSignature(World world, Vec3d center, Theme theme, float strength) {
        switch (theme) {
            case FIRE -> spawnFlameCrown(world, center, strength);
            case FROST -> spawnIceShards(world, center, strength);
            case CURSE, ENDER -> spawnCurseRift(world, center, strength);
            case POISON -> spawnPoisonBloom(world, center, strength);
            case WIND -> spawnWindSpiral(world, center, strength);
            case DENSITY -> spawnHeavyDebris(world, center, strength);
            case BREACH -> spawnBreachPulse(world, center, strength);
            case NEUTRAL -> spawnCleanImpact(world, center, strength);
        }
    }

    private void spawnFlameCrown(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 34 : 18;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.45 + random.nextDouble() * (1.2 + strength);
            world.addParticle(ParticleTypes.FLAME,
                    center.x + Math.cos(angle) * radius, center.y + 0.12, center.z + Math.sin(angle) * radius,
                    gaussian(0.04), 0.08 + random.nextDouble() * 0.16, gaussian(0.04));
            if (i % 7 == 0) {
                world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        center.x + Math.cos(angle) * radius * 0.6, center.y + 0.08, center.z + Math.sin(angle) * radius * 0.6,
                        0.0, 0.02, 0.0);
            }
        }
    }

    private void spawnIceShards(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 46 : 24;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            double speed = 0.06 + random.nextDouble() * (0.08 + strength * 0.03);
            world.addParticle(ParticleTypes.SNOWFLAKE,
                    center.x + gaussian(0.18), center.y + 0.28 + random.nextDouble() * 0.8, center.z + gaussian(0.18),
                    Math.cos(angle) * speed, 0.02 + random.nextDouble() * 0.04, Math.sin(angle) * speed);
            if (i % 4 == 0) {
                world.addParticle(ParticleTypes.GLOW,
                        center.x + gaussian(0.5), center.y + 0.1, center.z + gaussian(0.5),
                        gaussian(0.018), 0.012, gaussian(0.018));
            }
        }
    }

    private void spawnCurseRift(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 42 : 22;
        for (int i = 0; i < count; i++) {
            double angle = i * 0.48;
            double radius = 0.3 + i * 0.045 * strength;
            world.addParticle(ParticleTypes.PORTAL,
                    center.x + Math.cos(angle) * radius, center.y + 0.2 + i * 0.018, center.z + Math.sin(angle) * radius,
                    gaussian(0.35), gaussian(0.12), gaussian(0.35));
            if (i % 5 == 0) {
                world.addParticle(ParticleTypes.WITCH,
                        center.x + gaussian(0.8), center.y + 0.25 + random.nextDouble(), center.z + gaussian(0.8),
                        gaussian(0.02), 0.02, gaussian(0.02));
            }
        }
    }

    private void spawnPoisonBloom(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 38 : 20;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = random.nextDouble() * (1.6 + strength * 1.5);
            world.addParticle(ParticleTypes.WITCH,
                    center.x + Math.cos(angle) * radius, center.y + 0.08, center.z + Math.sin(angle) * radius,
                    gaussian(0.02), 0.035 + random.nextDouble() * 0.04, gaussian(0.02));
            if (i % 3 == 0) {
                world.addParticle(ParticleTypes.WITCH,
                        center.x + Math.cos(angle) * radius * 0.55, center.y + 0.2, center.z + Math.sin(angle) * radius * 0.55,
                        gaussian(0.025), 0.04, gaussian(0.025));
            }
        }
    }

    private void spawnWindSpiral(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 44 : 24;
        for (int i = 0; i < count; i++) {
            double angle = i * 0.42;
            double radius = 0.35 + i * 0.04;
            world.addParticle(i % 3 == 0 ? ParticleTypes.GUST : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.x + Math.cos(angle) * radius, center.y + 0.08 + i * 0.026, center.z + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.06, 0.03 + strength * 0.02, Math.cos(angle) * 0.06);
        }
    }

    private void spawnHeavyDebris(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 52 : 28;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.08 + random.nextDouble() * (0.12 + strength * 0.06);
            world.addParticle(ParticleTypes.ASH,
                    center.x + gaussian(0.22), center.y + 0.04, center.z + gaussian(0.22),
                    Math.cos(angle) * speed, 0.035 + random.nextDouble() * 0.07, Math.sin(angle) * speed);
            if (i % 6 == 0) {
                world.addParticle(ParticleTypes.LARGE_SMOKE,
                        center.x + gaussian(0.35), center.y + 0.35, center.z + gaussian(0.35),
                        Math.cos(angle) * speed * 0.55, 0.08, Math.sin(angle) * speed * 0.55);
            }
        }
    }

    private void spawnBreachPulse(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 26 : 14;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            double radius = 0.9 + strength * 1.5;
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    center.x + Math.cos(angle) * radius, center.y + 0.45 + gaussian(0.2), center.z + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.035, gaussian(0.025), -Math.sin(angle) * 0.035);
        }
        if (highQuality.getValue()) {
            world.addParticle(ParticleTypes.GLOW, center.x, center.y + 0.75, center.z, 0.0, 0.0, 0.0);
        }
    }

    private void spawnCleanImpact(World world, Vec3d center, float strength) {
        int count = highQuality.getValue() ? 32 : 16;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            world.addParticle(ParticleTypes.CLOUD,
                    center.x + gaussian(0.16), center.y + 0.22, center.z + gaussian(0.16),
                    Math.cos(angle) * (0.08 + strength * 0.03), 0.04, Math.sin(angle) * (0.08 + strength * 0.03));
        }
    }

    // ============ Helpers ============

    private void spawnLine(World world, Vec3d from, Vec3d to, ParticleEffect particle, int density) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        int steps = Math.max(2, MathHelper.ceil(length * density));
        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            Vec3d point = from.add(delta.multiply(progress));
            world.addParticle(particle,
                    point.x + gaussian(0.025), point.y + gaussian(0.025), point.z + gaussian(0.025),
                    gaussian(0.012), gaussian(0.012), gaussian(0.012));
        }
    }

    private float getHealth(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getHealth();
        } else if (entity instanceof MobEntity mob) {
            return mob.getHealth();
        }
        return 0.0F;
    }

    private ParticleEffect accentParticle(Theme theme) {
        return switch (theme) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case CURSE -> ParticleTypes.PORTAL;
            case POISON -> ParticleTypes.WITCH;
            case WIND -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case DENSITY -> ParticleTypes.ASH;
            case BREACH -> ParticleTypes.ELECTRIC_SPARK;
            case ENDER -> ParticleTypes.DRAGON_BREATH;
            case NEUTRAL -> ParticleTypes.CLOUD;
        };
    }

    private ParticleEffect highlightParticle(Theme theme) {
        return switch (theme) {
            case FIRE -> ParticleTypes.SOUL_FIRE_FLAME;
            case FROST -> ParticleTypes.GLOW;
            case CURSE, POISON -> ParticleTypes.WITCH;
            case WIND -> ParticleTypes.GUST;
            case DENSITY -> ParticleTypes.LARGE_SMOKE;
            case BREACH -> ParticleTypes.ELECTRIC_SPARK;
            case ENDER -> ParticleTypes.REVERSE_PORTAL;
            case NEUTRAL -> ParticleTypes.WHITE_ASH;
        };
    }

    private double gaussian(double spread) {
        return random.nextGaussian() * spread;
    }
}