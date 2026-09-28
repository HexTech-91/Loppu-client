package dev.luminous.mod.modules.impl.nagisa.acpro;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.AnimateUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.impl.nagisa.*;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.rotation.Rotation;
import dev.luminous.api.utils.rotation.RotationUtils;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.nagisa.GroundMace;
import dev.luminous.mod.modules.impl.nagisa.acpro.basic.CanPlaceCrystal;
import dev.luminous.mod.modules.impl.nagisa.acpro.basic.WallCheck;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ACEnum;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.AntiSurroundPage.AntiSurround;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.BreakPage.Breaks;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.Calc.Calc;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.ForcePlace.ForcePlace;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.GeneralPage.General;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.PlacePage.Place;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.Render.Render;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.RotatePage.Rotate;
import dev.luminous.mod.modules.impl.nagisa.acpro.mode.SyncPage.Sync;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity.RemovalReason;

public class ACPro extends Module {
   public static ACPro INSTANCE;
   public static BlockPos tempPos;
   public static BlockPos breakPos;
   public static EndCrystalEntity breakCrystal;
   public static BlockPos syncPos;
   public static float lastYaw = 0.0F;
   public static float lastPitch = 0.0F;
   static Vec3d placeVec3d;
   static Vec3d curVec3d;
   public final EnumSetting<ACEnum.Page> page;
   public final BooleanSetting preferAnchor;
   public final BooleanSetting preferpiston;
   public final EnumSetting<SwingSide> swingMode;
   public final EnumSetting<ACEnum.SwapMode> autoSwap;
   public final BooleanSetting eatingPause;
   public final BooleanSetting breakOnlyHasCrystal;
   public final SliderSetting switchCooldown;
   public final SliderSetting targetRange;
   public final SliderSetting updateDelay;
   public final SliderSetting wallRange;
   public final SliderSetting spamDelay;
   public final BooleanSetting rotate;
   public final EnumSetting<ACEnum.RotateMode> rotateMode;
   public final BooleanSetting onBreak;
   public final BooleanSetting faceVector;
   public final BooleanSetting checkLook;
   public final SliderSetting fov;
   public final BooleanSetting place;
   public final SliderSetting placeDelay;
   public final SliderSetting range;
   public final SliderSetting minDamage;
   public final SliderSetting maxSelf;
   public final SliderSetting noSuicide;
   public final SliderSetting ingionPiston;
   public final BooleanSetting Break;
   public final SliderSetting breakDelay;
   public final BooleanSetting instant;
   public final EnumSetting<Aura.TargetESP> mode;
   public final ColorSetting color;
   public final ColorSetting box;
   public final SliderSetting lineWidth;
   public final ColorSetting fill;
   public final SliderSetting sliderSpeed;
   public final BooleanSetting smart;
   public final BooleanSetting useThread;
   public final BooleanSetting doCrystal;
   public final BooleanSetting lite;
   public final EnumSetting<ACEnum.CalcMode> calcMode;
   public final BooleanSetting antiSurround;
   public final SliderSetting antiSurroundMax;
   public final BooleanSetting slowPlace;
   public final SliderSetting slowDelay;
   public final SliderSetting slowMinDamage;
   public final BooleanSetting forcePlace;
   public final SliderSetting forceMaxHealth;
   public final SliderSetting forceMin;
   public final BooleanSetting armorBreaker;
   public final SliderSetting maxDurable;
   public final SliderSetting armorBreakerDamage;
   public final BooleanSetting WebSync;
   public final SliderSetting hurtTime;
   public final SliderSetting SyncTime;
   public final SliderSetting WebMinDamage;
   public final BooleanSetting SyncTerrainIgnore;
   public final BooleanSetting WebDeSync;
   public final BooleanSetting breaktime;
   public final BooleanSetting posSync;
   public final BooleanSetting OnlySync;
   public final SliderSetting OnlySyncTime;
   public final SliderSetting SpamSyncTime;
   public final BooleanSetting forceWeb;
   public final BooleanSetting cancelPushSync;
   public final SliderSetting cancelPushSyncDmg;
   public final SliderSetting minAge;
   final BooleanSetting breakRemove;
   final ColorSetting text;
   final ColorSetting misscatext;
   final ColorSetting spamtext;
   final ColorSetting nullpostext;
   final BooleanSetting render;
   final BooleanSetting shrink;
   final SliderSetting startFadeTime;
   final SliderSetting fadeSpeed;
   private final Timer spamTimer;
   private final Timer delayTimer;
   private final Timer placeTimer;
   private final Timer noPosTimer;
   private final Timer switchTimer;
   private final Timer lastBreakTimer;
   public float breakDamage;
   public float tempDamage;
   public float lastDamage;
   public PlayerEntity displayTarget;
   public Vec3d directionVec;
   double fade;
   public static BlockPos crystalPos;

