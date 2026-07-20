package net.phoenixvine.chronicles.integration.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.PhoenixChronicles;

/**
 * GregTech CEu integration for Phoenix Chronicles - GTCEu is a soft dependency, not a hard one,
 * same convention as {@link net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat}.
 *
 * Everything GTCEu-specific this mod does (its registrate instance, the material-registry
 * listener, the energy-check task type's block-reading, the multiblock-formed mixin) used to be
 * wired up unconditionally: {@code PhoenixChronicles.CHRONICLES_REGISTRATE} was a STATIC FIELD
 * initialized with {@code GTRegistrate.create(...)} right on the always-loaded main mod class,
 * meaning the JVM had to resolve GTCEu classes the instant Forge constructed the mod - before any
 * "is GTCEu installed" check could ever run. That's the opposite of what {@code mods.toml}
 * already (falsely) claimed: {@code gtceu} has been declared {@code mandatory = false} there the
 * whole time, but the actual Java code would crash with {@code NoClassDefFoundError} on mod
 * construction without it regardless.
 *
 * The fix follows the same shape PhantasiaCompat already uses: check {@link #isAvailable()}
 * first, and keep every GTCEu-referencing method OFF the always-loaded {@code PhoenixChronicles}
 * class - in particular, registering {@link #addMaterialRegistries} as a mod-bus listener via a
 * method reference on {@code PhoenixChronicles} itself would need the JVM to resolve
 * {@code MaterialRegistryEvent} (the method's parameter type) at the point that reference is
 * created, which is exactly the same class of hazard the field was. Keeping the method (and the
 * listener registration) entirely inside THIS class, only ever reached after
 * {@link #isAvailable()} has already passed, avoids that.
 */
public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    /** Guard for every GTCEu-touching call in this mod - check before calling {@link #init}. */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    /**
     * Call from {@code PhoenixChronicles}'s constructor, guarded by {@link #isAvailable()}.
     * Creates and registers the GTCEu registrate instance (needed by {@code ChroniclesGTAddon},
     * which GTCEu's own addon-loading system only ever touches when GTCEu itself is present, so
     * it doesn't need its own guard), and hooks the material-registry listener onto the mod
     * event bus.
     */
    public static void init(IEventBus modEventBus) {
        PhoenixChronicles.CHRONICLES_REGISTRATE = GTRegistrate.create(PhoenixChronicles.MOD_ID);
        PhoenixChronicles.CHRONICLES_REGISTRATE.registerRegistrate();
        modEventBus.addListener(GTCEuCompat::addMaterialRegistries);
    }

    private static void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(PhoenixChronicles.MOD_ID);
    }
}
