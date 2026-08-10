package net.phoenixvine.chronicles.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.QuestToastManager;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncPlayerProgressPacket {

    @Nullable
    private final CompoundTag progressNbt;
    private final boolean initialSync;

    private static volatile int version = 0;

    public static int getVersion() {
        return version;
    }

    public S2CSyncPlayerProgressPacket(PlayerQuestData data) {
        this(data, false);
    }

    public S2CSyncPlayerProgressPacket(PlayerQuestData data, boolean initialSync) {
        this.progressNbt = data.serializeNBT();
        this.initialSync = initialSync;
    }

    public S2CSyncPlayerProgressPacket(FriendlyByteBuf buf) {
        this.progressNbt = buf.readNbt();
        this.initialSync = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(progressNbt != null ? progressNbt : new CompoundTag());
        buf.writeBoolean(initialSync);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || progressNbt == null) return;

            mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                if (initialSync) {
                    data.deserializeNBT(progressNbt);
                    version++;
                    return;
                }

                Map<ResourceLocation, QuestState> oldStates = new HashMap<>();
                for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                    oldStates.put(node.getId(), data.getQuestState(node.getId(), QuestState.LOCKED));
                }

                data.deserializeNBT(progressNbt);
                version++;

                for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                    QuestState oldState = oldStates.getOrDefault(node.getId(), QuestState.LOCKED);
                    QuestState newState = data.getQuestState(node.getId(), QuestState.LOCKED);
                    if (oldState == newState) continue;
                    boolean playSounds = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                            .isPlayToastSounds();
                    if (newState == QuestState.UNLOCKED) {
                        QuestToastManager.get().push(node, QuestToastManager.ToastType.UNLOCKED);

                        if (mc.player != null && playSounds)
                            mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
                    } else if (newState == QuestState.COMPLETED) {
                        QuestToastManager.get().push(node, QuestToastManager.ToastType.COMPLETED);
                        if (mc.player != null && playSounds)
                            mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f, 1.0f);
                    }
                }
            });
        }));
        ctx.get().setPacketHandled(true);
    }
}
