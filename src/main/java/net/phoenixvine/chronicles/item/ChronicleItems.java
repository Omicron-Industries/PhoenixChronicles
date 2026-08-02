package net.phoenixvine.chronicles.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.phoenixvine.chronicles.PhoenixChronicles;

public class ChronicleItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            PhoenixChronicles.MOD_ID);

    public static final RegistryObject<ChronicleBookItem> CHRONICLE_BOOK = ITEMS.register("chronicle_book",
            () -> new ChronicleBookItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<ChronicleLootCrateItem> CHRONICLE_LOOT_CRATE = ITEMS.register(
            "chronicle_loot_crate",
            () -> new ChronicleLootCrateItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<ItemFilterTokenItem> ITEM_FILTER_TOKEN = ITEMS.register(
            "item_filter_token",
            () -> new ItemFilterTokenItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<FluidFilterTokenItem> FLUID_FILTER_TOKEN = ITEMS.register(
            "fluid_filter_token",
            () -> new FluidFilterTokenItem(new Item.Properties().stacksTo(1)));
}
