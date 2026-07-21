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

public class DevWikiScreen extends Screen {

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_DONE, C_ACTIVE;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int SIDEBAR_W = 96;
    private static final int MARGIN = 10;
    private static final int LINE_H = 12;

    private static final String[] PAGE_NAMES = {
            "Overview", "Getting Started", "Canvas", "Quest Fields", "Tasks", "Rewards", "Variants",
            "Rich Text", "SNBT Format", "Live Stats", "API Reference", "Customization"
    };

    private final Screen parent;
    private int activePage = 0;
    private int scrollY = 0;
    private int cachedContentH = 0; 

    private final List<int[]> copyBtnBounds = new ArrayList<>(); 
    private final List<String> copyBtnTexts = new ArrayList<>();

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

        static WLine code(String s) {
            return new WLine(LT.CODE, s, "");
        }
    }

    private static String tr(String key, String fallback) {
        return net.minecraft.client.resources.language.I18n.exists(key) ?
                net.minecraft.client.resources.language.I18n.get(key) : fallback;
    }

    public DevWikiScreen(Screen parent) {
        super(Component.literal("Dev Wiki"));
        this.parent = parent;
    }

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

        addRenderableWidget(Button.builder(Component.literal("§7âœ• Close"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(width / 2 - 36, height - FOOTER_H + 6, 72, 16).build());
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        ChroniclesTheme theme = ChroniclesTheme.current();
        ChroniclesThemeRenderer.drawHeader(g, width, HEADER_H, "§fDev Wiki  §8â€” §7" + PAGE_NAMES[activePage], theme);

        int sidebarClipBot = height - FOOTER_H;
        g.enableScissor(0, HEADER_H, SIDEBAR_W, sidebarClipBot);
        g.fill(0, HEADER_H, SIDEBAR_W, sidebarClipBot, C_PANEL);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int rowY = HEADER_H + 8 + i * 16;
            if (rowY + 14 > sidebarClipBot) break; 
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

        ChroniclesThemeRenderer.drawFooter(g, width, height, FOOTER_H, C_BORDER, C_HEADER);

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

        int totalH = lines.stream().mapToInt(this::lineHeight).sum();
        cachedContentH = totalH;
        ChroniclesThemeRenderer.drawScrollbar(g, width - MARGIN / 2, contentTop, contentBot, scrollY, totalH);

        super.render(g, mx, my, partial);
    }

    private int renderLine(GuiGraphics g, WLine line, int x, int y, int w, int top, int bot, int mx, int my) {
        
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
                    int btnW = font.width("âŽ˜") + 8;
                    int btnX = x + w - btnW - 2;
                    int btnY2 = y + 1;
                    int btnH2 = lh - 2;
                    boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY2 && my < btnY2 + btnH2;

                    ChroniclesThemeRenderer.drawCodeBlock(g, font, line.a(), x, y, w, lh, mx, my, C_BORDER, C_ACCENT,
                            hov);

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

    private List<WLine> buildPage(int page) {
        return switch (page) {
            case 0 -> pageOverview();
            case 1 -> pageGettingStarted();
            case 2 -> pageCanvas();
            case 3 -> pageQuestFields();
            case 4 -> pageTasks();
            case 5 -> pageRewards();
            case 6 -> pageVariants();
            case 7 -> pageRichText();
            case 8 -> pageSnbtFormat();
            case 9 -> pageLiveStats();
            case 10 -> pageApiReference();
            case 11 -> pageCustomization();
            default -> List.of();
        };
    }

    private List<WLine> pageOverview() {
        int total = QuestTreeRegistry.getAllQuests().size();
        int cats = QuestTreeRegistry.getRootChapters().values().stream()
                .map(QuestNode::getCategory).distinct().mapToInt(c -> 1).sum();
        
        Set<String> catSet = new HashSet<>();
        QuestTreeRegistry.getAllQuests().values().forEach(n -> catSet.add(n.getCategory()));

        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.overview.1", "Phoenix Chronicles: Dev Reference")));
        lines.add(
                WLine.t(tr("phoenix_chronicles.wiki.overview.2", "In-game quest system for Minecraft Forge 1.20.1.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.overview.3",
                "Dev mode activates automatically in Creative or at op level â‰¥ 2.")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.overview.4", "Live registry")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.5.label", "Quests loaded:"), String.valueOf(total)));
        lines.add(
                WLine.kv(tr("phoenix_chronicles.wiki.overview.6.label", "Categories:"), String.valueOf(catSet.size())));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.7.label", "Task types:"),
                String.valueOf(PhoenixTaskRegistry.getEditorTypes().size()) + " registered"));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.overview.8", "Quick navigation")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.9.label", "Getting Started"),
                tr("phoenix_chronicles.wiki.overview.9.value",
                        "New to this? Start here - the actual click-by-click workflow")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.10.label", "Canvas"),
                tr("phoenix_chronicles.wiki.overview.10.value", "Pan, zoom, right-click, Alt+drag dep lines")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.11.label", "Quest Fields"),
                tr("phoenix_chronicles.wiki.overview.11.value", "All SNBT keys and their meanings")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.12.label", "Tasks"),
                tr("phoenix_chronicles.wiki.overview.12.value", "Every task type with expected fields")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.13.label", "Rewards"),
                tr("phoenix_chronicles.wiki.overview.13.value", "All reward types: including choice rewards")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.14.label", "Variants"),
                tr("phoenix_chronicles.wiki.overview.14.value",
                        "Per-quest overrides for expert mode, seasonal content, etc.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.15.label", "Rich Text"),
                tr("phoenix_chronicles.wiki.overview.15.value", "{#RRGGBB} colour, [img:â€¦] inline textures, [links]")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.16.label", "SNBT Format"),
                tr("phoenix_chronicles.wiki.overview.16.value", "Full file format reference and folder layout")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.17.label", "Live Stats"),
                tr("phoenix_chronicles.wiki.overview.17.value", "Per-category quest counts and type breakdown")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.overview.18.label", "Customization"), tr(
                "phoenix_chronicles.wiki.overview.18.value", "Sidebar, chapter theme, background pictures, toasts")));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.overview.19", "Tutorial quests")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.overview.20",
                "Add tutorial_steps to any quest SNBT to attach a guided overlay.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.overview.21",
                "Each step has a text field and an optional highlight target:")));
        lines.add(
                WLine.in(tr("phoenix_chronicles.wiki.overview.22", "none, sidebar, canvas, toolbar, node:{quest_id}")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.overview.23",
                "Progress is stored client-side in config/phoenix_chronicles/tutorial_progress.dat.")));
        return lines;
    }

    private List<WLine> pageGettingStarted() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.getting_started.1", "Getting Started")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.2",
                "The actual click-by-click path from an empty questbook to a working")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.3",
                "questline - everything here links to a page with the full detail.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.4", "1. Make a chapter")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.5.label", "Sidebar \"+ Category\" pill"),
                tr("phoenix_chronicles.wiki.getting_started.5.value", "Opens the new-chapter dialog")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.6",
                "A chapter is just a named category tab - quests get assigned to one by")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.7",
                "their own category field, they don't live \"inside\" it structurally.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.8", "2. Add your first quest")));
        lines.add(WLine.kv(
                tr("phoenix_chronicles.wiki.getting_started.9.label", "Right-click empty canvas â†’ \"+ New quest\""),
                tr("phoenix_chronicles.wiki.getting_started.9.value", "Opens the quest creator")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.10",
                "Set a title (required), pick a shape/icon, and place it - see Quest Fields")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.11", "for every field this form can set.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.12", "3. Give it something to do")));
        lines.add(WLine.kv(
                tr("phoenix_chronicles.wiki.getting_started.13.label",
                        "Right-click the quest â†’ \"Edit Tasks & Rewards\""),
                tr("phoenix_chronicles.wiki.getting_started.13.value", "Or the button inside the creator")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.14",
                "Add one or more tasks (Tasks page has every type) and whatever rewards")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.getting_started.15", "should be granted on completion (Rewards page).")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.16", "4. Chain it to the next quest")));
        lines.add(WLine.kv(
                tr("phoenix_chronicles.wiki.getting_started.17.label", "Alt + drag from one node to another"),
                tr("phoenix_chronicles.wiki.getting_started.17.value", "Draws a prerequisite dependency line")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.18",
                "There's no menu item for this - prerequisites are drawn directly on the")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.19",
                "canvas. The target quest stays LOCKED until its prerequisite(s) complete.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.20", "5. Write the description")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.21",
                "Click the description box in the quest detail view (edit mode) to open the")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.22",
                "text editor - see Rich Text for colour/image/link syntax and the \"---\"")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.23", "page-break marker for long lore.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.24", "6. Test it as a player would")));
        lines.add(WLine.kv(
                tr("phoenix_chronicles.wiki.getting_started.25.label",
                        "Right-click empty canvas â†’ \"âµ Enter Player Mode\""),
                tr("phoenix_chronicles.wiki.getting_started.25.value", "Simulates real progress")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.26",
                "Test mode uses its own throwaway progress data (nothing server-side, no")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.27",
                "other player is affected) and disables editing while it's on - click a")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.28",
                "quest to toggle it COMPLETED/LOCKED and watch prerequisites cascade for")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.29",
                "real. \"â†º Reset Player Mode Data\" clears it; exit the same way you entered.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.30", "Optional, once the basics work")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.31.label", "Chapter theme"),
                tr("phoenix_chronicles.wiki.getting_started.31.value",
                        "Right-click canvas â†’ \"Edit chapter themeâ€¦\" - see Customization")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.32.label", "Per-quest text/design"),
                tr("phoenix_chronicles.wiki.getting_started.32.value",
                        "Right-click a quest â†’ \"Edit Textsâ€¦\", \"Design toastâ€¦\"")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.33.label", "Variants"),
                tr("phoenix_chronicles.wiki.getting_started.33.value",
                        "\"â—ˆ Variants\" button in the quest creator - see the Variants page")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.34.label", "KubeJS/Java integration"),
                tr("phoenix_chronicles.wiki.getting_started.34.value",
                        "See API Reference once you need code, not just SNBT")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.getting_started.35", "If something looks wrong")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.36.label", "/chronicles validate"), tr(
                "phoenix_chronicles.wiki.getting_started.36.value", "Reports load errors and common config mistakes")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.getting_started.37.label", "Live Stats page"), tr(
                "phoenix_chronicles.wiki.getting_started.37.value", "Same load-error list, plus per-category counts")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.38",
                "Most \"my quest doesn't show up\" reports turn out to be a typo'd category")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.getting_started.39",
                "name or a prerequisite pointing at an ID that doesn't exist.")));
        return lines;
    }

    private List<WLine> pageCanvas() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.canvas.1", "Canvas Controls")));
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.2", "Mouse")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.3.label", "Left drag"),
                tr("phoenix_chronicles.wiki.canvas.3.value", "Pan the canvas")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.4.label", "Scroll wheel"),
                tr("phoenix_chronicles.wiki.canvas.4.value", "Zoom in / out")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.5.label", "Left click node"),
                tr("phoenix_chronicles.wiki.canvas.5.value", "Select / open quest detail")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.6.label", "Hover node"),
                tr("phoenix_chronicles.wiki.canvas.6.value", "Tooltip shows just the title/subtitle")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.7.label", "Shift + hover node"),
                tr("phoenix_chronicles.wiki.canvas.7.value",
                        "Tooltip expands to state, tasks, prereqs, validation warnings (dev)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.8.label", "Right click node"),
                "Context menu: Edit Quest (has its own Tasks/Rewards button), Texts, Design toastâ€¦, Set Iconâ€¦ (item/fluid/texture), Resizeâ€¦, Delete, Move categoryâ€¦"));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.9.label", "Right click canvas"),
                "Context menu: Add quest, Add group, Chapter themeâ€¦, Add pictureâ€¦, dep-line settings"));
        lines.add(
                WLine.kv(tr("phoenix_chronicles.wiki.canvas.10.label", "Right click picture"),
                        tr("phoenix_chronicles.wiki.canvas.10.value",
                                "Its own menu: Resize, Resize (scroll+drag)â€¦, Move category, Delete")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.11.label", "Shift + drag"),
                tr("phoenix_chronicles.wiki.canvas.11.value", "Move a node, group, or background picture (dev mode)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.12.label", "Alt + drag node"),
                tr("phoenix_chronicles.wiki.canvas.12.value", "Draw dependency line to another node")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.13.label", "Middle click"),
                tr("phoenix_chronicles.wiki.canvas.13.value", "Reset pan offset")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.14",
                "Keyboard  (all rebindable via Options â†’ Controls â†’ Phoenix Chronicles")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.15",
                "unless noted otherwise - these fire while the quest screen has focus, so")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.16",
                "they still work even though they're not \"global\" keybinds.)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.17.label", "Home"),
                tr("phoenix_chronicles.wiki.canvas.17.value",
                        "Fit all nodes to view (default binding - was \"F\" before it became")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.canvas.18", "rebindable; F is now Search instead)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.19.label", "F"),
                tr("phoenix_chronicles.wiki.canvas.19.value", "Open quest search overlay (used to require Ctrl+F)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.20.label", "L"),
                tr("phoenix_chronicles.wiki.canvas.20.value", "Toggle line style (Spline â†” Straight)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.21.label", "M"),
                tr("phoenix_chronicles.wiki.canvas.21.value", "Toggle minimap")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.22.label", "K"),
                tr("phoenix_chronicles.wiki.canvas.22.value",
                        "Open quest book (global, works outside the quest screen too)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.23.label", "U"), tr(
                "phoenix_chronicles.wiki.canvas.23.value", "Hold an item, press this: jump to quests requiring it")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.24.label", "P"),
                tr("phoenix_chronicles.wiki.canvas.24.value",
                        "Pin/unpin whichever quest is under the mouse to the HUD tracker")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.25.label", "Shift (hold, while scrolling)"),
                "Zoom anchors to the cursor instead of canvas center"));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.26",
                "(the default is to always zoom toward the canvas center now - a quest map,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.27",
                "not a world map - hold this key for the old cursor-anchored behavior)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.28.label", "V"),
                tr("phoenix_chronicles.wiki.canvas.28.value",
                        "Toggle validation panel (dev only, and not while holding Ctrl)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.29.label", "S"),
                tr("phoenix_chronicles.wiki.canvas.29.value", "Toggle Quest Stats panel (dev only)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.30.label", "G"),
                tr("phoenix_chronicles.wiki.canvas.30.value", "Toggle Subgraph mode (dev only)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.31.label", "I"),
                tr("phoenix_chronicles.wiki.canvas.31.value", "Run FTB import (dev only)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.32.label", "/"),
                tr("phoenix_chronicles.wiki.canvas.32.value",
                        "Open this wiki (dev only) - shown as \"?\" in the toolbar button")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.33.label", "ESC"),
                tr("phoenix_chronicles.wiki.canvas.33.value", "Close menus / deselect")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.34",
                "Undo / clipboard  (dev mode - NOT rebindable, fixed OS-style combos)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.35.label", "Ctrl+Z"),
                tr("phoenix_chronicles.wiki.canvas.35.value",
                        "Undo last node/group/picture move, dep-line change, paste, etc.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.36.label", "Ctrl+Y / Ctrl+Shift+Z"),
                tr("phoenix_chronicles.wiki.canvas.36.value", "Redo")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.37.label", "Ctrl+C"),
                tr("phoenix_chronicles.wiki.canvas.37.value", "Copy the selected quest's SNBT to the clipboard")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.38.label", "Ctrl+V"),
                tr("phoenix_chronicles.wiki.canvas.38.value", "Paste clipboard SNBT as a brand-new quest")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.39",
                "Copy/paste is also on the right-click quest menu, not just the key combo.")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.40", "Multi-select (dev mode)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.41.label", "Shift+click"),
                tr("phoenix_chronicles.wiki.canvas.41.value", "Add node to selection")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.42.label", "Ctrl+drag"),
                tr("phoenix_chronicles.wiki.canvas.42.value", "Box-select nodes")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.43.label", "Ctrl+G"),
                tr("phoenix_chronicles.wiki.canvas.43.value", "Group selected nodes")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.44.label", "Del"),
                tr("phoenix_chronicles.wiki.canvas.44.value", "Delete selected nodes")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.45", "Zoom")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.46.label", "Range"),
                tr("phoenix_chronicles.wiki.canvas.46.value", "12% â€“ 250%  (scroll or pinch)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.47.label", "Step"),
                tr("phoenix_chronicles.wiki.canvas.47.value", "12% per scroll tick")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.48", "Grid snapping  (node placement)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.49",
                "A grid-size pill in the title bar (left of the zoom %) controls snap size.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.50.label", "Click pill"),
                tr("phoenix_chronicles.wiki.canvas.50.value", "Cycle: 1 â†’ 4 â†’ 8 â†’ 16 â†’ 32 â†’ 1")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.51.label", "1 (free)"),
                tr("phoenix_chronicles.wiki.canvas.51.value", "Pixel-perfect: no snapping, any position")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.52.label", "4 / 8 / 16 / 32"),
                tr("phoenix_chronicles.wiki.canvas.52.value", "Snap to that many logical-unit grid squares")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.53.label", "Shift-drag"),
                tr("phoenix_chronicles.wiki.canvas.53.value", "Always bypasses snapping regardless of pill setting")));
        lines.add(WLine.sp());
        lines.add(WLine
                .sh(tr("phoenix_chronicles.wiki.canvas.54", "Dev tools  (right-click empty canvas, dev mode only)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.55",
                "Dev-only controls live in the right-click context menu, not the toolbar.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.56.label", "Test mode"),
                tr("phoenix_chronicles.wiki.canvas.56.value",
                        "Simulate player state: see quests as a normal player would")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.57.label", "â†º Reset"),
                tr("phoenix_chronicles.wiki.canvas.57.value", "Visible in Test mode only: clears simulated progress")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.58.label", "Subgraph"),
                tr("phoenix_chronicles.wiki.canvas.58.value",
                        "Highlight the selected node's transitive dependency tree")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.canvas.59.label", "Stats"), tr(
                "phoenix_chronicles.wiki.canvas.59.value", "Overlay a small stats card (quest counts, load errors)")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.canvas.60", "Toolbar (always visible in dev mode)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.61",
                "Toolbar holds: category selector, filter pills, zoom pill, ? wiki button.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.62",
                "Dev toggles and destructive actions are in the right-click menu to avoid")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.canvas.63", "toolbar overflow at smaller window sizes.")));
        return lines;
    }

    private List<WLine> pageCustomization() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.customization.1", "Customization")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.2",
                "Everything below is dev-mode-only pack authoring - players never see any of")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.3", "these editors, only their results.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.4", "Sidebar & Questbook Title")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.5.label", "Category tile"),
                tr("phoenix_chronicles.wiki.customization.5.value",
                        "Left-click select, right-click â†’ chapter theme editor")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.6.label", "Collapse toggle"),
                tr("phoenix_chronicles.wiki.customization.6.value",
                        "Small arrow above the category list - reclaims canvas width")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.7.label", "Questbook icon/name"),
                tr("phoenix_chronicles.wiki.customization.7.value",
                        "Top-left of the sidebar - click to open the naming popup")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.8",
                "Book icon + name default to a generic \"Quest Book\" until set.")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.9.label", "Sidebar Behavior"),
                tr("phoenix_chronicles.wiki.customization.9.value",
                        "Settings â†’ General - COLLAPSIBLE (default, click the arrow")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.customization.10",
                "above) or HOVER_TO_EXPAND (FTB Quests-style: always collapsed, opens on")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.customization.11",
                "mouseover, closes when the mouse leaves - no manual toggle in this mode).")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.12",
                "Chapter theme  (right-click canvas â†’ Edit chapter themeâ€¦)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.13.label", "Display name"),
                tr("phoenix_chronicles.wiki.customization.13.value",
                        "Raw override; empty = derived from the category slug")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.14.label", "Icon"),
                tr("phoenix_chronicles.wiki.customization.14.value", "Sidebar tile icon for this chapter")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.15.label", "Style"),
                tr("phoenix_chronicles.wiki.customization.15.value",
                        "DOT_GRID / GRID_LINES / HEX_GRID / DIAGONAL_LINES / SOLID / CUSTOM")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.16.label", "Color tint"),
                tr("phoenix_chronicles.wiki.customization.16.value", "#RRGGBB overlay on the canvas background")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.17",
                "Picking a texture (Browseâ€¦) auto-switches Style to CUSTOM - the two used to")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.18",
                "be independent, so choosing a texture silently did nothing until Style was")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.19",
                "also changed by hand. That's fixed; a texture pick sets both now.")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(
                tr("phoenix_chronicles.wiki.customization.20", "Custom textures  (Browseâ€¦ button, or Add pictureâ€¦)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.21",
                "Drop PNGs in config/phoenix_chronicles/textures/ - the browser lists them as")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.22",
                "phoenixcore:textures/custom/<relative-path>. That location was never a real")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.23",
                "game asset path (Minecraft only loads assets/<namespace>/... from a jar or")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.24",
                "resource pack), so these are dynamically registered at runtime the first time")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.25",
                "they're drawn (see CustomTextureCache) instead of needing a resource pack.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.26.label", "Left-click a thumbnail"),
                tr("phoenix_chronicles.wiki.customization.26.value", "Select and apply")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.27.label", "Right-click a thumbnail"),
                tr("phoenix_chronicles.wiki.customization.27.value", "Copy its resource location to clipboard")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.28",
                "Background pictures  (right-click canvas â†’ Add pictureâ€¦)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.29",
                "Freestanding decorative images, separate from the chapter theme above -")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.30",
                "positioned in canvas space, so they pan/zoom with the graph like nodes do.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.31.label", "Add"),
                tr("phoenix_chronicles.wiki.customization.31.value",
                        "Right-click empty canvas â†’ Add pictureâ€¦ â†’ pick a texture")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.32.label", "Move"),
                tr("phoenix_chronicles.wiki.customization.32.value", "Shift+drag the picture directly")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.33.label", "Right-click a picture"),
                "Move / Resize â–¸ / Resize (scroll+drag)â€¦ / Move to Chapter â–¸ / Delete"));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.34.label", "Resize â–¸"),
                tr("phoenix_chronicles.wiki.customization.34.value",
                        "Fixed presets: 32 / 64 / 128 / 256 / 512 / 1024 px")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.35.label", "Resize (scroll+drag)â€¦"),
                tr("phoenix_chronicles.wiki.customization.35.value",
                        "Interactive mode - bypasses canvas zoom/pan entirely")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.customization.36",
                "Scroll = resize (Â±20%, shift = fine Â±5%), drag = move, right-click/Esc = done")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.37",
                "Every add/move/resize/category-move/delete is one Ctrl+Z-undoable step, even")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.38",
                "a whole interactive resize session (one entry covers the full edit).")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.39",
                "Dependency lines  (right-click canvas â†’ Dependency linesâ€¦, or per-quest)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.40.label", "Line Style"),
                tr("phoenix_chronicles.wiki.customization.40.value",
                        "THIN / NORMAL / BOLD / THICK / WIDE / GLOW - controls rail width")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.41.label", "Line Anim Speed"),
                tr("phoenix_chronicles.wiki.customization.41.value", "How fast the arrow chain travels on hover")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.42",
                "Base rail/arrow colors (locked/active/done) come from the active theme's")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.43",
                "locked/activeColor/done colors - editing the theme recolors dependency lines")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.44",
                "along with everything else. The hover-boost colors (cyan for what a quest")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.45",
                "needs, amber for what it unlocks) are intentionally fixed, not themed.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.46",
                "Lines are static until you hover a connected quest; only that quest's own")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.47",
                "edges animate, to avoid the whole tree moving at once.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.48",
                "Quest toasts  (Settings screen, or right-click a quest â†’ Design toastâ€¦)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.49.label", "Toast Style"),
                tr("phoenix_chronicles.wiki.customization.49.value",
                        "COMPACT (corner banner) / ABOVE_HOTBAR / BIG_CENTER - global default")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.50.label", "Toast Position"),
                tr("phoenix_chronicles.wiki.customization.50.value", "Which corner, for the COMPACT style")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.51",
                "Any quest without its own design uses whichever Toast Style is selected.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.52.label", "Design toastâ€¦"),
                tr("phoenix_chronicles.wiki.customization.52.value",
                        "Per-quest custom layout - freeform icon/title/label position+color")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.53",
                "Drag the icon/title/label directly in the live preview; the side panel is split")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.54",
                "into tabs (Element, Icons, Backdrop, Presets) instead of one long scrolling list.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.56.label", "Backdrop tab"),
                tr("phoenix_chronicles.wiki.customization.56.value",
                        "Background is always its own independent box - drag its middle to move it,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.101",
                "drag a corner handle to resize it, or use \"Fit to elements now\" to snap it around")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.102",
                "the icon/title/label's CURRENT positions. It never moves on its own just because")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.106",
                "you dragged one of them elsewhere.")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.59.label", "Position fields / arrow keys"),
                tr("phoenix_chronicles.wiki.customization.59.value",
                        "Numeric % X/Y for pixel-precise placement, or nudge with arrow keys")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.60.label", "Alignment guides"),
                tr("phoenix_chronicles.wiki.customization.60.value",
                        "Dragging near screen-center or another element snaps + shows a guide line")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.103.label", "Previewing: Complete / Unlock"),
                tr("phoenix_chronicles.wiki.customization.103.value",
                        "Toggle which toast text the live preview shows, without triggering a real one")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.62.label", "Reset element"),
                tr("phoenix_chronicles.wiki.customization.62.value",
                        "Restores just the selected icon/title/label to default - leaves the rest alone")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.63.label", "Copy / Paste design"),
                tr("phoenix_chronicles.wiki.customization.63.value",
                        "In-memory clipboard - copy one quest's design, paste it onto another's")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.64.label", "Save as presetâ€¦ / Load preset"),
                tr("phoenix_chronicles.wiki.customization.64.value",
                        "Named, reusable templates saved to config/phoenix_chronicles/toast_presets.json")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.55.label", "Reset to default style"),
                tr("phoenix_chronicles.wiki.customization.55.value",
                        "Deletes the quest's custom design (only shown once one exists)")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.56", "Recipe viewer (EMI)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.57",
                "Clicking a task/reward item icon opens EMI's recipe browser for it, if EMI")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.58",
                "is installed (JEI has no integration yet). Pressing Escape returns you to")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.59",
                "the quest book instead of EMI's own default (a throwaway inventory screen,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.60",
                "then straight to gameplay) - this is a client setting, on by default:")));
        lines.add(WLine.kv(
                tr("phoenix_chronicles.wiki.customization.61.label", "Return to Quest Book from Recipe Viewer"),
                tr("phoenix_chronicles.wiki.customization.61.value", "Settings â†’ General - opt out here")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.customization.62",
                "Player Settings screen  (all players - gear icon, not dev-only)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.63",
                "Everything above this line on the page is dev/pack-authoring only. The")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.customization.64",
                "Settings screen itself is for every player and covers, by category:")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.65.label", "General"),
                tr("phoenix_chronicles.wiki.customization.65.value",
                        "Text Scale, Theme, Layout Density, Reduce Motion, Recipe Viewer, Theme Editor")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.66.label", "HUD"),
                tr("phoenix_chronicles.wiki.customization.66.value",
                        "Position, Opacity, Show Title/Progress/Rewards for the pinned-quest tracker")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.67.label", "Pop-Ups"),
                tr("phoenix_chronicles.wiki.customization.67.value",
                        "Master on/off, Style, Position, Sounds for unlock/complete toasts")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.68.label", "Canvas"),
                tr("phoenix_chronicles.wiki.customization.68.value",
                        "Hide Completed by default, Grid Snap, dependency line style/animation")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.69.label", "Phantasia"),
                tr("phoenix_chronicles.wiki.customization.69.value",
                        "Auto-Spin for embedded 3D multiblock/scene previews")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.70.label", "Inventory"),
                tr("phoenix_chronicles.wiki.customization.70.value",
                        "Show/hide and position the quest-book button on the inventory screen")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.customization.71.label", "Dev Mode"),
                tr("phoenix_chronicles.wiki.customization.71.value",
                        "The opt-in toggle that unlocks everything documented on this page")));
        return lines;
    }

    private List<WLine> pageQuestFields() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.quest_fields.1", "Quest SNBT Fields")));
        lines.add(
                WLine.t(tr("phoenix_chronicles.wiki.quest_fields.2", "All fields are optional except id and title.")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.3", "Identity")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.4.label", "id"),
                tr("phoenix_chronicles.wiki.quest_fields.4.value",
                        "Path portion only: e.g. \"my_quest\" â†’ phoenixcore:my_quest")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.5.label", "title"),
                tr("phoenix_chronicles.wiki.quest_fields.5.value", "Display name shown in quest headers and search")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.6.label", "description"),
                tr("phoenix_chronicles.wiki.quest_fields.6.value", "Lore / body text")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.7.label", "subtitle"),
                tr("phoenix_chronicles.wiki.quest_fields.7.value", "Smaller text below the title on the detail card")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.8", "Appearance")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.9.label", "category"),
                tr("phoenix_chronicles.wiki.quest_fields.9.value", "Chapter tab name: e.g. MAIN, MAGIC, COMBAT")));
        lines.add(
                WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.10.label", "shape"),
                        tr("phoenix_chronicles.wiki.quest_fields.10.value",
                                "SQUARE Â· CIRCLE Â· DIAMOND Â· HEXAGON Â· TRIANGLE Â· STAR Â· PENTAGON Â· SHIELD Â· CROSS")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.11.label", "node_size"),
                tr("phoenix_chronicles.wiki.quest_fields.11.value",
                        "TINY(14px) Â· SMALL(18px) Â· NORMAL(32px, default) Â· LARGE(48px) Â· HUGE(64px)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.104.label", "node_size_px"),
                tr("phoenix_chronicles.wiki.quest_fields.104.value",
                        "Optional exact pixel override (8-200), takes priority over node_size - set via the " +
                                "canvas's right-click â†’ \"Resize (scroll + drag)â€¦\", not hand-edited")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.12.label", "icon_item"), tr(
                "phoenix_chronicles.wiki.quest_fields.12.value", "Item id for the node icon: e.g. minecraft:diamond")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.105.label", "icon_fluid"),
                tr("phoenix_chronicles.wiki.quest_fields.105.value",
                        "Fluid id for the node icon (flat tinted square): e.g. minecraft:water - " +
                                "icon_texture > icon_fluid > icon_item in priority, mutually exclusive")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.13.label", "positionX"),
                tr("phoenix_chronicles.wiki.quest_fields.13.value", "Canvas X coordinate (pixels from left edge)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.14.label", "positionY"),
                tr("phoenix_chronicles.wiki.quest_fields.14.value", "Canvas Y coordinate (pixels from top edge)")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.15", "Visibility")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.16.label", "visibility"),
                tr("phoenix_chronicles.wiki.quest_fields.16.value", "NORMAL Â· HIDDEN Â· MYSTERY Â· DISABLED")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.17.label", "enable_if"), tr(
                "phoenix_chronicles.wiki.quest_fields.17.value", "Flag expression: quest hidden+disabled when false")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.18.label", "hide_dep_line"), tr(
                "phoenix_chronicles.wiki.quest_fields.18.value", "true / false: hides all dep lines on the canvas")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.19.label", "disabled_blocks_children"),
                tr("phoenix_chronicles.wiki.quest_fields.19.value", "true â†’ DISABLED quest still gates children")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.20", "Completion")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.21.label", "task_min_count"), tr(
                "phoenix_chronicles.wiki.quest_fields.21.value", "0 = all tasks required; N = complete any N tasks")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.22.label", "repeat_mode"),
                tr("phoenix_chronicles.wiki.quest_fields.22.value", "NONE Â· DAILY Â· COOLDOWN Â· INFINITE")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.23.label", "repeat_cooldown_hours"),
                tr("phoenix_chronicles.wiki.quest_fields.23.value", "Hours between repeats (COOLDOWN mode only)")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.24", "Prerequisites")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.25.label", "parent"), tr(
                "phoenix_chronicles.wiki.quest_fields.25.value", "Single primary parent quest id path, or \"none\"")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.26.label", "require_all_prereqs"),
                tr("phoenix_chronicles.wiki.quest_fields.26.value", "true = AND gate; false = OR gate (legacy)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.27.label", "prerequisites"),
                tr("phoenix_chronicles.wiki.quest_fields.27.value", "List of {id, required, forbidden, link} tags")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.28.label", "optional_prereq_min_count"),
                tr("phoenix_chronicles.wiki.quest_fields.28.value", "Min optional prereqs needed (0 = all)")));
        lines.add(WLine.sp());
        lines.add(WLine
                .sh(tr("phoenix_chronicles.wiki.quest_fields.29", "Rewards (on the quest, not inside rewards list)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.30.label", "reward_choice"),
                tr("phoenix_chronicles.wiki.quest_fields.30.value",
                        "true â†’ player picks N rewards instead of getting all")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.31.label", "reward_choice_count"), tr(
                "phoenix_chronicles.wiki.quest_fields.31.value", "How many rewards the player may pick (default 1)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.32.label", "auto_claim_rewards"),
                tr("phoenix_chronicles.wiki.quest_fields.32.value",
                        "true â†’ rewards are automatically given on completion")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.33", "Developer")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.34.label", "dev_notes"),
                tr("phoenix_chronicles.wiki.quest_fields.34.value",
                        "Free-text notes visible only in the quest editor (not to players)")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.35", "Tutorial")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.36.label", "tutorial_steps"), tr(
                "phoenix_chronicles.wiki.quest_fields.36.value", "List of {text, highlight} tags: see Overview page")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.quest_fields.37", "Multiplayer / Teams")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.quest_fields.38.label", "shared"),
                tr("phoenix_chronicles.wiki.quest_fields.38.value",
                        "true â†’ completing this quest cascades to all online teammates")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.quest_fields.39",
                "Uses Minecraft's built-in scoreboard teams (/team add, /team join).")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.quest_fields.40",
                "Task progress remains per-player; only the final COMPLETED state is shared.")));
        return lines;
    }

    private List<WLine> pageTasks() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.tasks.1", "Task Types")));

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.tasks.2", "Built-in")));
        String[][] builtins = {
                { "kill_entity", "Kill mobs", "target: entity_id, count, consume" },
                { "item_check", "Have item(s) in inventory",
                        "target: item_id, count, consume - also counts your AE2 network storage if AE2 is " +
                                "installed (toggle: ae2_storage_for_item_fluid_tasks in engine_settings.snbt)" },
                { "craft_item", "Craft an item", "target: item_id, count" },
                { "experience", "Reach an XP level", "count: level" },
                { "location_terminal", "Interact with a terminal", "target: terminal_id, consume" },
                { "advancement", "Earn an advancement", "target: advancement_id" },
                { "block_interact", "Place / right-click a block", "target: block_id, secondary: PLACE|RIGHT_CLICK" },
                { "fluid_check", "Have fluid in inventory",
                        "target: fluid_id, count: mB, consume - also counts your AE2 network storage if AE2 is " +
                                "installed (toggle: ae2_storage_for_item_fluid_tasks in engine_settings.snbt)" },
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
                { "ae2_item_storage", "Have an item stored in your AE2 network",
                        "target: item_id, count, consume - read via your held wireless terminal" },
                { "ae2_fluid_storage", "Have a fluid stored in your AE2 network",
                        "target: fluid_id, count: mB, consume - read via your held wireless terminal" },
        };
        for (String[] row : builtins) {

            lines.add(WLine.kv(row[0], tr("phoenix_chronicles.wiki.tasks.builtin." + row[0] + ".label", row[1])));
            lines.add(WLine.in(tr("phoenix_chronicles.wiki.tasks.builtin." + row[0] + ".detail", row[2])));
        }

        int kjsCount = PhoenixTaskRegistry.getEditorTypes().size() - builtins.length;
        if (kjsCount > 0) {
            lines.add(WLine.sp());
            lines.add(WLine
                    .sh(tr("phoenix_chronicles.wiki.tasks.3", "KubeJS / mod-registered") + "  (" + kjsCount + ")"));
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
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.tasks.3", "SNBT task entry format")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.tasks.4",
                "{type: \"kill_entity\", task_id: \"phoenixcore:task_â€¦\", description: \"â€¦\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.tasks.5",
                " target: \"minecraft:zombie\", count: 5, consume: false, optional: false}")));
        return lines;
    }

    private List<WLine> pageRewards() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.rewards.1", "Reward Types")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.2.label", "item"),
                tr("phoenix_chronicles.wiki.rewards.2.value", "Give item(s) to the player")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rewards.3", "Fields: type, item (item_id), count")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.4.label", "xp"),
                tr("phoenix_chronicles.wiki.rewards.4.value", "Award experience levels")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rewards.5", "Fields: type, levels (integer)")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.6.label", "command"),
                tr("phoenix_chronicles.wiki.rewards.6.value", "Run a server command as console")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rewards.7",
                "Fields: type, command  (%player% replaced with player name)")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.8.label", "loot_table"),
                tr("phoenix_chronicles.wiki.rewards.8.value", "Roll a loot table, give all resulting items")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rewards.9", "Fields: type, loot_table (resource location)")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.10.label", "script_event"),
                tr("phoenix_chronicles.wiki.rewards.10.value", "Fire PhoenixQuestScriptRewardEvent on the Forge bus")));
        lines.add(WLine.in(
                tr("phoenix_chronicles.wiki.rewards.11", "Fields: type, event_id, data (optional CompoundTag NBT)")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rewards.12",
                "KubeJS: listen with ForgeEvents.onEvent('â€¦ScriptRewardEvent', e => â€¦)")));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rewards.13", "Choice rewards")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.14",
                "Set reward_choice: true on the quest to let players pick from the rewards list.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.15",
                "reward_choice_count controls how many they may pick (default 1).")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.16",
                "The reward screen shows all options; unchosen rewards are discarded.")));
        lines.add(WLine.sp());
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rewards.17.label", "Example (pick 1 of 3)"),
                tr("phoenix_chronicles.wiki.rewards.17.value", "")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.18", "  reward_choice: true,  reward_choice_count: 1,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.19", "  rewards: [")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.20",
                "    {type: \"item\", item: \"minecraft:diamond_sword\", count: 1},")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.21",
                "    {type: \"item\", item: \"minecraft:diamond_pickaxe\", count: 1},")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.22",
                "    {type: \"item\", item: \"minecraft:elytra\", count: 1}")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.23", "  ]")));
        lines.add(WLine.sp());
        lines.add(WLine.div());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rewards.24", "SNBT reward entry format")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.rewards.25", "{type: \"item\", item: \"minecraft:diamond\", count: 3}")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.26",
                "{type: \"script_event\", event_id: \"my_event\", data: {key: 1}}")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rewards.27", "Java event hook")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.28", "@SubscribeEvent")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.rewards.29", "public void onReward(PhoenixQuestScriptRewardEvent e) {")));
        lines.add(
                WLine.t(tr("phoenix_chronicles.wiki.rewards.30", "    e.getPlayer();  e.getEventId();  e.getData();")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rewards.31", "}")));
        return lines;
    }

    private List<WLine> pageVariants() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.variants.1", "Variants  (per-quest conditional overrides)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.2",
                "A variant lets ONE quest present differently depending on a flag condition -")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.3",
                "a different title/description, different tasks, different rewards, even a")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.4",
                "different visibility - without duplicating the quest itself. Common uses:")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.5",
                "an \"expert mode\" pack toggle, seasonal/event content, or per-tier reward")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.6",
                "scaling. This is entirely separate from the enable_if field on the base")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.7",
                "quest - enable_if controls whether the quest exists at all; a variant only")));
        lines.add(WLine
                .t(tr("phoenix_chronicles.wiki.variants.8", "changes what it LOOKS like once it's already there.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.60",
                "A single quest isn't limited to one variant - add as many as you need (e.g.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.61",
                "one each for Normal / Expert / Hardcore pack modes); see \"Resolution rule\"")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.62",
                "below for how the list is evaluated when more than one is present.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.variants.9", "There is no built-in \"pack mode\" switch")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.10",
                "This is the part that trips people up: variants don't read from some")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.11",
                "dedicated \"current pack mode\" setting, because no such setting exists.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.12",
                "A variant's condition is a plain enable_if-style flag expression (see Quest")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.13",
                "Fields â†’ Visibility, and the API Reference page's flag section for full")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.14",
                "syntax) - evaluated fresh every time the quest is resolved. \"pack_mode\" is")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.15",
                "just a NAME a pack dev chooses to use consistently; you set what it means")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.16",
                "via one of the same flag mechanisms enable_if already uses:")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.17.label", "config:file#key=val"),
                tr("phoenix_chronicles.wiki.variants.17.value", "Read from a config/*.toml|.json|.properties file")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.18.label", "kjs:key"),
                tr("phoenix_chronicles.wiki.variants.18.value",
                        "Read from config/phoenix_chronicles/kjs_flags.json (KubeJS-writable)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.19.label", "flag:name"),
                tr("phoenix_chronicles.wiki.variants.19.value",
                        "Set once in Java via PhoenixQuestFlags.setFlag(name, bool)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.20",
                "Pick ONE mechanism and use it consistently across every variant's")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.21",
                "condition in the pack - mixing conventions per-quest is how packs end up")));
        lines.add(WLine
                .t(tr("phoenix_chronicles.wiki.variants.22", "with quests that silently never match any variant.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.variants.23", "Resolution rule: first match wins")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.24",
                "A quest's variants list is checked IN ORDER; the first whose condition")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.25",
                "evaluates true is the active one for that check - not the most specific,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.26",
                "not a merge of several matches. If a variant's condition is true, whatever")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.27",
                "field IT sets wins outright; a field it leaves blank falls back to the base")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.28",
                "quest's own value (fields never merge ACROSS variants either - only base")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.29",
                "â†” single matched variant). If nothing matches, the base quest is used")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.30",
                "as-is. Order variants from most to least specific in the list.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.variants.31", "What a variant can override")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.32.label", "title / description"),
                tr("phoenix_chronicles.wiki.variants.32.value", "Replace the base text - leave blank to inherit")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.33.label", "visibility"),
                tr("phoenix_chronicles.wiki.variants.33.value",
                        "NORMAL / HIDDEN / MYSTERY / DISABLED - leave as Inherit to keep base")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.34.label", "tasks"),
                tr("phoenix_chronicles.wiki.variants.34.value",
                        "REPLACES the base task list entirely, not merged - all-or-nothing")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.35.label", "rewards"),
                tr("phoenix_chronicles.wiki.variants.35.value", "REPLACES the base reward list entirely, not merged")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.36",
                "Not overridable per variant: category, shape, icon, position, prerequisites,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.37",
                "repeat mode - those stay the same regardless of which variant is active.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.variants.38", "Editing variants in-game")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.variants.39.label", "\"â—ˆ Variants (N)\" button"), tr(
                "phoenix_chronicles.wiki.variants.39.value", "In the Quest Creator, next to \"âŠž Tasks & Rewards\"")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.40",
                "Opens a list of this quest's variants (+ Add Variant / â–²â–¼ reorder / Ã— delete).")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.41",
                "Selecting one shows: a condition text box, title/description override boxes,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.42",
                "a visibility cycle button, and \"Edit Tasks/Rewardsâ€¦\" - the SAME task/reward")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.43",
                "editor screen used for the base quest, just scoped to that variant. A")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.44",
                "\"Clear task/reward override\" button appears once one is set, to revert")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.45",
                "back to inheriting the base list. There's no separate \"base vs. variant\"")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.46",
                "editing mode - base fields are still edited directly in the Quest Creator;")));
        lines.add(WLine
                .t(tr("phoenix_chronicles.wiki.variants.47", "this screen is purely the overlay list of overrides.")));
        lines.add(WLine.sp());
        lines.add(WLine.div());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.variants.48", "SNBT shape")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.49",
                "Only written at all if the quest has at least one variant:")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.50", "variants: [")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.51",
                "  { condition: \"config:pack_mode.toml#general.mode=expert\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.52", "    title: \"Expert-Only Challenge\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.53", "    visibility: \"HIDDEN\",")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.variants.54", "    tasks: [ {task_id: \"...\", type: \"...\", ...} ],")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.55",
                "    rewards: [ {type: \"item\", item: \"...\", count: 1} ] },")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.56",
                "  { condition: \"kjs:pack_tier>=2\", description: \"Tier 2 flavor text\" }")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.57", "]")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.58",
                "Each block only needs the keys it actually overrides - condition is the")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.variants.59", "only field always written.")));
        return lines;
    }

    private List<WLine> pageRichText() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.rich_text.1", "Rich Text in Descriptions")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.2",
                "Both the SNBT description field and .md files support rich text tags.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.3",
                "The parser handles { and [ only: & is never converted (unlike FTB Quests).")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.4", "Colour")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.5.label", "{#RRGGBB}"),
                tr("phoenix_chronicles.wiki.rich_text.5.value", "Set foreground colour: 6-digit hex, e.g. {#FF4444}")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.6.label", "{reset}"),
                tr("phoenix_chronicles.wiki.rich_text.6.value", "Return to default text colour")));
        lines.add(WLine
                .in(tr("phoenix_chronicles.wiki.rich_text.7", "Example:  {#FFD700}Golden text{reset} back to normal")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.8", "Inline images")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.9.label", "[img:rl,w,h]"),
                tr("phoenix_chronicles.wiki.rich_text.9.value", "Embed a texture inline with the text")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.10",
                "  rl  = resource location, e.g. minecraft:textures/item/diamond.png")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.11",
                "  w,h = pixel dimensions in GUI space (optional, default 16x16)")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rich_text.12",
                "Example:  [img:minecraft:textures/item/diamond.png,16,16]")));
        lines.add(WLine
                .in(tr("phoenix_chronicles.wiki.rich_text.13", "Example:  [img:mymod:textures/gui/banner.png,64,32]")));
        lines.add(WLine.sp());
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.14",
                "Images fall back to the SNBT description field if no .md file is found,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.15",
                "so you can embed textures directly in the quest creator without a .md file.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.16", "Links")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.17.label", "[label](url)"),
                tr("phoenix_chronicles.wiki.rich_text.17.value", "Clickable hyperlink: opens in the system browser")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.18.label", "[label](tip:text)"),
                tr("phoenix_chronicles.wiki.rich_text.18.value",
                        "Tooltip-only reference: shows text on hover, no click")));
        lines.add(WLine
                .in(tr("phoenix_chronicles.wiki.rich_text.19", "Example:  [Phoenix Wiki](https://example.com/wiki)")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rich_text.20",
                "Example:  [Mana Crystal](tip:Dropped by Silverfish in End biomes)")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.21", "Minecraft formatting codes")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.22",
                "§ codes (§l bold, §c red, etc.) work normally in both SNBT and .md files.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.23",
                "& is NOT processed: &wHelloooo passes through literally as written.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.24",
                "This avoids conflicts with other mods that use & for their own purposes.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.25", "Markdown files (.md)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.26",
                "Place a file at:  config/phoenix_chronicles/quests/{quest_id}.md")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.27",
                "The .md file content is shown in the fullscreen quest view. If absent,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.28",
                "the SNBT description field is used instead (including any rich text tags).")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.29",
                "# Heading, ## Subheading, and --- divider are supported in .md files.")));
        lines.add(WLine.sp());

        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.rich_text.30", "Page breaks")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.rich_text.31.label", "---"),
                tr("phoenix_chronicles.wiki.rich_text.31.value",
                        "A line with just 3+ dashes splits the description into pages instead")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rich_text.32",
                "of one continuous scroll - purely opt-in, a description with no marker")));
        lines.add(WLine.in(tr("phoenix_chronicles.wiki.rich_text.33", "behaves exactly as before.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.34",
                "Works in both the SNBT description field and .md files, and shows in both")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.35",
                "the compact card and fullscreen quest views with a Prev/Next pager pill.")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.36",
                "The in-game description editor has a dedicated \"PB\" toolbar button that")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.37",
                "inserts one at the cursor. FTB Quests imports: {@pagebreak} in the source")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.rich_text.38", "description maps to this automatically.")));
        return lines;
    }

    private List<WLine> pageSnbtFormat() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.snbt_format.1", "SNBT File Format")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.2",
                "Each quest is a single .snbt file in config/phoenix_chronicles/quests/")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.3",
                "The file name (without .snbt) becomes the quest id if no id field is present.")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.snbt_format.4", "Minimal quest")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.5", "{id: \"my_quest\", title: \"My Quest\"}")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.snbt_format.6", "Full example")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.7", "{")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.8", "  id: \"magic/first_spell\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.9", "  title: \"First Spell\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.10", "  description: \"Cast your first spell.\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.11", "  subtitle: \"Chapter 1\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.12", "  category: \"MAGIC\",")));
        lines.add(
                WLine.t(tr("phoenix_chronicles.wiki.snbt_format.13", "  shape: \"CIRCLE\",  node_size: \"NORMAL\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.14", "  icon_item: \"minecraft:book\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.15", "  positionX: 120,  positionY: 80,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.16", "  parent: \"magic/intro\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.17", "  visibility: \"NORMAL\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.18", "  repeat_mode: \"NONE\",")));
        lines.add(WLine
                .t(tr("phoenix_chronicles.wiki.snbt_format.19", "  reward_choice: true,  reward_choice_count: 1,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.20", "  auto_claim_rewards: false,")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.21",
                "  dev_notes: \"Placeholder until magic system is done.\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.22",
                "  tasks: [{type: \"checkmark\", task_id: \"phoenixcore:task_1\",")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.23", "           description: \"Cast a spell\"}],")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.24", "  rewards: [")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.25", "    {type: \"xp\", levels: 5},")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.26",
                "    {type: \"item\", item: \"minecraft:book\", count: 1}")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.27", "  ]")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.28", "}")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.snbt_format.29", "Prerequisites list (extended format)")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.30", "prerequisites: [")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.31", "  {id: \"magic/intro\", required: true},")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.32", "  {id: \"magic/side\",  required: false},")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.33", "  {id: \"magic/bad\",   forbidden: true},")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.snbt_format.34", "  {id: \"magic/link\",  required: true, link: true}")));
        lines.add(WLine.t(tr("phoenix_chronicles.wiki.snbt_format.35", "]")));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.snbt_format.36", "File locations")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.37.label", "Quest SNBT"),
                tr("phoenix_chronicles.wiki.snbt_format.37.value",
                        "config/phoenix_chronicles/quests/*.snbt  (any depth)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.38.label", "Quest markdown"),
                tr("phoenix_chronicles.wiki.snbt_format.38.value", "config/phoenix_chronicles/quests/{id}.md")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.39.label", "Categories"),
                tr("phoenix_chronicles.wiki.snbt_format.39.value",
                        "config/phoenix_chronicles/categories.txt  (one per line)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.40.label", "Groups"),
                tr("phoenix_chronicles.wiki.snbt_format.40.value", "config/phoenix_chronicles/quest_groups.json")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.41.label", "Settings"),
                tr("phoenix_chronicles.wiki.snbt_format.41.value", "config/phoenix_chronicles/settings.json")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.43.label", "Engine settings"),
                tr("phoenix_chronicles.wiki.snbt_format.43.value",
                        "config/phoenix_chronicles/engine_settings.snbt  (ae2_storage_for_item_fluid_tasks)")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.snbt_format.42.label", "Tutorial prog."),
                tr("phoenix_chronicles.wiki.snbt_format.42.value", "config/phoenix_chronicles/tutorial_progress.dat")));
        return lines;
    }

    private List<WLine> pageLiveStats() {
        var lines = new ArrayList<WLine>();
        lines.add(WLine.h(tr("phoenix_chronicles.wiki.live_stats.1", "Live Registry Stats")));
        lines.add(WLine.t(
                tr("phoenix_chronicles.wiki.live_stats.2", "Data pulled from the in-memory registry at render time.")));
        lines.add(WLine.sp());

        Map<String, List<QuestNode>> byCat = new LinkedHashMap<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            byCat.computeIfAbsent(n.getCategory(), k -> new ArrayList<>()).add(n);
        }

        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.live_stats.3.label", "Total quests:"),
                String.valueOf(QuestTreeRegistry.getAllQuests().size())));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.live_stats.4.label", "Categories:"),
                String.valueOf(byCat.size())));
        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.live_stats.5", "Per-category")));
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
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.live_stats.6", "Task types")));
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.live_stats.7.label", "Registered:"),
                String.valueOf(PhoenixTaskRegistry.getEditorTypes().size())));

        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.live_stats.8", "Repeat modes in use")));
        Map<QuestNode.RepeatMode, Long> repeatCounts = new LinkedHashMap<>();
        for (QuestNode.RepeatMode m : QuestNode.RepeatMode.values()) repeatCounts.put(m, 0L);
        QuestTreeRegistry.getAllQuests().values()
                .forEach(n -> repeatCounts.merge(n.getRepeatMode(), 1L, Long::sum));
        repeatCounts.forEach((mode, count) -> {
            if (count > 0) lines.add(WLine.kv(mode.name() + ":", count + " quest" + (count == 1 ? "" : "s")));
        });

        lines.add(WLine.sp());
        lines.add(WLine.sh(tr("phoenix_chronicles.wiki.live_stats.9", "Quests with tutorials")));
        long tutCount = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> !n.getTutorialSteps().isEmpty()).count();
        lines.add(WLine.kv(tr("phoenix_chronicles.wiki.live_stats.10.label", "Total:"),
                tutCount + " quest" + (tutCount == 1 ? "" : "s") + " have tutorial steps"));

        lines.add(WLine.sp());
        lines.add(WLine.div());
        List<String> errs = QuestFileLoader.LOAD_ERRORS;
        if (errs.isEmpty()) {
            lines.add(WLine.sh(tr("phoenix_chronicles.wiki.live_stats.11", "§aLoad errors: none")));
        } else {
            lines.add(WLine.sh("§c" + tr("phoenix_chronicles.wiki.live_stats.13", "Load errors:") + " " + errs.size()));
            lines.add(WLine.t(
                    tr("phoenix_chronicles.wiki.live_stats.12", "Run /chronicles validate in-game for full output.")));
            for (String e : errs) lines.add(WLine.t("âœ— " + e));
        }

        return lines;
    }

    private List<WLine> pageApiReference() {
        var L = new ArrayList<WLine>();
        L.add(WLine.h(tr("phoenix_chronicles.wiki.api_reference.1", "API Reference")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.2", "Click âŽ˜ to copy any snippet to clipboard.")));
        L.add(WLine.sp());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.3",
                "QuestAPI  (Java: net.phoenixvine.chronicles.QuestAPI)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.4",
                "The single entry point for other mods/scripts to read or push quest state.")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.5",
                "Every method here is safe to call with a bad/unknown quest ID or a null")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.6",
                "player - it logs a warning once (not spammed) and returns a safe default")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.7",
                "instead of throwing, so a typo doesn't silently fail with no explanation.")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.8.label", "fireExternalEvent"),
                tr("phoenix_chronicles.wiki.api_reference.8.value",
                        "Advance any external_trigger task listening for this id (server-side)")));
        L.add(WLine.code(
                "QuestAPI.fireExternalEvent(serverPlayer, \"mymod:killed_dragon\", null);"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.9.label", "forceComplete"),
                tr("phoenix_chronicles.wiki.api_reference.9.value",
                        "Force a quest straight to COMPLETED, bypassing its tasks (server-side)")));
        L.add(WLine.code("QuestAPI.forceComplete(serverPlayer, \"phoenixcore:my_quest\");"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.10.label", "setState"),
                tr("phoenix_chronicles.wiki.api_reference.10.value",
                        "Force a quest to any state, bypassing tasks/prereqs (server-side)")));
        L.add(WLine.code("QuestAPI.setState(serverPlayer, \"phoenixcore:my_quest\", QuestState.LOCKED);"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.11.label", "getState"),
                tr("phoenix_chronicles.wiki.api_reference.11.value",
                        "Read a player's current state for a quest - callable either side")));
        L.add(WLine.code("QuestState state = QuestAPI.getState(player, \"phoenixcore:my_quest\");"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.12.label", "getAllStates"),
                tr("phoenix_chronicles.wiki.api_reference.12.value",
                        "Every quest state for this player, keyed by ID - a snapshot map")));
        L.add(WLine.code("Map<ResourceLocation, QuestState> all = QuestAPI.getAllStates(player);"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.13.label", "isCompleted / isUnlocked"),
                tr("phoenix_chronicles.wiki.api_reference.13.value", "Convenience booleans built on getState")));
        L.add(WLine.code(
                "boolean done = QuestAPI.isCompleted(player, \"phoenixcore:my_quest\");"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.14.label", "getProgress"),
                tr("phoenix_chronicles.wiki.api_reference.14.value",
                        "0.0-1.0 fraction of non-optional tasks done (1.0 if already COMPLETED)")));
        L.add(WLine.code("float pct = QuestAPI.getProgress(player, \"phoenixcore:my_quest\");"));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.15",
                "Forge event hooks  (Java: net.phoenixvine.chronicles.event.QuestEvent)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.16",
                "All nested inside QuestEvent except PhoenixQuestScriptRewardEvent below, which")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.17",
                "is its own top-level class. getPlayer()/getNode() are on the QuestEvent base.")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.18.label", "QuestEvent.StateChanged"),
                tr("phoenix_chronicles.wiki.api_reference.18.value",
                        "Fired right after a quest's state actually changed")));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onStateChanged(QuestEvent.StateChanged e) {"));
        L.add(WLine.code("    if (e.getNewState() == QuestState.COMPLETED) {"));
        L.add(WLine.code("        ServerPlayer p = (ServerPlayer) e.getPlayer();"));
        L.add(WLine.code("        ResourceLocation id = e.getNode().getId();"));
        L.add(WLine.code("    }"));
        L.add(WLine.code("}"));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.19.label", "QuestEvent.RewardClaimed"),
                tr("phoenix_chronicles.wiki.api_reference.19.value",
                        "Cancelable - fired once rewards for a quest are granted")));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onReward(QuestEvent.RewardClaimed e) { â€¦ }  // cancel() to veto the grant"));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.20.label", "QuestEvent.ExternalEvent"),
                tr("phoenix_chronicles.wiki.api_reference.20.value",
                        "Cancelable - fired when QuestAPI.fireExternalEvent() runs")));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onExternal(QuestEvent.ExternalEvent e) {"));
        L.add(WLine.code("    String triggerId = e.getTriggerId();"));
        L.add(WLine.code("    CompoundTag data = e.getData();  // empty tag if none was passed"));
        L.add(WLine.code("}"));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.21.label", "QuestEvent.PlayerTick"),
                tr("phoenix_chronicles.wiki.api_reference.21.value",
                        "Cancelable - cancel to suppress default task checks this tick")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.22.label", "QuestEvent.PinChanged"),
                tr("phoenix_chronicles.wiki.api_reference.22.value",
                        "Fired when a player pins/unpins a quest on the HUD tracker")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.23.label", "QuestEvent.TreeReloaded"),
                tr("phoenix_chronicles.wiki.api_reference.23.value",
                        "Quest tree (re)loaded from disk - getPlayer()/getNode() are null")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.24.label", "PhoenixQuestScriptRewardEvent"),
                tr("phoenix_chronicles.wiki.api_reference.24.value",
                        "Fired by a script_event reward - carries custom data")));
        L.add(WLine.code("@SubscribeEvent"));
        L.add(WLine.code("public void onScriptReward(PhoenixQuestScriptRewardEvent e) {"));
        L.add(WLine.code("    String evtId = e.getEventId();      // matches event_id in SNBT"));
        L.add(WLine.code("    CompoundTag data = e.getData();     // optional NBT payload"));
        L.add(WLine.code("    ServerPlayer p = e.getServerPlayer();"));
        L.add(WLine.code("}"));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.25",
                "Custom task type  (Java - full QuestTask subclass)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.26",
                "Implement QuestTask, then register a builder in your mod's common setup:")));
        L.add(WLine.code("PhoenixTaskRegistry.register(\"mymod:eat_sun\", tag -> new EatSunTask(tag))"));
        L.add(WLine.code("        .label(\"Eat the Sun\")"));
        L.add(WLine.code("        .icon(\"§câ˜€\")"));
        L.add(WLine.code("        .tooltip(\"Eat a star.\\nTarget: star registry id.\")"));
        L.add(WLine.code("        .field(PhoenixTaskRegistry.FieldDef.itemId(\"target\", \"Star\"))"));
        L.add(WLine.code("        .register();"));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.27",
                ".field(...) entries drive the quest editor's auto-generated form for this")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.28",
                "task type - skip it and the editor just won't show dedicated fields for it.")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.86",
                "This registers a real Java subclass, so a JS script can't satisfy it directly -")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.87",
                "for a script-only custom task type with genuine per-player completion logic,")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.88",
                "use registerScripted below instead - no Java class needed at all.")));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.89",
                "Custom task type  (KubeJS - real script task, no Java mod needed)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.90",
                "registerScripted() hands back the same Builder as above, but backed by a")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.91",
                "script-driven task under the hood - your callbacks run fresh for every")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.92",
                "completion check, not just a fire-and-increment counter like the JSON bridge's")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.93",
                "external_trigger tasks below - this is genuine custom completion logic.")));
        L.add(WLine.code("PhoenixTaskRegistry.registerScripted('mypack:eat_sun')"));
        L.add(WLine.code("  .onCompleted((task, player) => {"));
        L.add(WLine.code("    // task.getData() is the raw SNBT tag for this quest's copy of the task -"));
        L.add(WLine.code("    // read whatever custom fields you declared with .field(...) below"));
        L.add(WLine.code("    return player.getPersistentData().getInt('suns_eaten')"));
        L.add(WLine.code("        >= task.getData().getInt('count')"));
        L.add(WLine.code("  })"));
        L.add(WLine.code("  .onConsume((task, player) => {"));
        L.add(WLine.code("    // optional - runs once when the player claims this quest's rewards"));
        L.add(WLine.code("  })"));
        L.add(WLine.code("  .progressString((task, player) => {"));
        L.add(WLine.code("    // optional - return null to fall back to a plain Done/Pending label"));
        L.add(WLine.code(
                "    return `${player.getPersistentData().getInt('suns_eaten')}/${task.getData().getInt('count')}`"));
        L.add(WLine.code("  })"));
        L.add(WLine.code("  .dependsOnInventory(false)  // default true - set false unless completion"));
        L.add(WLine.code("                              // can ONLY change when the player's items do"));
        L.add(WLine.code("  .label('Eat the Sun').icon('§câ˜€').tooltip('Eat a star.')"));
        L.add(WLine.code("  .field(PhoenixTaskRegistry.FieldDef.integer('count', 'Suns to eat'))"));
        L.add(WLine.code("  .register()"));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.94",
                "onCompleted is required; onConsume/progressString/dependsOnInventory are all")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.95",
                "optional and default to no-op/null/true respectively. Call this from a")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.96",
                "startup_script - PhoenixTaskRegistry is already a global binding, no")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.97",
                "Java.loadClass needed.")));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.29", "Custom enable_if flags  (Java)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.30",
                "enable_if expressions: comma = AND, pipe = OR (lower precedence than AND,")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.31",
                "so \"a,b|c,d\" = (a AND b) OR (c AND d)), ! negates a single term.")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.32",
                "A bare name checks the static/dynamic registry below; prefixed forms route to")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.33",
                "a built-in provider: mod:modid, rule:gameRuleName[ op value], config:file#key")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.34",
                "[= val], kjs:key. Comparison ops (rule:/config: only): = != > >= < <=")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.35",
                "enable_if: \"expert_mode\"                    (plain registered flag)")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.36",
                "enable_if: \"!hardcore,mod:refinedstorage\"    (NOT hardcore AND RS loaded)")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.37",
                "enable_if: \"rule:doDaylightCycle=false\"      (game rule comparison)")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.38.label", "setFlag"),
                tr("phoenix_chronicles.wiki.api_reference.38.value",
                        "Static value, set once (e.g. a pack-mode read at startup)")));
        L.add(WLine.code("PhoenixQuestFlags.setFlag(\"expert_mode\", true);"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.39.label", "registerCondition"),
                tr("phoenix_chronicles.wiki.api_reference.39.value",
                        "Dynamic - re-evaluated on every check, keep it cheap")));
        L.add(WLine.code("PhoenixQuestFlags.registerCondition(\"has_rs\","));
        L.add(WLine.code("        () -> ModList.get().isLoaded(\"refinedstorage\"));"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.40.label", "registerProvider"),
                tr("phoenix_chronicles.wiki.api_reference.40.value",
                        "Adds a whole new prefix namespace (your own PREFIX:expr syntax)")));
        L.add(WLine.code("PhoenixQuestFlags.registerProvider(new MyCustomProvider());"));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.41",
                "Unknown plain flag names default to TRUE (with a one-time console warning) so")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.42",
                "a typo doesn't silently hide a quest; unknown prefixes default to FALSE.")));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.43",
                "KubeJS - real plugin (PhoenixChroniclesKubeJSPlugin)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.44",
                "A genuine KubeJSPlugin (kubejs-forge is a compileOnly build dependency now,")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.45",
                "registered via kubejs.plugins.txt) allowlists QuestAPI, QuestState, QuestNode,")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.46",
                "QuestEvent, PhoenixQuestScriptRewardEvent, QuestTask, PhoenixTaskRegistry (+")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.47",
                "Builder/FieldDef), and PhoenixQuestFlags for script access, and adds three")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.48",
                "global bindings so scripts skip the Java.loadClass boilerplate entirely:")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.49.label", "QuestAPI"),
                tr("phoenix_chronicles.wiki.api_reference.49.value", "same methods as the Java API page above")));
        L.add(WLine.code("QuestAPI.fireExternalEvent(player, 'my_trigger_id', null)"));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.50.label", "PhoenixTaskRegistry"),
                tr("phoenix_chronicles.wiki.api_reference.50.value",
                        "only useful from script for an already-Java-defined task type -")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.51",
                "QuestTask is an abstract class with multiple abstract methods, not")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.52",
                "something a plain JS object can satisfy; see the JSON bridge below instead")));
        L.add(WLine.in(tr("phoenix_chronicles.wiki.api_reference.53", "for a script-only custom task type.")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.54.label", "PhoenixQuestFlags"),
                tr("phoenix_chronicles.wiki.api_reference.54.value",
                        "setFlag/registerCondition/registerProvider, same as the Java API")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.55",
                "The mod itself never touches this plugin class - KubeJS only instantiates it")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.56",
                "if KubeJS is actually installed, so nothing here requires KubeJS to be present.")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.57.label", "Listen for a quest state change"),
                tr("phoenix_chronicles.wiki.api_reference.57.value",
                        "server_scripts (standard Forge event, no bridge needed)")));
        L.add(WLine.code("ForgeEvents.onEvent("));
        L.add(WLine.code("  'net.phoenixvine.chronicles.event.QuestEvent$StateChanged', event => {"));
        L.add(WLine.code("    if (event.newState == 'COMPLETED') {"));
        L.add(WLine.code("      let id = event.node.id.path  // e.g. 'magic/first_spell'"));
        L.add(WLine.code("    }"));
        L.add(WLine.code("  })"));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.58.label", "Listen for a script_event reward"),
                tr("phoenix_chronicles.wiki.api_reference.58.value", "server_scripts")));
        L.add(WLine.code("ForgeEvents.onEvent("));
        L.add(WLine.code("  'net.phoenixvine.chronicles.event.PhoenixQuestScriptRewardEvent', event => {"));
        L.add(WLine.code("    if (event.eventId === 'my_event') {"));
        L.add(WLine.code("      event.serverPlayer.tell('Reward fired!')"));
        L.add(WLine.code("    }"));
        L.add(WLine.code("  })"));
        L.add(WLine.sp());
        L.add(WLine.div());

        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.59",
                "KubeJS - JSON bridges  (config file only, no plugin/Java needed at all)")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.60",
                "These two work even WITHOUT the plugin above and even without KubeJS itself -")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.61",
                "any script/tool that can write a JSON file to config/phoenix_chronicles/ can")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.62",
                "use them, since they're read straight off disk at (re)load, no scripting")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.63", "runtime involved on this mod's side at all.")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.98",
                "Simpler than registerScripted above, but the tradeoff is completion is always")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.99",
                "counter-based (fire an event N times) - use registerScripted instead if you")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.100",
                "need a live per-player check (network storage, a stat threshold, etc.).")));
        L.add(WLine.sp());
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.64.label", "Custom task type from script"),
                tr("phoenix_chronicles.wiki.api_reference.64.value", "config/phoenix_chronicles/kjs_task_types.json")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.65",
                "Every KJS-defined type is backed by ExternalTriggerTask under the hood (the")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.66",
                "one built-in task class designed for exactly this - script decides when it's")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.67",
                "done via QuestAPI.fireExternalEvent), but shows up in the quest editor's type")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.68",
                "dropdown under its OWN name/icon/tooltip/fields, indistinguishable from a")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.69", "real Java-registered type.")));
        L.add(WLine.code("const taskTypes = [{"));
        L.add(WLine.code("  type_id: 'mypack:sun_eaten', label: 'Eat the Sun', icon: '§câ˜€',"));
        L.add(WLine.code("  tooltip: 'Complete after eating a star.', default_trigger_id: 'mypack:sun_eaten',"));
        L.add(WLine.code("  fields: [{id: 'required', label: 'Times', type: 'integer'}]"));
        L.add(WLine.code("}]"));
        L.add(WLine.code("const file = new java.io.File('config/phoenix_chronicles/kjs_task_types.json')"));
        L.add(WLine.code("file.getParentFile().mkdirs()"));
        L.add(WLine.code("file.text = JSON.stringify(taskTypes, null, 2)"));
        L.add(WLine.sp());
        L.add(WLine.kv(
                tr("phoenix_chronicles.wiki.api_reference.70.label", "Export a flag value for enable_if: \"kjs:...\""),
                tr("phoenix_chronicles.wiki.api_reference.70.value", "config/phoenix_chronicles/kjs_flags.json")));
        L.add(WLine.code("const file = new java.io.File('config/phoenix_chronicles/kjs_flags.json')"));
        L.add(WLine.code("file.getParentFile().mkdirs()"));
        L.add(WLine.code("file.text = JSON.stringify({ expert_mode: Platform.isLoaded('somemod') }, null, 2)"));
        L.add(WLine.t(
                tr("phoenix_chronicles.wiki.api_reference.71", "Then in quest SNBT:  enable_if: \"kjs:expert_mode\"")));
        L.add(WLine.sp());
        L.add(WLine.div());
        L.add(WLine.sh(tr("phoenix_chronicles.wiki.api_reference.72", "In-game commands")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.73.label", "/chronicles status <id>"),
                tr("phoenix_chronicles.wiki.api_reference.73.value", "Any player - check your own quest state")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.74.label", "/chronicles emergency <id>"),
                tr("phoenix_chronicles.wiki.api_reference.74.value",
                        "Any player - get emergency items while that quest is ACTIVE")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.75.label", "/chronicles complete <id> [player]"),
                tr("phoenix_chronicles.wiki.api_reference.75.value", "Op - force-complete a quest")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.76.label", "/chronicles unlock <id> [player]"),
                tr("phoenix_chronicles.wiki.api_reference.76.value", "Op - bypass prerequisites, set UNLOCKED")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.77.label", "/chronicles active <id> [player]"),
                tr("phoenix_chronicles.wiki.api_reference.77.value", "Op - force-start a quest (state ACTIVE)")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.78.label", "/chronicles reset <id> [player]"),
                tr("phoenix_chronicles.wiki.api_reference.78.value",
                        "Op - full reset: state, task progress, claimed rewards")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.79.label", "/chronicles reload"),
                tr("phoenix_chronicles.wiki.api_reference.79.value",
                        "Op - hot-reload quests from config/, sync to all online players")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.80.label", "/chronicles export"),
                tr("phoenix_chronicles.wiki.api_reference.80.value",
                        "Op - snapshot every loaded quest to a timestamped export/ folder")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.81.label", "/chronicles import [subfolder]"),
                tr("phoenix_chronicles.wiki.api_reference.81.value",
                        "Op - additive load from a subfolder (default \"import\")")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.82.label", "/chronicles import-ftb [subfolder]"),
                tr("phoenix_chronicles.wiki.api_reference.82.value",
                        "Op - import an FTB Quests pack (default \"ftb_import\")")));
        L.add(WLine.kv(tr("phoenix_chronicles.wiki.api_reference.83.label", "/chronicles validate"), tr(
                "phoenix_chronicles.wiki.api_reference.83.value", "Op - report load errors and common config issues")));
        L.add(WLine.t(tr("phoenix_chronicles.wiki.api_reference.84",
                "[player] arguments default to yourself when omitted; specify one to target")));
        L.add(WLine.t(
                tr("phoenix_chronicles.wiki.api_reference.85", "another online player instead (e.g. from console).")));

        return L;
    }

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

