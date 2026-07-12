package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings.*;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.CategoryPrereqDefaults;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Full dependency-line settings screen.
 * Global controls: line shape (SPLINE/STRAIGHT) and visual style (THIN/NORMAL/THICK/GLOW)
 * with a live preview. Category-defaults section: this category's default prereq gate
 * (require-all vs any, optional-prereq min count), inherited by every quest in it unless
 * a quest sets its own override. Per-quest section: hide/show dep lines per quest node.
 */
public class DepLineSettingsScreen extends Screen {

    private final ChronicleOverviewScreen parent;
    private final String category;
    /**
     * Non-null when opened from a specific quest's/edge's right-click context menu — the
     * screen then shows only that quest plus quests that depend on it, hiding the global
     * appearance and category-defaults sections entirely. Null when opened generally
     * (empty-canvas right-click), showing the full category view.
     *
     * Stored as an id and re-resolved fresh via {@link #focusNode()} rather than keeping the
     * live QuestNode reference around: every per-quest edit on this screen writes the edited
     * quest's SNBT to disk, which QuestFileWatcher picks up and reacts to by reloading the
     * whole quest tree from disk - replacing every QuestNode instance. QuestNode has no
     * equals() override, so a stale reference silently stops matching anything afterward
     * (this was the "dependency list collapses to 1 entry after any click" bug: the stale
     * focusNode no longer .equals() any prerequisite list it used to be found in).
     */
    private final net.minecraft.resources.ResourceLocation focusNodeId;

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_DONE, C_ACTIVE;

    private static final int MARGIN = 8;
    private static final int HEADER_H = 28;
    private static final int SEARCH_H = 22;   // sticky search bar below header
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 3;
    private static final int ARROW_W = 16;
    /**
     * Fixed "what does this value mean" reference panel pinned to the top-right, so Shape/
     * Style/Speed columns reading "Inherit, Inherit, Inherit" don't require cycling through
     * every option just to find out what the possible values even are.
     */
    private static final int LEGEND_W = 118;

    private LineStyle lineShape;
    private LineVisualStyle lineVisual;
    private QuestChroniclesSettings.LineAnimSpeed lineAnimSpeed;
    private boolean lineArrows;

    /** null = no category default configured (quests fall back to the engine default of true). */
    private Boolean catRequireAllDefault;
    /** null = no category default configured; otherwise one of {@link #MIN_COUNT_STEPS}. */
    private Integer catOptionalMinDefault;
    private static final Integer[] MIN_COUNT_STEPS = { null, -1, 0, 1, 2, 3, 4, 5 };

    private EditBox searchBox;
    private String searchQuery = "";

    private int scrollY = 0;

    public DepLineSettingsScreen(ChronicleOverviewScreen parent, String category) {
        this(parent, category, null);
    }

    public DepLineSettingsScreen(ChronicleOverviewScreen parent, String category, QuestNode focusNode) {
        super(Component.literal(
                focusNode != null ? "Dependencies — " + focusNode.getTitle().getString() : "Dependency Line Settings"));
        this.parent = parent;
        this.category = category;
        this.focusNodeId = focusNode != null ? focusNode.getId() : null;
    }

    /** Re-resolves the focus quest fresh from the registry every call - see focusNodeId's doc. */
    private QuestNode focusNode() {
        return focusNodeId == null ? null : QuestTreeRegistry.getQuest(focusNodeId);
    }

