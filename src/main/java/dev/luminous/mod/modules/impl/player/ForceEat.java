package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

public class ForceEat extends Module {
    public static ForceEat INSTANCE;

    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Both));
    private final BooleanSetting autoSwitch = this.add(new BooleanSetting("AutoSwitch", true));
    private final BooleanSetting noInteract = this.add(new BooleanSetting("NoInteract", true));
    private final BooleanSetting noMove = this.add(new BooleanSetting("NoMove", false));

    private boolean isEating = false;
    private int eatTicks = 0;
    private int prevSlot = -1;
    private int switchBackTimer = 0;   // 吃完后延迟切换计时器
    private int startDelayTimer = 0;   // 切换后延迟开始吃计时器

    public ForceEat() {
        super("ForceEat", Category.Player);
        this.setChinese("主手进食");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        isEating = false;
        eatTicks = 0;
        prevSlot = -1;
        switchBackTimer = 0;
        startDelayTimer = 0;
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        if (isEating) {
            mc.options.useKey.setPressed(false);
            if (prevSlot != -1 && prevSlot < 9 && autoSwitch.getValue()) {
                InventoryUtil.switchToSlot(prevSlot);
            }
        }
        isEating = false;
        eatTicks = 0;
        switchBackTimer = 0;
        startDelayTimer = 0;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        // 吃完后延迟切换回原槽位
        if (switchBackTimer > 0) {
            switchBackTimer--;
            if (switchBackTimer == 0 && prevSlot != -1 && prevSlot < 9 && autoSwitch.getValue()) {
                InventoryUtil.switchToSlot(prevSlot);
                prevSlot = -1;
            }
        }

        // 启动前延迟（切换槽位后等待1 tick再开始吃）
        if (startDelayTimer > 0) {
            startDelayTimer--;
            if (startDelayTimer == 0) {
                startForceEating();
                mc.options.useKey.setPressed(true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }

        // 正在吃，继续
        if (isEating) {
            continueEating();
            return;
        }

        // 未进食时尝试自动启动
        if (!mc.player.isUsingItem()) {
            int appleSlot = findAppleSlot();
            if (appleSlot != -1 && startDelayTimer == 0) {
                prevSlot = mc.player.getInventory().selectedSlot;
                if (autoSwitch.getValue()) {
                    InventoryUtil.switchToSlot(appleSlot);
                }
                // 延迟1 tick后再开始吃
                startDelayTimer = 1;
            }
        } else {
            // 手动吃苹果时直接进入状态（无延迟）
            ItemStack stack = mc.player.getMainHandStack();
            boolean isApple = switch (mode.getValue()) {
                case GoldenApple -> stack.getItem() == Items.GOLDEN_APPLE;
                case EnchantedGoldenApple -> stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
                case Both -> stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
            };
            if (isApple && !isEating) {
                startForceEating();
                mc.options.useKey.setPressed(true);
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || !isEating) return;
        mc.options.useKey.setPressed(true);
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || !isEating) return;

        Packet<?> packet = event.getPacket();
        if (noInteract.getValue()) {
            if (packet instanceof PlayerInteractItemC2SPacket interactPacket && interactPacket.getHand() != Hand.MAIN_HAND) {
                event.cancel();
            }
            if (packet instanceof PlayerActionC2SPacket) {
                event.cancel();
            }
        }

        if (noMove.getValue() && packet instanceof PlayerMoveC2SPacket movePacket
                && (movePacket.changesPosition() || movePacket.changesLook())
                && (Math.abs(movePacket.getX(0.0) - mc.player.getX()) > 0.5
                || Math.abs(movePacket.getY(0.0) - mc.player.getY()) > 0.5
                || Math.abs(movePacket.getZ(0.0) - mc.player.getZ()) > 0.5)) {
            event.cancel();
        }
    }

    @EventListener
    public void onRender2D(Render2DEvent event) {
        // HUD 已删除
    }

    private int findAppleSlot() {
        return switch (mode.getValue()) {
            case GoldenApple -> InventoryUtil.findItem(Items.GOLDEN_APPLE);
            case EnchantedGoldenApple -> InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
            case Both -> {
                int slot = InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
                if (slot == -1) slot = InventoryUtil.findItem(Items.GOLDEN_APPLE);
                yield slot;
            }
        };
    }

    private boolean isAppleInHand() {
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        return switch (mode.getValue()) {
            case GoldenApple -> stack.getItem() == Items.GOLDEN_APPLE;
            case EnchantedGoldenApple -> stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
            case Both -> stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
        };
    }

    private void startForceEating() {
        isEating = true;
        eatTicks = 0;
        switchBackTimer = 0;
    }

    private void continueEating() {
        eatTicks++;
        if (eatTicks > 32) {
            stopEating();
            return;
        }
        if (!isAppleInHand()) {
            int appleSlot = findAppleSlot();
            if (appleSlot != -1) {
                if (autoSwitch.getValue()) {
                    InventoryUtil.switchToSlot(appleSlot);
                }
            } else {
                stopEating();
            }
        }
    }

    private void stopEating() {
        isEating = false;
        eatTicks = 0;
        mc.options.useKey.setPressed(false);
        if (prevSlot != -1 && prevSlot < 9 && autoSwitch.getValue()) {
            switchBackTimer = 1;   // 延迟1 tick切换
        } else {
            prevSlot = -1;
        }
    }

    public enum Mode {
        GoldenApple("GoldenApple"),
        EnchantedGoldenApple("EnchantedGoldenApple"),
        Both("Both");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}