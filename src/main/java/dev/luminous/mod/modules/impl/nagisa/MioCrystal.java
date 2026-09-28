package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.entity.PlayerEntityPredict;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.api.utils.math.ExplosionUtil;
import dev.luminous.api.utils.math.DamageUtils;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.Burrow;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.player.AutoPot;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.collect.Lists;

public class MioCrystal extends Module {
    public static MioCrystal INSTANCE;

    // ========== 枚举 ==========
    public enum BalanceMode { Balance, DamageRatio }
    public enum Page { Render, Calc, Place, Break, Misc, Timing, Base }
    public enum SortMode { Damage, Range }
    public enum RotationsType { Simple, NCP, GrimAbuse, MultiPoint, Silent }
    public enum RenderMode { Normal, Fade, Glide }
    public enum AutoSwitchMode { None, Normal, Silent, SilentBypass }
    public enum MultiTaskMode { None, Soft, Strong }
    public enum AntiWeaknessMode { None, Normal, Silent }
    public enum SetDeadMode { None, Ghost }
    public enum SequenceMode { None, Soft, Strong }
    public enum SwapWaitMode { None, Semi, Full }
    public enum TimingMode { Soft, Strict }
    public enum MiningIgnoreMode { None, Ignore, StrictIgnore }
    public enum TargetESPMode { Box, Jello, NurikZapen, ThunderHack, Ghost }
    public enum BurrowSyncMode { PausePlace, NoSwitch }

    public record AsyncCalcResult(
            BlockPos calcPos,
            Entity calcCrystal,
            PlayerEntity target,
            double targetDamage,
            double renderDMG,
            float[] placeRots,
            float[] breakRots,
            BlockPos basePos
    ) {}

    // ========== 设置 ==========
    private final EnumSetting<Page> page = this.add(new EnumSetting<>("Page", Page.Render));