    @Override
    protected void init() {
        super.init();
        ChroniclesTheme t = ChroniclesTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_ACCENT = t.accent.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_DONE = t.done.getColor();
        C_ACTIVE = t.activeColor.getColor();

        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        lineShape = s.getLineStyle();
        lineVisual = s.getLineVisualStyle();
        lineAnimSpeed = s.getLineAnimSpeed();
        lineArrows = s.isShowLineArrows();

        catRequireAllDefault = CategoryPrereqDefaults.getRequireAll(category);
        catOptionalMinDefault = CategoryPrereqDefaults.getOptionalMinCount(category);

        // Used to only exist for the unscoped (whole-category) view - the focused "quest +
        // dependents" view reserved the exact same header strip for it but left it empty, so a
        // hub quest with a lot of dependents had no way to search through them at all.
        searchBox = new EditBox(font, MARGIN, HEADER_H + 3, width - MARGIN * 2, SEARCH_H - 6, Component.empty());
        searchBox.setHint(Component.literal(
                focusNodeId != null ? "§8Filter dependents…" : "§8Filter quests…"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(v -> {
            searchQuery = v.toLowerCase().trim();
            scrollY = 0;
        });
        addRenderableWidget(searchBox);
    }

    // ── Layout helpers ─────────────────────────────────────────────────────────

    /** Computes how many pixels from contentTop each section starts (scrollY not applied). */
    private static final int GLOBAL_SECTION_LABEL_H = 10 + ROW_GAP;
    private static final int GLOBAL_ROW_COUNT = 4;
    private static final int PREVIEW_H = 40;
    private static final int PREVIEW_GAP = 8;
    private static final int DIVIDER_H = 6;
    private static final int CATEGORY_LABEL_H = 10 + ROW_GAP;
    private static final int CATEGORY_ROW_COUNT = 2;
    private static final int PER_QUEST_LABEL_H = 10 + ROW_GAP;

    private int globalEnd() {
        return GLOBAL_SECTION_LABEL_H + GLOBAL_ROW_COUNT * (ROW_H + ROW_GAP) + 4 + PREVIEW_H + PREVIEW_GAP;
    }

    private int categoryEnd() {
        return globalEnd() + DIVIDER_H + CATEGORY_LABEL_H + CATEGORY_ROW_COUNT * (ROW_H + ROW_GAP);
    }

    private int perQuestStart() {
        return categoryEnd() + DIVIDER_H + PER_QUEST_LABEL_H;
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        // Header
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        QuestNode focusForHeader = focusNode();
        g.drawCenteredString(font,
                focusForHeader != null ? "§fDependencies — " + focusForHeader.getTitle().getString() :
                        "§fDependency Line Settings",
                width / 2, 9, C_TEXT);

        // Draw the sticky search bar strip
        g.fill(0, HEADER_H, width, HEADER_H + SEARCH_H, C_HEADER);
        g.fill(0, HEADER_H + SEARCH_H - 1, width, HEADER_H + SEARCH_H, C_BORDER);

        int contentTop = HEADER_H + SEARCH_H + MARGIN;
        int contentH = height - HEADER_H - SEARCH_H - MARGIN - FOOTER_H - MARGIN;
        int x = MARGIN, w = width - MARGIN * 3 - LEGEND_W;

        renderLegend(g, width - MARGIN - LEGEND_W, contentTop);

        g.enableScissor(0, contentTop, width, contentTop + contentH);
        int y = contentTop - scrollY;

        if (focusNodeId == null) {
            // ── Global ────────────────────────────────────────────────────────
            g.drawString(font, "§8GLOBAL APPEARANCE:", x, y, C_TEXT_FAINT, false);
            y += GLOBAL_SECTION_LABEL_H;

            y = renderCycleRow(g, x, y, w, "§fLine Shape", lineShape.name(), mx, my) + ROW_GAP;
            y = renderCycleRow(g, x, y, w, "§fLine Style", lineVisual.name(), mx, my) + ROW_GAP;
            y = renderCycleRow(g, x, y, w, "§fDot Speed", lineAnimSpeed.name(), mx, my) + ROW_GAP;
            y = renderCycleRow(g, x, y, w, "§fDirectional Arrows", lineArrows ? "ON" : "OFF", mx, my) + ROW_GAP;

            // Preview box
            y += 4;
            g.fill(x, y, x + w, y + PREVIEW_H, C_PANEL);
            g.fill(x, y, x + w, y + 1, C_BORDER);
            g.fill(x, y + PREVIEW_H - 1, x + w, y + PREVIEW_H, C_BORDER);
            g.fill(x, y, x + 1, y + PREVIEW_H, C_BORDER);
            g.fill(x + w - 1, y, x + w, y + PREVIEW_H, C_BORDER);
            g.drawString(font, "§8Preview", x + 4, y + 2, C_TEXT_FAINT, false);
            drawPreviewLines(g, x + 4, y + 12, w - 8, PREVIEW_H - 16);
            y += PREVIEW_H + PREVIEW_GAP;

            // ── Category defaults ────────────────────────────────────────────
            g.fill(x, y, x + w, y + 1, C_BORDER);
            y += DIVIDER_H;
            g.drawString(font, "§8CATEGORY DEFAULTS  §7(" + category + ")", x, y, C_TEXT_FAINT, false);
            y += CATEGORY_LABEL_H;

            String requireAllLabel = catRequireAllDefault == null ? "No default (ALL)" :
                    catRequireAllDefault ? "ALL required" : "ANY sufficient";
            y = renderCycleRow(g, x, y, w, "§fPrereq gate default", requireAllLabel, mx, my) + ROW_GAP;

            String minCountLabel = minCountStepLabel(catOptionalMinDefault);
            y = renderCycleRow(g, x, y, w, "§fOptional prereq min-count", minCountLabel, mx, my) + ROW_GAP;

            g.fill(x, y, x + w, y + 1, C_BORDER);
            y += DIVIDER_H;
        }

        // ── Per-quest ─────────────────────────────────────────────────────────
        List<QuestNode> quests = questList();

        String countHint;
        if (focusNodeId != null) {
            QuestNode fn = focusNode();
            String anchor = fn != null ? fn.getTitle().getString() : focusNodeId.getPath();
            int depCount = Math.max(0, quests.size() - 1); // exclude the anchor row itself
            countHint = searchQuery.isEmpty() ? "(" + anchor + " + dependents)" :
                    "(" + anchor + " + " + depCount + " matching dependent" + (depCount == 1 ? "" : "s") + ")";
        } else {
            countHint = searchQuery.isEmpty() ? "(" + category + ")" :
                    "(" + quests.size() + " match" + (quests.size() == 1 ? "" : "es") + " in " + category + ")";
        }
        g.drawString(font, "§8PER-QUEST  §7" + countHint, x, y, C_TEXT_FAINT, false);
        y += PER_QUEST_LABEL_H;

        for (QuestNode quest : quests) {
            boolean hidden = quest.isHideDepLine();
            int hideW = 60, miniW = 44, miniGap = 3;
            int hideX = x + w - hideW - 2;
            int speedX = hideX - miniGap - miniW;
            int styleX = speedX - miniGap - miniW;
            int shapeX = styleX - miniGap - miniW;
            int btnY = y + 2;
            boolean hasEdges = !edgeParentsFor(quest).isEmpty();

            boolean hideHov = mx >= hideX && mx < hideX + hideW && my >= btnY && my < btnY + ROW_H - 4;
            boolean shapeHov = hasEdges && mx >= shapeX && mx < shapeX + miniW && my >= btnY && my < btnY + ROW_H - 4;
            boolean styleHov = hasEdges && mx >= styleX && mx < styleX + miniW && my >= btnY && my < btnY + ROW_H - 4;
            boolean speedHov = hasEdges && mx >= speedX && mx < speedX + miniW && my >= btnY && my < btnY + ROW_H - 4;
            boolean rowHov = mx >= x && mx < shapeX - 2 && my >= y && my < y + ROW_H;

            if (rowHov || hideHov || shapeHov || styleHov || speedHov) g.fill(x, y, x + w, y + ROW_H, 0x10FFFFFF);

            String name = quest.getTitle().getString();
            int nameMaxW = shapeX - x - 8;
            if (font.width(name) > nameMaxW) name = font.plainSubstrByWidth(name, Math.max(0, nameMaxW - 6)) + "…";
            g.drawString(font, "§7" + name, x + 4, y + 7, C_TEXT_DIM, false);

            // Shape / Style / Speed mini-buttons - apply uniformly across every edge this row
            // represents (see edgeParentsFor): disabled/dashed when the row has no edges to
            // style (the focus quest's own row in the scoped view has no single counterpart).
            drawMiniButton(g, shapeX, btnY, miniW, "Shape", rowShapeLabel(quest), hasEdges, shapeHov);
            drawMiniButton(g, styleX, btnY, miniW, "Style", rowVisualLabel(quest), hasEdges, styleHov);
            drawMiniButton(g, speedX, btnY, miniW, "Speed", rowSpeedLabel(quest), hasEdges, speedHov);

            // Toggle button
            g.fill(hideX, btnY, hideX + hideW, btnY + ROW_H - 4, hideHov ? 0x33FFFFFF : 0x11FFFFFF);
            g.fill(hideX, btnY, hideX + hideW, btnY + 1, hidden ? 0xFF444455 : C_DONE);
            g.drawCenteredString(font, hidden ? "§8HIDDEN" : "§aVISIBLE",
                    hideX + hideW / 2, btnY + 5, hidden ? C_TEXT_FAINT : C_DONE);

            y += ROW_H + ROW_GAP;
        }

        if (quests.isEmpty()) {
            String emptyMsg = searchQuery.isEmpty() ? "§8(no quests in this category)" :
                    "§8No quests match \"" + searchQuery + "\"";
            g.drawString(font, emptyMsg, x + 4, y, C_TEXT_FAINT, false);
        }

        g.disableScissor();

        // Footer
        int footerY = height - FOOTER_H;
        g.fill(0, footerY, width, height, C_HEADER);
        g.fill(0, footerY, width, footerY + 1, C_BORDER);

        int fbtnW = 80, fbtnGap = 8, fbtnY = footerY + 5;
        int saveX = width / 2 - fbtnW - fbtnGap / 2;
        int closeX = width / 2 + fbtnGap / 2;

        boolean saveHov = mx >= saveX && mx < saveX + fbtnW && my >= fbtnY && my < fbtnY + 18;
        boolean closeHov = mx >= closeX && mx < closeX + fbtnW && my >= fbtnY && my < fbtnY + 18;

        g.fill(saveX, fbtnY, saveX + fbtnW, fbtnY + 18, saveHov ? 0xFF2A4A2A : 0xFF1A2A1A);
        if (saveHov) g.fill(saveX, fbtnY, saveX + fbtnW, fbtnY + 1, C_DONE);
        g.drawCenteredString(font, "§a✓ Save", saveX + fbtnW / 2, fbtnY + 6, saveHov ? C_DONE : C_TEXT);

        g.fill(closeX, fbtnY, closeX + fbtnW, fbtnY + 18, closeHov ? 0xFF3A3A3A : 0xFF2A2A2A);
        if (closeHov) g.fill(closeX, fbtnY, closeX + fbtnW, fbtnY + 1, 0xFF888898);
        g.drawCenteredString(font, "§7✕ Close", closeX + fbtnW / 2, fbtnY + 6, closeHov ? 0xFFCCCCCC : C_TEXT);

        // Never called before - searchBox (and any future widget added here) existed and was
        // clickable/focusable but was never actually drawn to screen.
        super.render(g, mx, my, partial);
    }

    // ── Legend sidebar ──────────────────────────────────────────────────────────

    /**
     * Fixed reference panel (not part of the scrollable list, drawn once per frame at a constant
     * position) spelling out what Shape/Style/Speed actually mean - every per-quest row's
     * override starts at "Inherit", and without this you'd have to click through the full cycle
     * on a row just to discover what the possible values even are.
     */
    private void renderLegend(GuiGraphics g, int lx, int ly) {
        String[] lines = {
                "§8LEGEND",
                "§fShape",
                "§7Spline: curved S",
                "§7Straight: direct",
                "",
                "§fStyle",
                "§7Thin/Normal/Bold",
                "§7Thick/Wide: heavier",
                "§7Glow: soft halo",
                "",
                "§fSpeed",
                "§7Hover-arrow flow",
                "§7Slowest…Very Fast",
                "",
                "§7\"Inherit\" = use the",
                "§7Global Appearance",
                "§7above (or this row's",
                "§7own override)",
        };
        int lineH = 9;
        int panelH = lines.length * lineH + 8;

        g.fill(lx, ly, lx + LEGEND_W, ly + panelH, C_PANEL);
        g.fill(lx, ly, lx + LEGEND_W, ly + 1, C_BORDER);
        g.fill(lx, ly, lx + 1, ly + panelH, C_BORDER);
        g.fill(lx + LEGEND_W - 1, ly, lx + LEGEND_W, ly + panelH, C_BORDER);
        g.fill(lx, ly + panelH - 1, lx + LEGEND_W, ly + panelH, C_BORDER);

        int ty = ly + 4;
        for (String line : lines) {
            if (!line.isEmpty()) g.drawString(font, line, lx + 4, ty, C_TEXT_DIM, false);
            ty += lineH;
        }
    }

    // ── Preview drawing ────────────────────────────────────────────────────────

    private void drawPreviewLines(GuiGraphics g, int x, int y, int w, int h) {
        boolean spline = lineShape == LineStyle.SPLINE;
        int midX = x + w / 2;
        int ty = y + h / 4;
        int by = y + 3 * h / 4;

        // Stagger control points to show spline curve
        drawPreviewLine(g, x, ty, midX, by, 0xFF00CC66, spline);
        drawPreviewLine(g, midX, by, x + w, ty, 0xFFFFAA00, spline);

        g.drawString(font, "§8" + lineShape.name() + "  ·  " + lineVisual.name(),
                x + w - font.width(lineShape.name() + "  ·  " + lineVisual.name()), y - 10, C_TEXT_FAINT, false);
    }

    private void drawPreviewLine(GuiGraphics g, int x1, int y1, int x2, int y2, int col, boolean spline) {
        int steps = Math.max(16, Math.abs(x2 - x1) / 2 + Math.abs(y2 - y1) / 2);
        int dx = x2 - x1, dy = y2 - y1;
        int cx1 = x1 + dx / 3, cy1 = y1;
        int cx2 = x2 - dx / 3, cy2 = y2;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int px, py;
            if (spline) {
                float u = 1 - t;
                px = (int) (u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2);
                py = (int) (u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2);
            } else {
                px = x1 + (int) (t * dx);
                py = y1 + (int) (t * dy);
            }
            int rgb = col & 0x00FFFFFF;
            switch (lineVisual) {
                case THIN -> g.fill(px, py, px + 1, py + 1, col);
                case BOLD -> {
                    g.fill(px - 2, py - 2, px + 3, py + 3, col);
                    g.fill(px - 3, py - 2, px - 2, py + 3, rgb | 0x33000000);
                    g.fill(px + 3, py - 2, px + 4, py + 3, rgb | 0x33000000);
                    g.fill(px - 2, py - 3, px + 3, py - 2, rgb | 0x33000000);
                    g.fill(px - 2, py + 3, px + 3, py + 4, rgb | 0x33000000);
                }
                case THICK -> {
                    g.fill(px - 4, py - 4, px + 5, py + 5, rgb | 0x1C000000);
                    g.fill(px - 3, py - 3, px + 4, py + 4, rgb | 0x55000000);
                    g.fill(px - 3, py - 3, px + 4, py + 4, col);
                    g.fill(px - 1, py - 1, px + 2, py + 2, rgb | 0xFF000000);
                }
                case WIDE -> {
                    g.fill(px - 6, py - 6, px + 7, py + 7, rgb | 0x0F000000);
                    g.fill(px - 5, py - 5, px + 6, py + 6, rgb | 0x1E000000);
                    g.fill(px - 4, py - 4, px + 5, py + 5, rgb | 0x32000000);
                    g.fill(px - 4, py - 4, px + 5, py + 5, col);
                    g.fill(px - 2, py - 2, px + 3, py + 3, rgb | 0xFF000000);
                }
                case GLOW -> {
                    g.fill(px - 3, py - 3, px + 4, py + 4, rgb | 0x44000000);
                    g.fill(px - 2, py - 2, px + 3, py + 3, rgb | 0xAA000000);
                    g.fill(px - 1, py - 1, px + 2, py + 2, col);
                }
                default -> {  // NORMAL
                    g.fill(px - 1, py - 1, px + 2, py + 2, col);
                    g.fill(px - 2, py - 1, px - 1, py + 2, rgb | 0x33000000);
                    g.fill(px + 2, py - 1, px + 3, py + 2, rgb | 0x33000000);
                }
            }
        }
    }

    // ── Cycle row ──────────────────────────────────────────────────────────────

    private int renderCycleRow(GuiGraphics g, int x, int y, int w, String label, String value, int mx, int my) {
        int textY = y + (ROW_H - 8) / 2;
        int rArrowX = x + w - ARROW_W;
        int lArrowX = rArrowX - 2 - ARROW_W;
        boolean leftH = mx >= lArrowX && mx < lArrowX + ARROW_W && my >= y && my < y + ROW_H;
        boolean rightH = mx >= rArrowX && mx < rArrowX + ARROW_W && my >= y && my < y + ROW_H;

        if (leftH) g.fill(lArrowX, y, lArrowX + ARROW_W, y + ROW_H, 0x33FFFFFF);
        if (rightH) g.fill(rArrowX, y, rArrowX + ARROW_W, y + ROW_H, 0x33FFFFFF);
        g.drawCenteredString(font, "§7<", lArrowX + ARROW_W / 2, textY, leftH ? C_ACCENT : C_TEXT_FAINT);
        g.drawCenteredString(font, "§7>", rArrowX + ARROW_W / 2, textY, rightH ? C_ACCENT : C_TEXT_FAINT);

        g.drawString(font, label, x + 4, textY, C_TEXT, false);
        int valX = lArrowX - 6 - font.width(value);
        g.drawString(font, "§7" + value, valX, textY, C_TEXT_DIM, false);

        return y + ROW_H;
    }

    private static String minCountStepLabel(Integer v) {
        if (v == null) return "No default";
        if (v == -1) return "-1 (none needed)";
        if (v == 0) return "0 (all optional)";
        return String.valueOf(v);
    }

    private static int minCountStepIndex(Integer v) {
        for (int i = 0; i < MIN_COUNT_STEPS.length; i++) {
            if (java.util.Objects.equals(MIN_COUNT_STEPS[i], v)) return i;
        }
        return 0;
    }

    /**
     * Quests shown in the per-quest list. When focused on a specific quest, this is just that
     * quest plus quests that depend on it (i.e. have it as a prerequisite) — not the whole
     * category — since that's the only info relevant to the edge/quest the user right-clicked.
     */
    private List<QuestNode> questList() {
        if (focusNodeId != null) {
            List<QuestNode> result = new java.util.ArrayList<>();
            QuestNode fn = focusNode();
            // The focus quest's own row always shows regardless of the filter - it's not one of
            // the "dependents" being searched, it's the anchor everything else is relative to.
            if (fn != null) result.add(fn);
            QuestTreeRegistry.getAllQuests().values().stream()
                    .filter(n -> n.getPrerequisites().stream().anyMatch(p -> p.getId().equals(focusNodeId)))
                    .filter(n -> searchQuery.isEmpty() ||
                            n.getTitle().getString().toLowerCase().contains(searchQuery) ||
                            n.getId().getPath().toLowerCase().contains(searchQuery))
                    .sorted(Comparator.comparing(n -> n.getTitle().getString()))
                    .forEach(result::add);
            return result;
        }
        return QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> category.equals(n.getCategory()))
                .filter(n -> searchQuery.isEmpty() || n.getTitle().getString().toLowerCase().contains(searchQuery) ||
                        n.getId().getPath().toLowerCase().contains(searchQuery))
                .sorted(Comparator.comparing(n -> n.getTitle().getString()))
                .toList();
    }

