package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TickMovementEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.asm.accessors.IEntityVelocityUpdateS2CPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.impl.combat.Surround;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.player.NoFall;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class GroundMace extends Module {
    public static GroundMace INSTANCE;

    private final BooleanSetting antiNoFall = this.add(new BooleanSetting("AntiNoFall", true));
    private final BooleanSetting onlySwitchMace = this.add(new BooleanSetting("OnlySwitchMace", false, () -> antiNoFall.getValue()));
    public final BooleanSetting swap = this.add(new BooleanSetting("Swap", false));
    private final BooleanSetting noCrystal = this.add(new BooleanSetting("NoCrystal", true));
    private final BooleanSetting inventorySwap = this.add(new BooleanSetting("InventorySwap", false, this.swap::isOpen));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true));
    private final SliderSetting base = this.add(new SliderSetting("Base", 2.4, 1.1, 10.0, 0.01));
    private final SliderSetting steps = this.add(new SliderSetting("Steps", 0.5, 0.1, 5.0, 0.01));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", false));
    private final BooleanSetting headLimit = this.add(new BooleanSetting("HeadLimit", true));
    private final BooleanSetting moveSync = this.add(new BooleanSetting("MoveSync", false));
    private final EnumSetting<Timing> timing = this.add(new EnumSetting<>("Timing", Timing.Pre));
    private final SliderSetting height = this.add(new SliderSetting("Height", 25.0, 0.0, 2000.0, 0.1));
    public final SliderSetting auraDelay = this.add(new SliderSetting("AuraDelay", 0.0, 0.0, 2.0, 0.01));
    private final BooleanSetting noVelocityUpdate = this.add(new BooleanSetting("NoVelocityUpdate", true));
    private final BooleanSetting strictVelocity = this.add(new BooleanSetting("Strict", false, () -> this.noVelocityUpdate.getValue()));
    private final BooleanSetting pauseElytraFly = this.add(new BooleanSetting("PauseElytraFly", true));
    private final BooleanSetting pauseInBlock = this.add(new BooleanSetting("PauseInBlock", true));
    private final BooleanSetting ignoreWeb = this.add(new BooleanSetting("IgnoreWeb", false, () -> pauseInBlock.getValue()));
    private final BooleanSetting playerOnly = this.add(new BooleanSetting("PlayerOnly", true));
    private final BooleanSetting autoMatch = this.add(new BooleanSetting("AutoMatch", true));
    public final BooleanSetting surround = this.add(new BooleanSetting("Surround", false));
    public final BooleanSetting fix = this.add(new BooleanSetting("Fix", false));

    boolean ignore = false;
    private PlayerInteractEntityC2SPacket lastPacket = null;
    private boolean resending = false;

    private final Timer auraTimer = new Timer();
    private boolean noFallWasOn = false;
    private boolean criticalsWasOn = false;
    private boolean surroundEnabledByUs = false;

    public GroundMace() {
        super("GroundMace", Category.Mace);

        this.setChinese("平地锤");

        INSTANCE = this;
    }

    public static boolean shouldDisableNoFall() {
        if (INSTANCE == null || !INSTANCE.isOn() || !INSTANCE.antiNoFall.getValue()) {
            return false;
        }
        if (INSTANCE.onlySwitchMace.getValue()) {
            return mc.player != null && mc.player.getMainHandStack().getItem() == Items.MACE;
        }
        return true;
    }

    public static boolean shouldDisableCriticals() {
        return INSTANCE != null
                && INSTANCE.isOn()
                && (INSTANCE.resending || INSTANCE.lastPacket != null);
    }

    public static boolean isNoCrystalActive() {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        if (!INSTANCE.noCrystal.getValue()) return false;
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.MACE;
    }

    @Override
    public String getInfo() {
        if (!fix.getValue()) {
            if (onlyGround.getValue() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) {
                return "WaitGround";
            }
            if (headLimit.getValue() && isHeadBlocked()) {
                return "HeadLimited";
            }
        }
        return null;
    }

    @Override
    public void onEnable() {
        if (autoMatch.getValue()) {
            NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
            noFallWasOn = noFall != null && noFall.isOn();
            criticalsWasOn = Criticals.INSTANCE != null && Criticals.INSTANCE.isOn();
            if (noFallWasOn && noFall != null) {
                noFall.disable();
            }
            if (criticalsWasOn && Criticals.INSTANCE != null) {
                Criticals.INSTANCE.disable();
            }
        }
        surroundEnabledByUs = false;
        if (surround.getValue()) {
            Surround surroundModule = (Surround) Alien.MODULE.getModuleByName("Surround");
            if (surroundModule != null && !surroundModule.isOn()) {
                surroundModule.enable();
                surroundEnabledByUs = true;
            }
        }
    }

    @Override
    public void onDisable() {
        if (autoMatch.getValue()) {
            if (noFallWasOn) {
                NoFall noFall = (NoFall) Alien.MODULE.getModuleByName("NoFall");
                if (noFall != null && !noFall.isOn()) {
                    noFall.enable();
                }
            }
            if (criticalsWasOn && Criticals.INSTANCE != null && !Criticals.INSTANCE.isOn()) {
                Criticals.INSTANCE.enable();
            }
            noFallWasOn = false;
            criticalsWasOn = false;
        }
        if (surroundEnabledByUs) {
            Surround surroundModule = (Surround) Alien.MODULE.getModuleByName("Surround");
            if (surroundModule != null && surroundModule.isOn()) {
                surroundModule.disable();
            }
            surroundEnabledByUs = false;
        }
    }

    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        return mc.world == null ? null : mc.world.getEntityById(packetBuf.readVarInt());
    }

    public static InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        packetBuf.readVarInt();
        return packetBuf.readEnumConstant(InteractType.class);
    }

    private boolean isHeadBlocked() {
        BlockPos playerPos = mc.player.getBlockPos();
        return !mc.world.getBlockState(playerPos.up(1)).isAir()
                || !mc.world.getBlockState(playerPos.up(2)).isAir();
    }

    @EventListener(priority = -100)
    private void onMovementPacket(TickMovementEvent event) {
        if (!fix.getValue() && onlyGround.getValue() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) {
            this.lastPacket = null;
            return;
        }
        if (this.lastPacket != null && this.moveSync.getValue()) {
            // 目标已不在世界（死亡/消失）时丢弃缓存攻击包，避免无效伪造位移与空包重放
            if (getEntity(this.lastPacket) == null) {
                this.lastPacket = null;
                return;
            }
            switch (this.timing.getValue()) {
                case Pre -> {
                    this.doJumpSpoof();
                    if (fix.getValue()) {
                        sendFixPosition(getEntity(lastPacket));
                    }
                    sendCachedPacket();
                    if (fix.getValue()) {
                        sendBurrowPosition();
                    }
                    onAttackComplete();
                }
                case Post -> {
                    this.doJumpSpoof();
                    if (fix.getValue()) {
                        sendFixPosition(getEntity(lastPacket));
                    }
                    sendCachedPacket();
                    if (fix.getValue()) {
                        sendBurrowPosition();
                    }
                    onAttackComplete();
                }
                case All -> {
                    this.doJumpSpoof();
                    if (fix.getValue()) {
                        sendFixPosition(getEntity(lastPacket));
                    }
                    sendCachedPacket();
                    this.doJumpSpoof();
                    if (fix.getValue()) {
                        sendBurrowPosition();
                    }
                    onAttackComplete();
                }
            }
            this.lastPacket = null;
        }
    }

    private void sendCachedPacket() {
        if (!auraTimer.passedMs((long) (auraDelay.getValue() * 1000))) {
            return;
        }
        // 防御：缓存包不存在或目标已消失（死亡/离开视野）时不重放，避免向无效实体发包
        if (this.lastPacket == null || getEntity(this.lastPacket) == null) {
            this.lastPacket = null;
            return;
        }
        resending = true;
        mc.getNetworkHandler().sendPacket(this.lastPacket);
        resending = false;
        auraTimer.reset();
    }

    @EventListener(priority = 200)
    public void onPacketSend(PacketEvent.Send event) {
        if (resending) return;
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = event.getPacket();

        // 全局攻击冷却拦截：仅当可能发动重锤攻击时才检查
        if (packet instanceof PlayerInteractEntityC2SPacket attackPacket
                && getInteractType(attackPacket) == InteractType.ATTACK) {
            if ((mc.player.getMainHandStack().getItem() == Items.MACE || this.swap.getValue())
                    && !auraTimer.passedMs((long) (auraDelay.getValue() * 1000))) {
                event.cancel();
                return;
            }
        }

        if (!fix.getValue() && onlyGround.getValue() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) {
            return;
        }

        if ((this.timing.is(Timing.Pre) || this.timing.is(Timing.All))
                && this.checkMace(event)
                && (packet = event.getPacket()) instanceof PlayerInteractEntityC2SPacket attackPacket) {
            Entity target = getEntity(attackPacket);
            if (!this.moveSync.getValue()) {
                this.doJumpSpoof(target);
                if (fix.getValue()) {
                    // 非moveSync + Fix模式：取消原攻击包，通过sendCachedPacket重新发送，确保攻击后复位到burrow
                    event.cancel();
                    this.lastPacket = attackPacket;
                    auraTimer.reset();
                    sendCachedPacket();
                    sendBurrowPosition();
                }
                auraTimer.reset();
                onAttackComplete();
            } else if (attackPacket != this.lastPacket) {
                this.lastPacket = attackPacket;
                event.cancel();
            }
        }

        if (this.swap.getValue()) {
            this.doSwapAttack(event);
        }
    }

    @EventListener(priority = 200)
    public void onPacketSent(PacketEvent.Sent event) {
        if (resending) return;
        if (mc.player == null || mc.world == null) return;

        Packet<?> packet = event.getPacket();

        // 全局攻击冷却拦截：仅当可能发动重锤攻击时才检查
        if (packet instanceof PlayerInteractEntityC2SPacket attackPacket
                && getInteractType(attackPacket) == InteractType.ATTACK) {
            if ((mc.player.getMainHandStack().getItem() == Items.MACE || this.swap.getValue())
                    && !auraTimer.passedMs((long) (auraDelay.getValue() * 1000))) {
                event.cancel();
                return;
            }
        }

        if (!fix.getValue() && onlyGround.getValue() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) {
            return;
        }

        if ((this.timing.is(Timing.Post) || this.timing.is(Timing.All))
                && this.checkMace(event)
                && (packet = event.getPacket()) instanceof PlayerInteractEntityC2SPacket attackPacket) {
            Entity target = getEntity(attackPacket);
            if (!this.moveSync.getValue()) {
                this.doJumpSpoof(target);
                if (fix.getValue()) {
                    // Post timing：攻击包已发送，直接复位到burrow
                    sendBurrowPosition();
                }
                auraTimer.reset();
                onAttackComplete();
            } else if (attackPacket != this.lastPacket) {
                this.lastPacket = attackPacket;
                event.cancel();
            }
        }
    }

    @EventListener
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (this.noVelocityUpdate.getValue()
                && !nullCheck()
                && packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()) {
            try {
                if (this.strictVelocity.getValue()) {
                    int velocityY = MathHelper.clamp(
                            (int) (mc.player.getVelocity().y * 8000.0),
                            -31200,
                            31200
                    );
                    ((IEntityVelocityUpdateS2CPacket) velocityPacket).setY(velocityY);
                } else {
                    ((IEntityVelocityUpdateS2CPacket) velocityPacket).setY(0);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void doSwapAttack(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        int slot = this.getMaceSlot();
        if (slot == -1 || this.ignore) return;

        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)
                || getInteractType(packet) != InteractType.ATTACK) {
            return;
        }

        if (this.noCrystal.getValue() && getEntity(packet) instanceof EndCrystalEntity) {
            return;
        }

        if (this.playerOnly.getValue() && !(getEntity(packet) instanceof PlayerEntity)) {
            return;
        }

        this.lastPacket = packet;
        this.ignore = true;
        this.doSwapSpoof();
        this.ignore = false;
        event.cancel();
    }

    private boolean checkMace(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (!fix.getValue() && this.headLimit.getValue() && isHeadBlocked()) {
            return false;
        }

        if (this.pauseElytraFly.getValue()
                && ElytraFly.INSTANCE != null
                && ElytraFly.INSTANCE.isOn()) {
            return false;
        }

        if (!fix.getValue() && this.pauseInBlock.getValue() && EntityUtil.isInsideBlock()) {
            if (!(this.ignoreWeb.getValue() && isOnlyInWeb())) {
                return false;
            }
        }

        if (!(mc.player.getMainHandStack().getItem() == Items.MACE || this.swap.getValue())
                || !(packet instanceof PlayerInteractEntityC2SPacket attackPacket)
                || getInteractType(attackPacket) != InteractType.ATTACK) {
            return false;
        }

        Entity target = getEntity(attackPacket);

        if (target instanceof EndCrystalEntity) {
            return false;
        }

        if (this.playerOnly.getValue() && !(target instanceof PlayerEntity)) {
            return false;
        }

        return !mc.player.isInLava() && !mc.player.isTouchingWater();
    }

    private boolean isOnlyInWeb() {
        Box box = mc.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() != Blocks.COBWEB
                            && !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void doJumpSpoof() {
        doJumpSpoof(null);
    }

    private void doJumpSpoof(Entity target) {
        double targetHeight = this.height.getValue();

        if (this.smart.getValue()) {
            targetHeight = smartHeightCorrection(targetHeight);
        }

        if (targetHeight < 1.5) {
            return;
        }

        double base = MathHelper.clamp(this.base.getValue(), 1.1, 1000.0);
        double steps = MathHelper.clamp(this.steps.getValue(), 0.1, 5.0);

        /*
         * Fix 模式核心逻辑：
         *
         * 问题：自己在 burrow 中时，mc.player.getY() 在方块内部。
         * sendFakeY(0.0) 会将位置复位到方块内，服务端可能：
         *   1. 拒绝该位置包（无效位置）
         *   2. 认为玩家处于"地面"状态，fallDistance 归零
         * 导致重锤坠落伤害加成不生效。
         *
         * 解决方案：计算一个"有效基准 Y"（方块上方最近的可站立位置），
         * 所有位置包使用该基准 Y 发送，使服务端正确接收位置变化并计算坠落距离。
         */
        double fixBaseY = mc.player.getY();
        if (fix.getValue()) {
            fixBaseY = getBurrowClearanceY();
        }

        for (double i = 0.0; Math.pow(base, i) < targetHeight; i += steps) {
            this.sendFakeYAt(fixBaseY, Math.min(targetHeight, Math.pow(base, i)));
        }

        if (fix.getValue() && target != null) {
            sendFixPositionAt(fixBaseY, target);
        } else {
            this.sendFakeYAt(fixBaseY, 0.0);
        }
    }

    /**
     * Fix 模式：计算 burrow 中有效的基准 Y 位置。
     * 从玩家位置向上搜索，找到第一个有足够空气空间的位置。
     * 确保玩家位置不在方块内部，使服务端能正确接受位置包并计算坠落距离。
     */
    private double getBurrowClearanceY() {
        BlockPos pos = mc.player.getBlockPos();
        // 从玩家当前位置向上搜索，找 3 格空气空间（玩家身高 1.8 格）
        for (int y = 0; y <= 5; y++) {
            BlockPos checkPos = pos.up(y);
            if (mc.world.getBlockState(checkPos).isAir()
                    && mc.world.getBlockState(checkPos.up(1)).isAir()
                    && mc.world.getBlockState(checkPos.up(2)).isAir()) {
                return checkPos.getY();
            }
        }
        // 找不到足够空间，抬高 2 格作为保底
        return mc.player.getY() + 2.0;
    }

    /**
     * Fix 模式：发送位置包到目标头顶上方，使攻击能命中 burrow 中的目标。
     */
    private void sendFixPositionAt(double baseY, Entity target) {
        if (target == null || mc.player == null) {
            this.sendFakeYAt(baseY, 0.0);
            return;
        }
        double targetHeadY = target.getY() + target.getHeight();
        double fixOffset = targetHeadY - baseY + 0.5;
        double maxHeight = this.height.getValue();
        if (fixOffset > 0 && fixOffset < maxHeight) {
            this.sendFakeYAt(baseY, fixOffset);
        } else {
            this.sendFakeYAt(baseY, 0.0);
        }
    }

    /**
     * Fix 模式备用：发送位置包到目标头顶上方（使用玩家实际 Y 为基准）。
     * 用于 MoveSync 模式中无法获取 fixBaseY 的情况。
     */
    private void sendFixPosition(Entity target) {
        sendFixPositionAt(mc.player.getY(), target);
    }

    /**
     * 攻击完成后关闭由本模块开启的 Surround
     */
    private void onAttackComplete() {
        if (surroundEnabledByUs) {
            Surround surroundModule = (Surround) Alien.MODULE.getModuleByName("Surround");
            if (surroundModule != null && surroundModule.isOn()) {
                surroundModule.disable();
            }
            surroundEnabledByUs = false;
        }
    }

    /**
     * 使用指定基准 Y 发送位置包
     */
    private void sendFakeYAt(double baseY, double offset) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                baseY + offset,
                mc.player.getZ(),
                false
        ));
    }

    private void sendFakeY(double offset) {
        sendFakeYAt(mc.player.getY(), offset);
    }

    /**
     * Fix 模式：攻击完成后，将玩家位置复位到实际的 burrow 位置（mc.player.getY()），
     * 确保玩家全程在 bur 里，服务器端不会停留在 fixBaseY 上方。
     * 此方法必须在攻击包发送后调用。
     */
    private void sendBurrowPosition() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                false
        ));
    }

    private double smartHeightCorrection(double targetHeight) {
        if (mc.player == null || mc.world == null) {
            return targetHeight;
        }

        Box box = mc.player.getBoundingBox();
        double maximumHeight = targetHeight;
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);
        int minY = MathHelper.floor(box.maxY);
        int maxY = MathHelper.floor(box.maxY + targetHeight + 1.0);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                        continue;
                    }
                    double allowed = y - box.maxY - 0.01;
                    if (allowed >= 0.0) {
                        maximumHeight = Math.min(maximumHeight, allowed);
                    } else {
                        maximumHeight = Math.min(maximumHeight, 0.0);
                    }
                    break;
                }
            }
        }

        return maximumHeight;
    }

    private void doSwapSpoof() {
        if (this.lastPacket == null || getEntity(this.lastPacket) == null) {
            this.lastPacket = null;
            return;
        }

        int slot = this.getMaceSlot();
        if (slot == -1) {
            return;
        }

        int old = mc.player.getInventory().selectedSlot;
        this.doSwap(slot);
        mc.getNetworkHandler().sendPacket(this.lastPacket);

        if (this.inventorySwap.getValue()) {
            this.doSwap(slot);
            EntityUtil.syncInventory();
        } else {
            this.doSwap(old);
        }

        auraTimer.reset();
    }

    private void doSwap(int slot) {
        if (this.inventorySwap.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int getMaceSlot() {
        if (this.inventorySwap.getValue()) {
            return InventoryUtil.findItemInventorySlot(Items.MACE);
        }
        return InventoryUtil.findItem(Items.MACE);
    }

    public enum InteractType {
        INTERACT,
        ATTACK,
        INTERACT_AT
    }
}