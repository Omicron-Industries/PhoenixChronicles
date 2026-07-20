package net.phoenixvine.chronicles.tasks;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Checks that a player has sufficient energy stored, supporting both Forge Energy (FE/RF)
 * and GregTech EU as fully separate unit systems.
 *
 * Sources:
 * INVENTORY – sum FE across all IEnergyStorage items in player inventory (FE only)
 * HELD – FE in the currently-held item only (FE only)
 * BLOCK – energy stored in the last right-clicked block entity; reads BOTH FE and GTM EU,
 * matched against whichever energy type this task requires.
 * Populated once per right-click via {@link #onBlockRightClicked} — no per-tick polling.
 *
 * For BLOCK source the quest UI shows the cached reading from the most recent interaction.
 * Players just right-click their battery box / capacitor bank / energy hatch and then open the quest.
 */
public class EnergyStorageTask extends QuestTask {

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum EnergyType {
        FE,   // Forge Energy / RF
        EU,   // GregTech EU
        ANY   // whichever is non-zero (FE checked first)
    }

    public enum Source {
        INVENTORY,   // sum FE in all player inventory items
        HELD,        // FE in mainhand item only
        BLOCK        // cached from last right-clicked energy block
    }

    // ── Per-session block energy cache ────────────────────────────────────────
    // Key: player UUID → long[2] { fe_stored, eu_stored }
    // Populated by onBlockRightClicked(), never polled per-tick.
    private static final Map<UUID, long[]> blockCache = new HashMap<>();

    public static void onBlockRightClicked(Player player, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        long fe = 0L, eu = 0L;

        // Forge Energy
        IEnergyStorage feCap = be.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (feCap != null) fe = feCap.getEnergyStored();

        // GregTech EU (all faces — null = default direction)
        IEnergyContainer euCap = GTCapabilityHelper.getEnergyContainer(level, pos, null);
        if (euCap != null) eu = euCap.getEnergyStored();

        if (fe > 0 || eu > 0) {
            blockCache.put(player.getUUID(), new long[] { fe, eu });
        }
    }

    /** Call on player disconnect / world unload to avoid stale data. */
    public static void clearBlockCache(UUID playerId) {
        blockCache.remove(playerId);
    }

    // ── Task state ────────────────────────────────────────────────────────────

    private long requiredEnergy;
    private EnergyType energyType;
    private Source source;
    /**
     * Off by default, unlike the item/fluid hold-tasks - an energy reading is inherently
     * transient (a battery drains, a machine's buffer empties on its own), so continuously
     * re-checking is usually the correct default here. Available as an opt-in for packs that
     * specifically want "reached this threshold once" semantics instead.
     */
    private boolean sticky = false;

    public EnergyStorageTask(ResourceLocation taskId, Component description,
                             long requiredEnergy, EnergyType energyType, Source source) {
        super(taskId, description);
        this.requiredEnergy = Math.max(1, requiredEnergy);
        this.energyType = energyType != null ? energyType : EnergyType.FE;
        this.source = source != null ? source : Source.INVENTORY;
    }

    public long getRequiredEnergy() {
        return requiredEnergy;
    }

    public EnergyType getEnergyType() {
        return energyType;
    }

    public Source getSource() {
        return source;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    /**
     * BLOCK source is populated once per right-click, never per-tick - it must NOT be gated
     * behind "inventory changed" or it would never re-check at all outside a right-click.
     * INVENTORY/HELD read straight from the stack's own NBT-backed energy capability, which the
     * per-tick inventory fingerprint (hashes each tracked slot's current tag content) already
     * picks up regardless of what caused it to change - including a passive in-place recharge
     * that mutates an item's NBT without any slot/count change.
     */
    @Override
    public boolean dependsOnInventory() {
        return source != Source.BLOCK;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (getStored(player) >= requiredEnergy) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public String getProgressString(Player player) {
        String unit = (energyType == EnergyType.EU) ? "EU" : "FE";
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return format(requiredEnergy, unit) + " / " + format(requiredEnergy, unit);
        long stored = Math.min(getStored(player), requiredEnergy);
        return format(stored, unit) + " / " + format(requiredEnergy, unit);
    }

    private long getStored(Player player) {
        return switch (source) {
            case INVENTORY -> inventoryFE(player);
            case HELD -> heldFE(player);
            case BLOCK -> blockStored(player);
        };
    }

    private long inventoryFE(Player player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            total += stack.getCapability(ForgeCapabilities.ENERGY)
                    .map(IEnergyStorage::getEnergyStored).orElse(0);
        }
        return total;
    }

    private long heldFE(Player player) {
        return player.getMainHandItem()
                .getCapability(ForgeCapabilities.ENERGY)
                .map(IEnergyStorage::getEnergyStored).orElse(0);
    }

    private long blockStored(Player player) {
        long[] cache = blockCache.get(player.getUUID());
        if (cache == null) return 0L;
        return switch (energyType) {
            case FE -> cache[0];
            case EU -> cache[1];
            case ANY -> cache[0] > 0 ? cache[0] : cache[1];
        };
    }

    /**
     * Returns a user-facing description of what the task is checking,
     * shown in the hover tooltip on the main quest screen.
     */
    public String getSourceHint(Player player) {
        return switch (source) {
            case INVENTORY -> "in inventory";
            case HELD -> "in held item";
            case BLOCK -> {
                long[] cache = blockCache.get(player.getUUID());
                if (cache == null) yield "§8Right-click an energy block to link";
                String unit = energyType == EnergyType.EU ? "EU" : "FE";
                long val = blockStored(player);
                yield "in linked block  §8(currently " + format(val, unit) + ")";
            }
        };
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "energy_check");
        tag.putLong("required_energy", requiredEnergy);
        tag.putString("energy_type", energyType.name());
        tag.putString("source", source.name());
        tag.putBoolean("sticky", sticky);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.requiredEnergy = nbt.contains("required_energy") ? nbt.getLong("required_energy") :
                nbt.contains("required_fe") ? nbt.getLong("required_fe")   // legacy
                        : nbt.getLong("amount");

        if (nbt.contains("energy_type")) {
            try {
                this.energyType = EnergyType.valueOf(nbt.getString("energy_type").toUpperCase());
            } catch (Exception ignored) {
                this.energyType = EnergyType.FE;
            }
        } else if (nbt.contains("mode")) {
            // Legacy migration: old "INVENTORY"/"HELD" modes mapped to source, not type
            this.energyType = EnergyType.FE;
        }

        if (nbt.contains("source")) {
            try {
                this.source = Source.valueOf(nbt.getString("source").toUpperCase());
            } catch (Exception ignored) {
                this.source = Source.INVENTORY;
            }
        } else if (nbt.contains("mode")) {
            // Legacy migration from old single-enum approach
            String mode = nbt.getString("mode").toUpperCase();
            this.source = mode.equals("HELD") ? Source.HELD : Source.INVENTORY;
        }

        // Default false for quests saved before this field existed - opt-in, not opt-out, unlike
        // the item/fluid tasks (see the sticky field's own doc comment for why).
        this.sticky = nbt.getBoolean("sticky");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String format(long energy, String unit) {
        if (energy >= 1_000_000_000L) return String.format("%.1fG%s", energy / 1_000_000_000.0, unit);
        if (energy >= 1_000_000L) return String.format("%.1fM%s", energy / 1_000_000.0, unit);
        if (energy >= 1_000L) return String.format("%.1fk%s", energy / 1_000.0, unit);
        return energy + " " + unit;
    }
}
