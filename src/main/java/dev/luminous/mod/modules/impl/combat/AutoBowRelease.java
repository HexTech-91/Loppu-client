package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoBowRelease extends Module {
    public static AutoBowRelease INSTANCE;

    private final SliderSetting ticks = add(new SliderSetting("Ticks", 5, 0, 20, 1)
            .setSuffix("ticks"));

    public AutoBowRelease() {
        super("AutoBowRelease", Category.Combat);
        setChinese("自动拉弓");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        // 检查主手或副手是否为弓，且正在使用物品（拉弓）
        if ((mc.player.getMainHandStack().getItem() == Items.BOW
                || mc.player.getOffHandStack().getItem() == Items.BOW)
                && mc.player.isUsingItem()) {
            // 拉弓时间达到设定 ticks 时自动释放
            if (mc.player.getItemUseTime() >= ticks.getValueInt()) {
                // 发送释放使用物品的包
                mc.getNetworkHandler().sendPacket(
                        new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                                BlockPos.ORIGIN,
                                Direction.DOWN
                        )
                );
                // 立即停止客户端物品使用状态
                mc.player.stopUsingItem();
            }
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(ticks.getValueInt());
    }
}