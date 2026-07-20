package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.QuestAPI;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires a QuestAPI external trigger whenever a GTCEu multiblock successfully forms, so packs can
 * build "assemble this specific machine" quests using the existing {@code external_trigger} task
 * type - no new task type, no new tracking subsystem.
 *
 * GTCEu itself never posts a Forge event for this - confirmed by reading the actual
 * gtceu-1.20.1-7.5.3 sources: {@code MultiblockState#onBlockStateChanged} calls
 * {@code IMultiController#onStructureFormed()} as a plain method call, and nothing in that
 * package ever constructs or posts a Forge event. {@code MultiblockWorldSavedData#addMapping} is
 * the one place every successful formation is guaranteed to pass through regardless of which
 * multiblock class formed it, which is why a mixin here (not an event listener) is genuinely the
 * only way to hook this - not a workaround, there's no cleaner alternative available.
 *
 * GTCEu is a SOFT dependency of this mod (see GTCEuCompat) - this mixin only ever actually
 * applies when GTCEu is present, gated by PhoenixChroniclesMixinPlugin#shouldApplyMixin (see
 * phoenix_chronicles.mixins.json's "plugin" entry), so its target class simply doesn't exist to
 * mix into on a server without GTCEu, and that's handled gracefully rather than crashing.
 *
 * Trigger id fired: {@code "gtceu_multiblock:<machine registry id>"} - e.g.
 * {@code "gtceu_multiblock:gtceu:electric_blast_furnace"}. Phoenix Chronicles has no per-player
 * block-placement tracking system (unlike the sibling mod this was adapted from), so rather than
 * build one just for this, this fires for whichever player is standing nearest the controller
 * when it forms (10-block radius) - the same "you were there" fallback that mixin's own tracker
 * already fell back to when it had no recorded placer anyway. If nobody's within range, nothing
 * fires for that formation.
 */
@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public abstract class MultiblockFormedMixin {

    @Inject(method = "addMapping", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onMultiblockFormed(MultiblockState state, CallbackInfo ci) {
        if (!(state.getWorld() instanceof ServerLevel serverLevel)) return;

        ResourceLocation machineId = ForgeRegistries.BLOCKS.getKey(
                serverLevel.getBlockState(state.controllerPos).getBlock());
        if (machineId == null) return;

        Player nearest = serverLevel.getNearestPlayer(
                state.controllerPos.getX() + 0.5, state.controllerPos.getY() + 0.5, state.controllerPos.getZ() + 0.5,
                10.0, false);
        if (!(nearest instanceof ServerPlayer serverPlayer)) return;

        CompoundTag data = new CompoundTag();
        data.putString("machine_id", machineId.toString());
        QuestAPI.fireExternalEvent(serverPlayer, "gtceu_multiblock:" + machineId, data);
    }
}
