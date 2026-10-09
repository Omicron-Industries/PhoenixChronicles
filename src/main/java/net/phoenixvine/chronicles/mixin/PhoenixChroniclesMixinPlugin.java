package net.phoenixvine.chronicles.mixin;

import net.minecraftforge.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class PhoenixChroniclesMixinPlugin implements IMixinConfigPlugin {

    private static final String MULTIBLOCK_MIXIN = "net.phoenixvine.chronicles.mixin.MultiblockFormedMixin";
    private static final String MULTIBLOCK_MIXIN_GTM8 = "net.phoenixvine.chronicles.mixin.MultiblockFormedMixinGtm8";
    private static final String RECIPE_FINISHED_MIXIN = "net.phoenixvine.chronicles.mixin.GTRecipeFinishedMixin";
    private static final String RECIPE_FINISHED_MIXIN_GTM8 = "net.phoenixvine.chronicles.mixin.GTRecipeFinishedMixinGtm8";

    private static int gtceuApi() {
        var file = LoadingModList.get().getModFileById("gtceu");
        if (file == null) return -1;
        var marker = file.getFile().findResource("com", "gregtechceu", "gtceu", "api", "multiblock",
                "MultiblockWorldSavedData.class");
        return marker != null && java.nio.file.Files.exists(marker) ? 8 : 7;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(MULTIBLOCK_MIXIN)) return gtceuApi() == 7;
        if (mixinClassName.equals(MULTIBLOCK_MIXIN_GTM8)) return gtceuApi() == 8;
        if (mixinClassName.equals(RECIPE_FINISHED_MIXIN)) return gtceuApi() == 7;
        if (mixinClassName.equals(RECIPE_FINISHED_MIXIN_GTM8)) return gtceuApi() == 8;
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {}
}
