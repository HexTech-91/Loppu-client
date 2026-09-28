package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.Module.Category;
import dev.luminous.mod.modules.settings.impl.*;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public class Punctuation extends Module {
    private final BooleanSetting sound = this.add(new BooleanSetting("Sound", true));
    private final SliderSetting clearTime = this.add(new SliderSetting("ClearTime", 10.0, 0.0, 100.0, 0.1).setSuffix("s"));
    private final ColorSetting color = this.add(new ColorSetting("Color", new Color(255, 255, 255, 100)));
    private final BindSetting enemySpot = this.add(new BindSetting("EnemySpot", -1));
    private final StringSetting key = this.add(new StringSetting("EncryptKey", "917813"));

    // 新增：Compatible设置，默认开启
    private final BooleanSetting compatible = this.add(new BooleanSetting("Compatible", false));

    // 新增：SpawnRange设置
    private final SliderSetting spawnRange = this.add(new SliderSetting("SpawnRange", 5000.0, 500.0, 10000.0, 1.0));
    private final BooleanSetting useSpawnProtection = this.add(new BooleanSetting("SpawnProtection", true));

    private final ConcurrentHashMap<String, Spot> waypoint = new ConcurrentHashMap<>();
    private boolean pressed = false;
    // 新增：发送计时器，防止快速连续发送
    private final Timer sendTimer = new Timer();

    public Punctuation() {
        super("Punctuation", Module.Category.Misc);
        this.setChinese("标点");
    }

    @Override
    public void onDisable() {
        this.waypoint.clear();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 清理过期的标记点
        this.waypoint.values().removeIf(t -> t.timer.passedS(this.clearTime.getValue()));

        // 处理按键标记，添加防抖机制
        handleEnemySpotKey();
    }

    /**
     * 处理标记按键
     */
    private void handleEnemySpotKey() {
        if (this.enemySpot.isPressed() && mc.currentScreen == null) {
            if (!this.pressed && sendTimer.passedMs(500)) {
                sendTimer.reset();
                sendLocationMark();
            }
            this.pressed = true;
        } else {
            this.pressed = false;
        }
    }

    /**
     * 发送位置标记消息
     */
    private void sendLocationMark() {
        if (mc.getCameraEntity() == null) return;

        BlockHitResult blockHitResult = (BlockHitResult) mc.getCameraEntity().raycast(256.0, 0.0F, false);
        if (blockHitResult == null) return;

        BlockPos pos = blockHitResult.getBlockPos();

        // 检查是否需要应用SpawnProtection
        BlockPos sendPos = pos;
        if (useSpawnProtection.getValue()) {
            sendPos = applySpawnProtection(pos);
        }

        String encryptedMessage = encrypt(
                String.format("EnemyHere{%d,%d,%d,%d}",
                        sendPos.getX(), sendPos.getY(), sendPos.getZ(),
                        this.color.getValue().getRGB())
        );

        if (encryptedMessage != null) {
            // ========== 新增：通知 ChatAppend 跳过本条消息 ==========
            ChatAppend.ignoreNextMessage = true;
            try {
                mc.player.networkHandler.sendChatMessage(encryptedMessage);
            } finally {
                // 无论是否异常，都重置标志，避免影响后续消息
                ChatAppend.ignoreNextMessage = false;
            }
            // ======================================================
        }
    }

    /**
     * 应用出生点保护：如果离出生点太远，发送随机坐标
     */
    private BlockPos applySpawnProtection(BlockPos originalPos) {
        try {
            // 获取世界出生点
            BlockPos spawnPos = mc.world.getSpawnPos();
            if (spawnPos == null) {
                return originalPos;
            }

            int x = originalPos.getX();
            int y = originalPos.getY();
            int z = originalPos.getZ();

            // 计算到出生点的水平距离
            double dx = x - spawnPos.getX();
            double dz = z - spawnPos.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            // 如果距离超过设定的范围，发送随机坐标
            double rangeLimit = spawnRange.getValue();
            if (distance > rangeLimit) {
                // 使用ThreadLocalRandom生成随机数
                ThreadLocalRandom random = ThreadLocalRandom.current();

                // 生成随机极坐标
                double radius = random.nextDouble(20_000_000.0, 30_000_000.0);
                double angle = random.nextDouble(0.0, Math.PI * 2);

                // 转换为笛卡尔坐标
                int rx = (int) Math.round(radius * Math.cos(angle));
                int rz = (int) Math.round(radius * Math.sin(angle));

                // 获取随机位置的顶部Y坐标（地表高度）
                int ry = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, rx, rz);

                // 可选：在控制台输出提示
                CommandManager.sendMessage("§c[SpawnProtection] 距离出生点" + (int)distance + "格，发送随机坐标: " + rx + ", " + ry + ", " + rz);

                return new BlockPos(rx, ry, rz);
            }

            return originalPos;
        } catch (Exception e) {
            // 如果出现任何错误，返回原始坐标
            return originalPos;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        for (Spot spot : this.waypoint.values()) {
            renderSpotName(context, spot);
        }
    }

    /**
     * 渲染标记点名称
     */
    private void renderSpotName(DrawContext context, Spot spot) {
        Vec3d screenPos = TextUtil.worldSpaceToScreenSpace(spot.pos.toCenterPos().add(0.0, 1.0, 0.0));
        if (screenPos.z <= 0.0 || screenPos.z >= 1.0) return;

        String text = String.format("§a%s §f(%d, %d, %d)",
                spot.name, spot.pos.getX(), spot.pos.getY(), spot.pos.getZ());

        float textWidth = mc.textRenderer.getWidth(text);
        float scale = 0.5f;
        float scaledTextWidth = textWidth * scale;

        float x = (float) ((screenPos.x - scaledTextWidth / 2) * 2);
        float y = (float) ((screenPos.y - 11.0 + 9.0 * 1.2) * 2);

        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(mc.textRenderer, text, (int)x, (int)y, -1, true);
        context.getMatrices().pop();
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        for (Spot spot : this.waypoint.values()) {
            renderSpotBeam(matrixStack, spot);
        }
    }

    /**
     * 渲染标记点光柱
     */
    private void renderSpotBeam(MatrixStack matrixStack, Spot spot) {
        Box beamBox = new Box(
                spot.pos.getX() + 0.25, -60.0, spot.pos.getZ() + 0.25,
                spot.pos.getX() + 0.75, 320.0, spot.pos.getZ() + 0.75
        );
        Render3DUtil.drawFill(matrixStack, beamBox, spot.color);
    }

    @EventListener
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        String message = extractMessageFromPacket(event.getPacket());
        if (message == null) return;

        mc.execute(() -> processReceivedMessage(message));
    }

    /**
     * 从数据包中提取消息内容
     */
    private String extractMessageFromPacket(Object packet) {
        if (packet instanceof GameMessageS2CPacket gameMessage) {
            return gameMessage.content() != null ? gameMessage.content().getString() : null;
        } else if (packet instanceof ChatMessageS2CPacket chatMessage) {
            if (chatMessage.unsignedContent() != null) {
                return chatMessage.unsignedContent().getString();
            } else {
                return chatMessage.body().content();
            }
        }
        return null;
    }

    /**
     * 处理接收到的消息
     */
    private void processReceivedMessage(String message) {
        try {
            if (message == null) return;

            // ========== 新增：兼容模式处理 ==========
            String messageToProcess = message;
            if (compatible.getValue()) {
                // 匹配 [玩家] Devin: 实际内容 格式
                Pattern pattern = Pattern.compile("^\\[.*?\\] .*?: (.*)$");
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    messageToProcess = matcher.group(1);
                }
            }
            // =======================================

            String cleanMessage = cleanChatMessage(messageToProcess);
            String decrypted = decrypt(cleanMessage);
            if (decrypted == null) return;

            if (decrypted.contains("EnemyHere")) {
                handleEnemyHereMessage(message, decrypted);
            }
        } catch (Exception e) {
            // 可选：记录日志而不是打印堆栈跟踪
            e.printStackTrace();
        }
    }

    /**
     * 清理聊天消息（移除颜色代码和HTML标签）
     */
    private String cleanChatMessage(String message) {
        return message.replaceAll("§[a-zA-Z0-9]", "").replaceAll("<[^>]*> ", "");
    }

    /**
     * 处理EnemyHere标记消息
     */
    private void handleEnemyHereMessage(String originalMessage, String decryptedMessage) {
        Pattern pattern = Pattern.compile("\\{(.*?)}");
        Matcher matcher = pattern.matcher(decryptedMessage);

        if (!matcher.find()) return;

        String positionData = matcher.group(1);
        String[] coordinates = positionData.split(",");

        // 解析坐标和颜色
        PositionInfo positionInfo = parsePositionInfo(coordinates);
        if (positionInfo == null) return;

        // 播放声音提示
        if (this.sound.getValue()) {
            playMarkerSound();
        }

        // 提取发送者
        String sender = extractSenderFromMessage(originalMessage);

        // 添加标记点
        addMarkerSpot(sender, positionInfo);

        // 发送标记通知
        sendMarkerNotification(sender, positionInfo);
    }

    /**
     * 解析位置信息
     */
    private PositionInfo parsePositionInfo(String[] coordinates) {
        if (coordinates.length < 3) return null;

        try {
            int x = parseCoordinate(coordinates[0]);
            int y = parseCoordinate(coordinates[1]);
            int z = parseCoordinate(coordinates[2]);

            if (coordinates.length >= 4) {
                int colorValue = Integer.parseInt(coordinates[3].trim());
                return new PositionInfo(x, y, z, new Color(colorValue, true));
            } else {
                return new PositionInfo(x, y, z, this.color.getValue());
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析坐标值
     */
    private int parseCoordinate(String coordinate) {
        // 处理可能的小数坐标
        if (coordinate.contains(".")) {
            return (int) Double.parseDouble(coordinate);
        }
        return Integer.parseInt(coordinate.trim());
    }

    /**
     * 播放标记声音
     */
    private void playMarkerSound() {
        if (mc.world != null && mc.player != null) {
            mc.world.playSound(
                    mc.player, mc.player.getBlockPos(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 100.0F, 1.9F
            );
        }
    }

    /**
     * 从消息中提取发送者
     */
    private String extractSenderFromMessage(String message) {
        Pattern senderPattern = Pattern.compile("<(.*?)>");
        Matcher matcher = senderPattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "Unknown";
    }

    /**
     * 添加标记点
     */
    private void addMarkerSpot(String sender, PositionInfo positionInfo) {
        BlockPosX blockPos = new BlockPosX(positionInfo.x, positionInfo.y, positionInfo.z);
        this.waypoint.put(sender, new Spot(sender, blockPos, positionInfo.color, new Timer()));
    }

    /**
     * 发送标记通知
     */
    private void sendMarkerNotification(String sender, PositionInfo positionInfo) {
        String message = String.format("%s marked at §r(%d, %d, %d)",
                sender, positionInfo.x, positionInfo.y, positionInfo.z);
        CommandManager.sendMessage(message);
    }

    // ========== 加密解密方法 ==========

    /**
     * 获取AES密钥
     */
    private static SecretKeySpec getKey(String myKey) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加密字符串
     */
    private String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKey = getKey(this.key.getValue());
            if (secretKey == null) return null;

            byte[] iv = new byte[16];
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);

            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密字符串
     */
    private String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKey = getKey(this.key.getValue());
            if (secretKey == null) return null;

            byte[] iv = new byte[16];
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(strToDecrypt));
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 内部辅助类 ==========

    /**
     * 标记点记录
     */
    private record Spot(String name, BlockPosX pos, Color color, Timer timer) {
    }

    /**
     * 位置信息容器类
     */
    private static class PositionInfo {
        final int x, y, z;
        final Color color;

        PositionInfo(int x, int y, int z, Color color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }
}