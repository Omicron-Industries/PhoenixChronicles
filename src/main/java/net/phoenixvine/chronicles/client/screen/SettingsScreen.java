package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings.*;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings screen for Quest Chronicles.
 *
 * Categorized (left sidebar) instead of one long flat scroll - as more systems became
 * configurable this session, a single list was heading toward the same unmanageable sprawl this
 * pack's other screens have needed rework for. Each category's rows are built once, into a
 * {@link Row} list with its Y position computed right there - render() and mouseClicked() both
 * read from that same list, instead of (as before) independently re-deriving matching "y += ROW_H"
 * arithmetic in two places, which is exactly the class of bug that bit the Toast Designer and
 * Variant editor screens earlier (a label/row landing behind or overlapping its neighbor because
 * the two copies of the arithmetic drifted out of sync).
 */
public class SettingsScreen extends Screen {

    private int C_BG = 0xFF0B0B0F;
    private int C_PANEL = 0xFF14141A;
    private int C_HEADER = 0xFF0C0C10;
    private int C_BORDER = 0xFF353548;
    private int C_ACCENT = 0xFF00AA55;
    private int C_TEXT = 0xFFD8D8E4;
    private int C_TEXT_DIM = 0xFF7A7A8A;
    private int C_TEXT_FAINT = 0xFF404050;
    private int C_OK = 0xFF44CC88;
    private static final int C_CANCEL = 0xFF888898;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 8;
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 4;
    private static final int LABEL_LINE_H = 10;
    private static final int SIDEBAR_W = 128;
    private static final int PANEL_W = 380;
    private static final int ARROW_W = 18;
    private static final int ARROW_GAP = 2;

    private enum Category {

        GENERAL("General"),
        HUD("HUD"),
        POPUPS("Pop-Ups"),
        CANVAS("Canvas"),
        PHANTASIA("Phantasia"),
        INVENTORY("Inventory"),
        DEV("Dev Mode");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private enum RowType {
        TOGGLE,
        CYCLE,
        LINK,
        INFO
    }

    /**
     * One settings row. Built fresh (with its Y already assigned) whenever the category changes
     * or a value in it is edited, so render() and click-handling always agree on where every row
     * actually is - there is no second copy of this layout math anywhere else in this class.
     */
    private static final class Row {

        final RowType type;
        final String label;
        final Supplier<String> valueFn;
        final Runnable onLeft, onRight; // CYCLE/TOGGLE: onLeft/onRight both toggle for TOGGLE rows
        final Runnable onClick; // LINK rows
        int y;
        int height = ROW_H;
        String tooltip; // null = no tooltip; set via .tip(...) after construction

        Row(RowType type, String label, Supplier<String> valueFn, Runnable onLeft, Runnable onRight,
            Runnable onClick) {
            this.type = type;
            this.label = label;
            this.valueFn = valueFn;
            this.onLeft = onLeft;
            this.onRight = onRight;
            this.onClick = onClick;
        }

        /** Attaches a hover tooltip; chain onto any factory method. Wrap long text with \n. */
        Row tip(String text) {
            this.tooltip = text;
            return this;
        }

        static Row toggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            Runnable flip = () -> setter.accept(!getter.get());
            return new Row(RowType.TOGGLE, label, () -> getter.get() ? "§aYes" : "§cNo", flip, flip, null);
        }

        static <E extends Enum<E>> Row cycle(String label, Class<E> cls, Supplier<E> getter, Consumer<E> setter) {
            E[] vals = cls.getEnumConstants();
            Runnable left = () -> setter.accept(vals[(getter.get().ordinal() - 1 + vals.length) % vals.length]);
            Runnable right = () -> setter.accept(vals[(getter.get().ordinal() + 1) % vals.length]);
            return new Row(RowType.CYCLE, label, () -> getter.get().name(), left, right, null);
        }

        static Row intCycle(String label, int[] options, Supplier<Integer> getter, Consumer<Integer> setter) {
            Runnable left = () -> setter.accept(options[(indexOf(options, getter.get()) - 1 + options.length) %
                    options.length]);
            Runnable right = () -> setter.accept(options[(indexOf(options, getter.get()) + 1) % options.length]);
            return new Row(RowType.CYCLE, label, () -> String.valueOf(getter.get()), left, right, null);
        }

        private static int indexOf(int[] arr, int v) {
            for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
            return 0;
        }

        static Row link(String label, Runnable onClick) {
            return new Row(RowType.LINK, label, () -> "→", null, null, onClick);
        }

