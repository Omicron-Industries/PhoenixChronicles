package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VariantEditorScreen extends Screen {

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT;
    private static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_FORM_BG = 0x33000000;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 10;
    private static final int ROW_H = 20;
    private static final int FIELD_H = 15;
    private static final int FIELD_GAP = 4;

    private int listTop, listBottom, formTop;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    private final QuestNode questNode;
    private final List<QuestNode.QuestVariant> variants = new ArrayList<>();
    private int selected = -1;

    private EditBox conditionBox, titleBox, descBox;

    private static final QuestNode.Visibility[] VIS_CYCLE;
    static {
        QuestNode.Visibility[] v = QuestNode.Visibility.values();
        VIS_CYCLE = new QuestNode.Visibility[v.length + 1];
        System.arraycopy(v, 0, VIS_CYCLE, 1, v.length);
        VIS_CYCLE[0] = null;
    }

    public VariantEditorScreen(Screen parent, QuestNode questNode) {
        super(Component.literal("Quest Variants"));
        this.parent = parent;
        this.questNode = questNode;
        this.variants.addAll(questNode.getVariants());
        if (!variants.isEmpty()) selected = 0;
    }

    @Override
    protected void init() {
        PhoenixTheme th = PhoenixTheme.current();
        C_BG = th.bg.getColor();
        C_PANEL = th.panel.getColor();
        C_HEADER = th.header.getColor();
        C_BORDER = th.border.getColor();
        C_ACCENT = th.accent.getColor();
        C_TEXT = th.text.getColor();
        C_TEXT_DIM = th.textDim.getColor();
        C_TEXT_FAINT = th.textFaint.getColor();

        listTop = HEADER_H + 20;

        formTop = height - FOOTER_H - 4 * (FIELD_H + FIELD_GAP) - 36;
        listBottom = formTop - 10;

        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();

        addRenderableWidget(Button.builder(Component.literal("§7‹ Done"), b -> {
            flushToQuestNode();
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width / 2 - 40, height - FOOTER_H + (FOOTER_H - 14) / 2, 80, 14)
                .tooltip(Tooltip.create(Component.literal("Save changes and return to quest editor"))).build());

        addRenderableWidget(Button.builder(Component.literal("§a+ Add Variant"), b -> {
            variants.add(new QuestNode.QuestVariant(""));
            selected = variants.size() - 1;
            rebuildWidgets();
        }).bounds(MARGIN, listTop - 18, 110, 14).build());

        if (selected < 0 || selected >= variants.size()) {
            conditionBox = titleBox = descBox = null;
            return;
        }

        QuestNode.QuestVariant v = variants.get(selected);
        int fx = MARGIN;

        int fy = formTop + 14;
        int fw = width - MARGIN * 2;

        conditionBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        conditionBox.setMaxLength(160);
        conditionBox.setHint(Component.literal("§8condition — e.g. config:pack_mode=expert"));
        conditionBox.setValue(v.condition);
        conditionBox.setResponder(s -> v.condition = s);
        addRenderableWidget(conditionBox);
        fy += FIELD_H + FIELD_GAP;

        titleBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        titleBox.setMaxLength(128);
        titleBox.setHint(Component.literal("§8title override — blank = inherit base title"));
        titleBox.setValue(v.title != null ? v.title : "");
        titleBox.setResponder(s -> v.title = s.isBlank() ? null : s);
        addRenderableWidget(titleBox);
        fy += FIELD_H + FIELD_GAP;

        descBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(Component.literal("§8description override — blank = inherit base description"));
        descBox.setValue(v.description != null ? v.description : "");
        descBox.setResponder(s -> v.description = s.isBlank() ? null : s);
        addRenderableWidget(descBox);
        fy += FIELD_H + FIELD_GAP;

        int visIdx = indexOfVisibility(v.visibility);
        String visLabel = v.visibility == null ? "§8Visibility: Inherit" : "§7Visibility: " + v.visibility.name();
        addRenderableWidget(Button.builder(Component.literal(visLabel), b -> {
            v.visibility = VIS_CYCLE[(visIdx + 1) % VIS_CYCLE.length];
            rebuildWidgets();
        }).bounds(fx, fy, (fw - FIELD_GAP) / 2, FIELD_H)
                .tooltip(Tooltip
                        .create(Component.literal("Cycle: Inherit → Normal → Hidden → Mystery → Disabled")))
                .build());

        String taskRewardLabel = (v.tasks != null || v.rewards != null) ?
                "§eEdit Tasks/Rewards… §8(overridden)" : "§7Edit Tasks/Rewards…";
        addRenderableWidget(Button.builder(Component.literal(taskRewardLabel), b -> {

            flushToQuestNode();
            if (minecraft != null) minecraft.setScreen(new TaskRewardEditorScreen(this, questNode, v));
        }).bounds(fx + (fw - FIELD_GAP) / 2 + FIELD_GAP, fy, (fw - FIELD_GAP) / 2, FIELD_H).build());
        fy += FIELD_H + FIELD_GAP;

        if (v.tasks != null || v.rewards != null) {
            addRenderableWidget(Button.builder(Component.literal("§cClear task/reward override"), b -> {
                v.tasks = null;
                v.rewards = null;
                rebuildWidgets();
            }).bounds(fx, fy, fw, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "Revert this variant's tasks/rewards back to inheriting the quest's base list")))
                    .build());
        }
    }

    private static int indexOfVisibility(QuestNode.Visibility v) {
        for (int i = 0; i < VIS_CYCLE.length; i++) if (VIS_CYCLE[i] == v) return i;
        return 0;
    }

    private void flushToQuestNode() {
        questNode.clearVariants();
        for (QuestNode.QuestVariant v : variants) questNode.addVariant(v);

        if (net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(questNode.getId()) == questNode) {
            net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(questNode);
        }
        ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, width, height, C_BG);

        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, 0, width, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.drawCenteredString(font, "§fQuest Variants  §8— §7" + questNode.getId().getPath(),
                width / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, HEADER_H, width, listTop - 1, C_PANEL);
        g.fill(0, listTop - 1, width, listTop, C_BORDER);
        g.drawString(font, "§8VARIANTS  §7" + variants.size() + "  §8(first matching condition wins)",
                MARGIN + 120, HEADER_H + 3, C_TEXT_FAINT, false);

        int formPanelTop = formTop - 6;
        g.fill(0, formPanelTop, width, height - FOOTER_H, C_PANEL);
        g.fill(0, formPanelTop, width, formPanelTop + 1, C_BORDER);
        if (selected >= 0 && selected < variants.size()) {
            g.fill(MARGIN, formPanelTop + 2, width - MARGIN, height - FOOTER_H - 2, C_FORM_BG);
            drawBorder(g, MARGIN, formPanelTop + 2, width - MARGIN * 2, height - FOOTER_H - 2 - (formPanelTop + 2),
                    C_BORDER);
            g.drawString(font, "§8EDITING VARIANT " + (selected + 1), MARGIN + 6, formPanelTop + 6, C_TEXT_FAINT,
                    false);
        } else {
            g.drawCenteredString(font, "§8Add a variant, or select one below to edit it.",
                    width / 2, formPanelTop + 20, C_TEXT_FAINT);
        }

        g.fill(0, height - FOOTER_H, width, height, C_HEADER);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);

        g.enableScissor(0, listTop, width, listBottom);
        int ty = listTop;
        for (int i = 0; i < variants.size(); i++) {
            QuestNode.QuestVariant v = variants.get(i);
            if (ty + ROW_H > listBottom) break;
            boolean hov = mx >= MARGIN && mx < width - MARGIN && my >= ty && my < ty + ROW_H;
            boolean sel = i == selected;
            if (sel) g.fill(MARGIN, ty, width - MARGIN, ty + ROW_H, 0x3355AAFF);
            else if (hov) g.fill(MARGIN, ty, width - MARGIN, ty + ROW_H, C_ROW_HOVER);
            g.fill(MARGIN, ty + 2, MARGIN + 2, ty + ROW_H - 2, C_ACCENT);

            String cond = v.condition.isBlank() ? "§c(no condition set)" : "§7" + v.condition;
            String summary = variantSummary(v);
            int textX = MARGIN + 8;
            int maxW = width - MARGIN - textX - 40;
            String condLine = cond;
            if (font.width(condLine) > maxW) condLine = font.plainSubstrByWidth(condLine, maxW - 4) + "…";
            g.drawString(font, condLine, textX, ty + 3, C_TEXT_DIM, false);
            g.drawString(font, "§8" + summary, textX, ty + 12, C_TEXT_FAINT, false);

            int cx = width - MARGIN - 12;
            g.drawString(font, "§c×", cx, ty + 6, 0xFFFF5555, false);
            if (i > 0) g.drawString(font, "§7▲", cx - 24, ty + 6, C_TEXT_DIM, false);
            if (i < variants.size() - 1) g.drawString(font, "§7▼", cx - 12, ty + 6, C_TEXT_DIM, false);

            ty += ROW_H;
        }
        if (variants.isEmpty())
            g.drawString(font, "§8No variants yet — this quest behaves identically in every pack mode.",
                    MARGIN + 8, listTop + 4, C_TEXT_FAINT, false);
        g.disableScissor();

        super.render(g, mx, my, partial);
    }

    private String variantSummary(QuestNode.QuestVariant v) {
        List<String> parts = new ArrayList<>();
        if (v.title != null) parts.add("title");
        if (v.description != null) parts.add("desc");
        if (v.visibility != null) parts.add("visibility=" + v.visibility.name().toLowerCase());
        if (v.tasks != null) parts.add(v.tasks.size() + " task(s)");
        if (v.rewards != null) parts.add(v.rewards.size() + " reward(s)");
        return parts.isEmpty() ? "no overrides — condition only gates nothing" : String.join(", ", parts);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int ty = listTop;
            for (int i = 0; i < variants.size(); i++) {
                if (ty + ROW_H > listBottom) break;
                if (my >= ty && my < ty + ROW_H) {
                    int cx = width - MARGIN - 12;
                    if (mx >= cx - 2 && mx < cx + 10) {
                        variants.remove(i);
                        if (selected >= variants.size()) selected = variants.size() - 1;
                        rebuildWidgets();
                        return true;
                    }
                    if (i > 0 && mx >= cx - 26 && mx < cx - 14) {
                        java.util.Collections.swap(variants, i, i - 1);
                        selected = i - 1;
                        rebuildWidgets();
                        return true;
                    }
                    if (i < variants.size() - 1 && mx >= cx - 14 && mx < cx - 2) {
                        java.util.Collections.swap(variants, i, i + 1);
                        selected = i + 1;
                        rebuildWidgets();
                        return true;
                    }
                    if (mx >= MARGIN && mx < width - MARGIN) {
                        selected = i;
                        rebuildWidgets();
                        return true;
                    }
                }
                ty += ROW_H;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void onClose() {
        flushToQuestNode();
        ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, color);
    }
}
