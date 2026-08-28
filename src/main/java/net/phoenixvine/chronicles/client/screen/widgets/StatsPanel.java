package net.phoenixvine.chronicles.client.screen.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StatsPanel implements TogglePanel {

    private final ScreenContext ctx;
    private boolean open = false;

    public StatsPanel(ScreenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isVisible(ScreenContext ctx) {
        return ctx.isDevMode() && isOpen();
    }

    @Override
    public void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft, int contentRight) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        RenderSystem.disableDepthTest();

        int cl = contentLeft, cr = contentRight;
        int panW = Math.min(480, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = ChronicleOverviewScreen.HEADER_H + 10;
        int panH = ctx.height() - panY - 10;

        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B14);
        int bc = 0xFF4488CC;
        g.fill(panX, panY, panX + panW, panY + 1, bc);
        g.fill(panX, panY, panX + 1, panY + panH, bc);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, bc);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, bc);
        g.drawString(ctx.font(), "§bQuest Stats", panX + 6, panY + 4, 0xFF55AAEE, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF222233);

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

        int sy = panY + 18, lh = 10;
        int col1 = panX + 6, col2 = panX + panW / 2 + 10;

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
        sy += 5;

        g.drawString(ctx.font(), "§8Chapter", col1, sy, 0xFF666677, false);
        g.drawString(ctx.font(), "§8Quests", col2, sy, 0xFF666677, false);
        sy += lh;
        g.enableScissor(panX + 2, sy, panX + panW - 2, panY + panH - 4);
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(catCounts.entrySet());
        sorted.sort((a, b2) -> Integer.compare(b2.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, int[]> e : sorted) {
            if (sy + 9 > panY + panH - 4) break;
            int cnt = e.getValue()[0];

            int barMaxW = panW / 2 - 20;
            int barW = total > 0 ? (int) ((float) cnt / total * barMaxW) : 0;
            g.fill(col2 - 2, sy, col2 - 2 + barW, sy + 8, 0x334488CC);
            g.drawString(ctx.font(), "§7" + ctx.friendly(e.getKey()), col1, sy, 0xFFAAAAAA, false);
            g.drawString(ctx.font(), "§f" + cnt, col2, sy, 0xFFCCCCFF, false);
            sy += lh;
        }
        g.disableScissor();
        g.disableScissor();
        g.flush();
        RenderSystem.enableDepthTest();
        g.pose().popPose();
    }
}
