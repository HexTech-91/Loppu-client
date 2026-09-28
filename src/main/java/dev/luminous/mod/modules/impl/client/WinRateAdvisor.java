package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.Module;

import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WinRateAdvisor extends Module {
    public static WinRateAdvisor INSTANCE;
    public final BooleanSetting background = this.add(new BooleanSetting("Background", true));
    public final ColorSetting bgColor = this.add(new ColorSetting("BgColor", new Color(0, 0, 0, 180)));
    public final ColorSetting highWinRateColor = this.add(new ColorSetting("HighWinRateColor", new Color(85, 255, 85)));
    public final ColorSetting mediumWinRateColor = this.add(new ColorSetting("MediumWinRateColor", new Color(255, 255, 85)));
    public final ColorSetting lowWinRateColor = this.add(new ColorSetting("LowWinRateColor", new Color(255, 85, 85)));
    public final ColorSetting textColor = this.add(new ColorSetting("TextColor", new Color(255, 255, 255)));
    public final SliderSetting xOffset = this.add(new SliderSetting("XOffset", 0.0, -300.0, 300.0, 1.0));
    public final SliderSetting yOffset = this.add(new SliderSetting("YOffset", 100.0, 0.0, 500.0, 1.0));
    public final SliderSetting scale = this.add(new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
    public final SliderSetting rounding = this.add(new SliderSetting("Rounding", 10.0, 0.0, 30.0, 0.5));
    public final BooleanSetting shadow = this.add(new BooleanSetting("Shadow", true));
    public final SliderSetting checkRange = this.add(new SliderSetting("CheckRange", 30.0, 5.0, 100.0, 1.0));
    public final SliderSetting healthWeight = this.add(new SliderSetting("HealthWeight", 0.3, 0.0, 1.0, 0.05));
    public final SliderSetting armorWeight = this.add(new SliderSetting("ArmorWeight", 0.25, 0.0, 1.0, 0.05));
    public final SliderSetting distanceWeight = this.add(new SliderSetting("DistanceWeight", 0.2, 0.0, 1.0, 0.05));
    public final SliderSetting countWeight = this.add(new SliderSetting("CountWeight", 0.25, 0.0, 1.0, 0.05));
    public final SliderSetting strafingWeight = this.add(new SliderSetting("StrafingWeight", 0.15, 0.0, 1.0, 0.05));
    public final SliderSetting burrowWeight = this.add(new SliderSetting("BurrowWeight", 0.2, 0.0, 1.0, 0.05));
    public final SliderSetting gapWeight = this.add(new SliderSetting("GapWeight", 0.25, 0.0, 1.0, 0.05));
    public final SliderSetting chasingWeight = this.add(new SliderSetting("ChasingWeight", 0.15, 0.0, 1.0, 0.05));
    private boolean playerInBurrow = false;
    private boolean playerStrafing = false;
    private boolean playerChasing = false;
    private boolean inCombat = false;
    private Map<String, Integer> enemyPops = new HashMap();
    private String lastPoppedEnemy = "";
    private long lastPopTime = 0L;
    private static Set<String> friends = new HashSet();
    private float lastWinRate = 0.5F;
    private String lastAdvice = "等待数据...";
    private long lastUpdateTime = 0L;
    private final long UPDATE_INTERVAL = 2000L;

    public WinRateAdvisor() {
        super("WinRateAdvisor", Module.Category.Client);
        this.setChinese("胜率建议");
        INSTANCE = this;
    }

    @EventListener
    public void onTotemPop(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player != null && player != mc.player) {
            String playerName = player.getName().getString();
            if (!this.isFriend(playerName)) {
                this.enemyPops.put(playerName, (Integer)this.enemyPops.getOrDefault(playerName, 0) + 1);
                this.lastPoppedEnemy = playerName;
                this.lastPopTime = System.currentTimeMillis();
                this.updateAnalysis();
            }
        }
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (!nullCheck()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - this.lastUpdateTime > 2000L) {
                this.updateAnalysis();
                this.lastUpdateTime = currentTime;
            }

            DrawContext context = event.drawContext;
            float scaledWidth = mc.getWindow().getScaledWidth();
            float scaledHeight = mc.getWindow().getScaledHeight();
            float x = scaledWidth / 2.0F + this.xOffset.getValueFloat();
            float y = this.yOffset.getValueFloat();
            float scaleValue = this.scale.getValueFloat();
            float padding = 6.0F * scaleValue;
            float spacing = 5.0F * scaleValue;
            float lineHeight = 10.0F * scaleValue;
            String winRateText = String.format("胜率: %.1f%%", this.lastWinRate * 100.0F);
            String fullText = "胜率分析 " + winRateText + " | 建议: " + this.lastAdvice;
            float textWidth = mc.textRenderer.getWidth(fullText) * scaleValue;
            float totalWidth = textWidth + padding * 2.0F;
            float totalHeight = lineHeight + padding * 2.0F;
            if (this.background.getValue()) {
                float halfWidth = totalWidth / 2.0F;
                float radius = this.rounding.getValueFloat();
                Render2DUtil.drawRoundedRect(context.getMatrices(), x - halfWidth, y, totalWidth, radius * 2.0F, radius, this.bgColor.getValue());
                float rectHeight = totalHeight - radius;
                if (rectHeight > 0.0F) {
                    Render2DUtil.drawRect(context.getMatrices(), x - halfWidth, y + radius, totalWidth, rectHeight, this.bgColor.getValue());
                }
            }

            float textX = x - mc.textRenderer.getWidth(fullText) * scaleValue / 2.0F;
            float textY = y + padding;
            int color = this.textColor.getValue().getRGB();
            if (this.lastWinRate >= 0.7F) {
                color = this.highWinRateColor.getValue().getRGB();
            } else if (this.lastWinRate >= 0.4F) {
                color = this.mediumWinRateColor.getValue().getRGB();
            } else {
                color = this.lowWinRateColor.getValue().getRGB();
            }

            context.drawText(mc.textRenderer, fullText, (int)textX, (int)textY, color, this.shadow.getValue());
            if (this.background.getValue()) {
                float halfWidth = totalWidth / 2.0F;
                float lineY = y + totalHeight - 2.0F;
                float gradientHeight = 2.0F;
                Color startColor;
                Color endColor;
                if (this.lastWinRate >= 0.7F) {
                    startColor = new Color(0, 255, 255, 255);
                    endColor = new Color(138, 43, 226, 255);
                } else if (this.lastWinRate >= 0.4F) {
                    startColor = new Color(30, 144, 255, 255);
                    endColor = new Color(0, 255, 255, 255);
                } else {
                    startColor = new Color(255, 0, 0, 255);
                    endColor = new Color(255, 165, 0, 255);
                }

                float time = (float)System.currentTimeMillis() / 1000.0F;
                float offset = (float)Math.sin(time * 2.0F) * 0.5F + 0.5F;
                Color animStartColor = new Color(
                        (int)(startColor.getRed() + (endColor.getRed() - startColor.getRed()) * offset),
                        (int)(startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * offset),
                        (int)(startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * offset),
                        255
                );
                Color animEndColor = new Color(
                        (int)(endColor.getRed() + (startColor.getRed() - endColor.getRed()) * offset),
                        (int)(endColor.getGreen() + (startColor.getGreen() - endColor.getGreen()) * offset),
                        (int)(endColor.getBlue() + (startColor.getBlue() - endColor.getBlue()) * offset),
                        255
                );
                Render2DUtil.horizontalGradient(context.getMatrices(), x - halfWidth, lineY, x + halfWidth, lineY + gradientHeight, animStartColor, animEndColor);
            }
        }
    }

    private void updateAnalysis() {
        if (mc.player != null && mc.world != null) {
            this.updatePlayerStatus();
            float playerScore = this.calculatePlayerScore(mc.player);
            float totalEnemyScore = 0.0F;
            int enemyCount = 0;
            int enemiesInBurrow = 0;
            int enemiesStrafing = 0;
            double range = this.checkRange.getValue();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity && entity != mc.player) {
                    double distance = mc.player.squaredDistanceTo(entity);
                    if (distance <= range * range) {
                        PlayerEntity enemy = (PlayerEntity)entity;
                        String playerName = enemy.getName().getString();
                        if (!this.isFriend(playerName)) {
                            totalEnemyScore += this.calculatePlayerScore(enemy);
                            enemyCount++;
                            if (this.isInBurrow(enemy)) {
                                enemiesInBurrow++;
                            }

                            if (this.isStrafing(enemy)) {
                                enemiesStrafing++;
                            }
                        }
                    }
                }
            }

            if (enemyCount > 0) {
                float avgEnemyScore = totalEnemyScore / enemyCount;
                this.lastWinRate = this.calculateWinRate(playerScore, avgEnemyScore, enemyCount);
                this.lastAdvice = this.generateDetailedAdvice(playerScore, avgEnemyScore, enemyCount, enemiesInBurrow, enemiesStrafing);
            } else {
                this.lastWinRate = 1.0F;
                this.lastAdvice = "附近无敌人";
            }
        } else {
            this.lastWinRate = 0.5F;
            this.lastAdvice = "等待数据...";
        }
    }

    private float calculatePlayerScore(PlayerEntity player) {
        float healthScore = player.getHealth() / player.getMaxHealth() * this.healthWeight.getValueFloat();
        float armorScore = 0.0F;
        if (player.getArmor() > 0) {
            armorScore = player.getArmor() / 20.0F * this.armorWeight.getValueFloat();
        }

        float burrowScore = 0.0F;
        if (player == mc.player && this.playerInBurrow) {
            burrowScore = 0.3F * this.burrowWeight.getValueFloat();
        }

        float strafingScore = 0.0F;
        if (player == mc.player && this.playerStrafing) {
            strafingScore = 0.2F * this.strafingWeight.getValueFloat();
        }

        float chasingScore = 0.0F;
        if (player == mc.player && this.playerChasing) {
            chasingScore = 0.15F * this.chasingWeight.getValueFloat();
        }

        return healthScore + armorScore + burrowScore + strafingScore + chasingScore;
    }

    private float calculateWinRate(float playerScore, float avgEnemyScore, int enemyCount) {
        float baseWinRate = playerScore / (playerScore + avgEnemyScore);
        float countFactor = 1.0F - (enemyCount - 1) * 0.1F * this.countWeight.getValueFloat();
        countFactor = Math.max(0.1F, countFactor);
        float finalWinRate = baseWinRate * countFactor;
        return Math.max(0.0F, Math.min(1.0F, finalWinRate));
    }

    private String generateDetailedAdvice(float playerScore, float avgEnemyScore, int enemyCount, int enemiesInBurrow, int enemiesStrafing) {
        if (enemyCount == 0) {
            return "附近无敌人";
        } else {
            long timeSincePop = System.currentTimeMillis() - this.lastPopTime;
            if (timeSincePop < 3000L && !this.lastPoppedEnemy.isEmpty()) {
                int pops = (Integer)this.enemyPops.getOrDefault(this.lastPoppedEnemy, 0);
                if (pops >= 8) {
                    return this.lastPoppedEnemy + " 爆了" + pops + "个图腾，立即终结他！";
                } else if (pops >= 5) {
                    return this.lastPoppedEnemy + " 爆了" + pops + "个图腾，建议立即追击！";
                } else {
                    return pops >= 3 ? this.lastPoppedEnemy + " 爆了" + pops + "个图腾，建议继续追击！" : this.lastPoppedEnemy + " 爆了图腾，建议追击！";
                }
            } else if (!this.playerInBurrow && enemiesInBurrow > 0) {
                if (enemiesInBurrow >= 3) {
                    return "多个敌人在Burrow中，建议使用爆破或等待";
                } else {
                    return enemiesInBurrow >= 2 ? "多个敌人在Burrow中，建议使用Burrow" : "建议使用Burrow保护自己";
                }
            } else {
                if (this.inCombat) {
                    if (this.playerInBurrow) {
                        if (enemiesInBurrow > 0) {
                            if (this.lastWinRate >= 0.7) {
                                return "地道战中占据优势，尝试突破敌人防线";
                            }

                            if (this.lastWinRate >= 0.5) {
                                return "地道战中势均力敌，保持距离并寻找破绽";
                            }

                            return "地道战中处于劣势，准备撤退或寻找支援";
                        }

                        if (this.lastWinRate >= 0.7) {
                            return "地道战中占据优势，引诱敌人进入地道";
                        }

                        if (this.lastWinRate >= 0.5) {
                            return "地道战中势均力敌，保持警惕并准备反击";
                        }

                        return "地道战中处于劣势，尽快离开地道寻找机会";
                    }

                    if (enemiesInBurrow > 0) {
                        if (enemiesInBurrow >= 3) {
                            return "多个敌人在地道中，建议使用爆破或等待时机";
                        }

                        if (enemiesInBurrow >= 2) {
                            return "两个敌人在地道中，尝试分割或使用范围攻击";
                        }

                        return "敌人在地道中，尝试引诱出来或使用陷阱";
                    }
                }

                if (!this.playerStrafing && enemiesStrafing > 0) {
                    if (enemiesStrafing >= 3) {
                        return "多个敌人在走位，建议使用范围攻击或陷阱";
                    } else {
                        return enemiesStrafing >= 2 ? "多个敌人在走位，建议使用精准攻击或陷阱" : "建议使用走位躲避攻击";
                    }
                } else if (!this.playerChasing && this.lastWinRate >= 0.6F) {
                    return "建议追击敌人";
                } else if (this.lastWinRate >= 0.95F) {
                    return "绝对碾压，立即终结战斗！";
                } else if (this.lastWinRate >= 0.9F) {
                    return "绝对优势，全力进攻！";
                } else if (this.lastWinRate >= 0.85F) {
                    return "优势巨大，主动出击";
                } else if (this.lastWinRate >= 0.8F) {
                    return "优势明显，可以进攻";
                } else if (this.lastWinRate >= 0.75F) {
                    return "优势较大，保持节奏";
                } else if (this.lastWinRate >= 0.7F) {
                    return "优势较大，保持压力";
                } else if (this.lastWinRate >= 0.65F) {
                    return "有一定优势，稳步推进";
                } else if (this.lastWinRate >= 0.6F) {
                    return "有一定优势，谨慎进攻";
                } else if (this.lastWinRate >= 0.55F) {
                    return "微弱优势，把握时机";
                } else if (this.lastWinRate >= 0.5F) {
                    return "势均力敌，寻找破绽";
                } else if (this.lastWinRate >= 0.45F) {
                    return "势均力敌，保持耐心";
                } else if (this.lastWinRate >= 0.4F) {
                    return "势均力敌，寻找机会";
                } else if (this.lastWinRate >= 0.35F) {
                    return "略有劣势，调整战术";
                } else if (this.lastWinRate >= 0.3F) {
                    return "略有劣势，保持距离";
                } else if (this.lastWinRate >= 0.25F) {
                    return "处于劣势，寻找支援";
                } else if (this.lastWinRate >= 0.2F) {
                    return "处于劣势，考虑撤退";
                } else {
                    return this.lastWinRate >= 0.15F ? "劣势明显，避免正面冲突" : "严重劣势，立即撤退";
                }
            }
        }
    }

    private void updatePlayerStatus() {
        if (mc.player != null) {
            this.playerInBurrow = this.isInBurrow(mc.player);
            this.playerStrafing = this.isStrafing(mc.player);
            this.playerChasing = this.isChasing();
            this.inCombat = this.isInCombat();
        }
    }

    private boolean isInCombat() {
        if (mc.player != null && mc.world != null) {
            if (mc.player.getHealth() < mc.player.getMaxHealth()) {
                return true;
            } else if (mc.player.isUsingItem()) {
                return true;
            } else if (mc.player.getAttackCooldownProgress(0.0F) < 1.0F) {
                return true;
            } else {
                double range = this.checkRange.getValue();

                for (Entity entity : mc.world.getEntities()) {
                    if (entity instanceof PlayerEntity && entity != mc.player) {
                        double distance = mc.player.squaredDistanceTo(entity);
                        if (distance <= range * range) {
                            PlayerEntity enemy = (PlayerEntity)entity;
                            if (enemy.getHealth() < enemy.getMaxHealth()) {
                                return true;
                            }

                            if (enemy.isUsingItem()) {
                                return true;
                            }

                            if (enemy.getAttackCooldownProgress(0.0F) < 1.0F) {
                                return true;
                            }

                            if (Math.abs(enemy.getVelocity().x) > 0.05 || Math.abs(enemy.getVelocity().z) > 0.05) {
                                return true;
                            }
                        }
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isInBurrow(PlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        BlockPos headPos = player.getBlockPos().up();
        boolean feetBurrowed = !mc.world.getBlockState(playerPos).isAir() && !mc.world.getBlockState(playerPos).isReplaceable();
        boolean headBurrowed = !mc.world.getBlockState(headPos).isAir() && !mc.world.getBlockState(headPos).isReplaceable();
        return feetBurrowed || headBurrowed;
    }

    private boolean isStrafing(PlayerEntity player) {
        double sidewaysMovement = Math.abs(player.getVelocity().x) + Math.abs(player.getVelocity().z);
        return sidewaysMovement > 0.15 && !player.isSprinting();
    }

    private boolean isChasing() {
        if (mc.player != null && mc.world != null) {
            double range = this.checkRange.getValue();
            PlayerEntity nearestEnemy = null;
            double nearestDistance = range * range;

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity && entity != mc.player) {
                    double distance = mc.player.squaredDistanceTo(entity);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestEnemy = (PlayerEntity)entity;
                    }
                }
            }

            if (nearestEnemy == null) {
                return false;
            } else {
                Vec3d playerLook = mc.player.getRotationVec(1.0F);
                Vec3d toEnemy = nearestEnemy.getPos().subtract(mc.player.getPos()).normalize();
                double dotProduct = playerLook.dotProduct(toEnemy);
                boolean isMoving = Math.abs(mc.player.getVelocity().x) > 0.05 || Math.abs(mc.player.getVelocity().z) > 0.05;
                return isMoving && dotProduct > 0.7;
            }
        } else {
            return false;
        }
    }

    private boolean isFriend(String playerName) {
        try {
            if (Alien.FRIEND != null) {
                return Alien.FRIEND.isFriend(playerName);
            }
        } catch (Exception var3) {
        }

        return friends.contains(playerName);
    }

    public void addFriend(String playerName) {
        friends.add(playerName);
    }

    public void removeFriend(String playerName) {
        friends.remove(playerName);
    }

    public Set<String> getFriends() {
        return new HashSet(friends);
    }

    static {
        try {
            if (Alien.FRIEND != null) {
                friends.addAll(Alien.FRIEND.friendList);
            }
        } catch (Exception var1) {
        }
    }
}
