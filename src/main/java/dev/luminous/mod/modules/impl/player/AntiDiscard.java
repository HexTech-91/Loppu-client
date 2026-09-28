package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

public class AntiDiscard extends Module {
    private final SliderSetting slot1;
    private final SliderSetting slot2;
    private final SliderSetting slot3;

    public AntiDiscard() {
        super("AntiDiscard", Module.Category.Player);
        this.slot1 = add(new SliderSetting("Slot1", 1, 1, 9, 1));
        this.slot2 = add(new SliderSetting("Slot2", 1, 1, 9, 1));
        this.slot3 = add(new SliderSetting("Slot3", 1, 1, 9, 1));
        setChinese("防止丢弃");
    }

    /** 检查当前选中的快捷栏槽位是否被保护 */
    private boolean isCurrentSlotProtected() {
        int current = mc.player.getInventory().selectedSlot + 1; // 转换为 1-9
        return current == slot1.getValueInt()
                || current == slot2.getValueInt()
                || current == slot3.getValueInt();
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (isCurrentSlotProtected()) {
            // 清空丢弃键的按下状态，避免客户端本地丢弃
            while (mc.options.dropKey.wasPressed()) {
                // 持续消费事件，直到全部清除
            }
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM
                    || packet.getAction() == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
                if (isCurrentSlotProtected()) {
                    event.cancel();                         // 阻止服务端收到丢弃包
                    mc.player.getInventory().updateItems(); // 同步客户端物品栏，避免显示错误
                }
            }
        }
    }
}