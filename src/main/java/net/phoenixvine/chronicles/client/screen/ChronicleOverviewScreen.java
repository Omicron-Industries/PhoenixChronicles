package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import net.phoenixvine.chronicles.client.render.CanvasBackgroundRenderer;
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
    private static final int SIDEBAR_W = 110;
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
    private static final int C_LINE_LOCKED = 0x38FFFFFF;
    private static final int C_LINE_DONE = 0x9900CC66;
    private static final int C_LINE_ACTIVE = 0x88FFAA00;
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
    private QuestNode selectedNode = null;
    /** Last node the mouse hovered during render(), reused by the pin keybind in keyPressed(). */
    private ResourceLocation lastHoveredNodeId = null;
    /** Per-frame counts of which icon path each node took - reset and reported once per frame
     *  in renderNodesAndDetails() so the profiler log can show whether the 3D-vs-flat gate is
     *  actually routing nodes to the cheap path at low zoom. */
    private int dbgFull3DIconCount = 0;
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

    // ── Context menu (pure-render, no hidden buttons) ─────────────────────────
    private static final int CTX_ROW = 16;
    private static final int CTX_SEP = 5;
    private static final int CTX_W = 128;
    private boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    private int ctxX, ctxY;
    private QuestNode ctxNode = null;
    private boolean ctxMoveCatOpen = false;
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

    // ── Canvas caches ─────────────────────────────────────────────────────────
    private final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    private final Map<ResourceLocation, Button> nodeButtons = new LinkedHashMap<>();
    private final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();
    /** Per-category progress cache; invalidated by rebuild(). */
    private final Map<String, int[]> progressCache = new HashMap<>();
    /** Stub category names read from categories.txt; refreshed only on rebuild(). */
    private List<String> stubCategoryCache = null;

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
        super(Component.literal("Chronicle"));
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

        return new ArrayList<>(seen);
    }

    private static final int SIDEBAR_FOLDER_ROW_H = 18;
    private static final int SIDEBAR_CAT_ROW_H = 20;

    /** One row in the sidebar's folder/category list — either a folder header or a category. */
    private record SidebarRow(boolean isFolder, String id, String label, int y, int height, boolean inFolder,
                              boolean collapsed) {}

    /**
     * Single source of truth for sidebar row layout, consumed by both render() and
     * mouseClicked() so their geometry can never drift out of sync with each other.
     */
    private List<SidebarRow> buildSidebarRows() {
        List<SidebarRow> rows = new ArrayList<>();
        List<String> cats = buildCategoryList();
        int y = HEADER_H + 16;
        Set<String> drawnInFolder = new HashSet<>();

        for (var folder : net.phoenixvine.chronicles.registry.ChapterFolderRegistry.getFolders()) {
            List<String> fcats = folder.categories().stream().filter(cats::contains).toList();
            if (fcats.isEmpty()) continue;

            boolean collapsed = net.phoenixvine.chronicles.registry.ChapterFolderRegistry.isCollapsed(folder.id());
            rows.add(new SidebarRow(true, folder.id(), folder.label(), y, SIDEBAR_FOLDER_ROW_H, false, collapsed));
            y += SIDEBAR_FOLDER_ROW_H;

            if (!collapsed) {
                for (String cat : fcats) {
                    rows.add(new SidebarRow(false, cat, friendly(cat), y, SIDEBAR_CAT_ROW_H, true, false));
                    y += SIDEBAR_CAT_ROW_H;
                    drawnInFolder.add(cat);
                }
            } else {
                drawnInFolder.addAll(fcats);
            }
        }

        for (String cat : cats) {
            if (drawnInFolder.contains(cat)) continue;
            rows.add(new SidebarRow(false, cat, friendly(cat), y, SIDEBAR_CAT_ROW_H, false, false));
            y += SIDEBAR_CAT_ROW_H;
        }

        return rows;
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

        QuestGroupManager.invalidate(); // force reload from disk each time the screen opens
        openTimeMs = System.currentTimeMillis();
        rebuild();
    }

    private Path groupsConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    private void rebuild() {
        clearWidgets();
        nodeScreenPos.clear();
        nodeButtons.clear();
    //    lineCache.clear();
        searchCache.clear();
        progressCache.clear();
        stubCategoryCache = null;
        ctxOpen = false;
        ctxMoveCatOpen = false;
        ctxGroup = null;
        newCatBox = null;

        // Load quest groups (reads from disk only if not already loaded)
        QuestGroupManager.load(groupsConfigPath());

        if (minecraft != null && minecraft.player != null) {
            isDevMode = minecraft.player.isCreative() || minecraft.player.hasPermissions(2);
            playerData = minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        }

        int cl = SIDEBAR_W, cr = width;

        // ── Sidebar category tabs ──────────────────────────────────────────────
        // Fully custom-drawn (see buildSidebarRows/renderSidebar/sidebar click handling in
        // mouseClicked) instead of vanilla Button widgets - those used to paint their own gray
        // 9-slice chrome directly over the themed accent/progress-bar row underlay, which made
        // the sidebar look like plain stock buttons next to the rest of this redesigned UI.
        List<String> cats = buildCategoryList();
        if (!cats.isEmpty() && !cats.contains(selectedCategory)) selectedCategory = cats.get(0);

        // ── Sidebar bottom utilities ──────────────────────────────────────────
        // Gear button (all users see it; dev-only actions are inside the screen)
        // Rendered as plain text '⚙' with hover tooltip — no invasive button chrome
        // The actual click is handled in mouseClicked() below

        // "New category" form (dev only) - the "+ Category"/"Cancel" toggle itself is a custom
        // pill (see renderSidebarNewCategoryButton + its click handling), only the text input
        // stays a real EditBox.
        if (isDevMode && newCatFormOpen) {
            newCatBox = new EditBox(font, 4, height - 22, SIDEBAR_W - 8, 14, Component.empty());
            newCatBox.setHint(Component.literal("§8Name, press Enter"));
            newCatBox.setMaxLength(32);
            addRenderableWidget(newCatBox);
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
        Button btn = Button.builder(Component.empty(), b -> onNodeClicked(node))
                .bounds(sx, sy, sz, sz).build();
        btn.setAlpha(0f);
        btn.visible = !offCanvas;
        if (state == QuestState.LOCKED && !isDevMode) btn.active = false;
        addRenderableWidget(btn);
        nodeButtons.put(node.getId(), btn);
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

    /** How far a trimmed line end runs UNDER the node icon's edge (rather than stopping exactly
     *  at it), so the icon's own paint guarantees the connector reads as touching the quest with
     *  no stray subpixel gap. */
    private static final float TRIM_OVERLAP_PX = 2f;

    /** Time for one flowing dependency-line arrow to travel the full visible span of a curve,
     *  looping continuously parent -> child (FTBQ-style), independent of the dash/marching
     *  animation speed setting so arrows always read as "flow", even on locked/done edges. */
    /** Target apparent speed for flowing dependency-line arrows, in screen px/ms - each edge's
     *  actual traversal period is derived from this against its own trimmed length instead of
     *  sharing one fixed period, so short and long edges read as moving at the same rate. */
    private static final float ARROW_SPEED_PX_PER_MS = 0.15f;

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
        int canvasW = width - SIDEBAR_W;
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
        int leftBound   = 220; // Matches SIDEBAR_W
        int rightBound  = this.width;
        int topBound    = 40;  // Matches HEADER_H
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
                    col = ps == QuestState.COMPLETED ? DependencyLineRenderer.C_LINE_DONE :
                            ps == QuestState.ACTIVE ? DependencyLineRenderer.C_LINE_ACTIVE : DependencyLineRenderer.C_LINE_LOCKED;
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
                    col = prereqState == QuestState.COMPLETED ? DependencyLineRenderer.C_LINE_DONE :
                            prereqState == QuestState.ACTIVE ? DependencyLineRenderer.C_LINE_ACTIVE : DependencyLineRenderer.C_LINE_LOCKED;
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
        List<String> savedStubs = stubCategoryCache;
        rebuild();
        progressCache.putAll(savedProgress);
        stubCategoryCache = savedStubs;
    }

    /**
     * Panning fast-path: shifts all existing node buttons by (dx,dy) without
     * tearing down and recreating every widget. Much cheaper than rebuild().
     */
    private void panCanvas(int dx, int dy) {
        int cl = SIDEBAR_W, cr = width;
        int sz = scaledNodeSize();
        for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
            Button btn = e.getValue();
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
        if (key == 80 && ctrl) {
            FrameProfiler.setEnabled(!FrameProfiler.isEnabled());
            setFeedback(FrameProfiler.isEnabled() ? "§aProfiler ON" : "§7Profiler OFF");
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
            rebuild();
            setFeedback("Duplicated → " + newPath);
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
        int cl = SIDEBAR_W, cr = width;
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
        if (btn == 0 && handleTutorialClick(mx, my)) return true;

        // Minimap click — pan canvas to clicked point
        if (btn == 0 && isInMinimap(mx, my)) {
            mmDragging = true;
            minimapPanTo(mx, my, SIDEBAR_W);
            softRebuild();
            return true;
        }

        int cl = SIDEBAR_W, cr = width;

        if (btn == 0) {
            // Inspector removed from overview — all quest detail interactions now in QuestTasksScreen

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

            // ── Gear (utilities) click — left=open editor, right=export lang ──
            if (gearHovered((int) mx, (int) my) && minecraft != null) {
                minecraft.setScreen(new LangEditorScreen(this));
                return true;
            }

            // ── Sidebar "+ Category" / "Cancel" pill ──────────────────────────
            if (newCatButtonHovered((int) mx, (int) my)) {
                newCatFormOpen = !newCatFormOpen;
                rebuild();
                return true;
            }

            // ── Sidebar folder headers / category rows ────────────────────────
            if (mx < SIDEBAR_W - 1 && my >= HEADER_H + 14) {
                for (SidebarRow row : buildSidebarRows()) {
                    if (my < row.y() || my >= row.y() + row.height()) continue;
                    if (row.isFolder()) {
                        net.phoenixvine.chronicles.registry.ChapterFolderRegistry.toggleCollapsed(row.id());
                        rebuild();
                    } else {
                        selectedCategory = row.id();
                        selectedNode = null;
                        PhantasiaCompat.closePreview(phantasiaPreview);
                        phantasiaPreview = null;
                        viewOffX = 0;
                        viewOffY = 0;
                        ctxOpen = false;
                        ctxMoveCatOpen = false;
                        rebuild();
                    }
                    return true;
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

        // ── Bulk-ops panel clicks ─────────────────────────────────────────────
        if (btn == 0 && isDevMode && multiSelection.size() >= 2) {
            int bx = cl + 4, by = HEADER_H + 4;
            int bh = 38;
            if ((int) mx >= bx && (int) mx <= bx + 360 && (int) my >= by && (int) my <= by + bh) {
                // Shape picker row hit-test
                String[] shapeIds = { "SQUARE", "CIRCLE", "DIAMOND", "HEXAGON", "TRIANGLE", "STAR", "PENTAGON",
                        "SHIELD", "CROSS" };
                int slotW = 14, startX = bx + 6, slotY = by + 24;
                for (int i = 0; i < shapeIds.length; i++) {
                    int sx = startX + i * (slotW + 2);
                    if ((int) mx >= sx && (int) mx < sx + slotW && (int) my >= slotY && (int) my < slotY + 12) {
                        String newShape = shapeIds[i];
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

        // ── Context menu ──────────────────────────────────────────────────────
        if (ctxOpen && btn == 0) {
            if (handleCtxClick((int) mx, (int) my)) return true;
            ctxOpen = false;
            ctxMoveCatOpen = false;
            return true;
        }

        // ── Ctrl + left-click = toggle multi-select (dev mode) ───────────────
        if (btn == 0 && isDevMode && hasControlDown() && !hasShiftDown()) {
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
        }

        // ── Shift + right-click = open quest directly ─────────────────────────
        if (btn == 1 && hasShiftDown() && mx > cl && mx < cr) {
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
            QuestNode hit = null;
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    hit = QuestTreeRegistry.getQuest(e.getKey());
                    break;
                }
            }
            QuestGroup hitGrp = (hit == null) ? groupAtLabelBar(mx, my, cl) : null;
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
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
            boolean handled = super.mouseClicked(mx, my, btn);
            if (!handled) {
                if (isDevMode && minecraft != null) {
                    long now = System.currentTimeMillis();
                    int imx = (int) mx, imy = (int) my;
                    if (now - lastCanvasClickTime < 350 && Math.abs(imx - lastCanvasClickX) < 10 &&
                            Math.abs(imy - lastCanvasClickY) < 10) {
                        // Double-click on empty canvas → open creator pre-positioned
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
            for (int i = 0; i < cats.size(); i++) {
                int ry = subY + i * CTX_ROW;
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
        // Minimap drag — pan canvas as user drags over the minimap
        if (btn == 0 && mmDragging) {
            minimapPanTo(mx, my, SIDEBAR_W);
            return true;
        }
        if (btn == 0 && linkDragSource != null) {
            linkDragX = (int) mx;
            linkDragY = (int) my;
            return true;
        }
        if (btn == 0) {
            if (draggedGroup != null) {
                int cl = SIDEBAR_W;
                int screenX = (int) mx - groupDragGrabX;
                int screenY = (int) my - groupDragGrabY;
                draggedGroup.setX((int) ((screenX - cl - viewOffX) / posZoom()));
                draggedGroup.setY((int) ((screenY - HEADER_H - viewOffY) / posZoom()));
                return true;
            }
            if (draggedNode != null) {
                int cl = SIDEBAR_W;
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
                Button b = nodeButtons.get(draggedNode.getId());
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
        if (btn == 0 && mmDragging) {
            mmDragging = false;
            return true;
        }
        if (btn == 0 && linkDragSource != null) {
            QuestNode src = linkDragSource;
            linkDragSource = null;
            for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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

        // New quest (only on empty canvas, not on existing quest/group)
        if (!hasNode && !hasGroup) {
            items.add(new CtxItem("+ New quest", "§a", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new QuestCreatorScreen(this));
                    }));
        }

        // Dependency lines (empty canvas — always shown for all right-click contexts)
        if (!hasNode && !hasGroup) {
            final String cat = selectedCategory;
            items.add(new CtxItem("Dependency lines…", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new DepLineSettingsScreen(this, cat));
                    }));
        }

        // Chain-wire ops (multi-select, empty canvas right-click)
        if (!hasNode && !hasGroup && multiSelection.size() >= 2) {
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
        if (!hasNode && !hasGroup && isDevMode) {
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
            items.add(new CtxItem((testMode ? "§c⏵ Exit test mode" : "⏵ Enter test mode"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        testMode = !testMode;
                        if (!testMode) testModeData = new PlayerQuestData();
                        rebuild();
                    }));
            if (testMode) {
                items.add(new CtxItem("↺ Reset test data", "§7", false, false,
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
                        if (subgraphMode) rebuildSubgraph();
                    }));
            items.add(new CtxItem((statsOpen ? "§b∑ Hide stats" : "∑ Show stats"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        statsOpen = !statsOpen;
                        if (statsOpen) validationOpen = false;
                    }));
        }

        // Group creation & theme (only when right-clicking empty canvas, not on a node/group)
        if (!hasNode && !hasGroup) {
            int cl = SIDEBAR_W;
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
            items.add(CtxItem.sep());
            items.add(new CtxItem("⊞ Auto-arrange chapter", "§e", false, false,
                    () -> {
                        ctxOpen = false;
                        autoArrangeCategory();
                    }));
        }

        // Group editing (when right-clicking a group label bar)
        if (hasGroup) {
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
            items.add(new CtxItem("Edit quest", "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new QuestCreatorScreen(this, ctxNode));
                    }));
            items.add(new CtxItem("Edit tasks / rewards", "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new TaskRewardEditorScreen(this, ctxNode));
                    }));
            items.add(new CtxItem("Edit texts…", "§d", false, false,
                    () -> {
                        final QuestNode target = ctxNode;
                        ctxOpen = false;
                        minecraft.setScreen(new LangEditorScreen(this, target));
                    }));
            items.add(new CtxItem("Set icon item…", "§7", false, false,
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
            items.add(new CtxItem("Set icon texture…", "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                            ctxNode.setIconTexture(rl);
                            saveNodeIconTextureToDisk(ctxNode);
                            setFeedback("Icon texture → " + rl);
                            rebuild();
                        }));
                    }));
            items.add(new CtxItem("Clear icon", "§8", false, false,
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
            items.add(new CtxItem("Move to category  ▸", "§7", false, false,
                    () -> ctxMoveCatOpen = !ctxMoveCatOpen));
            items.add(CtxItem.sep());
            items.add(new CtxItem("Shift+drag to move", "§8", false, false,
                    () -> {
                        ctxOpen = false;
                        setFeedback("Shift-click and drag the node");
                    }));
            items.add(CtxItem.sep());
            items.add(new CtxItem("Dependency lines…", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        final String cat = selectedCategory;
                        minecraft.setScreen(new DepLineSettingsScreen(this, cat, ctxNode));
                    }));
            items.add(CtxItem.sep());
            items.add(new CtxItem("Copy quest §8(Ctrl+C)", "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        questCopy(ctxNode);
                    }));
            items.add(new CtxItem("Duplicate quest §8(Ctrl+D)", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        duplicateQuest(ctxNode);
                    }));
            items.add(new CtxItem("Force complete (dev)", "§e", false, false,
                    () -> {
                        final QuestNode target = ctxNode;
                        ctxOpen = false;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            // Send to server so state persists and cascade unlocks fire
                            mc.player.connection.sendCommand("chronicles complete " + target.getId().getPath());
                            setFeedback("Force-completed: " + target.getTitle().getString());
                        }
                    }));
            items.add(new CtxItem("Delete quest", "§c", false, true,
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

    private int ctxMoveCatY(List<CtxItem> items) {
        int y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW; // skip title row
        for (CtxItem item : items) {
            if (!item.isSep && item.label.contains("Move to category")) return y;
            y += item.isSep ? CTX_SEP : CTX_ROW;
        }
        return y;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        FrameProfiler.begin("TOTAL render()");
        if (feedbackTimer > 0) feedbackTimer--;

        // 1. Flush accumulated viewport panning inputs
        if (pendingPanDX != 0 || pendingPanDY != 0) {
            panCanvas(pendingPanDX, pendingPanDY);
            pendingPanDX = 0;
            pendingPanDY = 0;
        }

        // 2. Handle interactive transformations (Optimized: No line-cache rebuilding here!)
        handleLiveDragging(mx, my);

        int cl = SIDEBAR_W;
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

        FrameProfiler.end("TOTAL render()");
        FrameProfiler.endFrame();
        if (FrameProfiler.isEnabled()) renderProfilerPanel(g);
    }

    private void handleLiveDragging(int mx, int my) {
        if (draggedNode == null) return;

        int cl2 = SIDEBAR_W;
        int logX = (int) ((mx - dragGrabX - cl2 - viewOffX) / posZoom());
        int logY = (int) ((my - dragGrabY - HEADER_H - viewOffY) / posZoom());
        int snap2 = hasShiftDown() ? 1 : gridSnap;

        logX = Math.round((float) logX / snap2) * snap2;
        logY = Math.round((float) logY / snap2) * snap2;

        int nx = (int) (logX * posZoom()) + cl2 + viewOffX;
        int ny = (int) (logY * posZoom()) + HEADER_H + viewOffY;

        Button b = nodeButtons.get(draggedNode.getId());
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
        g.fill(0, 0, SIDEBAR_W, height, C_PANEL_DARK);
        g.fill(cl, 0, cr, height, C_BG);
        g.fill(cr, 0, width, height, C_PANEL_DARK);
        g.fill(cr, 0, cr + 1, height, C_BORDER);

        // Title bar text and decoration
        g.fill(0, 0, width, TOOLBAR_Y, C_HEADER);
        g.fill(0, TOOLBAR_Y - 1, width, TOOLBAR_Y, C_BORDER);
        String titlePrefix = testMode ? "§c⏵ TEST  §8⟫  §7" : "§8Chronicle  §8⟫  §7";
        g.drawString(font, titlePrefix + friendly(selectedCategory), cl + 8, 7, C_TEXT);
        if (testMode) g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFCC2222);

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

        // Toolbar field region
        g.enableScissor(0, TOOLBAR_Y, width, HEADER_H);
        renderToolbar(g, mx, my, cl, cr);
        g.disableScissor();
    }

    private void renderSidebarPanel(GuiGraphics g, int mx, int my) {
        g.enableScissor(0, HEADER_H, SIDEBAR_W - 1, height);
        g.fill(0, HEADER_H, SIDEBAR_W - 1, HEADER_H + 14, C_PANEL_DARK);
        g.drawCenteredString(font, "§8CHAPTERS", SIDEBAR_W / 2, HEADER_H + 3, C_TEXT_FAINT);
        g.fill(0, HEADER_H + 13, SIDEBAR_W - 1, HEADER_H + 14, C_BORDER);

        FrameProfiler.begin("sidebar");
        List<SidebarRow> sidebarRows = buildSidebarRows();
        int barW = SIDEBAR_W - 10;
        for (SidebarRow row : sidebarRows) {
            if (row.isFolder()) renderSidebarFolderRow(g, row, mx, my);
            else renderSidebarCatRow(g, row, barW, mx, my);
        }
        FrameProfiler.end("sidebar");

        if (sidebarRows.isEmpty()) {
            g.drawCenteredString(font, "§8No categories", SIDEBAR_W / 2, HEADER_H + 28, C_TEXT_FAINT);
            g.drawCenteredString(font, "§8Right-click canvas", SIDEBAR_W / 2, HEADER_H + 40, C_TEXT_FAINT);
            g.drawCenteredString(font, "§8to add one", SIDEBAR_W / 2, HEADER_H + 52, C_TEXT_FAINT);
        }

        g.disableScissor();
        g.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, height, C_BORDER);
    }

    private void renderCanvasLayers(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("background");
        CanvasBackgroundRenderer.drawBackground(g, cl, HEADER_H, cr, height, selectedCategory, zoom, viewOffX, viewOffY);
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
        for (Map.Entry<ResourceLocation, Button> e : nodeButtons.entrySet()) {
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
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            Button btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            renderNode(g, node, pos[0], pos[1], sz, btn.isMouseOver(mx, my), node == selectedNode);
        }
        FrameProfiler.setCounter("full3DIcons", dbgFull3DIconCount);
        FrameProfiler.end("node visuals");

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

        // Subgraph isolated display rule opacity layer
        if (subgraphMode && selectedNode != null && !subgraphNodes.isEmpty()) {
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                if (subgraphNodes.contains(entry.getKey())) continue;
                int[] pos = entry.getValue();
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                int nsz = node != null ? scaledNodeSize(node) : sz;
                g.fill(pos[0] - 1, pos[1] - 1, pos[0] + nsz + 1, pos[1] + nsz + 1, 0xCC000000);
            }
        }

        FrameProfiler.end("dev overlays");

        FrameProfiler.begin("badges/labels");
        // Notifications, claim status badges, and dynamic alpha context names
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            Button btn = nodeButtons.get(node.getId());
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

        renderSidebarNewCategoryButton(g, mx, my);
        renderSidebarGear(g, mx, my);

        if (!renderingAsBackdrop) renderTutorialOverlay(g, mx, my);

        // Context Tooltip detection frame handler
        if (!renderingAsBackdrop && draggedNode == null && !ctxOpen) {
            ResourceLocation nowHoverId = null;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                Button btn = nodeButtons.get(node.getId());
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
        int panelW = 220;
        int rowH = 11;
        int panelH = 20 + sections.size() * rowH + 6;
        int px = width - panelW - 4;
        int py = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);
        g.fill(px, py, px + panelW, py + panelH, 0xEE0D0D12);
        g.fill(px, py, px + panelW, py + 1, 0xFF00AA55);
        g.drawString(font, "§aProfiler §8(Ctrl+P to close)", px + 5, py + 4, 0xFFDDDDDD, false);

        double maxMs = sections.isEmpty() ? 1.0 : sections.get(0).getValue();
        int y = py + 16;
        for (var entry : sections) {
            double ms = entry.getValue();
            // Green → yellow → red as a section's cost approaches the most expensive one this frame
            float frac = maxMs > 0 ? (float) (ms / maxMs) : 0;
            int barColor = frac > 0.66f ? 0xFFFF5555 : frac > 0.33f ? 0xFFFFAA33 : 0xFF55CC77;
            int barW = (int) (frac * (panelW - 90));
            g.fill(px + 5, y + 1, px + 5 + Math.max(1, barW), y + rowH - 2, barColor);
            g.drawString(font, entry.getKey(), px + 5, y + 1, 0xFF888898, false);
            String msStr = String.format("%.2fms", ms);
            g.drawString(font, msStr, px + panelW - font.width(msStr) - 5, y + 1, 0xFFCCCCCC, false);
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
        int gy = gearY();
        return mx >= SIDEBAR_W - GEAR_SIZE - 4 && mx < SIDEBAR_W - 4 && my >= gy && my < gy + GEAR_SIZE;
    }

    private int newCatBtnY() {
        return height - (newCatFormOpen ? 38 : 22);
    }

    /** Custom pill matching the toolbar's pill style, replacing the old vanilla Button. */
    private void renderSidebarNewCategoryButton(GuiGraphics g, int mx, int my) {
        if (!isDevMode) return;
        int x = 4, y = newCatBtnY(), w = SIDEBAR_W - 24, h = 14;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0x33FFFFFF : 0x1AFFFFFF);
        g.fill(x, y, x + w, y + 1, hov ? C_BORDER_LIT : C_BORDER);
        g.drawCenteredString(font, newCatFormOpen ? "§8– Cancel" : "§a+ Category", x + w / 2, y + 3, C_TEXT);
    }

    private boolean newCatButtonHovered(int mx, int my) {
        if (!isDevMode) return false;
        int x = 4, y = newCatBtnY(), w = SIDEBAR_W - 24, h = 14;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Folder header row: chevron + label, subtly differentiated background from category rows. */
    private void renderSidebarFolderRow(GuiGraphics g, SidebarRow row, int mx, int my) {
        int y = row.y(), h = row.height();
        boolean hov = mx >= 0 && mx < SIDEBAR_W - 1 && my >= y && my < y + h;
        g.fill(0, y, SIDEBAR_W - 1, y + h, hov ? 0xFF1C1C24 : 0xFF15151B);
        g.fill(0, y + h - 1, SIDEBAR_W - 1, y + h, C_BORDER);
        String arrow = row.collapsed() ? "▶" : "▼";
        g.drawString(font, "§8" + arrow + " §7" + row.label(), 4, y + (h - 8) / 2, hov ? C_TEXT_DIM : C_TEXT_FAINT,
                false);
    }

    /** Category row: selection accent, hover highlight, label, done/total count, progress bar. */
    private void renderSidebarCatRow(GuiGraphics g, SidebarRow row, int barW, int mx, int my) {
        String cat = row.id();
        int y = row.y(), h = row.height();
        int indent = row.inFolder() ? 10 : 4;
        int catAccent = CAT_ACCENTS[Math.abs(cat.hashCode()) % CAT_ACCENTS.length];
        boolean isSel = cat.equals(selectedCategory);
        boolean hov = mx >= 0 && mx < SIDEBAR_W - 1 && my >= y && my < y + h;

        if (isSel) {
            g.fill(0, y, SIDEBAR_W - 1, y + h, C_SEL_TAB);
            g.fill(0, y, 3, y + h, catAccent);
        } else {
            if (hov) g.fill(0, y, SIDEBAR_W - 1, y + h, 0x14FFFFFF);
            g.fill(0, y + h / 2 - 4, 2, y + h / 2 + 4, (catAccent & 0x00FFFFFF) | 0x66000000);
        }

        int[] p = progressCache.computeIfAbsent(cat, this::computeCategoryProgress);
        String countStr = p[1] > 0 ? p[0] + "/" + p[1] : "";
        int countW = countStr.isEmpty() ? 0 : font.width(countStr) + 4;

        String label = row.label();
        int labelMaxW = SIDEBAR_W - indent - 4 - countW;
        if (font.width(label) > labelMaxW)
            label = font.plainSubstrByWidth(label, Math.max(0, labelMaxW - 6)) + "…";
        g.drawString(font, (isSel ? "§f" : "§8") + label, indent, y + 4, isSel ? C_TEXT : C_TEXT_DIM, false);

        if (p[1] > 0) {
            int countColor = (p[0] == p[1]) ? C_PROG_FILL : (p[0] > 0 ? C_PROG_ACT : C_TEXT_FAINT);
            g.drawString(font, "§8" + countStr, SIDEBAR_W - font.width(countStr) - 5, y + 4, countColor);
            int fill = (int) ((float) p[0] / p[1] * barW);
            g.fill(5, y + h - 6, 5 + barW, y + h - 5, 0x22FFFFFF);
            int barColor = (p[0] == p[1]) ? C_PROG_FILL :
                    (isSel ? catAccent : (p[0] > 0 ? C_PROG_ACT : 0x22FFFFFF));
            if (fill > 0) g.fill(5, y + h - 6, 5 + fill, y + h - 5, barColor);
        }
    }

    private void renderSidebarGear(GuiGraphics g, int mx, int my) {
        int gx = SIDEBAR_W - GEAR_SIZE - 4;
        int gy = gearY();
        boolean hov = gearHovered(mx, my);

        // Subtle separator above utilities area
        g.fill(4, gy - 6, SIDEBAR_W - 4, gy - 5, C_BORDER);

        // Gear glyph
        int col = hov ? 0xFFDDDDE8 : 0xFF555566;
        g.drawString(font, "⚙", gx + 1, gy + 1, col, false);

        if (hov) {
            // Tooltip panel
            int ttW = 200;
            int ttH = isDevMode ? 64 : 30;
            int ttX = gx - ttW - 4;
            int ttY = gy - ttH - 2;
            if (ttX < 2) ttX = 2;
            g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xFF1A1A24);
            g.fill(ttX, ttY, ttX + ttW, ttY + 1, C_BORDER);
            g.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, C_BORDER);
            g.fill(ttX, ttY, ttX + 1, ttY + ttH, C_BORDER);
            g.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, C_BORDER);
            g.drawString(font, "§dUtilities", ttX + 5, ttY + 4, C_TEXT, false);
            g.drawString(font, "§8§oLeft-click§r§8: Edit all quest texts", ttX + 5, ttY + 14, C_TEXT_DIM, false);
            if (isDevMode) {
                g.drawString(font, "§8§oRight-click§r§8: Export lang/en_us.json", ttX + 5, ttY + 24, C_TEXT_DIM, false);
                g.drawString(font, "§8§o[I]§r§8: Import FTB Quests chapter", ttX + 5, ttY + 34, C_TEXT_DIM, false);
                g.drawString(font, "§8(place .snbt in ftb_import/ folder)", ttX + 5, ttY + 44, C_TEXT_FAINT, false);
                g.drawString(font, "§8(pack's en_us.json also goes there)", ttX + 5, ttY + 54, C_TEXT_FAINT, false);
            }
        }
    }

    // ── Bulk-ops panel ────────────────────────────────────────────────────────

    private void renderBulkOpsPanel(GuiGraphics g, int mx, int my, int cl, int cr) {
        // Same z-ordering/opacity issue as the stats and tooltip panels: node icons render at
        // z=100 via g.renderItem(), so this needs to sit above that and be fully opaque or the
        // canvas behind it can show through.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);

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
        String[] glyphs = { "■", "●", "◆", "⬡", "▲", "★", "⬠", "❖", "✚" };
        String[] shapeIds = { "SQUARE", "CIRCLE", "DIAMOND", "HEXAGON", "TRIANGLE", "STAR", "PENTAGON", "SHIELD",
                "CROSS" };
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

    private void renderNode(GuiGraphics g, QuestNode node, int x, int y, int sz,
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

        String shape = node.getShapeType() != null ? node.getShapeType().toUpperCase() : "SQUARE";

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
                default -> g.fill(x + 2, y + 2, x + sz + 2, y + sz + 2, 0x44000000);
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
            default -> {  // SQUARE
                g.fill(x, y, x + sz, y + sz, fill);
                g.fill(x, y, x + sz, y + thickness, border);
                g.fill(x, y + sz - thickness, x + sz, y + sz, border);
                g.fill(x, y, x + thickness, y + sz, border);
                g.fill(x + sz - thickness, y, x + sz, y + sz, border);
            }
        }

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

        // Icon: try custom PNG first, then a picked texture, then scaled item, then state glyph
        String questPath = displaySource.getId().getPath();
        ResourceLocation customIcon = QuestIconCache.get(questPath);
        ResourceLocation pickedTexture = null;
        if (customIcon == null && !displaySource.getIconTexture().isEmpty()) {
            try {
                pickedTexture = new ResourceLocation(displaySource.getIconTexture());
            } catch (Exception ignored) {}
        }
        if (customIcon != null && sz >= 8) {
            int[] dims = QuestIconCache.getDimensions(questPath);
            int pad = Math.max(2, sz / 8);
            int iconSz = sz - pad * 2;
            g.blit(customIcon, x + pad, y + pad, 0, 0, iconSz, iconSz, dims[0], dims[1]);
            if (sz >= 20) renderStateBadge(g, x, y, sz, st);
        } else if (pickedTexture != null && sz >= 8) {
            int pad = Math.max(2, sz / 8);
            int iconSz = sz - pad * 2;
            g.blit(pickedTexture, x + pad, y + pad, 0, 0, iconSz, iconSz, iconSz, iconSz);
            if (sz >= 20) renderStateBadge(g, x, y, sz, st);
        } else {
            Item icon = displaySource.getIconItem();
            if (icon == null) icon = fallbackTaskIcon(displaySource);
            if (icon != null && icon != Items.AIR && sz >= 6) {
                // Reverted off-screen render-to-texture caching (ItemIconRenderCache) after two
                // rounds of visible corruption (noisy/garbled icons) that didn't resolve cleanly
                // - that path touches real GL framebuffer/projection state I can't verify without
                // actually running the client, and shipping broken visuals isn't worth the perf
                // win. Back to the plain, vanilla-proven full 3D render per node for correctness;
                // the node-visuals cost this brings back is a separate follow-up.
                float scale = sz / 16f * 0.75f;
                float cx = x + sz / 2f, cy = y + sz / 2f;
                g.pose().pushPose();
                g.pose().translate(cx, cy, 100f);
                g.pose().scale(scale, scale, scale);
                g.renderItem(new ItemStack(icon), -8, -8);
                g.pose().popPose();
                renderStateBadge(g, x, y, sz, st);
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
            }
        }

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


    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick) {
        drawBezierLine(g, x1, y1, x2, y2, color, style, animTick, null, null, null, null);
    }


    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick,
                                QuestChroniclesSettings.LineStyle shapeOverride,
                                QuestChroniclesSettings.LineVisualStyle visualOverride,
                                QuestChroniclesSettings.LineAnimSpeed speedOverride,
                                Boolean arrowOverride) {
        QuestChroniclesSettings settings = QuestChroniclesSettings.get();
        boolean spline = (shapeOverride != null ? shapeOverride : settings.getLineStyle()) ==
                QuestChroniclesSettings.LineStyle.SPLINE;
        QuestChroniclesSettings.LineVisualStyle vis = visualOverride != null ? visualOverride :
                settings.getLineVisualStyle();
        long speedDiv = (speedOverride != null ? speedOverride : settings.getLineAnimSpeed()).divisor;
        boolean showArrow = arrowOverride != null ? arrowOverride : settings.isShowLineArrows();
        drawBezierLine(g, x1, y1, x2, y2, color, style, animTick, spline, vis, speedDiv, showArrow);
    }

    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick, boolean spline,
                                QuestChroniclesSettings.LineVisualStyle vis, long speedDiv, boolean showArrow) {
        // Cleanly cast inputs to local GUI floats to prevent rounding gaps
        float xa = (float) x1;
        float ya = (float) y1;
        float xb = (float) x2;
        float yb = (float) y2;

        // Calculate elegant control points for the S-curve directly in GUI space
        float adx = Math.abs(xb - xa), ady = Math.abs(yb - ya);
        float cp1x, cp1y, cp2x, cp2y;
        if (!spline) {
            cp1x = xa; cp1y = ya; cp2x = xb; cp2y = yb;
        } else if (adx >= ady) {
            float mx = (xa + xb) / 2f;
            cp1x = mx; cp1y = ya; cp2x = mx; cp2y = yb;
        } else {
            float my = (ya + yb) / 2f;
            cp1x = xa; cp1y = my; cp2x = xb; cp2y = yb;
        }

        float dist = (float) Math.sqrt(adx * adx + ady * ady);
        if (dist < 0.5f) return;

        // Fixed step allocation: Ensures steps map to physical length so dashes never stretch
        int steps = Math.min(Math.max(16, (int) (dist / 4.0f)), 64);

        // Unpack colors efficiently
        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        // Base thickness definitions in GUI space
        float baseCoreW = switch (vis) {
            case THIN -> 0.4f;
            case BOLD -> 1.2f;
            case THICK -> 1.8f;
            case WIDE -> 2.6f;
            case GLOW -> 0.9f;
            default -> 0.75f; // NORMAL
        };

        // FTB QUESTS VISIBILITY MECHANIC:
        // As you zoom out, lines naturally look thinner. We dynamically increase their width
        // in GUI space here so they never drop below a readable, high-contrast physical thickness.
        // Grows at low zoom so lines stay readable, but capped hard - uncapped 1/zoom growth is
        // what turned these into ~14px-wide opaque wedges at 12% zoom, and several nodes'
        // diverging straight lines sharing a source read as one solid filled shape instead of
        // distinct lines once they got that wide.
        float currentZoom = Math.max(0.05f, zoom);
        float coreHalfW = Math.max(baseCoreW, Math.min(1.4f, 0.4f / currentZoom));
        float outlineHalfW = coreHalfW + Math.min(0.9f, 0.45f / currentZoom);

        // Setup standard dash pacing rules
        boolean isSolid = (style == 1 || style == 6 || style == 8);
        boolean isMarching = (style == 2 || style == 9);
        int dashPeriod = 8, dashOn = 5;
        if (style == 0) { dashPeriod = 10; dashOn = 3; }
        else if (style == 3 || style == 4) { dashPeriod = 14; dashOn = 6; }
        else if (style == 5) { dashPeriod = 8; dashOn = 3; }
        else if (style == 7 || style == 9) { dashPeriod = 20; dashOn = 5; }
        else if (style == 10) { dashPeriod = 16; dashOn = 2; }

        long effectiveSpeedDiv = isMarching ? speedDiv : 1L;
        int dashOffset = (int) ((animTick / effectiveSpeedDiv) % dashPeriod);

        // Near-black at ~15-20% opacity (the old floor of 30/255) reads as basically invisible
        // against this screen's near-black canvas background - bump the floor hard so the
        // casing actually shows as a distinct dark border regardless of what's behind it.
        int outlineAlpha = Math.min(255, Math.max(170, alpha));
        int outlineRgb = QuestChroniclesSettings.get().getTheme() == QuestChroniclesSettings.Theme.LIGHT
                ? 0x1A1A1A : 0xEDEDED;
        int outlineColor = (outlineAlpha << 24) | outlineRgb;

        // Fetch the running batch stream consumer and local screen matrix context
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = g.pose().last().pose();

        // Pre-calculate path segments to ensure lightning fast execution loops
        float[] pathX = new float[steps + 1];
        float[] pathY = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float mt = 1f - t;
            pathX[i] = mt * mt * mt * xa + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * xb;
            pathY[i] = mt * mt * mt * ya + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * yb;
        }

        // PASS 1: High-Contrast Background Alignment Ribbon
        if (outlineAlpha > 0) {
            for (int i = 0; i < steps; i++) {
                float sx = pathX[i], sy = pathY[i];
                float ex = pathX[i + 1], ey = pathY[i + 1];

                float dx = ex - sx, dy = ey - sy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 0.01f) continue;

                float nx = -dy / len;
                float ny = dx / len;

                float ox = nx * outlineHalfW;
                float oy = ny * outlineHalfW;

                writeVert(vc, mat, sx + ox, sy + oy, outlineColor);
                writeVert(vc, mat, sx - ox, sy - oy, outlineColor);
                writeVert(vc, mat, ex - ox, ey - oy, outlineColor);
                writeVert(vc, mat, ex + ox, ey + oy, outlineColor);
            }
        }

        // PASS 2: Core Ribbon Pathing (With clean directional orientation vectors)
        for (int i = 0; i < steps; i++) {
            if (!isSolid && ((i + dashOffset) % dashPeriod) >= dashOn) continue;

            float sx = pathX[i], sy = pathY[i];
            float ex = pathX[i + 1], ey = pathY[i + 1];

            float dx = ex - sx, dy = ey - sy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.01f) continue;

            float nx = -dy / len;
            float ny = dx / len;

            float cx = nx * coreHalfW;
            float cy = ny * coreHalfW;

            int segmentColor = (alpha << 24) | rgb;

            writeVert(vc, mat, sx + cx, sy + cy, segmentColor);
            writeVert(vc, mat, sx - cx, sy - cy, segmentColor);
            writeVert(vc, mat, ex - cx, ey - cy, segmentColor);
            writeVert(vc, mat, ex + cx, ey + cy, segmentColor);
        }

        // Pass 3: Arrowhead sprite (batched — see drawArrowSprite)
        if (showArrow) {
            // Grows at low zoom so the arrow stays legible, but capped hard - otherwise at very
            // low zoom (e.g. 12%) this blows up past 40px and swallows the nodes it points at.
            float arrowSize = Math.min(9f, 5.0f / currentZoom);
            float tipT = dist > 0.1f ? Math.max(0.5f, 1f - (arrowSize * 1.5f) / dist) : 0.5f;
            float mt2 = 1f - tipT;
            float tipX = mt2 * mt2 * mt2 * xa + 3 * mt2 * mt2 * tipT * cp1x + 3 * mt2 * tipT * tipT * cp2x + tipT * tipT * tipT * xb;
            float tipY = mt2 * mt2 * mt2 * ya + 3 * mt2 * mt2 * tipT * cp1y + 3 * mt2 * tipT * tipT * cp2y + tipT * tipT * tipT * yb;

            float dirX = xb - cp2x, dirY = yb - cp2y;
            float dLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (dLen > 0.01f) {
                dirX /= dLen; dirY /= dLen;
                int arrowAlpha = Math.max(alpha, 200);
                int arrowColor = (arrowAlpha << 24) | rgb;
                drawArrowSprite(g, tipX, tipY, dirX, dirY, arrowSize, arrowColor);
            }
        }
    }

    private static void writeVert(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat, float x, float y, int color) {
        vc.vertex(mat, x, y, 0.0f)
                .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    /**
     * Dependency-line arrowhead sprite. Transparent-background variant, since this is drawn
     * as a rotated overlay on top of the line ribbon rather than a standalone icon.
     */
    private static final net.minecraft.resources.ResourceLocation ARROW_SPRITE =
            new net.minecraft.resources.ResourceLocation("phoenix_chronicles",
                    "textures/gui/sprites/arrow_no_background.png");

    /**
     * One frame's queued arrowhead instances. RenderType.guiTextured(...) doesn't exist on
     * 1.20.1 (it's part of the later GuiGraphics rework) and GuiGraphics.blit() on this
     * version issues its own immediate Tesselator draw per call rather than batching through
     * bufferSource() - so textured quads here can't just get "another getBuffer() call for the
     * same RenderType" the way the untextured line ribbon does. Instead every arrow queues its
     * corner data here, and flushArrowQueue() builds ONE BufferBuilder covering every arrow on
     * screen and uploads it in a single draw call, which is what's actually being asked for.
     */
    private static final class ArrowInstance {
        final float tipX, tipY, dirX, dirY, halfSize;
        final int color;
        ArrowInstance(float tipX, float tipY, float dirX, float dirY, float halfSize, int color) {
            this.tipX = tipX; this.tipY = tipY; this.dirX = dirX; this.dirY = dirY;
            this.halfSize = halfSize; this.color = color;
        }
    }

    private final List<ArrowInstance> arrowQueue = new ArrayList<>();

    /** Queues one arrowhead instance for the batched flush at the end of the canvas pass. */
    private void drawArrowSprite(GuiGraphics g, float tipX, float tipY, float dirX, float dirY,
                                 float halfSize, int color) {
        arrowQueue.add(new ArrowInstance(tipX, tipY, dirX, dirY, halfSize, color));
    }

    /** One queued ribbon (outline or core) quad - four corners, already screen-space, one color. */
    private static final class RibbonQuad {
        final float x0, y0, x1, y1, x2, y2, x3, y3;
        final int color;
        RibbonQuad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.x2 = x2; this.y2 = y2; this.x3 = x3; this.y3 = y3;
            this.color = color;
        }
    }

    private final List<RibbonQuad> ribbonQueue = new ArrayList<>();

    private void queueRibbonQuad(float x0, float y0, float x1, float y1,
                                  float x2, float y2, float x3, float y3, int color) {
        ribbonQueue.add(new RibbonQuad(x0, y0, x1, y1, x2, y2, x3, y3, color));
    }

    /** Approximates a filled circle as a fan of thin triangular wedges (degenerate quads - two
     *  corners collapsed to the center point), queued through the same ribbon batch. Good enough
     *  at the small radii these end caps use to read as genuinely rounded rather than square. */
    private void queueRoundCap(float cx, float cy, float radius, int color) {
        if (radius < 0.6f) return;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            double a0 = i * 2 * Math.PI / segs;
            double a1 = (i + 1) * 2 * Math.PI / segs;
            float x1 = cx + (float) (radius * Math.cos(a0)), y1 = cy + (float) (radius * Math.sin(a0));
            float x2 = cx + (float) (radius * Math.cos(a1)), y2 = cy + (float) (radius * Math.sin(a1));
            queueRibbonQuad(cx, cy, x1, y1, x2, y2, cx, cy, color);
        }
    }

    /**
     * Uploads every queued ribbon quad (outline + core, every edge) as one raw Tesselator draw
     * call, mirroring flushArrowQueue()'s mechanism exactly - untextured, so this uses
     * POSITION_COLOR instead of POSITION_TEX_COLOR, but otherwise the identical approach.
     */
    private void flushRibbonQueue(GuiGraphics g) {
        FrameProfiler.setCounter("ribbonQuadsQueued", ribbonQueue.size());
        if (ribbonQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

        g.flush();

        // renderNodesAndDetails() (which now runs immediately before this, per the render-order
        // fix) does full 3D item-model rendering for every node with an item icon - and cube
        // models legitimately rely on backface culling to hide interior faces. If that gets left
        // enabled afterward, these quads (whose winding, unlike the arrow sprite's, comes out
        // consistently back-facing along a smooth path) would be silently culled in their
        // entirety while the arrow sprite - built from a different, front-facing winding - still
        // shows fine. 2D UI never needs culling, so just make sure it's off here regardless of
        // what 3D item rendering left behind.
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bb = tesselator.getBuilder();
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);

        for (RibbonQuad q : ribbonQueue) {
            int alpha = (q.color >>> 24) & 0xFF;
            int r = (q.color >>> 16) & 0xFF;
            int gg = (q.color >>> 8) & 0xFF;
            int b = q.color & 0xFF;
            bb.vertex(mat, q.x0, q.y0, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x1, q.y1, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x2, q.y2, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x3, q.y3, 0f).color(r, gg, b, alpha).endVertex();
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        ribbonQueue.clear();
    }

    /**
     * Uploads every queued arrowhead as a single textured draw call: one shader bind, one
     * texture bind, one BufferBuilder, one BufferUploader.drawWithShader(...). Must be called
     * once per frame after every line has had a chance to queue its arrow (currently: once
     * after the main dep-line pass + hover-boost pass in renderCanvasLayers()).
     */
    private void flushArrowQueue(GuiGraphics g) {
        FrameProfiler.setCounter("arrowsQueued", arrowQueue.size());
        if (arrowQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

        // RenderType.gui() (used by the line ribbon above) isn't one of GuiGraphics's
        // pre-allocated "fixed" buffers, so it falls back to sharing the exact same
        // Tesselator.getInstance().getBuilder() instance we're about to grab directly below.
        // If the "gui" batch is still pending (unflushed) when we call .begin() on that same
        // builder ourselves, it either throws ("already building") or silently discards the
        // pending line-ribbon vertices - which is exactly what was making the dep lines vanish.
        // Flushing here drains that pending batch first so the shared builder is safe to reuse.
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, ARROW_SPRITE);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bb = tesselator.getBuilder();
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);

        for (ArrowInstance a : arrowQueue) {
            int alpha = (a.color >>> 24) & 0xFF;
            int r = (a.color >>> 16) & 0xFF;
            int gg = (a.color >>> 8) & 0xFF;
            int b = a.color & 0xFF;

            // Forward = direction the arrow points; right = perpendicular, for the sprite's local axes
            float fx = a.dirX, fy = a.dirY;
            float rx = -a.dirY, ry = a.dirX;
            float half = a.halfSize;
            // arrow_no_background.png is a 45x75 portrait sprite - stretching it into a square
            // quad (equal length/width) is what made it read as a shapeless blob instead of a
            // crisp chevron. Keep the source aspect so it stays a recognizable arrow shape.
            float halfLen = half;
            float halfWid = half * (45f / 75f);

            // Quad centre sits half a sprite-length behind the tip, so the sprite's forward edge
            // (v=0 in the texture) lands exactly on the tip rather than the quad's centre.
            float cx = a.tipX - fx * halfLen;
            float cy = a.tipY - fy * halfLen;

            float tlX = cx - rx * halfWid + fx * halfLen, tlY = cy - ry * halfWid + fy * halfLen;
            float trX = cx + rx * halfWid + fx * halfLen, trY = cy + ry * halfWid + fy * halfLen;
            float brX = cx + rx * halfWid - fx * halfLen, brY = cy + ry * halfWid - fy * halfLen;
            float blX = cx - rx * halfWid - fx * halfLen, blY = cy - ry * halfWid - fy * halfLen;

            bb.vertex(mat, tlX, tlY, 0f).uv(0f, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, blX, blY, 0f).uv(0f, 1f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, brX, brY, 0f).uv(1f, 1f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, trX, trY, 0f).uv(1f, 0f).color(r, gg, b, alpha).endVertex();
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        arrowQueue.clear();
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

    /** Draws a single bright spark dot traveling from (x1,y1) to (x2,y2) along the S-bezier. */
    private void drawBezierSpark(GuiGraphics g, int x1, int y1, int x2, int y2, long animMs, int lineIdx) {
        float adx = Math.abs(x2 - x1), ady = Math.abs(y2 - y1);
        float cp1x, cp1y, cp2x, cp2y;
        if (adx >= ady) {
            float mx = (x1 + x2) / 2f;
            cp1x = mx;
            cp1y = y1;
            cp2x = mx;
            cp2y = y2;
        } else {
            float my = (y1 + y2) / 2f;
            cp1x = x1;
            cp1y = my;
            cp2x = x2;
            cp2y = my;
        }
        // Each line gets its own offset so sparks don't all sync
        float t = ((animMs / 1800f) + lineIdx * 0.37f) % 1f;
        float mt = 1f - t;
        int bx = Math.round(mt * mt * mt * x1 + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * x2);
        int by = Math.round(mt * mt * mt * y1 + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * y2);
        // Bright core + soft halo
        g.fill(bx - 1, by - 1, bx + 2, by + 2, 0xFFFFEE88);
        g.fill(bx - 2, by - 2, bx + 3, by + 3, 0x44FFCC44);
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
            iy += CTX_ROW;
        }

        // Move-category submenu
        if (ctxMoveCatOpen && ctxNode != null) {
            List<String> cats = buildCategoryList();
            cats.remove("ALL");
            int subX = x + CTX_W + 2;
            int subY = ctxMoveCatY(items);
            int subH = cats.size() * CTX_ROW + 4;
            g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
            g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
            g.fill(subX, subY, subX + CTX_W, subY + 1, C_CTX_BORDER);
            g.fill(subX, subY + subH - 1, subX + CTX_W, subY + subH, C_CTX_BORDER);
            g.fill(subX, subY, subX + 1, subY + subH, C_CTX_BORDER);
            g.fill(subX + CTX_W - 1, subY, subX + CTX_W, subY + subH, C_CTX_BORDER);
            int sy = subY + 2;
            for (String cat : cats) {
                boolean hov = mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW;
                if (hov) g.fill(subX + 1, sy, subX + CTX_W - 1, sy + CTX_ROW, C_CTX_HOVER);
                String mark = cat.equals(ctxNode.getCategory()) ? "§a● " : "§8  ";
                g.drawString(font, mark + "§7" + friendly(cat), subX + 8, sy + 4, C_CTX_TEXT);
                sy += CTX_ROW;
            }
        }

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
        g.fill(0, 0, SIDEBAR_W, height, C_PANEL_DARK);
        g.fill(SIDEBAR_W, 0, width, height, C_BG);
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, height, C_BORDER);
        CanvasBackgroundRenderer.drawBackground(g, SIDEBAR_W, HEADER_H, width, height, selectedCategory, zoom, viewOffX, viewOffY);
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
        g.fill(bx - 1, by - 1, bx + badgeSz + 1, by + badgeSz + 1, 0xAA0B0B0F);
        g.fill(bx, by, bx + badgeSz, by + badgeSz, bc);
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
        int cl = SIDEBAR_W, cr = width;
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
            if (getState(n) == QuestState.COMPLETED) done++;
        }
        return new int[] { done, total };
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

    /** Brightens a line color for hover highlighting. */
    private static int boostedLineColor(int col) {
        int r = Math.min(255, ((col >> 16) & 0xFF) + 90);
        int g2 = Math.min(255, ((col >> 8) & 0xFF) + 90);
        int b = Math.min(255, (col & 0xFF) + 90);
        return 0xFF000000 | (r << 16) | (g2 << 8) | b;
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

    private void deleteQuestFiles(QuestNode node) {
        QuestFileSaver.deleteQuestFiles(node);
    }

    public static FullQuestData loadMarkdownContent(Path mdPath) {
        Component title = Component.empty();
        StringBuilder desc = new StringBuilder();
        try (BufferedReader r = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("# ") && title.getString().isEmpty())
                    title = Component.literal(t.substring(2).trim());
                else if (!t.startsWith("#") && !t.isEmpty())
                    desc.append(t).append(' ');
            }
        } catch (IOException ignored) {}
        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of());
    }

    // ── Bezier proximity ─────────────────────────────────────────────────────

    /** True when (mx,my) is within tol pixels of the cubic S-bezier from (x1,y1) to (x2,y2). */
    private boolean pointNearBezier(int mx, int my, int x1, int y1, int x2, int y2, int tol) {
        int dx = x2 - x1, dy = y2 - y1;
        int cx1 = x1 + dx / 3, cy1 = y1;
        int cx2 = x2 - dx / 3, cy2 = y2;
        int steps = Math.max(16, Math.abs(dx) / 4 + Math.abs(dy) / 4);
        int tolSq = tol * tol;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float u = 1 - t;
            float bx = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            float by = u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2;
            int ddx = mx - (int) bx, ddy = my - (int) by;
            if (ddx * ddx + ddy * ddy <= tolSq) return true;
        }
        return false;
    }

    // ── Line context menu ─────────────────────────────────────────────────────

    private void renderLineCtxMenu(GuiGraphics g, double mx, double my) {
        QuestNode parentNode = lineCtxParentId == null ? null : QuestTreeRegistry.getQuest(lineCtxParentId);
        QuestNode childNode = lineCtxChildId == null ? null : QuestTreeRegistry.getQuest(lineCtxChildId);
        if (parentNode == null || childNode == null) {
            lineCtxOpen = false;
            return;
        }

        boolean isForbidden = childNode.isPrereqForbidden(lineCtxParentId);
        boolean isRequired = !isForbidden && childNode.isPrereqRequired(lineCtxParentId);
        boolean isLink = childNode.isPrereqLink(lineCtxParentId);
        boolean isCosmetic = childNode.isPrereqCosmetic(lineCtxParentId);

        // 3-way cycle label: required → optional → forbidden → required
        String cycleLabel;
        if (isForbidden) cycleLabel = "§aType: Forbidden  →  Required";
        else if (!isRequired) cycleLabel = "§cType: Optional  →  Forbidden";
        else cycleLabel = "§eType: Required  →  Optional";

        String linkLabel = isLink ? "§7Unmark as link" : "§dMark as link";
        String cosmeticLabel = isCosmetic ? "§7Unmark as cosmetic-only" : "§6Mark as cosmetic-only (no gate)";
        boolean childHidesLines = childNode.isHideDepLine();
        String hideLabel = childHidesLines ? "§aShow dep lines: " + childNode.getTitle().getString() :
                "§8Hide dep lines: " + childNode.getTitle().getString();

        String shapeLabel = "§7Line shape: §f" + lineOverrideLabel(childNode.getPrereqLineShape(lineCtxParentId));
        String visualLabel = "§7Line style: §f" + lineOverrideLabel(childNode.getPrereqLineVisual(lineCtxParentId));
        String speedLabel = "§7Dot speed: §f" + lineOverrideLabel(childNode.getPrereqLineSpeed(lineCtxParentId));
        String arrowLabel = "§7Arrow: §f" + arrowOverrideLabel(childNode.getPrereqLineArrow(lineCtxParentId));

        String[] labels = {
                "§cRemove connection",
                cycleLabel,
                linkLabel,
                cosmeticLabel,
                hideLabel,
                shapeLabel,
                visualLabel,
                speedLabel,
                arrowLabel,
                "§bDependency line settings…"
        };
        int menuW = 210, itemH = 14, pad = 4;
        int menuH = pad + labels.length * itemH + pad;
        int lx = lineCtxX, ly = lineCtxY;
        if (lx + menuW > width) lx = width - menuW - 2;
        if (ly + menuH > height) ly = height - menuH - 2;

        g.fill(lx, ly, lx + menuW, ly + menuH, 0xEE0D0D12);
        g.fill(lx, ly, lx + menuW, ly + 1, 0xFF9900FF);
        g.fill(lx, ly, lx + 1, ly + menuH, 0xFF9900FF);
        g.fill(lx + menuW - 1, ly, lx + menuW, ly + menuH, 0xFF9900FF);
        g.fill(lx, ly + menuH - 1, lx + menuW, ly + menuH, 0xFF9900FF);

        for (int i = 0; i < labels.length; i++) {
            int iy = ly + pad + i * itemH;
            boolean hov = mx >= lx && mx < lx + menuW && my >= iy && my < iy + itemH;
            if (hov) g.fill(lx + 1, iy, lx + menuW - 1, iy + itemH, 0x44FFFFFF);
            g.drawString(font, labels[i], lx + 6, iy + 2, 0xFFDDDDDD, false);
        }
    }

    private static String lineOverrideLabel(Enum<?> override) {
        return override == null ? "§8Inherit" : override.name();
    }

    private static String arrowOverrideLabel(Boolean override) {
        return override == null ? "§8Inherit" : (override ? "ON" : "OFF");
    }

    /** Cycles null → true → false → null. */
    private static Boolean cycleArrowOverride(Boolean current) {
        if (current == null) return Boolean.TRUE;
        return current ? Boolean.FALSE : null;
    }

    /** Cycles null → values[0] → values[1] → ... → values[last] → null. */
    private static <T extends Enum<T>> T cycleLineOverride(T current, T[] values) {
        if (current == null) return values[0];
        int next = current.ordinal() + 1;
        return next < values.length ? values[next] : null;
    }

    private void handleLineCtxClick(int mx, int my) {
        if (!lineCtxOpen) return;
        QuestNode parentNode = lineCtxParentId == null ? null : QuestTreeRegistry.getQuest(lineCtxParentId);
        QuestNode childNode = lineCtxChildId == null ? null : QuestTreeRegistry.getQuest(lineCtxChildId);
        if (parentNode == null || childNode == null) {
            lineCtxOpen = false;
            return;
        }

        boolean isForbidden = childNode.isPrereqForbidden(lineCtxParentId);
        boolean isRequired = !isForbidden && childNode.isPrereqRequired(lineCtxParentId);
        boolean isLink = childNode.isPrereqLink(lineCtxParentId);
        boolean isCosmetic = childNode.isPrereqCosmetic(lineCtxParentId);
        int menuW = 210, itemH = 14, pad = 4;
        int menuH = pad + 10 * itemH + pad;
        int lx = lineCtxX, ly = lineCtxY;
        if (lx + menuW > width) lx = width - menuW - 2;
        if (ly + menuH > height) ly = height - menuH - 2;

        if (mx < lx || mx >= lx + menuW || my < ly || my >= ly + menuH) {
            lineCtxOpen = false;
            return;
        }

        int idx = (my - ly - pad) / itemH;
        lineCtxOpen = false;

        if (idx == 0) {
            // Remove connection: child removes parentNode as a prereq; also remove parent→child edge
            childNode.removePrerequisite(parentNode);
            parentNode.removeChild(childNode);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback("Removed: " + parentNode.getTitle().getString() + " → " + childNode.getTitle().getString());
        } else if (idx == 1) {
            // 3-way cycle: required → optional → forbidden → required
            if (isForbidden) {
                childNode.setPrereqForbidden(lineCtxParentId, false);
                childNode.setPrereqRequired(lineCtxParentId, true);
                setFeedback("Prereq type: Required");
            } else if (!isRequired) {
                childNode.setPrereqForbidden(lineCtxParentId, true);
                setFeedback("Prereq type: Forbidden (must NOT be completed)");
            } else {
                childNode.setPrereqRequired(lineCtxParentId, false);
                setFeedback("Prereq type: Optional");
            }
            saveNodePrereqsToDisk(childNode);
            rebuild();
        } else if (idx == 2) {
            // Toggle link marker
            childNode.setPrereqLink(lineCtxParentId, !isLink);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback(isLink ? "Unmarked as link" : "Marked as link");
        } else if (idx == 3) {
            // Toggle cosmetic-only marker — when set, this edge is drawn but never gates unlock
            childNode.setPrereqCosmetic(lineCtxParentId, !isCosmetic);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback(isCosmetic ? "Unmarked as cosmetic-only (now gates unlock)" :
                    "Marked as cosmetic-only (no longer gates unlock)");
        } else if (idx == 4) {
            // Toggle dep line visibility for the child quest
            childNode.setHideDepLine(!childNode.isHideDepLine());
            saveNodeHideDepLineToDisk(childNode);
            rebuild();
            setFeedback(childNode.isHideDepLine() ? "Dep lines hidden: " + childNode.getTitle().getString() :
                    "Dep lines shown: " + childNode.getTitle().getString());
        } else if (idx == 5) {
            // Cycle this edge's line-shape override: Inherit → Spline → Straight → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineShape(lineCtxParentId),
                    QuestChroniclesSettings.LineStyle.values());
            childNode.setPrereqLineShape(lineCtxParentId, next);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback("Line shape: " + lineOverrideLabel(next));
        } else if (idx == 6) {
            // Cycle this edge's line-style override: Inherit → Thin → Normal → ... → Glow → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineVisual(lineCtxParentId),
                    QuestChroniclesSettings.LineVisualStyle.values());
            childNode.setPrereqLineVisual(lineCtxParentId, next);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback("Line style: " + lineOverrideLabel(next));
        } else if (idx == 7) {
            // Cycle this edge's dot-speed override: Inherit → Slowest → ... → Very Fast → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineSpeed(lineCtxParentId),
                    QuestChroniclesSettings.LineAnimSpeed.values());
            childNode.setPrereqLineSpeed(lineCtxParentId, next);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback("Dot speed: " + lineOverrideLabel(next));
        } else if (idx == 8) {
            // Cycle this edge's directional-arrow override: Inherit → On → Off → Inherit
            Boolean next = cycleArrowOverride(childNode.getPrereqLineArrow(lineCtxParentId));
            childNode.setPrereqLineArrow(lineCtxParentId, next);
            saveNodePrereqsToDisk(childNode);
            rebuild();
            setFeedback("Arrow: " + arrowOverrideLabel(next));
        } else if (idx == 9) {
            // Open dep line settings screen, scoped to the parent quest of this edge + its dependents
            final String cat = selectedCategory;
            if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, cat, parentNode));
        }
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
        PhantasiaCompat.closePreview(phantasiaPreview);
        phantasiaPreview = null;
        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // Progress bars are cached per-category; clear on each tick so they reflect
        // quest completions that arrived via S2C sync packets since last rebuild.
        progressCache.clear();
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

        int cl = SIDEBAR_W, cr = width;

        // ── Spotlight dim ─────────────────────────────────────────────────────
        int hx = 0, hy = 0, hw = 0, hh = 0;
        if (step.hasHighlight()) {
            if (TutorialStep.HL_SIDEBAR.equals(step.highlight())) {
                hx = 0;
                hy = 0;
                hw = SIDEBAR_W;
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