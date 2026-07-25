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
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ItemRequirementTask;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import java.util.function.Function;

public class ChronicleOverviewScreen extends Screen {

    private final SidebarPanel sidebarPanel = new SidebarPanel();

    private int sidebarW() {
        return sidebarPanel.width();
    }

    private boolean isSidebarNarrow() {
        return sidebarPanel.isNarrow();
    }

    private int sidebarVisualW() {
        return sidebarPanel.visualWidth();
    }

    private void updateSidebarHoverPeek(int mx, int my) {
        sidebarPanel.updateHoverPeek(mx, my, this::panCanvas);
    }

    private static final int HEADER_H = 38;
    private static final int TOOLBAR_Y = 22;
    private static final int TOOLBAR_H = 16;
    private static final int NODE_SIZE = 32;

    private int C_BG = 0xFF0B0B0F;
    private int C_PANEL_DARK = 0xFF0E0E12;
    private int C_HEADER = 0xFF09090D;
    private int C_BORDER = 0xFF252530;
    private int C_BORDER_LIT = 0xFF353548;
    private int C_SEL_TAB = 0xFF1A1A26;
    private int C_SEL_ACCENT = 0xFF00AA55;

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

    private int C_LINE_LOCKED = 0x38FFFFFF;
    private int C_LINE_DONE = 0x9900CC66;
    private int C_LINE_ACTIVE = 0x88FFAA00;

    private static final int C_LINE_ALMOST = 0xAAFFEE33;
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
    private int C_PROG_FILL = 0xFF00AA55;
    private static final int C_PROG_ACT = 0xFFBB8800;

    private String selectedChapter = "";

    private String viewChapterTracker = null;
    private QuestNode selectedNode = null;

    private ResourceLocation lastHoveredNodeId = null;

    private int dbgFull3DIconCount = 0;
    private int dbgCustomIconCount = 0;
    private int dbgPickedTextureIconCount = 0;
    private int dbgFluidIconCount = 0;
    private int dbgGlyphIconCount = 0;

    private final Map<String, Integer> dbgShapeCounts = new HashMap<>();
    private boolean isDevMode = false;
    private String feedbackMsg = "";
    private int feedbackTimer = 0;

    private int viewOffX = 0, viewOffY = 0;

    private int pendingPanDX = 0, pendingPanDY = 0;
    private float zoom = 1.0f;
    private static final float ZOOM_MIN = 0.12f;
    private static final float ZOOM_MAX = 2.5f;
    private static final float ZOOM_STEP = 0.12f;

    private boolean isPanning = false;

    private float posZoom() {
        return zoom;
    }

    private boolean hideCompleted = false;

    private long lastCanvasClickTime = 0;
    private int lastCanvasClickX = 0;
    private int lastCanvasClickY = 0;

    private QuestNode draggedNode = null;
    private int dragGrabX = 0, dragGrabY = 0;

    private int dragOrigX = 0, dragOrigY = 0;

    private boolean pickupPlaceActive = false;

    private QuestNode lastMovedNode = null;
    private int lastMoveOrigX = 0, lastMoveOrigY = 0;
    private long lastMoveTimeMs = 0;
    private static final long POST_MOVE_UNDO_WINDOW_MS = 1000;

    @Nullable
    private QuestGroup draggedGroup = null;
    private int groupDragGrabX = 0, groupDragGrabY = 0;

    @Nullable
    private BackgroundPictureConfig.Picture draggedPicture = null;
    private int pictureDragGrabX = 0, pictureDragGrabY = 0;

    private boolean picCtxOpen = false;
    private long picCtxOpenTimeMs = 0;
    private int picCtxX, picCtxY;
    @Nullable
    private BackgroundPictureConfig.Picture picCtxTarget = null;
    private boolean picCtxResizeOpen = false;
    private boolean picCtxMoveCatOpen = false;
    private boolean picCtxOpacityOpen = false;
    private boolean picCtxTintOpen = false;
    private static final int[] PIC_RESIZE_PRESETS = { 32, 64, 128, 256, 512, 1024 };
    private static final int[] PIC_OPACITY_PRESETS = { 100, 75, 50, 25, 10 };
    private static final String[] PIC_TINT_NAMES = { "None (white)", "Warm sepia", "Cool blue", "Faded gray",
            "Ghostly" };
    private static final int[] PIC_TINT_PRESETS = { 0xFFFFFF, 0xE0C088, 0x88AAE0, 0xAAAAAA, 0x99CCFF };

    @Nullable
    private BackgroundPictureConfig.Picture pictureEditMode = null;
    private static final float PIC_EDIT_MIN_SIZE = 4f, PIC_EDIT_MAX_SIZE = 4096f;

    @Nullable
    private QuestNode nodeSizeEditMode = null;

    private static final int CTX_ROW = 16;
    private static final int CTX_SEP = 5;
    private static final int CTX_W = 128;
    private boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    private int ctxX, ctxY;

    private int ctxRawX, ctxRawY;
    private QuestNode ctxNode = null;
    private boolean ctxMoveCatOpen = false;
    private int ctxMoveCatScroll = 0;
    private static final int CTX_MOVE_CAT_MAX_ROWS = 10;
    @Nullable
    private QuestGroup ctxGroup = null;

    private boolean renderingAsBackdrop = false;

    private String stateFilter = "ALL";
    private EditBox searchBox = null;
    private String searchQuery = "";
    private String[] searchWords = new String[0];

    final Map<ResourceLocation, String> searchCache = new HashMap<>();

    private Object phantasiaPreview = null;

    private final Set<ResourceLocation> multiSelection = new LinkedHashSet<>();

    private static final Set<ResourceLocation> collapsedSubtreeRoots = new HashSet<>();
    private final Set<ResourceLocation> hiddenByCollapse = new HashSet<>();

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

    private final UndoRedoManager undoRedo = new UndoRedoManager(this::setFeedback);

    private final TutorialOverlayRenderer tutorialOverlay = new TutorialOverlayRenderer();
    private final MinimapRenderer minimap = new MinimapRenderer();

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

    private final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    private final Map<ResourceLocation, NodeHitbox> nodeButtons = new LinkedHashMap<>();
    private final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();

    private final Map<String, int[]> progressCache = new HashMap<>();

    private final Map<String, Boolean> attentionCache = new HashMap<>();

    private final Map<ResourceLocation, List<String>> validationCache = new HashMap<>();

    private List<String> stubChapterCache = null;

    private List<String> chapterListCache = null;

    private boolean bulkMoveCatOpen = false;

    private QuestNode linkDragSource = null;
    private int linkDragX, linkDragY;

