package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SClaimAllRewardsPacket;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;

public class ClaimRewardsScreen extends Screen {

    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 30;
    private static final int ROW_H = 26;
    private static final int MARGIN = 10;
    private static final int ICON_SZ = 16;

    private final Screen parent;
    private int scrollY = 0;
    private List<QuestNode> unclaimed = List.of();

    public ClaimRewardsScreen(Screen parent) {
        super(Component.literal("Unclaimed Rewards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshList();

        addRenderableWidget(Button.builder(Component.literal("§a✔ Claim All"), b -> {
            ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimAllRewardsPacket());
            if (minecraft != null) minecraft.setScreen(new ClaimRewardsScreen(parent));
        }).bounds(MARGIN, height - FOOTER_H + 6, 110, 18).build());

        addRenderableWidget(Button.builder(Component.literal("§7‹ Back"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width - MARGIN - 80, height - FOOTER_H + 6, 80, 18).build());
    }

    private void refreshList() {
        List<QuestNode> list = new ArrayList<>();
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
            if (data.hasClaimedRewards(node.getId())) continue;
            if (node.isRewardChoice()) continue;
            if (node.getRewards().isEmpty()) continue;
            list.add(node);
        }
        unclaimed = list;
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
        String headerText = "§f🎁 Unclaimed Rewards  §8— §7" + unclaimed.size() + " quest(s)";
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
            for (QuestNode node : unclaimed) {
                if (ty + ROW_H > listTop() && ty < listBottom()) {
                    boolean hov = mx >= MARGIN && mx < MARGIN + rowW && my >= ty && my < ty + ROW_H;
                    g.fill(MARGIN, ty, MARGIN + rowW, ty + ROW_H, hov ? blend(panel, 0x22FFFFFF) : panel);
                    g.fill(MARGIN, ty, MARGIN + 2, ty + ROW_H, 0xFF44CC88);

                    String title = node.getTitle().getString();
                    int maxTitleW = rowW - 3 - 60 -
                            (unclaimed.size() > 0 ? node.getRewards().size() * (ICON_SZ + 2) : 0) - 70;
                    if (font.width(title) > Math.max(20, maxTitleW))
                        title = font.plainSubstrByWidth(title, Math.max(20, maxTitleW) - 6) + "…";
                    g.drawString(font, "§f" + title, MARGIN + 6, ty + (ROW_H - 8) / 2, text, false);

                    int ix = MARGIN + rowW - 66 - node.getRewards().size() * (ICON_SZ + 2);
                    for (QuestReward reward : node.getRewards()) {
                        drawRewardIcon(g, reward, ix, ty + (ROW_H - ICON_SZ) / 2);
                        ix += ICON_SZ + 2;
                    }

                    int btnX = MARGIN + rowW - 58;
                    boolean btnHov = mx >= btnX && mx < btnX + 52 && my >= ty + 4 && my < ty + ROW_H - 4;
                    g.fill(btnX, ty + 4, btnX + 52, ty + ROW_H - 4, btnHov ? 0xFF2C6644 : 0xFF1C4430);
                    g.drawCenteredString(font, "§aClaim", btnX + 26, ty + (ROW_H - 8) / 2, 0xFF88FFAA);
                }
                ty += ROW_H;
            }
            g.disableScissor();
        }

        super.render(g, mx, my, partial);
    }

    private static int blend(int base, int overlay) {
        return base | overlay;
    }

    private void drawRewardIcon(GuiGraphics g, QuestReward reward, int x, int y) {
        g.fill(x, y, x + ICON_SZ, y + ICON_SZ, 0xFF0F0F18);
        if (reward instanceof QuestReward.ItemReward ir) {
            ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
            if (ir.getNbt() != null && !ir.getNbt().isEmpty()) stack.setTag(ir.getNbt().copy());
            g.renderItem(stack, x, y);
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
            for (QuestNode node : unclaimed) {
                if (my >= ty && my < ty + ROW_H && ty >= listTop() - ROW_H && ty < listBottom()) {
                    int btnX = MARGIN + rowW - 58;
                    if (mx >= btnX && mx < btnX + 52 && my >= ty + 4 && my < ty + ROW_H - 4) {
                        ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), -1));
                        if (minecraft != null) minecraft.setScreen(new ClaimRewardsScreen(parent));
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
