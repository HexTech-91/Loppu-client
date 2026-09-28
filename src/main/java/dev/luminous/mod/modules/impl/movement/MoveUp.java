package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class MoveUp extends Module {
    public static MoveUp INSTANCE;
    public final SliderSetting setPosition;
    private final BooleanSetting toggle;

    public MoveUp() {
        super("MoveUp", Category.Movement);
        setChinese("向上移动");
        this.setPosition = add(new SliderSetting("SetPosition", 1.0, -5.0, 5.0, 0.1));
        this.toggle = add(new BooleanSetting("Toggle", false));
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {          // 注意：这是事件方法，不是 @Override
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player != null) {
            // 发送虚假的移动数据包，模拟跳跃过程
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            client.player.getX(),
                            client.player.getY() + 0.4199999868869781,
                            client.player.getZ(),
                            false
                    )
            );
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            client.player.getX(),
                            client.player.getY() + 0.7531999805212017,
                            client.player.getZ(),
                            false
                    )
            );

            // 实际设置玩家位置
            client.player.setPosition(
                    client.player.getX(),
                    client.player.getY() + this.setPosition.getValueFloat(),
                    client.player.getZ()
            );

            // 发送落地数据包
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            client.player.getX(),
                            client.player.getY(),
                            client.player.getZ(),
                            true
                    )
            );
        }

        // 如果启用了自动关闭，则关闭模块
        if (this.toggle.getValue()) {
            toggle();
        }
    }
}