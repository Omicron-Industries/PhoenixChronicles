package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.event.PhoenixQuestScriptRewardEvent;
import net.phoenixvine.chronicles.registry.RewardTableRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class QuestReward {

    public enum RewardType {
        ITEM,
        XP,
        COMMAND,
        LOOT_TABLE,
        SCRIPT_EVENT,
        REWARD_TABLE,
        LOOT_CRATE,
        CONFLUX_UNLOCK,
        OPEN_SCREEN
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

    public static List<QuestReward> pickWeighted(List<WeightedReward> pool, int n, Random random) {
        if (pool == null || pool.isEmpty() || n <= 0) return List.of();
        List<WeightedReward> remaining = new ArrayList<>(pool);
        List<QuestReward> picked = new ArrayList<>();
        int count = Math.min(n, remaining.size());
        for (int i = 0; i < count; i++) {
            int totalWeight = remaining.stream().mapToInt(WeightedReward::weight).sum();
            if (totalWeight <= 0) break;
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

    public static void giveItem(ServerPlayer player, Item item, int count, CompoundTag nbt) {
        if (player == null || item == null || item == Items.AIR || count <= 0) return;
        int remaining = count;
        int maxStack = Math.max(1, item.getMaxStackSize());
        while (remaining > 0) {
            int batch = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, batch);
            if (nbt != null && !nbt.isEmpty()) {
                stack.setTag(nbt.copy());
            }
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }
    }

    public static QuestReward deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("type")) return null;
        String type = tag.getString("type");
        return switch (type) {
            case "item" -> ItemReward.fromNBT(tag);
            case "xp" -> XPReward.fromNBT(tag);
            case "command" -> CommandReward.fromNBT(tag);
            case "loot_table" -> LootTableReward.fromNBT(tag);
            case "script_event" -> ScriptEventReward.fromNBT(tag);
            case "reward_table" -> RewardTableReward.fromNBT(tag);
            case "loot_crate" -> LootCrateReward.fromNBT(tag);
            case "conflux_unlock" -> ConfluxUnlockReward.fromNBT(tag);
            case "open_screen" -> OpenScreenReward.fromNBT(tag);
            default -> null;
        };
    }

    public static class ItemReward extends QuestReward {

        private final Item item;
        private final int count;
        private final CompoundTag nbt;

        public ItemReward(Item item, int count) {
            this(item, count, null);
        }

        public ItemReward(Item item, int count, CompoundTag nbt) {
            this.item = item;
            this.count = Math.max(1, count);
            this.nbt = (nbt != null && !nbt.isEmpty()) ? nbt.copy() : null;
        }

        public Item getItem() {
            return item;
        }

        public int getCount() {
            return count;
        }

        public CompoundTag getNbt() {
            return nbt;
        }

        @Override
        public RewardType getType() {
            return RewardType.ITEM;
        }

        @Override
        public Component getSummary() {
            if (item == null || item == Items.AIR) return Component.literal(count + "x Unknown Item");
            return Component.literal(count + "x ").append(item.getDescription());
        }

        @Override
        public void grant(ServerPlayer player) {
            giveItem(player, item, count, nbt);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "item");
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            tag.putString("item_id", id != null ? id.toString() : "minecraft:air");
            tag.putInt("count", count);
            if (nbt != null && !nbt.isEmpty()) tag.put("nbt", nbt.copy());
            return tag;
        }

        public static ItemReward fromNBT(CompoundTag tag) {
            String key = tag.contains("item_id") ? "item_id" : tag.contains("item") ? "item" : null;
            if (key == null) return null;

            ResourceLocation itemId = ResourceLocation.tryParse(tag.getString(key));
            if (itemId == null) return null;

            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) return null;

            int count = tag.contains("count") ? tag.getInt("count") : 1;
            CompoundTag nbt = tag.contains("nbt") ? tag.getCompound("nbt") : null;
            return new ItemReward(item, count, nbt);
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
            if (player != null) player.giveExperienceLevels(levels);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "xp");
            tag.putInt("levels", levels);
            return tag;
        }

        public static XPReward fromNBT(CompoundTag tag) {
            return new XPReward(tag.contains("levels") ? tag.getInt("levels") : 1);
        }
    }

    public static class CommandReward extends QuestReward {

        private final String command;

        public CommandReward(String command) {
            this.command = command != null ? command : "";
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
            String preview = command.length() > 32 ? command.substring(0, 29) + "…" : command;
            return Component.literal("/" + preview);
        }

        @Override
        public void grant(ServerPlayer player) {
            if (player == null || command.isBlank()) return;
            String resolved = command.replace("%player%", player.getName().getString());
            if (player.getServer() != null) {
                player.getServer().getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(4),
                        resolved);
            }
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "command");
            tag.putString("command", command);
            return tag;
        }

        public static CommandReward fromNBT(CompoundTag tag) {
            if (!tag.contains("command")) return null;
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
            return Component.literal("Loot: " + (lootTableId != null ? lootTableId.getPath() : "unknown"));
        }

        @Override
        public void grant(ServerPlayer player) {
            if (player == null || lootTableId == null || player.getServer() == null) return;
            LootTable table = player.getServer().getLootData().getLootTable(lootTableId);
            if (table == null || table == LootTable.EMPTY) return;

            LootParams params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .create(LootContextParamSets.GIFT);

            table.getRandomItems(params).forEach(stack -> {
                if (!stack.isEmpty()) {
                    if (!player.addItem(stack)) player.drop(stack, false);
                }
            });
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "loot_table");
            tag.putString("loot_table", lootTableId != null ? lootTableId.toString() : "minecraft:empty");
            return tag;
        }

        public static LootTableReward fromNBT(CompoundTag tag) {
            if (!tag.contains("loot_table")) return null;
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("loot_table"));
            return parsed != null ? new LootTableReward(parsed) : null;
        }
    }

    public static class ScriptEventReward extends QuestReward {

        private final String eventId;
        private final CompoundTag data;

        public ScriptEventReward(String eventId, CompoundTag data) {
            this.eventId = eventId != null ? eventId : "";
            this.data = data != null ? data.copy() : new CompoundTag();
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
            if (player == null || eventId.isBlank()) return;
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
            this.tableId = tableId != null ? tableId : "";
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
            if (player == null || tableId.isBlank()) return;
            net.phoenixvine.chronicles.model.RewardTable table = RewardTableRegistry.get(tableId);
            if (table != null) {
                table.grant(player);
            } else {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "[Phoenix Chronicles] Reward table '{}' not found: skipping reward for {}",
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
        private final List<WeightedReward> innerEntries;
        private final int pickCount;

        public LootCrateReward(String crateTitle, List<WeightedReward> innerEntries, int pickCount) {
            this.crateTitle = crateTitle != null ? crateTitle : "";
            this.innerEntries = innerEntries != null ? innerEntries : List.of();
            this.pickCount = Math.max(0, pickCount);
        }

        public LootCrateReward(String crateTitle, List<QuestReward> innerRewards) {
            this(crateTitle, innerRewards == null ? List.of() :
                    innerRewards.stream().map(r -> new WeightedReward(r, 1)).toList(), 0);
        }

        public String getCrateTitle() {
            return crateTitle;
        }

        public List<QuestReward> getInnerRewards() {
            return innerEntries.stream().map(WeightedReward::reward).toList();
        }

        public List<WeightedReward> getInnerEntries() {
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
            if (player == null) return;
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
            ListTag list = new ListTag();
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
            List<WeightedReward> inner = new ArrayList<>();
            if (tag.contains("rewards")) {
                ListTag list = tag.getList("rewards", Tag.TAG_COMPOUND);
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

    public static class ConfluxUnlockReward extends QuestReward {

        private final boolean flagMode;
        private final ResourceLocation nodeId;
        private final String flag;

        public ConfluxUnlockReward(ResourceLocation nodeId) {
            this.flagMode = false;
            this.nodeId = nodeId != null ? nodeId : ResourceLocation.fromNamespaceAndPath("phoenixcore", "unknown");
            this.flag = "";
        }

        public ConfluxUnlockReward(String flag) {
            this.flagMode = true;
            this.nodeId = ResourceLocation.fromNamespaceAndPath("phoenixcore", "unknown");
            this.flag = flag != null ? flag : "";
        }

        public boolean isFlagMode() {
            return flagMode;
        }

        public ResourceLocation getNodeId() {
            return nodeId;
        }

        public String getFlag() {
            return flag;
        }

        @Override
        public RewardType getType() {
            return RewardType.CONFLUX_UNLOCK;
        }

        @Override
        public Component getSummary() {
            return Component.literal("Conflux: " + (flagMode ? flag : nodeId.toString()));
        }

        @Override
        public void grant(ServerPlayer player) {
            if (player == null) return;
            if (flagMode) {
                net.phoenixvine.chronicles.integration.conflux.ConfluxCompat.grantFlag(player, flag);
            } else {
                net.phoenixvine.chronicles.integration.conflux.ConfluxCompat.grantUnlock(player, nodeId);
            }
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "conflux_unlock");
            tag.putBoolean("flag_mode", flagMode);
            tag.putString("node_id", nodeId.toString());
            tag.putString("flag", flag);
            return tag;
        }

        public static ConfluxUnlockReward fromNBT(CompoundTag tag) {
            boolean flagMode = tag.contains("flag_mode") && tag.getBoolean("flag_mode");
            if (flagMode) {
                return new ConfluxUnlockReward(tag.getString("flag"));
            }
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("node_id"));
            return new ConfluxUnlockReward(
                    parsed != null ? parsed : ResourceLocation.fromNamespaceAndPath("phoenixcore", "unknown"));
        }
    }

    public static class OpenScreenReward extends QuestReward {

        private final ResourceLocation screenId;

        public OpenScreenReward(ResourceLocation screenId) {
            this.screenId = screenId;
        }

        public ResourceLocation getScreenId() {
            return screenId;
        }

        @Override
        public RewardType getType() {
            return RewardType.OPEN_SCREEN;
        }

        @Override
        public Component getSummary() {
            return Component.literal("Opens: " + (screenId != null ? screenId : "unknown"));
        }

        @Override
        public void grant(ServerPlayer player) {
            if (player == null || screenId == null) return;
            net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new net.phoenixvine.chronicles.network.packet.S2COpenExternalScreenPacket(screenId));
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", "open_screen");
            tag.putString("screen_id", screenId != null ? screenId.toString() : "minecraft:empty");
            return tag;
        }

        public static OpenScreenReward fromNBT(CompoundTag tag) {
            if (!tag.contains("screen_id")) return null;
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("screen_id"));
            return parsed != null ? new OpenScreenReward(parsed) : null;
        }
    }
}
