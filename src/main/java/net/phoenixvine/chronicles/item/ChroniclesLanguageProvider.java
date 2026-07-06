package net.phoenixvine.chronicles.item;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import net.phoenixvine.chronicles.PhoenixChronicles;

public class ChroniclesLanguageProvider extends LanguageProvider {

    public ChroniclesLanguageProvider(PackOutput output, String locale) {
        super(output, PhoenixChronicles.MOD_ID, locale);
    }

    @Override
    protected void addTranslations() {
        // Replaces .lang("§dPhoenix Chronicle")
        // Note: Vanilla lang files use the section sign (§) for color codes formatting
        add(ChronicleItems.CHRONICLE_BOOK.get(), "§dPhoenix Chronicle");

        add("key.categories.phoenix_chronicles", "Phoenix Chronicles");
        add("key.phoenix_chronicles.item_lookup", "Quest Item Lookup");
    }
}
