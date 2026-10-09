package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry.FieldDef;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PhoenixRewardRegistry {

    public record Entry(
                        String typeId,
                        @Nullable String label,
                        @Nullable String tooltip,
                        List<FieldDef> fields,
                        @Nullable String requiredModId,
                        BiConsumer<ServerPlayer, CompoundTag> grant,
                        @Nullable Function<CompoundTag, String> summary) {

        public boolean isAvailable() {
            return requiredModId == null || net.minecraftforge.fml.ModList.get().isLoaded(requiredModId);
        }

        public String displayName() {
            return label != null ? label : typeId;
        }
    }

    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    private PhoenixRewardRegistry() {}

    public static Builder register(String typeId) {
        return new Builder(typeId);
    }

    @Nullable
    public static Entry get(String typeId) {
        return REGISTRY.get(typeId);
    }

    public static Collection<Entry> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static final class Builder {

        private final String typeId;
        private String label;
        private String tooltip;
        private final List<FieldDef> fields = new ArrayList<>();
        private String requiredModId;
        private BiConsumer<ServerPlayer, CompoundTag> grant = (player, data) -> {};
        private Function<CompoundTag, String> summary;

        private Builder(String typeId) {
            this.typeId = typeId;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder field(FieldDef field) {
            this.fields.add(field);
            return this;
        }

        public Builder requiresMod(String modId) {
            this.requiredModId = modId;
            return this;
        }

        public Builder onGrant(BiConsumer<ServerPlayer, CompoundTag> grant) {
            this.grant = grant;
            return this;
        }

        public Builder summary(Function<CompoundTag, String> summary) {
            this.summary = summary;
            return this;
        }

        public void register() {
            REGISTRY.put(typeId, new Entry(typeId, label, tooltip,
                    Collections.unmodifiableList(new ArrayList<>(fields)), requiredModId, grant, summary));
        }
    }
}
