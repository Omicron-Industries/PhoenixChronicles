package net.phoenixvine.chronicles.capability.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
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

/**
 * Converts FTB Quests chapter SNBT files into organized PhoenixCore configurations.
 * Creates a category subfolder for each chapter under outputDir/quests/,
 * a master category JSON file, and extracts individual quest SNBT files cleanly.
 *
 * IMPORT IS TWO-PASS:
 * Pass 1 (index): parse every chapter file, assign every quest a STABLE category
 * (derived from filename, not from a possibly-unresolved localized
 * title) and a title-based path, and record it in a GLOBAL registry.
 * Pass 2 (write): re-walk each chapter and write files, resolving dependencies
 * against the GLOBAL registry so cross-chapter prerequisites
 * (which are extremely common in FTB Quests "groups") resolve
 * correctly instead of silently vanishing.
 */
public class FtbQuestsImporter {

    private static final float COORD_SCALE = 80f;
    private static final float COORD_PADDING = 2.0f; // grid units of breathing room before scaling
    private static final Pattern LANG_KEY = Pattern.compile("^\\{([A-Za-z0-9_.-]+)}$");

    public record ImportResult(int imported, int skipped, String category, List<String> warnings) {}

    /** Where a given FTB quest id ended up living once imported. */
    private record QuestLoc(String category, String path) {}

    /**
     * A "quest_links" entry - FTB's mechanism for showing a quest defined in one chapter as a
     * lightweight placeholder node at a different position, often in a different chapter/category.
     * Resolved against the GLOBAL index in pass 2 since the target may live in a chapter that
     * hasn't been indexed yet.
     */
    private record LinkStub(String linkFtbId, String path, String linkedFtbId, double x, double y, String shape) {}

    /** Everything pass 1 figured out about one chapter, cached so pass 2 doesn't re-parse. */
    private record ChapterIndex(
                                Path file,
                                CompoundTag chapter,
                                String categorySlug,
                                String displayTitle,
                                Map<String, String> idToPath,   // ftbId -> path, scoped to this chapter's own quests
                                List<LinkStub> linkStubs,
                                double minX,
                                double minY) {}

    // ── Main Entry-Point ─────────────────────────────────────────────────────

    public static ImportResult importDirectory(Path importDir, Path outputDir) {
        Path cleanImportDir = importDir.toAbsolutePath().normalize();
        System.out.println("[PhoenixCore] Target Import Directory: " + cleanImportDir);

        if (!Files.exists(cleanImportDir)) {
            System.err.println("[PhoenixCore] ERROR: Import directory does not physically exist!");
            return new ImportResult(0, 0, "", List.of("Import dir not found: " + cleanImportDir));
        }

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
            System.err.println("[PhoenixCore] CRITICAL: " + critError);
            warnings.add(critError);
            return new ImportResult(0, 0, "", warnings);
        }
        System.out.println("[PhoenixCore] Found " + snbtFiles.size() + " chapter file(s).");

        // ── Pass 1: index every chapter and every quest into a GLOBAL registry ──
        List<ChapterIndex> chapters = new ArrayList<>();
        Map<String, QuestLoc> globalIndex = new HashMap<>();
        // Shared across ALL chapters: the final quest id is "phoenixcore:<path>" with no
        // chapter/category prefix, so slug uniqueness must be enforced pack-wide - not per
        // chapter - or two chapters with an identically-named quest (e.g. two "Compressor"
        // quests in different tiers) silently collide onto the same id and one vanishes.
        Set<String> globalUsedPaths = new HashSet<>();

        for (Path file : snbtFiles) {
            String fileName = file.getFileName().toString();
            String fallbackTitle = fileName.substring(0, fileName.length() - 5);
            try {
                String snbt = Files.readString(file, StandardCharsets.UTF_8);
                // FTB Quests writes chapter files via NightConfig's comma-optional dialect, which is
                // NOT legal SNBT by vanilla's grammar. Use the lenient parser instead of TagParser so
                // a chapter isn't silently dropped whole just because it lacks commas between entries.
                CompoundTag chapter = LenientSnbtParser.parse(snbt);
                ChapterIndex idx = indexChapter(chapter, file, fallbackTitle, langMap, warnings, globalUsedPaths);
                chapters.add(idx);
                for (Map.Entry<String, String> e : idx.idToPath().entrySet()) {
                    globalIndex.put(e.getKey(), new QuestLoc(idx.categorySlug(), e.getValue()));
                }
                // A quest_link's own id is a legitimate dependency target too - other quests in
                // FTB (in this chapter or elsewhere) can list a link's id as one of THEIR
                // "dependencies" rather than the real quest's id, and that arrow should terminate
                // at the stub's position (matching FTBQ's own behavior) instead of silently
                // failing to resolve. Without this, those prerequisite lines just vanish on import.
                for (LinkStub link : idx.linkStubs()) {
                    globalIndex.put(link.linkFtbId(), new QuestLoc(idx.categorySlug(), link.path()));
                }
            } catch (Exception e) {
                String errorMsg = "Failed to index " + fileName + ": " + e.getMessage();
                System.err.println("[PhoenixCore] " + errorMsg);
                warnings.add(errorMsg);
            }
        }

