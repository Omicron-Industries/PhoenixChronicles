package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Phoenix Archive lore entries from Java or KubeJS without touching Archive classes. Entries registered here
 * are handed to Archive whenever it (re)loads its lore, so register them early (mod setup or a KubeJS startup script).
 * Does nothing when Archive isn't installed.
 *
 * <pre>
 * ArchiveEntries.entry("mypack:intro").title("First contact").category("Story")
 *         .content("&amp;7You hear a hum...").icon("minecraft:book").unlockedByQuest("phoenix_chronicles:intro")
 *         .register();
 * ArchiveEntries.forQuest(node).category("Quests").register();   // title, text and icon taken from the quest
 * </pre>
 */
public final class ArchiveEntries {

    /** Everything an entry is made of; plain data so it can be held without Archive on the classpath. */
    public record Spec(ResourceLocation id, String title, String category, String content, String iconItem,
                       String lockedContent, String voiceLine, int order, String shader, boolean hidden,
                       String hiddenUntilId, List<String> questConditions, boolean anyQuest) {}

    private static final Map<ResourceLocation, Spec> REGISTERED = new LinkedHashMap<>();
    private static boolean listenerInstalled = false;

    private ArchiveEntries() {}

    public static Builder entry(String id) {
        return new Builder(id.contains(":") ? ResourceLocation.parse(id) :
                ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", id));
    }

    /** A builder pre-filled from a quest: its title, description and icon, unlocked when that quest is completed. */
    public static Builder forQuest(QuestNode node) {
        Builder b = new Builder(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles",
                "quest_" + node.getId().getPath().replace('/', '_')))
                .title(node.getTitle().getString())
                .content(node.getDescription().getString())
                .unlockedByQuest(node.getId().toString());
        if (node.getIconItem() != null) {
            ResourceLocation iconId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(node.getIconItem());
            if (iconId != null) b.icon(iconId.toString());
        }
        return b;
    }

    public static Collection<Spec> registered() {
        return Collections.unmodifiableCollection(REGISTERED.values());
    }

    private static void add(Spec spec) {
        if (!ArchiveLoreCompat.isAvailable()) return;
        REGISTERED.put(spec.id(), spec);
        if (!listenerInstalled) {
            listenerInstalled = true;
            ArchiveLoreCompatImpl.installEntryListener();
        }
    }

    public static final class Builder {

        private final ResourceLocation id;
        private String title = "";
        private String category = "Uncategorized";
        private String content = "";
        private String icon = "minecraft:paper";
        private String lockedContent = null;
        private String voiceLine = "";
        private int order = 0;
        private String shader = "";
        private boolean hidden = false;
        private String hiddenUntilId = "";
        private final List<String> quests = new ArrayList<>();
        private boolean anyQuest = false;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /** Body text; supports Archive's {@code &} colour codes and {@code &#RRGGBB}. */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder icon(String itemId) {
            this.icon = itemId;
            return this;
        }

        /** What the entry shows while still locked. */
        public Builder lockedContent(String lockedContent) {
            this.lockedContent = lockedContent;
            return this;
        }

        public Builder voiceLine(String voiceLine) {
            this.voiceLine = voiceLine;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder shader(String backgroundShader) {
            this.shader = backgroundShader;
            return this;
        }

        /** Leaves the entry out of the list entirely until it is unlocked. */
        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        /** Hidden until a different entry is unlocked. */
        public Builder hiddenUntil(String entryId) {
            this.hidden = true;
            this.hiddenUntilId = entryId;
            return this;
        }

        /** Unlocks when this Chronicles quest is completed. Call again to require several (all, unless anyQuest). */
        public Builder unlockedByQuest(String questId) {
            this.quests.add(questId);
            return this;
        }

        /** With several quest conditions, unlock on whichever is completed first instead of requiring all. */
        public Builder anyQuest() {
            this.anyQuest = true;
            return this;
        }

        public void register() {
            add(new Spec(id, title, category, content, icon, lockedContent, voiceLine, order, shader, hidden,
                    hiddenUntilId, List.copyOf(quests), anyQuest));
        }
    }
}