    private int gridSnap = 8;
    private static final int[] GRID_SNAP_CYCLE = { 1, 4, 8, 16, 32 };

    private boolean gridSnapEnabled = true;

    private boolean dragForceSnap = false;

    private final Set<ResourceLocation> unlockPathHighlight = new HashSet<>();

    private boolean validationOpen = false;

    private long openTimeMs = -1;
    private static final long OPEN_FADE_MS = 120;

    private ResourceLocation tooltipHoverNodeId = null;
    private long tooltipHoverStartMs = 0;
    private static final long TOOLTIP_DELAY_MS = 0;

    private final java.util.List<Runnable> pendingDeferredDraws = new java.util.ArrayList<>();

    private boolean testMode = false;
    private PlayerQuestData testModeData = new PlayerQuestData();
    private boolean subgraphMode = false;
    private final java.util.Set<ResourceLocation> subgraphNodes = new java.util.HashSet<>();

    private String questClipboard = null;

    private final ToolbarPanel toolbarPanel = new ToolbarPanel();

    private boolean minimapOpen = false;

    private boolean mmDragging = false;

    private boolean statsOpen = false;

    private PlayerQuestData playerData = null;

    public ChronicleOverviewScreen() {
        super(Component.literal("Chronicles"));

        selectedChapter = QuestChroniclesSettings.get().getLastChapter();

        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        hideCompleted = s.isHideCompletedByDefault();
        gridSnap = s.getDefaultGridSnap();
    }

