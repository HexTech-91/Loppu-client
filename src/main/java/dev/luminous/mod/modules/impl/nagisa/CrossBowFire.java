package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.TickEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.util.Hand;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;

public class CrossBowFire extends Module {
   public static CrossBowFire INSTANCE;
   private final BooleanSetting enable = this.add(new BooleanSetting("Enable", true));

   public CrossBowFire() {

      super("CrossBowFire", Category.Exploit);
      this.setChinese("诸葛连弩");

      INSTANCE = this;
   }

   @EventListener
   public void onTick(TickEvent event) {
      if (this.enable.getValue()) {
         if (!nullCheck()) {
            if (!HoleKick.INSTANCE.isOn() || HoleKick.INSTANCE.currentTarget == null) {
               ItemStack stack = mc.player.getMainHandStack();
               Hand hand = Hand.MAIN_HAND;
               if (!(stack.getItem() instanceof CrossbowItem)) {
                  stack = mc.player.getOffHandStack();
                  hand = Hand.OFF_HAND;
                  if (!(stack.getItem() instanceof CrossbowItem)) {
                     return;
                  }
               }

               mc.itemUseCooldown = 0;
               if (CrossbowItem.isCharged(stack)) {
                  mc.interactionManager.interactItem(mc.player, hand);
               }

               if (!CrossbowItem.isCharged(stack) && !mc.player.isUsingItem()) {
                  if (!mc.player.getProjectileType(stack).isEmpty()) {
                     mc.interactionManager.interactItem(mc.player, hand);
                  }
               } else {
                  if (mc.player.isUsingItem() && mc.player.getItemUseTime() >= 1) {
                     mc.player.stopUsingItem();
                  }
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.enable.getValue() ? "ON" : "OFF";
   }
}
