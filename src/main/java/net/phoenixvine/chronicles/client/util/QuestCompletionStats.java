package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestCompletionStats {

    public static final class Tally {

        public int done;
        public int total;

        void add(boolean isDone) {
            total++;
            if (isDone) done++;
        }

        public int percent() {
            return total <= 0 ? 0 : (int) (done * 100L / total);
        }

        public float fraction() {
            return total <= 0 ? 0f : (float) done / total;
        }
    }

    public final Tally required = new Tally();
    public final Tally optional = new Tally();
    public int disabled;
    public int repeatable;

    public final Map<String, Tally> chapters = new LinkedHashMap<>();

    /** Per chapter, required (non-optional) quests only - what a chapter-complete stamp is judged on. */
    public final Map<String, Tally> chaptersRequired = new LinkedHashMap<>();

    public Tally overall() {
        Tally t = new Tally();
        t.done = required.done + optional.done;
        t.total = required.total + optional.total;
        return t;
    }

    public static QuestCompletionStats compute(ScreenContext ctx) {
        QuestCompletionStats stats = new QuestCompletionStats();
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        Player player = mc.player;

        List<String> chapterOrder = ctx.buildChapterList();
        for (String c : chapterOrder) {
            stats.chapters.put(c.toUpperCase(Locale.ROOT), new Tally());
            stats.chaptersRequired.put(c.toUpperCase(Locale.ROOT), new Tally());
        }

        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (n.isLinkStub()) continue;

            String chapter = n.getChapter() != null ? n.getChapter().toUpperCase(Locale.ROOT) : "";
            Tally chapterTally = stats.chapters.get(chapter);
            if (chapterTally == null) continue;

            if (n.isFlagDisabled(server, player)) continue;
            if (ctx.isHiddenFromPlayer(n)) continue;

            if (n.getEffectiveVisibility(server, player) == QuestNode.Visibility.DISABLED) {
                stats.disabled++;
                continue;
            }
            if (n.getRepeatMode() != QuestNode.RepeatMode.NONE) {
                stats.repeatable++;
                continue;
            }

            boolean done = ctx.getState(n) == QuestState.COMPLETED;
            (n.isOptional() ? stats.optional : stats.required).add(done);
            chapterTally.add(done);
            if (!n.isOptional()) stats.chaptersRequired.get(chapter).add(done);
        }
        return stats;
    }
}
