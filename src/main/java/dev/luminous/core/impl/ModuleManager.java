package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.mod.Mod;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.*;
import dev.luminous.mod.modules.impl.combat.*;
import dev.luminous.mod.modules.impl.exploit.*;
import dev.luminous.mod.modules.impl.movement.*;
import dev.luminous.mod.modules.impl.misc.*;
import dev.luminous.mod.modules.impl.nagisa.*;
import dev.luminous.mod.modules.impl.nagisa.acpro.ACPro;
import dev.luminous.mod.modules.impl.nagisa.acpro.BasePlacer;
import dev.luminous.mod.modules.impl.player.*;      
import dev.luminous.mod.modules.impl.render.*;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class ModuleManager implements Wrapper {
    private final ArrayList<Module> modules = new ArrayList();
    private final ArrayList<ModuleManager.ToggleBanner> toggleBanners = new ArrayList();
    private boolean notificationMouseWasDown = false;
    private boolean interactiveToggleInBanner = false;

    public ArrayList<Module> getModules() {
        return this.modules;
    }

    public ModuleManager() {
        this.init();
    }


    public void init() {
        if (BaritoneUtil.loaded) {
            this.addModule(new BaritoneModule());
        }

        this.addModule(new Panic());
        this.addModule(new AutoKit());
        //this.addModule(new PotCount());
        this.addModule(new Bot());
        this.addModule(new PopEz());
        //this.addModule(new AutoPushGZ());
        this.addModule(new Fonts());
        //this.addModule(new MioCrystal());
        this.addModule(new NoTerrainScreen());
        this.addModule(new AutoCrystal());
        //this.addModule(new AutoCrystalOld());
        this.addModule(new Ambience());
        this.addModule(new Disabler());
        this.addModule(new AntiHunger());
        this.addModule(new PortalGod());
        //this.addModule(new SpeedTest());
        this.addModule(new AntiVoid());
        this.addModule(new AutoWalk());
        this.addModule(new VClip());
        this.addModule(new ExtraTab());
        this.addModule(new AntiWeak());
        //this.addModule(new MioCrystal());
        this.addModule(new Friend());
        this.addModule(new AspectRatio());
        this.addModule(new ChunkESP());
        this.addModule(new Aura());
        //this.addModule(new XinRunner());
        //this.addModule(new AutoPotion());
        //this.addModule(new TPAura());
        //this.addModule(new TPAnchor());
        this.addModule(new AntiDiscard());
        this.addModule(new BackTrack());
        //this.addModule(new AntiPiston());
        this.addModule(new FastCrystal());
        //this.addModule(new AntiBaseKill());
        //this.addModule(new AutoMine);
        this.addModule(new PistonCrystal());
        this.addModule(new AutoAnchor());
        this.addModule(new FakeName());
        this.addModule(new PhaseESP());
        this.addModule(new AutoArmor());
        //this.addModule(new Breaker());
        this.addModule(new AutoLog());
        this.addModule(new AutoEZ());
        //this.addModule(new AssistDupe());
        this.addModule(new AutoKouZi());
        this.addModule(new SelfTrap());
        //this.addModule(new BurrowAssist());
        this.addModule(new Sorter());
        this.addModule(new HeadTrap());
        this.addModule(new ArmorPreserve());
        this.addModule(new AutoMend());
        //this.addModule(new FollowElytraFly());
        this.addModule(new AutoPot());
        //this.addModule(new AutoPush());
        //this.addModule(new AutoPush2());
        //this.addModule(new FollowTarget());
        this.addModule(new ChestStealer());
        this.addModule(new AutoRegear());
        this.addModule(new Offhand());
        this.addModule(new Nuker());
        this.addModule(new ResetVL());
        //.addModule(new Keystrokes());
        this.addModule(new AutoTrap());
        this.addModule(new AutoWeb());
        //this.addModule(new DynamicIsland());
        //this.addModule(new AutoButton());
        this.addModule(new Blink());
        this.addModule(new ChorusControl());
        this.addModule(new BlockStrafe());
        this.addModule(new AntiSlowFalling());
        this.addModule(new LowHealthAlert());
        this.addModule(new FastSwim());
        this.addModule(new Blocker());
        this.addModule(new Quiver());
        this.addModule(new PearlIntercept());
        this.addModule(new CrystalPlaceESP());
        //this.addModule(new AutoCrystalPro());
        //this.addModule(new SpeedMine());
        this.addModule(new BowBomb());
        this.addModule(new BreakESP());
        this.addModule(new ForceEat());
        this.addModule(new AutoBowRelease());
        this.addModule(new PopLag());
        this.addModule(new Burrow());
        this.addModule(new NoGround());
        this.addModule(new RaytraceBypass());
        //this.addModule(new BurrowSkill());
        this.addModule(new BorrowAgent());
        this.addModule(new AutoVclip());
        this.addModule(new SmartDigger());
        this.addModule(new ACPro());
        this.addModule(new BasePlacer());
        this.addModule(new AAPro());
        //this.addModule(new M32KMace());
        this.addModule(new TPMace());
        this.addModule(new TPVoid());
        this.addModule(new VoidMace());
        this.addModule(new SkyMace());
        this.addModule(new GroundMace());
        this.addModule(new MaceAura());
        this.addModule(new LagMace());
        this.addModule(new LagSpoof());

        this.addModule(new HitBoxMace());
        this.addModule(new POPGod());
        //this.addModule(new AutoDick());
        //this.addModule(new dev.luminous.mod.modules.impl.client.KitMgr());
        //this.addModule(new dev.luminous.mod.modules.impl.client.FriendMgr());
        
        this.addModule(new GodHoleESP());
        this.addModule(new CrossBowFire());
        this.addModule(new PotHelper());
        //this.addModule(new AntiBurrow());
        this.addModule(new DriftVertical());
        this.addModule(new DriftHorizontal());
        //this.addModule(new AutoTrapDoor());
        this.addModule(new HighJump());
        this.addModule(new Punctuation());
        //this.addModule(new Analysis());
        this.addModule(new AutoQueue());
        //this.addModule(new WindowTitle());
        //this.addModule(new AntiAnchor());
        this.addModule(new CameraClip());
        this.addModule(new ChatAppend());
        //this.addModule(new AutoObsidian());
        this.addModule(new ClickGui());
        this.addModule(new InfiniteTrident());
        this.addModule(new DeathRespawn());
        this.addModule(new ColorsModule());
        this.addModule(new LavaFiller());
        this.addModule(new AntiPhase());
        this.addModule(new Clip());
        this.addModule(new BedrockPhase());
        this.addModule(new AntiCheat());
        this.addModule(new ItemsCounter());
        this.addModule(new Fov());
        this.addModule(new BackPackHUD());
        //this.addModule(new IRC());
        this.addModule(new Criticals());
        this.addModule(new CevBreaker());
        this.addModule(new Crosshair());
        this.addModule(new Chams());
        this.addModule(new CrystalChams());
        this.addModule(new LowArmorAlert());
        this.addModule(new AntiPacket());
        this.addModule(new AutoReconnect());
        this.addModule(new ESP());
        //this.addModule(new ArrowESP());
        this.addModule(new HoleESP());
        this.addModule(new Tracers());
        this.addModule(new PlayerEsp());
        this.addModule(new MovementSync());
        this.addModule(new ElytraFly());
        this.addModule(new ForceStand());
        //this.addModule(new FollowElytraFly());
        this.addModule(new PacketLogger());
        this.addModule(new MoveLogger());
        this.addModule(new TeleportLogger());
        this.addModule(new SkinFlicker());
        this.addModule(new EntityControl());
        this.addModule(new NameTags());
        this.addModule(new GodMode());
        this.addModule(new ShulkerViewer());
        this.addModule(new PingSpoof());
        this.addModule(new FakePlayer());
        this.addModule(new Spammer());
        this.addModule(new MotionCamera());
        this.addModule(new HighLight());
        this.addModule(new FastFall());
        this.addModule(new FastWeb());
        this.addModule(new Flatten());
        this.addModule(new Fly());
        this.addModule(new Yaw());
        //this.addModule(new AutoWither());
        this.addModule(new AutoHelp());
        this.addModule(new AutoAim());
        this.addModule(new AutoTK());
        this.addModule(new Freecam());
        this.addModule(new FreeLook());
        this.addModule(new TimerModule());
        this.addModule(new Tips());
        this.addModule(new ClientSetting());
        this.addModule(new TextRadar());
        this.addModule(new HUD());
        this.addModule(new NoResourcePack());
        this.addModule(new RocketExtend());
        this.addModule(new HoleFiller());
        this.addModule(new HoleSnap());
        this.addModule(new LogoutSpots());
        this.addModule(new AutoTool());
        this.addModule(new Trajectories());
        this.addModule(new AutoPearl());
        this.addModule(new AntiEffects());
        this.addModule(new NoFall());
        this.addModule(new NoRender());
        this.addModule(new NoSlow());
        this.addModule(new HoleKick());
        this.addModule(new HolePush());
        this.addModule(new SpeedBoost());
        this.addModule(new NoSound());
        this.addModule(new AirPlace());
        this.addModule(new CrawlHelper());
        this.addModule(new Xray());
        this.addModule(new DropDisplay());
        this.addModule(new RotationVisual());
        this.addModule(new PacketEat());
        this.addModule(new PacketFly());
        this.addModule(new PacketMine());
        this.addModule(new PacketControl());
        this.addModule(new Phase());
        //this.addModule(new AutoWindCharge());
        this.addModule(new PlaceRender());
        this.addModule(new InteractTweaks());
        this.addModule(new AutoRespawn());
        this.addModule(new PopChams());
        this.addModule(new Replenish());
        //this.addModule(new Follower());
        this.addModule(new ServerLagger());
        this.addModule(new Scaffold());
        this.addModule(new ShaderModule());
        this.addModule(new SkyBox());
//        this.addModule(new AntiCrawl());
        this.addModule(new AntiRegear());
        this.addModule(new SafeWalk());
        this.addModule(new NoJumpDelay());
        this.addModule(new Speed());
        this.addModule(new Sprint());
        this.addModule(new Strafe());
        this.addModule(new LongJump());
        this.addModule(new AntiLagBack());
        this.addModule(new Step());
        this.addModule(new Surround());
        this.addModule(new TotemParticle());
        this.addModule(new TotemHUD());
        this.addModule(new Velocity());
        this.addModule(new PearlFollow());
        this.addModule(new NameTag2D());
        this.addModule(new WindTP());
        this.addModule(new TwoDESP());
        this.addModule(new KillEffect());
        this.addModule(new MaceEffect());
        //this.addModule(new FWAntiSlowFalling());
        this.addModule(new ViewModel());
        //this.addModule(new MaceAura());
        this.addModule(new XCarry());
        this.addModule(new Zoom());

        this.addModule(new AutoFlirt());
        this.addModule(new WinRateAdvisor());
        this.addModule(new MoveUp());
        this.addModule(new IQBoost());
        this.addModule(new SkinChanger());
        this.addModule(new PlayerScale());
        this.addModule(new EveryonePig());
        this.addModule(new GrimLowHop());
        this.addModule(new TPFollow());
        //this.addModule(new FriendSync());
        this.modules.sort(Comparator.comparing(Mod::getName));
    }

    public void onKeyReleased(int eventKey) {
        if (eventKey != -1 && eventKey != 0) {
            this.handleKeyEvent(eventKey, false);
        }
    }

    public void onKeyPressed(int eventKey) {
        if (eventKey != -1 && eventKey != 0) {
            this.handleKeyEvent(eventKey, true);
        }
    }

    private void handleKeyEvent(int key, boolean isPressed) {
        for (Module module : this.modules) {
            BindSetting bindSetting = module.getBindSetting();
            if (bindSetting.getValue() == key) {
                if (isPressed && mc.currentScreen == null) {
                    module.toggle();
                    bindSetting.holding = true;
                } else if (!isPressed && bindSetting.isHoldEnable() && bindSetting.holding) {
                    module.toggle();
                    bindSetting.holding = false;
                }
            }

            for (Setting setting : module.getSettings()) {
                if (setting instanceof BindSetting bind && bind.getValue() == key) {
                    bind.setPressed(isPressed);
                }
            }
        }
    }

    public void onLogin() {
        for (Module module : this.modules) {
            if (module.isOn()) {
                module.onLogin();
            }
        }
    }

    public void onLogout() {
        for (Module module : this.modules) {
            if (module.isOn()) {
                module.onLogout();
            }
        }
    }

    public void onRender2D(DrawContext drawContext) {
        for (Module module : this.modules) {
            if (module.isOn()) {
                try {
                    module.onRender2D(drawContext, mc.getRenderTickCounter().getTickDelta(true));
                } catch (Exception var6) {
                    var6.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue()) {
                        CommandManager.sendMessage("§4An error has occurred (" + module.getName() + " [onRender2D]) Message: [" + var6.getMessage() + "]");
                    }
                }
            }
        }

        try {
            Alien.EVENT_BUS.post(Render2DEvent.get(drawContext, mc.getRenderTickCounter().getTickDelta(true)));
        } catch (Exception var5) {
            var5.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue()) {
                CommandManager.sendMessage("§4An error has occurred (Render3DEvent) Message: [" + var5.getMessage() + "]");
            }
        }

        this.renderToggleBanners(drawContext);
    }

    public void render3D(MatrixStack matrices) {
        GL11.glEnable(2848);

        for (Module module : this.modules) {
            if (module.isOn()) {
                try {
                    module.onRender3D(matrices);
                } catch (Exception var6) {
                    var6.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue()) {
                        CommandManager.sendMessage("§4An error has occurred (" + module.getName() + " [onRender3D]) Message: [" + var6.getMessage() + "]");
                    }
                }
            }
        }

        try {
            Alien.EVENT_BUS.post(Render3DEvent.get(matrices, mc.getRenderTickCounter().getTickDelta(true)));
        } catch (Exception var5) {
            var5.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue()) {
                CommandManager.sendMessage("§4An error has occurred (Render3DEvent) Message: [" + var5.getMessage() + "]");
            }
        }

        GL11.glDisable(2848);
    }

    public void showToggleBanner(Module module, boolean enabled) {
        if (module != null && mc != null) {
            if (ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.banner.getValue()) {
                if (ClickGui.getInstance() != null && ClickGui.getInstance().disableNotification.getValue()) {
                    return;
                }

                if (this.interactiveToggleInBanner) {
                    for (int i = this.toggleBanners.size() - 1; i >= 0; i--) {
                        ModuleManager.ToggleBanner b = (ModuleManager.ToggleBanner)this.toggleBanners.get(i);
                        if (b.name.equalsIgnoreCase(module.getDisplayName())) {
                            this.toggleBanners.set(i, new ModuleManager.ToggleBanner(b.name, enabled, b.start));
                            break;
                        }
                    }

                    this.interactiveToggleInBanner = false;
                    return;
                }

                this.toggleBanners.add(new ModuleManager.ToggleBanner(module.getDisplayName(), enabled, System.currentTimeMillis()));
                if (this.toggleBanners.size() > 5) {
                    this.toggleBanners.remove(0);
                }

                if (ClientSetting.INSTANCE.bannerSound.getValue() && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, ClientSetting.INSTANCE.bannerSoundPitch.getValueFloat()));
                }
            }
        }
    }

    private void renderToggleBanners(DrawContext ctx) {
        if (!this.toggleBanners.isEmpty() && mc != null) {
            if (ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.banner.getValue()) {
                long now = System.currentTimeMillis();
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                int rawW = mc.getWindow().getWidth();
                int rawH = mc.getWindow().getHeight();
                int mx = (int)(mc.mouse.getX() * sw / Math.max(1, rawW));
                int my = (int)(mc.mouse.getY() * sh / Math.max(1, rawH));
                boolean mouseDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1;
                ArrayList<ModuleManager.ToggleBanner> removeList = new ArrayList();
                long fi = (long)ClientSetting.INSTANCE.bannerFade.getValue();
                long hold = (long)ClientSetting.INSTANCE.bannerHold.getValue();
                if (ClientSetting.INSTANCE.onlyOne.getValue() && ClientSetting.INSTANCE.bannerStyle.getValue() == ClientSetting.BannerStyle.iOS) {
                    long start = ((ModuleManager.ToggleBanner)this.toggleBanners.get(0)).start;
                    long elapsed = now - start;
                    long total = fi + hold + fi;
                    if (elapsed > total) {
                        removeList.add((ModuleManager.ToggleBanner)this.toggleBanners.get(0));
                    }

                    double alpha;
                    double slide;
                    if (elapsed < fi) {
                        double p = Math.max(0.0, Math.min(1.0, (double)elapsed / fi));
                        double e = Easing.SineOut.ease(p);
                        alpha = e;
                        slide = 10.0 * e;
                    } else if (elapsed < fi + hold) {
                        alpha = 1.0;
                        slide = 10.0;
                    } else {
                        double p = Math.max(0.0, Math.min(1.0, (double)(elapsed - fi - hold) / fi));
                        double e = Easing.SineInOut.ease(p);
                        alpha = 1.0 - e;
                        slide = 10.0 - 10.0 * e;
                    }

                    int a = (int)Math.max(0L, Math.min(255L, Math.round(alpha * 255.0)));
                    int maxRows = Math.min(3, this.toggleBanners.size());
                    int rowH = 14;
                    int pad = 10;
                    int height = rowH * maxRows + pad * 2;
                    int width = Math.max(180, (int)(sw * 0.36));
                    int x = 14;
                    int y = (int)slide;
                    Color bg = new Color(255, 255, 255, (int)Math.round(a * 0.85));
                    float radius = height / 2.0F;
                    Alien.BLUR.applyBlur(30.0F, x, y, width, height);
                    Render2DUtil.drawPill(ctx.getMatrices(), x, y, width, height, bg);
                    Render2DUtil.drawRoundedStroke(ctx.getMatrices(), x, y, width, height, radius, new Color(220, 224, 230, Math.min(180, a)), 48);
                    Render2DUtil.drawGlow(ctx.getMatrices(), x - 3.0F, y - 3.0F, width + 6.0F, height + 6.0F, new Color(255, 255, 255, Math.min(54, a)).getRGB());
                    Render2DUtil.drawLine(ctx.getMatrices(), x + radius, y + 2.0F, x + width - radius, y + 2.0F, new Color(255, 255, 255, Math.min(90, a)).getRGB());
                    Render2DUtil.drawLine(
                            ctx.getMatrices(), x + radius, y + height - 2.2F, x + width - radius, y + height - 2.2F, new Color(120, 130, 140, Math.min(70, a)).getRGB()
                    );
                    int gx = x + pad;
                    int gy = y + pad;

                    for (int i = 0; i < maxRows; i++) {
                        ModuleManager.ToggleBanner b = (ModuleManager.ToggleBanner)this.toggleBanners.get(this.toggleBanners.size() - 1 - i);
                        Module modCur = this.getModuleByDisplayName(b.name);
                        boolean curEnabled = modCur != null && modCur.isOn();
                        String text = (curEnabled ? "已开启 " : "已关闭 ") + b.name;
                        Color textColor = new Color(30, 30, 30, a);
                        int tw = ClickGui.getInstance().font.getValue() ? (int)FontManager.ui.getWidth(text) : (int)TextUtil.getWidth(text);
                        int tx = x + (width - tw) / 2;
                        double ty = gy + i * rowH;
                        TextUtil.drawString(ctx, text, tx, ty, textColor.getRGB(), ClickGui.getInstance().font.getValue());
                        int toggleW = 28;
                        int toggleH = 14;
                        float swx = x + width - pad - toggleW;
                        float swy = y + pad + i * rowH + (rowH - toggleH) / 2.0F;
                        Color on = new Color(0, 120, 212, a);
                        Color track = curEnabled ? new Color(on.getRed(), on.getGreen(), on.getBlue(), Math.min(180, a)) : new Color(238, 238, 238, Math.min(200, a));
                        Render2DUtil.drawRoundedRect(ctx.getMatrices(), swx, swy, toggleW, toggleH, 6.0F, track);
                        float dotR = toggleH / 2.0F - 2.0F;
                        float dotCx = curEnabled ? swx + toggleW - toggleH / 2.0F : swx + toggleH / 2.0F;
                        float dotCy = swy + toggleH / 2.0F;
                        Render2DUtil.drawCircle(ctx.getMatrices(), dotCx, dotCy, dotR, new Color(255, 255, 255, Math.min(230, a)), 80);
                        if (mx >= swx && mx <= swx + toggleW && my >= swy && my <= swy + toggleH && mouseDown && !this.notificationMouseWasDown && modCur != null) {
                            this.interactiveToggleInBanner = true;
                            modCur.toggle();
                        }
                    }

                    this.notificationMouseWasDown = mouseDown;
                } else {
                    int baseY = 8;
                    int height = 16;
                    int gap = 4;
                    int index = 0;

                    for (ModuleManager.ToggleBanner b : this.toggleBanners) {
                        long elapsedx = now - b.start;
                        long totalx = fi + hold + fi;
                        if (elapsedx > totalx) {
                            removeList.add(b);
                        } else {
                            double alpha;
                            double slide;
                            if (elapsedx < fi) {
                                double p = Math.max(0.0, Math.min(1.0, (double)elapsedx / fi));
                                double e = Easing.SineOut.ease(p);
                                alpha = e;
                                slide = -height - 8 + 8.0 * e;
                            } else if (elapsedx < fi + hold) {
                                alpha = 1.0;
                                slide = 8.0;
                            } else {
                                double p = Math.max(0.0, Math.min(1.0, (double)(elapsedx - fi - hold) / fi));
                                double e = Easing.SineInOut.ease(p);
                                alpha = 1.0 - e;
                                slide = 8.0 + (-height - 8) * e;
                            }

                            int pad = 10;
                            Module modCur = this.getModuleByDisplayName(b.name);
                            boolean curEnabled = modCur != null && modCur.isOn();
                            String text = (curEnabled ? "已开启 " : "已关闭 ") + b.name;
                            int tw = ClickGui.getInstance().font.getValue() ? (int)FontManager.ui.getWidth(text) : (int)TextUtil.getWidth(text);
                            int width = Math.max(tw + pad * 2 + 8 + 30, 160);
                            int margin = 14;
                            int ay = (int)(
                                    margin
                                            + (
                                            ClientSetting.INSTANCE.bannerStack.getValue() == ClientSetting.StackDir.Down
                                                    ? slide + index * (height + gap)
                                                    : slide - index * (height + gap)
                                    )
                            );
                            int a = (int)Math.max(0L, Math.min(255L, Math.round(alpha * 255.0)));
                            int bgAlpha = (int)Math.round(a * 0.32);
                            Color bg = new Color(255, 255, 255, Math.min(220, a));
                            Color on = new Color(0, 120, 212, a);
                            new Color(204, 204, 204, a);
                            float radius = 3.5F;
                            Alien.BLUR.applyBlur(30.0F, margin, ay, width, height);
                            Render2DUtil.drawRoundedRect(ctx.getMatrices(), margin, ay, width, height, radius, bg);
                            Render2DUtil.drawRoundedStroke(ctx.getMatrices(), margin, ay, width, height, radius, new Color(220, 224, 230, Math.min(180, a)), 48);
                            Render2DUtil.drawGlow(
                                    ctx.getMatrices(), margin - 3.0F, ay - 3.0F, width + 6.0F, height + 6.0F, new Color(255, 255, 255, Math.min(54, a)).getRGB()
                            );
                            Render2DUtil.drawLine(
                                    ctx.getMatrices(), margin + 2.0F, ay + 2.0F, margin + width - 2.0F, ay + 2.0F, new Color(255, 255, 255, Math.min(90, a)).getRGB()
                            );
                            Render2DUtil.drawLine(
                                    ctx.getMatrices(),
                                    margin + 2.0F,
                                    ay + height - 2.2F,
                                    margin + width - 2.0F,
                                    ay + height - 2.2F,
                                    new Color(120, 130, 140, Math.min(70, a)).getRGB()
                            );
                            int toggleW = 28;
                            int toggleH = 14;
                            float tx = margin + width - pad - toggleW;
                            float ty = ay + (height - toggleH) / 2.0F;
                            Color track = curEnabled
                                    ? new Color(on.getRed(), on.getGreen(), on.getBlue(), Math.min(180, a))
                                    : new Color(238, 238, 238, Math.min(200, a));
                            Render2DUtil.drawRoundedRect(ctx.getMatrices(), tx, ty, toggleW, toggleH, 6.0F, track);
                            float dotR = toggleH / 2.0F - 2.0F;
                            float dotCx = curEnabled ? tx + toggleW - toggleH / 2.0F : tx + toggleH / 2.0F;
                            float dotCy = ty + toggleH / 2.0F;
                            Render2DUtil.drawCircle(ctx.getMatrices(), dotCx, dotCy, dotR, new Color(255, 255, 255, Math.min(230, a)), 80);
                            double textY = ay + (height - (ClickGui.getInstance().font.getValue() ? FontManager.ui.getFontHeight() : TextUtil.getHeight())) / 2.0;
                            int textAreaW = width - pad * 2 - toggleW;
                            int textX = margin + pad + (textAreaW - tw) / 2;
                            Color textColor = new Color(30, 30, 30, a);
                            TextUtil.drawString(ctx, text, textX, textY, textColor.getRGB(), ClickGui.getInstance().font.getValue());
                            if (mx >= tx && mx <= tx + toggleW && my >= ty && my <= ty + toggleH && mouseDown && !this.notificationMouseWasDown && modCur != null) {
                                this.interactiveToggleInBanner = true;
                                modCur.toggle();
                            }

                            index++;
                        }
                    }
                }

                if (!removeList.isEmpty()) {
                    this.toggleBanners.removeAll(removeList);
                }

                this.notificationMouseWasDown = mouseDown;
            }
        }
    }

    private void renderPotionListLegacy(DrawContext ctx) {
        if (mc.player != null) {
            int margin = 14;
            int startX = margin + 2;
            int startY = margin + 92;
            int pillH = 14;
            int pillPad = 6;
            int idx = 0;

            for (StatusEffectInstance se : mc.player.getStatusEffects()) {
                String name = ((StatusEffect)se.getEffectType().value()).getName().getString();
                int ticks = se.getDuration();
                int totalSec = Math.max(0, ticks / 20);
                int mm = totalSec / 60;
                int ss = totalSec % 60;
                String time = String.format("%d:%02d", mm, ss);
                String text = name + " " + time;
                int tw = ClickGui.getInstance().font.getValue() ? (int)FontManager.ui.getWidth(text) : (int)TextUtil.getWidth(text);
                int pillW = tw + pillPad * 2;
                int y = startY + idx * (pillH + 4);
                int a = 180;
                Render2DUtil.drawRoundedRect(ctx.getMatrices(), startX, y, pillW, pillH, 4.0F, new Color(255, 255, 255, a));
                Render2DUtil.drawRoundedStroke(ctx.getMatrices(), startX, y, pillW, pillH, 4.0F, new Color(220, 224, 230, 160), 48);
                int tx = startX + pillPad;
                double ty = y + (pillH - (ClickGui.getInstance().font.getValue() ? FontManager.ui.getFontHeight() : TextUtil.getHeight())) / 2.0;
                TextUtil.drawString(ctx, text, tx, ty, new Color(30, 30, 30).getRGB(), ClickGui.getInstance().font.getValue());
                if (++idx >= 5) {
                    break;
                }
            }
        }
    }

    private void renderPotionBanners(DrawContext ctx) {
    }

    private String formatDuration(int ticks) {
        int totalSec = Math.max(0, ticks / 20);
        int mm = totalSec / 60;
        int ss = totalSec % 60;
        return String.format("%d:%02d", mm, ss);
    }

    private Module getModuleByDisplayName(String name) {
        for (Module m : this.modules) {
            if (m.getDisplayName().equalsIgnoreCase(name) || m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }

        return null;
    }

    public void addModule(Module module) {
        this.modules.add(module);
    }

    public Module getModuleByName(String string) {
        for (Module module : this.modules) {
            if (module.getName().equalsIgnoreCase(string)) {
                return module;
            }
        }

        return null;
    }

    private static class ToggleBanner {
        public final String name;
        public final boolean enabled;
        public final long start;

        public ToggleBanner(String name, boolean enabled, long start) {
            this.name = name;
            this.enabled = enabled;
            this.start = start;
        }
    }
}
