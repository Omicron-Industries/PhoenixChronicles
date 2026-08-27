package net.phoenixvine.chronicles.client.screen.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.render.NodeRenderer;

import net.phoenixvine.chronicles.client.screen.utils.SidebarRow;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.registry.CategoryRegistry;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SidebarPanel {

    static final int SIDEBAR_CAT_ROW_H = 18;
    private static final int SIDEBAR_FOLDER_ROW_H = 14;
    static final int SIDEBAR_COLLAPSE_TOGGLE_H = 12;
    static final int SIDEBAR_DRAG_THRESHOLD = 4;
    private static final int GEAR_SIZE = 14;
    private static final int SIDEBAR_W_EXPANDED = 150;
    private static final int SIDEBAR_W_COLLAPSED = 12;
    private static final long SIDEBAR_ANIM_MS = 1000L;
    private static final int SIDEBAR_HOVER_MARGIN = 20;

    private static final int HEADER_H = 38;

    private static final int[] CAT_ACCENTS = {
            0xFF5566EE, 0xFF44BB77, 0xFFCC7722, 0xFFAA44CC,
            0xFF22AABB, 0xFFBB4444, 0xFF88AA22, 0xFF448899
    };

    record Colors(int border, int borderLit, int text, int textDim, int textFaint, int panelDark, int selTab,
                  int progFill, int progAct) {}

    public record MenuAction(String label, Runnable onClick) {}

    private static final int CTX_MENU_W = 140;
    private static final int CTX_ROW_H = 16;

    private boolean collapsed = false;
    private boolean hoverPeek = false;
    private int scrollY = 0;
    private float animW = SIDEBAR_W_COLLAPSED;
    private long lastAnimNanos = 0L;
    private int nodeLayoutAnimBaseX = SIDEBAR_W_COLLAPSED;

    private SidebarRow dragRow = null;
    private int dragStartX, dragStartY;
    private boolean dragMoved = false;

    private List<MenuAction> ctxActions = null;
    private int ctxX, ctxY;

    boolean collapsed() {
        return collapsed;
    }

    void setCollapsed(boolean c) {
        collapsed = c;
    }

    int scrollY() {
        return scrollY;
    }

    void resetScroll() {
        scrollY = 0;
    }

    SidebarRow dragRow() {
        return dragRow;
    }

    public void setDragRow(SidebarRow r) {
        dragRow = r;
    }

    int dragStartX() {
        return dragStartX;
    }

    int dragStartY() {
        return dragStartY;
    }

    public void setDragStart(int x, int y) {
        dragStartX = x;
        dragStartY = y;
    }

    boolean dragMoved() {
        return dragMoved;
    }

    public void setDragMoved(boolean m) {
        dragMoved = m;
    }

    public void syncLayoutBaseX(int cl) {
        nodeLayoutAnimBaseX = cl;
    }

    boolean contextMenuOpen() {
        return ctxActions != null;
    }

    public void openContextMenu(int mx, int my, List<MenuAction> actions, int screenW, int screenH) {
        ctxActions = actions;

        int h = actions.size() * CTX_ROW_H + 4;
        ctxX = Math.min(mx, screenW - CTX_MENU_W - 2);
        ctxY = Math.min(my, screenH - h - 2);
    }

    void closeContextMenu() {
        ctxActions = null;
    }

    void handleContextMenuClick(int mx, int my) {
        if (ctxActions == null) return;
        int h = ctxActions.size() * CTX_ROW_H + 4;
        if (mx >= ctxX && mx < ctxX + CTX_MENU_W && my >= ctxY && my < ctxY + h) {
            int idx = (my - (ctxY + 2)) / CTX_ROW_H;
            if (idx >= 0 && idx < ctxActions.size()) ctxActions.get(idx).onClick().run();
        }
        closeContextMenu();
    }

    void renderContextMenu(GuiGraphics g, Font font, int mx, int my, int screenW, int screenH, Colors colors) {
        if (ctxActions == null) return;
        int h = ctxActions.size() * CTX_ROW_H + 4;
        int x = Math.min(ctxX, screenW - CTX_MENU_W - 2);
        int y = Math.min(ctxY, screenH - h - 2);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        g.flush();
        RenderSystem.disableDepthTest();
        g.fill(x, y, x + CTX_MENU_W, y + h, 0xFF1A1A24);
        ChroniclesUIKit.drawBorder(g, x, y, CTX_MENU_W, h, colors.borderLit());
        for (int i = 0; i < ctxActions.size(); i++) {
            int ry = y + 2 + i * CTX_ROW_H;
            boolean hov = mx >= x && mx < x + CTX_MENU_W && my >= ry && my < ry + CTX_ROW_H;
            if (hov) g.fill(x + 1, ry, x + CTX_MENU_W - 1, ry + CTX_ROW_H, 0x22FFFFFF);
            g.drawString(font, (hov ? "§f" : "§7") + ctxActions.get(i).label(), x + 6, ry + 4,
                    hov ? colors.text() : colors.textDim(), false);
        }
        RenderSystem.enableDepthTest();
        g.flush();
        g.pose().popPose();
    }

    boolean isHoverSidebar() {
        return QuestChroniclesSettings.get().getSidebarBehavior() ==
                QuestChroniclesSettings.SidebarBehavior.HOVER_TO_EXPAND;
    }

    public int width() {
        if (isHoverSidebar()) return hoverPeek ? SIDEBAR_W_EXPANDED : SIDEBAR_W_COLLAPSED;
        return collapsed ? SIDEBAR_W_COLLAPSED : SIDEBAR_W_EXPANDED;
    }

    public boolean isNarrow() {
        return isHoverSidebar() ? !hoverPeek : collapsed;
    }

    public int visualWidth() {
        return isHoverSidebar() ? Math.round(animW) : width();
    }

    void updateHoverPeek(int mx, int my, BiConsumer<Integer, Integer> panCanvas) {
        if (!isHoverSidebar()) {
            hoverPeek = false;
            animW = SIDEBAR_W_COLLAPSED;
            lastAnimNanos = 0L;

            nodeLayoutAnimBaseX = width();
            return;
        }

        long now = System.nanoTime();
        long elapsedMs = lastAnimNanos == 0L ? 0L : (now - lastAnimNanos) / 1_000_000L;
        lastAnimNanos = now;

        int triggerW = hoverPeek ? Math.round(animW) + SIDEBAR_HOVER_MARGIN : SIDEBAR_W_COLLAPSED;
        boolean hovering = mx >= 0 && mx < triggerW;

        float target = hovering ? SIDEBAR_W_EXPANDED : SIDEBAR_W_COLLAPSED;
        float maxStep = (SIDEBAR_W_EXPANDED - SIDEBAR_W_COLLAPSED) * (elapsedMs / (float) SIDEBAR_ANIM_MS);
        if (animW < target) animW = Math.min(target, animW + maxStep);
        else if (animW > target) animW = Math.max(target, animW - maxStep);

        if (hovering) {
            if (!hoverPeek) {
                hoverPeek = true;
                scrollY = 0;
            }
        } else if (hoverPeek && animW <= SIDEBAR_W_COLLAPSED + 0.5f) {
            hoverPeek = false;
        }

        int desiredBaseX = visualWidth();
        int shift = desiredBaseX - nodeLayoutAnimBaseX;
        if (shift != 0) {
            panCanvas.accept(shift, 0);
            nodeLayoutAnimBaseX = desiredBaseX;
        }
    }

    public boolean collapseToggleHovered(int mx, int my) {
        return mx >= 0 && mx < width() - 1 && my >= HEADER_H + 1 && my < HEADER_H + 1 + SIDEBAR_COLLAPSE_TOGGLE_H;
    }

    private int gearY(int height) {
        return height - GEAR_SIZE - 4;
    }

    boolean gearHovered(int mx, int my, int height) {
        if (isNarrow()) return false;
        int gy = gearY(height);
        return mx >= width() - GEAR_SIZE - 4 && mx < width() - 4 && my >= gy && my < gy + GEAR_SIZE;
    }

    private int newCatBtnY(int height) {
        return gearY(height) - 4 - 14;
    }

    boolean newCatButtonHovered(int mx, int my, int height, boolean devMode) {
        if (!devMode || isNarrow()) return false;
        int x = 4, y = newCatBtnY(height), w = width() - 9, h = 14;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    List<SidebarRow> buildRows(Function<String, String> friendly, Function<String, int[]> progressLookup,
                               List<String> cats) {
        List<SidebarRow> rows = new ArrayList<>();
        int y = HEADER_H + 16 - scrollY;
        Set<String> drawnInFolder = new HashSet<>();

        Map<String, List<String>> childrenOf = new HashMap<>();
        Set<String> hasParent = new HashSet<>();
        for (String c : cats) {
            String parent = ChapterConfig.get(c).getParentChapter();
            if (!parent.isEmpty() && !parent.equals(c) && cats.contains(parent)) {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(c);
                hasParent.add(c);
            }
        }

        for (CategoryDefinition category : CategoryRegistry.getCategories()) {
            List<String> fcats = category.chapters().stream().filter(cats::contains)
                    .filter(c -> !hasParent.contains(c)).toList();

            boolean collapsedFolder = CategoryRegistry.isCollapsed(category.id());
            rows.add(new SidebarRow(true, category.id(), category.displayName(), y, SIDEBAR_FOLDER_ROW_H, false,
                    collapsedFolder, false, false));
            y += SIDEBAR_FOLDER_ROW_H;

            if (!collapsedFolder) {
                for (String cat : fcats) {
                    rows.add(new SidebarRow(false, cat, friendly.apply(cat), y, SIDEBAR_CAT_ROW_H, true, false,
                            false, false));
                    y += SIDEBAR_CAT_ROW_H;
                    drawnInFolder.add(cat);
                    y = emitSubChapters(rows, cat, childrenOf, y, true, friendly, progressLookup);
                }
            } else {
                drawnInFolder.addAll(fcats);
            }
        }

        List<String> standalone = new ArrayList<>();
        for (String cat : cats) if (!drawnInFolder.contains(cat) && !hasParent.contains(cat)) standalone.add(cat);
        for (String cat : applyStandaloneOrder(standalone)) {
            rows.add(new SidebarRow(false, cat, friendly.apply(cat), y, SIDEBAR_CAT_ROW_H, false, false, false,
                    false));
            y += SIDEBAR_CAT_ROW_H;
            y = emitSubChapters(rows, cat, childrenOf, y, false, friendly, progressLookup);
        }

        return rows;
    }

    private int emitSubChapters(List<SidebarRow> rows, String parent, Map<String, List<String>> childrenOf,
                                int y, boolean inFolder, Function<String, String> friendly,
                                Function<String, int[]> progressLookup) {
        List<String> children = childrenOf.get(parent);
        if (children == null) return y;
        int[] parentProgress = progressLookup.apply(parent);
        boolean locked = parentProgress[0] == 0;
        for (String child : children) {
            rows.add(new SidebarRow(false, child, friendly.apply(child), y, SIDEBAR_CAT_ROW_H, inFolder, false,
                    true, locked));
            y += SIDEBAR_CAT_ROW_H;
            y = emitSubChapters(rows, child, childrenOf, y, inFolder, friendly, progressLookup);
        }
        return y;
    }

    private List<String> applyStandaloneOrder(List<String> standalone) {
        List<String> order = CategoryRegistry.getStandaloneOrder();
        if (order.isEmpty()) return standalone;
        List<String> result = new ArrayList<>();
        for (String c : order) if (standalone.contains(c)) result.add(c);
        for (String c : standalone) if (!result.contains(c)) result.add(c);
        return result;
    }

    public int scrollAreaHeight(int height) {
        return Math.max(0, (newCatBtnY(height) - 6) - (HEADER_H + 1 + SIDEBAR_COLLAPSE_TOGGLE_H));
    }

    int contentHeight(int height, Function<String, String> friendly, Function<String, int[]> progressLookup,
                      List<String> cats) {
        int saved = scrollY;
        scrollY = 0;
        List<SidebarRow> rows = buildRows(friendly, progressLookup, cats);
        scrollY = saved;
        if (rows.isEmpty()) return 0;
        SidebarRow last = rows.get(rows.size() - 1);
        return (last.y() + last.height()) - (HEADER_H + 16);
    }

    void scrollBy(double delta, int contentHeight, int areaHeight) {
        int maxScroll = Math.max(0, contentHeight - areaHeight);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * SIDEBAR_CAT_ROW_H)));
    }

    SidebarRow rowAt(List<SidebarRow> rows, int mx, int my) {
        if (mx < 0 || mx >= width() - 1 || my < HEADER_H) return null;
        for (SidebarRow row : rows) {
            if (my >= row.y() && my < row.y() + row.height()) return row;
        }
        return null;
    }

    void handleDrop(SidebarRow source, int mx, int my, Function<String, String> friendly,
                    Function<String, int[]> progressLookup, Supplier<List<String>> buildChapterList,
                    Consumer<String> setFeedback, Runnable rebuild, List<String> cats) {
        List<SidebarRow> rows = buildRows(friendly, progressLookup, cats);
        SidebarRow target = rowAt(rows, mx, my);

        if (source.isFolder()) {
            List<CategoryDefinition> allCategories = CategoryRegistry.getCategories();
            int targetIndex = allCategories.size();
            if (target != null) {
                String targetCategoryId = target.isFolder() ? target.id() :
                        (CategoryRegistry.categoryFor(target.id()) != null ?
                                CategoryRegistry.categoryFor(target.id()).id() : null);
                if (targetCategoryId != null) {
                    for (int i = 0; i < allCategories.size(); i++) {
                        if (allCategories.get(i).id().equals(targetCategoryId)) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
            }
            CategoryRegistry.reorderCategory(source.id(), targetIndex);
            CategoryRegistry.save();
            setFeedback.accept("Category reordered");
        } else {
            String chap = source.id();
            CategoryDefinition currentCategory = CategoryRegistry.categoryFor(chap);

            String destCategoryId = null;
            if (target != null) {
                if (target.isFolder()) {
                    destCategoryId = target.id();
                } else {
                    CategoryDefinition tc = CategoryRegistry.categoryFor(target.id());
                    if (tc != null) destCategoryId = tc.id();
                }
            }

            boolean sameCategory = currentCategory != null && currentCategory.id().equals(destCategoryId);

            if (sameCategory) {
                String targetChap = null;
                if (target != null && !target.isFolder()) {
                    List<String> catChapters = currentCategory.chapters();
                    if (my < target.y() + target.height() / 2) {
                        int targetIdx = catChapters.indexOf(target.id());
                        if (targetIdx > 0) {
                            targetChap = catChapters.get(targetIdx - 1);
                        } else {
                            targetChap = target.id();
                        }
                    } else {
                        targetChap = target.id();
                    }
                }
                if (!chap.equals(targetChap)) {
                    CategoryRegistry.reorderCategoryChapter(currentCategory.id(), chap, targetChap);
                    setFeedback.accept("Reordered " + friendly.apply(chap));
                }
            } else {
                if (currentCategory != null) {
                    CategoryRegistry.removeChapterFromCategory(currentCategory.id(), chap);
                }
                if (destCategoryId != null) {
                    CategoryRegistry.addChapterToCategory(destCategoryId, chap);
                    setFeedback.accept("Moved " + friendly.apply(chap) + " into " + destCategoryId);
                } else if (currentCategory != null) {
                    setFeedback.accept("Removed " + friendly.apply(chap) + " from " + currentCategory.displayName());
                } else {
                    List<String> standalone = new ArrayList<>();
                    for (String c : buildChapterList.get()) {
                        if (CategoryRegistry.categoryFor(c) == null) {
                            standalone.add(c);
                        }
                    }
                    List<String> ordered = applyStandaloneOrder(standalone);

                    String targetChap = null;
                    if (target != null && !target.isFolder() && CategoryRegistry.categoryFor(target.id()) == null) {
                        if (my < target.y() + target.height() / 2) {
                            int targetIdx = ordered.indexOf(target.id());
                            if (targetIdx > 0) {
                                targetChap = ordered.get(targetIdx - 1);
                            } else {
                                targetChap = target.id();
                            }
                        } else {
                            targetChap = target.id();
                        }
                    }

                    if (!chap.equals(targetChap)) {
                        CategoryRegistry.reorderStandaloneChapter(chap, targetChap, ordered);
                        setFeedback.accept(targetChap != null ? "Reordered " + friendly.apply(chap) :
                                "Moved " + friendly.apply(chap) + " to end");
                    }
                }
            }
            CategoryRegistry.save();
        }
        rebuild.run();
    }

    private int chapterAccent(String cat) {
        int configured = ChapterConfig.get(cat).getEffectiveNameColor();
        if (configured != 0) return 0xFF000000 | (configured & 0x00FFFFFF);
        return CAT_ACCENTS[Math.abs(cat.hashCode()) % CAT_ACCENTS.length];
    }

    private static int blendColor(int base, int over, float a) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        return 0xFF000000 | ((int) (br + (or - br) * a) << 16) | ((int) (bg + (og - bg) * a) << 8) |
                (int) (bb + (ob - bb) * a);
    }

    void renderPanel(GuiGraphics g, Font font, int mx, int my, int width, int height, Colors colors,
                     boolean devMode, String selectedChapter, Function<String, String> friendly,
                     Function<String, int[]> progressLookup, Function<String, Boolean> attentionLookup,
                     Function<String, Boolean> rewardsLookup, Consumer<Runnable> deferDraw, List<String> cats) {
        g.fill(0, HEADER_H, visualWidth() - 1, HEADER_H + 1, colors.border());

        int toggleY = HEADER_H + 1;

        if (!isHoverSidebar()) {
            boolean toggleHov = collapseToggleHovered(mx, my);
            g.fill(0, toggleY, width() - 1, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H,
                    toggleHov ? 0xFF1C1C24 : colors.panelDark());
            g.fill(0, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H - 1, width() - 1, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H,
                    colors.border());
            g.drawCenteredString(font, collapsed ? "§7▶" : "§7◀", width() / 2, toggleY + 2,
                    toggleHov ? colors.text() : colors.textDim());

            if (collapsed && toggleHov) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 250f);
                g.flush();
                String tip = "§7Show chapters";
                int ttW = font.width(tip) + 10;
                int ttX = width() + 3, ttY = toggleY;
                g.fill(ttX, ttY, ttX + ttW, ttY + 14, 0xFF1A1A24);
                ChroniclesUIKit.drawBorder(g, ttX, ttY, ttW, 14, colors.borderLit());
                g.drawString(font, tip, ttX + 5, ttY + 3, colors.textDim(), false);
                g.pose().popPose();
            }
        }

        if (isNarrow()) {
            g.fill(visualWidth() - 1, 0, visualWidth(), height, colors.border());
            return;
        }

        int scrollTop = toggleY + SIDEBAR_COLLAPSE_TOGGLE_H;
        int scrollBottom = scrollTop + scrollAreaHeight(height);

        g.enableScissor(0, scrollTop, visualWidth() - 1, scrollBottom);

        FrameProfiler.begin("sidebar");
        List<SidebarRow> sidebarRows = buildRows(friendly, progressLookup, cats);
        for (SidebarRow row : sidebarRows) {
            if (row.y() + row.height() < scrollTop || row.y() > scrollBottom) continue;
            if (row.isFolder()) renderFolderRow(g, font, row, mx, my, colors);
            else renderCatRow(g, font, row, mx, my, colors, devMode, selectedChapter, progressLookup,
                    attentionLookup, rewardsLookup);
        }

        if (dragMoved && dragRow != null) {
            SidebarRow dropTarget = rowAt(sidebarRows, mx, my);
            if (dropTarget != null) {
                g.fill(1, dropTarget.y(), width() - 2, dropTarget.y() + dropTarget.height(), 0x4400DDFF);
                ChroniclesUIKit.drawBorder(g, 1, dropTarget.y(), width() - 3, dropTarget.height(), 0xFF00DDFF);
            }
        }
        FrameProfiler.end("sidebar");

        if (sidebarRows.isEmpty()) {
            g.drawCenteredString(font, "§8No", width() / 2, scrollTop + 10, colors.textFaint());
            g.drawCenteredString(font, "§8chapters", width() / 2, scrollTop + 20, colors.textFaint());
        }

        g.flush();
        g.disableScissor();
        g.fill(visualWidth() - 1, 0, visualWidth(), height, colors.border());

        int contentH = contentHeight(height, friendly, progressLookup, cats);
        int areaH = scrollAreaHeight(height);
        if (contentH > areaH && areaH > 0) {
            int trackH = areaH;
            int thumbH = Math.max(10, trackH * areaH / contentH);
            int maxScroll = contentH - areaH;
            int thumbY = scrollTop + (maxScroll > 0 ? (trackH - thumbH) * scrollY / maxScroll : 0);

            g.fill(visualWidth() - 3, scrollTop, visualWidth() - 1, scrollBottom, 0x22FFFFFF);
            g.fill(visualWidth() - 3, thumbY, visualWidth() - 1, thumbY + thumbH, 0xFF666677);
        }

        SidebarRow hovRow = my >= scrollTop && my < scrollBottom ? rowAt(sidebarRows, mx, my) : null;
        if (hovRow != null) {
            SidebarRow finalRow = hovRow;
            deferDraw.accept(() -> renderTooltip(g, font, width, height, finalRow, mx, my, colors, progressLookup));
        }
    }

    private void renderTooltip(GuiGraphics g, Font font, int width, int height, SidebarRow row, int mx, int my,
                               Colors colors, Function<String, int[]> progressLookup) {
        String line1 = row.label();
        String line2 = null;
        if (!row.isFolder()) {
            int[] p = progressLookup.apply(row.id());
            if (p[1] > 0) line2 = "§8" + p[0] + "/" + p[1] + " complete";
        }
        int ttW = Math.max(font.width(line1), line2 != null ? font.width(line2) : 0) + 10;
        int ttH = line2 != null ? 24 : 14;

        int ttX = Math.min(visualWidth() + 3, width - ttW - 2);
        int ttY = Math.max(2, Math.min(height - ttH - 2, my - ttH / 2));

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 250f);
        g.flush();
        g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xFF1A1A24);
        g.fill(ttX, ttY, ttX + ttW, ttY + 1, colors.borderLit());
        g.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, colors.border());
        g.fill(ttX, ttY, ttX + 1, ttY + ttH, colors.border());
        g.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, colors.border());
        g.drawString(font, "§f" + line1, ttX + 5, ttY + 4, colors.text(), false);
        if (line2 != null) g.drawString(font, line2, ttX + 5, ttY + 14, colors.textDim(), false);
        g.pose().popPose();
    }

    private void renderFolderRow(GuiGraphics g, Font font, SidebarRow row, int mx, int my, Colors colors) {
        int y = row.y(), h = row.height();
        boolean hov = mx >= 0 && mx < width() - 1 && my >= y && my < y + h;
        g.fill(0, y, width() - 1, y + h, hov ? 0xFF1C1C24 : 0xFF15151B);
        g.fill(0, y + h - 1, width() - 1, y + h, colors.border());

        int accent = categoryAccent(row.id());
        g.fill(0, y, 2, y + h, accent);

        String arrow = row.collapsed() ? "▶" : "▼";
        int textX = 6;
        g.drawString(font, "§8" + arrow, textX, y + (h - 8) / 2, hov ? colors.textDim() : colors.textFaint(), false);
        textX += 9;

        CategoryDefinition cat = CategoryRegistry.get(row.id());
        String iconId = cat != null ? cat.icon() : "";
        if (iconId != null && !iconId.isEmpty()) {
            Item item = resolveCategoryIcon(iconId);
            if (item != null) {
                int iconTextX = textX;
                try {
                    NodeRenderer.withNoIconMipBleed(g, () -> {
                        g.pose().pushPose();
                        g.pose().translate(iconTextX, y + (h - 16 * 0.625f) / 2f, 0f);
                        g.pose().scale(0.625f, 0.625f, 1f);
                        g.renderItem(new ItemStack(item), 0, 0);
                        g.pose().popPose();
                    });
                } catch (Exception ignored) {}
                textX += 11;
            }
        }

        String label = row.label();
        CategoryDefinition catForLabel = CategoryRegistry.get(row.id());
        if (catForLabel != null && catForLabel.nameColor() != 0) label = stripColorCodes(label);
        int maxLabelW = width() - textX - 3;
        if (font.width(label) > maxLabelW) label = font.plainSubstrByWidth(label, maxLabelW - 4) + "…";
        int nameAccent = categoryNameAccent(row.id());
        int labelColor = hov ? blendColor(nameAccent, colors.textDim(), 0.4f) :
                blendColor(nameAccent, colors.textFaint(), 0.6f);
        g.drawString(font, "§l" + label, textX, y + (h - 8) / 2, labelColor, false);
    }

    private static String stripColorCodes(String s) {
        return s.replaceAll("(?i)§[0-9a-f]", "");
    }

    private int categoryAccent(String categoryId) {
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        int configured = cat != null ? cat.color() : 0;
        if (configured != 0) return 0xFF000000 | (configured & 0x00FFFFFF);
        return CAT_ACCENTS[Math.abs(categoryId.hashCode()) % CAT_ACCENTS.length];
    }

    private int categoryNameAccent(String categoryId) {
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        int configured = cat != null ? cat.effectiveNameColor() : 0;
        if (configured != 0) return 0xFF000000 | (configured & 0x00FFFFFF);
        return CAT_ACCENTS[Math.abs(categoryId.hashCode()) % CAT_ACCENTS.length];
    }

    private Item resolveCategoryIcon(String iconId) {
        try {
            Item item = ForgeRegistries.ITEMS
                    .getValue(ResourceLocation.parse(iconId));
            return (item != null && item != Items.AIR) ? item : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void renderCatRow(GuiGraphics g, Font font, SidebarRow row, int mx, int my, Colors colors,
                              boolean devMode, String selectedChapter, Function<String, int[]> progressLookup,
                              Function<String, Boolean> attentionLookup, Function<String, Boolean> rewardsLookup) {
        String cat = row.id();
        int y = row.y(), h = row.height();

        int indent = (row.inFolder() ? 6 : 0) + (row.subChapter() ? 10 : 0);
        int iconX = 4 + indent;
        int iconY = y + (h - 16) / 2;
        boolean locked = row.locked();
        int accent = chapterAccent(cat);
        boolean isSel = cat.equals(selectedChapter);
        boolean hov = !locked && mx >= 0 && mx < width() - 1 && my >= y && my < y + h;

        if (isSel) {
            g.fill(1, y + 1, width() - 2, y + h - 1, colors.selTab());
            ChroniclesUIKit.drawBorder(g, 1, y + 1, width() - 3, h - 2, accent);
        } else if (hov) {
            g.fill(1, y + 1, width() - 2, y + h - 1, 0x14FFFFFF);
        }

        if (rewardsLookup.apply(cat)) {
            g.fill(1, y + 1, 3, y + h - 1, 0xFFFFCC00);
        }

        Item iconItem = ChapterConfig.get(cat).getIconItem();
        if (locked) RenderSystem.setShaderColor(0.55f, 0.55f, 0.55f, 1f);
        try {
            g.renderItem(new ItemStack(iconItem), iconX, iconY);
        } catch (Exception ignored) {} finally {
            if (locked) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        int textCol = locked ? colors.textFaint() :
                (isSel || hov) ? accent : blendColor(accent, colors.textDim(), 0.4f);
        String rowLabel = row.label();
        if (!locked && ChapterConfig.get(cat).getNameColor() != 0) rowLabel = stripColorCodes(rowLabel);
        String label = (locked ? "§8🔒 " : "") + rowLabel;
        int maxLabelW = width() - (iconX + 18) - 4;
        if (font.width(label) > maxLabelW) label = font.plainSubstrByWidth(label, maxLabelW - 4) + "…";
        g.drawString(font, label, iconX + 18, y + (h - 8) / 2, textCol, false);

        if (locked) return;

        int[] p = progressLookup.apply(cat);
        if (p[1] > 0) {
            float fraction = (float) p[0] / p[1];
            int barCol = (p[0] == p[1]) ? colors.progFill() : (p[0] > 0 ? colors.progAct() : 0x33FFFFFF);
            int barX0 = iconX, barW = width() - 4 - barX0;
            g.fill(barX0, y + h - 2, barX0 + barW, y + h - 1, 0x22FFFFFF);
            g.fill(barX0, y + h - 2, barX0 + Math.round(barW * fraction), y + h - 1, barCol);
        }

        boolean attention = attentionLookup.apply(cat);
        if (attention) {
            int bx = iconX + 12, by = iconY - 2;
            g.fill(bx, by, bx + 6, by + 6, 0xFFCC2233);
            g.fill(bx, by, bx + 6, by + 1, 0xFFFF5566);
            g.pose().pushPose();
            g.pose().translate(bx + 2.2f, by - 0.5f, 200f);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, "!", 0, 0, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    void renderNewChapterButton(GuiGraphics g, Font font, int mx, int my, int height, boolean devMode,
                                Colors colors) {
        if (!devMode) return;
        int x = 4, y = newCatBtnY(height), w = width() - 9, h = 14;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0x33FFFFFF : 0x1AFFFFFF);
        g.fill(x, y, x + w, y + 1, hov ? colors.borderLit() : colors.border());
        g.drawCenteredString(font, "§a+", x + w / 2, y + 3, colors.text());
        if (hov) {
            int ttW = font.width("New chapter") + 10;

            int ttX = visualWidth() + 3, ttY = y - 2;
            g.fill(ttX, ttY, ttX + ttW, ttY + 14, 0xFF1A1A24);
            ChroniclesUIKit.drawBorder(g, ttX, ttY, ttW, 14, colors.borderLit());
            g.drawString(font, "§7New chapter", ttX + 5, ttY + 3, colors.textDim(), false);
        }
    }

    void renderGear(GuiGraphics g, Font font, int mx, int my, int width, int height, boolean devMode,
                    Colors colors, Consumer<Runnable> deferDraw) {
        int gx = width() - GEAR_SIZE - 4;
        int gy = gearY(height);
        boolean hov = gearHovered(mx, my, height);

        g.fill(4, gy - 6, width() - 4, gy - 5, colors.border());

        int col = hov ? 0xFFDDDDE8 : 0xFF555566;
        g.drawString(font, "⚙", gx + 1, gy + 1, col, false);

        if (hov) {

            int ttW = 200;
            int ttH = devMode ? 64 : 30;

            int ttXRaw = visualWidth() + 3;
            int ttYRaw = gy - ttH - 2;
            if (ttXRaw + ttW > width) ttXRaw = width - ttW - 2;

            if (ttXRaw < 2) ttXRaw = 2;
            if (ttYRaw < 2) ttYRaw = 2;
            final int ttX = ttXRaw;
            final int ttY = ttYRaw;

            deferDraw.accept(() -> {

                g.flush();
                RenderSystem.disableDepthTest();
                g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xFF1A1A24);
                g.fill(ttX, ttY, ttX + ttW, ttY + 1, colors.border());
                g.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, colors.border());
                g.fill(ttX, ttY, ttX + 1, ttY + ttH, colors.border());
                g.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, colors.border());
                g.drawString(font, "§dUtilities", ttX + 5, ttY + 4, colors.text(), false);
                g.drawString(font, "§8§oLeft-click§r§8: Edit all quest texts", ttX + 5, ttY + 14, colors.textDim(),
                        false);
                if (devMode) {
                    g.drawString(font, "§8§oRight-click§r§8: Export lang/en_us.json", ttX + 5, ttY + 24,
                            colors.textDim(), false);
                    g.drawString(font, "§8§o[I]§r§8: Import FTB Quests chapter", ttX + 5, ttY + 34, colors.textDim(),
                            false);
                    g.drawString(font, "§8(place .snbt in ftb_import/ folder)", ttX + 5, ttY + 44, colors.textFaint(),
                            false);
                    g.drawString(font, "§8(pack's en_us.json also goes there)", ttX + 5, ttY + 54,
                            colors.textFaint(), false);
                }
                g.flush();
                RenderSystem.enableDepthTest();
            });
        }
    }
}
