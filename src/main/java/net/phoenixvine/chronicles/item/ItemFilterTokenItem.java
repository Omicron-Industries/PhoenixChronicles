package net.phoenixvine.chronicles.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemFilterTokenItem extends Item {

    public static final String TAG_FILTER = "phoenix_filter";

    public ItemFilterTokenItem(Properties props) {
        super(props);
    }

    @Nullable
    public static IItemFilter getFilter(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_FILTER)) return null;
        try {
            return ItemFilters.deserialize(tag.getCompound(TAG_FILTER));
        } catch (Exception e) {
            return null;
        }
    }

    public static void setFilter(ItemStack stack, IItemFilter filter) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_FILTER, filter.serialize());
    }

    public static boolean isFilterToken(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFilterTokenItem;
    }

    @Override
    public Component getName(ItemStack stack) {
        IItemFilter filter = getFilter(stack);
        if (filter == null) return Component.literal("§eUnconfigured Item Filter");
        return Component.literal("§eFilter: §f" + filter.describe());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        IItemFilter filter = getFilter(stack);
        if (filter == null) {
            lines.add(Component.literal("§7Right-click to configure this filter.").withStyle(ChatFormatting.ITALIC));
            return;
        }
        lines.add(Component.literal("§8Matches: §7" + filter.describe()));
        lines.add(Component.literal("§7Right-click to edit").withStyle(ChatFormatting.ITALIC));
        lines.add(Component.literal("§7Shift + Right-click to clear").withStyle(ChatFormatting.ITALIC));
        lines.add(Component
                .literal("§7Hold in the Tasks & Rewards editor to apply to a filter_item task")
                .withStyle(ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                stack.removeTagKey(TAG_FILTER);
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2SSetFilterTokenPacket((CompoundTag) null));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            openEditor(player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openEditor(Player player, ItemStack stack) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new net.phoenixvine.chronicles.client.screen.FilterTokenEditorScreen(mc.screen, stack));
    }
}
