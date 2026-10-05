package net.phoenixvine.chronicles.common.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.phoenixvine.chronicles.common.model.QuestReward.ItemReward;
import net.phoenixvine.chronicles.common.model.QuestReward.WeightedReward;
import net.phoenixvine.chronicles.common.model.RewardTable;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class RewardTableChest {

    private RewardTableChest() {}

    public static final String META_KEY = "phoenix_chronicles_reward";
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]*$");

    public record Result(boolean ok, String message) {

        static Result ok(String m) {
            return new Result(true, m);
        }

        static Result fail(String m) {
            return new Result(false, m);
        }
    }

    public static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && !id.contains("..") && VALID_ID.matcher(id).matches();
    }

    @Nullable
    public static Container targetContainer(ServerPlayer player) {
        HitResult hit = player.pick(6.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return HopperBlockEntity.getContainerAt(player.level(), ((BlockHitResult) hit).getBlockPos());
    }

    public static Result export(RewardTable table, Container container) {
        List<ItemStack> stacks = new ArrayList<>();
        int skipped = 0;
        for (WeightedReward entry : table.entries()) {
            if (entry.reward() instanceof ItemReward ir && ir.getItem() != null) {
                stacks.add(toStack(ir, entry.weight()));
            } else {
                skipped++;
            }
        }
        if (stacks.isEmpty()) {
            return Result.fail("Table '" + table.id() + "' has no item entries to export.");
        }

        List<Integer> freeSlots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) freeSlots.add(i);
        }
        if (freeSlots.size() < stacks.size()) {
            return Result.fail("Needs " + stacks.size() + " empty slot(s), but the container only has " +
                    freeSlots.size() + " free. Nothing was exported.");
        }

        for (int i = 0; i < stacks.size(); i++) container.setItem(freeSlots.get(i), stacks.get(i));
        container.setChanged();

        return Result.ok("Exported " + stacks.size() + " item entr" + (stacks.size() == 1 ? "y" : "ies") +
                " from '" + table.id() + "'." +
                (skipped > 0 ? " " + skipped + " non-item entr" + (skipped == 1 ? "y" : "ies") +
                        " stay in the table." : ""));
    }

    private static ItemStack toStack(ItemReward ir, int weight) {
        ItemStack stack = new ItemStack(ir.getItem(),
                Math.min(ir.getCount(), Math.max(1, ir.getItem().getMaxStackSize())));

        CompoundTag tag = ir.getNbt() != null ? ir.getNbt().copy() : new CompoundTag();
        CompoundTag meta = new CompoundTag();
        meta.putInt("weight", weight);
        if (ir.getCount() != stack.getCount()) meta.putInt("count", ir.getCount());
        if (!ir.isPushToAe2()) meta.putBoolean("no_ae2", true);
        tag.put(META_KEY, meta);

        CompoundTag display = tag.getCompound("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        lore.add(StringTag.valueOf(weightLoreJson(weight)));
        display.put("Lore", lore);
        tag.put("display", display);

        stack.setTag(tag);
        return stack;
    }

    private static String weightLoreJson(int weight) {
        return Component.Serializer.toJson(Component.literal("Reward weight: " + weight)
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
    }

    public static Result importInto(Path configDir, String id, @Nullable Integer pick, Container container) {
        List<WeightedReward> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            items.add(fromStack(stack));
        }
        if (items.isEmpty()) {
            return Result.fail("The container is empty. Nothing was imported, and the table was left alone.");
        }

        RewardTable old = RewardTableRegistry.get(id);
        List<WeightedReward> entries = new ArrayList<>(items);
        int kept = 0;
        String displayName = "";
        int pickCount = pick != null ? pick : 0;
        if (old != null) {
            displayName = old.displayName().equals(old.id()) ? "" : old.displayName();
            if (pick == null) pickCount = old.pickCount();
            for (WeightedReward e : old.entries()) {
                if (!(e.reward() instanceof ItemReward)) {
                    entries.add(e);
                    kept++;
                }
            }
        }

        RewardTable table = new RewardTable(id, displayName, entries, pickCount);
        try {
            Path file = RewardTableRegistry.save(configDir, table);
            return Result.ok((old == null ? "Created" : "Updated") + " table '" + id + "' with " + items.size() +
                    " item entr" + (items.size() == 1 ? "y" : "ies") +
                    (kept > 0 ? " (+" + kept + " non-item kept)" : "") +
                    (pickCount > 0 ? ", pick " + pickCount : ", grants all") + ". Saved to " + file.getFileName() +
                    (old != null ? " (previous version kept as .bak)." : "."));
        } catch (java.io.IOException e) {
            return Result.fail("Couldn't write the table file: " + e.getMessage());
        }
    }

    private static WeightedReward fromStack(ItemStack stack) {
        CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : null;

        int weight = 1;
        int count = stack.getCount();
        boolean pushAe2 = true;
        if (tag != null && tag.contains(META_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag meta = tag.getCompound(META_KEY);
            if (meta.contains("weight")) weight = Math.max(1, meta.getInt("weight"));
            if (meta.contains("count")) count = Math.max(1, meta.getInt("count"));
            if (meta.getBoolean("no_ae2")) pushAe2 = false;
            tag.remove(META_KEY);
            stripWeightLore(tag, weight);
        }
        if (tag != null && tag.isEmpty()) tag = null;

        return new WeightedReward(new ItemReward(stack.getItem(), count, tag, pushAe2), weight);
    }

    private static void stripWeightLore(CompoundTag tag, int weight) {
        if (!tag.contains("display", Tag.TAG_COMPOUND)) return;
        CompoundTag display = tag.getCompound("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        String ours = weightLoreJson(weight);
        for (int i = lore.size() - 1; i >= 0; i--) {
            if (lore.getString(i).equals(ours)) {
                lore.remove(i);
                break;
            }
        }
        if (lore.isEmpty()) display.remove("Lore");
        else display.put("Lore", lore);
        if (display.isEmpty()) tag.remove("display");
        else tag.put("display", display);
    }
}
