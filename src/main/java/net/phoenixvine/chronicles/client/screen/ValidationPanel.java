package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ItemRequirementTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ValidationPanel implements TogglePanel {

    private final ScreenContext ctx;
    private final Map<ResourceLocation, List<String>> cache = new HashMap<>();
    private boolean open = false;

    ValidationPanel(ScreenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
    }

    void toggle() {
        open = !open;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isVisible(ScreenContext ctx) {
        return ctx.isDevMode() && isOpen();
    }

    List<String> issuesFor(QuestNode node) {
        return cache.computeIfAbsent(node.getId(), id -> compute(node));
    }

    void invalidate(ResourceLocation id) {
        cache.remove(id);
    }

    void clear() {
        cache.clear();
    }

    Map<ResourceLocation, List<String>> snapshot() {
        return new HashMap<>(cache);
    }

    void restore(Map<ResourceLocation, List<String>> saved) {
        cache.putAll(saved);
    }

    private List<String> compute(QuestNode node) {
        List<String> issues = new ArrayList<>();

        if (node.isLinkStub()) return issues;

        if (node.getTasks().isEmpty()) issues.add("No tasks defined");

        if (node.getTitle().getString().isBlank()) issues.add("Missing title");

        if (!QuestFileSaver.doesQuestFileExist(node)) issues.add("No editable file on disk (datapack quest)");

        for (QuestTask task : node.getTasks()) {
            if (task instanceof ItemRequirementTask irt) {
                if (irt.getItem() == null || irt.getItem() == net.minecraft.world.item.Items.AIR) {
                    issues.add("Item task has missing/AIR item");
                }
            }
        }

        for (QuestNode prereq : node.getPrerequisites()) {
            if (QuestTreeRegistry.getQuest(prereq.getId()) == null) {
                issues.add("Broken prerequisite: " + prereq.getId().getPath());
            }
        }
        return issues;
    }

    @Override
    public void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft, int contentRight) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        int cl = contentLeft, cr = contentRight;
        int panW = Math.min(400, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = ChronicleOverviewScreen.HEADER_H + 10;
        int panH = ctx.height() - panY - 10;

        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B12);
        g.fill(panX, panY, panX + panW, panY + 1, 0xFFFF4444);
        g.fill(panX, panY, panX + 1, panY + panH, 0xFFFF4444);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, 0xFFFF4444);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, 0xFFFF4444);

        g.drawString(ctx.font(), "§cValidation Issues §8(V to close)", panX + 6, panY + 4, 0xFFFF6666, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF333344);

        int vy = panY + 18;
        int maxY = panY + panH - 4;
        boolean any = false;
        g.enableScissor(panX + 2, panY + 16, panX + panW - 2, maxY);
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            List<String> issues = issuesFor(node);
            if (issues.isEmpty()) continue;
            any = true;
            if (vy + 11 > maxY) break;
            g.drawString(ctx.font(), "§e" + node.getTitle().getString() + " §8[" + node.getId().getPath() + "]",
                    panX + 6, vy, 0xFFFFCC44, false);
            vy += 10;
            for (String issue : issues) {
                if (vy + 9 > maxY) break;
                g.drawString(ctx.font(), "§c  • " + issue, panX + 12, vy, 0xFFFF6666, false);
                vy += 9;
            }
        }
        g.disableScissor();
        if (!any) {
            g.drawString(ctx.font(), "§aNo issues found!", panX + 6, panY + 20, 0xFF44CC88, false);
        }
        g.disableScissor();
        g.pose().popPose();
    }
}
