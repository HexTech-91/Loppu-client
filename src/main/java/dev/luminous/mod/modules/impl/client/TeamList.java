package dev.luminous.mod.modules.impl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.nakano.FontRepository;
import dev.luminous.mod.gui.nakano.NvgFont;
import dev.luminous.mod.gui.nakano.NvgRenderer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

/** Compact transparent HUD for online friends and vanilla scoreboard teammates. Ported from Nakano. */
public final class TeamList extends Module {
    public static TeamList INSTANCE;

    private static final int ROW_HEIGHT = 48;
    private static final int HEAD_FRAME_SIZE = 40;
    private static final int HEAD_SIZE = 36;
    private static final float HEAD_CORNER_RADIUS = 7.0F;
    private static final float HEALTH_BAR_HEIGHT = 7.0F;
    private static final float HEALTH_BAR_RADIUS = HEALTH_BAR_HEIGHT * 0.5F;
    private static final float NAME_FONT_SIZE = 8.8F;
    private static final int TOTEM_BURST_MILLIS = 540;
    private static final int DEATH_ANIMATION_MILLIS = 650;
    private static final int DISAPPEAR_ANIMATION_MILLIS = 440;

    private final SliderSetting x = this.add(new SliderSetting("X", 2, 0, 100));
    private final SliderSetting y = this.add(new SliderSetting("Y", 15, 0, 100));
    private final SliderSetting scale = this.add(new SliderSetting("Scale", 1.0, 0.6, 1.6, 0.05));
    private final EnumSetting<Source> source = this.add(new EnumSetting<>("Source", Source.Both));
    private final BooleanSetting showSelf = this.add(new BooleanSetting("ShowSelf", false));
    private final SliderSetting maxPlayers = this.add(new SliderSetting("MaxPlayers", 8, 1, 24));
    private final SliderSetting shakeStrength = this.add(new SliderSetting("ShakeStrength", 2.25, 0.0, 5.0, 0.25));
    private final SliderSetting shakeTime = this.add(new SliderSetting("ShakeTime", 220, 80, 500, 10).setSuffix("ms"));

    private final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private final Map<UUID, Long> shakeStartedAt = new HashMap<>();
    private final Map<UUID, Integer> popCounts = new HashMap<>();
    private final Map<UUID, Long> popStartedAt = new HashMap<>();
    private final Map<UUID, MemberSnapshot> visibleMembers = new HashMap<>();
    private final Map<UUID, DepartureAnimation> departingMembers = new HashMap<>();

