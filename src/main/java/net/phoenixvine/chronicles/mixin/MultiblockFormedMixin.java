package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public abstract class MultiblockFormedMixin {

    @Inject(method = "addMapping", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onMultiblockFormed(MultiblockState state, CallbackInfo ci) {
        GtmHooks.onMultiblockFormed(state.getWorld(), state.controllerPos);
    }
}
