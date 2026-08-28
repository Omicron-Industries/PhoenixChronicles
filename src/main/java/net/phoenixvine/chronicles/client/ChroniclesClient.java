package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.util.CustomTextureCache;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.registry.DependencyLineStyleRegistry;
import net.phoenixvine.chronicles.registry.QuestBackgroundRegistry;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import static net.phoenixvine.chronicles.PhoenixChronicles.MOD_ID;

@OnlyIn(Dist.CLIENT)
public class ChroniclesClient {

    public static void onClientSetup(final FMLClientSetupEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PhoenixChronicles.LOGGER.info("Hey, we're on Minecraft version {}!", mc.getLaunchedVersion());

        if (QuestChroniclesSettings.get().isAlwaysProfilerEnabled()) {
            FrameProfiler.setEnabled(true);
        }

        DependencyLineStyleRegistry.registerBuiltins();
        QuestBackgroundRegistry.registerBuiltins();

        ChroniclesThemePalette.refresh(PhoenixTheme.current());
        ChronicleRichTextRenderer.imageResolver = CustomTextureCache::resolve;

        registerHudBar(mc);
    }

    private static void registerHudBar(Minecraft mc) {
        ResourceLocation iconPath = ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "textures/item/chronicles_quest_book.png");

        SuiteHudBar.register(
                MOD_ID,
                SuiteHudBar.PRIORITY_CHRONICLES,
                iconPath,
                () -> Component.literal("§fOpen Quest Book"),
                () -> 1,
                () -> mc.setScreen(new ChronicleOverviewScreen(mc.screen)),
                16,
                128,
                false);
    }
}
