package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;

public class ScriptTask extends QuestTask {

    private final String typeId;
    private CompoundTag data = new CompoundTag();

    public ScriptTask(ResourceLocation taskId, Component description, String typeId) {
        super(taskId, description);
        this.typeId = typeId;
    }

    public String getTypeId() {
        return typeId;
    }

    public CompoundTag getData() {
        return data;
    }

    private PhoenixTaskRegistry.ScriptTaskHandler handler() {
        PhoenixTaskRegistry.ScriptTaskHandler h = PhoenixTaskRegistry.getScriptHandler(typeId);
        if (h == null) {
            throw new IllegalStateException("No script handler registered for scripted task type '" + typeId +
                    "' - was it registered via PhoenixTaskRegistry.registerScripted(...)?");
        }
        return h;
    }

    @Override
    public boolean dependsOnInventory() {
        return handler().dependsOnInventory;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return handler().isCompletedFor.test(this, player);
    }

    @Override
    public void tryConsume(Player player) {
        handler().tryConsume.accept(this, player);
    }

    @Override
    public String getProgressString(Player player) {
        String s = handler().progressString.apply(this, player);
        return s != null ? s : (isCompletedFor(player) ? "Done" : "Pending");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = data.copy();
        tag.putString("type", typeId);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.data = nbt.copy();
    }
}

