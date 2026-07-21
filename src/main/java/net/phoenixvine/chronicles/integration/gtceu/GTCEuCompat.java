package net.phoenixvine.chronicles.integration.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.PhoenixChronicles;

public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }

    public static void init(IEventBus modEventBus) {
        PhoenixChronicles.CHRONICLES_REGISTRATE = GTRegistrate.create(PhoenixChronicles.MOD_ID);
        PhoenixChronicles.CHRONICLES_REGISTRATE.registerRegistrate();
        modEventBus.addListener(GTCEuCompat::addMaterialRegistries);
    }

    private static void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(PhoenixChronicles.MOD_ID);
    }
}

