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

public class QuestContentLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestContentLoader.class);

    private static String activeLocale = "en_us";

    public static void setActiveLocale(String locale) {
        activeLocale = locale != null ? locale.toLowerCase() : "en_us";
    }

    public static void reloadAllQuestsFromDisk() {
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
            ResourceLocation questId = new ResourceLocation("phoenix_chronicles", id.toLowerCase());

            Path resolvedFile = resolveLocaleFile(file, id);
            QuestContent content = parseQuestFile(resolvedFile);
            if (content == null) {
                LOGGER.warn("[Chronicles] Skipping quest file with no parseable content: {}", file);
                return;
            }

            QuestNode existing = QuestTreeRegistry.getQuest(questId);
            if (existing != null) {

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

    public static FullQuestData loadFullQuestDetails(ResourceLocation questId) {
        Path questsFolder = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("phoenix_chronicles")
                .resolve("quests");

        Path file = questsFolder.resolve(questId.getPath() + ".md");

        if (!Files.exists(file)) {
            LOGGER.warn("[Chronicles] Quest content file not found: {}", file);
            return null;
        }

        Path resolved = resolveLocaleFile(file, questId.getPath());
        QuestContent content = parseQuestFile(resolved);

        if (content == null) return null;

        return new FullQuestData(content.title(), content.description(), java.util.List.of());
    }

    public static QuestContent parseQuestFile(Path file) {
        FullQuestData data = net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen
                .loadMarkdownContent(file);
        Component title = data.title();
        if (title == null || title.getString().isBlank()) {

            String name = file.getFileName().toString();
            title = Component.literal(name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name);
        }
        return new QuestContent(title, data.description());
    }

    public static Path resolveLocaleFile(Path defaultFile, String id) {
        if (activeLocale.equals("en_us")) return defaultFile;

        Path langDir = defaultFile.getParent().resolve("lang").resolve(activeLocale);
        String fileName = defaultFile.getFileName().toString();
        Path localeFile = langDir.resolve(fileName);

        if (Files.exists(localeFile)) {
            LOGGER.debug("[Chronicles] Using locale override for {}: {}", id, localeFile);
            return localeFile;
        }
        return defaultFile;
    }

    public static void syncActiveLocaleFromClient() {
        Minecraft mc = Minecraft.getInstance();
        setActiveLocale(mc.options != null ? mc.options.languageCode : null);
    }

    public record QuestContent(Component title, Component description) {}
}
