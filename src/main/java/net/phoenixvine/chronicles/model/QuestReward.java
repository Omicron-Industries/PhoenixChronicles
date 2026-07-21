package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.event.PhoenixQuestScriptRewardEvent;
import net.phoenixvine.chronicles.registry.RewardTableRegistry;

import java.util.List;

public abstract class QuestReward {

    public enum RewardType {
        ITEM,
        XP,
        COMMAND,
        LOOT_TABLE,
        SCRIPT_EVENT,
        REWARD_TABLE,
        LOOT_CRATE
    }

    public abstract RewardType getType();

    public abstract Component getSummary();

    public abstract void grant(ServerPlayer player);

    public abstract CompoundTag serializeNBT();

    public record WeightedReward(QuestReward reward, int weight) {

        public WeightedReward {
            weight = Math.max(1, weight);
        }
    }

    public static List<QuestReward> pickWeighted(List<WeightedReward> pool, int n, java.util.Random random) {
        if (pool.isEmpty() || n <= 0) return java.util.List.of();
        List<WeightedReward> remaining = new java.util.ArrayList<>(pool);
        List<QuestReward> picked = new java.util.ArrayList<>();
        int count = Math.min(n, remaining.size());
        for (int i = 0; i < count; i++) {
            int totalWeight = remaining.stream().mapToInt(WeightedReward::weight).sum();
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            for (int j = 0; j < remaining.size(); j++) {
                cumulative += remaining.get(j).weight();
                if (roll < cumulative) {
                    picked.add(remaining.remove(j).reward());
                    break;
                }
            }
        }
        return picked;
    }

    public static QuestReward deserializeNBT(CompoundTag tag) {
        String type = tag.getString("type");
        return switch (type) {
            case "item" -> ItemReward.fromNBT(tag);
            case "xp" -> XPReward.fromNBT(tag);
            case "command" -> CommandReward.fromNBT(tag);
            case "loot_table" -> LootTableReward.fromNBT(tag);
            case "script_event" -> ScriptEventReward.fromNBT(tag);
            case "reward_table" -> RewardTableReward.fromNBT(tag);
            case "loot_crate" -> LootCrateReward.fromNBT(tag);
            default -> null;
        };
    }

    public static class ItemReward extends QuestReward {

        private final Item item;
        private final int count;

        public ItemReward(Item item, int count) {
            this.item = item;
            this.count = Math.max(1, count);
        }

        public Item getItem() {
            return item;
        }

        public int getCount() {
            return count;
        }

        @Override
        public RewardType getType() {
            return RewardType.ITEM;
        }

        @Override
        public Component getSummary() {
            return Component.literal(count + "x " + item.getDescription().getString());
        }

        @Override
        public void grant(ServerPlayer player) {
            ItemStack stack = new ItemStack(item, count);
            if (!player.addItem(stack)) {
                
                player.drop(stack, false);
            }
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "item");
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            tag.putString("item_id", id != null ? id.toString() : "minecraft:air");
            tag.putInt("count", count);
            return tag;
        }

