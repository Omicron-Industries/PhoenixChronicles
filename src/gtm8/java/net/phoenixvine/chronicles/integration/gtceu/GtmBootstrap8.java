package net.phoenixvine.chronicles.integration.gtceu;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.chronicles.PhoenixChronicles;

public final class GtmBootstrap8 implements GtmBootstrap {

    @Override
    public void init(IEventBus modEventBus) {
        PhoenixChronicles.CHRONICLES_REGISTRATE = GTRegistrate.create(PhoenixChronicles.MOD_ID);
    }
}