    // ── Per-quest line-style controls ──────────────────────────────────────────

    /**
     * The edge(s) a per-quest row's Shape/Style/Speed controls apply to. When scoped to a
     * specific focus quest, a dependent row has exactly one relevant edge (focusNode -> row);
     * the focus quest's own row (or any row in the unscoped whole-category view) instead
     * applies uniformly across ALL of that quest's own prerequisite edges, since there's no
     * single edge to disambiguate.
     */
    private List<net.minecraft.resources.ResourceLocation> edgeParentsFor(QuestNode row) {
        if (focusNodeId != null && !row.getId().equals(focusNodeId)) {
            return List.of(focusNodeId);
        }
        return row.getPrerequisites().stream().map(QuestNode::getId).toList();
    }

    private String rowShapeLabel(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return "—";
        LineStyle first = row.getPrereqLineShape(parents.get(0));
        for (net.minecraft.resources.ResourceLocation p : parents)
            if (row.getPrereqLineShape(p) != first) return "Mixed";
        return first == null ? "Inherit" : abbreviate(first.name());
    }

    private String rowVisualLabel(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return "—";
        LineVisualStyle first = row.getPrereqLineVisual(parents.get(0));
        for (net.minecraft.resources.ResourceLocation p : parents)
            if (row.getPrereqLineVisual(p) != first) return "Mixed";
        return first == null ? "Inherit" : abbreviate(first.name());
    }

