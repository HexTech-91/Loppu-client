package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import dev.luminous.mod.modules.impl.nagisa.AAPro;
import dev.luminous.mod.modules.impl.combat.CevBreaker;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.PickaxeItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.CobwebBlock;

public class SmartDigger extends Module {
   public static SmartDigger INSTANCE;
   public BlockPos currentMineTarget = null;
   public final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 6.0, 0.0, 8.0, 0.1).setSuffix("m"));
   public final SliderSetting range = this.add(new SliderSetting("Range", 6.0, 0.0, 8.0, 0.1).setSuffix("m"));
   private final SliderSetting updateDelay = this.add(new SliderSetting("UpdateDelay", 50, 0, 1000).setSuffix("ms"));
   private final Timer delayTimer = new Timer();
   private final BooleanSetting eatpause = this.add(new BooleanSetting("UsingPause", false));
   private final BooleanSetting burrow = this.add(new BooleanSetting("Burrow", true));
   private final BooleanSetting face = this.add(new BooleanSetting("Face", true));
   private final BooleanSetting down = this.add(new BooleanSetting("Down", false).setParent());
   public final BooleanSetting smart = this.add(new BooleanSetting("Smart", false, () -> this.down.isOpen()));
   public final SliderSetting yandy = this.add(new SliderSetting("Y", 2, 1, 3, () -> this.down.isOpen() && this.smart.getValue()).setSuffix("m"));
   private final BooleanSetting surround = this.add(new BooleanSetting("Surround", true).setParent());
   private final EnumSetting<SmartDigger.MineMode> mineMode = this.add(
      new EnumSetting<>("MineMode", SmartDigger.MineMode.Normal, () -> this.surround.isOpen())
   );
   private final BooleanSetting second = this.add(new BooleanSetting("DoubleMine", false).setParent());
   private final EnumSetting<SmartDigger.CoverMode> coverMode = this.add(
      new EnumSetting<>("CoverMode", SmartDigger.CoverMode.Normal, () -> this.second.isOpen())
   );
   private final BooleanSetting checkBedrock = this.add(new BooleanSetting("CheckBedrock", true));
   boolean mineBur = false;
   boolean mineBlocker = false;

   public SmartDigger() {
      super("SmartDigger", Module.Category.Combat);
      this.setChinese("智能挖脚");
      INSTANCE = this;
   }

   @EventListener
   public void onTick(ClientTickEvent event) {
      if (event.isPost()) {
         if (!CevBreaker.INSTANCE.isOn()) {
            if (!Blink.INSTANCE.isOn() || !Blink.INSTANCE.pauseModule.getValue()) {
               if (this.delayTimer.passedMs((long)this.updateDelay.getValue())) {
                  if (mc.player == null || !this.eatpause.getValue() || !mc.player.isUsingItem()) {
                     PlayerEntity player = CombatUtil.getClosestEnemy(this.targetRange.getValue());
                     if (player != null) {
                        if (mc.world == null
                           || mc.world.getBlockState(EntityUtil.getEntityPos(player)).getBlock() != Blocks.BEDROCK
                           || !this.checkBedrock.getValue()) {
                           this.doBreak(player);
                           this.delayTimer.reset();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void doBreak(PlayerEntity player) {
      if (mc.player == null || !this.eatpause.getValue() || !mc.player.isUsingItem()) {
         this.mineBur = false;
         this.mineBlocker = false;
         BlockPos pos = EntityUtil.getEntityPos(player, true);
         if (PacketMine.getBreakPos() == null
                 || PacketMine.getBreakPos().equals(PacketMine.secondPos)
                 || PacketMine.secondPos == null
                 || mc.world.isAir(PacketMine.secondPos)
                 || !this.second.getValue()) {
            double[] yOffset = new double[]{-0.8, 0.5, 1.1};
            double[] xzOffset = new double[]{0.3, -0.3};

            for (PlayerEntity entity : CombatUtil.getEnemies(this.targetRange.getValue())) {
               for (double y : yOffset) {
                  for (double x : xzOffset) {
                     for (double z : xzOffset) {
                        BlockPos offsetPos = new BlockPosX(entity.getX() + x, entity.getY() + y, entity.getZ() + z);
                        BlockPos currentBreakPos = PacketMine.getBreakPos();
                        if (currentBreakPos != null && offsetPos.equals(currentBreakPos) && this.canBreak(offsetPos)) {
                           return;
                        }
                     }
                  }
               }
            }

         List<Float> yList = new ArrayList<>();
         if (this.down.getValue()
            && mc.player != null
            && this.smart.getValue()
            && player.getBlockPos().getY() - mc.player.getBlockPos().getY() > this.yandy.getValue()) {
            yList.add(-0.8F);
         }

         if (this.burrow.getValue()) {
            yList.add(0.15F);
         }

         if (this.face.getValue()) {
            yList.add(1.1F);
         }

         Iterator var37 = yList.iterator();

         while (var37.hasNext()) {
            double y = ((Float)var37.next()).floatValue();

            for (double offset : xzOffset) {
               BlockPos offsetPos = new BlockPosX(player.getX() + offset, player.getY() + y, player.getZ() + offset);
               if (this.canBreak(offsetPos)) {
                  PacketMine.INSTANCE.mine(offsetPos);
                  this.currentMineTarget = offsetPos;
                  this.mineBur = true;
                  return;
               }
            }
         }

         var37 = yList.iterator();

         while (var37.hasNext()) {
            double y = ((Float)var37.next()).floatValue();

            for (double offset : xzOffset) {
               for (double offset2 : xzOffset) {
                  BlockPos offsetPos = new BlockPosX(player.getX() + offset2, player.getY() + y, player.getZ() + offset);
                  if (this.canBreak(offsetPos)) {
                     PacketMine.INSTANCE.mine(offsetPos);
                     this.mineBur = true;
                     return;
                  }
               }
            }
         }

         if (this.surround.getValue()) {
            ArrayList<BlockPos> validPositions = new ArrayList<>();

            for (Direction i : Direction.values()) {
               if (i != Direction.UP
                  && i != Direction.DOWN
                  && (
                     mc.player == null
                        || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                  )
                  && !(BlockUtil.getBlock(pos.offset(i)) instanceof CobwebBlock)
                  && this.canBreak(pos.offset(i))
                  && this.canPlaceCrystal(pos.offset(i), true)) {
                  validPositions.add(pos.offset(i));
               }
            }

            if (this.mineMode.getValue() == SmartDigger.MineMode.Normal) {
               boolean hasAir = false;

               for (Direction i : Direction.values()) {
                  if (i != Direction.UP
                     && i != Direction.DOWN
                     && (
                        mc.player == null
                           || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                     )
                     && mc.world != null
                     && (mc.world.isAir(pos.offset(i)) || pos.offset(i).equals(PacketMine.getBreakPos()))
                     && this.canPlaceCrystal(pos.offset(i), false)) {
                     hasAir = true;
                     break;
                  }
               }

               if (hasAir) {
                  return;
               }

               if (validPositions.size() >= 2 && this.second.getValue()) {
                  validPositions.sort(Comparator.comparingDouble(E -> mc.player != null ? E.getSquaredDistance(mc.player.getEyePos()) : 0.0));
                  PacketMine.INSTANCE.mine(validPositions.getFirst());
                  this.currentMineTarget = validPositions.getFirst();
               } else if (!validPositions.isEmpty()) {
                  BlockPos minePos = validPositions.stream()
                           .min(Comparator.comparingDouble(E -> mc.player != null ? E.getSquaredDistance(mc.player.getEyePos()) : 0.0))
                           .get();
                  PacketMine.INSTANCE.mine(minePos);
                  this.currentMineTarget = minePos;
               } else {
                  List<BlockPos> surroundList = new ArrayList<>();

                  for (Direction i : Direction.values()) {
                     if (i != Direction.UP
                        && i != Direction.DOWN
                        && (
                           mc.player == null
                              || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                        )
                        && !(BlockUtil.getBlock(pos.offset(i)) instanceof CobwebBlock)
                        && this.canBreak(pos.offset(i))
                        && this.canPlaceCrystal(pos.offset(i), false)) {
                        surroundList.add(pos.offset(i));
                     }
                  }

                  if (!surroundList.isEmpty()) {
                     BlockPos minePos = surroundList.stream().min(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos()))).get();
                     PacketMine.INSTANCE.mine(minePos);
                     this.currentMineTarget = minePos;
                  }
               }
            } else if (this.mineMode.getValue() == SmartDigger.MineMode.Always) {
               if (validPositions.size() >= 2 && this.second.getValue()) {
                  validPositions.sort(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos())));
                  PacketMine.INSTANCE.mine(validPositions.getFirst());
                  this.currentMineTarget = validPositions.getFirst();
               } else if (!validPositions.isEmpty()) {
                  BlockPos minePos = validPositions.stream().min(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos()))).get();
                  PacketMine.INSTANCE.mine(minePos);
                  this.currentMineTarget = minePos;
               } else {
                  ArrayList<BlockPos> fallbackPositions = new ArrayList<>();

                  for (Direction i : Direction.values()) {
                     if (i != Direction.UP
                        && i != Direction.DOWN
                        && (
                           mc.player == null
                              || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                        )
                        && !(BlockUtil.getBlock(pos.offset(i)) instanceof CobwebBlock)
                        && this.canBreak(pos.offset(i))
                        && this.canPlaceCrystal(pos.offset(i), false)) {
                        fallbackPositions.add(pos.offset(i));
                     }
                  }

                  if (!fallbackPositions.isEmpty()) {
                     BlockPos minePos = fallbackPositions.stream().min(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos()))).get();
                     PacketMine.INSTANCE.mine(minePos);
                     this.currentMineTarget = minePos;
                  }
               }
            } else if (this.mineMode.getValue() == SmartDigger.MineMode.First) {
               for (Direction i : Direction.values()) {
                  if (i != Direction.UP
                     && i != Direction.DOWN
                     && (
                        mc.player == null
                           || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                     )
                     && pos.offset(i).equals(PacketMine.getBreakPos())
                     && PacketMine.secondPos == null) {
                     return;
                  }
               }

               if (validPositions.size() >= 2 && this.second.getValue()) {
                  validPositions.sort(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos())));
                  PacketMine.INSTANCE.mine(validPositions.getFirst());
                  this.currentMineTarget = validPositions.getFirst();
               } else if (!validPositions.isEmpty()) {
                  BlockPos minePos = validPositions.stream().min(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos()))).get();
                  PacketMine.INSTANCE.mine(minePos);
                  this.currentMineTarget = minePos;
               } else {
                  ArrayList<BlockPos> fallbackPositions = new ArrayList<>();

                  for (Direction i : Direction.values()) {
                     if (i != Direction.UP
                        && i != Direction.DOWN
                        && (
                           mc.player == null
                              || !(Math.sqrt(mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos())) > this.range.getValue())
                        )
                        && !(BlockUtil.getBlock(pos.offset(i)) instanceof CobwebBlock)
                        && this.canBreak(pos.offset(i))
                        && this.canPlaceCrystal(pos.offset(i), false)) {
                        fallbackPositions.add(pos.offset(i));
                     }
                  }

                  if (!fallbackPositions.isEmpty()) {
                     BlockPos minePos = fallbackPositions.stream().min(Comparator.comparingDouble(E -> E.getSquaredDistance(mc.player.getEyePos()))).get();
                     PacketMine.INSTANCE.mine(minePos);
                     this.currentMineTarget = minePos;
                  }
               }
            }
         }
         }
      }
   }

   private void doMine(BlockPos pos) {
      if (this.canBreak(pos)) {
         this.mine(pos);
      } else if (this.canBreak(pos.up())) {
         this.mine(pos.up());
      }
   }

   public boolean canPlaceCrystal(BlockPos pos, boolean block) {
      BlockPos obsPos = pos.down();
      BlockPos boost = obsPos.up();
      return (BlockUtil.getBlock(obsPos) == Blocks.BEDROCK || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN || !block)
         && BlockUtil.noEntityBlockCrystal(boost, true, true)
         && BlockUtil.noEntityBlockCrystal(boost.up(), true, true);
   }

   public boolean isObsidian(BlockPos pos) {
      return mc.player == null
         ? false
         : mc.player.getEyePos().distanceTo(pos.toCenterPos()) <= this.range.getValue()
            && (
               BlockUtil.getBlock(pos) == Blocks.OBSIDIAN
                  || BlockUtil.getBlock(pos) == Blocks.ENDER_CHEST
                  || BlockUtil.getBlock(pos) == Blocks.NETHERITE_BLOCK
                  || BlockUtil.getBlock(pos) == Blocks.RESPAWN_ANCHOR
            )
            && BlockUtil.getClickSide(pos) != null;
   }

   void mine(BlockPos pos) {
      PacketMine.INSTANCE.mine(pos);
      this.currentMineTarget = pos;
   }

   private boolean canBreak(BlockPos pos) {
      // 跳过AAPro的锚点位置，避免误挖
      if (AAPro.INSTANCE.isOn() && AAPro.currentAnchorPos != null && pos.equals(AAPro.currentAnchorPos)) {
         return false;
      }
      return BlockUtil.getBlock(pos) instanceof CobwebBlock
         ? false
         : this.isObsidian(pos)
            && (BlockUtil.getClickSide(pos) != null || Objects.equals(PacketMine.getBreakPos(), pos))
            && (
                !pos.equals(PacketMine.secondPos)
                    || !(mc.player.getMainHandStack().getItem() instanceof PickaxeItem)
                    && !PacketMine.INSTANCE.autoSwitch.getValue()
                    && !PacketMine.INSTANCE.noGhostHand.getValue()
                    || !this.second.getValue()
            );
   }

   public enum CoverMode {
      Sync,
      Normal;
   }

   public enum MineMode {
      Normal,
      Always,
      First,
      Blocker;
   }

   private record PosWithDir(BlockPos pos, Direction dir) {
   }
}