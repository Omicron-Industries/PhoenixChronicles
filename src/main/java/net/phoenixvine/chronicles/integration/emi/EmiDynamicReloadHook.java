package net.phoenixvine.chronicles.integration.emi;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.event.QuestEvent;

import dev.emi.emi.runtime.EmiReloadManager;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EmiDynamicReloadHook {

    @SubscribeEvent
    public static void onQuestTreeReloaded(QuestEvent.TreeReloaded event) {

        if (!net.minecraftforge.fml.ModList.get().isLoaded("emi")) return;
        EmiReloadManager.reload();
    }
}

