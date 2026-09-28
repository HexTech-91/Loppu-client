package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IPlayerMoveC2SPacket;
import dev.luminous.asm.accessors.IPlayerInteractEntityC2SPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.exploit.BowBomb;
import dev.luminous.mod.modules.impl.exploit.Phase;
import dev.luminous.mod.modules.impl.nagisa.GroundMace;
import dev.luminous.mod.modules.impl.player.AutoPearl;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;

public class Criticals extends Module {
    public static Criticals INSTANCE;
    public final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.OldNCP));
    public final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true, () -> !this.mode.is(Mode.Ground)));
    private final BooleanSetting setOnGround = this.add(new BooleanSetting("SetNoGround", false, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting blockCheck = this.add(new BooleanSetting("BlockCheck", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting autoJump = this.add(new BooleanSetting("AutoJump", true, () -> this.mode.is(Mode.Ground)).setParent());
    private final BooleanSetting mini = this.add(new BooleanSetting("Mini", true, () -> this.mode.is(Mode.Ground) && this.autoJump.isOpen()));
    private final SliderSetting y = this.add(new SliderSetting("MotionY", 0.05, 0.0, 1.0, 0.001, () -> this.mode.is(Mode.Ground) && this.autoJump.isOpen()));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting crawlingDisable = this.add(new BooleanSetting("CrawlingDisable", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting flight = this.add(new BooleanSetting("Flight", false, () -> this.mode.is(Mode.Ground)));

    // 来自第二段的优化：可配置延迟 + Grim 版本选择
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 250, 0, 1000, 10, () -> this.mode.is(Mode.Grim)));
    public final EnumSetting<GrimVersion> grimVersion = this.add(new EnumSetting<>("GrimVersion", GrimVersion.GrimFull, () -> this.mode.is(Mode.Grim)));

    private final BooleanSetting phaseOnly = this.add(new BooleanSetting("PhaseOnly", false, () -> this.mode.is(Mode.Grim)));
    private final BooleanSetting wallsOnly = this.add(new BooleanSetting("WallsOnly", false, () -> this.mode.is(Mode.Grim) && this.phaseOnly.isOpen()));
    private final BooleanSetting moveFix = this.add(new BooleanSetting("MoveFix", false, () -> this.mode.is(Mode.Grim)));

    boolean requireJump = false;
    private long lastGrimCritTime = 0L; // 替换原来的 attackTimer

    public enum GrimVersion {
        GrimFull,   // 原第一段 Full 包序列（含视角）
        GrimV2,     // 第二段 V2（PositionAndOnGround）
        GrimV3      // 第二段 V3（首个包 onGround=true）
    }

    public Criticals() {
        super("Criticals", Module.Category.Combat);
        this.setChinese("刀刀暴击");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (this.mode.is(Mode.Grim)) {
            return this.mode.getValue().name() + "/" + this.grimVersion.getValue().name();
        }
        return this.mode.getValue().name();
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (GroundMace.shouldDisableCriticals()) return;

        if (this.mode.is(Mode.Ground)) {
            if (!BowBomb.send && !AutoPearl.throwing && !Phase.INSTANCE.isOn()
                    && this.setOnGround.getValue() && event.getPacket() instanceof PlayerMoveC2SPacket) {
                ((IPlayerMoveC2SPacket) event.getPacket()).setOnGround(false);
            }
            return;
        }

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            if (getInteractType(packet) != PlayerInteractEntityC2SPacket.InteractType.ATTACK) return;
            Entity entity = getEntity(packet);
            if (entity == null || entity instanceof EndCrystalEntity) return;
            if (this.onlyGround.getValue() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) return;
            if (mc.player.isInLava() || mc.player.isTouchingWater()) return;
            doCrit(entity);
        }
    }

    @Override
    public void onLogout() {
        if (this.mode.is(Mode.Ground) && this.autoDisable.getValue()) {
            this.disable();
        }
    }

    @Override
    public void onEnable() {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        this.requireJump = true;
        if (this.mode.is(Mode.Ground)) {
            if (nullCheck()) {
                if (this.autoDisable.getValue()) this.disable();
                return;
            }
            if (MovementUtil.isMoving() && this.autoDisable.getValue()) {
                this.disable();
                return;
            }
            if (this.crawlingDisable.getValue() && mc.player.isCrawling()) {
                this.disable();
                return;
            }
            if (mc.player.isOnGround() && this.autoJump.getValue()
                    && (!this.blockCheck.getValue() || BlockUtil.canCollide(mc.player, new Box(EntityUtil.getPlayerPos(true).up(2))))) {
                jump();
            }
        }
    }

    @Override
    public void onDisable() {
        // 重置状态（第二段优点）
        this.lastGrimCritTime = 0L;
        this.requireJump = false;
    }

    public void jump() {
        if (this.mini.getValue()) {
            MovementUtil.setMotionY(this.y.getValue());
        } else {
            mc.player.jump();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (!this.mode.is(Mode.Ground)) return;

        if (this.crawlingDisable.getValue() && mc.player.isCrawling()) {
            this.disable();
            return;
        }
        if (MovementUtil.isMoving() && this.autoDisable.getValue()) {
            this.disable();
            return;
        }
        if (this.flight.getValue() && mc.player.fallDistance > 0.0f) {
            MovementUtil.setMotionY(0.0);
            MovementUtil.setMotionX(0.0);
            MovementUtil.setMotionZ(0.0);
            this.requireJump = false;
            return;
        }
        if (this.blockCheck.getValue() && !BlockUtil.canCollide(mc.player, new Box(EntityUtil.getPlayerPos(true).up(2)))) {
            this.requireJump = true;
            return;
        }
        if (mc.player.isOnGround() && this.autoJump.getValue() && (this.flight.getValue() || this.requireJump)) {
            jump();
            this.requireJump = false;
        }
    }

    public void doCrit(Entity entity) {
        switch (this.mode.getValue().ordinal()) {
            case 7: // BBTT
                if (MovementUtil.isMoving() || !MovementUtil.isStatic()) return;
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.045, mc.player.getZ(), false));
                break;
            case 1: // Strict
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.062600301692775, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.07260029960661, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                break;
            case 2: // NCP
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                break;
            case 3: // OldNCP
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.058293536E-5, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 9.16580235E-6, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0371854E-7, mc.player.getZ(), false));
                break;
            case 0: // UpdatedNCP
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 2.71875E-7, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
                break;
            case 4: // Hypixel2K22
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0045, mc.player.getZ(), true));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.52121E-4, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.3, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.025, mc.player.getZ(), false));
                break;
            case 5: // Packet
                mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 5.0E-4, mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.0E-4, mc.player.getZ(), false));
                break;
            case 8: // Grim
                handleGrimCrit(entity);
                break;
        }
    }

    private void handleGrimCrit(Entity entity) {
        if (this.phaseOnly.getValue()) {
            if (this.wallsOnly.getValue()) {
                if (!isDoublePhased()) return;
            } else if (!isPhased()) {
                return;
            }
        }
        if (this.moveFix.getValue() && MovementUtil.isMoving()) return;

        // 使用可配置延迟（第二段优点）
        if (System.currentTimeMillis() - this.lastGrimCritTime < this.delay.getValue()) return;
        if (!mc.player.isOnGround() || mc.player.isCrawling()) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        // 根据 GrimVersion 选择发送序列（整合第二段优点，保留第一段 Full 包为 GrimFull）
        switch (this.grimVersion.getValue()) {
            case GrimFull:
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(x, y + 0.0625, z, yaw, pitch, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(x, y + 1.0E-7, z, yaw, pitch, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false));
                break;
            case GrimV2:
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 1.0E-7, z, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
                break;
            case GrimV3:
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.04535, z, false));
                break;
        }

        mc.player.addCritParticles(entity);
        this.lastGrimCritTime = System.currentTimeMillis();
    }

    // 保留第一段正确的相位检测（无无限循环）
    private boolean isPhased() {
        Box box = mc.player.getBoundingBox();
        BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isSolid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isDoublePhased() {
        Box box = mc.player.getBoundingBox();
        BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(pos);
            BlockState stateUp = mc.world.getBlockState(pos.up());
            if (state.isSolid() && stateUp.isSolid()) {
                return true;
            }
        }
        return false;
    }

    // 工具方法保持不变
    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        return mc.world == null ? null : mc.world.getEntityById(((IPlayerInteractEntityC2SPacket) packet).getEntityId());
    }

    public static PlayerInteractEntityC2SPacket.InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        final PlayerInteractEntityC2SPacket.InteractType[] result = new PlayerInteractEntityC2SPacket.InteractType[1];
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(net.minecraft.util.Hand hand) {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.INTERACT;
            }

            @Override
            public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.INTERACT_AT;
            }

            @Override
            public void attack() {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.ATTACK;
            }
        });
        return result[0];
    }

    public enum Mode {
        UpdatedNCP,
        Strict,
        NCP,
        OldNCP,
        Hypixel2K22,
        Packet,
        Ground,
        BBTT,
        Grim
    }
}