   public ACPro() {
      super("ACPro", Category.Combat);
      this.page = this.add(General.page);
      this.preferAnchor = this.add(General.preferAnchor);
      this.preferpiston = this.add(General.preferpiston);
      this.swingMode = this.add(General.swingMode);
      this.autoSwap = this.add(General.autoSwap);
      this.eatingPause = this.add(General.eatingPause);
      this.breakOnlyHasCrystal = this.add(General.breakOnlyHasCrystal);
      this.switchCooldown = this.add(General.switchCooldown);
      this.targetRange = this.add(General.targetRange);
      this.updateDelay = this.add(General.updateDelay);
      this.wallRange = this.add(General.wallRange);
      this.spamDelay = this.add(General.spamDelay);
      this.rotate = this.add(Rotate.rotate);
      this.rotateMode = this.add(new EnumSetting<>("RotateMode", ACEnum.RotateMode.OffTrack, () -> this.page.getValue() == ACEnum.Page.Rotation));
      this.onBreak = this.add(Rotate.onBreak);
      this.faceVector = this.add(new BooleanSetting("FaceVector", true, () -> this.page.getValue() == ACEnum.Page.Rotation));
      this.checkLook = this.add(Rotate.checkLook);
      this.fov = this.add(Rotate.fov);
      this.place = this.add(Place.place);
      this.placeDelay = this.add(Place.placeDelay);
      this.range = this.add(Place.range);
      this.minDamage = this.add(Place.minDamage);
      this.maxSelf = this.add(Place.maxSelf);
      this.noSuicide = this.add(Place.noSuicide);
      this.ingionPiston = this.add(Place.ingionPiston);
      this.Break = this.add(Breaks.Break);
      this.breakDelay = this.add(Breaks.breakDelay);
      this.instant = this.add(Breaks.instant);
      this.mode = this.add(Render.mode);
      this.color = this.add(Render.color);
      this.box = this.add(Render.box);
      this.lineWidth = this.add(Render.lineWidth);
      this.fill = this.add(Render.fill);
      this.sliderSpeed = this.add(Render.sliderSpeed);
      this.smart = this.add(Calc.smart);
      this.useThread = this.add(Calc.useThread);
      this.doCrystal = this.add(Calc.doCrystal);
      this.lite = this.add(Calc.lite);
      this.calcMode = this.add(Calc.calcMode);
      this.antiSurround = this.add(AntiSurround.antiSurround);
      this.antiSurroundMax = this.add(AntiSurround.antiSurroundMax);
      this.slowPlace = this.add(ForcePlace.slowPlace);
      this.slowDelay = this.add(ForcePlace.slowDelay);
      this.slowMinDamage = this.add(ForcePlace.slowMinDamage);
      this.forcePlace = this.add(ForcePlace.forcePlace);
      this.forceMaxHealth = this.add(ForcePlace.forceMaxHealth);
      this.forceMin = this.add(ForcePlace.forceMin);
      this.armorBreaker = this.add(ForcePlace.armorBreaker);
      this.maxDurable = this.add(ForcePlace.maxDurable);
      this.armorBreakerDamage = this.add(ForcePlace.armorBreakerDamage);
      this.WebSync = this.add(Sync.WebSync);
      this.hurtTime = this.add(Sync.HurtTime);
      this.SyncTime = this.add(Sync.SyncTime);
      this.WebMinDamage = this.add(Sync.WebMinDamage);
      this.SyncTerrainIgnore = this.add(Sync.SyncTerrainIgnore);
      this.WebDeSync = this.add(Sync.WebdeSync);
      this.breaktime = this.add(Sync.breaktime);
      this.posSync = this.add(Sync.posSync);
      this.OnlySync = this.add(Sync.OnlySync);
      this.OnlySyncTime = this.add(Sync.OnlySyncTime);
      this.SpamSyncTime = this.add(Sync.SpamSyncTime);
      this.forceWeb = this.add(Sync.forceWeb);
      this.cancelPushSync = this.add(Sync.cancelPushSync);
      this.cancelPushSyncDmg = this.add(Sync.cancelPushSyncDmg);
      this.minAge = this.add(new SliderSetting("MinAge", 0, 0, 20, () -> this.page.getValue() == ACEnum.Page.Break));
      this.breakRemove = this.add(Breaks.breakRemove);
      this.text = this.add(Render.text);
      this.misscatext = this.add(Render.misscatext);
      this.spamtext = this.add(Render.spamtext);
      this.nullpostext = this.add(Render.nullpostext);
      this.render = this.add(Render.render);
      this.shrink = this.add(Render.shrink);
      this.startFadeTime = this.add(Render.startFadeTime);
      this.fadeSpeed = this.add(Render.fadeSpeed);
      this.spamTimer = new Timer();
      this.delayTimer = new Timer();
      this.placeTimer = new Timer();
      this.noPosTimer = new Timer();
      this.switchTimer = new Timer();
      this.lastBreakTimer = new Timer();
      this.directionVec = null;
      this.fade = 0.0;
      this.setChinese("智能水晶");
      INSTANCE = this;
   }

