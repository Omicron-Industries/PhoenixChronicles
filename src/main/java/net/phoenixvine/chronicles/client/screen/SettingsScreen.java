package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings.*;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private static final int MIN_CONTENT_W = SIDEBAR_W + MARGIN + PANEL_W + MARGIN;
    private static final int MIN_CONTENT_H = 260;

    private float uiScale = 1f;
    private int vw, vh;

    private enum Category {

        GENERAL("General"),
        HUD("HUD"),
        POPUPS("Pop-Ups"),
        CANVAS("Canvas"),
        PHANTASIA("Phantasia"),
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

    private static final class Row {

        final RowType type;
        final String label;
        final Supplier<String> valueFn;
        final Runnable onLeft, onRight;
        final Runnable onClick;
        int y;
        int height = ROW_H;
        String tooltip;

        Row(RowType type, String label, Supplier<String> valueFn, Runnable onLeft, Runnable onRight,
            Runnable onClick) {
            this.type = type;
            this.label = label;
            this.valueFn = valueFn;
            this.onLeft = onLeft;
            this.onRight = onRight;
            this.onClick = onClick;
        }

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

        float neededW = MIN_CONTENT_W + 20f;
        float neededH = HEADER_H + FOOTER_H + MIN_CONTENT_H;
        uiScale = (width < neededW || height < neededH) ?
                Math.min(width / neededW, height / neededH) : 1f;
        uiScale = Math.max(0.1f, uiScale);
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        PhoenixTheme t = PhoenixTheme.current();
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
                rows.add(Row.cycle("§fQuest Node Move Style",
                        net.phoenixvine.chronicles.codec.QuestChroniclesSettings.NodeMoveMode.class,
                        settings::getNodeMoveMode, settings::setNodeMoveMode)
                        .tip("DRAG: shift+click and hold to move a quest node, release to drop it.\n" +
                                "PICKUP_PLACE: shift+click once to pick it up (it follows the cursor\n" +
                                "with no button held), click again to place it - Escape cancels\n" +
                                "either way and snaps it back to where it started."));
                rows.add(Row.link("§fTheme Editor",
                        () -> {
                            if (minecraft != null) minecraft.setScreen(new PhoenixThemeEditorScreen(this));
                        })
                        .tip("Opens the full color editor - customize every panel, text, and\nstate color, then save it as a named theme."));
                rows.add(Row.info("§8Keybinds: Minecraft's own Options → Controls → Phoenix Chronicles", 1));
            }
            case HUD -> {
                rows.add(Row.cycle("§fHUD Position", HUDPosition.class, settings::getHudPosition,
                        settings::setHudPosition)
                        .tip("Which screen corner the pinned-quest widget(s) anchor to and stack from\nwhile you're out playing (this does not affect the quest book itself)."));
                rows.add(new Row(RowType.CYCLE, "§fHUD Opacity",
                        () -> String.format("%.0f%%", settings.getHudOpacity() * 100),
                        () -> settings.setHudOpacity(settings.getHudOpacity() - 0.1f),
                        () -> settings.setHudOpacity(settings.getHudOpacity() + 0.1f), null)
                        .tip("How see-through the pinned quest widget's background panel is.\nLower = more of the game shows through behind it; text stays fully readable either way."));
                rows.add(Row.toggle("§fShow HUD Title", settings::isShowHUDTitle, settings::setShowHUDTitle)
                        .tip("Shows the quest icon, name, and pin marker as a header row\non each pinned widget. Turn off to hide that header row entirely -\nonly the remaining-task list below it will still show."));
                rows.add(Row.toggle("§fShow HUD Progress", settings::isShowHUDProgress, settings::setShowHUDProgress)
                        .tip("Shows a small \"done/total\" count (e.g. 2/5) next to the quest title.\nTurn off to hide just this counter - the list of remaining tasks\nbelow it is unaffected."));
                rows.add(Row.toggle("§fShow HUD Rewards", settings::isShowHUDRewards, settings::setShowHUDRewards)
                        .tip("Shows a row of small reward icons at the bottom of each pinned\nquest's HUD widget, below its remaining tasks."));
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
                rows.add(Row.toggle("§fShow Progress Ring", settings::isShowProgressArc, settings::setShowProgressArc)
                        .tip("Draws a ring around a quest node showing task-completion progress,\non top of the background color-coding. Off by default."));
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
                rows.add(Row.toggle("§fMiddle-Click Pickup/Place", settings::isMiddleClickPickupPlace,
                        settings::setMiddleClickPickupPlace)
                        .tip("OFF: hold the middle mouse button on a quest, move the mouse, and\n" +
                                "release to drop it (classic behavior).\n" +
                                "ON: middle-click a quest to pick it up - it follows the cursor with\n" +
                                "no button held - then middle-click again anywhere to drop it."));
                rows.add(Row.toggle("§fCascade Hidden Quests", settings::isCascadeHiddenQuests, on -> {
                    settings.setCascadeHiddenQuests(on);
                    settings.save();
                    if (parent instanceof ChronicleOverviewScreen overview) overview.rebuild();
                })
                        .tip("On by default: a locked HIDDEN/MYSTERY quest also hides every quest\n" +
                                "downstream of it, and hides the whole chapter if nothing else in it\n" +
                                "is visible yet - lets one gating quest hide a whole questline,\n" +
                                "FTB-Quests-style. Turn off to only hide that one quest and show\n" +
                                "everything else as normal, regardless of its prerequisites."));
                rows.add(Row.info("§8These can also be changed via right-click on the quest canvas.", 1));
            }
            case PHANTASIA -> rows.add(Row.toggle("§fAuto-Spin Previews", settings::isPhantasiaAutoSpin,
                    settings::setPhantasiaAutoSpin)
                    .tip("Slowly rotates embedded Phantasia multiblock/scene previews\n(quest nodes, toasts) instead of holding them still."));
            case DEV -> {

                rows.add(Row.toggle("§fDev Mode Enabled", () -> !settings.isDevModeDisabled(), on -> {
                    settings.setDevModeDisabled(!on);
                    settings.save();
                }).tip("Unlocks debug-only canvas tools (Stats/Validation panels,\nSubgraph mode, FTB import, Dev Wiki) for creative/op players."));
                rows.add(Row.toggle("§fShow Dev Info by Default", settings::isShowDevInfoByDefault,
                        settings::setShowDevInfoByDefault)
                        .tip("Opens quest editors with dev-only fields expanded by default."));
                rows.add(Row.toggle("§fShow Flag-Disabled Chapters", settings::isShowFlagDisabledChapters, on -> {
                    settings.setShowFlagDisabledChapters(on);
                    settings.save();
                    if (parent instanceof ChronicleOverviewScreen overview) overview.rebuild();
                })
                        .tip("Off by default: the sidebar hides chapters a chapter_flags.snbt\n" +
                                "rule has disabled (e.g. pack-mode variants like Genesis Hard/Expert\n" +
                                "when you're not in that mode), same as a real player would see.\n" +
                                "Turn on to see and edit every variant regardless of the current flag state."));
                rows.add(Row.toggle("§fShow Flag-Disabled Quests", settings::isShowFlagDisabledQuests, on -> {
                    settings.setShowFlagDisabledQuests(on);
                    settings.save();
                    if (parent instanceof ChronicleOverviewScreen overview) overview.rebuild();
                })
                        .tip("On by default: within a visible chapter, dev mode also shows\n" +
                                "individual quests a chapter_flags.snbt rule has disabled (marked\n" +
                                "with the purple ⚑ border), so you can see and edit them in place.\n" +
                                "Turn off to hide flag-disabled quests even while in dev mode."));
                rows.add(Row.toggle("§fGenerate .md Sidecar Files", settings::isGenerateMdSidecarFiles, on -> {
                    settings.setGenerateMdSidecarFiles(on);
                    settings.save();
                }).tip("Off by default: quest title/description already live in the .snbt\n" +
                        "and render identically either way (same markdown formatting support).\n" +
                        "Turn on if you'd rather edit description text in a real .md file next\n" +
                        "to the .snbt - useful for some workflows, but many packdevs prefer\n" +
                        "not to have an extra file per quest."));
                rows.add(Row.toggle("§fAlways-On Profiler", settings::isAlwaysProfilerEnabled, on -> {
                    settings.setAlwaysProfilerEnabled(on);
                    settings.save();
                    FrameProfiler.setEnabled(on);
                }).tip("Keeps the rolling render-cost profiler (Ctrl+P) running for the\n" +
                        "whole session and logs a snapshot every 10s - useful for tracking\n" +
                        "down intermittent performance issues without remembering to toggle it."));
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

    private int contentOffsetX() {
        int totalW = SIDEBAR_W + MARGIN + PANEL_W + MARGIN;
        return Math.max(0, (vw - totalW) / 2);
    }

    private void enableScissorScaled(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(Math.round(x1 * uiScale), Math.round(y1 * uiScale), Math.round(x2 * uiScale),
                Math.round(y2 * uiScale));
    }

    @Override
    public void render(GuiGraphics g, int rmx, int rmy, float partial) {
        g.fill(0, 0, width, height, C_BG);

        int mx = Math.round(rmx / uiScale);
        int my = Math.round(rmy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        g.fill(0, 0, vw, HEADER_H, C_HEADER);
        g.fill(0, 0, vw, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, vw, HEADER_H, C_BORDER);
        g.drawCenteredString(font, "§fChronicles Settings", vw / 2, 9, C_TEXT);

        int contentTop = HEADER_H;
        int contentBottom = vh - FOOTER_H;
        int off = contentOffsetX();

        g.fill(off, contentTop, off + SIDEBAR_W, contentBottom, C_PANEL);
        g.fill(off + SIDEBAR_W - 1, contentTop, off + SIDEBAR_W, contentBottom, C_BORDER);
        int sy = contentTop + MARGIN;
        for (Category cat : Category.values()) {
            if (cat == Category.PHANTASIA && !PhantasiaCompat.isAvailable()) continue;
            boolean sel = cat == selectedCategory;
            boolean hov = mx >= off && mx < off + SIDEBAR_W && my >= sy && my < sy + ROW_H;
            if (sel) {
                g.fill(off, sy, off + SIDEBAR_W - 1, sy + ROW_H, 0x22FFFFFF);
                g.fill(off, sy, off + 2, sy + ROW_H, C_ACCENT);
            } else if (hov) {
                g.fill(off, sy, off + SIDEBAR_W - 1, sy + ROW_H, 0x10FFFFFF);
            }
            g.drawString(font, (sel ? "§f" : "§7") + cat.label, off + MARGIN, sy + (ROW_H - 8) / 2,
                    sel ? C_TEXT : C_TEXT_DIM, false);
            sy += ROW_H;
        }

        int x = off + SIDEBAR_W + MARGIN;
        int w = Math.min(PANEL_W, vw - x - MARGIN);
        enableScissorScaled(g, x, contentTop, x + w, contentBottom);

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

            boolean overArrow = r.type != RowType.INFO && r.type != RowType.LINK && my >= ry && my < ry + r.height &&
                    mx >= x + w - (ARROW_GAP + ARROW_W * 2) && mx < x + w;
            if (r.tooltip != null && !overArrow && mx >= x && mx < x + w && my >= ry && my < ry + r.height) {
                hoveredTooltip = r.tooltip;
            }
        }
        g.disableScissor();

        int footerY = vh - FOOTER_H;
        g.fill(0, footerY, vw, vh, C_HEADER);
        g.fill(0, footerY, vw, footerY + 1, C_BORDER);

        int btnW = 100;
        int btnGap = 8;
        int btnY = footerY + 5;

        boolean saveHov = mx >= vw / 2 - btnW - btnGap / 2 && mx < vw / 2 - btnGap / 2 && my >= btnY &&
                my < btnY + 18;
        g.fill(vw / 2 - btnW - btnGap / 2, btnY, vw / 2 - btnGap / 2, btnY + 18,
                saveHov ? 0xFF2A4A2A : 0xFF1A2A1A);
        if (saveHov) g.fill(vw / 2 - btnW - btnGap / 2, btnY, vw / 2 - btnGap / 2, btnY + 1, C_OK);
        g.drawCenteredString(font, "§a✓ Save", vw / 2 - btnW / 2 - btnGap / 2, btnY + 6, saveHov ? C_OK : C_TEXT);

        boolean cancelHov = mx >= vw / 2 + btnGap / 2 && mx < vw / 2 + btnW + btnGap / 2 && my >= btnY &&
                my < btnY + 18;
        g.fill(vw / 2 + btnGap / 2, btnY, vw / 2 + btnW + btnGap / 2, btnY + 18,
                cancelHov ? 0xFF3A3A3A : 0xFF2A2A2A);
        if (cancelHov) g.fill(vw / 2 + btnGap / 2, btnY, vw / 2 + btnW + btnGap / 2, btnY + 1, C_CANCEL);
        g.drawCenteredString(font, "§7✕ Cancel", vw / 2 + btnW / 2 + btnGap / 2, btnY + 6,
                cancelHov ? C_CANCEL : C_TEXT);

        if (hoveredTooltip != null) {
            g.flush();
            renderRowTooltip(g, mx, my, hoveredTooltip);
        }

        g.pose().popPose();
    }

    private void renderRowTooltip(GuiGraphics g, int mx, int my, String text) {
        String[] lines = text.split("\n");
        int tw = 0;
        for (String line : lines) tw = Math.max(tw, font.width(line));
        int th = lines.length * LABEL_LINE_H + 4;
        int tx = mx + 12;
        int ty = my - 4;
        if (tx + tw + 8 > vw) tx = mx - tw - 20;
        if (ty + th > vh) ty = vh - th;
        if (ty < 0) ty = 0;

        g.fill(tx - 4, ty - 3, tx + tw + 4, ty + th, 0xFF0A0A0E);
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
    public boolean mouseClicked(double rmx, double rmy, int btn) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;

        int footerY = vh - FOOTER_H;
        int btnW = 100;
        int btnGap = 8;
        int btnY = footerY + 5;

        if (mx >= vw / 2 - btnW - btnGap / 2 && mx < vw / 2 - btnGap / 2 && my >= btnY && my < btnY + 18) {
            settings.save();
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (mx >= vw / 2 + btnGap / 2 && mx < vw / 2 + btnW + btnGap / 2 && my >= btnY && my < btnY + 18) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        int contentTop = HEADER_H;
        int contentBottom = vh - FOOTER_H;
        int off = contentOffsetX();

        if (mx >= off && mx < off + SIDEBAR_W && my >= contentTop && my < contentBottom) {
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

        int x = off + SIDEBAR_W + MARGIN;
        int w = Math.min(PANEL_W, vw - x - MARGIN);
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
                    if (mx < lArrowX) break;
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
