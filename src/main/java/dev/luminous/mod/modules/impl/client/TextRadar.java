package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TextRadar extends Module {
    public static TextRadar INSTANCE;
    private final DecimalFormat df = new DecimalFormat("0.0");

    public enum SortMode {
        Distance,
        Health
    }

    private final EnumSetting<SortMode> sortMode = this.add(new EnumSetting<>("SortMode", SortMode.Distance));
    private final SliderSetting detectionRange = this.add(new SliderSetting("DetectionRange", 256.0, 10.0, 340.0, 1.0));
    private final BooleanSetting font = this.add(new BooleanSetting("Font", false));
    private final BooleanSetting shadow = this.add(new BooleanSetting("Shadow", true));
    private final SliderSetting x = this.add(new SliderSetting("X", 0, 0, 100));
    private final SliderSetting y = this.add(new SliderSetting("Y", 15, 0, 100));
    private final ColorSetting color = this.add(new ColorSetting("Color", new Color(255, 255, 255)));
    private final ColorSetting friend = this.add(new ColorSetting("Friend").injectBoolean(true));
    private final BooleanSetting doubleBlank = this.add(new BooleanSetting("Double", false));
    private final BooleanSetting health = this.add(new BooleanSetting("Health", true));
    private final BooleanSetting pops = this.add(new BooleanSetting("Pops", true));
    public final BooleanSetting red = this.add(new BooleanSetting("Red", false));
    private final BooleanSetting distance = this.add(new BooleanSetting("Distance", true));
    private final BooleanSetting effects = this.add(new BooleanSetting("Effects", true));
    private final BooleanSetting turtle = this.add(new BooleanSetting("Turtle", true));
    private final ColorSetting turtleColor = this.add(new ColorSetting("TurtleColor", new Color(85, 85, 255), () -> turtle.getValue()));
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));
    private final Map<Integer, Long> turtlePlayers = new ConcurrentHashMap<>();

    public TextRadar() {
        super("TextRadar", Module.Category.Client);
        this.setChinese("文字雷达");
        INSTANCE = this;
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!turtle.getValue() || mc.world == null) return;

        if (event.getPacket() instanceof WorldEventS2CPacket packet) {
            int eventId = packet.getEventId();
            Vec3d splashPos = Vec3d.ofCenter(packet.getPos());

            if (debug.getValue()) {
                System.out.println("[TextRadar Debug] 收到WorldEventS2CPacket，事件ID: " + eventId + ", 位置: " + splashPos);
                mc.execute(() -> sendMessage("§e[TextRadar Debug] WorldEvent ID: " + eventId + " Pos: " + splashPos));
            }

            if (eventId == 2002) {
                if (debug.getValue()) {
                    System.out.println("[TextRadar Debug] 检测到药水爆炸事件");
                    mc.execute(() -> sendMessage("§a[TextRadar Debug] 检测到药水爆炸事件!"));
                }

                PotionEntity potionEntity = null;
                double minDst = Double.MAX_VALUE;

                for (Entity e : mc.world.getEntities()) {
                    if (e instanceof PotionEntity) {
                        double dst = e.getPos().distanceTo(splashPos);
                        if (debug.getValue()) {
                            System.out.println("[TextRadar Debug] 找到药水实体，距离: " + dst);
                        }
                        if (dst < detectionRange.getValue() && dst < minDst) {
                            minDst = dst;
                            potionEntity = (PotionEntity) e;
                        }
                    }
                }

                if (potionEntity == null) {
                    if (debug.getValue()) {
                        System.out.println("[TextRadar Debug] 未找到附近的药水实体，返回");
                        mc.execute(() -> sendMessage("§c[TextRadar Debug] 未找到药水实体"));
                    }
                    return;
                }

                Vec3d actualSplashPos = potionEntity.getPos();
                if (debug.getValue()) {
                    final double finalMinDst = minDst;
                    System.out.println("[TextRadar Debug] 找到最近的药水实体，距离: " + finalMinDst);
                    System.out.println("[TextRadar Debug] 药水实体实际位置: " + actualSplashPos);
                    mc.execute(() -> sendMessage("§a[TextRadar Debug] 找到药水实体，距离: " + finalMinDst));
                }

                ItemStack stack = potionEntity.getStack();
                PotionContentsComponent contents = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);

                if (debug.getValue()) {
                    String itemName = stack.getItem().getName(stack).getString();
                    System.out.println("[TextRadar Debug] 药水物品: " + itemName);
                    mc.execute(() -> sendMessage("§b[TextRadar Debug] 药水物品: " + itemName));
                }

                boolean hasResistance = false;
                boolean hasSlowness = false;
                int baseDuration = 0;

                if (debug.getValue()) {
                    System.out.println("[TextRadar Debug] 开始检查药水效果...");
                    mc.execute(() -> sendMessage("§d[TextRadar Debug] 开始检查药水效果..."));
                }

                for (var effect : contents.getEffects()) {
                    String effectName = effect.getEffectType().value().getName().getString();
                    int amp = effect.getAmplifier() + 1;
                    int dur = effect.getDuration();
                    if (debug.getValue()) {
                        System.out.println("[TextRadar Debug] 找到效果: " + effectName + ", 等级: " + amp + ", 持续时间: " + dur + "tick");
                        mc.execute(() -> sendMessage("§f[TextRadar Debug] 效果: " + effectName + " Lv." + amp + " " + dur + "tick"));
                    }
                    if (effect.getEffectType() == StatusEffects.RESISTANCE) {
                        hasResistance = true;
                        baseDuration = dur;
                    } else if (effect.getEffectType() == StatusEffects.SLOWNESS) {
                        hasSlowness = true;
                    }
                }

                if (!hasResistance || !hasSlowness) {
                    if (debug.getValue()) {
                        final boolean finalHasResistance = hasResistance;
                        final boolean finalHasSlowness = hasSlowness;
                        System.out.println("[TextRadar Debug] 不是神龟药水，返回 (hasResistance: " + finalHasResistance + ", hasSlowness: " + finalHasSlowness + ")");
                        mc.execute(() -> sendMessage("§c[TextRadar Debug] 不是神龟药水 (R:" + finalHasResistance + " S:" + finalHasSlowness + ")"));
                    }
                    return;
                }

                if (debug.getValue()) {
                    System.out.println("[TextRadar Debug] 确认是神龟药水");
                    mc.execute(() -> sendMessage("§a[TextRadar Debug] 确认是神龟药水!"));
                }

                // 第一次打印基础持续时间
                if (debug.getValue()) {
                    final int finalBaseDuration = baseDuration;
                    System.out.println("[TextRadar Debug] 基础持续时间: " + finalBaseDuration + "tick");
                    mc.execute(() -> sendMessage("§b[TextRadar Debug] 基础持续时间: " + finalBaseDuration + "tick"));
                }

                for (PlayerEntity player : mc.world.getPlayers()) {
                    Vec3d feetPos = player.getPos();
                    Vec3d eyePos = player.getEyePos();
                    double distFeet = feetPos.distanceTo(actualSplashPos);
                    double distEye = eyePos.distanceTo(actualSplashPos);
                    double dist = Math.min(distFeet, distEye);
                    if (debug.getValue()) {
                        System.out.println("[TextRadar Debug] 玩家: " + player.getName().getString() + ", 脚部距离: " + distFeet + ", 头部距离: " + distEye + ", 有效距离: " + dist);
                    }
                    if (dist < 4.0) {
                        double impact = 1.0 - (dist / 3.999);
                        if (debug.getValue()) {
                            System.out.println("[TextRadar Debug] 玩家: " + player.getName().getString() + ", 影响因子: " + impact);
                        }
                        if (impact > 0.0) {
                            int duration = (int) ((impact * baseDuration) + 0.5);
                            if (debug.getValue()) {
                                System.out.println("[TextRadar Debug] 玩家: " + player.getName().getString() + ", 计算持续时间: " + duration + "tick");
                            }
                            if (duration > 20) {
                                long expiryTime = System.currentTimeMillis() + (duration * 50L);
                                turtlePlayers.put(player.getId(), expiryTime);
                                if (debug.getValue()) {
                                    final PlayerEntity finalPlayer = player;
                                    final int finalDuration = duration;
                                    System.out.println("[TextRadar Debug] 记录玩家龟壳效果: " + finalPlayer.getName().getString() + ", 过期时间: " + expiryTime);
                                    mc.execute(() -> sendMessage("§a[TextRadar Debug] 记录龟壳效果: " + finalPlayer.getName().getString() + " " + (finalDuration/20) + "s"));
                                }
                            }
                        }
                    }
                }

                // 第二次打印基础持续时间（如果存在，已使用 final 副本）
                if (debug.getValue()) {
                    final int finalBaseDuration2 = baseDuration;
                    System.out.println("[TextRadar Debug] 基础持续时间: " + finalBaseDuration2 + "tick");
                    mc.execute(() -> sendMessage("§b[TextRadar Debug] 基础持续时间: " + finalBaseDuration2 + "tick"));
                }

                if (debug.getValue()) {
                    System.out.println("[TextRadar Debug] 处理完成");
                    mc.execute(() -> sendMessage("§a[TextRadar Debug] 处理完成"));
                }
            }
        }
    }

    @EventListener
    public void onTotemPop(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player != null) {
            turtlePlayers.remove(player.getId());
        }
    }

    @EventListener
    public void onGameLeft(GameLeftEvent event) {
        turtlePlayers.clear();
    }

    @Override
    public void onRender2D(DrawContext drawContext, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        List<AbstractClientPlayerEntity> players = new ArrayList<>(mc.world.getPlayers());

        switch (sortMode.getValue()) {
            case Distance:
                players.sort(Comparator.comparingDouble(playerx -> mc.player.distanceTo(playerx)));
                break;
            case Health:
                players.sort(Comparator.comparingDouble(playerx -> playerx.getHealth() + playerx.getAbsorptionAmount()));
                break;
        }

        // 百分比定位：X/Y 为屏幕宽度/高度的百分比（与 BackPackHUD 一致）
        int currentX = (int) (mc.getWindow().getScaledWidth() * (this.x.getValue() / 100.0));
        int currentY = (int) (mc.getWindow().getScaledHeight() * (this.y.getValue() / 100.0));

        for (PlayerEntity player : players) {
            if (player == mc.player) continue;

            // 计算神龟效果的有效性和剩余秒数（向上取整）
            boolean hasTurtleEffect = false;
            int turtleSeconds = 0;
            if (turtle.getValue() && turtlePlayers.containsKey(player.getId())) {
                long expiry = turtlePlayers.get(player.getId());
                long remaining = expiry - System.currentTimeMillis();
                turtleSeconds = (int) Math.ceil(remaining / 1000.0); // 向上取整秒数
                if (turtleSeconds > 0) {
                    hasTurtleEffect = true;
                } else {
                    // 效果已过期，移除记录
                    turtlePlayers.remove(player.getId());
                }
            }

            StringBuilder stringBuilder = new StringBuilder();
            String blank = this.doubleBlank.getValue() ? "  " : " ";

            if (this.health.getValue()) {
                stringBuilder.append(getHealthColor(player));
                stringBuilder.append(this.df.format(player.getHealth() + player.getAbsorptionAmount()));
                stringBuilder.append(blank);
            }

            stringBuilder.append(Formatting.RESET);
            stringBuilder.append(player.getName().getString());

            if (this.distance.getValue()) {
                stringBuilder.append(blank);
                stringBuilder.append(Formatting.WHITE);
                stringBuilder.append(this.df.format(mc.player.distanceTo(player)));
                stringBuilder.append("m");
            }

            if (this.effects.getValue()) {
                if (player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(Formatting.GRAY);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getStatusEffect(StatusEffects.SLOWNESS).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasStatusEffect(StatusEffects.SPEED)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(Formatting.AQUA);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getStatusEffect(StatusEffects.SPEED).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasStatusEffect(StatusEffects.STRENGTH)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(Formatting.DARK_RED);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getStatusEffect(StatusEffects.STRENGTH).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getStatusEffect(StatusEffects.STRENGTH).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(Formatting.BLUE);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getStatusEffect(StatusEffects.RESISTANCE).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
            }

            if (this.pops.getValue()) {
                int totemPopped = Alien.POP.getPop(player);
                if (totemPopped > 0) {
                    stringBuilder.append(blank);
                    stringBuilder.append(getPopColor(totemPopped));
                    stringBuilder.append("-");
                    stringBuilder.append(totemPopped);
                }
            }

            boolean isFriend = Alien.FRIEND.isFriend(player);
            int color;
            if (isFriend && this.friend.booleanValue) {
                color = this.friend.getValue().getRGB();
            } else if (hasTurtleEffect && this.turtle.getValue()) {
                color = this.turtleColor.getValue().getRGB();
            } else {
                color = this.color.getValue().getRGB();
            }

            // 绘制玩家信息文本
            if (this.font.getValue()) {
                FontManager.ui.drawString(drawContext.getMatrices(), stringBuilder.toString(),
                        currentX, currentY, color, this.shadow.getValue());
            } else {
                drawContext.drawText(mc.textRenderer, stringBuilder.toString(),
                        currentX, currentY, color, this.shadow.getValue());
            }

            // 绘制神龟图标和剩余时间
            if (hasTurtleEffect && this.turtle.getValue()) {
                int textWidth;
                if (this.font.getValue()) {
                    textWidth = (int) FontManager.ui.getWidth(stringBuilder.toString());
                } else {
                    textWidth = mc.textRenderer.getWidth(stringBuilder.toString());
                }
                int drawX = currentX + textWidth + 2;
                int drawY = currentY;

                StatusEffectSpriteManager spriteManager = mc.getStatusEffectSpriteManager();
                Sprite sprite = spriteManager.getSprite(StatusEffects.RESISTANCE);
                drawContext.drawSprite(drawX, drawY, 0, 8, 8, sprite);

                String turtleText = "Lv.4 " + turtleSeconds + "s";
                if (this.font.getValue()) {
                    FontManager.ui.drawString(drawContext.getMatrices(), turtleText,
                            drawX + 10, drawY, Formatting.BLUE.getColorValue(), this.shadow.getValue());
                } else {
                    drawContext.drawText(mc.textRenderer, turtleText,
                            drawX + 10, drawY, Formatting.BLUE.getColorValue(), this.shadow.getValue());
                }
            }

            // 更新 Y 坐标
            currentY += this.font.getValue() ? (int) FontManager.ui.getFontHeight() : 9;
        }
    }

    public static Formatting getHealthColor(PlayerEntity player) {
        double health = player.getHealth() + player.getAbsorptionAmount();
        if (health > 18.0) return Formatting.GREEN;
        if (health > 16.0) return Formatting.DARK_GREEN;
        if (health > 12.0) return Formatting.YELLOW;
        if (health > 8.0) return Formatting.GOLD;
        if (health > 4.0) return Formatting.RED;
        return Formatting.DARK_RED;
    }

    public static Formatting getPopColor(int totems) {
        if (INSTANCE.red.getValue()) return Formatting.DARK_RED;
        if (totems > 10) return Formatting.DARK_RED;
        if (totems > 8) return Formatting.DARK_RED;
        if (totems > 6) return Formatting.DARK_RED;
        if (totems > 4) return Formatting.DARK_RED;
        if (totems > 2) return Formatting.DARK_RED;
        return Formatting.DARK_RED;
    }

    public static int getTurtleSeconds(PlayerEntity player) {
        if (INSTANCE == null || !INSTANCE.turtle.getValue()) {
            return 0;
        }
        
        if (INSTANCE.turtlePlayers.containsKey(player.getId())) {
            long expiry = INSTANCE.turtlePlayers.get(player.getId());
            long remaining = expiry - System.currentTimeMillis();
            int seconds = (int) Math.ceil(remaining / 1000.0);
            if (seconds > 0) {
                return seconds;
            } else {
                INSTANCE.turtlePlayers.remove(player.getId());
            }
        }
        return 0;
    }

    public static boolean hasTurtleEffect(PlayerEntity player) {
        return getTurtleSeconds(player) > 0;
    }
}