package dev.luminous.mod.commands.impl;

import dev.luminous.mod.commands.Command;
import java.util.List;
import net.minecraft.text.Text;

public class EsuCommand extends Command {
    public EsuCommand() {
        super("esu", "此命令因安全考虑已被禁用");
    }

    @Override
    public void runCommand(String[] parameters) {
        // 完全禁用，只显示安全警告
        mc.player.sendMessage(Text.of("§c========================================="));
        mc.player.sendMessage(Text.of("§6⚠ 安全警告 ⚠"));
        mc.player.sendMessage(Text.of("§f此命令已被安全模块禁用"));
        mc.player.sendMessage(Text.of("§7原因: 存在潜在的数据隐私风险"));
        mc.player.sendMessage(Text.of("§7如需使用相关功能，请稍后..."));
        mc.player.sendMessage(Text.of("§c========================================="));

        // 可选：在客户端日志中记录
        System.out.println("[安全拦截] 玩家尝试使用已禁用的esu命令");
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        // 返回空数组，禁用自动补全
        return new String[0];
    }
}