    private String rowSpeedLabel(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return "—";
        QuestChroniclesSettings.LineAnimSpeed first = row.getPrereqLineSpeed(parents.get(0));
        for (net.minecraft.resources.ResourceLocation p : parents)
            if (row.getPrereqLineSpeed(p) != first) return "Mixed";
        return first == null ? "Inherit" : abbreviate(first.name());
    }

    private static String abbreviate(String enumName) {
        return switch (enumName) {
            case "SPLINE" -> "Spln";
            case "STRAIGHT" -> "Strt";
            case "THIN" -> "Thin";
            case "NORMAL" -> "Norm";
            case "BOLD" -> "Bold";
            case "THICK" -> "Thck";
            case "WIDE" -> "Wide";
            case "GLOW" -> "Glow";
            case "SLOWEST" -> "VSlow";
            case "SLOW" -> "Slow";
            case "FAST" -> "Fast";
            case "VERY_FAST" -> "VFast";
            default -> enumName;
        };
    }

    private void drawMiniButton(GuiGraphics g, int bx, int by, int bw, String label, String value,
                                boolean enabled, boolean hov) {
        int bh = ROW_H - 4;
        g.fill(bx, by, bx + bw, by + bh, !enabled ? 0x08FFFFFF : hov ? 0x33FFFFFF : 0x11FFFFFF);
        int col = !enabled ? C_TEXT_FAINT : hov ? C_ACCENT : C_TEXT_DIM;
        g.drawCenteredString(font, "§8" + value, bx + bw / 2, by + 2, col);
    }

