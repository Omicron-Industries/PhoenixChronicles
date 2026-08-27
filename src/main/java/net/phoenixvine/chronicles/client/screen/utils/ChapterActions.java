package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.*;
import net.phoenixvine.chronicles.client.screen.widgets.SidebarPanel;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket;
import net.phoenixvine.chronicles.registry.CategoryRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChapterActions {

    private final ScreenContext ctx;
    private final ChapterActionsState state;
    private final GraphEditorState editorState;

    private String armedDeleteCategoryId = null;
    private String armedDeleteChapterId = null;

  public ChapterActions(ScreenContext ctx, ChapterActionsState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    void forceCompleteChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = state.questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            ctx.setFeedback("§7No quests in '%s'", ctx.friendly(chapter));
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        ctx.setFeedback("§aForce-completed %d quest(s) in %s", ids.size(), ctx.friendly(chapter));
    }

    void resetChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = state.questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            ctx.setFeedback("§7No quests in '%s'", ctx.friendly(chapter));
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.RESET));
        ctx.setFeedback("§7Reset %d quest(s) in %s", ids.size(), ctx.friendly(chapter));
    }

    void forceCompleteCategoryOnRightClick(String categoryId) {
        List<ResourceLocation> ids = state.questIdsInCategory(categoryId);
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            ctx.setFeedback("§7No quests in '%s'", label);
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        ctx.setFeedback("§aForce-completed %d quest(s) in %s", ids.size(), label);
    }

    void resetCategoryOnRightClick(String categoryId) {
        List<ResourceLocation> ids = state.questIdsInCategory(categoryId);
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            ctx.setFeedback("§7No quests in '%s'", label);
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.RESET));
        ctx.setFeedback("§7Reset %d quest(s) in %s", ids.size(), label);
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
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");

        boolean wasStub = false;
        try {
            Path f = ChronicleOverviewScreen.chaptersFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (line.trim().equalsIgnoreCase(upper)) {
                        wasStub = true;
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}

        ChapterConfig savedConfig = ChapterConfig.get(chapter);
        CategoryDefinition owningCategory = CategoryRegistry.categoryFor(chapter);
        String owningCategoryId = owningCategory != null ? owningCategory.id() : null;

        int questCount = questsInChapter.size();
        boolean finalWasStub = wasStub;

        Runnable applyDelete = () -> {
            for (QuestNode n : questsInChapter) {
                QuestTreeRegistry.removeQuest(n.getId());
                QuestFileSaver.deleteQuestFiles(n);
                if (editorState.selectedNode == n) editorState.selectedNode = null;
            }

            try {
                Path f = ChronicleOverviewScreen.chaptersFile();
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
                CategoryRegistry.removeChapterFromCategory(owningCategory.id(), chapter);
                CategoryRegistry.save();
            }

            ChapterConfig.remove(upper);
            ChapterConfig.save();

            state.invalidateChapterCaches();

            if (ctx.selectedChapter().equalsIgnoreCase(chapter)) {
                List<String> remainingChapters = ctx.buildChapterList();
                state.setSelectedChapter(remainingChapters.isEmpty() ? "" : remainingChapters.get(0));
            }
            state.rebuild();
        };

        ctx.pushUndo("Undo: chapter restored (%d quest(s))".formatted(questCount), () -> {
            for (Map.Entry<QuestNode, String> e : savedSnbt.entrySet()) {
                QuestFileSaver.restoreRawSnbt(e.getKey(), e.getValue());
            }
            QuestFileLoader.loadAdditiveFromDisk(base);

            if (finalWasStub) {
                try {
                    Path f = ChronicleOverviewScreen.chaptersFile();
                    List<String> lines = new ArrayList<>();
                    if (Files.exists(f)) lines.addAll(Files.readAllLines(f, StandardCharsets.UTF_8));
                    if (lines.stream().noneMatch(l -> l.trim().equalsIgnoreCase(upper))) lines.add(upper);
                    Files.createDirectories(f.getParent());
                    Files.writeString(f, String.join("\n", lines), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
            ChapterConfig.put(upper, savedConfig);
            ChapterConfig.save();
            if (owningCategoryId != null) {
                CategoryRegistry.addChapterToCategory(owningCategoryId, upper);
                CategoryRegistry.save();
            }

            state.invalidateChapterCaches();
            state.setSelectedChapter(upper);
            state.rebuild();
        }, applyDelete);

        applyDelete.run();
        String countSuffix = questCount > 0 ? " (%d quests)".formatted(questCount) : "";
        ctx.setFeedbackDone("Chapter deleted: %s%s", chapter, countSuffix);
    }

    void deleteCategoryOnRightClick(String categoryId) {
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        if (cat == null) return;

        if (!cat.chapters().isEmpty() && !categoryId.equals(armedDeleteCategoryId)) {
            armedDeleteCategoryId = categoryId;
            ctx.setFeedback("§6Right-click '%s' -> Delete Category again to confirm §7(%d chapter(s) will become " +
                    "uncategorized, not deleted themselves)", cat.displayName(), cat.chapters().size());
            return;
        }

        armedDeleteCategoryId = null;
        int chapterCount = cat.chapters().size();
        CategoryRegistry.removeCategory(categoryId);
        CategoryRegistry.save();
        state.invalidateChapterCaches();
        state.rebuild();
        String uncatSuffix = chapterCount > 0 ? " (%d chapter(s) uncategorized)".formatted(chapterCount) : "";
        ctx.setFeedback("§aCategory deleted: %s%s", cat.displayName(), uncatSuffix);
    }

    void deleteChapterOnRightClick(String chapter) {
        int questCount = state.chapterQuestCount(chapter);
        if (questCount > 0 && !chapter.equals(armedDeleteChapterId)) {
            armedDeleteChapterId = chapter;
            ctx.setFeedback("§6Right-click '%s' -> Delete Chapter again to confirm §7(%d quest(s) will be deleted)",
                    ctx.friendly(chapter), questCount);
            return;
        }

        armedDeleteChapterId = null;
        deleteChapter(chapter);
        ctx.setFeedback("§aChapter deleted: %s", ctx.friendly(chapter));
    }

    void openSidebarContextMenu(SidebarRow row, int mx, int my) {
        List<SidebarPanel.MenuAction> actions = new ArrayList<>();
        Screen parent = state.thisScreen();
        if (row.isFolder()) {
            CategoryDefinition cat = CategoryRegistry.get(row.id());
            String currentLabel = cat != null ? cat.displayName() : row.label();
            actions.add(new SidebarPanel.MenuAction("Rename Category", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new NewFolderScreen(parent, row.id(), currentLabel, id -> state.rebuild()));
            }));
            actions.add(new SidebarPanel.MenuAction("Category Theme…", () -> Minecraft.getInstance().setScreen(new CategoryThemeScreen(parent, row.id()))));
            actions.add(new SidebarPanel.MenuAction("Force Complete Category",
                    () -> forceCompleteCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction("Reset Category",
                    () -> resetCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteCategoryId) ? "§cConfirm Delete Category" : "Delete Category",
                    () -> deleteCategoryOnRightClick(row.id())));
        } else {
            actions.add(new SidebarPanel.MenuAction("Chapter Settings…", () -> Minecraft.getInstance().setScreen(new ChapterThemeScreen(parent, row.id()))));
            actions.add(new SidebarPanel.MenuAction("Force Complete Chapter",
                    () -> forceCompleteChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction("Reset Chapter",
                    () -> resetChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteChapterId) ? "§cConfirm Delete Chapter" : "Delete Chapter",
                    () -> deleteChapterOnRightClick(row.id())));
        }
        state.openContextMenuFor(mx, my, actions);
    }
}
