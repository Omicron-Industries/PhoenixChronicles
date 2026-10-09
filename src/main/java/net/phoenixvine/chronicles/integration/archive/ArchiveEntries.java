package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArchiveEntries {

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

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder icon(String itemId) {
            this.icon = itemId;
            return this;
        }

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

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder hiddenUntil(String entryId) {
            this.hidden = true;
            this.hiddenUntilId = entryId;
            return this;
        }

        public Builder unlockedByQuest(String questId) {
            this.quests.add(questId);
            return this;
        }

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
