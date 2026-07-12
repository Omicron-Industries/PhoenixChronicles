package net.phoenixvine.chronicles;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixChronicles.MOD_ID)
@SuppressWarnings("removal")
public class PhoenixChronicles {

    public static final String MOD_ID = "phoenix_chronicles";
    public static final Logger LOGGER = LogManager.getLogger();
    public static GTRegistrate CHRONICLES_REGISTRATE = GTRegistrate.create(PhoenixChronicles.MOD_ID);

    public PhoenixChronicles() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addMaterialRegistries);
        modEventBus.addListener(this::addPackFinders);

        MinecraftForge.EVENT_BUS.register(this);
        CHRONICLES_REGISTRATE.registerRegistrate();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[Phoenix Chronicles] Bootstrapping standalone quest engine core...");

            // Call your network init inside the common setup step
            ChronicleNetwork.init();

            // TRANSFERRED: The quest engine now handles its own theme loading!
            ChroniclesTheme.loadThemes();

            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.isAvailable()) {
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.init();
            }

            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Hey, we're on Minecraft version {}!", Minecraft.getInstance().getLaunchedVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(PhoenixChronicles.MOD_ID);
    }

    /**
     * Registers config/phoenix_chronicles as a resource pack so quest text translation keys
     * resolve per-player. This event fires on both dist with different PackTypes (dedicated
     * servers only ever see SERVER_DATA) - the guard below means the client-only
     * ChroniclesLangPack class is never touched, let alone loaded, on a dedicated server.
     */
    private void addPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
        net.phoenixvine.chronicles.client.ChroniclesLangPack.register(event);
    }
}
