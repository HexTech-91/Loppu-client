package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.Module.Category;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;

public class BlockStrafe extends Module {
    public static BlockStrafe INSTANCE;

    private final SliderSetting speed = add(new SliderSetting("Speed", 10.0, 0.0, 20.0, 1.0).setSuffix("%"));

    private final SliderSetting anchorSpeed = add(new SliderSetting("AnchorSpeed", 3.0, 0.0, 20.0, 1.0).setSuffix("%"));

    // 总开关：是否启用特殊情景的独立速度（蛛网、药水效果）
    private final BooleanSetting extraSpeed = add(new BooleanSetting("ExtraSpeed", false));

    // 子设置：仅当 extraSpeed 开启时可见、生效
    private final SliderSetting webSpeed = add(new SliderSetting("WebSpeed", 10.0, 0.0, 20.0, 1.0,
            () -> extraSpeed.getValue()).setSuffix("%"));
    private final SliderSetting effectSpeed = add(new SliderSetting("EffectSpeed", 10.0, 0.0, 20.0, 1.0,
            () -> extraSpeed.getValue()).setSuffix("%"));

    private static final double SIN_45 = 0.7853981633974483;
    private static final double COS_45 = 0.7853981633974483; // sin(45°) == cos(45°)

    public BlockStrafe() {
        super("BlockStrafe", Module.Category.Movement);
        setChinese("方块灵活移动");
        INSTANCE = this;
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (!EntityUtil.isInsideBlock()) {
            return;
        }

        // 速度优先级：ExtraSpeed 开启时才有 蛛网/药水 独立速度，否则统一使用默认 Speed
        double chosenSpeed;
        if (extraSpeed.getValue() && isInWeb()) {
            chosenSpeed = webSpeed.getValue();
        } else if (extraSpeed.getValue() && mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            chosenSpeed = effectSpeed.getValue();
        } else if (AutoAnchor.INSTANCE.currentPos != null) {
            chosenSpeed = anchorSpeed.getValue();
        } else {
            chosenSpeed = speed.getValue();
        }

        double moveSpeed = 0.002873 * chosenSpeed;
        double forward = mc.player.input.movementForward;
        double sideways = mc.player.input.movementSideways;
        double yaw = mc.player.getYaw();

        if (forward == 0.0 && sideways == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        if (forward != 0.0 && sideways != 0.0) {
            forward *= Math.sin(SIN_45);
            sideways *= Math.cos(COS_45);
        }

        double sinYaw = -Math.sin(Math.toRadians(yaw));
        double cosYaw = Math.cos(Math.toRadians(yaw));

        event.setX(forward * moveSpeed * sinYaw + sideways * moveSpeed * cosYaw);
        event.setZ(forward * moveSpeed * cosYaw - sideways * moveSpeed * sinYaw);
    }

    // 原版蜘蛛网检测（替换掉不存在的 isInWeb 方法）
    private boolean isInWeb() {
        return mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.COBWEB
                || mc.world.getBlockState(mc.player.getBlockPos().up()).getBlock() == Blocks.COBWEB;
    }
}