        // ── Pass 2: write every chapter now that cross-chapter ids are known ──
        int totalImported = 0, totalSkipped = 0;
        String lastCat = "";
        // Every imported quest's title/description/subtitle gets a matching lang key here too,
        // regardless of whether the source pack used translate keys or bare literal text - so
        // a pack that "translates nothing" still comes out the other end fully re-translatable
        // via lang/en_us.json instead of only ever showing whatever baked text (or, if a lang
        // key genuinely couldn't be resolved, the raw key string) ended up in the SNBT.
        Map<String, String> langOut = new LinkedHashMap<>();
        for (ChapterIndex idx : chapters) {
            try {
                ImportResult r = writeChapter(idx, outputDir, globalIndex, langMap, warnings, langOut);
                totalImported += r.imported();
                totalSkipped += r.skipped();
                if (!r.category().isEmpty()) lastCat = r.category();
                System.out.println("[PhoenixCore] Wrote chapter: " + idx.file().getFileName() + " -> quests/" +
                        idx.categorySlug().toLowerCase(Locale.ROOT) + " (" + r.imported() + " quests, " + r.skipped() +
                        " skipped)");
            } catch (Exception e) {
                String errorMsg = "Failed to write " + idx.file().getFileName() + ": " + e.getMessage();
                System.err.println("[PhoenixCore] " + errorMsg);
                warnings.add(errorMsg);
                totalSkipped++;
            }
        }

        net.phoenixvine.chronicles.registry.QuestLangRegistry.mergeWrite(outputDir, langOut);
        if (!langOut.isEmpty()) warnings.add("Wrote " + langOut.size() + " lang key(s) to lang/en_us.json.");

