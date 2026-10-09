package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.util.ClientEmergencyState;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SRequestEmergencyItemsPacket;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EmergencyClaimScreen extends Screen {

    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 32;
    private static final int MARGIN = 10;
    private static final int ICON_SZ = 16;
    private static final int ICON_GAP = 2;
    private static final int ACTION_W = 96;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final long ARM_WINDOW_MS = 3000;

    private static final int COL_READY = 0xFFE0A030;
    private static final int COL_COOLDOWN = 0xFF666677;
    private static final int COL_USED = 0xFF444444;

    private record Row(ResourceLocation id, String title, List<QuestReward> rewards,
                       ClientEmergencyState.Availability availability) {}

    private final Screen parent;
    private List<Row> rows = List.of();
    private int scrollY = 0;
    private int refreshTicks = 0;
    private @Nullable ResourceLocation armedQuest = null;
    private long armedUntilMs = 0;

    public EmergencyClaimScreen(Screen parent) {
        super(Component.translatable("phoenix_chronicles.screen.emergency.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshRows();
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.back"), b -> onClose())
                .bounds(width - MARGIN - 80, height - FOOTER_H + 6, 80, 18).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            refreshRows();
        }
    }

    private void refreshRows() {
        List<Row> list = new ArrayList<>();
        PlayerQuestData data = minecraft != null && minecraft.player != null ?
                minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null) : null;
        if (data != null) {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.isFlagDisabled(null)) continue;
                if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.ACTIVE) continue;
                if (!ClientEmergencyState.hasEmergencyItems(node.getId())) continue;
                list.add(new Row(node.getId(), node.getTitle().getString(),
                        node.getEffectiveEmergencyKit().getRewards(),
                        ClientEmergencyState.availability(data, node.getId())));
            }

            for (ResourceLocation stationId : ClientEmergencyState.stationIds()) {
                var kit = ClientEmergencyState.stationKit(stationId);
                if (kit == null) continue;
                String label = ClientEmergencyState.stationLabel(stationId);
                list.add(new Row(stationId, "§6" + (label != null ? label : stationId.getPath()), kit.getRewards(),
                        ClientEmergencyState.availability(data, stationId)));
            }
        }
        list.sort(Comparator.comparingInt((Row r) -> r.availability().state().ordinal())
                .thenComparingLong(r -> r.availability().remainingMs()));
        rows = list;
    }

    private int listTop() {
        return HEADER_H + 18;
    }

    private int listBottom() {
        return height - FOOTER_H - 1;
    }

    private int actionX() {
        return width - MARGIN - ACTION_W - 6;
    }

    private String modeLine() {
        return "§8Quest items need that quest active; chapter and questbook items are always here.";
    }

    private static String frequencyTag(ResourceLocation id) {
        if (!ClientEmergencyState.isRepeatable(id)) return "one use";
        int seconds = ClientEmergencyState.getCooldownSeconds(id);
        return seconds > 0 ? "repeatable - " + QuestProgressTracker.formatDuration(seconds * 1000L) : "repeatable";
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        PhoenixTheme theme = PhoenixTheme.current();
        int bg = theme.bg.getColor(), panel = theme.panel.getColor(), header = theme.header.getColor();
        int border = theme.border.getColor(), text = theme.text.getColor();
        int textDim = theme.textDim.getColor(), textFaint = theme.textFaint.getColor();

        g.fill(0, 0, width, height, bg);
        g.fill(0, 0, width, HEADER_H, header);
        g.fill(0, HEADER_H - 1, width, HEADER_H, border);
        long ready = rows.stream().filter(r -> r.availability().state() == ClientEmergencyState.State.READY).count();
        g.drawString(font, ChroniclesUIKit.fitText(font, "§6⚠ §fEmergency Items  §8: §7" + ready + " ready",
                width - MARGIN * 2), MARGIN, 10, text, false);
        g.drawString(font, ChroniclesUIKit.fitText(font, modeLine(), width - MARGIN * 2), MARGIN, HEADER_H + 4,
                textFaint, false);

        g.fill(0, height - FOOTER_H, width, height, header);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, border);

        List<Component> hoveredTip = List.of();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, "§8No emergency items are available right now.", width / 2,
                    (listTop() + listBottom()) / 2, textFaint);
        } else {
            g.enableScissor(0, listTop(), width, listBottom());
            int rowW = width - MARGIN * 2;
            int ty = listTop() - scrollY;
            for (Row row : rows) {
                if (ty + ROW_H > listTop() && ty < listBottom()) {
                    List<Component> hit = renderRow(g, row, ty, rowW, mx, my, panel, text, textDim);
                    if (!hit.isEmpty()) hoveredTip = hit;
                }
                ty += ROW_H;
            }
            g.disableScissor();
        }

        super.render(g, mx, my, partial);
        if (!hoveredTip.isEmpty()) g.renderComponentTooltip(font, hoveredTip, mx, my);
    }

    private List<Component> renderRow(GuiGraphics g, Row row, int ty, int rowW, int mx, int my, int panel, int text,
                                      int textDim) {
        ClientEmergencyState.Availability av = row.availability();
        boolean hov = mx >= MARGIN && mx < MARGIN + rowW && my >= ty && my < ty + ROW_H;
        g.fill(MARGIN, ty + 1, MARGIN + rowW, ty + ROW_H - 1, hov ? blend(panel, 0x22FFFFFF) : panel);
        int accent = switch (av.state()) {
            case READY -> COL_READY;
            case COOLDOWN -> COL_COOLDOWN;
            case USED -> COL_USED;
        };
        g.fill(MARGIN, ty + 1, MARGIN + 2, ty + ROW_H - 1, accent);

        int actionX = actionX();
        int textW = actionX - (MARGIN + 6) - 8;
        String tag = frequencyTag(row.id());
        int tagW = font.width(tag) + 8;
        boolean tagFits = textW > tagW + 60;
        g.drawString(font, "§f" + ChroniclesUIKit.fitText(font, row.title(),
                tagFits ? textW - tagW : textW), MARGIN + 6, ty + 5, text, false);
        if (tagFits) g.drawString(font, "§8" + tag, MARGIN + 6 + textW - font.width(tag), ty + 5, textDim, false);

        List<Component> hoveredTip = List.of();
        int ix = MARGIN + 6;
        int iy = ty + 14;
        int maxIconX = actionX - 8 - ICON_SZ;
        if (row.rewards().isEmpty()) {
            g.drawString(font, "§8Fallback rewards", ix, iy + 4, textDim, false);
        }
        for (QuestReward reward : row.rewards()) {
            if (ix > maxIconX) {
                g.drawString(font, "§8…", ix, iy + 4, textDim, false);
                break;
            }
            boolean hovered = mx >= ix && mx < ix + ICON_SZ && my >= iy && my < iy + ICON_SZ;
            if (reward instanceof QuestReward.ItemReward itemReward) {
                ItemStack stack = new ItemStack(itemReward.getItem(), itemReward.getCount());
                g.renderItem(stack, ix, iy);
                g.renderItemDecorations(font, stack, ix, iy);
                if (hovered) hoveredTip = Screen.getTooltipFromItem(minecraft, stack);
            } else {
                g.fill(ix, iy, ix + ICON_SZ, iy + ICON_SZ, 0xFF0F0F18);
                g.drawCenteredString(font, glyphFor(reward), ix + ICON_SZ / 2, iy + 4, 0xFFAAAABB);
                if (hovered) hoveredTip = List.of(reward.getSummary());
            }
            ix += ICON_SZ + ICON_GAP;
        }

        renderAction(g, row, actionX, ty, mx, my);
        return hoveredTip;
    }

    private static String glyphFor(QuestReward reward) {
        return switch (reward.getType()) {
            case XP -> "§a✦";
            case COMMAND -> "§b◆";
            case LOOT_TABLE, LOOT_CRATE -> "§d❋";
            case FLUID -> "§3💧";
            case SCRIPT_EVENT -> "§e⚡";
            case EXTERNAL -> "§5⌘";
            default -> "§7★";
        };
    }

    private void renderAction(GuiGraphics g, Row row, int x, int ty, int mx, int my) {
        ClientEmergencyState.Availability av = row.availability();
        int y = ty + 6;
        int h = ROW_H - 12;
        switch (av.state()) {
            case READY -> {
                boolean armed = isArmed(row.id());
                boolean hov = mx >= x && mx < x + ACTION_W && my >= y && my < y + h;
                g.fill(x, y, x + ACTION_W, y + h, armed ? 0xFF6B4C1F : hov ? 0xFF4A3A18 : 0xFF2A2214);
                g.fill(x, y, x + ACTION_W, y + 1, COL_READY);
                g.drawCenteredString(font, armed ? "§eClick to confirm" : "§6Claim", x + ACTION_W / 2,
                        y + (h - 8) / 2, 0xFFFFD27A);
            }
            case COOLDOWN -> {
                g.fill(x, y, x + ACTION_W, y + h, 0xFF16161C);
                float progress = av.cooldownProgress();
                g.fill(x, y + h - 3, x + ACTION_W, y + h, 0xFF22222A);
                g.fill(x, y + h - 3, x + Math.round(ACTION_W * progress), y + h, COL_READY);
                g.drawCenteredString(font, "§7⏱ " + QuestProgressTracker.formatDuration(av.remainingMs()),
                        x + ACTION_W / 2, y + (h - 3 - 8) / 2 + 1, 0xFFAAAABB);
            }
            case USED -> {
                g.fill(x, y, x + ACTION_W, y + h, 0xFF16161C);
                g.drawCenteredString(font, "§8Used", x + ACTION_W / 2, y + (h - 8) / 2, 0xFF666677);
            }
        }
    }

    private boolean isArmed(ResourceLocation questId) {
        return questId.equals(armedQuest) && System.currentTimeMillis() < armedUntilMs;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int x = actionX();
            int ty = listTop() - scrollY;
            for (Row row : rows) {
                int y = ty + 6, h = ROW_H - 12;
                boolean inList = my >= listTop() && my < listBottom();
                if (inList && mx >= x && mx < x + ACTION_W && my >= y && my < y + h) {
                    if (row.availability().state() == ClientEmergencyState.State.READY) claim(row);
                    return true;
                }
                ty += ROW_H;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void claim(Row row) {
        ResourceLocation id = row.id();
        if (!ClientEmergencyState.isRepeatable(id) && !isArmed(id)) {
            armedQuest = id;
            armedUntilMs = System.currentTimeMillis() + ARM_WINDOW_MS;
            return;
        }
        armedQuest = null;
        ChronicleNetwork.CHANNEL.sendToServer(new C2SRequestEmergencyItemsPacket(id));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, rows.size() * ROW_H - (listBottom() - listTop()));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * ROW_H)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int blend(int base, int overlay) {
        int oa = (overlay >>> 24) & 0xFF;
        if (oa == 0) return base;
        float a = oa / 255f;
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        return 0xFF000000 | (Math.round(br + (or - br) * a) << 16) | (Math.round(bg + (og - bg) * a) << 8) |
                Math.round(bb + (ob - bb) * a);
    }
}
