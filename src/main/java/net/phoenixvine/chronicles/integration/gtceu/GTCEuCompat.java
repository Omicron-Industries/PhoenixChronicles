package net.phoenixvine.chronicles.integration.gtceu;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.PhoenixChronicles;

import org.jetbrains.annotations.NotNull;

public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    public static boolean usesNewApi() {
        try {
            Class.forName("com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData", false,
                    GTCEuCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static void init(@NotNull IEventBus modEventBus) {
        String name = "net.phoenixvine.chronicles.integration.gtceu." + (usesNewApi() ? "GtmBootstrap8" :
                "GtmBootstrap7");
        try {
            ((GtmBootstrap) Class.forName(name).getDeclaredConstructor().newInstance()).init(modEventBus);
        } catch (ReflectiveOperationException | LinkageError e) {
            PhoenixChronicles.LOGGER.error("Could not start the GregTech integration ({}); GregTech features of " +
                    "Chronicles are disabled.", name, e);
        }
    }
}
