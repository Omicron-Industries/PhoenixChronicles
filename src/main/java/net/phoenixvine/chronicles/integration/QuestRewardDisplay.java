package net.phoenixvine.chronicles.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class QuestRewardDisplay {

    private QuestRewardDisplay() {}

    private static final int MAX_LISTED_OPTIONS = 8;

    public record Entry(QuestReward reward, ItemStack stack, @Nullable Fluid fluid, int fluidAmountMb,
                        @Nullable CompoundTag fluidNbt, List<Component> tooltip) {

        public boolean isFluid() {
            return fluid != null;
        }
    }

    public static List<Entry> entries(QuestNode node) {
        List<Entry> out = new ArrayList<>();
        if (node == null || node.getRewards() == null) return out;
        for (QuestReward reward : node.getRewards()) {
            if (net.phoenixvine.chronicles.client.util.HiddenParts.rewardTargetHidden(node, reward)) continue;
            Entry e = entryFor(reward);
            if (e != null) out.add(e);
        }
        return out;
    }

    @Nullable
    public static Entry entryFor(@Nullable QuestReward reward) {
        if (reward == null) return null;

        if (reward instanceof QuestReward.ItemReward ir) {
            Item item = ir.getItem();
            if (item == null || item == Items.AIR) return null;
            ItemStack stack = new ItemStack(item, ir.getCount());
            if (ir.getNbt() != null && !ir.getNbt().isEmpty()) stack.setTag(ir.getNbt().copy());
            return new Entry(reward, stack, null, 0, null, ae2Note(ir.isPushToAe2()));
        }

        if (reward instanceof QuestReward.FluidReward fr) {
            Fluid fluid = fr.getFluid();
            if (fluid == null || fluid == Fluids.EMPTY) return null;
            return new Entry(reward, ItemStack.EMPTY, fluid, fr.getAmountMb(), fr.getNbt(),
                    ae2Note(fr.isPushToAe2()));
        }

        if (reward instanceof QuestReward.ChoiceBoxReward box) {
            List<Component> tip = new ArrayList<>();
            tip.add(box.getSummary());

            if (box.getMode() != QuestReward.ChoiceBoxReward.Mode.LOOTBOX) {
                int shown = 0;
                for (QuestReward opt : box.getOptions()) {
                    if (shown++ >= MAX_LISTED_OPTIONS) {
                        tip.add(Component.literal("§8… and " + (box.getOptions().size() - MAX_LISTED_OPTIONS) +
                                " more"));
                        break;
                    }
                    tip.add(Component.literal("§7• ").append(opt.getSummary()));
                }
            }
            return new Entry(reward, new ItemStack(Items.CHEST), null, 0, null, tip);
        }

        ItemStack icon;
        if (reward instanceof QuestReward.XPReward xp) {
            icon = new ItemStack(Items.EXPERIENCE_BOTTLE, Math.max(1, xp.getLevels()));
        } else if (reward instanceof QuestReward.LootTableReward || reward instanceof QuestReward.RewardTableReward) {
            icon = new ItemStack(Items.BUNDLE);
        } else if (reward instanceof QuestReward.LootCrateReward) {
            Item crate = ForgeRegistries.ITEMS
                    .getValue(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "loot_crate"));
            icon = new ItemStack(crate != null && crate != Items.AIR ? crate : Items.CHEST);
        } else if (reward instanceof QuestReward.CommandReward || reward instanceof QuestReward.ScriptEventReward) {
            icon = new ItemStack(Items.COMMAND_BLOCK);
        } else {
            icon = new ItemStack(Items.PAPER);
        }
        return new Entry(reward, icon, null, 0, null, List.of(reward.getSummary()));
    }

    private static List<Component> ae2Note(boolean pushToAe2) {
        if (!pushToAe2 || !AE2Compat.isAvailable()) return List.of();
        return List.of(Component.literal("§bDelivered to your linked AE2 network when available"));
    }
}
