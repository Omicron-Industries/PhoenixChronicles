package net.phoenixvine.chronicles.codec;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.FullQuestData;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads quest content files from:
 * config/phoenix_chronicles/quests/<id>.md
 *
 * Each file contains ONLY human-readable content:
 *
 * <pre>
 * ---
 * title: "Signal Lost"
 * ---
 *
 * # Signal Lost
 *
 * The outpost went dark three cycles ago...
 * </pre>
 *
 * No structural data (parent, position, shape, dependencies) belongs here.
 * That all lives in the chapter definition files.
 *
 * Localization: place translated files at
 * config/phoenix_chronicles/quests/lang/<locale>/<id>.md
 * The loader tries the locale file first, falls back to the default.
 */
public class QuestContentLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestContentLoader.class);

    /** Locale used for content resolution. Defaults to en_us. */
    private static String activeLocale = "en_us";

    public static void setActiveLocale(String locale) {
        activeLocale = locale != null ? locale.toLowerCase() : "en_us";
    }

    public static void reloadAllQuestsFromDisk() {
        // Do NOT call QuestTreeRegistry.clear() here. QuestFileLoader owns the
        // full-registry-clear lifecycle (position, shape, dependencies, previewMachineId,
        // etc). If this pass ever runs after that one - world join ordering, a locale
        // switch, anything - clearing here would wipe all of that back out, since this
        // pass only ever knows about title/description.
        Path questsFolder = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("quests");

        if (!Files.exists(questsFolder)) {
            LOGGER.info("[Chronicles] No quests folder found at {}", questsFolder);
            return;
        }

        try (Stream<Path> walk = Files.walk(questsFolder)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.toString().contains("/lang/") && !p.toString().contains("\\lang\\"))
                    .sorted()
                    .forEach(QuestContentLoader::loadQuestFile);
        } catch (IOException e) {
            LOGGER.error("[Chronicles] Failed to walk quests directory", e);
        }
    }

    private static void loadQuestFile(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String id = fileName.substring(0, fileName.lastIndexOf('.'));
            ResourceLocation questId = new ResourceLocation("phoenixcore", id.toLowerCase());

            Path resolvedFile = resolveLocaleFile(file, id);
            QuestContent content = parseQuestFile(resolvedFile);
            if (content == null) {
                LOGGER.warn("[Chronicles] Skipping quest file with no parseable content: {}", file);
                return;
            }

            QuestNode existing = QuestTreeRegistry.getQuest(questId);
            if (existing != null) {
                // Structural loader already registered this node - refresh content in place,
                // don't replace it (that would drop previewMachineId/position/shape/etc).
                existing.setTitle(content.title());
                existing.setDescription(content.description());
                return;
            }

            QuestNode node = new QuestNode(questId, content.title(), content.description());
            QuestTreeRegistry.registerBareQuestNode(node);
        } catch (Exception e) {
            LOGGER.error("[Chronicles] Failed to load quest file: {}", file.getFileName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Public JIT loader — used by ChronicleOverviewScreen when opening a node
    // -------------------------------------------------------------------------

    /**
     * Reads the full content for a quest by ID on demand (just-in-time).
     * Returns null if the file doesn't exist or fails to parse.
     */
    public static FullQuestData loadFullQuestDetails(ResourceLocation questId) {
        Path questsFolder = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("phoenix_chronicles")
                .resolve("quests");

        // Build the path from the quest id path component
        // e.g. "chapter_1/signal_lost" → quests/chapter_1/signal_lost.md
        Path file = questsFolder.resolve(questId.getPath() + ".md");

        if (!Files.exists(file)) {
            LOGGER.warn("[Chronicles] Quest content file not found: {}", file);
            return null;
        }

        Path resolved = resolveLocaleFile(file, questId.getPath());
        QuestContent content = parseQuestFile(resolved);

        if (content == null) return null;

        // Tasks are still populated from the QuestNode's task list (set by datapacks/config)
        // so we pass an empty list here; QuestTasksScreen reads from the node directly.
        return new FullQuestData(content.title(), content.description(), java.util.List.of());
    }

    // -------------------------------------------------------------------------
    // Parser
    // -------------------------------------------------------------------------

    /**
     * Parses a single quest .md file.
     *
     * Used to scan for a "---" front-matter block (title: key between two "---" delimiter
     * lines) - a format QuestFileSaver has never actually written (it writes plain
     * "# Title\n\ndesc", no delimiters at all), so on every real file this loop saw zero "---"
     * lines, never flipped pastFrontMatter to true, and treated the ENTIRE file - including the
     * body - as front matter to be silently discarded (only "title:" lines survived, and there
     * are none in the real format). Worse: a quest whose description legitimately uses the
     * "---" page-break marker (see QuestTasksScreen.DESC_PAGE_BREAK) would have those lines
     * miscounted as front-matter delimiters, corrupting the parse in a different way depending
     * on how many page breaks happened to be present. This only mattered on the path that
     * actually called this method (LangEditorScreen's reload button), which is presumably why it
     * went unnoticed for so long, but it's a real landmine for anyone hitting that path.
     *
     * Now just delegates to ChronicleOverviewScreen#loadMarkdownContent - the SAME parser every
     * other description read in the mod already goes through, already handles the real
     * "# Title\n\ndesc" format, and already preserves page-break markers correctly. Having two
     * independent parsers for the one file format is exactly how they silently drifted apart
     * before; there is no reason for this class to maintain its own.
     */
    public static QuestContent parseQuestFile(Path file) {
        FullQuestData data = net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen
                .loadMarkdownContent(file);
        Component title = data.title();
        if (title == null || title.getString().isBlank()) {
            // Fall back to filename if the file had no "# " heading line
            String name = file.getFileName().toString();
            title = Component.literal(name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name);
        }
        return new QuestContent(title, data.description());
    }

    // -------------------------------------------------------------------------
    // Locale resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves {@code defaultFile} to its locale-specific override if one exists on disk, else
     * returns {@code defaultFile} unchanged. Public so callers with their OWN markdown-reading
     * path (ChronicleOverviewScreen's live quest-open flow, rather than this class's own JIT
     * loader above) can get the same locale override behavior without duplicating the folder
     * convention. {@code id} is only used for the debug log line.
     */
    public static Path resolveLocaleFile(Path defaultFile, String id) {
        if (activeLocale.equals("en_us")) return defaultFile;

        // e.g. quests/<category>/lang/fr_fr/signal_lost.md — locale folder sits alongside the
        // default file within its own category folder, not one single global quests/lang/ tree.
        Path langDir = defaultFile.getParent().resolve("lang").resolve(activeLocale);
        String fileName = defaultFile.getFileName().toString();
        Path localeFile = langDir.resolve(fileName);

        if (Files.exists(localeFile)) {
            LOGGER.debug("[Chronicles] Using locale override for {}: {}", id, localeFile);
            return localeFile;
        }
        return defaultFile;
    }

    /**
     * Points locale resolution at the CLIENT's actual currently-selected game language. Cheap
     * enough to call on every quest open rather than needing a dedicated language-change event -
     * this keeps {@link #activeLocale} accurate even if the player changes language mid-session.
     */
    public static void syncActiveLocaleFromClient() {
        Minecraft mc = Minecraft.getInstance();
        setActiveLocale(mc.options != null ? mc.options.languageCode : null);
    }

    // -------------------------------------------------------------------------
    // Internal data carriers
    // -------------------------------------------------------------------------

    public record QuestContent(Component title, Component description) {}
}
