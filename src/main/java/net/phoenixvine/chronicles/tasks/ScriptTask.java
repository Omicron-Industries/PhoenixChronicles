package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;

/**
 * Backs a task type registered from KubeJS via
 * {@code PhoenixTaskRegistry.registerScripted("mypack:my_task").onCompleted(...)...register()}.
 *
 * The script's callbacks (isCompletedFor/tryConsume/progressString/dependsOnInventory) are
 * registered ONCE per task TYPE (see {@link PhoenixTaskRegistry.ScriptTaskHandler}), not per quest
 * instance, so this class only carries the per-quest SNBT fields the script itself defined for
 * this particular task - exposed as raw NBT via {@link #getData()} so the script can read
 * whatever custom fields it declared (e.g. {@code task.getData().getInt("count")}) without this
 * mod needing a dedicated Java field for every possible script-defined task type.
 */
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

    /** Raw SNBT fields for this task instance - includes type/task_id/description too, harmlessly. */
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
