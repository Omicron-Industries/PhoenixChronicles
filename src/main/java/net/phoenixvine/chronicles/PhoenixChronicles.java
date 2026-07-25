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

            ChronicleNetwork.init();

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
        if (net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isAlwaysProfilerEnabled()) {
            net.phoenixvine.chronicles.client.FrameProfiler.setEnabled(true);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void addPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
        net.phoenixvine.chronicles.client.ChroniclesLangPack.register(event);
    }
}
