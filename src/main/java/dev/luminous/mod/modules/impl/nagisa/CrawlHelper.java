package dev.luminous.mod.modules.impl.nagisa;

import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Phase;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.events.eventbus.EventListener;
import net.minecraft.entity.EntityPose;

public class CrawlHelper
        extends Module {
    public final BooleanSetting autoJump = this.add(new BooleanSetting("AutoJump", true));
    public final BooleanSetting autoPhase = this.add(new BooleanSetting("AutoPhase", true).setParent());
    public final SliderSetting waitTime = this.add(new SliderSetting("WaitTime", 500.0, 0.0, 2000.0, 50.0, () -> this.autoPhase.isOpen()));
    private final Timer phaseTimer = new Timer();

    public CrawlHelper() {
        super("CrawlHelper", Category.Movement);
        this.setChinese("爬行辅助");
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        boolean isMoving;
        if (CrawlHelper.mc.player == null || CrawlHelper.mc.world == null) {
            return;
        }
        if (event.isPost()) {
            return;
        }
        boolean isCrawlingPose = CrawlHelper.mc.player.getPose() == EntityPose.SWIMMING;
        boolean isLowHeight = CrawlHelper.mc.player.getBoundingBox().getLengthY() < 1.0;
        boolean isNotInWater = !CrawlHelper.mc.player.isTouchingWater();
        boolean isReallyCrawling = isCrawlingPose && isLowHeight && isNotInWater;
        boolean bl = isMoving = CrawlHelper.mc.player.input.movementForward != 0.0f || CrawlHelper.mc.player.input.movementSideways != 0.0f;
        if (isReallyCrawling) {
            boolean isPhaseActive;
            boolean isCollidingWall = CrawlHelper.mc.player.horizontalCollision;
            boolean bl2 = isPhaseActive = Phase.INSTANCE != null && Phase.INSTANCE.isOn();
            if (this.autoJump.getValue() && isMoving && CrawlHelper.mc.player.isOnGround()) {
                boolean shouldSuppressJump;
                boolean bl3 = shouldSuppressJump = this.autoPhase.getValue() && isCollidingWall || isPhaseActive;
                if (!shouldSuppressJump) {
                    CrawlHelper.mc.player.jump();
                }
            }
            if (this.autoPhase.getValue() && isMoving) {
                if (isCollidingWall) {
                    if (this.phaseTimer.passedMs(this.waitTime.getValueInt()) && Phase.INSTANCE != null) {
                        Phase.INSTANCE.enable();
                        this.phaseTimer.reset();
                    }
                } else {
                    this.phaseTimer.reset();
                }
            } else {
                this.phaseTimer.reset();
            }
        } else {
            this.phaseTimer.reset();
        }
    }

    @Override
    public void onEnable() {
        this.phaseTimer.reset();
    }
}