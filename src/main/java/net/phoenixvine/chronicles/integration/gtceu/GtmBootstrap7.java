package net.phoenixvine.chronicles.integration.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.chronicles.PhoenixChronicles;

public final class GtmBootstrap7 implements GtmBootstrap {

    @Override
    public void init(IEventBus modEventBus) {
        PhoenixChronicles.CHRONICLES_REGISTRATE = GTRegistrate.create(PhoenixChronicles.MOD_ID);
        PhoenixChronicles.CHRONICLES_REGISTRATE.registerRegistrate();
        modEventBus.addListener(GtmBootstrap7::addMaterialRegistries);
    }

    private static void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(PhoenixChronicles.MOD_ID);
    }
}
