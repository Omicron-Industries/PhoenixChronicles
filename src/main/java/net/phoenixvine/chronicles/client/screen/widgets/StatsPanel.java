package net.phoenixvine.chronicles.client.screen.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.client.util.QuestCompletionStats;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.model.QuestGroupManager;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class StatsPanel implements TogglePanel {

    private static final long REFRESH_MS = 1000;
    private static final int LINE_H = 10;
    private static final int ROW_H = 11;
    private static final int BAR_H = 6;
    private static final int RIGHT_W = 84;

    private final ScreenContext ctx;
    private boolean open = false;

    private QuestCompletionStats stats = null;
    private long statsAtMs = 0;

    private int scrollRows = 0;
    private int panX, panY, panW, panH;
    private int listTop, listBottom;

    public StatsPanel(ScreenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
        stats = null;
        scrollRows = 0;
    }

    @Override
    public void close() {
        open = false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!open || mx < panX || mx >= panX + panW || my < panY || my >= panY + panH) return false;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int total = stats != null ? stats.chapters.size() : 0;
        int maxScroll = Math.max(0, total - visibleRows);
        scrollRows = Math.max(0, Math.min(maxScroll, scrollRows - (int) Math.signum(delta)));
        return true;
    }

    private void drawBar(GuiGraphics g, int x, int y, int w, float fraction, int fill) {
        g.fill(x, y, x + w, y + BAR_H, 0xFF1A1A28);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
        if (fw > 0) g.fill(x, y, x + fw, y + BAR_H, fill);
    }

    private void drawProgressRow(GuiGraphics g, int x, int y, int labelW, int right, String label,
                                 QuestCompletionStats.Tally t, int fill) {
        var font = ctx.font();
        String l = font.width(label) > labelW - 4 ? font.plainSubstrByWidth(label, labelW - 8) + "…" : label;
        g.drawString(font, l, x, y, 0xFFCCCCDD, false);

        int barX = x + labelW;
        int barW = Math.max(10, right - RIGHT_W - barX);
        drawBar(g, barX, y + 1, barW, t.fraction(), fill);

        String pct = t.total <= 0 ? "-" : t.percent() + "%";
        String right1 = t.done + "/" + t.total + "  " + (t.total > 0 && t.done == t.total ? "§a" : "§f") + pct;
        g.drawString(font, right1, right - font.width(right1.replaceAll("§.", "")), y, 0xFFCCCCFF, false);
    }

    @Override
    public void render(@NotNull ScreenContext ctx, @NotNull GuiGraphics g, int mouseX, int mouseY, int contentLeft,
                       int contentRight) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        RenderSystem.disableDepthTest();

        int cl = contentLeft, cr = contentRight;
        panW = Math.min(480, cr - cl - 20);
        panX = cl + (cr - cl - panW) / 2;
        panY = ChronicleOverviewScreen.HEADER_H + 10;
        panH = ctx.height() - panY - 10;

        long now = System.currentTimeMillis();
        if (stats == null || now - statsAtMs > REFRESH_MS) {
            stats = QuestCompletionStats.compute(ctx);
            statsAtMs = now;
        }

        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B14);
        int bc = 0xFF4488CC;
        g.fill(panX, panY, panX + panW, panY + 1, bc);
        g.fill(panX, panY, panX + 1, panY + panH, bc);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, bc);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, bc);
        g.drawString(ctx.font(), "§bQuest Progress", panX + 6, panY + 4, 0xFF55AAEE, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF222233);

        int x = panX + 6, right = panX + panW - 6;
        int sy = panY + 19;
        int fill = ctx.colorNodeBorderDone();

        QuestCompletionStats.Tally overall = stats.overall();
        if (overall.total <= 0) {
            g.drawString(ctx.font(), "§8Nothing to track yet.", x, sy, 0xFF666677, false);
            sy += ROW_H + 2;
        } else {
            drawProgressRow(g, x, sy, 62, right, "§fOverall", overall, fill);
            sy += ROW_H + 1;
            if (stats.required.total > 0) {
                drawProgressRow(g, x, sy, 62, right, "§7Required", stats.required, fill);
                sy += ROW_H;
            }
            if (stats.optional.total > 0) {
                drawProgressRow(g, x, sy, 62, right, "§7Optional", stats.optional, fill);
                sy += ROW_H;
            }
        }
        if (stats.disabled > 0 || stats.repeatable > 0) {
            StringBuilder sb = new StringBuilder("§8Not counted:");
            if (stats.disabled > 0) sb.append(" ").append(stats.disabled).append(" disabled");
            if (stats.disabled > 0 && stats.repeatable > 0) sb.append(",");
            if (stats.repeatable > 0) sb.append(" ").append(stats.repeatable).append(" repeatable");
            g.drawString(ctx.font(), sb.toString(), x, sy + 1, 0xFF666677, false);
            sy += ROW_H;
        }
        sy += 2;
        g.fill(panX + 4, sy, panX + panW - 4, sy + 1, 0xFF222233);
        sy += 4;

        if (ctx.isDevMode()) sy = renderAuthoringStats(ctx, g, sy);

        g.drawString(ctx.font(), "§8Chapters", x, sy, 0xFF666677, false);
        sy += LINE_H;

        listTop = sy;
        listBottom = panY + panH - 4;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, stats.chapters.size() - visibleRows);
        scrollRows = Math.max(0, Math.min(scrollRows, maxScroll));

        g.enableScissor(panX + 2, listTop, panX + panW - 2, listBottom);
        int row = 0;
        for (Map.Entry<String, QuestCompletionStats.Tally> e : stats.chapters.entrySet()) {
            if (row++ < scrollRows) continue;
            if (sy + 9 > listBottom) break;
            QuestCompletionStats.Tally t = e.getValue();
            drawProgressRow(g, x, sy, 118, right, "§7" + ctx.friendly(e.getKey()), t,
                    t.total > 0 && t.done == t.total ? 0xFF55CC66 : fill);
            sy += ROW_H;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            String hint = "§8scroll · " + (scrollRows + 1) + "-" +
                    Math.min(stats.chapters.size(), scrollRows + visibleRows) + " of " + stats.chapters.size();
            g.drawString(ctx.font(), hint, right - ctx.font().width(hint.replaceAll("§.", "")), panY + 4, 0xFF666677,
                    false);
        }

        g.disableScissor();
        g.flush();
        RenderSystem.enableDepthTest();
        g.pose().popPose();
    }

    private int renderAuthoringStats(ScreenContext ctx, GuiGraphics g, int sy) {
        Collection<QuestNode> all = QuestTreeRegistry.getAllQuests().values();
        int total = all.size();
        int noTask = 0, noReward = 0, orphaned = 0;
        int totalTasks = 0, totalRewards = 0;
        int repeatable = 0, hiddenOrMystery = 0, disabled = 0, withCustomIcon = 0, linkStubs = 0;
        int validationIssueCount = 0;

        TreeMap<String, int[]> catCounts = new TreeMap<>();
        for (QuestNode n : all) {
            if (n.getTasks().isEmpty()) noTask++;
            if (n.getRewards().isEmpty()) noReward++;
            if (n.getPrerequisites().isEmpty() && n.getChildren().isEmpty()) orphaned++;
            totalTasks += n.getTasks().size();
            totalRewards += n.getRewards().size();
            if (n.getRepeatMode() != QuestNode.RepeatMode.NONE) repeatable++;
            if (n.getVisibility() == QuestNode.Visibility.HIDDEN || n.getVisibility() == QuestNode.Visibility.MYSTERY)
                hiddenOrMystery++;
            if (n.getVisibility() == QuestNode.Visibility.DISABLED) disabled++;
            if (n.isLinkStub()) linkStubs++;
            if (n.getIconItem() != null && n.getIconItem() != net.minecraft.world.item.Items.AIR) withCustomIcon++;
            if (!ctx.validationIssues(n).isEmpty()) validationIssueCount++;
            String cat = n.getChapter() != null ? n.getChapter() : "UNKNOWN";
            catCounts.computeIfAbsent(cat, k -> new int[1])[0]++;
        }
        int totalGroups = QuestGroupManager.getAll().size();

        int lh = LINE_H;
        int col1 = panX + 6, col2 = panX + panW / 2 + 10;

        g.drawString(ctx.font(), "§8Authoring", col1, sy, 0xFF666677, false);
        sy += lh;
        g.drawString(ctx.font(), "§fTotal quests:  §e" + total, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fTotal tasks:   §7" + totalTasks, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fNo tasks:      §c" + noTask, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fTotal rewards: §7" + totalRewards, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fNo rewards:    §8" + noReward, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fCategories:    §7" + catCounts.size(), col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fOrphaned:      §e" + orphaned, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fGroups:        §7" + totalGroups, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fRepeatable:    §b" + repeatable, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fHidden/Mystery:§7" + hiddenOrMystery, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fDisabled:      §7" + disabled, col1, sy, 0xFFDDDDFF, false);
        g.drawString(ctx.font(), "§fLink stubs:    §7" + linkStubs, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(ctx.font(), "§fCustom icons:  §7" + withCustomIcon + "§8/" + total, col1, sy, 0xFFDDDDFF,
                false);
        g.drawString(ctx.font(),
                validationIssueCount > 0 ? "§fValidation:    §c" + validationIssueCount + " issue(s)" :
                        "§fValidation:    §a✔ clean",
                col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.fill(panX + 4, sy, panX + panW - 4, sy + 1, 0xFF222233);
        return sy + 5;
    }
}