   @Override
   public String getInfo() {
      return this.displayTarget != null && this.lastDamage > 0.0F
         ? this.displayTarget.getName().getString() + ", " + new DecimalFormat("0.0").format(this.lastDamage)
         : null;
   }

   private void doInteract() {
      if (breakPos != null) {
         this.doBreak(breakPos);
         breakPos = null;
      }

      if (crystalPos != null) {
         this.doCrystal(crystalPos);
      }
   }

   @Override
   public void onRender3D(MatrixStack matrixStack) {
      if (nullCheck() || !render.getValue()) return;

      if (crystalPos == null) {
         fade = AnimateUtil.animate(fade, 0.0, fadeSpeed.getValue() / 20.0);
         return;
      }

      // Animate fade
      fade = AnimateUtil.animate(fade, 1.0, fadeSpeed.getValue() / 10.0);
      if (fade < 0.01) return;

      BlockPos baseBlock = crystalPos.down();

      // Shrink animation: when shrink enabled, box shrinks as fade progresses
      double scale = 1.0;
      if (shrink.getValue()) {
         scale = 1.0 - fade * 0.9;
         if (scale < 0.05) return;
      }

      // Build the box with scale applied from center
      double centerX = baseBlock.getX() + 0.5;
      double centerZ = baseBlock.getZ() + 0.5;
      double halfSize = 0.5 * scale;
      double minY = baseBlock.getY() + (1.0 - scale) / 2.0;
      double maxY = baseBlock.getY() + 1.0 - (1.0 - scale) / 2.0;

      Box renderBox = new Box(centerX - halfSize, minY, centerZ - halfSize,
                              centerX + halfSize, maxY, centerZ + halfSize);

      // Apply fade alpha to colors
      Color fillColor = ColorUtil.injectAlpha(fill.getValue(), (int)(fill.getValue().getAlpha() * fade));
      Color outlineColor = ColorUtil.injectAlpha(box.getValue(), (int)(box.getValue().getAlpha() * fade));

      // Draw fill
      if (fill.getBooleanValue()) {
         Render3DUtil.drawFill(matrixStack, renderBox, fillColor);
      }

      // Draw outline
      if (box.getBooleanValue()) {
         Render3DUtil.drawBox(matrixStack, renderBox, outlineColor, lineWidth.getValueFloat());
      }
   }

   @Override
   public void onEnable() {
      lastYaw = mc.player != null ? mc.player.getYaw() : 0.0F;
      lastPitch = mc.player != null ? mc.player.getPitch() : 0.0F;
      crystalPos = null;
      tempPos = null;
      breakPos = null;
      breakCrystal = null;
      this.displayTarget = null;
      this.lastBreakTimer.reset();
   }

   @Override
   public void onDisable() {
      crystalPos = null;
      tempPos = null;
      breakPos = null;
      breakCrystal = null;
      this.displayTarget = null;
   }

   private static boolean isAnyNoCrystalActive() {
      return VoidMace.isNoCrystalActive() || TPMace.isNoCrystalActive() || GroundMace.isNoCrystalActive()
              || HitBoxMace.isNoCrystalActive()
              || (MaceAura.INSTANCE != null && MaceAura.INSTANCE.isOn());
   }

