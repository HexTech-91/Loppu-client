package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* loaded from: AlienV4-Fix-crk.jar:dev/luminous/mod/modules/impl/misc/PopEz.class */
public class PopEz extends Module {
    public static PopEz INSTANCE;

    public enum Mode {
        Default,
        Custom
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Default));

    // 自定义模式下的消息模板，仅当模式为Custom时显示
    private final StringSetting customMessage = add(new StringSetting("CustomMessage", "",
            () -> mode.getValue() == Mode.Custom));

    private final SliderSetting randomChars = add(new SliderSetting("RandomChars", 3.0d, 0.0d, 20.0d, 1.0d));
    public final BooleanSetting slowSend = add(new BooleanSetting("SlowSend", false));

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private final Random random = new Random();
    private final Timer timer = new Timer();

    // 存储慢发送队列条目：玩家实体ID -> (爆图腾次数, 是否为自定义模式)
    private final Map<Integer, QueueEntry> popQueue = new ConcurrentHashMap<>();

    private static class QueueEntry {
        final int popCount;
        final boolean custom;

        QueueEntry(int popCount, boolean custom) {
            this.popCount = popCount;
            this.custom = custom;
        }
    }

    public PopEz() {
        super("PopEz", Module.Category.Misc);
        setChinese("POP嘲讽");
        INSTANCE = this;
    }

    @EventListener
    public void onTotem(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == null || player.equals(mc.player) || Alien.FRIEND.isFriend(player)) {
            return;
        }

        int popCount = 1;
        if (Alien.POP.popContainer.containsKey(player.getName().getString())) {
            popCount = Alien.POP.popContainer.get(player.getName().getString()).intValue();
        }

        if (slowSend.getValue()) {
            // 慢发送模式：先放入队列，不立刻发送
            boolean isCustom = mode.getValue() == Mode.Custom;
            popQueue.put(player.getId(), new QueueEntry(popCount, isCustom));
            return;
        }

        // 即时发送
        sendPopMessage(player, popCount);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!slowSend.getValue() || popQueue.isEmpty() || !timer.passedS(3.2d)) {
            return;
        }

        timer.reset();
        Iterator<Map.Entry<Integer, QueueEntry>> it = popQueue.entrySet().iterator();
        if (!it.hasNext()) {
            return;
        }

        Map.Entry<Integer, QueueEntry> entry = it.next();
        int playerId = entry.getKey();
        QueueEntry queueEntry = entry.getValue();

        PlayerEntity player = null;
        if (mc.world != null) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p.getId() == playerId) {
                    player = p;
                    break;
                }
            }
        }

        if (player != null) {
            sendPopMessage(player, queueEntry.popCount, queueEntry.custom);
        }
        popQueue.remove(playerId);
    }

    // 根据当前设置发送消息 (默认使用 mode 决定是否自定义)
    private void sendPopMessage(PlayerEntity player, int popCount) {
        sendPopMessage(player, popCount, mode.getValue() == Mode.Custom);
    }

    // 明确指定是否自定义模式的消息发送方法
    private void sendPopMessage(PlayerEntity player, int popCount, boolean useCustom) {
        if (nullCheck() || mc.player == null || mc.player.networkHandler == null) {
            return;
        }

        String message;
        if (useCustom) {
            String template = customMessage.getValue();
            if (template == null || template.trim().isEmpty()) {
                return; // 自定义消息为空时不发送
            }
            message = template
                    .replace("%player%", player.getName().getString())
                    .replace("%pop%", String.valueOf(popCount));
        } else {
            // 默认模式：标准嘲讽格式
            String name = player.getName().getString();
            if (popCount == 1) {
                message = name + " has popped " + popCount + " totem.";
            } else {
                message = name + " has popped " + popCount + " totems.";
            }
        }

        // 追加随机字符
        String randomString = generateRandomString(randomChars.getValueInt());
        if (!randomString.isEmpty()) {
            message = message + " " + randomString;
        }

        mc.player.networkHandler.sendChatMessage(message);
    }

    private String generateRandomString(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}