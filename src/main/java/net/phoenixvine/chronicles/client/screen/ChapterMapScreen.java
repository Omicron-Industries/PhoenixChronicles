package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.render.line.SolidLineStyle;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ChapterMapScreen extends Screen {

    private record Edge(String from, String to, int count) {}

    private static final int BOX_W = 120, BOX_H = 32, GAP_X = 70, GAP_Y = 16;

    private final ChronicleOverviewScreen parent;

    private List<String> chapters = List.of();
    private List<Edge> edges = List.of();
    private Map<String, Integer> chapterLayer = Map.of();
    private Map<String, Integer> questCounts = Map.of();
    private final Map<String, int[]> boxes = new HashMap<>();

    private int offsetX = 0, offsetY = 0;
    private boolean dragging = false;
    private double dragLastX, dragLastY;

    private static final SolidLineStyle LINE_STYLE = new SolidLineStyle(1.5f);

    public ChapterMapScreen(ChronicleOverviewScreen parent) {
        super(Component.translatable("phoenix_chronicles.screen.chapter_map.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        computeGraph();
        layoutBoxes();

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.close_bracketed"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private void computeGraph() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
        Map<String, Integer> edgeCount = new LinkedHashMap<>();

        for (QuestNode q : QuestTreeRegistry.getAllQuests().values()) {
            String qc = q.getChapter();
            if (qc == null || qc.isEmpty()) continue;
            counts.merge(qc, 1, Integer::sum);
            for (QuestNode prereq : q.getPrerequisites()) {
                if (prereq == null) continue;
                String pc = prereq.getChapter();
                if (pc == null || pc.isEmpty() || pc.equalsIgnoreCase(qc)) continue;
                dependsOn.computeIfAbsent(qc, k -> new LinkedHashSet<>()).add(pc);
                edgeCount.merge(pc + "|" + qc, 1, Integer::sum);
            }
        }
        questCounts = counts;

        Map<String, Set<String>> unlocks = new LinkedHashMap<>();
        for (var e : dependsOn.entrySet()) {
            for (String dep : e.getValue()) {
                unlocks.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }

        Map<String, Integer> layer = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String c : counts.keySet()) {
            if (!dependsOn.containsKey(c) || dependsOn.get(c).isEmpty()) {
                layer.put(c, 0);
                queue.add(c);
            }
        }
        int safety = counts.size() * counts.size() + 10;
        while (!queue.isEmpty() && safety-- > 0) {
            String c = queue.poll();
            int myLayer = layer.getOrDefault(c, 0);
            for (String next : unlocks.getOrDefault(c, Set.of())) {
                int nextLayer = layer.getOrDefault(next, -1);
                if (nextLayer < myLayer + 1) {
                    layer.put(next, myLayer + 1);
                    queue.add(next);
                }
            }
        }
        counts.keySet().forEach(c -> layer.putIfAbsent(c, 0));
        chapterLayer = layer;

        chapters = new ArrayList<>(counts.keySet());
        chapters.sort(Comparator.comparingInt(c -> layer.getOrDefault(c, 0)));

        List<Edge> edgeList = new ArrayList<>();
        for (var e : edgeCount.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            edgeList.add(new Edge(parts[0], parts[1], e.getValue()));
        }
        edges = edgeList;
    }

    private void layoutBoxes() {
        boxes.clear();
        Map<Integer, List<String>> byLayer = new TreeMap<>();
        for (String c : chapters) {
            byLayer.computeIfAbsent(chapterLayer.getOrDefault(c, 0), k -> new ArrayList<>()).add(c);
        }

        int startX = 30, startY = 30;
        for (var e : byLayer.entrySet()) {
            int lx = startX + e.getKey() * (BOX_W + GAP_X);
            int ly = startY;
            for (String c : e.getValue()) {
                boxes.put(c, new int[] { lx, ly, BOX_W, BOX_H });
                ly += BOX_H + GAP_Y;
            }
        }
    }

    private String friendlyChapter(String chapter) {
        if (chapter == null || chapter.isEmpty()) return "MAIN";
        String resolved = ChapterConfig.getResolvedDisplayName(chapter);
        if (resolved != null) return resolved;
        return chapter;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);
        g.drawCenteredString(font, "§dChapter Map", width / 2, 8, ChroniclesThemePalette.TEXT);
        g.drawCenteredString(font, "§7Click a chapter to jump to it  ·  §8Drag to pan", width / 2, 18,
                ChroniclesThemePalette.TEXT_FAINT);

        if (chapters.isEmpty()) {
            g.drawCenteredString(font, "§8No quests placed yet.", width / 2, height / 2,
                    ChroniclesThemePalette.TEXT_FAINT);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        for (Edge e : edges) {
            int[] fromBox = boxes.get(e.from());
            int[] toBox = boxes.get(e.to());
            if (fromBox == null || toBox == null) continue;
            int x1 = fromBox[0] + fromBox[2] + offsetX, y1 = fromBox[1] + fromBox[3] / 2 + offsetY;
            int x2 = toBox[0] + offsetX, y2 = toBox[1] + toBox[3] / 2 + offsetY;
            LINE_STYLE.render(g, x1, y1, x2, y2, ChroniclesThemePalette.BORDER_LIT, 0);
            int midX = (x1 + x2) / 2, midY = (y1 + y2) / 2;
            g.drawString(font, "§8" + e.count(), midX, midY - 4, ChroniclesThemePalette.TEXT_FAINT, false);
        }

        for (String chapter : chapters) {
            int[] b = boxes.get(chapter);
            if (b == null) continue;
            int x = b[0] + offsetX, y = b[1] + offsetY, w = b[2], h = b[3];
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            g.fill(x, y, x + w, y + h, hov ? ChroniclesThemePalette.SEL_TAB : ChroniclesThemePalette.PANEL);
            ChroniclesUIKit.drawBorder(g, x, y, w, h,
                    hov ? ChroniclesThemePalette.BORDER_LIT : ChroniclesThemePalette.BORDER);
            String name = ChroniclesUIKit.fitText(font, friendlyChapter(chapter), w - 8);
            g.drawCenteredString(font, "§f" + name, x + w / 2, y + 6, ChroniclesThemePalette.TEXT);
            int count = questCounts.getOrDefault(chapter, 0);
            g.drawCenteredString(font, "§7" + count + " quest" + (count == 1 ? "" : "s"), x + w / 2, y + 18,
                    ChroniclesThemePalette.TEXT_DIM);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        if (btn == 0) {
            for (String chapter : chapters) {
                int[] b = boxes.get(chapter);
                if (b == null) continue;
                int x = b[0] + offsetX, y = b[1] + offsetY, w = b[2], h = b[3];
                if (mx >= x && mx < x + w && my >= y && my < y + h) {
                    parent.setSelectedChapter(chapter);
                    if (minecraft != null) minecraft.setScreen(parent);
                    return true;
                }
            }
        }
        if (btn == 0 || btn == 2) {
            dragging = true;
            dragLastX = mx;
            dragLastY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging) {
            offsetX += (int) Math.round(mx - dragLastX);
            offsetY += (int) Math.round(my - dragLastY);
            dragLastX = mx;
            dragLastY = my;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
