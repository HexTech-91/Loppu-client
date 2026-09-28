package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4d;

public class TwoDESP
        extends Module {
    private final EnumSetting page = this.add(new EnumSetting<TwoDESPMode>("Settings", TwoDESPMode.Target));
    public final ColorSetting armorDuraColor = this.add(new ColorSetting("Armor Dura Color", new Color(0x2FFF00), () -> this.page.getValue() == TwoDESPMode.Color));
    public final ColorSetting hHealth = this.add(new ColorSetting("High Health Color", new Color(0, 255, 0, 255), () -> this.page.getValue() == TwoDESPMode.Color));
    public final ColorSetting mHealth = this.add(new ColorSetting("Mid Health Color", new Color(255, 255, 0, 255), () -> this.page.getValue() == TwoDESPMode.Color));
    public final ColorSetting lHealth = this.add(new ColorSetting("Low Health Color", new Color(255, 0, 0, 255), () -> this.page.getValue() == TwoDESPMode.Color));
    private final BooleanSetting outline = this.add(new BooleanSetting("Outline", true, () -> this.page.getValue() == TwoDESPMode.Setting));
    private final BooleanSetting renderHealth = this.add(new BooleanSetting("renderHealth", true, () -> this.page.getValue() == TwoDESPMode.Setting));
    private final BooleanSetting renderArmor = this.add(new BooleanSetting("Armor Dura", true, () -> this.page.getValue() == TwoDESPMode.Setting));
    private final SliderSetting durascale = this.add(new SliderSetting("DuraScale", 1.0, 0.0, 2.0, 0.1, () -> this.renderArmor.getValue()));
    private final BooleanSetting drawItem = this.add(new BooleanSetting("draw Item Name", true, () -> this.page.getValue() == TwoDESPMode.Setting));
    private final BooleanSetting drawItemC = this.add(new BooleanSetting("draw Item Count", true, () -> this.page.getValue() == TwoDESPMode.Setting && this.drawItem.getValue()));
    public final ColorSetting countColor = this.add(new ColorSetting("Item Count Color", new Color(255, 255, 0, 255), () -> this.page.getValue() == TwoDESPMode.Color && this.drawItemC.getValue()));
    public final ColorSetting textcolor = this.add(new ColorSetting("Item Name Color", new Color(255, 255, 255, 255), () -> this.page.getValue() == TwoDESPMode.Color && this.drawItem.getValue()));
    private final BooleanSetting font = this.add(new BooleanSetting("CustomFont", true, () -> this.page.getValue() == TwoDESPMode.Setting));
    private final BooleanSetting players = this.add(new BooleanSetting("Players", true, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting friends = this.add(new BooleanSetting("Friends", true, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting crystals = this.add(new BooleanSetting("Crystals", true, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting creatures = this.add(new BooleanSetting("Creatures", false, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting monsters = this.add(new BooleanSetting("Monsters", false, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting ambients = this.add(new BooleanSetting("Ambients", false, () -> this.page.getValue() == TwoDESPMode.Target));
    private final BooleanSetting others = this.add(new BooleanSetting("Others", false, () -> this.page.getValue() == TwoDESPMode.Target));
    private final ColorSetting playersC = this.add(new ColorSetting("PlayersBox", new Color(16749056), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting friendsC = this.add(new ColorSetting("FriendsBox", new Color(0x30FF00), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting crystalsC = this.add(new ColorSetting("CrystalsBox", new Color(48127), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting creaturesC = this.add(new ColorSetting("CreaturesBox", new Color(10527910), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting monstersC = this.add(new ColorSetting("MonstersBox", new Color(0xFF0000), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting ambientsC = this.add(new ColorSetting("AmbientsBox", new Color(8061183), () -> this.page.getValue() == TwoDESPMode.Color));
    private final ColorSetting othersC = this.add(new ColorSetting("OthersBox", new Color(16711778), () -> this.page.getValue() == TwoDESPMode.Color));

    public TwoDESP() {
        super("TwoDESP", Module.Category.Render);
        this.setChinese("2D显示");
    }

    public static float getRotations(Vec2f vec) {
        if (TwoDESP.mc.player == null) {
            return 0.0f;
        }
        double x = (double)vec.x - TwoDESP.mc.player.getPos().x;
        double z = (double)vec.y - TwoDESP.mc.player.getPos().z;
        return (float)(-(Math.atan2(x, z) * 57.29577951308232));
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // 启用混合模式用于透明渲染
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder bufferBuilder = null;
        boolean hasVertices = false;
        for (Entity ent : TwoDESP.mc.world.getEntities()) {
            if (!this.shouldRender(ent)) continue;
            if (bufferBuilder == null) {
                bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            }
            if (!this.drawBox(bufferBuilder, ent, matrix, context)) continue;
            hasVertices = true;
        }
        if (hasVertices && bufferBuilder != null) {
            BufferRenderer.drawWithGlobalProgram((BuiltBuffer)bufferBuilder.end());
        }

        // 禁用混合模式
        RenderSystem.disableBlend();

        for (Entity ent : TwoDESP.mc.world.getEntities()) {
            if (!this.shouldRender(ent)) continue;
            this.drawText(ent, context);
        }
    }

    public boolean shouldRender(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (TwoDESP.mc.player == null) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            if (entity == TwoDESP.mc.player && TwoDESP.mc.options.getPerspective().isFirstPerson()) {
                return false;
            }
            if (Alien.FRIEND.isFriend((PlayerEntity)entity)) {
                return this.friends.getValue();
            }
            return this.players.getValue();
        }
        if (entity instanceof EndCrystalEntity) {
            return this.crystals.getValue();
        }
        return switch (entity.getType().getSpawnGroup()) {
            case SpawnGroup.CREATURE, SpawnGroup.WATER_CREATURE -> this.creatures.getValue();
            case SpawnGroup.MONSTER -> this.monsters.getValue();
            case SpawnGroup.AMBIENT, SpawnGroup.WATER_AMBIENT -> this.ambients.getValue();
            default -> this.others.getValue();
        };
    }

    public Color getEntityColor(Entity entity) {
        if (entity == null) {
            return new Color(-1);
        }
        if (entity instanceof PlayerEntity) {
            if (Alien.FRIEND.isFriend((PlayerEntity)entity)) {
                return this.friendsC.getValue();
            }
            return this.playersC.getValue();
        }
        if (entity instanceof EndCrystalEntity) {
            return this.crystalsC.getValue();
        }
        return switch (entity.getType().getSpawnGroup()) {
            case SpawnGroup.CREATURE, SpawnGroup.WATER_CREATURE -> this.creaturesC.getValue();
            case SpawnGroup.MONSTER -> this.monstersC.getValue();
            case SpawnGroup.AMBIENT, SpawnGroup.WATER_AMBIENT -> this.ambientsC.getValue();
            default -> this.othersC.getValue();
        };
    }

    public boolean drawBox(BufferBuilder bufferBuilder, @NotNull Entity ent, Matrix4f matrix, DrawContext context) {
        double x = ent.prevX + (ent.getX() - ent.prevX) * (double)mc.getRenderTickCounter().getTickDelta(true);
        double y = ent.prevY + (ent.getY() - ent.prevY) * (double)mc.getRenderTickCounter().getTickDelta(true);
        double z = ent.prevZ + (ent.getZ() - ent.prevZ) * (double)mc.getRenderTickCounter().getTickDelta(true);
        Box axisAlignedBB2 = ent.getBoundingBox();
        Box axisAlignedBB = new Box(axisAlignedBB2.minX - ent.getX() + x - 0.05, axisAlignedBB2.minY - ent.getY() + y, axisAlignedBB2.minZ - ent.getZ() + z - 0.05, axisAlignedBB2.maxX - ent.getX() + x + 0.05, axisAlignedBB2.maxY - ent.getY() + y + 0.15, axisAlignedBB2.maxZ - ent.getZ() + z + 0.05);
        Vec3d[] vectors = new Vec3d[]{new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)};
        Color col = this.getEntityColor(ent);
        Vector4d position = null;
        for (Vec3d vector : vectors) {
            vector = TextUtil.worldSpaceToScreenSpace(new Vec3d(vector.x, vector.y, vector.z));
            if (!(vector.z > 0.0) || !(vector.z < 1.0)) continue;
            if (position == null) {
                position = new Vector4d(vector.x, vector.y, vector.z, 0.0);
            }
            position.x = Math.min(vector.x, position.x);
            position.y = Math.min(vector.y, position.y);
            position.z = Math.max(vector.x, position.z);
            position.w = Math.max(vector.y, position.w);
        }
        if (position != null) {
            LivingEntity lent;
            double posX = position.x;
            double posY = position.y;
            double endPosX = position.z;
            double endPosY = position.w;
            if (this.outline.getValue()) {
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 1.0), (float)posY, 1.5f, (float)(endPosY - posY + 0.5), Color.BLACK);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 1.0), (float)(posY - 0.5), (float)(endPosX - posX + 1.5), 1.5f, Color.BLACK);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(endPosX - 1.0), (float)posY, 1.5f, (float)(endPosY - posY + 0.5), Color.BLACK);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 1.0), (float)(endPosY - 1.0), (float)(endPosX - posX + 1.5), 1.5f, Color.BLACK);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 0.5), (float)posY, 0.5f, (float)(endPosY - posY), col);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)posX, (float)(endPosY - 0.5), (float)(endPosX - posX), 0.5f, col);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 0.5), (float)posY, (float)(endPosX - posX + 0.5), 0.5f, col);
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(endPosX - 0.5), (float)posY, 0.5f, (float)(endPosY - posY), col);
            }
            if (ent instanceof LivingEntity && (lent = (LivingEntity)ent).getHealth() != 0.0f && this.renderHealth.getValue()) {
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 4.0), (float)posY, 1.0f, (float)(endPosY - posY), Color.BLACK);
                Color color = this.getcolor(lent.getHealth());
                float healthHeight = (float)(endPosY - (endPosY + (posY - endPosY) * (double)lent.getHealth() / (double)lent.getMaxHealth()));
                this.drawRectToBuffer(bufferBuilder, matrix, (float)(posX - 4.0), (float)(endPosY - (double)healthHeight), 1.0f, healthHeight, color);
            }
            if (ent instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity)ent;
                if (this.renderArmor.getValue()) {
                    double height = (endPosY - posY) / 4.0;
                    ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
                    stacks.add((ItemStack)player.getInventory().armor.get(3));
                    stacks.add((ItemStack)player.getInventory().armor.get(2));
                    stacks.add((ItemStack)player.getInventory().armor.get(1));
                    stacks.add((ItemStack)player.getInventory().armor.get(0));
                    int i = -1;
                    for (ItemStack armor : stacks) {
                        ++i;
                        if (armor.isEmpty()) continue;
                        float durability = armor.getMaxDamage() - armor.getDamage();
                        int percent = (int)(durability / (float)armor.getMaxDamage() * 100.0f);
                        double finalH = height * (double)(percent / 100);
                        this.drawRectToBuffer(bufferBuilder, matrix, (float)(endPosX + 1.5), (float)((double)((float)posY) + height * (double)i + 1.2 * (double)(i + 1)), 1.5f, (float)finalH, this.armorDuraColor.getValue());
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void drawRectToBuffer(BufferBuilder bufferBuilder, Matrix4f matrix, float x, float y, float width, float height, Color color) {
        float a = (float)color.getAlpha() / 255.0f;
        float r = (float)color.getRed() / 255.0f;
        float g = (float)color.getGreen() / 255.0f;
        float b = (float)color.getBlue() / 255.0f;
        bufferBuilder.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        bufferBuilder.vertex(matrix, x, y + height, 0.0f).color(r, g, b, a);
        bufferBuilder.vertex(matrix, x + width, y + height, 0.0f).color(r, g, b, a);
        bufferBuilder.vertex(matrix, x + width, y, 0.0f).color(r, g, b, a);
    }

    public void drawText(Entity ent, DrawContext context) {
        double x = ent.prevX + (ent.getX() - ent.prevX) * (double)mc.getRenderTickCounter().getTickDelta(true);
        double y = ent.prevY + (ent.getY() - ent.prevY) * (double)mc.getRenderTickCounter().getTickDelta(true);
        double z = ent.prevZ + (ent.getZ() - ent.prevZ) * (double)mc.getRenderTickCounter().getTickDelta(true);
        Box axisAlignedBB2 = ent.getBoundingBox();
        Box axisAlignedBB = new Box(axisAlignedBB2.minX - ent.getX() + x - 0.05, axisAlignedBB2.minY - ent.getY() + y, axisAlignedBB2.minZ - ent.getZ() + z - 0.05, axisAlignedBB2.maxX - ent.getX() + x + 0.05, axisAlignedBB2.maxY - ent.getY() + y + 0.15, axisAlignedBB2.maxZ - ent.getZ() + z + 0.05);
        Vec3d[] vectors = new Vec3d[]{new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vec3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)};
        Color col = this.getEntityColor(ent);
        Vector4d position = null;
        for (Vec3d vector : vectors) {
            vector = TextUtil.worldSpaceToScreenSpace(new Vec3d(vector.x, vector.y, vector.z));
            if (!(vector.z > 0.0) || !(vector.z < 1.0)) continue;
            if (position == null) {
                position = new Vector4d(vector.x, vector.y, vector.z, 0.0);
            }
            position.x = Math.min(vector.x, position.x);
            position.y = Math.min(vector.y, position.y);
            position.z = Math.max(vector.x, position.z);
            position.w = Math.max(vector.y, position.w);
        }
        if (position != null) {
            double posX = position.x;
            double posY = position.y;
            double endPosX = position.z;
            double endPosY = position.w;
            if (ent instanceof ItemEntity) {
                ItemEntity entity = (ItemEntity)ent;
                if (this.drawItem.getValue()) {
                    float diff = (float)((endPosX - posX) / 2.0);
                    float textWidth = FontManager.ui.getWidth(entity.getDisplayName().getString());
                    float tagX = (float)(posX + (double)diff - (double)(textWidth / 2.0f));
                    int count = entity.getStack().getCount();
                    context.drawText(TwoDESP.mc.textRenderer, entity.getDisplayName().getString(), (int)tagX, (int)(posY - 10.0), this.textcolor.getValue().getRGB(), false);
                    if (this.drawItemC.getValue()) {
                        context.drawText(TwoDESP.mc.textRenderer, "x" + count, (int)(tagX + (float)TwoDESP.mc.textRenderer.getWidth(entity.getDisplayName().getString() + " ")), (int)posY - 10, this.countColor.getValue().getRGB(), false);
                    }
                }
            }
            if (ent instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity)ent;
                if (this.renderArmor.getValue()) {
                    double height = (endPosY - posY) / 4.0;
                    ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
                    stacks.add((ItemStack)player.getInventory().armor.get(3));
                    stacks.add((ItemStack)player.getInventory().armor.get(2));
                    stacks.add((ItemStack)player.getInventory().armor.get(1));
                    stacks.add((ItemStack)player.getInventory().armor.get(0));
                    int i = -1;
                    for (ItemStack armor : stacks) {
                        ++i;
                        if (armor.isEmpty()) continue;
                        float durability = armor.getMaxDamage() - armor.getDamage();
                        int percent = (int)(durability / (float)armor.getMaxDamage() * 100.0f);
                        double finalH = height * ((double)percent / 100.0);
                        context.drawItem(armor, (int)(endPosX + 4.0), (int)(posY + height * (double)i + 1.2 * (double)(i + 1) + finalH / 2.0));
                    }
                }
            }
        }
    }

    public Color getcolor(float health) {
        if (health >= 20.0f) {
            return this.hHealth.getValue();
        }
        if (20.0f > health && health > 10.0f) {
            return this.mHealth.getValue();
        }
        return this.lHealth.getValue();
    }

    public static enum TwoDESPMode {
        Setting,
        Target,
        Color;

    }
}