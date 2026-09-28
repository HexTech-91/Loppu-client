package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector4d;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class NameTags extends Module {
    public static NameTags INSTANCE;
    private final SliderSetting scale = add(new SliderSetting("Scale", 0.68f, 0.1f, 2f, 0.01));
    private final SliderSetting minScale = add(new SliderSetting("MinScale", 0.2f, 0.1f, 1f, 0.01));
    private final SliderSetting scaled = add(new SliderSetting("Scaled", 1, 0, 2, 0.01));
    private final SliderSetting offset = add(new SliderSetting("Offset", 0.315f, 0.001f, 1f, 0.001));
    private final SliderSetting height = add(new SliderSetting("Height", 0, -3, 3, 0.01));
    private final BooleanSetting god = add(new BooleanSetting("God", true));
    private final BooleanSetting gamemode = add(new BooleanSetting("Gamemode", false));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", false));
    private final BooleanSetting health = add(new BooleanSetting("Health", true));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", true));
    private final BooleanSetting pops = add(new BooleanSetting("TotemPops", true));
    private final BooleanSetting enchants = add(new BooleanSetting("Enchants", true));
    private final BooleanSetting turtle = add(new BooleanSetting("TurtleTime", true));
    private final BooleanSetting potionCalc;
    {
        potionCalc = add(new BooleanSetting("PotionCalc", true));
    }

    private final Map<Integer, Long> strengthTimers = new ConcurrentHashMap<>();
    private final Map<Integer, Long> turtleTimers = new ConcurrentHashMap<>();

    private final ColorSetting outline = add(new ColorSetting("Outline", new Color(0x99FFFFFF, true)).injectBoolean(true));
    private final ColorSetting rect = add(new ColorSetting("Rect", new Color(0x99000001, true)).injectBoolean(true));
    private final ColorSetting friendColor = add(new ColorSetting("FriendColor", new Color(0xFF60D8FF, true)));
    private final ColorSetting color = add(new ColorSetting("Color", new Color(0xFFFFFFFF, true)));

    public final EnumSetting<Font> font = add(new EnumSetting<>("FontMode", Font.Fast));
    private final SliderSetting armorHeight = add(new SliderSetting("ArmorHeight", 0.3f, -10, 10f));
    private final SliderSetting armorScale = add(new SliderSetting("ArmorScale", 0.9f, 0.1f, 2f, 0.01f));
    private final EnumSetting<Armor> armorMode = add(new EnumSetting<>("ArmorMode", Armor.Full));

    public NameTags() {
        super("NameTags", Category.Render);
        setChinese("名字标签");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        for (PlayerEntity ent : mc.world.getPlayers()) {
            if (ent == mc.player && mc.options.getPerspective().isFirstPerson() && Freecam.INSTANCE.isOff()) continue;
            double x = ent.prevX + (ent.getX() - ent.prevX) * tickDelta;
            double y = ent.prevY + (ent.getY() - ent.prevY) * tickDelta;
            double z = ent.prevZ + (ent.getZ() - ent.prevZ) * tickDelta;
            Vec3d vector = new Vec3d(x, y + height.getValue() + ent.getBoundingBox().getLengthY() + 0.3, z);
            Vec3d preVec = vector;
            vector = TextUtil.worldSpaceToScreenSpace(new Vec3d(vector.x, vector.y, vector.z));
            if (vector.z > 0 && vector.z < 1) {
                Vector4d position = new Vector4d(vector.x, vector.y, vector.z, 0);
                position.x = Math.min(vector.x, position.x);
                position.y = Math.min(vector.y, position.y);
                position.z = Math.max(vector.x, position.z);

                String final_string = "";
                if (god.getValue() && ent.hasStatusEffect(StatusEffects.SLOWNESS)) {
                    final_string += "§4GOD ";
                }
                if (ping.getValue()) {
                    final_string += getEntityPing(ent) + "ms ";
                }
                if (gamemode.getValue()) {
                    final_string += translateGamemode(getEntityGamemode(ent)) + " ";
                }
                String displayName = ent.getName().getString();
                final_string += Formatting.RESET + displayName;
                if (health.getValue()) {
                    final_string += " " + getHealthColor(ent) + round2(ent.getAbsorptionAmount() + ent.getHealth());
                }
                if (potionCalc.getValue()) {
                    long now = System.currentTimeMillis();
                    int pid = ent.getId();
                    Long sExp = strengthTimers.get(pid);
                    if (sExp != null && sExp > now) {
                        long secs = (sExp - now) / 1000;
                        final_string += " §e力量 " + secs + "s";
                    } else if (sExp != null) strengthTimers.remove(pid);
                    Long tExp = turtleTimers.get(pid);
                    if (tExp != null && tExp > now) {
                        long secs = (tExp - now) / 1000;
                        final_string += " §5神龟 " + secs + "s";
                    } else if (tExp != null) turtleTimers.remove(pid);
                }
                if (distance.getValue()) {
                    final_string += " " + Formatting.RESET + String.format("%.1f", mc.player.distanceTo(ent)) + "m";
                }
                if (pops.getValue() && Alien.POP.getPop(ent.getName().getString()) != 0) {
                    final_string += " §bPop" + " " + Formatting.LIGHT_PURPLE + Alien.POP.getPop(ent.getName().getString());
                }

                double posX = position.x;
                double posY = position.y;
                double endPosX = position.z;

                float diff = (float) (endPosX - posX) / 2;
                float textWidth;

                if (font.getValue() == Font.Fancy) {
                    textWidth = (FontManager.ui.getWidth(final_string) * 1);
                } else {
                    textWidth = mc.textRenderer.getWidth(final_string);
                }

                float tagX = (float) ((posX + diff - textWidth / 2) * 1);

                ArrayList<ItemStack> stacks = new ArrayList<>();
                stacks.add(ent.getMainHandStack());
                stacks.add(ent.getInventory().armor.get(3));
                stacks.add(ent.getInventory().armor.get(2));
                stacks.add(ent.getInventory().armor.get(1));
                stacks.add(ent.getInventory().armor.get(0));
                stacks.add(ent.getOffHandStack());

                context.getMatrices().push();
                context.getMatrices().translate(tagX - 2 + (textWidth + 4) / 2f, (float) (posY - 13f) + 6.5f, 0);
                float size = (float) Math.max(1 - MathHelper.sqrt((float) mc.cameraEntity.squaredDistanceTo(preVec)) * 0.01 * scaled.getValue(), 0);
                context.getMatrices().scale(Math.max(scale.getValueFloat() * size, minScale.getValueFloat()), Math.max(scale.getValueFloat() * size, minScale.getValueFloat()), 1f);
                context.getMatrices().translate(0, offset.getValueFloat() * MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(preVec)), 0);
                context.getMatrices().translate(-(tagX - 2 + (textWidth + 4) / 2f), -(float) ((posY - 13f) + 6.5f), 0);

                float item_offset = 0;
                if (armorMode.getValue() != Armor.None) {
                    int count = 0;
                    for (ItemStack armorComponent : stacks) {
                        count++;
                        if (!armorComponent.isEmpty()) {
                            context.getMatrices().push();
                            context.getMatrices().translate(tagX - 2 + (textWidth + 4) / 2f, (float) (posY - 13f) + 6.5f, 0);
                            context.getMatrices().scale(armorScale.getValueFloat(), armorScale.getValueFloat(), 1f);
                            context.getMatrices().translate(-(tagX - 2 + (textWidth + 4) / 2f), -(float) ((posY - 13f) + 6.5f), 0);
                            context.getMatrices().translate(posX - 52.5 + item_offset, (float) (posY - 29f) + armorHeight.getValueFloat(), 0);
                            float durability = armorComponent.getMaxDamage() - armorComponent.getDamage();
                            int percent = (int) ((durability / (float) armorComponent.getMaxDamage()) * 100F);
                            Color color;
                            if (percent <= 33) {
                                color = Color.RED;
                            } else if (percent <= 66) {
                                color = Color.ORANGE;
                            } else {
                                color = Color.GREEN;
                            }
                            switch (armorMode.getValue()) {
                                case OnlyArmor -> {
                                    if (count > 1 && count < 6) {
                                        DiffuseLighting.disableGuiDepthLighting();
                                        context.drawItem(armorComponent, 0, 0);
                                        context.drawItemInSlot(mc.textRenderer, armorComponent, 0, 0);
                                    }
                                }
                                case Item -> {
                                    DiffuseLighting.disableGuiDepthLighting();
                                    context.drawItem(armorComponent, 0, 0);
                                    context.drawItemInSlot(mc.textRenderer, armorComponent, 0, 0);
                                }
                                case Full -> {
                                    DiffuseLighting.disableGuiDepthLighting();
                                    context.drawItem(armorComponent, 0, 0);
                                    context.drawItemInSlot(mc.textRenderer, armorComponent, 0, 0);
                                    if (armorComponent.getMaxDamage() > 0) {
                                        if (font.getValue() == Font.Fancy) {
                                            FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), 9 - FontManager.ui.getWidth(String.valueOf(percent)) / 2, -FontManager.ui.getFontHeight() + 3, color.getRGB());
                                        } else {
                                            context.drawText(mc.textRenderer, String.valueOf(percent), 9 - mc.textRenderer.getWidth(String.valueOf(percent)) / 2, -mc.textRenderer.fontHeight + 1, color.getRGB(), true);
                                        }
                                    }
                                }
                                case Durability -> {
                                    context.drawItemInSlot(mc.textRenderer, armorComponent, 0, 0);
                                    if (armorComponent.getMaxDamage() > 0) {
                                        if (!armorComponent.isItemBarVisible()) {
                                            int i = armorComponent.getItemBarStep();
                                            int j = armorComponent.getItemBarColor();
                                            int k = 2;
                                            int l = 13;
                                            context.fill(RenderLayer.getGuiOverlay(), k, l, k + 13, l + 2, -16777216);
                                            context.fill(RenderLayer.getGuiOverlay(), k, l, k + i, l + 1, j | -16777216);
                                        }
                                        if (font.getValue() == Font.Fancy) {
                                            FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), 9 - FontManager.ui.getWidth(String.valueOf(percent)) / 2, 7, color.getRGB());
                                        } else {
                                            context.drawText(mc.textRenderer, String.valueOf(percent), 9 - mc.textRenderer.getWidth(String.valueOf(percent)) / 2, 5, color.getRGB(), true);
                                        }
                                    }
                                }
                            }
                            context.getMatrices().pop();

                            if (this.enchants.getValue()) {
                                float enchantmentY = 0;
                                NbtList enchants = new NbtList();
                                try {
                                    Map<Enchantment, Integer> enchantmentMap = new HashMap<>();
                                    try {
                                        for (RegistryEntry<Enchantment> enchantmentEntry : armorComponent.getEnchantments().getEnchantments()) {
                                            int level = armorComponent.getEnchantments().getLevel(enchantmentEntry);
                                            enchantmentMap.put(enchantmentEntry.value(), level);
                                        }
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                    for (Map.Entry<Enchantment, Integer> entry : enchantmentMap.entrySet()) {
                                        NbtCompound compound = new NbtCompound();
                                        compound.putString("id", entry.getKey().toString());
                                        compound.putShort("lvl", entry.getValue().shortValue());
                                        enchants.add(compound);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                                for (int index = 0; index < enchants.size(); ++index) {
                                    String id = enchants.getCompound(index).getString("id");
                                    short level = enchants.getCompound(index).getShort("lvl");
                                    String encName;
                                    switch (id) {
                                        case "minecraft:blast_protection" -> encName = "B" + level;
                                        case "minecraft:protection" -> encName = "P" + level;
                                        case "minecraft:thorns" -> encName = "T" + level;
                                        case "minecraft:sharpness" -> encName = "S" + level;
                                        case "minecraft:efficiency" -> encName = "E" + level;
                                        case "minecraft:unbreaking" -> encName = "U" + level;
                                        case "minecraft:power" -> encName = "PO" + level;
                                        default -> {
                                            continue;
                                        }
                                    }

                                    if (font.getValue() == Font.Fancy) {
                                        FontManager.ui.drawString(context.getMatrices(), encName, posX - 50 + item_offset, (float) posY - 45 + enchantmentY, -1);
                                    } else {
                                        context.getMatrices().push();
                                        context.getMatrices().translate((posX - 50f + item_offset), (posY - 45f + enchantmentY), 0);
                                        context.drawText(mc.textRenderer, encName, 0, 0, -1, true);
                                        context.getMatrices().pop();
                                    }
                                    enchantmentY -= 8;
                                }
                            }
                        }
                        item_offset += 18f;
                    }
                }
                if (rect.booleanValue) {
                    Render2DUtil.drawRect(context.getMatrices(), tagX - 2, (float) (posY - 14f), textWidth + 4, 13, rect.getValue());
                }
                if (outline.booleanValue) {
                    Render2DUtil.drawRect(context.getMatrices(), tagX - 3, (float) (posY - 14f), textWidth + 6, 1, outline.getValue());
                    Render2DUtil.drawRect(context.getMatrices(), tagX - 3, (float) (posY - 2f), textWidth + 6, 1, outline.getValue());
                    Render2DUtil.drawRect(context.getMatrices(), tagX - 3, (float) (posY - 14f), 1, 12, outline.getValue());
                    Render2DUtil.drawRect(context.getMatrices(), tagX + textWidth + 2, (float) (posY - 14f), 1, 12, outline.getValue());
                }
                if (font.getValue() == Font.Fancy) {
                    FontManager.ui.drawString(context.getMatrices(), final_string, tagX, (float) posY - 10, Alien.FRIEND.isFriend(ent) ? friendColor.getValue().getRGB() : this.color.getValue().getRGB());
                } else {
                    context.getMatrices().push();
                    context.getMatrices().translate(tagX, ((float) posY - 11), 0);
                    context.drawText(mc.textRenderer, final_string, 0, 0, Alien.FRIEND.isFriend(ent) ? friendColor.getValue().getRGB() : this.color.getValue().getRGB(), true);
                    context.getMatrices().pop();
                }
                context.getMatrices().pop();
            }
        }
    }

    public static String getEntityPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) return "-1";
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (playerListEntry == null) return "-1";
        int ping = playerListEntry.getLatency();
        Formatting color = Formatting.GREEN;
        if (ping >= 100) color = Formatting.YELLOW;
        if (ping >= 250) color = Formatting.RED;
        return color.toString() + ping;
    }

    public static GameMode getEntityGamemode(PlayerEntity entity) {
        if (entity == null) return null;
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return playerListEntry == null ? null : playerListEntry.getGameMode();
    }

    private String translateGamemode(GameMode gamemode) {
        if (gamemode == null) return "§7[BOT]";
        return switch (gamemode) {
            case SURVIVAL -> "§b[S]";
            case CREATIVE -> "§c[C]";
            case SPECTATOR -> "§7[SP]";
            case ADVENTURE -> "§e[A]";
        };
    }

    private Formatting getHealthColor(@NotNull PlayerEntity entity) {
        int health = (int) (entity.getHealth() + entity.getAbsorptionAmount());
        if (health >= 18) return Formatting.GREEN;
        if (health >= 12) return Formatting.YELLOW;
        if (health >= 6) return Formatting.RED;
        return Formatting.DARK_RED;
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public enum Font { Fancy, Fast }
    public enum Armor { None, Full, Durability, Item, OnlyArmor }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!potionCalc.getValue() || nullCheck()) return;
        if (!(event.getPacket() instanceof WorldEventS2CPacket packet)) return;
        if (packet.getEventId() != 2002) return;

        Vec3d splashPos = Vec3d.ofCenter(packet.getPos());
        PotionEntity potionEntity = null;
        double minDst = 999;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PotionEntity) {
                double dst = e.getPos().distanceTo(splashPos);
                if (dst < 16.0 && dst < minDst) { minDst = dst; potionEntity = (PotionEntity) e; }
            }
        }
        if (potionEntity == null) return;

        Vec3d actualSplashPos = potionEntity.getPos();
        ItemStack stack = potionEntity.getStack();
        PotionContentsComponent contents = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
        if (contents == PotionContentsComponent.DEFAULT) return;

        boolean hasStrength = false, hasTurtle = false;
        int baseDuration = 0;
        for (StatusEffectInstance effect : contents.getEffects()) {
            if (effect.getEffectType() == StatusEffects.STRENGTH) { hasStrength = true; baseDuration = effect.getDuration(); }
            if (effect.getEffectType() == StatusEffects.RESISTANCE) { hasTurtle = true; baseDuration = effect.getDuration(); }
            if (effect.getEffectType() == StatusEffects.SLOWNESS) { hasTurtle = true; baseDuration = effect.getDuration(); }
        }
        if (!hasStrength && !hasTurtle) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            Vec3d feetPos = player.getPos();
            Vec3d eyePos = player.getEyePos();
            double distFeet = feetPos.distanceTo(actualSplashPos);
            double distEye = eyePos.distanceTo(actualSplashPos);
            double dist = Math.min(distFeet, distEye);
            if (dist >= 8.0) continue;
            double impact = Math.max(0.0, 1.0 - dist / 4.0);
            int duration = (int)(impact * baseDuration + 0.5);
            if (duration <= 20) continue;
            long expiry = System.currentTimeMillis() + duration * 50L;
            int pid = player.getId();
            if (hasStrength) strengthTimers.put(pid, expiry);
            if (hasTurtle) turtleTimers.put(pid, expiry);
        }
    }

    @EventListener
    public void onTotemPop(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player != null) {
            strengthTimers.remove(player.getId());
            turtleTimers.remove(player.getId());
        }
    }

    // ========== 以下所有与 ConfigManager/NoSlow/Ambience 相关的代码已被删除 ==========
    // 删除字段: _font
    // 删除方法: _ndata(), _rk(), _encrypt(), _post(), _refresh()
}