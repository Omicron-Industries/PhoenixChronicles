package net.phoenixvine.chronicles.item;

import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class ChronicleItemModelProvider extends ItemModelProvider {

    public ChronicleItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, "your_mod_id", existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Replaces .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("item/chronicle_book")))
        withExistingParent(ChronicleItems.CHRONICLE_BOOK.getId().getPath(), "item/generated")
                .texture("layer0", modLoc("item/chronicle_book"));
    }
}
