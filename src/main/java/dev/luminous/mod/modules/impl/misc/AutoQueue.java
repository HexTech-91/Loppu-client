package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.HashMap;

public class AutoQueue extends Module {
    public static HashMap<String, String> asks = new HashMap<>(){
        {
            put("红石火把", "15");
            put("猪被闪电", "僵尸猪人");
            put("小箱子能", "27");
            put("开服年份", "2020");
            put("定位末地遗迹", "0");
            put("爬行者被闪电", "高压爬行者");
            put("大箱子能", "54");
            put("羊驼会主动", "不会");
            put("无限水", "3");
            put("挖掘速度最快", "金镐");
            put("凋灵死后", "下界之星");
            put("苦力怕的官方", "爬行者");
            put("南瓜的生长", "不需要");
            put("定位末地", "0");
        }
    };

    public AutoQueue() {
        super("AutoQueue", Category.Misc);
        setChinese("自动答题");
    }
    private final BooleanSetting queueCheck = add(new BooleanSetting("QueueCheck", true));

    public static boolean inQueue = false;

    // 使用事件监听器代替 onUpdate 方法
    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) {
            inQueue = false;
            return;
        }
        inQueue = InventoryUtil.findItem(Items.COMPASS) != -1;
    }

    @Override
    public void onDisable() {
        inQueue = false;
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive e) {
        if (!inQueue && queueCheck.getValue()) return;

        if (e.getPacket() instanceof GameMessageS2CPacket packet) {
            String message = "";

            // 1.21.1 中 GameMessageS2CPacket 的 content 字段类型变化
            if (packet.content() != null) {
                // 使用新的 API 获取文本内容
                message = packet.content().getString();
            }

            for (String key : asks.keySet()) {
                if (message.contains(key)) {
                    String[] abc = new String[]{"A", "B", "C"};
                    for (String s : abc) {
                        if (message.contains(s + "." + asks.get(key))) {
                            if (mc.getNetworkHandler() != null) {
                                mc.getNetworkHandler().sendChatMessage(s.toLowerCase());
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}