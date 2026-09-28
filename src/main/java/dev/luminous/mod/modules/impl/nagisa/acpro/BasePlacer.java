package dev.luminous.mod.modules.impl.nagisa.acpro;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.nagisa.HitBoxMace;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.nagisa.GroundMace;
import dev.luminous.mod.modules.impl.nagisa.HoleKick;
import dev.luminous.mod.modules.impl.nagisa.MaceAura;
import dev.luminous.mod.modules.impl.nagisa.SmartDigger;
import dev.luminous.mod.modules.impl.nagisa.TPMace;
import dev.luminous.mod.modules.impl.nagisa.VoidMace;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class BasePlacer extends Module {
   public static BasePlacer INSTANCE;
   public static BlockPos crystalPos;
   private final Timer placeTimer = new Timer();
   private final Timer delayTimer = new Timer();
   private final Timer batchTimer = new Timer();
   private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
   private final BooleanSetting detectMining = this.add(new BooleanSetting("DetectMining", false));
   private final BooleanSetting breakOnlyHasCrystal = this.add(new BooleanSetting("OnlyHold", true));
   private final BooleanSetting eatingPause = this.add(new BooleanSetting("EatingPause", true));
   private final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 12.0, 0.0, 20.0).setSuffix("m"));
   private final SliderSetting updateDelay = this.add(new SliderSetting("UpdateDelay", 50, 0, 1000).setSuffix("ms"));
   private final SliderSetting wallRange = this.add(new SliderSetting("WallRange", 6.0, 0.0, 6.0).setSuffix("m"));
   private final SliderSetting batchDelay = this.add(new SliderSetting("BatchDelay", 50, 0, 500));
   private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
   private final BooleanSetting yawStep = this.add(new BooleanSetting("YawStep", false));
   private final SliderSetting steps = this.add(new SliderSetting("Steps", 0.05, 0.0, 1.0, 0.01));
   private final BooleanSetting checkFov = this.add(new BooleanSetting("OnlyLooking", true));
   private final SliderSetting fov = this.add(new SliderSetting("Fov", 5.0, 0.0, 30.0));
   private final SliderSetting priority = this.add(new SliderSetting("Priority", 10, 0, 100));
   private final SliderSetting range = this.add(new SliderSetting("Range", 5.0, 0.0, 6.0).setSuffix("m"));
   private final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0).setSuffix("m"));
   private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 300, 0, 1000).setSuffix("ms"));
   private final BooleanSetting useThread = this.add(new BooleanSetting("UseThread", true));
   private final BooleanSetting doCrystal = this.add(new BooleanSetting("CalcDoPlace", false));
   private final BooleanSetting lite = this.add(new BooleanSetting("Lite", false));
   private final BooleanSetting syncCrystal = this.add(new BooleanSetting("SyncCrystal", true));
   private final BooleanSetting autoPlace = this.add(new BooleanSetting("AutoPlace", true));
   private final BooleanSetting smartPlace = this.add(new BooleanSetting("SmartPlace", true));
   private final BooleanSetting multiTarget = this.add(new BooleanSetting("MultiTarget", false));
   private final SliderSetting minDamage = this.add(new SliderSetting("MinDamage", 5.0, 0.0, 36.0).setSuffix("hp"));
   private final EnumSetting<BasePlacer.PrioritizeMode> prioritizeMode = this.add(new EnumSetting<>("PrioritizeMode", BasePlacer.PrioritizeMode.HIGH_DAMAGE));
   private final SliderSetting placeBlocks = this.add(new SliderSetting("PlaceBlocks", 2, 0, 10).setSuffix("Tick"));
   private final List<BlockPos> placeQueue = new ArrayList<>();
   public PlayerEntity displayTarget;
   public float breakDamage;
   public float tempDamage;
   public float lastDamage;
   public Vec3d directionVec = null;
   private BlockPos tempPos;
   private int batchProgress = 0;

   public BlockPos getTargetPos() {
      return this.tempPos;
   }

   private boolean isConflictWithOthers(BlockPos pos) {
      // 检查是否为SmartDigger的挖掘目标
      if (SmartDigger.INSTANCE != null && SmartDigger.INSTANCE.isOn() && SmartDigger.INSTANCE.currentMineTarget != null) {
         if (pos.equals(SmartDigger.INSTANCE.currentMineTarget) || pos.up().equals(SmartDigger.INSTANCE.currentMineTarget)) {
            return true;
         }
      }
      // 检查是否为HoleKick的目标（当HoleKick开启）
      if (HoleKick.INSTANCE != null && HoleKick.INSTANCE.isOn() && HoleKick.INSTANCE.currentTargetPos != null) {
         BlockPos holeKickPos = HoleKick.INSTANCE.currentTargetPos;
         // 检查是否在目标玩家附近（包括上下和四周）
         if (Math.abs(pos.getX() - holeKickPos.getX()) <= 2 &&
             Math.abs(pos.getY() - holeKickPos.getY()) <= 2 &&
             Math.abs(pos.getZ() - holeKickPos.getZ()) <= 2) {
            return true;
         }
      }
      return false;
   }

   public BasePlacer() {

      super("BasePlacer", Module.Category.Combat);

      this.setChinese("基座放置者");
      INSTANCE = this;
   }

   public static boolean canSee(Vec3d from, Vec3d to) {
      HitResult result = null;
      if (mc.world != null && mc.player != null) {
         result = mc.world.raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
      }

      return result == null || result.getType() == Type.MISS;
   }

   @Override
   public String getInfo() {
      return this.displayTarget != null && this.lastDamage > 0.0F
         ? this.displayTarget.getName().getString() + ", " + new DecimalFormat("0.0").format(this.lastDamage)
         : null;
   }

   @Override
   public void onDisable() {
      crystalPos = null;
      this.tempPos = null;
      this.placeQueue.clear();
      this.batchProgress = 0;
   }

   @Override
   public void onEnable() {
      INSTANCE = this;
      this.placeQueue.clear();
      this.batchProgress = 0;
      this.batchTimer.reset();
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
               this.tempPos = null;
               this.delayTimer.reset();
            } else if (this.breakOnlyHasCrystal.getValue()
               && !mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)
               && !mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL)
               && !ACPro.INSTANCE.findCrystal()) {
               this.tempPos = null;
               this.delayTimer.reset();
            } else {
               this.delayTimer.reset();
               this.breakDamage = 0.0F;
               this.tempPos = null;
               this.tempDamage = 0.0F;
               float tempTargetHealth = Float.MAX_VALUE;
               double tempTargetDistance = Double.MAX_VALUE;
               if (this.syncCrystal.getValue() && ACPro.INSTANCE.isOn() && ACPro.tempPos != null) {
                  this.tempPos = ACPro.tempPos;
                  this.tempDamage = ACPro.INSTANCE.tempDamage;
                  this.displayTarget = ACPro.INSTANCE.displayTarget;
               } else {
                  List<PlayerEntity> list = CombatUtil.getEnemies(this.targetRange.getValue());
                  if (!list.isEmpty()) {
                     this.placeQueue.clear();

                     label228:
                     for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue() + 1.0F)) {
                        CombatUtil.modifyPos = null;
                        // 联动检查：跳过与其他模块冲突的位置
                        if (this.isConflictWithOthers(pos)) continue;
                        if (!this.isEnemySurround(pos, list)
                           && !(mc.player.getEyePos().distanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0)) > this.range.getValue())
                           && this.canPlaceCrystal(pos, true, false)) {
                           CombatUtil.modifyPos = pos.down();
                           CombatUtil.modifyBlockState = Blocks.OBSIDIAN.getDefaultState();
                           if (!this.behindWall(pos) && this.canTouch(pos.down())) {
                              Iterator var8 = list.iterator();

                              while (true) {
                                 PlayerEntity target;
                                 float damage;
                                 float targetHealth;
                                 double targetDistance;
                                 boolean shouldUpdate;
                                 label186:
                                 while (true) {
                                    if (!var8.hasNext()) {
                                       if (!this.multiTarget.getValue() && this.tempPos != null && this.tempPos.equals(pos.down())) {
                                          this.placeQueue.add(this.tempPos);
                                       }
                                       continue label228;
                                    }

                                    target = (PlayerEntity)var8.next();
                                    if (pos.down().getY() <= target.getBlockY() - 1
                                       && (!this.lite.getValue() || !this.liteCheck(pos.toCenterPos().add(0.0, -0.5, 0.0), target.getPos()))) {
                                       damage = CrystalDamage.calculateDamage(pos, target, target);
                                       float selfDamage = CrystalDamage.calculateDamage(pos, mc.player, mc.player);
                                       targetHealth = EntityUtil.getHealth(target);
                                       targetDistance = mc.player.distanceTo(target);
                                       if (!(damage < this.minDamage.getValue())
                                          && !(selfDamage > ACPro.INSTANCE.maxSelf.getValue())
                                          && (
                                             !(ACPro.INSTANCE.noSuicide.getValue() > 0.0)
                                                || !(
                                                   selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - ACPro.INSTANCE.noSuicide.getValue()
                                                )
                                          )
                                          && (!this.smartPlace.getValue() || !ACPro.INSTANCE.smart.getValue() || !(damage < selfDamage))) {
                                          shouldUpdate = false;
                                          switch ((BasePlacer.PrioritizeMode)this.prioritizeMode.getValue()) {
                                             case HIGH_DAMAGE:
                                                shouldUpdate = this.tempPos == null || damage > this.tempDamage;
                                                break label186;
                                             case CLOSEST:
                                                shouldUpdate = this.tempPos == null || targetDistance < tempTargetDistance;
                                                break label186;
                                             case LOW_HEALTH:
                                                shouldUpdate = this.tempPos == null || targetHealth < tempTargetHealth;
                                             default:
                                                break label186;
                                          }
                                       }
                                    }
                                 }

                                 if (shouldUpdate) {
                                    this.displayTarget = target;
                                    this.tempPos = pos.down();
                                    this.tempDamage = damage;
                                    tempTargetHealth = targetHealth;
                                    tempTargetDistance = targetDistance;
                                 }

                                 if (this.multiTarget.getValue() && damage >= this.minDamage.getValue()) {
                                    boolean alreadyInQueue = false;

                                    for (BlockPos queuePos : this.placeQueue) {
                                       if (queuePos.equals(pos.down())) {
                                          alreadyInQueue = true;
                                          break;
                                       }
                                    }

                                    if (!alreadyInQueue) {
                                       this.placeQueue.add(pos.down());
                                    }
                                 }
                              }
                           }
                        }
                     }

                     CombatUtil.modifyPos = null;
                     if (this.tempPos != null && !BlockUtil.canPlace(this.tempPos, this.placeRange.getValue())) {
                        this.tempPos = null;
                        this.tempDamage = 0.0F;
                        this.placeQueue.clear();
                     }
                  }
               }

               this.lastDamage = this.tempDamage;
               crystalPos = this.tempPos;
               if (this.doCrystal.getValue() && this.tempPos != null) {
                  this.doPlace(this.tempPos, false);
               }

               if (this.autoPlace.getValue()) {
                  this.doInteract();
               }
            }
         }
      }
   }

   private void doInteract() {
      this.directionVec = null;
      if (!this.placeQueue.isEmpty() && this.batchTimer.passedMs(this.batchDelay.getValue())) {
         int placed = 0;

         for (int i = 0; i < this.placeQueue.size() && placed < this.placeBlocks.getValue(); i++) {
            BlockPos pos = this.placeQueue.get(i);
            if (this.doPlace(pos, true)) {
               placed++;
               this.placeQueue.remove(i);
               i--;
            }
         }

         if (!this.placeQueue.isEmpty()) {
            this.batchTimer.reset();
         }
      } else {
         if (crystalPos != null) {
            this.doPlace(crystalPos, false);
         }
      }
   }

   private boolean faceVector(Vec3d directionVec) {
      if (!this.yawStep.getValue()) {
         Alien.ROTATION.lookAt(directionVec);
         return true;
      } else {
         this.directionVec = directionVec;
         return !this.checkFov.getValue() || Alien.ROTATION.inFov(directionVec, this.fov.getValueFloat());
      }
   }

   public boolean canPlaceCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
      BlockPos obsPos = pos.down();
      BlockPos boost = obsPos.up();
      BlockPos boost2 = boost.up();
      return mc.world == null
         ? false
         : (
               BlockUtil.getBlock(obsPos) == Blocks.BEDROCK
                  || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN
                  || BlockUtil.canPlace(obsPos, this.placeRange.getValue())
            )
            && BlockUtil.getClickSideStrict(obsPos) != null
            && this.noEntityBlockCrystal(boost, ignoreCrystal, ignoreItem)
            && this.noEntityBlockCrystal(boost2, ignoreCrystal, ignoreItem)
            && (mc.world.isAir(boost) || BlockUtil.hasCrystal(boost) && BlockUtil.getBlock(boost) == Blocks.FIRE)
            && (!ClientSetting.INSTANCE.lowVersion.getValue() || mc.world.isAir(boost2));
   }

   private boolean liteCheck(Vec3d from, Vec3d to) {
      return !canSee(from, to) && !canSee(from, to.add(0.0, 1.8, 0.0));
   }

   private boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
      Iterator var4 = BlockUtil.getEntities(new Box(pos)).iterator();

      while (true) {
         if (!var4.hasNext()) {
            return true;
         }

         Entity entity = (Entity)var4.next();
         if (entity.isAlive() && (!ignoreItem || !(entity instanceof ItemEntity))) {
            if (!(entity instanceof EndCrystalEntity)) {
               break;
            }

            if (!ignoreCrystal) {
               return false;
            }

            if (mc.player == null
               || !mc.player.canSee(entity) && !(mc.player.getEyePos().distanceTo(entity.getPos()) <= this.wallRange.getValue())) {
               break;
            }
         }
      }

      return false;
   }

   private boolean behindWall(BlockPos pos) {
      Vec3d testVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5);
      HitResult result = null;
      if (mc.world != null && mc.player != null) {
         result = mc.world.raycast(new RaycastContext(EntityUtil.getEyesPos(), testVec, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
      }

      return result != null && result.getType() != Type.MISS
         ? mc.player.getEyePos().distanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0)) > this.wallRange.getValue()
         : false;
   }

   private boolean canTouch(BlockPos pos) {
      Direction side = BlockUtil.getClickSideStrict(pos);
      return mc.player == null
         ? false
         : side != null
            && pos.toCenterPos()
                  .add(
                     new Vec3d(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5)
                  )
                  .distanceTo(mc.player.getEyePos())
               <= this.range.getValue();
   }

   private boolean doPlace(BlockPos pos, boolean isBatch) {
      if (!this.placeTimer.passedMs((long)this.placeDelay.getValue())) {
         return false;
      }

      BasePlacer.PlaceInfo placeInfo = this.findPlaceInfo(pos);
      if (placeInfo == null) {
         return false;
      }

      int block = this.getBlock();
      if (block == -1) {
         return false;
      }

      Vec3d directionVec = new Vec3d(
         placeInfo.placePos.getX() + 0.5 + placeInfo.side.getVector().getX() * 0.5,
         placeInfo.placePos.getY() + 0.5 + placeInfo.side.getVector().getY() * 0.5,
         placeInfo.placePos.getZ() + 0.5 + placeInfo.side.getVector().getZ() * 0.5
      );
      if (!BlockUtil.canPlace(placeInfo.placePos, this.placeRange.getValue())) {
         return false;
      }

      if (isBatch && !this.batchTimer.passedMs(this.batchDelay.getValue())) {
         return false;
      }

      if (this.rotate.getValue() && !this.faceVector(directionVec)) {
         return false;
      }

      int old = mc.player != null ? mc.player.getInventory().selectedSlot : 0;
      this.doSwap(block);
      BlockUtil.placedPos.add(placeInfo.placePos);
      BlockUtil.clickBlock(placeInfo.clickPos, placeInfo.clickSide, false, Hand.MAIN_HAND);
      if (this.inventory.getValue()) {
         this.doSwap(block);
         EntityUtil.syncInventory();
      } else {
         this.doSwap(old);
      }

      this.placeTimer.reset();
      if (isBatch) {
         this.batchTimer.reset();
      }

      return true;
   }

   private BasePlacer.PlaceInfo findPlaceInfo(BlockPos targetPos) {
      Direction mainSide = BlockUtil.getPlaceSide(targetPos);
      if (mainSide != null && BlockUtil.canPlace(targetPos, this.placeRange.getValue())) {
         BlockPos clickPos = targetPos.offset(mainSide);
         return new BasePlacer.PlaceInfo(targetPos, clickPos, mainSide, mainSide.getOpposite());
      }

      for (Direction direction : Direction.values()) {
         if (direction != Direction.UP) {
            BlockPos neighborPos = targetPos.offset(direction);
            if (BlockUtil.canPlace(neighborPos, this.placeRange.getValue())) {
               Direction placeSide = BlockUtil.getPlaceSide(neighborPos);
               if (placeSide != null) {
                  BlockPos clickPos = neighborPos.offset(placeSide);
                  return new BasePlacer.PlaceInfo(neighborPos, clickPos, placeSide, placeSide.getOpposite());
               }
            }
         }
      }

      if (mc.player != null && targetPos.getY() > mc.player.getY()) {
         for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN) {
               BlockPos extendedPos = targetPos.offset(direction).down();
               if (BlockUtil.canPlace(extendedPos, this.placeRange.getValue())) {
                  Direction placeSide = BlockUtil.getPlaceSide(extendedPos);
                  if (placeSide != null) {
                     BlockPos clickPos = extendedPos.offset(placeSide);
                     return new BasePlacer.PlaceInfo(extendedPos, clickPos, placeSide, placeSide.getOpposite());
                  }
               }
            }
         }
      }

      return null;
   }

   private void doSwap(int slot) {
      if (this.inventory.getValue()) {
         if (mc.player != null) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
         }
      } else {
         InventoryUtil.switchToSlot(slot);
      }
   }

   private int getBlock() {
      return this.inventory.getValue() ? InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN) : InventoryUtil.findBlock(Blocks.OBSIDIAN);
   }

   /**
    * 检查给定位置是否为敌人的 surround 方块位置
    * surround 方块包括：脚下 + 北南东西四个方向
    */
   private boolean isEnemySurround(BlockPos pos, List<PlayerEntity> enemies) {
      BlockPos basePos = pos.down(); // 基座位置
      for (PlayerEntity enemy : enemies) {
         BlockPos enemyPos = EntityUtil.getEntityPos(enemy);
         // 检查是否为敌人脚下位置
         if (basePos.equals(enemyPos)) {
            return true;
         }
         // 检查是否为敌人 surround 方块位置（北南东西四个方向）
         for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (basePos.equals(enemyPos.offset(dir))) {
               return true;
            }
         }
      }
      return false;
   }

   private static class PlaceInfo {
      BlockPos placePos;
      BlockPos clickPos;
      Direction side;
      Direction clickSide;

      PlaceInfo(BlockPos placePos, BlockPos clickPos, Direction side, Direction clickSide) {
         this.placePos = placePos;
         this.clickPos = clickPos;
         this.side = side;
         this.clickSide = clickSide;
      }
   }

   private enum PrioritizeMode {
      HIGH_DAMAGE,
      CLOSEST,
      LOW_HEALTH;
   }
}