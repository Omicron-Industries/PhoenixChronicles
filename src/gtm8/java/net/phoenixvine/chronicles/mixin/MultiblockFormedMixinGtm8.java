package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public abstract class MultiblockFormedMixinGtm8 {

    @Inject(method = "addMapping", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onMultiblockFormed(PatternState state, CallbackInfo ci) {
        if (state.getController() == null) return;
        GtmHooks.onMultiblockFormed(state.getController().getLevel(), state.getControllerPos());
    }
}
