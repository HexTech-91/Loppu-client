package dev.luminous.mod.gui.nakano.animation;

import java.util.function.UnaryOperator;

/**
 * Easing functions ported from Nakano's Opal-style GUI.
 */
public enum Easing {
    DECELERATE(x -> 1.0F - ((x - 1.0F) * (x - 1.0F))),
    EASE_OUT_SINE(x -> (float) Math.sin(x * Math.PI * 0.5D)),
    EASE_OUT_EXPO(x -> x >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0F * x));

    private final UnaryOperator<Float> function;

    Easing(UnaryOperator<Float> function) {
        this.function = function;
    }

    public float apply(float value) {
        return function.apply(value);
    }
}