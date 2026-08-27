package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.phoenixvine.chronicles.registry.QuestLangRegistry;

import java.nio.file.Path;

public class ChroniclesLangPack {

    public static void register(AddPackFindersEvent event) {
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        QuestLangRegistry.ensurePackStructure(root);

        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    "phoenix_chronicles_lang",
                    Component.literal("Phoenix Chronicles Quest Translations"),
                    true,
                    id -> new PathPackResources(id, root, false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    public static void reload() {
        Minecraft.getInstance().reloadResourcePacks();
    }
}
