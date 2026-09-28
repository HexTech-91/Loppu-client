package dev.luminous.mod.gui;

import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.mod.modules.impl.client.FriendMgr;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class FriendMgrScreen extends Screen {

    // ── 深色主题统一配色 ──
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

    private static final int PANEL_X = 40;
    private static final int PANEL_W = 280;
    private static final int TOP_OFFSET = 30;
    private static final int BOTTOM_MARGIN = 20;
    private static final int BTN_W = 50;

    private TextFieldWidget inputField;
    private float scrollOffset;
    private boolean scrolling;

    // Dynamic friend entries list for display
    private final List<String> displayNames = new ArrayList<>();

    public FriendMgrScreen() {
        super(Text.literal("FriendMgr"));
        refreshDisplayNames();
    }

    private void refreshDisplayNames() {
        displayNames.clear();
        for (Map.Entry<String, Boolean> entry : Alien.FRIEND.friends.entrySet()) {
            String suffix = entry.getValue() ? "+" : "-";
            displayNames.add(entry.getKey() + suffix);
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int inputY = height - 60;
        inputField = new TextFieldWidget(textRenderer,
                cx - PANEL_W / 2 + 10, inputY, PANEL_W - 20, 20, Text.literal("玩家ID"));
        inputField.setMaxLength(64);
        inputField.setPlaceholder(Text.literal("键入玩家ID"));
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
        TextUtil.drawString(context, "好友管理", cx - TextUtil.getWidth("好友管理") / 2, TOP_OFFSET - 4, TEXT_WHITE);

        // ── Updated 按钮 ──
        int updatedBtnY = TOP_OFFSET + 12;
        int updatedBtnH = 20;
        boolean hoverUpdated = Render2DUtil.isHovered(mouseX, mouseY, panelX + 10, updatedBtnY, PANEL_W - 20, updatedBtnH);
        Render2DUtil.drawRoundedRect(context.getMatrices(), panelX + 10, updatedBtnY, PANEL_W - 20, updatedBtnH, 4,
                new Color(hoverUpdated ? BTN_BLUE_HOVER : BTN_BLUE, true));
        TextUtil.drawString(context, "Updated", cx - TextUtil.getWidth("Updated") / 2, updatedBtnY + 5, TEXT_WHITE);

        // ── 好友列表区域 ──
        int listTopY = updatedBtnY + updatedBtnH + 6;
        int listBottomY = inputField.getY() - 8;
        int listHeight = listBottomY - listTopY;
        int itemH = 22;
        int itemGap = 2;

        // Calculate total content height
        int totalContentH = displayNames.size() * (itemH + itemGap);
        int maxScroll = Math.max(0, totalContentH - listHeight);

        // Clamp scroll
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

        // Scissor/clip for friend list
        context.enableScissor(panelX + 2, listTopY, panelX + PANEL_W - 8, listBottomY);

        int drawY = listTopY + (int) scrollOffset;
        int itemX = panelX + 10;
        int btnAreaX = panelX + PANEL_W - 10 - BTN_W * 2 - 4;

        for (int i = 0; i < displayNames.size(); i++, drawY += itemH + itemGap) {
            String entry = displayNames.get(i);
            if (drawY + itemH < listTopY || drawY > listBottomY) continue;

            // Extract name and suffix
            String name;
            boolean hasPermission;
            if (entry.endsWith("+")) {
                name = entry.substring(0, entry.length() - 1);
                hasPermission = true;
            } else if (entry.endsWith("-")) {
                name = entry.substring(0, entry.length() - 1);
                hasPermission = false;
            } else {
                name = entry;
                hasPermission = true;
            }

            // Truncate long names with --
            String displayText = name;
            int nameMaxW = btnAreaX - itemX - 6;
            if (TextUtil.getWidth(displayText) > nameMaxW) {
                while (TextUtil.getWidth(displayText + "--") > nameMaxW && displayText.length() > 1) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText = displayText + "--";
            }

            int textColor = hasPermission ? TEXT_GREEN : TEXT_RED;
            TextUtil.drawString(context, displayText, itemX + 4, drawY + 6, textColor);

            // ── [删除] 按钮 ──
            int deleteBtnX = btnAreaX;
            boolean hoverDelete = Render2DUtil.isHovered(mouseX, mouseY, deleteBtnX, drawY, BTN_W, itemH);
            Render2DUtil.drawRoundedRect(context.getMatrices(), deleteBtnX, drawY, BTN_W, itemH, 4,
                    new Color(hoverDelete ? BTN_RED_HOVER : BTN_RED, true));
            TextUtil.drawString(context, "删除",
                    deleteBtnX + (BTN_W - TextUtil.getWidth("删除")) / 2, drawY + 6, TEXT_WHITE);

            // ── [恢复] 按钮 ──
            int restoreBtnX = btnAreaX + BTN_W + 4;
            boolean hoverRestore = Render2DUtil.isHovered(mouseX, mouseY, restoreBtnX, drawY, BTN_W, itemH);
            Render2DUtil.drawRoundedRect(context.getMatrices(), restoreBtnX, drawY, BTN_W, itemH, 4,
                    new Color(hoverRestore ? BTN_GREEN_HOVER : BTN_GREEN, true));
            TextUtil.drawString(context, "恢复",
                    restoreBtnX + (BTN_W - TextUtil.getWidth("恢复")) / 2, drawY + 6, TEXT_WHITE);
        }

        context.disableScissor();

        // Render input field and other widgets
        super.render(context, mouseX, mouseY, delta);

        // ── Add 按钮 ──
        int addBtnY = inputField.getY() + inputField.getHeight() + 4;
        int addBtnH = 20;
        boolean hoverAdd = Render2DUtil.isHovered(mouseX, mouseY, panelX + 10, addBtnY, PANEL_W - 20, addBtnH);
        Render2DUtil.drawRoundedRect(context.getMatrices(), panelX + 10, addBtnY, PANEL_W - 20, addBtnH, 4,
                new Color(hoverAdd ? BTN_GREEN_HOVER : BTN_GREEN, true));
        TextUtil.drawString(context, "Add", cx - TextUtil.getWidth("Add") / 2, addBtnY + 5, TEXT_WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = width / 2;
            int panelX = cx - PANEL_W / 2;

            // Updated button
            int updatedBtnY = TOP_OFFSET + 12;
            int updatedBtnH = 20;
            if (Render2DUtil.isHovered(mouseX, mouseY, panelX + 10, updatedBtnY, PANEL_W - 20, updatedBtnH)) {
                onUpdated();
                return true;
            }

            // Friend list area
            int listTopY = updatedBtnY + updatedBtnH + 6;
            int itemH = 22;
            int itemGap = 2;
            int btnAreaX = panelX + PANEL_W - 10 - BTN_W * 2 - 4;

            for (int i = 0; i < displayNames.size(); i++) {
                int drawY = listTopY + (int) scrollOffset + i * (itemH + itemGap);
                String entry = displayNames.get(i);
                String name = entry.endsWith("+") || entry.endsWith("-")
                        ? entry.substring(0, entry.length() - 1) : entry;

                // [删除] button
                int deleteBtnX = btnAreaX;
                if (Render2DUtil.isHovered(mouseX, mouseY, deleteBtnX, drawY, BTN_W, itemH)) {
                    onDeleteFriend(name);
                    return true;
                }

                // [恢复] button
                int restoreBtnX = btnAreaX + BTN_W + 4;
                if (Render2DUtil.isHovered(mouseX, mouseY, restoreBtnX, drawY, BTN_W, itemH)) {
                    onRestoreFriend(name);
                    return true;
                }
            }

            // Scrollbar click
            int listBottomY = inputField.getY() - 8;
            int listHeight = listBottomY - listTopY;
            int totalContentH = displayNames.size() * (itemH + itemGap);
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
            int updatedBtnY = TOP_OFFSET + 12;
            int updatedBtnH = 20;
            int listTopY = updatedBtnY + updatedBtnH + 6;
            int listBottomY = inputField.getY() - 8;
            int listHeight = listBottomY - listTopY;
            int itemH = 22;
            int itemGap = 2;
            int totalContentH = displayNames.size() * (itemH + itemGap);
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
        int updatedBtnY = TOP_OFFSET + 12;
        int updatedBtnH = 20;
        int listTopY = updatedBtnY + updatedBtnH + 6;
        int listBottomY = inputField.getY() - 8;
        int listHeight = listBottomY - listTopY;
        int itemH = 22;
        int itemGap = 2;
        int totalContentH = displayNames.size() * (itemH + itemGap);
        int maxScroll = Math.max(0, totalContentH - listHeight);

        scrollOffset += (float) (-verticalAmount * 15);
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // When ESC is pressed, disable the module
        if (FriendMgr.INSTANCE != null && FriendMgr.INSTANCE.isOn()) {
            FriendMgr.INSTANCE.disable();
        }
        return true;
    }

    @Override
    public void close() {
        if (FriendMgr.INSTANCE != null && FriendMgr.INSTANCE.isOn()) {
            FriendMgr.INSTANCE.disable();
        }
        super.close();
    }

    // ── Event handlers ──

    private void onDeleteFriend(String name) {
        // 点击删除：好友配置文件中此id加上"-"标签后缀（设置permission为false）
        Boolean status = Alien.FRIEND.getStatus(name);
        if (status != null && status) {
            Alien.FRIEND.togglePermission(name);
        }
        refreshDisplayNames();
        Alien.FRIEND.save();
    }

    private void onRestoreFriend(String name) {
        // 点击恢复：去掉配置文件中"-"后缀（恢复为正常好友，设置permission为true）
        Alien.FRIEND.add(name);
        refreshDisplayNames();
        Alien.FRIEND.save();
    }

    private void onAdd() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        Alien.FRIEND.add(text);
        refreshDisplayNames();
        Alien.FRIEND.save();
        inputField.setText("");
    }

    private void onUpdated() {
        // Read existing friends file, mark all with '+' suffix
        Alien.FRIEND.read();
        for (String name : new ArrayList<>(Alien.FRIEND.friends.keySet())) {
            Alien.FRIEND.friends.put(name, true);
        }
        Alien.FRIEND.save();
        Alien.FRIEND.read();
        refreshDisplayNames();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}