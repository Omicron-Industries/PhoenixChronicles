package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;

import lombok.Getter;
import lombok.Setter;

/**
 * Base abstract class for all Chronicle Quest Objectives.
 * Migrated to be stateless to ensure full multi-player capability isolation.
 */
@Getter
public abstract class QuestTask {

    private final ResourceLocation taskId;
    private Component description;
    @Setter
    private boolean optional = false;

    // REMOVED: protected boolean completed = false;
    // Tasks are singletons loaded from server files; they must not hold local state variables.

    public QuestTask(ResourceLocation taskId, Component description) {
        this.taskId = taskId;
        this.description = description;
    }

    /**
     * Resolves as a REAL translation key ("phoenix_chronicles.task.<taskId path>") when one is
     * registered, so each player's own client resolves it via their own selected game language
     * (see net.phoenixvine.chronicles.client.ChroniclesLangPack). Falls back to the baked
     * default when no entry exists yet. Keyed by the task's own stable id rather than its index
     * within the owning quest, so reordering tasks doesn't misattach translations.
     */
    public Component getDescription() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            String override = net.phoenixvine.chronicles.client.ClientTextOverrides.get(langKey());
            if (override != null) return Component.literal(override);
            Component d = ClientLangLookup.resolve(langKey());
            if (d != null) return d;
        }
        return description;
    }

    /**
     * The baked default text, ignoring any translation. Use this when re-persisting structural
     * data to SNBT so an active translation is never silently baked over the original-language
     * default just because the quest was re-saved for an unrelated edit.
     */
    public Component getDescriptionRaw() {
        return description;
    }

    /**
     * See {@link net.phoenixvine.chronicles.client.ClientTextOverrides}'s own doc - makes this
     * client's own edit visible immediately without any translation-resolution reload.
     */
    public void setDescription(Component description) {
        this.description = description;
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.phoenixvine.chronicles.client.ClientTextOverrides.put(langKey(), description.getString());
    }

    private String langKey() {
        return "phoenix_chronicles.task." + taskId.getPath().replace('/', '.');
    }

    /**
     * Isolated in its own nested class so the dedicated server JVM never loads
     * {@code net.minecraft.client.resources.language.I18n} - callers must check
     * {@code FMLEnvironment.dist == Dist.CLIENT} before reaching this class.
     */
    private static class ClientLangLookup {

        static Component resolve(String key) {
            return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : null;
        }
    }

    /**
     * Called once per server tick for each active (non-completed) quest the player has.
     * Override in tasks that need to poll world state (biome, structure, etc.).
     * The default is a no-op; keep overrides cheap.
     */
    public void onTick(Player player) {}

    /**
     * Context-driven completion check.
     * Every custom task must evaluate this against a specific player's metrics or capability data.
     */
    public abstract boolean isCompletedFor(Player player);

    /**
     * Called once, when the player claims this quest's rewards (see
     * QuestProgressTracker#grantRewards / #grantChosenReward) - the single point every
     * "consume"-flagged task's own tryConsume(Player) implementation was documented to be called
     * from ("Call this when claiming rewards"), but which nothing in the codebase actually
     * invoked: item/fluid tasks never withdrew the goods, kill/stat/terminal tasks never reset
     * their repeatable-tracking baseline. Every task with real consume behavior (item/fluid
     * withdrawal, AE2 network extraction, kill-count/stat-baseline resets for repeatable quests)
     * overrides this; the default here is a no-op so tasks with no such concept (biome, checkmark,
     * info, advancement, etc.) don't need to implement anything.
     */
    public void tryConsume(Player player) {}

    /**
     * True for tasks whose {@link #isCompletedFor} result can ONLY change when the player's
     * inventory contents change (item/tag/fluid/energy-in-item tasks) - overriding this lets
     * QuestProgressTracker's per-tick poll skip re-scanning these when it already knows the
     * inventory hasn't changed since the last tick, instead of re-walking every slot and
     * capability lookup 20 times a second per active task regardless of whether anything could
     * possibly have changed. Tasks gated on something else entirely (biome, structure, stats,
     * advancements, equipped enchantments, block-break/interact events, etc.) must NOT override
     * this to true - their result can change independent of inventory contents, so skipping them
     * on an unchanged inventory would silently delay/miss real completions.
     */
    public boolean dependsOnInventory() {
        return false;
    }

    /**
     * Cheap "already latched complete" read for the shared sticky/"completed" TaskProgressAccess
     * flag every dependsOnInventory() task uses - lets the tracker's per-tick skip check whether
     * a task it's about to bypass (because inventory didn't change) was ALREADY sticky-complete
     * from an earlier real scan, without needing to call the potentially-expensive
     * isCompletedFor to find out. Without this, a task that latched complete on some earlier
     * tick would read as "not done" on every subsequent tick where inventory happens to be
     * unchanged, which is wrong whenever some OTHER task in the same quest is what's actually
     * still blocking completion - the multi-task math needs this one's real (already-cached)
     * answer, not an assumed false. Tasks that don't use this NBT convention simply never
     * override dependsOnInventory() to true, so this method is never consulted for them.
     */
    public boolean isStickyCompleteCached(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    /**
     * Optional numeric progress label shown in the HUD and detail screen (e.g. "7/10").
     * Returns null when there is no meaningful partial progress to display (e.g. one-shot tasks).
     */
    public String getProgressString(Player player) {
        return null;
    }

    /** Optional item icon shown next to this task in the detail screen. Return null to show none. */
    public ResourceLocation getDisplayItemId() {
        return null;
    }

    // REMOVED: public boolean isCompleted()
    // REMOVED: public void setCompleted(boolean completed)

    /**
     * Serializes static structural task data (e.g., Target item ID, Required quantity) to NBT.
     */
    public abstract CompoundTag serializeNBT();

    /**
     * Deserializes structural settings back into the task configuration.
     */
    public abstract void deserializeNBT(CompoundTag nbt);
}
