package dev.luminous.mod.modules.impl.nagisa;

import com.mojang.authlib.GameProfile;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.ExplosionUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.BreakManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Burrow;
import dev.luminous.mod.modules.impl.combat.Surround;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.MathHelper;

public class BorrowAgent extends Module {
   public static BorrowAgent INSTANCE;
   public static Timer delay = new Timer();
   public final HashMap<PlayerEntity, Double> playerSpeeds;
   private BlockPos lastWebPos;
   private int webCleanAttempts;
   private final SliderSetting Delay;
   private final SliderSetting predictTicks;
   private final BooleanSetting terrainIgnore;
   public final BooleanSetting pause;
   public final BooleanSetting movepause;
   public SliderSetting speed;
   public BooleanSetting ccheck;
   private final SliderSetting cRange;
   private final SliderSetting breakMinSelf;
   public BooleanSetting mcheck;
   public BooleanSetting mself;
   private final BooleanSetting clearWeb;
   private final SliderSetting clearWebRange;
   private final SliderSetting clearWebDelay;
   private final SliderSetting clearWebMaxSelfDamage;
   private final BooleanSetting clearWebUseFixedPos;
   private final SliderSetting clearWebPlaceRange;
   private final BooleanSetting surround;
   private boolean wasBurrowActive = false;
   private boolean surroundTriggered = false;
   private static final BlockPos[] FIXED_CRYSTAL_OFFSETS = new BlockPos[]{
      new BlockPos(0, 0, 0),
      new BlockPos(1, 0, 0),
      new BlockPos(-1, 0, 0),
      new BlockPos(0, 0, 1),
      new BlockPos(0, 0, -1),
      new BlockPos(1, 0, 1),
      new BlockPos(1, 0, -1),
      new BlockPos(-1, 0, 1),
      new BlockPos(-1, 0, -1)
   };

   public BorrowAgent() {
      super("BorrowAgent", Module.Category.Combat);
      this.setChinese("埋身代理");
      this.playerSpeeds = new HashMap<>();
      this.Delay = this.add(new SliderSetting("Delay", 100, 0, 1000));
      this.predictTicks = this.add(new SliderSetting("PredictTicks", 4, 0, 10));
      this.terrainIgnore = this.add(new BooleanSetting("TerrainIgnore", true));
      this.pause = this.add(new BooleanSetting("PauseEat", true));
      this.movepause = this.add(new BooleanSetting("MovePause", true));
      this.speed = this.add(new SliderSetting("MaxSpeed", 8, 0, 20));
      this.ccheck = this.add(new BooleanSetting("CheckCrystal", true).setParent());
      this.cRange = this.add(new SliderSetting("Range", 5.0, 0.0, 6.0, () -> this.ccheck.isOpen()));
      this.breakMinSelf = this.add(new SliderSetting("BreakSelf", 12.0, 0.0, 36.0, () -> this.ccheck.isOpen()));
      this.mcheck = this.add(new BooleanSetting("CheckMine", true).setParent());
      this.mself = this.add(new BooleanSetting("Self", true, () -> this.mcheck.isOpen()));
      this.clearWeb = this.add(new BooleanSetting("ClearWeb", true).setParent());
      this.clearWebRange = this.add(new SliderSetting("ClearWebRange", 5.0, 0.0, 6.0, () -> this.clearWeb.isOpen()));
      this.clearWebDelay = this.add(new SliderSetting("ClearWebDelay", 300, 0, 1000, () -> this.clearWeb.isOpen()));
      this.clearWebMaxSelfDamage = this.add(new SliderSetting("ClearWebMaxSelfDamage", 8.0, 0.0, 36.0, () -> this.clearWeb.isOpen()));
      this.clearWebUseFixedPos = this.add(new BooleanSetting("ClearWebUseFixedPos", true, () -> this.clearWeb.isOpen()));
      this.clearWebPlaceRange = this.add(new SliderSetting("ClearWebPlaceRange", 5.0, 1.0, 10.0, () -> this.clearWeb.isOpen()));
      this.surround = this.add(new BooleanSetting("Surround", false));
      this.lastWebPos = null;
      this.webCleanAttempts = 0;
      INSTANCE = this;
   }

   private boolean canbur() {
      BlockPos pos1 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
      BlockPos pos2 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
      BlockPos pos3 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
      BlockPos pos4 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
      EntityUtil.getPlayerPos(true);
      return this.canPlace(pos1) || this.canPlace(pos2) || this.canPlace(pos3) || this.canPlace(pos4);
   }

