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
        // When the client receives the S2CSyncQuestsPacket or S2CReloadQuestsFromDiskPacket,
        // we tell EMI to reload its data using the updated QuestTreeRegistry.

        // EMI provides a command-driven reload API, but we can trigger it programmatically.
        // This will call ChroniclesEmiPlugin.register() again automatically.

        // EMI is a soft dependency, not a hard one - this class is registered via
        // @Mod.EventBusSubscriber, which Forge always scans/registers regardless of whether EMI
        // is installed (the event PARAMETER here is our own QuestEvent.TreeReloaded, so Forge's
        // registration scan itself doesn't choke - but TreeReloaded fires on server start and
        // after every live SNBT reload, i.e. essentially every world join, so without this guard
        // EmiReloadManager.reload() below would throw NoClassDefFoundError the first time
        // anyone joined a world without EMI installed.
        if (!net.minecraftforge.fml.ModList.get().isLoaded("emi")) return;
        EmiReloadManager.reload();
    }
}
