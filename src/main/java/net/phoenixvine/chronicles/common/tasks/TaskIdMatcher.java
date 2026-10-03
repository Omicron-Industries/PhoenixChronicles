package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TaskIdMatcher {

    private final List<ResourceLocation> ids;
    private final List<ResourceLocation> tags;
    private final List<String> invalid;

    private TaskIdMatcher(List<ResourceLocation> ids, List<ResourceLocation> tags, List<String> invalid) {
        this.ids = List.copyOf(ids);
        this.tags = List.copyOf(tags);
        this.invalid = List.copyOf(invalid);
    }

    public static TaskIdMatcher of(@Nullable ResourceLocation id) {
        return id == null ? parse("", true) : new TaskIdMatcher(List.of(id), List.of(), List.of());
    }

    public static TaskIdMatcher parse(@Nullable String spec) {
        return parse(spec, true);
    }

    public static TaskIdMatcher parse(@Nullable String spec, boolean allowTags) {
        List<ResourceLocation> ids = new ArrayList<>();
        List<ResourceLocation> tags = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        if (spec != null) {
            for (String raw : spec.trim().split("[,;\\s]+")) {
                if (raw.isEmpty()) continue;
                boolean isTag = raw.startsWith("#");
                if (isTag && !allowTags) {
                    invalid.add(raw);
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(isTag ? raw.substring(1) : raw);
                if (rl == null) {
                    invalid.add(raw);
                    continue;
                }
                List<ResourceLocation> bucket = isTag ? tags : ids;
                if (!bucket.contains(rl)) bucket.add(rl);
            }
        }
        return new TaskIdMatcher(ids, tags, invalid);
    }

    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    public List<ResourceLocation> ids() {
        return ids;
    }

    public List<ResourceLocation> tags() {
        return tags;
    }

    public List<String> invalidEntries() {
        return invalid;
    }

    @Nullable
    public ResourceLocation firstId() {
        return ids.isEmpty() ? null : ids.get(0);
    }

    public String spec() {
        List<String> parts = new ArrayList<>();
        for (ResourceLocation id : ids) parts.add(id.toString());
        for (ResourceLocation tag : tags) parts.add("#" + tag);
        return String.join(", ", parts);
    }

    public String displayName() {
        List<String> parts = new ArrayList<>();
        for (ResourceLocation id : ids) parts.add(id.getPath().replace('_', ' '));
        for (ResourceLocation tag : tags) parts.add("#" + tag.getPath().replace('_', ' '));
        return String.join(" / ", parts);
    }

    public boolean matchesId(@Nullable ResourceLocation id) {
        return id != null && ids.contains(id);
    }

    public boolean matchesEntity(EntityType<?> type) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (matchesId(key)) return true;
        for (ResourceLocation tag : tags) {
            if (type.is(TagKey.create(Registries.ENTITY_TYPE, tag))) return true;
        }
        return false;
    }

    public boolean matchesBiome(Holder<Biome> biome) {
        ResourceLocation key = biome.unwrapKey().map(k -> k.location()).orElse(null);
        if (matchesId(key)) return true;
        for (ResourceLocation tag : tags) {
            if (biome.is(TagKey.create(Registries.BIOME, tag))) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return spec();
    }
}
