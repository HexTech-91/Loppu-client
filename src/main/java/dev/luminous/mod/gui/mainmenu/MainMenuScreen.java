package dev.luminous.mod.gui.mainmenu;

import dev.luminous.Alien;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.core.impl.FontManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    private static final float BUTTON_WIDTH = 150.0f;
    private static final float BUTTON_HEIGHT = 20.0f;
    private static final float BUTTON_SPACING = 5.0f;
    private static final float BUTTON_RADIUS = 3.0f;

    private static final float BUTTON_HOVER_SCALE_MAX = 1.05f;
    private static final float BUTTON_HOVER_SCALE_MIN = 1.0f;
    private static final float BUTTON_HOVER_ANIMATION_SPEED = 0.04f;

    private static final float TITLE_Y_OFFSET = 50.0f;
    private static final float TITLE_FONT_SCALE = 1.8f;

    private static final String NAMESPACE = "loppu";

    private final List<MenuButton> buttons = new ArrayList<>();

    private Color buttonNormalColor = new Color(40, 40, 40, 120);
    private Color buttonHoveredColor = new Color(40, 60, 60, 180);

    private Color titleColor = new Color(255, 255, 255, 255);

    // 温柔版本信息：淡入淡出动画
    private final Animation versionFadeAnim = new Animation();
    private long lastInteractTime = 0L;
    private boolean versionVisible = false;

    // 背景温柔动画：进入主菜单时缓缓淡入
    private final Animation bgFadeAnim = new Animation();
    private long bgStartTime = 0L;

    private static class MenuConfig {
        public int buttonNormalColor = new Color(40, 40, 40, 120).getRGB();
        public int buttonHoveredColor = new Color(40, 60, 60, 180).getRGB();
    }

    private MenuConfig menuConfig = new MenuConfig();

    public MainMenuScreen() {
        super(Text.translatable("menu.singleplayer"));
        initializeButtons();
        loadSavedSettings();
    }

    private void initializeButtons() {
        buttons.add(new MenuButton("Singleplayer", () -> {
            client.setScreen(new SelectWorldScreen(this));
        }));

        buttons.add(new MenuButton("Multiplayer", () -> {
            client.setScreen(new MultiplayerScreen(this));
        }));

        buttons.add(new MenuButton("Options", () -> {
            client.setScreen(new OptionsScreen(this, client.options));
        }));

        buttons.add(new MenuButton("Account Manager", () -> {
            try {
                Class<?> accountScreenClass = Class.forName("ru.vidtu.ias.screen.AccountScreen");
                Object accountScreen = accountScreenClass.getConstructor(Screen.class).newInstance(this);
                client.setScreen((Screen) accountScreen);
            } catch (Exception e) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Account Manager is not available"), false);
                }
            }
        }));

        buttons.add(new MenuButton("Quit Game", () -> {
            client.stop();
        }));
    }

    private void loadSavedSettings() {
        try {
            if (Alien.CONFIG != null) {
                String btnNormalStr = (String) Alien.CONFIG.getClass().getMethod("getString", String.class, String.class)
                        .invoke(Alien.CONFIG, "menu_color_button_normal", String.valueOf(buttonNormalColor.getRGB()));
                String btnHoveredStr = (String) Alien.CONFIG.getClass().getMethod("getString", String.class, String.class)
                        .invoke(Alien.CONFIG, "menu_color_button_hovered", String.valueOf(buttonHoveredColor.getRGB()));

                try {
                    menuConfig.buttonNormalColor = Integer.parseInt(btnNormalStr);
                    menuConfig.buttonHoveredColor = Integer.parseInt(btnHoveredStr);
                } catch (NumberFormatException e) {
                }
            }
        } catch (Exception e) {
        }

        buttonNormalColor = new Color(menuConfig.buttonNormalColor, true);
        buttonHoveredColor = new Color(menuConfig.buttonHoveredColor, true);
    }

    private void saveSettings() {
        menuConfig.buttonNormalColor = buttonNormalColor.getRGB();
        menuConfig.buttonHoveredColor = buttonHoveredColor.getRGB();

        try {
            if (Alien.CONFIG != null) {
                Alien.CONFIG.getClass().getMethod("setString", String.class, String.class)
                        .invoke(Alien.CONFIG, "menu_color_button_normal", String.valueOf(menuConfig.buttonNormalColor));
                Alien.CONFIG.getClass().getMethod("setString", String.class, String.class)
                        .invoke(Alien.CONFIG, "menu_color_button_hovered", String.valueOf(menuConfig.buttonHoveredColor));
                Alien.CONFIG.getClass().getMethod("saveSettings").invoke(Alien.CONFIG);
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void init() {
        super.init();
    }

    private Identifier getBackgroundTexture() {
        try {
            return Identifier.of("loppu", "textures/gui/bg7.png");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 首次渲染时记录时间，用于背景淡入
        if (bgStartTime == 0L) {
            bgStartTime = System.currentTimeMillis();
        }
        renderBackgroundWithParallax(context, mouseX, mouseY);

        // ========= 唯一修改：按钮居中 =========
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float totalHeight = (buttons.size() * BUTTON_HEIGHT) + ((buttons.size() - 1) * BUTTON_SPACING);
        float startY = centerY - (totalHeight / 2.0f);

        renderPersonalizedTitle(context, centerX, startY);

        for (int i = 0; i < buttons.size(); i++) {
            float buttonX = centerX - (BUTTON_WIDTH / 2);
            float buttonY = startY + (i * (BUTTON_HEIGHT + BUTTON_SPACING));
            buttons.get(i).render(context, buttonX, buttonY, mouseX, mouseY);
        }
        // ========= 修改结束 =========

        // 温柔版本信息：鼠标悬停左下角区域时显示
        boolean hoverVersion = mouseX >= 0 && mouseX <= 200 && mouseY >= height - 60;
        if (hoverVersion) {
            lastInteractTime = System.currentTimeMillis();
        }
        versionVisible = System.currentTimeMillis() - lastInteractTime < 3000;

        renderVersionInfo(context);
    }

    private void renderVersionInfo(DrawContext context) {
        // 温柔实现：淡入淡出动画，只有鼠标悬停左下角时缓缓浮现
        double fade = versionFadeAnim.get(versionVisible ? 1.0 : 0.0, 600L, Easing.SineOut);
        if (fade < 0.02) return;

        int screenHeight = this.height;

        int leftMargin = 5;
        int bottomMargin = 5;

        String info = "Loppu v2.1.6";
        int lineHeight = 12;

        int yPos = screenHeight - bottomMargin - lineHeight;

        // 半透明底框，增加柔和感
        int textWidth = (int)FontManager.ui.getWidth(info);
        Render2DUtil.drawRoundedRect(context.getMatrices(),
                leftMargin - 2, yPos - 1,
                textWidth + 4, lineHeight + 3,
                3, new Color(0, 0, 0, (int)(50 * fade)));

        // 文字也带透明度渐变，缓缓浮现
        FontManager.ui.drawString(context.getMatrices(), info,
                leftMargin, yPos,
                ((255 & 0xFF) / 255.0f) * (float)fade,
                ((255 & 0xFF) / 255.0f) * (float)fade,
                ((255 & 0xFF) / 255.0f) * (float)fade,
                (float)fade * 0.8f, false);
    }

    private void renderPersonalizedTitle(DrawContext context, float centerX, float buttonsStartY) {
        String titleText;
        if (client != null && client.getSession() != null) {
            String playerName = client.getSession().getUsername();
            if (playerName != null && !playerName.isEmpty()) {
                titleText = "Hi, " + playerName;
            } else {
                titleText = "Welcome Back";
            }
        } else {
            titleText = "Welcome Back";
        }

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        float titleX = centerX;
        float titleY = 20.0f; // 保持原来的标题位置

        matrices.translate(titleX, titleY, 0);
        matrices.scale(TITLE_FONT_SCALE, TITLE_FONT_SCALE, 1.0f);

        Text title = Text.literal(titleText).styled(style -> style.withBold(true));

        float scaledTextWidth = client.textRenderer.getWidth(title) * TITLE_FONT_SCALE;

        context.drawTextWithShadow(client.textRenderer, title,
                (int)(-scaledTextWidth / (2 * TITLE_FONT_SCALE)),
                0,
                titleColor.getRGB());

        matrices.pop();
    }

    // 温柔背景：淡入动画 + 微视差效果 + 柔和暗色遮罩
    private void renderBackgroundWithParallax(DrawContext context, int mouseX, int mouseY) {
        // 背景缓缓淡入，800ms SineOut 缓动
        double bgFade = bgFadeAnim.get(1.0, 800L, Easing.SineOut);

        Identifier backgroundTexture = getBackgroundTexture();

        if (backgroundTexture != null) {
            try {
                AbstractTexture abstractTexture = client.getTextureManager().getTexture(backgroundTexture);

                // 微视差：鼠标位置影响背景偏移，幅度±8px
                float centerX = width / 2.0f;
                float centerY = height / 2.0f;
                float parallaxOffsetX = (mouseX - centerX) / centerX * 8.0f;
                float parallaxOffsetY = (mouseY - centerY) / centerY * 8.0f;

                int texWidth = width;
                int texHeight = height;

                if (abstractTexture instanceof NativeImageBackedTexture) {
                    texWidth = ((NativeImageBackedTexture) abstractTexture).getImage().getWidth();
                    texHeight = ((NativeImageBackedTexture) abstractTexture).getImage().getHeight();
                }

                // 绘制背景：图片画得比屏幕大，露出边缘部分，拖拽时不会露出黑边
                int padding = 64; // 每边多画64px，视差移动时显示的是图片边缘而非黑边
                int destWidth = width + padding * 2;
                int destHeight = height + padding * 2;
                int destX = (int)(-padding + parallaxOffsetX);
                int destY = (int)(-padding + parallaxOffsetY);

                context.getMatrices().push();
                context.drawTexture(backgroundTexture,
                        destX, destY,
                        destWidth, destHeight,
                        0, 0,
                        texWidth, texHeight,
                        texWidth, texHeight);
                context.getMatrices().pop();

                // 柔和暗色遮罩：让背景更温柔，不刺眼
                for (int y = 0; y < height; y++) {
                    float progress = (float) y / height;
                    // 从上到下渐变：顶部遮罩稍浅，底部稍深
                    int maskAlpha = (int) (60 + 40 * progress);
                    context.fill(0, y, width, y + 1,
                            new Color(0, 0, 0, (int) (maskAlpha * bgFade)).getRGB());
                }

                // 最上层：淡入遮罩（从全黑淡出到透明）
                if (bgFade < 1.0) {
                    int fadeAlpha = (int) ((1.0 - bgFade) * 255);
                    context.fill(0, 0, width, height,
                            new Color(0, 0, 0, fadeAlpha).getRGB());
                }

                return;
            } catch (Exception e) {
                System.err.println("Failed to render background: " + e.getMessage());
            }
        }

        renderFallbackBackground(context, bgFade);
    }

    // 温柔回退背景：平滑渐变，不刺眼
    private void renderFallbackBackground(DrawContext context, double fade) {
        // 柔和深蓝 → 深紫 渐变，比原来的红蓝更温柔
        Color topColor = new Color(8, 8, 35);
        Color bottomColor = new Color(25, 10, 40);

        for (int y = 0; y < height; y++) {
            float progress = (float) y / height;
            int r = (int) (topColor.getRed() + (bottomColor.getRed() - topColor.getRed()) * progress);
            int g = (int) (topColor.getGreen() + (bottomColor.getGreen() - topColor.getGreen()) * progress);
            int b = (int) (topColor.getBlue() + (bottomColor.getBlue() - topColor.getBlue()) * progress);
            int alpha = (int) (255 * fade);
            context.fill(0, y, width, y + 1,
                    new Color(r, g, b, alpha).getRGB());
        }

        // 最上层淡入遮罩
        if (fade < 1.0) {
            int fadeAlpha = (int) ((1.0 - fade) * 255);
            context.fill(0, 0, width, height,
                    new Color(0, 0, 0, fadeAlpha).getRGB());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // ========= 同样改为居中计算 =========
            float centerX = width / 2.0f;
            float centerY = height / 2.0f;
            float totalHeight = (buttons.size() * BUTTON_HEIGHT) + ((buttons.size() - 1) * BUTTON_SPACING);
            float startY = centerY - (totalHeight / 2.0f);

            for (int i = 0; i < buttons.size(); i++) {
                float buttonX = centerX - (BUTTON_WIDTH / 2);
                float buttonY = startY + (i * (BUTTON_HEIGHT + BUTTON_SPACING));
                if (isMouseOver(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
                    buttons.get(i).onClick();
                    return true;
                }
            }
            // ========= 修改结束 =========
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOver(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private class MenuButton {
        private final String text;
        private final Runnable action;
        private float hoverAnimation = 1.0f;

        public MenuButton(String text, Runnable action) {
            this.text = text;
            this.action = action;
        }

        public void render(DrawContext context, float x, float y, int mouseX, int mouseY) {
            boolean hovered = isMouseOver(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);

            if (hovered) {
                hoverAnimation = Math.min(BUTTON_HOVER_SCALE_MAX, hoverAnimation + BUTTON_HOVER_ANIMATION_SPEED);
            } else {
                hoverAnimation = Math.max(BUTTON_HOVER_SCALE_MIN, hoverAnimation - BUTTON_HOVER_ANIMATION_SPEED);
            }

            MatrixStack matrices = context.getMatrices();

            matrices.push();

            float centerX = x + BUTTON_WIDTH / 2;
            float centerY = y + BUTTON_HEIGHT / 2;

            matrices.translate(centerX, centerY, 0);
            matrices.scale(hoverAnimation, hoverAnimation, 1.0f);
            matrices.translate(-centerX, -centerY, 0);

            Color buttonColor = hovered ? buttonHoveredColor : buttonNormalColor;
            Render2DUtil.drawRoundedRect(matrices, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, buttonColor);

            FontManager.ui.drawCenteredString(matrices, text, x + BUTTON_WIDTH / 2, y + 6, Color.WHITE.getRGB());

            matrices.pop();
        }

        public void onClick() {
            action.run();
        }
    }
}