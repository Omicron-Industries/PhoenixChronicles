package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class QuestEditOps {

    private final ScreenContext ctx;
    private final QuestEditOpsState state;
    private final GraphEditorState editorState;

    private volatile boolean ftbImportInProgress = false;

    public QuestEditOps(ScreenContext ctx, QuestEditOpsState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    public void questCopy(QuestNode node) {
        String content = QuestFileSaver.readRawSnbt(node);
        if (content == null || content.isBlank()) {
            ctx.setFeedback("§cCopy failed. Quest file not found on disk");
            return;
        }
        editorState.questClipboard = content;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.keyboardHandler.setClipboard(content);
        ctx.setFeedback("§aCopied SNBT for '%s'  (Ctrl+V to paste)", node.getId().getPath());
    }

    public void questPaste() {
        String src = editorState.questClipboard;
        Minecraft mc = Minecraft.getInstance();
        if (src == null || src.isBlank()) {
            src = mc != null ? mc.keyboardHandler.getClipboard() : null;
        }
        if (src == null || src.isBlank()) {
            ctx.setFeedback("§eNothing to paste (Ctrl+C a quest first)");
            return;
        }

        if (!src.contains("id:")) {
            ctx.setFeedback("§eClipboard doesn't look like quest SNBT");
            return;
        }
        try {
            String newPath = QuestFileSaver.pasteQuestFromSnbt(src, ctx.selectedChapter());
            state.rebuild();
            ctx.setFeedback("§aPasted → %s", newPath);

            ResourceLocation newId = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", newPath);
            QuestNode pasted = QuestTreeRegistry.getQuest(newId);
            if (pasted != null) {
                String pastedSnbt = QuestFileSaver.readRawSnbt(pasted);
                Path pastedPath = QuestFileSaver.getQuestSnbtPath(pasted);
                ctx.pushUndo("Undo: pasted quest removed", () -> {
                    QuestTreeRegistry.removeQuest(newId);
                    state.deleteQuestFiles(pasted);
                    if (editorState.selectedNode == pasted) editorState.selectedNode = null;
                    state.rebuild();
                }, () -> {
                    QuestFileSaver.restoreRawSnbt(pasted, pastedSnbt);
                    QuestFileLoader.loadOneFromDisk(pastedPath);
                    state.rebuild();
                });
            }
        } catch (IOException e) {
            ctx.setFeedback("§cPaste error: %s", e.getMessage());
        }
    }

    public void chainMultiSelection() {
        List<QuestNode> ordered = editorState.multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (ordered.size() < 2) {
            ctx.setFeedback("§eSelect 2+ quests to chain");
            return;
        }
        int wired = 0;
        List<QuestNode[]> newLinks = new java.util.ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            QuestNode child = ordered.get(i);
            QuestNode parent = ordered.get(i - 1);
            if (!child.getPrerequisites().contains(parent)) {
                child.addPrerequisite(parent);
                state.saveNodePrereqsToDisk(child);
                newLinks.add(new QuestNode[] { child, parent });
                wired++;
            }
        }
        state.buildLineCache();
        state.rebuild();
        ctx.setFeedback("§aChained %d quests (%d new link%s)", ordered.size(), wired, wired == 1 ? "" : "s");
        if (!newLinks.isEmpty()) {
            ctx.pushUndo("Undo: chain links removed", () -> {
                for (QuestNode[] link : newLinks) {
                    link[0].removePrerequisite(link[1]);
                    state.saveNodePrereqsToDisk(link[0]);
                }
                state.buildLineCache();
                state.rebuild();
            }, () -> {
                for (QuestNode[] link : newLinks) {
                    link[0].addPrerequisite(link[1]);
                    state.saveNodePrereqsToDisk(link[0]);
                }
                state.buildLineCache();
                state.rebuild();
            });
        }
    }

    public void fanFromLeftmost() {
        List<QuestNode> nodes = editorState.multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (nodes.size() < 2) {
            ctx.setFeedback("§eSelect 2+ quests to fan");
            return;
        }
        QuestNode root = nodes.get(0);
        int wired = 0;
        List<QuestNode> newlyLinked = new java.util.ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            QuestNode child = nodes.get(i);
            if (!child.getPrerequisites().contains(root)) {
                child.addPrerequisite(root);
                state.saveNodePrereqsToDisk(child);
                newlyLinked.add(child);
                wired++;
            }
        }
        state.buildLineCache();
        state.rebuild();
        ctx.setFeedback("§aFanned from '%s' to %d quest%s", root.getId().getPath(), wired, wired == 1 ? "" : "s");
        if (!newlyLinked.isEmpty()) {
            ctx.pushUndo("Undo: fan links removed", () -> {
                for (QuestNode child : newlyLinked) {
                    child.removePrerequisite(root);
                    state.saveNodePrereqsToDisk(child);
                }
                state.buildLineCache();
                state.rebuild();
            }, () -> {
                for (QuestNode child : newlyLinked) {
                    child.addPrerequisite(root);
                    state.saveNodePrereqsToDisk(child);
                }
                state.buildLineCache();
                state.rebuild();
            });
        }
    }

    public void runFtbImport() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (ftbImportInProgress) {
            ctx.setFeedback("§eFTB import already in progress…");
            return;
        }
        Path base = mc.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        Path importDir = base.resolve("ftb_import");

        ftbImportInProgress = true;
        ctx.setFeedback("§7Importing FTB Quests… this may take a moment for large packs");

        Thread worker = new Thread(() -> {
            FtbQuestsImporter.ImportResult result = null;
            Exception error = null;
            try {
                java.nio.file.Files.createDirectories(importDir);
                result = FtbQuestsImporter.importDirectory(importDir, base);
            } catch (Exception e) {
                error = e;
            }
            FtbQuestsImporter.ImportResult finalResult = result;
            Exception finalError = error;
            mc.execute(() -> finishFtbImport(finalResult, finalError));
        }, "phoenix-chronicles-ftb-import");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishFtbImport(FtbQuestsImporter.ImportResult r, Exception error) {
        ftbImportInProgress = false;
        if (error != null) {
            ctx.setFeedback("§cFTB import error: %s", error.getMessage());
            return;
        }
        if (r.imported() == 0 && r.skipped() == 0) {
            ctx.setFeedback("§eNo .snbt files found in config/phoenix_chronicles/ftb_import/");
        } else {
            String skippedPart = r.skipped() > 0 ? " §c(%d skipped)".formatted(r.skipped()) : "";
            String warningsPart = r.warnings().isEmpty() ? "" :
                    " §8%d warnings".formatted(r.warnings().size());
            ctx.setFeedback("§aImported %d quests%s%s", r.imported(), skippedPart, warningsPart);
            if (r.imported() > 0) {
                QuestFileLoader.reloadAllQuestsFromDisk();

                ChroniclesLangPack.reload();
                state.rebuild();
            }
        }
    }

    public void duplicateQuest(QuestNode source) {
        if (!QuestFileSaver.doesQuestFileExist(source)) {
            ctx.setFeedback("Cannot duplicate. Source file not found on disk");
            return;
        }
        try {
            String newPath = QuestFileSaver.duplicateQuestOnDisk(source);
            ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(source.getId().getNamespace(), newPath);
            QuestNode duplicated = QuestTreeRegistry.getQuest(newId);
            if (duplicated != null) {
                String duplicatedSnbt = QuestFileSaver.readRawSnbt(duplicated);
                Path duplicatedPath = QuestFileSaver.getQuestSnbtPath(duplicated);
                ctx.pushUndo("Undo: duplicate removed", () -> {
                    QuestTreeRegistry.removeQuest(newId);
                    state.deleteQuestFiles(duplicated);
                    if (editorState.selectedNode == duplicated) editorState.selectedNode = null;
                    state.rebuild();
                }, () -> {
                    QuestFileSaver.restoreRawSnbt(duplicated, duplicatedSnbt);
                    QuestFileLoader.loadOneFromDisk(duplicatedPath);
                    state.rebuild();
                });
            }
            state.rebuild();
            ctx.setFeedbackDone("Duplicated → %s", newPath);
        } catch (IOException e) {
            e.printStackTrace();
            ctx.setFeedback("Duplicate failed: %s", e.getMessage());
        }
    }

    public void createLinkStubAt(int canvasX, int canvasY, QuestNode target) {
        if (target == null) return;

        String base = ("link_" + target.getId().getPath())
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]", "");
        if (base.isBlank()) base = "link_quest";

        String path = base;
        int suffix = 2;
        while (QuestTreeRegistry.getQuest(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", path)) != null) {
            path = base + "_" + suffix;
            suffix++;
        }

        ResourceLocation newId = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", path);
        QuestNode node = new QuestNode(newId, Component.literal(""), Component.literal(""));
        node.setChapter(ctx.selectedChapter());
        node.setCustomPosition(canvasX, canvasY);
        node.setLinkTarget(target.getId());

        QuestTreeRegistry.injectDynamicQuestNode(node, null);
        QuestFileSaver.saveOneQuestToDisk(node);
        state.rebuild();
        ctx.setFeedbackDone("§aLinked → %s", target.getId().getPath());

        ctx.pushUndo("Undo: link quest removed", () -> {
            QuestTreeRegistry.removeQuest(newId);
            state.deleteQuestFiles(node);
            if (editorState.selectedNode == node) editorState.selectedNode = null;
            state.rebuild();
        }, () -> {
            QuestTreeRegistry.injectDynamicQuestNode(node, null);
            QuestFileSaver.saveOneQuestToDisk(node);
            state.rebuild();
        });
    }
}
