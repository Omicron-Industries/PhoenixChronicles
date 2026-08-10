package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter;
import net.phoenixvine.chronicles.client.*;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ChronicleOverviewScreen extends Screen implements ScreenContext, NodeCtxMenuState {

    static final int HEADER_H = 38;
    static final int TOOLBAR_Y = 22;
    static final int TOOLBAR_H = 16;
    private static final int NODE_SIZE = 32;
    private static final int C_NBORD_SEL = 0xFF6688FF;
    private static final int C_LINE_ALMOST = 0xAAFFEE33;

    static final int C_CTX_BG = 0xFF1A1A22;
    static final int C_CTX_HOVER = 0xFF252532;
    static final int C_CTX_BORDER = 0xFF8844AA;
    static final int C_CTX_SEP = 0xFF2A2A38;
    static final int C_CTX_TEXT = 0xFFCCCCD8;
    static final int C_CTX_DANGER = 0xFFCC4444;
    private static final int C_PROG_ACT = 0xFFBB8800;
    private static final float ZOOM_MIN = 0.12f;
    private static final float ZOOM_MAX = 2.5f;
    private static final float ZOOM_STEP = 0.12f;
    private static final long POST_MOVE_UNDO_WINDOW_MS = 1000;
    private static final float PIC_EDIT_MIN_SIZE = 4f, PIC_EDIT_MAX_SIZE = 4096f;
    static final int CTX_ROW = 16;
    static final int CTX_SEP = 5;
    static final int CTX_W = 128;
    static final int CTX_MOVE_CAT_MAX_ROWS = 10;
    private static final Set<ResourceLocation> collapsedSubtreeRoots = new HashSet<>();
    private static final int[] GRID_SNAP_CYCLE = { 1, 4, 8, 16, 32, 64, 128 };
    static final long OPEN_FADE_MS = 120;
    private static final long TOOLTIP_DELAY_MS = 0;
    private static final int MIN_NODE_PX = 12;
    private static final float MIN_NODE_FLOOR_FRACTION = 0.375f;
    private static final int GROUP_LABEL_BAR_H = 11;
    final Map<ResourceLocation, String> searchCache = new HashMap<>();
    private final SidebarPanel sidebarPanel = new SidebarPanel();

    private final java.util.function.BiConsumer<Integer, Integer> panCanvasFn = this::panCanvas;

    private final Map<Item, ItemStack> iconStackCache = new HashMap<>();
    private final Map<QuestTask, ItemStack> nbtIconStackCache = new java.util.IdentityHashMap<>();

    private ItemStack cachedIconStack(Item icon) {
        return iconStackCache.computeIfAbsent(icon, ItemStack::new);
    }

    private final Map<String, Integer> dbgShapeCounts = new HashMap<>();
    private final EditBox searchBox = null;
    private final String searchQuery = "";
    private final String[] searchWords = new String[0];
    private final Set<ResourceLocation> multiSelection = new LinkedHashSet<>();

    private static final int BULK_PANEL_ROW_H = 13;
    private static final int BULK_PANEL_H = 51;

    @Nullable
    private Map<ResourceLocation, int[]> bulkDragOrigPositions = null;

    private final Set<ResourceLocation> hiddenByCollapse = new HashSet<>();
    private final MinimapRenderer minimap = new MinimapRenderer();
    final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    private final Map<ResourceLocation, NodeHitbox> nodeButtons = new LinkedHashMap<>();
    private final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();
    private final Map<String, int[]> progressCache = new HashMap<>();
    private final Map<String, Boolean> attentionCache = new HashMap<>();
    final ValidationPanel validationPanel = new ValidationPanel(this);
    private final StatsPanel statsPanel = new StatsPanel(this);
    private final TutorialOverlay tutorialOverlay = new TutorialOverlay();

    private void toggleStatsPanel() {
        if (statsPanel.isOpen()) {
            statsPanel.close();
        } else {
            statsPanel.open();
            validationPanel.close();
        }
    }

    private final Set<ResourceLocation> unlockPathHighlight = new HashSet<>();
    private final java.util.List<Runnable> pendingDeferredDraws = new java.util.ArrayList<>();
    private final java.util.Set<ResourceLocation> subgraphNodes = new java.util.HashSet<>();
    private final ToolbarPanel toolbarPanel = new ToolbarPanel();
    private int C_BG = 0xFF0B0B0F;
    private int C_PANEL_DARK = 0xFF0E0E12;
    private int C_HEADER = 0xFF09090D;
    int C_BORDER = 0xFF252530;
    private int C_BORDER_LIT = 0xFF353548;
    private int C_SEL_TAB = 0xFF1A1A26;
    int C_SEL_ACCENT = 0xFF00AA55;
    private int C_NODE_LOCKED = 0xFF1A1A24;
    private int C_NODE_UNLOCKED = 0xFF1E1E2C;
    private int C_NODE_ACTIVE = 0xFF221C00;
    private int C_NODE_DONE = 0xFF081A0E;
    private int C_NBORD_LOCKED = 0xFF2E2E40;
    private int C_NBORD_UNLOCKED = 0xFF4A4A60;
    private int C_NBORD_ACTIVE = 0xFFCC9900;
    int C_NBORD_DONE = 0xFF00BB66;
    private int C_NBORD_DEV = 0xFF8844AA;
    private int C_LINE_LOCKED = 0x38FFFFFF;
    private int C_LINE_DONE = 0x9900CC66;
    private int C_LINE_ACTIVE = 0x88FFAA00;
    int C_TEXT = 0xFFD8D8E4;
    int C_TEXT_DIM = 0xFF7A7A8A;
    int C_TEXT_FAINT = 0xFF404050;
    private int C_TEXT_DONE = 0xFF44CC88;
    private int C_TEXT_ACT = 0xFFFFBB33;
    private int C_PROG_FILL = 0xFF00AA55;
    String selectedChapter = "";
    private String viewChapterTracker = null;
    private QuestNode selectedNode = null;
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
    private QuestNode draggedNode = null;
    private int dragGrabX = 0, dragGrabY = 0;
    private int dragOrigX = 0, dragOrigY = 0;
    private boolean pickupPlaceActive = false;

    private boolean middleDragPickupActive = false;

    private boolean quickDepKeyDown = false;

    private QuestNode lastMovedNode = null;
    private int lastMoveOrigX = 0, lastMoveOrigY = 0;
    private long lastMoveTimeMs = 0;
    @Nullable
    private QuestGroup draggedGroup = null;
    private int groupDragGrabX = 0, groupDragGrabY = 0;
    @Nullable
    private BackgroundPictureConfig.Picture draggedPicture = null;
    private int pictureDragGrabX = 0, pictureDragGrabY = 0;
    private final PictureContextMenu pictureCtxMenu = new PictureContextMenu(this);
    private final NodeContextMenu nodeCtxMenu = new NodeContextMenu(this);
    @Nullable
    BackgroundPictureConfig.Picture pictureEditMode = null;
    @Nullable
    private QuestNode nodeSizeEditMode = null;
    private double nodeSizeDragAccX = 0, nodeSizeDragAccY = 0;
    private boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    private int ctxX, ctxY;
    private int ctxRawX, ctxRawY;
    private QuestNode ctxNode = null;
    private boolean ctxMoveCatOpen = false;
    private int ctxMoveCatScroll = 0;
    @Nullable
    private QuestGroup ctxGroup = null;
    private boolean renderingAsBackdrop = false;
    private String stateFilter = "ALL";
    private Object phantasiaPreview = null;
    private List<String> stubChapterCache = null;
    private List<String> chapterListCache = null;
    private long chapterListCacheAtMs = 0;
    private static final long CHAPTER_LIST_CACHE_TTL_MS = 2000;
    private boolean bulkMoveCatOpen = false;
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
    private boolean subgraphMode = false;
    private String questClipboard = null;
    private boolean minimapOpen = false;
    private boolean mmDragging = false;
    @Nullable
    private PlayerQuestData playerData = null;
    private int lastSeenProgressVersion = -1;

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

    static Path chaptersFile() {
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

    private static int nodeBorderThickness(int sz) {
        return Math.max(1, Math.min(4, sz / 28));
    }

    private static int blendColor(int base, int over, float a) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        return 0xFF000000 | ((int) (br + (or - br) * a) << 16) | ((int) (bg + (og - bg) * a) << 8) |
                (int) (bb + (ob - bb) * a);
    }

    private static float animPulse(float base, float amplitude, double periodDivisor) {
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
        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of());
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

    private float posZoom() {
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

    private void toggleSubtreeCollapse(QuestNode node) {
        if (!collapsedSubtreeRoots.remove(node.getId())) collapsedSubtreeRoots.add(node.getId());
        rebuild();
    }

    public QuestState getState(QuestNode node) {
        if (testMode) return testModeData.getQuestState(node.getId(), QuestState.LOCKED);
        if (playerData == null) return QuestState.LOCKED;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED);
    }

    private boolean isGatedHidden(QuestNode node) {
        if (node.isSelfGatedHidden(this::getState)) return true;
        return isAncestorGatedHidden(node);
    }

    private boolean isAncestorGatedHidden(QuestNode node) {
        return QuestChroniclesSettings.get().isCascadeHiddenQuests() && node.isAncestorGatedHidden(this::getState);
    }

    private @Nullable QuestNode resolveLinkTarget(QuestNode node) {
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

    private QuestTask fallbackTaskIconTask(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return task;
        }
        return null;
    }

    private QuestTask matchingIconTask(QuestNode node, Item icon) {
        for (QuestTask task : node.getTasks()) {
            if (task instanceof net.phoenixvine.chronicles.tasks.ItemRequirementTask t && t.getItem() == icon) {
                return task;
            }
        }
        return null;
    }

    private ItemStack nbtAwareIconStack(QuestTask task, Item icon) {
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

    private boolean isTaskDone(QuestTask task) {
        if (minecraft == null || minecraft.player == null) return false;
        return task.isCompletedFor(minecraft.player);
    }

    boolean chapterHasQuests(String chapter) {
        return chapterQuestCount(chapter) > 0;
    }

    int chapterQuestCount(String chapter) {
        int count = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) count++;
        }
        return count;
    }

    List<ResourceLocation> questIdsInChapter(String chapter) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) ids.add(n.getId());
        }
        return ids;
    }

    List<ResourceLocation> questIdsInCategory(String categoryId) {
        List<ResourceLocation> ids = new ArrayList<>();
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .get(categoryId);
        if (cat == null) return ids;
        for (String chapter : cat.chapters()) ids.addAll(questIdsInChapter(chapter));
        return ids;
    }

    private String armedForceCompleteChapterId = null;
    private String armedResetChapterId = null;
    private String armedForceCompleteCategoryId = null;
    private String armedResetCategoryId = null;

    private void forceCompleteChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            setFeedback("§7No quests in '%s'", friendly(chapter));
            return;
        }
        if (!chapter.equals(armedForceCompleteChapterId)) {
            armedForceCompleteChapterId = chapter;
            setFeedback("§6Right-click '%s' -> Force Complete Chapter again to confirm §7(%d quest(s))",
                    friendly(chapter), ids.size());
            return;
        }
        armedForceCompleteChapterId = null;
        net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket(ids,
                        net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        setFeedback("§aForce-completed %d quest(s) in %s", ids.size(), friendly(chapter));
    }

    private void resetChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            setFeedback("§7No quests in '%s'", friendly(chapter));
            return;
        }
        if (!chapter.equals(armedResetChapterId)) {
            armedResetChapterId = chapter;
            setFeedback("§6Right-click '%s' -> Reset Chapter again to confirm §7(%d quest(s))",
                    friendly(chapter), ids.size());
            return;
        }
        armedResetChapterId = null;
        net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket(ids,
                        net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket.Action.RESET));
        setFeedback("§7Reset %d quest(s) in %s", ids.size(), friendly(chapter));
    }

    private void forceCompleteCategoryOnRightClick(String categoryId) {
        List<ResourceLocation> ids = questIdsInCategory(categoryId);
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            setFeedback("§7No quests in '%s'", label);
            return;
        }
        if (!categoryId.equals(armedForceCompleteCategoryId)) {
            armedForceCompleteCategoryId = categoryId;
            setFeedback("§6Right-click '%s' -> Force Complete Category again to confirm §7(%d quest(s))",
                    label, ids.size());
            return;
        }
        armedForceCompleteCategoryId = null;
        net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket(ids,
                        net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        setFeedback("§aForce-completed %d quest(s) in %s", ids.size(), label);
    }

    private void resetCategoryOnRightClick(String categoryId) {
        List<ResourceLocation> ids = questIdsInCategory(categoryId);
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            setFeedback("§7No quests in '%s'", label);
            return;
        }
        if (!categoryId.equals(armedResetCategoryId)) {
            armedResetCategoryId = categoryId;
            setFeedback("§6Right-click '%s' -> Reset Category again to confirm §7(%d quest(s))",
                    label, ids.size());
            return;
        }
        armedResetCategoryId = null;
        net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket(ids,
                        net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket.Action.RESET));
        setFeedback("§7Reset %d quest(s) in %s", ids.size(), label);
    }

    void deleteChapter(String chapter) {
        String upper = chapter.toUpperCase(Locale.ROOT);

        List<QuestNode> questsInChapter = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (upper.equalsIgnoreCase(n.getChapter())) questsInChapter.add(n);
        }
        Map<QuestNode, String> savedSnbt = new LinkedHashMap<>();
        for (QuestNode n : questsInChapter) {
            String raw = QuestFileSaver.readRawSnbt(n);
            if (!raw.isBlank()) savedSnbt.put(n, raw);
        }
        Path chapterFolder = questsInChapter.isEmpty() ?
                Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles")
                        .resolve("quests").resolve(upper.toLowerCase(Locale.ROOT)) :
                QuestFileSaver.getQuestChapterFolder(questsInChapter.get(0));

        boolean wasStub = false;
        try {
            Path f = chaptersFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (line.trim().equalsIgnoreCase(upper)) {
                        wasStub = true;
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}

        net.phoenixvine.chronicles.client.ChapterConfig savedConfig = net.phoenixvine.chronicles.client.ChapterConfig
                .get(chapter);
        net.phoenixvine.chronicles.model.CategoryDefinition owningCategory = net.phoenixvine.chronicles.registry.CategoryRegistry
                .categoryFor(chapter);
        String owningCategoryId = owningCategory != null ? owningCategory.id() : null;

        int questCount = questsInChapter.size();
        boolean finalWasStub = wasStub;
        undoRedo.push(() -> {
            for (Map.Entry<QuestNode, String> e : savedSnbt.entrySet()) {
                QuestFileSaver.restoreRawSnbt(e.getKey(), e.getValue());
            }
            QuestFileLoader.loadAdditiveFromDisk(chapterFolder);

            if (finalWasStub) {
                try {
                    Path f = chaptersFile();
                    List<String> lines = new ArrayList<>();
                    if (Files.exists(f)) lines.addAll(Files.readAllLines(f, StandardCharsets.UTF_8));
                    if (lines.stream().noneMatch(l -> l.trim().equalsIgnoreCase(upper))) lines.add(upper);
                    Files.createDirectories(f.getParent());
                    Files.writeString(f, String.join("\n", lines), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
            net.phoenixvine.chronicles.client.ChapterConfig.put(upper, savedConfig);
            net.phoenixvine.chronicles.client.ChapterConfig.save();
            if (owningCategoryId != null) {
                net.phoenixvine.chronicles.registry.CategoryRegistry.addChapterToCategory(owningCategoryId, upper);
                net.phoenixvine.chronicles.registry.CategoryRegistry.save();
            }

            stubChapterCache = null;
            chapterListCache = null;
            selectedChapter = upper;
            rebuild();
            setFeedback("Undo: chapter restored (%d quest(s))", questCount);
        });

        for (QuestNode n : questsInChapter) {
            QuestTreeRegistry.removeQuest(n.getId());
            QuestFileSaver.deleteQuestFiles(n);
            if (selectedNode == n) selectedNode = null;
        }

        try {
            Path f = chaptersFile();
            if (Files.exists(f)) {
                List<String> remaining = new ArrayList<>();
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String cat = line.trim().toUpperCase(Locale.ROOT);
                    if (!cat.isEmpty() && !cat.equals(upper)) remaining.add(cat);
                }
                Files.writeString(f, String.join("\n", remaining), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}

        if (owningCategory != null) {
            net.phoenixvine.chronicles.registry.CategoryRegistry.removeChapterFromCategory(owningCategory.id(),
                    chapter);
            net.phoenixvine.chronicles.registry.CategoryRegistry.save();
        }

        net.phoenixvine.chronicles.client.ChapterConfig.remove(upper);
        net.phoenixvine.chronicles.client.ChapterConfig.save();

        stubChapterCache = null;
        chapterListCache = null;

        if (selectedChapter.equalsIgnoreCase(chapter)) {
            List<String> remainingChapters = buildChapterList();
            selectedChapter = remainingChapters.isEmpty() ? "" : remainingChapters.get(0);
        }
        rebuild();
        String countSuffix = questCount > 0 ? " (%d quests)".formatted(questCount) : "";
        setFeedback("Chapter deleted: %s%s  (Ctrl+Z to undo)", chapter, countSuffix);
    }

    private String armedDeleteCategoryId = null;

    private void deleteCategoryOnRightClick(String categoryId) {
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .get(categoryId);
        if (cat == null) return;

        if (!cat.chapters().isEmpty() && !categoryId.equals(armedDeleteCategoryId)) {
            armedDeleteCategoryId = categoryId;
            setFeedback("§6Right-click '%s' -> Delete Category again to confirm §7(%d chapter(s) will become " +
                    "uncategorized, not deleted themselves)", cat.displayName(), cat.chapters().size());
            return;
        }

        armedDeleteCategoryId = null;
        int chapterCount = cat.chapters().size();
        net.phoenixvine.chronicles.registry.CategoryRegistry.removeCategory(categoryId);
        net.phoenixvine.chronicles.registry.CategoryRegistry.save();
        chapterListCache = null;
        rebuild();
        String uncatSuffix = chapterCount > 0 ? " (%d chapter(s) uncategorized)".formatted(chapterCount) : "";
        setFeedback("§aCategory deleted: %s%s", cat.displayName(), uncatSuffix);
    }

    private String armedDeleteChapterId = null;

    private void deleteChapterOnRightClick(String chapter) {
        int questCount = chapterQuestCount(chapter);
        if (questCount > 0 && !chapter.equals(armedDeleteChapterId)) {
            armedDeleteChapterId = chapter;
            setFeedback("§6Right-click '%s' -> Delete Chapter again to confirm §7(%d quest(s) will be deleted)",
                    friendly(chapter), questCount);
            return;
        }

        armedDeleteChapterId = null;
        deleteChapter(chapter);
        setFeedback("§aChapter deleted: %s", friendly(chapter));
    }

    private void openSidebarContextMenu(SidebarRow row, int mx, int my) {
        List<SidebarPanel.MenuAction> actions = new ArrayList<>();
        if (row.isFolder()) {
            net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                    .get(row.id());
            String currentLabel = cat != null ? cat.displayName() : row.label();
            actions.add(new SidebarPanel.MenuAction("Rename Category", () -> {
                if (minecraft != null) {
                    minecraft.setScreen(new NewFolderScreen(this, row.id(), currentLabel, id -> rebuild()));
                }
            }));
            actions.add(new SidebarPanel.MenuAction("Category Theme…", () -> {
                if (minecraft != null) minecraft.setScreen(new CategoryThemeScreen(this, row.id()));
            }));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedForceCompleteCategoryId) ? "§aConfirm Force Complete Category" :
                            "Force Complete Category",
                    () -> forceCompleteCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedResetCategoryId) ? "§6Confirm Reset Category" : "Reset Category",
                    () -> resetCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteCategoryId) ? "§cConfirm Delete Category" : "Delete Category",
                    () -> deleteCategoryOnRightClick(row.id())));
        } else {
            actions.add(new SidebarPanel.MenuAction("Chapter Settings…", () -> {
                if (minecraft != null) minecraft.setScreen(new ChapterThemeScreen(this, row.id()));
            }));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedForceCompleteChapterId) ? "§aConfirm Force Complete Chapter" :
                            "Force Complete Chapter",
                    () -> forceCompleteChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedResetChapterId) ? "§6Confirm Reset Chapter" : "Reset Chapter",
                    () -> resetChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteChapterId) ? "§cConfirm Delete Chapter" : "Delete Chapter",
                    () -> deleteChapterOnRightClick(row.id())));
        }
        sidebarPanel.openContextMenu(mx, my, actions);
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

    private List<SidebarRow> buildSidebarRows() {
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
        C_BG = t.bg.getColor();
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

        int bg = t.bg.getColor();
        C_NODE_LOCKED = blendColor(bg, t.locked.getColor(), 0.18f);
        C_NODE_UNLOCKED = blendColor(bg, t.border.getColor(), 0.35f);

        C_NODE_ACTIVE = blendColor(bg, t.activeColor.getColor(), 0.32f);
        C_NODE_DONE = blendColor(bg, t.done.getColor(), 0.30f);

        C_NBORD_LOCKED = blendColor(t.locked.getColor(), 0xFF000000, 0.25f);
        C_NBORD_UNLOCKED = blendColor(t.border.getColor(), 0xFFFFFFFF, 0.15f);
        C_NBORD_ACTIVE = t.activeColor.getColor();
        C_NBORD_DONE = t.done.getColor();
        C_NBORD_DEV = blendColor(t.accent.getColor(), 0xFFCC44FF, 0.5f);

        C_LINE_LOCKED = 0x38000000 | (t.locked.getColor() & 0x00FFFFFF);
        C_LINE_DONE = 0x99000000 | (t.done.getColor() & 0x00FFFFFF);
        C_LINE_ACTIVE = 0x88000000 | (t.activeColor.getColor() & 0x00FFFFFF);
    }

    private Path groupsConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    void invalidateNodeCaches(QuestNode node) {
        if (node == null) return;
        validationPanel.invalidate(node.getId());
        searchCache.remove(node.getId());
        progressCache.remove(node.getChapter());
        attentionCache.remove(node.getChapter());
    }

    void rebuild() {
        clearWidgets();
        nodeScreenPos.clear();
        nodeButtons.clear();
        searchCache.clear();
        progressCache.clear();
        attentionCache.clear();
        validationPanel.clear();
        stubChapterCache = null;
        chapterListCache = null;
        ctxOpen = false;
        ctxMoveCatOpen = false;
        ctxGroup = null;

        QuestGroupManager.load(groupsConfigPath());
        recomputeHiddenByCollapse();

        if (minecraft != null && minecraft.player != null) {
            isDevMode = !QuestChroniclesSettings.get().isDevModeDisabled() &&
                    (minecraft.player.isCreative() || minecraft.player.hasPermissions(2));
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
        if (applyFitView()) {
            zoom = 1.0f;
            viewOffX = 0;
            viewOffY = 0;
        }
    }

    private void placeNodeRecursive(QuestNode node, int cl, int cr) {
        if (nodeButtons.containsKey(node.getId())) return;
        if (hiddenByCollapse.contains(node.getId())) return;

        if (hideCompleted && playerData != null &&
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

        NodeHitbox hb = new NodeHitbox();
        hb.x = sx;
        hb.y = sy;
        hb.w = sz;
        hb.h = sz;
        hb.visible = !offCanvas;
        if (!isDevMode && isGatedHidden(node)) hb.active = false;
        nodeButtons.put(node.getId(), hb);
        nodeScreenPos.put(node.getId(), new int[] { sx, sy });

        for (QuestNode child : node.getChildren()) {
            if (catMatches(child)) placeNodeRecursive(child, cl, cr);
        }
    }

    public int scaledNodeSize(QuestNode node) {
        int pixelSize = node.getNodePixelSize();
        int floor = Math.max(4, Math.round(pixelSize * MIN_NODE_FLOOR_FRACTION));
        return Math.max(floor, (int) (pixelSize * posZoom()));
    }

    public int scaledNodeSize() {
        return Math.max(MIN_NODE_PX, (int) (NODE_SIZE * posZoom()));
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

        selectedNode = target;
        if (subgraphMode) rebuildSubgraph();

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
                    for (QuestTask task : target.getTasks()) {
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
            FullQuestData fd = loadMarkdownContent(resolvedMdPath);
            assert playerData != null;
            minecraft.setScreen(
                    new QuestTasksScreen(this, Objects.requireNonNull(target), fd, playerData, openFullscreen));
        }
    }

    private void autoArrangeChapter() {
        final int X_STRIDE = 80;
        final int Y_STRIDE = 56;
        final int ORIGIN_X = 30;
        final int ORIGIN_Y = 30;

        List<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> selectedChapter.equalsIgnoreCase(n.getChapter()))
                .toList();
        if (nodes.isEmpty()) return;

        Map<ResourceLocation, int[]> oldPositions = new java.util.HashMap<>();
        for (QuestNode n : nodes) oldPositions.put(n.getId(), new int[] { n.getCustomX(), n.getCustomY() });
        undoRedo.push(() -> {
            for (QuestNode n : nodes) {
                int[] pos = oldPositions.get(n.getId());
                if (pos != null) n.setCustomPosition(pos[0], pos[1]);
            }
            QuestFileSaver.saveAllQuestsToDisk();
            rebuild();
        });

        Map<ResourceLocation, Integer> layer = new java.util.HashMap<>();

        java.util.Queue<QuestNode> queue = new java.util.ArrayDeque<>();
        for (QuestNode n : nodes) {
            boolean isRoot = n.getPrerequisites().stream()
                    .noneMatch(p -> selectedChapter.equalsIgnoreCase(p.getChapter()));
            if (isRoot) {
                layer.put(n.getId(), 0);
                queue.add(n);
            }
        }

        if (queue.isEmpty()) {
            nodes.forEach(n -> layer.put(n.getId(), 0));
        }
        int safety = nodes.size() * nodes.size();
        while (!queue.isEmpty() && safety-- > 0) {
            QuestNode n = queue.poll();
            int myLayer = layer.getOrDefault(n.getId(), 0);
            for (QuestNode child : n.getChildren()) {
                if (!selectedChapter.equalsIgnoreCase(child.getChapter())) continue;
                int childLayer = layer.getOrDefault(child.getId(), -1);
                if (childLayer < myLayer + 1) {
                    layer.put(child.getId(), myLayer + 1);
                    queue.add(child);
                }
            }
        }

        nodes.forEach(n -> layer.putIfAbsent(n.getId(), 0));

        Map<Integer, List<QuestNode>> byLayer = new java.util.TreeMap<>();
        for (QuestNode n : nodes) byLayer.computeIfAbsent(layer.get(n.getId()), k -> new ArrayList<>()).add(n);

        for (Map.Entry<Integer, List<QuestNode>> e : byLayer.entrySet()) {
            if (e.getKey() == 0) continue;
            e.getValue().sort(java.util.Comparator.comparingDouble(n -> {
                List<QuestNode> prereqs = n.getPrerequisites().stream()
                        .filter(p -> selectedChapter.equalsIgnoreCase(p.getChapter())).toList();
                if (prereqs.isEmpty()) return 0.0;
                return prereqs.stream()
                        .mapToInt(p -> byLayer.getOrDefault(layer.getOrDefault(p.getId(), 0), List.of()).indexOf(p))
                        .average().orElse(0.0);
            }));
        }

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
        setFeedback("Auto-arranged %d quest(s)", nodes.size());
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

    private void rebuildSubgraph() {
        subgraphNodes.clear();
        if (selectedNode == null) return;
        subgraphNodes.add(selectedNode.getId());

        java.util.ArrayDeque<QuestNode> queue = new java.util.ArrayDeque<>();
        queue.add(selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode p : cur.getPrerequisites()) {
                if (subgraphNodes.add(p.getId())) queue.add(p);
            }
        }

        queue.add(selectedNode);
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

    private void buildLineCache() {
        List<int[]> edges = new ArrayList<>();
        List<ResourceLocation[]> edgeNodes = new ArrayList<>();

        int leftBound = sidebarVisualW();
        int rightBound = this.width;
        int topBound = HEADER_H;
        int bottomBound = this.height;

        int linePadding = 400;

        for (Map.Entry<ResourceLocation, int[]> e : nodeScreenPos.entrySet()) {
            QuestNode parent = QuestTreeRegistry.getQuest(e.getKey());
            if (parent == null || !catMatches(parent)) continue;

            if (parent.isFlagDisabled()) continue;
            if (parent.isHideDepLine()) continue;

            int[] pPos = e.getValue();
            if (pPos == null) continue;
            int parentSz = scaledNodeSize(parent);
            int px = pPos[0] + parentSz / 2, py = pPos[1] + parentSz / 2;
            QuestState ps = getState(parent);

            List<QuestNode> children = parent.getChildren();
            if (children != null) {
                for (QuestNode child : children) {
                    if (child == null || !catMatches(child)) continue;
                    if (child.isHideDepLine()) continue;

                    int[] cPos = nodeScreenPos.get(child.getId());
                    if (cPos == null) continue;
                    int childSz = scaledNodeSize(child);
                    int cx2 = cPos[0] + childSz / 2, cy2 = cPos[1] + childSz / 2;

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

                        col = C_LINE_LOCKED;
                        style = 0;
                    }
                    int isPlainEdge = !isForbidden && !isCosmeticEdge && !isLinkEdge && !isOptionalPrereq ? 1 : 0;

                    int shapeOrd = -1, visOrd = -1, speedOrd = -1, arrowOrd = -1;
                    try {
                        var shapeOv = child.getPrereqLineShape(parent.getId());
                        if (shapeOv != null) shapeOrd = shapeOv.ordinal();
                        var visOv = child.getPrereqLineVisual(parent.getId());
                        if (visOv != null) visOrd = visOv.ordinal();
                        var speedOv = child.getPrereqLineSpeed(parent.getId());
                        if (speedOv != null) speedOrd = speedOv.ordinal();
                        Boolean arrowOv = child.getPrereqLineArrow(parent.getId());
                        if (arrowOv != null) arrowOrd = arrowOv ? 1 : 0;
                    } catch (Exception ignored) {}

                    int parentShapeKind = lineTrimShapeKind(parent);
                    int childShapeKind = lineTrimShapeKind(child);
                    int horizontalBulge = worldHorizontalBulge(parent, child);

                    edges.add(new int[] {
                            px, py, cx2, cy2, col, style, shapeOrd, visOrd, speedOrd, arrowOrd, parentSz, childSz,
                            parentShapeKind, childShapeKind, isPlainEdge, horizontalBulge });
                    edgeNodes.add(new ResourceLocation[] { parent.getId(), child.getId() });
                }
            }

            List<QuestNode> prerequisites = parent.getPrerequisites();
            if (prerequisites != null) {
                for (QuestNode prereq : prerequisites) {
                    if (prereq == null) continue;

                    List<QuestNode> prereqChildren = prereq.getChildren();
                    if (prereqChildren != null && prereqChildren.contains(parent)) continue;

                    if (!catMatches(prereq)) continue;
                    if (prereq.isFlagDisabled()) continue;

                    int[] prereqPos = nodeScreenPos.get(prereq.getId());
                    if (prereqPos == null) continue;

                    int prereqSz = scaledNodeSize(prereq);
                    int prx = prereqPos[0] + prereqSz / 2, pry = prereqPos[1] + prereqSz / 2;

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

                        col = C_LINE_LOCKED;
                        style = 0;
                    }
                    int isPlainEdge = !isForbidden && !isCosmeticEdge && !isLinkEdge && !isOptional ? 1 : 0;

                    int shapeOrd = -1, visOrd = -1, speedOrd = -1, arrowOrd = -1;
                    try {
                        var shapeOv = parent.getPrereqLineShape(prereq.getId());
                        if (shapeOv != null) shapeOrd = shapeOv.ordinal();
                        var visOv = parent.getPrereqLineVisual(prereq.getId());
                        if (visOv != null) visOrd = visOv.ordinal();
                        var speedOv = parent.getPrereqLineSpeed(prereq.getId());
                        if (speedOv != null) speedOrd = speedOv.ordinal();
                        Boolean arrowOv = parent.getPrereqLineArrow(prereq.getId());
                        if (arrowOv != null) arrowOrd = arrowOv ? 1 : 0;
                    } catch (Exception ignored) {}

                    int prereqShapeKind = lineTrimShapeKind(prereq);
                    int parentShapeKind2 = lineTrimShapeKind(parent);
                    int horizontalBulge2 = worldHorizontalBulge(prereq, parent);

                    edges.add(new int[] {
                            prx, pry, px, py, col, style, shapeOrd, visOrd, speedOrd, arrowOrd, prereqSz, parentSz,
                            prereqShapeKind, parentShapeKind2, isPlainEdge, horizontalBulge2 });
                    edgeNodes.add(new ResourceLocation[] { prereq.getId(), parent.getId() });
                }
            }
        }

        depLineRenderer.rebuild(edges, edgeNodes, posZoom(), QuestChroniclesSettings.get());
    }

    private static int worldHorizontalBulge(QuestNode a, QuestNode b) {
        int adx = Math.abs(a.getCustomX() - b.getCustomX());
        int ady = Math.abs(a.getCustomY() - b.getCustomY());
        return adx >= ady ? 1 : 0;
    }

    private static int lineTrimShapeKind(QuestNode n) {
        String s = n.getShapeType();
        if (s == null) return 0;
        return switch (s.toUpperCase(java.util.Locale.ROOT)) {
            case "STAR" -> 1;
            case "HEXAGON" -> 2;
            case "DIAMOND" -> 3;
            case "PENTAGON" -> 4;
            case "CIRCLE" -> 5;
            default -> 0;
        };
    }

    private void softRebuild() {
        Map<String, int[]> savedProgress = new HashMap<>(progressCache);
        Map<String, Boolean> savedAttention = new HashMap<>(attentionCache);
        Map<ResourceLocation, List<String>> savedValidation = validationPanel.snapshot();
        List<String> savedStubs = stubChapterCache;
        List<String> savedCats = chapterListCache;
        rebuild();
        progressCache.putAll(savedProgress);
        attentionCache.putAll(savedAttention);
        validationPanel.restore(savedValidation);
        stubChapterCache = savedStubs;
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
        if (searchBox != null && searchBox.isFocused()) {
            if (key == 256) {
                searchBox.setFocused(false);
                return true;
            }
            searchBox.keyPressed(key, scan, mods);
            return true;
        }

        if (key == 256 && draggedNode != null) {
            if (bulkDragOrigPositions != null) {
                int count = bulkDragOrigPositions.size();
                for (Map.Entry<ResourceLocation, int[]> e : bulkDragOrigPositions.entrySet()) {
                    QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
                    if (n != null) {
                        n.setCustomPosition(e.getValue()[0], e.getValue()[1]);
                        saveNodeToDisk(n);
                    }
                }
                bulkDragOrigPositions = null;
                setFeedback("Move cancelled (%d quests)", count);
            } else {
                draggedNode.setCustomPosition(dragOrigX, dragOrigY);
                saveNodeToDisk(draggedNode);
                setFeedback("Move cancelled");
            }
            draggedNode = null;
            pickupPlaceActive = false;
            middleDragPickupActive = false;
            dragForceSnap = false;
            rebuild();
            return true;
        }

        if (key == 256 && lastMovedNode != null) {
            System.currentTimeMillis();
        }

        boolean ctrl = (mods & 2) != 0;

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
                    setFeedback("%s: %s", pinPrefix, hovered.getTitle().getString());
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

        if (key == 256) {
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
                BackgroundPictureConfig.save();
                setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
                pictureEditMode = null;
                return true;
            }
            if (nodeSizeEditMode != null) {
                net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(nodeSizeEditMode);
                setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
                nodeSizeEditMode = null;
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
                undoRedo.undo();
                return true;
            }
            if (key == 89 || (key == 90 && shift)) {
                undoRedo.redo();
                return true;
            }
        }

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
            subgraphMode = !subgraphMode;
            if (subgraphMode) rebuildSubgraph();
            return true;
        }

        if (key == 67 && ctrl && !shift && isDevMode && selectedNode != null) {
            questCopy(selectedNode);
            return true;
        }

        if (key == 86 && ctrl && !shift && isDevMode) {
            questPaste();
            return true;
        }

        if (key == 68 && ctrl && !shift && isDevMode && selectedNode != null) {
            duplicateQuest(selectedNode);
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

        if (key == 68) {
            quickDepKeyDown = true;
        }

        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == 68) {
            quickDepKeyDown = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    private void questCopy(QuestNode node) {
        String content = QuestFileSaver.readRawSnbt(node);
        if (content == null || content.isBlank()) {
            setFeedback("§cCopy failed — quest file not found on disk");
            return;
        }
        questClipboard = content;
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(content);
        setFeedback("§aCopied SNBT for '%s'  (Ctrl+V to paste)", node.getId().getPath());
    }

    private void questPaste() {
        String src = questClipboard;
        if (src == null || src.isBlank()) {
            src = minecraft != null ? minecraft.keyboardHandler.getClipboard() : null;
        }
        if (src == null || src.isBlank()) {
            setFeedback("§eNothing to paste (Ctrl+C a quest first)");
            return;
        }

        if (!src.contains("id:")) {
            setFeedback("§eClipboard doesn't look like quest SNBT");
            return;
        }
        try {
            String newPath = QuestFileSaver.pasteQuestFromSnbt(src, selectedChapter);
            rebuild();
            setFeedback("§aPasted → %s", newPath);

            ResourceLocation newId = new ResourceLocation("phoenix_chronicles", newPath);
            QuestNode pasted = QuestTreeRegistry.getQuest(newId);
            if (pasted != null) {
                undoRedo.push(() -> {
                    QuestTreeRegistry.removeQuest(newId);
                    deleteQuestFiles(pasted);
                    if (selectedNode == pasted) selectedNode = null;
                    rebuild();
                    setFeedback("Undo: pasted quest removed");
                });
            }
        } catch (IOException e) {
            setFeedback("§cPaste error: %s", e.getMessage());
        }
    }

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
        setFeedback("§aChained %d quests (%d new link%s)", ordered.size(), wired, wired == 1 ? "" : "s");
    }

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
        setFeedback("§aFanned from '%s' to %d quest%s", root.getId().getPath(), wired, wired == 1 ? "" : "s");
    }

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
                String skippedPart = r.skipped() > 0 ? " §c(%d skipped)".formatted(r.skipped()) : "";
                String warningsPart = r.warnings().isEmpty() ? "" :
                        " §8— %d warnings".formatted(r.warnings().size());
                setFeedback("§aImported %d quests%s%s", r.imported(), skippedPart, warningsPart);
                if (r.imported() > 0) {
                    QuestFileLoader.reloadAllQuestsFromDisk();

                    ChroniclesLangPack.reload();
                    rebuild();
                }
            }
        } catch (Exception e) {
            setFeedback("§cFTB import error: %s", e.getMessage());
        }
    }

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
                undoRedo.push(() -> {
                    QuestTreeRegistry.removeQuest(newId);
                    deleteQuestFiles(duplicated);
                    if (selectedNode == duplicated) selectedNode = null;
                    rebuild();
                    setFeedback("Undo: duplicate removed");
                });
            }
            rebuild();
            setFeedback("Duplicated → %s  (Ctrl+Z to undo)", newPath);
        } catch (IOException e) {
            e.printStackTrace();
            setFeedback("Duplicate failed: %s", e.getMessage());
        }
    }

    private void createLinkStubAt(int canvasX, int canvasY, QuestNode target) {
        if (target == null) return;

        String base = ("link_" + target.getId().getPath())
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]", "");
        if (base.isBlank()) base = "link_quest";

        String path = base;
        int suffix = 2;
        while (QuestTreeRegistry.getQuest(new ResourceLocation("phoenix_chronicles", path)) != null) {
            path = base + "_" + suffix;
            suffix++;
        }

        ResourceLocation newId = new ResourceLocation("phoenix_chronicles", path);
        QuestNode node = new QuestNode(newId, Component.literal(""), Component.literal(""));
        node.setChapter(selectedChapter);
        node.setCustomPosition(canvasX, canvasY);
        node.setLinkTarget(target.getId());

        QuestTreeRegistry.injectDynamicQuestNode(node, null);
        QuestFileSaver.saveOneQuestToDisk(node);
        rebuild();
        setFeedback("§aLinked → %s  (Ctrl+Z to undo)", target.getId().getPath());

        undoRedo.push(() -> {
            QuestTreeRegistry.removeQuest(newId);
            deleteQuestFiles(node);
            if (selectedNode == node) selectedNode = null;
            rebuild();
            setFeedback("Undo: link quest removed");
        });
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
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

        if (nodeSizeEditMode != null) {
            float step = 1.1f;
            if (hasShiftDown()) step = 1f + (step - 1f) * 0.3f;
            if (hasControlDown()) step = 1f + (step - 1f) * 0.3f;
            float factor = delta > 0 ? step : (1f / step);
            int newPx = Math.round(nodeSizeEditMode.getNodePixelSize() * factor);
            nodeSizeEditMode.setSizeOverridePx(newPx);
            refreshNodeScreenPos(nodeSizeEditMode);
            setFeedback("Size: %dpx  (scroll to resize - shift/ctrl for finer steps, drag to move, " +
                    "right-click/Esc to finish)", nodeSizeEditMode.getNodePixelSize());
            return true;
        }

        if (pictureEditMode != null) {

            float step = hasShiftDown() ? 1.05f : 1.2f;
            float factor = delta > 0 ? step : (1f / step);
            pictureEditMode.w = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.w * factor));
            pictureEditMode.h = Math.max(PIC_EDIT_MIN_SIZE, Math.min(PIC_EDIT_MAX_SIZE, pictureEditMode.h * factor));
            return true;
        }
        int cl = sidebarW(), cr = width;
        if (mx <= cl && my > HEADER_H) {

            sidebarPanel.scrollBy(delta, sidebarContentHeight(), sidebarScrollAreaHeight());
            return true;
        }
        if (mx <= cl || mx >= cr || my <= HEADER_H) return super.mouseScrolled(mx, my, delta);

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
        if (sidebarPanel.contextMenuOpen()) {
            if (btn == 0) sidebarPanel.handleContextMenuClick((int) mx, (int) my);
            else sidebarPanel.closeContextMenu();
            return true;
        }

        if (nodeSizeEditMode != null) {
            if (btn == 1) {
                net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(nodeSizeEditMode);
                setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
                nodeSizeEditMode = null;
                softRebuild();
            }
            return true;
        }

        if (pictureEditMode != null) {
            if (btn == 1) {
                BackgroundPictureConfig.save();
                setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
                pictureEditMode = null;
            }
            return true;
        }

        if (btn == 0 && tutorialOverlay.mouseClicked(this, mx, my, btn)) return true;

        if (btn == 0 && isInMinimap(mx, my)) {
            mmDragging = true;
            minimapPanTo(mx, my, sidebarW());
            softRebuild();
            return true;
        }

        int cl = sidebarW(), cr = width;

        if (btn == 0) {

            if (questbookTitleHovered((int) mx, (int) my)) {
                if (minecraft != null) minecraft.setScreen(new QuestbookTitleScreen(this));
                return true;
            }

            if (my >= 0 && my < TOOLBAR_Y) {

                String zoomStr2 = Math.round(zoom * 100) + "%";
                int zw2 = font.width(zoomStr2);
                int zx2 = cr - zw2 - 10;

                int unclaimedCount2 = unclaimedRewardCount();
                if (unclaimedCount2 > 0) {
                    String claimLabel2 = "🎁 " + unclaimedCount2;
                    int cw2 = font.width(claimLabel2);
                    int cpx2 = zx2 - cw2 - 18;
                    if (mx >= cpx2 - 3 && mx < cpx2 + cw2 + 5 && my >= 3 && my < 16) {
                        if (minecraft != null) minecraft.setScreen(new ClaimRewardsScreen(this));
                        return true;
                    }
                    zx2 = cpx2;
                }

                String gridLabel2 = !gridSnapEnabled ? "Grid: off" :
                        (gridSnap == 1) ? "Grid: free" : "Grid: " + gridSnap;
                int gw2 = font.width(gridLabel2);
                int gpx2 = zx2 - gw2 - 18;
                if (mx >= gpx2 - 3 && mx < gpx2 + gw2 + 5 && my >= 3 && my < 16) {

                    for (int gi = 0; gi < GRID_SNAP_CYCLE.length; gi++) {
                        if (GRID_SNAP_CYCLE[gi] == gridSnap) {
                            gridSnap = GRID_SNAP_CYCLE[(gi + 1) % GRID_SNAP_CYCLE.length];
                            break;
                        }
                    }
                    return true;
                }

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

            int[][] pills = filterPillBounds(cl, cr);
            for (int i = 0; i < toolbarPanel.filterKeyCount(); i++) {
                int[] b = pills[i];
                if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                    stateFilter = toolbarPanel.filterKey(i);
                    selectedNode = null;
                    softRebuild();
                    return true;
                }
            }

            if (!sidebarPanel.isHoverSidebar() && sidebarCollapseToggleHovered((int) mx, (int) my)) {
                sidebarPanel.setCollapsed(!sidebarPanel.collapsed());
                sidebarPanel.resetScroll();
                rebuild();
                return true;
            }

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
                            net.phoenixvine.chronicles.registry.CategoryRegistry.toggleCollapsed(row.id());
                            net.phoenixvine.chronicles.registry.CategoryRegistry.save();
                            rebuild();
                        } else if (row.locked()) {

                            setFeedback("§7Locked — complete a quest in the parent chapter first");
                        } else {
                            selectedChapter = row.id();
                            selectedNode = null;
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
        }

        if (btn == 1 && gearHovered((int) mx, (int) my) && isDevMode) {
            Path base = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            LangEditorScreen.writeEnUsJson(base);

            net.phoenixvine.chronicles.client.ChroniclesLangPack.reload();
            setFeedback("§aExported lang/en_us.json");
            return true;
        }

        if (btn == 1 && isDevMode && !isSidebarNarrow()) {
            SidebarRow hitRow = sidebarRowAt(buildSidebarRows(), (int) mx, (int) my);
            if (hitRow != null) {
                openSidebarContextMenu(hitRow, (int) mx, (int) my);
                return true;
            }
        }

        if (btn == 0 && isDevMode && multiSelection.size() >= 2) {
            int bx = cl + 4, by = HEADER_H + 4;
            int bh = BULK_PANEL_H;
            if ((int) mx >= bx && (int) mx <= bx + 360 && (int) my >= by && (int) my <= by + bh) {

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
                                setFeedback("Shape → CUSTOM for %d quests", targets.size());
                                rebuild();
                            }));
                            return true;
                        }
                        for (ResourceLocation id : multiSelection) {
                            QuestNode n = QuestTreeRegistry.getQuest(id);
                            if (n != null) {
                                n.setShapeType(newShape);

                                saveNodeShapeToDisk(n, newShape);
                            }
                        }
                        setFeedback("Shape → %s for %d quests", newShape, multiSelection.size());
                        rebuild();
                        return true;
                    }
                }
                int actX = startX + shapeIds.length * (slotW + 2) + 8;

                if ((int) mx >= actX && (int) mx < actX + 58 && (int) my >= slotY && (int) my < slotY + 12) {
                    bulkMoveCatOpen = !bulkMoveCatOpen;
                    return true;
                }

                int sizeSlotW = 50, sizeStartX = bx + 6, sizeSlotY = slotY + BULK_PANEL_ROW_H;
                String[] sizeLabels = { "Tiny", "Small", "Normal", "Large", "Huge" };
                QuestNode.NodeSize[] sizeVals = QuestNode.NodeSize.values();
                for (int i = 0; i < sizeLabels.length; i++) {
                    int sx = sizeStartX + i * (sizeSlotW + 2);
                    if ((int) mx >= sx && (int) mx < sx + sizeSlotW && (int) my >= sizeSlotY &&
                            (int) my < sizeSlotY + 12) {
                        QuestNode.NodeSize newSize = sizeVals[i];
                        for (ResourceLocation id : multiSelection) {
                            QuestNode n = QuestTreeRegistry.getQuest(id);
                            if (n != null) {
                                n.setNodeSize(newSize);
                                net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(n);
                            }
                        }
                        setFeedback("Size → %s for %d quests", sizeLabels[i], multiSelection.size());
                        rebuild();
                        return true;
                    }
                }

                if (bulkMoveCatOpen) {
                    List<String> moveCats = buildChapterList();
                    moveCats.remove("ALL");
                    int subX = actX, subY = sizeSlotY + BULK_PANEL_ROW_H, subRH = 11;
                    for (int ci = 0; ci < moveCats.size(); ci++) {
                        int ry = subY + 2 + ci * subRH;
                        if ((int) mx >= subX + 2 && (int) mx < subX + 90 - 2 && (int) my >= ry &&
                                (int) my < ry + subRH) {
                            String newCat = moveCats.get(ci);
                            for (ResourceLocation sid : new ArrayList<>(multiSelection)) {
                                QuestNode sn = QuestTreeRegistry.getQuest(sid);
                                if (sn != null) {
                                    sn.setChapter(newCat);

                                    saveNodeChapterToDisk(sn, newCat);
                                }
                            }
                            bulkMoveCatOpen = false;
                            setFeedback("Moved %d quests to %s", multiSelection.size(), friendly(newCat));
                            rebuild();
                            return true;
                        }
                    }
                }

                int delX = actX + 62;
                if ((int) mx >= delX && (int) mx < delX + 44 && (int) my >= slotY && (int) my < slotY + 12) {
                    int count = multiSelection.size();
                    for (ResourceLocation id : new ArrayList<>(multiSelection)) {
                        QuestNode n = QuestTreeRegistry.getQuest(id);
                        if (n != null) {
                            QuestTreeRegistry.removeQuest(id);

                            deleteQuestFiles(n);
                        }
                    }
                    multiSelection.clear();
                    rebuild();
                    setFeedback("Deleted %d quests", count);
                    return true;
                }
                return true;
            }
        }

        if (depLineRenderer.isContextMenuOpen() && btn == 0) {

            depLineRenderer.handleContextMenuClick((int) mx, (int) my, width, height,
                    this::buildLineCache, this::setFeedback, this::openLineSettingsFor);
            depLineRenderer.closeContextMenu();
            return true;
        }
        if (depLineRenderer.isContextMenuOpen()) {
            depLineRenderer.closeContextMenu();
            return true;
        }

        if (pictureCtxMenu.isOpen() && btn == 0) {
            pictureCtxMenu.mouseClicked(this, mx, my, btn);
            return true;
        }
        if (pictureCtxMenu.isOpen()) {
            pictureCtxMenu.close();
            return true;
        }

        if (ctxOpen && btn == 0) {
            if (handleCtxClick((int) mx, (int) my)) return true;
            ctxOpen = false;
            ctxMoveCatOpen = false;
            return true;
        }

        if (btn == 0 && isDevMode && hasControlDown() && !hasShiftDown()) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    if (multiSelection.contains(e.getKey())) multiSelection.remove(e.getKey());
                    else multiSelection.add(e.getKey());
                    return true;
                }
            }

            multiSelection.clear();
            return true;
        }

        if (btn == 0 && pickupPlaceActive && draggedNode != null) {
            boolean wasBulk = bulkDragOrigPositions != null;
            finalizeDragRelease();
            draggedNode = null;
            pickupPlaceActive = false;
            dragForceSnap = false;
            softRebuild();
            if (!wasBulk) setFeedback("Placed");
            return true;
        }

        if (btn == 2 && middleDragPickupActive && draggedNode != null) {
            boolean wasBulk = bulkDragOrigPositions != null;
            finalizeDragRelease();
            draggedNode = null;
            middleDragPickupActive = false;
            dragForceSnap = false;
            softRebuild();
            if (!wasBulk) setFeedback("Placed");
            return true;
        }

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

        if (btn == 0 && isDevMode && hasShiftDown()) {

            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (draggedNode != null) {
                        pickupPlaceActive = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                                .getNodeMoveMode() ==
                                net.phoenixvine.chronicles.codec.QuestChroniclesSettings.NodeMoveMode.PICKUP_PLACE;
                        final int preX = draggedNode.getCustomX(), preY = draggedNode.getCustomY();
                        dragOrigX = preX;
                        dragOrigY = preY;
                        final QuestNode capturedNode = draggedNode;
                        beginDragUndo(capturedNode, preX, preY);
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        selectedNode = draggedNode;
                        if (subgraphMode) rebuildSubgraph();
                    }
                    return true;
                }
            }

            QuestGroup hitGrp = groupAtLabelBar(mx, my, cl);
            if (hitGrp != null) {
                draggedGroup = hitGrp;
                int sx = (int) (hitGrp.getX() * posZoom()) + viewOffX + cl;
                int sy = (int) (hitGrp.getY() * posZoom()) + viewOffY + HEADER_H;
                groupDragGrabX = (int) mx - sx;
                groupDragGrabY = (int) my - sy;
                return true;
            }

            BackgroundPictureConfig.Picture hitPic = pictureAt(mx, my, cl);
            if (hitPic != null) {
                final BackgroundPictureConfig.Picture capturedPic = hitPic;
                final float preX = hitPic.x, preY = hitPic.y;
                undoRedo.push(() -> {
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

        if (btn == 2 && isDevMode) {
            for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (draggedNode != null) {
                        final int preX = draggedNode.getCustomX(), preY = draggedNode.getCustomY();
                        dragOrigX = preX;
                        dragOrigY = preY;
                        final QuestNode capturedNode = draggedNode;
                        beginDragUndo(capturedNode, preX, preY);
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        selectedNode = draggedNode;
                        dragForceSnap = true;
                        middleDragPickupActive = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                                .isMiddleClickPickupPlace();
                        if (subgraphMode) rebuildSubgraph();
                    }
                    return true;
                }
            }
        }

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

            if (hit == null && hitGrp == null && depLineRenderer.tryOpenContextMenuAt((int) mx, (int) my, 6)) {
                ctxOpen = false;
                return true;
            }
            openCtx((int) mx, (int) my, hit, hitGrp);
            return true;
        }

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

                if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedChapter));
            }
        }

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

        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleCtxClick(int mx, int my) {
        List<CtxItem> items = buildCtxItems();
        int x = ctxX, y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW;

        for (CtxItem item : items) {
            if (item.isSep()) {
                y += CTX_SEP;
                continue;
            }
            if (mx >= x && mx <= x + CTX_W && my >= y && my <= y + CTX_ROW) {
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
                        ctxNode.setChapter(newCat);
                        saveNodeChapterToDisk(ctxNode, newCat);
                        setFeedback("Moved to %s", friendly(newCat));
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
        if ((pickupPlaceActive || middleDragPickupActive) && draggedNode != null) {
            updateDraggedNodeScreenPos(mx, my, currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(draggedNode.getId(), this::nodeCenterForLine, posZoom(),
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
        if (btn == 0 && sidebarPanel.dragRow() != null) {
            if (!sidebarPanel.dragMoved() &&
                    (Math.abs(mx - sidebarPanel.dragStartX()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD ||
                            Math.abs(my - sidebarPanel.dragStartY()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD)) {
                sidebarPanel.setDragMoved(true);
            }
            return true;
        }
        if (nodeSizeEditMode != null) {
            if (btn == 0) {
                int snap = currentDragSnap();
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
                    refreshNodeScreenPos(nodeSizeEditMode);
                    depLineRenderer.refreshEdgeEndpoints(nodeSizeEditMode.getId(), this::nodeCenterForLine, posZoom(),
                            QuestChroniclesSettings.get());
                }
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

                int screenX = (int) mx - pictureDragGrabX;
                int screenY = (int) my - pictureDragGrabY;
                float canvasX = (screenX - cl - viewOffX) / posZoom() + draggedPicture.w / 2f;
                float canvasY = (screenY - HEADER_H - viewOffY) / posZoom() + draggedPicture.h / 2f;
                draggedPicture.x = canvasX;
                draggedPicture.y = canvasY;
                return true;
            }
            if (draggedNode != null) {
                updateDraggedNodeScreenPos(mx, my, currentDragSnap());
                depLineRenderer.refreshEdgeEndpoints(draggedNode.getId(), this::nodeCenterForLine, posZoom(),
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
        }

        if (btn == 2 && draggedNode != null) {
            updateDraggedNodeScreenPos(mx, my, currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(draggedNode.getId(), this::nodeCenterForLine, posZoom(),
                    QuestChroniclesSettings.get());
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && nodeSizeEditMode != null) {
            nodeSizeDragAccX = 0;
            nodeSizeDragAccY = 0;
        }
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

            rescaleForZoom();
            return true;
        }
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
                    } else if (target != null && src.getPrerequisites().contains(target)) {
                        setFeedback("§cCan't link — would create a dependency cycle");
                    } else if (target != null && target.getPrerequisites().contains(src)) {
                        setFeedback("§eAlready a prerequisite");
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
            if (draggedNode != null && !pickupPlaceActive) {
                finalizeDragRelease();
                draggedNode = null;
                dragForceSnap = false;
                softRebuild();
                return true;
            }
            if (isPanning) {

                buildLineCache();
            }
            isPanning = false;
        }
        if (btn == 2 && draggedNode != null && !middleDragPickupActive) {
            finalizeDragRelease();
            draggedNode = null;
            dragForceSnap = false;
            softRebuild();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public List<CtxItem> buildCtxItems() {
        List<CtxItem> items = new ArrayList<>();
        boolean hasNode = (ctxNode != null);
        boolean hasGroup = (ctxGroup != null);

        boolean canEdit = !testMode;

        if (!hasNode && !hasGroup && canEdit) {
            items.add(new CtxItem("+ New quest", "§a", false, false,
                    () -> {
                        ctxOpen = false;
                        int cl = sidebarW();
                        int canvasX = (int) ((ctxRawX - cl - viewOffX) / posZoom());
                        int canvasY = (int) ((ctxRawY - HEADER_H - viewOffY) / posZoom());
                        minecraft.setScreen(new QuestCreatorScreen(this, canvasX, canvasY, selectedChapter));
                    }));
            items.add(new CtxItem("🔗 Link quest here…", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        int cl = sidebarW();
                        int canvasX = (int) ((ctxRawX - cl - viewOffX) / posZoom());
                        int canvasY = (int) ((ctxRawY - HEADER_H - viewOffY) / posZoom());
                        minecraft.setScreen(ParentSelectorScreen.singleSelect(this, null,
                                target -> createLinkStubAt(canvasX, canvasY, target)));
                    }));
        }

        if (!hasNode && !hasGroup) {
            final String cat = selectedChapter;
            items.add(new CtxItem("Dependency Lines", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new DepLineSettingsScreen(this, cat));
                    }));
        }

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

        if (!hasNode && !hasGroup && canEdit) {
            String label = questClipboard != null ? "⎘ Paste quest" : "⎘ Paste quest §8(clipboard)";
            items.add(new CtxItem(label, "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        questPaste();
                    }));
        }

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

                        if (subgraphMode && selectedNode != null) {
                            rebuildSubgraph();
                        }
                    }));
            items.add(new CtxItem((statsPanel.isOpen() ? "§b∑ Hide stats" : "∑ Show stats"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        toggleStatsPanel();
                    }));

            items.add(new CtxItem((gridSnapEnabled ? "⊞ Grid Snap: ON" : "§7⊞ Grid Snap: OFF"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        gridSnapEnabled = !gridSnapEnabled;
                    }));
            items.add(new CtxItem("⊞ Show Grid: " + gridDisplayMode.label, "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        gridDisplayMode = gridDisplayMode.next();
                    }));
        }

        if (!hasNode && !hasGroup && canEdit) {
            int cl = sidebarW();
            items.add(new CtxItem("+ New group here", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        int lx = (int) ((ctxX - cl - viewOffX) / posZoom());
                        int ly = (int) ((ctxY - HEADER_H - viewOffY) / posZoom());
                        minecraft.setScreen(new QuestGroupEditorScreen(this, selectedChapter, null, lx, ly));
                    }));
            items.add(new CtxItem("Edit chapter theme…", "§d", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(new ChapterThemeScreen(this, selectedChapter));
                    }));
            items.add(new CtxItem("🖼 Add picture…", "§d", false, false,
                    () -> {
                        ctxOpen = false;
                        final float px = (ctxX - cl - viewOffX) / posZoom();
                        final float py = (ctxY - HEADER_H - viewOffY) / posZoom();
                        final String cat = selectedChapter;
                        minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                            BackgroundPictureConfig.Picture pic = new BackgroundPictureConfig.Picture();
                            pic.texture = rl;
                            pic.x = px;
                            pic.y = py;
                            ResourceLocation loc;
                            try {
                                loc = CustomTextureCache.resolve(new ResourceLocation(rl));
                            } catch (Exception e) {
                                loc = null;
                            }

                            int[] nativeSz = loc != null ? CustomTextureCache.nativeSize(loc) : null;
                            if (nativeSz != null && nativeSz[0] > 0 && nativeSz[1] > 0) {
                                float maxDim = 128f;
                                float scale = maxDim / Math.max(nativeSz[0], nativeSz[1]);
                                pic.w = nativeSz[0] * scale;
                                pic.h = nativeSz[1] * scale;
                            }
                            BackgroundPictureConfig.add(cat, pic);
                            setFeedback("Picture placed — shift+drag to move, right-click to remove");
                        }));
                    }));
            items.add(CtxItem.sep());
            items.add(new CtxItem("⊞ Auto-arrange chapter", "§e", false, false,
                    () -> {
                        ctxOpen = false;
                        autoArrangeChapter();
                    }));
        }

        if (hasGroup && canEdit) {
            items.add(CtxItem.sep());
            QuestGroup grp = ctxGroup;
            items.add(new CtxItem("Edit group…", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        minecraft.setScreen(
                                new QuestGroupEditorScreen(this, selectedChapter, grp, grp.getX(), grp.getY()));
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
            if (ctxNode.isLinkStub() && ctxLinkTarget != null) {
                final QuestNode jumpTarget = ctxLinkTarget;
                items.add(new CtxItem("🔗 Jump to linked quest", "§b", false, false,
                        () -> {
                            ctxOpen = false;
                            navigateToNode(jumpTarget);
                        }));
                items.add(CtxItem.sep());
            }
            if (!ctxNode.getChildren().isEmpty()) {
                boolean nowCollapsed = collapsedSubtreeRoots.contains(ctxNode.getId());
                items.add(new CtxItem(nowCollapsed ? "▶ Expand Subtree" : "▼ Collapse Subtree", "§7", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            toggleSubtreeCollapse(target);
                        }));
            }
            if (canEdit) {
                items.add(new CtxItem("Edit Quest", "§7", false, false,
                        () -> {
                            ctxOpen = false;
                            minecraft.setScreen(new QuestCreatorScreen(this, ctxNode));
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
                items.add(new CtxItem("Set Icon…", "§7", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            minecraft.setScreen(new SetIconScreen(this, target));
                        }));
                items.add(new CtxItem("Resize (scroll + drag)…", "§7", false, false,
                        () -> {

                            final QuestNode editedNode = ctxNode;
                            final QuestNode.NodeSize uSize = ctxNode.getNodeSize();
                            final int uOverridePx = ctxNode.getSizeOverridePx();
                            final int uX = ctxNode.getCustomX(), uY = ctxNode.getCustomY();
                            undoRedo.push(() -> {
                                editedNode.setNodeSize(uSize);
                                if (uOverridePx > 0) editedNode.setSizeOverridePx(uOverridePx);
                                editedNode.setCustomPosition(uX, uY);
                                net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(editedNode);

                                rebuild();
                                setFeedback("Undo: node resize reverted");
                            });

                            ctxNode.setSizeOverridePx(ctxNode.getNodePixelSize());
                            nodeSizeEditMode = ctxNode;
                            nodeSizeDragAccX = 0;
                            nodeSizeDragAccY = 0;
                            setFeedback("§eScroll to resize, drag to move - right-click or Esc to finish");
                            ctxOpen = false;
                        }));
                items.add(CtxItem.sep());

                items.add(new CtxItem("Move to Chapter  ▸", "§7", false, false, () -> {}));
                items.add(CtxItem.sep());
                items.add(new CtxItem("Shift + drag to move", "§8", false, false,
                        () -> {
                            ctxOpen = false;
                            setFeedback("Shift-click and drag the node");
                        }));
            }
            items.add(CtxItem.sep());
            items.add(new CtxItem("Dependency Lines", "§b", false, false,
                    () -> {
                        ctxOpen = false;
                        final String cat = selectedChapter;
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

                                QuestState preState = playerData != null ?
                                        playerData.getQuestState(target.getId(), QuestState.LOCKED) : QuestState.LOCKED;
                                undoRedo.push(() -> {
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

                                mc.player.connection.sendCommand("chronicles complete " + target.getId().getPath());
                                setFeedback("Force-completed: %s  (Ctrl+Z to undo)", target.getTitle().getString());
                            }
                        }));
                items.add(new CtxItem("Reset Progress", "§7", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {

                                mc.player.connection.sendCommand("chronicles reset " + target.getId().getPath());
                                setFeedback("Progress reset: %s", target.getTitle().getString());
                            }
                        }));
                items.add(new CtxItem("Delete Quest", "§c", false, true,
                        () -> {
                            final QuestNode deleted = ctxNode;

                            final String savedContent = QuestFileSaver.readRawSnbt(deleted);
                            final Path categoryFolder = QuestFileSaver.getQuestChapterFolder(deleted);
                            undoRedo.push(() -> {

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
        ctxRawX = x;
        ctxRawY = y;
        ctxNode = node;
        ctxGroup = group;
        List<CtxItem> items = buildCtxItems();
        int menuH = menuHeight(items);
        if (ctxY + menuH > height - 4) ctxY = height - menuH - 4;
        if (ctxX + CTX_W > width - 4) ctxX = width - CTX_W - 4;

        ctxX = Math.max(4, ctxX);
        ctxY = Math.max(4, ctxY);
    }

    @Override
    public int menuHeight(List<CtxItem> items) {
        int h = 4;
        if (ctxNode != null) h += CTX_ROW;
        for (CtxItem i : items) h += i.isSep() ? CTX_SEP : CTX_ROW;
        return h;
    }

    private int ctxMoveCatY(List<CtxItem> items) {
        int y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW;
        for (CtxItem item : items) {
            if (!item.isSep() && item.label().contains("Move to Chapter")) return y;
            y += item.isSep() ? CTX_SEP : CTX_ROW;
        }
        return y;
    }

    @Override
    public int ctxMoveCatX(int catCount) {
        int subW = CTX_W + (catCount > CTX_MOVE_CAT_MAX_ROWS ? 6 : 0);
        int x = ctxX + CTX_W + 2;
        if (x + subW > width - 4) x = ctxX - subW - 2;
        return Math.max(4, x);
    }

    @Override
    public int ctxMoveCatYClamped(List<CtxItem> items, int catCount) {
        int y = ctxMoveCatY(items);
        int visibleRows = Math.min(catCount, CTX_MOVE_CAT_MAX_ROWS);
        int subH = visibleRows * CTX_ROW + 4;
        if (y + subH > height - 4) y = height - subH - 4;
        return Math.max(4, y);
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

        handleLiveDragging(mx, my);

        int cl = sidebarVisualW();
        int cr = width;
        int sz = scaledNodeSize();

        long animTick = QuestChroniclesSettings.get().isReduceMotion() ? 0L : System.currentTimeMillis();

        FrameProfiler.begin("header");
        renderHeaderAndBaseLayout(g, mx, my, cl, cr);
        FrameProfiler.end("header");

        PhantasiaCompat.tickPreview(phantasiaPreview);

        switch (gridDisplayMode) {
            case ALWAYS -> renderSnapGridOverlay(g, cl, cr);
            case CURSOR_BOX -> renderSnapCursorBox(g, mx, my, cl, cr);
            case ON_DRAG -> {
                if (draggedNode != null) renderSnapGridOverlay(g, cl, cr);
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

        if (draggedNode != null) renderDragSnapPosBox(g, mx, my);

        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, false);

        for (Runnable r : pendingDeferredDraws) r.run();
        pendingDeferredDraws.clear();

        if (minimapOpen) renderMinimap(g, mx, my, cl, cr);

        FrameProfiler.end("TOTAL render()");
        FrameProfiler.endFrame();
        if (FrameProfiler.isEnabled()) renderProfilerPanel(g);
    }

    private void handleLiveDragging(int mx, int my) {
        if (draggedNode == null) return;
        updateDraggedNodeScreenPos(mx, my, currentDragSnap());
    }

    private int currentDragSnap() {
        if (dragForceSnap) return gridSnap;
        return gridSnapEnabled ? gridSnap : 1;
    }

    private int[] computeDraggedNodeSnapLogicalPos(double mx, double my, int snap) {
        int cl = sidebarW();
        int logX = (int) ((mx - dragGrabX - cl - viewOffX) / posZoom());
        int logY = (int) ((my - dragGrabY - HEADER_H - viewOffY) / posZoom());
        logX = Math.round((float) logX / snap) * snap;
        logY = Math.round((float) logY / snap) * snap;
        return new int[] { logX, logY };
    }

    private void updateDraggedNodeScreenPos(double mx, double my, int snap) {
        int cl = sidebarW();
        int[] logPos = computeDraggedNodeSnapLogicalPos(mx, my, snap);
        int logX = logPos[0], logY = logPos[1];

        int nx = (int) (logX * posZoom()) + cl + viewOffX;
        int ny = (int) (logY * posZoom()) + HEADER_H + viewOffY;

        NodeHitbox b = nodeButtons.get(draggedNode.getId());
        if (b != null) {
            b.setX(nx);
            b.setY(ny);
        }

        nodeScreenPos.put(draggedNode.getId(), new int[] { nx, ny });
        draggedNode.setCustomPosition(logX, logY);

        if (bulkDragOrigPositions != null) {
            int[] grabbedOrig = bulkDragOrigPositions.get(draggedNode.getId());
            if (grabbedOrig != null) {
                int deltaX = logX - grabbedOrig[0];
                int deltaY = logY - grabbedOrig[1];
                for (Map.Entry<ResourceLocation, int[]> e : bulkDragOrigPositions.entrySet()) {
                    if (e.getKey().equals(draggedNode.getId())) continue;
                    QuestNode other = QuestTreeRegistry.getQuest(e.getKey());
                    if (other == null) continue;
                    other.setCustomPosition(e.getValue()[0] + deltaX, e.getValue()[1] + deltaY);
                    refreshNodeScreenPos(other);
                    depLineRenderer.refreshEdgeEndpoints(other.getId(), this::nodeCenterForLine, posZoom(),
                            QuestChroniclesSettings.get());
                }
            }
        }
    }

    private void beginDragUndo(QuestNode capturedNode, int preX, int preY) {
        if (multiSelection.contains(capturedNode.getId()) && multiSelection.size() >= 2) {
            Map<ResourceLocation, int[]> orig = new LinkedHashMap<>();
            for (ResourceLocation id : multiSelection) {
                QuestNode n = QuestTreeRegistry.getQuest(id);
                if (n != null) orig.put(id, new int[] { n.getCustomX(), n.getCustomY() });
            }
            bulkDragOrigPositions = orig;
            undoRedo.push(() -> {
                for (Map.Entry<ResourceLocation, int[]> e : orig.entrySet()) {
                    QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
                    if (n != null) {
                        n.setCustomPosition(e.getValue()[0], e.getValue()[1]);
                        saveNodeToDisk(n);
                    }
                }
                rebuild();
            });
        } else {
            bulkDragOrigPositions = null;
            undoRedo.push(() -> {
                capturedNode.setCustomPosition(preX, preY);
                saveNodeToDisk(capturedNode);
                rebuild();
            });
        }
    }

    private void finalizeDragRelease() {
        if (bulkDragOrigPositions != null) {
            int count = bulkDragOrigPositions.size();
            for (ResourceLocation id : bulkDragOrigPositions.keySet()) {
                QuestNode n = QuestTreeRegistry.getQuest(id);
                if (n != null) saveNodeToDisk(n);
            }
            bulkDragOrigPositions = null;
            lastMovedNode = null;
            setFeedback("Moved %d quests", count);
        } else if (draggedNode != null) {
            saveNodeToDisk(draggedNode);
            lastMovedNode = draggedNode;
            lastMoveOrigX = dragOrigX;
            lastMoveOrigY = dragOrigY;
            lastMoveTimeMs = System.currentTimeMillis();
        }
    }

    private void renderDragSnapPosBox(GuiGraphics g, int mx, int my) {
        if (draggedNode == null) return;
        int[] logPos = computeDraggedNodeSnapLogicalPos(mx, my, currentDragSnap());
        String label = "X: " + logPos[0] + ", Y: " + logPos[1];
        int tw = font.width(label);
        int bx = mx + 14, by = my + 14;
        g.fill(bx - 3, by - 2, bx + tw + 3, by + 11, 0xCC101010);
        ChroniclesUIKit.drawBorder(g, bx - 3, by - 2, tw + 6, 13, 0x66FFFFFF);
        g.drawString(font, "§f" + label, bx, by, C_TEXT, false);
    }

    private void refreshNodeScreenPos(QuestNode node) {
        int cl = sidebarW();
        int sx = (int) (node.getCustomX() * posZoom()) + viewOffX + cl;
        int sy = (int) (node.getCustomY() * posZoom()) + viewOffY + HEADER_H;
        nodeScreenPos.put(node.getId(), new int[] { sx, sy });
        NodeHitbox b = nodeButtons.get(node.getId());
        if (b != null) {
            int sz = scaledNodeSize(node);
            b.setX(sx);
            b.setY(sy);
            b.w = sz;
            b.h = sz;
        }
    }

    private void renderSnapGridOverlay(GuiGraphics g, int cl, int cr) {
        if (gridSnap <= 1) return;
        int step = Math.round(gridSnap * posZoom());
        if (step < 6) return;

        int top = HEADER_H, bottom = height;
        int color = 0x40FFFFFF;

        int firstX = cl + Math.floorMod(viewOffX, step);
        int firstY = top + Math.floorMod(viewOffY, step);
        for (int x = firstX; x < cr; x += step) {
            for (int y = firstY; y < bottom; y += step) {
                NodeShapeRenderer.queueFillRect(g, x, y, x + 1, y + 1, color);
            }
        }
        NodeShapeRenderer.flushFillQueue(g);
    }

    private void renderSnapCursorBox(GuiGraphics g, int mx, int my, int cl, int cr) {
        if (gridSnap <= 1) return;
        if (mx < cl || mx > cr || my < HEADER_H || my > height) return;

        int snap = gridSnap;
        int logX = (int) ((mx - cl - viewOffX) / posZoom());
        int logY = (int) ((my - HEADER_H - viewOffY) / posZoom());
        logX = Math.round((float) logX / snap) * snap;
        logY = Math.round((float) logY / snap) * snap;

        int sx = (int) (logX * posZoom()) + cl + viewOffX;
        int sy = (int) (logY * posZoom()) + HEADER_H + viewOffY;
        int sz = Math.round(snap * posZoom());
        if (sz < 4) return;

        int color = 0x88FFFFFF;
        g.fill(sx, sy, sx + sz, sy + 1, color);
        g.fill(sx, sy + sz - 1, sx + sz, sy + sz, color);
        g.fill(sx, sy, sx + 1, sy + sz, color);
        g.fill(sx + sz - 1, sy, sx + sz, sy + sz, color);
    }

    private void renderHeaderAndBaseLayout(GuiGraphics g, int mx, int my, int cl, int cr) {
        renderBackground(g);
        g.fill(0, 0, sidebarW(), height, C_PANEL_DARK);
        g.fill(cl, 0, cr, height, C_BG);
        g.fill(cr, 0, width, height, C_PANEL_DARK);
        g.fill(cr, 0, cr + 1, height, C_BORDER);

        g.fill(0, 0, width, TOOLBAR_Y, C_HEADER);
        g.fill(0, TOOLBAR_Y - 1, width, TOOLBAR_Y, C_BORDER);
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
        g.drawString(font, titleToDraw, cl + 8, 7, C_TEXT);
        if (testMode) g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFCC2222);
        if (pictureEditMode != null) {
            g.fill(cl, TOOLBAR_Y - 1, cr, TOOLBAR_Y, 0xFFFFCC33);
            String hint = "§e🖼 Editing picture — scroll to resize (shift = fine), drag to move, right-click/Esc to finish";
            g.drawCenteredString(font, hint, (cl + cr) / 2, 7, 0xFFFFEEAA);
        }

        g.enableScissor(0, 0, sidebarVisualW(), TOOLBAR_Y);
        renderQuestbookTitle(g, mx, my);
        g.disableScissor();

        String zoomStr = Math.round(zoom * 100) + "%";
        int zw = font.width(zoomStr);
        int zx = cr - zw - 10, zy = 3;
        g.fill(zx - 3, zy, zx + zw + 3, zy + 13, 0x22FFFFFF);
        g.drawString(font, "§7" + zoomStr, zx, zy + 3, C_TEXT_DIM);

        int unclaimedCount = unclaimedRewardCount();
        if (unclaimedCount > 0) {
            String claimLabel = "§d🎁 " + unclaimedCount;
            int cw = font.width(net.minecraft.util.StringUtil.stripColor(claimLabel));
            int cpx = zx - cw - 18, cpy = 3;
            boolean claimHov = mx >= cpx - 3 && mx < cpx + cw + 5 && my >= cpy && my < cpy + 13;
            g.fill(cpx - 3, cpy, cpx + cw + 5, cpy + 13, claimHov ? 0x44FFFFFF : 0x33AA4488);
            g.drawString(font, claimLabel, cpx, cpy + 3, C_TEXT_DIM, false);
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
        g.drawString(font, gridLabel, gpx, gpy + 3, C_TEXT_DIM, false);
        if (gridHov) {

            pendingDeferredDraws.add(() -> g.renderTooltip(font,
                    Component.literal("§7Click to cycle canvas snap grid size"), mx, my));
        }

        if (isDevMode) {
            String sgLabel = subgraphMode ? "§b⊛ Subgraph: " + subgraphNodes.size() : "§8⊛ Subgraph";
            int sgw = font.width(net.minecraft.util.StringUtil.stripColor(sgLabel));
            int sgx = gpx - sgw - 18, sgy = 3;
            boolean sgHov = mx >= sgx - 3 && mx < sgx + sgw + 5 && my >= sgy && my < sgy + 13;
            g.fill(sgx - 3, sgy, sgx + sgw + 5, sgy + 13,
                    subgraphMode ? 0x4444CCFF : (sgHov ? 0x44FFFFFF : 0x22FFFFFF));
            g.drawString(font, sgLabel, sgx, sgy + 3, C_TEXT_DIM, false);
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
        g.drawString(font, (hov ? "§f" : "§7") + name, 21, 7, hov ? C_TEXT : C_TEXT_DIM, false);
    }

    private SidebarPanel.Colors sidebarColors() {
        return new SidebarPanel.Colors(C_BORDER, C_BORDER_LIT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_PANEL_DARK,
                C_SEL_TAB, C_PROG_FILL, C_PROG_ACT);
    }

    private void renderSidebarPanel(GuiGraphics g, int mx, int my) {
        sidebarPanel.renderPanel(g, font, mx, my, width, height, sidebarColors(), isDevMode, selectedChapter,
                this::friendly, progressLookup(), attentionLookup(), pendingDeferredDraws::add, buildChapterList());
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
                viewOffY);

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
        depLineRenderer.render(g, animTick, hoveredNodeId, this::getState, C_LINE_ACTIVE, C_LINE_DONE, C_LINE_ALMOST,
                C_LINE_LOCKED);

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
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("node visuals");
        dbgFull3DIconCount = 0;
        dbgCustomIconCount = 0;
        dbgPickedTextureIconCount = 0;
        dbgFluidIconCount = 0;
        dbgGlyphIconCount = 0;
        dbgShapeCounts.clear();
        int visibleNodeCount = 0;

        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            visibleNodeCount++;
            int[] pos = entry.getValue();
            renderNodeShape(g, node, pos[0], pos[1], scaledNodeSize(node), btn.isMouseOver(mx, my),
                    node == selectedNode);
        }
        int shapeQuadCount = NodeShapeRenderer.flushFillQueue(g);

        boolean blockPanelOpen = (validationPanel.isOpen() || statsPanel.isOpen()) && isDevMode;
        int bpW = Math.min(480, cr - cl - 20);
        int bpX = cl + (cr - cl - bpW) / 2;
        int bpY = HEADER_H + 10;
        int bpH = height - bpY - 10;
        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            int nsz = scaledNodeSize(node);
            if (blockPanelOpen && pos[0] + nsz > bpX && pos[0] < bpX + bpW && pos[1] + nsz > bpY &&
                    pos[1] < bpY + bpH)
                continue;
            renderNodeDetails(g, node, pos[0], pos[1], nsz, btn.isMouseOver(mx, my), node == selectedNode);
        }

        int badgeQuadCount = NodeShapeRenderer.flushFillQueue(g);
        FrameProfiler.setCounter("shapeFillQuadsQueued", shapeQuadCount + badgeQuadCount);
        FrameProfiler.setCounter("visibleNodes", visibleNodeCount);
        FrameProfiler.setCounter("full3DIcons", dbgFull3DIconCount);
        FrameProfiler.setCounter("customIcons", dbgCustomIconCount);
        FrameProfiler.setCounter("pickedTexIcons", dbgPickedTextureIconCount);
        FrameProfiler.setCounter("fluidIcons", dbgFluidIconCount);
        FrameProfiler.setCounter("glyphIcons", dbgGlyphIconCount);
        for (Map.Entry<String, Integer> e : dbgShapeCounts.entrySet()) {
            FrameProfiler.setCounter("shape:" + e.getKey(), e.getValue());
        }
        FrameProfiler.end("node visuals");

        g.flush();

        FrameProfiler.begin("dev overlays");

        if (isDevMode && !multiSelection.isEmpty()) {
            long dashPhase = (System.currentTimeMillis() / 80) % 6;
            for (ResourceLocation id : multiSelection) {
                int[] pos = nodeScreenPos.get(id);
                if (pos == null) continue;
                QuestNode selNode = QuestTreeRegistry.getQuest(id);
                int selSz = selNode != null ? scaledNodeSize(selNode) : sz;
                int x1 = pos[0] - 2, y1 = pos[1] - 2, x2 = pos[0] + selSz + 2, y2 = pos[1] + selSz + 2;
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

        if (isDevMode) {
            float pulse = animPulse(0.65f, 0.35f, 400.0);
            int alpha = (int) (pulse * 255) << 24;
            int errCol = alpha | 0x00FF2222;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                if (validationPanel.issuesFor(node).isEmpty()) continue;
                int[] pos = entry.getValue();
                int nsz = scaledNodeSize(node);
                int x1 = pos[0] - 3, y1 = pos[1] - 3, x2 = pos[0] + nsz + 3, y2 = pos[1] + nsz + 3;
                g.fill(x1, y1, x2, y1 + 2, errCol);
                g.fill(x1, y2 - 2, x2, y2, errCol);
                g.fill(x1, y1, x1 + 2, y2, errCol);
                g.fill(x2 - 2, y1, x2, y2, errCol);
            }
        }

        if (subgraphMode && selectedNode != null && !subgraphNodes.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 150f);
            g.flush();
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                int[] pos = entry.getValue();
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                int nsz = node != null ? scaledNodeSize(node) : sz;
                if (subgraphNodes.contains(entry.getKey())) {

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

        for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            NodeHitbox btn = nodeButtons.get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            QuestState st = getState(node);
            int nodeSz = scaledNodeSize(node);

            if (st == QuestState.UNLOCKED && nodeSz >= 20) {
                int badgeX = pos[0] + nodeSz - 2;
                int badgeY = pos[1] - 1;
                g.fill(badgeX, badgeY, badgeX + font.width("NEW") + 4, badgeY + 8, 0xFF1144BB);
                g.drawString(font, "NEW", badgeX + 2, badgeY + 1, 0xFFAADDFF, false);
            }
            if (st == QuestState.COMPLETED && nodeSz >= 12 && !node.getRewards().isEmpty()) {
                PlayerQuestData pd = testMode ? testModeData : playerData;
                if (pd != null) {
                    int badgeX = pos[0] - 4;
                    int badgeY = pos[1] - 4;
                    if (!pd.hasClaimedRewards(node.getId())) {
                        float pulse = animPulse(0.7f, 0.3f, 600.0);
                        int bAlpha = (int) (pulse * 255) << 24;
                        g.fill(badgeX, badgeY, badgeX + 9, badgeY + 9, bAlpha | 0x00BB6600);
                        g.drawString(font, "!", badgeX + 2, badgeY + 1, bAlpha | 0x00FFD700, false);
                    } else {

                        g.fill(badgeX, badgeY, badgeX + 9, badgeY + 9, 0xFF0A2210);
                        g.drawString(font, "✔", badgeX + 2, badgeY + 1, 0xFF55CC77, false);
                    }
                }
            }

            if (zoom >= 0.55f) {
                int baseAlpha = zoom >= 0.75f ? 0xFF : (int) ((zoom - 0.55f) / 0.20f * 0xFF);
                if (!searchQuery.isEmpty() && !matchesSearch(node)) baseAlpha = baseAlpha * 30 / 100;
                int lc = st == QuestState.COMPLETED ? C_TEXT_DONE :
                        st == QuestState.ACTIVE ? C_TEXT_ACT : st == QuestState.LOCKED ? C_TEXT_FAINT : C_TEXT_DIM;
                if (baseAlpha < 0xFF) lc = (lc & 0x00FFFFFF) | (baseAlpha << 24);
                ChroniclesUIKit.drawScaledCenteredString(g, font, shortLabel(node), pos[0] + nodeSz / 2f,
                        pos[1] + nodeSz + 4, lc, QuestChroniclesSettings.get().getTextScaleMultiplier());
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

        if (!isSidebarNarrow()) {

            g.enableScissor(0, 0, sidebarVisualW(), height);
            renderSidebarNewChapterButton(g, mx, my);
            renderSidebarGear(g, mx, my);
            g.disableScissor();
        }

        if (tutorialOverlay.isVisible(this)) tutorialOverlay.render(this, g, mx, my, cl, cr);

        if (!renderingAsBackdrop && draggedNode == null && !ctxOpen && !(minimapOpen && isInMinimap(mx, my))) {
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

                if (tipNode != null) pendingDeferredDraws.add(() -> renderNodeTooltip(g, tipNode, mx, my));
            }
        }

        if (!renderingAsBackdrop && nodeCtxMenu.isVisible(this)) nodeCtxMenu.render(this, g, mx, my, cl, cr);
        if (!renderingAsBackdrop && pictureCtxMenu.isVisible(this)) pictureCtxMenu.render(this, g, mx, my, cl, cr);

        if (!unlockPathHighlight.isEmpty()) {
            float blink = animPulse(0.7f, 0.3f, 400.0);
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
        if (sidebarPanel.contextMenuOpen()) {
            sidebarPanel.renderContextMenu(g, font, (int) mx, (int) my, width, height, sidebarColors());
        }

        for (TogglePanel p : List.of(validationPanel, statsPanel)) {
            if (p.isVisible(this)) pendingDeferredDraws.add(() -> p.render(this, g, mx, my, cl, cr));
        }

        if (isDevMode && multiSelection.size() >= 2) {
            renderBulkOpsPanel(g, mx, my, cl, cr);
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
        return new ToolbarPanel.Colors(C_PANEL_DARK, C_BORDER, C_TEXT, C_TEXT_DIM);
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

    private void renderBulkOpsPanel(GuiGraphics g, int mx, int my, int cl, int cr) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        int n = multiSelection.size();
        int bx = cl + 4, by = HEADER_H + 4;
        int bw = 360, bh = BULK_PANEL_H;
        g.fill(bx, by, bx + bw, by + bh, 0xFF131319);
        g.fill(bx, by, bx + bw, by + 1, C_BORDER_LIT);
        g.fill(bx, by, bx + 1, by + bh, C_BORDER_LIT);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, C_BORDER_LIT);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, C_BORDER_LIT);
        g.fill(bx, by, bx + 2, by + bh, 0xFF00DDFF);

        g.drawString(font, "§b" + n + " selected", bx + 6, by + 4, 0xFF00DDFF);
        g.drawString(font, "§8Ctrl+click to toggle  ·  Esc to clear", bx + 6, by + 14, C_TEXT_FAINT);

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
        int actX = startX + glyphs.length * (slotW + 2) + 8;
        boolean catHov = mx >= actX && mx < actX + 58 && my >= slotY && my < slotY + 12;
        if (catHov || bulkMoveCatOpen) g.fill(actX, slotY, actX + 58, slotY + 12, 0xFF222233);
        g.drawString(font, "§7Move cat ▸", actX, slotY + 2, (catHov || bulkMoveCatOpen) ? 0xFFCCCCFF : C_TEXT_DIM);
        int delX = actX + 62;
        boolean delHov = mx >= delX && mx < delX + 44 && my >= slotY && my < slotY + 12;
        if (delHov) g.fill(delX, slotY, delX + 44, slotY + 12, 0xFF221212);
        g.drawString(font, "§cDel all", delX, slotY + 2, delHov ? 0xFFFF5555 : C_CTX_DANGER);

        String[] sizeLabels = { "Tiny", "Small", "Normal", "Large", "Huge" };
        int sizeSlotW = 50, sizeStartX = bx + 6, sizeSlotY = slotY + BULK_PANEL_ROW_H;
        for (int i = 0; i < sizeLabels.length; i++) {
            int sx = sizeStartX + i * (sizeSlotW + 2);
            boolean hov = mx >= sx && mx < sx + sizeSlotW && my >= sizeSlotY && my < sizeSlotY + 12;
            if (hov) g.fill(sx, sizeSlotY, sx + sizeSlotW, sizeSlotY + 12, 0xFF222233);
            g.drawString(font, "§7" + sizeLabels[i], sx + 3, sizeSlotY + 2, hov ? 0xFFFFFFFF : 0xFF888899);
        }

        if (bulkMoveCatOpen) {
            List<String> moveCats = buildChapterList();
            moveCats.remove("ALL");
            int subX = actX, subY = sizeSlotY + BULK_PANEL_ROW_H, subRH = 11, subW = 90;
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

    private ResourceLocation resolveShapeTexture(QuestNode node) {
        String tex = node.getShapeTexture();
        if (tex == null || tex.isEmpty()) return null;
        try {
            return CustomTextureCache.resolve(new ResourceLocation(tex));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderNodeShape(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                 boolean hovered, boolean selected) {
        QuestNode linkTargetNode = resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = getState(displaySource);

        boolean showActiveAccent = st == QuestState.ACTIVE && hovered;
        int fill = switch (st) {
            case COMPLETED -> C_NODE_DONE;
            case ACTIVE -> showActiveAccent ? C_NODE_ACTIVE : C_NODE_UNLOCKED;
            case LOCKED -> C_NODE_LOCKED;
            default -> C_NODE_UNLOCKED;
        };
        int border = switch (st) {
            case COMPLETED -> C_NBORD_DONE;
            case ACTIVE -> showActiveAccent ? C_NBORD_ACTIVE : C_NBORD_UNLOCKED;
            case LOCKED -> isDevMode ? C_NBORD_DEV : C_NBORD_LOCKED;
            default -> C_NBORD_UNLOCKED;
        };
        if (selected) border = C_NBORD_SEL;
        if (hovered) fill = blendColor(fill, 0xFFFFFFFF, 0.08f);

        boolean roomForEffects = sz >= 14;

        String shape = node.getShapeType() != null ? node.getShapeType().toUpperCase() : "SQUARE";
        ResourceLocation shapeTex = "CUSTOM".equals(shape) ? resolveShapeTexture(node) : null;

        FrameProfiler.begin("node:effects");

        if (selected) {
            int hlColor = (border & 0x00FFFFFF) | 0x44000000;
            int hx = x - 2, hy = y - 2, hsz = sz + 4;
            switch (shape) {
                case "CIRCLE" -> NodeShapeRenderer.fillCircle(g, hx, hy, hsz, hlColor);
                case "DIAMOND" -> NodeShapeRenderer.fillDiamond(g, hx, hy, hsz, hlColor);
                case "HEXAGON" -> NodeShapeRenderer.fillHexagon(g, hx, hy, hsz, hlColor);
                case "TRIANGLE" -> NodeShapeRenderer.fillTriangle(g, hx, hy, hsz, hlColor);
                case "STAR" -> NodeShapeRenderer.fillStar(g, hx, hy, hsz, hlColor);
                case "PENTAGON" -> NodeShapeRenderer.fillPentagon(g, hx, hy, hsz, hlColor);
                case "SHIELD" -> NodeShapeRenderer.fillShield(g, hx, hy, hsz, hlColor);
                case "CROSS" -> NodeShapeRenderer.fillCross(g, hx, hy, hsz, hlColor);
                default -> g.fill(hx, hy, hx + hsz, hy + hsz, hlColor);
            }
        }

        FrameProfiler.end("node:effects");

        FrameProfiler.begin("node:shape");
        dbgShapeCounts.merge(shape, 1, Integer::sum);

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

        int thickness = nodeBorderThickness(sz);

        int fx = x + thickness, fy = y + thickness;
        int fsz = Math.max(1, sz - 2 * thickness);

        boolean hasBackground = false;
        String backgroundType = node.getBackgroundType();
        if (backgroundType != null && !backgroundType.isEmpty()) {
            net.phoenixvine.chronicles.client.render.IQuestBackground background = net.phoenixvine.chronicles.registry.QuestBackgroundRegistry
                    .get(backgroundType);
            if (background != null) {
                background.render(g, node, fx, fy, fsz, System.currentTimeMillis());
                hasBackground = true;
            }
        }

        switch (shape) {
            case "CIRCLE" -> {
                if (!hasBackground) NodeShapeRenderer.fillCircle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCircle(g, x, y, sz, border, thickness);
            }
            case "DIAMOND" -> {
                if (!hasBackground) NodeShapeRenderer.fillDiamond(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineDiamond(g, x, y, sz, border, thickness);
            }
            case "HEXAGON" -> {
                if (!hasBackground) NodeShapeRenderer.fillHexagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineHexagon(g, x, y, sz, border, thickness);
            }
            case "TRIANGLE" -> {
                if (!hasBackground) NodeShapeRenderer.fillTriangle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineTriangle(g, x, y, sz, border, thickness);
            }
            case "STAR" -> {
                if (!hasBackground) NodeShapeRenderer.fillStar(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineStar(g, x, y, sz, border, thickness);
            }
            case "PENTAGON" -> {
                if (!hasBackground) NodeShapeRenderer.fillPentagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlinePentagon(g, x, y, sz, border, thickness);
            }
            case "SHIELD" -> {
                if (!hasBackground) NodeShapeRenderer.fillShield(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineShield(g, x, y, sz, border, thickness);
            }
            case "CROSS" -> {
                if (!hasBackground) NodeShapeRenderer.fillCross(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCross(g, x, y, sz, border, thickness);
            }
            case "CUSTOM" -> {
                if (shapeTex != null) {

                    int pad = Math.max(1, thickness);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x - pad, y - pad, sz + pad * 2, sz + pad * 2,
                            border);
                    if (!hasBackground) NodeShapeRenderer.blitCustomShape(g, shapeTex, x, y, sz, sz, fill);
                } else {

                    if (!hasBackground) NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                    NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
                }
            }
            default -> {
                if (!hasBackground) NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
            }
        }
        FrameProfiler.end("node:shape");
    }

    private static void withNoIconMipBleed(GuiGraphics g, Runnable render) {
        g.flush();
        int texId = net.minecraft.client.Minecraft.getInstance().getTextureManager()
                .getTexture(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).getId();
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId);
        com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        try {
            render.run();
        } finally {
            g.flush();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                    org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR);
        }
    }

    private void renderNodeDetails(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                   boolean hovered, boolean selected) {
        QuestNode linkTargetNode = resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = getState(displaySource);

        FrameProfiler.begin("node:overlays");

        if (node.getVisibility() == QuestNode.Visibility.DISABLED) {
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xBB0B0B0F);
            g.drawCenteredString(font, "§8✕", x + sz / 2, y + sz / 2 - 4, 0xFF444444);
        }

        if (isDevMode && node.isFlagDisabled()) {
            g.fill(x - 2, y - 2, x + sz + 2, y - 1, 0xBB7722BB);
            g.fill(x - 2, y + sz + 1, x + sz + 2, y + sz + 2, 0xBB7722BB);
            g.fill(x - 2, y - 1, x - 1, y + sz + 1, 0xBB7722BB);
            g.fill(x + sz + 1, y - 1, x + sz + 2, y + sz + 1, 0xBB7722BB);
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xCC0B0B0F);
            g.drawCenteredString(font, "§5⚑", x + sz / 2, y + sz / 2 - 4, 0xFFAA44CC);
        }

        if (!searchQuery.isEmpty() && !matchesSearch(node)) {
            g.fill(x - 1, y - 1, x + sz + 1, y + sz + 1, 0xCC0B0B0F);
        }

        if (st == QuestState.LOCKED && !isDevMode) {
            NodeShapeRenderer.queueFillRect(g, x + 1, y + 1, x + sz - 1, y + sz - 1, 0x440B0B0F);

            int w = sz - 2;
            for (int d = -sz; d < sz; d += 6) {
                int lxStart = Math.max(0, -d);
                int lxEnd = Math.min(w, w - d);
                if (lxEnd <= lxStart) continue;
                float sx = x + 1 + lxStart, sy = y + 1 + lxStart + d;
                float ex = x + 1 + lxEnd - 1, ey = y + 1 + lxEnd - 1 + d;
                NodeShapeRenderer.queueThinLine(g, sx, sy, ex, ey, 0.5f, 0x160B0B0F);
            }
        }
        FrameProfiler.end("node:overlays");

        FrameProfiler.begin("node:progress");

        List<QuestTask> tasks = node.getTasks();
        if (QuestChroniclesSettings.get().isShowProgressArc() && !tasks.isEmpty() && sz >= 14) {
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

        if (st == QuestState.UNLOCKED && sz >= 20) {
            float readyPulse = animPulse(0.65f, 0.35f, 700.0);
            int dotAlpha = (int) (readyPulse * 0xFF) & 0xFF;
            int dotColor = (dotAlpha << 24) | 0x004488FF;
            g.fill(x + sz - 6, y + 1, x + sz - 1, y + 6, dotColor);
        }
        FrameProfiler.end("node:progress");

        FrameProfiler.begin("node:icon");
        FrameProfiler.begin("node:icon:lookup");

        String questPath = displaySource.getId().getPath();
        ResourceLocation customIcon = QuestIconCache.get(questPath);
        ResourceLocation pickedTexture = null;
        if (customIcon == null && !displaySource.getIconTexture().isEmpty()) {
            try {
                pickedTexture = new ResourceLocation(displaySource.getIconTexture());
            } catch (Exception ignored) {}
        }
        net.minecraft.world.level.material.Fluid pickedFluid = null;
        if (customIcon == null && pickedTexture == null && !displaySource.getIconFluid().isEmpty()) {
            try {
                net.minecraft.world.level.material.Fluid f = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                        .getValue(new ResourceLocation(displaySource.getIconFluid()));
                if (f != null && f != net.minecraft.world.level.material.Fluids.EMPTY) pickedFluid = f;
            } catch (Exception ignored) {}
        }
        FrameProfiler.end("node:icon:lookup");

        int borderThickness = nodeBorderThickness(sz);
        int fillSz = Math.max(1, sz - borderThickness * 2);
        if (customIcon != null && sz >= 8) {
            int[] dims = QuestIconCache.getDimensions(questPath);
            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            g.blit(customIcon, x + off, y + off, 0, 0, iconSz, iconSz, dims[0], dims[1]);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            dbgCustomIconCount++;
        } else if (pickedTexture != null && sz >= 8) {
            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            g.blit(pickedTexture, x + off, y + off, 0, 0, iconSz, iconSz, iconSz, iconSz);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            dbgPickedTextureIconCount++;
        } else if (pickedFluid != null && sz >= 8) {

            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            int col = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(pickedFluid)
                    .getTintColor() | 0xFF000000;
            g.fill(x + off, y + off, x + off + iconSz, y + off + iconSz, col);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            dbgFluidIconCount++;
        } else {
            Item icon = displaySource.getIconItem();
            QuestTask iconTask = null;
            if (icon == null) {
                iconTask = fallbackTaskIconTask(displaySource);
                icon = iconTask != null ?
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(iconTask.getDisplayItemId()) :
                        null;
            } else {
                iconTask = matchingIconTask(displaySource, icon);
            }

            if (icon != null && icon != Items.AIR && sz >= 6) {

                float scale = fillSz / 16f * 0.75f;
                float cx = x + sz / 2f, cy = y + sz / 2f;
                ItemStack iconStack = iconTask != null ? nbtAwareIconStack(iconTask, icon) : cachedIconStack(icon);

                FrameProfiler.begin("node:icon3d");
                g.pose().pushPose();
                try {
                    g.pose().translate(cx, cy, 100f);

                    g.pose().scale(scale, scale, 1f);

                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    withNoIconMipBleed(g, () -> g.renderItem(iconStack, -8, -8));
                } catch (Exception ignored) {} finally {
                    g.pose().popPose();
                }
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

        if (node.isLinkStub() && sz >= 14) {

            float badgeScale = Math.max(0.6f, Math.min(2.2f, sz / 24f));
            int badgeW = Math.round(8 * badgeScale);
            int badgeH = Math.round(7 * badgeScale);
            if (linkTargetNode != null) {
                g.fill(x, y, x + badgeW, y + badgeH, 0xEE101820);
            } else {
                g.fill(x, y, x + badgeW, y + badgeH, 0xEE330808);
            }

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(badgeScale, badgeScale, 1f);
            if (linkTargetNode != null) {
                g.drawString(font, "§b🔗", 1, 0, 0xFF66CCFF, false);
            } else {
                g.drawString(font, "§c!", 2, 0, 0xFFFF6666, false);
            }
            g.pose().popPose();
        }

        if (isDevMode && sz >= 14 && !node.isLinkStub()) {
            List<String> issues = validationPanel.issuesFor(node);
            if (!issues.isEmpty()) {
                int bx = x, by = y + sz - 7;
                g.fill(bx, by, bx + 8, by + 7, 0xEE331800);
                g.drawString(font, "§6!", bx + 2, by, 0xFFFFAA00, false);
            }
        }

        if (sz >= 14 && collapsedSubtreeRoots.contains(node.getId())) {
            int bx = x + sz - 8, by = y + sz - 7;
            g.fill(bx, by, bx + 8, by + 7, 0xEE10182A);
            g.drawString(font, "§b▶", bx, by, 0xFF66CCFF, false);
        }
        FrameProfiler.end("node:badges");
    }

    private void renderQuestGroup(GuiGraphics g, QuestGroup grp, int cl, int cr) {
        int sx = (int) (grp.getX() * posZoom()) + viewOffX + cl;
        int sy = (int) (grp.getY() * posZoom()) + viewOffY + HEADER_H;
        int sw = (int) (grp.getWidth() * posZoom());
        int sh = (int) (grp.getHeight() * posZoom());

        if (sx + sw < cl || sx > cr || sy + sh < HEADER_H || sy > height) return;

        g.fill(sx, sy, sx + sw, sy + sh, grp.getColor());

        int bc = grp.getBorderColor();
        g.fill(sx, sy, sx + sw, sy + 1, bc);
        g.fill(sx, sy + sh - 1, sx + sw, sy + sh, bc);
        g.fill(sx, sy, sx + 1, sy + sh, bc);
        g.fill(sx + sw - 1, sy, sx + sw, sy + sh, bc);

        g.fill(sx + 1, sy + 1, sx + sw - 1, sy + GROUP_LABEL_BAR_H, (grp.getBorderColor() & 0x00FFFFFF) | 0x55000000);

        if (sw > 20) {
            String label = grp.getLabel();
            int maxLabelW = sw - 8;
            if (font.width(label.replaceAll("§.", "")) > maxLabelW) {
                label = font.plainSubstrByWidth(label, maxLabelW - 6) + "…";
            }
            g.drawString(font, "§f" + label, sx + 4, sy + 2, 0xFFFFFFFF);
        }

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

    private void renderGroupIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getValue(new ResourceLocation(icon.id));
                    if (item == null || item == Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    try {
                        g.pose().translate(x + size / 2f, y + size / 2f, 100f);
                        g.pose().scale(scale, scale, 1f);
                        ItemStack groupIconStack = new ItemStack(item);
                        withNoIconMipBleed(g, () -> g.renderItem(groupIconStack, -8, -8));
                    } finally {

                        g.pose().popPose();
                    }
                }
                case FLUID -> {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getValue(new ResourceLocation(icon.id));
                    ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                    g.fill(x, y, x + size, y + 1, 0xFF444455);
                    g.fill(x, y + size - 1, x + size, y + size, 0xFF444455);
                    g.fill(x, y, x + 1, y + size, 0xFF444455);
                    g.fill(x + size - 1, y, x + size, y + size, 0xFF444455);
                }
                case TEXTURE -> g.blit(new ResourceLocation(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {

        }
    }

    @Nullable
    private QuestGroup groupAtLabelBar(double mx, double my, int cl) {
        for (QuestGroup grp : QuestGroupManager.forChapter(selectedChapter)) {
            int sx = (int) (grp.getX() * posZoom()) + viewOffX + cl;
            int sy = (int) (grp.getY() * posZoom()) + viewOffY + HEADER_H;
            int sw = (int) (grp.getWidth() * posZoom());
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + GROUP_LABEL_BAR_H) {
                return grp;
            }
        }
        return null;
    }

    private BackgroundPictureConfig.Picture pictureAt(double mx, double my, int cl) {
        List<BackgroundPictureConfig.Picture> pics = BackgroundPictureConfig.get(selectedChapter);
        BackgroundPictureConfig.Picture hit = null;
        for (BackgroundPictureConfig.Picture pic : pics) {
            int[] rect = BackgroundPictureRenderer.screenRect(pic, cl, HEADER_H, posZoom(), viewOffX, viewOffY);
            if (mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3]) hit = pic;
        }
        return hit;
    }

    private void drawProgressArc(GuiGraphics g, int cx, int cy, int r,
                                 float fraction, int fillColor, int bgColor) {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float s = (float) (1.0 / gs);

        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        int pcx = (int) Math.round(cx * gs);
        int pcy = (int) Math.round(cy * gs);

        for (int dr = 0; dr <= 1; dr++) {
            int pr = (int) Math.round((r - dr * 0.5) * gs);
            if (pr <= 0) continue;
            int steps = Math.max(64, pr * 5);
            for (int i = 0; i < steps; i++) {
                double angle = (i * 2.0 * Math.PI / steps) - Math.PI / 2.0;
                int px = (int) Math.round(pcx + pr * Math.cos(angle));
                int py = (int) Math.round(pcy + pr * Math.sin(angle));
                int col = (i < fraction * steps) ? fillColor : bgColor;
                if ((col >>> 24) == 0) continue;
                NodeShapeRenderer.queueFillRect(g, px, py, px + 1, py + 1, col);
            }
        }

        g.pose().popPose();
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

    public void renderBackdrop(GuiGraphics g) {
        g.fill(0, 0, sidebarW(), height, C_PANEL_DARK);
        g.fill(sidebarW(), 0, width, height, C_BG);
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.fill(sidebarW() - 1, 0, sidebarW(), height, C_BORDER);
        CanvasBackgroundRenderer.drawBackground(g, sidebarW(), HEADER_H, width, height, selectedChapter, zoom,
                viewOffX, viewOffY);
    }

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

    private boolean catMatches(QuestNode n) {
        QuestNode.Visibility vis = n.getVisibility();

        if (n.isFlagDisabled()) return isDevMode && QuestChroniclesSettings.get().isShowFlagDisabledQuests();

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

        sb.append(n.getTitle().getString().toLowerCase()).append(' ');
        sb.append(n.getId().getPath().replace('_', ' ').toLowerCase()).append(' ');
        sb.append(n.getId().toString().toLowerCase()).append(' ');
        if (!n.getDescription().getString().isEmpty())
            sb.append(n.getDescription().getString().toLowerCase()).append(' ');
        if (n.getSubtitle() != null && !n.getSubtitle().isEmpty()) sb.append(n.getSubtitle().toLowerCase()).append(' ');
        sb.append(n.getChapter().toLowerCase()).append(' ');

        for (QuestTask task : n.getTasks()) {
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

        for (QuestReward reward : n.getRewards()) {
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
    }

    @Override
    public int colorBorder() {
        return C_BORDER;
    }

    @Override
    public int colorSelectAccent() {
        return C_SEL_ACCENT;
    }

    @Override
    public int colorText() {
        return C_TEXT;
    }

    @Override
    public int colorTextDim() {
        return C_TEXT_DIM;
    }

    @Override
    public int colorTextFaint() {
        return C_TEXT_FAINT;
    }

    @Override
    public int colorNodeBorderDone() {
        return C_NBORD_DONE;
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

    private int unclaimedRewardCount() {
        if (playerData == null) return 0;
        int count = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled()) continue;
            if (playerData.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (playerData.hasClaimedRewards(node.getId())) continue;
            if (node.isRewardChoice()) continue;
            if (node.getRewards().isEmpty()) continue;
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

    private String shortLabel(QuestNode node) {
        String t = node.getTitle().getString();

        int maxW = (int) (scaledNodeSize(node) * 1.6f) + 40;
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    @Override
    public String shortName(QuestNode node, int maxW) {
        String t = node.getTitle().getString();
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    private void fitToCanvas() {
        if (applyFitView()) return;
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
        if (minX == Integer.MAX_VALUE) return true;
        int canvasW = cr - cl - 20, canvasH = height - HEADER_H - 20;
        int contentW = maxX - minX, contentH = maxY - minY;
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.min(
                (float) canvasW / contentW,
                (float) canvasH / contentH)));
        viewOffX = (int) (canvasW / 2f - (minX + contentW / 2f) * posZoom()) + 10;
        viewOffY = (int) (canvasH / 2f - (minY + contentH / 2f) * posZoom()) + 10;
        return false;
    }

    private void renderNodeTooltip(GuiGraphics g, QuestNode node, int mx, int my) {
        QuestNode linkTarget = resolveLinkTarget(node);
        node = linkTarget != null ? linkTarget : node;

        QuestState st = getState(node);
        String title = node.getTitle().getString();
        String sub = node.getSubtitle() != null && !node.getSubtitle().isBlank() ? node.getSubtitle() : null;

        boolean showFull = hasShiftDown();

        List<String> lines = new ArrayList<>();
        lines.add("§f" + title);
        if (sub != null) lines.add("§8" + sub);
        if (node.isOptional()) lines.add("§d★ Optional quest");

        if (showFull) {

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

            String stateLine = switch (st) {
                case COMPLETED -> "§a✔ Complete";
                case ACTIVE -> "§e▶ In progress — " + taskDone + "/" + taskTotal;
                case UNLOCKED -> "§b○ Ready to start";
                case LOCKED -> "§8✕ Locked";
            };

            List<String> prereqLines = new ArrayList<>();
            if (!node.getPrerequisites().isEmpty()) {
                for (QuestNode req : node.getPrerequisites()) {
                    QuestState rs = getState(req);
                    String mark = rs == QuestState.COMPLETED ? "§a✔" : "§8○";
                    prereqLines.add("  " + mark + " §8" + req.getTitle().getString());
                }
            }

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

            if (isDevMode) {
                List<String> issues = validationPanel.issuesFor(node);
                if (!issues.isEmpty()) {
                    lines.add("§8─────────────");
                    lines.add("§6⚠ Validation issues:");
                    issues.forEach(i -> lines.add("  §e• §7" + i));
                }
            }
        } else {
            lines.add("§8Hold Shift for details");
        }

        float ts = QuestChroniclesSettings.get().getTextScaleMultiplier();
        int lineH = Math.round((font.lineHeight + 2) * ts);
        int padH = 6, padW = 8;
        int tipW = Math.round(lines.stream().mapToInt(font::width).max().orElse(60) * ts) + padW * 2;
        int tipH = lines.size() * lineH + padH * 2;

        int tx = mx + 10, ty = my + 12;
        if (tx + tipW > width - 4) tx = mx - tipW - 4;
        if (ty + tipH > height - 4) ty = my - tipH - 4;

        tx = Math.max(4, tx);
        ty = Math.max(4, ty);

        if (minimapOpen) {
            int[] mb = minimapBounds(width);
            boolean overlapsX = tx < mb[2] && tx + tipW > mb[0];
            boolean overlapsY = ty < mb[3] && ty + tipH > mb[1];
            if (overlapsX && overlapsY) {
                int leftOfMap = mb[0] - tipW - 4;
                if (leftOfMap >= 4) tx = leftOfMap;
                else ty = Math.max(4, mb[1] - tipH - 4);
            }
        }

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        g.fill(tx, ty, tx + tipW, ty + tipH, 0xFF0D0D14);
        g.fill(tx, ty, tx + tipW, ty + 1, C_BORDER_LIT);
        g.fill(tx, ty + tipH - 1, tx + tipW, ty + tipH, C_BORDER_LIT);
        g.fill(tx, ty, tx + 1, ty + tipH, C_BORDER_LIT);
        g.fill(tx + tipW - 1, ty, tx + tipW, ty + tipH, C_BORDER_LIT);
        g.fill(tx, ty, tx + 1, ty + tipH, 0xFF884499);

        int lx = tx + padW, ly = ty + padH;
        for (String line : lines) {
            ChroniclesUIKit.drawScaledString(g, font, line, lx, ly, 0xFFCCCCDD, ts);
            ly += lineH;
        }
        g.pose().popPose();
    }

    private int[] computeChapterProgress(String cat) {
        int done = 0, total = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled()) continue;

            total++;

            if (getDisplayState(n) == QuestState.COMPLETED) done++;
        }
        return new int[] { done, total };
    }

    private boolean computeChapterHasAttention(String cat) {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled()) continue;
            if (getDisplayState(n) == QuestState.ACTIVE) return true;
        }
        return false;
    }

    public void setFeedback(String msg, Object... args) {
        feedbackMsg = msg.formatted(args);
        feedbackTimer = 100;
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

    private void saveNodeToDisk(QuestNode node) {
        QuestFileSaver.updateNodePosition(node);
    }

    private void saveNodeShapeToDisk(QuestNode node, String shape) {
        QuestFileSaver.updateNodeShape(node, shape);
    }

    private void saveNodeChapterToDisk(QuestNode node, String cat) {
        QuestFileSaver.updateNodeChapter(node, cat);
    }

    void saveNodePrereqsToDisk(QuestNode node) {
        QuestFileSaver.updateNodePrerequisites(node);
    }

    private void saveNodeShapeTextureToDisk(QuestNode node) {
        QuestFileSaver.updateNodeShapeTexture(node);
    }

    private void deleteQuestFiles(QuestNode node) {
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

    private int[] minimapBounds(int cr) {
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
        }
    }

    private enum GridDisplayMode {

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

    private static final class NodeHitbox {

        int x, y, w, h;
        boolean visible = true;
        boolean active = true;

        int getX() {
            return x;
        }

        void setX(int nx) {
            x = nx;
        }

        int getY() {
            return y;
        }

        void setY(int ny) {
            y = ny;
        }

        boolean isMouseOver(double mx, double my) {
            return visible && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    record CtxItem(String label, String color, boolean isSep, boolean isDanger, Runnable action) {

        static CtxItem sep() {
            return new CtxItem("", "", true, false, () -> {});
        }
    }
}
