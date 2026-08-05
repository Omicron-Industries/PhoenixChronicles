package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;

public final class LootCrateItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final LootCrateItemRenderer INSTANCE = new LootCrateItemRenderer();

    private final ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());

    private LootCrateItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .renderItem(chest, poseStack, buffer, packedLight, packedOverlay);
    }
}
