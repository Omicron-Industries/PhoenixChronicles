package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;

import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class QuestTask {

    private final ResourceLocation taskId;
    private Component description;
    @Setter
    private boolean optional = false;

    public QuestTask(ResourceLocation taskId, Component description) {
        this.taskId = taskId;
        this.description = description;
    }

    public Component getDescription() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            String override = net.phoenixvine.chronicles.client.ClientTextOverrides.get(langKey());
            if (override != null) return Component.literal(override);
            Component d = ClientLangLookup.resolve(langKey());
            if (d != null) return d;
        }
        return description;
    }

    public Component getDescriptionRaw() {
        return description;
    }

    public void setDescription(Component description) {
        this.description = description;
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.phoenixvine.chronicles.client.ClientTextOverrides.put(langKey(), description.getString());
    }

    private String langKey() {
        return "phoenix_chronicles.task." + taskId.getPath().replace('/', '.');
    }

    private static class ClientLangLookup {

        static Component resolve(String key) {
            return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : null;
        }
    }

    public void onTick(Player player) {}

    public abstract boolean isCompletedFor(Player player);

    public void tryConsume(Player player) {}

    public boolean dependsOnInventory() {
        return false;
    }

    public boolean isStickyCompleteCached(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    public String getProgressString(Player player) {
        return null;
    }

    public ResourceLocation getDisplayItemId() {
        return null;
    }

    public abstract CompoundTag serializeNBT();

    public abstract void deserializeNBT(CompoundTag nbt);
}

