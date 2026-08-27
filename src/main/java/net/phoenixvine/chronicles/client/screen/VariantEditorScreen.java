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

    private static final int MIN_W = 460;
    private static final int MIN_H = 340;
    private float uiScale = 1f;
    private int vw, vh;

    private int listTop, listBottom, formTop;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    private final QuestNode questNode;
    private final List<QuestNode.QuestVariant> variants = new ArrayList<>();
    private int selected = -1;

    private EditBox conditionBox, titleBox, subtitleBox, descBox;

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

        uiScale = (width < MIN_W || height < MIN_H) ? Math.min(width / (float) MIN_W, height / (float) MIN_H) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        listTop = HEADER_H + 20;

        formTop = vh - FOOTER_H - 5 * (FIELD_H + FIELD_GAP) - 36;
        listBottom = formTop - 10;

        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        clearWidgets();

        addRenderableWidget(Button.builder(Component.literal("§7‹ Done"), b -> {
            flushToQuestNode();
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(vw / 2 - 40, vh - FOOTER_H + (FOOTER_H - 14) / 2, 80, 14)
                .tooltip(Tooltip.create(Component.literal("Save changes and return to quest editor"))).build());

        addRenderableWidget(Button.builder(Component.literal("§a+ Add Variant"), b -> {
            variants.add(new QuestNode.QuestVariant(""));
            selected = variants.size() - 1;
            rebuildWidgets();
        }).bounds(MARGIN, listTop - 18, 110, 14).build());

        if (selected < 0 || selected >= variants.size()) {
            conditionBox = titleBox = subtitleBox = descBox = null;
            return;
        }

        QuestNode.QuestVariant v = variants.get(selected);
        int fx = MARGIN;

        int fy = formTop + 14;
        int fw = vw - MARGIN * 2;

        conditionBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        conditionBox.setMaxLength(160);
        conditionBox.setHint(Component.literal("§8condition: e.g. config:pack_mode=expert"));
        conditionBox.setValue(v.condition);
        conditionBox.setResponder(s -> v.condition = s);
        addRenderableWidget(conditionBox);
        fy += FIELD_H + FIELD_GAP;

        titleBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        titleBox.setMaxLength(128);
        titleBox.setHint(Component.literal("§8title override: blank = inherit base title"));
        titleBox.setValue(v.title != null ? v.title : "");
        titleBox.setResponder(s -> v.title = s.isBlank() ? null : s);
        addRenderableWidget(titleBox);
        fy += FIELD_H + FIELD_GAP;

        subtitleBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        subtitleBox.setMaxLength(128);
        subtitleBox.setHint(Component.literal("§8subtitle override: blank = inherit base subtitle"));
        subtitleBox.setValue(v.subtitle != null ? v.subtitle : "");
        subtitleBox.setResponder(s -> v.subtitle = s.isBlank() ? null : s);
        addRenderableWidget(subtitleBox);
        fy += FIELD_H + FIELD_GAP;

        descBox = new EditBox(font, fx, fy, fw, FIELD_H, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(Component.literal("§8description override: blank = inherit base description"));
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
    public void render(@NotNull GuiGraphics g, int rawMx, int rawMy, float partial) {
        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, vw, vh, C_BG);

        g.fill(0, 0, vw, HEADER_H, C_HEADER);
        g.fill(0, 0, vw, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, vw, HEADER_H, C_BORDER);
        g.drawCenteredString(font, "§fQuest Variants  §8: §7" + questNode.getId().getPath(),
                vw / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, HEADER_H, vw, listTop - 1, C_PANEL);
        g.fill(0, listTop - 1, vw, listTop, C_BORDER);
        g.drawString(font, "§8VARIANTS  §7" + variants.size() + "  §8(first matching condition wins)",
                MARGIN + 120, HEADER_H + 3, C_TEXT_FAINT, false);

        int formPanelTop = formTop - 6;
        g.fill(0, formPanelTop, vw, vh - FOOTER_H, C_PANEL);
        g.fill(0, formPanelTop, vw, formPanelTop + 1, C_BORDER);
        if (selected >= 0 && selected < variants.size()) {
            g.fill(MARGIN, formPanelTop + 2, vw - MARGIN, vh - FOOTER_H - 2, C_FORM_BG);
            drawBorder(g, MARGIN, formPanelTop + 2, vw - MARGIN * 2, vh - FOOTER_H - 2 - (formPanelTop + 2),
                    C_BORDER);
            g.drawString(font, "§8EDITING VARIANT " + (selected + 1), MARGIN + 6, formPanelTop + 6, C_TEXT_FAINT,
                    false);
        } else {
            g.drawCenteredString(font, "§8Add a variant, or select one below to edit it.",
                    vw / 2, formPanelTop + 20, C_TEXT_FAINT);
        }

        g.fill(0, vh - FOOTER_H, vw, vh, C_HEADER);
        g.fill(0, vh - FOOTER_H, vw, vh - FOOTER_H + 1, C_BORDER);

        int ty = listTop;
        for (int i = 0; i < variants.size(); i++) {
            QuestNode.QuestVariant v = variants.get(i);
            if (ty + ROW_H > listBottom) break;
            boolean hov = mx >= MARGIN && mx < vw - MARGIN && my >= ty && my < ty + ROW_H;
            boolean sel = i == selected;
            if (sel) g.fill(MARGIN, ty, vw - MARGIN, ty + ROW_H, 0x3355AAFF);
            else if (hov) g.fill(MARGIN, ty, vw - MARGIN, ty + ROW_H, C_ROW_HOVER);
            g.fill(MARGIN, ty + 2, MARGIN + 2, ty + ROW_H - 2, C_ACCENT);

            String cond = v.condition.isBlank() ? "§c(no condition set)" : "§7" + v.condition;
            String summary = variantSummary(v);
            int textX = MARGIN + 8;
            int maxW = vw - MARGIN - textX - 40;
            String condLine = cond;
            if (font.width(condLine) > maxW) condLine = font.plainSubstrByWidth(condLine, maxW - 4) + "…";
            g.drawString(font, condLine, textX, ty + 3, C_TEXT_DIM, false);
            g.drawString(font, "§8" + summary, textX, ty + 12, C_TEXT_FAINT, false);

            int cx = vw - MARGIN - 12;
            g.drawString(font, "§c×", cx, ty + 6, 0xFFFF5555, false);
            if (i > 0) g.drawString(font, "§7▲", cx - 24, ty + 6, C_TEXT_DIM, false);
            if (i < variants.size() - 1) g.drawString(font, "§7▼", cx - 12, ty + 6, C_TEXT_DIM, false);

            ty += ROW_H;
        }
        if (variants.isEmpty())
            g.drawString(font, "§8No variants yet: this quest behaves identically in every pack mode.",
                    MARGIN + 8, listTop + 4, C_TEXT_FAINT, false);

        super.render(g, mx, my, partial);

        g.pose().popPose();
    }

    private String variantSummary(QuestNode.QuestVariant v) {
        List<String> parts = new ArrayList<>();
        if (v.title != null) parts.add("title");
        if (v.subtitle != null) parts.add("subtitle");
        if (v.description != null) parts.add("desc");
        if (v.visibility != null) parts.add("visibility=" + v.visibility.name().toLowerCase());
        if (v.tasks != null) parts.add(v.tasks.size() + " task(s)");
        if (v.rewards != null) parts.add(v.rewards.size() + " reward(s)");
        return parts.isEmpty() ? "no overrides: condition only gates nothing" : String.join(", ", parts);
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0) {
            int ty = listTop;
            for (int i = 0; i < variants.size(); i++) {
                if (ty + ROW_H > listBottom) break;
                if (my >= ty && my < ty + ROW_H) {
                    int cx = vw - MARGIN - 12;
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
                    if (mx >= MARGIN && mx < vw - MARGIN) {
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
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double dragX, double dragY) {
        return super.mouseDragged(rawMx / uiScale, rawMy / uiScale, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        return super.mouseReleased(rawMx / uiScale, rawMy / uiScale, btn);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double delta) {
        return super.mouseScrolled(rawMx / uiScale, rawMy / uiScale, delta);
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