    private void cycleRowShape(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return;

        LineStyle[] vals = LineStyle.values();
        LineStyle current = row.getPrereqLineShape(parents.get(0));
        LineStyle next = current == null ? vals[0] :
                (current.ordinal() + 1 < vals.length ? vals[current.ordinal() + 1] : null);

        for (net.minecraft.resources.ResourceLocation p : parents) {
            row.setPrereqLineShape(p, next);
        }

        // FIX: Use your dedicated QuestFileSaver class method!
        QuestFileSaver.updateNodePrerequisites(row);

        if (this.parent != null) {
            this.parent.rebuildFromExternal();
        }
    }

    private void cycleRowVisual(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return;

        LineVisualStyle[] vals = LineVisualStyle.values();
        LineVisualStyle current = row.getPrereqLineVisual(parents.get(0));
        LineVisualStyle next = current == null ? vals[0] :
                (current.ordinal() + 1 < vals.length ? vals[current.ordinal() + 1] : null);

        for (net.minecraft.resources.ResourceLocation p : parents) {
            row.setPrereqLineVisual(p, next);
        }

        // FIX: Use your dedicated QuestFileSaver class method!
        QuestFileSaver.updateNodePrerequisites(row);

        if (this.parent != null) {
            this.parent.rebuildFromExternal();
        }
    }

