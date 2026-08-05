package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.item.ChronicleItems;
import net.phoenixvine.chronicles.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.item.ItemFilterTokenItem;

import org.jetbrains.annotations.Nullable;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FilterTokenModelSwap {

    private FilterTokenModelSwap() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        swap(event, ChronicleItems.ITEM_FILTER_TOKEN.getId(), true);
        swap(event, ChronicleItems.FLUID_FILTER_TOKEN.getId(), false);
    }

    private static void swap(ModelEvent.ModifyBakingResult event, ResourceLocation itemId, boolean itemMode) {
        ModelResourceLocation key = new ModelResourceLocation(itemId, "inventory");
        BakedModel original = event.getModels().get(key);
        if (original == null) return;
        event.getModels().put(key, new DelegatingModel(original, itemMode));
    }

    private static final class DelegatingModel implements BakedModel {

        private final BakedModel fallback;
        private final boolean itemMode;
        private final ItemOverrides overrides;

        DelegatingModel(BakedModel fallback, boolean itemMode) {
            this.fallback = fallback;
            this.itemMode = itemMode;
            this.overrides = new ItemOverrides() {

                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack,
                                          @Nullable net.minecraft.client.multiplayer.ClientLevel level,
                                          @Nullable net.minecraft.world.entity.LivingEntity entity, int seed) {
                    BakedModel resolved = resolveTarget(stack);
                    return resolved != null ? resolved : DelegatingModel.this.fallback;
                }
            };
        }

        @Nullable
        private BakedModel resolveTarget(ItemStack stack) {
            try {
                Minecraft mc = Minecraft.getInstance();
                ItemStack display = ItemStack.EMPTY;
                if (itemMode) {
                    IItemFilter f = ItemFilterTokenItem.getFilter(stack);
                    if (f != null) display = f.getDisplayStack();
                } else {
                    IFluidFilter f = FluidFilterTokenItem.getFilter(stack);
                    if (f != null) {
                        var fluid = f.getDisplayFluid();
                        if (fluid != null) {
                            var bucket = fluid.getBucket();
                            if (bucket != null && bucket != net.minecraft.world.item.Items.AIR)
                                display = new ItemStack(bucket);
                        }
                    }
                }
                if (display.isEmpty()) return null;
                if (display.getItem() instanceof ItemFilterTokenItem ||
                        display.getItem() instanceof FluidFilterTokenItem)
                    return null;
                return mc.getItemRenderer().getItemModelShaper().getItemModel(display.getItem());
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(@Nullable BlockState state,
                                                                                  @Nullable Direction direction,
                                                                                  RandomSource random) {
            return fallback.getQuads(state, direction, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return fallback.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return fallback.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return fallback.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return fallback.isCustomRenderer();
        }

        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return fallback.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return fallback.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
