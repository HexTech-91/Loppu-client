package dev.luminous.mod.gui.nakano.click;

import dev.luminous.mod.gui.nakano.FontRepository;
import dev.luminous.mod.gui.nakano.NvgFont;
import dev.luminous.mod.gui.nakano.NvgRenderer;
import dev.luminous.mod.gui.nakano.animation.Animation;
import dev.luminous.mod.gui.nakano.animation.Easing;
import org.lwjgl.glfw.GLFW;

import java.util.List;

final class CategoryPanel {
    private static final float HEADER_HEIGHT = 22.0F;
    private static final int GLOW_A = 0xFFF4F4F5;
    private static final int GLOW_B = 0xFF777A82;

    private final GuiCategory category;
    private final List<ModulePanel> modules;
    private final Animation openAnimation;
    private final Animation expandAnimation = new Animation(Easing.EASE_OUT_SINE, 240);
    private final Animation scrollAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);

    private float x;
    private float y;
    private float width;
    private float renderedHeight;
    private float scrollTarget;
    private boolean collapsed;
    private boolean closing;

    CategoryPanel(GuiCategory category, int index) {
        this.category = category;
        this.modules = category.modules().stream().map(ModulePanel::new).toList();
        this.openAnimation = new Animation(Easing.EASE_OUT_SINE, 100L + index * 80L);
    }

    void prepareOpen() {
        closing = false;
        collapsed = false;
        scrollTarget = 0.0F;
    }

    void requestClose() {
        closing = true;
        modules.forEach(ModulePanel::cancelBinding);
    }

    boolean closeFinished() {
        return closing && openAnimation.isAt(0.0F);
    }

    void render(
            float x, float y, float width, float availableHeight,
            boolean displayBinds, float glowBreath, double mouseX, double mouseY
    ) {
        this.x = x;
        this.y = y;
        this.width = width;

        openAnimation.run(closing ? 0.0F : 1.0F);
        expandAnimation.run(collapsed ? 0.0F : 1.0F);

        float expandedHeight = expandedHeight();
        float visibleExpandedHeight = Math.min(availableHeight, expandedHeight);
        float visibleHeight = HEADER_HEIGHT
                + (visibleExpandedHeight - HEADER_HEIGHT) * expandAnimation.value();
        float maxScroll = Math.max(0, expandedHeight - availableHeight);
        scrollTarget = Math.max(-maxScroll, Math.min(0, scrollTarget));
        scrollAnimation.run(scrollTarget);

        float animatedHeight = visibleHeight * openAnimation.value();
        renderedHeight = animatedHeight;
        NvgRenderer.withGlobalAlpha(openAnimation.value(), () -> {
            if (NakanoClickGuiScreen.glowEnabled()) {
                NvgRenderer.gradientGlowOutline(
                        x, y, width, animatedHeight, 5.0F,
                        GLOW_A, GLOW_B,
                        NakanoClickGuiScreen.glowStrength() * glowBreath
                );
            }
            NvgRenderer.scissor(x, y, width, animatedHeight, () -> {
                float bottomRadius = 5.0F * (1.0F - expandAnimation.value());
                NvgRenderer.roundedRectVarying(
                        x, y, width, HEADER_HEIGHT, 5, 5, bottomRadius, bottomRadius, 0xF00E0E17
                );

                NvgFont bold = FontRepository.get("productsans-bold");
                bold.draw(category.displayName(), x + 7, y + HEADER_HEIGHT * 0.5F + 1, 8.7F, 0xFFFFFFFF);

                float moduleClipHeight = Math.max(0.0F, animatedHeight - HEADER_HEIGHT);
                NvgRenderer.scissor(x, y + HEADER_HEIGHT, width, moduleClipHeight, () ->
                        NvgRenderer.withGlobalAlpha(
                                expandAnimation.value(),
                                () -> renderModules(displayBinds, mouseX, mouseY)
                        )
                );

                if (moduleClipHeight > 0.0F) {
                    float cornerMaskHeight = Math.min(8.0F, moduleClipHeight);
                    NvgRenderer.roundedRectVarying(
                            x, y + animatedHeight - cornerMaskHeight, width, cornerMaskHeight,
                            0.0F, 0.0F, 5.0F, 5.0F, 0xF012121B
                    );
                }
            });
        });
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contains(x, y, width, HEADER_HEIGHT, mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                collapsed = !collapsed;
                scrollTarget = 0.0F;
                modules.forEach(ModulePanel::cancelBinding);
            }
            return true;
        }
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (collapsed || expandAnimation.value() < 0.98F) {
            return true;
        }
        for (ModulePanel module : modules) {
            if (module.mouseClicked(mouseX, mouseY, button)) {
                if (module.isSelectingBind()) {
                    modules.stream().filter(other -> other != module).forEach(ModulePanel::cancelBinding);
                }
                return true;
            }
        }
        return false;
    }

    void mouseReleased() {
        modules.forEach(ModulePanel::mouseReleased);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, float availableHeight) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        for (ModulePanel module : modules) {
            if (module.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        if (collapsed || expandAnimation.value() < 0.98F) {
            scrollTarget = 0.0F;
            return true;
        }
        float maxScroll = Math.max(0, expandedHeight() - availableHeight);
        scrollTarget = Math.max(-maxScroll, Math.min(0, scrollTarget + (float) verticalAmount * 42.0F));
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModulePanel module : modules) {
            if (module.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    boolean charTyped(char character, int modifiers) {
        for (ModulePanel module : modules) {
            if (module.charTyped(character, modifiers)) {
                return true;
            }
        }
        return false;
    }

    boolean isSelectingBind() {
        return modules.stream().anyMatch(ModulePanel::isSelectingBind);
    }

    int moduleCount() {
        return modules.size();
    }

    float getPreferredWidth() {
        NvgFont bold = FontRepository.get("productsans-bold");
        float headerWidth = bold.width(category.displayName(), 8.7F) + 14.0F;
        float maxModuleWidth = 0.0F;
        for (ModulePanel module : modules) {
            maxModuleWidth = Math.max(maxModuleWidth, module.getPreferredWidth());
        }
        return Math.max(headerWidth, maxModuleWidth);
    }

    private float expandedHeight() {
        float height = HEADER_HEIGHT;
        for (ModulePanel module : modules) {
            height += module.totalHeight();
        }
        return height;
    }

    private void renderModules(boolean displayBinds, double mouseX, double mouseY) {
        float moduleY = y + HEADER_HEIGHT + scrollAnimation.value();
        for (int index = 0; index < modules.size(); index++) {
            ModulePanel module = modules.get(index);
            module.render(x, moduleY, width, index == modules.size() - 1, displayBinds, mouseX, mouseY);
            moduleY += module.totalHeight();
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return contains(x, y, width, renderedHeight, mouseX, mouseY);
    }

    private static boolean contains(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