   private boolean canPlace(BlockPos pos) {
      // 检查是否为SmartDigger的目标
      if (SmartDigger.INSTANCE.isOn() && SmartDigger.INSTANCE.currentMineTarget != null
          && SmartDigger.INSTANCE.currentMineTarget.equals(pos)) {
         return false;
      }
      // 检查是否为HoleKick的目标（当HoleKick开启时）
      if (HoleKick.INSTANCE.isOn() && HoleKick.INSTANCE.currentTargetPos != null
          && HoleKick.INSTANCE.currentTargetPos.equals(pos)) {
         return false;
      }

      if (!BlockUtil.allowAirPlace() && BlockUtil.getPlaceSide(pos) == null) {
         return false;
      } else {
         return !BlockUtil.canReplace(pos) ? false : !this.hasEntity(pos);
      }
   }

   private boolean hasEntity(BlockPos pos) {
      for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
         if (entity != mc.player
            && entity.isAlive()
            && !(entity instanceof ItemEntity)
            && !(entity instanceof ExperienceOrbEntity)
            && !(entity instanceof ArrowEntity)
            && !(entity instanceof ExperienceBottleEntity)) {
            return true;
         }
      }

      return false;
   }

   @EventListener
   public void onTick(ClientTickEvent event) {
      if (event.isPost()) {
         if (!nullCheck()) {
            if (isMaceActive()) {
               return;
            }
            boolean burrowActive = Burrow.INSTANCE != null && Burrow.INSTANCE.isOn();
            if (this.wasBurrowActive
               && !burrowActive
               && this.surround.getValue()
               && !this.surroundTriggered
               && Surround.INSTANCE != null
               && !Surround.INSTANCE.isOn()) {
               Surround.INSTANCE.enable();
               this.surroundTriggered = true;
            }

            if (this.surroundTriggered && Surround.INSTANCE != null && !Surround.INSTANCE.isOn()) {
               this.surroundTriggered = false;
            }

            this.wasBurrowActive = burrowActive;
            if (!delay.passed((long)this.Delay.getValue())) {
               return;
            }

            this.updatePlayerSpeed(mc.player);
            if (this.isPlayerInBurrow()) {
               return;
            }

            if (Math.abs(mc.player.getY() - 320.0) < 0.1) {
               return;
            }

            if (this.hasBlockAbove()) {
               return;
            }

            if (this.movepause.getValue() && this.getPlayerSpeed(mc.player) > 0.1) {
               return;
            }

            boolean shouldBurrow = true;
            if (this.ccheck.getValue()) {
               boolean foundCrystal = this.findAndBreakCrystal();
               if (!foundCrystal && this.mcheck.getValue() && !this.checkmine(this.mself.getValue())) {
                  shouldBurrow = false;
               }
            } else if (this.mcheck.getValue() && !this.checkmine(this.mself.getValue())) {
               shouldBurrow = false;
            }

            if (shouldBurrow
               && (!this.pause.getValue() || !mc.player.isUsingItem())
               && !mc.options.sneakKey.isPressed()
               && this.canbur()
               && mc.player.isOnGround()
               && this.getPlayerSpeed(mc.player) < this.speed.getValueInt()) {
               boolean webCleared = false;
               if (this.clearWeb.getValue()) {
                  List<BlockPos> webPositions = this.findWebAtBurrowPos();
                  if (!webPositions.isEmpty()) {
                     this.clearWebWithCrystal(webPositions);
                     webCleared = true;
                  }
               }

               if (webCleared) {
                  if (!Burrow.INSTANCE.isOn()) {
                     Burrow.INSTANCE.enable();
                     delay.reset();
                  }

                  return;
               }

               if (!Burrow.INSTANCE.isOn()) {
                  Burrow.INSTANCE.enable();
                  delay.reset();
               }
            }
         }
      }
   }

   private boolean isPlayerInBurrow() {
      BlockPos playerPos = mc.player.getBlockPos();
      return mc.world.getBlockState(playerPos).getBlock() != Blocks.AIR;
   }

   private boolean hasBlockAbove() {
      BlockPos playerPos = mc.player.getBlockPos();
      BlockPos headPos = playerPos.up(2);
      return mc.world.getBlockState(headPos).getBlock() != Blocks.AIR;
   }

   private void updatePlayerSpeed(PlayerEntity player) {
      if (player != null) {
         Vec3d motion = player.getVelocity();
         double speedSq = motion.x * motion.x + motion.z * motion.z;
         this.playerSpeeds.put(player, speedSq);
      }
   }

   public boolean findcrystal() {
      BorrowAgent.PlayerAndPredict self = new BorrowAgent.PlayerAndPredict(mc.player);

      for (Entity crystal : mc.world.getEntities()) {
         if (crystal instanceof EndCrystalEntity && EntityUtil.getEyesPos().squaredDistanceTo(crystal.getPos()) <= Math.pow(this.cRange.getValue(), 2.0)) {
            float selfDamage = this.calculateDamage(crystal.getPos(), self.player, self.predict);
            if (selfDamage >= this.breakMinSelf.getValue()) {
               return true;
            }
         }
      }

      return false;
   }

   public double getPlayerSpeed(PlayerEntity player) {
      return this.playerSpeeds.get(player) == null ? 0.0 : this.turnIntoKpH(this.playerSpeeds.get(player));
   }

   public double turnIntoKpH(double input) {
      return MathHelper.sqrt((float)input) * 71.2729367892;
   }

   public float calculateDamage(Vec3d pos, PlayerEntity player, PlayerEntity predict) {
      if (this.terrainIgnore.getValue()) {
         CombatUtil.terrainIgnore = true;
      }

      float damage = ExplosionUtil.calculateDamage(new BlockPosX(pos), predict);
      CombatUtil.terrainIgnore = false;
      return damage;
   }

   public boolean checkmine(boolean self) {
      ArrayList<BlockPos> pos = new ArrayList<>();
      pos.add(EntityUtil.getPlayerPos(true));
      pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
      pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
      pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));
      pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));

      for (BreakManager.BreakData breakData : Alien.BREAK.breakMap.values()) {
         if (breakData != null && breakData.getEntity() != null) {
            for (BlockPos pos1 : pos) {
               if (pos1.equals(breakData.pos) && breakData.getEntity() != mc.player) {
                  return true;
               }
            }
         }
      }

      if (!self) {
         return false;
      }

      for (BlockPos pos12 : pos) {
         if (pos12.equals(PacketMine.getBreakPos())) {
            return true;
         }
      }

      return false;
   }

   private List<BlockPos> findWebAtBurrowPos() {
      List<BlockPos> webPositions = new ArrayList<>();
      Vec3d playerPos = mc.player.getPos();
      float[] xOffsets = new float[]{0.0F, 0.3F, -0.3F};
      float[] zOffsets = new float[]{0.0F, 0.3F, -0.3F};
      BlockPos currentWebPos = null;

      for (float x : xOffsets) {
         for (float z : zOffsets) {
            for (int y = 0; y <= 2; y++) {
               BlockPos pos = new BlockPosX(playerPos.x + x, playerPos.y, playerPos.z + z).up(y);
               if (mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                  webPositions.add(pos);
                  currentWebPos = pos;
               }
            }
         }
      }

      if (currentWebPos != null) {
         if (this.lastWebPos != null && this.lastWebPos.equals(currentWebPos)) {
            this.webCleanAttempts++;
            if (this.webCleanAttempts >= 4) {
               Module iqlost = Alien.MODULE.getModuleByName("IQLost");
               if (iqlost != null) {
                  try {
                     Method method = iqlost.getClass().getMethod("setStubbornWebWarning");
                     method.invoke(iqlost);
                  } catch (Exception var16) {
                  }
               }
            }
         } else {
            this.lastWebPos = currentWebPos;
            this.webCleanAttempts = 1;
         }
      } else {
         this.lastWebPos = null;
         this.webCleanAttempts = 0;
      }

      return webPositions;
   }

   private void clearWebWithCrystal(List<BlockPos> webPositions) {
      if (delay.passed((long)this.clearWebDelay.getValue())) {
         if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL || mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
            for (BlockPos webPos : webPositions) {
               if (!(mc.player.getEyePos().distanceTo(Vec3d.ofCenter(webPos)) > this.clearWebRange.getValue())) {
                  if (this.clearWebUseFixedPos.getValue()) {
                     for (BlockPos offset : FIXED_CRYSTAL_OFFSETS) {
                        BlockPos crystalPos = webPos.add(offset);
                        if (this.canPlaceCrystal(crystalPos) && this.canExplosionReach(crystalPos, webPos)) {
                           float selfDamage = ExplosionUtil.calculateDamage(crystalPos.toCenterPos(), mc.player, mc.player, 6.0F);
                           if (selfDamage <= this.clearWebMaxSelfDamage.getValue()) {
                              this.placeAndBreakCrystal(crystalPos);
                              return;
                           }
                        }
                     }
                  } else {
                     for (BlockPos pos : BlockUtil.getSphere((float)this.clearWebPlaceRange.getValue() + 1.0F, mc.player.getEyePos())) {
                        if (this.canPlaceCrystal(pos) && this.canExplosionReach(pos, webPos)) {
                           float selfDamage = ExplosionUtil.calculateDamage(pos.toCenterPos(), mc.player, mc.player, 6.0F);
                           if (selfDamage <= this.clearWebMaxSelfDamage.getValue()) {
                              this.placeAndBreakCrystal(pos);
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean canPlaceCrystal(BlockPos pos) {
      BlockPos obsidianPos = pos.down();
      BlockPos above1 = obsidianPos.up();
      BlockPos above2 = above1.up();
      Block block = mc.world.getBlockState(obsidianPos).getBlock();
      if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
         return false;
      } else if (BlockUtil.getClickSideStrict(obsidianPos) == null) {
         return false;
      } else {
         return !this.noEntityBlockCrystal(above1) || !this.noEntityBlockCrystal(above2)
            ? false
            : mc.world.isAir(above1) || BlockUtil.hasCrystal(above1);
      }
   }

   private boolean noEntityBlockCrystal(BlockPos pos) {
      for (Entity entity : mc.world.getEntities()) {
         if (!(entity instanceof EndCrystalEntity) && !(entity instanceof ItemEntity) && entity.getBoundingBox().intersects(new Box(pos))) {
            return false;
         }
      }

      return true;
   }

   private boolean canExplosionReach(BlockPos crystalPos, BlockPos webPos) {
      return crystalPos.getSquaredDistance(webPos) <= 16.0;
   }

   private void placeAndBreakCrystal(BlockPos crystalPos) {
      BlockUtil.clickBlock(crystalPos, null, false, Hand.MAIN_HAND, SwingSide.All);
      delay.reset();

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EndCrystalEntity crystal) {
            Vec3d crystalVec = crystal.getPos();
            if (crystalVec.squaredDistanceTo(Vec3d.ofCenter(crystalPos)) <= 1.0) {
               mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
               break;
            }
         }
      }
   }

   private boolean findAndBreakCrystal() {
      BorrowAgent.PlayerAndPredict self = new BorrowAgent.PlayerAndPredict(mc.player);
      boolean foundCrystal = false;
      BlockPos playerPos = EntityUtil.getPlayerPos(true);

      for (Entity crystal : mc.world.getEntities()) {
         if (crystal instanceof EndCrystalEntity) {
            double distance = EntityUtil.getEyesPos().squaredDistanceTo(crystal.getPos());
            if (distance <= Math.pow(this.cRange.getValue(), 2.0)) {
               BlockPos crystalPos = crystal.getBlockPos();
               if (this.isCrystalNearBurrowPos(crystalPos, playerPos)) {
                  float selfDamage = this.calculateDamage(crystal.getPos(), self.player, self.predict);
                  mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                  mc.player.resetLastAttackedTicks();
                  EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
                  foundCrystal = true;
                  break;
               }
            }
         }
      }

      return foundCrystal;
   }

   private boolean isCrystalNearBurrowPos(BlockPos crystalPos, BlockPos playerPos) {
      int xDiff = Math.abs(crystalPos.getX() - playerPos.getX());
      int yDiff = Math.abs(crystalPos.getY() - playerPos.getY());
      int zDiff = Math.abs(crystalPos.getZ() - playerPos.getZ());
      return xDiff <= 2 && yDiff <= 2 && zDiff <= 2;
   }

   private boolean isMaceActive() {
      return (GroundMace.INSTANCE != null && GroundMace.INSTANCE.isOn())
              || (TPMace.INSTANCE != null && TPMace.INSTANCE.isOn())
              || (VoidMace.INSTANCE != null && VoidMace.INSTANCE.isOn())
              || (MaceAura.INSTANCE != null && MaceAura.INSTANCE.isOn());
   }

   public class PlayerAndPredict {
      PlayerEntity player;
      PlayerEntity predict;

      public PlayerAndPredict(PlayerEntity player) {
         this.player = player;
         if (!(BorrowAgent.this.predictTicks.getValueFloat() > 0.0F)) {
            this.predict = player;
         } else {
            this.predict = new PlayerEntity(
               Wrapper.mc.world,
               player.getBlockPos(),
               player.getYaw(),
               new GameProfile(UUID.fromString("66123666-1234-5432-6666-667563866600"), "PredictEntity339")
            ) {
               public boolean isSpectator() {
                  return false;
               }

               public boolean isCreative() {
                  return false;
               }
            };
            Vec3d motion = player.getVelocity();
            Vec3d motionVec = player.getPos()
               .add(
                  motion.x * BorrowAgent.INSTANCE.predictTicks.getValueInt(),
                  motion.y * BorrowAgent.INSTANCE.predictTicks.getValueInt(),
                  motion.z * BorrowAgent.INSTANCE.predictTicks.getValueInt()
               );
            this.predict.setPos(motionVec.x, motionVec.y, motionVec.z);
            this.predict.setPitch(player.getPitch());
            this.predict.headYaw = player.headYaw;
            this.predict.bodyYaw = player.bodyYaw;
            this.predict.setOnGround(player.isOnGround());
            this.predict.getInventory().clone(player.getInventory());
            this.predict.setHealth(player.getHealth());

            for (StatusEffectInstance se : player.getStatusEffects()) {
               this.predict.addStatusEffect(se);
            }
         }
      }
   }
}