   @EventListener
   public void onTick(ClientTickEvent event) {
      if (isAnyNoCrystalActive()) {
         return; // 有锤子模块的NoCrystal激活中，暂停执行
      }
      if (event.isPost()) {
         if (this.delayTimer.passedMs((long)this.updateDelay.getValue())) {
            if (mc.player != null && this.eatingPause.getValue() && mc.player.isUsingItem()) {
               this.lastBreakTimer.reset();
            } else {
               this.updateCrystalPos();
               this.doInteract();
               this.delayTimer.reset();
            }
         }
      }
   }

   private void updateCrystalPos() {
      BlockPos prevPos = crystalPos;
      this.update();
      this.lastDamage = this.tempDamage;
      crystalPos = tempPos;
      if (crystalPos != null && !crystalPos.equals(prevPos)) {
         fade = 0.0;
      }
   }

   private void update() {
      if (mc.player != null && mc.world != null) {
         if (this.eatingPause.getValue() && mc.player.isUsingItem()) {
            tempPos = null;
         } else if (!this.switchTimer.passedMs((long)this.switchCooldown.getValue())) {
            tempPos = null;
         } else {
            tempPos = null;
            this.tempDamage = 0.0F;
            breakPos = null;
            breakCrystal = null;
            this.breakDamage = 0.0F;
            List<PlayerEntity> list = CombatUtil.getEnemies(this.targetRange.getValue());
            if (list.isEmpty()) {
               this.lastBreakTimer.reset();
            } else {
               PlayerEntity self = mc.player;

               for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue() + 1.0F)) {
                  if (!WallCheck.behindWall(pos)
                     && !(mc.player.getEyePos().distanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0)) > this.range.getValue())
                     && this.canTouch(pos.down())
                     && CanPlaceCrystal.canPlaceCrystal(pos, true, false)) {
                     for (PlayerEntity target : list) {
                        if (target.hurtTime <= this.hurtTime.getValueInt()) {
                           float damage = CrystalDamage.calculateDamage(pos, target, target);
                           float selfDamage = CrystalDamage.calculateDamage(pos, self, self);
                           if ((tempPos == null || damage > this.tempDamage)
                              && !(selfDamage > this.maxSelf.getValue())
                              && (
                                 !(this.noSuicide.getValue() > 0.0)
                                    || !(selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - this.noSuicide.getValue())
                              )
                              && (
                                 !(damage < EntityUtil.getHealth(target))
                                    || !(damage < this.getDamage(target)) && (!this.smart.getValue() || !(damage < selfDamage))
                              )) {
                              this.displayTarget = target;
                              tempPos = pos;
                              this.tempDamage = damage;
                           }
                        }
                     }
                  }
               }

               for (Entity entity : mc.world.getEntities()) {
                  if (entity instanceof EndCrystalEntity crystal
                     && (mc.player.canSee(crystal) || !(mc.player.getEyePos().distanceTo(crystal.getPos()) > this.wallRange.getValue()))
                     && !(mc.player.getEyePos().distanceTo(crystal.getPos()) > this.range.getValue())) {
                     BlockPos crystalBlockPos = BlockPos.ofFloored(crystal.getPos());
                     for (PlayerEntity target : list) {
                           float damage = CrystalDamage.calculateDamage(BlockPos.ofFloored(crystal.getPos()), target, target);
                           float selfDamage = CrystalDamage.calculateDamage(BlockPos.ofFloored(crystal.getPos()), self, self);
                           if ((breakPos == null || damage > this.breakDamage)
                              && !(selfDamage > this.maxSelf.getValue())
                              && (
                                 !(this.noSuicide.getValue() > 0.0)
                                    || !(selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - this.noSuicide.getValue())
                              )
                              && (
                                 !(damage < EntityUtil.getHealth(target))
                                    || !(damage < this.getDamage(target)) && (!this.smart.getValue() || !(damage < selfDamage))
                              )) {
                              breakPos = new BlockPosX(crystal.getPos());
                              breakCrystal = crystal;
                              if (damage > this.tempDamage) {
                                 this.displayTarget = target;
                              }
                           }
                        }
                  }
               }

               if (this.doCrystal.getValue() && breakPos != null) {
                  this.doBreak(breakPos);
                  breakPos = null;
               }

