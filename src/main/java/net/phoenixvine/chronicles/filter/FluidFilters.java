package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FluidFilters {

    public static final String TYPE_EXACT = "exact";
    public static final String TYPE_TAG = "tag";
    public static final String TYPE_MOD = "mod";
    public static final String TYPE_ANY_OF = "any_of";
    public static final String TYPE_ALL_OF = "all_of";
    public static final String TYPE_NOT = "not";

    public record ExactFluid(ResourceLocation fluidId, @Nullable CompoundTag nbt) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
            if (!fluidId.equals(id)) return false;
            return NbtMatchUtil.matches(stack.getTag(), nbt);
        }

        @Override
        public String describe() {
            return nbt != null && !nbt.isEmpty() ? fluidId + " {…}" : fluidId.toString();
        }

        @Override
        public net.minecraft.world.level.material.Fluid getDisplayFluid() {
            return ForgeRegistries.FLUIDS.getValue(fluidId);
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_EXACT);
            t.putString("fluid", fluidId.toString());
            if (nbt != null && !nbt.isEmpty()) t.put("nbt", nbt);
            return t;
        }

        public static ExactFluid deserialize(CompoundTag t) {
            ResourceLocation id = ResourceLocation.parse(t.getString("fluid"));
            CompoundTag nbt = t.contains("nbt") ? t.getCompound("nbt") : null;
            return new ExactFluid(id, nbt);
        }
    }

    public record FluidTag(TagKey<Fluid> tag) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
            return stack.getFluid().is(tag);
        }

        @Override
        public String describe() {
            return "#" + tag.location();
        }

        @Override
        public net.minecraft.world.level.material.Fluid getDisplayFluid() {
            var iter = net.minecraft.core.registries.BuiltInRegistries.FLUID.getTagOrEmpty(tag).iterator();
            return iter.hasNext() ? iter.next().value() : null;
        }

        @Override
        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putString("filter_type", TYPE_TAG);
            t.putString("tag", tag.location().toString());
            return t;
        }

        public static FluidTag deserialize(CompoundTag t) {
            return new FluidTag(FluidTags.create(ResourceLocation.parse(t.getString("tag"))));
        }
    }

    public record Mod(String modId) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
            return id != null && id.getNamespace().equals(modId);
        }

        @Override
        public String describe() {
            return "mod:" + modId;
        }

        @Override
        public net.minecraft.world.level.material.Fluid getDisplayFluid() {
            return java.util.stream.StreamSupport.stream(ForgeRegistries.FLUIDS.spliterator(), false)
                    .filter(fluid -> {
                        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
                        return id != null && id.getNamespace().equals(modId);
                    })
                    .findFirst().orElse(null);
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

    public record AnyOf(List<IFluidFilter> children) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
            for (IFluidFilter f : children) if (f.test(stack)) return true;
            return false;
        }

        @Override
        public String describe() {
            return children.stream().map(IFluidFilter::describe)
                    .reduce((a, b) -> a + " OR " + b).orElse("(empty)");
        }

        @Override
        public net.minecraft.world.level.material.Fluid getDisplayFluid() {
            return children.isEmpty() ? null : children.get(0).getDisplayFluid();
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
            List<IFluidFilter> kids = new ArrayList<>();
            ListTag list = t.getList("filters", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) kids.add(FluidFilters.deserialize(list.getCompound(i)));
            return new AnyOf(List.copyOf(kids));
        }
    }

    public record AllOf(List<IFluidFilter> children) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
            for (IFluidFilter f : children) if (!f.test(stack)) return false;
            return true;
        }

        @Override
        public String describe() {
            return children.stream().map(IFluidFilter::describe)
                    .reduce((a, b) -> a + " AND " + b).orElse("(empty)");
        }

        @Override
        public net.minecraft.world.level.material.Fluid getDisplayFluid() {
            return children.isEmpty() ? null : children.get(0).getDisplayFluid();
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
            List<IFluidFilter> kids = new ArrayList<>();
            ListTag list = t.getList("filters", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) kids.add(FluidFilters.deserialize(list.getCompound(i)));
            return new AllOf(List.copyOf(kids));
        }
    }

    public record Not(IFluidFilter child) implements IFluidFilter {

        @Override
        public boolean test(FluidStack stack) {
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
            return new Not(FluidFilters.deserialize(t.getCompound("filter")));
        }
    }

    public static IFluidFilter deserialize(CompoundTag tag) {
        String type = tag.getString("filter_type");
        return switch (type) {
            case TYPE_EXACT -> ExactFluid.deserialize(tag);
            case TYPE_TAG -> FluidTag.deserialize(tag);
            case TYPE_MOD -> Mod.deserialize(tag);
            case TYPE_ANY_OF -> AnyOf.deserialize(tag);
            case TYPE_ALL_OF -> AllOf.deserialize(tag);
            case TYPE_NOT -> Not.deserialize(tag);
            default -> {
                System.err.println("[Chronicles] Unknown fluid filter type: " + type);

                yield new IFluidFilter() {

                    @Override
                    public boolean test(FluidStack stack) {
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

    public static IFluidFilter exact(ResourceLocation fluidId) {
        return new ExactFluid(fluidId, null);
    }

    public static IFluidFilter exact(ResourceLocation fluidId, CompoundTag nbt) {
        return new ExactFluid(fluidId, nbt);
    }

    public static IFluidFilter tag(String tagPath) {
        return new FluidTag(FluidTags.create(ResourceLocation.parse(tagPath)));
    }

    public static IFluidFilter mod(String modId) {
        return new Mod(modId);
    }

    public static IFluidFilter anyOf(IFluidFilter... filters) {
        return new AnyOf(List.of(filters));
    }

    public static IFluidFilter allOf(IFluidFilter... filters) {
        return new AllOf(List.of(filters));
    }

    public static IFluidFilter not(IFluidFilter filter) {
        return new Not(filter);
    }

    private FluidFilters() {}
}
