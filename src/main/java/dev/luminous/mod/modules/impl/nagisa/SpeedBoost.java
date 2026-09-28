package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;

/**
 * 速度提升模块 - 类似 LongJump 的 Strafe 模式
 * 启动后 10 tick 平滑到 400 km/h，然后惯性平滑衰减到 20 km/h
 * 最后限制滑行长度，到头的话水平速度锁0
 */
public class SpeedBoost extends Module {
    public static SpeedBoost INSTANCE;

    // 速度目标 (km/h -> 游戏速度转换)
    // 400 km/h ≈ 1.55 游戏速度 (1 km/h ≈ 0.00387 游戏速度)
    // 20 km/h ≈ 0.078 游戏速度
    private static final double MAX_SPEED_KMH = 400.0;
    private static final double MIN_SPEED_KMH = 20.0;
    private static final double KMH_TO_GAME = 0.00387;

    // 设置
    private final SliderSetting maxSpeed = this.add(
            new SliderSetting("MaxSpeed", 400.0, 100.0, 500.0, 10.0).setSuffix("km/h")
    );
    private final SliderSetting minSpeed = this.add(
            new SliderSetting("MinSpeed", 20.0, 5.0, 50.0, 1.0).setSuffix("km/h")
    );
    private final SliderSetting accelerateTicks = this.add(
            new SliderSetting("AccelerateTicks", 10.0, 5.0, 20.0, 1.0).setSuffix("t")
    );
    private final SliderSetting length = this.add(
            new SliderSetting("Length", 50.0, 10.0, 200.0, 1.0).setSuffix("m")
    );
    private final BooleanSetting autoDisable = this.add(
            new BooleanSetting("AutoDisable", true)
    );

    // 状态
    private final Timer timer = new Timer();
    private final Timer lagTimer = new Timer();
    private int ticks;
    private double currentSpeed;
    private double startX, startZ;
    private double traveledDistance;
    private Phase phase;
    private boolean active;

    private enum Phase {
        IDLE,
        ACCELERATING,
        DECAYING,
        DONE
    }

    public SpeedBoost() {
        super("SpeedBoost", Module.Category.Movement);
        this.setChinese("速度提升");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return String.format("%.0f km/h", currentSpeed / KMH_TO_GAME);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        resetState();
        phase = Phase.IDLE;
        active = false;
        ticks = 0;
        currentSpeed = MovementUtil.getSpeed(false);
        startX = mc.player.getX();
        startZ = mc.player.getZ();
        traveledDistance = 0.0;
        timer.reset();
        lagTimer.reset();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        phase = Phase.IDLE;
        active = false;
        ticks = 0;
        currentSpeed = 0.0;
        traveledDistance = 0.0;
    }

    @EventListener(priority = 100)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lagTimer.reset();
            resetState();
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || event.isPost()) return;

        // 计算已滑行距离
        if (active && phase != Phase.DONE) {
            double dx = mc.player.getX() - startX;
            double dz = mc.player.getZ() - startZ;
            traveledDistance = Math.sqrt(dx * dx + dz * dz);
        }

        // 检查滑行长度限制
        double maxLength = length.getValue();
        if (active && traveledDistance >= maxLength) {
            phase = Phase.DONE;
            currentSpeed = 0.0;
            MovementUtil.setMotionX(0.0);
            MovementUtil.setMotionZ(0.0);
            if (autoDisable.getValue()) {
                disable();
            }
            return;
        }

        // 状态机
        switch (phase) {
            case IDLE -> {
                if (MovementUtil.isMoving()) {
                    phase = Phase.ACCELERATING;
                    active = true;
                    ticks = 0;
                    timer.reset();
                }
            }
            case ACCELERATING -> {
                ticks++;
                int targetTicks = accelerateTicks.getValueInt();
                double maxSpeedValue = maxSpeed.getValue() * KMH_TO_GAME;
                double minSpeedValue = minSpeed.getValue() * KMH_TO_GAME;

                if (ticks <= targetTicks) {
                    // 平滑加速
                    double progress = (double) ticks / targetTicks;
                    // 使用 ease-out 缓动函数实现平滑
                    progress = 1 - Math.pow(1 - progress, 3);
                    currentSpeed = minSpeedValue + (maxSpeedValue - minSpeedValue) * progress;
                } else {
                    // 加速完成，进入衰减阶段
                    currentSpeed = maxSpeedValue;
                    phase = Phase.DECAYING;
                    ticks = 0;
                    timer.reset();
                }
            }
            case DECAYING -> {
                ticks++;
                double maxSpeedValue = maxSpeed.getValue() * KMH_TO_GAME;
                double minSpeedValue = minSpeed.getValue() * KMH_TO_GAME;

                // 平滑衰减 (使用惯性衰减)
                double decayProgress = (double) ticks / 100.0; // 100 tick 衰减周期
                decayProgress = MathHelper.clamp(decayProgress, 0.0, 1.0);
                // 使用 ease-in 缓动函数
                double easeIn = decayProgress * decayProgress * decayProgress;
                currentSpeed = maxSpeedValue - (maxSpeedValue - minSpeedValue) * easeIn;

                if (currentSpeed <= minSpeedValue) {
                    currentSpeed = minSpeedValue;
                    // 可以选择继续滑行或停止
                }
            }
            case DONE -> {
                // 速度已锁定为0
            }
        }
    }

    @EventListener(priority = -50)
    public void onMove(MoveEvent event) {
        if (nullCheck() || !active) return;
        if (mc.player.isRiding() || mc.player.isHoldingOntoLadder()
                || mc.player.getAbilities().flying || mc.player.isFallFlying()) {
            return;
        }

        if (phase == Phase.DONE) {
            // 滑行长度到头，锁0
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        if (!MovementUtil.isMoving()) {
            currentSpeed = 0.0;
            return;
        }

        // 检查 lag
        if (!lagTimer.passed(500)) {
            return;
        }

        // 应用速度 - 类似 LongJump 的 Strafe 逻辑
        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        double yaw = mc.player.getYaw();

        if (forward == 0.0 && strafe == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
        } else {
            if (forward != 0.0 && strafe != 0.0) {
                forward *= Math.sin(Math.PI / 4);
                strafe *= Math.cos(Math.PI / 4);
            }
            double speed = currentSpeed;
            event.setX((forward * speed * -Math.sin(Math.toRadians(yaw)) + strafe * speed * Math.cos(Math.toRadians(yaw))) * 0.99);
            event.setZ((forward * speed * Math.cos(Math.toRadians(yaw)) - strafe * speed * -Math.sin(Math.toRadians(yaw))) * 0.99);
        }
    }
}