               if (this.posSync.getValue()) {
                  syncPos = tempPos;
               }

               if (this.doCrystal.getValue() && tempPos != null) {
                  this.doCrystal(tempPos);
               }
            }
         }
      }
   }

   private boolean canTouch(BlockPos pos) {
      Direction side = BlockUtil.getClickSideStrict(pos);
      if (side == null) {
         return false;
      }

      Vec3d vec3d = new Vec3d(
         side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5
      );
      return pos.toCenterPos().add(vec3d).distanceTo(mc.player.getEyePos()) <= this.range.getValue();
   }

   private void doCrystal(BlockPos pos) {
      if (CanPlaceCrystal.canPlaceCrystal(pos, false, true)) {
         if (mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)
            || mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL)
            || this.findCrystal()) {
            this.doPlace(pos);
         }
      } else {
         this.doBreak(pos);
      }
   }

   private double getDamage(PlayerEntity target) {
      if (this.forcePlace.getValue() && EntityUtil.getHealth(target) <= this.forceMaxHealth.getValue()) {
         return this.forceMin.getValue();
      }

      if (this.armorBreaker.getValue()) {
         for (ItemStack armor : target.getInventory().armor) {
            if (!armor.isEmpty()) {
               return this.armorBreakerDamage.getValue();
            }
         }
      }

      return this.minDamage.getValue();
   }

   public boolean findCrystal() {
      return this.autoSwap.getValue() == ACEnum.SwapMode.Off ? false : this.getCrystal() != -1;
   }

   public void doBreak(BlockPos pos) {
      this.noPosTimer.reset();
      if (this.Break.getValue()) {
         if (this.spamTimer.passedMs(this.spamDelay.getValueInt())) {
            this.lastBreakTimer.reset();
            if (this.switchTimer.passedMs((long)this.switchCooldown.getValue())) {
               EndCrystalEntity targetCrystal = null;
               if (mc.world != null) {
                  for (EndCrystalEntity entity : BlockUtil.getEndCrystals(
                     new Box(
                        pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1
                     )
                  )) {
                     if (entity.age >= this.minAge.getValueInt() && !(entity.squaredDistanceTo(pos.toCenterPos()) > 4.0)) {
                        targetCrystal = entity;
                        break;
                     }
                  }
               }

               if (targetCrystal != null && targetCrystal.isAlive()) {
                  Vec3d crystalPos3d = targetCrystal.getPos().add(0.0, 0.0, 0.0);
                  if (!this.rotate.getValue() || !this.onBreak.getValue() || this.faceVector(crystalPos3d)) {
                     if (CombatUtil.breakTimer.passedMs((long)this.breakDelay.getValue())) {
                        CombatUtil.breakTimer.reset();
                        syncPos = pos;
                        Objects.requireNonNull(mc.getNetworkHandler()).sendPacket(PlayerInteractEntityC2SPacket.attack(targetCrystal, mc.player.isSneaking()));
                        mc.player.resetLastAttackedTicks();
                        EntityUtil.swingHand(Hand.MAIN_HAND, this.swingMode.getValue());
                        this.spamTimer.reset();
                        if (this.breakRemove.getValue()) {
                           mc.world.removeEntity(targetCrystal.getId(), RemovalReason.KILLED);
                        }

                        breakCrystal = null;
                        if (crystalPos != null
                           && this.displayTarget != null
                           && this.lastDamage >= this.getDamage(this.displayTarget)
                           && this.instant.getValue()) {
                           this.doPlace(crystalPos);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void doPlace(BlockPos pos) {
      this.noPosTimer.reset();
      if (this.place.getValue()) {
         if (this.spamTimer.passedMs(this.spamDelay.getValue())) {
            if (mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)
               || mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL)
               || this.findCrystal()) {
               if (this.canTouch(pos.down())) {
                  BlockPos obsPos = pos.down();
                  Direction facing = BlockUtil.getClickSide(obsPos);
                  Vec3d vec = obsPos.toCenterPos()
                     .add(
                        facing.getVector().getX() * 0.5, facing.getVector().getY() * 0.5, facing.getVector().getZ() * 0.5
                     );
                  if (facing != Direction.UP && facing != Direction.DOWN) {
                     vec = vec.add(0.0, 0.45, 0.0);
                  }

                  if (!this.rotate.getValue() || this.faceVector(vec)) {
                     if (this.placeTimer.passedMs((long)this.placeDelay.getValue())) {
                        if (!mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)
                           && !mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL)) {
                           this.placeTimer.reset();
                           syncPos = pos;
                           int old = mc.player.getInventory().selectedSlot;
                           int crystal = this.getCrystal();
                           if (crystal == -1) {
                              return;
                           }

                           this.doSwap(crystal);
                           this.placeCrystal(pos);
                           if (this.autoSwap.getValue() == ACEnum.SwapMode.Silent) {
                              this.doSwap(old);
                           } else if (this.autoSwap.getValue() == ACEnum.SwapMode.Inventory) {
                              this.doSwap(crystal);
                              EntityUtil.syncInventory();
                           }

                           this.spamTimer.reset();
                        } else {
                           this.placeTimer.reset();
                           syncPos = pos;
                           this.placeCrystal(pos);
                           this.spamTimer.reset();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void doSwap(int slot) {
      if (this.autoSwap.getValue() != ACEnum.SwapMode.Silent && this.autoSwap.getValue() == ACEnum.SwapMode.Inventory) {
         int oldSlot = mc.player.getInventory().selectedSlot;
         int hotbarSlot = slot >= 36 ? slot - 36 : -1;
         if (hotbarSlot != -1) {
            InventoryUtil.switchToSlot(hotbarSlot);
         } else {
            InventoryUtil.inventorySwap(slot, oldSlot);
         }
      } else {
         InventoryUtil.switchToSlot(slot);
      }
   }

   private int getCrystal() {
      if (this.autoSwap.getValue() == ACEnum.SwapMode.Silent) {
         return InventoryUtil.findItem(Items.END_CRYSTAL);
      } else if (this.autoSwap.getValue() == ACEnum.SwapMode.Normal) {
         return InventoryUtil.findItem(Items.END_CRYSTAL);
      } else {
         return this.autoSwap.getValue() == ACEnum.SwapMode.Inventory ? InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL) : -1;
      }
   }

   public void placeCrystal(BlockPos pos) {
      boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
      BlockPos obsPos = pos.down();
      Direction facing = BlockUtil.getClickSide(obsPos);
      BlockUtil.clickBlock(obsPos, facing, false, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, this.swingMode.getValue());
   }

   private boolean faceVector(Vec3d directionVec) {
      this.directionVec = directionVec;
      if (this.rotate.getValue() && this.rotateMode.getValue() != ACEnum.RotateMode.OffTrack) {
         Rotation rotation = RotationUtils.calculate(directionVec);
         if (rotation != null && mc.player != null) {
            float yawDiff = MathHelper.wrapDegrees(rotation.getYaw() - lastYaw);
            float pitchDiff = MathHelper.wrapDegrees(rotation.getPitch() - lastPitch);
            float stepVal = 1.0F;
            float targetYaw = lastYaw;
            float targetPitch = lastPitch;
            if (Math.abs(yawDiff) > 0.1F || Math.abs(pitchDiff) > 0.1F) {
               if (stepVal >= 1.0F) {
                  targetYaw = rotation.getYaw();
                  targetPitch = rotation.getPitch();
               } else {
                  float maxTurn = 180.0F * stepVal;
                  if (Math.abs(yawDiff) > maxTurn) {
                     yawDiff = Math.signum(yawDiff) * maxTurn;
                  }

                  if (Math.abs(pitchDiff) > maxTurn) {
                     pitchDiff = Math.signum(pitchDiff) * maxTurn;
                  }

                  targetYaw = lastYaw + yawDiff;
                  targetPitch = lastPitch + pitchDiff;
               }
            }
         }
      }

      if (!this.faceVector.getValue()) {
         return true;
      }

      if (this.rotateMode.getValue() == ACEnum.RotateMode.OffTrack) {
         return true;
      }

      Rotation targetRotation = RotationUtils.calculate(directionVec);
      if (targetRotation != null) {
         float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotation.getYaw() - lastYaw));
         float pitchDiff = Math.abs(targetRotation.getPitch() - lastPitch);
         float totalDiff = yawDiff + pitchDiff;
         if (totalDiff <= this.fov.getValueFloat()) {
            return true;
         }
      }

      return !this.checkLook.getValue();
   }

   private record PlayerAndPredict(PlayerEntity player) {
   }
}
