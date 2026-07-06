package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.phoenixvine.chronicles.registry.QuestLangRegistry;

import java.nio.file.Path;

/**
 * Registers config/phoenix_chronicles itself as an always-active, non-optional resource pack
 * so quest title/description/task translation keys resolve through Minecraft's OWN Language
 * system - meaning each player's own selected game language picks their own translation,
 * exactly like vanilla item/block names, instead of one language baked for the whole server.
 *
 * Client-only by construction (PathPackResources, Minecraft.getInstance(), resource-pack
 * registration). Callers must gate on AddPackFindersEvent#getPackType() == CLIENT_RESOURCES
 * before reaching this class so it's never loaded on a dedicated server.
 */
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

    /** Call after writing new/changed lang files so the translations take effect immediately. */
    public static void reload() {
        Minecraft.getInstance().reloadResourcePacks();
    }
}
