package net.phoenixvine.chronicles.filter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * All built-in {@link IItemFilter} implementations + the static deserializer.
 *
 * Quick-reference:
 *
 * ExactItem — one specific item, optional NBT subset check
 * Tag — any item in an item tag (e.g. "forge:ingots/copper")
 * Mod — any item registered by a given mod id
 * AnyOf — OR: matches if ANY child filter matches (union / "one of these")
 * AllOf — AND: matches only if ALL child filters match (e.g. tag + nbt)
 * Not — NOT: inverts a single child filter
 *
 * Serialization key: {@code "filter_type"} → one of the TYPE_* constants below.
 */
public final class ItemFilters {

    public static final String TYPE_EXACT = "exact";
    public static final String TYPE_TAG = "tag";
    public static final String TYPE_MOD = "mod";
    public static final String TYPE_ANY_OF = "any_of";
    public static final String TYPE_ALL_OF = "all_of";
    public static final String TYPE_NOT = "not";

    // ── ExactItem ─────────────────────────────────────────────────────────────

    public record ExactItem(Item item, @Nullable CompoundTag nbt) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            if (stack.getItem() != item) return false;
            if (nbt == null || nbt.isEmpty()) return true;
            CompoundTag stackTag = stack.getTag();
            if (stackTag == null) return false;
            for (String key : nbt.getAllKeys()) {
                if (!stackTag.contains(key)) return false;
                if (!stackTag.get(key).equals(nbt.get(key))) return false;
            }
            return true;
        }

        @Override
        public String describe() {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            String base = id != null ? id.toString() : "unknown";
            return nbt != null && !nbt.isEmpty() ? base + " {…}" : base;
        }

        @Override
        public ItemStack getDisplayStack() {
            return new ItemStack(item);
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_EXACT);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            t.putString("item", id != null ? id.toString() : "minecraft:air");
            if (nbt != null && !nbt.isEmpty()) t.put("nbt", nbt);
            return t;
        }

        public static ExactItem deserialize(CompoundTag t) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(t.getString("item")));
            CompoundTag nbt = t.contains("nbt") ? t.getCompound("nbt") : null;
            return new ExactItem(item == null ? net.minecraft.world.item.Items.AIR : item, nbt);
        }
    }

    // ── Tag ───────────────────────────────────────────────────────────────────

    public record Tag(TagKey<Item> tag) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            return stack.is(tag);
        }

        @Override
        public String describe() {
            return "#" + tag.location();
        }

        @Override
        public ItemStack getDisplayStack() {
            var iter = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();
            return iter.hasNext() ? new ItemStack(iter.next().value()) : ItemStack.EMPTY;
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_TAG);
            t.putString("tag", tag.location().toString());
            return t;
        }

        public static Tag deserialize(CompoundTag t) {
            return new Tag(ItemTags.create(new ResourceLocation(t.getString("tag"))));
        }
    }

    // ── Mod ───────────────────────────────────────────────────────────────────

    public record Mod(String modId) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && id.getNamespace().equals(modId);
        }

        @Override
        public String describe() {
            return "mod:" + modId;
        }

        @Override
        public ItemStack getDisplayStack() {
            return StreamSupport.stream(ForgeRegistries.ITEMS.spliterator(), false)
                    .filter(item -> {
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                        return id != null && id.getNamespace().equals(modId);
                    })
                    .map(ItemStack::new)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_MOD);
            t.putString("mod", modId);
            return t;
        }

        public static Mod deserialize(CompoundTag t) {
            return new Mod(t.getString("mod"));
        }
    }

    // ── AnyOf (OR) ────────────────────────────────────────────────────────────

    public record AnyOf(List<IItemFilter> children) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            for (IItemFilter f : children) if (f.test(stack)) return true;
            return false;
        }

        @Override
        public String describe() {
            if (children.isEmpty()) return "(empty or)";
            if (children.size() == 1) return children.get(0).describe();
            return children.stream().map(IItemFilter::describe)
                    .reduce((a, b) -> a + " OR " + b).orElse("?");
        }

        @Override
        public ItemStack getDisplayStack() {
            return children.isEmpty() ? ItemStack.EMPTY : children.get(0).getDisplayStack();
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_ANY_OF);
            ListTag list = new ListTag();
            children.forEach(c -> list.add(c.serialize()));
            t.put("filters", list);
            return t;
        }

        public static AnyOf deserialize(CompoundTag t) {
            List<IItemFilter> kids = new ArrayList<>();
            ListTag list = t.getList("filters", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) kids.add(ItemFilters.deserialize(list.getCompound(i)));
            return new AnyOf(List.copyOf(kids));
        }
    }

    // ── AllOf (AND) ───────────────────────────────────────────────────────────

    public record AllOf(List<IItemFilter> children) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            for (IItemFilter f : children) if (!f.test(stack)) return false;
            return true;
        }

        @Override
        public String describe() {
            if (children.isEmpty()) return "(empty and)";
            return children.stream().map(IItemFilter::describe)
                    .reduce((a, b) -> a + " AND " + b).orElse("?");
        }

        @Override
        public ItemStack getDisplayStack() {
            return children.isEmpty() ? ItemStack.EMPTY : children.get(0).getDisplayStack();
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_ALL_OF);
            ListTag list = new ListTag();
            children.forEach(c -> list.add(c.serialize()));
            t.put("filters", list);
            return t;
        }

        public static AllOf deserialize(CompoundTag t) {
            List<IItemFilter> kids = new ArrayList<>();
            ListTag list = t.getList("filters", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) kids.add(ItemFilters.deserialize(list.getCompound(i)));
            return new AllOf(List.copyOf(kids));
        }
    }

    // ── Not ───────────────────────────────────────────────────────────────────

    public record Not(IItemFilter child) implements IItemFilter {

        @Override
        public boolean test(ItemStack stack) {
            return !child.test(stack);
        }

        @Override
        public String describe() {
            return "NOT(" + child.describe() + ")";
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_NOT);
            t.put("filter", child.serialize());
            return t;
        }

        public static Not deserialize(CompoundTag t) {
            return new Not(ItemFilters.deserialize(t.getCompound("filter")));
        }
    }

    // ── Deserializer ──────────────────────────────────────────────────────────

    /**
     * Reconstruct any {@link IItemFilter} from its serialized CompoundTag.
     * Returns an always-false sentinel filter if the type is unrecognized.
     */
    public static IItemFilter deserialize(CompoundTag tag) {
        String type = tag.getString("filter_type");
        return switch (type) {
            case TYPE_EXACT -> ExactItem.deserialize(tag);
            case TYPE_TAG -> Tag.deserialize(tag);
            case TYPE_MOD -> Mod.deserialize(tag);
            case TYPE_ANY_OF -> AnyOf.deserialize(tag);
            case TYPE_ALL_OF -> AllOf.deserialize(tag);
            case TYPE_NOT -> Not.deserialize(tag);
            default -> {
                System.err.println("[Chronicles] Unknown item filter type: " + type);
                // Fixed: Explicitly implement all abstract methods for the fallback instance
                yield new IItemFilter() {

                    @Override
                    public boolean test(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public String describe() {
                        return "INVALID";
                    }

                    @Override
                    public CompoundTag serialize() {
                        return new CompoundTag();
                    }
                };
            }
        };
    }

    // ── Factory helpers (for code/KubeJS use) ────────────────────────────────

    public static IItemFilter exact(Item item) {
        return new ExactItem(item, null);
    }

    public static IItemFilter exact(Item item, CompoundTag nbt) {
        return new ExactItem(item, nbt);
    }

    public static IItemFilter tag(String tagPath) {
        return new Tag(ItemTags.create(new ResourceLocation(tagPath)));
    }

    public static IItemFilter mod(String modId) {
        return new Mod(modId);
    }

    public static IItemFilter anyOf(IItemFilter... filters) {
        return new AnyOf(List.of(filters));
    }

    public static IItemFilter allOf(IItemFilter... filters) {
        return new AllOf(List.of(filters));
    }

    public static IItemFilter not(IItemFilter filter) {
        return new Not(filter);
    }

    private ItemFilters() {}
}
