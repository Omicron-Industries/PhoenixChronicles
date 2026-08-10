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

        net.phoenixvine.chronicles.item.ChronicleItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addPackFinders);
        modEventBus.addListener(this::buildCreativeTab);

        MinecraftForge.EVENT_BUS.register(this);

        if (net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.isAvailable()) {
            net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.init(modEventBus);
        }

        if (net.minecraftforge.fml.loading.FMLLoader.getDist().isClient()) {
            modEventBus.addListener(net.phoenixvine.chronicles.client.ChronicleShaders::onRegisterShaders);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[Phoenix Chronicles] Bootstrapping standalone quest engine core...");

            ChronicleNetwork.init();

            PhoenixTheme.loadThemes();

            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() &&
                    net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.isAvailable()) {
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
        if (net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isAlwaysProfilerEnabled()) {
            net.phoenixvine.chronicles.client.FrameProfiler.setEnabled(true);
        }
        net.phoenixvine.chronicles.registry.DependencyLineStyleRegistry.registerBuiltins();
        net.phoenixvine.chronicles.registry.QuestBackgroundRegistry.registerBuiltins();

        net.phoenixvine.chronicles.client.render.ChroniclesThemePalette.refresh(
                net.phoenixvine.wiki.theme.PhoenixTheme.current());

        net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.imageResolver = net.phoenixvine.chronicles.client.CustomTextureCache::resolve;
    }

    private void addPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
        net.phoenixvine.chronicles.client.ChroniclesLangPack.register(event);
    }

    private void buildCreativeTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        event.accept(net.phoenixvine.chronicles.item.ChronicleItems.CHRONICLE_BOOK);
        event.accept(net.phoenixvine.chronicles.item.ChronicleItems.CHRONICLE_LOOT_CRATE);
        event.accept(net.phoenixvine.chronicles.item.ChronicleItems.ITEM_FILTER_TOKEN);
        event.accept(net.phoenixvine.chronicles.item.ChronicleItems.FLUID_FILTER_TOKEN);
    }
}