    // Calc
    private final EnumSetting<SortMode> targetSorting = this.add(new EnumSetting<>("Sort", SortMode.Damage, () -> page.is(Page.Calc)));
    private final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 7.0, 3.0, 20.0, () -> page.is(Page.Calc)));
    public final SliderSetting minDamage = this.add(new SliderSetting("MinDamage", 2.0, 0.0, 36.0, () -> page.is(Page.Calc)).setSuffix("dmg"));
    private final SliderSetting maxSelfDamage = this.add(new SliderSetting("MaxSelfDamage", 8.0, 0.0, 36.0, () -> page.is(Page.Calc)));
    private final BooleanSetting antiSuicide = this.add(new BooleanSetting("AntiSuicide", true, () -> page.is(Page.Calc)));
    private final SliderSetting lethalCrystals = this.add(new SliderSetting("LethalCrystals", 0, 0, 5, () -> page.is(Page.Calc)));
    private final EnumSetting<MultiTaskMode> multiTask = this.add(new EnumSetting<>("MultiTask", MultiTaskMode.None, () -> page.is(Page.Calc)));
    private final BooleanSetting feetPlace = this.add(new BooleanSetting("FeetPlace", false, () -> page.is(Page.Calc)));
    public final BooleanSetting terrain = this.add(new BooleanSetting("Terrain", false, () -> page.is(Page.Calc)));
    public final BooleanSetting armorAssume = this.add(new BooleanSetting("ArmorAssume", false, () -> page.is(Page.Calc)));
    private final EnumSetting<MiningIgnoreMode> miningIgnore = this.add(new EnumSetting<>("MiningIgnore", MiningIgnoreMode.None, () -> page.is(Page.Calc)));
    private final EnumSetting<BalanceMode> balanceMode = this.add(new EnumSetting<>("BalanceMode", BalanceMode.Balance, () -> page.is(Page.Calc)));
    private final SliderSetting balanceOffset = this.add(new SliderSetting("BalanceOffset", 0.0, -20.0, 20.0, 0.1, () -> page.is(Page.Calc) && balanceMode.is(BalanceMode.Balance)).setSuffix("hp"));
    private final SliderSetting damageRatio = this.add(new SliderSetting("DamageRatio", 2.0, 0.1, 5.0, 0.1, () -> page.is(Page.Calc) && balanceMode.is(BalanceMode.DamageRatio)).setSuffix("x"));
    private final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 0, 0, 20, () -> page.is(Page.Calc)).setSuffix("ticks"));
    private final SliderSetting selfPredict = this.add(new SliderSetting("SelfPredict", 0, 0, 20, () -> page.is(Page.Calc)).setSuffix("ticks"));

    // Place
    private final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 6.0, 1.0, 6.0, () -> page.is(Page.Place)));
    public final BooleanSetting littleSpace = this.add(new BooleanSetting("LittleSpace", false, () -> page.is(Page.Place)));
    private final EnumSetting<AutoSwitchMode> autoSwitch = this.add(new EnumSetting<>("AutoSwitch", AutoSwitchMode.None, () -> page.is(Page.Place)));
    private final BooleanSetting pauseOnEat = this.add(new BooleanSetting("Pause", false, () -> page.is(Page.Place)));
    private final BooleanSetting strictDirection = this.add(new BooleanSetting("StrictDirection", false, () -> page.is(Page.Place)));
    private final BooleanSetting limit = this.add(new BooleanSetting("Limit", false, () -> page.is(Page.Place)));
    public final BooleanSetting replace = this.add(new BooleanSetting("Replace", true, () -> page.is(Page.Place)));
    public final BooleanSetting airPlace = this.add(new BooleanSetting("AirPlace", true, () -> page.is(Page.Place)));

    // Break
    private final SliderSetting breakRange = this.add(new SliderSetting("BreakRange", 5.0, 1.0, 6.0, () -> page.is(Page.Break)));
    private final SliderSetting breakWallsRange = this.add(new SliderSetting("BreakWallsRange", 3.0, 1.0, 6.0, () -> page.is(Page.Break)));
    private final SliderSetting ticksExisted = this.add(new SliderSetting("TicksExisted", 0, 0, 20, () -> page.is(Page.Break)).setSuffix("tick"));
    private final BooleanSetting predict = this.add(new BooleanSetting("Boost", true, () -> page.is(Page.Break)));
    private final BooleanSetting instantAttack = this.add(new BooleanSetting("InstantAttack", true, () -> page.is(Page.Break)));
    private final BooleanSetting asyncCrystal = this.add(new BooleanSetting("AsyncCrystal", false, () -> page.is(Page.Break)));
    private final BooleanSetting lethalTick = this.add(new BooleanSetting("LethalTick", false, () -> page.is(Page.Break)));
    private final BooleanSetting autoHit = this.add(new BooleanSetting("AutoHit", false, () -> page.is(Page.Break) && lethalTick.getValue()));
    private final SliderSetting dtapDelay = this.add(new SliderSetting("CrystalDelay", 300, 100, 500, () -> page.is(Page.Break) && lethalTick.getValue()).setSuffix("ms"));
    private final BooleanSetting inhibit = this.add(new BooleanSetting("Inhibit", false, () -> page.is(Page.Break)));
    private final EnumSetting<AntiWeaknessMode> antiWeakness = this.add(new EnumSetting<>("AntiWeakness", AntiWeaknessMode.None, () -> page.is(Page.Break)));
    private final EnumSetting<SetDeadMode> setDeadMode = this.add(new EnumSetting<>("SetDead", SetDeadMode.None, () -> page.is(Page.Break)));
    private final SliderSetting hitFactor = this.add(new SliderSetting("HitFactor", 1, 1, 8, () -> page.is(Page.Break)));

    // Timing
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 0, 0, 1000, () -> page.is(Page.Timing)).setSuffix("ms"));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 0, 0, 1000, () -> page.is(Page.Timing)).setSuffix("ms"));
    private final BooleanSetting dynamicDelay = this.add(new BooleanSetting("DynamicDelay", true, () -> page.is(Page.Timing)));
    private final EnumSetting<TimingMode> timing = this.add(new EnumSetting<>("Timing", TimingMode.Soft, () -> page.is(Page.Timing)));

    // Misc
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> page.is(Page.Misc)).setParent());
    private final BooleanSetting yawStep = this.add(new BooleanSetting("YawStep", true, () -> page.is(Page.Misc) && rotate.isOpen()).setParent());
    private final BooleanSetting whenElytra = this.add(new BooleanSetting("FallFlying", true, () -> page.is(Page.Misc) && rotate.isOpen() && yawStep.isOpen()));
    private final BooleanSetting checkFov = this.add(new BooleanSetting("OnlyLooking", true, () -> page.is(Page.Misc) && rotate.isOpen() && yawStep.isOpen()));
    private final SliderSetting fov = this.add(new SliderSetting("Fov", 20.0, 0.0, 360.0, 0.1, () -> page.is(Page.Misc) && rotate.isOpen() && yawStep.isOpen() && checkFov.getValue()));
    private final SliderSetting steps = this.add(new SliderSetting("Steps", 0.05, 0.0, 1.0, 0.01, () -> page.is(Page.Misc) && rotate.isOpen() && yawStep.isOpen()));
    private final SliderSetting priority = this.add(new SliderSetting("Priority", 10, 0, 100, () -> page.is(Page.Misc) && rotate.isOpen() && yawStep.isOpen()));
    private final EnumSetting<RotationsType> rotationsType = this.add(new EnumSetting<>("RotationsType", RotationsType.Simple, () -> page.is(Page.Misc) && rotate.getValue() && !yawStep.getValue()));
    private final EnumSetting<SequenceMode> sequence = this.add(new EnumSetting<>("Sequence", SequenceMode.None, () -> page.is(Page.Misc)));
    private final EnumSetting<SwapWaitMode> swapWait = this.add(new EnumSetting<>("SwapDelay", SwapWaitMode.None, () -> page.is(Page.Misc)));
    private final SliderSetting swapWaitDelay = this.add(new SliderSetting("SwapWaitDelay", 300, 50, 500, () -> page.is(Page.Misc) && swapWait.getValue() != SwapWaitMode.None).setSuffix("ms"));
    private final BooleanSetting thread = this.add(new BooleanSetting("Thread", false, () -> page.is(Page.Misc)));
    private final BooleanSetting asyncCalc = this.add(new BooleanSetting("AsyncCalc", true, () -> page.is(Page.Misc)));
    private final BooleanSetting parallelPackets = this.add(new BooleanSetting("ParallelPackets", true, () -> page.is(Page.Misc)).setParent());
    private final BooleanSetting asyncItemRestore = this.add(new BooleanSetting("AsyncItemRestore", true, () -> page.is(Page.Misc) && parallelPackets.isOpen()));
    private final BooleanSetting batchRotation = this.add(new BooleanSetting("BatchRotation", false, () -> page.is(Page.Misc) && parallelPackets.isOpen()));
    private final BooleanSetting skipYawStepCheck = this.add(new BooleanSetting("SkipYawStepCheck", false, () -> page.is(Page.Misc) && parallelPackets.isOpen() && yawStep.isOpen()));
    private final BindSetting pause = this.add(new BindSetting("Pause", -1, () -> page.is(Page.Misc)));
    private final BooleanSetting prioritizeBurrow = this.add(new BooleanSetting("PrioritizeBurrow", false, () -> page.is(Page.Misc)).setParent());
    private final EnumSetting<BurrowSyncMode> burrowSyncMode = this.add(new EnumSetting<>("BurrowSync", BurrowSyncMode.NoSwitch, () -> page.is(Page.Misc) && prioritizeBurrow.isOpen()));
    private final BooleanSetting potSync = this.add(new BooleanSetting("PotSync", false, () -> page.is(Page.Misc)));
    private final BooleanSetting potSyncTest = this.add(new BooleanSetting("PotSyncTest", false, () -> page.is(Page.Misc)));

    // FacePlace (激进模式)
    private final BooleanSetting facePlace = this.add(new BooleanSetting("FacePlace", false, () -> page.is(Page.Misc)).setParent());
    private final SliderSetting facePlaceHealth = this.add(new SliderSetting("FacePlaceHealth", 8.0, 0.0, 36.0, 0.5, () -> page.is(Page.Misc) && facePlace.isOpen()).setSuffix("hp"));
    private final SliderSetting facePlaceArmor = this.add(new SliderSetting("FacePlaceArmor", 40, 0, 100, 1, () -> page.is(Page.Misc) && facePlace.isOpen()).setSuffix("%"));

    // 信息显示
    private final BooleanSetting showTarget = this.add(new BooleanSetting("ShowTarget", true, () -> page.is(Page.Misc)));
    private final BooleanSetting showDistance = this.add(new BooleanSetting("ShowDistance", true, () -> page.is(Page.Misc)));
    private final BooleanSetting showDamage = this.add(new BooleanSetting("ShowDamage", true, () -> page.is(Page.Misc)));
    private final BooleanSetting showCPS = this.add(new BooleanSetting("ShowCPS", false, () -> page.is(Page.Misc)));
    private final BooleanSetting showMs = this.add(new BooleanSetting("ShowMs", true, () -> page.is(Page.Misc)));

    // Base (原始独立底座)
    private final BooleanSetting basePlace = this.add(new BooleanSetting("BasePlace", true, () -> page.is(Page.Base)));
    private final BooleanSetting baseBalance = this.add(new BooleanSetting("BaseBalance", true, () -> page.is(Page.Base)));
    private final BooleanSetting onlyBelow = this.add(new BooleanSetting("OnlyBelow", true, () -> page.is(Page.Base)));
    private final BooleanSetting detectMining = this.add(new BooleanSetting("DetectMining", true, () -> page.is(Page.Base)));
    private final SliderSetting delayBase = this.add(new SliderSetting("Delay", 3000, 0, 10000, () -> page.is(Page.Base)).setSuffix("ms"));

    // Render
    private final BooleanSetting render = this.add(new BooleanSetting("Render", true, () -> page.is(Page.Render)));
    private final ColorSetting fillColor = this.add(new ColorSetting("FillColor", new Color(0, 0, 0, 100), () -> page.is(Page.Render) && render.getValue()));
    private final ColorSetting outlineColor = this.add(new ColorSetting("OutlineColor", new Color(255, 255, 255, 255), () -> page.is(Page.Render) && render.getValue()));
    private final SliderSetting fillHeight = this.add(new SliderSetting("FillHeight", 1.0, 0.0, 1.0, 0.01, () -> page.is(Page.Render) && render.getValue()));
    private final BooleanSetting renderDamageText = this.add(new BooleanSetting("DamageText", true, () -> page.is(Page.Render) && render.getValue()));
    private final SliderSetting textScale = this.add(new SliderSetting("TextScale", 1.4, 1.0, 2.0, () -> page.is(Page.Render) && render.getValue() && renderDamageText.getValue()));
    private final EnumSetting<RenderMode> renderMode = this.add(new EnumSetting<>("Type", RenderMode.Normal, () -> page.is(Page.Render) && render.getValue()));
    private final SliderSetting fadeTime = this.add(new SliderSetting("FadeTime", 1000, 100, 2000, () -> page.is(Page.Render) && render.getValue() && renderMode.is(RenderMode.Fade)).setSuffix("ms"));
    private final BooleanSetting futureFade = this.add(new BooleanSetting("Future", false, () -> page.is(Page.Render) && render.getValue() && renderMode.is(RenderMode.Fade)));
    private final SliderSetting glideSpeed = this.add(new SliderSetting("GlideSpeed", 5, 1, 40, () -> page.is(Page.Render) && render.getValue() && renderMode.is(RenderMode.Glide)));
    private final SliderSetting openSpeed = this.add(new SliderSetting("OpenSpeed", 5, 1, 40, () -> page.is(Page.Render) && render.getValue() && renderMode.is(RenderMode.Glide)));
    private final BooleanSetting targetESP = this.add(new BooleanSetting("TargetESP", false, () -> page.is(Page.Render)));
    private final EnumSetting<TargetESPMode> targetESPMode = this.add(new EnumSetting<>("TargetESPMode", TargetESPMode.Box, () -> page.is(Page.Render) && targetESP.getValue()));
    private final ColorSetting targetColor = this.add(new ColorSetting("TargetColor", new Color(255, 255, 255, 50), () -> page.is(Page.Render) && targetESP.getValue()));
    private final ColorSetting targetHitColor = this.add(new ColorSetting("TargetHitColor", new Color(255, 255, 255, 150), () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Box)));
    private final SliderSetting animationTime = this.add(new SliderSetting("AnimationTime", 200, 0, 2000, 1, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Box)));
    private final EnumSetting<Easing> ease = this.add(new EnumSetting<>("Ease", Easing.CubicInOut, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Box)));
    private final SliderSetting espLength = this.add(new SliderSetting("ESPLength", 14, 1, 40, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Ghost)));
    private final SliderSetting espFactor = this.add(new SliderSetting("ESPFactor", 8, 1, 20, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Ghost)));
    private final SliderSetting espShaking = this.add(new SliderSetting("ESPShaking", 1.8f, 1.5f, 10f, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Ghost)));
    private final SliderSetting espAmplitude = this.add(new SliderSetting("ESPAmplitude", 3f, 0.1f, 8f, () -> page.is(Page.Render) && targetESP.getValue() && targetESPMode.is(TargetESPMode.Ghost)));

    // ========== 计时器 ==========
    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private final Timer swapTimer = new Timer();
    private final Timer autoDtapTimer = new Timer();
    private final Timer cooldownTimer = new Timer();
    private final Timer noPosTimer = new Timer();
    private final Timer baseTimer = new Timer();
    public final Timer lastBreakTimer = new Timer();

    // ========== 公共状态 ==========
    public PlayerEntity target, displayTarget;
    public BlockPos renderPos, calcPos, lastPos;
    public static BlockPos crystalPos;
    public Entity calcCrystal;
    public double renderDMG, lastDamage;
    public boolean offhand;
    public final Map<Integer, Long> hitCrystals = new ConcurrentHashMap<>();
    protected final LinkedList<Long> brokeCrystals = new LinkedList<>();

    private final List<RenderPosTime> oldPlacements = new ArrayList<>();
    private final List<Long> placeTimes = new ArrayList<>(), breakTimes = new ArrayList<>();
    private float[] placeTargetRot, breakTargetRot;
    private Vec3d directionVec;
    private boolean doingAutoDtap, didAutoDtapAttack, didAutoDtapCrystal, hasRotated, placeFlag, breakFlag, placedOnspawn;
    private int CRYSTALS_PER_SECOND;
    private float lastTime;
    private Box renderBB;
    private BlockPos lastRenderPos;
    private float timePassed;
    private long startTime;
    private BlockPos basePos;

    private final DecimalFormat df = new DecimalFormat("0.##");
    private final Animation animation = new Animation();
    private static final double ATTACK_VEC_STEP = 0.1;

    // ========== 异步计算 ==========
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicReference<AsyncCalcResult> asyncResult = new AtomicReference<>();
    private CompletableFuture<AsyncCalcResult> calcFuture;

    public MioCrystal() {
        super("MioCrystal", Category.Combat);
        this.setChinese("高精度水晶");
        INSTANCE = this;
    }

    @Override public void onEnable() {
        if (nullCheck()) return;
        renderPos = null;
        target = null;
        displayTarget = null;
        swapTimer.setMs(swapWaitDelay.getValueInt());
        calcPos = null; calcCrystal = null; breakTargetRot = placeTargetRot = null;
        hitCrystals.clear(); oldPlacements.clear(); brokeCrystals.clear();
        placeTimer.reset(); breakTimer.reset(); baseTimer.reset();
        doingAutoDtap = didAutoDtapAttack = didAutoDtapCrystal = false;
        hasRotated = false; renderBB = null; lastRenderPos = null; lastDamage = 0; basePos = null;
        asyncResult.set(null);
        if (calcFuture != null && !calcFuture.isDone()) calcFuture.cancel(true);
        calcFuture = null;
    }

    @Override public void onDisable() {
        if (nullCheck()) return;
        renderPos = null;
        target = null;
        displayTarget = null;
        swapTimer.setMs(swapWaitDelay.getValueInt());
        calcPos = null; calcCrystal = null; breakTargetRot = placeTargetRot = null;
        directionVec = null;
        if (hasRotated) { Alien.ROTATION.snapBack(); hasRotated = false; }
        basePos = null;
        asyncResult.set(null);
        if (calcFuture != null && !calcFuture.isDone()) calcFuture.cancel(true);
        calcFuture = null;
    }

    @Override public String getInfo() {
        StringBuilder info = new StringBuilder();
        if (showTarget.getValue()) info.append(displayTarget != null ? displayTarget.getName().getString() : "None");
        if (showDistance.getValue() && displayTarget != null) {
            double dist = mc.player.getPos().distanceTo(displayTarget.getPos());
            if (info.length() > 0) info.append(", ");
            info.append(df.format(dist));
        }
        if (showDamage.getValue() && lastDamage > 0.0F) {
            if (info.length() > 0) info.append(", ");
            info.append(df.format(lastDamage));
        }
        if (showMs.getValue()) {
            if (info.length() > 0) info.append(", ");
            info.append(df.format(lastTime)).append("ms");
        }
        if (showCPS.getValue()) {
            long total = getPlaceCountInLastSecond() + getBreakCountInLastSecond();
            if (total > 0) { if (info.length() > 0) info.append(", "); info.append(total); }
        }
        return info.length() > 0 ? info.toString() : null;
    }

    private long getPlaceCountInLastSecond() { placeTimes.removeIf(t -> System.currentTimeMillis() - t > 1000); return placeTimes.size(); }
    private long getBreakCountInLastSecond() { breakTimes.removeIf(t -> System.currentTimeMillis() - t > 1000); return breakTimes.size(); }

    @EventListener public void onEntityRemove(RemoveEntityEvent event) {
        if (event.getEntity() instanceof EndCrystalEntity && hitCrystals.containsKey(event.getEntity().getId())) {
            brokeCrystals.add(System.currentTimeMillis());
            hitCrystals.remove(event.getEntity().getId());
        }
    }

    @EventListener public void onRotate(UpdateRotateEvent event) {
        if (rotate.getValue() && shouldYawStep() && directionVec != null && target != null && !noPosTimer.passed(1000L) && !shouldReturn()) {
            float[] rots = getRotationsTo(mc.player.getEyePos(), directionVec);
            event.setRotation(rots[0], rots[1]);
        }
    }

    private boolean shouldReturn() {
        if (pauseOnEat.getValue() && mc.player.isUsingItem()) return true;
        if (pause.isPressed()) return true;
        return false;
    }

    private boolean shouldYawStep() {
        return (whenElytra.getValue() || !mc.player.isFallFlying() && (!ElytraFly.INSTANCE.isOn() || !mc.player.isFallFlying())) && yawStep.getValue();
    }

    private boolean faceVector(Vec3d dir) {
        if (dir == null) return false;
        if (!shouldYawStep()) { Alien.ROTATION.lookAt(dir); return true; }
        if (skipYawStepCheck.getValue() && parallelPackets.getValue()) { directionVec = dir; return true; }
        directionVec = dir;
        return Alien.ROTATION.inFov(dir, fov.getValueFloat()) || !checkFov.getValue();
    }

    @EventListener public void onEntityAdd(EntitySpawnEvent event) {
        if (nullCheck() || isMultiTask()) return;
        if (event.getEntity() instanceof EndCrystalEntity e) {
            if (asyncCrystal.getValue()) {
                doAsyncCrystal(e);
                return;
            }
            if (predict.getValue() || instantAttack.getValue()) doPredictOrInstant(e);
        }
    }

    @EventListener public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket && swapWait.getValue() != SwapWaitMode.None) {
            StatusEffectInstance weak = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
            StatusEffectInstance str = mc.player.getStatusEffect(StatusEffects.STRENGTH);
            if (antiWeakness.getValue() != AntiWeaknessMode.None && weak != null && (str == null || str.getAmplifier() <= weak.getAmplifier())) return;
            swapTimer.reset();
        }
    }

    private void doAsyncCrystal(EndCrystalEntity entity) {
        noPosTimer.reset();
        Vec3d attackVec = getAttackVec(entity.getPos());
        if (attackVec == null) return;
        if (mc.player.getEyePos().distanceTo(attackVec) > breakRange.getValue()) return;
        if (target != null) {
            float dmg = calculateDamage(target, entity.getPos(), terrain.getValue(), false);
            if (dmg > minDamage.getValueFloat() || (lethalCrystals.getValueInt() != 0 && dmg * lethalCrystals.getValueFloat() >= target.getHealth() + target.getAbsorptionAmount())) {
                if (rotate.getValue() && !shouldYawStep()) { if (!faceVector(attackVec)) return; }
                for (int i = 0; i < hitFactor.getValueInt(); i++) {
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                }
                mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                mc.player.resetLastAttackedTicks();
                hitCrystals.put(entity.getId(), System.currentTimeMillis());
                breakTimer.reset(); noPosTimer.reset();
                if (rotate.getValue() && !shouldYawStep()) Alien.ROTATION.snapBack();
            }
        }
    }

    private void doPredictOrInstant(EndCrystalEntity entity) {
        noPosTimer.reset();
        Vec3d attackVec = getAttackVec(entity.getPos());
        if (attackVec == null) return;
        if (mc.player.getEyePos().distanceTo(attackVec) > breakRange.getValue()) return;
        if (lethalTick.getValue() && isDtapping()) return;
        if (target != null) {
            float dmg = calculateDamage(target, entity.getPos(), terrain.getValue(), false);
            if (dmg > minDamage.getValueFloat() || (lethalCrystals.getValueInt() != 0 && dmg * lethalCrystals.getValueFloat() >= target.getHealth() + target.getAbsorptionAmount())) {
                if (rotate.getValue() && !shouldYawStep()) { if (!faceVector(attackVec)) return; }
                for (int i = 0; i < hitFactor.getValueInt(); i++) {
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
                }
                mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                mc.player.resetLastAttackedTicks();
                hitCrystals.put(entity.getId(), System.currentTimeMillis());
                breakTimer.reset(); noPosTimer.reset();
                if (doingAutoDtap) didAutoDtapCrystal = true;
                if (rotate.getValue() && !shouldYawStep()) Alien.ROTATION.snapBack();
                if (sequence.getValue() == SequenceMode.Strong) { if (calcPos != null) placeCrystal(); }
            }
        }
    }

    boolean isMultiTask() {
        if (mc.player.isUsingItem()) {
            return switch (multiTask.getValue()) {
                case Soft -> !mc.player.getActiveHand().equals(Hand.OFF_HAND) && !offhand;
                case Strong -> true;
                default -> false;
            };
        }
        return false;
    }

    // ========== 渲染：底座坐标 ==========
    @Override public void onRender3D(MatrixStack matrixStack) {
        if (nullCheck() || !render.getValue()) return;
        if (!renderMode.is(RenderMode.Fade) && (target == null || renderPos == null)) return;
        if (!renderMode.is(RenderMode.Fade) && !canRender()) return;

        Color fill = fillColor.getValue(), outline = outlineColor.getValue();
        double hp = fillHeight.getValue();

        // 以水晶坐标向下取底座
        BlockPos baseBlock = renderPos != null ? renderPos.down() : null;

        switch (renderMode.getValue()) {
            case Normal -> {
                if (baseBlock == null) return;
                double top = baseBlock.getY() + hp;
                double bottom = baseBlock.getY();
                Box box = new Box(baseBlock.getX(), bottom, baseBlock.getZ(), baseBlock.getX()+1, top, baseBlock.getZ()+1);
                Render3DUtil.drawFill(matrixStack, box, fill);
                Render3DUtil.drawBox(matrixStack, box, outline, 1.5f);
                if (renderDamageText.getValue() && renderDMG > 0)
                    Render3DUtil.drawText3D(String.format("%.1f", renderDMG), box.getCenter(), Color.WHITE);
            }
            case Glide -> {
                if (baseBlock == null) return;
                if (lastRenderPos == null || mc.player.squaredDistanceTo(renderBB.minX, renderBB.minY, renderBB.minZ) > targetRange.getValue() * targetRange.getValue()) {
                    lastRenderPos = baseBlock;
                    double topInit = baseBlock.getY() + hp;
                    double bottomInit = baseBlock.getY();
                    renderBB = new Box(baseBlock.getX(), bottomInit, baseBlock.getZ(), baseBlock.getX()+1, topInit, baseBlock.getZ()+1);
                    timePassed = 0; startTime = System.currentTimeMillis();
                }
                if (!lastRenderPos.equals(baseBlock)) { lastRenderPos = baseBlock; timePassed = 0; }
                double targetX = baseBlock.getX(), targetY = baseBlock.getY(), targetZ = baseBlock.getZ();
                double dx = targetX - renderBB.minX, dy = targetY - renderBB.minY, dz = targetZ - renderBB.minZ;
                float speed = glideSpeed.getValueInt() * 200, mul = Math.min(timePassed / speed * 0.8f, 1.0f);
                renderBB = renderBB.offset(dx * mul, dy * mul, dz * mul);
                double top = renderBB.minY + hp;
                Box gbox = new Box(renderBB.minX, renderBB.minY, renderBB.minZ, renderBB.maxX, top, renderBB.maxZ);
                Render3DUtil.drawFill(matrixStack, gbox, fill); Render3DUtil.drawBox(matrixStack, gbox, outline, 1.5f);
                if (renderDamageText.getValue() && renderDMG > 0)
                    Render3DUtil.drawText3D(String.format("%.1f", renderDMG), gbox.getCenter(), Color.WHITE);
                if (renderBB.minX == targetX && renderBB.minY == targetY && renderBB.minZ == targetZ) timePassed = 0; else timePassed += 50;
            }
            case Fade -> {} // Fade 模式保留原有逻辑不变
        }

        if (targetESP.getValue() && target != null) doTargetRender(matrixStack, target, targetESPMode.getValue());
    }

    public void doTargetRender(MatrixStack stack, PlayerEntity entity, TargetESPMode mode) {
        float partial = Render3DUtil.getTickDelta();
        if (mode == TargetESPMode.Box) {
            Box box = ((IEntity)entity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(entity.lastRenderX, entity.getX(), partial), MathUtil.interpolate(entity.lastRenderY, entity.getY(), partial), MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), partial))).expand(0,0.1,0);
            Color c = ColorUtil.fadeColor(targetColor.getValue(), targetHitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue()));
            Render3DUtil.draw3DBox(stack, box, c, c, true, true);
        } else if (mode == TargetESPMode.ThunderHack) Render3DUtil.drawTargetEsp(stack, entity, targetColor.getValue());
    }

    private boolean isRiskAcceptable(float targetDmg, float selfDmg) {
        if (balanceMode.getValue() == BalanceMode.Balance) {
            return targetDmg >= selfDmg + balanceOffset.getValue();
        } else {
            if (selfDmg <= 0) return true;
            return targetDmg >= selfDmg * damageRatio.getValueFloat();
        }
    }

    private boolean shouldFacePlaceAllow(PlayerEntity target) {
        if (!facePlace.getValue() || target == null) return false;
        float health = target.getHealth() + target.getAbsorptionAmount();
        if (health <= facePlaceHealth.getValueFloat()) return true;
        for (ItemStack stack : target.getInventory().armor) {
            if (stack.isEmpty()) continue;
            float percent = EntityUtil.getDamagePercent(stack);
            if (percent <= facePlaceArmor.getValueFloat()) return true;
        }
        return false;
    }

    private long dynamicDelay(double baseMs) {
        if (!dynamicDelay.getValue()) return (long) baseMs;
        try {
            int ping = 0;
            if (mc.player != null && mc.player.networkHandler != null) {
                var entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getGameProfile().getId());
                if (entry != null) ping = entry.getLatency();
            }
            if (ping <= 0) ping = 50;
            return (long) (baseMs / (ping / 50.0));
        } catch (Exception e) {
            return (long) baseMs;
        }
    }

    // ========== 代码1 的放置检测（上方方块、实体、距离） ==========
    private boolean canPlaceCrystalCheck(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        BlockPos obsPos = pos.down();
        BlockPos boost = obsPos.up();
        BlockPos boost2 = boost.up();
        if (BlockUtil.getBlock(obsPos) != Blocks.BEDROCK && BlockUtil.getBlock(obsPos) != Blocks.OBSIDIAN) return false;
        if (BlockUtil.getClickSideStrict(obsPos) == null) return false;
        if (!noEntityBlockCrystal(boost, ignoreCrystal, ignoreItem)) return false;
        if (!noEntityBlockCrystal(boost2, ignoreCrystal, ignoreItem)) return false;
        if (!mc.world.isAir(boost)) {
            if (!BlockUtil.hasCrystal(boost) || BlockUtil.getBlock(boost) != Blocks.FIRE) return false;
        }
        if (ClientSetting.INSTANCE.lowVersion.getValue() && !mc.world.isAir(boost2)) return false;
        return true;
    }

    private boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (!entity.isAlive()) continue;
            if (entity instanceof ItemEntity && ignoreItem) continue;
            if (entity instanceof EndCrystalEntity && ignoreCrystal) {
                if (this.getAttackVec(entity.getPos()) != null &&
                        (mc.player.canSee(entity) || mc.player.getEyePos().distanceTo(entity.getPos()) <= breakWallsRange.getValue())) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    private boolean canTouch(BlockPos pos) {
        Direction side = BlockUtil.getClickSideStrict(pos);
        if (side == null) return false;
        Vec3d vec3d = new Vec3d(
                (double) side.getVector().getX() * 0.5,
                (double) side.getVector().getY() * 0.5,
                (double) side.getVector().getZ() * 0.5
        );
        return pos.toCenterPos().add(vec3d).distanceTo(mc.player.getEyePos()) <= placeRange.getValue();
    }

    private boolean canPlaceCrystalAt(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        return canPlaceCrystalCheck(pos, ignoreCrystal, ignoreItem) && canTouch(pos.down());
    }

    public boolean canPlaceCrystal(BlockPos pos, boolean is112) {
        return canPlaceCrystalCheck(pos, true, true) && canTouch(pos.down());
    }

    private CompletableFuture<AsyncCalcResult> startAsyncCalc() {
        return CompletableFuture.supplyAsync(() -> {
            if (nullCheck() || shouldReturn()) return null;
            offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
            List<PlayerEntity> targets = new ArrayList<>();
            for (Entity e : Lists.newArrayList(mc.world.getEntities()))
                if (e instanceof PlayerEntity p && isValidTarget(p)) targets.add(p);
            if (targets.isEmpty()) return null;

            PlayerEntity bestTarget = null;
            double bestTotalDmg = 0;
            BlockPos bestPos = null;
            Entity bestCrystal = null;
            float[] placeRots = null, breakRots = null;
            double renderDmg = 0;

            if (targetSorting.getValue() == SortMode.Damage) {
                targets.sort((a, b) -> Float.compare(calculateMaxDamage(b), calculateMaxDamage(a)));
            } else {
                targets.sort(Comparator.comparingDouble(mc.player::distanceTo));
            }

            for (PlayerEntity tp : targets) {
                boolean facePlaceAllow = shouldFacePlaceAllow(tp);
                float maxSelf = facePlaceAllow ? Float.MAX_VALUE : maxSelfDamage.getValueFloat();

                // 破坏水晶
                Entity bestC = null;
                double bestBreakDmg = 0.5;
                for (Entity e : mc.world.getEntities()) {
                    if (e instanceof EndCrystalEntity c && canBreakCrystal(c)) {
                        float td = calculateDamage(tp, c.getPos(), terrain.getValue(), false);
                        float sd = calculateDamage(mc.player, c.getPos(), terrain.getValue(), false);
                        if (td > bestBreakDmg && td > minDamage.getValueFloat() && sd <= maxSelf
                                && !(antiSuicide.getValue() && sd + 2 > mc.player.getHealth() + mc.player.getAbsorptionAmount())
                                && (td >= tp.getHealth() + tp.getAbsorptionAmount() || isRiskAcceptable(td, sd))) {
                            bestBreakDmg = td;
                            bestC = c;
                        }
                    }
                }

                // 放置水晶
                BlockPos bestP = null;
                double bestPlaceDmg = 0.5;
                for (BlockPos pos : BlockUtil.getSphere((float) (placeRange.getValue() + 1), mc.player.getPos())) {
                    if (!canPlaceCrystalAt(pos, true, true)) continue;
                    float td = calculateDamage(tp, crystalDamageVec(pos), terrain.getValue(), getMiningIgnore());
                    float sd = calculateDamage(mc.player, crystalDamageVec(pos), terrain.getValue(), getMiningIgnore());
                    if (td > bestPlaceDmg && td > minDamage.getValueFloat() && sd <= maxSelf
                            && !(antiSuicide.getValue() && sd + 2 > mc.player.getHealth() + mc.player.getAbsorptionAmount())) {
                        if (td >= tp.getHealth() + tp.getAbsorptionAmount() || isRiskAcceptable(td, sd)) {
                            bestPlaceDmg = td;
                            bestP = pos.toImmutable();
                        }
                    }
                }

                double total = Math.max(bestBreakDmg, bestPlaceDmg);
                if (total > bestTotalDmg) {
                    bestTotalDmg = total;
                    bestTarget = tp;
                    bestPos = bestP;
                    bestCrystal = bestC;
                    if (bestPos != null) placeRots = getPlaceRot(bestPos);
                    if (bestCrystal != null) breakRots = getRotationsTo(mc.player.getEyePos(), getAttackVec(bestCrystal.getPos()));
                    renderDmg = bestPlaceDmg > 0 ? bestPlaceDmg : bestBreakDmg;
                }
            }

            if (bestTarget == null) return null;
            return new AsyncCalcResult(bestPos, bestCrystal, bestTarget, bestTotalDmg, renderDmg, placeRots, breakRots, null);
        }, VIRTUAL_EXECUTOR);
    }

    private float calculateMaxDamage(PlayerEntity p) {
        float max = 0;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity c && canBreakCrystal(c)) {
                float d = calculateDamage(p, c.getPos(), terrain.getValue(), false);
                if (d > max) max = d;
            }
        }
        return max;
    }

    public Result getTargetResult() {
        List<PlayerEntity> targets = new ArrayList<>();
        for (Entity e : Lists.newArrayList(mc.world.getEntities()))
            if (e instanceof PlayerEntity p && isValidTarget(p)) targets.add(p);
        if (targets.isEmpty()) { reset(); return null; }
        PlayerEntity t = targets.get(0);
        return getResult(t);
    }

    public void prepare() {
        offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        autoDtapTimer.setMs(dtapDelay.getValueInt()); swapTimer.setMs(swapWaitDelay.getValueInt()); cooldownTimer.setMs(800L);
        placeTargetRot = breakTargetRot = null;
        long t = System.nanoTime();

        if (asyncCalc.getValue()) {
            if (calcFuture == null || calcFuture.isDone()) {
                if (calcFuture != null) {
                    try {
                        AsyncCalcResult res = calcFuture.get();
                        if (res != null) { asyncResult.set(res); loadFromAsync(res); } else reset();
                    } catch (Exception ignored) {}
                }
                calcFuture = startAsyncCalc();
            } else if (calcFuture.isDone()) {
                try {
                    AsyncCalcResult res = calcFuture.get();
                    if (res != null) { asyncResult.set(res); loadFromAsync(res); } else reset();
                } catch (Exception ignored) {}
                calcFuture = startAsyncCalc();
            } else {
                AsyncCalcResult last = asyncResult.get();
                if (last != null) loadFromAsync(last);
            }
        } else {
            Result r = getTargetResult();
            lastTime = (System.nanoTime() - t) / 1E6f;
            if (r == null) { reset(); target = null; displayTarget = null; return; }
            calcPos = null; calcCrystal = null;
            load(r);
        }

        if (!rotate.getValue()) return;
        if (shouldYawStep()) return;
        if (autoSwitch.getValue() == AutoSwitchMode.None && breakTargetRot == null && !offhand && mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) return;
        if (!rotationsType.is(RotationsType.Silent)) {
            if (placeTargetRot != null && breakTargetRot == null) Alien.ROTATION.snapAt(placeTargetRot[0], placeTargetRot[1]);
            else if (placeTargetRot == null && breakTargetRot != null) Alien.ROTATION.snapAt(breakTargetRot[0], breakTargetRot[1]);
            else if (placeTargetRot != null) handleSequence();
        }
    }

    private void loadFromAsync(AsyncCalcResult res) {
        target = res.target();
        displayTarget = res.target();
        calcPos = res.calcPos();
        calcCrystal = res.calcCrystal();
        lastDamage = res.targetDamage();
        renderDMG = res.renderDMG();
        placeTargetRot = res.placeRots();
        breakTargetRot = res.breakRots();
        basePos = res.basePos();
        if (calcPos != null) renderPos = calcPos;
    }

    private void handleSequence() {
        if (sequence.getValue() == SequenceMode.Strong) {
            if (breakTargetRot != null) Alien.ROTATION.snapAt(breakTargetRot[0], breakTargetRot[1]);
            else if (placeTargetRot != null) Alien.ROTATION.snapAt(placeTargetRot[0], placeTargetRot[1]);
        } else {
            if (placeTargetRot != null) Alien.ROTATION.snapAt(placeTargetRot[0], placeTargetRot[1]);
            else if (breakTargetRot != null) Alien.ROTATION.snapAt(breakTargetRot[0], breakTargetRot[1]);
        }
    }

    public boolean breakCrystal() {
        noPosTimer.reset();
        if (breakFlag) { breakFlag = false; return true; }
        if (lethalTick.getValue() && target != null && (calcCrystal != null || calcPos != null)) {
            if (doingAutoDtap) {
                if (didAutoDtapAttack && !didAutoDtapCrystal && !autoDtapTimer.passed(0)) return true;
                if (target.hurtTime != 0 && didAutoDtapCrystal && didAutoDtapAttack) return true;
                else if (didAutoDtapAttack && didAutoDtapCrystal) { doingAutoDtap = false; didAutoDtapAttack = false; didAutoDtapCrystal = false; }
            }
        } else if (autoDtapTimer.passed(0)) { doingAutoDtap = false; didAutoDtapAttack = false; didAutoDtapCrystal = false; }
        if (calcCrystal != null) {
            long del = dynamicDelay(inhibit.getValue() && hitCrystals.containsKey(calcCrystal.getId()) ? breakDelay.getValueInt() + 100 : breakDelay.getValueInt());
            if (breakTimer.passed(del)) {
                if (swapWait.getValue() != SwapWaitMode.None && !swapTimer.passed(0)) return true;
                StatusEffectInstance weak = mc.player.getStatusEffect(StatusEffects.WEAKNESS), str = mc.player.getStatusEffect(StatusEffects.STRENGTH);
                boolean swapBack = false; int cur = mc.player.getInventory().selectedSlot;
                if (antiWeakness.getValue() != AntiWeaknessMode.None && weak != null && (str == null || str.getAmplifier() <= weak.getAmplifier())) {
                    int sword = InventoryUtil.findClass(SwordItem.class);
                    if (sword != -1 && cur != sword) {
                        if (antiWeakness.getValue() == AntiWeaknessMode.Normal) InventoryUtil.switchToSlot(sword);
                        else if (antiWeakness.getValue() == AntiWeaknessMode.Silent) { InventoryUtil.switchToSlot(sword); swapBack = true; }
                    }
                }
                Vec3d attackVec = getAttackVec(calcCrystal.getPos());
                if (attackVec == null) return false;
                if (rotate.getValue() && !shouldYawStep()) {
                    if (!faceVector(attackVec)) return false;
                    if (batchRotation.getValue()) directionVec = attackVec;
                }
                if (shouldYawStep()) directionVec = attackVec;
                for (int i = 0; i < hitFactor.getValueInt(); i++) {
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(calcCrystal, mc.player.isSneaking()));
                }
                mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                mc.player.resetLastAttackedTicks();
                if (!mc.player.isOnGround()) mc.player.addCritParticles(calcCrystal);
                hitCrystals.put(calcCrystal.getId(), System.currentTimeMillis());
                noPosTimer.reset(); breakTimes.add(System.currentTimeMillis());
                if (swapBack) {
                    if (asyncItemRestore.getValue() && parallelPackets.getValue()) { int f = cur; mc.execute(() -> InventoryUtil.switchToSlot(f)); }
                    else InventoryUtil.switchToSlot(cur);
                }
                lastTime = breakTimer.getMs(); breakTimer.reset();
                if (setDeadMode.getValue() == SetDeadMode.Ghost) mc.executeSync(() -> mc.world.removeEntity(calcCrystal.getId(), Entity.RemovalReason.KILLED));
                if (doingAutoDtap && didAutoDtapAttack && !didAutoDtapCrystal) didAutoDtapCrystal = true;
                if (rotate.getValue() && !shouldYawStep()) Alien.ROTATION.snapBack();
                return false;
            }
            return true;
        }
        return true;
    }

    boolean isDtapping() { return doingAutoDtap && !(didAutoDtapAttack && didAutoDtapCrystal && autoDtapTimer.passed(0)); }

    public void placeCrystal() {
        noPosTimer.reset();
        if (placeFlag) { placeFlag = false; return; }
        if (placedOnspawn) return;
        if (calcPos != null) {
            long pDelay = dynamicDelay(placeDelay.getValueInt());
            if (placeTimer.passed(pDelay)) {
                if (swapWait.getValue() == SwapWaitMode.Full && !swapTimer.passed(swapWaitDelay.getValueInt())) return;
                boolean silent = false;
                int slot = InventoryUtil.findItem(Items.END_CRYSTAL);
                if (slot == -1 && !offhand) { renderPos = null; return; }
                if (!offhand && autoSwitch.getValue() == AutoSwitchMode.Normal) {
                    if (pauseOnEat.getValue()) { if (!isEatingGap()) InventoryUtil.switchToSlot(slot); }
                    else InventoryUtil.switchToSlot(slot);
                }
                if (!offhand && (autoSwitch.getValue() == AutoSwitchMode.Silent || autoSwitch.getValue() == AutoSwitchMode.SilentBypass))
                    if (!(mc.player.getMainHandStack().getItem() instanceof EndCrystalItem)) silent = true;
                if (autoSwitch.getValue() != AutoSwitchMode.Silent && autoSwitch.getValue() != AutoSwitchMode.SilentBypass)
                    if (!(mc.player.getMainHandStack().getItem() instanceof EndCrystalItem) && !offhand) { renderPos = null; return; }
                int old = mc.player.getInventory().selectedSlot;
                if (silent) {
                    if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) InventoryUtil.inventorySwap(slot < 9 ? slot + 36 : slot, old);
                    else InventoryUtil.switchToSlot(slot);
                }

                BlockPos obsPos = calcPos.down();
                Direction side = BlockUtil.getClickSideStrict(obsPos);
                if (side != null) {
                    Vec3d vec = obsPos.toCenterPos().add(
                            side.getVector().getX() * 0.5,
                            side.getVector().getY() * 0.5,
                            side.getVector().getZ() * 0.5
                    );
                    if (rotate.getValue() && !shouldYawStep()) { if (!faceVector(vec)) return; if (batchRotation.getValue()) directionVec = vec; }
                    if (shouldYawStep()) directionVec = vec;
                    BlockHitResult res = new BlockHitResult(vec, side, obsPos, false);
                    Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, res, id));
                    mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                    noPosTimer.reset(); placeTimes.add(System.currentTimeMillis());
                    if (silent) {
                        if (asyncItemRestore.getValue() && parallelPackets.getValue()) {
                            int restore = slot < 9 ? slot+36 : slot; int finalOld = old;
                            mc.execute(() -> { if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) InventoryUtil.inventorySwap(restore, finalOld); else InventoryUtil.switchToSlot(finalOld); });
                        } else {
                            if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) InventoryUtil.inventorySwap(slot<9?slot+36:slot, old);
                            else InventoryUtil.switchToSlot(old);
                        }
                    }
                    lastPos = calcPos; placeTimer.reset();
                    if (rotate.getValue() && !shouldYawStep()) Alien.ROTATION.snapBack();
                }
            }
            renderPos = calcPos; // 保持原始水晶坐标，渲染会向下偏移
            if (renderPos != null && renderMode.is(RenderMode.Fade)) {
                oldPlacements.removeIf(p -> p.pos.equals(renderPos));
                oldPlacements.add(new RenderPosTime(renderPos, System.currentTimeMillis(), renderDMG));
            }
        }
    }

    public float calculateBaseDamage(BlockPos crystalPos, PlayerEntity target, PlayerEntity predict) {
        return DamageUtils.overridingExplosionDamage(target, predict, crystalDamageVec(crystalPos), 12.0F, crystalPos.down(), Blocks.OBSIDIAN.getDefaultState());
    }

    private void placeBase() {
        if (basePos == null || !baseTimer.passedMs(dynamicDelay(delayBase.getValue()))) return;
        if (detectMining.getValue() && Alien.BREAK.isMining(basePos)) return;
        boolean offhandObsidian = mc.player.getOffHandStack().getItem() == Items.OBSIDIAN;
        int obsSlot = InventoryUtil.findBlock(Blocks.OBSIDIAN);
        boolean fromInventory = false;
        if (obsSlot == -1) {
            obsSlot = InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
            fromInventory = true;
        }
        if (obsSlot == -1 && !offhandObsidian) return;
        int old = mc.player.getInventory().selectedSlot;
        boolean silent = false;
        if (!offhandObsidian) {
            if (autoSwitch.getValue() == AutoSwitchMode.Normal) {
                if (!fromInventory) InventoryUtil.switchToSlot(obsSlot);
                else return;
            } else if (autoSwitch.getValue() == AutoSwitchMode.Silent || autoSwitch.getValue() == AutoSwitchMode.SilentBypass) {
                if (!(mc.player.getMainHandStack().getItem() == Items.OBSIDIAN)) silent = true;
            } else if (autoSwitch.getValue() == AutoSwitchMode.None) {
                if (!(mc.player.getMainHandStack().getItem() == Items.OBSIDIAN)) return;
            }
        }
        if (silent) {
            if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) {
                int targetSlot = obsSlot < 9 ? obsSlot + 36 : obsSlot;
                InventoryUtil.inventorySwap(targetSlot, old);
            } else {
                InventoryUtil.switchToSlot(obsSlot);
            }
        }
        BlockUtil.placeBlock(basePos, rotate.getValue());
        if (silent) {
            if (asyncItemRestore.getValue() && parallelPackets.getValue()) {
                int restore = obsSlot < 9 ? obsSlot+36 : obsSlot; int finalOld = old;
                mc.execute(() -> {
                    if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) InventoryUtil.inventorySwap(restore, finalOld);
                    else InventoryUtil.switchToSlot(finalOld);
                });
            } else {
                if (autoSwitch.getValue() == AutoSwitchMode.SilentBypass) InventoryUtil.inventorySwap(obsSlot<9?obsSlot+36:obsSlot, old);
                else InventoryUtil.switchToSlot(old);
            }
        } else if (!silent && autoSwitch.getValue() != AutoSwitchMode.None && !offhandObsidian) {
            if (autoSwitch.getValue() == AutoSwitchMode.Normal) InventoryUtil.switchToSlot(old);
        }
        baseTimer.reset();
        basePos = null;
    }

    private Vec3d getAttackVec(Vec3d crystalFeetPos) {
        return MathUtil.getPointToBoxFromBottom(mc.player.getEyePos(), crystalFeetPos, breakRange.getValue(), 2.0, ATTACK_VEC_STEP);
    }

    public boolean canBreakCrystal(EndCrystalEntity crystal) {
        if (ticksExisted.getValueInt() != 0 && crystal.age <= ticksExisted.getValueInt()) return false;
        Vec3d attackVec = getAttackVec(crystal.getPos());
        if (attackVec == null) return false;
        double dist = mc.player.getEyePos().distanceTo(attackVec);
        if (dist > breakRange.getValue()) return false;
        if (!mc.player.canSee(crystal)) {
            if (dist > breakWallsRange.getValue()) return false;
        }
        return true;
    }

    public PlaceResult getBestPlaceResult(PlayerEntity targetPlayer, Entity currentCalcCrystal) {
        BlockPos bestPos = null; double bestDmg = 0.5;
        for (BlockPos pos : BlockUtil.getSphere((float) (placeRange.getValue() + 1), mc.player.getPos())) {
            if (!canPlaceCrystalAt(pos, true, true)) continue;
            float td = calculateDamage(targetPlayer, crystalDamageVec(pos), terrain.getValue(), getMiningIgnore());
            float sd = calculateDamage(mc.player, crystalDamageVec(pos), terrain.getValue(), getMiningIgnore());
            if (td > bestDmg && td > minDamage.getValueFloat() && sd <= maxSelfDamage.getValueFloat()
                    && !(antiSuicide.getValue() && sd + 2 > mc.player.getHealth() + mc.player.getAbsorptionAmount())
                    && (td >= targetPlayer.getHealth() + targetPlayer.getAbsorptionAmount() || isRiskAcceptable(td, sd))) {
                bestDmg = td; bestPos = pos;
            }
        }
        return new PlaceResult(targetPlayer, bestDmg, bestPos, getPlaceRot(bestPos));
    }

    public boolean getMiningIgnore() { return switch (miningIgnore.getValue()) { case Ignore -> true; case StrictIgnore -> offhand || mc.player.getMainHandStack().getItem() instanceof EndCrystalItem; default -> false; }; }

    public Result getResult(PlayerEntity p) { BreakResult b = getBestBreakResult(p); return new Result(b, getBestPlaceResult(p, b.calcCrystal)); }

    public void load(Result r) {
        if (r == null) { target = null; displayTarget = null; return; }
        target = r.target(); displayTarget = r.target();
        if (InventoryUtil.findItem(Items.END_CRYSTAL) != -1 || offhand) {
            if (r.calcPos() != null) { calcPos = r.calcPos(); placeTargetRot = r.placeRots(); renderDMG = r.bestPlaceDMG(); lastDamage = r.bestPlaceDMG(); }
            else if (r.bestPlaceDMG() == 0) { renderPos = null; lastDamage = 0; }
        } else { calcPos = null; renderPos = null; placeTargetRot = null; renderDMG = 0; lastDamage = 0; }
        if (r.calcCrystal() != null) { calcCrystal = r.calcCrystal(); breakTargetRot = r.breakRots(); }
    }

    public Vec3d crystalDamageVec(BlockPos pos) { return Vec3d.of(pos).add(0.5,1,0.5); }

    public float[] getPlaceRot(BlockPos pos) {
        if (pos == null || !rotate.getValue()) return null;
        BlockPos obsPos = pos.down();
        Direction s = BlockUtil.getClickSideStrict(obsPos);
        if (s == null) return null;
        Vec3d v = obsPos.toCenterPos().add(s.getVector().getX()*0.5, s.getVector().getY()*0.5, s.getVector().getZ()*0.5);
        return getRotationsTo(mc.player.getEyePos(), v);
    }

    public void reset() { calcCrystal=null; calcPos=null; breakTargetRot=null; placeTargetRot=null; }

    public AntiFeetPlaceResult handleBBCrystal(BlockPos pos, BlockPos last, Entity cur) {
        Box bb = new Box(0,0,0,1,2,1).offset(pos.up());
        boolean afp = false;
        if (last != null && last.equals(pos)) {
            for (Entity e : Lists.newArrayList(mc.world.getNonSpectatingEntities(Entity.class, bb))) {
                if (e instanceof EndCrystalEntity) { if (!e.isAlive() || e.getBlockPos().down().equals(pos) || e.equals(cur)) continue; }
                if (feetPlace.getValue() && e instanceof ItemEntity) { afp = true; continue; }
                if (e instanceof PlayerEntity p && !bb.intersects(p.getBoundingBox())) continue;
                if (!e.isAlive()) continue;
                return new AntiFeetPlaceResult(false, afp);
            }
        } else {
            for (Entity e : Lists.newArrayList(mc.world.getNonSpectatingEntities(Entity.class, bb))) {
                if (e instanceof PlayerEntity p && !bb.intersects(p.getBoundingBox())) continue;
                if (feetPlace.getValue() && e instanceof ItemEntity) { afp = true; continue; }
                if (!e.isAlive() || e.equals(cur)) continue;
                return new AntiFeetPlaceResult(false, afp);
            }
        }
        return new AntiFeetPlaceResult(true, afp);
    }

    public BreakResult getBestBreakResult(PlayerEntity tp) {
        Entity best = null; double max = 0.5;
        boolean facePlaceAllow = shouldFacePlaceAllow(tp);
        float maxSelf = facePlaceAllow ? Float.MAX_VALUE : maxSelfDamage.getValueFloat();
        for (Entity e : Lists.newArrayList(mc.world.getEntities())) {
            if (e instanceof EndCrystalEntity c && canBreakCrystal(c)) {
                float td = calculateDamage(tp, e.getPos(), terrain.getValue(), false);
                float sd = calculateDamage(mc.player, e.getPos(), terrain.getValue(), false);
                if ((max < td && td > minDamage.getValueFloat()) || (lethalCrystals.getValueInt() != 0 && td * lethalCrystals.getValueFloat() >= tp.getHealth() + tp.getAbsorptionAmount())) {
                    if (sd > maxSelf || antiSuicide.getValue() && sd+2 > mc.player.getHealth() + mc.player.getAbsorptionAmount()) continue;
                    if (td < tp.getHealth() + tp.getAbsorptionAmount() && !isRiskAcceptable(td, sd)) continue;
                    max = td; best = e;
                }
            }
        }
        float[] rots = null;
        if (best != null && rotate.getValue()) {
            Vec3d attackVec = getAttackVec(best.getPos());
            if (attackVec != null) rots = getRotationsTo(mc.player.getEyePos(), attackVec);
        }
        return new BreakResult(tp, max, best, rots);
    }

    public boolean canRender() { return calcPos != null && (InventoryUtil.findItem(Items.END_CRYSTAL) != -1 || offhand); }

    public float[] getRotationsTo(Vec3d eyes, Vec3d target) {
        double dx = target.x - eyes.x, dy = target.y - eyes.y, dz = target.z - eyes.z;
        double dxz = Math.sqrt(dx*dx + dz*dz);
        return new float[]{ (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, (float) (-Math.toDegrees(Math.atan2(dy, dxz))) };
    }

    public float calculateDamage(LivingEntity t, Vec3d p, boolean tc, boolean mi) {
        return ExplosionUtil.calculateDamage(p, t, null, 6f);
    }

    public float calculateDamage(Vec3d p, PlayerEntity t, PlayerEntity pred) {
        return calculateDamage(t, p, false, false);
    }

    private boolean isEatingGap() {
        return mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    @EventListener public void onFrameFlip(TickEvent event) {
        if (!event.isPost() || nullCheck()) return;
        long now = System.currentTimeMillis();
        brokeCrystals.removeIf(t -> now - t > 1000);
        CRYSTALS_PER_SECOND = brokeCrystals.size();
    }

    @EventListener(priority = EventPriority.LOWEST) public void onPlayerUpdatePre(TickEvent event) {
        if (nullCheck() || !event.isPre()) return;
        if (isMultiTask()) { reset(); return; }
        prepare();
        if (lethalTick.getValue() && autoHit.getValue() && calcPos == null && !mc.player.isUsingItem() && cooldownTimer.passed(0)) tryAutoHit();
        if (timing.getValue() == TimingMode.Soft) interact();
    }

    @EventListener(priority = EventPriority.LOWEST) public void onPlayerUpdatePost(TickEvent event) {
        if (nullCheck() || !event.isPost() || isMultiTask()) return;
        if (timing.getValue() == TimingMode.Strict) interact();
    }

    public void interact() {
        if (pauseOnEat.getValue() && mc.player.isUsingItem()) return;
        if (pause.isPressed()) return;

        if (potSync.getValue() && AutoPot.INSTANCE != null && AutoPot.INSTANCE.isOn() && AutoPot.INSTANCE.isActuallyThrowing()) {
            if (placedOnspawn) placedOnspawn = false;
            return;
        }
        if (potSyncTest.getValue() && PotHelper.INSTANCE != null && PotHelper.INSTANCE.isOn() && PotHelper.INSTANCE.wasJustTriggered()) {
            if (placedOnspawn) placedOnspawn = false;
            return;
        }

        boolean br = breakCrystal();

        boolean burrowActive = prioritizeBurrow.getValue() && Burrow.INSTANCE != null && Burrow.INSTANCE.isOn();
        if (burrowActive) {
            if (burrowSyncMode.is(BurrowSyncMode.PausePlace)) {
                if (placedOnspawn) placedOnspawn = false;
                return;
            } else if (burrowSyncMode.is(BurrowSyncMode.NoSwitch)) {
                if (Burrow.INSTANCE.isPlacing()) {
                    if (placedOnspawn) placedOnspawn = false;
                    return;
                }
            }
        }

        if (InventoryUtil.findItem(Items.END_CRYSTAL) != -1 || offhand) {
            if (!limit.getValue() || !br || calcCrystal == null) placeCrystal();
        }
        if (basePlace.getValue() && basePos != null && BlockUtil.canPlace(basePos)) {
            placeBase();
        }

        if (placedOnspawn) placedOnspawn = false;
    }

    private void tryAutoHit() {
        if (target == null || mc.player == null) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < 1) return;
        if (mc.player.getMainHandStack().getItem() instanceof SwordItem) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            int sword = InventoryUtil.findClass(SwordItem.class);
            if (sword != -1) {
                int old = mc.player.getInventory().selectedSlot;
                InventoryUtil.switchToSlot(sword);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                InventoryUtil.switchToSlot(old);
            }
        }
    }

    public void beginAutoDtap() {
        if (!lethalTick.getValue()) return;
        doingAutoDtap = true;
        didAutoDtapAttack = true;
        didAutoDtapCrystal = false;
        autoDtapTimer.reset();
        breakFlag = true;
    }

    private boolean isValidTarget(PlayerEntity p) {
        return p != mc.player && !Alien.FRIEND.isFriend(p.getName().getString()) && p.isAlive() && p.getHealth() > 0 && mc.player.distanceTo(p) <= targetRange.getValue();
    }

    private record Result(BreakResult br, PlaceResult pr) {
        public PlayerEntity target() { return br.target; }
        public double getDamage() { return Math.max(br.bestDMG, pr.bestDMG); }
        public BlockPos calcPos() { return pr.calcPos; }
        public Entity calcCrystal() { return br.calcCrystal; }
        public float[] placeRots() { return pr.rots; }
        public float[] breakRots() { return br.rots; }
        public double bestPlaceDMG() { return pr.bestDMG; }
    }
    private record BreakResult(PlayerEntity target, double bestDMG, Entity calcCrystal, float[] rots) {}
    private record PlaceResult(PlayerEntity target, double bestDMG, BlockPos calcPos, float[] rots) {}
    private record RenderPosTime(BlockPos pos, long time, double damage) {}
    private record AntiFeetPlaceResult(boolean isPlaceAvailable, boolean isAntiFeetPlace) {}

    public void onThread() {
        if (isOff() || nullCheck()) return;
        if (thread.getValue()) {
            if (isMultiTask()) { reset(); return; }
            prepare();
            if (lethalTick.getValue() && autoHit.getValue() && calcPos == null && !mc.player.isUsingItem() && cooldownTimer.passed(0)) tryAutoHit();
            if (timing.getValue() == TimingMode.Soft) interact();
        }
    }
}