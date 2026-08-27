package net.phoenixvine.chronicles;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.phoenixvine.chronicles.client.ChroniclesClient;
import net.phoenixvine.chronicles.client.ChronicleShaders;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.item.ChronicleItems;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixChronicles.MOD_ID)
@SuppressWarnings("removal")
public class PhoenixChronicles {

    public static final String MOD_ID = "phoenix_chronicles";
    public static final Logger LOGGER = LogManager.getLogger();

    public static GTRegistrate CHRONICLES_REGISTRATE = null;

    public PhoenixChronicles() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ChronicleItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ChroniclesClient::onClientSetup);
            modEventBus.addListener(ChronicleShaders::onRegisterShaders);
        }

        modEventBus.addListener(this::addPackFinders);
        modEventBus.addListener(this::buildCreativeTab);

        MinecraftForge.EVENT_BUS.register(this);

        if (GTCEuCompat.isAvailable()) {
            GTCEuCompat.init(modEventBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[Phoenix Chronicles] Bootstrapping standalone quest engine core...");

            ChronicleNetwork.init();

            PhoenixTheme.loadThemes();

            if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("phantasia")) {
                PhantasiaCompat.init();
            }

            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        ChroniclesLangPack.register(event);
    }

    private void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        event.accept(ChronicleItems.CHRONICLE_BOOK);
        event.accept(ChronicleItems.CHRONICLE_LOOT_CRATE);
        event.accept(ChronicleItems.ITEM_FILTER_TOKEN);
        event.accept(ChronicleItems.FLUID_FILTER_TOKEN);
    }
}