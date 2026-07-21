package net.phoenixvine.chronicles.mixin;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {}

