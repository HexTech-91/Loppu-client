package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.InitEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.asm.accessors.ISimpleRegistry;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;

public class HUD extends Module {
    public static HUD INSTANCE;
    public final EnumSetting<Page> page = this.add(new EnumSetting<Page>("Page", Page.General));
    public final BooleanSetting renderingUp = this.add(new BooleanSetting("RenderingUp", false, () -> this.page.is(Page.General)));
    public final BooleanSetting font = this.add(new BooleanSetting("Font", true, () -> this.page.is(Page.General)));
    public final BooleanSetting shadow = this.add(new BooleanSetting("Shadow", true, () -> this.page.is(Page.General)));
    public final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false, () -> this.page.is(Page.General)));
    public final BooleanSetting sort = this.add(new BooleanSetting("Sort", false, () -> this.page.is(Page.General)));
    public final SliderSetting xOffset = this.add(new SliderSetting("XOffset", 0.0, 0.0, 50.0, 0.1, () -> this.page.is(Page.General)));
    public final SliderSetting yOffset = this.add(new SliderSetting("YOffset", 0.0, 0.0, 50.0, 0.1, () -> this.page.is(Page.General)));
    public final SliderSetting textOffset = this.add(new SliderSetting("TextOffset", 0.0, -10.0, 10.0, 0.1, () -> this.page.is(Page.General)));
    public final SliderSetting interval = this.add(new SliderSetting("Interval", 0.0, 0.0, 15.0, 0.1, () -> this.page.is(Page.General)));
    public final SliderSetting enableLength = this.add(new SliderSetting("EnableLength", 200, 0, 1000, () -> this.page.is(Page.General)));
    public final SliderSetting disableLength = this.add(new SliderSetting("DisableLength", 200, 0, 1000, () -> this.page.is(Page.General)));
    public final SliderSetting fadeLength = this.add(new SliderSetting("FadeLength", 200, 0, 1000, () -> this.page.is(Page.General)));
    public final EnumSetting<Easing> easing = this.add(new EnumSetting<Easing>("Easing", Easing.CircInOut, () -> this.page.is(Page.General)));
    public final BooleanSetting arrayList = this.add(new BooleanSetting("ArrayList", true, () -> this.page.is(Page.Element)).setParent());
    public final BooleanSetting listSort = this.add(new BooleanSetting("ListSort", true, () -> this.page.is(Page.Element) && this.arrayList.isOpen()));
    public final BooleanSetting armor = this.add(new BooleanSetting("Armor", true, () -> this.page.is(Page.Element)).setParent());
    public final SliderSetting armorOffsetX = this.add(new SliderSetting("ArmorOffsetX", 1.0, -1000.0, 1000.0, -1.0, () -> this.page.is(Page.Element) && this.armor.isOpen()));
    public final SliderSetting armorOffsetY = this.add(new SliderSetting("ArmorOffsetY", 1.0, -1000.0, 1000.0, -1.0, () -> this.page.is(Page.Element) && this.armor.isOpen()));
    public final BooleanSetting durability = this.add(new BooleanSetting("Durability", true, () -> this.page.is(Page.Element) && this.armor.isOpen()));
    public final BooleanSetting waterMark = this.add(new BooleanSetting("WaterMark", true, () -> this.page.is(Page.Element)).setParent());
    public final ColorSetting pulse = this.add(new ColorSetting("Pulse", new Color(79, 0, 0), () -> this.page.is(Page.Element) && this.waterMark.isOpen()).injectBoolean(true));
    public final StringSetting waterMarkString = this.add(new StringSetting("Title", "%hackname% %version%", () -> this.page.is(Page.Element) && this.waterMark.isOpen()));
    public final SliderSetting waterMarkOffset = this.add(new SliderSetting("Offset", 1.0, 0.0, 100.0, -1.0, () -> this.page.is(Page.Element) && this.waterMark.isOpen()));
    public final BooleanSetting fps = this.add(new BooleanSetting("FPS", true, () -> this.page.is(Page.Element)));
    public final BooleanSetting ping = this.add(new BooleanSetting("Ping", true, () -> this.page.is(Page.Element)));
    public final BooleanSetting tps = this.add(new BooleanSetting("TPS", true, () -> this.page.is(Page.Element)));
    public final BooleanSetting ip = this.add(new BooleanSetting("IP", false, () -> this.page.is(Page.Element)));
    public final BooleanSetting time = this.add(new BooleanSetting("Time", false, () -> this.page.is(Page.Element)));
    public final BooleanSetting speed = this.add(new BooleanSetting("Speed", true, () -> this.page.is(Page.Element)));
    public final BooleanSetting brand = this.add(new BooleanSetting("Brand", false, () -> this.page.is(Page.Element)));
    public final BooleanSetting potions = this.add(new BooleanSetting("Potions", true, () -> this.page.is(Page.Element)));

    // --- 新增：自定义药水位置设置 ---
    public final BooleanSetting customLocation = this.add(new BooleanSetting("CustomLocation", false, () -> this.potions.getValue()));
    public final SliderSetting potionOffsetX = this.add(new SliderSetting("PotionOffsetX", 10.0, -1000.0, 1000.0, -1.0, () -> this.potions.getValue() && this.customLocation.getValue()));
    public final SliderSetting potionOffsetY = this.add(new SliderSetting("PotionOffsetY", 10.0, -1000.0, 1000.0, -1.0, () -> this.potions.getValue() && this.customLocation.getValue()));
    // --- 新增结束 ---

    public final BooleanSetting coords = this.add(new BooleanSetting("Coords", true, () -> this.page.is(Page.Element)).setParent());
    public final BooleanSetting coordsColorSync = this.add(new BooleanSetting("CoordsColorSync", true, () -> this.page.is(Page.Element) && this.coords.isOpen()));
    public final ColorSetting color = this.add(new ColorSetting("Color", new Color(208, 0, 0), () -> this.page.is(Page.Color)));
    private final EnumSetting<ColorMode> colorMode = this.add(new EnumSetting<ColorMode>("ColorMode", ColorMode.Pulse, () -> this.page.is(Page.Color)));
    private final SliderSetting rainbowSpeed = this.add(new SliderSetting("RainbowSpeed", 4.0, 1.0, 10.0, 0.1, () -> this.page.is(Page.Color) && this.colorMode.getValue() == ColorMode.Rainbow));
    private final SliderSetting saturation = this.add(new SliderSetting("Saturation", 130.0, 1.0, 255.0, () -> this.page.is(Page.Color) && this.colorMode.getValue() == ColorMode.Rainbow));
    private final SliderSetting rainbowDelay = this.add(new SliderSetting("Delay", 350, 0, 1000, () -> this.page.is(Page.Color) && this.colorMode.getValue() == ColorMode.Rainbow));
    private final ColorSetting endColor = this.add(new ColorSetting("SecondColor", new Color(255, 0, 0, 255), () -> this.page.is(Page.Color) && this.colorMode.getValue() == ColorMode.Pulse).injectBoolean(true));
    private final SliderSetting pulseSpeed = this.add(new SliderSetting("PulseSpeed", 1.0, 0.0, 5.0, 0.1, () -> this.page.is(Page.Color)));
    private final SliderSetting pulseCounter = this.add(new SliderSetting("Counter", 10, 1, 50, () -> this.page.is(Page.Color)));
    public final BooleanSetting blur = this.add(new BooleanSetting("Blur", false, () -> this.page.is(Page.Color)).setParent());
    public final SliderSetting radius = this.add(new SliderSetting("Radius", 10.0, 0.0, 100.0, () -> this.page.is(Page.Color) && this.blur.isOpen()));
    private final BooleanSetting backGround = this.add(new BooleanSetting("BackGround", false, () -> this.page.is(Page.Color)).setParent());
    public final SliderSetting width = this.add(new SliderSetting("Width", 0.0, 0.0, 15.0, () -> this.page.is(Page.Color) && this.backGround.isOpen()));
    private final ColorSetting bgColor = this.add(new ColorSetting("BGColor", new Color(0, 0, 0, 100), () -> this.page.is(Page.Color) && this.backGround.isOpen()));
    private final ColorSetting rect = this.add(new ColorSetting("Rect", new Color(208, 0, 0), () -> this.page.is(Page.Color)).injectBoolean(false));
    private final ColorSetting glow = this.add(new ColorSetting("Glow", new Color(208, 0, 100), () -> this.page.is(Page.Color)).injectBoolean(false));
    private final DecimalFormat decimal = new DecimalFormat("0.0");

    // 分离列表：infoList 不再包含药水，potionInfoList 仅包含药水
    private final ArrayList<Info> infoList = new ArrayList<>();
    private final ArrayList<Info> potionInfoList = new ArrayList<>();
    private final ArrayList<Info> moduleList = new ArrayList<>();

    // 用于追踪玩家状态变化（死亡重生、切换服务器等）
    private UUID lastPlayerUuid = null;

    public HUD() {
        super("HUD", Module.Category.Client);
        this.setChinese("界面");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new InitHandler());

        // 药水信息独立存储到 potionInfoList
        for (StatusEffect potionEffect : Registries.STATUS_EFFECT) {
            try {
                RegistryEntry<StatusEffect> effectRegistryEntry = (RegistryEntry<StatusEffect>)((ISimpleRegistry)Registries.STATUS_EFFECT).getValueToEntry().get(potionEffect);
                this.potionInfoList.add(new Info(() -> {
                    StatusEffectInstance effect = HUD.mc.player.getStatusEffect(effectRegistryEntry);
                    if (effect != null) {
                        String s = potionEffect.getName().getString() + " " + (effect.getAmplifier() + 1);
                        String s2 = HUD.getDuration(effect);
                        return s + " " + s2;
                    }
                    return "";
                }, () -> HUD.mc.player.hasStatusEffect(effectRegistryEntry) && this.potions.getValue(),
                        () -> {
                            int rgb = effectRegistryEntry.value().getColor();
                            return (0xFF << 24) | (rgb & 0x00FFFFFF);
                        }));
            } catch (Exception exception) {}
        }

        // 其余信息添加到 infoList
        this.infoList.add(new Info(() -> "ServerBrand \u00a7f" + (mc.isInSingleplayer() ? "Vanilla" : mc.getNetworkHandler().getBrand().replaceAll("\\(.*?\\)", "")), this.brand::getValue));
        this.infoList.add(new Info(() -> "Server \u00a7f" + (mc.isInSingleplayer() ? "SinglePlayer" : HUD.mc.getCurrentServerEntry().address), this.ip::getValue));
        this.infoList.add(new Info(() -> "TPS \u00a7f" + Alien.SERVER.getTPS() + " [" + Alien.SERVER.getCurrentTPS() + "]", this.tps::getValue));
        this.infoList.add(new Info(() -> {
            double x = HUD.mc.player.getX() - HUD.mc.player.prevX;
            double z = HUD.mc.player.getZ() - HUD.mc.player.prevZ;
            double dist = Math.sqrt(x * x + z * z) / 1000.0;
            double div = 1.388888888888889E-5;
            float timer = Alien.TIMER.get();
            double playerSpeed = dist / div * (double)timer;
            return String.format("Speed \u00a7f%skm/h", this.decimal.format(playerSpeed));
        }, this.speed::getValue));
        this.infoList.add(new Info(() -> "Time \u00a7f" + new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date()), this.time::getValue));
        this.infoList.add(new Info(() -> {
            PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(HUD.mc.player.getUuid());
            Object playerPing = playerListEntry == null ? "Unknown" : playerListEntry.getLatency() + "ms";
            return "Ping \u00a7f" + (String)playerPing;
        }, this.ping::getValue));
        this.infoList.add(new Info(() -> "FPS \u00a7f" + Alien.FPS.getFps(), this.fps::getValue));
    }

    public static String getDuration(StatusEffectInstance pe) {
        if (pe.isInfinite()) {
            return "\u221e";
        }
        int var1 = pe.getDuration();
        int mins = var1 / 1200;
        int sec = var1 % 1200 / 20;
        return String.format("%d:%02d", mins, sec);
    }

    @Override
    public void onRender2D(DrawContext drawContext, float tickDelta) {
        if (this.waterMark.getValue()) {
            if (this.pulse.booleanValue) {
                TextUtil.drawStringPulse(drawContext, this.waterMarkString.getValue().replaceAll("%version%", Alien.VERSION).replaceAll("%hackname%", Alien.NAME), this.waterMarkOffset.getValueInt(), this.waterMarkOffset.getValueInt(), this.color.getValue(), this.pulse.getValue(), this.pulseSpeed.getValue(), this.pulseCounter.getValueInt(), this.font.getValue(), this.shadow.getValue());
            } else {
                TextUtil.drawString(drawContext, this.waterMarkString.getValue().replaceAll("%version%", Alien.VERSION).replaceAll("%hackname%", Alien.NAME), this.waterMarkOffset.getValueInt(), this.waterMarkOffset.getValueInt(), this.color.getValue().getRGB(), this.font.getValue(), this.shadow.getValue());
            }
        }
        int fontHeight = this.getHeight();
        if (this.coords.getValue()) {
            String coordsString = HUD.getCoords();
            this.drawCoord(drawContext, coordsString, mc.getWindow().getScaledHeight() - fontHeight - (HUD.mc.currentScreen instanceof ChatScreen ? 15 : 0));
        }

        // --- 药水渲染逻辑 ---
        if (this.potions.getValue()) {
            if (!this.customLocation.getValue()) {
                // 原行为：药水与其他信息合并列表渲染
                List<Info> combined = new ArrayList<>(potionInfoList);
                combined.addAll(infoList);
                Info.onRender(drawContext, combined, this.renderingUp.getValue());
            } else {
                // 自定义位置：分别渲染主信息和药水
                Info.onRender(drawContext, infoList, this.renderingUp.getValue());
                renderCustomPotions(drawContext);
            }
        } else {
            Info.onRender(drawContext, infoList, this.renderingUp.getValue());
        }

        if (this.arrayList.getValue()) {
            Info.onRender(drawContext, this.moduleList, !this.renderingUp.getValue());
        }
    }

    @EventListener(priority = -999)
    public void onUpdate(ClientTickEvent event) {
        if (HUD.nullCheck()) {
            // 玩家为空时重置所有动画状态，避免状态残留
            resetAllAnimations();
            lastPlayerUuid = null;
            return;
        }

        // 检测玩家是否变化（死亡重生、切换服务器等）
        UUID currentUuid = mc.player.getUuid();
        if (lastPlayerUuid == null || !lastPlayerUuid.equals(currentUuid)) {
            // 玩家变化，重置所有动画状态
            resetAllAnimations();
            lastPlayerUuid = currentUuid;
        }

        if (event.isPost()) {
            Info.onUpdate(this.infoList, this.sort.getValue());
            Info.onUpdate(this.potionInfoList, false); // 药水通常不按宽度排序
            if (this.arrayList.getValue()) {
                Info.onUpdate(this.moduleList, this.listSort.getValue());
            }
        }
    }

    /**
     * 重置所有Info的动画状态，用于处理玩家状态变化（死亡重生、切换服务器等）
     */
    private void resetAllAnimations() {
        for (Info info : potionInfoList) {
            info.reset();
        }
        for (Info info : infoList) {
            info.reset();
        }
        for (Info info : moduleList) {
            info.reset();
        }
    }

    // --- 自定义药水绘制方法 ---
    private void renderCustomPotions(DrawContext context) {
        boolean up = this.renderingUp.getValue();
        int offsetX = this.potionOffsetX.getValueInt();
        int offsetY = this.potionOffsetY.getValueInt();
        double fontHeight = this.getFontHeight();
        double interval = this.interval.getValue();

        double currentY;
        if (up) {
            currentY = offsetY + 1.0;
        } else {
            currentY = mc.getWindow().getScaledHeight() - offsetY - fontHeight;
            if (mc.currentScreen instanceof ChatScreen) {
                currentY -= 15;
            }
        }

        for (Info info : potionInfoList) {
            if (info.string == null) continue;
            double fade = info.fadeAnimation.get(info.isOn ? 1.0 : 0.0,
                    this.fadeLength.getValueInt(),
                    this.easing.getValue());
            if (fade > 0.04 || info.currentX > 0.0) {
                int color;
                if (info.colorSupplier != null) {
                    int baseColor = info.colorSupplier.getAsInt();
                    int alpha = (baseColor >> 24) & 0xFF;
                    int newAlpha = (int)(alpha * fade);
                    color = (baseColor & 0x00FFFFFF) | (newAlpha << 24);
                } else {
                    color = ColorUtil.injectAlpha(this.getColor(20.0), (int)(255 * fade));
                }
                TextUtil.drawString(context, info.string, offsetX, currentY, color,
                        this.font.getValue(), this.shadow.getValue());
            }
            if (up) {
                currentY += (fontHeight + interval) * fade;
            } else {
                currentY -= (fontHeight + interval) * fade;
            }
        }
    }

    private static String getCoords() {
        boolean inNether = HUD.mc.world.getRegistryKey().equals(World.NETHER);
        int posX = HUD.mc.player.getBlockX();
        int posY = HUD.mc.player.getBlockY();
        int posZ = HUD.mc.player.getBlockZ();
        float factor = !inNether ? 0.125f : 8.0f;
        int anotherWorldX = (int)(HUD.mc.player.getX() * (double)factor);
        int anotherWorldZ = (int)(HUD.mc.player.getZ() * (double)factor);
        return "XYZ \u00a7f" + (inNether ? posX + ", " + posY + ", " + posZ + " \u00a77[\u00a7f" + anotherWorldX + ", " + anotherWorldZ + "\u00a77]\u00a7f" : posX + ", " + posY + ", " + posZ + "\u00a77 [\u00a7f" + anotherWorldX + ", " + anotherWorldZ + "\u00a77]");
    }

    private int getWidth(String s) {
        if (this.lowerCase.getValue()) {
            s = s.toLowerCase();
        }
        if (this.font.getValue()) {
            return (int)FontManager.ui.getWidth(s);
        }
        return HUD.mc.textRenderer.getWidth(s);
    }

    private int getHeight() {
        if (this.font.getValue()) {
            return (int)FontManager.ui.getFontHeight();
        }
        return 9;
    }

    private void drawCoord(DrawContext drawContext, String s, int y) {
        if (this.coordsColorSync.getValue()) {
            if (this.lowerCase.getValue()) {
                s = s.toLowerCase();
            }
            TextUtil.drawString(drawContext, s, 2.0, y, this.getColor(20.0), this.font.getValue(), this.shadow.getValue());
        } else if (this.pulse.booleanValue) {
            TextUtil.drawStringPulse(drawContext, s, 2.0, y, this.color.getValue(), this.pulse.getValue(), this.pulseSpeed.getValue(), this.pulseCounter.getValueInt(), this.font.getValue(), this.shadow.getValue());
        } else {
            TextUtil.drawString(drawContext, s, 2.0, y, this.color.getValue().getRGB(), this.font.getValue(), this.shadow.getValue());
        }
    }

    public int getColor(double counter) {
        if (this.colorMode.getValue() != ColorMode.Custom) {
            return this.rainbow(counter).getRGB();
        }
        return this.color.getValue().getRGB();
    }

    private Color rainbow(double delay) {
        if (this.colorMode.getValue() == ColorMode.Pulse) {
            if (this.endColor.booleanValue) {
                return ColorUtil.pulseColor(this.color.getValue(), this.endColor.getValue(), delay, this.pulseCounter.getValueInt(), this.pulseSpeed.getValue());
            }
            return ColorUtil.pulseColor(this.color.getValue(), delay, this.pulseCounter.getValueInt(), this.pulseSpeed.getValue());
        }
        if (this.colorMode.getValue() == ColorMode.Rainbow) {
            double rainbowState = Math.ceil(((double)System.currentTimeMillis() * this.rainbowSpeed.getValue() + delay * this.rainbowDelay.getValue()) / 20.0);
            return Color.getHSBColor((float)(rainbowState % 360.0 / 360.0), this.saturation.getValueFloat() / 255.0f, 1.0f);
        }
        return this.color.getValue();
    }

    private int getFontHeight() {
        if (this.font.getValue()) {
            return (int)FontManager.ui.getFontHeight();
        }
        return 9;
    }

    public static enum Page {
        General,
        Element,
        Color;
    }

    private static enum ColorMode {
        Custom,
        Pulse,
        Rainbow;
    }

    public class InitHandler {
        @EventListener
        public void onInit(InitEvent event) {
            for (Module module : Alien.MODULE.getModules()) {
                HUD.this.moduleList.add(new Info(module::getArrayName, () -> module.isOn() && module.drawn.getValue()));
            }
            Alien.EVENT_BUS.unsubscribe(this);
        }
    }

    public class Info {
        public final Callable<String> info;
        public String string;
        public final BooleanSupplier drawn;
        public double currentX = 0.0;
        public boolean isOn;
        public final Animation animation = new Animation();
        public final Animation fadeAnimation = new Animation();
        static double fontHeight;
        static double currentY;
        static int windowWidth;
        static boolean fromUp;
        static double counter;

        private final IntSupplier colorSupplier;

        public Info(Callable<String> info, BooleanSupplier drawn) {
            this(info, drawn, null);
        }

        public Info(Callable<String> info, BooleanSupplier drawn, IntSupplier colorSupplier) {
            this.info = info;
            this.drawn = drawn;
            this.colorSupplier = colorSupplier;
            try {
                this.string = this.info.call();
            } catch (Exception exception) {
            }
        }

        /**
         * 重置动画状态，用于玩家状态变化时清理残留状态
         */
        public void reset() {
            this.currentX = 0.0;
            this.isOn = false;
            this.animation.reset();
            this.fadeAnimation.reset();
        }

        public static void onRender(DrawContext context, List<Info> list, boolean fromUp) {
            counter = 20.0;
            Info.fromUp = fromUp;
            fontHeight = INSTANCE.getFontHeight();
            currentY = fromUp ? 1.0 + HUD.INSTANCE.yOffset.getValue() : (double)Wrapper.mc.getWindow().getScaledHeight() - fontHeight - 1.0 - (Wrapper.mc.currentScreen instanceof ChatScreen && HUD.INSTANCE.yOffset.getValue() < 12.0 ? 12.0 - HUD.INSTANCE.yOffset.getValue() + HUD.INSTANCE.interval.getValue() / 2.0 : 0.0) - HUD.INSTANCE.yOffset.getValue();
            windowWidth = Wrapper.mc.getWindow().getScaledWidth();
            for (Info s : list) {
                s.draw(context);
            }
        }

        public static void onUpdate(List<Info> list, boolean sort) {
            for (Info s : list) {
                s.onUpdate();
            }
            if (sort) {
                list.sort(Comparator.comparingInt(info -> info.string == null ? 0 : -INSTANCE.getWidth(info.string)));
            }
        }

        public void onUpdate() {
            this.isOn = this.drawn.getAsBoolean();
            if (this.isOn) {
                try {
                    this.string = HUD.this.lowerCase.getValue() ? this.info.call().toLowerCase() : this.info.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void draw(DrawContext context) {
            if (this.currentX > 0.0 || this.isOn) {
                this.currentX = this.animation.get(this.isOn ? (double)(HUD.this.getWidth(this.string) + 1) : 0.0, this.isOn ? (long)HUD.this.enableLength.getValueInt() : (long)HUD.this.disableLength.getValueInt(), HUD.this.easing.getValue());
                double width = this.currentX + (double)HUD.this.xOffset.getValueFloat();
                double fade = this.fadeAnimation.get(this.isOn ? 1.0 : 0.0, HUD.this.fadeLength.getValueInt(), HUD.this.easing.getValue());
                if (fade > 0.04) {
                    int c;
                    if (this.colorSupplier != null) {
                        int baseColor = this.colorSupplier.getAsInt();
                        int alpha = (baseColor >> 24) & 0xFF;
                        int newAlpha = (int)(alpha * fade);
                        c = (baseColor & 0x00FFFFFF) | (newAlpha << 24);
                    } else {
                        c = ColorUtil.injectAlpha(HUD.this.getColor(counter += fromUp ? fade : -fade), (int)((double)HUD.this.color.getValue().getAlpha() * fade));
                    }
                    if (HUD.this.blur.getValue()) {
                        Alien.BLUR.applyBlur((float)(HUD.this.radius.getValue() * fade), (float)((double)windowWidth - width - (double)(HUD.this.width.getValueFloat() / 2.0f)), (float)currentY - 1.0f - HUD.this.interval.getValueFloat() / 2.0f, (float)width + HUD.this.width.getValueFloat() - HUD.this.xOffset.getValueFloat(), (float)fontHeight + HUD.this.interval.getValueFloat());
                    }
                    if (HUD.this.backGround.getValue()) {
                        Render2DUtil.drawRect(context.getMatrices(), (float)((double)windowWidth - width - (double)(HUD.this.width.getValueFloat() / 2.0f)), (float)currentY - 1.0f - HUD.this.interval.getValueFloat() / 2.0f, (float)width + HUD.this.width.getValueFloat() - HUD.this.xOffset.getValueFloat(), (float)fontHeight + HUD.this.interval.getValueFloat(), ColorUtil.injectAlpha(HUD.this.bgColor.sync ? c : HUD.this.bgColor.getValue().getRGB(), (int)((double)HUD.this.bgColor.getValue().getAlpha() * fade)));
                    }
                    if (HUD.this.glow.booleanValue) {
                        Render2DUtil.drawGlow(context.getMatrices(), (float)((double)windowWidth - width - (double)(HUD.this.width.getValueFloat() / 2.0f)), (float)currentY - 1.0f - HUD.this.interval.getValueFloat() / 2.0f, (float)width + HUD.this.width.getValueFloat() - HUD.this.xOffset.getValueFloat(), (float)fontHeight + HUD.this.interval.getValueFloat(), ColorUtil.injectAlpha(HUD.this.glow.sync ? c : HUD.this.glow.getValue().getRGB(), (int)((double)HUD.this.glow.getValue().getAlpha() * fade)));
                    }
                    TextUtil.drawString(context, this.string, (double)windowWidth - width, currentY + (double)HUD.this.textOffset.getValueFloat(), c, HUD.this.font.getValue(), HUD.this.shadow.getValue());
                    if (HUD.this.rect.booleanValue) {
                        Render2DUtil.drawRect(context.getMatrices(), (float)windowWidth + HUD.this.width.getValueFloat() / 2.0f - HUD.this.xOffset.getValueFloat(), (float)currentY - 1.0f - HUD.this.interval.getValueFloat() / 2.0f, 1.0f, (float)fontHeight + HUD.this.interval.getValueFloat(), HUD.this.rect.sync ? c : ColorUtil.injectAlpha(HUD.this.rect.getValue(), (int)((double)HUD.this.rect.getValue().getAlpha() * fade)).getRGB());
                    }
                    currentY += fromUp ? (fontHeight + HUD.this.interval.getValue()) * fade : -(fontHeight + HUD.this.interval.getValue()) * fade;
                }
            }
        }
    }
}