    public TeamList() {
        super("TeamList", "Shows online friends and scoreboard teammates.", Category.Client);
        this.setChinese("队伍列表");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) {
            clearTracking();
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        Set<UUID> deaths = new HashSet<>();
        for (AbstractClientPlayerEntity player : collectMembers()) {
            UUID uuid = player.getUuid();
            if (isDead(player)) {
                deaths.add(uuid);
                startDeparture(
                        uuid,
                        visibleMembers.getOrDefault(uuid, MemberSnapshot.of(player)),
                        DepartureReason.DEATH,
                        now
                );
                continue;
            }

            float health = player.getHealth() + player.getAbsorptionAmount();
            int hurtTime = player.hurtTime;
            PlayerState previous = playerStates.put(uuid, new PlayerState(health, hurtTime));
            int popCount = Alien.POP == null ? 0 : Alien.POP.getPop(player);
            Integer previousPopCount = popCounts.put(uuid, popCount);
            seen.add(uuid);
            visibleMembers.put(uuid, MemberSnapshot.of(player));
            departingMembers.remove(uuid);

            if (previous != null
                    && ((hurtTime > 0 && hurtTime > previous.hurtTime())
                    || health < previous.health() - 0.01F)) {
                shakeStartedAt.put(uuid, now);
            }
            if (previousPopCount != null && popCount > previousPopCount) {
                popStartedAt.put(uuid, now);
            }
        }

        visibleMembers.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }
            startDeparture(
                    entry.getKey(),
                    entry.getValue(),
                    deaths.contains(entry.getKey()) ? DepartureReason.DEATH : DepartureReason.DISAPPEARED,
                    now
            );
            return true;
        });
        playerStates.keySet().retainAll(seen);
        shakeStartedAt.keySet().retainAll(seen);
        popCounts.keySet().retainAll(seen);
        popStartedAt.keySet().retainAll(seen);
        shakeStartedAt.entrySet().removeIf(entry -> now - entry.getValue() > shakeTime.getValueInt());
        popStartedAt.entrySet().removeIf(entry -> now - entry.getValue() > TOTEM_BURST_MILLIS);
        departingMembers.entrySet().removeIf(entry -> entry.getValue().finished(now));
    }

    @EventListener
    private void onTotemPop(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID uuid = player.getUuid();
        int currentCount = Alien.POP == null ? 1 : Alien.POP.getPop(player);
        Integer previousCount = popCounts.put(uuid, currentCount);
        if (previousCount == null || currentCount > previousCount) {
            popStartedAt.put(uuid, System.currentTimeMillis());
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (nullCheck() || mc.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        List<AbstractClientPlayerEntity> members = collectMembers().stream()
                .filter(player -> !isDead(player))
                .filter(player -> !departingMembers.containsKey(player.getUuid()))
                .toList();
        long now = System.currentTimeMillis();
        List<DepartureAnimation> departures = departingMembers.values().stream()
                .filter(animation -> !animation.finished(now))
                .sorted(Comparator.comparing(animation -> animation.snapshot().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (members.isEmpty() && departures.isEmpty()) {
            return;
        }

        int panelWidth = getPanelWidth(members, departures);
        int panelHeight = (members.size() + departures.size()) * ROW_HEIGHT;
        float renderScale = scale.getValueFloat();
        // 百分比定位：X/Y 为屏幕宽度/高度的百分比（与 BackPackHUD 一致），仍钳制在屏幕内
        float screenWidth = mc.getWindow().getScaledWidth();
        float screenHeight = mc.getWindow().getScaledHeight();
        float anchorX = screenWidth * (x.getValueFloat() / 100.0F);
        float anchorY = screenHeight * (y.getValueFloat() / 100.0F);
        float maxX = Math.max(2.0F, screenWidth - panelWidth * renderScale - 2.0F);
        float maxY = Math.max(2.0F, screenHeight - panelHeight * renderScale - 2.0F);
        float renderX = MathHelper.clamp(anchorX, 2.0F, maxX);
        float renderY = MathHelper.clamp(anchorY, 2.0F, maxY);
        List<NameLabel> names = new ArrayList<>();

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(renderX, renderY, 0.0F);
            matrices.scale(renderScale, renderScale, 1.0F);
            drawMembers(context, members, departures, panelWidth, now, names, renderX, renderY, renderScale);
        } finally {
            matrices.pop();
        }
        renderNames(names);
    }

    @Override
    public void onDisable() {
        clearTracking();
    }

    @Override
    public void onLogout() {
        clearTracking();
    }

    private List<AbstractClientPlayerEntity> collectMembers() {
        Team ownTeam = mc.player.getScoreboardTeam();
        List<AbstractClientPlayerEntity> result = new ArrayList<>();

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                if (showSelf.getValue()) {
                    result.add(player);
                }
                continue;
            }

            boolean friend = Alien.FRIEND != null && Alien.FRIEND.isFriend(player);
            boolean scoreboardTeammate = ownTeam != null && ownTeam.equals(player.getScoreboardTeam());
            if (source.getValue().includes(friend, scoreboardTeammate)) {
                result.add(player);
            }
        }

        result.sort(Comparator.comparing(player -> player.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        int limit = Math.min(maxPlayers.getValueInt(), result.size());
        return new ArrayList<>(result.subList(0, limit));
    }

    private int getPanelWidth(List<AbstractClientPlayerEntity> members, List<DepartureAnimation> departures) {
        int widestName = 0;
        for (AbstractClientPlayerEntity player : members) {
            widestName = Math.max(widestName, getNameWidth(player.getGameProfile().getName()));
        }
        for (DepartureAnimation departure : departures) {
            widestName = Math.max(widestName, getNameWidth(departure.snapshot().name()));
        }
        return MathHelper.clamp(HEAD_FRAME_SIZE + widestName + 32, 168, 250);
    }

    private void drawMembers(
            DrawContext context,
            List<AbstractClientPlayerEntity> members,
            List<DepartureAnimation> departures,
            int width,
            long now,
            List<NameLabel> names,
            float renderX,
            float renderY,
            float renderScale
    ) {
        int rowY = 0;
        for (AbstractClientPlayerEntity player : members) {
            UUID uuid = player.getUuid();
            int[] shake = getShakeOffset(uuid, now);
            int headX = 8 + shake[0];
            int headY = rowY + 2 + shake[1];

            float popProgress = getPopProgress(uuid, now);
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            try {
                if (popProgress >= 0.0F) {
                    float pulseScale = 1.0F + 0.14F * (float) Math.sin(popProgress * Math.PI);
                    float centerX = headX + HEAD_FRAME_SIZE / 2.0F;
                    float centerY = headY + HEAD_FRAME_SIZE / 2.0F;
                    matrices.translate(centerX, centerY, 0.0F);
                    matrices.scale(pulseScale, pulseScale, 1.0F);
                    matrices.translate(-centerX, -centerY, 0.0F);
                }

                drawHead(context, player.getSkinTextures(), headX, headY, 1.0F);
                if (popProgress >= 0.0F) {
                    drawTotemBurst(context, headX, headY, popProgress);
                }
            } finally {
                matrices.pop();
            }

            int contentX = 58;
            queueName(names, player.getGameProfile().getName(), contentX, rowY + 13.0F, 1.0F, 0xFFF5F5F5,
                    renderX, renderY, renderScale);
            drawHealthBar(context, MemberSnapshot.of(player), contentX, rowY + 27, width - contentX - 10, 1.0F);
            rowY += ROW_HEIGHT;
        }

        for (DepartureAnimation departure : departures) {
            drawDeparture(context, departure, rowY, width, now, names, renderX, renderY, renderScale);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawDeparture(
            DrawContext context,
            DepartureAnimation departure,
            int rowY,
            int width,
            long now,
            List<NameLabel> names,
            float renderX,
            float renderY,
            float renderScale
    ) {
        float progress = departure.progress(now);
        float eased = easeOutCubic(progress);
        float alpha = 1.0F - eased;
        boolean death = departure.reason() == DepartureReason.DEATH;
        float offsetX = death ? -20.0F * eased : 26.0F * eased;
        float rowScale = death ? 1.0F - 0.22F * eased : 1.0F;
        int statusColor = death ? 0xFFE85C68 : 0xFF91A1C1;
        String status = death ? "  DEAD" : "  LEFT VIEW";
        MemberSnapshot snapshot = departure.snapshot();

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        try {
            float centerX = HEAD_FRAME_SIZE * 0.5F;
            float centerY = rowY + ROW_HEIGHT * 0.5F;
            matrices.translate(offsetX + centerX, centerY, 0.0F);
            matrices.scale(rowScale, rowScale, 1.0F);
            matrices.translate(-centerX, -centerY, 0.0F);

            drawHead(context, snapshot.skin(), 8, rowY + 2, alpha);
            Render2DUtil.drawRoundedStroke(
                    matrices,
                    8.0F, rowY + 2.0F, HEAD_FRAME_SIZE, HEAD_FRAME_SIZE, 10.0F,
                    new Color(withAlpha(statusColor, alpha), true), 16
            );
            int contentX = 58;
            queueDepartureName(
                    names, snapshot.name() + status, contentX, rowY + 13.0F,
                    centerX, centerY, offsetX, rowScale,
                    withAlpha(statusColor, alpha),
                    renderX, renderY, renderScale
            );
            drawHealthBar(context, snapshot, contentX, rowY + 27, width - contentX - 10, alpha);
        } finally {
            matrices.pop();
        }
    }

    private void drawHead(DrawContext context, SkinTextures skin, int x, int y, float alpha) {
        float headX = x + 2.0F;
        float headY = y + 2.0F;
        boolean stencilAlreadyEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.clearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
        try {
            Render2DUtil.drawRoundedRect(
                    context.getMatrices(), headX, headY, HEAD_SIZE, HEAD_SIZE, HEAD_CORNER_RADIUS, Color.WHITE
            );
        } finally {
            RenderSystem.colorMask(true, true, true, true);
        }

        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        try {
            context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            PlayerSkinDrawer.draw(context, skin, Math.round(headX), Math.round(headY), HEAD_SIZE);
            context.draw();
        } finally {
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            if (!stencilAlreadyEnabled) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        }
    }

    private void drawTotemBurst(DrawContext context, int x, int y, float progress) {
        float eased = easeOutCubic(progress);
        float distance = 7.0F + 16.0F * eased;
        float sparkSize = 4.5F - 1.5F * eased;
        float rotation = progress * (float) Math.PI * 1.5F;
        int alpha = MathHelper.clamp(Math.round(255.0F * (1.0F - progress)), 0, 255);

        for (int index = 0; index < 4; index++) {
            float angle = rotation + index * (float) Math.PI * 0.5F;
            float sparkX = x + HEAD_FRAME_SIZE * 0.5F + (float) Math.cos(angle) * distance - sparkSize * 0.5F;
            float sparkY = y + HEAD_FRAME_SIZE * 0.5F + (float) Math.sin(angle) * distance - sparkSize * 0.5F;
            int color = index % 2 == 0 ? 0xFF63DE9C : 0xFFFFD265;
            Render2DUtil.drawRoundedRect(
                    context.getMatrices(), sparkX, sparkY, sparkSize, sparkSize, sparkSize * 0.5F,
                    new Color(withAlpha(color, alpha / 255.0F), true)
            );
        }

        float pulse = 1.0F - Math.abs(progress * 2.0F - 1.0F);
        int coreAlpha = MathHelper.clamp(Math.round(185.0F * pulse), 0, 255);
        Render2DUtil.drawRoundedRect(
                context.getMatrices(),
                x + HEAD_FRAME_SIZE * 0.5F - 3.0F,
                y + HEAD_FRAME_SIZE * 0.5F - 3.0F,
                6.0F, 6.0F, 3.0F,
                new Color(0x00F6E997 | coreAlpha << 24, true)
        );
    }

    private void drawHealthBar(DrawContext context, MemberSnapshot snapshot, int x, int y, int width, float alpha) {
        float ratio = MathHelper.clamp(snapshot.health() / Math.max(1.0F, snapshot.maxHealth()), 0.0F, 1.0F);

        Render2DUtil.drawRoundedRect(
                context.getMatrices(), x, y, width, HEALTH_BAR_HEIGHT, HEALTH_BAR_RADIUS,
                new Color(withAlpha(0xEBDCE0E5, alpha), true)
        );
        if (ratio > 0.0F) {
            float fillWidth = Math.max(HEALTH_BAR_HEIGHT, width * ratio);
            Render2DUtil.drawRoundedRect(
                    context.getMatrices(), x, y, fillWidth, HEALTH_BAR_HEIGHT, HEALTH_BAR_RADIUS,
                    new Color(withAlpha(0xF54C9D77, alpha), true)
            );
        }
    }

    private int getNameWidth(String name) {
        return Math.round(mc.textRenderer.getWidth(name) * 1.5F);
    }

    private void queueName(
            List<NameLabel> names, String name, float localX, float localY, float rowScale,
            int color, float renderX, float renderY, float renderScale
    ) {
        names.add(new NameLabel(
                name, renderX + localX * renderScale, renderY + localY * renderScale,
                NAME_FONT_SIZE * rowScale * renderScale, color
        ));
    }

    private void queueDepartureName(
            List<NameLabel> names, String name, float localX, float localY,
            float centerX, float centerY, float offsetX, float rowScale,
            int color, float renderX, float renderY, float renderScale
    ) {
        float transformedX = offsetX + centerX + (localX - centerX) * rowScale;
        float transformedY = centerY + (localY - centerY) * rowScale;
        queueName(names, name, transformedX, transformedY, rowScale, color, renderX, renderY, renderScale);
    }

    private void renderNames(List<NameLabel> names) {
        if (names.isEmpty()) {
            return;
        }

        boolean ownsFrame = NvgRenderer.beginFrame();
        try {
            NvgFont font = FontRepository.get("productsans-medium");
            for (NameLabel name : names) {
                font.draw(name.text(), name.x(), name.y(), name.size(), name.color());
            }
        } finally {
            if (ownsFrame) {
                NvgRenderer.endFrame();
            }
        }
    }

    private float getPopProgress(UUID uuid, long now) {
        Long started = popStartedAt.get(uuid);
        if (started == null) {
            return -1.0F;
        }

        long elapsed = now - started;
        if (elapsed < 0L || elapsed >= TOTEM_BURST_MILLIS) {
            popStartedAt.remove(uuid);
            return -1.0F;
        }
        return (float) elapsed / TOTEM_BURST_MILLIS;
    }

    private int[] getShakeOffset(UUID uuid, long now) {
        Long started = shakeStartedAt.get(uuid);
        if (started == null) {
            return new int[]{0, 0};
        }

        long elapsed = now - started;
        int duration = shakeTime.getValueInt();
        if (elapsed < 0L || elapsed >= duration) {
            shakeStartedAt.remove(uuid);
            return new int[]{0, 0};
        }

        double progress = (double) elapsed / duration;
        double decay = (1.0D - progress) * (1.0D - progress);
        double amplitude = shakeStrength.getValue() * decay;
        double seed = (uuid.hashCode() & 255) * 0.03125D;
        int offsetX = (int) Math.round(Math.sin(elapsed * 0.23D + seed) * amplitude);
        int offsetY = (int) Math.round(Math.cos(elapsed * 0.31D + seed) * amplitude * 0.65D);
        return new int[]{offsetX, offsetY};
    }

    private void clearTracking() {
        playerStates.clear();
        shakeStartedAt.clear();
        popCounts.clear();
        popStartedAt.clear();
        visibleMembers.clear();
        departingMembers.clear();
    }

    private void startDeparture(UUID uuid, MemberSnapshot snapshot, DepartureReason reason, long now) {
        DepartureAnimation existing = departingMembers.get(uuid);
        if (existing == null || (reason == DepartureReason.DEATH && existing.reason() != DepartureReason.DEATH)) {
            departingMembers.put(uuid, new DepartureAnimation(snapshot, reason, now));
        }
    }

    private static boolean isDead(AbstractClientPlayerEntity player) {
        return player.isDead() || player.getHealth() <= 0.0F;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - MathHelper.clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, float alpha) {
        int adjustedAlpha = MathHelper.clamp(Math.round(((color >>> 24) & 0xFF) * MathHelper.clamp(alpha, 0.0F, 1.0F)), 0, 255);
        return adjustedAlpha << 24 | color & 0x00FFFFFF;
    }

    private record PlayerState(float health, int hurtTime) {
    }

    private record NameLabel(String text, float x, float y, float size, int color) {
    }

    private record MemberSnapshot(SkinTextures skin, String name, float health, float maxHealth) {
        private static MemberSnapshot of(AbstractClientPlayerEntity player) {
            float absorption = player.getAbsorptionAmount();
            return new MemberSnapshot(
                    player.getSkinTextures(),
                    player.getGameProfile().getName(),
                    player.getHealth() + absorption,
                    Math.max(1.0F, player.getMaxHealth() + absorption)
            );
        }
    }

    private record DepartureAnimation(MemberSnapshot snapshot, DepartureReason reason, long startedAt) {
        private float progress(long now) {
            return MathHelper.clamp((now - startedAt) / (float) duration(), 0.0F, 1.0F);
        }

        private boolean finished(long now) {
            return now - startedAt >= duration();
        }

        private int duration() {
            return reason == DepartureReason.DEATH ? DEATH_ANIMATION_MILLIS : DISAPPEAR_ANIMATION_MILLIS;
        }
    }

    private enum DepartureReason {
        DEATH,
        DISAPPEARED
    }

    public enum Source {
        Friends,
        Scoreboard,
        Both;

        private boolean includes(boolean friend, boolean scoreboardTeammate) {
            return switch (this) {
                case Friends -> friend;
                case Scoreboard -> scoreboardTeammate;
                case Both -> friend || scoreboardTeammate;
            };
        }
    }
}