    QuestState getState(QuestNode node) {
        if (testMode) return testModeData.getQuestState(node.getId(), QuestState.LOCKED);
        if (playerData == null) return QuestState.LOCKED;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED);
    }

    private QuestNode resolveLinkTarget(QuestNode node) {
        return node.isLinkStub() ? QuestTreeRegistry.getQuest(node.getLinkTarget()) : null;
    }

    private Item fallbackTaskIcon(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            ResourceLocation id = task.getDisplayItemId();
            if (id == null) continue;
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return item;
        }
        return null;
    }

    private QuestState getDisplayState(QuestNode node) {
        QuestNode target = resolveLinkTarget(node);
        return getState(target != null ? target : node);
    }

    private boolean isTaskDone(QuestTask task) {
        if (minecraft == null || minecraft.player == null) return false;
        return task.isCompletedFor(minecraft.player);
    }

    static Path chaptersFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("categories.txt");
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

    void deleteChapter(String chapter) {
        String upper = chapter.toUpperCase(Locale.ROOT);

        List<QuestNode> questsInChapter = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (upper.equalsIgnoreCase(n.getChapter())) questsInChapter.add(n);
        }
        Map<QuestNode, String> savedSnbt = new LinkedHashMap<>();
        for (QuestNode n : questsInChapter) {
            String raw = QuestFileSaver.readRawSnbt(n);
            if (raw != null && !raw.isBlank()) savedSnbt.put(n, raw);
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
            setFeedback("Undo: chapter restored (" + questCount + " quest(s))");
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
        setFeedback("Chapter deleted: " + chapter + (questCount > 0 ? " (" + questCount + " quests)" : "") +
                "  (Ctrl+Z to undo)");
    }

    List<String> buildChapterList() {
        if (chapterListCache != null) return new ArrayList<>(chapterListCache);

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getChapter();
            if (c != null) seen.add(c);
        }

        if (stubChapterCache == null) {
            stubChapterCache = new ArrayList<>();
            try {
                Path f = chaptersFile();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String cat = line.trim().toUpperCase();
                        if (!cat.isEmpty()) stubChapterCache.add(cat);
                    }
                }
            } catch (IOException ignored) {}
        }
        for (String cat : stubChapterCache) seen.add(cat);

        for (CategoryDefinition cd : net.phoenixvine.chronicles.registry.CategoryRegistry.getCategories()) {
            for (String chap : cd.chapters()) {
                if (chap != null && !chap.isBlank()) seen.add(chap.toUpperCase());
            }
        }

        chapterListCache = new ArrayList<>(seen);
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
        ChroniclesTheme t = ChroniclesTheme.current();

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

    static void invalidateNodeCachesUpChain(Screen from, QuestNode node) {
        Screen s = from;
        for (int i = 0; i < 8 && s != null; i++) {
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

    void invalidateNodeCaches(QuestNode node) {
        if (node == null) {
            validationCache.clear();
            searchCache.clear();
            progressCache.clear();
            attentionCache.clear();
            return;
        }
        validationCache.remove(node.getId());
        searchCache.remove(node.getId());
        if (node.getChapter() != null) {
            progressCache.remove(node.getChapter());
            attentionCache.remove(node.getChapter());
        }
    }

    void rebuild() {
        clearWidgets();
        nodeScreenPos.clear();
        nodeButtons.clear();
        searchCache.clear();
        progressCache.clear();
        attentionCache.clear();
        validationCache.clear();
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
            restoreViewForChapter(selectedChapter);
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

    private void restoreViewForChapter(String cat) {
        if (!applyFitView()) {
            zoom = 1.0f;
            viewOffX = 0;
            viewOffY = 0;
        }
    }

    private void placeNodeRecursive(QuestNode node, int cl, int cr) {
        if (nodeButtons.containsKey(node.getId())) return;
        if (hiddenByCollapse.contains(node.getId())) return;

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
        boolean hiddenFromPlayers = node.getVisibility() == QuestNode.Visibility.HIDDEN ||
                node.getVisibility() == QuestNode.Visibility.MYSTERY;
        if (state == QuestState.LOCKED && hiddenFromPlayers && !isDevMode) hb.active = false;
        nodeButtons.put(node.getId(), hb);
        nodeScreenPos.put(node.getId(), new int[] { sx, sy });

        for (QuestNode child : node.getChildren()) {
            if (catMatches(child)) placeNodeRecursive(child, cl, cr);
        }
    }

    private static final int MIN_NODE_PX = 12;

    private static final float MIN_NODE_FLOOR_FRACTION = 0.375f;

    private int scaledNodeSize(QuestNode node) {
        int pixelSize = node.getNodePixelSize();
        int floor = Math.max(4, Math.round(pixelSize * MIN_NODE_FLOOR_FRACTION));
        return Math.max(floor, (int) (pixelSize * posZoom()));
    }

    private int scaledNodeSize() {
        return Math.max(MIN_NODE_PX, (int) (NODE_SIZE * posZoom()));
    }

    private static int nodeBorderThickness(int sz) {
        return Math.max(1, Math.min(4, sz / 28));
    }

    void onNodeClicked(QuestNode node) {
        onNodeClicked(node, false);
    }

    void onNodeClicked(QuestNode node, boolean openFullscreen) {
        ctxOpen = false;
        ctxMoveCatOpen = false;

        QuestNode linkTarget = resolveLinkTarget(node);
        QuestNode effective = linkTarget != null ? linkTarget : node;

        QuestState st = getState(effective);

        selectedNode = effective;
        if (subgraphMode) rebuildSubgraph();

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

        QuestNode.Visibility effVis = effective.getVisibility();
        boolean hiddenFromPlayers = effVis == QuestNode.Visibility.HIDDEN || effVis == QuestNode.Visibility.MYSTERY;
        if (st == QuestState.LOCKED && hiddenFromPlayers && !isDevMode) return;
        if (minecraft != null) {

            Path mdPath = QuestFileSaver.getQuestMarkdownPath(effective);

            net.phoenixvine.chronicles.codec.QuestContentLoader.syncActiveLocaleFromClient();
            Path resolvedMdPath = net.phoenixvine.chronicles.codec.QuestContentLoader
                    .resolveLocaleFile(mdPath, effective.getId().getPath());
            FullQuestData fd = loadMarkdownContent(resolvedMdPath);
            minecraft.setScreen(new QuestTasksScreen(this, effective, fd, playerData, openFullscreen));
        }
    }

    private void autoArrangeChapter() {
        final int X_STRIDE = 80;
        final int Y_STRIDE = 56;
        final int ORIGIN_X = 30;
        final int ORIGIN_Y = 30;

        List<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> selectedChapter.equalsIgnoreCase(n.getChapter()))
                .collect(java.util.stream.Collectors.toList());
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
        setFeedback("Auto-arranged " + nodes.size() + " quest(s)");
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
        if (node.getChapter() != null && !node.getChapter().equals(selectedChapter)) {
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
            int parentSz = scaledNodeSize(parent);
            int px = pPos[0] + parentSz / 2, py = pPos[1] + parentSz / 2;
            QuestState ps = getState(parent);

            for (QuestNode child : parent.getChildren()) {
                if (!catMatches(child)) continue;
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

                    QuestState childState = getState(child);
                    col = ps == QuestState.COMPLETED ?
                            (childState == QuestState.COMPLETED ? C_LINE_DONE : C_LINE_ALMOST) :
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

                edges.add(new int[] {
                        px, py, cx2, cy2, col, style, shapeOrd, visOrd, speedOrd, arrowOrd, parentSz, childSz });
                edgeNodes.add(new ResourceLocation[] { parent.getId(), child.getId() });
            }

            for (QuestNode prereq : parent.getPrerequisites()) {
                if (prereq.getChildren().contains(parent)) continue;
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

                    QuestState destState = getState(parent);
                    col = prereqState == QuestState.COMPLETED ?
                            (destState == QuestState.COMPLETED ? C_LINE_DONE : C_LINE_ALMOST) :
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

                edges.add(new int[] {
                        prx, pry, px, py, col, style, shapeOrd, visOrd, speedOrd, arrowOrd, prereqSz, parentSz });
                edgeNodes.add(new ResourceLocation[] { prereq.getId(), parent.getId() });
            }
        }

        depLineRenderer.rebuild(edges, edgeNodes, posZoom(), QuestChroniclesSettings.get());
    }

    private void softRebuild() {
        Map<String, int[]> savedProgress = new HashMap<>(progressCache);
        Map<String, Boolean> savedAttention = new HashMap<>(attentionCache);
        Map<ResourceLocation, List<String>> savedValidation = new HashMap<>(validationCache);
        List<String> savedStubs = stubChapterCache;
        List<String> savedCats = chapterListCache;
        rebuild();
        progressCache.putAll(savedProgress);
        attentionCache.putAll(savedAttention);
        validationCache.putAll(savedValidation);
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
            draggedNode.setCustomPosition(dragOrigX, dragOrigY);
            saveNodeToDisk(draggedNode);
            draggedNode = null;
            pickupPlaceActive = false;
            dragForceSnap = false;
            rebuild();
            setFeedback("Move cancelled");
            return true;
        }

        if (key == 256 && lastMovedNode != null &&
                System.currentTimeMillis() - lastMoveTimeMs < POST_MOVE_UNDO_WINDOW_MS) {
            lastMovedNode.setCustomPosition(lastMoveOrigX, lastMoveOrigY);
            saveNodeToDisk(lastMovedNode);
            lastMovedNode = null;
            rebuild();
            setFeedback("Move undone");
            return true;
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
                setFeedback(hovered != null ?
                        (nowPinned ? "§dPinned: " + hovered.getTitle().getString() :
                                "§7Unpinned: " + hovered.getTitle().getString()) :
                        (nowPinned ? "§dPinned" : "§7Unpinned"));
            }
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_LINE_STYLE.matches(key, scan)) {
            QuestChroniclesSettings s = QuestChroniclesSettings.get();
            boolean nowSpline = s.isSplineLines();
            s.setLineStyle(
                    nowSpline ? QuestChroniclesSettings.LineStyle.STRAIGHT : QuestChroniclesSettings.LineStyle.SPLINE);
            s.save();
            setFeedback("Line style: " + (nowSpline ? "Straight" : "Spline"));
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
            if (minecraft != null) minecraft.setScreen(new DevWikiScreen(this));
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_VALIDATION.matches(key, scan) && !ctrl && isDevMode) {
            validationOpen = !validationOpen;
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
            statsOpen = !statsOpen;
            if (statsOpen) validationOpen = false;
            return true;
        }

        return super.keyPressed(key, scan, mods);
    }

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
            setFeedback("§aPasted → " + newPath);

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
            setFeedback("§cPaste error: " + e.getMessage());
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
        setFeedback("§aChained " + ordered.size() + " quests (" + wired + " new link" + (wired == 1 ? "" : "s") + ")");
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
        setFeedback("§aFanned from '" + root.getId().getPath() + "' to " + wired + " quest" + (wired == 1 ? "" : "s"));
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
                setFeedback("§aImported " + r.imported() + " quests" +
                        (r.skipped() > 0 ? " §c(" + r.skipped() + " skipped)" : "") +
                        (r.warnings().isEmpty() ? "" : " §8— " + r.warnings().size() + " warnings"));
                if (r.imported() > 0) {
                    QuestFileLoader.reloadAllQuestsFromDisk();

                    ChroniclesLangPack.reload();
                    rebuild();
                }
            }
        } catch (Exception e) {
            setFeedback("§cFTB import error: " + e.getMessage());
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
            setFeedback("Duplicated → " + newPath + "  (Ctrl+Z to undo)");
        } catch (IOException e) {
            e.printStackTrace();
            setFeedback("Duplicate failed: " + e.getMessage());
        }
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
            setFeedback("Size: " + nodeSizeEditMode.getNodePixelSize() +
                    "px  (scroll to resize - shift/ctrl for finer steps, drag to move, right-click/Esc to finish)");
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

        if (btn == 0 && handleTutorialClick(mx, my)) return true;

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
                    minecraft.setScreen(new DevWikiScreen(this));
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
            if (hitRow != null && !hitRow.isFolder() && minecraft != null) {
                minecraft.setScreen(new ChapterThemeScreen(this, hitRow.id()));
                return true;
            }
        }

        if (btn == 0 && isDevMode && multiSelection.size() >= 2) {
            int bx = cl + 4, by = HEADER_H + 4;
            int bh = 38;
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
                                setFeedback("Shape → CUSTOM for " + targets.size() + " quests");
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
                        setFeedback("Shape → " + newShape + " for " + multiSelection.size() + " quests");
                        rebuild();
                        return true;
                    }
                }
                int actX = startX + shapeIds.length * (slotW + 2) + 8;

                if ((int) mx >= actX && (int) mx < actX + 58 && (int) my >= slotY && (int) my < slotY + 12) {
                    bulkMoveCatOpen = !bulkMoveCatOpen;
                    return true;
                }

                if (bulkMoveCatOpen) {
                    List<String> moveCats = buildChapterList();
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
                                    sn.setChapter(newCat);

                                    saveNodeChapterToDisk(sn, newCat);
                                }
                            }
                            bulkMoveCatOpen = false;
                            setFeedback("Moved " + multiSelection.size() + " quests to " + friendly(newCat));
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
                    setFeedback("Deleted " + count + " quests");
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

        if (picCtxOpen && btn == 0) {
            handlePictureCtxClick((int) mx, (int) my);
            return true;
        }
        if (picCtxOpen) {
            closePictureCtx();
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
            saveNodeToDisk(draggedNode);
            lastMovedNode = draggedNode;
            lastMoveOrigX = dragOrigX;
            lastMoveOrigY = dragOrigY;
            lastMoveTimeMs = System.currentTimeMillis();
            draggedNode = null;
            pickupPlaceActive = false;
            dragForceSnap = false;
            softRebuild();
            setFeedback("Placed");
            return true;
        }

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
                        undoRedo.push(() -> {
                            capturedNode.setCustomPosition(preX, preY);

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
                        undoRedo.push(() -> {
                            capturedNode.setCustomPosition(preX, preY);
                            saveNodeToDisk(capturedNode);
                            rebuild();
                        });
                        dragGrabX = (int) mx - e.getValue().getX();
                        dragGrabY = (int) my - e.getValue().getY();
                        selectedNode = draggedNode;
                        dragForceSnap = true;
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
                    openPictureCtx((int) mx, (int) my, hitPic);
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
    public void mouseMoved(double mx, double my) {
        if (pickupPlaceActive && draggedNode != null) {
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
                nodeSizeEditMode.setCustomPosition(
                        nodeSizeEditMode.getCustomX() + (int) (dx / posZoom()),
                        nodeSizeEditMode.getCustomY() + (int) (dy / posZoom()));
                refreshNodeScreenPos(nodeSizeEditMode);
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
                    if (target != null && target != src && !target.getPrerequisites().contains(src)) {
                        target.addPrerequisite(src);
                        target.setPrereqLink(src.getId(), true);
                        saveNodePrereqsToDisk(target);
                        setFeedback(
                                "§aLinked: " + src.getId().getPath() + " → prereq of " + target.getId().getPath());
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
            if (draggedNode != null && !pickupPlaceActive) {
                saveNodeToDisk(draggedNode);
                lastMovedNode = draggedNode;
                lastMoveOrigX = dragOrigX;
                lastMoveOrigY = dragOrigY;
                lastMoveTimeMs = System.currentTimeMillis();
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
        if (btn == 2 && draggedNode != null) {
            saveNodeToDisk(draggedNode);
            lastMovedNode = draggedNode;
            lastMoveOrigX = dragOrigX;
            lastMoveOrigY = dragOrigY;
            lastMoveTimeMs = System.currentTimeMillis();
            draggedNode = null;
            dragForceSnap = false;
            softRebuild();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    private record CtxItem(String label, String color, boolean isSep, boolean isDanger, Runnable action) {

        static CtxItem sep() {
            return new CtxItem("", "", true, false, () -> {});
        }
    }

    private List<CtxItem> buildCtxItems() {
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
            items.add(new CtxItem((statsOpen ? "§b∑ Hide stats" : "∑ Show stats"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        statsOpen = !statsOpen;
                        if (statsOpen) validationOpen = false;
                    }));

            items.add(new CtxItem((gridSnapEnabled ? "⊞ Grid Snap: ON" : "§7⊞ Grid Snap: OFF"), "§7", false, false,
                    () -> {
                        ctxOpen = false;
                        gridSnapEnabled = !gridSnapEnabled;
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
            if (ctxLinkTarget != null) {
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
                                setFeedback("Force-completed: " + target.getTitle().getString() + "  (Ctrl+Z to undo)");
                            }
                        }));
                items.add(new CtxItem("Reset Progress", "§7", false, false,
                        () -> {
                            final QuestNode target = ctxNode;
                            ctxOpen = false;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {

                                mc.player.connection.sendCommand("chronicles reset " + target.getId().getPath());
                                setFeedback("Progress reset: " + target.getTitle().getString());
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

    private int menuHeight(List<CtxItem> items) {
        int h = 4;
        if (ctxNode != null) h += CTX_ROW;
        for (CtxItem i : items) h += i.isSep ? CTX_SEP : CTX_ROW;
        return h;
    }

    private static final int PIC_CTX_H = 4 + CTX_ROW * 7 + CTX_SEP;

    private void openPictureCtx(int x, int y, BackgroundPictureConfig.Picture pic) {
        picCtxOpen = true;
        picCtxOpenTimeMs = System.currentTimeMillis();
        picCtxResizeOpen = false;
        picCtxMoveCatOpen = false;
        picCtxOpacityOpen = false;
        picCtxTintOpen = false;
        picCtxTarget = pic;
        picCtxX = x;
        picCtxY = y;
        if (picCtxY + PIC_CTX_H > height - 4) picCtxY = height - PIC_CTX_H - 4;
        if (picCtxX + CTX_W > width - 4) picCtxX = width - CTX_W - 4;

        picCtxX = Math.max(4, picCtxX);
        picCtxY = Math.max(4, picCtxY);
    }

    private void closePictureCtx() {
        picCtxOpen = false;
        picCtxResizeOpen = false;
        picCtxMoveCatOpen = false;
        picCtxOpacityOpen = false;
        picCtxTintOpen = false;
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
        g.flush();

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
        iy = drawPicCtxRow(g, x, iy, "Opacity  ▸", "§7", false, mx, my);
        iy = drawPicCtxRow(g, x, iy, "Tint  ▸", "§7", false, mx, my);
        iy = drawPicCtxRow(g, x, iy, "Move to Chapter  ▸", "§7", false, mx, my);
        g.fill(x + 6, iy + 2, x + CTX_W - 6, iy + 3, C_CTX_SEP);
        iy += CTX_SEP;
        drawPicCtxRow(g, x, iy, "Delete picture", "§c", true, mx, my);

        if (picCtxResizeOpen) renderPicResizeSubmenu(g, x, y + 2 + CTX_ROW, mx, my);
        if (picCtxOpacityOpen) renderPicOpacitySubmenu(g, x, y + 2 + CTX_ROW * 3, mx, my);
        if (picCtxTintOpen) renderPicTintSubmenu(g, x, y + 2 + CTX_ROW * 4, mx, my);
        if (picCtxMoveCatOpen) renderPicMoveCatSubmenu(g, x, y + 2 + CTX_ROW * 5, mx, my);

        g.pose().popPose();
    }

    private void renderPicResizeSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        ResourceLocation nativeLoc = picCtxTexture();
        int[] nativeSz = nativeLoc != null ? CustomTextureCache.nativeSize(nativeLoc) : null;
        int rows = PIC_RESIZE_PRESETS.length + (nativeSz != null ? 1 : 0);
        int subH = rows * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, C_CTX_BORDER);
        int sy = subY + 2;
        if (nativeSz != null) {
            drawPicCtxRow(g, subX, sy, "§b" + nativeSz[0] + "x" + nativeSz[1] + " §8(native)", "", false, mx, my);
            sy += CTX_ROW;
        }
        for (int size : PIC_RESIZE_PRESETS) {
            boolean isCurrent = picCtxTarget != null && Math.round(picCtxTarget.w) == size;
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawPicCtxRow(g, subX, sy, mark + size + "px", "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    @Nullable
    private ResourceLocation picCtxTexture() {
        if (picCtxTarget == null || picCtxTarget.texture == null || picCtxTarget.texture.isBlank()) return null;
        try {
            return new ResourceLocation(picCtxTarget.texture);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderPicOpacitySubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        int subH = PIC_OPACITY_PRESETS.length * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, C_CTX_BORDER);
        int sy = subY + 2;
        for (int pct : PIC_OPACITY_PRESETS) {
            boolean isCurrent = picCtxTarget != null && Math.round(picCtxTarget.opacity * 100) == pct;
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawPicCtxRow(g, subX, sy, mark + pct + "%", "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    private void renderPicTintSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        int subH = PIC_TINT_PRESETS.length * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, C_CTX_BORDER);
        int sy = subY + 2;
        for (int i = 0; i < PIC_TINT_PRESETS.length; i++) {
            boolean isCurrent = picCtxTarget != null && picCtxTarget.color == PIC_TINT_PRESETS[i];
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawPicCtxRow(g, subX, sy, mark + PIC_TINT_NAMES[i], "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    private void renderPicMoveCatSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        List<String> cats = buildChapterList();
        cats.remove("ALL");
        cats.remove(selectedChapter);
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

    private boolean handlePictureCtxClick(int mx, int my) {
        if (picCtxTarget == null) return false;
        BackgroundPictureConfig.Picture pic = picCtxTarget;
        int x = picCtxX, y = picCtxY;

        if (picCtxResizeOpen) {
            ResourceLocation nativeLoc = picCtxTexture();
            int[] nativeSz = nativeLoc != null ? CustomTextureCache.nativeSize(nativeLoc) : null;
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW;
            int sy = subY;
            if (nativeSz != null) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    applyPicResize(pic, nativeSz[0], nativeSz[1]);
                    return true;
                }
                sy += CTX_ROW;
            }
            for (int i = 0; i < PIC_RESIZE_PRESETS.length; i++) {
                int ry = sy + i * CTX_ROW;
                if (mx >= subX && mx <= subX + CTX_W && my >= ry && my <= ry + CTX_ROW) {
                    int size = PIC_RESIZE_PRESETS[i];
                    applyPicResize(pic, size, size);
                    return true;
                }
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + PIC_CTX_H) {
                closePictureCtx();
                return true;
            }
        }
        if (picCtxOpacityOpen) {
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 3;
            int sy = subY;
            for (int pct : PIC_OPACITY_PRESETS) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final float oldOpacity = pic.opacity;
                    undoRedo.push(() -> {
                        pic.opacity = oldOpacity;
                        BackgroundPictureConfig.save();
                        setFeedback("Undo: picture opacity reverted");
                    });
                    pic.opacity = pct / 100f;
                    BackgroundPictureConfig.save();
                    setFeedback("Picture opacity set to " + pct + "%  (Ctrl+Z to undo)");
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
        if (picCtxTintOpen) {
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 4;
            int sy = subY;
            for (int i = 0; i < PIC_TINT_PRESETS.length; i++) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final int oldColor = pic.color;
                    final int newColor = PIC_TINT_PRESETS[i];
                    undoRedo.push(() -> {
                        pic.color = oldColor;
                        BackgroundPictureConfig.save();
                        setFeedback("Undo: picture tint reverted");
                    });
                    pic.color = newColor;
                    BackgroundPictureConfig.save();
                    setFeedback("Picture tint set to " + PIC_TINT_NAMES[i] + "  (Ctrl+Z to undo)");
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
        if (picCtxMoveCatOpen) {
            List<String> cats = buildChapterList();
            cats.remove("ALL");
            cats.remove(selectedChapter);
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 5;
            int sy = subY + 2;
            for (String cat : cats) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final String oldCat = selectedChapter;
                    final String newCat = cat;
                    undoRedo.push(() -> {
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
        int rowY4 = rowY3 + CTX_ROW;
        int rowY5 = rowY4 + CTX_ROW;
        int rowY6 = rowY5 + CTX_ROW + CTX_SEP;

        if (mx < x || mx > x + CTX_W) {
            closePictureCtx();
            return true;
        }
        if (my >= rowY0 && my < rowY0 + CTX_ROW) {

            setFeedback("Shift-click and drag the picture");
            closePictureCtx();
            return true;
        }
        if (my >= rowY1 && my < rowY1 + CTX_ROW) {
            picCtxResizeOpen = !picCtxResizeOpen;
            picCtxOpacityOpen = false;
            picCtxTintOpen = false;
            picCtxMoveCatOpen = false;
            return true;
        }
        if (my >= rowY2 && my < rowY2 + CTX_ROW) {

            final BackgroundPictureConfig.Picture editedPic = pic;
            final float ux = pic.x, uy = pic.y, uw = pic.w, uh = pic.h;
            undoRedo.push(() -> {
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
            picCtxOpacityOpen = !picCtxOpacityOpen;
            picCtxResizeOpen = false;
            picCtxTintOpen = false;
            picCtxMoveCatOpen = false;
            return true;
        }
        if (my >= rowY4 && my < rowY4 + CTX_ROW) {
            picCtxTintOpen = !picCtxTintOpen;
            picCtxResizeOpen = false;
            picCtxOpacityOpen = false;
            picCtxMoveCatOpen = false;
            return true;
        }
        if (my >= rowY5 && my < rowY5 + CTX_ROW) {
            picCtxMoveCatOpen = !picCtxMoveCatOpen;
            picCtxResizeOpen = false;
            picCtxOpacityOpen = false;
            picCtxTintOpen = false;
            return true;
        }
        if (my >= rowY6 && my < rowY6 + CTX_ROW) {
            final BackgroundPictureConfig.Picture deleted = pic;
            final String cat = selectedChapter;
            undoRedo.push(() -> {
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

    private void applyPicResize(BackgroundPictureConfig.Picture pic, float w, float h) {
        final float oldW = pic.w, oldH = pic.h;
        undoRedo.push(() -> {
            pic.w = oldW;
            pic.h = oldH;
            BackgroundPictureConfig.save();
            setFeedback("Undo: picture resized");
        });
        pic.w = w;
        pic.h = h;
        BackgroundPictureConfig.save();
        setFeedback("Picture resized  (Ctrl+Z to undo)");
        closePictureCtx();
    }

    private int ctxMoveCatY(List<CtxItem> items) {
        int y = ctxY + 2;
        if (ctxNode != null) y += CTX_ROW;
        for (CtxItem item : items) {
            if (!item.isSep && item.label.contains("Move to Chapter")) return y;
            y += item.isSep ? CTX_SEP : CTX_ROW;
        }
        return y;
    }

    private int ctxMoveCatX(int catCount) {
        int subW = CTX_W + (catCount > CTX_MOVE_CAT_MAX_ROWS ? 6 : 0);
        int x = ctxX + CTX_W + 2;
        if (x + subW > width - 4) x = ctxX - subW - 2;
        return Math.max(4, x);
    }

    private int ctxMoveCatYClamped(List<CtxItem> items, int catCount) {
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

        if (draggedNode != null && dragForceSnap) renderSnapGridOverlay(g, cl, cr);

        renderSidebarPanel(g, mx, my);

        renderCanvasLayers(g, mx, my, cl, cr, animTick);

        FrameProfiler.setCounter("screenWidgets", this.renderables.size());
        FrameProfiler.begin("widgets (super.render)");
        if (!renderingAsBackdrop) super.render(g, mx, my, partial);
        FrameProfiler.end("widgets (super.render)");

        renderDepLines(g, mx, my, cl, cr, animTick);

        renderNodesAndDetails(g, mx, my, cl, cr, sz);
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

    private void updateDraggedNodeScreenPos(double mx, double my, int snap) {
        int cl = sidebarW();
        int logX = (int) ((mx - dragGrabX - cl - viewOffX) / posZoom());
        int logY = (int) ((my - dragGrabY - HEADER_H - viewOffY) / posZoom());
        logX = Math.round((float) logX / snap) * snap;
        logY = Math.round((float) logY / snap) * snap;

        int nx = (int) (logX * posZoom()) + cl + viewOffX;
        int ny = (int) (logY * posZoom()) + HEADER_H + viewOffY;

        NodeHitbox b = nodeButtons.get(draggedNode.getId());
        if (b != null) {
            b.setX(nx);
            b.setY(ny);
        }

        nodeScreenPos.put(draggedNode.getId(), new int[] { nx, ny });
        draggedNode.setCustomPosition(logX, logY);
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
        int color = 0x33FFFFFF;

        int firstX = cl + Math.floorMod(viewOffX, step);
        for (int x = firstX; x < cr; x += step) {
            g.fill(x, top, x + 1, bottom, color);
        }
        int firstY = top + Math.floorMod(viewOffY, step);
        for (int y = firstY; y < bottom; y += step) {
            g.fill(cl, y, cr, y + 1, color);
        }
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

        g.drawString(font, titlePrefix + chapterBreadcrumb(selectedChapter), cl + 8, 7, C_TEXT);
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
        depLineRenderer.render(g, animTick, hoveredNodeId, this::getState);

        if (linkDragSource != null) {
            int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
            if (srcPos != null) {
                int sz2 = scaledNodeSize();
                int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
                depLineRenderer.renderLinkDragPreview(g, sx, sy, linkDragX, linkDragY, animTick, posZoom());
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

        boolean blockPanelOpen = (validationOpen || statsOpen) && isDevMode;
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
                if (pd != null && !pd.hasClaimedRewards(node.getId())) {
                    float pulse = animPulse(0.7f, 0.3f, 600.0);
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

        if (!renderingAsBackdrop) renderTutorialOverlay(g, mx, my);

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

        if (!renderingAsBackdrop && ctxOpen && isDevMode) renderCtxMenu(g, mx, my);
        if (!renderingAsBackdrop && picCtxOpen && isDevMode) renderPictureCtxMenu(g, mx, my);

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

        if (validationOpen && isDevMode) pendingDeferredDraws.add(() -> renderValidationPanel(g, cl, cr));
        if (statsOpen && isDevMode) pendingDeferredDraws.add(() -> renderStatsPanel(g, cl, cr));

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
        return toolbarPanel.filterPillBounds(cl, TOOLBAR_Y, TOOLBAR_H, font);
    }

    private void renderToolbar(GuiGraphics g, int mx, int my, int cl, int cr) {
        toolbarPanel.render(g, font, mx, my, width, cl, cr, TOOLBAR_Y, TOOLBAR_H, toolbarColors(), stateFilter,
                hideCompleted, minimapOpen, isDevMode);
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
                setFeedback("Chapter '" + friendly(id) + "' created");
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
        int bw = 360, bh = 38;
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

        if (bulkMoveCatOpen) {
            List<String> moveCats = buildChapterList();
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

        boolean roomForEffects = sz >= 14;

        FrameProfiler.begin("node:effects");

        if (selected)
            g.fill(x - 2, y - 2, x + sz + 2, y + sz + 2, (border & 0x00FFFFFF) | 0x44000000);

        FrameProfiler.end("node:effects");

        FrameProfiler.begin("node:shape");
        String shape = node.getShapeType() != null ? node.getShapeType().toUpperCase() : "SQUARE";
        dbgShapeCounts.merge(shape, 1, Integer::sum);

        ResourceLocation shapeTex = "CUSTOM".equals(shape) ? resolveShapeTexture(node) : null;

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

        switch (shape) {
            case "CIRCLE" -> {
                NodeShapeRenderer.fillCircle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCircle(g, x, y, sz, border, thickness);
            }
            case "DIAMOND" -> {
                NodeShapeRenderer.fillDiamond(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineDiamond(g, x, y, sz, border, thickness);
            }
            case "HEXAGON" -> {
                NodeShapeRenderer.fillHexagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineHexagon(g, x, y, sz, border, thickness);
            }
            case "TRIANGLE" -> {
                NodeShapeRenderer.fillTriangle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineTriangle(g, x, y, sz, border, thickness);
            }
            case "STAR" -> {
                NodeShapeRenderer.fillStar(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineStar(g, x, y, sz, border, thickness);
            }
            case "PENTAGON" -> {
                NodeShapeRenderer.fillPentagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlinePentagon(g, x, y, sz, border, thickness);
            }
            case "SHIELD" -> {
                NodeShapeRenderer.fillShield(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineShield(g, x, y, sz, border, thickness);
            }
            case "CROSS" -> {
                NodeShapeRenderer.fillCross(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCross(g, x, y, sz, border, thickness);
            }
            case "CUSTOM" -> {
                if (shapeTex != null) {

                    int pad = Math.max(1, thickness);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x - pad, y - pad, sz + pad * 2, sz + pad * 2,
                            border);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x, y, sz, sz, fill);
                } else {

                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                    NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
                }
            }
            default -> {
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
            }
        }
        FrameProfiler.end("node:shape");
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
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0x440B0B0F);

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
            if (icon == null) icon = fallbackTaskIcon(displaySource);

            if (icon != null && icon != Items.AIR && sz >= 6) {

                float scale = fillSz / 16f * 0.75f;
                float cx = x + sz / 2f, cy = y + sz / 2f;

                FrameProfiler.begin("node:icon3d");
                g.pose().pushPose();
                try {
                    g.pose().translate(cx, cy, 100f);
                    g.pose().scale(scale, scale, 1f);

                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    g.renderItem(new ItemStack(icon), -8, -8);
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
            if (linkTargetNode != null) {
                g.fill(x, y, x + 8, y + 7, 0xEE101820);
                g.drawString(font, "§b🔗", x + 1, y, 0xFF66CCFF, false);
            } else {
                g.fill(x, y, x + 8, y + 7, 0xEE330808);
                g.drawString(font, "§c!", x + 2, y, 0xFFFF6666, false);
            }
        }

        if (isDevMode && sz >= 14 && !node.isLinkStub()) {
            List<String> issues = getValidationIssues(node);
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

    private static final int GROUP_LABEL_BAR_H = 11;

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
                        g.renderItem(new ItemStack(item), -8, -8);
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

    private void renderCtxMenu(GuiGraphics g, int mx, int my) {
        List<CtxItem> items = buildCtxItems();
        int menuH = menuHeight(items);
        int x = ctxX, y = ctxY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

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
            if (hov && item.label.contains("Move to Chapter")) moveCatRowHov = true;
            iy += CTX_ROW;
        }

        List<String> cats = buildChapterList();
        cats.remove("ALL");
        int subX = ctxMoveCatX(cats.size());
        int subY = ctxMoveCatYClamped(items, cats.size());
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
                String mark = cat.equals(ctxNode.getChapter()) ? "§a● " : "§8  ";
                g.drawString(font, mark + "§7" + friendly(cat), subX + 8, sy + 4, C_CTX_TEXT);
                sy += CTX_ROW;
            }
            g.disableScissor();

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

        if (n.isFlagDisabled()) return isDevMode;

        if (!isDevMode && vis == QuestNode.Visibility.HIDDEN) {
            if (getState(n) == QuestState.LOCKED) return false;
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
            if (!stateMatch) return false;
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
                    sb.append(rid.toString()).append(' ');
                }
            }
        }

        return sb.toString().toLowerCase();
    }

    private List<String> getValidationIssues(QuestNode node) {
        return validationCache.computeIfAbsent(node.getId(), id -> computeValidationIssues(node));
    }

    private List<String> computeValidationIssues(QuestNode node) {
        List<String> issues = new ArrayList<>();

        if (node.isLinkStub()) return issues;

        if (node.getTasks().isEmpty()) issues.add("No tasks defined");

        if (node.getTitle().getString().isBlank()) issues.add("Missing title");

        if (!QuestFileSaver.doesQuestFileExist(node)) issues.add("No editable file on disk (datapack quest)");

        for (QuestTask task : node.getTasks()) {
            if (task instanceof ItemRequirementTask irt) {
                if (irt.getItem() == null || irt.getItem() == net.minecraft.world.item.Items.AIR) {
                    issues.add("Item task has missing/AIR item");
                }
            }
        }

        for (QuestNode prereq : node.getPrerequisites()) {
            if (QuestTreeRegistry.getQuest(prereq.getId()) == null) {
                issues.add("Broken prerequisite: " + prereq.getId().getPath());
            }
        }
        return issues;
    }

    String friendly(String cat) {
        if (cat == null || cat.equals("ALL")) return "All Chapters";
        String resolved = ChapterConfig.getResolvedDisplayName(cat);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : cat.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
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
        int maxW = scaledNodeSize(node) + 28;
        return font.width(t) > maxW ? font.plainSubstrByWidth(t, maxW - 4) + "…" : t;
    }

    private String shortName(QuestNode node, int maxW) {
        String t = node.getTitle().getString();
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
        QuestNode linkTarget = resolveLinkTarget(node);
        if (linkTarget != null) {
            renderNodeTooltip(g, linkTarget, mx, my);
            return;
        }

        QuestState st = getState(node);
        String title = node.getTitle().getString();
        String sub = node.getSubtitle() != null && !node.getSubtitle().isBlank() ? node.getSubtitle() : null;

        boolean showFull = hasShiftDown();

        List<String> lines = new ArrayList<>();
        lines.add("§f" + title);
        if (sub != null) lines.add("§8" + sub);

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
                List<String> issues = getValidationIssues(node);
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

    void setFeedback(String msg) {
        feedbackMsg = msg;
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

                    if (desc.length() > 0) desc.append("\n\n");
                    desc.append(t);
                    pendingParagraphBreak = true;
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

    private void renderValidationPanel(GuiGraphics g, int cl, int cr) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        int panW = Math.min(400, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = HEADER_H + 10;
        int panH = height - panY - 10;

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
        g.disableScissor();
        if (!any) {
            g.drawString(font, "§aNo issues found!", panX + 6, panY + 20, 0xFF44CC88, false);
        }
        g.disableScissor();
        g.pose().popPose();
    }

    private int[] minimapBounds(int cr) {
        return minimap.bounds(cr, height);
    }

    private void renderMinimap(GuiGraphics g, int mx, int my, int cl, int cr) {
        minimap.render(g, font, cl, cr, width, height, HEADER_H, NODE_SIZE, posZoom(), viewOffX, viewOffY,
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

    private void renderStatsPanel(GuiGraphics g, int cl, int cr) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        RenderSystem.disableDepthTest();

        int panW = Math.min(480, cr - cl - 20);
        int panX = cl + (cr - cl - panW) / 2;
        int panY = HEADER_H + 10;
        int panH = height - panY - 10;

        g.enableScissor(panX, panY, panX + panW, panY + panH);
        g.fill(panX, panY, panX + panW, panY + panH, 0xFF0B0B14);
        int bc = 0xFF4488CC;
        g.fill(panX, panY, panX + panW, panY + 1, bc);
        g.fill(panX, panY, panX + 1, panY + panH, bc);
        g.fill(panX + panW - 1, panY, panX + panW, panY + panH, bc);
        g.fill(panX, panY + panH - 1, panX + panW, panY + panH, bc);
        g.drawString(font, "§bQuest Stats", panX + 6, panY + 4, 0xFF55AAEE, false);
        g.fill(panX + 4, panY + 14, panX + panW - 4, panY + 15, 0xFF222233);

        Collection<QuestNode> all = QuestTreeRegistry.getAllQuests().values();
        int total = all.size();
        int noTask = 0, noReward = 0, orphaned = 0;
        int totalTasks = 0, totalRewards = 0;
        int repeatable = 0, hiddenOrMystery = 0, disabled = 0, withCustomIcon = 0, linkStubs = 0;
        int validationIssueCount = 0;

        java.util.TreeMap<String, int[]> catCounts = new java.util.TreeMap<>();
        for (QuestNode n : all) {
            if (n.getTasks().isEmpty()) noTask++;
            if (n.getRewards().isEmpty()) noReward++;
            if (n.getPrerequisites().isEmpty() && n.getChildren().isEmpty()) orphaned++;
            totalTasks += n.getTasks().size();
            totalRewards += n.getRewards().size();
            if (n.getRepeatMode() != QuestNode.RepeatMode.NONE) repeatable++;
            if (n.getVisibility() == QuestNode.Visibility.HIDDEN || n.getVisibility() == QuestNode.Visibility.MYSTERY)
                hiddenOrMystery++;
            if (n.getVisibility() == QuestNode.Visibility.DISABLED) disabled++;
            if (n.isLinkStub()) linkStubs++;
            if (n.getIconItem() != null && n.getIconItem() != net.minecraft.world.item.Items.AIR) withCustomIcon++;
            if (!getValidationIssues(n).isEmpty()) validationIssueCount++;
            String cat = n.getChapter() != null ? n.getChapter() : "UNKNOWN";
            catCounts.computeIfAbsent(cat, k -> new int[1])[0]++;
        }
        int totalGroups = QuestGroupManager.getAll().size();

        int sy = panY + 18, lh = 10;
        int col1 = panX + 6, col2 = panX + panW / 2 + 10;

        g.drawString(font, "§fTotal quests:  §e" + total, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fTotal tasks:   §7" + totalTasks, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fNo tasks:      §c" + noTask, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fTotal rewards: §7" + totalRewards, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fNo rewards:    §8" + noReward, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fCategories:    §7" + catCounts.size(), col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fOrphaned:      §e" + orphaned, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fGroups:        §7" + totalGroups, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fRepeatable:    §b" + repeatable, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fHidden/Mystery:§7" + hiddenOrMystery, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fDisabled:      §7" + disabled, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, "§fLink stubs:    §7" + linkStubs, col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.drawString(font, "§fCustom icons:  §7" + withCustomIcon + "§8/" + total, col1, sy, 0xFFDDDDFF, false);
        g.drawString(font, validationIssueCount > 0 ? "§fValidation:    §c" + validationIssueCount + " issue(s)" :
                "§fValidation:    §a✔ clean", col2, sy, 0xFFDDDDFF, false);
        sy += lh;
        g.fill(panX + 4, sy, panX + panW - 4, sy + 1, 0xFF222233);
        sy += 5;

        g.drawString(font, "§8Chapter", col1, sy, 0xFF666677, false);
        g.drawString(font, "§8Quests", col2, sy, 0xFF666677, false);
        sy += lh;
        g.enableScissor(panX + 2, sy, panX + panW - 2, panY + panH - 4);
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(catCounts.entrySet());
        sorted.sort((a, b2) -> Integer.compare(b2.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, int[]> e : sorted) {
            if (sy + 9 > panY + panH - 4) break;
            int cnt = e.getValue()[0];

            int barMaxW = panW / 2 - 20;
            int barW = total > 0 ? (int) ((float) cnt / total * barMaxW) : 0;
            g.fill(col2 - 2, sy, col2 - 2 + barW, sy + 8, 0x334488CC);
            g.drawString(font, "§7" + friendly(e.getKey()), col1, sy, 0xFFAAAAAA, false);
            g.drawString(font, "§f" + cnt, col2, sy, 0xFFCCCCFF, false);
            sy += lh;
        }
        g.disableScissor();
        g.disableScissor();
        g.flush();
        RenderSystem.enableDepthTest();
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

    private int lastSeenProgressVersion = -1;

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

    private void renderTutorialOverlay(GuiGraphics g, int mx, int my) {
        tutorialOverlay.render(g, mx, my, font,
                new TutorialOverlayRenderer.Layout(width, height, sidebarW(), HEADER_H, TOOLBAR_Y, TOOLBAR_H),
                new TutorialOverlayRenderer.Colors(C_SEL_ACCENT, C_BORDER, C_TEXT_FAINT, C_TEXT_DIM, C_TEXT,
                        C_NBORD_DONE),
                nodeScreenPos, this::scaledNodeSize, this::scaledNodeSize, this::getState);
    }

    private boolean handleTutorialClick(double mx, double my) {
        return tutorialOverlay.handleClick(mx, my, this::getState);
    }
}
