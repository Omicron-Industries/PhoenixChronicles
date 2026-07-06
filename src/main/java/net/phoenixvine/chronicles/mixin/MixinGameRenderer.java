package net.phoenixvine.chronicles.mixin;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Example client-side mixin. Add @Inject / @Redirect / @ModifyArg methods here
 * to hook into GameRenderer. Delete this and add your own mixins as needed.
 *
 * Common patterns:
 * 
 * @Inject(method = "renderLevel", at = @At("HEAD"))
 *                private void onRenderLevelHead(CallbackInfo ci) { ... }
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {}
