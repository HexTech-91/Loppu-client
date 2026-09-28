package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.SendMessageEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatAppend extends Module {
    public static ChatAppend INSTANCE;

    // ========== 新增：与 Punctuation 联动的标志 ==========
    public static boolean ignoreNextMessage = false;
    // ====================================================

    // 模式枚举
    public enum Mode {
        Custom,
        Bot,
        Meteor
    }

    // 绕过开关枚举（模拟布尔值）
    public enum BypassToggle {
        Disable,
        Enable
    }

    // 模式选择
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Custom));

    // ----- Custom 模式设置（固定后缀）-----
    private final StringSetting customSuffix = this.add(new StringSetting("Text", "我已被吓哭o(╥﹏╥)o",
            () -> mode.getValue() == Mode.Custom));

    // ----- Bot 模式设置（随机字母数字后缀 + 绕过）-----
    // 随机字符长度（滑动条，整数），仅 Bot 模式可见
    private final SliderSetting randomLength = this.add(new SliderSetting("Length", 3.0, 1.0, 16.0, 1.0,
            () -> mode.getValue() == Mode.Bot));

    // 绕过开关，Bot 和 Meteor 模式均可见
    private final EnumSetting<BypassToggle> bypassEnabled = this.add(new EnumSetting<>("Bypass-Enabled", BypassToggle.Disable,
            () -> mode.getValue() == Mode.Bot || mode.getValue() == Mode.Meteor));

    // 绕过前缀列表（多个前缀用逗号分隔），Bot 和 Meteor 模式且开启绕过时可见
    private final StringSetting bypassPrefixes = this.add(new StringSetting("Bypass-Prefixes", ";",
            () -> (mode.getValue() == Mode.Bot || mode.getValue() == Mode.Meteor) && bypassEnabled.getValue() == BypassToggle.Enable));

    // 随机字符生成器
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public ChatAppend() {
        super("ChatAppend", Category.Misc);
        this.setChinese("消息后缀");
        INSTANCE = this;
    }

    @EventListener
    public void onSendMessage(SendMessageEvent event) {
        // ========== 新增：如果 Punctuation 标记了忽略，则跳过本次修改并清除标志 ==========
        if (ignoreNextMessage) {
            ignoreNextMessage = false;
            return;
        }
        // =============================================================================

        // 通用检查
        if (!nullCheck() || event.isCancelled() || AutoReconnect.inQueueServer) {
            return;
        }

        String originalMessage = event.message;

        switch (mode.getValue()) {
            case Custom:
                handleCustomMode(originalMessage, event);
                break;
            case Bot:
                handleBotMode(originalMessage, event);
                break;
            case Meteor:
                handleMeteorMode(originalMessage, event);
                break;
        }
    }

    // Custom 模式：固定后缀，跳过命令
    private void handleCustomMode(String message, SendMessageEvent event) {
        if (message.startsWith("/") || message.startsWith("!") ||
                message.startsWith("$") || message.startsWith("#")) {
            return;
        }
        String suffix = customSuffix.getValue();
        if (!message.endsWith(suffix)) {
            event.message = message + " " + suffix;
        }
    }

    // Bot 模式：随机字母数字后缀，长度可调
    private void handleBotMode(String message, SendMessageEvent event) {
        // 绕过检查
        if (bypassEnabled.getValue() == BypassToggle.Enable) {
            String prefixesStr = bypassPrefixes.getValue();
            List<String> prefixes = Arrays.stream(prefixesStr.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());
            for (String prefix : prefixes) {
                if (message.startsWith(prefix)) {
                    return; // 不修改消息
                }
            }
        }

        int len = (int) randomLength.getValue();
        if (len > 0) {
            StringBuilder randomSuffix = new StringBuilder(" <");
            for (int i = 0; i < len; i++) {
                randomSuffix.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
            }
            randomSuffix.append(">");
            event.message = message + randomSuffix.toString();
        }
    }

    // Meteor 模式：固定三位随机数字后缀（格式：" (123)"）
    private void handleMeteorMode(String message, SendMessageEvent event) {
        // 绕过检查（与 Bot 模式共用 bypassEnabled 和 bypassPrefixes）
        if (bypassEnabled.getValue() == BypassToggle.Enable) {
            String prefixesStr = bypassPrefixes.getValue();
            List<String> prefixes = Arrays.stream(prefixesStr.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());
            for (String prefix : prefixes) {
                if (message.startsWith(prefix)) {
                    return; // 不修改消息
                }
            }
        }

        // 生成三位随机数字
        int num = RANDOM.nextInt(1000); // 0-999
        String formattedNum = String.format("%03d", num); // 保证三位，不足补零
        event.message = message + " (" + formattedNum + ")";
    }
}