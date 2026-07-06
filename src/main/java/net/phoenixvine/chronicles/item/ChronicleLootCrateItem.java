package net.phoenixvine.chronicles.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestReward.WeightedReward;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * A consumable crate item that grants a set of serialized QuestRewards when right-clicked.
 *
 * NBT layout:
 * crate_title: String (display name suffix, e.g. "Starter Kit")
 * pick: int (0/absent = grant every reward; >0 = roll that many weighted-random
 * rewards from the pool at open time)
 * rewards: ListTag (CompoundTag entries — same format as quest reward SNBT, each
 * optionally carrying a "weight" int, default 1)
 *
 * Given as a reward via {@link net.phoenixvine.chronicles.model.QuestReward.LootCrateReward}.
 */
public class ChronicleLootCrateItem extends Item {

    public static final String TAG_TITLE = "crate_title";
    public static final String TAG_REWARDS = "rewards";
    public static final String TAG_PICK = "pick";

    private static final Random RANDOM = new Random();

    public ChronicleLootCrateItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        String title = (tag != null && tag.contains(TAG_TITLE)) ? tag.getString(TAG_TITLE) : "";
        String suffix = title.isEmpty() ? "" : ": §d" + title;
        return Component.literal("§6Chronicle Crate" + suffix);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_REWARDS)) {
            sp.sendSystemMessage(Component.literal("§cThis crate has no rewards configured."));
            return InteractionResultHolder.fail(stack);
        }

        ListTag rewardList = tag.getList(TAG_REWARDS, Tag.TAG_COMPOUND);
        int pickCount = tag.contains(TAG_PICK) ? tag.getInt(TAG_PICK) : 0;

        List<QuestReward> toGrant;
        if (pickCount > 0 && pickCount < rewardList.size()) {
            List<WeightedReward> pool = new java.util.ArrayList<>();
            for (int i = 0; i < rewardList.size(); i++) {
                CompoundTag entryTag = rewardList.getCompound(i);
                QuestReward reward = QuestReward.deserializeNBT(entryTag);
                if (reward != null) {
                    int weight = entryTag.contains("weight") ? entryTag.getInt("weight") : 1;
                    pool.add(new WeightedReward(reward, weight));
                }
            }
            toGrant = QuestReward.pickWeighted(pool, pickCount, RANDOM);
        } else {
            toGrant = new java.util.ArrayList<>();
            for (int i = 0; i < rewardList.size(); i++) {
                QuestReward reward = QuestReward.deserializeNBT(rewardList.getCompound(i));
                if (reward != null) toGrant.add(reward);
            }
        }

        int granted = 0;
        for (QuestReward reward : toGrant) {
            reward.grant(sp);
            granted++;
        }

        sp.sendSystemMessage(Component.literal("§6✦ §eCrate opened! §7(" + granted + " reward(s) granted)"));
        level.playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.5f, 1.2f);

        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        if (tag.contains(TAG_REWARDS)) {
            int count = tag.getList(TAG_REWARDS, Tag.TAG_COMPOUND).size();
            int pickCount = tag.contains(TAG_PICK) ? tag.getInt(TAG_PICK) : 0;
            String desc = (pickCount > 0 && pickCount < count) ?
                    pickCount + " of " + count + " reward(s) inside (weighted random)" : count + " reward(s) inside";
            lines.add(Component.literal("§8" + desc));
        }
        lines.add(Component.literal("§7Right-click to open").withStyle(ChatFormatting.ITALIC));
    }

    // ── Static helpers for building crate stacks ──────────────────────────────

    /** Legacy convenience: builds a crate that grants every listed reward when opened. */
    public static ItemStack build(String crateTitle, List<QuestReward> rewards) {
        return build(crateTitle, rewards.stream().map(r -> new WeightedReward(r, 1)).toList(), 0);
    }

    /** Builds a crate; if {@code pickCount > 0} the crate rolls that many weighted-random rewards on open. */
    public static ItemStack build(String crateTitle, List<WeightedReward> entries, int pickCount) {
        ItemStack stack = new ItemStack(ChronicleItems.CHRONICLE_LOOT_CRATE.get());
        CompoundTag tag = stack.getOrCreateTag();
        if (!crateTitle.isBlank()) tag.putString(TAG_TITLE, crateTitle);
        if (pickCount > 0) tag.putInt(TAG_PICK, pickCount);
        ListTag rewardList = new ListTag();
        for (WeightedReward e : entries) {
            CompoundTag entryTag = e.reward().serializeNBT();
            if (e.weight() != 1) entryTag.putInt("weight", e.weight());
            rewardList.add(entryTag);
        }
        tag.put(TAG_REWARDS, rewardList);
        return stack;
    }
}
