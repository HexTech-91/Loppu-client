/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.MatrixStack
 */
package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.path.TPUtils;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.exploit.MaceSpoof;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.events.eventbus.EventListener;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public class FlatMace
        extends Module {
    public static FlatMace INSTANCE;
    private final SliderSetting height = this.add(new SliderSetting("Height", 5.0, 1.0, 30.0, 0.5));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 200.0, 10.0, 2000.0, 10.0));
    private final BooleanSetting holdMode = this.add(new BooleanSetting("HoldMode", true));
    private final BooleanSetting autoMaceSpoof = this.add(new BooleanSetting("AutoMaceSpoof", true));
    private final BooleanSetting internalCam = this.add(new BooleanSetting("InternalCam", false).setParent());
    private final SliderSetting camSpeed = this.add(new SliderSetting("HSpeed", 1.0, 0.0, 3.0, () -> this.internalCam.getValue()));
    private final SliderSetting camVSpeed = this.add(new SliderSetting("VSpeed", 0.42, 0.0, 3.0, () -> this.internalCam.getValue()));
    private final BooleanSetting camRotate = this.add(new BooleanSetting("Rotate", true, () -> this.internalCam.getValue()));
    private final Timer tpTimer = new Timer();
    private float fakeYaw;
    private float fakePitch;
    private float prevFakeYaw;
    private float prevFakePitch;
    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double prevFakeX;
    private double prevFakeY;
    private double prevFakeZ;
    private float playerYaw;
    private float playerPitch;
    private boolean prevMaceSpoofState;
    private boolean prevAuraState;

    public FlatMace() {
        super("FlatMace", Category.Mace);
        this.setChinese("伪平地锤");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return String.format("+%.1f %.0fms", this.height.getValue(), this.delay.getValue());
    }

    @Override
    public void onEnable() {
        this.tpTimer.reset();
        if (FlatMace.nullCheck()) {
            this.disable();
            return;
        }
        if (this.internalCam.getValue()) {
            this.playerYaw = this.getYaw();
            this.playerPitch = this.getPitch();
            this.fakePitch = this.getPitch();
            this.fakeYaw = this.getYaw();
            this.prevFakePitch = this.fakePitch;
            this.prevFakeYaw = this.fakeYaw;
            this.fakeX = FlatMace.mc.player.getX();
            this.fakeY = FlatMace.mc.player.getY() + (double)FlatMace.mc.player.getEyeHeight(FlatMace.mc.player.getPose());
            this.fakeZ = FlatMace.mc.player.getZ();
            this.prevFakeX = this.fakeX;
            this.prevFakeY = this.fakeY;
            this.prevFakeZ = this.fakeZ;
        }
        if (this.autoMaceSpoof.getValue()) {
            if (MaceSpoof.INSTANCE != null && !MaceSpoof.INSTANCE.isOn()) {
                this.prevMaceSpoofState = true;
                MaceSpoof.INSTANCE.enable();
                this.forceMaceSpoofSettings();
            } else {
                this.prevMaceSpoofState = false;
            }
        }
        if (Aura.INSTANCE != null && !Aura.INSTANCE.isOn()) {
            this.prevAuraState = true;
            Aura.INSTANCE.enable();
        } else {
            this.prevAuraState = false;
        }
    }

    @Override
    public void onDisable() {
        this.prevAuraState = false;
        this.prevMaceSpoofState = false;
        if (Aura.INSTANCE != null && Aura.INSTANCE.isOn()) {
            try {
                Aura.INSTANCE.disable();
            } catch (Throwable ignored) {}
        }
        if (MaceSpoof.INSTANCE != null && MaceSpoof.INSTANCE.isOn()) {
            try {
                MaceSpoof.INSTANCE.setLagMode(MaceSpoof.LagMode.Normal);
                MaceSpoof.INSTANCE.setTimerMode(MaceSpoof.TimerMode.None);
                MaceSpoof.INSTANCE.setLandDistance(2.5);
            } catch (Throwable ignored) {}
            try {
                MaceSpoof.INSTANCE.disable();
            } catch (Throwable ignored) {}
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!this.internalCam.getValue()) {
            return;
        }
        if (this.camRotate.getValue() && FlatMace.mc.crosshairTarget != null && FlatMace.mc.crosshairTarget.getPos() != null) {
            float[] angle = RotationManager.getRotation(FlatMace.mc.crosshairTarget.getPos());
            this.playerYaw = angle[0];
            this.playerPitch = angle[1];
        }
        if (BaritoneUtil.isPathing()) {
            double[] motion = MovementUtil.directionSpeed(this.camSpeed.getValue());
            this.prevFakeX = this.fakeX;
            this.prevFakeY = this.fakeY;
            this.prevFakeZ = this.fakeZ;
            this.fakeX += motion[0];
            this.fakeZ += motion[1];
            if (FlatMace.mc.options.jumpKey.isPressed()) {
                this.fakeY += this.camVSpeed.getValue();
            }
            if (FlatMace.mc.options.sneakKey.isPressed()) {
                this.fakeY -= this.camVSpeed.getValue();
            }
        }
    }

    @EventListener(priority=200)
    public void onRotate(UpdateRotateEvent event) {
        if (!this.internalCam.getValue() || FlatMace.nullCheck() || !this.isOn()) {
            return;
        }
        if (!BaritoneUtil.isPathing() && !event.isModified()) {
            event.setYawWithoutSync(this.playerYaw);
            event.setPitchWithoutSync(this.playerPitch);
        }
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (!this.internalCam.getValue() || FlatMace.nullCheck() || !this.isOn()) {
            return;
        }
        this.prevFakeYaw = this.fakeYaw;
        this.prevFakePitch = this.fakePitch;
        this.fakeYaw = this.getYaw();
        this.fakePitch = this.getPitch();
    }

    private float getYaw() {
        return FlatMace.mc.player.getYaw();
    }

    private float getPitch() {
        return FlatMace.mc.player.getPitch();
    }

    @EventListener
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (!this.internalCam.getValue() || FlatMace.nullCheck() || !this.isOn() || FlatMace.mc.player == null) {
            return;
        }
        double[] motion = MovementUtil.directionSpeed(this.camSpeed.getValue());
        this.prevFakeX = this.fakeX;
        this.prevFakeY = this.fakeY;
        this.prevFakeZ = this.fakeZ;
        this.fakeX += motion[0];
        this.fakeZ += motion[1];
        if (FlatMace.mc.options.jumpKey.isPressed()) {
            this.fakeY += this.camVSpeed.getValue();
        }
        if (FlatMace.mc.options.sneakKey.isPressed()) {
            this.fakeY -= this.camVSpeed.getValue();
        }
        FlatMace.mc.player.input.movementForward = 0.0f;
        FlatMace.mc.player.input.movementSideways = 0.0f;
        FlatMace.mc.player.input.jumping = false;
        FlatMace.mc.player.input.sneaking = false;
    }

    public double getFakeX() {
        return MathUtil.interpolate(this.prevFakeX, this.fakeX, (double)mc.getRenderTickCounter().getTickDelta(true));
    }

    public double getFakeY() {
        return MathUtil.interpolate(this.prevFakeY, this.fakeY, (double)mc.getRenderTickCounter().getTickDelta(true));
    }

    public double getFakeZ() {
        return MathUtil.interpolate(this.prevFakeZ, this.fakeZ, (double)mc.getRenderTickCounter().getTickDelta(true));
    }

    public float getFakeYaw() {
        return MathUtil.interpolate(this.prevFakeYaw, this.fakeYaw, mc.getRenderTickCounter().getTickDelta(true));
    }

    public float getFakePitch() {
        return MathUtil.interpolate(this.prevFakePitch, this.fakePitch, mc.getRenderTickCounter().getTickDelta(true));
    }

    @EventListener
    public void onTick(ClientTickEvent clientTickEvent) {
        if (FlatMace.nullCheck() || clientTickEvent.isPost()) {
            return;
        }
        if (this.autoMaceSpoof.getValue() && MaceSpoof.INSTANCE != null && MaceSpoof.INSTANCE.isOn()) {
            this.forceMaceSpoofSettings();
        }
        if (this.holdMode.getValue() && !FlatMace.mc.options.jumpKey.isPressed()) {
            return;
        }
        if (!this.tpTimer.passedMs((long)this.delay.getValue())) {
            return;
        }
        double d = FlatMace.mc.player.getX();
        double d2 = FlatMace.mc.player.getY() + this.height.getValue();
        double d3 = FlatMace.mc.player.getZ();
        TPUtils.newTeleport(new Vec3d(d, d2, d3));
        FlatMace.mc.player.setPosition(d, d2, d3);
        this.tpTimer.reset();
    }

    private void forceMaceSpoofSettings() {
        if (MaceSpoof.nullCheck()) return;
        MaceSpoof.INSTANCE.setLagMode(MaceSpoof.LagMode.Progressive);
        MaceSpoof.INSTANCE.setTimerMode(MaceSpoof.TimerMode.Timer);
        MaceSpoof.INSTANCE.setFastTimer(MaceSpoof.INSTANCE.getProgTimer());
        MaceSpoof.INSTANCE.setLandDistance(MaceSpoof.INSTANCE.getLandDistProg());
    }
}
