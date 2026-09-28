package dev.luminous.mod.gui.nakano.click;

import dev.luminous.mod.gui.nakano.NvgRenderer;
import dev.luminous.mod.gui.nakano.animation.Animation;
import dev.luminous.mod.gui.nakano.animation.Easing;
import dev.luminous.mod.modules.impl.client.ClickGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Nakano's Opal-style ClickGUI, ported into the loppu client. */
public final class NakanoClickGuiScreen extends Screen {
    private static final double GLOW_BREATH_PERIOD_SECONDS = 2.6D;
    private static final float PAN_SPEED = 42.0F;
    private static NakanoClickGuiScreen instance;

    private final List<CategoryPanel> panels = new ArrayList<>();
    private final Animation screenAnimation = new Animation(Easing.EASE_OUT_SINE, 260);
    private float panX;
    private float panY;
    private boolean closing;

    private NakanoClickGuiScreen() {
        super(Text.literal("Nakano ClickGUI"));
        rebuildPanels();
    }

    public static NakanoClickGuiScreen getInstance() {
        if (instance == null) {
            instance = new NakanoClickGuiScreen();
        }
        return instance;
    }

    static boolean glowEnabled() {
        ClickGui clickGui = ClickGui.getInstance();
        return clickGui == null || clickGui.nakanoGlow.getValue();
    }

    static float glowStrength() {
        ClickGui clickGui = ClickGui.getInstance();
        return clickGui == null ? 0.65F : clickGui.nakanoGlowStrength.getValueFloat();
    }

    public void prepareOpen() {
        if (panels.stream().allMatch(panel -> panel.moduleCount() == 0)) {
            rebuildPanels();
        }
        panX = 0.0F;
        panY = 0.0F;
        closing = false;
        panels.forEach(CategoryPanel::prepareOpen);
    }

    public void requestClose() {
        // Close immediately, matching the classic ClickGUI's ESC behavior.
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        // Background is drawn by the NanoVG pipeline.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        boolean ownsFrame = NvgRenderer.beginFrame();
        try {
            screenAnimation.run(closing ? 0.0F : 1.0F);
            NvgRenderer.withGlobalAlpha(screenAnimation.value(),
                    () -> renderClickGui(mouseX, mouseY));
        } finally {
            if (ownsFrame) {
                NvgRenderer.endFrame();
            }
        }

        NvgRenderer.flushText(context);
    }

    private void renderClickGui(int mouseX, int mouseY) {
        NvgRenderer.rect(0, 0, width, height, 0x8A05050A);

        int panelCount = panels.size();
        float spacing = 4.0F;
        float sideMargin = Math.max(8.0F, width * 0.012F);

        float[] panelWidths = new float[panelCount];
        float totalContentWidth = 0.0F;
        for (int i = 0; i < panelCount; i++) {
            float pw = panels.get(i).getPreferredWidth();
            panelWidths[i] = pw;
            totalContentWidth += pw;
        }

        float totalWidth = totalContentWidth + spacing * (panelCount - 1);
        float startX = (width - totalWidth) * 0.5F;
        // Ensure first panel doesn't go off-screen when total width exceeds screen width
        startX = Math.max(startX, sideMargin);
        float panelY = 18.0F;
        float availableHeight = Math.max(60.0F, height - panelY - 12.0F);
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean displayBinds = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
        double glowPhase = System.nanoTime() / 1_000_000_000.0D
                * (Math.PI * 2.0D / GLOW_BREATH_PERIOD_SECONDS);
        float glowBreath = 0.55F + 0.45F * (float) ((Math.sin(glowPhase) + 1.0D) * 0.5D);

        float currentX = startX + panX;
        float currentY = panelY + panY;
        for (int index = 0; index < panelCount; index++) {
            panels.get(index).render(
                    currentX, currentY, panelWidths[index],
                    availableHeight, displayBinds, glowBreath,
                    mouseX, mouseY
            );
            currentX += panelWidths[index] + spacing;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        for (CategoryPanel panel : panels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panels.forEach(CategoryPanel::mouseReleased);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean leftShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean rightShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (leftShift) {
            // First try to scroll long setting text
            for (CategoryPanel panel : panels) {
                if (panel.mouseScrolled(mouseX, mouseY, verticalAmount, 0)) return true;
            }
            // Fall through to panel panning
            panX += (float) verticalAmount * PAN_SPEED;
            clampPan();
            return true;
        }
        if (rightShift) {
            panX += (float) verticalAmount * PAN_SPEED;
            clampPan();
            return true;
        }
        if (Screen.hasControlDown()) {
            panY += (float) verticalAmount * PAN_SPEED;
            clampPan();
            return true;
        }
        float availableHeight = Math.max(60.0F, height - 30.0F);
        for (CategoryPanel panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, verticalAmount, availableHeight)) return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (CategoryPanel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            ClickGui clickGui = ClickGui.getInstance();
            if (clickGui == null || clickGui.getBindSetting().getValue() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                requestClose();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        float arrowPan = 30.0F;
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            panX += arrowPan;
            clampPan();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            panX -= arrowPan;
            clampPan();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            panY += arrowPan;
            clampPan();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            panY -= arrowPan;
            clampPan();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        for (CategoryPanel panel : panels) {
            if (panel.charTyped(character, modifiers)) return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void close() {
        if (panels.stream().anyMatch(CategoryPanel::isSelectingBind)) return;
        requestClose();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void clampPan() {
        int panelCount = panels.size();
        float spacing = 4.0F;
        float sideMargin = Math.max(8.0F, width * 0.012F);
        float totalContentWidth = 0.0F;
        for (int i = 0; i < panelCount; i++) {
            totalContentWidth += panels.get(i).getPreferredWidth();
        }
        float totalWidth = totalContentWidth + spacing * (panelCount - 1);
        float startX = Math.max(sideMargin, (width - totalWidth) * 0.5F);

        float maxRight = sideMargin;
        float maxLeft = Math.min(maxRight, -(startX + totalWidth - width) - sideMargin);
        panX = Math.max(maxLeft, Math.min(maxRight, panX));

        float panelY = 18.0F;
        float availableHeight = Math.max(60.0F, height - panelY - 12.0F);
        float maxDown = 20.0F;
        float maxUp = -Math.max(0, availableHeight * 0.5F);
        panY = Math.max(maxUp, Math.min(maxDown, panY));
    }

    private void rebuildPanels() {
        panels.clear();
        GuiCategory[] categories = GuiCategory.values();
        for (int index = 0; index < categories.length; index++) {
            panels.add(new CategoryPanel(categories[index], index));
        }
    }
}