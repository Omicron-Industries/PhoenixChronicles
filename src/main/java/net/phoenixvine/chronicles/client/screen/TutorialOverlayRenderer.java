package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.TutorialProgressTracker;
import net.phoenixvine.chronicles.tracker.TutorialStep;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

class TutorialOverlayRenderer {

    record Colors(int accent, int border, int textFaint, int textDim, int text, int nbordDone) {}

    record Layout(int width, int height, int sidebarW, int headerH, int toolbarY, int toolbarH) {}

    private int[] tutPrevBtn = null;
    private int[] tutNextBtn = null;
    private int[] tutSkipBtn = null;

    QuestNode findActiveTutorialQuest(Function<QuestNode, QuestState> stateLookup) {
        if (!TutorialProgressTracker.isInitialized()) {
            Path cfg = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            TutorialProgressTracker.init(cfg);
        }
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (n.getTutorialSteps().isEmpty()) continue;
            String qid = n.getId().getPath();
            if (TutorialProgressTracker.isDismissed(qid)) continue;
            QuestState st = stateLookup.apply(n);
            if (st == QuestState.ACTIVE || st == QuestState.UNLOCKED) return n;
        }
        return null;
    }

    void render(GuiGraphics g, int mx, int my, Font font, Layout layout, Colors colors,
                Map<ResourceLocation, int[]> nodeScreenPos, Function<QuestNode, Integer> scaledNodeSizeFor,
                Supplier<Integer> scaledNodeSizeDefault, Function<QuestNode, QuestState> stateLookup) {
        tutPrevBtn = null;
        tutNextBtn = null;
        tutSkipBtn = null;

        QuestNode tutQuest = findActiveTutorialQuest(stateLookup);
        if (tutQuest == null) return;

        List<TutorialStep> steps = tutQuest.getTutorialSteps();
        String qid = tutQuest.getId().getPath();
        int stepIdx = TutorialProgressTracker.getStep(qid);
        if (stepIdx < 0 || stepIdx >= steps.size()) return;
        TutorialStep step = steps.get(stepIdx);

        int width = layout.width(), height = layout.height();
        int cl = layout.sidebarW(), cr = width;

        int hx = 0, hy = 0, hw = 0, hh = 0;
        if (step.hasHighlight()) {
            if (TutorialStep.HL_SIDEBAR.equals(step.highlight())) {
                hx = 0;
                hy = 0;
                hw = layout.sidebarW();
                hh = height;
            } else if (TutorialStep.HL_CANVAS.equals(step.highlight())) {
                hx = cl;
                hy = layout.headerH();
                hw = cr - cl;
                hh = height - layout.headerH();
            } else if (TutorialStep.HL_TOOLBAR.equals(step.highlight())) {
                hx = 0;
                hy = layout.toolbarY();
                hw = width;
                hh = layout.toolbarH();
            } else if (step.isNodeHighlight()) {
                String nid = step.nodeHighlightId();
                if (nid != null) {
                    ResourceLocation rid = new ResourceLocation(PhoenixChronicles.MOD_ID, nid);
                    int[] pos = nodeScreenPos.get(rid);
                    if (pos != null) {
                        QuestNode hlNode = QuestTreeRegistry.getQuest(rid);
                        int sz = hlNode != null ? scaledNodeSizeFor.apply(hlNode) : scaledNodeSizeDefault.get();
                        hx = pos[0] - 4;
                        hy = pos[1] - 4;
                        hw = sz + 8;
                        hh = sz + 8;
                    }
                }
            }
        }

        int dimColor = 0xBB000000;
        if (hw > 0) {

            g.fill(0, 0, width, hy, dimColor);
            g.fill(0, hy + hh, width, height, dimColor);
            g.fill(0, hy, hx, hy + hh, dimColor);
            g.fill(hx + hw, hy, width, hy + hh, dimColor);

            g.fill(hx - 1, hy - 1, hx + hw + 1, hy, colors.accent());
            g.fill(hx - 1, hy + hh, hx + hw + 1, hy + hh + 1, colors.accent());
            g.fill(hx - 1, hy, hx, hy + hh, colors.accent());
            g.fill(hx + hw, hy, hx + hw + 1, hy + hh, colors.accent());
        } else {
            g.fill(0, 0, width, height, dimColor);
        }

        int boxW = Math.min(380, width - 40);
        int boxX = (width - boxW) / 2;

        List<String> wrappedLines = new ArrayList<>();
        String remaining = step.text();
        int maxLineW = boxW - 20;
        while (!remaining.isEmpty()) {
            if (font.width(remaining) <= maxLineW) {
                wrappedLines.add(remaining);
                break;
            }
            String sub = font.plainSubstrByWidth(remaining, maxLineW);
            int sp = sub.lastIndexOf(' ');
            String lineOut = sp > 0 ? sub.substring(0, sp) : sub;
            wrappedLines.add(lineOut);
            remaining = remaining.substring(lineOut.length()).trim();
        }

        int textH = wrappedLines.size() * 11;
        int btnRowH = 18;
        int boxH = 14 + textH + 8 + btnRowH + 8;

        int boxY = (hw > 0 && hy + hh > height * 2 / 3) ? hy - boxH - 10 : (hw > 0 ? hy + hh + 10 : height - boxH - 20);
        boxY = Math.max(layout.headerH() + 4, Math.min(boxY, height - boxH - 4));

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0E0E18);
        g.fill(boxX, boxY, boxX + boxW, boxY + 1, colors.accent());
        g.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, colors.border());
        g.fill(boxX, boxY, boxX + 1, boxY + boxH, colors.border());
        g.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, colors.border());

        String counter = "Step " + (stepIdx + 1) + " / " + steps.size();
        g.drawString(font, "§8" + counter, boxX + 10, boxY + 5, colors.textFaint(), false);

        g.drawString(font, "§7" + tutQuest.getTitle().getString(),
                boxX + boxW - font.width(tutQuest.getTitle().getString()) - 10, boxY + 5, colors.textDim(), false);

        int ty = boxY + 16;
        for (String line : wrappedLines) {
            g.drawString(font, "§f" + line, boxX + 10, ty, colors.text(), false);
            ty += 11;
        }

        int btnY = boxY + boxH - btnRowH - 5;
        int btnH = btnRowH - 2;

        int skipW = font.width("Skip") + 12;
        int skipX = boxX + boxW - skipW - 6;
        tutSkipBtn = new int[] { skipX, btnY, skipX + skipW, btnY + btnH };
        boolean skipHov = mx >= skipX && mx < skipX + skipW && my >= btnY && my < btnY + btnH;
        g.fill(skipX, btnY, skipX + skipW, btnY + btnH, skipHov ? 0x33FFFFFF : 0x1AFFFFFF);
        g.drawCenteredString(font, "§8Skip", skipX + skipW / 2, btnY + 4,
                skipHov ? colors.textDim() : colors.textFaint());

        boolean isLast = stepIdx == steps.size() - 1;
        String nextLabel = isLast ? "§aFinish" : "§fNext →";
        int nextW = font.width(isLast ? "Finish" : "Next →") + 16;
        int nextX = skipX - nextW - 4;
        tutNextBtn = new int[] { nextX, btnY, nextX + nextW, btnY + btnH };
        boolean nextHov = mx >= nextX && mx < nextX + nextW && my >= btnY && my < btnY + btnH;
        g.fill(nextX, btnY, nextX + nextW, btnY + btnH, nextHov ? 0x55006633 : 0x2A006633);
        g.fill(nextX, btnY, nextX + nextW, btnY + 1, nextHov ? colors.nbordDone() : 0xFF004422);
        g.drawCenteredString(font, nextLabel, nextX + nextW / 2, btnY + 4, nextHov ? colors.nbordDone() : 0xFF55BB77);

        if (stepIdx > 0) {
            int prevW = font.width("← Prev") + 12;
            int prevX = boxX + 6;
            tutPrevBtn = new int[] { prevX, btnY, prevX + prevW, btnY + btnH };
            boolean prevHov = mx >= prevX && mx < prevX + prevW && my >= btnY && my < btnY + btnH;
            g.fill(prevX, btnY, prevX + prevW, btnY + btnH, prevHov ? 0x33FFFFFF : 0x1AFFFFFF);
            g.drawCenteredString(font, "§8← Prev", prevX + prevW / 2, btnY + 4,
                    prevHov ? colors.textDim() : colors.textFaint());
        }
    }

    boolean handleClick(double mx, double my, Function<QuestNode, QuestState> stateLookup) {
        if (tutNextBtn == null && tutPrevBtn == null && tutSkipBtn == null) return false;

        QuestNode tutQuest = findActiveTutorialQuest(stateLookup);
        if (tutQuest == null) return false;
        String qid = tutQuest.getId().getPath();

        if (tutNextBtn != null && mx >= tutNextBtn[0] && mx < tutNextBtn[2] && my >= tutNextBtn[1] &&
                my < tutNextBtn[3]) {
            TutorialProgressTracker.advance(qid, tutQuest.getTutorialSteps().size());
            return true;
        }
        if (tutPrevBtn != null && mx >= tutPrevBtn[0] && mx < tutPrevBtn[2] && my >= tutPrevBtn[1] &&
                my < tutPrevBtn[3]) {
            TutorialProgressTracker.back(qid);
            return true;
        }
        if (tutSkipBtn != null && mx >= tutSkipBtn[0] && mx < tutSkipBtn[2] && my >= tutSkipBtn[1] &&
                my < tutSkipBtn[3]) {
            TutorialProgressTracker.dismiss(qid);
            return true;
        }

        return tutNextBtn != null;
    }
}
