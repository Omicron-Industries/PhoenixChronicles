package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemeRenderer;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * In-game developer wiki / reference panel.
 * Opened via the ? button in the toolbar (dev mode) or pressing ? on the keyboard.
 *
 * Each page is a list of WLine records rendered top-to-bottom. Some pages pull live
 * data from the registries so values are always current.
 */
public class DevWikiScreen extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_DONE, C_ACTIVE;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int SIDEBAR_W = 96;
    private static final int MARGIN = 10;
    private static final int LINE_H = 12;

    // ── Pages ─────────────────────────────────────────────────────────────────
    private static final String[] PAGE_NAMES = {
            "Overview", "Canvas", "Quest Fields", "Tasks", "Rewards", "Rich Text", "SNBT Format", "Live Stats",
            "API Reference", "Customization"
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private int activePage = 0;
    private int scrollY = 0;
    private int cachedContentH = 0; // updated each render frame for scroll clamping

    // ── Copy-button tracking (rebuilt each frame) ─────────────────────────────
    private final List<int[]> copyBtnBounds = new ArrayList<>(); // {x1,y1,x2,y2}
    private final List<String> copyBtnTexts = new ArrayList<>();

    // ── Content line model ────────────────────────────────────────────────────
    private enum LT {
        HEADING,
        SUBHEADING,
        KV,
        TEXT,
        INDENT,
        DIVIDER,
        SPACER,
        CODE
    }

    private record WLine(LT type, String a, String b) {

        static WLine h(String s) {
            return new WLine(LT.HEADING, s, "");
        }

        static WLine sh(String s) {
            return new WLine(LT.SUBHEADING, s, "");
        }

        static WLine kv(String k, String v) {
            return new WLine(LT.KV, k, v);
        }

        static WLine t(String s) {
            return new WLine(LT.TEXT, s, "");
        }

        static WLine in(String s) {
            return new WLine(LT.INDENT, s, "");
        }

        static WLine div() {
            return new WLine(LT.DIVIDER, "", "");
        }

        static WLine sp() {
            return new WLine(LT.SPACER, "", "");
        }

        /** Monospace-style code line with a copy button. */
        static WLine code(String s) {
            return new WLine(LT.CODE, s, "");
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public DevWikiScreen(Screen parent) {
        super(Component.literal("Dev Wiki"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
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

        clearWidgets();

        // Sidebar page list is hand-drawn in render()/sidebarRowAt() now, not vanilla Button
        // widgets - vanilla's gray 9-slice background plus its own text drop-shadow made the §8
        // (dark_gray) unselected label color read as barely-legible shadowy text against a
        // similarly dark, busy background. Hand-drawn rows use this screen's actual theme colors
        // (C_TEXT_DIM, which is tuned for exactly this "readable but secondary" role elsewhere in
        // this mod) instead.

        // Close button
        addRenderableWidget(Button.builder(Component.literal("§7✕ Close"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(width / 2 - 36, height - FOOTER_H + 6, 72, 16).build());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        // Delegated Design Components via central design framework
        ChroniclesTheme theme = ChroniclesTheme.current();
        ChroniclesThemeRenderer.drawHeader(g, width, HEADER_H, "§fDev Wiki  §8— §7" + PAGE_NAMES[activePage], theme);

        // Sidebar rendering configurations
        int sidebarClipBot = height - FOOTER_H;
        g.enableScissor(0, HEADER_H, SIDEBAR_W, sidebarClipBot);
        g.fill(0, HEADER_H, SIDEBAR_W, sidebarClipBot, C_PANEL);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int rowY = HEADER_H + 8 + i * 16;
            if (rowY + 14 > sidebarClipBot) break; // clamp to footer, same as the old button loop
            boolean sel = i == activePage;
            boolean hov = mx >= 0 && mx < SIDEBAR_W && my >= rowY - 1 && my < rowY + 15;
            if (sel) {
                g.fill(0, rowY - 1, SIDEBAR_W, rowY + 15, 0x22FFFFFF);
                g.fill(0, rowY - 1, 2, rowY + 15, C_ACCENT);
            } else if (hov) {
                g.fill(0, rowY - 1, SIDEBAR_W, rowY + 15, 0x14FFFFFF);
            }
            int textCol = sel ? C_TEXT : (hov ? C_TEXT : C_TEXT_DIM);
            g.drawString(font, PAGE_NAMES[i], 8, rowY + 3, textCol, false);
        }
        g.disableScissor();
        g.fill(SIDEBAR_W - 1, HEADER_H, SIDEBAR_W, height, C_BORDER);

        // Delegated Footer Construction
        ChroniclesThemeRenderer.drawFooter(g, width, height, FOOTER_H, C_BORDER, C_HEADER);

        // Scissored Content Workspace
        int cx = SIDEBAR_W + MARGIN;
        int cw = width - cx - MARGIN;
        int contentTop = HEADER_H + MARGIN;
        int contentBot = height - FOOTER_H - MARGIN;

        g.enableScissor(cx, contentTop, cx + cw, contentBot);
        copyBtnBounds.clear();
        copyBtnTexts.clear();

        List<WLine> lines = buildPage(activePage);
        int y = contentTop - scrollY;
        for (WLine line : lines) {
            y = renderLine(g, line, cx, y, cw, contentTop, contentBot, mx, my);
        }
        g.disableScissor();

        // Delegated Scroll Track Painter
        int totalH = lines.stream().mapToInt(this::lineHeight).sum();
        cachedContentH = totalH;
        ChroniclesThemeRenderer.drawScrollbar(g, width - MARGIN / 2, contentTop, contentBot, scrollY, totalH);

        super.render(g, mx, my, partial);
    }

    private int renderLine(GuiGraphics g, WLine line, int x, int y, int w, int top, int bot, int mx, int my) {
        // 1. Off-screen structural skipping optimization
        if (y > bot) return y + lineHeight(line);

        switch (line.type()) {
            case HEADING -> {
                if (y + LINE_H >= top) {
                    ChroniclesThemeRenderer.drawHeading(g, font, line.a(), x, y, w, C_ACCENT, C_TEXT);
                }
                return y + LINE_H + 6;
            }
            case SUBHEADING -> {
                if (y + LINE_H >= top) {
                    ChroniclesThemeRenderer.drawSubheading(g, font, line.a(), x, y, C_TEXT_DIM);
                }
                return y + LINE_H + 4;
            }
            case KV -> {
                if (y + LINE_H >= top) {
                    ChroniclesThemeRenderer.drawKeyValue(g, font, line.a(), line.b(), x, y, w, C_ACCENT, C_TEXT_DIM);
                }
                return y + LINE_H + 1;
            }
            case TEXT -> {
                if (y + LINE_H >= top) {
                    ChroniclesThemeRenderer.drawText(g, font, line.a(), x, y, C_TEXT_DIM);
                }
                return y + LINE_H + 1;
            }
            case INDENT -> {
                if (y + LINE_H >= top) {
                    ChroniclesThemeRenderer.drawBulletPoint(g, font, line.a(), x, y, C_TEXT_DIM);
                }
                return y + LINE_H + 1;
            }
            case DIVIDER -> {
                if (y + 1 >= top) {
                    ChroniclesThemeRenderer.drawDivider(g, x, y, w, C_BORDER);
                }
                return y + 8;
            }
            case SPACER -> {
                return y + 6;
            }
            case CODE -> {
                int lh = LINE_H + 4;
                if (y + lh >= top) {
                    int btnW = font.width("⎘") + 8;
                    int btnX = x + w - btnW - 2;
                    int btnY2 = y + 1;
                    int btnH2 = lh - 2;
                    boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY2 && my < btnY2 + btnH2;

                    // Centralized Monospace Code block painter processing
                    ChroniclesThemeRenderer.drawCodeBlock(g, font, line.a(), x, y, w, lh, mx, my, C_BORDER, C_ACCENT,
                            hov);

                    // Register standard interaction boundary data mirrors back onto screen fields
                    copyBtnBounds.add(new int[] { btnX, btnY2, btnX + btnW, btnY2 + btnH2 });
                    copyBtnTexts.add(line.a());
                }
                return y + lh + 1;
            }
        }
        return y + LINE_H;
    }

    private int lineHeight(WLine line) {
        return switch (line.type()) {
            case HEADING -> LINE_H + 6;
            case SUBHEADING -> LINE_H + 4;
            case KV, TEXT, INDENT -> LINE_H + 1;
            case DIVIDER -> 8;
            case SPACER -> 6;
            case CODE -> LINE_H + 5;
        };
    }

    // ── Page builders ─────────────────────────────────────────────────────────

    private List<WLine> buildPage(int page) {
        return switch (page) {
            case 0 -> pageOverview();
            case 1 -> pageCanvas();
            case 2 -> pageQuestFields();
            case 3 -> pageTasks();
            case 4 -> pageRewards();
            case 5 -> pageRichText();
            case 6 -> pageSnbtFormat();
            case 7 -> pageLiveStats();
            case 8 -> pageApiReference();
            case 9 -> pageCustomization();
            default -> List.of();
        };
    }

    private List<WLine> pageOverview() {
        int total = QuestTreeRegistry.getAllQuests().size();
        int cats = QuestTreeRegistry.getRootChapters().values().stream()
                .map(QuestNode::getCategory).distinct().mapToInt(c -> 1).sum();
        // count more accurately
        Set<String> catSet = new HashSet<>();
        QuestTreeRegistry.getAllQuests().values().forEach(n -> catSet.add(n.getCategory()));

        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Phoenix Chronicles — Dev Reference"));
        lines.add(WLine.t("In-game quest system for Minecraft Forge 1.20.1."));
        lines.add(WLine.t("Dev mode activates automatically in Creative or at op level ≥ 2."));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Live registry"));
        lines.add(WLine.kv("Quests loaded:", String.valueOf(total)));
        lines.add(WLine.kv("Categories:", String.valueOf(catSet.size())));
        lines.add(WLine.kv("Task types:", String.valueOf(PhoenixTaskRegistry.getEditorTypes().size()) + " registered"));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh("Quick navigation"));
        lines.add(WLine.kv("Canvas", "Pan, zoom, right-click, Alt+drag dep lines"));
        lines.add(WLine.kv("Quest Fields", "All SNBT keys and their meanings"));
        lines.add(WLine.kv("Tasks", "Every task type with expected fields"));
        lines.add(WLine.kv("Rewards", "All reward types — including choice rewards"));
        lines.add(WLine.kv("Rich Text", "{#RRGGBB} colour, [img:…] inline textures, [links]"));
        lines.add(WLine.kv("SNBT Format", "Full file format reference and folder layout"));
        lines.add(WLine.kv("Live Stats", "Per-category quest counts and type breakdown"));
        lines.add(WLine.kv("Customization", "Sidebar, chapter theme, background pictures, toasts"));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh("Tutorial quests"));
        lines.add(WLine.t("Add tutorial_steps to any quest SNBT to attach a guided overlay."));
        lines.add(WLine.t("Each step has a text field and an optional highlight target:"));
        lines.add(WLine.in("none, sidebar, canvas, toolbar, node:{quest_id}"));
        lines.add(WLine.t("Progress is stored client-side in config/phoenix_chronicles/tutorial_progress.dat."));
        return lines;
    }

    private List<WLine> pageCanvas() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Canvas Controls"));
        lines.add(WLine.sh("Mouse"));
        lines.add(WLine.kv("Left drag", "Pan the canvas"));
        lines.add(WLine.kv("Scroll wheel", "Zoom in / out"));
        lines.add(WLine.kv("Left click node", "Select / open quest detail"));
        lines.add(WLine.kv("Right click node",
                "Context menu: Edit, Tasks/Rewards, Texts, Design toast…, Delete, Move category…"));
        lines.add(WLine.kv("Right click canvas",
                "Context menu: Add quest, Add group, Chapter theme…, Add picture…, dep-line settings"));
        lines.add(
                WLine.kv("Right click picture", "Its own menu: Resize, Resize (scroll+drag)…, Move category, Delete"));
        lines.add(WLine.kv("Shift + drag", "Move a node, group, or background picture (dev mode)"));
        lines.add(WLine.kv("Alt + drag node", "Draw dependency line to another node"));
        lines.add(WLine.kv("Middle click", "Reset pan offset"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Keyboard"));
        lines.add(WLine.kv("F", "Fit all nodes to view"));
        lines.add(WLine.kv("Ctrl+F", "Open quest search overlay"));
        lines.add(WLine.kv("Ctrl+Z", "Undo last node move or dep-line change"));
        lines.add(WLine.kv("Ctrl+Y", "Redo"));
        lines.add(WLine.kv("L", "Toggle line style (Spline ↔ Straight)"));
        lines.add(WLine.kv("V", "Toggle validation panel (dev)"));
        lines.add(WLine.kv("I", "Run FTB import (dev)"));
        lines.add(WLine.kv("?", "Open this wiki"));
        lines.add(WLine.kv("ESC", "Close menus / deselect"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Multi-select (dev mode)"));
        lines.add(WLine.kv("Shift+click", "Add node to selection"));
        lines.add(WLine.kv("Ctrl+drag", "Box-select nodes"));
        lines.add(WLine.kv("Ctrl+G", "Group selected nodes"));
        lines.add(WLine.kv("Del", "Delete selected nodes"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Zoom"));
        lines.add(WLine.kv("Range", "35% – 250%  (scroll or pinch)"));
        lines.add(WLine.kv("Step", "12% per scroll tick"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Grid snapping  (node placement)"));
        lines.add(WLine.t("A grid-size pill in the title bar (left of the zoom %) controls snap size."));
        lines.add(WLine.kv("Click pill", "Cycle: 1 → 4 → 8 → 16 → 32 → 1"));
        lines.add(WLine.kv("1 (free)", "Pixel-perfect — no snapping, any position"));
        lines.add(WLine.kv("4 / 8 / 16 / 32", "Snap to that many logical-unit grid squares"));
        lines.add(WLine.kv("Shift-drag", "Always bypasses snapping regardless of pill setting"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Dev tools  (right-click empty canvas, dev mode only)"));
        lines.add(WLine.t("Dev-only controls live in the right-click context menu, not the toolbar."));
        lines.add(WLine.kv("Test mode", "Simulate player state — see quests as a normal player would"));
        lines.add(WLine.kv("↺ Reset", "Visible in Test mode only — clears simulated progress"));
        lines.add(WLine.kv("Subgraph", "Highlight the selected node's transitive dependency tree"));
        lines.add(WLine.kv("Stats", "Overlay a small stats card (quest counts, load errors)"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Toolbar (always visible in dev mode)"));
        lines.add(WLine.t("Toolbar holds: category selector, filter pills, zoom pill, ? wiki button."));
        lines.add(WLine.t("Dev toggles and destructive actions are in the right-click menu to avoid"));
        lines.add(WLine.t("toolbar overflow at smaller window sizes."));
        return lines;
    }

    private List<WLine> pageCustomization() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Customization"));
        lines.add(WLine.t("Everything below is dev-mode-only pack authoring - players never see any of"));
        lines.add(WLine.t("these editors, only their results."));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Sidebar & Questbook Title"));
        lines.add(WLine.kv("Category tile", "Left-click select, right-click → chapter theme editor"));
        lines.add(WLine.kv("Collapse toggle", "Small arrow above the category list - reclaims canvas width"));
        lines.add(WLine.kv("Questbook icon/name", "Top-left of the sidebar - click to open the naming popup"));
        lines.add(WLine.t("Book icon + name default to a generic \"Quest Book\" until set."));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh("Chapter theme  (right-click canvas → Edit chapter theme…)"));
        lines.add(WLine.kv("Display name", "Raw override; empty = derived from the category slug"));
        lines.add(WLine.kv("Icon", "Sidebar tile icon for this chapter"));
        lines.add(WLine.kv("Style", "DOT_GRID / GRID_LINES / HEX_GRID / DIAGONAL_LINES / SOLID / CUSTOM"));
        lines.add(WLine.kv("Color tint", "#RRGGBB overlay on the canvas background"));
        lines.add(WLine.t("Picking a texture (Browse…) auto-switches Style to CUSTOM - the two used to"));
        lines.add(WLine.t("be independent, so choosing a texture silently did nothing until Style was"));
        lines.add(WLine.t("also changed by hand. That's fixed; a texture pick sets both now."));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Custom textures  (Browse… button, or Add picture…)"));
        lines.add(WLine.t("Drop PNGs in config/phoenix_chronicles/textures/ - the browser lists them as"));
        lines.add(WLine.t("phoenixcore:textures/custom/<relative-path>. That location was never a real"));
        lines.add(WLine.t("game asset path (Minecraft only loads assets/<namespace>/... from a jar or"));
        lines.add(WLine.t("resource pack), so these are dynamically registered at runtime the first time"));
        lines.add(WLine.t("they're drawn (see CustomTextureCache) instead of needing a resource pack."));
        lines.add(WLine.kv("Left-click a thumbnail", "Select and apply"));
        lines.add(WLine.kv("Right-click a thumbnail", "Copy its resource location to clipboard"));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh("Background pictures  (right-click canvas → Add picture…)"));
        lines.add(WLine.t("Freestanding decorative images, separate from the chapter theme above -"));
        lines.add(WLine.t("positioned in canvas space, so they pan/zoom with the graph like nodes do."));
        lines.add(WLine.kv("Add", "Right-click empty canvas → Add picture… → pick a texture"));
        lines.add(WLine.kv("Move", "Shift+drag the picture directly"));
        lines.add(WLine.kv("Right-click a picture",
                "Move / Resize ▸ / Resize (scroll+drag)… / Move to category ▸ / Delete"));
        lines.add(WLine.kv("Resize ▸", "Fixed presets: 32 / 64 / 128 / 256 / 512 / 1024 px"));
        lines.add(WLine.kv("Resize (scroll+drag)…", "Interactive mode - bypasses canvas zoom/pan entirely"));
        lines.add(WLine.in("Scroll = resize (±20%, shift = fine ±5%), drag = move, right-click/Esc = done"));
        lines.add(WLine.t("Every add/move/resize/category-move/delete is one Ctrl+Z-undoable step, even"));
        lines.add(WLine.t("a whole interactive resize session (one entry covers the full edit)."));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh("Dependency lines  (right-click canvas → Dependency lines…, or per-quest)"));
        lines.add(WLine.kv("Line Style", "THIN / NORMAL / BOLD / THICK / WIDE / GLOW - controls rail width"));
        lines.add(WLine.kv("Line Anim Speed", "How fast the arrow chain travels on hover"));
        lines.add(WLine.t("Base rail/arrow colors (locked/active/done) come from the active theme's"));
        lines.add(WLine.t("locked/activeColor/done colors - editing the theme recolors dependency lines"));
        lines.add(WLine.t("along with everything else. The hover-boost colors (cyan for what a quest"));
        lines.add(WLine.t("needs, amber for what it unlocks) are intentionally fixed, not themed."));
        lines.add(WLine.t("Lines are static until you hover a connected quest; only that quest's own"));
        lines.add(WLine.t("edges animate, to avoid the whole tree moving at once."));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh("Quest toasts  (Settings screen, or right-click a quest → Design toast…)"));
        lines.add(WLine.kv("Toast Style", "COMPACT (corner banner) / ABOVE_HOTBAR / BIG_CENTER - global default"));
        lines.add(WLine.kv("Toast Position", "Which corner, for the COMPACT style"));
        lines.add(WLine.t("Any quest without its own design uses whichever Toast Style is selected."));
        lines.add(WLine.kv("Design toast…", "Per-quest custom layout - freeform icon/title/label position+color"));
        lines.add(WLine.t("Drag the icon/title/label directly in the live preview; side panel edits"));
        lines.add(WLine.t("scale, color, and bold for whichever element is selected."));
        lines.add(WLine.kv("Reset to default style", "Deletes the quest's custom design (only shown once one exists)"));
        return lines;
    }

    private List<WLine> pageQuestFields() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Quest SNBT Fields"));
        lines.add(WLine.t("All fields are optional except id and title."));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Identity"));
        lines.add(WLine.kv("id", "Path portion only — e.g. \"my_quest\" → phoenixcore:my_quest"));
        lines.add(WLine.kv("title", "Display name shown in quest headers and search"));
        lines.add(WLine.kv("description", "Lore / body text"));
        lines.add(WLine.kv("subtitle", "Smaller text below the title on the detail card"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Appearance"));
        lines.add(WLine.kv("category", "Chapter tab name — e.g. MAIN, MAGIC, COMBAT"));
        lines.add(
                WLine.kv("shape", "SQUARE · CIRCLE · DIAMOND · HEXAGON · TRIANGLE · STAR · PENTAGON · SHIELD · CROSS"));
        lines.add(WLine.kv("node_size", "SMALL · NORMAL (default) · LARGE — canvas icon size"));
        lines.add(WLine.kv("icon_item", "Item id for the node icon — e.g. minecraft:diamond"));
        lines.add(WLine.kv("positionX", "Canvas X coordinate (pixels from left edge)"));
        lines.add(WLine.kv("positionY", "Canvas Y coordinate (pixels from top edge)"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Visibility"));
        lines.add(WLine.kv("visibility", "NORMAL · HIDDEN · MYSTERY · DISABLED"));
        lines.add(WLine.kv("enable_if", "Flag expression — quest hidden+disabled when false"));
        lines.add(WLine.kv("hide_dep_line", "true / false — hides all dep lines on the canvas"));
        lines.add(WLine.kv("disabled_blocks_children", "true → DISABLED quest still gates children"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Completion"));
        lines.add(WLine.kv("task_min_count", "0 = all tasks required; N = complete any N tasks"));
        lines.add(WLine.kv("repeat_mode", "NONE · DAILY · COOLDOWN · INFINITE"));
        lines.add(WLine.kv("repeat_cooldown_hours", "Hours between repeats (COOLDOWN mode only)"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Prerequisites"));
        lines.add(WLine.kv("parent", "Single primary parent quest id path, or \"none\""));
        lines.add(WLine.kv("require_all_prereqs", "true = AND gate; false = OR gate (legacy)"));
        lines.add(WLine.kv("prerequisites", "List of {id, required, forbidden, link} tags"));
        lines.add(WLine.kv("optional_prereq_min_count", "Min optional prereqs needed (0 = all)"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Rewards (on the quest, not inside rewards list)"));
        lines.add(WLine.kv("reward_choice", "true → player picks N rewards instead of getting all"));
        lines.add(WLine.kv("reward_choice_count", "How many rewards the player may pick (default 1)"));
        lines.add(WLine.kv("auto_claim_rewards", "true → rewards are automatically given on completion"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Developer"));
        lines.add(WLine.kv("dev_notes", "Free-text notes visible only in the quest editor (not to players)"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Tutorial"));
        lines.add(WLine.kv("tutorial_steps", "List of {text, highlight} tags — see Overview page"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Multiplayer / Teams"));
        lines.add(WLine.kv("shared", "true → completing this quest cascades to all online teammates"));
        lines.add(WLine.t("Uses Minecraft's built-in scoreboard teams (/team add, /team join)."));
        lines.add(WLine.t("Task progress remains per-player; only the final COMPLETED state is shared."));
        return lines;
    }

    private List<WLine> pageTasks() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Task Types"));

        // Built-in types
        lines.add(WLine.sh("Built-in"));
        String[][] builtins = {
                { "kill_entity", "Kill mobs", "target: entity_id, count, consume" },
                { "item_check", "Have item(s) in inventory", "target: item_id, count, consume" },
                { "craft_item", "Craft an item", "target: item_id, count" },
                { "experience", "Reach an XP level", "count: level" },
                { "location_terminal", "Interact with a terminal", "target: terminal_id, consume" },
                { "advancement", "Earn an advancement", "target: advancement_id" },
                { "block_interact", "Place / right-click a block", "target: block_id, secondary: PLACE|RIGHT_CLICK" },
                { "fluid_check", "Have fluid in inventory", "target: fluid_id, count: mB, consume" },
                { "stat", "Reach a stat value", "target: stat_id (e.g. minecraft:jump), count" },
                { "dimension", "Enter a dimension", "secondary: dimension_id" },
                { "biome", "Visit a biome", "target: biome_id" },
                { "structure", "Enter a structure", "target: structure_id" },
                { "checkmark", "Manual checkbox", "(no fields)" },
                { "tag_item", "Have item matching tag", "target: tag (e.g. c:ores/iron), count" },
                { "info", "Display-only text panel", "target: body text" },
                { "external_trigger", "Fired by QuestAPI.fireExternalEvent()", "target: trigger_id, count: times" },
                { "energy_check", "Have stored energy",
                        "target: FE|EU|ANY, secondary: INVENTORY|HELD|BLOCK, count: FE" },
        };
        for (String[] row : builtins) {
            lines.add(WLine.kv(row[0], row[1]));
            lines.add(WLine.in(row[2]));
        }

        // KubeJS / registry extensions
        int kjsCount = PhoenixTaskRegistry.getEditorTypes().size() - builtins.length;
        if (kjsCount > 0) {
            lines.add(WLine.sp());
            lines.add(WLine.sh("KubeJS / mod-registered  (" + kjsCount + ")"));
            Set<String> builtinIds = new HashSet<>();
            for (String[] b : builtins) builtinIds.add(b[0]);
            for (PhoenixTaskRegistry.TaskEntry entry : PhoenixTaskRegistry.getEditorTypes()) {
                if (!builtinIds.contains(entry.typeId())) {
                    lines.add(WLine.kv(entry.typeId(),
                            entry.editorLabel() != null ? entry.editorLabel() : entry.typeId()));
                    if (entry.editorTooltip() != null)
                        lines.add(WLine.in(entry.editorTooltip().split("\n")[0]));
                }
            }
        }

        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh("SNBT task entry format"));
        lines.add(WLine.t("{type: \"kill_entity\", task_id: \"phoenixcore:task_…\", description: \"…\","));
        lines.add(WLine.t(" target: \"minecraft:zombie\", count: 5, consume: false, optional: false}"));
        return lines;
    }

    private List<WLine> pageRewards() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Reward Types"));
        lines.add(WLine.sp());
        lines.add(WLine.kv("item", "Give item(s) to the player"));
        lines.add(WLine.in("Fields: type, item (item_id), count"));
        lines.add(WLine.sp());
        lines.add(WLine.kv("xp", "Award experience levels"));
        lines.add(WLine.in("Fields: type, levels (integer)"));
        lines.add(WLine.sp());
        lines.add(WLine.kv("command", "Run a server command as console"));
        lines.add(WLine.in("Fields: type, command  (%player% replaced with player name)"));
        lines.add(WLine.sp());
        lines.add(WLine.kv("loot_table", "Roll a loot table, give all resulting items"));
        lines.add(WLine.in("Fields: type, loot_table (resource location)"));
        lines.add(WLine.sp());
        lines.add(WLine.kv("script_event", "Fire PhoenixQuestScriptRewardEvent on the Forge bus"));
        lines.add(WLine.in("Fields: type, event_id, data (optional CompoundTag NBT)"));
        lines.add(WLine.in("KubeJS: listen with ForgeEvents.onEvent('…ScriptRewardEvent', e => …)"));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh("Choice rewards"));
        lines.add(WLine.t("Set reward_choice: true on the quest to let players pick from the rewards list."));
        lines.add(WLine.t("reward_choice_count controls how many they may pick (default 1)."));
        lines.add(WLine.t("The reward screen shows all options; unchosen rewards are discarded."));
        lines.add(WLine.sp());
        lines.add(WLine.kv("Example (pick 1 of 3)", ""));
        lines.add(WLine.t("  reward_choice: true,  reward_choice_count: 1,"));
        lines.add(WLine.t("  rewards: ["));
        lines.add(WLine.t("    {type: \"item\", item: \"minecraft:diamond_sword\", count: 1},"));
        lines.add(WLine.t("    {type: \"item\", item: \"minecraft:diamond_pickaxe\", count: 1},"));
        lines.add(WLine.t("    {type: \"item\", item: \"minecraft:elytra\", count: 1}"));
        lines.add(WLine.t("  ]"));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh("SNBT reward entry format"));
        lines.add(WLine.t("{type: \"item\", item: \"minecraft:diamond\", count: 3}"));
        lines.add(WLine.t("{type: \"script_event\", event_id: \"my_event\", data: {key: 1}}"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Java event hook"));
        lines.add(WLine.t("@SubscribeEvent"));
        lines.add(WLine.t("public void onReward(PhoenixQuestScriptRewardEvent e) {"));
        lines.add(WLine.t("    e.getPlayer();  e.getEventId();  e.getData();"));
        lines.add(WLine.t("}"));
        return lines;
    }

    private List<WLine> pageRichText() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Rich Text in Descriptions"));
        lines.add(WLine.t("Both the SNBT description field and .md files support rich text tags."));
        lines.add(WLine.t("The parser handles { and [ only — & is never converted (unlike FTB Quests)."));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Colour"));
        lines.add(WLine.kv("{#RRGGBB}", "Set foreground colour — 6-digit hex, e.g. {#FF4444}"));
        lines.add(WLine.kv("{reset}", "Return to default text colour"));
        lines.add(WLine.in("Example:  {#FFD700}Golden text{reset} back to normal"));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Inline images"));
        lines.add(WLine.kv("[img:rl,w,h]", "Embed a texture inline with the text"));
        lines.add(WLine.t("  rl  = resource location, e.g. minecraft:textures/item/diamond.png"));
        lines.add(WLine.t("  w,h = pixel dimensions in GUI space (optional, default 16x16)"));
        lines.add(WLine.in("Example:  [img:minecraft:textures/item/diamond.png,16,16]"));
        lines.add(WLine.in("Example:  [img:mymod:textures/gui/banner.png,64,32]"));
        lines.add(WLine.sp());
        lines.add(WLine.t("Images fall back to the SNBT description field if no .md file is found,"));
        lines.add(WLine.t("so you can embed textures directly in the quest creator without a .md file."));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Links"));
        lines.add(WLine.kv("[label](url)", "Clickable hyperlink — opens in the system browser"));
        lines.add(WLine.kv("[label](tip:text)", "Tooltip-only reference — shows text on hover, no click"));
        lines.add(WLine.in("Example:  [Phoenix Wiki](https://example.com/wiki)"));
        lines.add(WLine.in("Example:  [Mana Crystal](tip:Dropped by Silverfish in End biomes)"));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Minecraft formatting codes"));
        lines.add(WLine.t("§ codes (§l bold, §c red, etc.) work normally in both SNBT and .md files."));
        lines.add(WLine.t("& is NOT processed — &wHelloooo passes through literally as written."));
        lines.add(WLine.t("This avoids conflicts with other mods that use & for their own purposes."));
        lines.add(WLine.sp());

        lines.add(WLine.sh("Markdown files (.md)"));
        lines.add(WLine.t("Place a file at:  config/phoenix_chronicles/quests/{quest_id}.md"));
        lines.add(WLine.t("The .md file content is shown in the fullscreen quest view. If absent,"));
        lines.add(WLine.t("the SNBT description field is used instead (including any rich text tags)."));
        lines.add(WLine.t("# Heading, ## Subheading, and --- divider are supported in .md files."));
        return lines;
    }

    private List<WLine> pageSnbtFormat() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("SNBT File Format"));
        lines.add(WLine.t("Each quest is a single .snbt file in config/phoenix_chronicles/quests/"));
        lines.add(WLine.t("The file name (without .snbt) becomes the quest id if no id field is present."));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Minimal quest"));
        lines.add(WLine.t("{id: \"my_quest\", title: \"My Quest\"}"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Full example"));
        lines.add(WLine.t("{"));
        lines.add(WLine.t("  id: \"magic/first_spell\","));
        lines.add(WLine.t("  title: \"First Spell\","));
        lines.add(WLine.t("  description: \"Cast your first spell.\","));
        lines.add(WLine.t("  subtitle: \"Chapter 1\","));
        lines.add(WLine.t("  category: \"MAGIC\","));
        lines.add(WLine.t("  shape: \"CIRCLE\",  node_size: \"NORMAL\","));
        lines.add(WLine.t("  icon_item: \"minecraft:book\","));
        lines.add(WLine.t("  positionX: 120,  positionY: 80,"));
        lines.add(WLine.t("  parent: \"magic/intro\","));
        lines.add(WLine.t("  visibility: \"NORMAL\","));
        lines.add(WLine.t("  repeat_mode: \"NONE\","));
        lines.add(WLine.t("  reward_choice: true,  reward_choice_count: 1,"));
        lines.add(WLine.t("  auto_claim_rewards: false,"));
        lines.add(WLine.t("  dev_notes: \"Placeholder until magic system is done.\","));
        lines.add(WLine.t("  tasks: [{type: \"checkmark\", task_id: \"phoenixcore:task_1\","));
        lines.add(WLine.t("           description: \"Cast a spell\"}],"));
        lines.add(WLine.t("  rewards: ["));
        lines.add(WLine.t("    {type: \"xp\", levels: 5},"));
        lines.add(WLine.t("    {type: \"item\", item: \"minecraft:book\", count: 1}"));
        lines.add(WLine.t("  ]"));
        lines.add(WLine.t("}"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Prerequisites list (extended format)"));
        lines.add(WLine.t("prerequisites: ["));
        lines.add(WLine.t("  {id: \"magic/intro\", required: true},"));
        lines.add(WLine.t("  {id: \"magic/side\",  required: false},"));
        lines.add(WLine.t("  {id: \"magic/bad\",   forbidden: true},"));
        lines.add(WLine.t("  {id: \"magic/link\",  required: true, link: true}"));
        lines.add(WLine.t("]"));
        lines.add(WLine.sp());
        lines.add(WLine.sh("File locations"));
        lines.add(WLine.kv("Quest SNBT", "config/phoenix_chronicles/quests/*.snbt  (any depth)"));
        lines.add(WLine.kv("Quest markdown", "config/phoenix_chronicles/quests/{id}.md"));
        lines.add(WLine.kv("Categories", "config/phoenix_chronicles/categories.txt  (one per line)"));
        lines.add(WLine.kv("Groups", "config/phoenix_chronicles/quest_groups.json"));
        lines.add(WLine.kv("Settings", "config/phoenix_chronicles/settings.json"));
        lines.add(WLine.kv("Tutorial prog.", "config/phoenix_chronicles/tutorial_progress.dat"));
        return lines;
    }

    private List<WLine> pageLiveStats() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h("Live Registry Stats"));
        lines.add(WLine.t("Data pulled from the in-memory registry at render time."));
        lines.add(WLine.sp());

        Map<String, List<QuestNode>> byCat = new LinkedHashMap<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            byCat.computeIfAbsent(n.getCategory(), k -> new ArrayList<>()).add(n);
        }

        lines.add(WLine.kv("Total quests:", String.valueOf(QuestTreeRegistry.getAllQuests().size())));
        lines.add(WLine.kv("Categories:", String.valueOf(byCat.size())));
        lines.add(WLine.sp());
        lines.add(WLine.sh("Per-category"));
        byCat.entrySet().stream()
                .sorted(Map.Entry.<String, List<QuestNode>>comparingByValue(Comparator.comparingInt(l -> -l.size())))
                .forEach(e -> {
                    long repeatable = e.getValue().stream().filter(QuestNode::isRepeatable).count();
                    long hasTutorial = e.getValue().stream().filter(n -> !n.getTutorialSteps().isEmpty()).count();
                    String suffix = (repeatable > 0 ? "  " + repeatable + " repeatable" : "") +
                            (hasTutorial > 0 ? "  " + hasTutorial + " tutorial" : "");
                    lines.add(WLine.kv(e.getKey() + ":", e.getValue().size() + " quests" + suffix));
                });

        lines.add(WLine.sp());
        lines.add(WLine.sh("Task types"));
        lines.add(WLine.kv("Registered:", String.valueOf(PhoenixTaskRegistry.getEditorTypes().size())));

        lines.add(WLine.sp());
        lines.add(WLine.sh("Repeat modes in use"));
        Map<QuestNode.RepeatMode, Long> repeatCounts = new LinkedHashMap<>();
        for (QuestNode.RepeatMode m : QuestNode.RepeatMode.values()) repeatCounts.put(m, 0L);
        QuestTreeRegistry.getAllQuests().values()
                .forEach(n -> repeatCounts.merge(n.getRepeatMode(), 1L, Long::sum));
        repeatCounts.forEach((mode, count) -> {
            if (count > 0) lines.add(WLine.kv(mode.name() + ":", count + " quest" + (count == 1 ? "" : "s")));
        });

        lines.add(WLine.sp());
        lines.add(WLine.sh("Quests with tutorials"));
        long tutCount = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> !n.getTutorialSteps().isEmpty()).count();
        lines.add(WLine.kv("Total:", tutCount + " quest" + (tutCount == 1 ? "" : "s") + " have tutorial steps"));

        // Load errors
        lines.add(WLine.sp());
        lines.add(WLine.div());
        List<String> errs = QuestFileLoader.LOAD_ERRORS;
        if (errs.isEmpty()) {
            lines.add(WLine.sh("§aLoad errors: none"));
        } else {
            lines.add(WLine.sh("§cLoad errors: " + errs.size()));
            lines.add(WLine.t("Run /chronicles validate in-game for full output."));
            for (String e : errs) lines.add(WLine.t("✗ " + e));
        }

        return lines;
    }

    private List<WLine> pageApiReference() {
        var L = new ArrayList<WLine>();
        L.add(WLine.h("API Reference"));
        L.add(WLine.t("Click ⎘ to copy any snippet to clipboard."));
        L.add(WLine.sp());

        // ── QuestAPI ──────────────────────────────────────────────────────────
        L.add(WLine.sh("QuestAPI  (Java — net.phoenix.core.integration.phoenix_chronicles)"));
        L.add(WLine.kv("completeQuest", "Force-complete a quest for a player (server-side)"));
        L.add(WLine.code("QuestAPI.completeQuest(serverPlayer, new ResourceLocation(\"phoenixcore\", \"my_quest\"));"));
        L.add(WLine.kv("unlockQuest", "Bypass prerequisites and unlock a quest"));
        L.add(WLine.code("QuestAPI.unlockQuest(serverPlayer, new ResourceLocation(\"phoenixcore\", \"my_quest\"));"));
        L.add(WLine.kv("resetQuest", "Reset progress on a quest (repeatable use)"));
        L.add(WLine.code("QuestAPI.resetQuest(serverPlayer, new ResourceLocation(\"phoenixcore\", \"my_quest\"));"));
        L.add(WLine.kv("fireExternalEvent", "Trigger an external_trigger task by id"));
        L.add(WLine.code("QuestAPI.fireExternalEvent(serverPlayer, \"my_trigger_id\");"));
        L.add(WLine.kv("getState", "Read a player's current quest state"));
        L.add(WLine.code("QuestState state = QuestAPI.getState(serverPlayer, questId);"));
        L.add(WLine.kv("getProgress", "0.0–1.0 completion ratio for a quest"));
        L.add(WLine.code("float pct = QuestAPI.getProgress(serverPlayer, questId);"));
        L.add(WLine.sp());

        // ── Java events ───────────────────────────────────────────────────────
        L.add(WLine.sh("Forge event hooks  (Java)"));
        L.add(WLine.kv("QuestCompletedEvent", "Fired on server bus when any quest completes"));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onComplete(QuestCompletedEvent e) {"));
        L.add(WLine.code("    ServerPlayer p = e.getPlayer();"));
        L.add(WLine.code("    ResourceLocation id = e.getQuest().getId();"));
        L.add(WLine.code("}"));
        L.add(WLine.sp());
        L.add(WLine.kv("QuestUnlockedEvent", "Fired on server bus when a quest becomes available"));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onUnlock(QuestUnlockedEvent e) { … }"));
        L.add(WLine.sp());
        L.add(WLine.kv("PhoenixQuestScriptRewardEvent", "Fired by a script_event reward — carry custom data"));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onReward(PhoenixQuestScriptRewardEvent e) {"));
        L.add(WLine.code("    String evtId = e.getEventId();   // matches event_id in SNBT"));
        L.add(WLine.code("    CompoundTag data = e.getData();  // optional NBT payload"));
        L.add(WLine.code("    ServerPlayer p = e.getPlayer();"));
        L.add(WLine.code("}"));
        L.add(WLine.sp());

        // ── Task registration ─────────────────────────────────────────────────
        L.add(WLine.sh("Custom task type  (Java)"));
        L.add(WLine.t("Implement QuestTask, then register in your mod constructor or common setup:"));
        L.add(WLine.code("PhoenixTaskRegistry.register("));
        L.add(WLine.code("    \"my_task_type\",           // type id used in SNBT"));
        L.add(WLine.code("    MyTask::new,               // CompoundTag → QuestTask"));
        L.add(WLine.code("    \"My Task\",                // editor label"));
        L.add(WLine.code("    \"Does something custom\"   // editor tooltip"));
        L.add(WLine.code(");"));
        L.add(WLine.sp());

        // ── Flag registration ─────────────────────────────────────────────────
        L.add(WLine.sh("Custom enable_if flag  (Java)"));
        L.add(WLine.t("Flags are evaluated each render tick — keep the supplier cheap:"));
        L.add(WLine.code("PhoenixQuestFlags.register(\"my_flag\", () -> MyMod.isSomethingEnabled());"));
        L.add(WLine.t("Usage in SNBT:  enable_if: \"my_flag\"  or  enable_if: \"my_flag,other_flag\""));
        L.add(WLine.t("Prefix a flag with ! to negate:  enable_if: \"!my_flag\""));
        L.add(WLine.sp());
        L.add(WLine.div());

        // ── KubeJS ────────────────────────────────────────────────────────────
        L.add(WLine.sh("KubeJS — startup_scripts/chronicles.js"));
        L.add(WLine.sp());
        L.add(WLine.kv("Register a task type", ""));
        L.add(WLine.code("PhoenixTaskRegistry.registerKJS("));
        L.add(WLine.code("  'my_task_type',"));
        L.add(WLine.code("  tag => { return { check: player => true }; },"));
        L.add(WLine.code("  'My Task Label',"));
        L.add(WLine.code("  'Tooltip text'"));
        L.add(WLine.code(");"));
        L.add(WLine.sp());
        L.add(WLine.kv("Listen for quest complete", "client_scripts or server_scripts"));
        L.add(WLine.code("ForgeEvents.onEvent('net.phoenix.core.integration"));
        L.add(WLine.code("  .phoenix_chronicles.event.QuestCompletedEvent', e => {"));
        L.add(WLine.code("    let id = e.quest.id.path;  // e.g. 'magic/first_spell'"));
        L.add(WLine.code("    let player = e.player;"));
        L.add(WLine.code("});"));
        L.add(WLine.sp());
        L.add(WLine.kv("Listen for script_event reward", ""));
        L.add(WLine.code("ForgeEvents.onEvent('net.phoenix.core.integration"));
        L.add(WLine.code("  .phoenix_chronicles.QuestScriptRewardEvent', e => {"));
        L.add(WLine.code("    if (e.eventId === 'my_event') {"));
        L.add(WLine.code("        e.player.tell('Reward fired!');"));
        L.add(WLine.code("    }"));
        L.add(WLine.code("});"));
        L.add(WLine.sp());
        L.add(WLine.kv("Register a flag", ""));
        L.add(WLine.code("PhoenixQuestFlags.registerKJS('my_flag', () => someCondition);"));
        L.add(WLine.sp());
        L.add(WLine.kv("Fire external trigger", "server_scripts"));
        L.add(WLine.code("QuestAPI.fireExternalEvent(player, 'my_trigger_id');"));
        L.add(WLine.sp());
        L.add(WLine.div());
        L.add(WLine.sh("In-game commands"));
        L.add(WLine.kv("/chronicles status <id>", "Any player — check your own quest state"));
        L.add(WLine.kv("/chronicles emergency <id>", "Any player — get emergency items for active quest"));
        L.add(WLine.kv("/chronicles complete <id>", "Op — force-complete a quest"));
        L.add(WLine.kv("/chronicles unlock <id>", "Op — bypass prerequisites"));
        L.add(WLine.kv("/chronicles reset <id>", "Op — reset quest to LOCKED"));
        L.add(WLine.kv("/chronicles active <id>", "Op — force-start a quest"));
        L.add(WLine.kv("/chronicles validate", "Op — report load errors and config issues"));

        return L;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int visibleH = (height - FOOTER_H - MARGIN) - (HEADER_H + MARGIN);
        int maxScroll = Math.max(0, cachedContentH - visibleH);
        scrollY = Math.max(0, Math.min(maxScroll, (int) (scrollY - delta * 14)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int sidebarClipBot = height - FOOTER_H;
            if (mx >= 0 && mx < SIDEBAR_W && my >= HEADER_H) {
                for (int i = 0; i < PAGE_NAMES.length; i++) {
                    int rowY = HEADER_H + 8 + i * 16;
                    if (rowY + 14 > sidebarClipBot) break;
                    if (my >= rowY - 1 && my < rowY + 15) {
                        activePage = i;
                        scrollY = 0;
                        return true;
                    }
                }
            }
            for (int i = 0; i < copyBtnBounds.size(); i++) {
                int[] b = copyBtnBounds.get(i);
                if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                    if (minecraft != null)
                        minecraft.keyboardHandler.setClipboard(copyBtnTexts.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
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
