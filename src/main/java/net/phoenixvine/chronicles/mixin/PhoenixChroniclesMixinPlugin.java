package net.phoenixvine.chronicles.mixin;

import net.minecraftforge.fml.ModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates mixins that target an optional dependency's classes, so this mod's mixin config can stay
 * "required": true (catching genuine bugs in the mixins that DO always apply) without that also
 * demanding a mixin like MultiblockFormedMixin succeed against a target class
 * (com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData) that may not even exist on the
 * classpath when GTCEu isn't installed (GTCEu is a soft dependency - see GTCEuCompat). Returning
 * false from shouldApplyMixin is Mixin's own sanctioned way to skip a mixin without that counting
 * as a "required" failure - this isn't a workaround, it's the standard mechanism for exactly this
 * situation.
 */
public class PhoenixChroniclesMixinPlugin implements IMixinConfigPlugin {

    private static final String MULTIBLOCK_MIXIN = "net.phoenixvine.chronicles.mixin.MultiblockFormedMixin";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(MULTIBLOCK_MIXIN)) {
            return ModList.get().isLoaded("gtceu");
        }
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