        System.out.println(
                "[PhoenixCore] Import Finished. Total Imported: " + totalImported + ", Total Skipped: " + totalSkipped);
        return new ImportResult(totalImported, totalSkipped, lastCat, warnings);
    }

    // ── Pass 1: Indexing ─────────────────────────────────────────────────────

    private static ChapterIndex indexChapter(CompoundTag chapter, Path file, String fallbackTitle,
                                             Map<String, String> langMap, List<String> warnings,
                                             Set<String> globalUsedPaths) {
        // Category identity is ALWAYS derived from the stable filename, never from a
        // localized title that might fail to resolve. This is what actually goes on
        // disk as the folder name / category id.
        String categorySlug = slugify(fallbackTitle).toUpperCase(Locale.ROOT);
        if (categorySlug.isEmpty()) categorySlug = "IMPORTED";

        // Display name (what shows up in-game) prefers the resolved lang-key title,
        // and falls back to a human-readable version of the filename ("the_factory"
        // -> "The Factory") rather than either raw filename text or a broken "{...}"
        // lang key literal.
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
                // A single malformed quest entry must never sink indexing for the whole chapter -
                // that would silently drop every OTHER quest in the file too.
                warnings.add("Chapter " + file.getFileName() + ": quest #" + i + " failed to index (" + e.getMessage() +
                        ") - skipped.");
            }
        }

        // "quest_links" - lightweight placeholders showing a quest defined elsewhere (often a
        // different chapter/category) at a position within THIS chapter. The real quest may not
        // be indexed yet at this point (chapters are processed in directory order), so only the
        // stub's own path is assigned here; resolving linked_quest happens in pass 2 against the
        // now-complete global index.
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
                linkStubs.add(new LinkStub(linkFtbId, path, linkedFtbId, x, y, shape));
            } catch (Exception e) {
                warnings.add("Chapter " + file.getFileName() + ": quest_links #" + i + " failed to index (" +
                        e.getMessage() + ") - skipped.");
            }
        }

        return new ChapterIndex(file, chapter, categorySlug, displayTitle, idToPath, linkStubs, minX, minY);
    }

    // ── Pass 2: Writing ──────────────────────────────────────────────────────

    private static ImportResult writeChapter(ChapterIndex idx, Path outputDir, Map<String, QuestLoc> globalIndex,
                                             Map<String, String> langMap, List<String> warnings,
                                             Map<String, String> langOut) throws IOException {
        CompoundTag chapter = idx.chapter();
        ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);
        if (quests.isEmpty() && idx.linkStubs().isEmpty()) return new ImportResult(0, 0, idx.categorySlug(), List.of());

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
                String nodeSnbt = convertQuest(q, idx, globalIndex, langMap, warnings, langOut);
                Path outFile = categoryFolder.resolve(path + ".snbt");
                Files.writeString(outFile, nodeSnbt, StandardCharsets.UTF_8);
                imported++;
            } catch (Exception e) {
                warnings.add("Quest " + ftbId + " (" + path + "): " + e.getMessage());
                skipped++;
            }
        }

        // quest_links - write each as a lightweight stub node pointing at the real quest,
        // now that every chapter has been indexed and the target is guaranteed resolvable.
        for (LinkStub link : idx.linkStubs()) {
            try {
                QuestLoc target = globalIndex.get(link.linkedFtbId());
                if (target == null) {
                    warnings.add("Chapter " + idx.file().getFileName() + ": quest link " + link.path() + " points at " +
                            link.linkedFtbId() + ", which was not found in any imported chapter - dropped.");
                    skipped++;
                    continue;
                }
                String linkSnbt = convertLinkStub(link, idx, target);
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

    /** A quest_links entry - a lightweight placeholder node with no tasks, pointing at the real quest. */
    private static String convertLinkStub(LinkStub link, ChapterIndex idx, QuestLoc target) {
        StringBuilder sb = new StringBuilder("{\n");
        append(sb, "id", link.path());
        // Display info (title/icon/state) is resolved from the target node at render time -
        // the stub's own title is never actually shown, just needs to be non-empty.
        append(sb, "title", "Linked Quest");
        append(sb, "category", idx.categorySlug());
        append(sb, "shape", mapShape(link.shape()));

        int px = (int) Math.round((link.x() - idx.minX() + COORD_PADDING) * COORD_SCALE);
        int py = (int) Math.round((link.y() - idx.minY() + COORD_PADDING) * COORD_SCALE);
        sb.append("    positionX: ").append(px).append(",\n");
        sb.append("    positionY: ").append(py).append(",\n");
        append(sb, "link_target", "phoenixcore:" + target.path());

        sb.append("}");
        return sb.toString();
    }

    // ── Registry Generation ──────────────────────────────────────────────────

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

    // ── Quest Conversion ──────────────────────────────────────────────────────

    private static String convertQuest(CompoundTag q, ChapterIndex idx, Map<String, QuestLoc> globalIndex,
                                       Map<String, String> langMap, List<String> warnings,
                                       Map<String, String> langOut) {
        StringBuilder sb = new StringBuilder("{\n");
        String ftbId = q.getString("id");
        String path = idx.idToPath().get(ftbId);

        append(sb, "id", path);

        String rawTitle = q.contains("title") ? q.get("title").getAsString() : "";
        String title = resolveText(rawTitle, langMap, warnings, false);

        String rawSubtitle = q.contains("subtitle") ? q.get("subtitle").getAsString() : "";
        String subtitle = resolveText(rawSubtitle, langMap, warnings, false);

        // Never leave a quest unnamed: title -> first item task's item name -> id. Subtitle is
        // deliberately NOT in this fallback chain - it used to be tried right after title, which
        // meant every title-less quest (a real, common FTBQ pattern: no "title" key at all, just
        // an icon + a subtitle, which FTBQ itself renders by deriving a display name from the
        // icon) had its subtitle text silently consumed as the quest's TITLE instead, and the
        // "write it as a separate subtitle" check below then always failed since resolvedTitle
        // WAS that same subtitle text - the subtitle just vanished. A subtitle should never be
        // asked to stand in for a missing title; it's a fine standalone thing to lose title-less
        // quests to the item-name fallback for, same as any other title-less quest.
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

        append(sb, "category", idx.categorySlug());
        append(sb, "shape", mapShape(q.getString("shape")));

        double rawX = numeric(q.get("x"));
        double rawY = numeric(q.get("y"));
        int px = (int) Math.round((rawX - idx.minX() + COORD_PADDING) * COORD_SCALE);
        int py = (int) Math.round((rawY - idx.minY() + COORD_PADDING) * COORD_SCALE);
        sb.append("    positionX: ").append(px).append(",\n");
        sb.append("    positionY: ").append(py).append(",\n");

        String iconId = q.contains("icon") ? extractItemId(q.get("icon")) : "";
        if (iconId.isEmpty() || iconId.equals("minecraft:air")) {
            // FTB Quests itself falls back to the first required item's texture when a quest
            // has no explicit icon set - match that instead of leaving the node with no icon.
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
                // Resolve against the GLOBAL registry so cross-chapter prerequisites work.
                QuestLoc loc = globalIndex.get(depFtbId);
                if (loc == null) {
                    warnings.add("Quest " + ftbId + ": dependency " + depFtbId +
                            " was not found in any imported chapter (likely outside this import batch) - dropped.");
                    continue;
                }
                depSb.append("        {id: \"").append(loc.path()).append("\", category: \"")
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
            List<String> rewardSnbts = new ArrayList<>();
            for (int i = 0; i < ftbRewards.size(); i++) {
                convertReward(ftbRewards.getCompound(i), path, warnings, rewardSnbts);
            }
            if (!rewardSnbts.isEmpty()) {
                sb.append("    rewards: [\n");
                for (String rewardSnbt : rewardSnbts) sb.append("        ").append(rewardSnbt).append(",\n");
                sb.append("    ],\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /** When a quest has no usable title/subtitle, try naming it after its first concrete item task. */
    private static String itemBasedFallbackTitle(CompoundTag q, Map<String, String> langMap, List<String> warnings) {
        String itemId = firstTaskItemId(q);
        if (itemId.isEmpty()) return "";
        return "Obtain " + itemId.substring(itemId.lastIndexOf(':') + 1).replace('_', ' ');
    }

    /**
     * First concrete item id among this quest's "item"-type tasks, or "" if none. Falls back to
     * the first item IN a tag filter when the task has no concrete item (e.g. "any iron ingot")
     * rather than returning nothing - same fix as TagItemTask/FilterItemTask's own icon
     * resolution, applied here too since this is what actually picks the QUEST NODE's icon
     * (not just a task's), and previously left tag-only quests with no icon at all.
     */
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

    // ── Task Conversion ───────────────────────────────────────────────────────

    private static String convertTask(CompoundTag t, String questPath, int idx,
                                      Map<String, String> langMap, List<String> warnings,
                                      Map<String, String> langOut) {
        String type = t.getString("type");
        String taskId = "phoenixcore:" + questPath + "_task_" + idx;
        boolean optional = t.getBoolean("optional_task");

        String rawTaskTitle = t.contains("title") ? t.get("title").getAsString() : "";

        return switch (type) {
            case "item" -> convertItemTask(t, taskId, optional, rawTaskTitle, langMap, warnings, langOut);
            case "checkmark" -> {
                String desc = taskDesc(taskId, rawTaskTitle, "Complete Checkmark", langMap, warnings, langOut);
                yield "{type: \"checkmark\", task_id: \"" + taskId + "\", description: " + componentJsonSnbt(desc) +
                        "}";
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
                // Unknown/unsupported FTB task type (e.g. "observation"). Don't silently drop it -
                // a quest that loses ALL of its tasks can end up auto-completable or malformed.
                // Fall back to a checkmark so the quest still requires manual completion.
                warnings.add("Task type '" + type + "' on " + questPath +
                        " has no PhoenixCore equivalent; converted to checkmark.");
                String desc = taskDesc(taskId, rawTaskTitle,
                        "Complete: " + (type.isEmpty() ? "unknown task" : type), langMap, warnings, langOut);
                yield fallbackCheckmark(taskId, desc);
            }
        };
    }

    /**
     * Resolves this task's description text (same as {@link #resolveText} would) AND records it
     * as a lang key keyed by the task's own stable {@code task_id} - not index-within-quest like
     * the quest-level title/description keys, since task order can be reordered/insert-shifted
     * by a packdev later, which would silently misattach an existing translation to the wrong
     * task if the key were index-based.
     */
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

        // A pure "itemfilters:tag" (require any item with a given tag) still gets its own
        // dedicated tag-based task, wherever it appears (top-level OR nested in and/or).
        String tagValue = findTagFilterValue(itemTag, 0);
        if (tagValue != null && !tagValue.isEmpty()) {
            String tagDesc = taskDesc(taskId, rawTaskTitle,
                    "Have tag " + tagValue.substring(tagValue.lastIndexOf(':') + 1).replace('_', ' '),
                    langMap, warnings, langOut);
            return "{type: \"tag_item\", task_id: \"" + taskId + "\", tag: \"" + tagValue + "\", required: " +
                    (count <= 0 ? 1 : count) + (optional ? ", optional: true" : "") + ", description: " +
                    componentJsonSnbt(tagDesc) + "}";
        }

        // Otherwise try to find a real, concrete item id anywhere in the filter tree
        // (handles itemfilters:and/or by recursing into their nested item lists instead
        // of emitting the literal string "itemfilters:and"/"itemfilters:or" as an item id).
        String itemId = extractItemId(itemTag);
        if (!itemId.isEmpty() && !itemId.equals("minecraft:air")) {
            String desc = taskDesc(taskId, rawTaskTitle,
                    itemId.substring(itemId.lastIndexOf(':') + 1).replace('_', ' '), langMap, warnings, langOut);
            return "{type: \"item_check\", task_id: \"" + taskId + "\", item_id: \"" + itemId + "\"" +
                    ", count: " + (count <= 0 ? 1 : count) + ", consume: false" + (optional ? ", optional: true" : "") +
                    ", description: " + componentJsonSnbt(desc) + "}";
        }

        // Pure regex/negation filter with no resolvable concrete item (e.g. an id_regex
        // matching "any MV-tier hatch"). We can't faithfully replicate arbitrary regex
        // matching, so fall back to a checkmark rather than writing an invalid item id
        // that would fail to resolve at load time.
        warnings.add(
                "Task " + taskId + ": item filter had no resolvable concrete item or tag; converted to checkmark.");
        String desc = taskDesc(taskId, rawTaskTitle, "Complete Item Requirement", langMap, warnings, langOut);
        return fallbackCheckmark(taskId, desc);
    }

    /**
     * Converts one FTB reward into zero or more PhoenixCore reward SNBT strings, appended to
     * {@code out}. Unlike task conversion (which always falls back to a checkmark), a reward
     * with no equivalent genuinely has nothing sane to fall back to - so instead of silently
     * dropping it (the old behavior, which made rewards vanish with zero trace), it now always
     * either converts or logs a warning explaining exactly what got skipped and why.
     */
    private static void convertReward(CompoundTag r, String questPath, List<String> warnings, List<String> out) {
        String type = r.getString("type");
        switch (type) {
            case "item" -> {
                String itemId = extractItemId(r.get("item"));
                if (itemId.isEmpty() || itemId.equals("minecraft:air")) itemId = r.getString("item");
                if (itemId.isEmpty() || itemId.equals("minecraft:air")) {
                    warnings.add("Quest " + questPath + ": item reward had no resolvable item id - dropped.");
                    return;
                }
                int count = r.contains("count") ? r.getInt("count") : 1;
                out.add("{type: \"item\", item_id: \"" + itemId + "\", count: " + (count <= 0 ? 1 : count) + "}");
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
                // Was wrapping the command text in an extra pair of literal escaped quotes
                // (\" ... \"), which SNBT decodes into ACTUAL quote characters baked into the
                // stored command string itself (e.g. `"give @s diamond"` instead of
                // `give @s diamond`) - Minecraft's command dispatcher then fails on the leading
                // `"`, silently breaking every imported command reward.
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
            }
            case "choice" -> {
                // FTB's "pick one of these" reward group. PhoenixCore has no per-reward-group
                // choice mechanic (only a whole-quest reward_choice_count), so the closest
                // faithful behavior is to grant every option rather than silently dropping the
                // whole group - flatten its nested rewards instead of losing them outright.
                ListTag nested = r.getList("rewards", Tag.TAG_COMPOUND);
                if (nested.isEmpty()) {
                    warnings.add("Quest " + questPath + ": choice reward group had no nested rewards - dropped.");
                    return;
                }
                warnings.add("Quest " + questPath + ": choice reward group has no single-choice equivalent - " +
                        "all " + nested.size() + " option(s) will be granted instead of picking one.");
                for (int i = 0; i < nested.size(); i++) convertReward(nested.getCompound(i), questPath, warnings, out);
            }
            default -> warnings.add(
                    "Quest " + questPath + ": reward type '" + type + "' has no PhoenixCore equivalent - dropped.");
        }
    }

    // ── Helper API Layers ─────────────────────────────────────────────────────

    private static double numeric(Tag tag) {
        return (tag instanceof NumericTag n) ? n.getAsDouble() : 0.0;
    }

    /** Finds the first concrete (non-filter) item id anywhere in an item/itemfilters tree. */
    private static String extractItemId(Tag tag) {
        String id = extractItemIdRecursive(tag, 0);
        return id == null || id.isEmpty() ? "minecraft:air" : id;
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
            // These filter types never point at a single concrete item - the caller
            // should fall back to a tag-based task or a checkmark instead.
            case "itemfilters:tag", "itemfilters:id_regex", "itemfilters:not", "itemfilters:block", "itemfilters:mod", "itemfilters:list" -> {
                return null;
            }
            default -> {
                return id.isEmpty() ? null : id;
            }
        }
    }

    /** Resolves a tag id (e.g. "c:ingots/iron") to the first concrete item registered in it. */
    private static String firstItemInTag(String tagId) {
        try {
            var tag = net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation(tagId));
            var iter = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();
            if (iter.hasNext()) {
                net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(iter.next().value());
                return id != null ? id.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Recursively searches an item/itemfilters tree for the first itemfilters:tag value. */
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
                // FTBQ's own "insert a bigger break here" marker. Maps onto Chronicles' own page
                // break syntax (a lone "---" line - see ChronicleMarkdownParser/QuestTasksScreen's
                // splitDescPages) instead of just a bigger visual gap within one continuous
                // scroll: FTBQ authors placed these deliberately to split content into distinct
                // pages, so an import should actually turn into real pagination, not merely read
                // as "a bit more spacing than usual" while still being one long scroll.
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
        // Trim leading/trailing blank lines only - blank lines in the middle are the
        // paragraph-break separators FTBQ authors relied on.
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

    /**
     * Turns "the_factory" / "some-thing" into "The Factory" / "Some Thing" - used only as a
     * fallback when a chapter has no usable title in the file itself, so this reads a filename.
     * Words that are ALREADY fully uppercase (2+ letters) are left exactly as-is instead of
     * being title-cased, so tech-tier acronyms like "UHV"/"LuV" in a filename survive intact
     * rather than coming out "Uhv"/"Luv" - title-casing every word indiscriminately doesn't know
     * the difference between an ordinary word and an acronym.
     */
    private static String humanize(String raw) {
        if (raw == null || raw.isBlank()) return "Imported Chapter";
        String[] words = raw.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (w.length() > 1 && w.equals(w.toUpperCase())) {
                sb.append(w); // already an acronym - don't "correct" its casing
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.length() > 1 ? w.substring(1) : "");
            }
        }
        return sb.length() == 0 ? "Imported Chapter" : sb.toString();
    }

    /** True if text is non-blank AND isn't just a leftover unresolved "{lang.key}" literal. */
    private static boolean isUsableTitle(String text) {
        if (text == null || text.isBlank()) return false;
        return !LANG_KEY.matcher(text.trim()).matches();
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

        // Lang json (e.g. kubejs/assets/<modid>/lang/en_us.json, or a resourcepack's
        // assets/<modid>/lang/en_us.json) almost never lives inside the ftbquests chapter
        // folder itself - it's a sibling tree. Scan importDir directly (unfiltered, shallow),
        // AND scan a handful of likely pack roots (importDir's ancestors) but restricted to
        // paths that actually pass through a "lang" directory, so we don't waste time walking
        // unrelated multi-gigabyte trees like mods/, saves/, logs/, screenshots/.
        loadLangJsonsFrom(importDir, map, false);

        Path root = importDir;
        for (int i = 0; i < 5 && root != null; i++) {
            root = root.getParent();
            if (root == null || !Files.isDirectory(root)) break;
            loadLangJsonsFrom(root, map, true);
        }

        return map;
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
                    // Only en_us.json - many packs bundle multiple locale files (e.g. zh_cn.json)
                    // right next to it, and matching ANY *.json in a lang/ folder let whichever
                    // locale happened to be walked last silently clobber English translations for
                    // any shared key, regardless of the game's actual selected language.
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
                return flattenComponent(JsonParser.parseString(trimmed), langMap, warnings, richCapable);
            } catch (Exception ignored) {}
        }

        Matcher m = LANG_KEY.matcher(trimmed);
        if (m.matches()) {
            String key = m.group(1);
            String resolved = langMap.get(key);
            if (resolved != null) return convertFormatting(resolved, richCapable);
            warnings.add("Unresolved lang key: " + key);
        }
        return convertFormatting(raw, richCapable);
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
        return out.replaceAll("(?i)&([0-9a-fk-or])", "§$1").replace(">?", "").trim();
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
