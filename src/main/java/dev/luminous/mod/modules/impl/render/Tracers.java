package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

public class Tracers
        extends Module {
    public enum Mode {
        Default,
        Combat
    }

    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Default));

    // Combat 模式专用设置（均带有布尔开关，默认启用）
    private final ColorSetting rivalPlayer = this.add(new ColorSetting("RivalPlayer", new Color(255, 0, 0, 100), () -> mode.getValue() == Mode.Combat).injectBoolean(true));
    private final ColorSetting friendPlayer = this.add(new ColorSetting("FriendPlayer", new Color(0, 255, 0, 100), () -> mode.getValue() == Mode.Combat).injectBoolean(true));
    private final BooleanSetting ignoreArmorless = this.add(new BooleanSetting("IgnoreArmorless", false, () -> mode.getValue() == Mode.Combat));

    // Default 模式下的设置（仅在 Default 模式可见）
    private final ColorSetting item = this.add(new ColorSetting("Item", new Color(255, 255, 255, 100), () -> mode.getValue() == Mode.Default).injectBoolean(true));
    private final ColorSetting player = this.add(new ColorSetting("Player", new Color(255, 255, 255, 100), () -> mode.getValue() == Mode.Default).injectBoolean(true));
    private final ColorSetting chest = this.add(new ColorSetting("Chest", new Color(255, 255, 255, 100), () -> mode.getValue() == Mode.Default).injectBoolean(false));
    private final ColorSetting enderChest = this.add(new ColorSetting("EnderChest", new Color(255, 100, 255, 100), () -> mode.getValue() == Mode.Default).injectBoolean(false));
    private final ColorSetting shulkerBox = this.add(new ColorSetting("ShulkerBox", new Color(15, 255, 255, 100), () -> mode.getValue() == Mode.Default).injectBoolean(false));

    public Tracers() {
        super("Tracers", Module.Category.Render);
        this.setChinese("追踪者");
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        Tracers.mc.options.getBobView().setValue(false);

        if (mode.getValue() == Mode.Combat) {
            // Combat 模式：根据开关分别绘制敌方/友方玩家追踪线
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof PlayerEntity && entity != Tracers.mc.player) {
                    PlayerEntity playerEntity = (PlayerEntity) entity;
                    boolean isFriend = Alien.FRIEND.isFriend(playerEntity);

                    // 根据好友状态选择对应的设置项，并检查其布尔开关是否开启
                    ColorSetting activeSetting = isFriend ? friendPlayer : rivalPlayer;
                    if (!activeSetting.booleanValue) continue; // 开关关闭则跳过

                    // 忽略无护甲玩家检查
                    if (ignoreArmorless.getValue()) {
                        boolean hasArmor = false;
                        for (ItemStack armorStack : playerEntity.getInventory().armor) {
                            if (!armorStack.isEmpty()) {
                                hasArmor = true;
                                break;
                            }
                        }
                        if (!hasArmor) continue;
                    }

                    this.drawLine(entity.getPos(), activeSetting.getValue());
                }
            }
            return;
        }

        // Default 模式：原有逻辑
        if (this.item.booleanValue || this.player.booleanValue) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ItemEntity && this.item.booleanValue) {
                    this.drawLine(entity.getPos(), this.item.getValue());
                    continue;
                }
                if (!(entity instanceof PlayerEntity) || !this.player.booleanValue || entity == Tracers.mc.player) continue;
                this.drawLine(entity.getPos(), this.player.getValue());
            }
        }
        ArrayList<BlockEntity> blockEntities = BlockUtil.getTileEntities();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof ChestBlockEntity && this.chest.booleanValue) {
                this.drawLine(blockEntity.getPos().toCenterPos(), this.chest.getValue());
                continue;
            }
            if (blockEntity instanceof EnderChestBlockEntity && this.enderChest.booleanValue) {
                this.drawLine(blockEntity.getPos().toCenterPos(), this.enderChest.getValue());
                continue;
            }
            if (!(blockEntity instanceof ShulkerBoxBlockEntity) || !this.shulkerBox.booleanValue) continue;
            this.drawLine(blockEntity.getPos().toCenterPos(), this.shulkerBox.getValue());
        }
    }

    private void drawLine(Vec3d pos, Color color) {
        Render3DUtil.drawLine(pos, Tracers.mc.gameRenderer.getCamera().getPos().add(Vec3d.fromPolar((float)Tracers.mc.player.getPitch(mc.getRenderTickCounter().getTickDelta(true)), (float)Tracers.mc.player.getYaw(mc.getRenderTickCounter().getTickDelta(true))).multiply(0.2)), color);
    }
}