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
import net.phoenixvine.chronicles.client.rich.ChroniclesConditionalBlockParser;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.util.CustomTextureCache;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.registry.DependencyLineStyleRegistry;
import net.phoenixvine.chronicles.common.registry.QuestBackgroundRegistry;
import net.phoenixvine.wiki.client.rich.WikiRichTextRenderer;
import net.phoenixvine.wiki.client.rich.markdown.BlockParserRegistry;
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

        PhoenixTheme.registerMod("net.phoenixvine.chronicles", MOD_ID);

        ChroniclesThemePalette.refresh(PhoenixTheme.current());
        WikiRichTextRenderer.registerImageResolver(loc -> CustomTextureCache.resolve(wikiTexturePath(loc)));

        BlockParserRegistry.DEFAULT.registerFirst(new ChroniclesConditionalBlockParser());

        registerHudBar(mc);
    }

    /**
     * Lets wiki image tags use short texture ids: {@code [img:ae2:block/dense_energy_cell_3,64,64]} resolves to
     * {@code ae2:textures/block/dense_energy_cell_3.png}. Full paths pass through untouched.
     */
    private static ResourceLocation wikiTexturePath(ResourceLocation loc) {
        String path = loc.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path = path + ".png";
        return path.equals(loc.getPath()) ? loc : ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), path);
    }

    private static void registerHudBar(Minecraft mc) {
        ResourceLocation iconPath = ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "textures/gui/chronicles_quest_book_icon.png");

        SuiteHudBar.register(
                MOD_ID,
                SuiteHudBar.PRIORITY_CHRONICLES,
                iconPath,
                () -> Component.literal("§fOpen Quest Book"),
                () -> 1,
                () -> mc.setScreen(new ChronicleOverviewScreen(mc.screen)),
                16,
                16,
                false);
    }
}
