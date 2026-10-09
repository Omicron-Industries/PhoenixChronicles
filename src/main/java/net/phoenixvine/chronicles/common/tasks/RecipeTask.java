package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

import org.jetbrains.annotations.Nullable;

public class RecipeTask extends QuestTask {

    private ResourceLocation recipeType;
    @Nullable
    private ResourceLocation recipeId;
    private int required;

    public RecipeTask(ResourceLocation taskId, Component description, ResourceLocation recipeType,
                      @Nullable ResourceLocation recipeId, int required) {
        super(taskId, description);
        this.recipeType = recipeType;
        this.recipeId = recipeId;
        this.required = Math.max(1, required);
    }

    public ResourceLocation getRecipeType() {
        return recipeType;
    }

    @Nullable
    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    public int getRequired() {
        return required;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("current") >= required;
    }

    @Override
    public String getProgressString(Player player) {
        int current = TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("current");
        return Math.min(current, required) + "/" + required;
    }

    public boolean matches(ResourceLocation finishedType, @Nullable ResourceLocation finishedId) {
        boolean sameType = recipeType.equals(finishedType) ||
                (recipeType.getNamespace().equals("minecraft") && recipeType.getPath().equals(finishedType.getPath()));
        if (!sameType) return false;
        return recipeId == null || recipeId.equals(finishedId);
    }

    public void onRecipeCompleted(Player player, int times) {
        TaskProgressAccess.with(player, getTaskId(), nbt -> {
            int current = nbt.getInt("current");
            if (current >= required) return;
            nbt.putInt("current", Math.min(required, current + Math.max(1, times)));
        });
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "recipe");
        tag.putString("recipe_type", recipeType.toString());
        if (recipeId != null) tag.putString("recipe_id", recipeId.toString());
        tag.putInt("required", required);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ResourceLocation parsedType = ResourceLocation.tryParse(nbt.getString("recipe_type"));
        if (parsedType != null) this.recipeType = parsedType;
        this.recipeId = nbt.contains("recipe_id") ? ResourceLocation.tryParse(nbt.getString("recipe_id")) : null;
        if (nbt.contains("required")) this.required = Math.max(1, nbt.getInt("required"));
    }
}