        public static ItemReward fromNBT(CompoundTag tag) {
            String key = tag.contains("item_id") ? "item_id" : tag.contains("item") ? "item" : null;
            if (key == null) return null;
            ResourceLocation itemId = new ResourceLocation(tag.getString(key));
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) return null;
            int count = tag.contains("count") ? tag.getInt("count") : 1;
            return new ItemReward(item, count);
        }
    }

    public static class XPReward extends QuestReward {

        private final int levels;

        public XPReward(int levels) {
            this.levels = Math.max(1, levels);
        }

        public int getLevels() {
            return levels;
        }

        @Override
        public RewardType getType() {
            return RewardType.XP;
        }

        @Override
        public Component getSummary() {
            return Component.literal(levels + " XP level" + (levels != 1 ? "s" : ""));
        }

        @Override
        public void grant(ServerPlayer player) {
            player.giveExperienceLevels(levels);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "xp");
            tag.putInt("levels", levels);
            return tag;
        }

        public static XPReward fromNBT(CompoundTag tag) {
            return new XPReward(tag.getInt("levels"));
        }
    }

    public static class CommandReward extends QuestReward {

        private final String command;

        public CommandReward(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        @Override
        public RewardType getType() {
            return RewardType.COMMAND;
        }

        @Override
        public Component getSummary() {
            String preview = command.length() > 32 ? command.substring(0, 29) + "â€¦" : command;
            return Component.literal("/" + preview);
        }

        @Override
        public void grant(ServerPlayer player) {
            String resolved = command.replace("%player%", player.getName().getString());
            player.getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(4),
                    resolved);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "command");
            tag.putString("command", command);
            return tag;
        }

        public static CommandReward fromNBT(CompoundTag tag) {
            return new CommandReward(tag.getString("command"));
        }
    }

    public static class LootTableReward extends QuestReward {

        private final ResourceLocation lootTableId;

        public LootTableReward(ResourceLocation lootTableId) {
            this.lootTableId = lootTableId;
        }

        public ResourceLocation getLootTableId() {
            return lootTableId;
        }

        @Override
        public RewardType getType() {
            return RewardType.LOOT_TABLE;
        }

        @Override
        public Component getSummary() {
            return Component.literal("Loot: " + lootTableId.getPath());
        }

        @Override
        public void grant(ServerPlayer player) {
            LootTable table = player.getServer().getLootData().getLootTable(lootTableId);
            LootParams params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .create(LootContextParamSets.GIFT);
            table.getRandomItems(params).forEach(stack -> {
                if (!player.addItem(stack)) player.drop(stack, false);
            });
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "loot_table");
            tag.putString("loot_table", lootTableId.toString());
            return tag;
        }

        public static LootTableReward fromNBT(CompoundTag tag) {
            if (!tag.contains("loot_table")) return null;
            return new LootTableReward(new ResourceLocation(tag.getString("loot_table")));
        }
    }

    public static class ScriptEventReward extends QuestReward {

        private final String eventId;
        private final CompoundTag data;

        public ScriptEventReward(String eventId, CompoundTag data) {
            this.eventId = eventId;
            this.data = data != null ? data : new CompoundTag();
        }

        public String getEventId() {
            return eventId;
        }

        public CompoundTag getData() {
            return data;
        }

        @Override
        public RewardType getType() {
            return RewardType.SCRIPT_EVENT;
        }

        @Override
        public Component getSummary() {
            return Component.literal("Script: " + eventId);
        }

        @Override
        public void grant(ServerPlayer player) {
            MinecraftForge.EVENT_BUS.post(
                    new PhoenixQuestScriptRewardEvent(player, eventId, data.copy()));
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "script_event");
            tag.putString("event_id", eventId);
            if (!data.isEmpty()) tag.put("data", data.copy());
            return tag;
        }

        public static ScriptEventReward fromNBT(CompoundTag tag) {
            String id = tag.getString("event_id");
            if (id.isBlank()) return null;
            CompoundTag data = tag.contains("data") ? tag.getCompound("data") : new CompoundTag();
            return new ScriptEventReward(id, data);
        }
    }

    public static class RewardTableReward extends QuestReward {

        private final String tableId;

        public RewardTableReward(String tableId) {
            this.tableId = tableId;
        }

        public String getTableId() {
            return tableId;
        }

        @Override
        public RewardType getType() {
            return RewardType.REWARD_TABLE;
        }

        @Override
        public Component getSummary() {
            net.phoenixvine.chronicles.model.RewardTable table = RewardTableRegistry.get(tableId);
            return table != null ? table.getSummary() : Component.literal("Table: " + tableId + " (not loaded)");
        }

        @Override
        public void grant(ServerPlayer player) {
            net.phoenixvine.chronicles.model.RewardTable table = RewardTableRegistry.get(tableId);
            if (table != null) {
                table.grant(player);
            } else {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "[Phoenix Chronicles] Reward table '{}' not found â€” skipping reward for {}",
                        tableId, player.getName().getString());
            }
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "reward_table");
            tag.putString("table_id", tableId);
            return tag;
        }

        public static RewardTableReward fromNBT(CompoundTag tag) {
            String id = tag.getString("table_id");
            return id.isBlank() ? null : new RewardTableReward(id);
        }
    }

    public static class LootCrateReward extends QuestReward {

        private final String crateTitle;
        private final java.util.List<WeightedReward> innerEntries;
        private final int pickCount;

        public LootCrateReward(String crateTitle, java.util.List<WeightedReward> innerEntries, int pickCount) {
            this.crateTitle = crateTitle != null ? crateTitle : "";
            this.innerEntries = innerEntries != null ? innerEntries : java.util.List.of();
            this.pickCount = Math.max(0, pickCount);
        }

        public LootCrateReward(String crateTitle, java.util.List<QuestReward> innerRewards) {
            this(crateTitle, innerRewards == null ? java.util.List.of() :
                    innerRewards.stream().map(r -> new WeightedReward(r, 1)).toList(), 0);
        }

        public String getCrateTitle() {
            return crateTitle;
        }

        public java.util.List<QuestReward> getInnerRewards() {
            return innerEntries.stream().map(WeightedReward::reward).toList();
        }

        public java.util.List<WeightedReward> getInnerEntries() {
            return innerEntries;
        }

        public int getPickCount() {
            return pickCount;
        }

        @Override
        public RewardType getType() {
            return RewardType.LOOT_CRATE;
        }

        @Override
        public Component getSummary() {
            String pickPart = pickCount > 0 && pickCount < innerEntries.size() ?
                    pickCount + "/" + innerEntries.size() + " weighted random" :
                    innerEntries.size() + " reward" + (innerEntries.size() == 1 ? "" : "s");
            return Component.literal(
                    "§6Loot crate" + (crateTitle.isEmpty() ? "" : ": §d" + crateTitle) + " §8(" + pickPart + ")");
        }

        @Override
        public void grant(ServerPlayer player) {
            ItemStack crate = net.phoenixvine.chronicles.item.ChronicleLootCrateItem.build(
                    crateTitle, innerEntries, pickCount);
            if (!player.addItem(crate)) player.drop(crate, false);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "loot_crate");
            if (!crateTitle.isEmpty()) tag.putString("crate_title", crateTitle);
            if (pickCount > 0) tag.putInt("pick", pickCount);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (WeightedReward e : innerEntries) {
                CompoundTag entryTag = e.reward().serializeNBT();
                if (e.weight() != 1) entryTag.putInt("weight", e.weight());
                list.add(entryTag);
            }
            tag.put("rewards", list);
            return tag;
        }

        public static LootCrateReward fromNBT(CompoundTag tag) {
            String title = tag.contains("crate_title") ? tag.getString("crate_title") : "";
            int pickCount = tag.contains("pick") ? tag.getInt("pick") : 0;
            java.util.List<WeightedReward> inner = new java.util.ArrayList<>();
            if (tag.contains("rewards")) {
                net.minecraft.nbt.ListTag list = tag.getList("rewards", net.minecraft.nbt.Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entryTag = list.getCompound(i);
                    QuestReward r = QuestReward.deserializeNBT(entryTag);
                    if (r != null) {
                        int weight = entryTag.contains("weight") ? entryTag.getInt("weight") : 1;
                        inner.add(new WeightedReward(r, weight));
                    }
                }
            }
            return new LootCrateReward(title, inner, pickCount);
        }
    }
}

