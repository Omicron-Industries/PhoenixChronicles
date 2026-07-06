package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;
import lombok.Setter;

/**
 * Base abstract class for all Chronicle Quest Objectives.
 * Migrated to be stateless to ensure full multi-player capability isolation.
 */
@Getter
public abstract class QuestTask {

    private final ResourceLocation taskId;
    @Setter
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
