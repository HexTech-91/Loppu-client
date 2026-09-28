package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class AutoPot extends Module {
    public static AutoPot INSTANCE;

    // ========== 设置 ==========
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 1.0, 0.0, 10.0, 0.1).setSuffix("s"));
    private final SliderSetting saveDuration = this.add(new SliderSetting("SaveDuration", 2.0, 0.0, 60.0, 0.1).setSuffix("s"));
    private final BooleanSetting speed = this.add(new BooleanSetting("Speed", true));
    private final BooleanSetting resistance = this.add(new BooleanSetting("Resistance", true));
    private final BooleanSetting strength = this.add(new BooleanSetting("Strength", true));
    private final BooleanSetting slowFalling = this.add(new BooleanSetting("SlowFalling", false));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", false));

    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true, () -> !this.smart.getValue()));
    private final BooleanSetting strictGround = this.add(new BooleanSetting("StrictGround", false, () -> onlyGround.getValue()));
    private final SliderSetting excessDistance = this.add(new SliderSetting("ExcessDistance", 0.0, 0.0, 1.0, 0.01, () -> onlyGround.getValue()));
    private final BooleanSetting potStrafe = this.add(new BooleanSetting("PotStrafe", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", false));
    // ========== 删除了 yawStep, steps, priority 设置 ==========

    private final BindSetting speedKey = this.add(new BindSetting("SpeedKey", -1));
    private final BindSetting strengthKey = this.add(new BindSetting("StrengthKey", -1));
    private final BindSetting resistanceKey = this.add(new BindSetting("ResistanceKey", -1));

    private final Timer delayTimer = new Timer();
    private boolean throwing = false;
    private boolean actuallyThrowing = false;
    private Direction throwDirection = Direction.DOWN;
    private boolean turtlePress;
    private boolean speedPress;
    private boolean strengthPress;
    private final AutoPotTick tickListener = new AutoPotTick();

    public AutoPot() {
        super("AutoPot", Module.Category.Player);
        this.setChinese("自动药水");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(this.tickListener);
    }

    // ========== 查找药水方法 ==========
    public static int findPotionInventorySlot(StatusEffect targetEffect) {
        for (int i = 35; i >= 0; --i) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() != Items.SPLASH_POTION) continue;
            PotionContentsComponent contents = itemStack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : contents.getEffects()) {
                if (effect.getEffectType().value() != targetEffect) continue;
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findPotion(StatusEffect targetEffect) {
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem() != Items.SPLASH_POTION) continue;
            PotionContentsComponent contents = itemStack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : contents.getEffects()) {
                if (effect.getEffectType().value() != targetEffect) continue;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        this.throwing = false;
        this.actuallyThrowing = false;
        Alien.EVENT_BUS.unsubscribe(this.tickListener);
    }

    public boolean isActuallyThrowing() {
        return this.actuallyThrowing;
    }

    // ========== 地面条件判断（保留原所有逻辑） ==========
    public boolean isGroundConditionSatisfied() {
        if (smart.getValue()) {
            if (potStrafe.getValue() && EntityUtil.isInsideBlock()) {
                this.throwDirection = Direction.DOWN;
                return true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos checkPos = new BlockPosX(mc.player.getPos()).offset(direction);
                if (!mc.world.isAir(checkPos)) {
                    this.throwDirection = direction;
                    return true;
                }
            }
            double excess = excessDistance.getValue();
            if (excess > 0.0) {
                double playerFeetY = mc.player.getY();
                BlockPos downPos = new BlockPosX(mc.player.getPos()).down();
                if (!mc.world.isAir(downPos)) {
                    VoxelShape shape = mc.world.getBlockState(downPos).getCollisionShape(mc.world, downPos);
                    if (!shape.isEmpty()) {
                        double blockTopY = downPos.getY() + shape.getMax(Direction.Axis.Y);
                        double distance = playerFeetY - blockTopY;
                        if (distance >= 0 && distance < excess) {
                            this.throwDirection = Direction.DOWN;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        if (!onlyGround.getValue()) {
            this.throwDirection = Direction.DOWN;
            return true;
        }

        if (mc.player.isOnGround() || Alien.PLAYER.isInWeb((PlayerEntity) mc.player)) {
            if (strictGround.getValue()) {
                BlockPos downPos = new BlockPosX(mc.player.getPos()).down();
                if (!mc.world.isAir(downPos)) {
                    this.throwDirection = Direction.DOWN;
                    return true;
                }
            } else {
                this.throwDirection = Direction.DOWN;
                return true;
            }
        }

        if (potStrafe.getValue() && EntityUtil.isInsideBlock()) {
            this.throwDirection = Direction.DOWN;
            return true;
        }

        double excess = excessDistance.getValue();
        if (excess > 0.0) {
            double playerFeetY = mc.player.getY();
            BlockPos downPos = new BlockPosX(mc.player.getPos()).down();
            if (!mc.world.isAir(downPos)) {
                VoxelShape shape = mc.world.getBlockState(downPos).getCollisionShape(mc.world, downPos);
                if (!shape.isEmpty()) {
                    double blockTopY = downPos.getY() + shape.getMax(Direction.Axis.Y);
                    double distance = playerFeetY - blockTopY;
                    if (distance >= 0 && distance < excess) {
                        this.throwDirection = Direction.DOWN;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ========== 效果是否需要投掷的判断（保留 saveDuration） ==========
    private boolean shouldThrowResistance() {
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
        return effect == null ||
                effect.getAmplifier() < 2 ||
                (effect.getDuration() / 20.0f) < saveDuration.getValue();
    }

    private boolean shouldThrowSpeed() {
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.SPEED);
        return effect == null || (effect.getDuration() / 20.0f) < saveDuration.getValue();
    }

    private boolean shouldThrowStrength() {
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.STRENGTH);
        return effect == null || (effect.getDuration() / 20.0f) < saveDuration.getValue();
    }

    private boolean shouldThrowSlowFalling() {
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.SLOW_FALLING);
        return effect == null || (effect.getDuration() / 20.0f) < saveDuration.getValue();
    }

    // ========== 重试判断（保留） ==========
    private boolean shouldRetryThrow(StatusEffect targetEffect) {
        if (!this.isOn()) return false;
        if (!checkThrow(targetEffect)) return false;

        float threshold = (float) saveDuration.getValue();

        if (targetEffect == StatusEffects.RESISTANCE) {
            StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
            return effect == null || effect.getAmplifier() < 2 || (effect.getDuration() / 20.0f) < threshold;
        } else if (targetEffect == StatusEffects.SPEED) {
            StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.SPEED);
            return effect == null || (effect.getDuration() / 20.0f) < threshold;
        } else if (targetEffect == StatusEffects.STRENGTH) {
            StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.STRENGTH);
            return effect == null || (effect.getDuration() / 20.0f) < threshold;
        } else if (targetEffect == StatusEffects.SLOW_FALLING) {
            StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.SLOW_FALLING);
            return effect == null || (effect.getDuration() / 20.0f) < threshold;
        }
        return false;
    }

    public boolean checkThrow(StatusEffect targetEffect) {
        if (!EntityUtil.inInventory()) return false;
        if (this.usingPause.getValue() && mc.player.isUsingItem()) return false;
        return findPotion(targetEffect) != -1 || (this.inventory.getValue() && findPotionInventorySlot(targetEffect) != -1);
    }

    public boolean shouldThrowNow() {
        if (!this.isOn()) return false;
        if (this.inventory.getValue() && !EntityUtil.inInventory()) return false;
        if (this.usingPause.getValue() && mc.player.isUsingItem()) return false;
        if (!this.delayTimer.passedMs((long)(this.delay.getValue() * 1000.0))) return false;
        if (!isGroundConditionSatisfied()) return false;

        if (this.resistance.getValue() && shouldThrowResistance() && checkThrow(StatusEffects.RESISTANCE.value())) return true;
        if (this.speed.getValue() && shouldThrowSpeed() && checkThrow(StatusEffects.SPEED.value())) return true;
        if (this.strength.getValue() && shouldThrowStrength() && checkThrow(StatusEffects.STRENGTH.value())) return true;
        if (this.slowFalling.getValue() && shouldThrowSlowFalling() && checkThrow(StatusEffects.SLOW_FALLING.value())) return true;
        return false;
    }

    // ========== 核心更新逻辑（删除 yawStep 相关分支） ==========
    @EventListener
    public void onUpdate(UpdateEvent event) {
        this.throwing = false;
        if (this.inventory.getValue() && !EntityUtil.inInventory()) return;
        if (!this.delayTimer.passedMs((long)(delay.getValue() * 1000.0))) return;
        if (!isGroundConditionSatisfied()) return;

        // 删除 pendingEffect/directionVec 检查和 RotationEvent 等待逻辑，直接投掷
        if (this.resistance.getValue() && shouldThrowResistance()) {
            this.throwing = this.checkThrow(StatusEffects.RESISTANCE.value());
            if (this.isThrow()) {
                this.throwPotion(StatusEffects.RESISTANCE.value());
                return;
            }
        }
        if (this.speed.getValue() && shouldThrowSpeed()) {
            this.throwing = this.checkThrow(StatusEffects.SPEED.value());
            if (this.isThrow()) {
                this.throwPotion(StatusEffects.SPEED.value());
                return;
            }
        }
        if (this.strength.getValue() && shouldThrowStrength()) {
            this.throwing = this.checkThrow(StatusEffects.STRENGTH.value());
            if (this.isThrow()) {
                this.throwPotion(StatusEffects.STRENGTH.value());
                return;
            }
        }
        if (this.slowFalling.getValue() && shouldThrowSlowFalling()) {
            this.throwing = this.checkThrow(StatusEffects.SLOW_FALLING.value());
            if (this.isThrow()) {
                this.throwPotion(StatusEffects.SLOW_FALLING.value());
            }
        }
    }

    // ========== 删除 onRotate 和 isRotationReady ==========

    // ========== 投掷方法（保留原有 smart 方向计算，改用 snapAt/snapBack） ==========
    public void throwPotion(StatusEffect targetEffect) {
        this.actuallyThrowing = true;
        float yaw = Alien.ROTATION.rotationYaw;
        float pitch = 90.0f;

        if (this.smart.getValue() && this.throwDirection != Direction.DOWN) {
            if (this.throwDirection == Direction.UP) {
                pitch = -90.0f;
            } else {
                BlockPos clickBlockPos = new BlockPosX(mc.player.getPos()).offset(this.throwDirection);
                Vec3d center = clickBlockPos.toCenterPos();
                double offsetX = this.throwDirection.getOffsetX() * 0.5;
                double offsetY = this.throwDirection.getOffsetY() * 0.5;
                double offsetZ = this.throwDirection.getOffsetZ() * 0.5;
                Vec3d clickPos = new Vec3d(center.x + offsetX, center.y + offsetY, center.z + offsetZ);
                float[] angles = Alien.ROTATION.getRotation(clickPos);
                yaw = angles[0];
                pitch = angles[1];
            }
        }

        // 直接瞬间旋转并投掷
        executeThrowDirect(targetEffect, yaw, pitch);
    }

    private void executeThrowDirect(StatusEffect targetEffect, float yaw, float pitch) {
        int oldSlot = mc.player.getInventory().selectedSlot;
        int newSlot;

        // 瞬间转向
        Alien.ROTATION.snapAt(yaw, pitch);

        if (this.inventory.getValue() && (newSlot = findPotionInventorySlot(targetEffect)) != -1) {
            InventoryUtil.inventorySwap(newSlot, mc.player.getInventory().selectedSlot);

            final int inventorySlot = newSlot;
            final float finalYaw = Alien.ROTATION.getLastYaw();
            final float finalPitch = Alien.ROTATION.getLastPitch();
            mc.execute(() -> {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, finalYaw, finalPitch));
                mc.execute(() -> {
                    InventoryUtil.inventorySwap(inventorySlot, mc.player.getInventory().selectedSlot);
                    EntityUtil.syncInventory();
                    this.delayTimer.reset();
                    this.throwing = false;
                    Alien.ROTATION.snapBack();  // 恢复旋转

                    mc.execute(() -> {
                        mc.execute(() -> {
                            if (this.shouldRetryThrow(targetEffect)) {
                                this.throwPotion(targetEffect);
                            } else {
                                this.actuallyThrowing = false;
                            }
                        });
                    });
                });
            });
        } else if ((newSlot = findPotion(targetEffect)) != -1) {
            InventoryUtil.switchToSlot(newSlot);

            final float finalYaw = Alien.ROTATION.getLastYaw();
            final float finalPitch = Alien.ROTATION.getLastPitch();
            mc.execute(() -> {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, finalYaw, finalPitch));
                mc.execute(() -> {
                    InventoryUtil.switchToSlot(oldSlot);
                    this.delayTimer.reset();
                    this.throwing = false;
                    Alien.ROTATION.snapBack();  // 恢复旋转

                    mc.execute(() -> {
                        mc.execute(() -> {
                            if (this.shouldRetryThrow(targetEffect)) {
                                this.throwPotion(targetEffect);
                            } else {
                                this.actuallyThrowing = false;
                            }
                        });
                    });
                });
            });
        } else {
            this.actuallyThrowing = false;
            Alien.ROTATION.snapBack(); // 没找到药水也要恢复旋转
        }
    }

    public boolean isThrow() {
        return this.throwing;
    }

    // ========== 按键触发内部类（保留原有逻辑） ==========
    public class AutoPotTick {
        @EventListener
        public void onTick(ClientTickEvent event) {
            if (Module.nullCheck() || event.isPost()) return;
            if (AutoPot.this.inventory.getValue() && !EntityUtil.inInventory()) return;
            if (Wrapper.mc.currentScreen == null) {
                if (AutoPot.this.resistanceKey.isPressed()) {
                    if (!AutoPot.this.turtlePress && AutoPot.this.checkThrow(StatusEffects.RESISTANCE.value())) {
                        AutoPot.this.throwDirection = Direction.DOWN;
                        AutoPot.this.throwPotion(StatusEffects.RESISTANCE.value());
                        AutoPot.this.turtlePress = true;
                        return;
                    }
                } else {
                    AutoPot.this.turtlePress = false;
                }

                if (AutoPot.this.strengthKey.isPressed()) {
                    if (!AutoPot.this.strengthPress && AutoPot.this.checkThrow(StatusEffects.STRENGTH.value())) {
                        if (AutoPot.this.isGroundConditionSatisfied()) {
                            AutoPot.this.throwPotion(StatusEffects.STRENGTH.value());
                        }
                        AutoPot.this.strengthPress = true;
                        return;
                    }
                } else {
                    AutoPot.this.strengthPress = false;
                }

                if (AutoPot.this.speedKey.isPressed()) {
                    if (!AutoPot.this.speedPress && AutoPot.this.checkThrow(StatusEffects.SPEED.value())) {
                        if (AutoPot.this.isGroundConditionSatisfied()) {
                            AutoPot.this.throwPotion(StatusEffects.SPEED.value());
                        }
                        AutoPot.this.speedPress = true;
                    }
                } else {
                    AutoPot.this.speedPress = false;
                }
            } else {
                AutoPot.this.speedPress = false;
                AutoPot.this.turtlePress = false;
                AutoPot.this.strengthPress = false;
            }
        }
    }
}