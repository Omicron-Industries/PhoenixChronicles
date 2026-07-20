package net.phoenixvine.chronicles;

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
    /**
     * Null unless GTCEu is actually loaded - was a static field initialized straight from
     * {@code GTRegistrate.create(...)}, meaning the JVM had to resolve GTCEu classes the instant
     * this class loaded, before any "is GTCEu installed" check could run at all. GTCEu is
     * supposed to be a soft dependency ({@code mods.toml} already declares it
     * {@code mandatory = false}), so that was backwards - see GTCEuCompat#init, which sets this
     * (guarded) from the constructor below. {@code ChroniclesGTAddon#getRegistrate()} reads this
     * field, but that's only ever called by GTCEu's own addon-loading system, which itself only
     * runs when GTCEu is present - so by the time anything reads a non-null value here, GTCEu is
     * guaranteed to already be loaded.
     */
    public static GTRegistrate CHRONICLES_REGISTRATE = null;

    public PhoenixChronicles() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addPackFinders);

        MinecraftForge.EVENT_BUS.register(this);

        if (net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.isAvailable()) {
            net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.init(modEventBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[Phoenix Chronicles] Bootstrapping standalone quest engine core...");

            // Call your network init inside the common setup step
            ChronicleNetwork.init();

            // TRANSFERRED: The quest engine now handles its own theme loading!
            ChroniclesTheme.loadThemes();

            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.isAvailable()) {
                try {
                    net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.init();
                } catch (Throwable t) {
                    LOGGER.error("Phantasia integration failed to initialize — Phantasia-linked quest tasks" +
                            " will be unavailable this session.", t);
                }
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
