package dev.luminous.mod.gui.nakano.animation;

/**
 * Destination-based animation ported from Nakano's Opal-style GUI.
 */
public final class Animation {
    private final Easing easing;
    private final long durationMillis;

    private long startTime;
    private float startValue;
    private float destination;
    private float value;

    public Animation(Easing easing, long durationMillis) {
        this.easing = easing;
        this.durationMillis = Math.max(1L, durationMillis);
        this.startTime = System.currentTimeMillis();
    }

    public void run(float newDestination) {
        if (Float.compare(destination, newDestination) != 0) {
            startValue = value;
            destination = newDestination;
            startTime = System.currentTimeMillis();
        }

        float progress = Math.min(1.0F, (System.currentTimeMillis() - startTime) / (float) durationMillis);
        float eased = easing.apply(progress);
        value = startValue + (destination - startValue) * eased;
        if (progress >= 1.0F) {
            value = destination;
        }
    }

    public float value() {
        return value;
    }

    public boolean isAt(float target) {
        return Math.abs(value - target) < 0.001F && Float.compare(destination, target) == 0;
    }
}