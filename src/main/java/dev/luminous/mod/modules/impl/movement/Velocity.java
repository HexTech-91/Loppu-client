/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.block.Blocks
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.projectile.FishingBobberEntity
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
 *  net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket$Action
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$Full
 *  net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket
 *  net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Box
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.math.Direction$AxisDirection
 *  net.minecraft.world.World
 */
package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.EntityVelocityUpdateEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TickEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.asm.accessors.IEntityVelocityUpdateS2CPacket;
import dev.luminous.asm.accessors.IExplosionS2CPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class Velocity extends Module {
    public static Velocity INSTANCE;
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Plain));
    private final SliderSetting lagPause = add(new SliderSetting("LagPause", 50, 0, 500, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    public final BooleanSetting ignorePearlLag = add(new BooleanSetting("IgnorePearlLag", true, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)).setParent());
    private final SliderSetting phaseTime = add(new SliderSetting("PhaseTime", 250, 0, 1000, () -> (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && ignorePearlLag.isOpen()));
    public final BooleanSetting noRotation = add(new BooleanSetting("NoRotation", false, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    public final BooleanSetting flagInWall = add(new BooleanSetting("FlagInWall", false, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)).setParent());
    public final BooleanSetting whenPushOutOfBlocks = add(new BooleanSetting("WhilePushOut", false, () -> (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && flagInWall.isOpen()));
    public final BooleanSetting staticSetting = add(new BooleanSetting("Static", false, () -> mode.is(Mode.Grim)));
    public final BooleanSetting cancelAll = add(new BooleanSetting("CancelAll", false, () -> !mode.is(Mode.None)));
    private final SliderSetting horizontal = add(new SliderSetting("Horizontal", 0f, 0f, 100f, 1f, () -> !mode.is(Mode.None) && !cancelAll.getValue()));
    private final SliderSetting vertical = add(new SliderSetting("Vertical", 0f, 0f, 100f, 1f, () -> !mode.is(Mode.None) && !cancelAll.getValue()));
    public final BooleanSetting whileLiquid = add(new BooleanSetting("WhileLiquid", false));
    public final BooleanSetting whileElytra = add(new BooleanSetting("FallFlying", false));
    public final BooleanSetting noClimb = add(new BooleanSetting("NoClimb", false));
    public final BooleanSetting noExplosions = add(new BooleanSetting("NoExplosions", false));
    public final BooleanSetting waterPush = add(new BooleanSetting("NoWaterPush", false));
    public final BooleanSetting entityPush = add(new BooleanSetting("NoEntityPush", true));
    public final BooleanSetting blockPush = add(new BooleanSetting("NoBlockPush", true));
    public final BooleanSetting fishBob = add(new BooleanSetting("NoFishBob", true));
    public final Timer pearlTimer = new Timer();
    private final Timer lagBackTimer = new Timer();
    private boolean flag;
    private static boolean pushOutOfBlocks = false;

    public Velocity() {
        super("Velocity", Category.Movement);
        setChinese("反击退");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (mode.is(Mode.None)) {
            return null;
        }
        return mode.getValue().name() + ", " + (cancelAll.getValue() ? "Cancel" : horizontal.getValueInt() + "%, " + vertical.getValueInt() + "%");
    }

    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (noRotation()) {
            event.setRotation(Alien.ROTATION.rotationYaw, 89.0f);
        }
    }

    public boolean noRotation() {
        return isOn() && (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && EntityUtil.isInsideBlock() && noRotation.getValue();
    }

    // 液体检查辅助方法 - 修复水中反击退失效问题
    private boolean isInLiquid() {
        return mc.player.isTouchingWater() || mc.player.isSubmergedInWater() || mc.player.isInLava();
    }

    @EventListener
    public void onPacketEvent(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (!ignorePearlLag.getValue() || pearlTimer.passed(phaseTime.getValueInt())) {
                lagBackTimer.reset();
            }
        }
    }

    @EventListener
    public void onVelocity(EntityVelocityUpdateEvent event) {
        if (nullCheck()) return;
        if (event.getEntity() != mc.player) return;
        if (mode.is(Mode.None)) return;

        // 液体检查 - 修复水中反击退失效
        if (isInLiquid() && !whileLiquid.getValue()) return;

        // 鞘翅检查
        if (mc.player.isFallFlying() && !whileElytra.getValue()) return;

        // 攀爬检查
        if (noClimb.getValue() && mc.player.isClimbing()) return;

        switch (mode.getValue().ordinal()) {
            case 1: { // Grim
                if (!lagBackTimer.passedMs(lagPause.getValue())) return;

                boolean insideBlock = EntityUtil.isInsideBlock();
                if (insideBlock || getPos() != null || (staticSetting.getValue() && MovementUtil.isStatic())) {
                    if (event.getX() == 0.0 && event.getZ() == 0.0) break;
                    flag = true;
                    break;
                }
                return;
            }
            case 2: { // Wall
                if (!lagBackTimer.passedMs(lagPause.getValue())) return;

                if (EntityUtil.isInsideBlock()) {
                    if (event.getX() == 0.0 && event.getZ() == 0.0) break;
                    flag = true;
                    break;
                }
                return;
            }
        }

        if (cancelAll.getValue()) {
            event.cancel();
        } else {
            double h = horizontal.getValueInt() / 100.0;
            double v = vertical.getValueInt() / 100.0;
            event.setX(event.getX() * h);
            event.setZ(event.getZ() * h);
            event.setY(event.getY() * v);
        }
    }

    @EventListener
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (!ignorePearlLag.getValue() || pearlTimer.passed(phaseTime.getValueInt())) {
                lagBackTimer.reset();
            }
        }

        if (nullCheck()) return;

        // 液体检查 - 修复水中反击退失效
        if (isInLiquid() && !whileLiquid.getValue()) return;

        // 钓鱼竿检查
        if (fishBob.getValue() && event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 31 && packet.getEntity((World) mc.world) instanceof FishingBobberEntity fishHook) {
                if (fishHook.getHookedEntity() == mc.player) {
                    event.setCancelled(true);
                }
            }
        }

        if (mode.is(Mode.Grim) || mode.is(Mode.Wall)) {
            if (!lagBackTimer.passedMs(lagPause.getValue())) return;

            boolean insideBlock = EntityUtil.isInsideBlock();
            if (mode.is(Mode.Wall) && !insideBlock) return;

            // 处理爆炸包
            if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
                ((IExplosionS2CPacket) explosion).setVelocityX(0);
                ((IExplosionS2CPacket) explosion).setVelocityY(0);
                ((IExplosionS2CPacket) explosion).setVelocityZ(0);
                flag = true;
            }
        } else if (mode.is(Mode.Plain)) {
            if (cancelAll.getValue()) {
                if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
                    ((IExplosionS2CPacket) explosion).setVelocityX(0);
                    ((IExplosionS2CPacket) explosion).setVelocityY(0);
                    ((IExplosionS2CPacket) explosion).setVelocityZ(0);
                    flag = true;
                } else if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet && packet.getEntityId() == mc.player.getId()) {
                    event.cancel();
                }
            } else {
                float h = horizontal.getValueFloat() / 100;
                float v = vertical.getValueFloat() / 100;

                if (event.getPacket() instanceof ExplosionS2CPacket) {
                    IExplosionS2CPacket packet = (IExplosionS2CPacket) event.getPacket();
                    packet.setVelocityX(packet.getX() * h);
                    packet.setVelocityY(packet.getY() * v);
                    packet.setVelocityZ(packet.getZ() * h);

                    if (noExplosions.getValue()) event.cancel();
                } else if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                    if (packet.getEntityId() == mc.player.getId()) {
                        ((IEntityVelocityUpdateS2CPacket) packet).setX((int) (packet.getVelocityX() * h));
                        ((IEntityVelocityUpdateS2CPacket) packet).setY((int) (packet.getVelocityY() * v));
                        ((IEntityVelocityUpdateS2CPacket) packet).setZ((int) (packet.getVelocityZ() * h));
                    }
                }
            }
        }
    }

    @EventListener
    public void onUpdate(TickEvent event) {
        if (event.isPost()) return;
        if (nullCheck()) return;

        // 液体检查 - 修复水中反击退失效
        if (isInLiquid() && !whileLiquid.getValue()) return;

        // Blink模块兼容性
        if (Blink.INSTANCE != null && Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }

        // 推动出方块检测
        if (flagInWall.getValue()) {
            pushOutOfBlocks = false;
            pushOutOfBlocks(mc.player.getX() - mc.player.getWidth() * 0.35, mc.player.getZ() + mc.player.getWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() - mc.player.getWidth() * 0.35, mc.player.getZ() - mc.player.getWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() + mc.player.getWidth() * 0.35, mc.player.getZ() - mc.player.getWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() + mc.player.getWidth() * 0.35, mc.player.getZ() + mc.player.getWidth() * 0.35);
        }

        if (flag) {
            if (lagBackTimer.passedMs(lagPause.getValue()) && (flagInWall.getValue() && (!pushOutOfBlocks || whenPushOutOfBlocks.getValue()) || !EntityUtil.isInsideBlock())) {
                // 发送位置更新包
                mc.getNetworkHandler().sendPacket((Packet) new PlayerMoveC2SPacket.Full(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        Alien.ROTATION.rotationYaw, Alien.ROTATION.rotationPitch,
                        mc.player.isOnGround()
                ));

                // 发送停止破坏方块包
                BlockPos pos = getPos();
                if (pos != null) {
                    mc.getNetworkHandler().sendPacket((Packet) new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                            pos,
                            mc.player.getHorizontalFacing().getOpposite()
                    ));
                }
            }
            flag = false;
        }
    }

    public BlockPos getPos() {
        if (mc.world.getBlockState(mc.player.getBlockPos().down()).getBlock() == Blocks.OBSIDIAN) {
            return mc.player.getBlockPos().down();
        }
        return null;
    }

    private void pushOutOfBlocks(double x, double z) {
        BlockPos blockPos = BlockPos.ofFloored(x, mc.player.getY(), z);
        if (wouldCollideAt(blockPos)) {
            Direction direction = null;
            double closestDistance = Double.MAX_VALUE;

            double dx = x - blockPos.getX();
            double dz = z - blockPos.getZ();

            for (Direction dir : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
                double distance = dir.getAxis() == Direction.Axis.X ?
                        (dir.getDirection() == Direction.AxisDirection.POSITIVE ? 1 - dx : dx) :
                        (dir.getDirection() == Direction.AxisDirection.POSITIVE ? 1 - dz : dz);

                if (distance < closestDistance && !wouldCollideAt(blockPos.offset(dir))) {
                    closestDistance = distance;
                    direction = dir;
                }
            }

            if (direction != null) {
                pushOutOfBlocks = true;
            }
        }
    }

    private boolean wouldCollideAt(BlockPos pos) {
        Box playerBox = mc.player.getBoundingBox();
        Box blockBox = new Box(
                pos.getX(), playerBox.minY, pos.getZ(),
                pos.getX() + 1, playerBox.maxY, pos.getZ() + 1
        ).contract(1.0E-7);

        return !mc.world.isSpaceEmpty(mc.player, blockBox);
    }

    public enum Mode {
        Plain,
        Grim,
        Wall,
        None
    }
}

