package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.TextRadar;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.util.Formatting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector4d;

public class NameTag2D
        extends Module {
    public static NameTag2D INSTANCE;
    private final SliderSetting scale = this.add(new SliderSetting("Scale", (double)0.68f, (double)0.1f, 2.0, 0.01));
    private final SliderSetting minScale = this.add(new SliderSetting("MinScale", (double)0.2f, (double)0.1f, 1.0, 0.01));
    private final SliderSetting scaled = this.add(new SliderSetting("Scaled", 1.0, 0.0, 2.0, 0.01));
    private final SliderSetting offset = this.add(new SliderSetting("Offset", (double)0.315f, (double)0.001f, 1.0, 0.001));
    private final SliderSetting height = this.add(new SliderSetting("Height", 0.0, -3.0, 3.0, 0.01));
    private final BooleanSetting god = this.add(new BooleanSetting("God", true));
    private final BooleanSetting gamemode = this.add(new BooleanSetting("Gamemode", false));
    private final BooleanSetting ping = this.add(new BooleanSetting("Ping", false));
    private final BooleanSetting health = this.add(new BooleanSetting("Health", true));
    private final BooleanSetting distance = this.add(new BooleanSetting("Distance", true));
    private final BooleanSetting pops = this.add(new BooleanSetting("TotemPops", true));
    private final BooleanSetting enchants = this.add(new BooleanSetting("Enchants", true));
    private final ColorSetting outline = this.add(new ColorSetting("Outline", new Color(-1711276033, true)).injectBoolean(true));
    private final ColorSetting rect = this.add(new ColorSetting("Rect", new Color(-1728053247, true)).injectBoolean(true));
    private final ColorSetting friendColor = this.add(new ColorSetting("FriendColor", new Color(-14811363, true)));
    private final ColorSetting color = this.add(new ColorSetting("Color", new Color(-1, true)));
    private final ColorSetting turtleColor = this.add(new ColorSetting("TurtleColor", new Color(85, 85, 255)));
    public final EnumSetting<Font> font = this.add(new EnumSetting<Font>("FontMode", Font.Fast));
    private final SliderSetting armorHeight = this.add(new SliderSetting("ArmorHeight", 0.3f, -10.0, 10.0));
    private final SliderSetting armorScale = this.add(new SliderSetting("ArmorScale", (double)0.9f, (double)0.1f, 2.0, 0.01f));
    private final EnumSetting<Armor> armorMode = this.add(new EnumSetting<Armor>("ArmorMode", Armor.Full));

    public NameTag2D() {
        super("NameTag2D", Module.Category.Render);
        this.setChinese("2D名字标签");
        INSTANCE = this;
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        DrawContext context = event.drawContext;
        float tickDelta = event.tickDelta;
        for (PlayerEntity ent : NameTag2D.mc.world.getPlayers()) {
            Vec3d vector;
            if (ent == NameTag2D.mc.player && NameTag2D.mc.options.getPerspective().isFirstPerson() && Freecam.INSTANCE.isOff()) continue;
            double x = ent.prevX + (ent.getX() - ent.prevX) * (double)tickDelta;
            double y = ent.prevY + (ent.getY() - ent.prevY) * (double)tickDelta;
            double z = ent.prevZ + (ent.getZ() - ent.prevZ) * (double)tickDelta;
            // 使用 getHeight() 替代 getBoundingBox().getLengthY()
            Vec3d preVec = vector = new Vec3d(x, y + this.height.getValue() + ent.getHeight() + 0.3, z);
            vector = TextUtil.worldSpaceToScreenSpace(new Vec3d(vector.x, vector.y, vector.z));
            if (!(vector.z > 0.0) || !(vector.z < 1.0)) continue;
            Vector4d position = new Vector4d(vector.x, vector.y, vector.z, 0.0);
            position.x = Math.min(vector.x, position.x);
            position.y = Math.min(vector.y, position.y);
            position.z = Math.max(vector.x, position.z);
            
            boolean hasTurtle = TextRadar.hasTurtleEffect(ent);
            int turtleSeconds = 0;
            String turtleText = "";
            float turtleIconWidth = 0;
            float turtleTextWidth = 0;
            
            if (hasTurtle) {
                turtleSeconds = TextRadar.getTurtleSeconds(ent);
                turtleText = turtleSeconds + "s";
                turtleIconWidth = 8;
                turtleTextWidth = this.font.getValue() == Font.Fancy 
                    ? FontManager.ui.getWidth(turtleText) 
                    : mc.textRenderer.getWidth(turtleText);
            }
            
            Object final_string = "";
            if (this.god.getValue() && ent.hasStatusEffect(StatusEffects.SLOWNESS)) {
                final_string = (String)final_string + "\u00a74GOD ";
            }
            if (this.ping.getValue()) {
                final_string = (String)final_string + NameTag2D.getEntityPing(ent) + "ms ";
            }
            if (this.gamemode.getValue()) {
                final_string = (String)final_string + this.translateGamemode(NameTag2D.getEntityGamemode(ent)) + " ";
            }
            final_string = (String)final_string + String.valueOf(Formatting.RESET) + ent.getName().getString();
            if (this.health.getValue()) {
                final_string = (String)final_string + " " + String.valueOf(this.getHealthColor(ent)) + NameTag2D.round2(ent.getAbsorptionAmount() + ent.getHealth());
            }
            if (this.distance.getValue()) {
                final_string = (String)final_string + " " + String.valueOf(Formatting.RESET) + String.format("%.1f", Float.valueOf(NameTag2D.mc.player.distanceTo((Entity)ent))) + "m";
            }
            if (this.pops.getValue() && Alien.POP.getPop(ent.getName().getString()) != 0) {
                final_string = (String)final_string + " \u00a7bPop " + String.valueOf(Formatting.LIGHT_PURPLE) + Alien.POP.getPop(ent.getName().getString());
            }
            
            double posX = position.x;
            double posY = position.y;
            double endPosX = position.z;
            float diff = (float)(endPosX - posX) / 2.0f;
            
            float baseTextWidth = this.font.getValue() == Font.Fancy 
                ? FontManager.ui.getWidth((String)final_string) * 1.0f 
                : (float)NameTag2D.mc.textRenderer.getWidth((String)final_string);
            
            float totalWidth = baseTextWidth;
            if (hasTurtle) {
                totalWidth += turtleIconWidth + 2 + turtleTextWidth + 2;
            }
            
            float tagX = (float)((posX + (double)diff - (double)(totalWidth / 2.0f)) * 1.0);
            ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
            stacks.add(ent.getMainHandStack());
            stacks.add((ItemStack)ent.getInventory().armor.get(3));
            stacks.add((ItemStack)ent.getInventory().armor.get(2));
            stacks.add((ItemStack)ent.getInventory().armor.get(1));
            stacks.add((ItemStack)ent.getInventory().armor.get(0));
            stacks.add(ent.getOffHandStack());
            context.getMatrices().push();
            context.getMatrices().translate(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f, (float)(posY - 13.0) + 6.5f, 0.0f);

            // 修复距离计算
            double distanceToEntity = NameTag2D.mc.cameraEntity.getPos().distanceTo(preVec);
            float clampedDistance = MathHelper.clamp((float)distanceToEntity, 0.0f, 100.0f);
            float size = (float)Math.max(1.0 - (double)(clampedDistance * 0.01f) * this.scaled.getValue(), 0.0);

            context.getMatrices().scale(Math.max(this.scale.getValueFloat() * size, this.minScale.getValueFloat()), Math.max(this.scale.getValueFloat() * size, this.minScale.getValueFloat()), 1.0f);
            context.getMatrices().translate(0.0f, this.offset.getValueFloat() * clampedDistance, 0.0f);
            context.getMatrices().translate(-(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f), -((float)(posY - 13.0 + 6.5)), 0.0f);
            float item_offset = 0.0f;
            if (this.armorMode.getValue() != Armor.None) {
                int count = 0;
                for (ItemStack armorComponent : stacks) {
                    ++count;
                    if (!armorComponent.isEmpty()) {
                        context.getMatrices().push();
                        context.getMatrices().translate(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f, (float)(posY - 13.0) + 6.5f, 0.0f);
                        context.getMatrices().scale(this.armorScale.getValueFloat(), this.armorScale.getValueFloat(), 1.0f);
                        context.getMatrices().translate(-(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f), -((float)(posY - 13.0 + 6.5)), 0.0f);
                        context.getMatrices().translate(posX - 52.5 + (double)item_offset, (double)((float)(posY - 29.0) + this.armorHeight.getValueFloat()), 0.0);
                        float durability = armorComponent.getMaxDamage() - armorComponent.getDamage();
                        int percent = (int)(durability / (float)armorComponent.getMaxDamage() * 100.0f);
                        Color color = percent <= 33 ? Color.RED : (percent <= 66 ? Color.ORANGE : Color.GREEN);
                        switch (this.armorMode.getValue().ordinal()) {
                            case 4: {
                                if (count <= 1 || count >= 6) break;
                                DiffuseLighting.disableGuiDepthLighting();
                                context.drawItem(armorComponent, 0, 0);
                                context.drawItemInSlot(NameTag2D.mc.textRenderer, armorComponent, 0, 0);
                                break;
                            }
                            case 3: {
                                DiffuseLighting.disableGuiDepthLighting();
                                context.drawItem(armorComponent, 0, 0);
                                context.drawItemInSlot(NameTag2D.mc.textRenderer, armorComponent, 0, 0);
                                break;
                            }
                            case 1: {
                                DiffuseLighting.disableGuiDepthLighting();
                                context.drawItem(armorComponent, 0, 0);
                                context.drawItemInSlot(NameTag2D.mc.textRenderer, armorComponent, 0, 0);
                                if (armorComponent.getMaxDamage() <= 0) break;
                                if (this.font.getValue() == Font.Fancy) {
                                    FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), (double)(9.0f - FontManager.ui.getWidth(String.valueOf(percent)) / 2.0f), (double)(-FontManager.ui.getFontHeight() + 3.0f), color.getRGB());
                                    break;
                                }
                                TextRenderer PackResourceMetadata = NameTag2D.mc.textRenderer;
                                String string = String.valueOf(percent);
                                int n = 9 - NameTag2D.mc.textRenderer.getWidth(String.valueOf(percent)) / 2;
                                Objects.requireNonNull(NameTag2D.mc.textRenderer);
                                context.drawText(PackResourceMetadata, string, n, -9 + 1, color.getRGB(), true);
                                break;
                            }
                            case 2: {
                                context.drawItemInSlot(NameTag2D.mc.textRenderer, armorComponent, 0, 0);
                                if (armorComponent.getMaxDamage() <= 0) break;
                                if (!armorComponent.isItemBarVisible()) {
                                    int i = armorComponent.getItemBarStep();
                                    int j = armorComponent.getItemBarColor();
                                    int k = 2;
                                    int l = 13;
                                    context.fill(RenderLayer.getGuiOverlay(), k, l, k + 13, l + 2, -16777216);
                                    context.fill(RenderLayer.getGuiOverlay(), k, l, k + i, l + 1, j | 0xFF000000);
                                }
                                if (this.font.getValue() == Font.Fancy) {
                                    FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), (double)(9.0f - FontManager.ui.getWidth(String.valueOf(percent)) / 2.0f), 7.0, color.getRGB());
                                    break;
                                }
                                context.drawText(NameTag2D.mc.textRenderer, String.valueOf(percent), 9 - NameTag2D.mc.textRenderer.getWidth(String.valueOf(percent)) / 2, 5, color.getRGB(), true);
                            }
                        }
                        context.getMatrices().pop();
                        // 注释掉有问题的附魔显示代码
                        /*
                        if (this.enchants.getValue()) {
                            float enchantmentY = 0.0f;
                            if (armorComponent.hasEnchantments()) {
                                // 这部分代码在1.21.1中有问题，暂时注释掉
                            }
                        }
                        */
                    }
                    item_offset += 18.0f;
                }
            }
            if (this.rect.booleanValue) {
                Render2DUtil.drawRect(context.getMatrices(), tagX - 2.0f, (float)(posY - 14.0), totalWidth + 4.0f, 13.0f, this.rect.getValue());
            }
            if (this.outline.booleanValue) {
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float)(posY - 14.0), totalWidth + 6.0f, 1.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float)(posY - 2.0), totalWidth + 6.0f, 1.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float)(posY - 14.0), 1.0f, 12.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX + totalWidth + 2.0f, (float)(posY - 14.0), 1.0f, 12.0f, this.outline.getValue());
            }
            
            boolean isFriend = Alien.FRIEND.isFriend(ent);
            int textColor;
            
            if (isFriend) {
                textColor = this.friendColor.getValue().getRGB();
            } else if (hasTurtle) {
                textColor = this.turtleColor.getValue().getRGB();
            } else {
                textColor = this.color.getValue().getRGB();
            }
            
            float currentX = tagX;
            
            if (hasTurtle) {
                int iconDrawX = (int)currentX;
                int iconDrawY = (int)posY - 11;
                
                StatusEffectSpriteManager spriteManager = mc.getStatusEffectSpriteManager();
                Sprite sprite = spriteManager.getSprite(StatusEffects.RESISTANCE);
                if (sprite != null) {
                    context.drawSprite(iconDrawX, iconDrawY, 0, 8, 8, sprite);
                }
                
                currentX += turtleIconWidth + 2;
                
                if (this.font.getValue() == Font.Fancy) {
                    FontManager.ui.drawString(context.getMatrices(), turtleText, currentX, iconDrawY, this.turtleColor.getValue().getRGB(), true);
                } else {
                    context.drawText(mc.textRenderer, turtleText, (int)currentX, iconDrawY, this.turtleColor.getValue().getRGB(), true);
                }
                
                currentX += turtleTextWidth + 2;
            }
            
            if (this.font.getValue() == Font.Fancy) {
                FontManager.ui.drawString(context.getMatrices(), (String)final_string, currentX, (double)((float)posY - 10.0f), textColor);
            } else {
                context.getMatrices().push();
                context.getMatrices().translate(currentX, (float)posY - 11.0f, 0.0f);
                context.drawText(NameTag2D.mc.textRenderer, (String)final_string, 0, 0, textColor, true);
                context.getMatrices().pop();
            }
            
            context.getMatrices().pop();
        }
    }

    public static String getEntityPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) {
            return "-1";
        }
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (playerListEntry == null) {
            return "-1";
        }
        int ping = playerListEntry.getLatency();
        Formatting color = Formatting.GREEN;
        if (ping >= 100) {
            color = Formatting.YELLOW;
        }
        if (ping >= 250) {
            color = Formatting.RED;
        }
        return color.toString() + ping;
    }

    public static GameMode getEntityGamemode(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return playerListEntry == null ? null : playerListEntry.getGameMode();
    }

    private String translateGamemode(GameMode gamemode) {
        if (gamemode == null) {
            return "\u00a77[BOT]";
        }
        return switch (gamemode) {
            case SURVIVAL -> "\u00a7b[S]";
            case CREATIVE -> "\u00a7c[C]";
            case SPECTATOR -> "\u00a77[SP]";
            case ADVENTURE -> "\u00a7e[A]";
            default -> throw new IllegalStateException("Unexpected gamemode: " + gamemode);
        };
    }

    private Formatting getHealthColor(@NotNull PlayerEntity entity) {
        int health = (int)((float)((int)entity.getHealth()) + entity.getAbsorptionAmount());
        if (health >= 18) {
            return Formatting.GREEN;
        }
        if (health >= 12) {
            return Formatting.YELLOW;
        }
        if (health >= 6) {
            return Formatting.RED;
        }
        return Formatting.DARK_RED;
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public static enum Font {
        Fancy,
        Fast;
    }

    public static enum Armor {
        None,
        Full,
        Durability,
        Item,
        OnlyArmor;
    }
}