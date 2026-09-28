package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.player.NoFall;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class LagMace extends Module {

    public static LagMace INSTANCE;

    // ========== 设置项 ==========
    private final SliderSetting minDistance = add(new SliderSetting("MinDistance", 3.0, 0.0, 20.0, 0.1));

    private final BooleanSetting pauseElytraFly = add(new BooleanSetting("PauseElytraFly", false));
    private final BooleanSetting pauseInBlock = add(new BooleanSetting("PauseInBlock", false));
    private final BooleanSetting antiNoFall = add(new BooleanSetting("AntiNoFall", false));
    private final BooleanSetting onlySwitchMace = add(
            new BooleanSetting("OnlySwitchMace", false, () -> antiNoFall.getValue())
    );
    private final BooleanSetting requireMace = add(new BooleanSetting("RequireMace", false));

    // ========== 状态变量 ==========
    private Vec3d lagBackPos = Vec3d.ZERO;
    private double fallDistance = 0.0;
    private PlayerEntity lastAttack = null;
    private boolean noFallWasOn = false;

    public LagMace() {
        super("LagMace", Category.Mace);
        setChinese("自动重锤");
        INSTANCE = this;
    }

    // ========== 辅助解析（兼容所有版本） ==========
    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        int entityId = buf.readVarInt();
        return mc.world == null ? null : mc.world.getEntityById(entityId);
    }

    public static InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        buf.readVarInt(); // entityId
        return buf.readEnumConstant(InteractType.class);
    }

    public enum InteractType {
        INTERACT, ATTACK, INTERACT_AT
    }

    // ========== 供 NoFall 调用 ==========
    public static boolean shouldDisableNoFall() {
        if (INSTANCE == null || !INSTANCE.isOn() || !INSTANCE.antiNoFall.getValue()) return false;
        if (INSTANCE.onlySwitchMace.getValue()) {
            return mc.player != null && mc.player.getMainHandStack().getItem() == net.minecraft.item.Items.MACE;
        }
        return true;
    }

    // ========== 生命周期 ==========
    @Override
    public void onEnable() {
        if (nullCheck()) return;
        doLagBack();
        this.lagBackPos = mc.player.getPos();
        this.fallDistance = 0.0;

        if (antiNoFall.getValue()) {
            NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
            if (noFall != null && noFall.isOn()) {
                noFallWasOn = true;
                noFall.disable();
            }
        }
    }

    @Override
    public void onDisable() {
        if (antiNoFall.getValue() && noFallWasOn) {
            NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
            if (noFall != null && !noFall.isOn()) {
                noFall.enable();
            }
            noFallWasOn = false;
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf((int) this.fallDistance);
    }

    // ========== 核心 Tick 逻辑 ==========
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || !event.isPre()) return;

        // ----- 暂停条件检查 -----
        if (requireMace.getValue() && mc.player.getMainHandStack().getItem() != net.minecraft.item.Items.MACE) return;
        if (pauseElytraFly.getValue() && ElytraFly.INSTANCE != null && ElytraFly.INSTANCE.isOn()) return;
        if (pauseInBlock.getValue() && EntityUtil.isInsideBlock()) return;

        // ----- 执行 LagBack 检测 -----
        doLagBackMode();
    }

    private void doLagBackMode() {
        // 1. 向下找到地面（第一个非空气方块）
        BlockPos bottom = mc.player.getBlockPos();
        while (mc.world.isAir(bottom) && bottom.getY() > mc.world.getBottomY()) {
            bottom = bottom.down();
        }
        double groundY = bottom.getY() + 1.0;

        // 2. 计算从记录点到地面的高度差
        double currentFall = this.lagBackPos.getY() - groundY;
        this.fallDistance = currentFall;

        // 3. 若低于阈值，更新记录点
        if (currentFall < this.minDistance.getValue()) {
            this.lagBackPos = mc.player.getPos();
            return;
        }

        // 4. 触发回弹
        doLagBack();
    }

    private void doLagBack() {
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(
                        mc.player.getX(), 10000.0, mc.player.getZ(), false
                )
        );
    }

    // ========== 事件监听 ==========
    @EventListener
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()) {
            double vy = velocityPacket.getVelocityY() / 8000.0;
            if (vy > 0.0) doLagBack();
        }
    }

    @EventListener
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet
                && getInteractType(packet) == InteractType.ATTACK) {
            Entity target = getEntity(packet);
            if (target instanceof PlayerEntity) {
                this.lastAttack = (PlayerEntity) target;
            }
        }
    }

    @EventListener
    private void onPacketReceiveHurt(PacketEvent.Receive event) {
        if (nullCheck() || this.lastAttack == null) return;
        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket
                && statusPacket.getStatus() == 4) {
            Entity entity = statusPacket.getEntity(mc.world);
            if (entity == this.lastAttack) {
                this.lastAttack = null;
                doLagBack();
            }
        }
    }
}