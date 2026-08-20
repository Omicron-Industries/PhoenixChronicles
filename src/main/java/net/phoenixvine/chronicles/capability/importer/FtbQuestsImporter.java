package net.phoenixvine.chronicles.capability.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FtbQuestsImporter {

    private static final float COORD_SCALE = 80f;
    private static final float COORD_PADDING = 2.0f;
    private static final Pattern LANG_KEY = Pattern.compile("^\\{([A-Za-z0-9_.-]+)}$");

    private static Map<Long, CompoundTag> ftbRewardTables = Map.of();

    public record ImportResult(int imported, int skipped, String category, List<String> warnings) {}

    private record QuestLoc(String category, String path) {}

    private record LinkStub(String linkFtbId, String path, String linkedFtbId, double x, double y, String shape,
                            List<String> dependencyFtbIds, boolean oneCompleted, Integer minRequiredDependencies) {}

    private record ChapterIndex(
                                Path file,
                                CompoundTag chapter,
                                String categorySlug,
                                String displayTitle,
                                Map<String, String> idToPath,
                                List<LinkStub> linkStubs,
                                double minX,
                                double minY,
                                String ftbGroupId) {}

    public static ImportResult importDirectory(Path importDir, Path outputDir) {
        Path cleanImportDir = importDir.toAbsolutePath().normalize();
        System.out.println("[PhoenixChronicles] Target Import Directory: " + cleanImportDir);

        if (!Files.exists(cleanImportDir)) {
            System.err.println("[PhoenixChronicles] ERROR: Import directory does not physically exist!");
            return new ImportResult(0, 0, "", List.of("Import dir not found: " + cleanImportDir));
        }

        ftbRewardTables = loadFtbRewardTables(outputDir);
        Map<String, String> langMap = buildLangMap(cleanImportDir);
        List<String> warnings = new ArrayList<>();
        if (!langMap.isEmpty()) warnings.add("Loaded " + langMap.size() + " lang key(s) for translation.");

        List<Path> snbtFiles;
        try (var stream = Files.list(cleanImportDir)) {
            snbtFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt"))
                    .toList();
        } catch (IOException e) {
            String critError = "Failed to list import dir: " + e.getMessage();
            System.err.println("[PhoenixChronicles] CRITICAL: " + critError);
            warnings.add(critError);
            return new ImportResult(0, 0, "", warnings);
        }
        System.out.println("[PhoenixChronicles] Found " + snbtFiles.size() + " chapter file(s).");

        List<ChapterIndex> chapters = new ArrayList<>();
        Map<String, QuestLoc> globalIndex = new HashMap<>();
        Map<String, CompoundTag> globalQuestsById = new HashMap<>();

        Set<String> globalUsedPaths = new HashSet<>();

        for (Path file : snbtFiles) {
            String fileName = file.getFileName().toString();
            String fallbackTitle = fileName.substring(0, fileName.length() - 5);
            try {
                String snbt = Files.readString(file, StandardCharsets.UTF_8);

                CompoundTag chapter = LenientSnbtParser.parse(snbt);
                ChapterIndex idx = indexChapter(chapter, file, fallbackTitle, langMap, warnings, globalUsedPaths);
                chapters.add(idx);
                for (Map.Entry<String, String> e : idx.idToPath().entrySet()) {
                    globalIndex.put(e.getKey(), new QuestLoc(idx.categorySlug(), e.getValue()));
                }

                for (LinkStub link : idx.linkStubs()) {
                    globalIndex.put(link.linkFtbId(), new QuestLoc(idx.categorySlug(), link.path()));
                }

                indexQuestsById(chapter, globalQuestsById);
            } catch (Exception e) {
                String errorMsg = "Failed to index " + fileName + ": " + e.getMessage();
                System.err.println("[PhoenixChronicles] " + errorMsg);
                warnings.add(errorMsg);
            }
        }

        autoIncludeChaptersForDanglingDeps(chapters, globalIndex, outputDir, langMap, warnings, globalUsedPaths,
                globalQuestsById);

        chapters.sort(Comparator.comparingInt(
                idx -> idx.chapter().contains("order_index") ? idx.chapter().getInt("order_index") : 0));

        Map<String, Map<String, String>> localStandIns = buildLocalStandIns(chapters);

        int totalImported = 0, totalSkipped = 0;
        String lastCat = "";

        Map<String, String> langOut = new LinkedHashMap<>();
        for (ChapterIndex idx : chapters) {
            try {
                ImportResult r = writeChapter(idx, outputDir, globalIndex, localStandIns, langMap, warnings, langOut,
                        globalQuestsById);
                totalImported += r.imported();
                totalSkipped += r.skipped();
                if (!r.category().isEmpty()) lastCat = r.category();
                System.out.println("[PhoenixChronicles] Wrote chapter: " + idx.file().getFileName() + " -> quests/" +
                        idx.categorySlug().toLowerCase(Locale.ROOT) + " (" + r.imported() + " quests, " + r.skipped() +
                        " skipped)");
            } catch (Exception e) {
                String errorMsg = "Failed to write " + idx.file().getFileName() + ": " + e.getMessage();
                System.err.println("[PhoenixChronicles] " + errorMsg);
                warnings.add(errorMsg);
                totalSkipped++;
            }
        }

        importChapterGroups(chapters, cleanImportDir, langMap, warnings);

        net.phoenixvine.chronicles.registry.QuestLangRegistry.mergeWrite(outputDir, langOut);
        if (!langOut.isEmpty()) warnings.add("Wrote " + langOut.size() + " lang key(s) to lang/en_us.json.");

        System.out.println(
                "[PhoenixChronicles] Import Finished. Total Imported: " + totalImported + ", Total Skipped: " +
                        totalSkipped);
        return new ImportResult(totalImported, totalSkipped, lastCat, warnings);
    }

    private static ChapterIndex indexChapter(CompoundTag chapter, Path file, String fallbackTitle,
                                             Map<String, String> langMap, List<String> warnings,
                                             Set<String> globalUsedPaths) {
        String categorySlug = slugify(fallbackTitle).toUpperCase(Locale.ROOT);
        if (categorySlug.isEmpty()) categorySlug = "IMPORTED";

        String rawTitle = chapter.contains("title") ? chapter.get("title").getAsString() : "";
        String resolvedTitle = resolveText(rawTitle, langMap, warnings, false);
        String displayTitle = isUsableTitle(resolvedTitle) ? resolvedTitle : humanize(fallbackTitle);

        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);

        Map<String, String> idToPath = new LinkedHashMap<>();
        double minX = 0, minY = 0;
        boolean first = true;

        for (int i = 0; i < quests.size(); i++) {
            try {
                CompoundTag q = quests.getCompound(i);
                String ftbId = q.getString("id");

                String rawQuestTitle = q.contains("title") ? q.get("title").getAsString() : "";
                String questTitle = resolveText(rawQuestTitle, langMap, warnings, false);
                String forSlug = isUsableTitle(questTitle) ? stripForSlug(questTitle) : "";

                String path = uniquePath(ftbId, forSlug, globalUsedPaths);
                idToPath.put(ftbId, path);
                globalUsedPaths.add(path);

                double x = numeric(q.get("x"));
                double y = numeric(q.get("y"));
                if (first) {
                    minX = x;
                    minY = y;
                    first = false;
                } else {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                }
            } catch (Exception e) {

                warnings.add("Chapter " + file.getFileName() + ": quest #" + i + " failed to index (" + e.getMessage() +
                        ") - skipped.");
            }
        }

        List<LinkStub> linkStubs = new ArrayList<>();
        ListTag questLinks = chapter.getList("quest_links", Tag.TAG_COMPOUND);
        for (int i = 0; i < questLinks.size(); i++) {
            try {
                CompoundTag link = questLinks.getCompound(i);
                String linkFtbId = link.getString("id");
                String linkedFtbId = link.getString("linked_quest");
                if (linkedFtbId.isEmpty()) continue;
                String path = uniquePath(linkFtbId, "link", globalUsedPaths);
                globalUsedPaths.add(path);
                double x = numeric(link.get("x"));
                double y = numeric(link.get("y"));
                String shape = link.contains("shape") ? link.getString("shape") : "";

                List<String> dependencyFtbIds = new ArrayList<>();
                ListTag linkDeps = link.getList("dependencies", Tag.TAG_STRING);
                for (int di = 0; di < linkDeps.size(); di++) {
                    String depId = linkDeps.getString(di);
                    if (!depId.isEmpty()) dependencyFtbIds.add(depId);
                }
                boolean oneCompleted = "one_completed".equals(link.getString("dependency_requirement"));
                Integer minRequiredDependencies = link.contains("min_required_dependencies") ?
                        link.getInt("min_required_dependencies") : null;

                linkStubs.add(new LinkStub(linkFtbId, path, linkedFtbId, x, y, shape, dependencyFtbIds, oneCompleted,
                        minRequiredDependencies));

                if (first) {
                    minX = x;
                    minY = y;
                    first = false;
                } else {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                }
            } catch (Exception e) {
                warnings.add("Chapter " + file.getFileName() + ": quest_links #" + i + " failed to index (" +
                        e.getMessage() + ") - skipped.");
            }
        }

        String ftbGroupId = chapter.contains("group") ? tagAsString(chapter, "group").trim() : "";
        return new ChapterIndex(file, chapter, categorySlug, displayTitle, idToPath, linkStubs, minX, minY,
                ftbGroupId);
    }

    private static void autoIncludeChaptersForDanglingDeps(List<ChapterIndex> chapters,
                                                           Map<String, QuestLoc> globalIndex, Path outputDir,
                                                           Map<String, String> langMap, List<String> warnings,
                                                           Set<String> globalUsedPaths,
                                                           Map<String, CompoundTag> globalQuestsById) {
        Map<Path, String> dirLabel = new LinkedHashMap<>();
        Path realChaptersDir = outputDir.resolve("ftbquests").resolve("quests").resolve("chapters");
        if (Files.isDirectory(realChaptersDir)) dirLabel.put(realChaptersDir.toAbsolutePath().normalize(), "");

        Path instanceRoot = outputDir.getParent();
        if (instanceRoot != null) {
            Path overridesRoot = instanceRoot.resolve("config-overrides");
            if (Files.isDirectory(overridesRoot)) {
                try (var stream = Files.list(overridesRoot)) {
                    stream.filter(Files::isDirectory).forEach(modeDir -> {
                        Path chaptersDir = modeDir.resolve("ftbquests").resolve("quests").resolve("chapters");
                        if (Files.isDirectory(chaptersDir)) {
                            dirLabel.put(chaptersDir.toAbsolutePath().normalize(),
                                    modeDir.getFileName().toString());
                        }
                    });
                } catch (IOException ignored) {}
            }
        }
        if (dirLabel.isEmpty()) return;

        Set<Path> alreadyIncluded = new HashSet<>();
        for (ChapterIndex idx : chapters) alreadyIncluded.add(idx.file().toAbsolutePath().normalize());

        List<Path> candidates = new ArrayList<>();
        for (Path dir : dirLabel.keySet()) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> !Files.isDirectory(p))
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt"))
                        .map(p -> p.toAbsolutePath().normalize())
                        .filter(p -> !alreadyIncluded.contains(p))
                        .forEach(candidates::add);
            } catch (IOException ignored) {}
        }

        List<String> autoIncluded = new ArrayList<>();
        int safetyCap = 40;
        while (safetyCap-- > 0 && !candidates.isEmpty()) {
            Set<String> unresolved = new HashSet<>();
            for (ChapterIndex idx : chapters) {
                for (String refId : collectReferencedIds(idx.chapter())) {
                    if (!globalIndex.containsKey(refId)) unresolved.add(refId);
                }
            }
            if (unresolved.isEmpty()) break;

            Path matchedFile = null;
            CompoundTag matchedChapter = null;
            for (Path candidate : candidates) {
                try {
                    CompoundTag parsed = LenientSnbtParser.parse(Files.readString(candidate, StandardCharsets.UTF_8));
                    if (!Collections.disjoint(topLevelQuestIds(parsed), unresolved)) {
                        matchedFile = candidate;
                        matchedChapter = parsed;
                        break;
                    }
                } catch (Exception ignored) {

                }
            }
            if (matchedFile == null) break;

            candidates.remove(matchedFile);
            String fileName = matchedFile.getFileName().toString();
            String fallbackTitle = fileName.substring(0, fileName.length() - 5);
            try {
                ChapterIndex idx = indexChapter(matchedChapter, matchedFile, fallbackTitle, langMap, warnings,
                        globalUsedPaths);
                chapters.add(idx);
                for (Map.Entry<String, String> e : idx.idToPath().entrySet()) {
                    globalIndex.put(e.getKey(), new QuestLoc(idx.categorySlug(), e.getValue()));
                }
                for (LinkStub link : idx.linkStubs()) {
                    globalIndex.put(link.linkFtbId(), new QuestLoc(idx.categorySlug(), link.path()));
                }
                indexQuestsById(matchedChapter, globalQuestsById);
                String sourceLabel = "";
                for (Map.Entry<Path, String> e : dirLabel.entrySet()) {
                    if (matchedFile.startsWith(e.getKey())) {
                        sourceLabel = e.getValue();
                        break;
                    }
                }
                autoIncluded.add(sourceLabel.isEmpty() ? fileName : sourceLabel + "/" + fileName);
            } catch (Exception e) {
                warnings.add(
                        "Failed to auto-include " + fileName + " (needed to resolve a dependency): " + e.getMessage());
            }
        }

        if (!autoIncluded.isEmpty()) {
            warnings.add("Auto-included " + autoIncluded.size() + " chapter file(s) not present in the import " +
                    "folder because other imported chapters depend on quests inside them: " +
                    String.join(", ", autoIncluded) + ". Entries prefixed with a mode name (normal/hardmode/expert) " +
                    "came from config-overrides rather than the live chapter file, and were merged into the " +
                    "matching chapter/category alongside it - this covers quests only reachable in a different " +
                    "pack mode than the one currently active.");
        }
    }

    private static void indexQuestsById(CompoundTag chapter, Map<String, CompoundTag> globalQuestsById) {
        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < quests.size(); i++) {
            CompoundTag q = quests.getCompound(i);
            String id = q.getString("id");
            if (!id.isEmpty()) globalQuestsById.put(id, q);
        }
    }

    private static Map<String, Map<String, String>> buildLocalStandIns(List<ChapterIndex> chapters) {
        Map<String, Map<String, String>> standIns = new HashMap<>();
        for (ChapterIndex idx : chapters) {
            for (LinkStub link : idx.linkStubs()) {
                standIns.computeIfAbsent(link.linkedFtbId(), k -> new HashMap<>())
                        .put(idx.categorySlug(), link.path());
            }
        }
        return standIns;
    }

    private static QuestLoc resolveDependencyLoc(String depFtbId, ChapterIndex dependingChapter,
                                                 Map<String, QuestLoc> globalIndex,
                                                 Map<String, Map<String, String>> localStandIns) {
        String dependingChapterSlug = dependingChapter.categorySlug();

        String localPath = dependingChapter.idToPath().get(depFtbId);
        if (localPath != null) return new QuestLoc(dependingChapterSlug, localPath);
        for (LinkStub link : dependingChapter.linkStubs()) {
            if (link.linkFtbId().equals(depFtbId)) return new QuestLoc(dependingChapterSlug, link.path());
        }

        QuestLoc real = globalIndex.get(depFtbId);
        if (real != null) {
            if (real.category().equals(dependingChapterSlug)) return real;
            Map<String, String> standInsForTarget = localStandIns.get(depFtbId);
            String standInPath = standInsForTarget == null ? null : standInsForTarget.get(dependingChapterSlug);
            return standInPath != null ? new QuestLoc(dependingChapterSlug, standInPath) : real;
        }
        return null;
    }

    private static Set<String> collectReferencedIds(CompoundTag chapter) {
        Set<String> ids = new HashSet<>();
        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < quests.size(); i++) {
            ListTag deps = quests.getCompound(i).getList("dependencies", Tag.TAG_STRING);
            for (int di = 0; di < deps.size(); di++) {
                String depId = deps.getString(di);
                if (!depId.isEmpty()) ids.add(depId);
            }
        }
        ListTag links = chapter.getList("quest_links", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            CompoundTag link = links.getCompound(i);
            String linkedId = link.getString("linked_quest");
            if (!linkedId.isEmpty()) ids.add(linkedId);
            ListTag linkDeps = link.getList("dependencies", Tag.TAG_STRING);
            for (int di = 0; di < linkDeps.size(); di++) {
                String depId = linkDeps.getString(di);
                if (!depId.isEmpty()) ids.add(depId);
            }
        }
        return ids;
    }

    private static Set<String> topLevelQuestIds(CompoundTag chapter) {
        Set<String> ids = new HashSet<>();
        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < quests.size(); i++) {
            String id = quests.getCompound(i).getString("id");
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static void importChapterGroups(List<ChapterIndex> chapters, Path importDir,
                                            Map<String, String> langMap, List<String> warnings) {
        Map<String, List<String>> groupIdToChapters = new LinkedHashMap<>();
        for (ChapterIndex idx : chapters) {
            String groupId = idx.ftbGroupId();
            if (groupId == null || groupId.isBlank()) continue;
            groupIdToChapters.computeIfAbsent(groupId, k -> new ArrayList<>()).add(idx.categorySlug());
        }
        if (groupIdToChapters.isEmpty()) return;

        Map<String, String> groupTitles = loadChapterGroupTitles(importDir, langMap, warnings);

        int created = 0;
        for (Map.Entry<String, List<String>> e : groupIdToChapters.entrySet()) {
            String ftbGroupId = e.getKey();
            String categoryId = "ftb_" + ftbGroupId.toLowerCase(Locale.ROOT);
            String label = groupTitles.get(ftbGroupId);
            if (label == null || label.isBlank()) label = "Imported Group (" + ftbGroupId.substring(0,
                    Math.min(6, ftbGroupId.length())) + ")";

            if (net.phoenixvine.chronicles.registry.CategoryRegistry.get(categoryId) == null) {
                net.phoenixvine.chronicles.registry.CategoryRegistry.addCategory(categoryId, label);
                created++;
            }
            for (String chapterSlug : e.getValue()) {
                net.phoenixvine.chronicles.registry.CategoryRegistry.addChapterToCategory(categoryId, chapterSlug);
            }
        }
        net.phoenixvine.chronicles.registry.CategoryRegistry.save();
        if (created > 0) {
            warnings.add("Imported " + created + " chapter group(s) as categories" +
                    (groupTitles.isEmpty() ? " (no chapter_groups.snbt found - used placeholder names, rename them " +
                            "in the chapter settings screen)." : "."));
        }
    }

    private static Map<String, String> loadChapterGroupTitles(Path importDir, Map<String, String> langMap,
                                                              List<String> warnings) {
        Map<String, String> titles = new HashMap<>();
        List<Path> candidates = new ArrayList<>();
        candidates.add(importDir.resolve("chapter_groups.snbt"));
        if (importDir.getParent() != null) candidates.add(importDir.getParent().resolve("chapter_groups.snbt"));

        Path found = null;
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                found = candidate;
                break;
            }
        }
        if (found == null) return titles;

        try {
            String snbt = Files.readString(found, StandardCharsets.UTF_8);
            CompoundTag root = LenientSnbtParser.parse(snbt);
            ListTag groups = root.getList("chapter_groups", Tag.TAG_COMPOUND);
            for (int i = 0; i < groups.size(); i++) {
                CompoundTag g = groups.getCompound(i);
                if (!g.contains("id")) continue;

                String id = tagAsString(g, "id").trim();
                if (id.isEmpty()) continue;
                String rawTitle = g.contains("title") ? tagAsString(g, "title") : "";
                String resolved = resolveText(rawTitle, langMap, warnings, false);
                if (isUsableTitle(resolved)) titles.put(id, resolved);
            }
        } catch (Exception e) {
            warnings.add("Failed to read " + found.getFileName() + ": " + e.getMessage());
        }
        return titles;
    }

    private static ImportResult writeChapter(ChapterIndex idx, Path outputDir, Map<String, QuestLoc> globalIndex,
                                             Map<String, Map<String, String>> localStandIns,
                                             Map<String, String> langMap, List<String> warnings,
                                             Map<String, String> langOut,
                                             Map<String, CompoundTag> globalQuestsById) throws IOException {
        CompoundTag chapter = idx.chapter();
        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);

        Path questsBaseDir = outputDir.resolve("quests");
        Path categoryFolder = questsBaseDir.resolve(idx.categorySlug().toLowerCase(Locale.ROOT));
        Files.createDirectories(categoryFolder);

        writeMasterCategoryJson(categoryFolder, idx.categorySlug(), idx.displayTitle(),
                extractItemId(chapter.get("icon")),
                chapter.contains("order_index") ? chapter.getInt("order_index") : 0);

        int imported = 0, skipped = 0;
        for (int i = 0; i < quests.size(); i++) {
            CompoundTag q = quests.getCompound(i);
            String ftbId = q.getString("id");
            String path = idx.idToPath().get(ftbId);
            try {
                String nodeSnbt = convertQuest(q, idx, globalIndex, localStandIns, langMap, warnings, langOut);
                Path outFile = categoryFolder.resolve(path + ".snbt");
                Files.writeString(outFile, nodeSnbt, StandardCharsets.UTF_8);
                imported++;
            } catch (Exception e) {
                warnings.add("Quest " + ftbId + " (" + path + "): " + e.getMessage());
                skipped++;
            }
        }

        for (LinkStub link : idx.linkStubs()) {
            try {

                QuestLoc target = globalIndex.get(link.linkedFtbId());
                if (target == null) {
                    warnings.add("Chapter " + idx.file().getFileName() + ": quest link " + link.path() + " points at " +
                            link.linkedFtbId() + ", which was not found in any imported chapter - dropped.");
                    skipped++;
                    continue;
                }
                String linkSnbt = convertLinkStub(link, idx, target, globalIndex, localStandIns, warnings,
                        globalQuestsById);
                Path outFile = categoryFolder.resolve(link.path() + ".snbt");
                Files.writeString(outFile, linkSnbt, StandardCharsets.UTF_8);
                imported++;
            } catch (Exception e) {
                warnings.add("Quest link " + link.path() + ": " + e.getMessage());
                skipped++;
            }
        }

        return new ImportResult(imported, skipped, idx.categorySlug(), warnings);
    }

    private static String convertLinkStub(LinkStub link, ChapterIndex idx, QuestLoc target,
                                          Map<String, QuestLoc> globalIndex,
                                          Map<String, Map<String, String>> localStandIns, List<String> warnings,
                                          Map<String, CompoundTag> globalQuestsById) {
        StringBuilder sb = new StringBuilder("{\n");
        append(sb, "id", link.path());

        append(sb, "title", "");
        append(sb, "chapter", idx.categorySlug());
        append(sb, "shape", mapShape(link.shape()));

        int px = (int) Math.round((link.x() - idx.minX() + COORD_PADDING) * COORD_SCALE);
        int py = (int) Math.round((link.y() - idx.minY() + COORD_PADDING) * COORD_SCALE);
        sb.append("    positionX: ").append(px).append(",\n");
        sb.append("    positionY: ").append(py).append(",\n");
        append(sb, "link_target", "phoenix_chronicles:" + target.path());

        java.util.LinkedHashSet<String> depIds = new java.util.LinkedHashSet<>(link.dependencyFtbIds());
        CompoundTag targetTag = globalQuestsById.get(link.linkedFtbId());
        boolean targetOneCompleted = link.oneCompleted();
        Integer targetMinRequired = link.minRequiredDependencies();
        if (targetTag != null) {
            ListTag targetDeps = targetTag.getList("dependencies", Tag.TAG_STRING);
            for (int i = 0; i < targetDeps.size(); i++) {
                String depId = targetDeps.getString(i);
                if (!depId.isEmpty()) depIds.add(depId);
            }
            if (!link.oneCompleted() && link.minRequiredDependencies() == null) {
                targetOneCompleted = "one_completed".equals(targetTag.getString("dependency_requirement"));
                targetMinRequired = targetTag.contains("min_required_dependencies") ?
                        targetTag.getInt("min_required_dependencies") : null;
            }
        }

        if (!depIds.isEmpty()) {
            StringBuilder depSb = new StringBuilder();
            int depCount = 0;
            for (String depFtbId : depIds) {
                if (depFtbId.equals(link.linkedFtbId())) continue;
                QuestLoc loc = resolveDependencyLoc(depFtbId, idx, globalIndex, localStandIns);
                if (loc == null) {
                    warnings.add("Quest link " + link.path() + ": dependency " + depFtbId +
                            " was not found in any imported chapter (likely outside this import batch) - dropped.");
                    continue;
                }
                depSb.append("        {id: \"").append(loc.path()).append("\", chapter: \"")
                        .append(loc.category()).append("\", required: true},\n");
                depCount++;
            }
            if (depCount > 0) {
                sb.append("    prerequisites: [\n").append(depSb).append("    ],\n");
            }
        }

        boolean requireAll = !targetOneCompleted && targetMinRequired == null;
        sb.append("    require_all_prereqs: ").append(requireAll).append(",\n");
        if (targetMinRequired != null) {
            sb.append("    optional_prereq_min_count: ").append(targetMinRequired).append(",\n");
        }

        sb.append("}");
        return sb.toString();
    }

    private static void writeMasterCategoryJson(Path categoryFolder, String categorySlug, String displayName,
                                                String iconItem, int orderIndex) throws IOException {
        JsonObject master = new JsonObject();
        master.addProperty("id", categorySlug.toLowerCase(Locale.ROOT));
        master.addProperty("name", displayName);
        master.addProperty("icon",
                iconItem.isEmpty() || iconItem.equals("minecraft:air") ? "minecraft:book" : iconItem);
        master.addProperty("order", orderIndex);
        master.add("background", null);
        master.add("requirements", new JsonArray());

        Path jsonPath = categoryFolder.resolve(categorySlug.toLowerCase(Locale.ROOT) + ".json");
        Files.writeString(jsonPath, master.toString(), StandardCharsets.UTF_8);
    }

    private static String convertQuest(CompoundTag q, ChapterIndex idx, Map<String, QuestLoc> globalIndex,
                                       Map<String, Map<String, String>> localStandIns, Map<String, String> langMap,
                                       List<String> warnings, Map<String, String> langOut) {
        StringBuilder sb = new StringBuilder("{\n");
        String ftbId = q.getString("id");
        String path = idx.idToPath().get(ftbId);

        append(sb, "id", path);

        String rawTitle = q.contains("title") ? q.get("title").getAsString() : "";
        String title = resolveText(rawTitle, langMap, warnings, false);

        String rawSubtitle = q.contains("subtitle") ? q.get("subtitle").getAsString() : "";
        String subtitle = resolveText(rawSubtitle, langMap, warnings, false);

        String resolvedTitle = firstUsable(title, itemBasedFallbackTitle(q, langMap, warnings),
                "Quest " + shortId(ftbId));
        append(sb, "title", escape(resolvedTitle));

        if (isUsableTitle(subtitle) && !subtitle.equals(resolvedTitle)) {
            append(sb, "subtitle", escape(subtitle));
        }

        String desc = buildDescription(q.getList("description", Tag.TAG_STRING), langMap, warnings);
        if (!desc.isEmpty()) append(sb, "description", escape(desc));

        String langPrefix = "phoenix_chronicles.quest." + path.replace('/', '.') + ".";
        langOut.put(langPrefix + "title", resolvedTitle);
        if (isUsableTitle(subtitle) && !subtitle.equals(resolvedTitle)) langOut.put(langPrefix + "subtitle", subtitle);
        if (!desc.isEmpty()) langOut.put(langPrefix + "description", desc);

        append(sb, "chapter", idx.categorySlug());
        append(sb, "shape", mapShape(q.getString("shape")));

        double rawX = numeric(q.get("x"));
        double rawY = numeric(q.get("y"));
        int px = (int) Math.round((rawX - idx.minX() + COORD_PADDING) * COORD_SCALE);
        int py = (int) Math.round((rawY - idx.minY() + COORD_PADDING) * COORD_SCALE);
        sb.append("    positionX: ").append(px).append(",\n");
        sb.append("    positionY: ").append(py).append(",\n");

        String iconId = q.contains("icon") ? extractItemId(q.get("icon")) : "";
        if (iconId.isEmpty() || iconId.equals("minecraft:air")) {

            iconId = firstTaskItemId(q);
        }
        if (!iconId.isEmpty() && !iconId.equals("minecraft:air")) {
            append(sb, "icon_item", iconId);
        }

        ListTag deps = q.getList("dependencies", Tag.TAG_STRING);
        if (!deps.isEmpty()) {
            StringBuilder depSb = new StringBuilder();
            int depCount = 0;
            for (int i = 0; i < deps.size(); i++) {
                String depFtbId = deps.getString(i);

                QuestLoc loc = resolveDependencyLoc(depFtbId, idx, globalIndex, localStandIns);
                if (loc == null) {
                    warnings.add("Quest " + ftbId + ": dependency " + depFtbId +
                            " was not found in any imported chapter (likely outside this import batch) - dropped.");
                    continue;
                }
                depSb.append("        {id: \"").append(loc.path()).append("\", chapter: \"")
                        .append(loc.category()).append("\", required: true},\n");
                depCount++;
            }
            if (depCount > 0) {
                sb.append("    prerequisites: [\n").append(depSb).append("    ],\n");
            }
        }

        boolean oneCompleted = "one_completed".equals(q.getString("dependency_requirement"));
        boolean hasMinDeps = q.contains("min_required_dependencies");
        boolean requireAll = !oneCompleted && !hasMinDeps;
        sb.append("    require_all_prereqs: ").append(requireAll).append(",\n");

        if (hasMinDeps) {
            sb.append("    optional_prereq_min_count: ").append(q.getInt("min_required_dependencies")).append(",\n");
        }

        if (q.getBoolean("hide_dependency_lines")) {
            sb.append("    hide_dep_line: true,\n");
        }

        if (q.getBoolean("optional")) {
            sb.append("    optional: true,\n");
        }

        if (q.getBoolean("invisible")) {
            sb.append("    visibility: \"HIDDEN\",\n");
        } else if (q.getBoolean("hide_until_deps_visible")) {
            sb.append("    visibility: \"MYSTERY\",\n");
        }

        ListTag ftbTags = q.getList("tags", Tag.TAG_STRING);
        if (!ftbTags.isEmpty()) {
            StringBuilder tagsSb = new StringBuilder("[");
            for (int i = 0; i < ftbTags.size(); i++) {
                if (i > 0) tagsSb.append(", ");
                tagsSb.append('"').append(escape(ftbTags.getString(i))).append('"');
            }
            tagsSb.append("]");
            sb.append("    tags: ").append(tagsSb).append(",\n");
        }

        if (q.contains("repeat_time")) {
            long repeatTime = q.getLong("repeat_time");
            if (repeatTime == 0) {
                sb.append("    repeat_mode: \"INFINITE\",\n");
            } else if (repeatTime > 0) {
                long seconds = repeatTime / 20L;
                int hours = (int) Math.max(1, seconds / 3600L);
                sb.append("    repeat_mode: \"COOLDOWN\",\n");
                sb.append("    repeat_cooldown_hours: ").append(hours).append(",\n");
            }
        } else if (q.getBoolean("can_repeat")) {

            sb.append("    repeat_mode: \"INFINITE\",\n");
        }

        ListTag ftbTasks = q.getList("tasks", Tag.TAG_COMPOUND);
        if (!ftbTasks.isEmpty()) {
            sb.append("    tasks: [\n");
            for (int i = 0; i < ftbTasks.size(); i++) {
                String taskSnbt = convertTask(ftbTasks.getCompound(i), path, i, langMap, warnings, langOut);
                if (taskSnbt != null) sb.append("        ").append(taskSnbt).append(",\n");
            }
            sb.append("    ],\n");
        }

        ListTag ftbRewards = q.getList("rewards", Tag.TAG_COMPOUND);
        if (!ftbRewards.isEmpty()) {

            boolean singleChoiceGroup = ftbRewards.size() == 1 &&
                    "choice".equals(ftbRewards.getCompound(0).getString("type"));
            List<String> rewardSnbts = new ArrayList<>();
            boolean rewardChoice = false;
            if (singleChoiceGroup) {
                ChoiceOptions resolved = resolveChoiceOptions(ftbRewards.getCompound(0), path, warnings);
                if (resolved != null) {
                    for (int i = 0; i < resolved.options().size(); i++)
                        convertReward(resolved.options().getCompound(i), path, warnings, rewardSnbts);
                    rewardChoice = !rewardSnbts.isEmpty();
                }
            } else {
                for (int i = 0; i < ftbRewards.size(); i++) {
                    convertReward(ftbRewards.getCompound(i), path, warnings, rewardSnbts);
                }
            }
            if (!rewardSnbts.isEmpty()) {
                if (rewardChoice) {
                    sb.append("    reward_choice: true,\n");
                    sb.append("    reward_choice_count: 1,\n");
                }
                sb.append("    rewards: [\n");
                for (String rewardSnbt : rewardSnbts) sb.append("        ").append(rewardSnbt).append(",\n");
                sb.append("    ],\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private static String itemBasedFallbackTitle(CompoundTag q, Map<String, String> langMap, List<String> warnings) {
        String itemId = firstTaskItemId(q);
        if (itemId.isEmpty()) return "";
        return "Obtain " + localizedItemName(itemId, langMap);
    }

    private static String localizedItemName(String itemId, Map<String, String> langMap) {
        int colon = itemId.indexOf(':');
        String namespace = colon > 0 ? itemId.substring(0, colon) : "minecraft";
        String path = colon > 0 ? itemId.substring(colon + 1) : itemId;

        String itemKey = langMap.get("item." + namespace + "." + path);
        if (itemKey != null && !itemKey.isBlank()) return itemKey;

        String blockKey = langMap.get("block." + namespace + "." + path);
        if (blockKey != null && !blockKey.isBlank()) return blockKey;

        return path.replace('_', ' ');
    }

    private static String firstTaskItemId(CompoundTag q) {
        ListTag tasks = q.getList("tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < tasks.size(); i++) {
            CompoundTag t = tasks.getCompound(i);
            if (!"item".equals(t.getString("type"))) continue;
            Tag itemTag = t.get("item");
            String itemId = extractItemId(itemTag);
            if (!itemId.isEmpty() && !itemId.equals("minecraft:air")) {
                return itemId;
            }
            String tagValue = findTagFilterValue(itemTag, 0);
            if (tagValue != null && !tagValue.isEmpty()) {
                String firstInTag = firstItemInTag(tagValue);
                if (firstInTag != null) return firstInTag;
            }
        }
        return "";
    }

    private static String convertTask(CompoundTag t, String questPath, int idx,
                                      Map<String, String> langMap, List<String> warnings,
                                      Map<String, String> langOut) {
        String type = t.getString("type");
        String taskId = "phoenix_chronicles:" + questPath + "_task_" + idx;
        boolean optional = t.getBoolean("optional_task");

        String rawTaskTitle = t.contains("title") ? t.get("title").getAsString() : "";

        return switch (type) {
            case "item" -> convertItemTask(t, taskId, optional, rawTaskTitle, langMap, warnings, langOut);
            case "fluid" -> convertFluidTask(t, taskId, optional, rawTaskTitle, langMap, warnings, langOut);
            case "checkmark" -> {
                String desc = taskDesc(taskId, rawTaskTitle, "Complete Checkmark", langMap, warnings, langOut);
                yield "{type: \"checkmark\", task_id: \"" + taskId + "\"" +
                        (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "kill" -> {
                String entityRaw = t.contains("entity") ? t.getString("entity") : "minecraft:pig";
                String entityId = entityRaw.contains(":") ? entityRaw : "minecraft:" + entityRaw;
                long count = t.contains("value") ? t.getLong("value") : 1L;
                String desc = taskDesc(taskId, rawTaskTitle,
                        "Defeat " + entityId.substring(entityId.lastIndexOf(':') + 1).replace('_', ' '),
                        langMap, warnings, langOut);
                yield "{type: \"kill_entity\", task_id: \"" + taskId + "\", entity_id: \"" + entityId +
                        "\", required: " + (count <= 0 ? 1 : count) + (optional ? ", optional: true" : "") +
                        ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "xp" -> {
                long xpPoints = t.contains("value") ? t.getLong("value") : 0L;
                int levels = "levels".equalsIgnoreCase(t.getString("xp_type")) ?
                        (int) Math.max(1, xpPoints) : (int) Math.max(1, Math.round(Math.sqrt(xpPoints / 5.0)));
                String desc = taskDesc(taskId, rawTaskTitle, "Reach Level " + levels, langMap, warnings, langOut);
                yield "{type: \"experience\", task_id: \"" + taskId + "\", required_level: " + levels +
                        (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "advancement" -> {
                String advId = t.getString("advancement");
                if (advId.isEmpty()) yield fallbackCheckmark(taskId, "Unsupported advancement task");
                String desc = taskDesc(taskId, rawTaskTitle,
                        advId.substring(advId.lastIndexOf('/') + 1).replace('_', ' '), langMap, warnings, langOut);
                yield "{type: \"advancement\", task_id: \"" + taskId + "\", advancement_id: \"" + advId + "\"" +
                        (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "dimension" -> {
                String dimRaw = t.getString("dimension");
                if (dimRaw.isEmpty()) yield fallbackCheckmark(taskId, "Unsupported dimension task");
                String dimId = dimRaw.contains(":") ? dimRaw : "minecraft:" + dimRaw;
                String desc = taskDesc(taskId, rawTaskTitle,
                        "Travel to " + dimId.substring(dimId.lastIndexOf(':') + 1).replace('_', ' '), langMap, warnings,
                        langOut);
                yield "{type: \"dimension\", task_id: \"" + taskId + "\", target: \"" + dimId + "\"" +
                        (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "timer" -> {
                long ftbTicks = t.contains("time") ? t.getLong("time") : 6000L;
                int durationSeconds = (int) Math.max(1, ftbTicks / 20L);
                String desc = taskDesc(taskId, rawTaskTitle, "Wait " + durationSeconds + "s", langMap, warnings,
                        langOut);
                yield "{type: \"timer\", task_id: \"" + taskId + "\", duration_seconds: " + durationSeconds +
                        (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
            }
            case "custom" -> {

                ListTag customTags = t.getList("tags", Tag.TAG_STRING);
                Integer timerMinutes = null;
                for (int ti = 0; ti < customTags.size(); ti++) {
                    String tagVal = customTags.getString(ti);
                    if (tagVal.startsWith("moni_timer_")) {
                        try {
                            timerMinutes = Integer.parseInt(tagVal.substring("moni_timer_".length()));
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
                if (timerMinutes != null) {
                    int durationSeconds = Math.max(1, timerMinutes * 60);
                    String desc = taskDesc(taskId, rawTaskTitle, "Wait " + durationSeconds + "s", langMap, warnings,
                            langOut);
                    yield "{type: \"timer\", task_id: \"" + taskId + "\", duration_seconds: " + durationSeconds +
                            (optional ? ", optional: true" : "") + ", description: " + componentJsonSnbt(desc) + "}";
                }
                warnings.add("Task type 'custom' on " + questPath +
                        " has no PhoenixChronicles equivalent (pack-defined behavior); converted to checkmark.");
                String desc = taskDesc(taskId, rawTaskTitle, "Complete: " + rawTaskTitle, langMap, warnings, langOut);
                yield fallbackCheckmark(taskId, desc);
            }
            case "stat" -> {
                String statRaw = t.getString("stat");
                if (statRaw.isEmpty()) yield fallbackCheckmark(taskId, "Unsupported stat task");
                String statId = statRaw.contains(":") ? statRaw : "minecraft:" + statRaw;
                long target = t.contains("value") ? t.getLong("value") : 1L;
                String desc = taskDesc(taskId, rawTaskTitle,
                        "Increase " + statId.substring(statId.lastIndexOf(':') + 1).replace('_', ' '), langMap,
                        warnings, langOut);
                yield "{type: \"stat\", task_id: \"" + taskId + "\", stat_id: \"" + statId + "\", target: " +
                        (target <= 0 ? 1 : target) + ", consume: false" + (optional ? ", optional: true" : "") +
                        ", description: " + componentJsonSnbt(desc) + "}";
            }
            default -> {

                warnings.add("Task type '" + type + "' on " + questPath +
                        " has no PhoenixChronicles equivalent; converted to checkmark.");
                String desc = taskDesc(taskId, rawTaskTitle,
                        "Complete: " + (type.isEmpty() ? "unknown task" : type), langMap, warnings, langOut);
                yield fallbackCheckmark(taskId, desc);
            }
        };
    }

    private static String taskDesc(String taskId, String rawTitle, String fallback,
                                   Map<String, String> langMap, List<String> warnings, Map<String, String> langOut) {
        String desc = !rawTitle.isEmpty() ? resolveText(rawTitle, langMap, warnings, false) : fallback;
        langOut.put(taskLangKey(taskId), desc);
        return desc;
    }

    private static String taskLangKey(String taskId) {
        String path = taskId.contains(":") ? taskId.substring(taskId.indexOf(':') + 1) : taskId;
        return "phoenix_chronicles.task." + path.replace('/', '.');
    }

    private static String fallbackCheckmark(String taskId, String description) {
        return "{type: \"checkmark\", task_id: \"" + taskId + "\", description: " + componentJsonSnbt(description) +
                "}";
    }

    private static String convertItemTask(CompoundTag t, String taskId, boolean optional, String rawTaskTitle,
                                          Map<String, String> langMap, List<String> warnings,
                                          Map<String, String> langOut) {
        Tag itemTag = t.get("item");
        long count = t.contains("count") ? t.getLong("count") : 1L;

        String tagValue = findTagFilterValue(itemTag, 0);
        if (tagValue != null && !tagValue.isEmpty()) {
            String tagDesc = taskDesc(taskId, rawTaskTitle,
                    "Have tag " + tagValue.substring(tagValue.lastIndexOf(':') + 1).replace('_', ' '),
                    langMap, warnings, langOut);
            return "{type: \"tag_item\", task_id: \"" + taskId + "\", tag: \"" + tagValue + "\", required: " +
                    (count <= 0 ? 1 : count) + (optional ? ", optional: true" : "") + ", description: " +
                    componentJsonSnbt(tagDesc) + "}";
        }

        String itemId = extractItemId(itemTag);
        if (!itemId.isEmpty() && !itemId.equals("minecraft:air")) {
            String desc = taskDesc(taskId, rawTaskTitle, localizedItemName(itemId, langMap), langMap, warnings,
                    langOut);

            CompoundTag matchedEntry = findMatchedItemEntry(itemTag, 0);
            CompoundTag itemNbt = matchedEntry != null ?
                    extractItemNbt(matchedEntry, "Task " + taskId, warnings) : null;
            return "{type: \"item_check\", task_id: \"" + taskId + "\", item_id: \"" + itemId + "\"" +
                    ", count: " + (count <= 0 ? 1 : count) + ", consume: false" + (optional ? ", optional: true" : "") +
                    (itemNbt != null && !itemNbt.isEmpty() ? ", nbt_filter: " + itemNbt : "") +
                    ", description: " + componentJsonSnbt(desc) + "}";
        }

        warnings.add(
                "Task " + taskId + ": item filter had no resolvable concrete item or tag; converted to checkmark.");
        String desc = taskDesc(taskId, rawTaskTitle, "Complete Item Requirement", langMap, warnings, langOut);
        return fallbackCheckmark(taskId, desc);
    }

    private static String convertFluidTask(CompoundTag t, String taskId, boolean optional, String rawTaskTitle,
                                           Map<String, String> langMap, List<String> warnings,
                                           Map<String, String> langOut) {
        String fluidRaw = t.getString("fluid");
        String fluidId = fluidRaw.isEmpty() ? "" : (fluidRaw.contains(":") ? fluidRaw : "minecraft:" + fluidRaw);
        long amount = t.contains("amount") ? t.getLong("amount") : 1000L;

        if (fluidId.isEmpty() || fluidId.equals("minecraft:empty")) {
            warnings.add("Task " + taskId + ": fluid task had no resolvable fluid; converted to checkmark.");
            String desc = taskDesc(taskId, rawTaskTitle, "Complete Fluid Requirement", langMap, warnings, langOut);
            return fallbackCheckmark(taskId, desc);
        }

        String desc = taskDesc(taskId, rawTaskTitle,
                "Collect " + fluidId.substring(fluidId.lastIndexOf(':') + 1).replace('_', ' '), langMap, warnings,
                langOut);
        CompoundTag fluidNbt = t.contains("nbt", Tag.TAG_COMPOUND) ? t.getCompound("nbt") : null;
        long clampedAmount = Math.min(Integer.MAX_VALUE, Math.max(1, amount));
        return "{type: \"fluid_check\", task_id: \"" + taskId + "\", fluid_id: \"" + fluidId + "\"" +
                ", amount: " + clampedAmount + ", consume: false" + (optional ? ", optional: true" : "") +
                (fluidNbt != null && !fluidNbt.isEmpty() ? ", nbt_filter: " + fluidNbt : "") +
                ", description: " + componentJsonSnbt(desc) + "}";
    }

    private static Map<Long, CompoundTag> loadFtbRewardTables(Path outputDir) {
        Map<Long, CompoundTag> result = new HashMap<>();
        Path configRoot = outputDir.getParent();
        if (configRoot == null) return result;
        Path rewardTablesDir = configRoot.resolve("ftbquests").resolve("quests").resolve("reward_tables");
        if (!Files.isDirectory(rewardTablesDir)) return result;
        try (var stream = Files.list(rewardTablesDir)) {
            for (Path file : stream.filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt")).toList()) {
                try {
                    CompoundTag table = LenientSnbtParser.parse(Files.readString(file, StandardCharsets.UTF_8));
                    String idHex = table.getString("id");
                    if (idHex.isEmpty()) continue;
                    result.put(Long.parseUnsignedLong(idHex, 16), table);
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
        return result;
    }

    private record ChoiceOptions(ListTag options, String sourceLabel) {}

    private static ChoiceOptions resolveChoiceOptions(CompoundTag r, String questPath, List<String> warnings) {
        ListTag nested = r.getList("rewards", Tag.TAG_COMPOUND);
        String sourceLabel = "choice reward group";
        if (nested.isEmpty() && r.contains("table_id")) {

            CompoundTag table = ftbRewardTables.get(r.getLong("table_id"));
            if (table != null) {
                nested = table.getList("rewards", Tag.TAG_COMPOUND);
                sourceLabel = "reward table '" + table.getString("id") + "'";
            }
        }
        if (nested.isEmpty()) {
            warnings.add("Quest " + questPath + ": choice reward group had no resolvable rewards - dropped.");
            return null;
        }
        return new ChoiceOptions(nested, sourceLabel);
    }

    private static void convertReward(CompoundTag r, String questPath, List<String> warnings, List<String> out) {
        String type = r.getString("type");

        if (type.isEmpty() && r.contains("item")) type = "item";
        switch (type) {
            case "item" -> {
                String itemId = extractItemId(r.get("item"));
                if (itemId.isEmpty() || itemId.equals("minecraft:air")) itemId = r.getString("item");
                if (itemId.isEmpty() || itemId.equals("minecraft:air")) {
                    warnings.add("Quest " + questPath + ": item reward had no resolvable item id - dropped.");
                    return;
                }
                int count = r.contains("count") ? r.getInt("count") : 1;
                CompoundTag itemNbt = extractItemNbt(r.get("item"), "Quest " + questPath + " item reward", warnings,
                        false);
                out.add("{type: \"item\", item_id: \"" + itemId + "\", count: " + (count <= 0 ? 1 : count) +
                        (itemNbt != null && !itemNbt.isEmpty() ? ", nbt: " + itemNbt : "") + "}");
            }
            case "xp" -> {
                long xp = r.contains("xp") ? r.getLong("xp") : 0L;
                if (xp <= 0) {
                    warnings.add("Quest " + questPath + ": xp reward had value <= 0 - dropped.");
                    return;
                }
                out.add("{type: \"xp\", levels: " + ((int) Math.max(1, Math.round(Math.sqrt(xp / 5.0)))) + "}");
            }
            case "xp_levels" -> {
                long levels = r.contains("xp_levels") ? r.getLong("xp_levels") : 0L;
                if (levels <= 0) {
                    warnings.add("Quest " + questPath + ": xp_levels reward had value <= 0 - dropped.");
                    return;
                }
                out.add("{type: \"xp\", levels: " + levels + "}");
            }
            case "command" -> {
                String cmd = r.getString("command").trim();
                if (cmd.isEmpty()) {
                    warnings.add("Quest " + questPath + ": command reward had an empty command - dropped.");
                    return;
                }

                out.add("{type: \"command\", command: \"" + escape(cmd.startsWith("/") ? cmd.substring(1) : cmd) +
                        "\"}");
            }
            case "loot" -> {
                String table = r.getString("table");
                if (table.isEmpty()) {
                    warnings.add("Quest " + questPath + ": loot reward had no table id - dropped.");
                    return;
                }
                String mod = r.contains("table_mod") ? r.getString("table_mod") : "minecraft";
                out.add("{type: \"loot_table\", loot_table: \"" + mod + ":" + table + "\"}");
                warnings.add("Quest " + questPath + ": loot reward '" + mod + ":" + table +
                        "' imported as a live loot table reference - its contents are rolled fresh " +
                        "(with luck/looting bonuses) the moment each player claims the quest, not fixed at import.");
            }
            case "choice" -> {
                ChoiceOptions resolved = resolveChoiceOptions(r, questPath, warnings);
                if (resolved == null) return;

                warnings.add("Quest " + questPath + ": " + resolved.sourceLabel() +
                        " is mixed with other unconditional rewards on this quest, which has no single-choice " +
                        "equivalent - all " + resolved.options().size() +
                        " option(s) will be granted instead of picking one.");
                for (int i = 0; i < resolved.options().size(); i++)
                    convertReward(resolved.options().getCompound(i), questPath, warnings, out);
            }
            default -> warnings.add(
                    "Quest " + questPath + ": reward type '" + type +
                            "' has no PhoenixChronicles equivalent - dropped.");
        }
    }

    private static double numeric(Tag tag) {
        return (tag instanceof NumericTag n) ? n.getAsDouble() : 0.0;
    }

    private static String extractItemId(Tag tag) {
        String id = extractItemIdRecursive(tag, 0);
        return id == null || id.isEmpty() ? "minecraft:air" : id;
    }

    private static final Set<String> VOLATILE_NBT_KEY_SUBSTRINGS = Set.of(
            "energy", "fluid", "fuel", "charge", "power", "heat", "temperature",
            "damage", "durability", "repaircost", "cooldown", "timer", "ticks");

    private static CompoundTag extractItemNbt(Tag tag, String contextLabel, List<String> warnings) {
        return extractItemNbt(tag, contextLabel, warnings, true);
    }

    private static CompoundTag extractItemNbt(Tag tag, String contextLabel, List<String> warnings,
                                              boolean stripVolatile) {
        if (tag == null || tag.getId() != Tag.TAG_COMPOUND) return null;
        CompoundTag ct = (CompoundTag) tag;
        if (!ct.contains("tag", Tag.TAG_COMPOUND)) return null;
        CompoundTag raw = ct.getCompound("tag");
        if (raw.isEmpty()) return null;

        if (!stripVolatile) return raw.copy();

        CompoundTag filtered = new CompoundTag();
        List<String> stripped = new ArrayList<>();
        for (String key : raw.getAllKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            boolean volatileKey = VOLATILE_NBT_KEY_SUBSTRINGS.stream().anyMatch(lower::contains);
            if (volatileKey) {
                stripped.add(key);
            } else {
                filtered.put(key, raw.get(key).copy());
            }
        }
        if (!stripped.isEmpty() && warnings != null) {
            warnings.add(contextLabel + ": excluded volatile-looking NBT key(s) " + stripped +
                    " from the item NBT filter - they look like per-instance state (energy/fuel/damage/etc.) " +
                    "that would never exactly match again, rather than the item's stable identity.");
        }
        return filtered.isEmpty() ? null : filtered;
    }

    private static String extractItemIdRecursive(Tag tag, int depth) {
        if (tag == null || depth > 8) return null;
        if (tag.getId() == Tag.TAG_STRING) {
            String s = tag.getAsString();
            return s.isEmpty() ? null : s;
        }
        if (tag.getId() != Tag.TAG_COMPOUND) return null;

        CompoundTag ct = (CompoundTag) tag;
        String id = ct.getString("id");

        switch (id) {
            case "itemfilters:and", "itemfilters:or" -> {
                if (!ct.contains("tag")) return null;
                ListTag items = ct.getCompound("tag").getList("items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    String found = extractItemIdRecursive(items.getCompound(i), depth + 1);
                    if (found != null) return found;
                }
                return null;
            }

            case "itemfilters:tag", "itemfilters:id_regex", "itemfilters:not", "itemfilters:block", "itemfilters:mod", "itemfilters:list" -> {
                return null;
            }
            default -> {
                return id.isEmpty() ? null : id;
            }
        }
    }

    private static CompoundTag findMatchedItemEntry(Tag tag, int depth) {
        if (tag == null || depth > 8 || tag.getId() != Tag.TAG_COMPOUND) return null;
        CompoundTag ct = (CompoundTag) tag;
        String id = ct.getString("id");

        switch (id) {
            case "itemfilters:and", "itemfilters:or" -> {
                if (!ct.contains("tag")) return null;
                ListTag items = ct.getCompound("tag").getList("items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag found = findMatchedItemEntry(items.getCompound(i), depth + 1);
                    if (found != null) return found;
                }
                return null;
            }

            case "itemfilters:tag", "itemfilters:id_regex", "itemfilters:not", "itemfilters:block", "itemfilters:mod", "itemfilters:list" -> {
                return null;
            }
            default -> {
                return id.isEmpty() ? null : ct;
            }
        }
    }

    private static String firstItemInTag(String tagId) {
        try {
            var tag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse(tagId));
            var iter = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();
            if (iter.hasNext()) {
                net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(iter.next().value());
                return id != null ? id.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String findTagFilterValue(Tag tag, int depth) {
        if (tag == null || depth > 8 || tag.getId() != Tag.TAG_COMPOUND) return null;
        CompoundTag ct = (CompoundTag) tag;
        String id = ct.getString("id");
        if ("itemfilters:tag".equals(id)) {
            return ct.contains("tag") ? ct.getCompound("tag").getString("value") : null;
        }
        if (("itemfilters:and".equals(id) || "itemfilters:or".equals(id)) && ct.contains("tag")) {
            ListTag items = ct.getCompound("tag").getList("items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                String found = findTagFilterValue(items.getCompound(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String buildDescription(ListTag lines, Map<String, String> langMap, List<String> warnings) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.getString(i);
            if (line.startsWith("{@pagebreak}")) {

                if (sb.length() > 0) sb.append("\n\n---\n\n");
                continue;
            }
            if (line.startsWith("{image:")) {
                String inner = line.substring(1, line.length() - 1);
                String imgBody = normalizeImageBody(inner.substring("image:".length()));
                if (sb.length() > 0) sb.append("\n");
                sb.append("[img:").append(imgBody).append("]");
                continue;
            }
            line = resolveText(line, langMap, warnings, true).trim();
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }

        String result = sb.toString();
        return result.replaceAll("^\n+", "").replaceAll("\n+$", "");
    }

    private static String normalizeImageBody(String raw) {
        raw = raw.trim();
        int spaceIdx = raw.indexOf(' ');
        if (spaceIdx < 0) return raw;
        String rl = raw.substring(0, spaceIdx);
        Integer w = null, h = null;
        for (String token : raw.substring(spaceIdx + 1).split("\\s+")) {
            int c = token.indexOf(':');
            if (c < 0) continue;
            String key = token.substring(0, c);
            String val = token.substring(c + 1);
            try {
                if ("width".equalsIgnoreCase(key)) w = (int) Double.parseDouble(val);
                else if ("height".equalsIgnoreCase(key)) h = (int) Double.parseDouble(val);
            } catch (NumberFormatException ignored) {}
        }
        return (w != null && h != null) ? rl + "," + w + "," + h : rl;
    }

    private static String mapShape(String ftb) {
        return switch (ftb == null ? "" : ftb.toLowerCase(Locale.ROOT)) {
            case "gear" -> "STAR";
            case "circle" -> "CIRCLE";
            case "diamond" -> "DIAMOND";
            case "hexagon" -> "HEXAGON";
            case "pentagon" -> "PENTAGON";
            default -> "SQUARE";
        };
    }

    private static String uniquePath(String hexId, String title, Set<String> usedPaths) {
        String base = titleSlug(title);
        if (base.isEmpty()) base = "q_" + hexId.toLowerCase(Locale.ROOT);
        if (!usedPaths.contains(base)) return base;
        String candidate = base + "_" + hexId.substring(0, Math.min(4, hexId.length())).toLowerCase(Locale.ROOT);
        int n = 2;
        while (usedPaths.contains(candidate)) candidate = base + "_" + (n++);
        return candidate;
    }

    private static String shortId(String hexId) {
        return hexId == null || hexId.isEmpty() ? "unknown" :
                hexId.substring(0, Math.min(6, hexId.length())).toLowerCase(Locale.ROOT);
    }

    private static String titleSlug(String title) {
        if (title == null || title.isBlank()) return "";
        String s = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
        return s.length() > 48 ? s.substring(0, 48).replaceAll("_+$", "") : s;
    }

    private static String slugify(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String stripForSlug(String displayText) {
        if (displayText == null) return "";
        return displayText.replaceAll("§.", "")
                .replaceAll("\\{#[0-9A-Fa-f]{6}\\}", "")
                .replaceAll("(?i)\\{reset}", "")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1").trim();
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isBlank()) return "Imported Chapter";
        String[] words = raw.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (w.length() > 1 && w.equals(w.toUpperCase())) {
                sb.append(w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.length() > 1 ? w.substring(1) : "");
            }
        }
        return sb.length() == 0 ? "Imported Chapter" : sb.toString();
    }

    private static boolean isUsableTitle(String text) {
        if (text == null || text.isBlank()) return false;
        return !LANG_KEY.matcher(text.trim()).matches();
    }

    private static String tagAsString(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return "";
        Tag value = tag.get(key);
        if (value == null) return "";
        if (value instanceof StringTag) return value.getAsString();
        if (value instanceof NumericTag num) return String.valueOf(num.getAsLong());
        return value.getAsString();
    }

    private static String firstUsable(String... candidates) {
        for (String c : candidates) {
            if (isUsableTitle(c)) return c;
        }
        return candidates.length > 0 ? candidates[candidates.length - 1] : "";
    }

    private static Map<String, String> buildLangMap(Path importDir) {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(importDir)) return map;

        loadLangJsonsFrom(importDir, map, false);

        Path root = importDir;
        for (int i = 0; i < 5 && root != null; i++) {
            root = root.getParent();
            if (root == null || !Files.isDirectory(root)) break;
            loadLangJsonsFrom(root, map, true);

            Path modsDir = root.resolve("mods");
            if (Files.isDirectory(modsDir)) loadLangFromModJars(modsDir, map);
        }

        return map;
    }

    private static void loadLangFromModJars(Path modsDir, Map<String, String> map) {
        try (Stream<Path> jars = Files.list(modsDir)) {
            for (Path jar : jars.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .toList()) {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar.toFile())) {
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith("assets/") || !name.endsWith("/lang/en_us.json")) continue;
                        try (java.io.InputStream is = zf.getInputStream(entry)) {
                            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            JsonElement el = JsonParser.parseString(raw);
                            if (el.isJsonObject()) {
                                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                                    if (e.getValue().isJsonPrimitive() &&
                                            e.getValue().getAsJsonPrimitive().isString()) {
                                        map.putIfAbsent(e.getKey(), e.getValue().getAsString());
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static final Set<String> LANG_SCAN_SKIP_DIRS = Set.of(
            "mods", "saves", "logs", "screenshots", "crash-reports", "libraries",
            ".git", ".gradle", "build", "cache", "backups", "world");

    private static void loadLangJsonsFrom(Path root, Map<String, String> map, boolean requireLangSegment) {
        try (Stream<Path> walk = Files.walk(root, 10)) {
            List<Path> jsonFiles = walk
                    .filter(p -> {
                        for (Path segment : root.relativize(p)) {
                            if (LANG_SCAN_SKIP_DIRS.contains(segment.toString().toLowerCase(Locale.ROOT))) return false;
                        }
                        return true;
                    })
                    .filter(Files::isRegularFile)

                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("en_us.json"))
                    .filter(p -> !requireLangSegment || hasLangSegment(root, p))
                    .toList();
            for (Path p : jsonFiles) {
                try {
                    String raw = Files.readString(p, StandardCharsets.UTF_8);
                    JsonElement el = JsonParser.parseString(raw);
                    if (el.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                                map.put(e.getKey(), e.getValue().getAsString());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static boolean hasLangSegment(Path root, Path file) {
        for (Path segment : root.relativize(file)) {
            if (segment.toString().equalsIgnoreCase("lang")) return true;
        }
        return false;
    }

    public static String resolveText(String raw, Map<String, String> langMap, List<String> warnings,
                                     boolean richCapable) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";

        if (trimmed.startsWith("[") || (trimmed.startsWith("{") && trimmed.contains("\""))) {
            try {

                return flattenComponent(JsonParser.parseString(trimmed), langMap, warnings, richCapable).trim();
            } catch (Exception ignored) {}
        }

        Matcher m = LANG_KEY.matcher(trimmed);
        if (m.matches()) {
            String key = m.group(1);
            String resolved = langMap.get(key);
            if (resolved != null) return convertFormatting(resolved, richCapable).trim();
            warnings.add("Unresolved lang key: " + key);
        }
        return convertFormatting(raw, richCapable).trim();
    }

    private static String flattenComponent(JsonElement el, Map<String, String> langMap, List<String> warnings,
                                           boolean richCapable) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return convertFormatting(el.getAsString(), richCapable);
        if (el.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : el.getAsJsonArray())
                sb.append(flattenComponent(child, langMap, warnings, richCapable));
            return sb.toString();
        }
        if (!el.isJsonObject()) return "";
        JsonObject obj = el.getAsJsonObject();

        String text = "";
        if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) {
            String key = obj.get("translate").getAsString();
            String resolved = langMap.get(key);
            if (resolved != null) text = resolved;
            else {
                warnings.add("Unresolved lang key: " + key);
                text = key;
            }
        } else if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
            text = obj.get("text").getAsString();
        }
        return convertFormatting(text, richCapable);
    }

    public static String convertFormatting(String s, boolean richCapable) {
        if (s == null) return "";

        String out = richCapable ? s.replaceAll("&#([0-9A-Fa-f]{6})", "{#$1}") : s.replaceAll("&#([0-9A-Fa-f]{6})", "");
        return out.replaceAll("(?i)&([0-9a-fk-or])", "§$1").replace(">?", "");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String componentJsonSnbt(String plainText) {
        JsonObject obj = new JsonObject();
        obj.addProperty("text", plainText == null ? "" : plainText);
        return "\"" + escape(obj.toString()) + "\"";
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append("    ").append(key).append(": \"").append(value).append("\",\n");
    }
}
