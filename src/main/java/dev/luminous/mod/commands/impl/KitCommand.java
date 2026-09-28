package dev.luminous.mod.commands.impl;

import dev.luminous.Alien;
import dev.luminous.core.Manager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.modules.impl.misc.ChestStealer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


public class KitCommand extends Command {
   public KitCommand() {
      super("kit", "[save/load/delete/list] [name]");
   }

   @Override
   public void runCommand(String[] parameters) {
      if (parameters.length == 0) {
         this.sendUsage();
         return;
      }

      String subCommand = parameters[0].toLowerCase();

      switch (subCommand) {
         case "save":
            handleSave(parameters);
            break;
         case "load":
            handleLoad(parameters);
            break;
         case "delete":
            handleDelete(parameters);
            break;
         case "list":
            handleList();
            break;
         default:
            this.sendUsage();
      }
   }

   private void handleSave(String[] parameters) {
      if (parameters.length != 2) {
         this.sendChatMessage("§fUsage: kit save <name>");
         return;
      }

      if (mc.player == null) {
         return;
      }

      try {
         File folder = Manager.getFile("kits");
         if (!folder.exists()) {
            folder.mkdirs();
         }

         File file = new File(folder, parameters[1] + ".kit");
         try (PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < 36; i++) {
               printwriter.println(i + ":" + mc.player.getInventory().getStack(i).getItem().getTranslationKey());
            }
         }
         this.sendChatMessage("§fKit [" + parameters[1] + "] saved");
      } catch (Exception e) {
         this.sendChatMessage("§fKit [" + parameters[1] + "] save failed");
         e.printStackTrace();
      }
   }

   private void handleLoad(String[] parameters) {
      if (parameters.length != 2) {
         this.sendChatMessage("§fUsage: kit load <name>");
         return;
      }

      try {
         File folder = Manager.getFile("kits");
         File file = new File(folder, parameters[1] + ".kit");

         if (file.exists()) {
            if (Alien.KIT_CONFIG != null) {
               Alien.KIT_CONFIG.setCurrentKit(parameters[1]);
               this.sendChatMessage("§fKit [" + parameters[1] + "] loaded");
               
               ChestStealer chestStealer = (ChestStealer) Alien.MODULE.getModuleByName("ChestStealer");
               if (chestStealer != null && chestStealer.isOn()) {
                  chestStealer.reloadKit();
               }
            } else {
               this.sendChatMessage("§fKitConfigManager not initialized");
            }
         } else {
            this.sendChatMessage("§fKit [" + parameters[1] + "] not found");
         }
      } catch (Exception e) {
         this.sendChatMessage("§fFailed to load kit");
         e.printStackTrace();
      }
   }

   private void handleDelete(String[] parameters) {
      if (parameters.length != 2) {
         this.sendChatMessage("§fUsage: kit delete <name>");
         return;
      }

      try {
         File file = Manager.getFile("kits" + File.separator + parameters[1] + ".kit");
         if (file.exists()) {
            file.delete();
            this.sendChatMessage("§fKit [" + parameters[1] + "] deleted");
         } else {
            this.sendChatMessage("§fKit [" + parameters[1] + "] not found");
         }
      } catch (Exception e) {
         this.sendChatMessage("§fFailed to delete kit");
         e.printStackTrace();
      }
   }

   private void handleList() {
      try {
         File folder = Manager.getFile("kits");
         if (!folder.exists() || !folder.isDirectory()) {
            this.sendChatMessage("§fNo kits found");
            return;
         }

         boolean found = false;
         for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".kit")) {
               String name = file.getName().substring(0, file.getName().length() - 4);
               this.sendChatMessage("§fKit: [" + name + "]");
               found = true;
            }
         }

         if (!found) {
            this.sendChatMessage("§fNo kits found");
         }
      } catch (Exception e) {
         this.sendChatMessage("§fFailed to list kits");
         e.printStackTrace();
      }
   }

   @Override
   public String[] getAutocorrect(int count, List<String> seperated) {
      if (count != 1) {
         return null;
      }

      String input = ((String) seperated.getLast()).toLowerCase();
      List<String> correct = new ArrayList<>();

      for (String x : List.of("save", "load", "delete", "list")) {
         if (x.toLowerCase().startsWith(input)) {
            correct.add(x);
         }
      }

      return correct.toArray(new String[0]);
   }
}