    private void cycleRowSpeed(QuestNode row) {
        List<net.minecraft.resources.ResourceLocation> parents = edgeParentsFor(row);
        if (parents.isEmpty()) return;

        QuestChroniclesSettings.LineAnimSpeed[] vals = QuestChroniclesSettings.LineAnimSpeed.values();
        QuestChroniclesSettings.LineAnimSpeed current = row.getPrereqLineSpeed(parents.get(0));
        QuestChroniclesSettings.LineAnimSpeed next = current == null ? vals[0] :
                (current.ordinal() + 1 < vals.length ? vals[current.ordinal() + 1] : null);

        for (net.minecraft.resources.ResourceLocation p : parents) {
            row.setPrereqLineSpeed(p, next);
        }

        // FIX: Use your dedicated QuestFileSaver class method!
        QuestFileSaver.updateNodePrerequisites(row);

        if (this.parent != null) {
            this.parent.rebuildFromExternal();
        }
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int footerY = height - FOOTER_H;
        int fbtnW = 80, fbtnGap = 8, fbtnY = footerY + 5;
        int saveX = width / 2 - fbtnW - fbtnGap / 2;
        int closeX = width / 2 + fbtnGap / 2;

        if (mx >= saveX && mx < saveX + fbtnW && my >= fbtnY && my < fbtnY + 18) {
            saveGlobal();
            return true;
        }
        if (mx >= closeX && mx < closeX + fbtnW && my >= fbtnY && my < fbtnY + 18) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        int contentTop = HEADER_H + SEARCH_H + MARGIN;
        int contentH = height - HEADER_H - SEARCH_H - MARGIN - FOOTER_H - MARGIN;
        if (my < contentTop || my >= contentTop + contentH) return super.mouseClicked(mx, my, btn);

        int x = MARGIN, w = width - MARGIN * 3 - LEGEND_W;
        int y = contentTop - scrollY;

        if (focusNodeId == null) {
            // Skip global label
            y += GLOBAL_SECTION_LABEL_H;

            // Line Shape
            if (hitArrows(x, y, w, mx, my)) {
                LineStyle[] vals = LineStyle.values();
                lineShape = vals[(lineShape.ordinal() + (isRight(x, w, mx) ? 1 : -1) + vals.length) % vals.length];
                return true;
            }
            y += ROW_H + ROW_GAP;

            // Line Style
            if (hitArrows(x, y, w, mx, my)) {
                LineVisualStyle[] vals = LineVisualStyle.values();
                lineVisual = vals[(lineVisual.ordinal() + (isRight(x, w, mx) ? 1 : -1) + vals.length) % vals.length];
                return true;
            }
            y += ROW_H + ROW_GAP;

            // Dot Speed
            if (hitArrows(x, y, w, mx, my)) {
                QuestChroniclesSettings.LineAnimSpeed[] vals = QuestChroniclesSettings.LineAnimSpeed.values();
                lineAnimSpeed = vals[(lineAnimSpeed.ordinal() + (isRight(x, w, mx) ? 1 : -1) + vals.length) %
                        vals.length];
                return true;
            }
            y += ROW_H + ROW_GAP;

            // Directional Arrows
            if (hitArrows(x, y, w, mx, my)) {
                lineArrows = !lineArrows;
                return true;
            }
            y += ROW_H + ROW_GAP;

            // Skip preview box
            y += 4 + PREVIEW_H + PREVIEW_GAP;

            // Category defaults: skip divider + label
            y += DIVIDER_H + CATEGORY_LABEL_H;

            // Prereq gate default
            if (hitArrows(x, y, w, mx, my)) {
                // 3-way cycle: no-default → ALL → ANY → no-default
                if (catRequireAllDefault == null) catRequireAllDefault = true;
                else if (catRequireAllDefault) catRequireAllDefault = false;
                else catRequireAllDefault = null;
                return true;
            }
            y += ROW_H + ROW_GAP;

            // Optional prereq min-count default
            if (hitArrows(x, y, w, mx, my)) {
                int idx = minCountStepIndex(catOptionalMinDefault);
                idx = (idx + (isRight(x, w, mx) ? 1 : -1) + MIN_COUNT_STEPS.length) % MIN_COUNT_STEPS.length;
                catOptionalMinDefault = MIN_COUNT_STEPS[idx];
                return true;
            }
            y += ROW_H + ROW_GAP;

            y += DIVIDER_H;
        }

        // Skip per-quest label
        y += PER_QUEST_LABEL_H;

        // Per-quest toggle buttons (same list as render)
        List<QuestNode> quests = questList();

        for (QuestNode quest : quests) {
            int hideW = 60, miniW = 44, miniGap = 3;
            int hideX = x + w - hideW - 2;
            int speedX = hideX - miniGap - miniW;
            int styleX = speedX - miniGap - miniW;
            int shapeX = styleX - miniGap - miniW;
            int btnY = y + 2;
            boolean hasEdges = !edgeParentsFor(quest).isEmpty();

            if (mx >= hideX && mx < hideX + hideW && my >= btnY && my < btnY + ROW_H - 4) {
                quest.setHideDepLine(!quest.isHideDepLine());

                // FIXED: Using localized file writer helper API method
                QuestFileSaver.updateHideDepLine(quest);

                parent.rebuildFromExternal();
                return true;
            }
            if (hasEdges && mx >= shapeX && mx < shapeX + miniW && my >= btnY && my < btnY + ROW_H - 4) {
                cycleRowShape(quest);
                return true;
            }
            if (hasEdges && mx >= styleX && mx < styleX + miniW && my >= btnY && my < btnY + ROW_H - 4) {
                cycleRowVisual(quest);
                return true;
            }
            if (hasEdges && mx >= speedX && mx < speedX + miniW && my >= btnY && my < btnY + ROW_H - 4) {
                cycleRowSpeed(quest);
                return true;
            }
            y += ROW_H + ROW_GAP;
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void saveGlobal() {
        if (focusNodeId == null) {
            QuestChroniclesSettings s = QuestChroniclesSettings.get();
            s.setLineStyle(lineShape);
            s.setLineVisualStyle(lineVisual);
            s.setLineAnimSpeed(lineAnimSpeed);
            s.setShowLineArrows(lineArrows);
            s.save();
            saveCategoryPrereqDefaults();
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /**
     * Writes this category's prereq-gate defaults into category_prereq_defaults.snbt,
     * preserving every other category's entry, then reloads CategoryPrereqDefaults so the
     * change takes effect immediately (integrated singleplayer server shares this JVM/registry).
     */
    private void saveCategoryPrereqDefaults() {
        Path configDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
        Path file = configDir.resolve("category_prereq_defaults.snbt");
        try {
            net.minecraft.nbt.CompoundTag root;
            if (Files.exists(file)) {
                root = net.minecraft.nbt.TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
            } else {
                root = new net.minecraft.nbt.CompoundTag();
            }

            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            if (catRequireAllDefault != null) entry.putBoolean("require_all", catRequireAllDefault);
            if (catOptionalMinDefault != null) entry.putInt("optional_min_count", catOptionalMinDefault);

            if (entry.isEmpty()) root.remove(category);
            else root.put(category, entry);

            Files.createDirectories(configDir);
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);

            CategoryPrereqDefaults.load(configDir);
            parent.rebuildFromExternal();
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save category_prereq_defaults.snbt: " + e.getMessage());
        }
    }

    private boolean hitArrows(int x, int y, int w, double mx, double my) {
        int rArrowX = x + w - ARROW_W;
        int lArrowX = rArrowX - 2 - ARROW_W;
        return my >= y && my < y + ROW_H && mx >= lArrowX;
    }

    private boolean isRight(int x, int w, double mx) {
        return mx >= x + w - ARROW_W;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollY = Math.max(0, (int) (scrollY - delta * 12));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {  // ESC
            if (searchBox != null && searchBox.isFocused() && !searchQuery.isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
