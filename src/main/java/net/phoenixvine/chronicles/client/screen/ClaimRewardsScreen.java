package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SClaimAllRewardsPacket;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;

public class ClaimRewardsScreen extends Screen {

    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 26;
    private static final int MARGIN = 10;
    private static final int ICON_SZ = 16;

    private record Row(QuestNode node, boolean pendingBoxOnly) {}

    private final Screen parent;
    private static final int REFRESH_INTERVAL_TICKS = 5;

    private int scrollY = 0;
    private int refreshTicks = 0;
    private List<Row> unclaimed = List.of();

    public ClaimRewardsScreen(Screen parent) {
        super(Component.translatable("phoenix_chronicles.screen.claim_rewards.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshList();

        addRenderableWidget(
                Button.builder(Component.translatable("phoenix_chronicles.screen.claim_rewards.claim_all"), b -> {
                    ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimAllRewardsPacket());
                }).bounds(MARGIN, height - FOOTER_H + 6, 110, 18).build());

        addRenderableWidget(Button.builder(Component.literal("§e⚡ Auto-claim…"), b -> {
            if (minecraft != null) minecraft.setScreen(new AutoClaimSettingsScreen(this));
        }).bounds(MARGIN + 116, height - FOOTER_H + 6, 100, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.back"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width - MARGIN - 80, height - FOOTER_H + 6, 80, 18).build());
    }

    @Override
    public void tick() {
        super.tick();
        // The claimed state arrives from the server a moment after a claim is sent, so re-reading the list here
        // (rather than reopening the screen right away, which showed the stale pre-claim list) picks it up.
        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            refreshList();
            int maxScroll = Math.max(0, unclaimed.size() * ROW_H - (listBottom() - listTop()));
            scrollY = Math.min(scrollY, maxScroll);
        }
    }

    private void refreshList() {
        List<Row> list = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) {
            unclaimed = list;
            return;
        }
        PlayerQuestData data = minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        if (data == null) {
            unclaimed = list;
            return;
        }
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled(null)) continue;
            if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (node.isRewardChoice()) continue;
            if (node.getRewards().isEmpty()) continue;

            boolean pendingBox = hasPendingMenuBox(node, data);
            if (data.hasClaimedRewards(node.getId()) && !pendingBox) continue;
            list.add(new Row(node, data.hasClaimedRewards(node.getId())));
        }
        unclaimed = list;
    }

    /** The quest's rewards minus the ones auto-claim has already granted. */
    private List<QuestReward> remainingRewards(QuestNode node) {
        List<QuestReward> all = node.getRewards();
        PlayerQuestData data = minecraft != null && minecraft.player != null ?
                minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null) : null;
        if (data == null) return all;
        List<QuestReward> remaining = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (!data.isRewardIndexClaimed(node.getId(), i)) remaining.add(all.get(i));
        }
        return remaining;
    }

    private boolean hasPendingMenuBox(QuestNode node, PlayerQuestData data) {
        List<QuestReward> rewards = node.getRewards();
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i) instanceof QuestReward.ChoiceBoxReward box &&
                    box.getMode() == QuestReward.ChoiceBoxReward.Mode.MENU &&
                    !data.isChoiceBoxResolved(node.getId(), i)) {
                return true;
            }
        }
        return false;
    }

    private int listTop() {
        return HEADER_H + 1;
    }

    private int listBottom() {
        return height - FOOTER_H - 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int bg = PhoenixTheme.current().bg.getColor();
        int panel = PhoenixTheme.current().panel.getColor();
        int header = PhoenixTheme.current().header.getColor();
        int border = PhoenixTheme.current().border.getColor();
        int text = PhoenixTheme.current().text.getColor();
        int textDim = PhoenixTheme.current().textDim.getColor();
        int textFaint = PhoenixTheme.current().textFaint.getColor();

        g.fill(0, 0, width, height, bg);
        g.fill(0, 0, width, HEADER_H, header);
        g.fill(0, HEADER_H - 1, width, HEADER_H, border);
        String headerText = "§f🎁 Unclaimed Rewards  §8: §7" + unclaimed.size() + " quest(s)";
        g.drawString(font, ChroniclesUIKit.fitText(font, headerText, width - MARGIN * 2), MARGIN, 10, text, false);

        g.fill(0, height - FOOTER_H, width, height, header);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, border);

        if (unclaimed.isEmpty()) {
            g.drawCenteredString(font, "§8Nothing to claim right now.", width / 2, (listTop() + listBottom()) / 2,
                    textFaint);
        } else {
            g.enableScissor(0, listTop(), width, listBottom());
            int rowW = width - MARGIN * 2;
            int ty = listTop() - scrollY;
            for (Row row : unclaimed) {
                QuestNode node = row.node();
                if (ty + ROW_H > listTop() && ty < listBottom()) {
                    boolean hov = mx >= MARGIN && mx < MARGIN + rowW && my >= ty && my < ty + ROW_H;
                    g.fill(MARGIN, ty, MARGIN + rowW, ty + ROW_H, hov ? blend(panel, 0x22FFFFFF) : panel);
                    g.fill(MARGIN, ty, MARGIN + 2, ty + ROW_H, row.pendingBoxOnly() ? 0xFFCC88FF : 0xFF44CC88);

                    String title = node.getTitle().getString();
                    List<QuestReward> remaining = remainingRewards(node);
                    int maxTitleW = rowW - 3 - 60 -
                            (unclaimed.size() > 0 ? remaining.size() * (ICON_SZ + 2) : 0) - 70;
                    if (font.width(title) > Math.max(20, maxTitleW))
                        title = font.plainSubstrByWidth(title, Math.max(20, maxTitleW) - 6) + "…";
                    g.drawString(font, "§f" + title, MARGIN + 6, ty + (ROW_H - 8) / 2, text, false);

                    int ix = MARGIN + rowW - 66 - remaining.size() * (ICON_SZ + 2);
                    for (QuestReward reward : remaining) {
                        drawRewardIcon(g, reward, ix, ty + (ROW_H - ICON_SZ) / 2);
                        ix += ICON_SZ + 2;
                    }

                    int btnX = MARGIN + rowW - 58;
                    boolean btnHov = mx >= btnX && mx < btnX + 52 && my >= ty + 4 && my < ty + ROW_H - 4;
                    if (row.pendingBoxOnly()) {
                        g.fill(btnX, ty + 4, btnX + 52, ty + ROW_H - 4, btnHov ? 0xFF6B4C8F : 0xFF473262);
                        g.drawCenteredString(font, "§dOpen", btnX + 26, ty + (ROW_H - 8) / 2, 0xFFDDAAFF);
                    } else {
                        g.fill(btnX, ty + 4, btnX + 52, ty + ROW_H - 4, btnHov ? 0xFF2C6644 : 0xFF1C4430);
                        g.drawCenteredString(font, "§aClaim", btnX + 26, ty + (ROW_H - 8) / 2, 0xFF88FFAA);
                    }
                }
                ty += ROW_H;
            }
            g.disableScissor();
        }

        super.render(g, mx, my, partial);
    }

    private static int blend(int base, int overlay) {
        int oa = (overlay >>> 24) & 0xFF;
        if (oa == 0) return base;
        float a = oa / 255f;
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int rr = Math.round(br + (or - br) * a);
        int rg = Math.round(bg + (og - bg) * a);
        int rb = Math.round(bb + (ob - bb) * a);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private void drawRewardIcon(GuiGraphics g, QuestReward reward, int x, int y) {
        g.fill(x, y, x + ICON_SZ, y + ICON_SZ, 0xFF0F0F18);
        if (reward instanceof QuestReward.ItemReward ir) {
            ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
            if (ir.getNbt() != null && !ir.getNbt().isEmpty()) stack.setTag(ir.getNbt().copy());
            g.renderItem(stack, x, y);
        } else if (reward instanceof QuestReward.ChoiceBoxReward box) {
            String glyph = box.getMode() == QuestReward.ChoiceBoxReward.Mode.LOOTBOX ? "🎲" : "🎁";
            g.drawString(font, "§7" + glyph, x + 4, y + 4, 0xFF888898, false);
        } else {
            String glyph = switch (reward.getType()) {
                case XP -> "⚡";
                case COMMAND -> "◆";
                case LOOT_TABLE, LOOT_CRATE -> "📦";
                case SCRIPT_EVENT -> "✦";
                default -> "★";
            };
            g.drawString(font, "§7" + glyph, x + 4, y + 4, 0xFF888898, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && !unclaimed.isEmpty()) {
            int rowW = width - MARGIN * 2;
            int ty = listTop() - scrollY;
            for (Row row : unclaimed) {
                QuestNode node = row.node();
                if (my >= ty && my < ty + ROW_H && ty >= listTop() - ROW_H && ty < listBottom()) {
                    int btnX = MARGIN + rowW - 58;
                    if (mx >= btnX && mx < btnX + 52 && my >= ty + 4 && my < ty + ROW_H - 4) {
                        if (row.pendingBoxOnly()) {
                            if (minecraft != null) {
                                minecraft.setScreen(parent);
                                if (parent instanceof ChronicleOverviewScreen overview) {
                                    overview.navigateToNode(node);
                                }
                            }
                        } else {
                            ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), -1));
                        }
                        return true;
                    }
                }
                ty += ROW_H;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, unclaimed.size() * ROW_H - (listBottom() - listTop()));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * ROW_H)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
