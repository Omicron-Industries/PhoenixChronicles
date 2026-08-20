package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.integration.conflux.ConfluxCompat;
import net.phoenixvine.chronicles.model.QuestTask;

public class ConfluxResearchTask extends QuestTask {

    private boolean flagMode = false;
    private ResourceLocation nodeId = ResourceLocation.fromNamespaceAndPath("phoenixcore", "unknown");
    private String flag = "";

    public ConfluxResearchTask(ResourceLocation taskId, Component description) {
        super(taskId, description);
    }

    public boolean isFlagMode() {
        return flagMode;
    }

    public void setFlagMode(boolean flagMode) {
        this.flagMode = flagMode;
    }

    public ResourceLocation getNodeId() {
        return nodeId;
    }

    public void setNodeId(ResourceLocation nodeId) {
        this.nodeId = nodeId;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag == null ? "" : flag;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return flagMode ? ConfluxCompat.hasFlag(player, flag) : ConfluxCompat.isUnlocked(player, nodeId);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "conflux_research");
        tag.putBoolean("flag_mode", flagMode);
        tag.putString("node_id", nodeId.toString());
        tag.putString("flag", flag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("flag_mode")) this.flagMode = nbt.getBoolean("flag_mode");
        if (nbt.contains("node_id")) {
            ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString("node_id"));
            if (parsed != null) this.nodeId = parsed;
        }
        if (nbt.contains("flag")) this.flag = nbt.getString("flag");
    }
}
