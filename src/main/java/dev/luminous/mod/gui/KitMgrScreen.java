package dev.luminous.mod.gui;

import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.core.Manager;
import dev.luminous.mod.modules.impl.client.KitMgr;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class KitMgrScreen extends Screen {

    // ── 深色主题统一配色（与 FriendMgrScreen 一致）──
    private static final int BG_COLOR = 0xFF080C18;
    private static final int CARD_COLOR = 0xCC0F1A2E;
    private static final int CARD_BORDER = 0xCC3A6A9A;
    private static final int BTN_NORMAL = 0xCC1A2D4A;
    private static final int BTN_HOVER = 0xCC2A4570;
    private static final int BTN_GREEN = 0xCC0D4A2A;
    private static final int BTN_GREEN_HOVER = 0xCC1A6B3D;
    private static final int BTN_RED = 0xCC4A1A1A;
    private static final int BTN_RED_HOVER = 0xCC6B2A2A;
    private static final int BTN_BLUE = 0xCC1A3A6A;
    private static final int BTN_BLUE_HOVER = 0xCC2A5A8A;
    private static final int SCROLLBAR = 0xCC2A4A7A;
    private static final int SCROLLBAR_HOVER = 0xCC3A6A9A;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF7A8BA0;
    private static final int TEXT_GREEN = 0xFF2EAA5A;
    private static final int TEXT_RED = 0xFFAA3A3A;

    private static final int PANEL_W = 280;
    private static final int TOP_OFFSET = 30;
    private static final int BOTTOM_MARGIN = 20;
    private static final int BTN_W = 50;

    private TextFieldWidget inputField;
    private float scrollOffset;
    private boolean scrolling;

    private final List<String> kitNames = new ArrayList<>();
    private String activeKitName = null;

    public KitMgrScreen() {
        super(Text.literal("KitMgr"));
        refreshKitList();
    }

    private void refreshKitList() {
        kitNames.clear();
        try {
            File folder = Manager.getFile("kits");
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".kit")) {
                            String name = f.getName().substring(0, f.getName().length() - 4);
                            kitNames.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int inputY = height - 60;
        inputField = new TextFieldWidget(textRenderer,
                cx - PANEL_W / 2 + 10, inputY, PANEL_W - 20, 20, Text.literal("Kit名称"));
        inputField.setMaxLength(64);
        inputField.setPlaceholder(Text.literal("键入Kit名称"));
        addDrawableChild(inputField);
        setInitialFocus(inputField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int cx = width / 2;
        int panelX = cx - PANEL_W / 2;

        // ── 背景卡片（带圆角边框）──
        float cardTop = TOP_OFFSET - 10;
        float cardH = (height - BOTTOM_MARGIN) - cardTop;
        Render2DUtil.drawRoundedRect(context.getMatrices(), panelX, cardTop, PANEL_W, cardH, 6, new Color(CARD_BORDER, true));
        Render2DUtil.drawRoundedRect(context.getMatrices(), panelX + 1, cardTop + 1, PANEL_W - 2, cardH - 2, 5, new Color(CARD_COLOR, true));

        // ── 标题 ──
        String title = "Kit管理";
        TextUtil.drawString(context, title, cx - TextUtil.getWidth(title) / 2, TOP_OFFSET - 4, TEXT_WHITE);

        // ── 列表区域 ──
        int listTopY = TOP_OFFSET + 12;
        int listBottomY = inputField.getY() - 8;
        int listHeight = listBottomY - listTopY;
        int itemH = 22;
        int itemGap = 2;

        int totalContentH = kitNames.size() * (itemH + itemGap);
        int maxScroll = Math.max(0, totalContentH - listHeight);

        if (scrollOffset > 0) scrollOffset = 0;
        if (scrollOffset < -maxScroll) scrollOffset = -maxScroll;

        // Scrollbar
        if (maxScroll > 0) {
            int scrollBarH = Math.max(20, listHeight * listHeight / totalContentH);
            int scrollBarY = listTopY + (int)((float)(-scrollOffset) / maxScroll * (listHeight - scrollBarH));
            boolean hoverScroll = Render2DUtil.isHovered(mouseX, mouseY, panelX + PANEL_W - 6, scrollBarY, 4, scrollBarH);
            Render2DUtil.drawRoundedRect(context.getMatrices(), panelX + PANEL_W - 6, scrollBarY, 4, scrollBarH, 2,
                    new Color(hoverScroll ? SCROLLBAR_HOVER : SCROLLBAR, true));
        }

        // Scissor/clip for kit list
        context.enableScissor(panelX + 2, listTopY, panelX + PANEL_W - 8, listBottomY);

        int drawY = listTopY + (int) scrollOffset;
        int itemX = panelX + 10;
        int itemW = PANEL_W - 28 - 6; // width for name text (left area)
        int btnAreaX = panelX + PANEL_W - 10 - BTN_W * 2 - 4; // start of button area

        for (int i = 0; i < kitNames.size(); i++, drawY += itemH + itemGap) {
            String name = kitNames.get(i);
            if (drawY + itemH < listTopY || drawY > listBottomY) continue;

            boolean isActive = name.equals(activeKitName);

            // ── 名称文本 ──
            String displayText = name;
            int nameMaxW = btnAreaX - itemX - 6;
            if (TextUtil.getWidth(displayText) > nameMaxW) {
                while (TextUtil.getWidth(displayText + "--") > nameMaxW && displayText.length() > 1) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText = displayText + "--";
            }

            TextUtil.drawString(context, displayText, itemX + 4, drawY + 6, isActive ? TEXT_GREEN : TEXT_WHITE);

            // ── [启用] 按钮 ──
            int enableBtnX = btnAreaX;
            int enableBtnW = BTN_W;
            boolean hoverEnable = Render2DUtil.isHovered(mouseX, mouseY, enableBtnX, drawY, enableBtnW, itemH);
            int enableColor = isActive ? BTN_NORMAL : (hoverEnable ? BTN_GREEN_HOVER : BTN_GREEN);
            Render2DUtil.drawRoundedRect(context.getMatrices(), enableBtnX, drawY, enableBtnW, itemH, 4,
                    new Color(enableColor, true));
            String enableText = isActive ? "已启用" : "启用";
            TextUtil.drawString(context, enableText,
                    enableBtnX + (enableBtnW - TextUtil.getWidth(enableText)) / 2, drawY + 6,
                    isActive ? TEXT_GRAY : TEXT_WHITE);

            // ── [删除] 按钮 ──
            int deleteBtnX = btnAreaX + BTN_W + 4;
            int deleteBtnW = BTN_W;
            boolean hoverDelete = Render2DUtil.isHovered(mouseX, mouseY, deleteBtnX, drawY, deleteBtnW, itemH);
            Render2DUtil.drawRoundedRect(context.getMatrices(), deleteBtnX, drawY, deleteBtnW, itemH, 4,
                    new Color(hoverDelete ? BTN_RED_HOVER : BTN_RED, true));
            TextUtil.drawString(context, "删除",
                    deleteBtnX + (deleteBtnW - TextUtil.getWidth("删除")) / 2, drawY + 6, TEXT_WHITE);
        }

        context.disableScissor();

        // Render input field and other widgets
        super.render(context, mouseX, mouseY, delta);

        // ── Add 按钮 ──
        int addBtnY = inputField.getY() + inputField.getHeight() + 4;
        int addBtnH = 20;
        boolean hoverAdd = Render2DUtil.isHovered(mouseX, mouseY, panelX + 10, addBtnY, PANEL_W - 20, addBtnH);
        Render2DUtil.drawRoundedRect(context.getMatrices(), panelX + 10, addBtnY, PANEL_W - 20, addBtnH, 4,
                new Color(hoverAdd ? BTN_BLUE_HOVER : BTN_BLUE, true));
        TextUtil.drawString(context, "Add", cx - TextUtil.getWidth("Add") / 2, addBtnY + 5, TEXT_WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = width / 2;
            int panelX = cx - PANEL_W / 2;

            // Kit list area
            int listTopY = TOP_OFFSET + 12;
            int listBottomY = inputField.getY() - 8;
            int itemH = 22;
            int itemGap = 2;
            int btnAreaX = panelX + PANEL_W - 10 - BTN_W * 2 - 4;

            for (int i = 0; i < kitNames.size(); i++) {
                int drawY = listTopY + (int) scrollOffset + i * (itemH + itemGap);
                String name = kitNames.get(i);

                // [启用] button
                int enableBtnX = btnAreaX;
                if (Render2DUtil.isHovered(mouseX, mouseY, enableBtnX, drawY, BTN_W, itemH)) {
                    if (!name.equals(activeKitName)) {
                        onEnableKit(name);
                    }
                    return true;
                }

                // [删除] button
                int deleteBtnX = btnAreaX + BTN_W + 4;
                if (Render2DUtil.isHovered(mouseX, mouseY, deleteBtnX, drawY, BTN_W, itemH)) {
                    onDeleteKit(name);
                    return true;
                }
            }

            // Scrollbar click
            int listHeight = listBottomY - listTopY;
            int totalContentH = kitNames.size() * (itemH + itemGap);
            int maxScroll = Math.max(0, totalContentH - listHeight);
            if (maxScroll > 0 && Render2DUtil.isHovered(mouseX, mouseY, panelX + PANEL_W - 6, listTopY, 4, listHeight)) {
                scrolling = true;
                return true;
            }

            // Add button
            int addBtnY = inputField.getY() + inputField.getHeight() + 4;
            int addBtnH = 20;
            if (Render2DUtil.isHovered(mouseX, mouseY, panelX + 10, addBtnY, PANEL_W - 20, addBtnH)) {
                onAdd();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrolling && button == 0) {
            int cx = width / 2;
            int panelX = cx - PANEL_W / 2;
            int listTopY = TOP_OFFSET + 12;
            int listBottomY = inputField.getY() - 8;
            int listHeight = listBottomY - listTopY;
            int itemH = 22;
            int itemGap = 2;
            int totalContentH = kitNames.size() * (itemH + itemGap);
            int maxScroll = Math.max(0, totalContentH - listHeight);

            float ratio = (float) listHeight / totalContentH;
            float scrollBarH = Math.max(20, listHeight * ratio);
            float dragRange = listHeight - scrollBarH;

            float relY = (float) mouseY - listTopY - scrollBarH / 2;
            float scrollRatio = relY / dragRange;
            scrollOffset = -scrollRatio * maxScroll;
            scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int cx = width / 2;
        int panelX = cx - PANEL_W / 2;
        int listTopY = TOP_OFFSET + 12;
        int listBottomY = inputField.getY() - 8;
        int listHeight = listBottomY - listTopY;
        int itemH = 22;
        int itemGap = 2;
        int totalContentH = kitNames.size() * (itemH + itemGap);
        int maxScroll = Math.max(0, totalContentH - listHeight);

        scrollOffset += (float) (-verticalAmount * 15);
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (KitMgr.INSTANCE != null && KitMgr.INSTANCE.isOn()) {
            KitMgr.INSTANCE.disable();
        }
        return true;
    }

    @Override
    public void close() {
        if (KitMgr.INSTANCE != null && KitMgr.INSTANCE.isOn()) {
            KitMgr.INSTANCE.disable();
        }
        super.close();
    }

    // ── Event handlers ──

    private void onEnableKit(String name) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        activeKitName = name;
        String prefix = Alien.getPrefix();
        String command = prefix + "kit load " + name;
        client.player.networkHandler.sendChatMessage(command);
    }

    private void onDeleteKit(String name) {
        try {
            File folder = Manager.getFile("kits");
            File kitFile = new File(folder, name + ".kit");
            if (kitFile.exists()) {
                kitFile.delete();
            }
            if (name.equals(activeKitName)) {
                activeKitName = null;
            }
            refreshKitList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onAdd() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || client.player == null || client.getNetworkHandler() == null) return;
        String prefix = Alien.getPrefix();
        String command = prefix + "kit save " + text;
        client.player.networkHandler.sendChatMessage(command);
        inputField.setText("");
        // Refresh list after a short delay to allow the kit file to be created
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (client != null) {
                client.execute(this::refreshKitList);
            }
        }).start();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}