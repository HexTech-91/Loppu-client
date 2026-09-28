
package dev.luminous.core.impl;

import dev.luminous.mod.modules.impl.player.TimerModule;

public class TimerManager {
    public float timer = 1.0f;
    public float lastTimer;
    private int skipTicks;

    public void set(float factor) {
        if (factor < 0.1f) {
            factor = 0.1f;
        }
        this.timer = factor;
    }

    public void reset() {
        this.lastTimer = this.timer = this.getDefault();
        this.skipTicks = 0;
    }

    public void tryReset() {
        if (this.lastTimer != this.getDefault()) {
            this.reset();
        }
    }

    public float get() {
        return this.timer;
    }

    public float getDefault() {
        return TimerModule.INSTANCE.isOn() ? (TimerModule.INSTANCE.boostKey.isPressed() ? TimerModule.INSTANCE.boost.getValueFloat() : TimerModule.INSTANCE.multiplier.getValueFloat()) : 1.0f;
    }

    public void addSkipTicks(int ticks) {
        if (ticks <= 0) {
            return;
        }
        this.skipTicks += ticks;
    }

    public boolean consumeSkipTick() {
        if (this.skipTicks <= 0) {
            return false;
        }
        --this.skipTicks;
        return true;
    }

    public void clearSkipTicks() {
        this.skipTicks = 0;
    }
}
