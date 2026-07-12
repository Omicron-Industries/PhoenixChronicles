package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter;
import net.phoenixvine.chronicles.client.*;
import net.phoenixvine.chronicles.client.render.BackgroundPictureRenderer;
import net.phoenixvine.chronicles.client.render.CanvasBackgroundRenderer;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.render.DependencyLineRenderer;
import net.phoenixvine.chronicles.client.render.NodeShapeRenderer;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.tracker.TutorialProgressTracker;
import net.phoenixvine.chronicles.tracker.TutorialStep;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class ChronicleOverviewScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    // FTBQ-style compact list: small icon + accent-colored name per row, back to a wider panel
    // (was briefly a 48px icon-tile grid; that made a dozen+ categories require heavy scrolling
    // and buried the name behind a hover tooltip). The selected category's full name still shows
    // in the canvas title bar too (renderHeaderAndBaseLayout).
    private static final int SIDEBAR_W_EXPANDED = 150;
    private static final int SIDEBAR_W_COLLAPSED = 12;
    private boolean sidebarCollapsed = false;
    private int sidebarScrollY = 0;

    /**
     * All 45-odd call sites read this instead of a raw constant so the collapse toggle can
     * actually reclaim canvas width, not just hide tile detail within the same footprint.
     */
    private int sidebarW() {
        return sidebarCollapsed ? SIDEBAR_W_COLLAPSED : SIDEBAR_W_EXPANDED;
    }

    private static final int HEADER_H = 38;  // title bar (22) + search/filter row (16)
    private static final int TOOLBAR_Y = 22;  // search row starts here
    private static final int TOOLBAR_H = 16;
    private static final int NODE_SIZE = 32;

    // ── Palette (themed fields are instance vars, set in init via ChroniclesTheme) ──
    private int C_BG = 0xFF0B0B0F;
    private int C_PANEL = 0xFF14141A;
    private int C_PANEL_DARK = 0xFF0E0E12;
    private int C_HEADER = 0xFF09090D;
    private int C_BORDER = 0xFF252530;
    private int C_BORDER_LIT = 0xFF353548;
    private int C_SEL_TAB = 0xFF1A1A26;
    private int C_SEL_ACCENT = 0xFF00AA55;
    // Node fill/border — instance fields so they update when the theme changes
    private int C_NODE_LOCKED = 0xFF1A1A24;
    private int C_NODE_UNLOCKED = 0xFF1E1E2C;
    private int C_NODE_ACTIVE = 0xFF221C00;
    private int C_NODE_DONE = 0xFF081A0E;
    private int C_NBORD_LOCKED = 0xFF2E2E40;
    private int C_NBORD_UNLOCKED = 0xFF4A4A60;
    private int C_NBORD_ACTIVE = 0xFFCC9900;
    private int C_NBORD_DONE = 0xFF00BB66;
    private static final int C_NBORD_SEL = 0xFF6688FF;
    private int C_NBORD_DEV = 0xFF8844AA;
    // Dependency-line base colors - theme-derived (same t.locked/done/activeColor as the node
    // border colors above), just at the line renderer's own alpha levels instead of the node
    // borders' opaque one, since these used to be DependencyLineRenderer's own hardcoded
    // constants entirely disconnected from the theme editor.
    private int C_LINE_LOCKED = 0x38FFFFFF;
    private int C_LINE_DONE = 0x9900CC66;
    private int C_LINE_ACTIVE = 0x88FFAA00;
    private int C_TEXT = 0xFFD8D8E4;
    private int C_TEXT_DIM = 0xFF7A7A8A;
    private int C_TEXT_FAINT = 0xFF404050;
    private int C_TEXT_DONE = 0xFF44CC88;
    private int C_TEXT_ACT = 0xFFFFBB33;
    private static final int C_CTX_BG = 0xFF1A1A22;
    private static final int C_CTX_HOVER = 0xFF252532;
    private static final int C_CTX_BORDER = 0xFF8844AA;
    private static final int C_CTX_SEP = 0xFF2A2A38;
    private static final int C_CTX_TEXT = 0xFFCCCCD8;
    private static final int C_CTX_DANGER = 0xFFCC4444;
    private static final int C_PROG_BG = 0xFF1A1A22;
    private int C_PROG_FILL = 0xFF00AA55;
    private static final int C_PROG_ACT = 0xFFBB8800;

    // ── State ─────────────────────────────────────────────────────────────────
    private String selectedCategory = "";
    /**
     * Which category the current zoom/viewOffX/viewOffY belong to - null until the very first
     * rebuild(). Used to detect a genuine category switch (save the outgoing category's view,
     * restore the incoming one's) versus every OTHER rebuild() call (quest edits, etc.), which
     * must leave the live pan/zoom completely alone.
     */
    private String viewCategoryTracker = null;
    private QuestNode selectedNode = null;
    /** Last node the mouse hovered during render(), reused by the pin keybind in keyPressed(). */
    private ResourceLocation lastHoveredNodeId = null;
    /**
     * Per-frame counts of which icon path each node took - reset and reported once per frame
     * in renderNodesAndDetails() so the profiler log can show whether the 3D-vs-flat gate is
     * actually routing nodes to the cheap path at low zoom.
     */
    private int dbgFull3DIconCount = 0;
    private int dbgCustomIconCount = 0;
    private int dbgPickedTextureIconCount = 0;
    private int dbgGlyphIconCount = 0;
    /** Per-shape-type node counts this frame, so node:shape cost can be attributed to specific shapes. */
    private final Map<String, Integer> dbgShapeCounts = new HashMap<>();
    private boolean isDevMode = false;
    private String feedbackMsg = "";
    private int feedbackTimer = 0;

    // ── Panning & zoom ────────────────────────────────────────────────────────
    private int viewOffX = 0, viewOffY = 0;
    // mouseDragged can fire many times between rendered frames (raw input events aren't capped
    // to the frame rate) - on a large real pack, panCanvas()'s O(nodes+edges) work at every one
    // of those events (instead of once per frame) is what actually made panning laggy. Accumulate
    // the delta here and apply it exactly once per render() call instead.
    private int pendingPanDX = 0, pendingPanDY = 0;
    private float zoom = 1.0f;
    private static final float ZOOM_MIN = 0.12f;
    private static final float ZOOM_MAX = 2.5f;
    private static final float ZOOM_STEP = 0.12f;
    // Node SIZE keeps scaling all the way up with zoom (so zooming in for detail still gets you
    // bigger, more legible nodes/icons/text) but POSITION spacing stops growing once zoom passes
    // this cap. Without this, node size and inter-node distance both scale 1:1 with zoom forever,
    // so the ratio between them never changes - but the ABSOLUTE screen distance between two
    // connected quests keeps growing, meaning a layout that looks great zoomed out needs constant
    // panning to follow a dependency chain once zoomed in far enough for detail work.
    private static final float SPACING_ZOOM_CAP = 1.1f;
    private boolean isPanning = false;

    /** Zoom factor to use for node/group POSITION math - capped, unlike the raw `zoom` used for size. */
    private float posZoom() {
        return Math.min(zoom, SPACING_ZOOM_CAP);
    }

    // ── Filter state ──────────────────────────────────────────────────────────
    private boolean hideCompleted = false;

    // ── Double-click to create (dev mode) ─────────────────────────────────────
    private long lastCanvasClickTime = 0;
    private int lastCanvasClickX = 0;
    private int lastCanvasClickY = 0;

    // ── Dev drag ──────────────────────────────────────────────────────────────
    private QuestNode draggedNode = null;
    private int dragGrabX = 0, dragGrabY = 0;

    // ── Group drag ────────────────────────────────────────────────────────────
    @Nullable
    private QuestGroup draggedGroup = null;
    private int groupDragGrabX = 0, groupDragGrabY = 0;

    // ── Background picture drag (see BackgroundPictureConfig) ─────────────────
    @Nullable
    private BackgroundPictureConfig.Picture draggedPicture = null;
    private int pictureDragGrabX = 0, pictureDragGrabY = 0;

    // ── Background picture context menu (self-contained, mirrors DependencyLineRenderer's own
    // ctx menu rather than the shared ctxNode/ctxGroup system - pictures aren't quest nodes or
    // groups, and that system's title-row/move-category logic is written specifically around
    // QuestNode) ────────────────────────────────────────────────────────────────
    private boolean picCtxOpen = false;
    private long picCtxOpenTimeMs = 0;
    private int picCtxX, picCtxY;
    @Nullable
    private BackgroundPictureConfig.Picture picCtxTarget = null;
    private boolean picCtxResizeOpen = false;
    private boolean picCtxMoveCatOpen = false;
    private static final int[] PIC_RESIZE_PRESETS = { 32, 64, 128, 256, 512, 1024 };

    // ── Sidebar drag-and-drop (folder reorder / category move-into-folder) ────
    @Nullable
    private SidebarRow sidebarDragRow = null;
    private int sidebarDragStartX, sidebarDragStartY;
    private boolean sidebarDragMoved = false;
    private static final int SIDEBAR_DRAG_THRESHOLD = 4;

    /**
     * Interactive picture resize/reposition mode ("Resize (scroll + drag)…" in the picture's
     * context menu) - while active, mouse wheel and plain left-drag are hijacked away from their
     * normal canvas zoom/pan behavior and instead scale/move this one picture, since a picture
     * needs to be resized independently of whatever zoom level the canvas itself is at.
     */
    @Nullable
    private BackgroundPictureConfig.Picture pictureEditMode = null;
    private static final float PIC_EDIT_MIN_SIZE = 4f, PIC_EDIT_MAX_SIZE = 4096f;

    // ── Context menu (pure-render, no hidden buttons) ─────────────────────────
    private static final int CTX_ROW = 16;
    private static final int CTX_SEP = 5;
    private static final int CTX_W = 128;
    private boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    private int ctxX, ctxY;
    private QuestNode ctxNode = null;
    private boolean ctxMoveCatOpen = false;
    // "Move to Category" submenu: capped height + scroll instead of growing to fit every
    // chapter, which ran off-screen (and became entirely unreachable past that point) on packs
    // with a lot of categories. Reset whenever the submenu closes so it doesn't reopen scrolled.
    private int ctxMoveCatScroll = 0;
    private static final int CTX_MOVE_CAT_MAX_ROWS = 10;
    @Nullable
    private QuestGroup ctxGroup = null;

    // ── New-category inline form ───────────────────────────────────────────────
    private boolean newCatFormOpen = false;
    private EditBox newCatBox = null;

    // Set while a child screen (e.g. QuestTasksScreen compact) renders us as backdrop.
    // Skips widget rendering and tooltips so they don't bleed over the child's card.
    private boolean renderingAsBackdrop = false;

    // ── State filter (toolbar pills) ─────────────────────────────────────────
    private String stateFilter = "ALL";
    private EditBox searchBox = null;
    private String searchQuery = "";
    private String[] searchWords = new String[0];
    // Per-node search haystacks — built once per quest, cleared only on rebuild() or screen open.
    final Map<ResourceLocation, String> searchCache = new HashMap<>();

    // ── Phantasia 3D preview widget ───────────────────────────────────────────
    /** Phantasia 3D preview widget — typed as Object to avoid compile dep on Phantasia. */
    private Object phantasiaPreview = null;

    // ── Multi-select (dev mode) ───────────────────────────────────────────────
    private final Set<ResourceLocation> multiSelection = new LinkedHashSet<>();

    // ── Undo / redo ───────────────────────────────────────────────────────────
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 30;

    // ── Tutorial overlay ──────────────────────────────────────────────────────
    // Button hit-boxes computed each frame in renderTutorialOverlay, used in mouseClicked
    private int[] tutPrevBtn = null;
    private int[] tutNextBtn = null;
    private int[] tutSkipBtn = null;

    /**
     * Lightweight stand-in for a vanilla Button, used purely for node hover/click hit-testing.
     * Previously every node was a real (alpha=0, invisible) Button registered via
     * addRenderableWidget - vanilla widgets carry their own per-frame render/hover/narration
     * bookkeeping, and with 100+ nodes on screen that added up to "widgets (super.render)"
     * becoming the single largest chunk of this screen's render time in profiling on larger
     * packs, bigger than all of our own node drawing put together. A plain bounds check has none
     * of that overhead. Field/method names deliberately mirror Button's own API (getX/setX/
     * visible/isMouseOver) so every existing call site below needed only a type change, not a
     * rewrite.
     */
    private static final class NodeHitbox {

        int x, y, w, h;
        boolean visible = true;
        boolean active = true;

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        void setX(int nx) {
            x = nx;
        }

        void setY(int ny) {
            y = ny;
        }

        boolean isMouseOver(double mx, double my) {
            return visible && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // ── Canvas caches ─────────────────────────────────────────────────────────
    private final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    private final Map<ResourceLocation, NodeHitbox> nodeButtons = new LinkedHashMap<>();
    private final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();
    /** Per-category progress cache; invalidated by rebuild(). */
    private final Map<String, int[]> progressCache = new HashMap<>();
    /** Per-category "has an ACTIVE quest" cache for the sidebar attention badge; same lifetime as progressCache. */
    private final Map<String, Boolean> attentionCache = new HashMap<>();
    /**
     * Per-quest validation-issue cache; invalidated by rebuild(). getValidationIssues() used to
     * recompute from scratch on every call, including a real Files.exists() disk stat inside it -
     * called once per node PER FRAME from the dev-mode "warning badge" overlay loop (~126 nodes
     * at 60fps = thousands of filesystem calls/sec), which was the actual dominant cost behind
     * "dev overlays" in the profiler. Quest data only changes on rebuild(), same invalidation
     * point as progressCache/attentionCache above.
     */
    private final Map<ResourceLocation, List<String>> validationCache = new HashMap<>();
    /** Stub category names read from categories.txt; refreshed only on rebuild(). */
    private List<String> stubCategoryCache = null;
    /**
     * Full category name list (quests + stubs), refreshed only on rebuild(). buildCategoryList()
     * used to rescan every quest in the registry on every call - and it's called multiple times
     * per render frame (sidebar row layout, content-height measurement, etc.) - so with a large
     * pack this was a full quest-registry walk happening well over 100x/sec. Cached here instead;
     * callers still get their own ArrayList copy since several of them mutate it in place
     * (e.g. `cats.remove("ALL")`).
     */
    private List<String> categoryListCache = null;

    // ── Bulk-ops extra state ──────────────────────────────────────────────────
    private boolean bulkMoveCatOpen = false;

    // ── Prereq link drag (Alt+drag in dev mode) ───────────────────────────────
    private QuestNode linkDragSource = null;
    private int linkDragX, linkDragY;

    // ── Grid snap ────────────────────────────────────────────────────────────────
    /** Cycle: 1 (free) → 4 → 8 → 16 → 32 → back to 1. Shift-drag always bypasses to 1. */
    private int gridSnap = 8;
    private static final int[] GRID_SNAP_CYCLE = { 1, 4, 8, 16, 32 };

    // ── Unlock path visualization ─────────────────────────────────────────────
    private final Set<ResourceLocation> unlockPathHighlight = new HashSet<>();

    // ── Validation panel ─────────────────────────────────────────────────────
    private boolean validationOpen = false;

    // ── Open-fade animation ───────────────────────────────────────────────────
    private long openTimeMs = -1;
    private static final long OPEN_FADE_MS = 120;

    // ── Tooltip hover delay ───────────────────────────────────────────────────
    private ResourceLocation tooltipHoverNodeId = null;
    private long tooltipHoverStartMs = 0;
    private static final long TOOLTIP_DELAY_MS = 450;

    /**
     * Deferred "utility" tooltips (grid pill, sidebar row, anything drawn from an early render()
     * step that can visually overhang later-drawn regions) queue themselves here instead of
     * drawing immediately. Elevated z + flush only wins the depth test against the specific
     * depth-tested draws (item icons at z=100 via g.renderItem()) — it does nothing against
     * ordinary fills/strings drawn in a LATER render() step, which just overpaint the earlier
     * pixels regardless of z-pose. Queuing here and flushing once, at the true end of render(),
     * guarantees these always paint after canvas/nodes/overlays no matter which step decided to
     * show one. Only one can be pending per frame; that's fine today since the header pill and
     * sidebar rows don't overlap, but if that ever changes this needs to become a list.
     */
    private Runnable pendingTooltip = null;

    // ── Category accent colors (cycling palette keyed by hash) ────────────────
    private static final int[] CAT_ACCENTS = {
            0xFF5566EE, 0xFF44BB77, 0xFFCC7722, 0xFFAA44CC,
            0xFF22AABB, 0xFFBB4444, 0xFF88AA22, 0xFF448899
    };

    // ── Test mode (simulate fresh-player view, no server calls) ──────────────
    private boolean testMode = false;
    private PlayerQuestData testModeData = new PlayerQuestData();
    private boolean subgraphMode = false;
    private final java.util.Set<ResourceLocation> subgraphNodes = new java.util.HashSet<>();

    // ── Quest clipboard (Ctrl+C / Ctrl+V) ────────────────────────────────────
    /** Raw SNBT text of the last copied quest. null if nothing copied. */
    private String questClipboard = null;

    // ── Minimap ───────────────────────────────────────────────────────────────
    private boolean minimapOpen = false;
    private static final int MM_W = 130, MM_H = 78, MM_PAD = 4;
    /** True while the user is dragging inside the minimap. */
    private boolean mmDragging = false;

    // ── Stats dashboard ───────────────────────────────────────────────────────
    private boolean statsOpen = false;

    // ── Detail panel ──────────────────────────────────────────────────────────
    private PlayerQuestData playerData = null;

    public ChronicleOverviewScreen() {
        super(Component.literal("Chronicles"));
        // Reopen on whatever chapter was last viewed (including across a full world/game
        // restart) instead of always landing back on the first one - rebuild()'s validity check
        // just below still falls back to the first chapter if this category no longer exists.
        selectedCategory = QuestChroniclesSettings.get().getLastCategory();
    }

    // ── Capability helpers ────────────────────────────────────────────────────

    QuestState getState(QuestNode node) {
        if (testMode) return testModeData.getQuestState(node.getId(), QuestState.LOCKED);
        if (playerData == null) return QuestState.LOCKED;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED);
    }

    /** Resolves a link stub to the real quest it points at, or null if dangling/not a stub. */
    private QuestNode resolveLinkTarget(QuestNode node) {
        return node.isLinkStub() ? QuestTreeRegistry.getQuest(node.getLinkTarget()) : null;
    }

    /**
     * When a quest has no explicitly-set icon, shows the first task's representative item
     * instead of nothing - matches FTB Quests' own "fall back to the first required item"
     * convention. Works live for already-imported quests too, not just future re-imports,
     * since it reuses each task's own getDisplayItemId() (already fixed to resolve a
     * representative item/fluid-bucket for tag-based tasks rather than returning null).
     */
    private Item fallbackTaskIcon(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            if (id == null) continue;
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return item;
        }
        return null;
    }

    /** Same as getState, but a link stub reports the REAL target's state (locked/done/etc). */
    private QuestState getDisplayState(QuestNode node) {
        QuestNode target = resolveLinkTarget(node);
        return getState(target != null ? target : node);
    }

    private boolean isTaskDone(QuestTask task) {
        if (minecraft == null || minecraft.player == null) return false;
        return task.isCompletedFor(minecraft.player);
    }

    // ── Category persistence ──────────────────────────────────────────────────

    /**
     * Returns the path of the flat file that stores stub category names
     * (categories that exist but have no quests in them yet).
     */
    private Path categoriesFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("categories.txt");
    }

    /**
     * Loads stub categories from disk and merges them with whatever categories
     * are already present in the registry (from quests).
     */
    List<String> buildCategoryList() {
        if (categoryListCache != null) return new ArrayList<>(categoryListCache);

        // Use LinkedHashSet for O(1) contains while preserving insertion order
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getCategory();
            if (c != null) seen.add(c);
        }

        // Stub categories from disk — read once per rebuild(), then cached
        if (stubCategoryCache == null) {
            stubCategoryCache = new ArrayList<>();
            try {
                Path f = categoriesFile();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String cat = line.trim().toUpperCase();
                        if (!cat.isEmpty()) stubCategoryCache.add(cat);
                    }
                }
            } catch (IOException ignored) {}
        }
        for (String cat : stubCategoryCache) seen.add(cat);

        categoryListCache = new ArrayList<>(seen);
        return new ArrayList<>(categoryListCache);
    }

    private static final int SIDEBAR_FOLDER_ROW_H = 14;
    // Compact single-line row: icon + name side by side, vertically centered.
    private static final int SIDEBAR_CAT_ROW_H = 18;

    /**
     * One row in the sidebar's folder/category list — either a folder header or a category.
     * subChapter/locked are only meaningful for a category row: subChapter means this category
     * has a parentCategory (CategoryConfig) and is nested directly under it rather than listed
     * independently; locked means its parent chapter has no completed quest yet, so it's shown
     * but not selectable (same "visible but not yet accessible" idea as a locked quest node).
     */
    private record SidebarRow(boolean isFolder, String id, String label, int y, int height, boolean inFolder,
                              boolean collapsed, boolean subChapter, boolean locked) {}

    /**
     * Single source of truth for sidebar row layout, consumed by both render() and
     * mouseClicked() so their geometry can never drift out of sync with each other.
     */
    private List<SidebarRow> buildSidebarRows() {
        List<SidebarRow> rows = new ArrayList<>();
        List<String> cats = buildCategoryList();
        int y = HEADER_H + 16 - sidebarScrollY;
        Set<String> drawnInFolder = new HashSet<>();

        // Sub-chapters (CategoryConfig.parentCategory) are nested directly under their parent
        // wherever it ends up (folder or standalone) instead of being placed independently -
        // only counts as a sub-chapter if the parent actually exists in this pack's category
        // list, so a dangling/typo'd parent reference doesn't just make the category vanish.
        Map<String, List<String>> childrenOf = new HashMap<>();
        Set<String> hasParent = new HashSet<>();
        for (String c : cats) {
            String parent = net.phoenixvine.chronicles.client.CategoryConfig.get(c).getParentCategory();
            if (!parent.isEmpty() && !parent.equals(c) && cats.contains(parent)) {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(c);
                hasParent.add(c);
            }
        }

        for (var folder : net.phoenixvine.chronicles.registry.ChapterFolderRegistry.getFolders()) {
            List<String> fcats = folder.categories().stream().filter(cats::contains)
                    .filter(c -> !hasParent.contains(c)).toList();
            if (fcats.isEmpty()) continue;

            boolean collapsed = net.phoenixvine.chronicles.registry.ChapterFolderRegistry.isCollapsed(folder.id());
            rows.add(new SidebarRow(true, folder.id(), folder.label(), y, SIDEBAR_FOLDER_ROW_H, false, collapsed,
                    false, false));
            y += SIDEBAR_FOLDER_ROW_H;

            if (!collapsed) {
                for (String cat : fcats) {
                    rows.add(new SidebarRow(false, cat, friendly(cat), y, SIDEBAR_CAT_ROW_H, true, false, false,
                            false));
                    y += SIDEBAR_CAT_ROW_H;
                    drawnInFolder.add(cat);
                    y = emitSubChapters(rows, cat, childrenOf, y, true);
                }
            } else {
                drawnInFolder.addAll(fcats);
            }
        }

        List<String> standalone = new ArrayList<>();
        for (String cat : cats) if (!drawnInFolder.contains(cat) && !hasParent.contains(cat)) standalone.add(cat);
        for (String cat : applyStandaloneOrder(standalone)) {
            rows.add(new SidebarRow(false, cat, friendly(cat), y, SIDEBAR_CAT_ROW_H, false, false, false, false));
            y += SIDEBAR_CAT_ROW_H;
            y = emitSubChapters(rows, cat, childrenOf, y, false);
        }

        return rows;
    }

    /** Recursively emits `parent`'s sub-chapters (indented), locked until parent has a completed quest. */
    private int emitSubChapters(List<SidebarRow> rows, String parent, Map<String, List<String>> childrenOf,
                                int y, boolean inFolder) {
        List<String> children = childrenOf.get(parent);
        if (children == null) return y;
        int[] parentProgress = progressCache.computeIfAbsent(parent, this::computeCategoryProgress);
        boolean locked = parentProgress[0] == 0;
        for (String child : children) {
            rows.add(new SidebarRow(false, child, friendly(child), y, SIDEBAR_CAT_ROW_H, inFolder, false, true,
                    locked));
            y += SIDEBAR_CAT_ROW_H;
            y = emitSubChapters(rows, child, childrenOf, y, inFolder);
        }
        return y;
    }

    /**
     * Applies any custom drag-reordered order for standalone (non-foldered) categories -
     * ChapterFolderRegistry.getStandaloneOrder() only lists categories someone has actually
     * dragged before, so anything not in it (including every category before the first ever
     * reorder) keeps its natural relative order, just appended after the explicitly-ordered ones.
     */
    private List<String> applyStandaloneOrder(List<String> standalone) {
        List<String> order = net.phoenixvine.chronicles.registry.ChapterFolderRegistry.getStandaloneOrder();
        if (order.isEmpty()) return standalone;
        List<String> result = new ArrayList<>();
        for (String c : order) if (standalone.contains(c)) result.add(c);
        for (String c : standalone) if (!result.contains(c)) result.add(c);
        return result;
    }

    /**
     * Vertical space actually available for the scrolling row list - between the header border
     * and the fixed "+"/gear buttons pinned to the bottom of the sidebar.
     */
    private int sidebarScrollAreaHeight() {
        return Math.max(0, (newCatBtnY() - 6) - (HEADER_H + 1));
    }

    /**
     * Total height the row list would need if fully unscrolled - reuses buildSidebarRows()
     * rather than duplicating its folder/category iteration, since that's the one place this
     * layout is computed. Temporarily zeroes the scroll offset for the call; single-threaded
     * UI code, so there's no reentrancy concern with the brief mutation.
     */
    private int sidebarContentHeight() {
        int saved = sidebarScrollY;
        sidebarScrollY = 0;
        List<SidebarRow> rows = buildSidebarRows();
        sidebarScrollY = saved;
        if (rows.isEmpty()) return 0;
        SidebarRow last = rows.get(rows.size() - 1);
        return (last.y() + last.height()) - (HEADER_H + 16);
    }

    /** Persists the current set of stub categories (those with no quests) to disk. */
    private void saveStubCategories(List<String> fullCatList) {
        try {
            Path f = categoriesFile();
            Files.createDirectories(f.getParent());
            // Only write categories that have NO quests — quest-backed ones reload naturally
            Set<String> questCats = new HashSet<>();
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                if (n.getCategory() != null) questCats.add(n.getCategory());
            }
            List<String> stubs = new ArrayList<>();
            for (String c : fullCatList) {
                if (!questCats.contains(c)) stubs.add(c);
            }
            Files.writeString(f, String.join("\n", stubs), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Init / rebuild ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        ChroniclesTheme t = ChroniclesTheme.current();
        // ChroniclesThemePalette.refresh() was never actually called anywhere in the codebase -
        // its static fields (BG, PANEL, TEXT, etc.) all sat at Java's default 0 the whole time.
        // Every popup opened from this screen (QuestTextInputScreen, the item/fluid pickers,
        // CategoryThemeScreen, QuestGroupEditorScreen, ParentSelectorScreen, ...) reads its colors
        // straight from that shared palette instead of a local copy like this screen's own C_*
        // fields - with TEXT=0, Font.drawString() forces the alpha bits on and renders solid
        // black regardless of the configured theme, and with BG=0 (alpha 0) g.fill() painted
        // nothing, which is what read as those screens "bleeding" the parent through. Keep the
        // shared palette in sync whenever this screen (re)initializes.
        ChroniclesThemePalette.refresh(t);
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_PANEL_DARK = t.header.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_BORDER_LIT = t.accent.getColor();
        C_SEL_TAB = t.panel.getColor();
        C_SEL_ACCENT = t.accent.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_TEXT_DONE = t.done.getColor();
        C_TEXT_ACT = t.activeColor.getColor();
        C_PROG_FILL = t.accent.getColor();

        // Node fills — bg tinted toward each state color
        int bg = t.bg.getColor();
        C_NODE_LOCKED = blendColor(bg, t.locked.getColor(), 0.18f);
        C_NODE_UNLOCKED = blendColor(bg, t.border.getColor(), 0.35f);
        C_NODE_ACTIVE = blendColor(bg, t.activeColor.getColor(), 0.22f);
        C_NODE_DONE = blendColor(bg, t.done.getColor(), 0.18f);
        // Node borders — straight from theme palette
        C_NBORD_LOCKED = blendColor(t.locked.getColor(), 0xFF000000, 0.25f);
        C_NBORD_UNLOCKED = blendColor(t.border.getColor(), 0xFFFFFFFF, 0.15f);
        C_NBORD_ACTIVE = t.activeColor.getColor();
        C_NBORD_DONE = t.done.getColor();
        C_NBORD_DEV = blendColor(t.accent.getColor(), 0xFFCC44FF, 0.5f);
        // Dependency-line colors — same theme colors as the node borders, kept at the line
        // renderer's own (much lower) alpha levels rather than the borders' opaque one.
        C_LINE_LOCKED = 0x38000000 | (t.locked.getColor() & 0x00FFFFFF);
        C_LINE_DONE = 0x99000000 | (t.done.getColor() & 0x00FFFFFF);
        C_LINE_ACTIVE = 0x88000000 | (t.activeColor.getColor() & 0x00FFFFFF);

        QuestGroupManager.invalidate(); // force reload from disk each time the screen opens
        openTimeMs = System.currentTimeMillis();
        rebuild();
    }

    private Path groupsConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    void rebuild() {
        clearWidgets();
        nodeScreenPos.clear();
        nodeButtons.clear();
        searchCache.clear();
        progressCache.clear();
        attentionCache.clear();
        validationCache.clear();
        stubCategoryCache = null;
        categoryListCache = null;
        ctxOpen = false;
        ctxMoveCatOpen = false;
        ctxGroup = null;
        newCatBox = null;

        // Load quest groups (reads from disk only if not already loaded)
        QuestGroupManager.load(groupsConfigPath());

        if (minecraft != null && minecraft.player != null) {
            isDevMode = !QuestChroniclesSettings.get().isDevModeDisabled() &&
                    (minecraft.player.isCreative() || minecraft.player.hasPermissions(2));
            playerData = minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        }

        int cl = sidebarW(), cr = width;

        // ── Sidebar category tabs ──────────────────────────────────────────────
        // Fully custom-drawn (see buildSidebarRows/renderSidebar/sidebar click handling in
        // mouseClicked) instead of vanilla Button widgets - those used to paint their own gray
        // 9-slice chrome directly over the themed accent/progress-bar row underlay, which made
        // the sidebar look like plain stock buttons next to the rest of this redesigned UI.
        List<String> cats = buildCategoryList();
        if (!cats.isEmpty() && !cats.contains(selectedCategory)) selectedCategory = cats.get(0);

        // Genuine category switch (including the very first rebuild() on screen open) - save
        // the outgoing category's pan/zoom and load the incoming one's. Every OTHER rebuild()
        // call (quest edits, etc.) hits the equals() check below and leaves zoom/viewOffX/Y alone.
        if (!selectedCategory.equals(viewCategoryTracker)) {
            if (viewCategoryTracker != null) saveViewForCategory(viewCategoryTracker);
            restoreViewForCategory(selectedCategory);
            viewCategoryTracker = selectedCategory;
            QuestChroniclesSettings settings = QuestChroniclesSettings.get();
            if (!selectedCategory.equals(settings.getLastCategory())) {
                settings.setLastCategory(selectedCategory);
                settings.save();
            }
        }

        // ── Sidebar bottom utilities ──────────────────────────────────────────
        // Gear button (all users see it; dev-only actions are inside the screen)
        // Rendered as plain text '⚙' with hover tooltip — no invasive button chrome
        // The actual click is handled in mouseClicked() below

        // "New category" form (dev only) - the "+ Category"/"Cancel" toggle itself is a custom
        // pill (see renderSidebarNewCategoryButton + its click handling), only the text input
        // stays a real EditBox.
        if (isDevMode && newCatFormOpen) {
            // Wider than sidebarW() and deliberately floats out over the canvas edge - the
            // icon-strip sidebar isn't wide enough to type a category name into comfortably.
            // Sits directly above the "+" toggle button.
            newCatBox = new EditBox(font, 4, newCatBtnY() - 4 - 14, 130, 14, Component.empty());
            newCatBox.setHint(Component.literal("§8Name, press Enter"));
            newCatBox.setMaxLength(32);
            // addWidget (not addRenderableWidget) - it still needs input/focus handling, but we
            // render it ourselves in renderSidebarNewCategoryButton at an elevated z so its own
            // backing panel and text don't lose the depth test against node icons drawn later in
            // the frame (step 7, z=100) the way the default super.render() timing (step 6, z=0) did.
            addWidget(newCatBox);
        }

        // Search is now handled by the Ctrl+F overlay — no persistent toolbar search box.

        // ── Quest node buttons ────────────────────────────────────────────────
        for (QuestNode root : QuestTreeRegistry.getRootChapters().values()) {
            if (!catMatches(root)) continue;
            placeNodeRecursive(root, cl, cr);
        }
        // Second pass: catch nodes whose parent is in a different category (cross-category links).
        // These nodes are never reached by the root traversal above when their category is selected.
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (catMatches(n)) placeNodeRecursive(n, cl, cr);
        }
        buildLineCache();
    }

    /** Persists the current pan/zoom into the given category's CategoryConfig entry. */
    private void saveViewForCategory(String cat) {
        if (cat == null || cat.isEmpty()) return;
        CategoryConfig cfg = CategoryConfig.get(cat);
        cfg.setView(zoom, viewOffX, viewOffY);
        CategoryConfig.put(cat, cfg);
        CategoryConfig.save();
    }

    /** Applies a category's saved pan/zoom, or the default centered 100% view if none was ever saved. */
    private void restoreViewForCategory(String cat) {
        CategoryConfig cfg = CategoryConfig.get(cat);
        if (cfg.getViewZoom() != 0f) {
            zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, cfg.getViewZoom()));
            viewOffX = cfg.getViewOffX();
            viewOffY = cfg.getViewOffY();
        } else {
            zoom = 1.0f;
            viewOffX = 0;
            viewOffY = 0;
        }
    }

    // ── Node placement (zoom-aware) ───────────────────────────────────────────

    private void placeNodeRecursive(QuestNode node, int cl, int cr) {
        if (nodeButtons.containsKey(node.getId())) return; // already placed (cross-category link)
        // Skip completed nodes when "hide done" is active (dev mode always shows all)
        if (hideCompleted && !isDevMode && playerData != null &&
                playerData.getQuestState(node.getId(), net.phoenixvine.chronicles.model.QuestState.LOCKED) ==
                        net.phoenixvine.chronicles.model.QuestState.COMPLETED) {
            for (QuestNode child : node.getChildren()) placeNodeRecursive(child, cl, cr);
            return;
        }
        int cx = node.getCustomX() != 0 ? node.getCustomX() : 20;
        int cy = node.getCustomY() != 0 ? node.getCustomY() : 40;

        int sz = scaledNodeSize(node);
        int sx = (int) (cx * posZoom()) + viewOffX + cl;
        int sy = (int) (cy * posZoom()) + viewOffY + HEADER_H;

        boolean offCanvas = sx < cl - sz - 2 || sx > cr + 2 || sy < HEADER_H - sz - 2 || sy > height + 2;

        QuestState state = getDisplayState(node);
        NodeHitbox hb = new NodeHitbox();
        hb.x = sx;
        hb.y = sy;
        hb.w = sz;
        hb.h = sz;
        hb.visible = !offCanvas;
        if (state == QuestState.LOCKED && !isDevMode) hb.active = false;
        nodeButtons.put(node.getId(), hb);
        nodeScreenPos.put(node.getId(), new int[] { sx, sy });

        for (QuestNode child : node.getChildren()) {
            if (catMatches(child)) placeNodeRecursive(child, cl, cr);
        }
    }

    // FTB Quests keeps nodes readable even zoomed way out by never shrinking them below a
    // comfortable floor. An 8px floor was small enough to disappear into the background at
    // the extreme zoom-out range large imported packs need - 12px keeps a node a distinct,
    // clickable dot instead of a near-invisible speck.
    private static final int MIN_NODE_PX = 12;

    private int scaledNodeSize(QuestNode node) {
        return Math.max(MIN_NODE_PX, (int) (node.getNodePixelSize() * zoom));
    }

    private int scaledNodeSize() {
        return Math.max(MIN_NODE_PX, (int) (NODE_SIZE * zoom));
    }

    void onNodeClicked(QuestNode node) {
        ctxOpen = false;
        ctxMoveCatOpen = false;

        // Link stub: interact with the REAL quest's data, but stay on the current category/
        // camera view - a normal click shouldn't yank you away to wherever the quest actually
        // lives. Jumping there is a deliberate separate action (context menu "Jump to linked quest").
        QuestNode linkTarget = resolveLinkTarget(node);
        QuestNode effective = linkTarget != null ? linkTarget : node;

        QuestState st = getState(effective);

        // A plain click is the only way most players/devs "pick" a node - selectedNode used to
        // only get set as a side effect of starting a canvas drag, so Subgraph mode (and the
        // node-selection border highlight at renderNodeShape/renderNodeDetails) never reflected
        // whatever you'd actually clicked on, only whatever you'd last dragged (if anything).
        selectedNode = effective;
        if (subgraphMode) rebuildSubgraph();

        // In test mode: clicking toggles COMPLETED/LOCKED and propagates unlocks
        if (testMode) {
            if (st == QuestState.COMPLETED) {
                testModeData.setQuestState(effective.getId(), QuestState.LOCKED);
            } else {
                testModeData.setQuestState(effective.getId(), QuestState.COMPLETED);
            }
            propagateTestUnlocks();
            softRebuild();
            return;
        }

        if (st == QuestState.LOCKED && !isDevMode) return;
        if (minecraft != null) {
            // Must match QuestFileSaver's quests/<category>/<id>.md layout, not the old flat
            // root path - otherwise this always misses and the description shows up blank.
            Path mdPath = QuestFileSaver.getQuestMarkdownPath(effective);
            FullQuestData fd = loadMarkdownContent(mdPath);
            minecraft.setScreen(new QuestTasksScreen(this, effective, fd, playerData));
        }
    }

    /**
     * Layered auto-layout for the current chapter.
     * Uses longest-path layering (roots at layer 0) then simple barycenter ordering.
     * X = layer * X_STRIDE, Y = slot * Y_STRIDE within each layer.
     * Pushes an undo entry and saves to disk.
     */
    private void autoArrangeCategory() {
        final int X_STRIDE = 80;
        final int Y_STRIDE = 56;
        final int ORIGIN_X = 30;
        final int ORIGIN_Y = 30;

        List<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> selectedCategory.equalsIgnoreCase(n.getCategory()))
                .collect(java.util.stream.Collectors.toList());
        if (nodes.isEmpty()) return;

        // Push undo (capture old positions)
        Map<ResourceLocation, int[]> oldPositions = new java.util.HashMap<>();
        for (QuestNode n : nodes) oldPositions.put(n.getId(), new int[] { n.getCustomX(), n.getCustomY() });
        pushUndo(() -> {
            for (QuestNode n : nodes) {
                int[] pos = oldPositions.get(n.getId());
                if (pos != null) n.setCustomPosition(pos[0], pos[1]);
            }
            QuestFileSaver.saveAllQuestsToDisk();
            rebuild();
        });

        // Phase 1 — assign layers via longest-path from roots
        Map<ResourceLocation, Integer> layer = new java.util.HashMap<>();
        // BFS starting from nodes with no in-category prerequisites
        java.util.Queue<QuestNode> queue = new java.util.ArrayDeque<>();
        for (QuestNode n : nodes) {
            boolean isRoot = n.getPrerequisites().stream()
                    .noneMatch(p -> selectedCategory.equalsIgnoreCase(p.getCategory()));
            if (isRoot) {
                layer.put(n.getId(), 0);
                queue.add(n);
            }
        }
        // If no roots found (cycle or all cross-category), put all at layer 0
        if (queue.isEmpty()) {
            nodes.forEach(n -> layer.put(n.getId(), 0));
        }
        int safety = nodes.size() * nodes.size();
        while (!queue.isEmpty() && safety-- > 0) {
            QuestNode n = queue.poll();
            int myLayer = layer.getOrDefault(n.getId(), 0);
            for (QuestNode child : n.getChildren()) {
                if (!selectedCategory.equalsIgnoreCase(child.getCategory())) continue;
                int childLayer = layer.getOrDefault(child.getId(), -1);
                if (childLayer < myLayer + 1) {
                    layer.put(child.getId(), myLayer + 1);
                    queue.add(child);
                }
            }
        }
        // Any unassigned node gets layer 0
        nodes.forEach(n -> layer.putIfAbsent(n.getId(), 0));

        // Phase 2 — group nodes by layer
        Map<Integer, List<QuestNode>> byLayer = new java.util.TreeMap<>();
        for (QuestNode n : nodes) byLayer.computeIfAbsent(layer.get(n.getId()), k -> new ArrayList<>()).add(n);

        // Phase 3 — barycenter sort within each layer (reduce crossings)
        for (Map.Entry<Integer, List<QuestNode>> e : byLayer.entrySet()) {
            if (e.getKey() == 0) continue; // roots keep natural order
            e.getValue().sort(java.util.Comparator.comparingDouble(n -> {
                List<QuestNode> prereqs = n.getPrerequisites().stream()
                        .filter(p -> selectedCategory.equalsIgnoreCase(p.getCategory())).toList();
                if (prereqs.isEmpty()) return 0.0;
                return prereqs.stream()
                        .mapToInt(p -> byLayer.getOrDefault(layer.getOrDefault(p.getId(), 0), List.of()).indexOf(p))
                        .average().orElse(0.0);
            }));
        }

        // Phase 4 — assign coordinates
        for (Map.Entry<Integer, List<QuestNode>> e : byLayer.entrySet()) {
            int lyr = e.getKey();
            List<QuestNode> layerNodes = e.getValue();
            for (int slot = 0; slot < layerNodes.size(); slot++) {
                int x = ORIGIN_X + lyr * X_STRIDE;
                int y = ORIGIN_Y + slot * Y_STRIDE;
                layerNodes.get(slot).setCustomPosition(x, y);
            }
        }

        QuestFileSaver.saveAllQuestsToDisk();
        viewOffX = 0;
        viewOffY = 0;
        rebuild();
        setFeedback("Auto-arranged " + nodes.size() + " quest(s)");
    }

    /** Recalculates UNLOCKED state for all quests in testModeData based on current COMPLETED set. */
    private void propagateTestUnlocks() {
        // Reset all non-completed quests to LOCKED, then unlock eligible ones
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (testModeData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                testModeData.setQuestState(n.getId(), QuestState.LOCKED);
        }
        // Unlock quests whose prerequisites are all completed in testModeData
        boolean changed = true;
        while (changed) {
            changed = false;
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                if (testModeData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.LOCKED) continue;
                boolean prereqsMet = n.getPrerequisites().isEmpty() ||
                        n.getPrerequisites().stream().allMatch(
                                p -> testModeData.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED);
                if (prereqsMet) {
                    testModeData.setQuestState(n.getId(), QuestState.UNLOCKED);
                    changed = true;
                }
            }
        }
    }

    /** Rebuilds subgraphNodes to all ancestors + descendants of selectedNode (BFS both directions). */
    private void rebuildSubgraph() {
        subgraphNodes.clear();
        if (selectedNode == null) return;
        subgraphNodes.add(selectedNode.getId());
        // BFS ancestors (prereqs)
        java.util.ArrayDeque<QuestNode> queue = new java.util.ArrayDeque<>();
        queue.add(selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode p : cur.getPrerequisites()) {
                if (subgraphNodes.add(p.getId())) queue.add(p);
            }
        }
        // BFS descendants (children)
        queue.add(selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode c : cur.getChildren()) {
                if (subgraphNodes.add(c.getId())) queue.add(c);
            }
        }
    }

    /** Called by SearchOverlayScreen to pan the canvas to a quest and select it. */
    public void navigateToNode(QuestNode node) {
        if (node.getCategory() != null && !node.getCategory().equals(selectedCategory)) {
            selectedCategory = node.getCategory();
            rebuild();
        }
        int canvasW = width - sidebarW();
        int canvasH = height - HEADER_H;
        viewOffX = (int) (canvasW / 2f - node.getCustomX() * posZoom());
        viewOffY = (int) (canvasH / 2f - node.getCustomY() * posZoom());
        onNodeClicked(node);
    }

    private void buildLineCache() {
        List<int[]> edges = new ArrayList<>();
        List<ResourceLocation[]> edgeNodes = new ArrayList<>();
        int sz = scaledNodeSize();

        // VIEW-FRUSTUM CULLING BOUNDARIES
        // Ensures lines completely outside the visible canvas are never processed or cached
        int leftBound = 220; // Matches sidebarW()
        int rightBound = this.width;
        int topBound = 40;  // Matches HEADER_H
        int bottomBound = this.height;
        int linePadding = 64;  // Accommodates Bezier curve arcs curving outwards

        for (Map.Entry<ResourceLocation, int[]> e : nodeScreenPos.entrySet()) {
            QuestNode parent = QuestTreeRegistry.getQuest(e.getKey());
            if (parent == null || !catMatches(parent)) continue;
            // Flag-disabled quests and their dep lines are always invisible (even in dev mode)
            if (parent.isFlagDisabled()) continue;
            // Per-quest dep-line visibility
            if (parent.isHideDepLine()) continue;
            int[] pPos = e.getValue();
            int px = pPos[0] + sz / 2, py = pPos[1] + sz / 2;
            QuestState ps = getState(parent);

            // style: 0=locked, 1=done, 2=active, 3=optional-locked, 4=optional-done
            // 5=forbidden-locked, 6=forbidden-done
            // 7=link-locked, 8=link-done, 9=link-active
            for (QuestNode child : parent.getChildren()) {
                if (!catMatches(child)) continue;
                if (child.isHideDepLine()) continue;

                int[] cPos = nodeScreenPos.get(child.getId());
                if (cPos == null) continue;
                int cx2 = cPos[0] + sz / 2, cy2 = cPos[1] + sz / 2;

                // --- FRUSTUM CULLING ---
                // If both the start point (px, py) AND endpoint (cx2, cy2) are out of bounds, skip calculation
                if ((px < leftBound - linePadding && cx2 < leftBound - linePadding) ||
                        (px > rightBound + linePadding && cx2 > rightBound + linePadding) ||
                        (py < topBound - linePadding && cy2 < topBound - linePadding) ||
                        (py > bottomBound + linePadding && cy2 > bottomBound + linePadding)) {
                    continue;
                }

                boolean isForbidden = child.isPrereqForbidden(parent.getId());
                boolean isLinkEdge = child.isPrereqLink(parent.getId());
                boolean isCosmeticEdge = child.isPrereqCosmetic(parent.getId());
                boolean isOptionalPrereq = !isForbidden && child.hasPerPrereqFlags() &&
                        !child.isPrereqRequired(parent.getId());

                int col, style;
                if (isForbidden) {
                    col = ps == QuestState.COMPLETED ? 0xFFAA2222 : 0xFF661111;
                    style = ps == QuestState.COMPLETED ? 6 : 5;
                } else if (isCosmeticEdge) {
                    // Decoration-only — never gates unlock. Faint sparse dots regardless of state.
                    col = 0x1AFFFFFF;
                    style = 10;
                } else if (isLinkEdge) {
                    col = ps == QuestState.COMPLETED ? 0x6600AA55 :
                            ps == QuestState.ACTIVE ? 0x66FFAA00 : 0x26FFFFFF;
                    style = ps == QuestState.ACTIVE ? 9 : (ps == QuestState.COMPLETED ? 8 : 7);
                } else if (isOptionalPrereq) {
                    col = ps == QuestState.COMPLETED ? 0xFF336644 : 0xFF2A2A3A;
                    style = ps == QuestState.COMPLETED ? 4 : 3;
                } else {
                    col = ps == QuestState.COMPLETED ? C_LINE_DONE :
                            ps == QuestState.ACTIVE ? C_LINE_ACTIVE :
                                    C_LINE_LOCKED;
                    style = ps == QuestState.ACTIVE ? 2 : (ps == QuestState.COMPLETED ? 1 : 0);
                }
                int shapeOrd = child.getPrereqLineShape(parent.getId()) != null ?
                        child.getPrereqLineShape(parent.getId()).ordinal() : -1;
                int visOrd = child.getPrereqLineVisual(parent.getId()) != null ?
                        child.getPrereqLineVisual(parent.getId()).ordinal() : -1;
                int speedOrd = child.getPrereqLineSpeed(parent.getId()) != null ?
                        child.getPrereqLineSpeed(parent.getId()).ordinal() : -1;
                Boolean arrowOv = child.getPrereqLineArrow(parent.getId());
                int arrowOrd = arrowOv == null ? -1 : (arrowOv ? 1 : 0);

                edges.add(new int[] { px, py, cx2, cy2, col, style, shapeOrd, visOrd, speedOrd, arrowOrd });
                edgeNodes.add(new ResourceLocation[] { parent.getId(), child.getId() });
            }

            // Also emit lines for prerequisites that are NOT already covered by a child→parent link
            for (QuestNode prereq : parent.getPrerequisites()) {
                if (prereq.getChildren().contains(parent)) continue; // already drawn above
                if (!catMatches(prereq)) continue;
                if (prereq.isFlagDisabled()) continue;

                int[] prereqPos = nodeScreenPos.get(prereq.getId());
                if (prereqPos == null) continue;
                int prx = prereqPos[0] + sz / 2, pry = prereqPos[1] + sz / 2;

                // --- FRUSTUM CULLING ---
                // If both the start point (prx, pry) AND endpoint (px, py) are out of bounds, skip calculation
                if ((prx < leftBound - linePadding && px < leftBound - linePadding) ||
                        (prx > rightBound + linePadding && px > rightBound + linePadding) ||
                        (pry < topBound - linePadding && py < topBound - linePadding) ||
                        (pry > bottomBound + linePadding && py > bottomBound + linePadding)) {
                    continue;
                }

                QuestState prereqState = getState(prereq);
                boolean isForbidden = parent.isPrereqForbidden(prereq.getId());
                boolean isLinkEdge = parent.isPrereqLink(prereq.getId());
                boolean isCosmeticEdge = parent.isPrereqCosmetic(prereq.getId());
                boolean isOptional = !isForbidden && parent.hasPerPrereqFlags() &&
                        !parent.isPrereqRequired(prereq.getId());

                int col, style;
                if (isForbidden) {
                    col = prereqState == QuestState.COMPLETED ? 0xFFAA2222 : 0xFF661111;
                    style = prereqState == QuestState.COMPLETED ? 6 : 5;
                } else if (isCosmeticEdge) {
                    col = 0x1AFFFFFF;
                    style = 10;
                } else if (isLinkEdge) {
                    col = prereqState == QuestState.COMPLETED ? 0x6600AA55 :
                            prereqState == QuestState.ACTIVE ? 0x66FFAA00 : 0x26FFFFFF;
                    style = prereqState == QuestState.ACTIVE ? 9 : (prereqState == QuestState.COMPLETED ? 8 : 7);
                } else if (isOptional) {
                    col = prereqState == QuestState.COMPLETED ? 0xFF336644 : 0xFF2A2A3A;
                    style = prereqState == QuestState.COMPLETED ? 4 : 3;
                } else {
                    col = prereqState == QuestState.COMPLETED ? C_LINE_DONE :
                            prereqState == QuestState.ACTIVE ? C_LINE_ACTIVE :
                                    C_LINE_LOCKED;
                    style = prereqState == QuestState.ACTIVE ? 2 : (prereqState == QuestState.COMPLETED ? 1 : 0);
                }
                int shapeOrd = parent.getPrereqLineShape(prereq.getId()) != null ?
                        parent.getPrereqLineShape(prereq.getId()).ordinal() : -1;
                int visOrd = parent.getPrereqLineVisual(prereq.getId()) != null ?
                        parent.getPrereqLineVisual(prereq.getId()).ordinal() : -1;
                int speedOrd = parent.getPrereqLineSpeed(prereq.getId()) != null ?
                        parent.getPrereqLineSpeed(prereq.getId()).ordinal() : -1;
                Boolean arrowOv = parent.getPrereqLineArrow(prereq.getId());
                int arrowOrd = arrowOv == null ? -1 : (arrowOv ? 1 : 0);

                edges.add(new int[] { prx, pry, px, py, col, style, shapeOrd, visOrd, speedOrd, arrowOrd });
                edgeNodes.add(new ResourceLocation[] { prereq.getId(), parent.getId() });
            }
        }

        // edges only just changed (positions/zoom/pan/style) - resolve the expensive per-edge
        // geometry exactly once here instead of leaving it for render() to redo every frame
        // regardless of whether anything moved.
        depLineRenderer.rebuild(edges, edgeNodes, zoom, sz, QuestChroniclesSettings.get());
    }

    /**
     * Lightweight rebuild that preserves the progress and stub-category caches.
     * Use when quest data and category list haven't changed — only zoom, filters,
     * visibility, or node positions. Saves ~2× the work of a full rebuild().
     */
    private void softRebuild() {
        // Preserve caches across the widget teardown
        Map<String, int[]> savedProgress = new HashMap<>(progressCache);
        Map<String, Boolean> savedAttention = new HashMap<>(attentionCache);
        Map<ResourceLocation, List<String>> savedValidation = new HashMap<>(validationCache);
        List<String> savedStubs = stubCategoryCache;
        List<String> savedCats = categoryListCache;
        rebuild();
        progressCache.putAll(savedProgress);
        attentionCache.putAll(savedAttention);
        validationCache.putAll(savedValidation);
        stubCategoryCache = savedStubs;
        categoryListCache = savedCats;
    }

    /**
     * Panning fast-path: shifts all existing node buttons by (dx,dy) without
     * tearing down and recreating every widget. Much cheaper than rebuild().
     */
    private void panCanvas(int dx, int dy) {
        int cl = sidebarW(), cr = width;
        int sz = scaledNodeSize();
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            NodeHitbox btn = e.getValue();
            int nx = btn.getX() + dx;
            int ny = btn.getY() + dy;
            btn.setX(nx);
            btn.setY(ny);
            int[] pos = nodeScreenPos.get(e.getKey());
            if (pos != null) {
                pos[0] = nx;
                pos[1] = ny;
            }
            btn.visible = nx + sz > cl && nx < cr && ny + sz > HEADER_H && ny < height;
        }
        // Pure pan doesn't change any quest's state/color/visibility - just shift the cached
        // line endpoints instead of recomputing the whole tree's colors every dragged pixel
        // (buildLineCache() was previously called here on every mouseDragged event, which made
        // panning visibly laggy on packs with a lot of quests).
        depLineRenderer.panShift(dx, dy);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & 2) != 0;

        // ── Ctrl+F — open search overlay ─────────────────────────────────────
        if (key == 70 && ctrl) {
            openSearchOverlay();
            return true;
        }

        // ── Ctrl+P — toggle the render-time profiler panel (dev tool) ─────────
        // Ctrl+Shift+P — force an immediate detailed log snapshot right now, instead of waiting
        // out the normal 10s interval - for "that just felt laggy, capture it before the EMA
        // smooths it away" investigation.
        if (key == 80 && ctrl) {
            if ((mods & 1) != 0) {
                FrameProfiler.logNow();
                setFeedback("§aProfiler snapshot logged");
            } else {
                FrameProfiler.setEnabled(!FrameProfiler.isEnabled());
                setFeedback(FrameProfiler.isEnabled() ? "§aProfiler ON" : "§7Profiler OFF");
            }
            return true;
        }

        // ── Pin keybind — toggles the pin on whichever quest is under the mouse ──
        if (ChronicleKeyBindings.PIN_QUEST.matches(key, scan) &&
                !(newCatBox != null && newCatBox.isFocused())) {
            if (lastHoveredNodeId != null && playerData != null) {
                playerData.togglePin(lastHoveredNodeId);
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(lastHoveredNodeId));
                QuestNode hovered = QuestTreeRegistry.getQuest(lastHoveredNodeId);
                boolean nowPinned = playerData.isPinned(lastHoveredNodeId);
                setFeedback(hovered != null ?
                        (nowPinned ? "§dPinned: " + hovered.getTitle().getString() :
                                "§7Unpinned: " + hovered.getTitle().getString()) :
                        (nowPinned ? "§dPinned" : "§7Unpinned"));
            }
            return true;
        }

        // ── L — toggle line style (spline ↔ straight) ────────────────────────
        if (key == 76 && !ctrl) {
            QuestChroniclesSettings s = QuestChroniclesSettings.get();
            boolean nowSpline = s.isSplineLines();
            s.setLineStyle(
                    nowSpline ? QuestChroniclesSettings.LineStyle.STRAIGHT : QuestChroniclesSettings.LineStyle.SPLINE);
            s.save();
            setFeedback("Line style: " + (nowSpline ? "Straight" : "Spline"));
            return true;
        }

        if (key == 257 && newCatFormOpen && newCatBox != null && newCatBox.isFocused()) {
            commitNewCategory();
            return true;
        }
        if (key == 256) {
            if (depLineRenderer.isContextMenuOpen()) {
                depLineRenderer.closeContextMenu();
                return true;
            }
            if (!unlockPathHighlight.isEmpty()) {
                unlockPathHighlight.clear();
                return true;
            }
            if (validationOpen) {
                validationOpen = false;
                return true;
            }
            if (statsOpen) {
                statsOpen = false;
                return true;
            }
            if (ctxOpen) {
                ctxOpen = false;
                ctxMoveCatOpen = false;
                return true;
            }
            if (picCtxOpen) {
                closePictureCtx();
                return true;
            }
            if (pictureEditMode != null) {
                BackgroundPictureConfig.save();
                setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
                pictureEditMode = null;
                return true;
            }
            if (newCatFormOpen) {
                newCatFormOpen = false;
                softRebuild();
                return true;
            }
        }
        if (key == 256 && isDevMode && !multiSelection.isEmpty()) {
            multiSelection.clear();
            bulkMoveCatOpen = false;
            return true;
        }
        boolean shift = (mods & 1) != 0;
        if (ctrl && isDevMode) {
            if (key == 90 && !shift) {
                undo();
                return true;
            }
            if (key == 89 || (key == 90 && shift)) {
                redo();
                return true;
            }
        }
        if (key == 70 && !ctrl && !shift) {
            fitToCanvas();
            return true;
        }
        if (key == 47 && isDevMode) { // '?' (slash key with shift = ?)
            if (minecraft != null) minecraft.setScreen(new DevWikiScreen(this));
            return true;
        }
        if (key == 86 && !ctrl && !shift && isDevMode) {
            validationOpen = !validationOpen;
            return true;
        }
        if (key == 73 && !ctrl && !shift && isDevMode) {
            runFtbImport();
            return true;
        }
        // G — toggle subgraph view (ancestors + descendants of selected node)
        if (key == 71 && !ctrl && !shift && isDevMode) {
            subgraphMode = !subgraphMode;
            if (subgraphMode) rebuildSubgraph();
            return true;
        }
        // Ctrl+C — copy selected quest SNBT to clipboard
        if (key == 67 && ctrl && !shift && isDevMode && selectedNode != null) {
            questCopy(selectedNode);
            return true;
        }
        // Ctrl+V — paste from clipboard as new quest
        if (key == 86 && ctrl && !shift && isDevMode) {
            questPaste();
            return true;
        }
        // Ctrl+D — duplicate selected quest (keyboard shortcut for context menu action)
        if (key == 68 && ctrl && !shift && isDevMode && selectedNode != null) {
            duplicateQuest(selectedNode);
            return true;
        }
        // M — toggle minimap
        if (key == 77 && !ctrl && !shift) {
            minimapOpen = !minimapOpen;
            return true;
        }
        // Shift+V — stats dashboard
        if (key == 86 && !ctrl && shift && isDevMode) {
            statsOpen = !statsOpen;
            if (statsOpen) validationOpen = false;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Quest clipboard ───────────────────────────────────────────────────────

    private void questCopy(QuestNode node) {
        String content = QuestFileSaver.readRawSnbt(node);
        if (content == null || content.isBlank()) {
            setFeedback("§cCopy failed — quest file not found on disk");
            return;
        }
        questClipboard = content;
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(content);
        setFeedback("§aCopied SNBT for '" + node.getId().getPath() + "'  (Ctrl+V to paste)");
    }

    private void questPaste() {
        // Prefer our in-memory clipboard; fall back to system clipboard
        String src = questClipboard;
        if (src == null || src.isBlank()) {
            src = minecraft != null ? minecraft.keyboardHandler.getClipboard() : null;
        }
        if (src == null || src.isBlank()) {
            setFeedback("§eNothing to paste (Ctrl+C a quest first)");
            return;
        }
        // Quick validity check — must contain "id:" key
        if (!src.contains("id:")) {
            setFeedback("§eClipboard doesn't look like quest SNBT");
            return;
        }
        try {
            String newPath = QuestFileSaver.pasteQuestFromSnbt(src);
            rebuild();
            setFeedback("§aPasted → " + newPath);
        } catch (IOException e) {
            setFeedback("§cPaste error: " + e.getMessage());
        }
    }

    // ── Chain-wire helpers ────────────────────────────────────────────────────

    /** Sorts selected nodes by canvas X, then wires them A→B→C→D as prerequisites. */
    private void chainMultiSelection() {
        List<QuestNode> ordered = multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (ordered.size() < 2) {
            setFeedback("§eSelect 2+ quests to chain");
            return;
        }
        int wired = 0;
        for (int i = 1; i < ordered.size(); i++) {
            QuestNode child = ordered.get(i);
            QuestNode parent = ordered.get(i - 1);
            if (!child.getPrerequisites().contains(parent)) {
                child.addPrerequisite(parent);
                saveNodePrereqsToDisk(child);
                wired++;
            }
        }
        buildLineCache();
        rebuild();
        setFeedback("§aChained " + ordered.size() + " quests (" + wired + " new link" + (wired == 1 ? "" : "s") + ")");
    }

    /** Wires the leftmost selected node as a prerequisite of all others. */
    private void fanFromLeftmost() {
        List<QuestNode> nodes = multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (nodes.size() < 2) {
            setFeedback("§eSelect 2+ quests to fan");
            return;
        }
        QuestNode root = nodes.get(0);
        int wired = 0;
        for (int i = 1; i < nodes.size(); i++) {
            QuestNode child = nodes.get(i);
            if (!child.getPrerequisites().contains(root)) {
                child.addPrerequisite(root);
                saveNodePrereqsToDisk(child);
                wired++;
            }
        }
        buildLineCache();
        rebuild();
        setFeedback("§aFanned from '" + root.getId().getPath() + "' to " + wired + " quest" + (wired == 1 ? "" : "s"));
    }

    // ── FTB Quests import ─────────────────────────────────────────────────────

    private void runFtbImport() {
        if (minecraft == null) return;
        Path base = minecraft.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        Path importDir = base.resolve("ftb_import");
        try {
            java.nio.file.Files.createDirectories(importDir);
            FtbQuestsImporter.ImportResult r = FtbQuestsImporter.importDirectory(importDir, base);
            if (r.imported() == 0 && r.skipped() == 0) {
                setFeedback("§eNo .snbt files found in config/phoenix_chronicles/ftb_import/");
            } else {
                setFeedback("§aImported " + r.imported() + " quests" +
                        (r.skipped() > 0 ? " §c(" + r.skipped() + " skipped)" : "") +
                        (r.warnings().isEmpty() ? "" : " §8— " + r.warnings().size() + " warnings"));
                if (r.imported() > 0) {
                    QuestFileLoader.reloadAllQuestsFromDisk();
                    // New lang keys were just written for these quests - reload resource packs
                    // so translation keys resolve immediately instead of showing raw key text
                    // until the next manual /reload or restart.
                    ChroniclesLangPack.reload();
                    rebuild();
                }
            }
        } catch (Exception e) {
            setFeedback("§cFTB import error: " + e.getMessage());
        }
    }

    // ── Undo / redo ───────────────────────────────────────────────────────────

    private void pushUndo(Runnable action) {
        undoStack.push(action);
        if (undoStack.size() > MAX_UNDO) undoStack.pollLast();
        redoStack.clear(); // new action clears the redo branch
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            setFeedback("Nothing to undo");
            return;
        }
        Runnable action = undoStack.pop();
        action.run();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            setFeedback("Nothing to redo");
            return;
        }
        Runnable action = redoStack.pop();
        action.run();
    }

    // ── Quest duplication ─────────────────────────────────────────────────────

    private void duplicateQuest(QuestNode source) {
        if (!QuestFileSaver.doesQuestFileExist(source)) {
            setFeedback("Cannot duplicate — source file not found on disk");
            return;
        }
        try {
            String newPath = QuestFileSaver.duplicateQuestOnDisk(source);
            ResourceLocation newId = new ResourceLocation(source.getId().getNamespace(), newPath);
            QuestNode duplicated = QuestTreeRegistry.getQuest(newId);
            if (duplicated != null) {
                pushUndo(() -> {
                    QuestTreeRegistry.removeQuest(newId);
                    deleteQuestFiles(duplicated);
                    if (selectedNode == duplicated) selectedNode = null;
                    rebuild();
                    setFeedback("Undo: duplicate removed");
                });
            }
            rebuild();
            setFeedback("Duplicated → " + newPath + "  (Ctrl+Z to undo)");
        } catch (IOException e) {
            e.printStackTrace();
            setFeedback("Duplicate failed: " + e.getMessage());
        }
    }

    private void commitNewCategory() {
        if (newCatBox == null) return;
        String name = newCatBox.getValue().trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!name.isEmpty()) {
            List<String> current = buildCategoryList();
            if (!current.contains(name)) {
                // Add it to the list and persist to disk immediately
                current.add(name);
                saveStubCategories(current);
                selectedCategory = name;
                setFeedback("Category '" + friendly(name) + "' created");
            }
        }
        newCatFormOpen = false;
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (ctxMoveCatOpen && ctxNode != null) {
            List<String> cats = buildCategoryList();
            cats.remove("ALL");
            int subX = ctxX + CTX_W + 2;
            int subY = ctxMoveCatY(buildCtxItems());
            int visibleRows = Math.min(cats.size(), CTX_MOVE_CAT_MAX_ROWS);
            int subH = visibleRows * CTX_ROW + 4;
            if (mx >= subX && mx <= subX + CTX_W && my >= subY && my <= subY + subH) {
                int maxScroll = Math.max(0, cats.size() - CTX_MOVE_CAT_MAX_ROWS);
                ctxMoveCatScroll = Math.max(0, Math.min(maxScroll, ctxMoveCatScroll - (int) Math.signum(delta)));
                return true;
            }
        }
        // Interactive picture resize mode hijacks the scroll wheel away from canvas zoom entirely
        // while active - see pictureEditMode's field comment.
        if (pictureEditMode != null) {
            // Shift+scroll for fine 5% steps (detail work), plain scroll for a faster 20% step -
            // the old flat 10% step made reaching the top of the (much wider, now up to 4096px)
            // range feel like it was capped much lower than it actually was.
            float step = hasShiftDown() ? 1.05f : 1.2f;
            float factor = delta > 0 ? step : (1f / step);
            pictureEditMode.w = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.w * factor));
            pictureEditMode.h = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.h * factor));
            return true;
        }
        int cl = sidebarW(), cr = width;
        if (mx <= cl && my > HEADER_H) {
            // Icon tiles are taller than the old text rows, so with more than a handful of
            // categories the list can overflow the sidebar height entirely, pushing the "+"
            // and gear buttons off-screen and unreachable. Scroll the row list instead of
            // falling through to vanilla's (no-op here) scroll handling.
            int maxScroll = Math.max(0, sidebarContentHeight() - sidebarScrollAreaHeight());
            sidebarScrollY = Math.max(0, Math.min(maxScroll, sidebarScrollY - (int) (delta * SIDEBAR_CAT_ROW_H)));
            return true;
        }
        if (mx <= cl || mx >= cr || my <= HEADER_H) return super.mouseScrolled(mx, my, delta);

        float oldZoom = zoom;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom + (float) delta * ZOOM_STEP));
        if (zoom == oldZoom) return true;

        // Anchor zoom to mouse cursor: keep the canvas point under the cursor fixed
        float ratio = zoom / oldZoom;
        int canvasMx = (int) mx - cl;
        int canvasMy = (int) my - HEADER_H;
        viewOffX = (int) (canvasMx - (canvasMx - viewOffX) * ratio);
        viewOffY = (int) (canvasMy - (canvasMy - viewOffY) * ratio);

        softRebuild();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Interactive picture resize mode: left-click/drag pans the picture instead of the
        // canvas (handled in mouseDragged), right-click confirms and exits the mode.
        if (pictureEditMode != null) {
            if (btn == 1) {
                BackgroundPictureConfig.save();
                setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
                pictureEditMode = null;
            }
            return true;
        }

        if (btn == 0 && handleTutorialClick(mx, my)) return true;

        // Minimap click — pan canvas to clicked point
        if (btn == 0 && isInMinimap(mx, my)) {
            mmDragging = true;
            minimapPanTo(mx, my, sidebarW());
            softRebuild();
            return true;
        }

        int cl = sidebarW(), cr = width;

        if (btn == 0) {
            // Inspector removed from overview — all quest detail interactions now in QuestTasksScreen

            // ── Questbook title (icon + name above the category list) ─────────────
            if (questbookTitleHovered((int) mx, (int) my)) {
                if (minecraft != null) minecraft.setScreen(new QuestbookTitleScreen(this));
                return true;
            }

            // ── Title bar: grid-size pill click ──────────────────────────────────
            if (my >= 0 && my < TOOLBAR_Y) {
                // Recompute grid pill bounds (same formula as in render)
                String zoomStr2 = Math.round(zoom * 100) + "%";
                int zw2 = font.width(zoomStr2);
                int zx2 = cr - zw2 - 10;
                String gridLabel2 = (gridSnap == 1) ? "Grid: free" : "Grid: " + gridSnap;
                int gw2 = font.width(gridLabel2);
                int gpx2 = zx2 - gw2 - 18;
                if (mx >= gpx2 - 3 && mx < gpx2 + gw2 + 5 && my >= 3 && my < 16) {
                    // Cycle to next grid size
                    for (int gi = 0; gi < GRID_SNAP_CYCLE.length; gi++) {
                        if (GRID_SNAP_CYCLE[gi] == gridSnap) {
                            gridSnap = GRID_SNAP_CYCLE[(gi + 1) % GRID_SNAP_CYCLE.length];
                            break;
                        }
                    }
                    return true;
                }

                // ── Title bar: subgraph mode pill click (dev only) ────────────────
                if (isDevMode) {
                    String sgLabel2 = subgraphMode ? "Subgraph: " + subgraphNodes.size() : "Subgraph";
                    int sgw2 = font.width(sgLabel2);
                    int sgx2 = gpx2 - sgw2 - 18;
                    if (mx >= sgx2 - 3 && mx < sgx2 + sgw2 + 5 && my >= 3 && my < 16) {
                        subgraphMode = !subgraphMode;
                        if (subgraphMode) rebuildSubgraph();
                        return true;
                    }
                }
            }

            // ── Toolbar right-side button clicks (Settings, Fit) ──────────────────
            if (my >= TOOLBAR_Y && my < HEADER_H) {
                int rx = cr - 4;
                int fitW = font.width("⊞ Fit") + 10;
                int settingsW = font.width("⚙") + 10;

                // Fit button
                rx -= fitW + 2;
                if (mx >= rx && mx < rx + fitW) {
                    fitToCanvas();
                    return true;
                }

                // Settings button
                rx -= settingsW + 2;
                if (mx >= rx && mx < rx + settingsW && minecraft != null) {
                    minecraft.setScreen(new SettingsScreen(this));
                    return true;
                }

                // Wiki button (dev only)
                if (isDevMode) {
                    int wikiW = font.width("?") + 10;
                    rx -= wikiW + 2;
                    if (mx >= rx && mx < rx + wikiW && minecraft != null) {
                        minecraft.setScreen(new DevWikiScreen(this));
                        return true;
                    }
                }

                // Minimap toggle
                {
                    String mmLabel = "⊡ Map";
                    int mmW = font.width(mmLabel) + 10;
                    rx -= mmW + 2;
                    if (mx >= rx && mx < rx + mmW) {
                        minimapOpen = !minimapOpen;
                        return true;
                    }
                }
                // Hide-completed toggle
                String hideLabel = hideCompleted ? "§a✔ Hide done" : "§8✔ Hide done";
                int hideW = font.width(hideLabel.replaceAll("§.", "")) + 10;
                rx -= hideW + 2;
                if (mx >= rx && mx < rx + hideW) {
                    hideCompleted = !hideCompleted;
                    softRebuild();
                    return true;
                }
            }

            // ── Filter pill clicks ─────────────────────────────────────────────
            int[][] pills = filterPillBounds(cl, cr);
            for (int i = 0; i < FILTER_KEYS.length; i++) {
                int[] b = pills[i];
                if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                    stateFilter = FILTER_KEYS[i];
                    selectedNode = null;
                    softRebuild();
                    return true;
                }
            }

            // ── Sidebar collapse/expand toggle - works regardless of collapsed state ──
            if (sidebarCollapseToggleHovered((int) mx, (int) my)) {
                sidebarCollapsed = !sidebarCollapsed;
                sidebarScrollY = 0;
                rebuild(); // node placement/canvas bounds depend on sidebarW()
                return true;
            }

            if (!sidebarCollapsed) {
                // ── Gear (utilities) click — left=open editor, right=export lang ──
                if (gearHovered((int) mx, (int) my) && minecraft != null) {
                    minecraft.setScreen(new LangEditorScreen(this));
                    return true;
                }

                // ── Sidebar "+ Category" / "Cancel" pill ──────────────────────────
                if (newCatButtonHovered((int) mx, (int) my)) {
                    if (newCatFormOpen) {
                        newCatFormOpen = false;
                        rebuild();
                    } else if (minecraft != null) {
                        minecraft.setScreen(new NewChapterChoiceScreen(this));
                    }
                    return true;
                }

                // ── Sidebar folder headers / category rows ────────────────────────
                int scrollTop = HEADER_H + 1 + SIDEBAR_COLLAPSE_TOGGLE_H;
                int scrollBottom = scrollTop + sidebarScrollAreaHeight();
                if (mx < sidebarW() - 1 && my >= scrollTop && my < scrollBottom) {
                    for (SidebarRow row : buildSidebarRows()) {
                        if (my < row.y() || my >= row.y() + row.height()) continue;
                        // Dev mode: defer the actual action to mouseReleased so a drag can be
                        // told apart from a click - previously the only way to reorder chapter
                        // folders or move a category into/out of one was right-click menus
                        // (Folder dropdown, "top"/"bottom" style), which several testers asked
                        // to be drag-and-drop instead, FTBQ-style.
                        if (isDevMode) {
                            sidebarDragRow = row;
                            sidebarDragStartX = (int) mx;
                            sidebarDragStartY = (int) my;
                            sidebarDragMoved = false;
                            return true;
                        }
                        if (row.isFolder()) {
                            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.toggleCollapsed(row.id());
                            rebuild();
                        } else if (row.locked()) {
                            // Not gated for devs (isDevMode already routed to the drag-setup
                            // branch above and returned) - a real player can see the sub-chapter
                            // exists but can't open it until its parent has a completed quest.
                            setFeedback("§7Locked — complete a quest in the parent chapter first");
                        } else {
                            selectedCategory = row.id();
                            selectedNode = null;
                            PhantasiaCompat.closePreview(phantasiaPreview);
                            phantasiaPreview = null;
                            // zoom/viewOffX/Y no longer hard-reset here - rebuild() below detects
                            // the category change and restores that chapter's own saved pan/zoom
                            // (or the default centered view if it's never had one saved).
                            ctxOpen = false;
                            ctxMoveCatOpen = false;
                            rebuild();
                        }
                        return true;
                    }
                }
            }
        }

        if (btn == 1 && gearHovered((int) mx, (int) my) && isDevMode) {
            Path base = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            LangEditorScreen.writeEnUsJson(base);
            setFeedback("§aExported lang/en_us.json");
            return true;
        }

        // ── Right-click a sidebar chapter tab: open its icon/color/name editor ──
        if (btn == 1 && isDevMode && !sidebarCollapsed) {
            SidebarRow hitRow = sidebarRowAt(buildSidebarRows(), (int) mx, (int) my);
            if (hitRow != null && !hitRow.isFolder() && minecraft != null) {
                minecraft.setScreen(new CategoryThemeScreen(this, hitRow.id()));
                return true;
            }
        }

        // ── Bulk-ops panel clicks ─────────────────────────────────────────────
        if (btn == 0 && isDevMode && multiSelection.size() >= 2) {
            int bx = cl + 4, by = HEADER_H + 4;
            int bh = 38;
            if ((int) mx >= bx && (int) mx <= bx + 360 && (int) my >= by && (int) my <= by + bh) {
                // Shape picker row hit-test
                String[] shapeIds = { "SQUARE", "CIRCLE", "DIAMOND", "HEXAGON", "TRIANGLE", "STAR", "PENTAGON",
                        "SHIELD", "CROSS", "CUSTOM" };
                int slotW = 14, startX = bx + 6, slotY = by + 24;
                for (int i = 0; i < shapeIds.length; i++) {
                    int sx = startX + i * (slotW + 2);
                    if ((int) mx >= sx && (int) mx < sx + slotW && (int) my >= slotY && (int) my < slotY + 12) {
                        String newShape = shapeIds[i];
                        if ("CUSTOM".equals(newShape)) {
                            List<ResourceLocation> targets = new ArrayList<>(multiSelection);
                            if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                                for (ResourceLocation id : targets) {
                                    QuestNode n = QuestTreeRegistry.getQuest(id);
                                    if (n != null) {
                                        n.setShapeType("CUSTOM");
                                        n.setShapeTexture(rl);
                                        saveNodeShapeToDisk(n, "CUSTOM");
                                        saveNodeShapeTextureToDisk(n);
                                    }
                                }
                                setFeedback("Shape → CUSTOM for " + targets.size() + " quests");
                                rebuild();
                            }));
                            return true;
                        }
                        for (ResourceLocation id : multiSelection) {
                            QuestNode n = QuestTreeRegistry.getQuest(id);
                            if (n != null) {
                                n.setShapeType(newShape);
                                // FIXED: Routed to restored local wrapper hook
                                saveNodeShapeToDisk(n, newShape);
                            }
                        }
                        setFeedback("Shape → " + newShape + " for " + multiSelection.size() + " quests");
                        rebuild();
                        return true;
                    }
                }
                int actX = startX + shapeIds.length * (slotW + 2) + 8;
                // "Move cat" toggle
                if ((int) mx >= actX && (int) mx < actX + 58 && (int) my >= slotY && (int) my < slotY + 12) {
                    bulkMoveCatOpen = !bulkMoveCatOpen;
                    return true;
                }
                // Bulk move cat submenu
                if (bulkMoveCatOpen) {
                    List<String> moveCats = buildCategoryList();
                    moveCats.remove("ALL");
                    int subX = actX, subY = slotY + 13, subRH = 11;
                    for (int ci = 0; ci < moveCats.size(); ci++) {
                        int ry = subY + 2 + ci * subRH;
                        if ((int) mx >= subX + 2 && (int) mx < subX + 90 - 2 && (int) my >= ry &&
                                (int) my < ry + subRH) {
                            String newCat = moveCats.get(ci);
                            for (ResourceLocation sid : new ArrayList<>(multiSelection)) {
                                QuestNode sn = QuestTreeRegistry.getQuest(sid);
                                if (sn != null) {
                                    sn.setCategory(newCat);
                                    // FIXED: Routed to restored local wrapper hook
                                    saveNodeCategoryToDisk(sn, newCat);
                                }
                            }
                            bulkMoveCatOpen = false;
                            setFeedback("Moved " + multiSelection.size() + " quests to " + friendly(newCat));
                            rebuild();
                            return true;
                        }
                    }
                }
                // Delete all selected
                int delX = actX + 62;
                if ((int) mx >= delX && (int) mx < delX + 44 && (int) my >= slotY && (int) my < slotY + 12) {
                    int count = multiSelection.size();
                    for (ResourceLocation id : new ArrayList<>(multiSelection)) {
                        QuestNode n = QuestTreeRegistry.getQuest(id);
                        if (n != null) {
                            QuestTreeRegistry.removeQuest(id);
                            // FIXED: Routed to restored local wrapper hook
                            deleteQuestFiles(n);
                        }
                    }
                    multiSelection.clear();
                    rebuild();
                    setFeedback("Deleted " + count + " quests");
                    return true;
                }
                return true; // absorb all clicks on the panel
            }
        }

        // ── Line context menu ─────────────────────────────────────────────────
        if (depLineRenderer.isContextMenuOpen() && btn == 0) {
            depLineRenderer.handleContextMenuClick((int) mx, (int) my, width, height,
                    this::rebuild, this::setFeedback, this::openLineSettingsFor);
            depLineRenderer.closeContextMenu();
            return true;
        }
        if (depLineRenderer.isContextMenuOpen()) {
            depLineRenderer.closeContextMenu();
            return true;
        }

        // ── Background picture context menu ────────────────────────────────────
        if (picCtxOpen && btn == 0) {
            handlePictureCtxClick((int) mx, (int) my);
            return true;
        }
        if (picCtxOpen) {
            closePictureCtx();
            return true;
        }

        // ── Context menu ──────────────────────────────────────────────────────
        if (ctxOpen && btn == 0) {
            if (handleCtxClick((int) mx, (int) my)) return true;
            ctxOpen = false;
            ctxMoveCatOpen = false;
            return true;
        }

        // ── Ctrl + left-click = toggle multi-select (dev mode) ───────────────
        if (btn == 0 && isDevMode && hasControlDown() && !hasShiftDown()) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    if (multiSelection.contains(e.getKey())) multiSelection.remove(e.getKey());
                    else multiSelection.add(e.getKey());
                    return true;
                }
            }
            // Clicking empty canvas clears selection
            multiSelection.clear();
            return true;
        }

        // ── Alt + left-click = start prerequisite link drag (dev mode) ──────
        if (btn == 0 && isDevMode && hasAltDown() && !hasShiftDown()) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    linkDragSource = QuestTreeRegistry.getQuest(e.getKey());
                    linkDragX = (int) mx;
                    linkDragY = (int) my;
                    return true;
                }
            }
        }

        // ── Shift + left-click = dev node drag (or group drag) ───────────────
        if (btn == 0 && isDevMode && hasShiftDown()) {
            // Try node first
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (draggedNode != null) {
                        // Capture position before drag so Ctrl+Z can restore it
                        final int preX = draggedNode.getCustomX(), preY = draggedNode.getCustomY();
                        final QuestNode capturedNode = draggedNode;
                        pushUndo(() -> {
                            capturedNode.setCustomPosition(preX, preY);
                            // FIXED: Routed to restored local wrapper hook
                            saveNodeToDisk(capturedNode);
                            rebuild();
                        });
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        selectedNode = draggedNode;
                        if (subgraphMode) rebuildSubgraph();
                    }
                    return true;
                }
            }
            // Try group label bar
            QuestGroup hitGrp = groupAtLabelBar(mx, my, cl);
            if (hitGrp != null) {
                draggedGroup = hitGrp;
                int sx = (int) (hitGrp.getX() * posZoom()) + viewOffX + cl;
                int sy = (int) (hitGrp.getY() * posZoom()) + viewOffY + HEADER_H;
                groupDragGrabX = (int) mx - sx;
                groupDragGrabY = (int) my - sy;
                return true;
            }
            // Try a placed background picture
            BackgroundPictureConfig.Picture hitPic = pictureAt(mx, my, cl);
            if (hitPic != null) {
                final BackgroundPictureConfig.Picture capturedPic = hitPic;
                final float preX = hitPic.x, preY = hitPic.y;
                pushUndo(() -> {
                    capturedPic.x = preX;
                    capturedPic.y = preY;
                    BackgroundPictureConfig.save();
                    setFeedback("Undo: picture moved back");
                });
                draggedPicture = hitPic;
                int[] rect = BackgroundPictureRenderer.screenRect(hitPic, cl, HEADER_H, posZoom(), viewOffX, viewOffY);
                pictureDragGrabX = (int) mx - rect[0];
                pictureDragGrabY = (int) my - rect[1];
                return true;
            }
        }

        // ── Shift + right-click = open quest directly ─────────────────────────
        if (btn == 1 && hasShiftDown() && mx > cl && mx < cr) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null && (getState(node) != QuestState.LOCKED || isDevMode)) {
                        onNodeClicked(node);
                        return true;
                    }
                }
            }
            return true;
        }

        // ── Right-click on canvas = dev context menu ──────────────────────────
        if (btn == 1 && isDevMode && mx > cl && mx < cr) {
            // A second right-click while a menu is already open used to silently re-target it
            // to the new click location/node and restart its open-fade animation, instead of
            // requiring the first one to be dismissed - close it instead, matching how a
            // right-click menu is expected to behave (toggle, not stack/retarget).
            if (ctxOpen) {
                ctxOpen = false;
                return true;
            }
            QuestNode hit = null;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    hit = QuestTreeRegistry.getQuest(e.getKey());
                    break;
                }
            }
            QuestGroup hitGrp = (hit == null) ? groupAtLabelBar(mx, my, cl) : null;
            // Right-click on a picture opens its own small menu instead of the shared node/group
            // one - see the field comment above picCtxOpen for why this is a separate system.
            if (hit == null && hitGrp == null) {
                BackgroundPictureConfig.Picture hitPic = pictureAt(mx, my, cl);
                if (hitPic != null) {
                    ctxOpen = false;
                    openPictureCtx((int) mx, (int) my, hitPic);
                    return true;
                }
            }
            // Check if near a line (higher priority than empty-canvas menu)
            if (hit == null && hitGrp == null && depLineRenderer.tryOpenContextMenuAt((int) mx, (int) my, 6)) {
                ctxOpen = false;
                return true;
            }
            openCtx((int) mx, (int) my, hit, hitGrp);
            return true;
        }
        // ── Right-click non-dev: show unlock path for locked quests, dep lines on empty canvas ──
        if (btn == 1 && !isDevMode && mx > cl && mx < cr) {
            boolean hitNode = false;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null && getState(node) == QuestState.LOCKED) {
                        computeUnlockPath(node);
                        hitNode = true;
                    }
                    break;
                }
            }
            if (!hitNode) {
                unlockPathHighlight.clear();
                // Empty canvas right-click → open dep line settings
                if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedCategory));
            }
        }

        // ── Left-click on canvas = pan start / double-click to create ──────────
        if (btn == 0 && mx > cl && mx < cr && my > HEADER_H) {
            // Node buttons are plain hit-test data (NodeHitbox), not real widgets, so a plain
            // click on one no longer gets dispatched via super.mouseClicked() - check manually,
            // same active/visible gating a vanilla Button.mouseClicked() would have applied.
            boolean handled = false;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                NodeHitbox hb = e.getValue();
                if (hb.active && hb.isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null) onNodeClicked(node);
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                if (isDevMode && minecraft != null) {
                    long now = System.currentTimeMillis();
                    int imx = (int) mx, imy = (int) my;
                    if (hasShiftDown() && now - lastCanvasClickTime < 350 && Math.abs(imx - lastCanvasClickX) < 10 &&
                            Math.abs(imy - lastCanvasClickY) < 10) {
                        // Shift + double-click on empty canvas → open creator pre-positioned.
                        // Plain double-click was firing accidentally during normal fast navigation
                        // clicks, so it's now gated behind Shift to require deliberate intent.
                        int canvasX = (int) ((imx - cl - viewOffX) / posZoom());
                        int canvasY = (int) ((imy - HEADER_H - viewOffY) / posZoom());
                        lastCanvasClickTime = 0;
                        minecraft.setScreen(new QuestCreatorScreen(this, canvasX, canvasY));
                        return true;
                    }
                    lastCanvasClickTime = System.currentTimeMillis();
                    lastCanvasClickX = imx;
                    lastCanvasClickY = imy;
                }
                isPanning = true;
            }
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleCtxClick(int mx, int my) {
        List<CtxItem> items = buildCtxItems();
        int x = ctxX, y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW; // skip title row

        for (CtxItem item : items) {
            if (item.isSep) {
                y += CTX_SEP;
                continue;
            }
            if (mx >= x && mx <= x + CTX_W && my >= y && my <= y + CTX_ROW) {
                item.action.run();
                return true;
            }
            y += CTX_ROW;
        }

        if (ctxMoveCatOpen) {
            List<String> cats = buildCategoryList();
            cats.remove("ALL");
            int subX = x + CTX_W + 2;
            int subY = ctxMoveCatY(items);
            int visibleRows = Math.min(cats.size(), CTX_MOVE_CAT_MAX_ROWS);
            for (int i = ctxMoveCatScroll; i < Math.min(cats.size(), ctxMoveCatScroll + visibleRows); i++) {
                int ry = subY + (i - ctxMoveCatScroll) * CTX_ROW;
                if (mx >= subX && mx <= subX + CTX_W && my >= ry && my <= ry + CTX_ROW) {
                    String newCat = cats.get(i);
                    if (ctxNode != null) {
                        ctxNode.setCategory(newCat);
                        saveNodeCategoryToDisk(ctxNode, newCat);
                        setFeedback("Moved to " + friendly(newCat));
                    }
                    ctxOpen = false;
                    ctxMoveCatOpen = false;
                    rebuild();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && sidebarDragRow != null) {
            if (!sidebarDragMoved && (Math.abs(mx - sidebarDragStartX) > SIDEBAR_DRAG_THRESHOLD ||
                    Math.abs(my - sidebarDragStartY) > SIDEBAR_DRAG_THRESHOLD)) {
                sidebarDragMoved = true;
            }
            return true;
        }
        if (pictureEditMode != null) {
            if (btn == 0) {
                pictureEditMode.x += (float) (dx / posZoom());
                pictureEditMode.y += (float) (dy / posZoom());
            }
            return true;
        }
        // Minimap drag — pan canvas as user drags over the minimap
        if (btn == 0 && mmDragging) {
            minimapPanTo(mx, my, sidebarW());
            return true;
        }
        if (btn == 0 && linkDragSource != null) {
            linkDragX = (int) mx;
            linkDragY = (int) my;
            return true;
        }
        if (btn == 0) {
            if (draggedGroup != null) {
                int cl = sidebarW();
                int screenX = (int) mx - groupDragGrabX;
                int screenY = (int) my - groupDragGrabY;
                draggedGroup.setX((int) ((screenX - cl - viewOffX) / posZoom()));
                draggedGroup.setY((int) ((screenY - HEADER_H - viewOffY) / posZoom()));
                return true;
            }
            if (draggedPicture != null) {
                int cl = sidebarW();
                // Grab offset was measured against the picture's top-left corner; convert back
                // through that corner rather than directly through the mouse point, then re-add
                // half the picture's size since Picture.x/y is center-anchored.
                int screenX = (int) mx - pictureDragGrabX;
                int screenY = (int) my - pictureDragGrabY;
                float canvasX = (screenX - cl - viewOffX) / posZoom() + draggedPicture.w / 2f;
                float canvasY = (screenY - HEADER_H - viewOffY) / posZoom() + draggedPicture.h / 2f;
                draggedPicture.x = canvasX;
                draggedPicture.y = canvasY;
                return true;
            }
            if (draggedNode != null) {
                int cl = sidebarW();
                int rawX = (int) mx - dragGrabX;
                int rawY = (int) my - dragGrabY;
                // Snap logical position to grid (Shift = free/pixel-perfect)
                int logX = (int) ((rawX - cl - viewOffX) / posZoom());
                int logY = (int) ((rawY - HEADER_H - viewOffY) / posZoom());
                int snap = hasShiftDown() ? 1 : gridSnap;
                logX = Math.round((float) logX / snap) * snap;
                logY = Math.round((float) logY / snap) * snap;
                // Recompute screen position from snapped logical position
                int nx = (int) (logX * posZoom()) + cl + viewOffX;
                int ny = (int) (logY * posZoom()) + HEADER_H + viewOffY;
                NodeHitbox b = nodeButtons.get(draggedNode.getId());
                if (b != null) {
                    b.setX(nx);
                    b.setY(ny);
                }
                nodeScreenPos.put(draggedNode.getId(), new int[] { nx, ny });
                draggedNode.setCustomPosition(logX, logY);
                buildLineCache();
                return true;
            }
            if (isPanning) {
                viewOffX += (int) dx;
                viewOffY += (int) dy;
                pendingPanDX += (int) dx;
                pendingPanDY += (int) dy;
                return true;
            }
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && sidebarDragRow != null) {
            SidebarRow source = sidebarDragRow;
            boolean moved = sidebarDragMoved;
            sidebarDragRow = null;
            sidebarDragMoved = false;
            if (!moved) {
                // Not actually a drag - fall through to the original click behavior.
                if (source.isFolder()) {
                    net.phoenixvine.chronicles.registry.ChapterFolderRegistry.toggleCollapsed(source.id());
                    rebuild();
                } else {
                    selectedCategory = source.id();
                    selectedNode = null;
                    PhantasiaCompat.closePreview(phantasiaPreview);
                    phantasiaPreview = null;
                    ctxOpen = false;
                    ctxMoveCatOpen = false;
                    rebuild();
                }
                return true;
            }
            handleSidebarDrop(source, (int) mx, (int) my);
            return true;
        }
        if (btn == 0 && mmDragging) {
            mmDragging = false;
            return true;
        }
        if (btn == 0 && linkDragSource != null) {
            QuestNode src = linkDragSource;
            linkDragSource = null;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode target = QuestTreeRegistry.getQuest(e.getKey());
                    if (target != null && target != src && !target.getPrerequisites().contains(src)) {
                        target.addPrerequisite(src);
                        target.setPrereqLink(src.getId(), true);
                        saveNodePrereqsToDisk(target);
                        setFeedback("§aLinked: " + src.getId().getPath() + " → prereq of " + target.getId().getPath());
                        buildLineCache();
                        rebuild();
                    }
                    return true;
                }
            }
            return true;
        }
        if (btn == 0) {
            if (draggedGroup != null) {
                QuestGroupManager.save(groupsConfigPath());
                draggedGroup = null;
                return true;
            }
            if (draggedPicture != null) {
                BackgroundPictureConfig.save();
                draggedPicture = null;
                return true;
            }
            if (draggedNode != null) {
                saveNodeToDisk(draggedNode);
                draggedNode = null;
                softRebuild();
                return true;
            }
            isPanning = false;
        }
        return super.mouseReleased(mx, my, btn);
    }

    // ── Context menu construction ─────────────────────────────────────────────

    private record CtxItem(String label, String color, boolean isSep, boolean isDanger, Runnable action) {

        static CtxItem sep() {
            return new CtxItem("", "", true, false, () -> {});
        }
    }

    private List<CtxItem> buildCtxItems() {
        List<CtxItem> items = new ArrayList<>();
        boolean hasNode = (ctxNode != null);
        boolean hasGroup = (ctxGroup != null);
        // Opening this menu at all still requires isDevMode (see mouseClicked's right-click
        // gate) so a privileged user can always reach it to exit test mode - but every actual
        // EDITING action below is additionally gated on !testMode, so "test mode" genuinely
        // behaves like a player preview instead of just being dev mode with an extra toggle.
        boolean canEdit = !testMode;

        // New quest (only on empty canvas, not on existing quest/group)
        if (!hasNode && !hasGroup && canEdit) {
            items.add(new CtxItem("+ New quest", "§a", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new QuestCreatorScreen(this));
                    }));
        }

        // Dependency lines (empty canvas — always shown for all right-click contexts)
        if (!hasNode && !hasGroup) {
            final String cat = selectedCategory;
            items.add(new CtxItem("Dependency Lines", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new DepLineSettingsScreen(this, cat));
                    }));
        }

        // Chain-wire ops (multi-select, empty canvas right-click)
        if (!hasNode && !hasGroup && canEdit && multiSelection.size() >= 2) {
            items.add(CtxItem.sep());
            items.add(new CtxItem("→ Chain selected (left→right)", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        chainMultiSelection();
                    }));
            items.add(new CtxItem("★ Fan from leftmost", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        fanFromLeftmost();
                    }));
        }

        // Paste from clipboard
        if (!hasNode && !hasGroup && canEdit) {
            String label = questClipboard != null ? "⎘ Paste quest" : "⎘ Paste quest §8(clipboard)";
            items.add(new CtxItem(label, "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        questPaste();
                    }));
        }

        // Dev-mode quick-toggles on empty canvas
        if (!hasNode && !hasGroup && isDevMode) {
            items.add(CtxItem.sep());
            items.add(new CtxItem((testMode ? "§c⏵ Exit Player Mode" : "⏵ Enter Player Mode"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        testMode = !testMode;
                        if (!testMode) testModeData = new PlayerQuestData();
                        rebuild();
                    }));
            if (testMode) {
                items.add(new CtxItem("↺ Reset Player Mode Data", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            testModeData = new PlayerQuestData();
                            rebuild();
                        }));
            }
            items.add(new CtxItem((subgraphMode ? "§b⊛ Exit subgraph" : "⊛ Subgraph mode"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        subgraphMode = !subgraphMode;
                        // This entry only appears on the empty-canvas context menu (!hasNode), so
                        // ctxNode is always null here - checking it made rebuildSubgraph() never
                        // run and subgraphNodes stayed empty, so the dim overlay never showed.
                        // Use whatever node is already selected instead.
                        if (subgraphMode && selectedNode != null) {
                            rebuildSubgraph();
                        }
                    }));
            items.add(new CtxItem((statsOpen ? "§b∑ Hide stats" : "∑ Show stats"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        statsOpen = !statsOpen;
                        if (statsOpen) validationOpen = false;
                    }));
        }

        // Group creation & theme (only when right-clicking empty canvas, not on a node/group)
        if (!hasNode && !hasGroup && canEdit) {
            int cl = sidebarW();
            items.add(new CtxItem("+ New group here", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        int lx = (int) ((ctxX - cl - viewOffX) / posZoom());
                        int ly = (int) ((ctxY - HEADER_H - viewOffY) / posZoom());
                        minecraft.setScreen(new QuestGroupEditorScreen(this, selectedCategory, null, lx, ly));
                    }));
            items.add(new CtxItem("Edit chapter theme…", "§d", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new CategoryThemeScreen(this, selectedCategory));
                    }));
            items.add(new CtxItem("🖼 Add picture…", "§d", false, false,
                    () -> {
                        ctxOpen = false;
                        final float px = (ctxX - cl - viewOffX) / posZoom();
                        final float py = (ctxY - HEADER_H - viewOffY) / posZoom();
                        final String cat = selectedCategory;
                        minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                            BackgroundPictureConfig.Picture pic = new BackgroundPictureConfig.Picture();
                            pic.texture = rl;
                            pic.x = px;
                            pic.y = py;
                            BackgroundPictureConfig.add(cat, pic);
                            setFeedback("Picture placed — shift+drag to move, right-click to remove");
                        }));
                    }));
            items.add(CtxItem.sep());
            items.add(new CtxItem("⊞ Auto-arrange chapter", "§e", false, false,
                    () -> {
                        ctxOpen = false;
                        autoArrangeCategory();
                    }));
        }

        // Group editing (when right-clicking a group label bar)
        if (hasGroup && canEdit) {
            items.add(CtxItem.sep());
            QuestGroup grp = ctxGroup;
            items.add(new CtxItem("Edit group…", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(
                                new QuestGroupEditorScreen(this, selectedCategory, grp, grp.getX(), grp.getY()));
                    }));
            items.add(new CtxItem("Delete group", "§c", false, true,
                    () -> {
                        QuestGroupManager.remove(grp.getId());
                        QuestGroupManager.save(groupsConfigPath());
                        ctxOpen = false;
                        ctxGroup = null;
                        setFeedback("Group deleted");
                    }));
        }

        if (hasNode) {
            items.add(CtxItem.sep());
            QuestNode ctxLinkTarget = resolveLinkTarget(ctxNode);
            if (ctxLinkTarget != null) {
                final QuestNode jumpTarget = ctxLinkTarget;
                items.add(new CtxItem("🔗 Jump to linked quest", "§b", false, false,
                        () -> {
                            ctxOpen = false;
                            navigateToNode(jumpTarget);
                        }));
                items.add(CtxItem.sep());
            }
            if (canEdit) {
                items.add(new CtxItem("Edit Quest", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            minecraft.setScreen(new QuestCreatorScreen(this, ctxNode));
                        }));
                items.add(new CtxItem("Edit Tasks & Rewards", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            minecraft.setScreen(new TaskRewardEditorScreen(this, ctxNode));
                        }));
                items.add(new CtxItem("Edit Texts...", "§d", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            minecraft.setScreen(new LangEditorScreen(this, target));
                        }));
                items.add(new CtxItem("Design Pop-Up", "§6", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            minecraft.setScreen(new ToastDesignerScreen(this, target));
                        }));
                items.add(new CtxItem("Set Icon by Item", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                                ctxNode.setIconItem(stack.getItem());
                                ctxNode.setIconTexture("");
                                saveNodeIconToDisk(ctxNode);
                                saveNodeIconTextureToDisk(ctxNode);
                                setFeedback("Icon → " + stack.getHoverName().getString());
                                rebuild();
                            }));
                        }));
                items.add(new CtxItem("Set Icon by Texture", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                                ctxNode.setIconTexture(rl);
                                saveNodeIconTextureToDisk(ctxNode);
                                setFeedback("Icon texture → " + rl);
                                rebuild();
                            }));
                        }));
                items.add(new CtxItem("Clear Icon", "§8", false, false,
                        () -> {
                            ctxNode.setIconItem(null);
                            ctxNode.setIconTexture("");
                            saveNodeIconToDisk(ctxNode);
                            saveNodeIconTextureToDisk(ctxNode);
                            setFeedback("Icon cleared");
                            ctxOpen = false;
                            rebuild();
                        }));
                items.add(CtxItem.sep());
                // Hovering this row (see renderCtxMenu) opens the submenu automatically - no click
                // action needed, but the row still needs to exist to occupy its place in the list
                // and report its own bounds for that hover check.
                items.add(new CtxItem("Move to Category  ▸", "§7", false, false, () -> {}));
                items.add(CtxItem.sep());
                items.add(new CtxItem("Shift + drag to move", "§8", false, false,
                        () -> {
                            ctxOpen = false;
                            setFeedback("Shift-click and drag the node");
                        }));
            } // canEdit
            items.add(CtxItem.sep());
            items.add(new CtxItem("Dependency Lines", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        final String cat = selectedCategory;
                        minecraft.setScreen(new DepLineSettingsScreen(this, cat, ctxNode));
                    }));
            if (canEdit) {
                items.add(CtxItem.sep());
                items.add(new CtxItem("Copy Quest §8(Ctrl+C)", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            questCopy(ctxNode);
                        }));
                items.add(new CtxItem("Duplicate Quest §8(Ctrl+D)", "§b", false, false,
                        () -> {
                            ctxOpen = false;
                            duplicateQuest(ctxNode);
                        }));
                items.add(new CtxItem("Force Complete Quest", "§e", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                // Snapshot the pre-completion state so undo can restore it - devSetState
                                // only flips the state key (task progress is untouched by "complete"),
                                // so replaying the matching unlock/active command is an exact revert.
                                // A prior LOCKED state has no direct "re-lock" command, so undo falls
                                // back to a full reset there (closest available approximation).
                                QuestState preState = playerData != null ?
                                        playerData.getQuestState(target.getId(), QuestState.LOCKED) : QuestState.LOCKED;
                                pushUndo(() -> {
                                    Minecraft mc2 = Minecraft.getInstance();
                                    if (mc2.player == null) return;
                                    String cmd = switch (preState) {
                                        case UNLOCKED -> "chronicles unlock " + target.getId().getPath();
                                        case ACTIVE -> "chronicles active " + target.getId().getPath();
                                        default -> "chronicles reset " + target.getId().getPath();
                                    };
                                    mc2.player.connection.sendCommand(cmd);
                                    setFeedback("Undo: force-complete reverted");
                                });
                                // Send to server so state persists and cascade unlocks fire
                                mc.player.connection.sendCommand("chronicles complete " + target.getId().getPath());
                                setFeedback("Force-completed: " + target.getTitle().getString() + "  (Ctrl+Z to undo)");
                            }
                        }));
                items.add(new CtxItem("Reset Progress", "§7", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                // Full reset (state + task progress + claimed rewards), not just a
                                // state flip - see ChronicleEvents.devResetQuest() server-side.
                                mc.player.connection.sendCommand("chronicles reset " + target.getId().getPath());
                                setFeedback("Progress reset: " + target.getTitle().getString());
                            }
                        }));
                items.add(new CtxItem("Delete Quest", "§c", false, true,
                        () -> {
                            final QuestNode deleted = ctxNode;
                            // Read file content BEFORE deleting so we can restore it on undo
                            final String savedContent = QuestFileSaver.readRawSnbt(deleted);
                            final Path categoryFolder = QuestFileSaver.getQuestCategoryFolder(deleted);
                            pushUndo(() -> {
                                // Restore file + re-inject into registry
                                QuestFileSaver.restoreRawSnbt(deleted, savedContent);
                                QuestFileLoader.loadAdditiveFromDisk(categoryFolder);
                                rebuild();
                                setFeedback("Undo: quest restored");
                            });
                            QuestTreeRegistry.removeQuest(deleted.getId());
                            deleteQuestFiles(deleted);
                            if (selectedNode == deleted) selectedNode = null;
                            ctxOpen = false;
                            rebuild();
                            setFeedback("Quest deleted  (Ctrl+Z to undo)");
                        }));
            } // canEdit
        }
        return items;
    }

    private void openCtx(int x, int y, QuestNode node) {
        openCtx(x, y, node, null);
    }

    private void openCtx(int x, int y, QuestNode node, @Nullable QuestGroup group) {
        ctxOpen = true;
        ctxOpenTimeMs = System.currentTimeMillis();
        ctxMoveCatOpen = false;
        ctxX = x;
        ctxY = y;
        ctxNode = node;
        ctxGroup = group;
        List<CtxItem> items = buildCtxItems();
        int menuH = menuHeight(items);
        if (ctxY + menuH > height - 4) ctxY = height - menuH - 4;
        if (ctxX + CTX_W > width - 4) ctxX = width - CTX_W - 4;
    }

    private int menuHeight(List<CtxItem> items) {
        int h = 4;
        if (ctxNode != null) h += CTX_ROW; // title row
        for (CtxItem i : items) h += i.isSep ? CTX_SEP : CTX_ROW;
        return h;
    }

    // ── Background picture context menu ─────────────────────────────────────

    private static final int PIC_CTX_H = 4 + CTX_ROW * 5 + CTX_SEP;

    private void openPictureCtx(int x, int y, BackgroundPictureConfig.Picture pic) {
        picCtxOpen = true;
        picCtxOpenTimeMs = System.currentTimeMillis();
        picCtxResizeOpen = false;
        picCtxMoveCatOpen = false;
        picCtxTarget = pic;
        picCtxX = x;
        picCtxY = y;
        if (picCtxY + PIC_CTX_H > height - 4) picCtxY = height - PIC_CTX_H - 4;
        if (picCtxX + CTX_W > width - 4) picCtxX = width - CTX_W - 4;
    }

    private void closePictureCtx() {
        picCtxOpen = false;
        picCtxResizeOpen = false;
        picCtxMoveCatOpen = false;
        picCtxTarget = null;
    }

    private int drawPicCtxRow(GuiGraphics g, int x, int iy, String label, String color, boolean danger,
                              int mx, int my) {
        boolean hov = mx >= x + 1 && mx <= x + CTX_W - 1 && my >= iy && my <= iy + CTX_ROW;
        if (hov) g.fill(x + 1, iy, x + CTX_W - 1, iy + CTX_ROW, C_CTX_HOVER);
        g.drawString(font, color + label, x + 8, iy + 4, danger ? C_CTX_DANGER : C_CTX_TEXT);
        return iy + CTX_ROW;
    }

    private void renderPictureCtxMenu(GuiGraphics g, int mx, int my) {
        if (picCtxTarget == null) {
            picCtxOpen = false;
            return;
        }
        int x = picCtxX, y = picCtxY;
        int menuH = PIC_CTX_H;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        int ctxAlpha = (int) Math.min(255, (System.currentTimeMillis() - picCtxOpenTimeMs) * 255 / OPEN_FADE_MS);
        int fadedBg = (ctxAlpha << 24) | (C_CTX_BG & 0x00FFFFFF);
        g.fill(x + 3, y + 3, x + CTX_W + 3, y + menuH + 3, (Math.min(0x55, ctxAlpha / 3)) << 24);
        g.fill(x, y, x + CTX_W, y + menuH, fadedBg);
        g.fill(x, y, x + CTX_W, y + 1, C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + CTX_W, y + menuH, C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, C_CTX_BORDER);
        g.fill(x + CTX_W - 1, y, x + CTX_W, y + menuH, C_CTX_BORDER);

        int iy = y + 2;
        iy = drawPicCtxRow(g, x, iy, "Move  §8(shift+drag)", "§7", false, mx, my);
        iy = drawPicCtxRow(g, x, iy, "Resize  ▸", "§7", false, mx, my);
        iy = drawPicCtxRow(g, x, iy, "Resize (scroll + drag)…", "§7", false, mx, my);
        iy = drawPicCtxRow(g, x, iy, "Move to category  ▸", "§7", false, mx, my);
        g.fill(x + 6, iy + 2, x + CTX_W - 6, iy + 3, C_CTX_SEP);
        iy += CTX_SEP;
        drawPicCtxRow(g, x, iy, "Delete picture", "§c", true, mx, my);

        if (picCtxResizeOpen) renderPicResizeSubmenu(g, x, y + 2 + CTX_ROW, mx, my);
        if (picCtxMoveCatOpen) renderPicMoveCatSubmenu(g, x, y + 2 + CTX_ROW * 3, mx, my);

        g.pose().popPose();
    }

    private void renderPicResizeSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        int subH = PIC_RESIZE_PRESETS.length * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, C_CTX_BORDER);
        int sy = subY + 2;
        for (int size : PIC_RESIZE_PRESETS) {
            boolean isCurrent = picCtxTarget != null && Math.round(picCtxTarget.w) == size;
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawPicCtxRow(g, subX, sy, mark + size + "px", "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    private void renderPicMoveCatSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        List<String> cats = buildCategoryList();
        cats.remove("ALL");
        cats.remove(selectedCategory);
        int subX = x + CTX_W + 2;
        int subH = Math.max(CTX_ROW, cats.size() * CTX_ROW) + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, C_CTX_BORDER);
        int sy = subY + 2;
        if (cats.isEmpty()) {
            g.drawString(font, "§8(no other chapters)", subX + 6, sy + 4, C_CTX_TEXT);
        }
        for (String cat : cats) {
            drawPicCtxRow(g, subX, sy, "§7" + friendly(cat), "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    /** @return true if the click landed somewhere in the picture menu/submenus (handled either way). */
    private boolean handlePictureCtxClick(int mx, int my) {
        if (picCtxTarget == null) return false;
        BackgroundPictureConfig.Picture pic = picCtxTarget;
        int x = picCtxX, y = picCtxY;

        // Submenus take priority while open
        if (picCtxResizeOpen) {
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW;
            for (int i = 0; i < PIC_RESIZE_PRESETS.length; i++) {
                int ry = subY + i * CTX_ROW;
                if (mx >= subX && mx <= subX + CTX_W && my >= ry && my <= ry + CTX_ROW) {
                    final float oldW = pic.w, oldH = pic.h;
                    final int size = PIC_RESIZE_PRESETS[i];
                    pushUndo(() -> {
                        pic.w = oldW;
                        pic.h = oldH;
                        BackgroundPictureConfig.save();
                        setFeedback("Undo: picture resized");
                    });
                    pic.w = size;
                    pic.h = size;
                    BackgroundPictureConfig.save();
                    setFeedback("Picture resized  (Ctrl+Z to undo)");
                    closePictureCtx();
                    return true;
                }
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + PIC_CTX_H) {
                closePictureCtx();
                return true;
            }
        }
        if (picCtxMoveCatOpen) {
            List<String> cats = buildCategoryList();
            cats.remove("ALL");
            cats.remove(selectedCategory);
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 3;
            int sy = subY + 2;
            for (String cat : cats) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final String oldCat = selectedCategory;
                    final String newCat = cat;
                    pushUndo(() -> {
                        BackgroundPictureConfig.remove(newCat, pic);
                        BackgroundPictureConfig.add(oldCat, pic);
                        setFeedback("Undo: picture moved back to " + friendly(oldCat));
                    });
                    BackgroundPictureConfig.remove(oldCat, pic);
                    BackgroundPictureConfig.add(newCat, pic);
                    setFeedback("Picture moved to " + friendly(newCat) + "  (Ctrl+Z to undo)");
                    closePictureCtx();
                    return true;
                }
                sy += CTX_ROW;
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + PIC_CTX_H) {
                closePictureCtx();
                return true;
            }
        }

        int rowY0 = y + 2;
        int rowY1 = rowY0 + CTX_ROW;
        int rowY2 = rowY1 + CTX_ROW;
        int rowY3 = rowY2 + CTX_ROW;
        int rowY4 = rowY3 + CTX_ROW + CTX_SEP;

        if (mx < x || mx > x + CTX_W) {
            closePictureCtx();
            return true;
        }
        if (my >= rowY0 && my < rowY0 + CTX_ROW) {
            // "Move" is a reminder, same convention as the quest node menu's own
            // "Shift+drag to move" row - the actual move gesture (and its own undo entry) lives
            // in the shift+drag handler in mouseClicked/mouseDragged/mouseReleased.
            setFeedback("Shift-click and drag the picture");
            closePictureCtx();
            return true;
        }
        if (my >= rowY1 && my < rowY1 + CTX_ROW) {
            picCtxResizeOpen = !picCtxResizeOpen;
            picCtxMoveCatOpen = false;
            return true;
        }
        if (my >= rowY2 && my < rowY2 + CTX_ROW) {
            // Enters interactive edit mode - pushes ONE undo entry covering the whole session
            // (position + size), restorable no matter how many scroll/drag adjustments happen
            // before the mode is exited.
            final BackgroundPictureConfig.Picture editedPic = pic;
            final float ux = pic.x, uy = pic.y, uw = pic.w, uh = pic.h;
            pushUndo(() -> {
                editedPic.x = ux;
                editedPic.y = uy;
                editedPic.w = uw;
                editedPic.h = uh;
                BackgroundPictureConfig.save();
                setFeedback("Undo: picture edit reverted");
            });
            pictureEditMode = pic;
            setFeedback("§eScroll to resize, drag to move - right-click or Esc to finish");
            closePictureCtx();
            return true;
        }
        if (my >= rowY3 && my < rowY3 + CTX_ROW) {
            picCtxMoveCatOpen = !picCtxMoveCatOpen;
            picCtxResizeOpen = false;
            return true;
        }
        if (my >= rowY4 && my < rowY4 + CTX_ROW) {
            final BackgroundPictureConfig.Picture deleted = pic;
            final String cat = selectedCategory;
            pushUndo(() -> {
                BackgroundPictureConfig.add(cat, deleted);
                setFeedback("Undo: picture restored");
            });
            BackgroundPictureConfig.remove(cat, deleted);
            setFeedback("Picture deleted  (Ctrl+Z to undo)");
            closePictureCtx();
            return true;
        }
        closePictureCtx();
        return true;
    }

    private int ctxMoveCatY(List<CtxItem> items) {
        int y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW; // skip title row
        for (CtxItem item : items) {
            if (!item.isSep && item.label.contains("Move to Category")) return y;
            y += item.isSep ? CTX_SEP : CTX_ROW;
        }
        return y;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        FrameProfiler.begin("TOTAL render()");
        if (feedbackTimer > 0) feedbackTimer--;
        pendingTooltip = null;

        // 1. Flush accumulated viewport panning inputs
        if (pendingPanDX != 0 || pendingPanDY != 0) {
            panCanvas(pendingPanDX, pendingPanDY);
            pendingPanDX = 0;
            pendingPanDY = 0;
        }

        // 2. Handle interactive transformations (Optimized: No line-cache rebuilding here!)
        handleLiveDragging(mx, my);

        int cl = sidebarW();
        int cr = width;
        int sz = scaledNodeSize();
        long animTick = System.currentTimeMillis();

        // 3. Draw core frame panels (Title bar, pills, backgrounds, toolbar)
        FrameProfiler.begin("header");
        renderHeaderAndBaseLayout(g, mx, my, cl, cr);
        FrameProfiler.end("header");

        PhantasiaCompat.tickPreview(phantasiaPreview);

        // 4. Draw chapters list sidebar
        renderSidebarPanel(g, mx, my);

        // 5. Draw canvas layer nodes (Spline connectors, groups, sparks, hover rules)
        renderCanvasLayers(g, mx, my, cl, cr, animTick);

        // 6. Native Screen vanilla widgets layer (Buttons, Edit Boxes)
        FrameProfiler.setCounter("screenWidgets", this.renderables.size());
        FrameProfiler.begin("widgets (super.render)");
        if (!renderingAsBackdrop) super.render(g, mx, my, partial);
        FrameProfiler.end("widgets (super.render)");

        // 6b. Dependency lines draw UNDER nodes (FTBQ-style) - each line is now trimmed to stop
        // at the gap between the two icons (see buildLineGeometry()'s trim step) instead of
        // running center-to-center, so the icons cleanly cap the connector ends rather than the
        // line crossing over their faces.
        renderDepLines(g, mx, my, cl, cr, animTick);

        // 7. Draw individual Node geometries, status rings, text tags
        renderNodesAndDetails(g, mx, my, cl, cr, sz);

        // 8. Draw global overlay modifiers, modals, dev diagnostics, and panels
        FrameProfiler.begin("overlays");
        renderScreenOverlays(g, mx, my, cl, cr, sz);
        FrameProfiler.end("overlays");

        // 9. Deferred utility tooltips (grid pill, sidebar rows, ...) — drawn dead last so
        // nothing painted in steps 3-8 above can overhang/overpaint them. See pendingTooltip.
        if (pendingTooltip != null) {
            pendingTooltip.run();
            pendingTooltip = null;
        }

        FrameProfiler.end("TOTAL render()");
        FrameProfiler.endFrame();
        if (FrameProfiler.isEnabled()) renderProfilerPanel(g);
    }

    private void handleLiveDragging(int mx, int my) {
        if (draggedNode == null) return;

        int cl2 = sidebarW();
        int logX = (int) ((mx - dragGrabX - cl2 - viewOffX) / posZoom());
        int logY = (int) ((my - dragGrabY - HEADER_H - viewOffY) / posZoom());
        int snap2 = hasShiftDown() ? 1 : gridSnap;

        logX = Math.round((float) logX / snap2) * snap2;
        logY = Math.round((float) logY / snap2) * snap2;

        int nx = (int) (logX * posZoom()) + cl2 + viewOffX;
        int ny = (int) (logY * posZoom()) + HEADER_H + viewOffY;

        NodeHitbox b = nodeButtons.get(draggedNode.getId());
        if (b != null) {
            b.setX(nx);
            b.setY(ny);
        }

        nodeScreenPos.put(draggedNode.getId(), new int[] { nx, ny });
        draggedNode.setCustomPosition(logX, logY);

        // PERFORMANCE FIX: Removed buildLineCache() from here.
        // Line calculation is deferred until mouseReleased() executes.
    }

    private void renderHeaderAndBaseLayout(GuiGraphics g, int mx, int my, int cl, int cr) {
        renderBackground(g);
        g.fill(0, 0, sidebarW(), height, C_PANEL_DARK);
        g.fill(cl, 0, cr, height, C_BG);
        g.fill(cr, 0, width, height, C_PANEL_DARK);
        g.fill(cr, 0, cr + 1, height, C_BORDER);

        // Title bar text and decoration
        g.fill(0, 0, width, TOOLBAR_Y, C_HEADER);
        g.fill(0, TOOLBAR_Y - 1, width, TOOLBAR_Y, C_BORDER);
        String titlePrefix = testMode ? "§c⏵ PLAYER  §8⟫  §7" : "§8Chronicles  §8⟫  §7";
        // Breadcrumb through the parent chain for a true sub-chapter (CategoryConfig.
        // parentCategory), so "AE2" nested under "Tips & Tricks" reads as "Tips & Tricks › AE2"
        // instead of just "AE2" with no indication it's nested at all.
        g.drawString(font, titlePrefix + categoryBreadcrumb(selectedCategory), cl + 8, 7, C_TEXT);
        if (testMode) g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFCC2222);
        if (pictureEditMode != null) {
            g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFFFCC33);
            String hint = "§e🖼 Editing picture — scroll to resize (shift = fine), drag to move, right-click/Esc to finish";
            g.drawCenteredString(font, hint, (cl + cr) / 2, 7, 0xFFFFEEAA);
        }

        renderQuestbookTitle(g, mx, my);

        // Zoom information pill
        String zoomStr = Math.round(zoom * 100) + "%";
        int zw = font.width(zoomStr);
        int zx = cr - zw - 10, zy = 3;
        g.fill(zx - 3, zy, zx + zw + 3, zy + 13, 0x22FFFFFF);
        g.drawString(font, "§7" + zoomStr, zx, zy + 3, C_TEXT_DIM);

        // Snap grid configuration pill
        String gridLabel = (gridSnap == 1) ? "§8Grid: §afree" : "§8Grid: §7" + gridSnap;
        int gw = font.width(net.minecraft.util.StringUtil.stripColor(gridLabel));
        int gpx = zx - gw - 18, gpy = 3;
        boolean gridHov = mx >= gpx - 3 && mx < gpx + gw + 5 && my >= gpy && my < gpy + 13;
        g.fill(gpx - 3, gpy, gpx + gw + 5, gpy + 13, gridHov ? 0x44FFFFFF : 0x22FFFFFF);
        g.drawString(font, gridLabel, gpx, gpy + 3, C_TEXT_DIM, false);
        if (gridHov) {
            // Queued, not drawn immediately: this runs from step 3 (renderHeaderAndBaseLayout),
            // and steps 5-8 (canvas/dep-lines/nodes/overlays) draw AFTER this and would just
            // overpaint it — elevated z + flush only beats depth-tested draws (item icons at
            // z=100), not later ordinary fills/strings. See pendingTooltip.
            pendingTooltip = () -> g.renderTooltip(font,
                    Component.literal("§7Click to cycle canvas snap grid size"), mx, my);
        }

        // Subgraph mode pill (dev only) - always visible (not just while active) so the feature
        // is actually discoverable by hovering, instead of only being explained by a status
        // badge that only appears once you've already found the G keybind or context menu entry.
        if (isDevMode) {
            String sgLabel = subgraphMode ? "§b⊛ Subgraph: " + subgraphNodes.size() : "§8⊛ Subgraph";
            int sgw = font.width(net.minecraft.util.StringUtil.stripColor(sgLabel));
            int sgx = gpx - sgw - 18, sgy = 3;
            boolean sgHov = mx >= sgx - 3 && mx < sgx + sgw + 5 && my >= sgy && my < sgy + 13;
            g.fill(sgx - 3, sgy, sgx + sgw + 5, sgy + 13,
                    subgraphMode ? 0x4444CCFF : (sgHov ? 0x44FFFFFF : 0x22FFFFFF));
            g.drawString(font, sgLabel, sgx, sgy + 3, C_TEXT_DIM, false);
            if (sgHov) {
                pendingTooltip = () -> g.renderComponentTooltip(font, List.of(
                        Component.literal("§b⊛ Subgraph mode"),
                        Component.literal("§7Dims every quest that isn't an ancestor or"),
                        Component.literal("§7descendant of the currently selected one,"),
                        Component.literal("§7isolating just its dependency chain."),
                        Component.literal("§8Click a quest to select it, then click this"),
                        Component.literal("§8pill (or press G) to toggle it on/off.")), mx, my);
            }
        }

        // Toolbar field region
        g.enableScissor(0, TOOLBAR_Y, width, HEADER_H);
        renderToolbar(g, mx, my, cl, cr);
        g.disableScissor();
    }

    private static final int SIDEBAR_COLLAPSE_TOGGLE_H = 12;

    private boolean sidebarCollapseToggleHovered(int mx, int my) {
        return mx >= 0 && mx < sidebarW() - 1 && my >= HEADER_H + 1 && my < HEADER_H + 1 + SIDEBAR_COLLAPSE_TOGGLE_H;
    }

    /** Book icon + pack-configured name pinned above the category list - click to rename/re-icon it. */
    private boolean questbookTitleHovered(int mx, int my) {
        return !sidebarCollapsed && mx >= 0 && mx < sidebarW() - 1 && my >= 0 && my < TOOLBAR_Y;
    }

    private void renderQuestbookTitle(GuiGraphics g, int mx, int my) {
        if (sidebarCollapsed) return;
        boolean hov = questbookTitleHovered(mx, my);
        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        if (hov) g.fill(0, 0, sidebarW() - 1, TOOLBAR_Y - 1, 0x14FFFFFF);

        net.minecraft.world.item.Item iconItem = s.getQuestbookIconItem();
        int iconY = (TOOLBAR_Y - 1 - 16) / 2;
        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), 3, iconY);

        String name = s.getQuestbookName();
        int maxW = sidebarW() - 22;
        if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - 4) + "…";
        g.drawString(font, (hov ? "§f" : "§7") + name, 21, 7, hov ? C_TEXT : C_TEXT_DIM, false);
    }

    private void renderSidebarPanel(GuiGraphics g, int mx, int my) {
        g.fill(0, HEADER_H, sidebarW() - 1, HEADER_H + 1, C_BORDER);

        // Collapse/expand toggle - always visible, whether collapsed or not, since it's the only
        // way back out of the collapsed state.
        boolean toggleHov = sidebarCollapseToggleHovered(mx, my);
        int toggleY = HEADER_H + 1;
        g.fill(0, toggleY, sidebarW() - 1, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H, toggleHov ? 0xFF1C1C24 : C_PANEL_DARK);
        g.fill(0, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H - 1, sidebarW() - 1, toggleY + SIDEBAR_COLLAPSE_TOGGLE_H,
                C_BORDER);
        g.drawCenteredString(font, sidebarCollapsed ? "§7▶" : "§7◀", sidebarW() / 2, toggleY + 2,
                toggleHov ? C_TEXT : C_TEXT_DIM);

        if (sidebarCollapsed) {
            if (toggleHov) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 250f);
                g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session
                String tip = "§7Show chapters";
                int ttW = font.width(tip) + 10;
                int ttX = sidebarW() + 3, ttY = toggleY;
                g.fill(ttX, ttY, ttX + ttW, ttY + 14, 0xFF1A1A24);
                ChroniclesUIKit.drawBorder(g, ttX, ttY, ttW, 14, C_BORDER_LIT);
                g.drawString(font, tip, ttX + 5, ttY + 3, C_TEXT_DIM, false);
                g.pose().popPose();
            }
            g.fill(sidebarW() - 1, 0, sidebarW(), height, C_BORDER);
            return;
        }

        int scrollTop = toggleY + SIDEBAR_COLLAPSE_TOGGLE_H;
        int scrollBottom = scrollTop + sidebarScrollAreaHeight();
        g.enableScissor(0, scrollTop, sidebarW() - 1, scrollBottom);

        FrameProfiler.begin("sidebar");
        List<SidebarRow> sidebarRows = buildSidebarRows();
        for (SidebarRow row : sidebarRows) {
            if (row.y() + row.height() < scrollTop || row.y() > scrollBottom) continue; // culled
            if (row.isFolder()) renderSidebarFolderRow(g, row, mx, my);
            else renderSidebarCatRow(g, row, mx, my);
        }
        // Drop-target highlight while dragging a folder/category in the sidebar (see
        // handleSidebarDrop) - the actual reorder/move only happens on release, this is just
        // showing where it would land.
        if (sidebarDragMoved && sidebarDragRow != null) {
            SidebarRow dropTarget = sidebarRowAt(sidebarRows, mx, my);
            if (dropTarget != null) {
                g.fill(1, dropTarget.y(), sidebarW() - 2, dropTarget.y() + dropTarget.height(), 0x4400DDFF);
                ChroniclesUIKit.drawBorder(g, 1, dropTarget.y(), sidebarW() - 3, dropTarget.height(), 0xFF00DDFF);
            }
        }
        FrameProfiler.end("sidebar");

        if (sidebarRows.isEmpty()) {
            g.drawCenteredString(font, "§8No", sidebarW() / 2, scrollTop + 10, C_TEXT_FAINT);
            g.drawCenteredString(font, "§8chapters", sidebarW() / 2, scrollTop + 20, C_TEXT_FAINT);
        }

        g.disableScissor();
        g.fill(sidebarW() - 1, 0, sidebarW(), height, C_BORDER);

        // Scrollbar hint - a thin indicator on the right edge of the sidebar, only when the list
        // actually overflows, so it's obvious there's more to scroll to instead of the list just
        // silently cutting off (which is what made the "+ category" button unreachable before).
        int contentH = sidebarContentHeight();
        int areaH = sidebarScrollAreaHeight();
        if (contentH > areaH && areaH > 0) {
            int trackH = areaH;
            int thumbH = Math.max(10, trackH * areaH / contentH);
            int maxScroll = contentH - areaH;
            int thumbY = scrollTop + (maxScroll > 0 ? (trackH - thumbH) * sidebarScrollY / maxScroll : 0);
            g.fill(sidebarW() - 3, scrollTop, sidebarW() - 1, scrollBottom, 0x22FFFFFF);
            g.fill(sidebarW() - 3, thumbY, sidebarW() - 1, thumbY + thumbH, 0xFF666677);
        }

        // Queued, not drawn immediately: "last, on top of everything" used to be true because
        // this was the last draw call *within this method* - but render() draws canvas/dep-lines/
        // nodes/overlays AFTER renderSidebarPanel(), so those steps were repainting straight over
        // this tooltip whenever it overhung onto the canvas (sidebarW()+3 puts its left edge
        // right at the canvas boundary). Queuing it defers the draw to the true end of render().
        // See pendingTooltip.
        SidebarRow hovRow = my >= scrollTop && my < scrollBottom ? sidebarRowAt(sidebarRows, mx, my) : null;
        if (hovRow != null) pendingTooltip = () -> renderSidebarTooltip(g, hovRow, mx, my);
    }

    /**
     * Category accent color: the pack-configured CategoryConfig color if set, else the old
     * hash-derived fallback so existing categories that haven't been given a color yet still
     * read as visually distinct from one another instead of all defaulting to one color.
     */
    private int categoryAccent(String cat) {
        int configured = CategoryConfig.get(cat).getColor();
        if (configured != 0) return 0xFF000000 | (configured & 0x00FFFFFF);
        return CAT_ACCENTS[Math.abs(cat.hashCode()) % CAT_ACCENTS.length];
    }

    private SidebarRow sidebarRowAt(List<SidebarRow> rows, int mx, int my) {
        if (mx < 0 || mx >= sidebarW() - 1 || my < HEADER_H) return null;
        for (SidebarRow row : rows) {
            if (my >= row.y() && my < row.y() + row.height()) return row;
        }
        return null;
    }

    /**
     * Resolves a sidebar drag-and-drop: dropping a folder header onto another row reorders
     * chapter folders (previously only possible by hand-editing chapter_folders.snbt or via a
     * right-click "top"/"bottom" style menu, which testers asked to be drag-and-drop instead).
     * Dropping a category tile onto a folder (or a category already inside one) moves that
     * category into the folder; dropping it on empty space or an ungrouped category removes it
     * from whichever folder it was in.
     */
    private void handleSidebarDrop(SidebarRow source, int mx, int my) {
        List<SidebarRow> rows = buildSidebarRows();
        SidebarRow target = sidebarRowAt(rows, mx, my);

        if (source.isFolder()) {
            List<net.phoenixvine.chronicles.registry.ChapterFolderRegistry.ChapterFolder> allFolders = net.phoenixvine.chronicles.registry.ChapterFolderRegistry
                    .getFolders();
            int targetIndex = allFolders.size(); // default: drop at the end
            if (target != null) {
                String targetFolderId = target.isFolder() ? target.id() :
                        (net.phoenixvine.chronicles.registry.ChapterFolderRegistry.folderFor(target.id()) != null ?
                                net.phoenixvine.chronicles.registry.ChapterFolderRegistry.folderFor(target.id()).id() :
                                null);
                if (targetFolderId != null) {
                    for (int i = 0; i < allFolders.size(); i++) {
                        if (allFolders.get(i).id().equals(targetFolderId)) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
            }
            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.reorderFolder(source.id(), targetIndex);
            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.save();
            setFeedback("Folder reordered");
        } else {
            String cat = source.id();
            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.ChapterFolder currentFolder = net.phoenixvine.chronicles.registry.ChapterFolderRegistry
                    .folderFor(cat);

            String destFolderId = null;
            if (target != null) {
                if (target.isFolder()) {
                    destFolderId = target.id();
                } else {
                    net.phoenixvine.chronicles.registry.ChapterFolderRegistry.ChapterFolder tf = net.phoenixvine.chronicles.registry.ChapterFolderRegistry
                            .folderFor(target.id());
                    if (tf != null) destFolderId = tf.id();
                }
            }

            if (currentFolder != null) {
                net.phoenixvine.chronicles.registry.ChapterFolderRegistry.removeCategoryFromFolder(
                        currentFolder.id(), cat);
            }
            if (destFolderId != null) {
                net.phoenixvine.chronicles.registry.ChapterFolderRegistry.addCategoryToFolder(destFolderId, cat);
                setFeedback("Moved " + friendly(cat) + " into " + destFolderId);
            } else if (currentFolder != null) {
                setFeedback("Removed " + friendly(cat) + " from " + currentFolder.label());
            } else {
                // Both source and target (if any) are standalone chapters - previously this drop
                // did nothing at all, since there was no persisted order for ungrouped chapters
                // to reorder within. See ChapterFolderRegistry.reorderStandaloneCategory().
                List<String> standalone = new ArrayList<>();
                for (String c : buildCategoryList()) {
                    if (net.phoenixvine.chronicles.registry.ChapterFolderRegistry.folderFor(c) == null) {
                        standalone.add(c);
                    }
                }
                List<String> ordered = applyStandaloneOrder(standalone);
                String targetCat = (target != null && !target.isFolder() &&
                        net.phoenixvine.chronicles.registry.ChapterFolderRegistry.folderFor(target.id()) == null) ?
                                target.id() : null;
                if (!cat.equals(targetCat)) {
                    net.phoenixvine.chronicles.registry.ChapterFolderRegistry.reorderStandaloneCategory(cat, targetCat,
                            ordered);
                    setFeedback(targetCat != null ? "Reordered " + friendly(cat) : "Moved " + friendly(cat) +
                            " to end");
                }
            }
            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.save();
        }
        rebuild();
    }

    private void renderSidebarTooltip(GuiGraphics g, SidebarRow row, int mx, int my) {
        String line1 = row.isFolder() ? row.label() : row.label();
        String line2 = null;
        if (!row.isFolder()) {
            int[] p = progressCache.computeIfAbsent(row.id(), this::computeCategoryProgress);
            if (p[1] > 0) line2 = "§8" + p[0] + "/" + p[1] + " complete";
        }
        int ttW = Math.max(font.width(line1), line2 != null ? font.width(line2) : 0) + 10;
        int ttH = line2 != null ? 24 : 14;
        int ttX = sidebarW() + 3;
        int ttY = Math.min(height - ttH - 2, my - ttH / 2);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 250f);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session
        g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xFF1A1A24);
        g.fill(ttX, ttY, ttX + ttW, ttY + 1, C_BORDER_LIT);
        g.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, C_BORDER);
        g.fill(ttX, ttY, ttX + 1, ttY + ttH, C_BORDER);
        g.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, C_BORDER);
        g.drawString(font, "§f" + line1, ttX + 5, ttY + 4, C_TEXT, false);
        if (line2 != null) g.drawString(font, line2, ttX + 5, ttY + 14, C_TEXT_DIM, false);
        g.pose().popPose();
    }

    private void renderCanvasLayers(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("background");
        CanvasBackgroundRenderer.drawBackground(g, cl, HEADER_H, cr, height, selectedCategory, zoom, viewOffX,
                viewOffY);
        // Freely-placed decorative pictures (canvas right-click → "Add picture…") - separate from
        // the chapter theme's own single CUSTOM background texture, drawn between the background
        // pattern and the quest nodes so they read as backdrop, not foreground clutter.
        BackgroundPictureRenderer.render(g, cl, HEADER_H, cr, height, selectedCategory, zoom, viewOffX, viewOffY);
        if (pictureEditMode != null) {
            int[] rect = BackgroundPictureRenderer.screenRect(pictureEditMode, cl, HEADER_H, posZoom(), viewOffX,
                    viewOffY);
            ChroniclesUIKit.drawBorder(g, rect[0] - 1, rect[1] - 1, rect[2] - rect[0] + 2, rect[3] - rect[1] + 2,
                    0xFFFFCC33);
        }
        FrameProfiler.end("background");

        FrameProfiler.begin("groups");
        for (QuestGroup grp : QuestGroupManager.forCategory(selectedCategory)) {
            renderQuestGroup(g, grp, cl, cr);
        }
        FrameProfiler.end("groups");

        g.disableScissor();
    }

    /**
     * Runs BEFORE renderNodesAndDetails() so node icons draw on top and cap the connector ends.
     * This used to run after nodes instead, because a short connector between two adjacent
     * nodes (MIN_NODE_PX keeps nodes from shrinking below a floor size, so they can end up right
     * next to each other when zoomed out) had nowhere visible to show when drawn center-to-center
     * underneath two same-sized squares. buildLineGeometry() now trims each end of the curve to
     * stop at the gap between the icons instead, so there's an actual visible segment again and
     * this can go back to drawing under the nodes like FTBQ does.
     */
    private void renderDepLines(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        // Hover detection stays here - it's a mouse/button concern, and lastHoveredNodeId is
        // also read elsewhere (e.g. the pin keybind), not just by line rendering.
        ResourceLocation hoveredNodeId = null;
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                hoveredNodeId = e.getKey();
                break;
            }
        }
        lastHoveredNodeId = hoveredNodeId;

        FrameProfiler.setCounter("nodes", nodeButtons.size());
        FrameProfiler.setCounter("zoom%", Math.round(zoom * 100));
        depLineRenderer.render(g, animTick, hoveredNodeId, this::getState);

        // Interactive connection draft line preview
        if (linkDragSource != null) {
            int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
            if (srcPos != null) {
                int sz2 = scaledNodeSize();
                int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
                depLineRenderer.renderLinkDragPreview(g, sx, sy, linkDragX, linkDragY, animTick, zoom);
                g.drawString(font, "§dAlt+release on target to link", sx - 50, sy - 14, 0xFFAA66FF, false);
            }
        }

        g.disableScissor();
    }

    private void renderNodesAndDetails(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("node visuals");
        dbgFull3DIconCount = 0;
        dbgCustomIconCount = 0;
        dbgPickedTextureIconCount = 0;
        dbgGlyphIconCount = 0;
        dbgShapeCounts.clear();
        int visibleNodeCount = 0;
        // Two passes: queue every node's shape first, flush ONCE as a single batched draw call,
        // THEN render overlays/icons/badges - which must paint on top of the now-flushed shapes.
        // (See NodeShapeRenderer.queueFillRect/flushFillQueue and renderNodeShape's doc comment.)
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            visibleNodeCount++;
            int[] pos = entry.getValue();
            renderNodeShape(g, node, pos[0], pos[1], sz, btn.isMouseOver(mx, my), node == selectedNode);
        }
        int shapeQuadCount = NodeShapeRenderer.flushFillQueue(g);
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            renderNodeDetails(g, node, pos[0], pos[1], sz, btn.isMouseOver(mx, my), node == selectedNode);
        }
        // renderNodeDetails() queues each node's state badge (renderStateBadge) instead of
        // drawing it immediately - flush that second batch now that every node's icon (drawn
        // immediately, in between) is already on screen underneath it.
        int badgeQuadCount = NodeShapeRenderer.flushFillQueue(g);
        FrameProfiler.setCounter("shapeFillQuadsQueued", shapeQuadCount + badgeQuadCount);
        FrameProfiler.setCounter("visibleNodes", visibleNodeCount);
        FrameProfiler.setCounter("full3DIcons", dbgFull3DIconCount);
        FrameProfiler.setCounter("customIcons", dbgCustomIconCount);
        FrameProfiler.setCounter("pickedTexIcons", dbgPickedTextureIconCount);
        FrameProfiler.setCounter("glyphIcons", dbgGlyphIconCount);
        for (Map.Entry<String, Integer> e : dbgShapeCounts.entrySet()) {
            FrameProfiler.setCounter("shape:" + e.getKey(), e.getValue());
        }
        FrameProfiler.end("node visuals");

        // Same missing-flush bleed-through bug fixed elsewhere this session: node icons render
        // via g.renderItem() at z=100, queued into their own RenderType buffer. Everything below
        // this point (selection outlines, validation-warning borders, badges/labels) is plain
        // g.fill()/g.drawString() content that's meant to draw ON TOP of those icons, but without
        // forcing the icon batch to submit first, it doesn't reliably win the draw order against
        // it - this was the "quest icons always render above everything else in the canvas"
        // bleed, and it's why the one thing that DID already flush before drawing (the right-click
        // context menu, see renderCtxMenu) was the one exception that layered correctly.
        g.flush();

        FrameProfiler.begin("dev overlays");
        // Developer multi-selection bounding rules
        if (isDevMode && !multiSelection.isEmpty()) {
            long dashPhase = (System.currentTimeMillis() / 80) % 6;
            for (ResourceLocation id : multiSelection) {
                int[] pos = nodeScreenPos.get(id);
                if (pos == null) continue;
                int x1 = pos[0] - 2, y1 = pos[1] - 2, x2 = pos[0] + sz + 2, y2 = pos[1] + sz + 2;
                int selCol = 0xFF00DDFF;
                for (int px = x1; px < x2; px++) {
                    if ((px + dashPhase) % 6 < 3) {
                        g.fill(px, y1, px + 1, y1 + 1, selCol);
                        g.fill(px, y2, px + 1, y2 + 1, selCol);
                    }
                }
                for (int py = y1; py < y2; py++) {
                    if ((py + dashPhase) % 6 < 3) {
                        g.fill(x1, py, x1 + 1, py + 1, selCol);
                        g.fill(x2, py, x2 + 1, py + 1, selCol);
                    }
                }
            }
        }

        // Developer content configuration warning metrics
        if (isDevMode) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
            int alpha = (int) (pulse * 255) << 24;
            int errCol = alpha | 0x00FF2222;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                if (getValidationIssues(node).isEmpty()) continue;
                int[] pos = entry.getValue();
                int nsz = scaledNodeSize(node);
                int x1 = pos[0] - 3, y1 = pos[1] - 3, x2 = pos[0] + nsz + 3, y2 = pos[1] + nsz + 3;
                g.fill(x1, y1, x2, y1 + 2, errCol);
                g.fill(x1, y2 - 2, x2, y2, errCol);
                g.fill(x1, y1, x1 + 2, y2, errCol);
                g.fill(x2 - 2, y1, x2, y2, errCol);
            }
        }

        // Subgraph isolated display rule opacity layer - node icons render via g.renderItem() at
        // z=100 (the same depth-buffer bleed-through pattern found everywhere else this session),
        // so this dim quad needs its own elevated z + flush or it loses the depth test against
        // icons already drawn above and never visibly appears despite being issued later.
        if (subgraphMode && selectedNode != null && !subgraphNodes.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 150f);
            g.flush();
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                int[] pos = entry.getValue();
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                int nsz = node != null ? scaledNodeSize(node) : sz;
                if (subgraphNodes.contains(entry.getKey())) {
                    // Included nodes get a positive highlight ring too, not just an absence of
                    // dimming - in a densely-connected chapter the subgraph can cover most/all of
                    // what's on screen, where a pure "dim everything else" effect can have nothing
                    // left to dim and read as if the mode did nothing at all.
                    int x1 = pos[0] - 2, y1 = pos[1] - 2, x2 = pos[0] + nsz + 2, y2 = pos[1] + nsz + 2;
                    g.fill(x1, y1, x2, y1 + 1, 0xFF44CCFF);
                    g.fill(x1, y2 - 1, x2, y2, 0xFF44CCFF);
                    g.fill(x1, y1, x1 + 1, y2, 0xFF44CCFF);
                    g.fill(x2 - 1, y1, x2, y2, 0xFF44CCFF);
                } else {
                    g.fill(pos[0] - 1, pos[1] - 1, pos[0] + nsz + 1, pos[1] + nsz + 1, 0xCC000000);
                }
            }
            g.flush();
            g.pose().popPose();

            // Status badge - always visible while the mode is on, regardless of whether the
            // dim/highlight above had any visible effect for this particular graph shape.
            String badge = "§b⊛ Subgraph  §7" + subgraphNodes.size() + " node" +
                    (subgraphNodes.size() == 1 ? "" : "s") + " isolated  §8(G to exit)";
            int bw = font.width(net.minecraft.util.StringUtil.stripColor(badge)) + 10;
            int bx = cl + 6, by = HEADER_H + 4;
            g.fill(bx, by, bx + bw, by + 12, 0xCC101018);
            ChroniclesUIKit.drawBorder(g, bx, by, bw, 12, 0xFF44CCFF);
            g.drawString(font, badge, bx + 5, by + 2, C_TEXT, false);
        }

        FrameProfiler.end("dev overlays");

        FrameProfiler.begin("badges/labels");
        // Notifications, claim status badges, and dynamic alpha context names
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            QuestState st = getState(node);

            if (st == QuestState.UNLOCKED && sz >= 20) {
                int badgeX = pos[0] + sz - 2;
                int badgeY = pos[1] - 1;
                g.fill(badgeX, badgeY, badgeX + font.width("NEW") + 4, badgeY + 8, 0xFF1144BB);
                g.drawString(font, "NEW", badgeX + 2, badgeY + 1, 0xFFAADDFF, false);
            }
            if (st == QuestState.COMPLETED && sz >= 12 && !node.getRewards().isEmpty()) {
                PlayerQuestData pd = testMode ? testModeData : playerData;
                if (pd != null && !pd.hasClaimedRewards(node.getId())) {
                    float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 600.0);
                    int bAlpha = (int) (pulse * 255) << 24;
                    int badgeX = pos[0] - 4;
                    int badgeY = pos[1] - 4;
                    g.fill(badgeX, badgeY, badgeX + 9, badgeY + 9, bAlpha | 0x00BB6600);
                    g.drawString(font, "!", badgeX + 2, badgeY + 1, bAlpha | 0x00FFD700, false);
                }
            }

            if (zoom >= 0.55f) {
                int baseAlpha = zoom >= 0.75f ? 0xFF : (int) ((zoom - 0.55f) / 0.20f * 0xFF);
                if (!searchQuery.isEmpty() && !matchesSearch(node)) baseAlpha = baseAlpha * 30 / 100;
                int lc = st == QuestState.COMPLETED ? C_TEXT_DONE :
                        st == QuestState.ACTIVE ? C_TEXT_ACT : st == QuestState.LOCKED ? C_TEXT_FAINT : C_TEXT_DIM;
                if (baseAlpha < 0xFF) lc = (lc & 0x00FFFFFF) | (baseAlpha << 24);
                g.drawCenteredString(font, shortLabel(node), pos[0] + sz / 2, pos[1] + sz + 4, lc);
            }
        }
        FrameProfiler.end("badges/labels");

        g.disableScissor();
    }

    private void renderScreenOverlays(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        if (feedbackTimer > 0 && !feedbackMsg.isEmpty()) {
            g.fill(cl, height - 13, cr, height, C_HEADER);
            g.fill(cl, height - 13, cl + 1, height, C_SEL_ACCENT);
            g.drawString(font, "§7" + feedbackMsg, cl + 6, height - 10, C_TEXT_DIM);
        }

        if (!sidebarCollapsed) {
            renderSidebarNewCategoryButton(g, mx, my);
            renderSidebarGear(g, mx, my);
        }

        if (!renderingAsBackdrop) renderTutorialOverlay(g, mx, my);

        // Context Tooltip detection frame handler
        if (!renderingAsBackdrop && draggedNode == null && !ctxOpen) {
            ResourceLocation nowHoverId = null;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                NodeHitbox btn = nodeButtons.get(node.getId());
                if (btn == null || !btn.visible || !btn.isMouseOver(mx, my)) continue;
                nowHoverId = node.getId();
                break;
            }
            if (!Objects.equals(nowHoverId, tooltipHoverNodeId)) {
                tooltipHoverNodeId = nowHoverId;
                tooltipHoverStartMs = System.currentTimeMillis();
            }
            if (nowHoverId != null && System.currentTimeMillis() - tooltipHoverStartMs >= TOOLTIP_DELAY_MS) {
                QuestNode tipNode = QuestTreeRegistry.getQuest(nowHoverId);
                if (tipNode != null) renderNodeTooltip(g, tipNode, mx, my);
            }
        }

        if (!renderingAsBackdrop && ctxOpen && isDevMode) renderCtxMenu(g, mx, my);
        if (!renderingAsBackdrop && picCtxOpen && isDevMode) renderPictureCtxMenu(g, mx, my);

        // Lineage unlock requirement paths overlay indicators
        if (!unlockPathHighlight.isEmpty()) {
            long pulse = System.currentTimeMillis();
            float blink = (float) (Math.sin(pulse / 400.0) * 0.3 + 0.7);
            int ringAlpha = (int) (blink * 0xAA) & 0xFF;
            for (ResourceLocation uid : unlockPathHighlight) {
                int[] upos = nodeScreenPos.get(uid);
                if (upos == null) continue;
                int ux = upos[0], uy = upos[1];
                g.fill(ux - 3, uy - 3, ux + sz + 3, uy - 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy + sz + 2, ux + sz + 3, uy + sz + 3, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy - 2, ux - 2, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux + sz + 2, uy - 2, ux + sz + 3, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
            }
            g.drawString(font, "§bUnlock path — §8Esc to clear", cl + 6, height - 10, 0xFF4488FF, false);
        }

        if (depLineRenderer.isContextMenuOpen()) depLineRenderer.renderContextMenu(g, font, mx, my, width, height);
        if (validationOpen && isDevMode) renderValidationPanel(g, cl, cr);
        if (statsOpen && isDevMode) renderStatsPanel(g, cl, cr);
        if (minimapOpen) renderMinimap(g, mx, my, cl, cr);

        if (isDevMode && multiSelection.size() >= 2) {
            renderBulkOpsPanel(g, mx, my, cl, cr);
        }

        // Initial transition sequence fade-out mask
        if (openTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            if (elapsed < OPEN_FADE_MS) {
                float t = 1f - (float) elapsed / OPEN_FADE_MS;
                int fadeAlpha = (int) (t * t * 0xFF) & 0xFF;
                if (fadeAlpha > 0) g.fill(0, 0, width, height, (fadeAlpha << 24) | 0x000000);
            }
        }
    }

    /**
     * Ctrl+P toggles this - a named-section timing breakdown of render(), so a bottleneck can
     * be found directly instead of needing an external profiler attached to the whole game.
     */
    private void renderProfilerPanel(GuiGraphics g) {
        var sections = FrameProfiler.sortedSections();
        int panelW = 260;
        int rowH = 11;
        int panelH = 20 + 10 + sections.size() * rowH + 6;
        int px = width - panelW - 4;
        int py = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);
        g.fill(px, py, px + panelW, py + panelH, 0xEE0D0D12);
        g.fill(px, py, px + panelW, py + 1, 0xFF00AA55);
        g.drawString(font, "§aProfiler §8(Ctrl+P close, Ctrl+Shift+P log now)", px + 5, py + 4, 0xFFDDDDDD, false);

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        g.drawString(font, "§8Heap: §7" + usedMb + "MB §8/ §7" + maxMb + "MB", px + 5, py + 15, 0xFFAAAAAA, false);

        double localMax = sections.isEmpty() ? 1.0 : sections.get(0).getValue();
        int y = py + 28;
        for (var entry : sections) {
            double ms = entry.getValue();
            double worst = FrameProfiler.maxMsFor(entry.getKey());
            // Green → yellow → red as a section's cost approaches the most expensive one this frame
            float frac = localMax > 0 ? (float) (ms / localMax) : 0;
            int barColor = frac > 0.66f ? 0xFFFF5555 : frac > 0.33f ? 0xFFFFAA33 : 0xFF55CC77;
            int barW = (int) (frac * (panelW - 110));
            g.fill(px + 5, y + 1, px + 5 + Math.max(1, barW), y + rowH - 2, barColor);
            g.drawString(font, entry.getKey(), px + 5, y + 1, 0xFF888898, false);
            // avg vs worst-case-this-window so a smoothed-away single-frame stutter still shows up
            String msStr = String.format("%.2f §8/ §7%.2fms", ms, worst);
            g.drawString(font, msStr, px + panelW - font.width(net.minecraft.util.StringUtil.stripColor(msStr)) - 5,
                    y + 1, 0xFFCCCCCC, false);
            y += rowH;
        }
        g.pose().popPose();
    }

    // ── Ctrl+F search overlay ─────────────────────────────────────────────────

    private void openSearchOverlay() {
        if (minecraft != null) minecraft.setScreen(new SearchOverlayScreen(this));
    }

    // ── Filter pills ──────────────────────────────────────────────────────────

    private static final String[] FILTER_KEYS = { "ALL", "AVAILABLE", "ACTIVE", "COMPLETE", "LOCKED" };
    private static final String[] FILTER_GLYPHS = { "◉", "○", "◑", "✔", "🔒" };
    private static final int[] FILTER_COLORS = {
            0xFFAAAAAA, // ALL — neutral
            0xFF55BBFF, // AVAILABLE — blue
            0xFFFFBB33, // ACTIVE — amber
            0xFF44CC88, // COMPLETE — green
            0xFF666688, // LOCKED — muted purple
    };

    /** Draws compact pill-style filter tabs in the toolbar row. */
    private void drawFilterPills(GuiGraphics g, int mx, int my, int cl, int cr) {
        int px = cl + 4;
        int py = TOOLBAR_Y + 2;
        int ph = TOOLBAR_H - 4;

        for (int i = 0; i < FILTER_KEYS.length; i++) {
            boolean sel = stateFilter.equals(FILTER_KEYS[i]);
            String label = FILTER_GLYPHS[i] + " " +
                    (FILTER_KEYS[i].charAt(0) + FILTER_KEYS[i].substring(1).toLowerCase());
            int pw = font.width(label) + 8;
            boolean hov = mx >= px && mx < px + pw && my >= py && my < py + ph;

            // Background
            int bg = sel ? (FILTER_COLORS[i] & 0x00FFFFFF | 0x33000000) : (hov ? 0x22FFFFFF : 0x00000000);
            if (bg != 0) g.fill(px, py, px + pw, py + ph, bg);

            // Accent underline when selected
            if (sel) g.fill(px, py + ph - 1, px + pw, py + ph, FILTER_COLORS[i]);

            // Label
            int col = sel ? FILTER_COLORS[i] : (hov ? 0xFFCCCCCC : 0xFF666677);
            g.drawString(font, label, px + 4, py + 2, col, false);

            px += pw + 4;
        }
    }

    /** Returns pill bounds for hit-testing in mouseClicked. [x0,y0,x1,y1] per filter. */
    private int[][] filterPillBounds(int cl, int cr) {
        int px = cl + 4;
        int py = TOOLBAR_Y + 2, ph = TOOLBAR_H - 4;
        int[][] bounds = new int[FILTER_KEYS.length][4];
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            String label = FILTER_GLYPHS[i] + " " +
                    (FILTER_KEYS[i].charAt(0) + FILTER_KEYS[i].substring(1).toLowerCase());
            int pw = font.width(label) + 8;
            bounds[i] = new int[] { px, py, px + pw, py + ph };
            px += pw + 4;
        }
        return bounds;
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private void renderToolbar(GuiGraphics g, int mx, int my, int cl, int cr) {
        int ty = TOOLBAR_Y;
        g.fill(0, ty, width, ty + TOOLBAR_H, C_PANEL_DARK);
        g.fill(0, ty + TOOLBAR_H - 1, width, ty + TOOLBAR_H, C_BORDER);

        // Search box is a widget rendered by super.render() — just leave space for it.
        // Filter pills follow the search box (offset by SEARCH_BOX_W + gap).
        drawFilterPills(g, mx, my, cl, cr);

        // Settings + Fit controls (right side, before inspector edge)
        int rx = cr - 4;
        rx = drawToolbarBtnR(g, mx, my, rx, ty, "⊞ Fit");
        rx -= 2;
        rx = drawToolbarBtnR(g, mx, my, rx, ty, "⚙");
        if (isDevMode) {
            rx -= 2;
            rx = drawToolbarBtnR(g, mx, my, rx, ty, "?");
        }
        rx -= 2;
        // Hide-completed toggle
        String hideLabel = hideCompleted ? "§a✔ Hide done" : "§8✔ Hide done";
        rx = drawToolbarBtnR(g, mx, my, rx, ty, hideLabel);
        rx -= 2;

        // Minimap toggle (always visible)
        String mmLabel = minimapOpen ? "§a⊡ Map" : "§8⊡ Map";
        rx = drawToolbarBtnR(g, mx, my, rx, ty, mmLabel);
        rx -= 2;

        // DEV indicator (right-click canvas for dev options)
        if (isDevMode) {
            String devLabel = "DEV";
            int dbx = rx - font.width(devLabel) - 12;
            g.fill(dbx, ty + 4, dbx + font.width(devLabel) + 8, ty + TOOLBAR_H - 4, 0x221a0d26);
            g.fill(dbx, ty + TOOLBAR_H - 4, dbx + font.width(devLabel) + 8, ty + TOOLBAR_H - 3, 0xFF9955CC);
            g.drawString(font, "§5" + devLabel, dbx + 4, ty + 4, 0xFF9955CC, false);
        }
    }

    private int drawToolbarBtnR(GuiGraphics g, int mx, int my, int rx, int ty, String label) {
        int tw = font.width(label.replaceAll("§.", "")) + 10;
        int th = TOOLBAR_H - 8;
        int bx = rx - tw, by = ty + 4;
        boolean hov = mx >= bx && mx < bx + tw && my >= by && my < by + th;
        if (hov) g.fill(bx, by, bx + tw, by + th, 0x22FFFFFF);
        g.drawString(font, label, bx + 5, by + 3, hov ? C_TEXT : C_TEXT_DIM, false);
        return bx - 2;
    }

    // ── Inspector removed from overview (shown only in QuestTasksScreen) ───────
    // All inspector rendering methods have been moved to QuestTasksScreen for a cleaner canvas.

    // ── Sidebar gear ──────────────────────────────────────────────────────────

    private static final int GEAR_SIZE = 14;

    private int gearY() {
        return height - GEAR_SIZE - 4;
    }

    private boolean gearHovered(int mx, int my) {
        if (sidebarCollapsed) return false;
        int gy = gearY();
        return mx >= sidebarW() - GEAR_SIZE - 4 && mx < sidebarW() - 4 && my >= gy && my < gy + GEAR_SIZE;
    }

    /**
     * Sits one row above the gear button - at icon-strip width (48px) there's no longer room
     * to place them side by side the way the old wide sidebar did.
     */
    private int newCatBtnY() {
        return gearY() - 4 - 14;
    }

    /**
     * Compact "+" icon tile matching the icon-strip aesthetic - was a full-width text pill,
     * which no longer fits at icon-strip width. Tooltip on hover explains it (same pattern as
     * renderSidebarGear) since there's no room for a text label either.
     */
    private void renderSidebarNewCategoryButton(GuiGraphics g, int mx, int my) {
        if (!isDevMode) return;
        int x = 4, y = newCatBtnY(), w = sidebarW() - 9, h = 14;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0x33FFFFFF : 0x1AFFFFFF);
        g.fill(x, y, x + w, y + 1, hov ? C_BORDER_LIT : C_BORDER);
        g.drawCenteredString(font, newCatFormOpen ? "§8–" : "§a+", x + w / 2, y + 3, C_TEXT);
        if (hov && !newCatFormOpen) {
            int ttW = font.width("New chapter") + 10;
            int ttX = sidebarW() + 3, ttY = y - 2;
            g.fill(ttX, ttY, ttX + ttW, ttY + 14, 0xFF1A1A24);
            ChroniclesUIKit.drawBorder(g, ttX, ttY, ttW, 14, C_BORDER_LIT);
            g.drawString(font, "§7New chapter", ttX + 5, ttY + 3, C_TEXT_DIM, false);
        }

        // Inline "new chapter" name form - drawn here (not via super.render()'s default widget
        // pass) and pushed above node-icon z (100) so its own opaque backing panel and the
        // EditBox's text don't bleed/lose the depth test against canvas content behind it, since
        // it deliberately floats out over the canvas edge (see newCatBox's own comment).
        if (newCatFormOpen && newCatBox != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 150f);
            g.flush();
            int bx = newCatBox.getX() - 2, by = newCatBox.getY() - 2;
            int bw = newCatBox.getWidth() + 4, bh = newCatBox.getHeight() + 4;
            g.fill(bx, by, bx + bw, by + bh, C_PANEL);
            ChroniclesUIKit.drawBorder(g, bx, by, bw, bh, C_BORDER_LIT);
            newCatBox.render(g, mx, my, 0f);
            g.flush();
            g.pose().popPose();
        }
    }

    private boolean newCatButtonHovered(int mx, int my) {
        if (!isDevMode || sidebarCollapsed) return false;
        int x = 4, y = newCatBtnY(), w = sidebarW() - 9, h = 14;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Called by NewChapterChoiceScreen's "New Chapter" button to open the inline name form. */
    public void openNewChapterForm() {
        if (minecraft != null) minecraft.setScreen(this);
        newCatFormOpen = true;
        rebuild();
    }

    /**
     * Folder header row: just a centered chevron - full label only shows as a tooltip (see
     * renderSidebarTooltip) since there's no room for text at icon-strip width.
     */
    private void renderSidebarFolderRow(GuiGraphics g, SidebarRow row, int mx, int my) {
        int y = row.y(), h = row.height();
        boolean hov = mx >= 0 && mx < sidebarW() - 1 && my >= y && my < y + h;
        g.fill(0, y, sidebarW() - 1, y + h, hov ? 0xFF1C1C24 : 0xFF15151B);
        g.fill(0, y + h - 1, sidebarW() - 1, y + h, C_BORDER);
        String arrow = row.collapsed() ? "▶" : "▼";
        g.drawString(font, "§8" + arrow, 4, y + (h - 8) / 2, hov ? C_TEXT_DIM : C_TEXT_FAINT, false);
        String label = row.label();
        int maxLabelW = sidebarW() - 16;
        if (font.width(label) > maxLabelW) label = font.plainSubstrByWidth(label, maxLabelW - 4) + "…";
        g.drawString(font, "§l" + label, 13, y + (h - 8) / 2, hov ? C_TEXT_DIM : C_TEXT_FAINT, false);
    }

    /**
     * Category row: FTBQ-style compact list - small item icon, accent-colored name, selection
     * outline, a thin progress underline, and a small red attention badge on the icon when the
     * category has a quest currently ACTIVE. Full count still shows on hover (renderSidebarTooltip)
     * or in the canvas title bar once selected.
     */
    private void renderSidebarCatRow(GuiGraphics g, SidebarRow row, int mx, int my) {
        String cat = row.id();
        int y = row.y(), h = row.height();
        // Sub-chapters get extra indent on top of the normal in-folder indent, so nesting under
        // a parent chapter reads as visually distinct from just being grouped in a folder -
        // stacks with inFolder's indent when a sub-chapter's parent also happens to be foldered.
        int indent = (row.inFolder() ? 6 : 0) + (row.subChapter() ? 10 : 0);
        int iconX = 4 + indent;
        int iconY = y + (h - 16) / 2;
        boolean locked = row.locked();
        int accent = categoryAccent(cat);
        boolean isSel = cat.equals(selectedCategory);
        boolean hov = !locked && mx >= 0 && mx < sidebarW() - 1 && my >= y && my < y + h;

        if (isSel) {
            g.fill(1, y + 1, sidebarW() - 2, y + h - 1, C_SEL_TAB);
            ChroniclesUIKit.drawBorder(g, 1, y + 1, sidebarW() - 3, h - 2, accent);
        } else if (hov) {
            g.fill(1, y + 1, sidebarW() - 2, y + h - 1, 0x14FFFFFF);
        }

        net.minecraft.world.item.Item iconItem = CategoryConfig.get(cat).getIconItem();
        if (locked) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.55f, 0.55f, 0.55f, 1f);
        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), iconX, iconY);
        if (locked) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Name tinted with the category's accent color - dimmed a bit when not selected/hovered
        // so the selected row still reads as the visually "loudest" one in the list. A locked
        // sub-chapter (parent has no completed quest yet) is shown but greyed and unselectable,
        // same "visible but not yet accessible" convention as a locked quest node.
        int textCol = locked ? C_TEXT_FAINT : (isSel || hov) ? accent : blendColor(accent, C_TEXT_DIM, 0.4f);
        String label = (locked ? "§8🔒 " : "") + row.label();
        int maxLabelW = sidebarW() - (iconX + 18) - 4;
        if (font.width(label) > maxLabelW) label = font.plainSubstrByWidth(label, maxLabelW - 4) + "…";
        g.drawString(font, label, iconX + 18, y + (h - 8) / 2, textCol, false);

        if (locked) return; // no progress bar / attention badge for an inaccessible sub-chapter

        // Thin progress underline along the bottom edge of the row
        int[] p = progressCache.computeIfAbsent(cat, this::computeCategoryProgress);
        if (p[1] > 0) {
            float fraction = (float) p[0] / p[1];
            int barCol = (p[0] == p[1]) ? C_PROG_FILL : (p[0] > 0 ? C_PROG_ACT : 0x33FFFFFF);
            int barX0 = iconX, barW = sidebarW() - 4 - barX0;
            g.fill(barX0, y + h - 2, barX0 + barW, y + h - 1, 0x22FFFFFF);
            g.fill(barX0, y + h - 2, barX0 + Math.round(barW * fraction), y + h - 1, barCol);
        }

        // Small red attention badge on the icon's top-right corner when a quest in this
        // category is currently ACTIVE - mirrors FTBQ's "something needs a look" indicator.
        boolean attention = attentionCache.computeIfAbsent(cat, this::computeCategoryHasAttention);
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

    private void renderSidebarGear(GuiGraphics g, int mx, int my) {
        int gx = sidebarW() - GEAR_SIZE - 4;
        int gy = gearY();
        boolean hov = gearHovered(mx, my);

        // Subtle separator above utilities area
        g.fill(4, gy - 6, sidebarW() - 4, gy - 5, C_BORDER);

        // Gear glyph
        int col = hov ? 0xFFDDDDE8 : 0xFF555566;
        g.drawString(font, "⚙", gx + 1, gy + 1, col, false);

        if (hov) {
            // Tooltip panel — anchored just outside the (icon-strip width) sidebar rather than
            // squeezed to its left edge, which is what was making it read as "cut off by the
            // sidebar": at ~48px sidebar width, gx - ttW - 4 always clamped to the same x=2
            // regardless of icon position, jamming a 200px-wide panel into a much narrower strip.
            int ttW = 200;
            int ttH = isDevMode ? 64 : 30;
            int ttXRaw = sidebarW() + 3;
            int ttYRaw = gy - ttH - 2;
            if (ttXRaw + ttW > width) ttXRaw = width - ttW - 2;
            if (ttYRaw < 2) ttYRaw = 2;
            final int ttX = ttXRaw;
            final int ttY = ttYRaw;
            boolean dev = isDevMode;
            // This tooltip runs from step 8 (renderScreenOverlays), already after node icons draw
            // in step 7 - but neither elevating its z above the icons' z=100 depth write, nor
            // outright disabling the depth test around it, reliably beat them (icons still bled
            // through the tooltip text). g.renderItem()'s icon geometry apparently isn't actually
            // submitted to the GPU in step-7 call order - it seems to only get flushed out
            // whenever a later step's g.flush() call forces a batched submission of everything
            // still pending, at which point per-render-type ordering inside that single submission
            // can still place it on top regardless of which quad was queued first. The one
            // approach that's reliably worked for other tooltips in this screen (the grid-snap
            // pill, sidebar category rows) is deferring the draw into pendingTooltip, which runs
            // at the true tail of render() - after every other step, including this one - so
            // there is nothing left afterward that could still be holding pending icon geometry.
            pendingTooltip = () -> {
                // Belt-and-suspenders: deferring to the true tail of render() (see the comment
                // above) protects against submission-order surprises, but item icons still write
                // real depth values wherever they landed - disabling the depth test here too
                // means this quad paints regardless of whatever's already in the depth buffer,
                // the same way vanilla's own Screen.renderTooltip() guarantees tooltips always
                // sit on top of item icons.
                g.flush();
                RenderSystem.disableDepthTest();
                g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xFF1A1A24);
                g.fill(ttX, ttY, ttX + ttW, ttY + 1, C_BORDER);
                g.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, C_BORDER);
                g.fill(ttX, ttY, ttX + 1, ttY + ttH, C_BORDER);
                g.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, C_BORDER);
                g.drawString(font, "§dUtilities", ttX + 5, ttY + 4, C_TEXT, false);
                g.drawString(font, "§8§oLeft-click§r§8: Edit all quest texts", ttX + 5, ttY + 14, C_TEXT_DIM, false);
                if (dev) {
                    g.drawString(font, "§8§oRight-click§r§8: Export lang/en_us.json", ttX + 5, ttY + 24,
                            C_TEXT_DIM, false);
                    g.drawString(font, "§8§o[I]§r§8: Import FTB Quests chapter", ttX + 5, ttY + 34, C_TEXT_DIM,
                            false);
                    g.drawString(font, "§8(place .snbt in ftb_import/ folder)", ttX + 5, ttY + 44, C_TEXT_FAINT,
                            false);
                    g.drawString(font, "§8(pack's en_us.json also goes there)", ttX + 5, ttY + 54, C_TEXT_FAINT,
                            false);
                }
                g.flush();
                RenderSystem.enableDepthTest();
            };
        }
    }

    // ── Bulk-ops panel ────────────────────────────────────────────────────────

    private void renderBulkOpsPanel(GuiGraphics g, int mx, int my, int cl, int cr) {
        // Same z-ordering/opacity issue as the stats and tooltip panels: node icons render at
        // z=100 via g.renderItem(), so this needs to sit above that and be fully opaque or the
        // canvas behind it can show through.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        int n = multiSelection.size();
        int bx = cl + 4, by = HEADER_H + 4;
        int bw = 360, bh = 38;
        g.fill(bx, by, bx + bw, by + bh, 0xFF131319);
        g.fill(bx, by, bx + bw, by + 1, C_BORDER_LIT);
        g.fill(bx, by, bx + 1, by + bh, C_BORDER_LIT);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, C_BORDER_LIT);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, C_BORDER_LIT);
        g.fill(bx, by, bx + 2, by + bh, 0xFF00DDFF); // cyan left accent

        g.drawString(font, "§b" + n + " selected", bx + 6, by + 4, 0xFF00DDFF);
        g.drawString(font, "§8Ctrl+click to toggle  ·  Esc to clear", bx + 6, by + 14, C_TEXT_FAINT);

        // Shape picker row
        String[] glyphs = { "■", "●", "◆", "⬡", "▲", "★", "⬠", "❖", "✚", "▩" };
        String[] shapeIds = { "SQUARE", "CIRCLE", "DIAMOND", "HEXAGON", "TRIANGLE", "STAR", "PENTAGON", "SHIELD",
                "CROSS", "CUSTOM" };
        int slotW = 14, startX = bx + 6, slotY = by + 24;
        for (int i = 0; i < glyphs.length; i++) {
            int sx = startX + i * (slotW + 2);
            boolean hov = mx >= sx && mx < sx + slotW && my >= slotY && my < slotY + 12;
            if (hov) g.fill(sx, slotY, sx + slotW, slotY + 12, 0xFF222233);
            g.drawString(font, "§7" + glyphs[i], sx + 2, slotY + 2, hov ? 0xFFFFFFFF : 0xFF888899);
        }
        // "Move to cat ▸" and "Delete all" labels
        int actX = startX + glyphs.length * (slotW + 2) + 8;
        boolean catHov = mx >= actX && mx < actX + 58 && my >= slotY && my < slotY + 12;
        if (catHov || bulkMoveCatOpen) g.fill(actX, slotY, actX + 58, slotY + 12, 0xFF222233);
        g.drawString(font, "§7Move cat ▸", actX, slotY + 2, (catHov || bulkMoveCatOpen) ? 0xFFCCCCFF : C_TEXT_DIM);
        int delX = actX + 62;
        boolean delHov = mx >= delX && mx < delX + 44 && my >= slotY && my < slotY + 12;
        if (delHov) g.fill(delX, slotY, delX + 44, slotY + 12, 0xFF221212);
        g.drawString(font, "§cDel all", delX, slotY + 2, delHov ? 0xFFFF5555 : C_CTX_DANGER);

        // Bulk move submenu
        if (bulkMoveCatOpen) {
            List<String> moveCats = buildCategoryList();
            moveCats.remove("ALL");
            int subX = actX, subY = slotY + 13, subRH = 11, subW = 90;
            g.fill(subX, subY, subX + subW, subY + moveCats.size() * subRH + 4, 0xFF1A1A24);
            g.fill(subX, subY, subX + subW, subY + 1, C_BORDER_LIT);
            g.fill(subX, subY, subX + 1, subY + moveCats.size() * subRH + 4, C_BORDER_LIT);
            g.fill(subX + subW - 1, subY, subX + subW, subY + moveCats.size() * subRH + 4, C_BORDER_LIT);
            for (int ci = 0; ci < moveCats.size(); ci++) {
                int ry = subY + 2 + ci * subRH;
                boolean rHov = mx >= subX + 2 && mx < subX + subW - 2 && my >= ry && my < ry + subRH;
                if (rHov) g.fill(subX + 2, ry, subX + subW - 2, ry + subRH, 0xFF222233);
                g.drawString(font, "§7" + friendly(moveCats.get(ci)), subX + 4, ry + 2, rHov ? 0xFFCCCCFF : C_TEXT_DIM);
            }
        }
        g.pose().popPose();
    }

    // ── Node rendering (zoom + shape aware) ───────────────────────────────────

    /** Resolves a "CUSTOM"-shape node's picked texture, or null if none set / malformed. */
    private ResourceLocation resolveShapeTexture(QuestNode node) {
        String tex = node.getShapeTexture();
        if (tex == null || tex.isEmpty()) return null;
        try {
            return CustomTextureCache.resolve(new ResourceLocation(tex));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * First of two node-render passes: effects (halo/bloom/pulse, unbatched - already cheap)
     * plus the shape fill/outline (batched - see NodeShapeRenderer.queueFillRect/flushFillQueue).
     * Must run for every visible node, THEN NodeShapeRenderer.flushFillQueue(g) once, THEN
     * renderNodeDetails() for every visible node - overlays/icons/badges need the now-flushed
     * shape to already be on screen underneath them.
     */
    private void renderNodeShape(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                 boolean hovered, boolean selected) {
        // Link stubs (FTB's "quest link" equivalent) are a placeholder pointing at a real quest
        // defined elsewhere - show ITS state/icon so the shortcut reads as "is the real quest
        // done", not the stub's own meaningless (task-less, trivially-completable) state.
        QuestNode linkTargetNode = resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = getState(displaySource);
        int fill = switch (st) {
            case COMPLETED -> C_NODE_DONE;
            case ACTIVE -> C_NODE_ACTIVE;
            case LOCKED -> C_NODE_LOCKED;
            default -> C_NODE_UNLOCKED;
        };
        int border = switch (st) {
            case COMPLETED -> C_NBORD_DONE;
            case ACTIVE -> C_NBORD_ACTIVE;
            case LOCKED -> isDevMode ? C_NBORD_DEV : C_NBORD_LOCKED;
            default -> C_NBORD_UNLOCKED;
        };
        if (selected) border = C_NBORD_SEL;
        if (hovered) fill = blendColor(fill, 0xFFFFFFFF, 0.08f);

        FrameProfiler.begin("node:effects");
        // Selection glow halo
        if (selected)
            g.fill(x - 2, y - 2, x + sz + 2, y + sz + 2, (border & 0x00FFFFFF) | 0x44000000);

        // Decorative glow/bloom/shadow effects extend several pixels beyond the node's own
        // footprint. That's fine when nodes have room to breathe, but on a large imported
        // pack zoomed far out (hundreds of nodes just a few px apart) those halos overlap
        // adjacent nodes and smear the whole view into an illegible blob. Skip them below
        // this size and let the plain fill+outline (still zoom-scaled) carry the node instead.
        boolean roomForEffects = sz >= 14;

        // COMPLETED: soft green bloom — layered expanding fills, each softer
        if (st == QuestState.COMPLETED && roomForEffects) {
            int bloomRgb = C_NBORD_DONE & 0x00FFFFFF;
            g.fill(x - 4, y - 4, x + sz + 4, y + sz + 4, 0x0C000000 | bloomRgb);
            g.fill(x - 3, y - 3, x + sz + 3, y + sz + 3, 0x18000000 | bloomRgb);
            g.fill(x - 2, y - 2, x + sz + 2, y + sz + 2, 0x28000000 | bloomRgb);
        }

        // ACTIVE: pulsing outer glow
        if (st == QuestState.ACTIVE && roomForEffects) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.4 + 0.6);
            int baseColor = C_NBORD_ACTIVE & 0x00FFFFFF;
            for (int d = 3; d >= 1; d--) {
                int alpha = (int) (pulse * 0x50 * (1f - d * 0.28f)) & 0xFF;
                g.fill(x - d, y - d, x + sz + d, y + sz + d, (alpha << 24) | baseColor);
            }
        }
        FrameProfiler.end("node:effects");

        FrameProfiler.begin("node:shape");
        String shape = node.getShapeType() != null ? node.getShapeType().toUpperCase() : "SQUARE";
        dbgShapeCounts.merge(shape, 1, Integer::sum);

        ResourceLocation shapeTex = "CUSTOM".equals(shape) ? resolveShapeTexture(node) : null;

        // Drop shadow — shape-matched so it doesn't bleed outside non-square nodes
        if (roomForEffects) {
            switch (shape) {
                case "CIRCLE" -> NodeShapeRenderer.fillCircle(g, x + 2, y + 2, sz, 0x44000000);
                case "DIAMOND" -> NodeShapeRenderer.fillDiamond(g, x + 2, y + 2, sz, 0x44000000);
                case "HEXAGON" -> NodeShapeRenderer.fillHexagon(g, x + 2, y + 2, sz, 0x44000000);
                case "TRIANGLE" -> NodeShapeRenderer.fillTriangle(g, x + 2, y + 2, sz, 0x44000000);
                case "STAR" -> NodeShapeRenderer.fillStar(g, x + 2, y + 2, sz, 0x44000000);
                case "PENTAGON" -> NodeShapeRenderer.fillPentagon(g, x + 2, y + 2, sz, 0x44000000);
                case "SHIELD" -> NodeShapeRenderer.fillShield(g, x + 2, y + 2, sz, 0x44000000);
                case "CROSS" -> NodeShapeRenderer.fillCross(g, x + 2, y + 2, sz, 0x44000000);
                case "CUSTOM" -> {
                    if (shapeTex != null)
                        NodeShapeRenderer.blitCustomShape(g, shapeTex, x + 2, y + 2, sz, sz, 0x44000000);
                    else NodeShapeRenderer.queueFillRect(g, x + 2, y + 2, x + sz + 2, y + sz + 2, 0x44000000);
                }
                default -> NodeShapeRenderer.queueFillRect(g, x + 2, y + 2, x + sz + 2, y + sz + 2, 0x44000000);
            }
        }

        // Outline stroke thickness grows with the node's zoomed pixel size, so borders don't
        // stay a hairline 1px when zoomed way in, or look disproportionately thick relative
        // to a tiny node when zoomed way out.
        int thickness = Math.max(1, Math.min(4, sz / 28));

        switch (shape) {
            case "CIRCLE" -> {
                NodeShapeRenderer.fillCircle(g, x, y, sz, fill);
                NodeShapeRenderer.outlineCircle(g, x, y, sz, border, thickness);
            }
            case "DIAMOND" -> {
                NodeShapeRenderer.fillDiamond(g, x, y, sz, fill);
                NodeShapeRenderer.outlineDiamond(g, x, y, sz, border, thickness);
            }
            case "HEXAGON" -> {
                NodeShapeRenderer.fillHexagon(g, x, y, sz, fill);
                NodeShapeRenderer.outlineHexagon(g, x, y, sz, border, thickness);
            }
            case "TRIANGLE" -> {
                NodeShapeRenderer.fillTriangle(g, x, y, sz, fill);
                NodeShapeRenderer.outlineTriangle(g, x, y, sz, border, thickness);
            }
            case "STAR" -> {
                NodeShapeRenderer.fillStar(g, x, y, sz, fill);
                NodeShapeRenderer.outlineStar(g, x, y, sz, border, thickness);
            }
            case "PENTAGON" -> {
                NodeShapeRenderer.fillPentagon(g, x, y, sz, fill);
                NodeShapeRenderer.outlinePentagon(g, x, y, sz, border, thickness);
            }
            case "SHIELD" -> {
                NodeShapeRenderer.fillShield(g, x, y, sz, fill);
                NodeShapeRenderer.outlineShield(g, x, y, sz, border, thickness);
            }
            case "CROSS" -> {
                NodeShapeRenderer.fillCross(g, x, y, sz, fill);
                NodeShapeRenderer.outlineCross(g, x, y, sz, border, thickness);
            }
            case "CUSTOM" -> {
                if (shapeTex != null) {
                    // Border "ring" - the same texture blitted slightly larger and tinted with
                    // the border color, drawn first so the smaller tinted-fill copy on top only
                    // leaves that backing plate visible around its edges. Needs no extra art
                    // from the pack dev beyond the one shape PNG.
                    int pad = Math.max(1, thickness);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x - pad, y - pad, sz + pad * 2, sz + pad * 2,
                            border);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x, y, sz, sz, fill);
                } else {
                    // No texture picked yet - fall back to a plain square so the node isn't
                    // invisible while a pack dev is still setting it up.
                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                    NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
                }
            }
            default -> {  // SQUARE
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
            }
        }
        FrameProfiler.end("node:shape");
    }

    /**
     * Second of two node-render passes - see renderNodeShape()'s doc comment. Must run only
     * after NodeShapeRenderer.flushFillQueue(g) has painted every node's shape for this frame.
     */
    private void renderNodeDetails(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                   boolean hovered, boolean selected) {
        QuestNode linkTargetNode = resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = getState(displaySource);

        FrameProfiler.begin("node:overlays");
        // DISABLED visibility: grayed-out overlay — visible but can't be completed
        if (node.getVisibility() == QuestNode.Visibility.DISABLED) {
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xBB0B0B0F);
            g.drawCenteredString(font, "§8✕", x + sz / 2, y + sz / 2 - 4, 0xFF444444);
        }

        // Flag-disabled (enable_if = false): dev-only dashed purple border + "⚑" glyph
        if (isDevMode && node.isFlagDisabled()) {
            g.fill(x - 2, y - 2, x + sz + 2, y - 1, 0xBB7722BB);
            g.fill(x - 2, y + sz + 1, x + sz + 2, y + sz + 2, 0xBB7722BB);
            g.fill(x - 2, y - 1, x - 1, y + sz + 1, 0xBB7722BB);
            g.fill(x + sz + 1, y - 1, x + sz + 2, y + sz + 1, 0xBB7722BB);
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xCC0B0B0F);
            g.drawCenteredString(font, "§5⚑", x + sz / 2, y + sz / 2 - 4, 0xFFAA44CC);
        }

        // Search dim: if search is active and this node doesn't match, fade it out
        if (!searchQuery.isEmpty() && !matchesSearch(node)) {
            g.fill(x - 1, y - 1, x + sz + 1, y + sz + 1, 0xCC0B0B0F);
        }

        // LOCKED: light hatch overlay to indicate inaccessible nodes
        if (st == QuestState.LOCKED && !isDevMode) {
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0x440B0B0F);
            // Diagonal hatch lines (every 6px, running top-left to bottom-right)
            for (int d = -(sz); d < sz; d += 6) {
                for (int i = 0; i < sz - 1; i++) {
                    int hx = x + 1 + i;
                    int hy = y + 1 + i + d;
                    if (hx < x + 1 || hx >= x + sz - 1 || hy < y + 1 || hy >= y + sz - 1) continue;
                    g.fill(hx, hy, hx + 1, hy + 1, 0x160B0B0F);
                }
            }
        }
        FrameProfiler.end("node:overlays");

        FrameProfiler.begin("node:progress");
        // Progress arc ring around the node (clockwise from top, proportional to tasks done)
        List<QuestTask> tasks = node.getTasks();
        if (!tasks.isEmpty() && sz >= 14) {
            int total = 0, done = 0;
            if (minecraft != null && minecraft.player != null) {
                for (QuestTask t : tasks) {
                    if (t.isOptional()) continue;
                    total++;
                    if (isTaskDone(t)) done++;
                }
            }
            if (total > 0) {
                float fraction = st == QuestState.COMPLETED ? 1f : (float) done / total;
                int arcColor = st == QuestState.COMPLETED ? C_NBORD_DONE :
                        st == QuestState.ACTIVE ? C_NBORD_ACTIVE : 0xFF4488BB;
                drawProgressArc(g, x + sz / 2, y + sz / 2, sz / 2 + 3, fraction, arcColor, 0x22FFFFFF);
            }
        }

        // UNLOCKED: small pulsing "ready" dot in top-right corner
        if (st == QuestState.UNLOCKED && sz >= 20) {
            float readyPulse = (float) (Math.sin(System.currentTimeMillis() / 700.0) * 0.35 + 0.65);
            int dotAlpha = (int) (readyPulse * 0xFF) & 0xFF;
            int dotColor = (dotAlpha << 24) | 0x004488FF;
            g.fill(x + sz - 6, y + 1, x + sz - 1, y + 6, dotColor);
        }
        FrameProfiler.end("node:progress");

        FrameProfiler.begin("node:icon");
        FrameProfiler.begin("node:icon:lookup");
        // Icon: try custom PNG first, then a picked texture, then scaled item, then state glyph
        String questPath = displaySource.getId().getPath();
        ResourceLocation customIcon = QuestIconCache.get(questPath);
        ResourceLocation pickedTexture = null;
        if (customIcon == null && !displaySource.getIconTexture().isEmpty()) {
            try {
                pickedTexture = new ResourceLocation(displaySource.getIconTexture());
            } catch (Exception ignored) {}
        }
        FrameProfiler.end("node:icon:lookup");
        if (customIcon != null && sz >= 8) {
            int[] dims = QuestIconCache.getDimensions(questPath);
            int pad = Math.max(2, sz / 8);
            int iconSz = sz - pad * 2;
            g.blit(customIcon, x + pad, y + pad, 0, 0, iconSz, iconSz, dims[0], dims[1]);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            dbgCustomIconCount++;
        } else if (pickedTexture != null && sz >= 8) {
            int pad = Math.max(2, sz / 8);
            int iconSz = sz - pad * 2;
            g.blit(pickedTexture, x + pad, y + pad, 0, 0, iconSz, iconSz, iconSz, iconSz);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            dbgPickedTextureIconCount++;
        } else {
            Item icon = displaySource.getIconItem();
            if (icon == null) icon = fallbackTaskIcon(displaySource);
            // Reverted the sz>=16 LOD gate: it turned out to make nodes go iconless far more
            // broadly than intended without measurably helping the frame cost, so the actual
            // bottleneck is something else - see the new per-section "node visuals" profiler
            // breakdown below instead of guessing again.
            if (icon != null && icon != Items.AIR && sz >= 6) {
                // Reverted off-screen render-to-texture caching (ItemIconRenderCache) after two
                // rounds of visible corruption (noisy/garbled icons) that didn't resolve cleanly
                // - that path touches real GL framebuffer/projection state I can't verify without
                // actually running the client, and shipping broken visuals isn't worth the perf
                // win. Back to the plain, vanilla-proven full 3D render per node for correctness;
                // the node-visuals cost this brings back is a separate follow-up.
                float scale = sz / 16f * 0.75f;
                float cx = x + sz / 2f, cy = y + sz / 2f;

                // Reverted the texture-filter smoothing toggle (setFilter(true,false) before the
                // draw, restored after). The math behind "only touches zoomed-in icons, vast
                // majority of draws untouched" was wrong: scale > 1.1 triggers whenever sz > ~23px,
                // which is true for EVERY node at 73%+ zoom (NODE_SIZE=32 means sz=32 at 100% zoom
                // alone) - i.e. the common case at normal viewing zoom, not a rare edge case. Two
                // extra texture-parameter changes (a GL sync point on some drivers) per node per
                // frame at typical zoom is a very plausible match for "node visuals still slow on
                // a normal-sized chapter" persisting after the two prior perf fixes, which were
                // both specifically about behavior at extreme zoom-out. Flagged as unverified from
                // the start; this is that revert.
                FrameProfiler.begin("node:icon3d");
                g.pose().pushPose();
                g.pose().translate(cx, cy, 100f);
                // Z left at 1 deliberately - scaling it along with X/Y multiplies g.renderItem()'s
                // own internal z-offset by this (zoom-dependent) scale factor too. At normal zoom
                // scale is ~1 so it went unnoticed, but scale grows directly with node size/zoom,
                // and at high zoom it inflated the icon's effective depth enough to win the depth
                // test against everything drawn after it regardless of flush ordering - the "only
                // happens zoomed in a lot" bleed.
                g.pose().scale(scale, scale, 1f);
                g.renderItem(new ItemStack(icon), -8, -8);
                g.pose().popPose();
                FrameProfiler.end("node:icon3d");

                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
                dbgFull3DIconCount++;
            } else if (sz >= 10) {
                String glyph = switch (st) {
                    case COMPLETED -> "✔";
                    case ACTIVE -> "▶";
                    case LOCKED -> "✕";
                    default -> "○";
                };
                int gc = switch (st) {
                    case COMPLETED -> C_NBORD_DONE;
                    case ACTIVE -> C_NBORD_ACTIVE;
                    case LOCKED -> isDevMode ? C_NBORD_DEV : C_NBORD_LOCKED;
                    default -> C_NBORD_UNLOCKED;
                };
                g.drawCenteredString(font, glyph, x + sz / 2, y + sz / 2 - 4, gc);
                dbgGlyphIconCount++;
            }
        }
        FrameProfiler.end("node:icon");

        FrameProfiler.begin("node:badges");
        // Link stub badge - small chain-link glyph in the top-left corner so a shortcut node
        // never reads as an identical duplicate of the real quest. A dangling link (target
        // failed to load - check /chronicles validate) gets a distinct red "broken link" badge
        // instead, so it's obviously different from a normal working link rather than just
        // silently rendering as a blank, dataless placeholder.
        if (node.isLinkStub() && sz >= 14) {
            if (linkTargetNode != null) {
                g.fill(x, y, x + 8, y + 7, 0xEE101820);
                g.drawString(font, "§b🔗", x + 1, y, 0xFF66CCFF, false);
            } else {
                g.fill(x, y, x + 8, y + 7, 0xEE330808);
                g.drawString(font, "§c!", x + 2, y, 0xFFFF6666, false);
            }
        }

        // Dev-mode validation warning badge — orange ⚠ in bottom-left corner
        // (link stubs are trivially task-less by design - validate the target, not the stub)
        if (isDevMode && sz >= 14 && !node.isLinkStub()) {
            List<String> issues = getValidationIssues(node);
            if (!issues.isEmpty()) {
                int bx = x, by = y + sz - 7;
                g.fill(bx, by, bx + 8, by + 7, 0xEE331800);
                g.drawString(font, "§6!", bx + 2, by, 0xFFFFAA00, false);
            }
        }
        FrameProfiler.end("node:badges");
    }

    // ── Quest group rendering ─────────────────────────────────────────────────

    /** Height of the label bar at the top of a group rectangle (in screen pixels). */
    private static final int GROUP_LABEL_BAR_H = 11;

    private void renderQuestGroup(GuiGraphics g, QuestGroup grp, int cl, int cr) {
        int sx = (int) (grp.getX() * posZoom()) + viewOffX + cl;
        int sy = (int) (grp.getY() * posZoom()) + viewOffY + HEADER_H;
        int sw = (int) (grp.getWidth() * posZoom());
        int sh = (int) (grp.getHeight() * posZoom());

        // Cull if entirely outside the canvas viewport
        if (sx + sw < cl || sx > cr || sy + sh < HEADER_H || sy > height) return;

        // Fill
        g.fill(sx, sy, sx + sw, sy + sh, grp.getColor());

        // 1-pixel border
        int bc = grp.getBorderColor();
        g.fill(sx, sy, sx + sw, sy + 1, bc);
        g.fill(sx, sy + sh - 1, sx + sw, sy + sh, bc);
        g.fill(sx, sy, sx + 1, sy + sh, bc);
        g.fill(sx + sw - 1, sy, sx + sw, sy + sh, bc);

        // Label bar at the top (slightly more opaque tint)
        g.fill(sx + 1, sy + 1, sx + sw - 1, sy + GROUP_LABEL_BAR_H, (grp.getBorderColor() & 0x00FFFFFF) | 0x55000000);

        // Label text (clipped to group width)
        if (sw > 20) {
            String label = grp.getLabel();
            int maxLabelW = sw - 8;
            if (font.width(label.replaceAll("§.", "")) > maxLabelW) {
                label = font.plainSubstrByWidth(label, maxLabelW - 6) + "…";
            }
            g.drawString(font, "§f" + label, sx + 4, sy + 2, 0xFFFFFFFF);
        }

        // Icon strip — small item/fluid/texture icons this group carries, drawn just under the
        // label bar (the bar itself is only 11px tall, too thin for a 16x16 item render).
        List<QuestGroup.GroupIcon> icons = grp.getIcons();
        if (!icons.isEmpty() && sw >= 24 && sh > GROUP_LABEL_BAR_H + 14) {
            int iconSz = 10, gap = 2;
            int stripY = sy + GROUP_LABEL_BAR_H + 2;
            int ix = sx + sw - 4;
            for (int i = icons.size() - 1; i >= 0 && ix - iconSz >= sx + 4; i--) {
                ix -= iconSz;
                renderGroupIcon(g, icons.get(i), ix, stripY, iconSz);
                ix -= gap;
            }
        }
    }

    /** Draws one group icon (item, fluid swatch, or arbitrary texture) at the given screen rect. */
    private void renderGroupIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getValue(new ResourceLocation(icon.id));
                    if (item == null || item == Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    g.pose().translate(x + size / 2f, y + size / 2f, 100f);
                    // Z left at 1 - see the matching comment on the node icon render above;
                    // scaling it too multiplies g.renderItem()'s own internal z-offset by this
                    // (zoom-dependent) scale, which is what caused icons to bleed above everything
                    // at high zoom.
                    g.pose().scale(scale, scale, 1f);
                    g.renderItem(new ItemStack(item), -8, -8);
                    g.pose().popPose();
                }
                case FLUID -> {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getValue(new ResourceLocation(icon.id));
                    if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
                    int col = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid)
                            .getTintColor() | 0xFF000000;
                    g.fill(x, y, x + size, y + size, col);
                    g.fill(x, y, x + size, y + 1, 0xFF444455);
                    g.fill(x, y + size - 1, x + size, y + size, 0xFF444455);
                    g.fill(x, y, x + 1, y + size, 0xFF444455);
                    g.fill(x + size - 1, y, x + size, y + size, 0xFF444455);
                }
                case TEXTURE -> g.blit(new ResourceLocation(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {
            // Bad/renamed registry id or texture path — skip this icon rather than crash the frame.
        }
    }

    /**
     * Returns the group whose label bar is under (mx, my), or null.
     * Used for context-menu detection.
     */
    @Nullable
    private QuestGroup groupAtLabelBar(double mx, double my, int cl) {
        for (QuestGroup grp : QuestGroupManager.forCategory(selectedCategory)) {
            int sx = (int) (grp.getX() * posZoom()) + viewOffX + cl;
            int sy = (int) (grp.getY() * posZoom()) + viewOffY + HEADER_H;
            int sw = (int) (grp.getWidth() * posZoom());
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + GROUP_LABEL_BAR_H) {
                return grp;
            }
        }
        return null;
    }

    /** Topmost placed background picture under the cursor, or null - last-drawn (last in list) wins ties. */
    private BackgroundPictureConfig.Picture pictureAt(double mx, double my, int cl) {
        List<BackgroundPictureConfig.Picture> pics = BackgroundPictureConfig.get(selectedCategory);
        BackgroundPictureConfig.Picture hit = null;
        for (BackgroundPictureConfig.Picture pic : pics) {
            int[] rect = BackgroundPictureRenderer.screenRect(pic, cl, HEADER_H, posZoom(), viewOffX, viewOffY);
            if (mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3]) hit = pic;
        }
        return hit;
    }

    private void drawArrowhead(GuiGraphics g, float tipX, float tipY, float dirX, float dirY, int color,
                               float scale) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.0001f) return;
        float ux = dirX / len, uy = dirY / len;
        float px = -uy, py = ux; // perpendicular unit vector
        float length = 9f * scale;    // tip-to-base distance
        float halfBaseW = 5f * scale; // half-width at the base
        int steps = Math.max(4, Math.round(length));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps; // 0 at tip, 1 at base
            float cx = tipX - ux * length * t;
            float cy = tipY - uy * length * t;
            int halfW = Math.round(halfBaseW * t);
            for (int w = -halfW; w <= halfW; w++) {
                int wx = Math.round(cx + px * w);
                int wy = Math.round(cy + py * w);
                g.fill(wx, wy, wx + 1, wy + 1, color);
            }
        }
    }

    // ── Progress arc ─────────────────────────────────────────────────────────

    /**
     * Draws a clockwise progress arc ring at physical pixel resolution.
     * Renders a 2-physical-pixel-wide ring (inner radius r−1, outer radius r)
     * so the arc is visible and clean at any GUI scale.
     */
    private void drawProgressArc(GuiGraphics g, int cx, int cy, int r,
                                 float fraction, int fillColor, int bgColor) {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float s = (float) (1.0 / gs);

        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        int pcx = (int) Math.round(cx * gs);
        int pcy = (int) Math.round(cy * gs);

        // Draw at two radii for a 2-physical-pixel wide ring
        for (int dr = 0; dr <= 1; dr++) {
            int pr = (int) Math.round((r - dr * 0.5) * gs);
            if (pr <= 0) continue;
            int steps = Math.max(64, pr * 5); // enough steps to hit every pixel once
            for (int i = 0; i < steps; i++) {
                double angle = (i * 2.0 * Math.PI / steps) - Math.PI / 2.0;
                int px = (int) Math.round(pcx + pr * Math.cos(angle));
                int py = (int) Math.round(pcy + pr * Math.sin(angle));
                int col = (i < fraction * steps) ? fillColor : bgColor;
                if ((col >>> 24) == 0) continue;
                g.fill(px, py, px + 1, py + 1, col);
            }
        }

        g.pose().popPose();
    }

    // Shape fill/outline primitives (circle, diamond, hexagon, triangle, star, pentagon, shield,
    // cross), fillPolygon, drawLine, and plot moved to NodeShapeRenderer (client/render) -
    // stateless, no reason to live on the screen. dead sfill() deleted.

    private int secDiv(GuiGraphics g, int x, int y, int pw) {
        g.fill(x, y, x + pw, y + 1, C_BORDER);
        return y + 5;
    }

    private int countDone(List<QuestTask> tasks) {
        int n = 0;
        for (QuestTask t : tasks) if (isTaskDone(t)) n++;
        return n;
    }

    // ── Context menu render ───────────────────────────────────────────────────

    private void renderCtxMenu(GuiGraphics g, int mx, int my) {
        List<CtxItem> items = buildCtxItems();
        int menuH = menuHeight(items);
        int x = ctxX, y = ctxY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // Same missing-flush bug as ChroniclesUIKit.drawDropdown() had: without this, quest node
        // icons underneath (written to the depth buffer at z=100 via g.renderItem()) could still
        // win the depth test against this menu's own z=400 fills and show through it, reading as
        // "the context menu is transparent" - for both the quest menu and the group/chapter one,
        // since they share this exact render path.
        g.flush();

        int ctxAlpha = (int) Math.min(255, (System.currentTimeMillis() - ctxOpenTimeMs) * 255 / OPEN_FADE_MS);
        int fadedBg = (ctxAlpha << 24) | (C_CTX_BG & 0x00FFFFFF);
        g.fill(x + 3, y + 3, x + CTX_W + 3, y + menuH + 3, (Math.min(0x55, ctxAlpha / 3)) << 24);
        g.fill(x, y, x + CTX_W, y + menuH, fadedBg);
        g.fill(x, y, x + CTX_W, y + 1, C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + CTX_W, y + menuH, C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, C_CTX_BORDER);
        g.fill(x + CTX_W - 1, y, x + CTX_W, y + menuH, C_CTX_BORDER);

        int iy = y + 2;
        if (ctxNode != null) {
            g.fill(x + 1, iy, x + 3, iy + CTX_ROW, C_CTX_BORDER);
            g.drawString(font, "§5" + shortName(ctxNode, CTX_W - 12), x + 6, iy + 4, C_CTX_TEXT);
            iy += CTX_ROW;
        }

        boolean moveCatRowHov = false;
        for (CtxItem item : items) {
            if (item.isSep) {
                g.fill(x + 6, iy + 2, x + CTX_W - 6, iy + 3, C_CTX_SEP);
                iy += CTX_SEP;
                continue;
            }
            boolean hov = mx >= x + 1 && mx <= x + CTX_W - 1 && my >= iy && my <= iy + CTX_ROW;
            if (hov) g.fill(x + 1, iy, x + CTX_W - 1, iy + CTX_ROW, C_CTX_HOVER);
            g.drawString(font, (item.isDanger ? "§c" : item.color) + item.label, x + 8, iy + 4,
                    item.isDanger ? C_CTX_DANGER : C_CTX_TEXT);
            if (hov && item.label.contains("Move to Category")) moveCatRowHov = true;
            iy += CTX_ROW;
        }

        // Move-category submenu - opens on hovering its row (see moveCatRowHov above) instead of
        // requiring a click, and caps its height with a scrollbar instead of growing to fit every
        // chapter, which used to run off-screen (and become entirely unreachable past that point)
        // on packs with a lot of categories.
        List<String> cats = buildCategoryList();
        cats.remove("ALL");
        int subX = x + CTX_W + 2;
        int subY = ctxMoveCatY(items);
        int visibleRows = Math.min(cats.size(), CTX_MOVE_CAT_MAX_ROWS);
        int subH = visibleRows * CTX_ROW + 4;
        boolean overSubmenu = ctxMoveCatOpen && mx >= subX && mx <= subX + CTX_W + (cats.size() >
                CTX_MOVE_CAT_MAX_ROWS ? 6 : 0) && my >= subY && my <= subY + subH;
        boolean wasOpen = ctxMoveCatOpen;
        ctxMoveCatOpen = ctxNode != null && (moveCatRowHov || overSubmenu);
        if (ctxMoveCatOpen && !wasOpen) ctxMoveCatScroll = 0;

        if (ctxMoveCatOpen) {
            int maxScroll = Math.max(0, cats.size() - CTX_MOVE_CAT_MAX_ROWS);
            ctxMoveCatScroll = Math.max(0, Math.min(ctxMoveCatScroll, maxScroll));

            g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
            g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
            g.fill(subX, subY, subX + CTX_W, subY + 1, C_CTX_BORDER);
            g.fill(subX, subY + subH - 1, subX + CTX_W, subY + subH, C_CTX_BORDER);
            g.fill(subX, subY, subX + 1, subY + subH, C_CTX_BORDER);
            g.fill(subX + CTX_W - 1, subY, subX + CTX_W, subY + subH, C_CTX_BORDER);

            g.enableScissor(subX, subY, subX + CTX_W, subY + subH);
            int sy = subY + 2;
            for (int i = ctxMoveCatScroll; i < Math.min(cats.size(), ctxMoveCatScroll + visibleRows + 1); i++) {
                String cat = cats.get(i);
                boolean hov = mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW;
                if (hov) g.fill(subX + 1, sy, subX + CTX_W - 1, sy + CTX_ROW, C_CTX_HOVER);
                String mark = cat.equals(ctxNode.getCategory()) ? "§a● " : "§8  ";
                g.drawString(font, mark + "§7" + friendly(cat), subX + 8, sy + 4, C_CTX_TEXT);
                sy += CTX_ROW;
            }
            g.disableScissor();

            // Scrollbar thumb, only when there's more than fits
            if (cats.size() > CTX_MOVE_CAT_MAX_ROWS) {
                int trackH = subH - 4;
                int thumbH = Math.max(10, trackH * visibleRows / cats.size());
                int thumbY = subY + 2 + (maxScroll == 0 ? 0 : (trackH - thumbH) * ctxMoveCatScroll / maxScroll);
                g.fill(subX + CTX_W - 3, subY + 2, subX + CTX_W - 1, subY + subH - 2, 0x33FFFFFF);
                g.fill(subX + CTX_W - 3, thumbY, subX + CTX_W - 1, thumbY + thumbH, 0x99FFFFFF);
            }
        }

        g.flush();
        g.pose().popPose();
    }

    // ── Background rendering ──────────────────────────────────────────────────

    /**
     * Renders the full quest graph (background, nodes, sidebar) without interactive widgets or
     * tooltips. Safe to call from a child screen that overlays its own UI on top.
     * Flushes all batched renders and disables any scissor before returning.
     */
    public void renderForChildScreen(GuiGraphics g) {
        renderingAsBackdrop = true;
        try {
            render(g, -9999, -9999, 0f);
        } finally {
            renderingAsBackdrop = false;
        }
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    /** Renders only the static canvas backdrop — no widgets, no scissors, safe to call from child screens. */
    public void renderBackdrop(GuiGraphics g) {
        g.fill(0, 0, sidebarW(), height, C_PANEL_DARK);
        g.fill(sidebarW(), 0, width, height, C_BG);
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.fill(sidebarW() - 1, 0, sidebarW(), height, C_BORDER);
        CanvasBackgroundRenderer.drawBackground(g, sidebarW(), HEADER_H, width, height, selectedCategory, zoom,
                viewOffX, viewOffY);
    }

    // drawBackground + drawDotGrid/drawGridLines/drawHexGrid/drawHexOutline/drawDiagonalLines/
    // drawCustomBg moved to CanvasBackgroundRenderer (client/render).

    // ── State badge (small corner indicator when node has an icon) ────────────

    private void renderStateBadge(GuiGraphics g, int nx, int ny, int sz, QuestState st) {
        int badgeSz = Math.min(8, Math.max(4, sz / 5));
        int bx = nx + sz - badgeSz - 1, by = ny + sz - badgeSz - 1;
        int bc = switch (st) {
            case COMPLETED -> C_NBORD_DONE;
            case ACTIVE -> C_NBORD_ACTIVE;
            case LOCKED -> C_NBORD_LOCKED;
            default -> 0xFF4488FF;
        };
        NodeShapeRenderer.queueFillRect(g, bx - 1, by - 1, bx + badgeSz + 1, by + badgeSz + 1, 0xAA0B0B0F);
        NodeShapeRenderer.queueFillRect(g, bx, by, bx + badgeSz, by + badgeSz, bc);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private boolean catMatches(QuestNode n) {
        QuestNode.Visibility vis = n.getVisibility();
        // Flag-disabled quests never appear (treated as nonexistent), even in dev mode
        if (n.isFlagDisabled()) return isDevMode; // dev can still see them with a faint marker
        // HIDDEN quests invisible to non-devs until prerequisites satisfied
        if (!isDevMode && vis == QuestNode.Visibility.HIDDEN) {
            if (getState(n) == QuestState.LOCKED) return false;
        }
        // DISABLED quests are always visible (shown grayed out); they are NOT hidden
        // Category filter
        if (!selectedCategory.equals(n.getCategory())) return false;
        // State filter (hard filter — search is soft/dim only via matchesSearch)
        if (!stateFilter.equals("ALL")) {
            QuestState st = getState(n);
            boolean stateMatch = switch (stateFilter) {
                case "AVAILABLE" -> st == QuestState.UNLOCKED;
                case "ACTIVE" -> st == QuestState.ACTIVE;
                case "COMPLETE" -> st == QuestState.COMPLETED;
                case "LOCKED" -> st == QuestState.LOCKED;
                default -> true;
            };
            if (!stateMatch) return false;
        }
        return true;
    }

    /** Returns true if this node matches the current search query (any-word-order, title+id+desc). */
    private boolean matchesSearch(QuestNode n) {
        if (searchWords.length == 0) return true;
        String hay = searchCache.computeIfAbsent(n.getId(), id -> buildSearchHaystack(n));
        for (String word : searchWords) {
            if (!hay.contains(word)) return false;
        }
        return true;
    }

    String buildSearchHaystack(QuestNode n) {
        StringBuilder sb = new StringBuilder();

        // Core text fields (lowercased for case-insensitive search)
        sb.append(n.getTitle().getString().toLowerCase()).append(' ');
        sb.append(n.getId().getPath().replace('_', ' ').toLowerCase()).append(' ');
        sb.append(n.getId().toString().toLowerCase()).append(' ');
        if (!n.getDescription().getString().isEmpty())
            sb.append(n.getDescription().getString().toLowerCase()).append(' ');
        if (n.getSubtitle() != null && !n.getSubtitle().isEmpty()) sb.append(n.getSubtitle().toLowerCase()).append(' ');
        sb.append(n.getCategory().toLowerCase()).append(' ');

        // Tasks — description text + item name + item ID + item tags
        for (QuestTask task : n.getTasks()) {
            sb.append(task.getDescription().getString().toLowerCase()).append(' ');

            ResourceLocation displayId = task.getDisplayItemId();
            if (displayId != null) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(displayId);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    // Item display name
                    sb.append(item.getDescription().getString().toLowerCase()).append(' ');
                    // Item registry path (e.g. "iron_ingot" → "iron ingot")
                    sb.append(displayId.getPath().replace('_', ' ').toLowerCase()).append(' ');
                    sb.append(displayId.toString().toLowerCase()).append(' ');
                    // Item tags
                    try {
                        net.minecraft.core.Registry<net.minecraft.world.item.Item> itemReg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
                        var holder = itemReg.getHolder(itemReg.getId(item));
                        if (holder.isPresent()) {
                            for (var tag : holder.get().tags().toList()) {
                                sb.append(tag.location().getPath().replace('/', ' ').replace('_', ' ').toLowerCase())
                                        .append(' ');
                                sb.append(tag.location().toString().toLowerCase()).append(' ');
                            }
                        }
                    } catch (Exception ignored) {}
                    // Tooltips — use the local player if available so mods see a real player
                    try {
                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft
                                .getInstance().player;
                        var tooltipLines = stack.getTooltipLines(localPlayer,
                                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                        for (int ti = 1; ti < tooltipLines.size(); ti++) { // skip index 0 (display name — already in
                            // haystack)
                            String txt = tooltipLines.get(ti).getString().trim().toLowerCase();
                            if (!txt.isEmpty()) sb.append(txt).append(' ');
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Rewards — item name + ID
        for (QuestReward reward : n.getRewards()) {
            sb.append(reward.getSummary().getString()).append(' ');
            if (reward instanceof QuestReward.ItemReward ir) {
                ResourceLocation rid = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ir.getItem());
                if (rid != null) {
                    sb.append(rid.getPath().replace('_', ' ')).append(' ');
                    sb.append(rid.toString()).append(' ');
                }
            }
        }

        return sb.toString().toLowerCase();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns a list of human-readable warnings about this quest node.
     * Only meaningful in dev mode — called at render time, kept cheap.
     */
    private List<String> getValidationIssues(QuestNode node) {
        return validationCache.computeIfAbsent(node.getId(), id -> computeValidationIssues(node));
    }

    private List<String> computeValidationIssues(QuestNode node) {
        List<String> issues = new ArrayList<>();
        // Link stubs are pure visual pointers to a real quest elsewhere - by design they have
        // no tasks/rewards of their own, so none of the "this quest is incomplete" checks below
        // apply to them. Real packs commonly have many quest_links (imported from FTB), and
        // flagging every single one as broken was the main source of false-positive red badges.
        if (node.isLinkStub()) return issues;
        // No tasks at all
        if (node.getTasks().isEmpty()) issues.add("No tasks defined");
        // No title
        if (node.getTitle().getString().isBlank()) issues.add("Missing title");
        // SNBT file doesn't exist on disk (unsaved / loaded from datapack)
        if (!QuestFileSaver.doesQuestFileExist(node)) issues.add("No editable file on disk (datapack quest)");
        // Check that registered item IDs in item_check tasks are resolvable
        for (QuestTask task : node.getTasks()) {
            if (task instanceof ItemRequirementTask irt) {
                if (irt.getItem() == null || irt.getItem() == net.minecraft.world.item.Items.AIR) {
                    issues.add("Item task has missing/AIR item");
                }
            }
        }
        // Prerequisites that no longer exist
        for (QuestNode prereq : node.getPrerequisites()) {
            if (QuestTreeRegistry.getQuest(prereq.getId()) == null) {
                issues.add("Broken prerequisite: " + prereq.getId().getPath());
            }
        }
        return issues;
    }

    String friendly(String cat) {
        if (cat == null || cat.equals("ALL")) return "All Chapters";
        String resolved = CategoryConfig.getResolvedDisplayName(cat);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : cat.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** friendly(cat), prefixed with "Parent › Grandparent › " for a true sub-chapter. Cycle-safe. */
    private String categoryBreadcrumb(String cat) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cur = cat;
        while (cur != null && !cur.isEmpty() && visited.add(cur)) {
            String parent = CategoryConfig.get(cur).getParentCategory();
            if (parent.isEmpty()) break;
            chain.add(0, parent);
            cur = parent;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : chain) sb.append(friendly(p)).append(" §8›  §7");
        sb.append(friendly(cat));
        return sb.toString();
    }

    private String shortLabel(QuestNode node) {
        String t = node.getTitle().getString();
        int maxW = scaledNodeSize() + 28;
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    private String shortName(QuestNode node, int maxW) {
        String t = node.getTitle().getString();
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    // ── Fit-to-canvas ─────────────────────────────────────────────────────────

    private void fitToCanvas() {
        if (nodeScreenPos.isEmpty()) return;
        int cl = sidebarW(), cr = width;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!catMatches(n)) continue;
            int nx = n.getCustomX() != 0 ? n.getCustomX() : 20;
            int ny = n.getCustomY() != 0 ? n.getCustomY() : 40;
            minX = Math.min(minX, nx);
            minY = Math.min(minY, ny);
            maxX = Math.max(maxX, nx + NODE_SIZE);
            maxY = Math.max(maxY, ny + NODE_SIZE);
        }
        if (minX == Integer.MAX_VALUE) return;
        int canvasW = cr - cl - 20, canvasH = height - HEADER_H - 20;
        int contentW = maxX - minX, contentH = maxY - minY;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.min(
                (float) canvasW / contentW,
                (float) canvasH / contentH)));
        viewOffX = (int) (canvasW / 2f - (minX + contentW / 2f) * posZoom()) + 10;
        viewOffY = (int) (canvasH / 2f - (minY + contentH / 2f) * posZoom()) + 10;
        rebuild();
    }

    // ── Hover tooltip ─────────────────────────────────────────────────────────

    private void renderNodeTooltip(GuiGraphics g, QuestNode node, int mx, int my) {
        // Link stub - show the real quest's tooltip, not the placeholder's empty one.
        QuestNode linkTarget = resolveLinkTarget(node);
        if (linkTarget != null) {
            renderNodeTooltip(g, linkTarget, mx, my);
            return;
        }

        QuestState st = getState(node);
        String title = node.getTitle().getString();
        String sub = node.getSubtitle() != null && !node.getSubtitle().isBlank() ? node.getSubtitle() : null;

        // Task progress summary
        List<QuestTask> tasks = node.getTasks();
        int taskDone = 0, taskTotal = 0;
        List<String> taskLines = new ArrayList<>();
        if (minecraft != null && minecraft.player != null) {
            for (QuestTask t : tasks) {
                if (t.isOptional()) continue;
                taskTotal++;
                boolean done = isTaskDone(t);
                if (done) taskDone++;
                String prog = t.getProgressString(minecraft.player);
                String check = done ? "§a✔ " : "§8✗ ";
                taskLines.add(check + "§7" + t.getDescription().getString() +
                        (prog != null && !prog.isBlank() ? " §8(" + prog + ")" : ""));
            }
        }

        // State line
        String stateLine = switch (st) {
            case COMPLETED -> "§a✔ Complete";
            case ACTIVE -> "§e▶ In progress — " + taskDone + "/" + taskTotal;
            case UNLOCKED -> "§b○ Ready to start";
            case LOCKED -> "§8✕ Locked";
        };

        // Prereqs
        List<String> prereqLines = new ArrayList<>();
        if (!node.getPrerequisites().isEmpty()) {
            for (QuestNode req : node.getPrerequisites()) {
                QuestState rs = getState(req);
                String mark = rs == QuestState.COMPLETED ? "§a✔" : "§8○";
                prereqLines.add("  " + mark + " §8" + req.getTitle().getString());
            }
        }

        // Build tooltip lines
        List<String> lines = new ArrayList<>();
        lines.add("§f" + title);
        if (sub != null) lines.add("§8" + sub);
        lines.add(stateLine);
        if (!taskLines.isEmpty()) {
            lines.add("§8─────────────");
            lines.addAll(taskLines.stream().limit(6).toList());
            if (taskLines.size() > 6) lines.add("§8  … +" + (taskLines.size() - 6) + " more");
        }
        if (!prereqLines.isEmpty() && st == QuestState.LOCKED) {
            lines.add("§8─────────────");
            lines.add("§8Requires:");
            lines.addAll(prereqLines.stream().limit(4).toList());
        }
        // Dev-mode validation warnings
        if (isDevMode) {
            List<String> issues = getValidationIssues(node);
            if (!issues.isEmpty()) {
                lines.add("§8─────────────");
                lines.add("§6⚠ Validation issues:");
                issues.forEach(i -> lines.add("  §e• §7" + i));
            }
        }

        int lineH = font.lineHeight + 2;
        int padH = 6, padW = 8;
        int tipW = lines.stream().mapToInt(font::width).max().orElse(60) + padW * 2;
        int tipH = lines.size() * lineH + padH * 2;

        int tx = mx + 10, ty = my + 12;
        if (tx + tipW > width - 4) tx = mx - tipW - 4;
        if (ty + tipH > height - 4) ty = my - tipH - 4;

        // Node icons render via g.renderItem() translated to z=100, which persists in the depth
        // buffer - a fill drawn later at the default z=0 can still lose the depth test and let
        // those icons show through underneath it. Push above that, and use a fully opaque
        // background (0xFF, not 0xF0) so the canvas behind can't show through either.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        g.fill(tx, ty, tx + tipW, ty + tipH, 0xFF0D0D14);
        g.fill(tx, ty, tx + tipW, ty + 1, C_BORDER_LIT);
        g.fill(tx, ty + tipH - 1, tx + tipW, ty + tipH, C_BORDER_LIT);
        g.fill(tx, ty, tx + 1, ty + tipH, C_BORDER_LIT);
        g.fill(tx + tipW - 1, ty, tx + tipW, ty + tipH, C_BORDER_LIT);
        g.fill(tx, ty, tx + 1, ty + tipH, 0xFF884499); // left accent bar

        int lx = tx + padW, ly = ty + padH;
        for (String line : lines) {
            g.drawString(font, line, lx, ly, 0xFFCCCCDD);
            ly += lineH;
        }
        g.pose().popPose();
    }

    private int[] computeProgress() {
        return computeCategoryProgress(selectedCategory);
    }

    private int[] computeCategoryProgress(String cat) {
        int done = 0, total = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getCategory())) continue;
            if (n.isFlagDisabled()) continue; // flag-disabled = nonexistent, excluded from progress
            // DISABLED visibility still counts toward progress (visible but uncompletable)
            total++;
            // A link stub has no tasks of its own and is trivially "completable" under its OWN
            // id - getState(n) here would read that meaningless stub state instead of asking
            // whether the REAL quest it points to is actually done, which is what "X/Y complete"
            // should mean. getDisplayState() already resolves this correctly for rendering.
            if (getDisplayState(n) == QuestState.COMPLETED) done++;
        }
        return new int[] { done, total };
    }

    /**
     * True if a category has a quest currently ACTIVE (started, in progress) - drives the sidebar's attention badge.
     */
    private boolean computeCategoryHasAttention(String cat) {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getCategory())) continue;
            if (n.isFlagDisabled()) continue;
            if (getDisplayState(n) == QuestState.ACTIVE) return true; // resolve link stubs, same as progress
        }
        return false;
    }

    private List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(test) > maxW && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(test);
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private static int blendColor(int base, int over, float a) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        return 0xFF000000 | ((int) (br + (or - br) * a) << 16) | ((int) (bg + (og - bg) * a) << 8) |
                (int) (bb + (ob - bb) * a);
    }

    private void setFeedback(String msg) {
        feedbackMsg = msg;
        feedbackTimer = 100;
    }

    /** Callback for DependencyLineRenderer's context menu "Dependency line settings…" entry. */
    private void openLineSettingsFor(QuestNode parentNode) {
        if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedCategory, parentNode));
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    /** Called by DepLineSettingsScreen to trigger a line-cache rebuild after per-quest hide toggles. */
    void rebuildFromExternal() {
        rebuild();
    }

    // Every method below used to hand-roll its own TagParser read/mutate/write cycle (or, in
    // deleteQuestFiles' case, its own path resolution) directly in this class. That logic now
    // lives once in QuestFileSaver — the class this screen already relies on for full-registry
    // saves — so these are thin delegates kept only to preserve the call sites and package-visible
    // signatures (saveNodeHideDepLineToDisk/saveNodePrereqsToDisk are used by DepLineSettingsScreen)
    // used throughout this file.

    /** Saves the hide_dep_line flag for a single quest node to its SNBT file. */
    void saveNodeHideDepLineToDisk(QuestNode node) {
        QuestFileSaver.updateHideDepLine(node);
    }

    private void saveNodeToDisk(QuestNode node) {
        QuestFileSaver.updateNodePosition(node);
    }

    private void saveNodeShapeToDisk(QuestNode node, String shape) {
        QuestFileSaver.updateNodeShape(node, shape);
    }

    private void saveNodeCategoryToDisk(QuestNode node, String cat) {
        QuestFileSaver.updateNodeCategory(node, cat);
    }

    void saveNodePrereqsToDisk(QuestNode node) {
        QuestFileSaver.updateNodePrerequisites(node);
    }

    private void saveNodeIconToDisk(QuestNode node) {
        QuestFileSaver.updateNodeIcon(node);
    }

    private void saveNodeIconTextureToDisk(QuestNode node) {
        QuestFileSaver.updateNodeIconTexture(node);
    }

    private void saveNodeShapeTextureToDisk(QuestNode node) {
        QuestFileSaver.updateNodeShapeTexture(node);
    }

    private void deleteQuestFiles(QuestNode node) {
        QuestFileSaver.deleteQuestFiles(node);
    }

    public static FullQuestData loadMarkdownContent(Path mdPath) {
        Component title = Component.empty();
        StringBuilder desc = new StringBuilder();
        // A blank line is a deliberate paragraph break - collapsing every line (blank or not)
        // into one space-joined run is what turned well-formatted multi-paragraph .md bodies
        // (e.g. FTBQ imports, which write one line per source paragraph) into a single text
        // blob. Consecutive non-blank lines still join with a space (soft-wrapped source
        // formatting, not an intentional break); ChronicleRichTextRenderer already renders "\n"
        // as a hard line break.
        boolean pendingParagraphBreak = false;
        try (BufferedReader r = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("# ") && title.getString().isEmpty()) {
                    title = Component.literal(t.substring(2).trim());
                } else if (!t.startsWith("#") && !t.isEmpty()) {
                    if (desc.length() > 0) desc.append(pendingParagraphBreak ? "\n\n" : ' ');
                    desc.append(t);
                    pendingParagraphBreak = false;
                } else if (t.isEmpty()) {
                    pendingParagraphBreak = true;
                }
            }
        } catch (IOException ignored) {}
        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of());
    }

    // ── Unlock path BFS ───────────────────────────────────────────────────────

    private void computeUnlockPath(QuestNode target) {
        unlockPathHighlight.clear();
        // BFS backwards through prerequisites until we hit completed/active nodes
        java.util.Queue<QuestNode> queue = new java.util.LinkedList<>();
        for (QuestNode prereq : target.getPrerequisites()) queue.add(prereq);
        java.util.Set<ResourceLocation> visited = new java.util.HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        PlayerQuestData data = mc.player == null ? null :
                mc.player.getCapability(
                        QuestCapabilityProvider.PLAYER_QUESTS)
                        .orElse(null);
        while (!queue.isEmpty()) {
            QuestNode n = queue.poll();
            if (!visited.add(n.getId())) continue;
            unlockPathHighlight.add(n.getId());
            // Stop following chain once we hit a completed/active node
            if (data != null) {
                QuestState st = data.getQuestState(n.getId(), QuestState.LOCKED);
                if (st == QuestState.COMPLETED || st == QuestState.ACTIVE) continue;
            }
            for (QuestNode req : n.getPrerequisites()) queue.add(req);
        }
    }

    // ── Validation panel ──────────────────────────────────────────────────────

    private void renderValidationPanel(GuiGraphics g, int cl, int cr) {
        // Same fix as renderStatsPanel: node icons render via g.renderItem() at z=100, which
        // persists in the depth buffer - a flat z=0 fill drawn after them can still lose the
        // depth test and show them through regardless of paint order. Push above that z.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        int panW = Math.min(400, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = HEADER_H + 10;
        int panH = height - panY - 10;

        // Must be fully opaque (0xFF, not 0xF0) - the previous near-opaque fill let the quest
        // graph canvas show faintly through behind the whole panel.
        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B12);
        g.fill(panX, panY, panX + panW, panY + 1, 0xFFFF4444);
        g.fill(panX, panY, panX + 1, panY + panH, 0xFFFF4444);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, 0xFFFF4444);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, 0xFFFF4444);

        g.drawString(font, "§cValidation Issues §8(V to close)", panX + 6, panY + 4, 0xFFFF6666, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF333344);

        int vy = panY + 18;
        int maxY = panY + panH - 4;
        boolean any = false;
        g.enableScissor(panX + 2, panY + 16, panX + panW - 2, maxY);
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            List<String> issues = getValidationIssues(node);
            if (issues.isEmpty()) continue;
            any = true;
            if (vy + 11 > maxY) break;
            g.drawString(font, "§e" + node.getTitle().getString() + " §8[" + node.getId().getPath() + "]",
                    panX + 6, vy, 0xFFFFCC44, false);
            vy += 10;
            for (String issue : issues) {
                if (vy + 9 > maxY) break;
                g.drawString(font, "§c  • " + issue, panX + 12, vy, 0xFFFF6666, false);
                vy += 9;
            }
        }
        g.disableScissor(); // pop inner (issue list) scissor
        if (!any) {
            g.drawString(font, "§aNo issues found!", panX + 6, panY + 20, 0xFF44CC88, false);
        }
        g.disableScissor(); // pop outer (whole panel) scissor
        g.pose().popPose();
    }

    // ── Minimap ───────────────────────────────────────────────────────────────

    /** Returns the screen-space bounds of the minimap widget: [x, y, x2, y2]. */
    private int[] minimapBounds(int cr) {
        int mx2 = cr - MM_PAD;
        int my2 = height - MM_PAD;
        return new int[] { mx2 - MM_W, my2 - MM_H, mx2, my2 };
    }

    private void renderMinimap(GuiGraphics g, int mx, int my, int cl, int cr) {
        int[] b = minimapBounds(cr);
        int bx = b[0], by = b[1], bx2 = b[2], by2 = b[3];

        // Background
        g.fill(bx, by, bx2, by2, 0xE0060609);
        // Border
        g.fill(bx, by, bx2, by + 1, 0xFF334);
        g.fill(bx, by2 - 1, bx2, by2, 0xFF334);
        g.fill(bx, by, bx + 1, by2, 0xFF334);
        g.fill(bx2 - 1, by, bx2, by2, 0xFF334);

        int innerW = MM_W - 2, innerH = MM_H - 14;
        int innerX = bx + 1, innerY = by + 13;

        g.drawString(font, "§8Map  §7M", bx + 3, by + 3, 0xFF666677, false);
        g.fill(bx, by + 12, bx2, by + 13, 0xFF222233);

        // Collect all visible nodes for the current category
        Collection<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> catMatches(n) && !n.isFlagDisabled())
                .collect(java.util.stream.Collectors.toList());

        if (nodes.isEmpty()) {
            g.drawCenteredString(font, "§8empty", bx + MM_W / 2, by + MM_H / 2 - 4, 0xFF444455);
            return;
        }

        // Compute canvas bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (QuestNode n : nodes) {
            int nx = n.getCustomX(), ny = n.getCustomY();
            if (nx < minX) minX = nx;
            if (nx > maxX) maxX = nx;
            if (ny < minY) minY = ny;
            if (ny > maxY) maxY = ny;
        }
        int rangeX = Math.max(1, maxX - minX + NODE_SIZE);
        int rangeY = Math.max(1, maxY - minY + NODE_SIZE);
        float scaleX = (float) innerW / rangeX;
        float scaleY = (float) innerH / rangeY;
        float scale = Math.min(scaleX, scaleY) * 0.9f;

        // Centre the content
        int offsetX = innerX + (int) ((innerW - rangeX * scale) / 2);
        int offsetY = innerY + (int) ((innerH - rangeY * scale) / 2);

        g.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);

        // Draw node dots
        for (QuestNode n : nodes) {
            QuestState st = getState(n);
            int col = switch (st) {
                case COMPLETED -> 0xFF00BB66;
                case ACTIVE -> 0xFFBB8800;
                case UNLOCKED -> 0xFF5566CC;
                default -> 0xFF333344;
            };
            int dx = offsetX + (int) ((n.getCustomX() - minX) * scale);
            int dy = offsetY + (int) ((n.getCustomY() - minY) * scale);
            int ds = Math.max(2, (int) (NODE_SIZE * scale));
            g.fill(dx, dy, dx + ds, dy + ds, col);
        }

        // Viewport rectangle
        int canvasW = cr - cl, canvasH = height - HEADER_H;
        float vpLeft = (-viewOffX) / posZoom();
        float vpTop = (-viewOffY) / posZoom();
        float vpRight = vpLeft + canvasW / posZoom();
        float vpBottom = vpTop + canvasH / posZoom();
        int rx1 = offsetX + (int) ((vpLeft - minX) * scale);
        int ry1 = offsetY + (int) ((vpTop - minY) * scale);
        int rx2 = offsetX + (int) ((vpRight - minX) * scale);
        int ry2 = offsetY + (int) ((vpBottom - minY) * scale);
        g.fill(rx1, ry1, rx2, ry1 + 1, 0xFFFFFFAA);
        g.fill(rx1, ry2 - 1, rx2, ry2, 0xFFFFFFAA);
        g.fill(rx1, ry1, rx1 + 1, ry2, 0xFFFFFFAA);
        g.fill(rx2 - 1, ry1, rx2, ry2, 0xFFFFFFAA);

        g.disableScissor();

        // Store state needed for click handling
        minimapRenderState = new int[] { offsetX, offsetY, minX, minY, (int) (scale * 1000) };
    }

    /**
     * Transient state written by renderMinimap, read by mouse handlers. int[5]: offsetX, offsetY, minX, minY,
     * scale*1000
     */
    private int[] minimapRenderState = null;

    private boolean isInMinimap(double x, double y) {
        if (!minimapOpen) return false;
        int[] b = minimapBounds(width);
        return x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
    }

    /** Pans the canvas so the point clicked on the minimap appears at the canvas centre. */
    public void minimapPanTo(double sx, double sy, int cl) {
        if (minimapRenderState == null) return;
        int offsetX = minimapRenderState[0], offsetY = minimapRenderState[1];
        int minX = minimapRenderState[2], minY = minimapRenderState[3];
        float scale = minimapRenderState[4] / 1000f;
        // Invert: canvasCoord = (screenPos - offset) / scale + min
        float cx = (float) (sx - offsetX) / scale + minX;
        float cy = (float) (sy - offsetY) / scale + minY;
        // Centre the viewport on that canvas coord
        int canvasW = width - cl, canvasH = height - HEADER_H;
        viewOffX = (int) (canvasW / 2f - cx * posZoom());
        viewOffY = (int) (canvasH / 2f - cy * posZoom());
    }

    // ── Stats dashboard ───────────────────────────────────────────────────────

    private void renderStatsPanel(GuiGraphics g, int cl, int cr) {
        // Node icons render via g.renderItem() translated to z=100 (so they draw above other
        // canvas elements) - that write persists in the depth buffer, so a flat g.fill() at the
        // default z=0 drawn AFTER them in the same frame can still lose the depth test and let
        // those icons show through, regardless of paint order. Push this whole panel to z=200,
        // above any node icon's z, so it's guaranteed to occlude them.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        int panW = Math.min(480, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = HEADER_H + 10;
        int panH = height - panY - 10;

        // Background + border. Must be fully opaque (0xFF, not 0xF0) - the previous near-opaque
        // fill let the quest graph canvas show faintly through behind the whole panel.
        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B14);
        int bc = 0xFF4488CC;
        g.fill(panX, panY, panX + panW, panY + 1, bc);
        g.fill(panX, panY, panX + 1, panY + panH, bc);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, bc);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, bc);
        g.drawString(font, "§bQuest Stats §8(Shift+V to close)", panX + 6, panY + 4, 0xFF55AAEE, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF222233);

        Collection<QuestNode> all = QuestTreeRegistry.getAllQuests().values();
        int total = all.size();
        int noTask = 0, noReward = 0, orphaned = 0;
        int totalTasks = 0, totalRewards = 0;
        // Per-category map
        java.util.TreeMap<String, int[]> catCounts = new java.util.TreeMap<>(); // [count]
        for (QuestNode n : all) {
            if (n.getTasks().isEmpty()) noTask++;
            if (n.getRewards().isEmpty()) noReward++;
            if (n.getPrerequisites().isEmpty() && n.getChildren().isEmpty()) orphaned++;
            totalTasks += n.getTasks().size();
            totalRewards += n.getRewards().size();
            String cat = n.getCategory() != null ? n.getCategory() : "UNKNOWN";
            catCounts.computeIfAbsent(cat, k -> new int[1])[0]++;
        }

        int sy = panY + 18, lh = 10;
        int col1 = panX + 6, col2 = panX + panW / 2 + 10;

        g.drawString(font, "§fTotal quests:  §e" + total, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fTotal tasks:   §7" + totalTasks, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fNo tasks:      §c" + noTask, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fNo rewards:    §8" + noReward, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fOrphaned:      §e" + orphaned, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fCategories:    §7" + catCounts.size(), col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.fill(panX + 4, sy, panX + panW - 4, sy + 1, 0xFF222233);
        sy += 5;

        // Per-category table
        g.drawString(font, "§8Category", col1, sy, 0xFF666677, false);
        g.drawString(font, "§8Quests", col2, sy, 0xFF666677, false);
        sy += lh;
        g.enableScissor(panX + 2, sy, panX + panW - 2, panY + panH - 4);
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(catCounts.entrySet());
        sorted.sort((a, b2) -> Integer.compare(b2.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, int[]> e : sorted) {
            if (sy + 9 > panY + panH - 4) break;
            int cnt = e.getValue()[0];
            // Mini bar
            int barMaxW = panW / 2 - 20;
            int barW = total > 0 ? (int) ((float) cnt / total * barMaxW) : 0;
            g.fill(col2 - 2, sy, col2 - 2 + barW, sy + 8, 0x334488CC);
            g.drawString(font, "§7" + friendly(e.getKey()), col1, sy, 0xFFAAAAAA, false);
            g.drawString(font, "§f" + cnt, col2, sy, 0xFFCCCCFF, false);
            sy += lh;
        }
        g.disableScissor(); // pop inner (category list) scissor
        g.disableScissor(); // pop outer (whole panel) scissor
        g.pose().popPose();
    }

    @Override
    public void onClose() {
        saveViewForCategory(viewCategoryTracker);
        PhantasiaCompat.closePreview(phantasiaPreview);
        phantasiaPreview = null;
        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }

    private int lastSeenProgressVersion = -1;

    @Override
    public void tick() {
        super.tick();
        // Progress bars are cached per-category; used to clear unconditionally every tick,
        // which meant a full O(quests) rescan per visible category up to 20x/sec even when
        // nothing changed. Only clear when an actual S2C progress sync has landed since we
        // last checked.
        int v = net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket.getVersion();
        if (v != lastSeenProgressVersion) {
            lastSeenProgressVersion = v;
            progressCache.clear();
            attentionCache.clear();
        }
    }

    // ── Tutorial overlay ──────────────────────────────────────────────────────

    /** Finds the first ACTIVE/UNLOCKED quest that has tutorial steps and hasn't been dismissed. */
    private QuestNode findActiveTutorialQuest() {
        if (!TutorialProgressTracker.isInitialized()) {
            Path cfg = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            TutorialProgressTracker.init(cfg);
        }
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (n.getTutorialSteps().isEmpty()) continue;
            String qid = n.getId().getPath();
            if (TutorialProgressTracker.isDismissed(qid)) continue;
            QuestState st = getState(n);
            if (st == QuestState.ACTIVE || st == QuestState.UNLOCKED) return n;
        }
        return null;
    }

    private void renderTutorialOverlay(GuiGraphics g, int mx, int my) {
        tutPrevBtn = null;
        tutNextBtn = null;
        tutSkipBtn = null;

        QuestNode tutQuest = findActiveTutorialQuest();
        if (tutQuest == null) return;

        java.util.List<TutorialStep> steps = tutQuest.getTutorialSteps();
        String qid = tutQuest.getId().getPath();
        int stepIdx = TutorialProgressTracker.getStep(qid);
        if (stepIdx < 0 || stepIdx >= steps.size()) return;
        TutorialStep step = steps.get(stepIdx);

        int cl = sidebarW(), cr = width;

        // ── Spotlight dim ─────────────────────────────────────────────────────
        int hx = 0, hy = 0, hw = 0, hh = 0;
        if (step.hasHighlight()) {
            if (TutorialStep.HL_SIDEBAR.equals(step.highlight())) {
                hx = 0;
                hy = 0;
                hw = sidebarW();
                hh = height;
            } else if (TutorialStep.HL_CANVAS.equals(step.highlight())) {
                hx = cl;
                hy = HEADER_H;
                hw = cr - cl;
                hh = height - HEADER_H;
            } else if (TutorialStep.HL_TOOLBAR.equals(step.highlight())) {
                hx = 0;
                hy = TOOLBAR_Y;
                hw = width;
                hh = TOOLBAR_H;
            } else if (step.isNodeHighlight()) {
                String nid = step.nodeHighlightId();
                if (nid != null) {
                    ResourceLocation rid = new ResourceLocation(PhoenixChronicles.MOD_ID, nid);
                    int[] pos = nodeScreenPos.get(rid);
                    if (pos != null) {
                        int sz = scaledNodeSize();
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
            // Four rectangles around the spotlight
            g.fill(0, 0, width, hy, dimColor);
            g.fill(0, hy + hh, width, height, dimColor);
            g.fill(0, hy, hx, hy + hh, dimColor);
            g.fill(hx + hw, hy, width, hy + hh, dimColor);
            // Glowing border around highlight
            g.fill(hx - 1, hy - 1, hx + hw + 1, hy, C_SEL_ACCENT);
            g.fill(hx - 1, hy + hh, hx + hw + 1, hy + hh + 1, C_SEL_ACCENT);
            g.fill(hx - 1, hy, hx, hy + hh, C_SEL_ACCENT);
            g.fill(hx + hw, hy, hx + hw + 1, hy + hh, C_SEL_ACCENT);
        } else {
            g.fill(0, 0, width, height, dimColor);
        }

        // ── Text box ──────────────────────────────────────────────────────────
        int boxW = Math.min(380, width - 40);
        int boxX = (width - boxW) / 2;

        // Word-wrap the step text
        java.util.List<String> wrappedLines = new java.util.ArrayList<>();
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
        // Position above highlight if it's in the lower half, else below
        int boxY = (hw > 0 && hy + hh > height * 2 / 3) ? hy - boxH - 10 : (hw > 0 ? hy + hh + 10 : height - boxH - 20);
        boxY = Math.max(HEADER_H + 4, Math.min(boxY, height - boxH - 4));

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0E0E18);
        g.fill(boxX, boxY, boxX + boxW, boxY + 1, C_SEL_ACCENT);
        g.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, C_BORDER);
        g.fill(boxX, boxY, boxX + 1, boxY + boxH, C_BORDER);
        g.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, C_BORDER);

        // Step counter
        String counter = "Step " + (stepIdx + 1) + " / " + steps.size();
        g.drawString(font, "§8" + counter, boxX + 10, boxY + 5, C_TEXT_FAINT, false);
        // Quest title
        g.drawString(font, "§7" + tutQuest.getTitle().getString(),
                boxX + boxW - font.width(tutQuest.getTitle().getString()) - 10, boxY + 5, C_TEXT_DIM, false);

        // Text lines
        int ty = boxY + 16;
        for (String line : wrappedLines) {
            g.drawString(font, "§f" + line, boxX + 10, ty, C_TEXT, false);
            ty += 11;
        }

        // ── Navigation buttons ────────────────────────────────────────────────
        int btnY = boxY + boxH - btnRowH - 5;
        int btnH = btnRowH - 2;

        // Skip (right-aligned)
        int skipW = font.width("Skip") + 12;
        int skipX = boxX + boxW - skipW - 6;
        tutSkipBtn = new int[] { skipX, btnY, skipX + skipW, btnY + btnH };
        boolean skipHov = mx >= skipX && mx < skipX + skipW && my >= btnY && my < btnY + btnH;
        g.fill(skipX, btnY, skipX + skipW, btnY + btnH, skipHov ? 0x33FFFFFF : 0x1AFFFFFF);
        g.drawCenteredString(font, "§8Skip", skipX + skipW / 2, btnY + 4, skipHov ? C_TEXT_DIM : C_TEXT_FAINT);

        // Next / Finish
        boolean isLast = stepIdx == steps.size() - 1;
        String nextLabel = isLast ? "§aFinish" : "§fNext →";
        int nextW = font.width(isLast ? "Finish" : "Next →") + 16;
        int nextX = skipX - nextW - 4;
        tutNextBtn = new int[] { nextX, btnY, nextX + nextW, btnY + btnH };
        boolean nextHov = mx >= nextX && mx < nextX + nextW && my >= btnY && my < btnY + btnH;
        g.fill(nextX, btnY, nextX + nextW, btnY + btnH, nextHov ? 0x55006633 : 0x2A006633);
        g.fill(nextX, btnY, nextX + nextW, btnY + 1, nextHov ? C_NBORD_DONE : 0xFF004422);
        g.drawCenteredString(font, nextLabel, nextX + nextW / 2, btnY + 4, nextHov ? C_NBORD_DONE : 0xFF55BB77);

        // Prev (only if not first step)
        if (stepIdx > 0) {
            int prevW = font.width("← Prev") + 12;
            int prevX = boxX + 6;
            tutPrevBtn = new int[] { prevX, btnY, prevX + prevW, btnY + btnH };
            boolean prevHov = mx >= prevX && mx < prevX + prevW && my >= btnY && my < btnY + btnH;
            g.fill(prevX, btnY, prevX + prevW, btnY + btnH, prevHov ? 0x33FFFFFF : 0x1AFFFFFF);
            g.drawCenteredString(font, "§8← Prev", prevX + prevW / 2, btnY + 4, prevHov ? C_TEXT_DIM : C_TEXT_FAINT);
        }
    }

    /** Called from mouseClicked — handles tutorial nav buttons before other handlers. */
    private boolean handleTutorialClick(double mx, double my) {
        if (tutNextBtn == null && tutPrevBtn == null && tutSkipBtn == null) return false;

        QuestNode tutQuest = findActiveTutorialQuest();
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
