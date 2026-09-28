package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
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
import net.minecraft.util.math.Vec3d;

public class PlayerEsp extends Module {
    private final ColorSetting item = add(new ColorSetting("Item", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting player = add(new ColorSetting("Player", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting chest = add(new ColorSetting("Chest", new Color(255, 255, 255, 100)).injectBoolean(false));
    private final ColorSetting enderChest = add(new ColorSetting("EnderChest", new Color(255, 100, 255, 100)).injectBoolean(false));
    private final ColorSetting shulkerBox = add(new ColorSetting("ShulkerBox", new Color(15, 255, 255, 100)).injectBoolean(false));
    public PlayerEsp() {
        super("PlayerESP", Module.Category.Render);
        this.setChinese("玩家ESP");
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        PlayerEsp.mc.options.getBobView().setValue(false);
        if (item.booleanValue || player.booleanValue) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ItemEntity && item.booleanValue) {
                    drawLine(entity.getPos(), item.getValue());
                } else if (entity instanceof PlayerEntity && player.booleanValue && entity != PlayerEsp.mc.player) {
                    drawLine(entity.getPos(), player.getValue());
                }
            }
        }
        ArrayList<BlockEntity> blockEntities = BlockUtil.getTileEntities();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof ChestBlockEntity && chest.booleanValue) {
                drawLine(blockEntity.getPos().toCenterPos(), chest.getValue());
            } else if (blockEntity instanceof EnderChestBlockEntity && enderChest.booleanValue) {
                drawLine(blockEntity.getPos().toCenterPos(), enderChest.getValue());
            } else if (blockEntity instanceof ShulkerBoxBlockEntity && shulkerBox.booleanValue) {
                drawLine(blockEntity.getPos().toCenterPos(), shulkerBox.getValue());
            }
        }
    }


    private void drawLine(Vec3d pos, Color color) {
        if (PlayerEsp.mc.player == null || PlayerEsp.mc.gameRenderer.getCamera() == null) {
            return;
        }
        
        Vec3d cameraPos = PlayerEsp.mc.gameRenderer.getCamera().getPos();
        float pitch = PlayerEsp.mc.player.getPitch(PlayerEsp.mc.getRenderTickCounter().getTickDelta(true));
        float yaw = PlayerEsp.mc.player.getYaw(PlayerEsp.mc.getRenderTickCounter().getTickDelta(true));
        Vec3d direction = Vec3d.fromPolar(pitch, yaw).multiply(0.2);
        Vec3d end = cameraPos.add(direction);
        
        Render3DUtil.drawLine(pos, end, color);
    }
}