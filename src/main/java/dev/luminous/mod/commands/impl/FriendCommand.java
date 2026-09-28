package dev.luminous.mod.commands.impl;

import dev.luminous.Alien;
import dev.luminous.mod.commands.Command;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class FriendCommand extends Command {

    public FriendCommand() {
        super("friend", "[name/reset/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            this.sendUsage();
            return;
        }

        String firstArg = parameters[0].toLowerCase();
        switch (firstArg) {
            case "reset":
                Alien.FRIEND.friendList.clear();
                this.sendChatMessage("§fFriends list got reset");
                break;
            case "list":
                if (Alien.FRIEND.friendList.isEmpty()) {
                    this.sendChatMessage("§fFriends list is empty");
                    return;
                }
                StringBuilder friends = new StringBuilder();
                int countInLine = 0;
                boolean firstLine = true;
                for (String name : Alien.FRIEND.friendList) {
                    if (!firstLine || countInLine > 0) {
                        friends.append(", ");
                    }
                    friends.append(name);
                    countInLine++;
                    if (countInLine == 4) {
                        this.sendChatMessage((firstLine ? "§eFriends §a" : "§a") + friends);
                        friends = new StringBuilder();
                        firstLine = false;
                        countInLine = 0;
                    }
                }
                if (countInLine > 0) {
                    this.sendChatMessage((firstLine ? "§eFriends §a" : "§a") + friends);
                }
                break;
            case "add":
                if (parameters.length == 2) {
                    String name = parameters[1];
                    Alien.FRIEND.add(name);
                    this.sendChatMessage("§f" + name + " §ahas been friended");
                } else {
                    this.sendUsage();
                }
                break;
            case "remove":
            case "del":
                if (parameters.length == 2) {
                    String name = parameters[1];
                    if (Alien.FRIEND.friendList.contains(name)) {
                        Alien.FRIEND.remove(name);
                        this.sendChatMessage("§f" + name + " §chas been unfriended");
                    } else {
                        this.sendChatMessage("§f" + name + " §cis not in your friend list");
                    }
                } else {
                    this.sendUsage();
                }
                break;
            default:
                if (parameters.length == 1) {
                    String name = parameters[0];
                    boolean isFriend = Alien.FRIEND.isFriend(name);
                    this.sendChatMessage("§f" + name + (isFriend ? " §ais friended" : " §cisn't friended"));
                } else {
                    this.sendUsage();
                }
        }
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        // 正在输入第一个参数 -> 补全子命令（按下 Tab 时自动列出所有匹配项）
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            List<String> suggestions = new ArrayList<>();
            for (String cmd : List.of("add", "remove", "list", "reset")) {
                if (cmd.startsWith(input)) {
                    suggestions.add(cmd);
                }
            }
            return suggestions.toArray(new String[0]);
        }
        // 正在输入第二个参数 -> 补全玩家名（仅当子命令为 add 或 remove 时）
        else if (count == 2) {
            String subCmd = seperated.get(0).toLowerCase();
            if (subCmd.equals("add") || subCmd.equals("remove")) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    List<String> playerNames = new ArrayList<>();
                    for (PlayerEntity player : client.world.getPlayers()) {
                        playerNames.add(player.getGameProfile().getName());
                    }
                    return playerNames.toArray(new String[0]);
                }
            }
        }
        return null;
    }
}