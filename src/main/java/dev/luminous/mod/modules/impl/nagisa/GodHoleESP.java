package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GodHoleESP extends Module {
    private final SliderSetting renderDistance = add(new SliderSetting("RenderDistance", 20f, 1f, 50f));

    private final SliderSetting horizontalRadius = add(new SliderSetting("HorizontalRadius", 8, 0, 32, 1));

    private final SliderSetting verticalRadius = add(new SliderSetting("VerticalRadius", 4, 0, 16, 1));

    private final SliderSetting boxHeight = add(new SliderSetting("BoxHeight", 2.0, 0.0, 5.0, 0.1));

    private final SliderSetting startFade = add(new SliderSetting("AlphaFade", 5f, 1f, 20f));

    private final SliderSetting distance = add(new SliderSetting("Distance", 20f, 1f, 50f));

    private final ColorSetting fillColor = add(new ColorSetting("FillColor", new Color(0, 255, 255, 100))
            .injectBoolean(true));

    private final ColorSetting outlineColor = add(new ColorSetting("OutlineColor", new Color(0, 255, 255, 255))
            .injectBoolean(true));

    private final SliderSetting updateDelay = add(new SliderSetting("UpdateDelay", 50, 0, 1000));

    private final List<BlockPos> godHoles = new CopyOnWriteArrayList<>();
    private final List<BlockPos> tempGodHoles = new CopyOnWriteArrayList<>();
    private final Timer timer = new Timer();
    private boolean drawing = false;

    public GodHoleESP() {
        super("GodHoleESP", Category.Render);
        setChinese("神洞透视");
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (!drawing && timer.passedMs((long) updateDelay.getValue())) {
            godHoles.clear();
            godHoles.addAll(tempGodHoles);
            timer.reset();
            tempGodHoles.clear();

            BlockPos playerPos = mc.player.getBlockPos();
            int hRadius = horizontalRadius.getValueInt();
            int vRadius = verticalRadius.getValueInt();

            for (int dx = -hRadius; dx <= hRadius; dx++) {
                for (int dy = -vRadius; dy <= vRadius; dy++) {
                    for (int dz = -hRadius; dz <= hRadius; dz++) {
                        BlockPos pos = playerPos.add(dx, dy, dz);
                        if (isGodHole(pos)) {
                            tempGodHoles.add(pos);
                        }
                    }
                }
            }
        }
    }

    private boolean isGodHole(BlockPos pos) {
        // Check if there are two air blocks
        if (!mc.world.isAir(pos) || !mc.world.isAir(pos.up())) {
            return false;
        }

        // Check if there is bedrock above
        if (mc.world.getBlockState(pos.up(2)).getBlock() != Blocks.BEDROCK) {
            return false;
        }

        // Check if there is bedrock around
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            if (mc.world.getBlockState(pos.offset(dir)).getBlock() != Blocks.BEDROCK) {
                return false;
            }
        }

        return true;
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        drawing = true;
        drawGodHoles(event.matrixStack);
        drawing = false;
    }

    private void drawGodHoles(MatrixStack matrixStack) {
        double maxDistance = distance.getValue();
        double boxH = boxHeight.getValue();

        for (BlockPos pos : godHoles) {
            double distanceTo = mc.player.getPos().distanceTo(pos.toCenterPos());
            if (distanceTo > maxDistance) continue;

            double alpha = distanceTo > startFade.getValue()
                    ? Math.max(Math.min(1, (1 - ((distanceTo - startFade.getValue()) / (maxDistance - startFade.getValue())))), 0)
                    : 1;

            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + boxH, pos.getZ() + 1);

            // Draw fill
            if (fillColor.booleanValue) {
                Color fill = ColorUtil.injectAlpha(fillColor.getValue(), (int) (fillColor.getValue().getAlpha() * alpha));
                Render3DUtil.drawFill(matrixStack, box, fill);
            }

            // Draw outline
            if (outlineColor.booleanValue) {
                Color outline = ColorUtil.injectAlpha(outlineColor.getValue(), (int) (outlineColor.getValue().getAlpha() * alpha));
                Render3DUtil.drawBox(matrixStack, box, outline);
            }
        }
    }
}