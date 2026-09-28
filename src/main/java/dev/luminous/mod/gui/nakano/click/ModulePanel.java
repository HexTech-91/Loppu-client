package dev.luminous.mod.gui.nakano.click;

import dev.luminous.mod.gui.nakano.FontRepository;
import dev.luminous.mod.gui.nakano.NvgFont;
import dev.luminous.mod.gui.nakano.NvgRenderer;
import dev.luminous.mod.gui.nakano.animation.Animation;
import dev.luminous.mod.gui.nakano.animation.Easing;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ModulePanel {
    static final float ROW_HEIGHT = 22.0F;
    private static final float SETTING_HEIGHT = 20.0F;
    static final float LEFT_PADDING = 7.0F;
    static final float TOGGLE_WIDTH = 20.0F;
    static final float RIGHT_PADDING = 8.0F;
    static final float GAP = 4.0F;
    private static final int ACCENT_A = 0xFFF4F4F5;
    private static final int ACCENT_B = 0xFF858891;

    private final GuiModule module;
    private final Animation toggleAnimation = new Animation(Easing.DECELERATE, 150);
    private final Animation expandAnimation = new Animation(Easing.DECELERATE, 125);
    private final Animation hoverAnimation = new Animation(Easing.DECELERATE, 120);

    private float x;
    private float y;
    private float width;
    private boolean expanded;
    private boolean selectingModuleBind;
    private BindSetting selectingSettingBind;
    private SliderSetting draggingSlider;
    private ColorSetting draggingColor;
    private StringSetting editingString;
    private final Map<Setting, Float> textScrollOffsets = new HashMap<>();

    private static final float TEXT_SCROLL_STEP = 8.0F;

    ModulePanel(GuiModule module) {
        this.module = module;
    }

    float getPreferredWidth() {
        NvgFont bold = FontRepository.get("productsans-bold");
        NvgFont medium = FontRepository.get("productsans-medium");
        float nameWidth = Math.max(bold.width(module.name(), 8.2F), medium.width(module.name(), 8.2F));
        return nameWidth + LEFT_PADDING + GAP + TOGGLE_WIDTH + RIGHT_PADDING;
    }

    private float getTextScrollOffset(Setting setting, float textWidth, float maxTextWidth) {
        if (textWidth <= maxTextWidth) {
            textScrollOffsets.remove(setting);
            return 0.0F;
        }
        float maxOffset = textWidth - maxTextWidth;
        float offset = textScrollOffsets.getOrDefault(setting, 0.0F);
        return Math.min(offset, maxOffset);
    }

    void resetTextScrollOffsets() {
        textScrollOffsets.clear();
    }

    void render(float x, float y, float width, boolean last, boolean displayBinds, double mouseX, double mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;

        boolean hovering = contains(x, y, width, ROW_HEIGHT, mouseX, mouseY);
        toggleAnimation.run(module.enabled() ? 1.0F : 0.0F);
        expandAnimation.run(expanded ? 1.0F : 0.0F);
        hoverAnimation.run(hovering ? 1.0F : 0.0F);

        float extraHeight = expandedHeight();
        float totalHeight = ROW_HEIGHT + extraHeight;
        float bottomRadius = last ? 5.0F : 0.0F;

        NvgRenderer.roundedRectVarying(
                x, y, width, totalHeight, 0, 0, bottomRadius, bottomRadius,
                NvgRenderer.mix(0xE51A1A27, 0xF0262638, hoverAnimation.value() * 0.55F)
        );

        if (toggleAnimation.value() > 0.001F) {
            int left = NvgRenderer.withAlpha(ACCENT_A, toggleAnimation.value() * 0.35F);
            int right = NvgRenderer.withAlpha(ACCENT_B, toggleAnimation.value() * 0.35F);
            float toggleBottomRadius = last && extraHeight <= 0.01F ? 5.0F : 0.0F;
            NvgRenderer.roundedGradientVarying(
                    x, y, width, ROW_HEIGHT, 0.0F, 0.0F,
                    toggleBottomRadius, toggleBottomRadius, left, right, 0.0F
            );
        }

        NvgFont font = FontRepository.get(module.enabled() ? "productsans-bold" : "productsans-medium");
        int textColor = module.enabled() ? 0xFFFFFFFF : 0xFFB5B5C4;
        float maxTextWidth = width - LEFT_PADDING - GAP - TOGGLE_WIDTH - RIGHT_PADDING;
        NvgRenderer.scissor(x + LEFT_PADDING, y, maxTextWidth, ROW_HEIGHT, () ->
                font.draw(module.name(), x + LEFT_PADDING, y + ROW_HEIGHT * 0.5F, 8.2F, textColor)
        );

        float toggleX = x + width - TOGGLE_WIDTH - RIGHT_PADDING + 4.0F;
        if (selectingModuleBind) {
            drawRightAligned("[...]", toggleX - 5, y + ROW_HEIGHT * 0.5F, 7.0F, 0xFFFFFFFF);
        } else if (displayBinds && module.boundKey() != -1) {
            drawRightAligned("[" + module.module().getBindSetting().getKeyString() + "]",
                    toggleX - 5, y + ROW_HEIGHT * 0.5F, 6.5F, 0xFFBFC2D8);
        }

        drawToggle(toggleX, y + (ROW_HEIGHT - 8.0F) * 0.5F, module.enabled(), toggleAnimation.value());

        if (extraHeight > 0.5F) {
            NvgRenderer.withGlobalAlpha(expandAnimation.value(), () ->
                    NvgRenderer.scissor(x, y + ROW_HEIGHT, width, extraHeight,
                            () -> renderSettings(mouseX, mouseY, last))
            );
        }
    }

    private void renderSettings(double mouseX, double mouseY, boolean last) {
        List<Setting> settings = visibleSettings();
        float settingY = y + ROW_HEIGHT;
        float fullHeight = settings.size() * SETTING_HEIGHT;
        NvgRenderer.roundedRectVarying(
                x, settingY, width, fullHeight, 0.0F, 0.0F,
                last ? 5.0F : 0.0F, last ? 5.0F : 0.0F, 0xE612121B
        );

        for (int index = 0; index < settings.size(); index++) {
            Setting setting = settings.get(index);
            float rowY = settingY + index * SETTING_HEIGHT;
            boolean hovered = contains(x, rowY, width, SETTING_HEIGHT, mouseX, mouseY);
            if (hovered) {
                NvgRenderer.rect(x, rowY, width, SETTING_HEIGHT, 0x352F3040);
            }
            renderSetting(setting, rowY, mouseX);
        }
    }

    private void renderSetting(Setting setting, float rowY, double mouseX) {
        NvgFont font = FontRepository.get("productsans-medium");
        String label = setting.getName();
        float fontS = 7.2F;
        float labelPadLeft = LEFT_PADDING;

        if (setting instanceof BooleanSetting value) {
            float maxTextWidth = width - labelPadLeft - GAP - TOGGLE_WIDTH - RIGHT_PADDING;
            float textWidth = font.width(label, fontS);
            float offset = getTextScrollOffset(setting, textWidth, maxTextWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxTextWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + SETTING_HEIGHT * 0.5F, fontS, 0xFFD2D4E3)
            );
            float toggleX = x + width - TOGGLE_WIDTH - RIGHT_PADDING + 4.0F;
            drawToggle(toggleX, rowY + (SETTING_HEIGHT - 8.0F) * 0.5F,
                    value.getValue(), value.getValue() ? 1.0F : 0.0F);
            return;
        }

        if (setting instanceof SliderSetting value) {
            if (draggingSlider == value) {
                setSliderFromMouse(value, mouseX);
            }
            float labelWidth = font.width(label, 6.8F);
            float sliderX = x + labelPadLeft + labelWidth + GAP;
            float sliderWidth = Math.max(16.0F, width - (sliderX - x) - TOGGLE_WIDTH - RIGHT_PADDING - GAP);
            float progress = value.getRange() == 0.0
                    ? 0.0F
                    : (float) ((value.getValue() - value.getMin()) / value.getRange());
            float maxLabelWidth = width - labelPadLeft - GAP - 30.0F - RIGHT_PADDING;
            float textWidth = font.width(label, 6.8F);
            float offset = getTextScrollOffset(setting, textWidth, maxLabelWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxLabelWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + 6.0F, 6.8F, 0xFFD2D4E3)
            );
            drawRightAligned(formatNumber(value.getValue()) + value.getSuffix(),
                    x + width - RIGHT_PADDING, rowY + 5.5F, 6.0F, 0xFF9FA3B8);
            NvgRenderer.roundedRect(sliderX, rowY + 12.0F, sliderWidth, 2.5F, 1.25F, 0xFF343447);
            NvgRenderer.roundedGradient(sliderX, rowY + 12.0F,
                    sliderWidth * clamp01(progress), 2.5F, 1.25F, ACCENT_A, ACCENT_B, 0);
            return;
        }

        if (setting instanceof EnumSetting<?> value) {
            float maxTextWidth = width - labelPadLeft - GAP - 30.0F - RIGHT_PADDING;
            float textWidth = font.width(label, fontS);
            float offset = getTextScrollOffset(setting, textWidth, maxTextWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxTextWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + SETTING_HEIGHT * 0.5F, fontS, 0xFFD2D4E3)
            );
            drawRightAligned(String.valueOf(value.getValue()), x + width - RIGHT_PADDING,
                    rowY + SETTING_HEIGHT * 0.5F, 6.5F, 0xFFBFC2D8);
            return;
        }

        if (setting instanceof BindSetting value) {
            float maxTextWidth = width - labelPadLeft - GAP - 50.0F - RIGHT_PADDING;
            float textWidth = font.width(label, fontS);
            float offset = getTextScrollOffset(setting, textWidth, maxTextWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxTextWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + SETTING_HEIGHT * 0.5F, fontS, 0xFFD2D4E3)
            );
            String shown = selectingSettingBind == value ? "[...]" : "[" + value.getKeyString() + "]";
            drawRightAligned(shown, x + width - RIGHT_PADDING,
                    rowY + SETTING_HEIGHT * 0.5F, 6.5F, 0xFFBFC2D8);
            return;
        }

        if (setting instanceof StringSetting value) {
            boolean secret = label.toLowerCase(Locale.ROOT).contains("password");
            String raw = secret ? "*".repeat(Math.min(16, value.getValue().length())) : value.getValue();
            String shown = editingString == value ? raw + "_" : raw;
            float maxTextWidth = width - labelPadLeft - GAP - 50.0F - RIGHT_PADDING;
            float textWidth = font.width(label, fontS);
            float offset = getTextScrollOffset(setting, textWidth, maxTextWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxTextWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + SETTING_HEIGHT * 0.5F, fontS, 0xFFD2D4E3)
            );
            drawRightAligned(shown, x + width - RIGHT_PADDING,
                    rowY + SETTING_HEIGHT * 0.5F, 6.5F, 0xFFBFC2D8);
            return;
        }

        if (setting instanceof ColorSetting value) {
            if (draggingColor == value) {
                setColorFromMouse(value, mouseX);
            }
            float maxTextWidth = width - labelPadLeft - GAP - TOGGLE_WIDTH - GAP - RIGHT_PADDING;
            float textWidth = font.width(label, fontS);
            float offset = getTextScrollOffset(setting, textWidth, maxTextWidth);
            NvgRenderer.scissor(x + labelPadLeft, rowY, maxTextWidth, SETTING_HEIGHT, () ->
                font.draw(label, x + labelPadLeft - offset,
                        rowY + SETTING_HEIGHT * 0.5F, fontS, 0xFFD2D4E3)
            );
            float colorX = x + width - TOGGLE_WIDTH - RIGHT_PADDING + 4.0F;
            float colorWidth = TOGGLE_WIDTH + GAP;
            drawHueBar(colorX, rowY + 5.0F, colorWidth, 8.0F, value.getValue().getAlpha());
            int rgb = value.getValue().getRGB();
            NvgRenderer.outline(colorX, rowY + 5.0F, colorWidth, 8.0F, 3.0F, 1.0F,
                    value.sync ? 0xFFFFFFFF : NvgRenderer.withAlpha(rgb, 1.0F));
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectingModuleBind) {
            module.setBoundKey(-(button + 2));
            selectingModuleBind = false;
            return true;
        }
        if (selectingSettingBind != null) {
            selectingSettingBind.setValue(-(button + 2));
            selectingSettingBind = null;
            return true;
        }

        if (contains(x, y, width, ROW_HEIGHT, mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                module.toggle();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                expanded = !expanded;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                selectingModuleBind = true;
                editingString = null;
            }
            return true;
        }

        if (!expanded || expandAnimation.value() < 0.8F) {
            return false;
        }

        List<Setting> settings = visibleSettings();
        for (int index = 0; index < settings.size(); index++) {
            float rowY = y + ROW_HEIGHT + index * SETTING_HEIGHT;
            if (!contains(x, rowY, width, SETTING_HEIGHT, mouseX, mouseY)) {
                continue;
            }
            Setting setting = settings.get(index);
            if (setting instanceof BooleanSetting value) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && value.hasParent()) {
                    value.setOpen(!value.isOpen());
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    value.setValue(!value.getValue());
                }
            } else if (setting instanceof SliderSetting value && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingSlider = value;
                setSliderFromMouse(value, mouseX);
            } else if (setting instanceof EnumSetting<?> value) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    decreaseEnum(value);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    value.increaseEnum();
                }
            } else if (setting instanceof BindSetting value) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    value.setValue(-1);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    selectingSettingBind = value;
                    editingString = null;
                }
            } else if (setting instanceof StringSetting value) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    value.setValue("");
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    editingString = value;
                }
            } else if (setting instanceof ColorSetting value) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggingColor = value;
                    setColorFromMouse(value, mouseX);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    value.setSync(!value.sync);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && value.injectBoolean) {
                    value.booleanValue = !value.booleanValue;
                }
            }
            return true;
        }
        return false;
    }

    void mouseReleased() {
        draggingSlider = null;
        draggingColor = null;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!expanded || amount == 0.0) {
            return false;
        }
        List<Setting> settings = visibleSettings();
        for (int index = 0; index < settings.size(); index++) {
            float rowY = y + ROW_HEIGHT + index * SETTING_HEIGHT;
            if (!contains(x, rowY, width, SETTING_HEIGHT, mouseX, mouseY)) {
                continue;
            }
            Setting setting = settings.get(index);

            // Left shift + scroll to scroll long setting text horizontally
            long window = MinecraftClient.getInstance().getWindow().getHandle();
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
                NvgFont font = FontRepository.get("productsans-medium");
                String label = setting.getName();
                float fontS = 7.2F;
                float textWidth = font.width(label, fontS);
                float maxTextWidth = width - LEFT_PADDING - GAP - TOGGLE_WIDTH - RIGHT_PADDING;
                if (textWidth <= maxTextWidth) return false;
                float currentOffset = textScrollOffsets.getOrDefault(setting, 0.0F);
                float maxOffset = textWidth - maxTextWidth;
                float newOffset = currentOffset + (float) (-amount * TEXT_SCROLL_STEP);
                newOffset = Math.max(0, Math.min(newOffset, maxOffset));
                if (newOffset <= 0.001F) textScrollOffsets.remove(setting);
                else textScrollOffsets.put(setting, newOffset);
                return true;
            }

            if (setting instanceof SliderSetting value) {
                value.setValue(clamp(value.getValue() + Math.signum(amount) * value.getIncrement(),
                        value.getMin(), value.getMax()));
                return true;
            }
            if (setting instanceof EnumSetting<?> value) {
                if (amount > 0) value.increaseEnum(); else decreaseEnum(value);
                return true;
            }
            if (setting instanceof ColorSetting value) {
                Color current = value.getValue();
                int alpha = (int) clamp(current.getAlpha() + Math.signum(amount) * 10.0, 0.0, 255.0);
                value.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), alpha));
                return true;
            }
            return false;
        }
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectingModuleBind) {
            module.setBoundKey(keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode);
            selectingModuleBind = false;
            return true;
        }
        if (selectingSettingBind != null) {
            selectingSettingBind.setValue(keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode);
            selectingSettingBind = null;
            return true;
        }
        if (editingString != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                editingString = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String value = editingString.getValue();
                if (!value.isEmpty()) editingString.setValue(value.substring(0, value.length() - 1));
            }
            return true;
        }
        return false;
    }

    boolean charTyped(char character, int modifiers) {
        if (editingString == null || Character.isISOControl(character)) {
            return false;
        }
        String value = editingString.getValue();
        if (value.length() < 128) {
            editingString.setValue(value + character);
        }
        return true;
    }

    void cancelBinding() {
        selectingModuleBind = false;
        selectingSettingBind = null;
        editingString = null;
        draggingSlider = null;
        draggingColor = null;
        textScrollOffsets.clear();
    }

    boolean isSelectingBind() {
        return selectingModuleBind || selectingSettingBind != null || editingString != null;
    }

    float totalHeight() {
        expandAnimation.run(expanded ? 1.0F : 0.0F);
        return ROW_HEIGHT + expandedHeight();
    }

    private float expandedHeight() {
        return visibleSettings().size() * SETTING_HEIGHT * expandAnimation.value();
    }

    private List<Setting> visibleSettings() {
        return module.settings().stream().filter(Setting::isVisible).toList();
    }

    private void setSliderFromMouse(SliderSetting setting, double mouseX) {
        float sliderX = x + LEFT_PADDING + 20.0F;
        float sliderWidth = Math.max(18.0F, width - (sliderX - x) - TOGGLE_WIDTH - RIGHT_PADDING - GAP);
        double progress = clamp((mouseX - sliderX) / sliderWidth, 0.0, 1.0);
        setting.setValue(clamp(setting.getMin() + progress * setting.getRange(), setting.getMin(), setting.getMax()));
    }

    private void setColorFromMouse(ColorSetting setting, double mouseX) {
        float colorX = x + width - TOGGLE_WIDTH - RIGHT_PADDING + 4.0F;
        float colorWidth = TOGGLE_WIDTH + GAP;
        float progress = (float) clamp((mouseX - colorX) / colorWidth, 0.0, 1.0);
        Color current = setting.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        float saturation = Math.max(0.72F, hsb[1]);
        float brightness = Math.max(0.72F, hsb[2]);
        int rgb = Color.HSBtoRGB(progress, saturation, brightness);
        Color next = new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, current.getAlpha());
        setting.setSync(false);
        setting.setValue(next);
    }

    private static void decreaseEnum(EnumSetting<?> setting) {
        Enum<?> current = setting.getValue();
        Enum<?>[] values = current.getDeclaringClass().getEnumConstants();
        int next = (current.ordinal() - 1 + values.length) % values.length;
        setting.setEnumValue(values[next].name());
    }

    private static void drawHueBar(float x, float y, float width, float height, int alpha) {
        int[] colors = {0xFFFF4D4D, 0xFFFFFF4D, 0xFF4DFF4D, 0xFF4DFFFF, 0xFF4D4DFF, 0xFFFF4DFF, 0xFFFF4D4D};
        float segment = width / 6.0F;
        float opacity = alpha / 255.0F;
        for (int i = 0; i < 6; i++) {
            NvgRenderer.roundedGradient(x + i * segment, y, segment + 0.5F, height,
                    i == 0 || i == 5 ? 3.0F : 0.0F,
                    NvgRenderer.withAlpha(colors[i], opacity),
                    NvgRenderer.withAlpha(colors[i + 1], opacity), 0.0F);
        }
    }

    private static void drawToggle(float x, float y, boolean enabled, float progress) {
        int track = NvgRenderer.mix(0xFF45464D, 0xFFB6B8BF, progress);
        NvgRenderer.roundedRect(x, y, 14, 8, 4, track);
        float knobX = x + 1.5F + 6.0F * progress;
        NvgRenderer.roundedRect(knobX, y + 1.5F, 5, 5, 2.5F,
                enabled ? 0xFFFFFFFF : 0xFFD1D2DC);
    }

    private static void drawRightAligned(String text, float right, float y, float size, int color) {
        NvgFont font = FontRepository.get("productsans-medium");
        font.draw(text, right - font.width(text, size), y, size, color);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-7) return Long.toString(Math.round(value));
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean contains(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
