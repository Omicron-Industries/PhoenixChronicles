package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.screen.WikiScreen;
import net.phoenixvine.wiki.client.screen.WikiTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class ChronicleWikiScreen extends WikiScreen {

    public ChronicleWikiScreen(Screen parent, WikiTheme theme) {
        super(parent, "phoenix_chronicles", "wiki", theme);
    }

    @Override
    protected Map<String, UnaryOperator<String>> dynamicPageResolvers() {
        return Map.of("live_stats", ChronicleWikiScreen::resolveLiveStats);
    }

    private static String resolveLiveStats(String markdown) {
        Map<String, List<QuestNode>> byChapter = new LinkedHashMap<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            byChapter.computeIfAbsent(n.getChapter(), k -> new ArrayList<>()).add(n);
        }

        StringBuilder perChapter = new StringBuilder();
        byChapter.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(l -> -l.size())))
                .forEach(e -> {
                    long repeatable = e.getValue().stream().filter(QuestNode::isRepeatable).count();
                    long tutorial = e.getValue().stream().filter(n -> !n.getTutorialSteps().isEmpty()).count();
                    perChapter.append("- **").append(e.getKey()).append(":** ").append(e.getValue().size())
                            .append(" quests");
                    if (repeatable > 0) perChapter.append(", ").append(repeatable).append(" repeatable");
                    if (tutorial > 0) perChapter.append(", ").append(tutorial).append(" tutorial");
                    perChapter.append("\n");
                });
        if (perChapter.isEmpty()) perChapter.append("*No quests loaded.*\n");

        Map<QuestNode.RepeatMode, Long> repeatCounts = new LinkedHashMap<>();
        for (QuestNode.RepeatMode m : QuestNode.RepeatMode.values()) repeatCounts.put(m, 0L);
        QuestTreeRegistry.getAllQuests().values().forEach(n -> repeatCounts.merge(n.getRepeatMode(), 1L, Long::sum));
        StringBuilder repeatBlock = new StringBuilder();
        repeatCounts.forEach((mode, count) -> {
            if (count > 0) repeatBlock.append("- **").append(mode.name()).append(":** ").append(count)
                    .append(count == 1 ? " quest\n" : " quests\n");
        });
        if (repeatBlock.isEmpty()) repeatBlock.append("*No quests loaded.*\n");

        long tutorialCount = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> !n.getTutorialSteps().isEmpty()).count();

        List<String> errors;
        synchronized (QuestFileLoader.LOAD_ERRORS) {
            errors = new ArrayList<>(QuestFileLoader.LOAD_ERRORS);
        }
        String loadErrorsBlock;
        if (errors.isEmpty()) {
            loadErrorsBlock = ":::tip\nNo load errors.\n:::";
        } else {
            StringBuilder b = new StringBuilder(":::warning\n").append(errors.size()).append(" load error(s):\n\n");
            for (String e : errors) b.append("- ").append(e).append("\n");
            b.append(":::");
            loadErrorsBlock = b.toString();
        }

        return markdown
                .replace("{{total_quests}}", String.valueOf(QuestTreeRegistry.getAllQuests().size()))
                .replace("{{category_count}}", String.valueOf(byChapter.size()))
                .replace("{{per_chapter_block}}", perChapter.toString().trim())
                .replace("{{task_types_registered}}", String.valueOf(PhoenixTaskRegistry.getEditorTypes().size()))
                .replace("{{repeat_mode_block}}", repeatBlock.toString().trim())
                .replace("{{tutorial_count}}", String.valueOf(tutorialCount))
                .replace("{{load_errors_block}}", loadErrorsBlock);
    }
}
