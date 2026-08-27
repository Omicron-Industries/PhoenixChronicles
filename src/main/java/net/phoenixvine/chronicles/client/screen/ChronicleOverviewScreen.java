package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.client.registry.SeenQuestTracker;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.registry.CategoryRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ChronicleOverviewScreen extends Screen
                                     implements ScreenContext, NodeCtxMenuState, DragControllerState,
        GraphLayoutState, QuestEditOpsState, ChapterActionsState,
                                     NodeRendererState, BulkOpsPanelState, NodeContextMenuBuilderState {

    public static final int HEADER_H = 38;
    public static final int TOOLBAR_Y = 22;
    public static final int TOOLBAR_H = 16;
    public static final int NODE_SIZE = 32;
    public static final int C_NBORD_SEL = 0xFF6688FF;
    private static final int C_LINE_ALMOST = 0xAAFFEE33;

    public static final int C_CTX_BG = 0xFF1A1A22;
    public static final int C_CTX_HOVER = 0xFF252532;
    public static final int C_CTX_BORDER = 0xFF8844AA;
    public static final int C_CTX_SEP = 0xFF2A2A38;
    public static final int C_CTX_TEXT = 0xFFCCCCD8;
    public static final int C_CTX_DANGER = 0xFFCC4444;
    private static final int C_PROG_ACT = 0xFFBB8800;
    private static final float ZOOM_MIN = 0.12f;
    private static final float ZOOM_MAX = 2.5f;
    private static final float ZOOM_STEP = 0.12f;
    private static final long POST_MOVE_UNDO_WINDOW_MS = 1000;
    private static final float PIC_EDIT_MIN_SIZE = 4f, PIC_EDIT_MAX_SIZE = 4096f;
    public static final int CTX_ROW = 16;
    public static final int CTX_SEP = 5;
    public static final int CTX_W = 128;
    static final int CTX_MOVE_CAT_MAX_ROWS = 10;
    public static final Set<ResourceLocation> collapsedSubtreeRoots = new HashSet<>();
    private static final int[] GRID_SNAP_CYCLE = { 1, 4, 8, 16, 32, 64, 128 };
    public static final long OPEN_FADE_MS = 120;
    private static final long TOOLTIP_DELAY_MS = 0;
    public static final int MIN_NODE_PX = 12;
    public static final float MIN_NODE_FLOOR_FRACTION = 0.375f;
    public static final int GROUP_LABEL_BAR_H = 11;
    final Map<ResourceLocation, String> searchCache = new HashMap<>();
    private final SidebarPanel sidebarPanel = new SidebarPanel();

    SidebarPanel sidebarPanelInstance() {
        return sidebarPanel;
    }

    private final java.util.function.BiConsumer<Integer, Integer> panCanvasFn = this::panCanvas;

    private final Map<Item, ItemStack> iconStackCache = new HashMap<>();
    private final Map<QuestTask, ItemStack> nbtIconStackCache = new java.util.IdentityHashMap<>();

    @Override
    public ItemStack cachedIconStack(Item icon) {
        return iconStackCache.computeIfAbsent(icon, ItemStack::new);
    }

    private final Map<String, Integer> dbgShapeCounts = new HashMap<>();

    private final Set<ResourceLocation> hiddenByCollapse = new HashSet<>();
    private final MinimapRenderer minimap = new MinimapRenderer();
    final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    private final Map<ResourceLocation, NodeHitbox> nodeButtons = new LinkedHashMap<>();
    private final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();
    private final Map<String, int[]> progressCache = new HashMap<>();
    private final Map<String, Boolean> attentionCache = new HashMap<>();
    private final Map<String, Boolean> rewardsCache = new HashMap<>();
    final ValidationPanel validationPanel = new ValidationPanel(this);
    private final StatsPanel statsPanel = new StatsPanel(this);
    private final TutorialOverlay tutorialOverlay = new TutorialOverlay();

    @Override
    public void toggleStatsPanel() {
        if (statsPanel.isOpen()) {
            statsPanel.close();
        } else {
            statsPanel.open();
            validationPanel.close();
        }
    }

    private final Set<ResourceLocation> unlockPathHighlight = new HashSet<>();

    private QuestNode nonDevCtxNode;
    private int nonDevCtxX, nonDevCtxY;
    private final java.util.List<Runnable> pendingDeferredDraws = new java.util.ArrayList<>();
    private final java.util.Set<ResourceLocation> subgraphNodes = new java.util.HashSet<>();
    private final ToolbarPanel toolbarPanel = new ToolbarPanel();

    ToolbarPanel toolbarPanelInstance() {
        return toolbarPanel;
    }

    private final PaletteState palette = new PaletteState();
    String selectedChapter = "";
    private String viewChapterTracker = null;
    private ResourceLocation lastHoveredNodeId = null;
    private int dbgFull3DIconCount = 0;
    private int dbgCustomIconCount = 0;
    private int dbgPickedTextureIconCount = 0;
    private int dbgFluidIconCount = 0;
    private int dbgGlyphIconCount = 0;
    private boolean isDevMode = false;
    private String feedbackMsg = "";
    private int feedbackTimer = 0;
    final UndoRedoManager undoRedo = new UndoRedoManager(this::setFeedback);
    private int viewOffX = 0, viewOffY = 0;
    private int pendingPanDX = 0, pendingPanDY = 0;
    private float zoom = 1.0f;
    private boolean isPanning = false;
    private boolean hideCompleted = false;
    private long lastCanvasClickTime = 0;
    private int lastCanvasClickX = 0;
    private int lastCanvasClickY = 0;
    private int dragGrabX = 0, dragGrabY = 0;
    private int dragOrigX = 0, dragOrigY = 0;
    private boolean pickupPlaceActive = false;

    private boolean middleDragPickupActive = false;

    private boolean quickDepKeyDown = false;

    private int groupDragGrabX = 0, groupDragGrabY = 0;
    @Nullable
    private BackgroundPictureConfig.Picture draggedPicture = null;
    private int pictureDragGrabX = 0, pictureDragGrabY = 0;
    private float pictureDragStartX, pictureDragStartY;
    private final PictureContextMenu pictureCtxMenu = new PictureContextMenu(this);
    private final TogglePanel.NodeContextMenu nodeCtxMenu = new TogglePanel.NodeContextMenu(this);
    private final GraphEditorState editorState = new GraphEditorState();

    GraphEditorState editorStateInstance() {
        return editorState;
    }

    private final DragController dragController = new DragController(this, this, editorState);
    private final GraphLayoutEngine layoutEngine = new GraphLayoutEngine(this, this, this);
    private final QuestEditOps questEditOps = new QuestEditOps(this, this, editorState);
    private final ChapterActions chapterActions = new ChapterActions(this, this, editorState);
    private final BulkOpsPanel bulkOpsPanel = new BulkOpsPanel(this, this, editorState);
    private final NodeRenderer nodeRenderer = new NodeRenderer(this, this, editorState);
    private final NodeContextMenuBuilder ctxMenuBuilder = new NodeContextMenuBuilder(this, this, this, editorState);
    @Nullable
    BackgroundPictureConfig.Picture pictureEditMode = null;
    private float pictureEditStartX, pictureEditStartY, pictureEditStartW, pictureEditStartH;
    @Nullable
    private QuestNode nodeSizeEditMode = null;
    private QuestNode.NodeSize nodeSizeEditStartSize;
    private int nodeSizeEditStartOverridePx, nodeSizeEditStartX, nodeSizeEditStartY;
    private double nodeSizeDragAccX = 0, nodeSizeDragAccY = 0;
    private boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    private int ctxX, ctxY;

    private float ctxScale = 1f;
    private int ctxRawX, ctxRawY;
    private QuestNode ctxNode = null;
    private boolean ctxMoveCatOpen = false;
    private int ctxMoveCatScroll = 0;
    @Nullable
    private QuestGroup ctxGroup = null;
    private boolean renderingAsBackdrop = false;

    String stateFilter = "ALL";
    private Object phantasiaPreview = null;
    private List<String> chapterListCache = null;
    private long chapterListCacheAtMs = 0;
    private static final long CHAPTER_LIST_CACHE_TTL_MS = 2000;
    private QuestNode linkDragSource = null;
    private int linkDragX, linkDragY;
    private int gridSnap = 8;
    private boolean gridSnapEnabled = true;
    private GridDisplayMode gridDisplayMode = GridDisplayMode.ON_DRAG;
    private boolean dragForceSnap = false;

    private long openTimeMs = -1;
    private ResourceLocation tooltipHoverNodeId = null;
    private long tooltipHoverStartMs = 0;
    private boolean testMode = false;
    private PlayerQuestData testModeData = new PlayerQuestData();
    private boolean minimapOpen = false;
    private boolean mmDragging = false;
    @Nullable
    private PlayerQuestData playerData = null;
    private int lastSeenProgressVersion = -1;
    private String lastTickHand = null;

    private final Screen parent;

    public ChronicleOverviewScreen(Screen parent) {
        super(Component.literal("Chronicles"));

        this.parent = parent;

        selectedChapter = QuestChroniclesSettings.get().getLastChapter();

        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        hideCompleted = s.isHideCompletedByDefault();
        gridSnap = s.getDefaultGridSnap();
    }

    public ChronicleOverviewScreen() {
        this(null);
    }

  public static Path chaptersFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("categories.txt");
    }

    static void invalidateNodeCachesUpChain(Screen from, QuestNode node) {
        Screen s = from;
        for (int i = 0; i < 8; i++) {
            if (s instanceof ChronicleOverviewScreen overview) {
                overview.invalidateNodeCaches(node);
                return;
            }
            if (s instanceof QuestCreatorScreen qcs) s = qcs.getParentScreen();
            else if (s instanceof QuestTasksScreen qts) s = qts.getParentScreen();
            else if (s instanceof TaskRewardEditorScreen tres) s = tres.getParentScreen();
            else if (s instanceof VariantEditorScreen ves) s = ves.getParentScreen();
            else break;
        }
    }

    public static int nodeBorderThickness(int sz) {
        return Math.max(1, Math.min(4, sz / 28));
    }

    public static int blendColor(int base, int over, float a) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        return 0xFF000000 | ((int) (br + (or - br) * a) << 16) | ((int) (bg + (og - bg) * a) << 8) |
                (int) (bb + (ob - bb) * a);
    }

    public static float animPulse(float base, float amplitude, double periodDivisor) {
        if (QuestChroniclesSettings.get().isReduceMotion()) return base;
        return base + amplitude * (float) Math.sin(System.currentTimeMillis() / periodDivisor);
    }

    public static FullQuestData loadMarkdownContent(Path mdPath) {
        Component title = Component.empty();
        StringBuilder desc = new StringBuilder();

        boolean pendingParagraphBreak = false;
        try (BufferedReader r = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("# ") && title.getString().isEmpty()) {
                    title = Component.literal(t.substring(2).trim());
                } else if (t.matches("-{3,}")) {

                    if (!desc.isEmpty()) desc.append("\n\n");
                    desc.append(t);
                    pendingParagraphBreak = true;
                } else if (!t.startsWith("#") && !t.isEmpty()) {
                    if (!desc.isEmpty()) desc.append(pendingParagraphBreak ? "\n\n" : ' ');
                    desc.append(t);
                    pendingParagraphBreak = false;
                } else if (t.isEmpty()) {
                    pendingParagraphBreak = true;
                }
            }
        } catch (IOException ignored) {}
        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of(), Collections.emptyList(),
                Collections.emptyList());
    }

    public int sidebarW() {
        return sidebarPanel.width();
    }

    private boolean isSidebarNarrow() {
        return sidebarPanel.isNarrow();
    }

    private int sidebarVisualW() {
        return sidebarPanel.visualWidth();
    }

    private void updateSidebarHoverPeek(int mx, int my) {
        sidebarPanel.updateHoverPeek(mx, my, panCanvasFn);
    }

    @Override
    public float posZoom() {
        return zoom;
    }

    private void recomputeHiddenByCollapse() {
        hiddenByCollapse.clear();
        for (ResourceLocation rootId : collapsedSubtreeRoots) {
            QuestNode n = QuestTreeRegistry.getQuest(rootId);
            if (n != null) collectDescendants(n, hiddenByCollapse);
        }
    }

    private void collectDescendants(QuestNode node, Set<ResourceLocation> out) {
        for (QuestNode child : node.getChildren()) {
            if (out.add(child.getId())) collectDescendants(child, out);
        }
    }

    @Override
    public void toggleSubtreeCollapse(QuestNode node) {
        if (!collapsedSubtreeRoots.remove(node.getId())) collapsedSubtreeRoots.add(node.getId());
        rebuild();
    }

    public QuestState getState(QuestNode node) {
        if (testMode) return testModeData.getQuestState(node.getId(), QuestState.LOCKED);
        if (playerData == null) return QuestState.LOCKED;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED);
    }

    @Override
    public boolean isGatedHidden(QuestNode node) {
        if (node.isSelfGatedHidden(this::getState)) return true;
        return isAncestorGatedHidden(node);
    }

    private boolean isAncestorGatedHidden(QuestNode node) {
        return QuestChroniclesSettings.get().isCascadeHiddenQuests() && node.isAncestorGatedHidden(this::getState);
    }

    @Override
    public @Nullable QuestNode resolveLinkTarget(QuestNode node) {
        return node.isLinkStub() ? QuestTreeRegistry.getQuest(node.getLinkTarget()) : node;
    }

    private Item fallbackTaskIcon(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return item;
        }
        return null;
    }

    @Override
    public QuestTask fallbackTaskIconTask(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return task;
        }
        return null;
    }

    @Override
    public QuestTask matchingIconTask(QuestNode node, Item icon) {
        for (QuestTask task : node.getTasks()) {
            if (task instanceof net.phoenixvine.chronicles.tasks.ItemRequirementTask t && t.getItem() == icon) {
                return task;
            }
        }
        return null;
    }

    @Override
    public ItemStack nbtAwareIconStack(QuestTask task, Item icon) {
        if (!(task instanceof net.phoenixvine.chronicles.tasks.ItemRequirementTask t) || t.getNbtFilter() == null ||
                t.getNbtFilter().isEmpty()) {
            return cachedIconStack(icon);
        }
        return nbtIconStackCache.computeIfAbsent(task, k -> {
            ItemStack stack = new ItemStack(icon);
            stack.setTag(t.getNbtFilter().copy());
            return stack;
        });
    }

    private QuestState getDisplayState(QuestNode node) {
        QuestNode target = resolveLinkTarget(node);
        return getState(target != null ? target : node);
    }

    @Override
    public boolean isTaskDone(QuestTask task) {
        if (minecraft == null || minecraft.player == null) return false;
        return task.isCompletedFor(minecraft.player);
    }

    boolean chapterHasQuests(String chapter) {
        return chapterQuestCount(chapter) > 0;
    }

    @Override
    public int chapterQuestCount(String chapter) {
        int count = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) count++;
        }
        return count;
    }

    @Override
    public List<ResourceLocation> questIdsInChapter(String chapter) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) ids.add(n.getId());
        }
        return ids;
    }

    @Override
    public List<ResourceLocation> questIdsInCategory(String categoryId) {
        List<ResourceLocation> ids = new ArrayList<>();
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .get(categoryId);
        if (cat == null) return ids;
        for (String chapter : cat.chapters()) ids.addAll(questIdsInChapter(chapter));
        return ids;
    }

    private void forceCompleteChapterOnRightClick(String chapter) {
        chapterActions.forceCompleteChapterOnRightClick(chapter);
    }

    private void resetChapterOnRightClick(String chapter) {
        chapterActions.resetChapterOnRightClick(chapter);
    }

    private void forceCompleteCategoryOnRightClick(String categoryId) {
        chapterActions.forceCompleteCategoryOnRightClick(categoryId);
    }

    private void resetCategoryOnRightClick(String categoryId) {
        chapterActions.resetCategoryOnRightClick(categoryId);
    }

    void deleteChapter(String chapter) {
        chapterActions.deleteChapter(chapter);
    }

    private void deleteCategoryOnRightClick(String categoryId) {
        chapterActions.deleteCategoryOnRightClick(categoryId);
    }

    private void deleteChapterOnRightClick(String chapter) {
        chapterActions.deleteChapterOnRightClick(chapter);
    }

    private void openSidebarContextMenu(SidebarRow row, int mx, int my) {
        chapterActions.openSidebarContextMenu(row, mx, my);
    }

    @Override
    public void setSelectedChapter(String chapter) {
        selectedChapter = chapter;
    }

    @Override
    public void invalidateChapterCaches() {
        chapterListCache = null;
    }

    @Override
    public void openContextMenuFor(int mx, int my, List<SidebarPanel.MenuAction> actions) {
        sidebarPanel.openContextMenu(mx, my, actions, width, height);
    }

    @Override
    public ChronicleOverviewScreen thisScreen() {
        return this;
    }

    public List<String> buildChapterList() {
        if (chapterListCache != null && System.currentTimeMillis() - chapterListCacheAtMs > CHAPTER_LIST_CACHE_TTL_MS) {
            chapterListCache = null;
        }
        if (chapterListCache == null) {
            Set<String> chapters = new TreeSet<>();
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                String cat = n.getChapter();
                if (cat != null && !cat.isBlank()) chapters.add(cat.toUpperCase(Locale.ROOT));
            }
            try {
                Path f = chaptersFile();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String cat = line.trim().toUpperCase(Locale.ROOT);
                        if (!cat.isEmpty()) chapters.add(cat);
                    }
                }
            } catch (IOException ignored) {}

            if (!isDevMode || !QuestChroniclesSettings.get().isShowFlagDisabledChapters()) {
                MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
                chapters.removeIf(c -> !net.phoenixvine.chronicles.registry.ChapterFlagRegistry.isChapterEnabled(c));
                if (QuestChroniclesSettings.get().isCascadeHiddenQuests())
                    chapters.removeIf(c -> QuestTreeRegistry.isChapterGatedHidden(c, this::getState));
            }

            chapterListCache = new ArrayList<>(chapters);
            chapterListCacheAtMs = System.currentTimeMillis();
        }
        return new ArrayList<>(chapterListCache);
    }

    private Function<String, int[]> progressLookup() {
        return cat -> progressCache.computeIfAbsent(cat, this::computeChapterProgress);
    }

    private Function<String, Boolean> attentionLookup() {
        return cat -> attentionCache.computeIfAbsent(cat, this::computeChapterHasAttention);
    }

    private Function<String, Boolean> rewardsLookup() {
        return cat -> rewardsCache.computeIfAbsent(cat, this::computeChapterHasUnclaimedRewards);
    }

    List<SidebarRow> buildSidebarRows() {
        return sidebarPanel.buildRows(this::friendly, progressLookup(), buildChapterList());
    }

    private int sidebarScrollAreaHeight() {
        return sidebarPanel.scrollAreaHeight(height);
    }

    private int sidebarContentHeight() {
        return sidebarPanel.contentHeight(height, this::friendly, progressLookup(), buildChapterList());
    }

    @Override
    protected void init() {
        refreshPalette();
        QuestGroupManager.invalidate();
        openTimeMs = System.currentTimeMillis();
        rebuild();
    }

    @Override
    protected void repositionElements() {
        softRebuild();
    }

    private void refreshPalette() {
        PhoenixTheme t = PhoenixTheme.current();
        ChroniclesThemePalette.refresh(t);
        palette.refresh(t);
    }

    @Override
    public Path groupsConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    void invalidateNodeCaches(QuestNode node) {
        if (node == null) return;
        validationPanel.invalidate(node.getId());
        searchCache.remove(node.getId());
        progressCache.remove(node.getChapter());
        attentionCache.remove(node.getChapter());
        rewardsCache.remove(node.getChapter());
    }

    @Override
    public void rebuild() {
        clearWidgets();
        nodeScreenPos.clear();
        nodeButtons.clear();
        searchCache.clear();
        progressCache.clear();
        attentionCache.clear();
        rewardsCache.clear();
        validationPanel.clear();
        chapterListCache = null;
        ctxOpen = false;
        ctxMoveCatOpen = false;
        ctxGroup = null;

        QuestGroupManager.load(groupsConfigPath());
        recomputeHiddenByCollapse();

        if (minecraft != null && minecraft.player != null) {

            isDevMode = !QuestChroniclesSettings.get().isDevModeDisabled() && minecraft.player.hasPermissions(2);
            playerData = minecraft.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        }

        int cl = sidebarW(), cr = width;

        List<String> cats = buildChapterList();
        if (!cats.isEmpty() && !cats.contains(selectedChapter)) selectedChapter = cats.get(0);

        if (!selectedChapter.equals(viewChapterTracker)) {
            restoreViewForChapter();
            viewChapterTracker = selectedChapter;
            QuestChroniclesSettings settings = QuestChroniclesSettings.get();
            if (!selectedChapter.equals(settings.getLastChapter())) {
                settings.setLastChapter(selectedChapter);
                settings.save();
            }
        }

        for (QuestNode root : QuestTreeRegistry.getRootChapters().values()) {
            if (!catMatches(root)) continue;
            placeNodeRecursive(root, cl, cr);
        }

        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (catMatches(n)) placeNodeRecursive(n, cl, cr);
        }
        buildLineCache();

        sidebarPanel.syncLayoutBaseX(cl);
    }

    private void restoreViewForChapter() {
        if (!applyFitView()) {
            zoom = 1.0f;
            viewOffX = 0;
            viewOffY = 0;
        }
    }

    private void placeNodeRecursive(QuestNode node, int cl, int cr) {
        layoutEngine.placeNodeRecursive(node, cl, cr);
    }

    public int scaledNodeSize(QuestNode node) {
        return layoutEngine.scaledNodeSize(node);
    }

    public int scaledNodeSize() {
        return layoutEngine.scaledNodeSize();
    }

    void onNodeClicked(QuestNode node) {
        onNodeClicked(node, false);
    }

    void onNodeClicked(QuestNode node, boolean openFullscreen) {
        if (node == null) return;
        ctxOpen = false;
        ctxMoveCatOpen = false;

        QuestNode target = resolveLinkTarget(node);
        if (target == null) {
            setFeedback("§cBroken link: %s", node.getLinkTarget());
            return;
        }

        QuestState st = getState(target);

        editorState.selectedNode = target;
        if (editorState.subgraphMode) rebuildSubgraph();

        if (testMode) {
            if (st == QuestState.COMPLETED) {
                testModeData.setQuestState(Objects.requireNonNull(target).getId(), QuestState.LOCKED);
            } else {
                testModeData.setQuestState(Objects.requireNonNull(target).getId(), QuestState.COMPLETED);
            }
            propagateTestUnlocks();
            softRebuild();
            return;
        }

        if (!isDevMode && isGatedHidden(Objects.requireNonNull(target))) return;

        String externalScreenIdStr = target.getExternalScreenId();
        if (minecraft != null && externalScreenIdStr != null && !externalScreenIdStr.isEmpty()) {
            net.minecraft.resources.ResourceLocation externalScreenId = net.minecraft.resources.ResourceLocation
                    .tryParse(externalScreenIdStr);
            if (externalScreenId != null &&
                    net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry.isRegistered(externalScreenId)) {
                net.minecraft.client.gui.screens.Screen external = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                        .open(externalScreenId,
                                target);
                if (external != null) {
                    for (QuestTask task : target.getEffectiveTasks(minecraft.getSingleplayerServer())) {
                        if (task instanceof net.phoenixvine.chronicles.tasks.ScreenOpenedTask &&
                                minecraft.player != null && !task.isCompletedFor(minecraft.player)) {
                            net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                                    new net.phoenixvine.chronicles.network.packet.C2SScreenOpenedTaskPacket(
                                            task.getTaskId()));
                        }
                    }
                    minecraft.setScreen(external);
                    return;
                }
            }
        }

        if (minecraft != null) {

            Path mdPath = QuestFileSaver.getQuestMarkdownPath(Objects.requireNonNull(target));

            net.phoenixvine.chronicles.codec.QuestContentLoader.syncActiveLocaleFromClient();
            Path resolvedMdPath = net.phoenixvine.chronicles.codec.QuestContentLoader
                    .resolveLocaleFile(mdPath, Objects.requireNonNull(target).getId().getPath());

            FullQuestData mdData = loadMarkdownContent(resolvedMdPath);
            net.minecraft.server.MinecraftServer server = minecraft.getSingleplayerServer();

            Component effTitle = mdData.title() != null && !mdData.title().getString().isBlank() ? mdData.title() :
                    target.getEffectiveTitleRaw(server);
            Component effDesc = mdData.description() != null && !mdData.description().getString().isBlank() ?
                    mdData.description() : target.getEffectiveDescriptionRaw(server);
            FullQuestData fd = new FullQuestData(effTitle, effDesc, mdData.tasks(),
                    target.getEffectiveTasks(server), target.getEffectiveRewards(server));
            assert playerData != null;
            SeenQuestTracker.markSeen(target.getId());
            minecraft.setScreen(
                    new QuestTasksScreen(this, Objects.requireNonNull(target), fd, playerData, openFullscreen));
        }
    }

    @Override
    public void autoArrangeChapter() {
        layoutEngine.autoArrangeChapter();
    }

    private void propagateTestUnlocks() {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (testModeData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                testModeData.setQuestState(n.getId(), QuestState.LOCKED);
        }

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

    @Override
    public void rebuildSubgraph() {
        subgraphNodes.clear();
        if (editorState.selectedNode == null) return;
        subgraphNodes.add(editorState.selectedNode.getId());

        java.util.ArrayDeque<QuestNode> queue = new java.util.ArrayDeque<>();
        queue.add(editorState.selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode p : cur.getPrerequisites()) {
                if (subgraphNodes.add(p.getId())) queue.add(p);
            }
        }

        queue.add(editorState.selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode c : cur.getChildren()) {
                if (subgraphNodes.add(c.getId())) queue.add(c);
            }
        }
    }

    public void navigateToNode(QuestNode node) {
        if (!node.getChapter().equals(selectedChapter)) {
            selectedChapter = node.getChapter();
            rebuild();
        }
        int canvasW = width - sidebarW();
        int canvasH = height - HEADER_H;
        viewOffX = (int) (canvasW / 2f - node.getCustomX() * posZoom());
        viewOffY = (int) (canvasH / 2f - node.getCustomY() * posZoom());
        onNodeClicked(node);
    }

    @Override
    public void buildLineCache() {
        depLineRenderer.rebuildFromGraph(this, sidebarVisualW(), this::catMatches, palette.lineLocked,
                QuestChroniclesSettings.get());
    }

    private void softRebuild() {
        Map<String, int[]> savedProgress = new HashMap<>(progressCache);
        Map<String, Boolean> savedAttention = new HashMap<>(attentionCache);
        Map<String, Boolean> savedRewards = new HashMap<>(rewardsCache);
        Map<ResourceLocation, List<String>> savedValidation = validationPanel.snapshot();
        List<String> savedCats = chapterListCache;
        rebuild();
        progressCache.putAll(savedProgress);
        attentionCache.putAll(savedAttention);
        rewardsCache.putAll(savedRewards);
        validationPanel.restore(savedValidation);
        chapterListCache = savedCats;
    }

    private void rescaleForZoom() {
        int cl = sidebarVisualW(), cr = width;
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
            if (n == null) continue;
            NodeHitbox btn = e.getValue();
            int cx = n.getCustomX() != 0 ? n.getCustomX() : 20;
            int cy = n.getCustomY() != 0 ? n.getCustomY() : 40;
            int sz = scaledNodeSize(n);
            int sx = (int) (cx * posZoom()) + viewOffX + cl;
            int sy = (int) (cy * posZoom()) + viewOffY + HEADER_H;
            btn.x = sx;
            btn.y = sy;
            btn.w = sz;
            btn.h = sz;
            btn.visible = sx + sz > cl && sx < cr && sy + sz > HEADER_H && sy < height;
            int[] pos = nodeScreenPos.get(e.getKey());
            if (pos != null) {
                pos[0] = sx;
                pos[1] = sy;
            }
        }
        buildLineCache();
    }

    private void panCanvas(int dx, int dy) {
        int cl = sidebarVisualW(), cr = width;
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
            QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
            int nsz = n != null ? scaledNodeSize(n) : sz;
            btn.visible = nx + nsz > cl && nx < cr && ny + nsz > HEADER_H && ny < height;
        }

        depLineRenderer.panShift(dx, dy);
    }

    private void openWiki() {
        if (minecraft == null) return;
        PhoenixTheme theme = PhoenixTheme.current();
        WikiTheme wikiTheme = new WikiTheme(
                theme.bg.getColor(), theme.panel.getColor(), theme.header.getColor(), theme.border.getColor(),
                theme.accent.getColor(), theme.text.getColor(), theme.textDim.getColor(), theme.textFaint.getColor(),
                theme.done.getColor(), theme.activeColor.getColor());
        minecraft.setScreen(new ChronicleWikiScreen(this, wikiTheme));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tryHandleDragCancelEscape(key)) return true;

        boolean ctrl = (mods & 2) != 0;
        boolean shift = (mods & 1) != 0;

        if (tryHandleSmokeTestKeybind(key)) return true;

        if (tryHandleQuickKeybinds(key, scan, ctrl, mods)) return true;
        if (key == 256 && tryHandleEscapeDispatch()) return true;

        if (key == 256 && isDevMode && !editorState.multiSelection.isEmpty()) {
            editorState.multiSelection.clear();
            bulkOpsPanel.reset();
            return true;
        }

        if (tryHandleUndoRedoKeybind(key, ctrl, shift)) return true;
        if (tryHandleActionKeybinds(key, scan, ctrl, shift)) return true;

        if (key == 68) {
            quickDepKeyDown = true;
        }

        return super.keyPressed(key, scan, mods);
    }

    private boolean tryHandleSmokeTestKeybind(int key) {
        if (isDevMode && key == 298) {
            ScreenClickSmokeTest.run(this);
            return true;
        }
        return false;
    }

    private boolean tryHandleDragCancelEscape(int key) {
        if (key == 256 && editorState.draggedNode != null) {
            if (editorState.bulkDragOrigPositions != null) {
                int count = editorState.bulkDragOrigPositions.size();
                for (Map.Entry<ResourceLocation, int[]> e : editorState.bulkDragOrigPositions.entrySet()) {
                    QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
                    if (n != null) {
                        n.setCustomPosition(e.getValue()[0], e.getValue()[1]);
                        saveNodeToDisk(n);
                    }
                }
                editorState.bulkDragOrigPositions = null;
                setFeedback("Move cancelled (%d quests)", count);
            } else {
                editorState.draggedNode.setCustomPosition(dragOrigX, dragOrigY);
                saveNodeToDisk(editorState.draggedNode);
                setFeedback("Move cancelled");
            }
            editorState.draggedNode = null;
            pickupPlaceActive = false;
            middleDragPickupActive = false;
            dragForceSnap = false;
            rebuild();
            return true;
        }
        return false;
    }

    private boolean tryHandleQuickKeybinds(int key, int scan, boolean ctrl, int mods) {
        if (ChronicleKeyBindings.SEARCH.matches(key, scan)) {
            openSearchOverlay();
            return true;
        }

        if (key == 80 && ctrl && (mods & 1) == 0) {
            FrameProfiler.setEnabled(!FrameProfiler.isEnabled());
            setFeedback(FrameProfiler.isEnabled() ? "§aProfiler ON" : "§7Profiler OFF");
            return true;
        }

        if (ChronicleKeyBindings.PIN_QUEST.matches(key, scan)) {
            if (lastHoveredNodeId != null && playerData != null) {
                playerData.togglePin(lastHoveredNodeId);
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(lastHoveredNodeId));
                QuestNode hovered = QuestTreeRegistry.getQuest(lastHoveredNodeId);
                boolean nowPinned = playerData.isPinned(lastHoveredNodeId);
                String pinPrefix = nowPinned ? "§dPinned" : "§7Unpinned";
                if (hovered != null) {
                    setFeedback("%s: %s", pinPrefix,
                            hovered.getEffectiveTitleRaw(minecraft.getSingleplayerServer()).getString());
                } else {
                    setFeedback(pinPrefix);
                }
            }
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_LINE_STYLE.matches(key, scan)) {
            QuestChroniclesSettings s = QuestChroniclesSettings.get();
            boolean nowSpline = s.isSplineLines();
            s.setLineStyle(
                    nowSpline ? QuestChroniclesSettings.LineStyle.STRAIGHT : QuestChroniclesSettings.LineStyle.SPLINE);
            s.save();
            setFeedback("Line style: %s", nowSpline ? "Straight" : "Spline");
            return true;
        }
        return false;
    }

    private boolean tryHandleEscapeDispatch() {
        if (sidebarPanel.contextMenuOpen()) {
            sidebarPanel.closeContextMenu();
            return true;
        }
        if (depLineRenderer.isContextMenuOpen()) {
            depLineRenderer.closeContextMenu();
            return true;
        }
        if (!unlockPathHighlight.isEmpty()) {
            unlockPathHighlight.clear();
            return true;
        }
        for (TogglePanel p : List.of(validationPanel, statsPanel)) {
            if (p.isOpen()) {
                p.close();
                return true;
            }
        }
        if (ctxOpen) {
            ctxOpen = false;
            ctxMoveCatOpen = false;
            return true;
        }
        if (pictureCtxMenu.isOpen()) {
            pictureCtxMenu.close();
            return true;
        }
        if (pictureEditMode != null) {
            finalizePictureEdit();
            setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
            return true;
        }
        if (nodeSizeEditMode != null) {
            finalizeNodeSizeEdit();
            setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
            softRebuild();
            return true;
        }
        return false;
    }

    private boolean tryHandleUndoRedoKeybind(int key, boolean ctrl, boolean shift) {
        if (ctrl && isDevMode) {
            if (key == 90 && !shift) {
                undoRedo.undo();
                return true;
            }
            if (key == 89 || (key == 90 && shift)) {
                undoRedo.redo();
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleActionKeybinds(int key, int scan, boolean ctrl, boolean shift) {
        if (ChronicleKeyBindings.FIT_TO_CANVAS.matches(key, scan)) {
            fitToCanvas();
            return true;
        }

        if (ChronicleKeyBindings.OPEN_DEV_WIKI.matches(key, scan) && isDevMode) {
            openWiki();
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_VALIDATION.matches(key, scan) && !ctrl && isDevMode) {
            validationPanel.toggle();
            return true;
        }

        if (ChronicleKeyBindings.IMPORT_FTB.matches(key, scan) && isDevMode) {
            runFtbImport();
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_SUBGRAPH.matches(key, scan) && isDevMode) {
            editorState.subgraphMode = !editorState.subgraphMode;
            if (editorState.subgraphMode) rebuildSubgraph();
            return true;
        }

        if (key == 67 && ctrl && !shift && isDevMode && editorState.selectedNode != null) {
            questCopy(editorState.selectedNode);
            return true;
        }

        if (key == 86 && ctrl && !shift && isDevMode) {
            questPaste();
            return true;
        }

        if (key == 68 && ctrl && !shift && isDevMode && editorState.selectedNode != null) {
            duplicateQuest(editorState.selectedNode);
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_MINIMAP.matches(key, scan)) {
            minimapOpen = !minimapOpen;
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_STATS.matches(key, scan) && isDevMode) {
            toggleStatsPanel();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == 68) {
            quickDepKeyDown = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void questCopy(QuestNode node) {
        questEditOps.questCopy(node);
    }

    @Override
    public void questPaste() {
        questEditOps.questPaste();
    }

    @Override
    public void chainMultiSelection() {
        questEditOps.chainMultiSelection();
    }

    @Override
    public void fanFromLeftmost() {
        questEditOps.fanFromLeftmost();
    }

    private void runFtbImport() {
        questEditOps.runFtbImport();
    }

    @Override
    public void duplicateQuest(QuestNode source) {
        questEditOps.duplicateQuest(source);
    }

    @Override
    public void createLinkStubAt(int canvasX, int canvasY, QuestNode target) {
        questEditOps.createLinkStubAt(canvasX, canvasY, target);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tryHandleMoveCatDropdownScroll(mx, my, delta)) return true;
        if (tryHandleNodeSizeEditScroll(delta)) return true;
        if (tryHandlePictureEditScroll(delta)) return true;

        int cl = sidebarW(), cr = width;
        if (mx <= cl && my > HEADER_H) {

            sidebarPanel.scrollBy(delta, sidebarContentHeight(), sidebarScrollAreaHeight());
            return true;
        }
        if (mx <= cl || mx >= cr || my <= HEADER_H) return super.mouseScrolled(mx, my, delta);

        return handleCanvasZoomScroll(mx, my, delta, cl, cr);
    }

    private boolean tryHandleMoveCatDropdownScroll(double mx, double my, double delta) {
        if (ctxMoveCatOpen && ctxNode != null) {
            List<String> cats = buildChapterList();
            cats.remove("ALL");
            int subX = ctxMoveCatX(cats.size());
            int subY = ctxMoveCatYClamped(buildCtxItems(), cats.size());
            int visibleRows = Math.min(cats.size(), CTX_MOVE_CAT_MAX_ROWS);
            int subH = visibleRows * CTX_ROW + 4;
            if (mx >= subX && mx <= subX + CTX_W && my >= subY && my <= subY + subH) {
                int maxScroll = Math.max(0, cats.size() - CTX_MOVE_CAT_MAX_ROWS);
                ctxMoveCatScroll = Math.max(0, Math.min(maxScroll, ctxMoveCatScroll - (int) Math.signum(delta)));
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleNodeSizeEditScroll(double delta) {
        if (nodeSizeEditMode != null) {
            float step = 1.1f;
            if (hasShiftDown()) step = 1f + (step - 1f) * 0.3f;
            if (hasControlDown()) step = 1f + (step - 1f) * 0.3f;
            float factor = delta > 0 ? step : (1f / step);
            int newPx = Math.round(nodeSizeEditMode.getNodePixelSize() * factor);
            nodeSizeEditMode.setSizeOverridePx(newPx);
            dragController.refreshNodeScreenPos(nodeSizeEditMode);
            setFeedback("Size: %dpx  (scroll to resize - shift/ctrl for finer steps, drag to move, " +
                    "right-click/Esc to finish)", nodeSizeEditMode.getNodePixelSize());
            return true;
        }
        return false;
    }

    private boolean tryHandlePictureEditScroll(double delta) {
        if (pictureEditMode != null) {

            float step = hasShiftDown() ? 1.05f : 1.2f;
            float factor = delta > 0 ? step : (1f / step);
            pictureEditMode.w = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.w * factor));
            pictureEditMode.h = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.h * factor));
            return true;
        }
        return false;
    }

    private boolean handleCanvasZoomScroll(double mx, double my, double delta, int cl, int cr) {
        float oldPosZoom = posZoom();
        float oldZoom = zoom;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom + (float) delta * ZOOM_STEP));
        if (zoom == oldZoom) return true;
        float newPosZoom = posZoom();

        if (oldPosZoom != newPosZoom) {
            int canvasW = cr - cl, canvasH = height - HEADER_H;
            boolean cursorAnchored = ChronicleKeyBindings.CURSOR_ZOOM.isDown();
            float anchorX = cursorAnchored ? (float) mx - cl : canvasW / 2f;
            float anchorY = cursorAnchored ? (float) my - HEADER_H : canvasH / 2f;
            float worldCx = (anchorX - viewOffX) / oldPosZoom;
            float worldCy = (anchorY - viewOffY) / oldPosZoom;
            viewOffX = (int) (anchorX - worldCx * newPosZoom);
            viewOffY = (int) (anchorY - worldCy * newPosZoom);
        }

        rescaleForZoom();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (tryHandleSidebarContextMenuClick(mx, my, btn)) return true;
        if (tryHandleDepLineContextMenuClick(mx, my, btn)) return true;
        if (tryHandlePictureContextMenuClick(mx, my, btn)) return true;
        if (tryHandleNodeContextMenuClick(mx, my, btn)) return true;
        if (tryHandleNonDevCtxMenuClick(mx, my, btn)) return true;

        if (tryHandleNodeResizeModeClick(btn)) return true;
        if (tryHandlePictureEditModeClick(btn)) return true;

        if (btn == 0 && tutorialOverlay.mouseClicked(this, mx, my, btn)) return true;

        if (tryHandleMinimapClick(mx, my, btn)) return true;

        int cl = sidebarW(), cr = width;

        if (btn == 0) {
            if (tryHandleLeftClickUi(mx, my, cl, cr)) return true;
        }

        if (tryHandleLangExportClick(mx, my, btn)) return true;

        if (tryHandleSidebarRowContextMenuClick(mx, my, btn)) return true;

        if (bulkOpsPanel.mouseClicked(mx, my, btn, cl)) return true;

        if (tryHandleMultiSelectToggleClick(mx, my, btn)) return true;

        if (tryHandlePickupPlaceReleaseClick(btn)) return true;

        if (tryHandleMiddleDragReleaseClick(btn)) return true;

        if (tryHandleQuickDepDragStart(mx, my, btn)) return true;

        if (tryHandleShiftDragStart(mx, my, btn, cl)) return true;

        if (tryHandleMiddleClickNodeDragStart(mx, my, btn)) return true;

        if (tryHandleShiftRightClickForceComplete(mx, my, btn, cl, cr)) return true;

        if (tryHandleDevRightClickContextMenu(mx, my, btn, cl, cr)) return true;

        handleNonDevRightClick(mx, my, btn, cl, cr);

        if (tryHandleCanvasClick(mx, my, btn, cl, cr)) return true;

        return super.mouseClicked(mx, my, btn);
    }

    private boolean tryHandleSidebarContextMenuClick(double mx, double my, int btn) {
        if (sidebarPanel.contextMenuOpen()) {
            if (btn == 0) sidebarPanel.handleContextMenuClick((int) mx, (int) my);
            else sidebarPanel.closeContextMenu();
            return true;
        }
        return false;
    }

    private boolean tryHandleNodeResizeModeClick(int btn) {
        if (nodeSizeEditMode != null) {
            if (btn == 1) {
                finalizeNodeSizeEdit();
                setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
                softRebuild();
            }
            return true;
        }
        return false;
    }

    private boolean tryHandlePictureEditModeClick(int btn) {
        if (pictureEditMode != null) {
            if (btn == 1) {
                finalizePictureEdit();
                setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
            }
            return true;
        }
        return false;
    }

    private boolean tryHandleMinimapClick(double mx, double my, int btn) {
        if (btn == 0 && isInMinimap(mx, my)) {
            mmDragging = true;
            minimapPanTo(mx, my, sidebarW());
            softRebuild();
            return true;
        }
        return false;
    }

    private boolean tryHandleLeftClickUi(double mx, double my, int cl, int cr) {
        if (tryHandleQuestbookTitleClick(mx, my)) return true;
        if (tryHandleHeaderBarClick(mx, my, cr)) return true;
        if (tryHandleToolbarButtonClick(mx, my)) return true;
        if (tryHandleFilterPillClick(mx, my, cl, cr)) return true;
        if (tryHandleSidebarCollapseToggleClick(mx, my)) return true;
        if (tryHandleSidebarPanelClick(mx, my)) return true;
        return false;
    }

    private boolean tryHandleQuestbookTitleClick(double mx, double my) {
        if (questbookTitleHovered((int) mx, (int) my)) {
            if (minecraft != null) minecraft.setScreen(new QuestbookTitleScreen(this));
            return true;
        }
        return false;
    }

    private boolean tryHandleHeaderBarClick(double mx, double my, int cr) {
        if (my < 0 || my >= TOOLBAR_Y) return false;

        int[][] layout = computeHeaderBarLayout(cr);
        int[] claimBtn = layout[0], gridBtn = layout[1], subgraphBtn = layout[2];

        if (claimBtn != null && hitsRect(claimBtn, mx, my)) {
            if (minecraft != null) minecraft.setScreen(new ClaimRewardsScreen(this));
            return true;
        }

        if (hitsRect(gridBtn, mx, my)) {
            for (int gi = 0; gi < GRID_SNAP_CYCLE.length; gi++) {
                if (GRID_SNAP_CYCLE[gi] == gridSnap) {
                    gridSnap = GRID_SNAP_CYCLE[(gi + 1) % GRID_SNAP_CYCLE.length];
                    break;
                }
            }
            return true;
        }

        if (subgraphBtn != null && hitsRect(subgraphBtn, mx, my)) {
            editorState.subgraphMode = !editorState.subgraphMode;
            if (editorState.subgraphMode) rebuildSubgraph();
            return true;
        }

        return false;
    }

    private static boolean hitsRect(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    int[][] computeHeaderBarLayout(int cr) {
        String zoomStr2 = Math.round(zoom * 100) + "%";
        int zw2 = font.width(zoomStr2);
        int zx2 = cr - zw2 - 10;

        int[] claimBtn = null;
        int unclaimedCount2 = unclaimedRewardCount();
        if (unclaimedCount2 > 0) {
            String claimLabel2 = "🎁 " + unclaimedCount2;
            int cw2 = font.width(claimLabel2);
            int cpx2 = zx2 - cw2 - 18;
            claimBtn = new int[] { cpx2 - 3, 3, cpx2 + cw2 + 5, 16 };
            zx2 = cpx2;
        }

        String gridLabel2 = !gridSnapEnabled ? "Grid: off" :
                (gridSnap == 1) ? "Grid: free" : "Grid: " + gridSnap;
        int gw2 = font.width(gridLabel2);
        int gpx2 = zx2 - gw2 - 18;
        int[] gridBtn = { gpx2 - 3, 3, gpx2 + gw2 + 5, 16 };

        int[] subgraphBtn = null;
        if (isDevMode) {
            String sgLabel2 = editorState.subgraphMode ? "Subgraph: " + subgraphNodes.size() : "Subgraph";
            int sgw2 = font.width(sgLabel2);
            int sgx2 = gpx2 - sgw2 - 18;
            subgraphBtn = new int[] { sgx2 - 3, 3, sgx2 + sgw2 + 5, 16 };
        }

        return new int[][] { claimBtn, gridBtn, subgraphBtn };
    }

    private boolean tryHandleToolbarButtonClick(double mx, double my) {
        if (my >= TOOLBAR_Y && my < HEADER_H) {
            if (hitsToolbarBtn("fit", mx, my)) {
                fitToCanvas();
                return true;
            }
            if (hitsToolbarBtn("settings", mx, my) && minecraft != null) {
                minecraft.setScreen(new SettingsScreen(this));
                return true;
            }
            if (isDevMode && hitsToolbarBtn("wiki", mx, my) && minecraft != null) {
                openWiki();
                return true;
            }
            if (hitsToolbarBtn("hideDone", mx, my)) {
                hideCompleted = !hideCompleted;
                softRebuild();
                return true;
            }
            if (hitsToolbarBtn("map", mx, my)) {
                minimapOpen = !minimapOpen;
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleFilterPillClick(double mx, double my, int cl, int cr) {
        int[][] pills = filterPillBounds(cl, cr);
        for (int i = 0; i < toolbarPanel.filterKeyCount(); i++) {
            int[] b = pills[i];
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                stateFilter = toolbarPanel.filterKey(i);
                editorState.selectedNode = null;
                softRebuild();
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleSidebarCollapseToggleClick(double mx, double my) {
        if (!sidebarPanel.isHoverSidebar() && sidebarCollapseToggleHovered((int) mx, (int) my)) {
            sidebarPanel.setCollapsed(!sidebarPanel.collapsed());
            sidebarPanel.resetScroll();
            rebuild();
            return true;
        }
        return false;
    }

    private boolean tryHandleSidebarPanelClick(double mx, double my) {
        if (!isSidebarNarrow()) {

            if (gearHovered((int) mx, (int) my) && minecraft != null) {
                minecraft.setScreen(new LangEditorScreen(this));
                return true;
            }

            if (newCatButtonHovered((int) mx, (int) my)) {
                if (minecraft != null) minecraft.setScreen(new NewChapterChoiceScreen(this));
                return true;
            }

            int scrollTop = HEADER_H + 1 + SidebarPanel.SIDEBAR_COLLAPSE_TOGGLE_H;
            int scrollBottom = scrollTop + sidebarScrollAreaHeight();
            if (mx < sidebarW() - 1 && my >= scrollTop && my < scrollBottom) {
                for (SidebarRow row : buildSidebarRows()) {
                    if (my < row.y() || my >= row.y() + row.height()) continue;

                    if (isDevMode) {
                        sidebarPanel.setDragRow(row);
                        sidebarPanel.setDragStart((int) mx, (int) my);
                        sidebarPanel.setDragMoved(false);
                        return true;
                    }
                    if (row.isFolder()) {
                        CategoryRegistry.toggleCollapsed(row.id());
                        CategoryRegistry.save();
                        rebuild();
                    } else if (row.locked()) {

                        setFeedback("§7Locked. Complete a quest in the parent chapter first");
                    } else {
                        selectedChapter = row.id();
                        editorState.selectedNode = null;
                        PhantasiaCompat.closePreview(phantasiaPreview);
                        phantasiaPreview = null;

                        ctxOpen = false;
                        ctxMoveCatOpen = false;
                        rebuild();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryHandleLangExportClick(double mx, double my, int btn) {
        if (btn == 1 && gearHovered((int) mx, (int) my) && isDevMode) {
            Path base = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            LangEditorScreen.writeEnUsJson(base);

            ChroniclesLangPack.reload();
            setFeedback("§aExported lang/en_us.json");
            return true;
        }
        return false;
    }

    private boolean tryHandleSidebarRowContextMenuClick(double mx, double my, int btn) {
        if (btn == 1 && isDevMode && !isSidebarNarrow()) {
            SidebarRow hitRow = sidebarRowAt(buildSidebarRows(), (int) mx, (int) my);
            if (hitRow != null) {
                openSidebarContextMenu(hitRow, (int) mx, (int) my);
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleDepLineContextMenuClick(double mx, double my, int btn) {
        if (depLineRenderer.isContextMenuOpen() && btn == 0) {

            depLineRenderer.handleContextMenuClick((int) mx, (int) my, width, height,
                    this::buildLineCache, this::setFeedback, this::openLineSettingsFor, this::pushUndo);
            depLineRenderer.closeContextMenu();
            return true;
        }
        if (depLineRenderer.isContextMenuOpen()) {
            depLineRenderer.closeContextMenu();
            return true;
        }
        return false;
    }

    private boolean tryHandlePictureContextMenuClick(double mx, double my, int btn) {
        if (pictureCtxMenu.isOpen() && btn == 0) {
            pictureCtxMenu.mouseClicked(this, mx, my, btn);
            return true;
        }
        if (pictureCtxMenu.isOpen()) {
            pictureCtxMenu.close();
            return true;
        }
        return false;
    }

    private boolean tryHandleNodeContextMenuClick(double mx, double my, int btn) {
        if (ctxOpen && btn == 0) {
            if (handleCtxClick((int) mx, (int) my)) return true;
            ctxOpen = false;
            ctxMoveCatOpen = false;
            return true;
        }
        return false;
    }

    private boolean tryHandleMultiSelectToggleClick(double mx, double my, int btn) {
        if (btn == 0 && isDevMode && hasControlDown() && !hasShiftDown()) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    if (editorState.multiSelection.contains(e.getKey())) editorState.multiSelection.remove(e.getKey());
                    else editorState.multiSelection.add(e.getKey());
                    return true;
                }
            }

            editorState.multiSelection.clear();
            return true;
        }
        return false;
    }

    private boolean tryHandlePickupPlaceReleaseClick(int btn) {
        if (btn == 0 && pickupPlaceActive && editorState.draggedNode != null) {
            boolean wasBulk = editorState.bulkDragOrigPositions != null;
            dragController.finalizeDragRelease();
            editorState.draggedNode = null;
            pickupPlaceActive = false;
            dragForceSnap = false;
            softRebuild();
            if (!wasBulk) setFeedback("Placed");
            return true;
        }
        return false;
    }

    private boolean tryHandleMiddleDragReleaseClick(int btn) {
        if (btn == 2 && middleDragPickupActive && editorState.draggedNode != null) {
            boolean wasBulk = editorState.bulkDragOrigPositions != null;
            dragController.finalizeDragRelease();
            editorState.draggedNode = null;
            middleDragPickupActive = false;
            dragForceSnap = false;
            softRebuild();
            if (!wasBulk) setFeedback("Placed");
            return true;
        }
        return false;
    }

    private boolean tryHandleQuickDepDragStart(double mx, double my, int btn) {
        if (btn == 0 && isDevMode && (quickDepKeyDown || (hasAltDown() && !hasShiftDown()))) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    linkDragSource = QuestTreeRegistry.getQuest(e.getKey());
                    linkDragX = (int) mx;
                    linkDragY = (int) my;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryHandleShiftDragStart(double mx, double my, int btn, int cl) {
        if (btn == 0 && isDevMode && hasShiftDown()) {

            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    editorState.draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (editorState.draggedNode != null) {
                        pickupPlaceActive = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                                .getNodeMoveMode() ==
                                net.phoenixvine.chronicles.codec.QuestChroniclesSettings.NodeMoveMode.PICKUP_PLACE;
                        final int preX = editorState.draggedNode.getCustomX(),
                                preY = editorState.draggedNode.getCustomY();
                        dragOrigX = preX;
                        dragOrigY = preY;
                        final QuestNode capturedNode = editorState.draggedNode;
                        dragController.beginDragUndo(capturedNode, preX, preY);
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        editorState.selectedNode = editorState.draggedNode;
                        if (editorState.subgraphMode) rebuildSubgraph();
                    }
                    return true;
                }
            }

            QuestGroup hitGrp = groupAtLabelBar(mx, my, cl);
            if (hitGrp != null) {
                editorState.draggedGroup = hitGrp;
                int sx = (int) (hitGrp.getX() * posZoom()) + viewOffX + cl;
                int sy = (int) (hitGrp.getY() * posZoom()) + viewOffY + HEADER_H;
                groupDragGrabX = (int) mx - sx;
                groupDragGrabY = (int) my - sy;
                return true;
            }

            BackgroundPictureConfig.Picture hitPic = pictureAt(mx, my, cl);
            if (hitPic != null) {

                pictureDragStartX = hitPic.x;
                pictureDragStartY = hitPic.y;
                draggedPicture = hitPic;
                int[] rect = BackgroundPictureRenderer.screenRect(hitPic, cl, HEADER_H, posZoom(), viewOffX, viewOffY);
                pictureDragGrabX = (int) mx - rect[0];
                pictureDragGrabY = (int) my - rect[1];
                return true;
            }
        }
        return false;
    }

    private boolean tryHandleMiddleClickNodeDragStart(double mx, double my, int btn) {
        if (btn == 2 && isDevMode) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    editorState.draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (editorState.draggedNode != null) {
                        final int preX = editorState.draggedNode.getCustomX(),
                                preY = editorState.draggedNode.getCustomY();
                        dragOrigX = preX;
                        dragOrigY = preY;
                        final QuestNode capturedNode = editorState.draggedNode;
                        dragController.beginDragUndo(capturedNode, preX, preY);
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        editorState.selectedNode = editorState.draggedNode;
                        dragForceSnap = true;
                        middleDragPickupActive = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                                .isMiddleClickPickupPlace();
                        if (editorState.subgraphMode) rebuildSubgraph();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryHandleShiftRightClickForceComplete(double mx, double my, int btn, int cl, int cr) {
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
        return false;
    }

    private boolean tryHandleDevRightClickContextMenu(double mx, double my, int btn, int cl, int cr) {
        if (btn == 1 && isDevMode && mx > cl && mx < cr) {

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

            if (hit == null && hitGrp == null) {
                BackgroundPictureConfig.Picture hitPic = pictureAt(mx, my, cl);
                if (hitPic != null) {
                    ctxOpen = false;
                    pictureCtxMenu.open((int) mx, (int) my, hitPic);
                    return true;
                }
            }

            if (hit == null && hitGrp == null) {
                depLineRenderer.tryOpenContextMenuAt((int) mx, (int) my, 6);
            }
            openCtx((int) mx, (int) my, hit, hitGrp);
            return true;
        }
        return false;
    }

    private void handleNonDevRightClick(double mx, double my, int btn, int cl, int cr) {
        if (btn == 1 && !isDevMode && mx > cl && mx < cr) {
            boolean hitNode = false;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null && getState(node) == QuestState.LOCKED) {
                        nonDevCtxNode = node;
                        nonDevCtxX = (int) mx;
                        nonDevCtxY = (int) my;
                        hitNode = true;
                    }
                    break;
                }
            }
            if (!hitNode) {
                unlockPathHighlight.clear();
                nonDevCtxNode = null;

                if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedChapter));
            }
        }
    }

    private List<CtxItem> buildNonDevCtxItems() {
        List<CtxItem> items = new ArrayList<>();
        if (nonDevCtxNode == null) return items;
        items.add(new CtxItem("👁 Show unlock path", "§b", false, false,
                () -> computeUnlockPath(nonDevCtxNode)));
        QuestNode linkTarget = nonDevCtxNode.isLinkStub() ? resolveLinkTarget(nonDevCtxNode) : null;
        if (linkTarget != null) {
            items.add(new CtxItem("🔗 Jump to linked quest", "§b", false, false,
                    () -> navigateToNode(linkTarget)));
        }
        return items;
    }

    private boolean tryHandleNonDevCtxMenuClick(double mx, double my, int btn) {
        if (nonDevCtxNode != null && btn == 0) {
            List<CtxItem> items = buildNonDevCtxItems();
            int iy = nonDevCtxY;
            for (CtxItem item : items) {
                if (mx >= nonDevCtxX && mx <= nonDevCtxX + CTX_W && my >= iy && my <= iy + CTX_ROW) {
                    item.action().run();
                    nonDevCtxNode = null;
                    return true;
                }
                iy += CTX_ROW;
            }
            nonDevCtxNode = null;
            return true;
        }
        return false;
    }

    private void renderNonDevCtxMenu(GuiGraphics g, int mx, int my) {
        if (nonDevCtxNode == null) return;
        List<CtxItem> items = buildNonDevCtxItems();
        int menuH = items.size() * CTX_ROW + 2;
        int x = Math.min(nonDevCtxX, width - CTX_W - 4);
        int y = Math.min(nonDevCtxY, height - menuH - 4);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.flush();
        RenderSystem.disableDepthTest();

        g.fill(x, y, x + CTX_W, y + menuH, C_CTX_BG);
        g.fill(x, y, x + CTX_W, y + 1, C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + CTX_W, y + menuH, C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, C_CTX_BORDER);
        g.fill(x + CTX_W - 1, y, x + CTX_W, y + menuH, C_CTX_BORDER);

        int iy = y + 1;
        for (CtxItem item : items) {
            boolean hov = mx >= x && mx <= x + CTX_W && my >= iy && my <= iy + CTX_ROW;
            if (hov) g.fill(x + 1, iy, x + CTX_W - 1, iy + CTX_ROW, C_CTX_HOVER);
            g.drawString(font, item.color() + item.label(), x + 6, iy + 4, C_CTX_TEXT);
            iy += CTX_ROW;
        }

        RenderSystem.enableDepthTest();
        g.flush();
        g.pose().popPose();
    }

    private boolean tryHandleCanvasClick(double mx, double my, int btn, int cl, int cr) {
        if (btn == 0 && mx > cl && mx < cr && my > HEADER_H) {

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

                        int canvasX = (int) ((imx - cl - viewOffX) / posZoom());
                        int canvasY = (int) ((imy - HEADER_H - viewOffY) / posZoom());
                        lastCanvasClickTime = 0;
                        minecraft.setScreen(new QuestCreatorScreen(this, canvasX, canvasY, selectedChapter));
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
        return false;
    }

    private boolean handleCtxClick(int mx, int my) {
        List<CtxItem> items = buildCtxItems();

        int lmx = Math.round(ctxX + (mx - ctxX) / ctxScale);
        int lmy = Math.round(ctxY + (my - ctxY) / ctxScale);
        int x = ctxX, y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW;

        for (CtxItem item : items) {
            if (item.isSep()) {
                y += CTX_SEP;
                continue;
            }
            if (lmx >= x && lmx <= x + CTX_W && lmy >= y && lmy <= y + CTX_ROW) {
                item.action().run();
                return true;
            }
            y += CTX_ROW;
        }

        if (ctxMoveCatOpen) {
            List<String> cats = buildChapterList();
            cats.remove("ALL");
            int subX = ctxMoveCatX(cats.size());
            int subY = ctxMoveCatYClamped(items, cats.size());
            int visibleRows = Math.min(cats.size(), CTX_MOVE_CAT_MAX_ROWS);
            for (int i = ctxMoveCatScroll; i < Math.min(cats.size(), ctxMoveCatScroll + visibleRows); i++) {
                int ry = subY + (i - ctxMoveCatScroll) * CTX_ROW;
                if (mx >= subX && mx <= subX + CTX_W && my >= ry && my <= ry + CTX_ROW) {
                    String newCat = cats.get(i);
                    if (ctxNode != null) {
                        QuestNode movedNode = ctxNode;
                        String oldCat = movedNode.getChapter();
                        movedNode.setChapter(newCat);
                        saveNodeChapterToDisk(movedNode, newCat);
                        setFeedback("Moved to %s", friendly(newCat));
                        pushUndo("Undo: quest moved back to " + friendly(oldCat), () -> {
                            movedNode.setChapter(oldCat);
                            saveNodeChapterToDisk(movedNode, oldCat);
                            rebuild();
                        }, () -> {
                            movedNode.setChapter(newCat);
                            saveNodeChapterToDisk(movedNode, newCat);
                            rebuild();
                        });
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
    public void mouseMoved(double mx, double my) {
        if ((pickupPlaceActive || middleDragPickupActive) && editorState.draggedNode != null) {
            dragController.updateDraggedNodeScreenPos(mx, my, dragController.currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(editorState.draggedNode.getId(), this::nodeCenterForLine, posZoom(),
                    QuestChroniclesSettings.get());
        }
        super.mouseMoved(mx, my);
    }

    @Nullable
    private int[] nodeCenterForLine(ResourceLocation id) {
        int[] pos = nodeScreenPos.get(id);
        QuestNode n = QuestTreeRegistry.getQuest(id);
        if (pos == null || n == null) return null;
        int sz = scaledNodeSize(n);
        return new int[] { pos[0] + sz / 2, pos[1] + sz / 2 };
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (tryHandleSidebarRowDragThreshold(mx, my, btn)) return true;
        if (tryHandleNodeSizeEditDrag(btn, dx, dy)) return true;
        if (tryHandlePictureEditDrag(btn, dx, dy)) return true;

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
            if (tryHandleLeftButtonDragMove(mx, my, dx, dy)) return true;
        }

        if (btn == 2 && editorState.draggedNode != null) {
            dragController.updateDraggedNodeScreenPos(mx, my, dragController.currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(editorState.draggedNode.getId(), this::nodeCenterForLine, posZoom(),
                    QuestChroniclesSettings.get());
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private boolean tryHandleSidebarRowDragThreshold(double mx, double my, int btn) {
        if (btn == 0 && sidebarPanel.dragRow() != null) {
            if (!sidebarPanel.dragMoved() &&
                    (Math.abs(mx - sidebarPanel.dragStartX()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD ||
                            Math.abs(my - sidebarPanel.dragStartY()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD)) {
                sidebarPanel.setDragMoved(true);
            }
            return true;
        }
        return false;
    }

    private boolean tryHandleNodeSizeEditDrag(int btn, double dx, double dy) {
        if (nodeSizeEditMode != null) {
            if (btn == 0) {
                int snap = dragController.currentDragSnap();
                nodeSizeDragAccX += dx / posZoom();
                nodeSizeDragAccY += dy / posZoom();
                int stepX = (int) Math.round(nodeSizeDragAccX / snap) * snap;
                int stepY = (int) Math.round(nodeSizeDragAccY / snap) * snap;
                if (stepX != 0 || stepY != 0) {
                    nodeSizeEditMode.setCustomPosition(
                            nodeSizeEditMode.getCustomX() + stepX,
                            nodeSizeEditMode.getCustomY() + stepY);
                    nodeSizeDragAccX -= stepX;
                    nodeSizeDragAccY -= stepY;
                    dragController.refreshNodeScreenPos(nodeSizeEditMode);
                    depLineRenderer.refreshEdgeEndpoints(nodeSizeEditMode.getId(), this::nodeCenterForLine, posZoom(),
                            QuestChroniclesSettings.get());
                }
            }
            return true;
        }
        return false;
    }

    private boolean tryHandlePictureEditDrag(int btn, double dx, double dy) {
        if (pictureEditMode != null) {
            if (btn == 0) {
                pictureEditMode.x += (float) (dx / posZoom());
                pictureEditMode.y += (float) (dy / posZoom());
            }
            return true;
        }
        return false;
    }

    private boolean tryHandleLeftButtonDragMove(double mx, double my, double dx, double dy) {
        if (editorState.draggedGroup != null) {
            int cl = sidebarW();
            int screenX = (int) mx - groupDragGrabX;
            int screenY = (int) my - groupDragGrabY;
            editorState.draggedGroup.setX((int) ((screenX - cl - viewOffX) / posZoom()));
            editorState.draggedGroup.setY((int) ((screenY - HEADER_H - viewOffY) / posZoom()));
            return true;
        }
        if (draggedPicture != null) {
            int cl = sidebarW();

            int screenX = (int) mx - pictureDragGrabX;
            int screenY = (int) my - pictureDragGrabY;
            float canvasX = (screenX - cl - viewOffX) / posZoom() + draggedPicture.w / 2f;
            float canvasY = (screenY - HEADER_H - viewOffY) / posZoom() + draggedPicture.h / 2f;
            draggedPicture.x = canvasX;
            draggedPicture.y = canvasY;
            return true;
        }
        if (editorState.draggedNode != null) {
            dragController.updateDraggedNodeScreenPos(mx, my, dragController.currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(editorState.draggedNode.getId(), this::nodeCenterForLine,
                    posZoom(),
                    QuestChroniclesSettings.get());
            return true;
        }
        if (isPanning) {
            viewOffX += (int) dx;
            viewOffY += (int) dy;
            pendingPanDX += (int) dx;
            pendingPanDY += (int) dy;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        resetNodeSizeDragAccumulator(btn);

        if (tryHandleSidebarRowDrop(mx, my, btn)) return true;

        if (tryHandleMinimapDragEnd(btn)) return true;

        if (tryHandleLinkDragEnd(mx, my, btn)) return true;

        if (btn == 0) {
            if (tryHandleLeftButtonDragRelease()) return true;
        }

        if (tryHandleMiddleButtonDragRelease(btn)) return true;

        return super.mouseReleased(mx, my, btn);
    }

    private void resetNodeSizeDragAccumulator(int btn) {
        if (btn == 0 && nodeSizeEditMode != null) {
            nodeSizeDragAccX = 0;
            nodeSizeDragAccY = 0;
        }
    }

    private boolean tryHandleSidebarRowDrop(double mx, double my, int btn) {
        if (btn == 0 && sidebarPanel.dragRow() != null) {
            SidebarRow source = sidebarPanel.dragRow();
            boolean moved = sidebarPanel.dragMoved();
            sidebarPanel.setDragRow(null);
            sidebarPanel.setDragMoved(false);
            if (!moved) {

                if (source.isFolder()) {
                    net.phoenixvine.chronicles.registry.CategoryRegistry.toggleCollapsed(source.id());
                    net.phoenixvine.chronicles.registry.CategoryRegistry.save();
                    rebuild();
                } else {
                    selectedChapter = source.id();
                    editorState.selectedNode = null;
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
        return false;
    }

    private boolean tryHandleMinimapDragEnd(int btn) {
        if (btn == 0 && mmDragging) {
            mmDragging = false;

            rescaleForZoom();
            return true;
        }
        return false;
    }

    private boolean tryHandleLinkDragEnd(double mx, double my, int btn) {
        if (btn == 0 && linkDragSource != null) {
            QuestNode src = linkDragSource;
            linkDragSource = null;
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode target = QuestTreeRegistry.getQuest(e.getKey());
                    if (target != null && target != src && !target.getPrerequisites().contains(src) &&
                            !src.getPrerequisites().contains(target)) {
                        target.addPrerequisite(src);
                        target.setPrereqLink(src.getId(), true);
                        saveNodePrereqsToDisk(target);
                        setFeedback("§aLinked: %s → prereq of %s", src.getId().getPath(), target.getId().getPath());
                        buildLineCache();
                        rebuild();
                        QuestNode undoTarget = target;
                        QuestNode undoSrc = src;
                        pushUndo("Undo: dependency link removed", () -> {
                            undoTarget.removePrerequisite(undoSrc);
                            undoTarget.setPrereqLink(undoSrc.getId(), false);
                            saveNodePrereqsToDisk(undoTarget);
                            buildLineCache();
                            rebuild();
                        }, () -> {
                            undoTarget.addPrerequisite(undoSrc);
                            undoTarget.setPrereqLink(undoSrc.getId(), true);
                            saveNodePrereqsToDisk(undoTarget);
                            buildLineCache();
                            rebuild();
                        });
                    } else if (target != null && src.getPrerequisites().contains(target)) {
                        setFeedback("§cCan't link. Would create a dependency cycle");
                    } else if (target != null && target.getPrerequisites().contains(src)) {
                        setFeedback("§eAlready a prerequisite");
                    }
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean tryHandleLeftButtonDragRelease() {
        if (editorState.draggedGroup != null) {
            QuestGroupManager.save(groupsConfigPath());
            editorState.draggedGroup = null;
            return true;
        }
        if (draggedPicture != null) {
            BackgroundPictureConfig.save();
            BackgroundPictureConfig.Picture movedPic = draggedPicture;
            float startX = pictureDragStartX, startY = pictureDragStartY;
            float endX = movedPic.x, endY = movedPic.y;
            if (startX != endX || startY != endY) {
                pushUndo("Undo: picture moved back", () -> {
                    movedPic.x = startX;
                    movedPic.y = startY;
                    BackgroundPictureConfig.save();
                }, () -> {
                    movedPic.x = endX;
                    movedPic.y = endY;
                    BackgroundPictureConfig.save();
                });
            }
            draggedPicture = null;
            return true;
        }
        if (editorState.draggedNode != null && !pickupPlaceActive) {
            dragController.finalizeDragRelease();
            editorState.draggedNode = null;
            dragForceSnap = false;
            softRebuild();
            return true;
        }
        if (isPanning) {

            buildLineCache();
        }
        isPanning = false;
        return false;
    }

    private boolean tryHandleMiddleButtonDragRelease(int btn) {
        if (btn == 2 && editorState.draggedNode != null && !middleDragPickupActive) {
            dragController.finalizeDragRelease();
            editorState.draggedNode = null;
            dragForceSnap = false;
            softRebuild();
            return true;
        }
        return false;
    }

    @Override
    public List<CtxItem> buildCtxItems() {
        return ctxMenuBuilder.buildCtxItems();
    }

    private void openCtx(int x, int y, QuestNode node) {
        ctxMenuBuilder.openCtx(x, y, node);
    }

    private void openCtx(int x, int y, QuestNode node, @Nullable QuestGroup group) {
        ctxMenuBuilder.openCtx(x, y, node, group);
    }

    @Override
    public int menuHeight(List<CtxItem> items) {
        return ctxMenuBuilder.menuHeight(items);
    }

    @Override
    public int ctxMoveCatX(int catCount) {
        return ctxMenuBuilder.ctxMoveCatX(catCount);
    }

    @Override
    public int ctxMoveCatYClamped(List<CtxItem> items, int catCount) {
        return ctxMenuBuilder.ctxMoveCatYClamped(items, catCount);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        FrameProfiler.begin("TOTAL render()");

        refreshPalette();
        if (feedbackTimer > 0) feedbackTimer--;
        pendingDeferredDraws.clear();
        updateSidebarHoverPeek(mx, my);

        if (pendingPanDX != 0 || pendingPanDY != 0) {
            panCanvas(pendingPanDX, pendingPanDY);
            pendingPanDX = 0;
            pendingPanDY = 0;
        }

        dragController.handleLiveDragging(mx, my);

        int cl = sidebarVisualW();
        int cr = width;
        int sz = scaledNodeSize();

        long animTick = QuestChroniclesSettings.get().isReduceMotion() ? 0L : System.currentTimeMillis();

        FrameProfiler.begin("header");
        renderHeaderAndBaseLayout(g, mx, my, cl, cr);
        FrameProfiler.end("header");

        PhantasiaCompat.tickPreview(phantasiaPreview);

        switch (gridDisplayMode) {
            case ALWAYS -> dragController.renderSnapGridOverlay(g, cl, cr);
            case CURSOR_BOX -> dragController.renderSnapCursorBox(g, mx, my, cl, cr);
            case ON_DRAG -> {
                if (editorState.draggedNode != null) dragController.renderSnapGridOverlay(g, cl, cr);
            }
        }

        renderSidebarPanel(g, mx, my);

        renderCanvasLayers(g, mx, my, cl, cr, animTick);

        FrameProfiler.setCounter("screenWidgets", this.renderables.size());
        FrameProfiler.begin("widgets (super.render)");
        if (!renderingAsBackdrop) super.render(g, mx, my, partial);
        FrameProfiler.end("widgets (super.render)");

        renderDepLines(g, mx, my, cl, cr, animTick);

        renderNodesAndDetails(g, mx, my, cl, cr, sz);
        renderLinkDragHint(g);
        if (nodeSizeEditMode != null) {
            int[] pos = nodeScreenPos.get(nodeSizeEditMode.getId());
            if (pos != null) {
                int nsz = scaledNodeSize(nodeSizeEditMode);
                ChroniclesUIKit.drawBorder(g, pos[0] - 2, pos[1] - 2, nsz + 4, nsz + 4, 0xFFFFCC33);
            }
        }

        FrameProfiler.begin("overlays");
        renderScreenOverlays(g, mx, my, cl, cr, sz);
        FrameProfiler.end("overlays");

        if (editorState.draggedNode != null) dragController.renderDragSnapPosBox(g, mx, my);

        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, false);

        for (Runnable r : pendingDeferredDraws) r.run();
        pendingDeferredDraws.clear();

        if (minimapOpen) renderMinimap(g, mx, my, cl, cr);

        FrameProfiler.end("TOTAL render()");
        FrameProfiler.endFrame();
        if (FrameProfiler.isEnabled()) renderProfilerPanel(g);
    }

    private void renderHeaderAndBaseLayout(GuiGraphics g, int mx, int my, int cl, int cr) {
        renderBackground(g);

        int visW = sidebarVisualW();
        g.fill(0, 0, visW, height, palette.panelDark);

        ChapterConfig canvasCfg = selectedChapter.isEmpty() ? new ChapterConfig() :
                ChapterConfig.getEffective(selectedChapter);
        if (canvasCfg.getColorAlpha() > 0) {
            g.fill(visW, 0, cr, height, palette.bg);
        }

        g.fill(cr, 0, width, height, palette.panelDark);
        g.fill(cr, 0, cr + 1, height, palette.border);

        g.fill(0, 0, width, TOOLBAR_Y, palette.header);
        g.fill(0, TOOLBAR_Y - 1, width, TOOLBAR_Y, palette.border);
        String titlePrefix = testMode ? "§c⏵ PLAYER  §8⟫  §7" : "§8Chronicles  §8⟫  §7";

        int pillReserve = font.width(Math.round(zoom * 100) + "%") + 10;
        if (unclaimedRewardCount() > 0)
            pillReserve += font.width("🎁 " + unclaimedRewardCount()) + 18;
        pillReserve += font.width("Grid: off") + 18;
        if (isDevMode) pillReserve += font.width("⊛ Subgraph: 000") + 18;
        int titleMaxW = Math.max(20, (cr - pillReserve) - (cl + 8));
        String titleFull = titlePrefix + chapterBreadcrumb(selectedChapter);
        String titleToDraw = titleFull;
        if (font.width(net.minecraft.util.StringUtil.stripColor(titleFull)) > titleMaxW) {
            titleToDraw = font.plainSubstrByWidth(titleFull, titleMaxW - font.width("…")) + "…";
        }
        g.drawString(font, titleToDraw, cl + 8, 7, palette.text);
        if (testMode) g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFCC2222);
        if (pictureEditMode != null) {
            g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFFFCC33);
            String hint = "§e🖼 Editing picture. Scroll to resize (shift = fine), drag to move, right-click/Esc to finish";
            g.drawCenteredString(font, hint, (cl + cr) / 2, 7, 0xFFFFEEAA);
        }

        g.enableScissor(0, 0, sidebarVisualW(), TOOLBAR_Y);
        renderQuestbookTitle(g, mx, my);
        g.disableScissor();

        String zoomStr = Math.round(zoom * 100) + "%";
        int zw = font.width(zoomStr);
        int zx = cr - zw - 10, zy = 3;
        g.fill(zx - 3, zy, zx + zw + 3, zy + 13, 0x22FFFFFF);
        g.drawString(font, "§7" + zoomStr, zx, zy + 3, palette.textDim);

        int unclaimedCount = unclaimedRewardCount();
        if (unclaimedCount > 0) {
            String claimLabel = "§d🎁 " + unclaimedCount;
            int cw = font.width(net.minecraft.util.StringUtil.stripColor(claimLabel));
            int cpx = zx - cw - 18, cpy = 3;
            boolean claimHov = mx >= cpx - 3 && mx < cpx + cw + 5 && my >= cpy && my < cpy + 13;
            g.fill(cpx - 3, cpy, cpx + cw + 5, cpy + 13, claimHov ? 0x44FFFFFF : 0x33AA4488);
            g.drawString(font, claimLabel, cpx, cpy + 3, palette.textDim, false);
            if (claimHov) {
                pendingDeferredDraws.add(() -> g.renderTooltip(font,
                        Component.literal("§7" + unclaimedCount + " quest(s) with unclaimed rewards - click to open"),
                        mx, my));
            }
            zx = cpx;
        }

        String gridLabel = !gridSnapEnabled ? "§8Grid: §c§loff" :
                (gridSnap == 1) ? "§8Grid: §afree" : "§8Grid: §7" + gridSnap;
        int gw = font.width(net.minecraft.util.StringUtil.stripColor(gridLabel));
        int gpx = zx - gw - 18, gpy = 3;
        boolean gridHov = mx >= gpx - 3 && mx < gpx + gw + 5 && my >= gpy && my < gpy + 13;
        g.fill(gpx - 3, gpy, gpx + gw + 5, gpy + 13, gridHov ? 0x44FFFFFF : 0x22FFFFFF);
        g.drawString(font, gridLabel, gpx, gpy + 3, palette.textDim, false);
        if (gridHov) {

            pendingDeferredDraws.add(() -> g.renderTooltip(font,
                    Component.literal("§7Click to cycle canvas snap grid size"), mx, my));
        }

        if (isDevMode) {
            String sgLabel = editorState.subgraphMode ? "§b⊛ Subgraph: " + subgraphNodes.size() : "§8⊛ Subgraph";
            int sgw = font.width(net.minecraft.util.StringUtil.stripColor(sgLabel));
            int sgx = gpx - sgw - 18, sgy = 3;
            boolean sgHov = mx >= sgx - 3 && mx < sgx + sgw + 5 && my >= sgy && my < sgy + 13;
            g.fill(sgx - 3, sgy, sgx + sgw + 5, sgy + 13,
                    editorState.subgraphMode ? 0x4444CCFF : (sgHov ? 0x44FFFFFF : 0x22FFFFFF));
            g.drawString(font, sgLabel, sgx, sgy + 3, palette.textDim, false);
            if (sgHov) {
                pendingDeferredDraws.add(() -> g.renderComponentTooltip(font, List.of(
                        Component.literal("§b⊛ Subgraph mode"),
                        Component.literal("§7Dims every quest that isn't an ancestor or"),
                        Component.literal("§7descendant of the currently selected one,"),
                        Component.literal("§7isolating just its dependency chain."),
                        Component.literal("§8Click a quest to select it, then click this"),
                        Component.literal("§8pill (or press G) to toggle it on/off.")), mx, my));
            }
        }

        g.enableScissor(0, TOOLBAR_Y, width, HEADER_H);
        renderToolbar(g, mx, my, cl, cr);
        g.disableScissor();
    }

    private boolean sidebarCollapseToggleHovered(int mx, int my) {
        return sidebarPanel.collapseToggleHovered(mx, my);
    }

    private boolean questbookTitleHovered(int mx, int my) {
        return !isSidebarNarrow() && mx >= 0 && mx < sidebarW() - 1 && my >= 0 && my < TOOLBAR_Y;
    }

    private void renderQuestbookTitle(GuiGraphics g, int mx, int my) {
        if (isSidebarNarrow()) return;
        boolean hov = questbookTitleHovered(mx, my);
        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        if (hov) g.fill(0, 0, sidebarW() - 1, TOOLBAR_Y - 1, 0x14FFFFFF);

        net.minecraft.world.item.Item iconItem = s.getQuestbookIconItem();
        int iconY = (TOOLBAR_Y - 1 - 16) / 2;
        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), 3, iconY);

        String name = s.getQuestbookName();
        int maxW = sidebarW() - 22;
        if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - 4) + "…";
        g.drawString(font, (hov ? "§f" : "§7") + name, 21, 7, hov ? palette.text : palette.textDim, false);
    }

    private SidebarPanel.Colors sidebarColors() {
        return new SidebarPanel.Colors(palette.border, palette.borderLit, palette.text, palette.textDim,
                palette.textFaint, palette.panelDark,
                palette.selTab, palette.progFill, C_PROG_ACT);
    }

    private void renderSidebarPanel(GuiGraphics g, int mx, int my) {
        sidebarPanel.renderPanel(g, font, mx, my, width, height, sidebarColors(), isDevMode, selectedChapter,
                this::friendly, progressLookup(), attentionLookup(), rewardsLookup(), pendingDeferredDraws::add,
                buildChapterList());
    }

    private SidebarRow sidebarRowAt(List<SidebarRow> rows, int mx, int my) {
        return sidebarPanel.rowAt(rows, mx, my);
    }

    private void handleSidebarDrop(SidebarRow source, int mx, int my) {
        sidebarPanel.handleDrop(source, mx, my, this::friendly, progressLookup(), this::buildChapterList,
                this::setFeedback, this::rebuild, buildChapterList());
    }

    private void renderCanvasLayers(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("background");

        CanvasBackgroundRenderer.drawBackground(g, cl, HEADER_H, cr, height, selectedChapter, posZoom(), viewOffX,
                viewOffY, this.gridSnap);

        BackgroundPictureRenderer.render(g, cl, HEADER_H, cr, height, selectedChapter, posZoom(), viewOffX, viewOffY);
        if (pictureEditMode != null) {
            int[] rect = BackgroundPictureRenderer.screenRect(pictureEditMode, cl, HEADER_H, posZoom(), viewOffX,
                    viewOffY);
            ChroniclesUIKit.drawBorder(g, rect[0] - 1, rect[1] - 1, rect[2] - rect[0] + 2, rect[3] - rect[1] + 2,
                    0xFFFFCC33);
        }
        FrameProfiler.end("background");

        FrameProfiler.begin("groups");
        for (QuestGroup grp : QuestGroupManager.forChapter(selectedChapter)) {
            renderQuestGroup(g, grp, cl, cr);
        }
        FrameProfiler.end("groups");

        g.disableScissor();
    }

    private void renderDepLines(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

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
        depLineRenderer.render(g, animTick, hoveredNodeId, this::getState, palette.lineActive, palette.lineDone,
                C_LINE_ALMOST,
                palette.lineLocked);

        if (linkDragSource != null) {
            int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
            if (srcPos != null) {
                int sz2 = scaledNodeSize();
                int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
                depLineRenderer.renderLinkDragPreview(g, sx, sy, linkDragX, linkDragY, animTick, posZoom());
            }
        }

        g.disableScissor();
    }

    private void renderLinkDragHint(GuiGraphics g) {
        if (linkDragSource == null) return;
        int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
        if (srcPos == null) return;
        int sz2 = scaledNodeSize();
        int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
        g.drawString(font, "§dRelease on a quest to link", sx - 50, sy - 14, 0xFFAA66FF, false);
    }

    private void renderNodesAndDetails(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        nodeRenderer.renderNodesAndDetails(g, mx, my, cl, cr, sz);
    }

    private void renderScreenOverlays(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        if (feedbackTimer > 0 && !feedbackMsg.isEmpty()) {
            g.fill(cl, height - 13, cr, height, palette.header);
            g.fill(cl, height - 13, cl + 1, height, palette.selAccent);
            String clipped = font.plainSubstrByWidth("§7" + feedbackMsg, Math.max(0, cr - cl - 12));
            g.drawString(font, clipped, cl + 6, height - 10, palette.textDim);
        }

        if (!isSidebarNarrow()) {

            g.enableScissor(0, 0, sidebarVisualW(), height);

            RenderSystem.disableDepthTest();
            renderSidebarNewChapterButton(g, mx, my);
            renderSidebarGear(g, mx, my);
            g.flush();
            RenderSystem.enableDepthTest();
            g.disableScissor();
        }

        if (tutorialOverlay.isVisible(this)) tutorialOverlay.render(this, g, mx, my, cl, cr);

        if (!renderingAsBackdrop && editorState.draggedNode == null && !ctxOpen &&
                !(minimapOpen && isInMinimap(mx, my))) {
            ResourceLocation nowHoverId = null;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                ChronicleOverviewScreen.NodeHitbox btn = nodeButtons.get(node.getId());
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

                if (tipNode != null) pendingDeferredDraws.add(() -> renderNodeTooltip(g, tipNode, mx, my));
            }
        }

        if (!renderingAsBackdrop && nodeCtxMenu.isVisible(this)) nodeCtxMenu.render(this, g, mx, my, cl, cr);
        if (!renderingAsBackdrop && pictureCtxMenu.isVisible(this)) pictureCtxMenu.render(this, g, mx, my, cl, cr);

        if (!unlockPathHighlight.isEmpty()) {
            float blink = ChronicleOverviewScreen.animPulse(0.7f, 0.3f, 400.0);
            int ringAlpha = (int) (blink * 0xAA) & 0xFF;
            g.enableScissor(cl, HEADER_H, cr, height);
            for (ResourceLocation uid : unlockPathHighlight) {
                int[] upos = nodeScreenPos.get(uid);
                if (upos == null) continue;
                NodeHitbox uhb = nodeButtons.get(uid);
                if (uhb == null || !uhb.visible) continue;
                int ux = upos[0], uy = upos[1];
                g.fill(ux - 3, uy - 3, ux + sz + 3, uy - 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy + sz + 2, ux + sz + 3, uy + sz + 3, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy - 2, ux - 2, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux + sz + 2, uy - 2, ux + sz + 3, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
            }
            g.disableScissor();
            g.drawString(font, "§bUnlock path. §8Esc to clear", cl + 6, height - 10, 0xFF4488FF, false);
        }

        if (!renderingAsBackdrop) renderNonDevCtxMenu(g, (int) mx, (int) my);

        if (depLineRenderer.isContextMenuOpen()) depLineRenderer.renderContextMenu(g, font, mx, my, width, height);
        if (sidebarPanel.contextMenuOpen()) {
            sidebarPanel.renderContextMenu(g, font, (int) mx, (int) my, width, height, sidebarColors());
        }

        for (TogglePanel p : List.of(validationPanel, statsPanel)) {
            if (p.isVisible(this)) pendingDeferredDraws.add(() -> p.render(this, g, mx, my, cl, cr));
        }

        if (isDevMode && editorState.multiSelection.size() >= 2) {
            bulkOpsPanel.render(g, mx, my, cl);
        }

        if (openTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            if (elapsed < OPEN_FADE_MS) {
                float t = 1f - (float) elapsed / OPEN_FADE_MS;
                int fadeAlpha = (int) (t * t * 0xFF) & 0xFF;
                if (fadeAlpha > 0) g.fill(0, 0, width, height, (fadeAlpha << 24) | 0x000000);
            }
        }
    }

    private void renderProfilerPanel(GuiGraphics g) {
        var sections = FrameProfiler.sortedSections();
        int panelW = 260;
        int rowH = 11;
        int extraRows = 2;
        int panelH = 20 + 10 + (extraRows + sections.size()) * rowH + 10;
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

        int gapY = py + 26;
        double gapAvg = FrameProfiler.wallClockGapAvgMs();
        double gapMax = FrameProfiler.wallClockGapMaxMs();
        int gapColor = gapMax > 50 ? 0xFFFF5555 : gapMax > 25 ? 0xFFFFAA33 : 0xFF7A9AAA;
        g.drawString(font, "§8Frame gap: §7" +
                String.format("%.2fms (max %.2fms)", gapAvg, gapMax), px + 5, gapY, gapColor, false);

        long gcCount = FrameProfiler.gcCountThisWindow();
        long gcTimeMs = FrameProfiler.gcTimeMsThisWindow();
        int gcColor = gcTimeMs > 0 ? 0xFFFF5555 : 0xFF7A9AAA;
        g.drawString(font, "§8GC: §7" + gcCount + " collections, " + gcTimeMs + "ms",
                px + 5, gapY + rowH, gcColor, false);

        double localMax = sections.isEmpty() ? 1.0 : sections.get(0).getValue();
        int y = gapY + rowH * 2 + 12;
        for (var entry : sections) {
            double ms = entry.getValue();
            double worst = FrameProfiler.maxMsFor(entry.getKey());

            float frac = localMax > 0 ? (float) (ms / localMax) : 0;
            int barColor = frac > 0.66f ? 0xFFFF5555 : frac > 0.33f ? 0xFFFFAA33 : 0xFF55CC77;
            int barW = (int) (frac * (panelW - 110));
            g.fill(px + 5, y + 1, px + 5 + Math.max(1, barW), y + rowH - 2, barColor);
            g.drawString(font, entry.getKey(), px + 5, y + 1, 0xFF888898, false);

            String msStr = String.format("%.2f §8/ §7%.2fms", ms, worst);
            g.drawString(font, msStr, px + panelW - font.width(net.minecraft.util.StringUtil.stripColor(msStr)) - 5,
                    y + 1, 0xFFCCCCCC, false);
            y += rowH;
        }
        g.pose().popPose();
    }

    private void openSearchOverlay() {
        if (minecraft != null) minecraft.setScreen(new SearchOverlayScreen(this));
    }

    private ToolbarPanel.Colors toolbarColors() {
        return new ToolbarPanel.Colors(palette.panelDark, palette.border, palette.text, palette.textDim);
    }

    private int[][] filterPillBounds(int cl, int cr) {
        return toolbarPanel.filterPillBounds(cl, cr, TOOLBAR_Y, TOOLBAR_H, font, isDevMode);
    }

    private void renderToolbar(GuiGraphics g, int mx, int my, int cl, int cr) {
        toolbarPanel.render(g, font, mx, my, width, cl, cr, TOOLBAR_Y, TOOLBAR_H, toolbarColors(), stateFilter,
                hideCompleted, minimapOpen, isDevMode, pendingDeferredDraws::add);
    }

    private boolean hitsToolbarBtn(String key, double mx, double my) {
        return toolbarPanel.hits(key, mx, my);
    }

    private boolean gearHovered(int mx, int my) {
        return sidebarPanel.gearHovered(mx, my, height);
    }

    private boolean newCatButtonHovered(int mx, int my) {
        return sidebarPanel.newCatButtonHovered(mx, my, height, isDevMode);
    }

    public void openNewChapterForm() {
        if (minecraft != null) {
            minecraft.setScreen(new NewChapterScreen(this, id -> {
                selectedChapter = id;
                rebuild();
                setFeedback("Chapter '%s' created", friendly(id));
            }));
        }
    }

    private void renderSidebarNewChapterButton(GuiGraphics g, int mx, int my) {
        sidebarPanel.renderNewChapterButton(g, font, mx, my, height, isDevMode, sidebarColors());
    }

    private void renderSidebarGear(GuiGraphics g, int mx, int my) {
        sidebarPanel.renderGear(g, font, mx, my, width, height, isDevMode, sidebarColors(), pendingDeferredDraws::add);
    }

    private ResourceLocation resolveShapeTexture(QuestNode node) {
        return nodeRenderer.resolveShapeTexture(node);
    }

    private void renderNodeShape(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                 boolean hovered, boolean selected) {
        nodeRenderer.renderNodeShape(g, node, x, y, sz, hovered, selected);
    }

    private void renderNodeDetails(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                   boolean hovered, boolean selected) {
        nodeRenderer.renderNodeDetails(g, node, x, y, sz, hovered, selected);
    }

    private void renderQuestGroup(GuiGraphics g, QuestGroup grp, int cl, int cr) {
        nodeRenderer.renderQuestGroup(g, grp, cl, cr);
    }

    private void renderGroupIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        nodeRenderer.renderGroupIcon(g, icon, x, y, size);
    }

    @Nullable
    private QuestGroup groupAtLabelBar(double mx, double my, int cl) {
        return nodeRenderer.groupAtLabelBar(mx, my, cl);
    }

    private BackgroundPictureConfig.Picture pictureAt(double mx, double my, int cl) {
        return nodeRenderer.pictureAt(mx, my, cl);
    }

    private void drawProgressArc(GuiGraphics g, int cx, int cy, int r,
                                 float fraction, int fillColor, int bgColor) {
        nodeRenderer.drawProgressArc(g, cx, cy, r, fraction, fillColor, bgColor);
    }

    public void renderForChildScreen(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int liveW = mc.getWindow().getGuiScaledWidth();
        int liveH = mc.getWindow().getGuiScaledHeight();
        if (liveW != this.width || liveH != this.height) {
            this.width = liveW;
            this.height = liveH;
            softRebuild();
        }

        renderingAsBackdrop = true;
        try {
            render(g, -9999, -9999, 0f);
        } finally {
            renderingAsBackdrop = false;
        }
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    private void renderStateBadge(GuiGraphics g, int nx, int ny, int sz, QuestState st) {
        nodeRenderer.renderStateBadge(g, nx, ny, sz, st);
    }

    @Override
    public boolean catMatches(QuestNode n) {
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
        QuestNode.Visibility vis = n.getEffectiveVisibility(server);

        if (n.isFlagDisabled(server)) return isDevMode && QuestChroniclesSettings.get().isShowFlagDisabledQuests();

        if (!isDevMode) {
            if (vis == QuestNode.Visibility.HIDDEN && getState(n) == QuestState.LOCKED) return false;
            if (isAncestorGatedHidden(n)) return false;
        }

        if (!selectedChapter.equals(n.getChapter())) return false;

        if (!stateFilter.equals("ALL")) {
            QuestState st = getState(n);
            boolean stateMatch = switch (stateFilter) {
                case "AVAILABLE" -> st == QuestState.UNLOCKED;
                case "ACTIVE" -> st == QuestState.ACTIVE;
                case "COMPLETE" -> st == QuestState.COMPLETED;
                case "LOCKED" -> st == QuestState.LOCKED;
                default -> true;
            };
            return stateMatch;
        }
        return true;
    }

    String buildSearchHaystack(QuestNode n) {
        StringBuilder sb = new StringBuilder();
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;

        sb.append(n.getEffectiveTitleRaw(server).getString().toLowerCase()).append(' ');
        sb.append(n.getId().getPath().replace('_', ' ').toLowerCase()).append(' ');
        sb.append(n.getId().toString().toLowerCase()).append(' ');
        if (!n.getEffectiveDescriptionRaw(server).getString().isEmpty())
            sb.append(n.getEffectiveDescriptionRaw(server).getString().toLowerCase()).append(' ');
        if (n.getSubtitle() != null && !n.getSubtitle().isEmpty()) sb.append(n.getSubtitle().toLowerCase()).append(' ');
        sb.append(n.getChapter().toLowerCase()).append(' ');

        for (QuestTask task : n.getEffectiveTasks(server)) {
            sb.append(task.getDescription().getString().toLowerCase()).append(' ');

            ResourceLocation displayId = task.getDisplayItemId();
            if (displayId != null) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(displayId);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {

                    sb.append(item.getDescription().getString().toLowerCase()).append(' ');

                    sb.append(displayId.getPath().replace('_', ' ').toLowerCase()).append(' ');
                    sb.append(displayId.toString().toLowerCase()).append(' ');

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

                    try {
                        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft
                                .getInstance().player;
                        var tooltipLines = stack.getTooltipLines(localPlayer,
                                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                        for (int ti = 1; ti < tooltipLines.size(); ti++) {

                            String txt = tooltipLines.get(ti).getString().trim().toLowerCase();
                            if (!txt.isEmpty()) sb.append(txt).append(' ');
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        for (QuestReward reward : n.getEffectiveRewards(server)) {
            sb.append(reward.getSummary().getString()).append(' ');
            if (reward instanceof QuestReward.ItemReward ir) {
                ResourceLocation rid = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ir.getItem());
                if (rid != null) {
                    sb.append(rid.getPath().replace('_', ' ')).append(' ');
                    sb.append(rid).append(' ');
                }
            }
        }

        return sb.toString().toLowerCase();
    }

    public String friendly(String cat) {
        if (cat == null || cat.equals("ALL")) return "All Chapters";
        String resolved = ChapterConfig.getResolvedDisplayName(cat);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : cat.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public boolean isDevMode() {
        return this.isDevMode;
    }

    @Override
    public boolean isRenderingAsBackdrop() {
        return this.renderingAsBackdrop;
    }

    @Override
    public Map<ResourceLocation, int[]> nodeScreenPos() {
        return this.nodeScreenPos;
    }

    @Override
    public String selectedChapter() {
        return this.selectedChapter;
    }

    @Override
    public UndoRedoManager undoRedo() {
        return this.undoRedo;
    }

    @Override
    public List<String> validationIssues(QuestNode node) {
        return validationPanel.issuesFor(node);
    }

    @Override
    @Nullable
    public BackgroundPictureConfig.Picture pictureEditMode() {
        return this.pictureEditMode;
    }

    @Override
    public void setPictureEditMode(@Nullable BackgroundPictureConfig.Picture picture) {
        this.pictureEditMode = picture;
        if (picture != null) {
            pictureEditStartX = picture.x;
            pictureEditStartY = picture.y;
            pictureEditStartW = picture.w;
            pictureEditStartH = picture.h;
        }
    }

    private void finalizePictureEdit() {
        BackgroundPictureConfig.Picture pic = pictureEditMode;
        if (pic == null) return;
        float startX = pictureEditStartX, startY = pictureEditStartY;
        float startW = pictureEditStartW, startH = pictureEditStartH;
        float endX = pic.x, endY = pic.y, endW = pic.w, endH = pic.h;
        BackgroundPictureConfig.save();
        if (startX != endX || startY != endY || startW != endW || startH != endH) {
            pushUndo("Undo: picture edit reverted", () -> {
                pic.x = startX;
                pic.y = startY;
                pic.w = startW;
                pic.h = startH;
                BackgroundPictureConfig.save();
            }, () -> {
                pic.x = endX;
                pic.y = endY;
                pic.w = endW;
                pic.h = endH;
                BackgroundPictureConfig.save();
            });
        }
        pictureEditMode = null;
    }

    @Override
    public int colorBorder() {
        return palette.border;
    }

    @Override
    public int colorSelectAccent() {
        return palette.selAccent;
    }

    @Override
    public int colorText() {
        return palette.text;
    }

    @Override
    public int colorTextDim() {
        return palette.textDim;
    }

    @Override
    public int colorTextFaint() {
        return palette.textFaint;
    }

    @Override
    public int colorNodeBorderDone() {
        return palette.nbordDone;
    }

    @Override
    public void setCtxOpen(boolean open) {
        ctxOpen = open;
    }

    @Override
    public void setCtxOpenTimeMs(long timeMs) {
        ctxOpenTimeMs = timeMs;
    }

    @Override
    public void setCtxX(int x) {
        ctxX = x;
    }

    @Override
    public void setCtxY(int y) {
        ctxY = y;
    }

    @Override
    public int ctxRawX() {
        return ctxRawX;
    }

    @Override
    public int ctxRawY() {
        return ctxRawY;
    }

    @Override
    public void setCtxRawX(int x) {
        ctxRawX = x;
    }

    @Override
    public void setCtxRawY(int y) {
        ctxRawY = y;
    }

    @Override
    public void setCtxNode(@Nullable QuestNode node) {
        ctxNode = node;
    }

    @Override
    public void setCtxGroup(@Nullable QuestGroup group) {
        ctxGroup = group;
    }

    @Override
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    @Override
    public void setTestModeData(PlayerQuestData data) {
        this.testModeData = data;
    }

    @Override
    public void setGridSnapEnabled(boolean enabled) {
        this.gridSnapEnabled = enabled;
    }

    @Override
    public GridDisplayMode gridDisplayMode() {
        return gridDisplayMode;
    }

    @Override
    public void setGridDisplayMode(GridDisplayMode mode) {
        this.gridDisplayMode = mode;
    }

    @Override
    public void setNodeSizeEditMode(@Nullable QuestNode node) {
        this.nodeSizeEditMode = node;
        if (node != null) {
            nodeSizeEditStartSize = node.getNodeSize();
            nodeSizeEditStartOverridePx = node.getSizeOverridePx();
            nodeSizeEditStartX = node.getCustomX();
            nodeSizeEditStartY = node.getCustomY();
        }
    }

    private void finalizeNodeSizeEdit() {
        QuestNode node = nodeSizeEditMode;
        if (node == null) return;
        QuestNode.NodeSize startSize = nodeSizeEditStartSize;
        int startOverridePx = nodeSizeEditStartOverridePx, startX = nodeSizeEditStartX, startY = nodeSizeEditStartY;
        int endOverridePx = node.getSizeOverridePx(), endX = node.getCustomX(), endY = node.getCustomY();
        QuestFileSaver.saveOneQuestToDisk(node);
        if (startOverridePx != endOverridePx || startX != endX || startY != endY) {
            pushUndo("Undo: node resize reverted", () -> {
                node.setNodeSize(startSize);
                if (startOverridePx > 0) node.setSizeOverridePx(startOverridePx);
                node.setCustomPosition(startX, startY);
                QuestFileSaver.saveOneQuestToDisk(node);
                softRebuild();
            }, () -> {
                node.setNodeSize(startSize);
                if (endOverridePx > 0) node.setSizeOverridePx(endOverridePx);
                node.setCustomPosition(endX, endY);
                QuestFileSaver.saveOneQuestToDisk(node);
                softRebuild();
            });
        }
        nodeSizeEditMode = null;
    }

    @Override
    public void setNodeSizeDragAccX(double v) {
        this.nodeSizeDragAccX = v;
    }

    @Override
    public void setNodeSizeDragAccY(double v) {
        this.nodeSizeDragAccY = v;
    }

    @Override
    public boolean ctxOpen() {
        return ctxOpen;
    }

    @Override
    public long ctxOpenTimeMs() {
        return ctxOpenTimeMs;
    }

    @Override
    public int ctxX() {
        return ctxX;
    }

    @Override
    public int ctxY() {
        return ctxY;
    }

    @Override
    public float ctxScale() {
        return ctxScale;
    }

    @Override
    public void setCtxScale(float scale) {
        ctxScale = scale;
    }

    @Override
    @Nullable
    public QuestNode ctxNode() {
        return ctxNode;
    }

    @Override
    @Nullable
    public QuestGroup ctxGroup() {
        return ctxGroup;
    }

    @Override
    public boolean ctxMoveCatOpen() {
        return ctxMoveCatOpen;
    }

    @Override
    public void setCtxMoveCatOpen(boolean open) {
        ctxMoveCatOpen = open;
    }

    @Override
    public int ctxMoveCatScroll() {
        return ctxMoveCatScroll;
    }

    @Override
    public void setCtxMoveCatScroll(int scroll) {
        ctxMoveCatScroll = scroll;
    }

    @Override
    public int viewOffX() {
        return viewOffX;
    }

    @Override
    public int viewOffY() {
        return viewOffY;
    }

    @Override
    public int gridSnap() {
        return gridSnap;
    }

    @Override
    public boolean gridSnapEnabled() {
        return gridSnapEnabled;
    }

    @Override
    public boolean dragForceSnap() {
        return dragForceSnap;
    }

    @Override
    public Map<ResourceLocation, NodeHitbox> nodeButtons() {
        return nodeButtons;
    }

    @Override
    public int dragGrabX() {
        return dragGrabX;
    }

    @Override
    public int dragGrabY() {
        return dragGrabY;
    }

    @Override
    public int dragOrigX() {
        return dragOrigX;
    }

    @Override
    public int dragOrigY() {
        return dragOrigY;
    }

    @Override
    public Set<ResourceLocation> hiddenByCollapse() {
        return hiddenByCollapse;
    }

    @Override
    public boolean hideCompleted() {
        return hideCompleted;
    }

    @Override
    public net.phoenixvine.chronicles.capability.PlayerQuestData playerData() {
        return playerData;
    }

    @Override
    public void resetViewOffset() {
        viewOffX = 0;
        viewOffY = 0;
    }

    @Override
    public Set<ResourceLocation> subgraphNodes() {
        return subgraphNodes;
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public PlayerQuestData testModeData() {
        return testModeData;
    }

    @Override
    public int colorNodeLocked() {
        return palette.nodeLocked;
    }

    @Override
    public int colorNodeUnlocked() {
        return palette.nodeUnlocked;
    }

    @Override
    public int colorNodeActive() {
        return palette.nodeActive;
    }

    @Override
    public int colorNodeDone() {
        return palette.nodeDone;
    }

    @Override
    public int colorNodeBorderLocked() {
        return palette.nbordLocked;
    }

    @Override
    public int colorNodeBorderUnlocked() {
        return palette.nbordUnlocked;
    }

    @Override
    public int colorNodeBorderActive() {
        return palette.nbordActive;
    }

    @Override
    public int colorNodeBorderDev() {
        return palette.nbordDev;
    }

    @Override
    public int colorTextDone() {
        return palette.textDone;
    }

    @Override
    public int colorTextActive() {
        return palette.textAct;
    }

    @Override
    public int colorBorderLit() {
        return palette.borderLit;
    }

    @Override
    public boolean validationPanelOpen() {
        return validationPanel.isOpen();
    }

    @Override
    public boolean statsPanelOpen() {
        return statsPanel.isOpen();
    }

    @Override
    public boolean minimapOpen() {
        return minimapOpen;
    }

    @Override
    public int dbgFull3DIconCount() {
        return dbgFull3DIconCount;
    }

    @Override
    public void setDbgFull3DIconCount(int count) {
        dbgFull3DIconCount = count;
    }

    @Override
    public int dbgCustomIconCount() {
        return dbgCustomIconCount;
    }

    @Override
    public void setDbgCustomIconCount(int count) {
        dbgCustomIconCount = count;
    }

    @Override
    public int dbgPickedTextureIconCount() {
        return dbgPickedTextureIconCount;
    }

    @Override
    public void setDbgPickedTextureIconCount(int count) {
        dbgPickedTextureIconCount = count;
    }

    @Override
    public int dbgFluidIconCount() {
        return dbgFluidIconCount;
    }

    @Override
    public void setDbgFluidIconCount(int count) {
        dbgFluidIconCount = count;
    }

    @Override
    public int dbgGlyphIconCount() {
        return dbgGlyphIconCount;
    }

    @Override
    public void setDbgGlyphIconCount(int count) {
        dbgGlyphIconCount = count;
    }

    @Override
    public Map<String, Integer> dbgShapeCounts() {
        return dbgShapeCounts;
    }

    int unclaimedRewardCount() {
        if (playerData == null) return 0;
        int count = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled(null)) continue;
            if (playerData.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (playerData.hasClaimedRewards(node.getId())) continue;
            if (node.isRewardChoice()) continue;
            if (node.getEffectiveRewards(minecraft != null ? minecraft.getSingleplayerServer() : null).isEmpty())
                continue;
            count++;
        }
        return count;
    }

    private String chapterBreadcrumb(String cat) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cur = cat;
        while (cur != null && !cur.isEmpty() && visited.add(cur)) {
            String parent = ChapterConfig.get(cur).getParentChapter();
            if (parent.isEmpty()) break;
            chain.add(0, parent);
            cur = parent;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : chain) sb.append(friendly(p)).append(" §8›  §7");
        sb.append(friendly(cat));
        return sb.toString();
    }

    @Override
    public String shortLabel(QuestNode node) {
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
        String t = node.getEffectiveTitleRaw(server).getString();

        int maxW = (int) (scaledNodeSize(node) * 1.6f) + 40;
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    @Override
    public String shortName(QuestNode node, int maxW) {
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
        String t = node.getEffectiveTitleRaw(server).getString();
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    private void fitToCanvas() {
        if (!applyFitView()) return;
        rebuild();
    }

    private boolean applyFitView() {
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
        if (minX == Integer.MAX_VALUE) return false;
        int canvasW = cr - cl - 20, canvasH = height - HEADER_H - 20;
        int contentW = maxX - minX, contentH = maxY - minY;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.min(
                (float) canvasW / contentW,
                (float) canvasH / contentH)));
        viewOffX = (int) (canvasW / 2f - (minX + contentW / 2f) * posZoom()) + 10;
        viewOffY = (int) (canvasH / 2f - (minY + contentH / 2f) * posZoom()) + 10;
        return true;
    }

    private void renderNodeTooltip(GuiGraphics g, QuestNode node, int mx, int my) {
        nodeRenderer.renderNodeTooltip(g, node, mx, my);
    }

    private int[] computeChapterProgress(String cat) {
        int done = 0, total = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;

            total++;

            if (getDisplayState(n) == QuestState.COMPLETED) done++;
        }
        return new int[] { done, total };
    }

    private boolean computeChapterHasAttention(String cat) {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;
            if (getDisplayState(n) == QuestState.ACTIVE) return true;
        }
        return false;
    }

    private boolean computeChapterHasUnclaimedRewards(String cat) {
        if (playerData == null) return false;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;
            if (playerData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (playerData.hasClaimedRewards(n.getId())) continue;
            if (n.isRewardChoice()) continue;
            if (n.getEffectiveRewards(minecraft != null ? minecraft.getSingleplayerServer() : null).isEmpty()) continue;
            return true;
        }
        return false;
    }

    public void setFeedback(String msg, Object... args) {
        feedbackMsg = msg.formatted(args);
        feedbackTimer = 100;
    }

    @Override
    public void pushUndo(String undoMsg, Runnable revertAction, Runnable redoAction) {
        String redoMsg = undoMsg.startsWith("Undo: ") ? "Redo: " + undoMsg.substring("Undo: ".length()) :
                "Redo: " + undoMsg;
        undoRedo.push(
                () -> {
                    revertAction.run();
                    setFeedback(undoMsg);
                },
                () -> {
                    redoAction.run();
                    setFeedback(redoMsg);
                });
    }

    @Override
    public void setFeedbackDone(String doneMsg, Object... args) {
        setFeedback(doneMsg + "  (Ctrl+Z to undo)", args);
    }

    private void openLineSettingsFor(QuestNode parentNode) {
        if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedChapter, parentNode));
    }

    void rebuildFromExternal() {
        rebuild();
    }

    void saveNodeHideDepLineToDisk(QuestNode node) {
        QuestFileSaver.updateHideDepLine(node);
    }

    @Override
    public void saveNodeToDisk(QuestNode node) {
        QuestFileSaver.updateNodePosition(node);
    }

    @Override
    public void refreshEdgeEndpointsFor(QuestNode node) {
        depLineRenderer.refreshEdgeEndpoints(node.getId(), this::nodeCenterForLine, posZoom(),
                QuestChroniclesSettings.get());
    }

    @Override
    public void saveNodeShapeToDisk(QuestNode node, String shape) {
        QuestFileSaver.updateNodeShape(node, shape);
    }

    @Override
    public void saveNodeChapterToDisk(QuestNode node, String cat) {
        QuestFileSaver.updateNodeChapter(node, cat);
    }

    @Override
    public void saveNodePrereqsToDisk(QuestNode node) {
        QuestFileSaver.updateNodePrerequisites(node);
    }

    @Override
    public void saveNodeShapeTextureToDisk(QuestNode node) {
        QuestFileSaver.updateNodeShapeTexture(node);
    }

    @Override
    public void deleteQuestFiles(QuestNode node) {
        QuestFileSaver.deleteQuestFiles(node);
    }

    private void computeUnlockPath(QuestNode target) {
        unlockPathHighlight.clear();

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

            if (data != null) {
                QuestState st = data.getQuestState(n.getId(), QuestState.LOCKED);
                if (st == QuestState.COMPLETED || st == QuestState.ACTIVE) continue;
            }
            for (QuestNode req : n.getPrerequisites()) queue.add(req);
        }
    }

    @Override
    public int[] minimapBounds(int cr) {
        return minimap.bounds(cr, height);
    }

    private void renderMinimap(GuiGraphics g, int mx, int my, int cl, int cr) {
        minimap.render(g, font, cl, cr, width, height, HEADER_H, posZoom(), viewOffX, viewOffY,
                this::catMatches, this::getState);
    }

    private boolean isInMinimap(double x, double y) {
        return minimap.isInMinimap(x, y, minimapOpen, width, height);
    }

    public void minimapPanTo(double sx, double sy, int cl) {
        int[] p = minimap.panTo(sx, sy, cl, width, height, HEADER_H, posZoom());
        if (p == null) return;
        viewOffX = p[0];
        viewOffY = p[1];
    }

    @Override
    public void onClose() {
        PhantasiaCompat.closePreview(phantasiaPreview);
        phantasiaPreview = null;
        quickDepKeyDown = false;
        linkDragSource = null;
        minecraft.setScreen(parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        int v = S2CSyncPlayerProgressPacket.getVersion();
        if (v != lastSeenProgressVersion) {
            lastSeenProgressVersion = v;
            progressCache.clear();
            attentionCache.clear();
            rewardsCache.clear();
        }

        if (minecraft != null && minecraft.player != null) {
            String currentHand = minecraft.player.getMainHandItem().getItem().toString();
            if (this.lastTickHand == null) this.lastTickHand = currentHand;
            if (!currentHand.equals(this.lastTickHand)) {
                this.lastTickHand = currentHand;
                softRebuild();
            }
        }
    }

   public enum GridDisplayMode {

        ON_DRAG("On Move"),
        ALWAYS("Always"),
        CURSOR_BOX("Cursor Box");

        final String label;

        GridDisplayMode(String label) {
            this.label = label;
        }

        GridDisplayMode next() {
            GridDisplayMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static final class NodeHitbox {

        public int x;
        public int y;
        public int w;
        public int h;
        public boolean visible = true;
        public boolean active = true;

        int getX() {
            return x;
        }

        public void setX(int nx) {
            x = nx;
        }

        int getY() {
            return y;
        }

        public void setY(int ny) {
            y = ny;
        }

        public boolean isMouseOver(double mx, double my) {
            return visible && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    record CtxItem(String label, String color, boolean isSep, boolean isDanger, Runnable action) {

        static CtxItem sep() {
            return new CtxItem("", "", true, false, () -> {});
        }
    }
}