        /** A plain non-interactive info line - may wrap onto more than one visual line. */
        static Row info(String text, int lineCount) {
            Row r = new Row(RowType.INFO, text, null, null, null, null);
            r.height = lineCount * LABEL_LINE_H;
            return r;
        }
    }

    private final Screen parent;
    private final QuestChroniclesSettings settings;
    private Category selectedCategory = Category.GENERAL;
    private List<Row> rows = new ArrayList<>();
    private int scrollY = 0;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Chronicles Settings"));
        this.parent = parent;
        this.settings = QuestChroniclesSettings.load();
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
        C_OK = t.done.getColor();
        rebuildRows();
    }

    private void selectCategory(Category cat) {
        selectedCategory = cat;
        scrollY = 0;
        rebuildRows();
    }

    /** Builds the row list (with Y positions assigned) for whichever category is selected. */
    private void rebuildRows() {
        rows = new ArrayList<>();
        int y = HEADER_H + MARGIN;

        switch (selectedCategory) {
            case GENERAL -> {
                rows.add(Row.cycle("§fText Scale", TextScale.class, settings::getTextScale, settings::setTextScale)
                        .tip("Scales all quest text and UI labels up or down."));
                rows.add(Row.cycle("§fTheme", Theme.class, settings::getTheme, settings::setTheme)
                        .tip("Switches the built-in color theme. Use the Theme Editor below to\ncreate or tweak your own."));
                rows.add(Row.cycle("§fLayout Density", Density.class, settings::getDensity, settings::setDensity)
                        .tip("Controls spacing between quest nodes and UI rows -\ntighter for more on screen, looser for readability."));
                rows.add(Row.toggle("§fReduce Motion", settings::isReduceMotion, settings::setReduceMotion)
                        .tip("Freezes blinking/pulsing effects and animated dependency lines\n(validation warnings, unclaimed rewards, ACTIVE glow, etc.)."));
                rows.add(Row.toggle("§fReturn to Quest Book from Recipe Viewer",
                        settings::isReturnToQuestbookFromRecipeViewer,
                        settings::setReturnToQuestbookFromRecipeViewer)
                        .tip("When opening EMI from a task/reward icon, closing EMI brings you\nback to the quest book instead of EMI's own default (a throwaway\ninventory screen, then straight to gameplay)."));
                rows.add(Row.cycle("§fSidebar Behavior", SidebarBehavior.class, settings::getSidebarBehavior,
                        settings::setSidebarBehavior)
                        .tip("COLLAPSIBLE: click the small arrow to pin the sidebar open/closed.\n" +
                                "HOVER_TO_EXPAND: FTB Quests-style - always collapsed, moving the\n" +
                                "mouse over it opens it, moving away closes it - no clicking needed."));
                rows.add(Row.link("§fTheme Editor",
                        () -> {
                            if (minecraft != null) minecraft.setScreen(new ChroniclesThemeEditorScreen(this));
                        })
                        .tip("Opens the full color editor - customize every panel, text, and\nstate color, then save it as a named theme."));
                rows.add(Row.info("§8Keybinds: Minecraft's own Options → Controls → Phoenix Chronicles", 1));
            }
            case HUD -> {
                rows.add(Row.cycle("§fHUD Position", HUDPosition.class, settings::getHudPosition,
                        settings::setHudPosition)
                        .tip("Where the persistent quest-tracker HUD sits on screen\nwhile you're out playing, not in the quest book."));
                rows.add(new Row(RowType.CYCLE, "§fHUD Opacity",
                        () -> String.format("%.0f%%", settings.getHudOpacity() * 100),
                        () -> settings.setHudOpacity(settings.getHudOpacity() - 0.1f),
                        () -> settings.setHudOpacity(settings.getHudOpacity() + 0.1f), null)
                        .tip("Background transparency of the HUD tracker."));
                rows.add(Row.toggle("§fShow HUD Title", settings::isShowHUDTitle, settings::setShowHUDTitle)
                        .tip("Shows the pinned quest's name on the HUD."));
                rows.add(Row.toggle("§fShow HUD Progress", settings::isShowHUDProgress, settings::setShowHUDProgress)
                        .tip("Shows task completion progress (e.g. 2/5) on the HUD."));
                rows.add(Row.toggle("§fShow HUD Rewards", settings::isShowHUDRewards, settings::setShowHUDRewards)
                        .tip("Shows a preview of unclaimed reward icons on the HUD."));
            }
            case POPUPS -> {
                rows.add(Row.toggle("§fShow Pop-Ups", settings::isShowToasts, settings::setShowToasts)
                        .tip("Master switch - turn off to silence every quest pop-up,\nregardless of the settings below."));
                rows.add(Row.cycle("§fPop-Up Style", ToastStyle.class, settings::getToastStyle,
                        settings::setToastStyle).tip("Overall visual style of the quest completion/unlock pop-up."));
                rows.add(Row.cycle("§fPop-Up Position", HUDPosition.class, settings::getToastPosition,
                        settings::setToastPosition).tip("Corner of the screen pop-ups slide in from."));
                rows.add(Row.toggle("§fPlay Pop-Up Sounds", settings::isPlayToastSounds, settings::setPlayToastSounds)
                        .tip("Plays a sound alongside quest unlock/completion pop-ups."));
                rows.add(Row.info("§8Individual pop-ups: right-click a quest → Design Pop-Up", 1));
            }
            case CANVAS -> {
                rows.add(Row.toggle("§fHide Completed by Default", settings::isHideCompletedByDefault,
                        settings::setHideCompletedByDefault)
                        .tip("Newly opened quest books start with completed quests\nhidden from the canvas."));
                rows.add(Row.intCycle("§fDefault Grid Snap", new int[] { 1, 2, 4, 8, 16, 32 },
                        settings::getDefaultGridSnap, settings::setDefaultGridSnap)
                        .tip("Default grid size new quest editors snap node dragging to."));
                rows.add(Row.cycle("§fDependency Line Style", LineStyle.class, settings::getLineStyle,
                        settings::setLineStyle)
                        .tip("Shape of the lines connecting prerequisite quests\n(straight, curved, elbow, etc.)."));
                rows.add(Row.cycle("§fLine Visual Style", LineVisualStyle.class, settings::getLineVisualStyle,
                        settings::setLineVisualStyle)
                        .tip("Rendering treatment for dependency lines - solid, dashed,\nhollow rail, etc."));
                rows.add(Row.cycle("§fLine Animation Speed", LineAnimSpeed.class, settings::getLineAnimSpeed,
                        settings::setLineAnimSpeed)
                        .tip("How fast the marching-ants/spark animation travels along\nactive dependency lines. Ignored if Reduce Motion is on."));
                rows.add(Row.toggle("§fShow Line Arrows", settings::isShowLineArrows, settings::setShowLineArrows)
                        .tip("Draws directional arrowheads on dependency lines."));
                rows.add(Row.info("§8These can also be changed via right-click on the quest canvas.", 1));
            }
            case PHANTASIA -> rows.add(Row.toggle("§fAuto-Spin Previews", settings::isPhantasiaAutoSpin,
                    settings::setPhantasiaAutoSpin)
                    .tip("Slowly rotates embedded Phantasia multiblock/scene previews\n(quest nodes, toasts) instead of holding them still."));
            case INVENTORY -> {
                rows.add(Row.toggle("§fShow in Inventory", settings::isShowInventoryButton,
                        settings::setShowInventoryButton)
                        .tip("Adds a button to the player inventory screen for quickly\nopening the quest book."));
                rows.add(Row.cycle("§fButton Position", InvButtonPos.class, settings::getInvButtonPos,
                        settings::setInvButtonPos).tip("Corner of the inventory screen the button is anchored to."));
            }
            case DEV -> {
                // Saved immediately (not just on the footer Save button) since closing this
                // screen any other way (Escape, clicking away) previously discarded the toggle
                // silently, making it look like the choice didn't persist across restarts.
                rows.add(Row.toggle("§fDev Mode Enabled", () -> !settings.isDevModeDisabled(), on -> {
                    settings.setDevModeDisabled(!on);
                    settings.save();
                }).tip("Unlocks debug-only canvas tools (Stats/Validation panels,\nSubgraph mode, FTB import, Dev Wiki) for creative/op players."));
                rows.add(Row.toggle("§fShow Dev Info by Default", settings::isShowDevInfoByDefault,
                        settings::setShowDevInfoByDefault)
                        .tip("Opens quest editors with dev-only fields expanded by default."));
                rows.add(Row.info(
                        "§8Off by default - click to opt in. Still only takes effect if you're\n" +
                                "§8creative or op level 2+; this can't grant dev tools by itself.",
                        2));
            }
        }

        for (Row r : rows) {
            r.y = y;
            y += r.height + ROW_GAP;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        // Header
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, 0, width, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.drawCenteredString(font, "§fChronicles Settings", width / 2, 9, C_TEXT);

        int contentTop = HEADER_H;
        int contentBottom = height - FOOTER_H;

        // Sidebar
        g.fill(0, contentTop, SIDEBAR_W, contentBottom, C_PANEL);
        g.fill(SIDEBAR_W - 1, contentTop, SIDEBAR_W, contentBottom, C_BORDER);
        int sy = contentTop + MARGIN;
        for (Category cat : Category.values()) {
            if (cat == Category.PHANTASIA && !PhantasiaCompat.isAvailable()) continue;
            boolean sel = cat == selectedCategory;
            boolean hov = mx >= 0 && mx < SIDEBAR_W && my >= sy && my < sy + ROW_H;
            if (sel) {
                g.fill(0, sy, SIDEBAR_W - 1, sy + ROW_H, 0x22FFFFFF);
                g.fill(0, sy, 2, sy + ROW_H, C_ACCENT);
            } else if (hov) {
                g.fill(0, sy, SIDEBAR_W - 1, sy + ROW_H, 0x10FFFFFF);
            }
            g.drawString(font, (sel ? "§f" : "§7") + cat.label, MARGIN, sy + (ROW_H - 8) / 2,
                    sel ? C_TEXT : C_TEXT_DIM, false);
            sy += ROW_H;
        }

        // Content area
        int x = SIDEBAR_W + MARGIN;
        int w = Math.min(PANEL_W, width - x - MARGIN);
        g.enableScissor(x, contentTop, x + w, contentBottom);

        String hoveredTooltip = null;
        for (Row r : rows) {
            int ry = r.y - scrollY;
            if (ry + r.height < contentTop || ry > contentBottom) continue;
            switch (r.type) {
                case INFO -> {
                    int ly = ry;
                    for (String line : r.label.split("\n")) {
                        g.drawString(font, line, x, ly, C_TEXT_FAINT, false);
                        ly += LABEL_LINE_H;
                    }
                }
                case LINK -> {
                    boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
                    if (hov) g.fill(x, ry, x + w, ry + ROW_H, 0x10FFFFFF);
                    int textY = ry + (ROW_H - 8) / 2;
                    g.drawString(font, r.label, x + 4, textY, C_TEXT, false);
                    g.drawCenteredString(font, "§7→", x + w - ARROW_W / 2, textY, hov ? C_ACCENT : C_TEXT_DIM);
                }
                default -> renderValueRow(g, x, ry, w, r, mx, my);
            }
            if (r.tooltip != null && mx >= x && mx < x + w && my >= ry && my < ry + r.height) {
                hoveredTooltip = r.tooltip;
            }
        }
        g.disableScissor();

        // Footer
        int footerY = height - FOOTER_H;
        g.fill(0, footerY, width, height, C_HEADER);
        g.fill(0, footerY, width, footerY + 1, C_BORDER);

        int btnW = 100;
        int btnGap = 8;
        int btnY = footerY + 5;

        boolean saveHov = mx >= width / 2 - btnW - btnGap / 2 && mx < width / 2 - btnGap / 2 && my >= btnY &&
                my < btnY + 18;
        g.fill(width / 2 - btnW - btnGap / 2, btnY, width / 2 - btnGap / 2, btnY + 18,
                saveHov ? 0xFF2A4A2A : 0xFF1A2A1A);
        if (saveHov) g.fill(width / 2 - btnW - btnGap / 2, btnY, width / 2 - btnGap / 2, btnY + 1, C_OK);
        g.drawCenteredString(font, "§a✓ Save", width / 2 - btnW / 2 - btnGap / 2, btnY + 6, saveHov ? C_OK : C_TEXT);

        boolean cancelHov = mx >= width / 2 + btnGap / 2 && mx < width / 2 + btnW + btnGap / 2 && my >= btnY &&
                my < btnY + 18;
        g.fill(width / 2 + btnGap / 2, btnY, width / 2 + btnW + btnGap / 2, btnY + 18,
                cancelHov ? 0xFF3A3A3A : 0xFF2A2A2A);
        if (cancelHov) g.fill(width / 2 + btnGap / 2, btnY, width / 2 + btnW + btnGap / 2, btnY + 1, C_CANCEL);
        g.drawCenteredString(font, "§7✕ Cancel", width / 2 + btnW / 2 + btnGap / 2, btnY + 6,
                cancelHov ? C_CANCEL : C_TEXT);

        // Tooltip is drawn dead last - after the footer too - so it never gets clipped by the
        // content scissor or buried under anything else on the screen. The explicit flush() forces
        // every row's batched text (labels, values, and the < > cycle arrows) to actually get
        // uploaded before the tooltip paints - without it, GuiGraphics can hold those glyphs in its
        // buffer and flush them after this call, which visually put the arrows on top despite this
        // being later in draw order.
        if (hoveredTooltip != null) {
            g.flush();
            renderRowTooltip(g, mx, my, hoveredTooltip);
        }
    }

    private void renderRowTooltip(GuiGraphics g, int mx, int my, String text) {
        String[] lines = text.split("\n");
        int tw = 0;
        for (String line : lines) tw = Math.max(tw, font.width(line));
        int th = lines.length * LABEL_LINE_H + 4;
        int tx = mx + 12;
        int ty = my - 4;
        if (tx + tw + 8 > width) tx = mx - tw - 20;
        if (ty + th > height) ty = height - th;
        if (ty < 0) ty = 0;

        g.fill(tx - 4, ty - 3, tx + tw + 4, ty + th, 0xEE0A0A0E);
        g.fill(tx - 4, ty - 3, tx + tw + 4, ty - 2, C_ACCENT);
        int ly = ty;
        for (String line : lines) {
            g.drawString(font, "§7" + line, tx, ly, C_TEXT, false);
            ly += LABEL_LINE_H;
        }
    }

    private void renderValueRow(GuiGraphics g, int x, int y, int w, Row r, int mx, int my) {
        int textY = y + (ROW_H - 8) / 2;
        boolean rowHov = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        if (rowHov) g.fill(x, y, x + w, y + ROW_H, 0x10FFFFFF);

        int rArrowX = x + w - ARROW_W;
        int lArrowX = rArrowX - ARROW_GAP - ARROW_W;
        boolean leftHov = mx >= lArrowX && mx < lArrowX + ARROW_W && my >= y && my < y + ROW_H;
        boolean rightHov = mx >= rArrowX && mx < rArrowX + ARROW_W && my >= y && my < y + ROW_H;
        if (leftHov) g.fill(lArrowX, y, lArrowX + ARROW_W, y + ROW_H, 0x33FFFFFF);
        if (rightHov) g.fill(rArrowX, y, rArrowX + ARROW_W, y + ROW_H, 0x33FFFFFF);
        g.drawCenteredString(font, "§7<", lArrowX + ARROW_W / 2, textY, leftHov ? C_ACCENT : C_TEXT_DIM);
        g.drawCenteredString(font, "§7>", rArrowX + ARROW_W / 2, textY, rightHov ? C_ACCENT : C_TEXT_DIM);

        g.drawString(font, r.label, x + 4, textY, C_TEXT, false);
        String value = r.valueFn.get();
        int valueX = lArrowX - 6 - font.width(value);
        g.drawString(font, value, valueX, textY, C_TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int footerY = height - FOOTER_H;
        int btnW = 100;
        int btnGap = 8;
        int btnY = footerY + 5;

        if (mx >= width / 2 - btnW - btnGap / 2 && mx < width / 2 - btnGap / 2 && my >= btnY && my < btnY + 18) {
            settings.save();
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (mx >= width / 2 + btnGap / 2 && mx < width / 2 + btnW + btnGap / 2 && my >= btnY && my < btnY + 18) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        int contentTop = HEADER_H;
        int contentBottom = height - FOOTER_H;

        // Sidebar category clicks
        if (mx >= 0 && mx < SIDEBAR_W && my >= contentTop && my < contentBottom) {
            int sy = contentTop + MARGIN;
            for (Category cat : Category.values()) {
                if (cat == Category.PHANTASIA && !PhantasiaCompat.isAvailable()) continue;
                if (my >= sy && my < sy + ROW_H) {
                    selectCategory(cat);
                    return true;
                }
                sy += ROW_H;
            }
            return true;
        }

        // Row clicks
        int x = SIDEBAR_W + MARGIN;
        int w = Math.min(PANEL_W, width - x - MARGIN);
        for (Row r : rows) {
            int ry = r.y - scrollY;
            if (my < ry || my >= ry + ROW_H || mx < x || mx >= x + w) continue;
            switch (r.type) {
                case LINK -> {
                    if (r.onClick != null) r.onClick.run();
                    return true;
                }
                case TOGGLE -> {
                    r.onLeft.run();
                    rebuildRows();
                    return true;
                }
                case CYCLE -> {
                    int rArrowX = x + w - ARROW_W;
                    int lArrowX = rArrowX - ARROW_GAP - ARROW_W;
                    if (mx < lArrowX) break; // clicked the label/value area, not an arrow - no-op
                    (mx >= rArrowX ? r.onRight : r.onLeft).run();
                    rebuildRows();
                    return true;
                }
                default -> {}
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollY = Math.max(0, (int) (scrollY - delta * 12));
        return true;
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
