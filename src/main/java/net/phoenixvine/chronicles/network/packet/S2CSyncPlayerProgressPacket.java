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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncPlayerProgressPacket {

    private final CompoundTag progressNbt;

    public S2CSyncPlayerProgressPacket(PlayerQuestData data) {
        this.progressNbt = data.serializeNBT();
    }

    public S2CSyncPlayerProgressPacket(FriendlyByteBuf buf) {
        this.progressNbt = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(progressNbt);
    }

    private static boolean receivedFirstSync = false;

    private static long graceDeadlineMs = 0;
    private static long firstSyncTimeMs = 0;
    private static final long LOGIN_GRACE_MS = 3000;
    private static final long MAX_GRACE_MS = 15000;

    private static volatile int version = 0;

    public static int getVersion() {
        return version;
    }

    public static void resetForNewSession() {
        receivedFirstSync = false;
        graceDeadlineMs = 0;
        firstSyncTimeMs = 0;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> applyOnClient(progressNbt)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(CompoundTag nbt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean isFirstSync = !receivedFirstSync;
            receivedFirstSync = true;

            Map<ResourceLocation, QuestState> oldStates = new HashMap<>();
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                oldStates.put(node.getId(), data.getQuestState(node.getId(), QuestState.LOCKED));
            }

            data.deserializeNBT(nbt);
            version++;

            long now = System.currentTimeMillis();
            if (isFirstSync) {
                firstSyncTimeMs = now;
                graceDeadlineMs = now + LOGIN_GRACE_MS;
                return; 
            }
            if (now < graceDeadlineMs) {

                graceDeadlineMs = Math.min(now + LOGIN_GRACE_MS, firstSyncTimeMs + MAX_GRACE_MS);
                return;
            }

            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState oldState = oldStates.getOrDefault(node.getId(), QuestState.LOCKED);
                QuestState newState = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (oldState == newState) continue;
                boolean playSounds = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isPlayToastSounds();
                if (newState == QuestState.UNLOCKED) {
                    QuestToastManager.get().push(node, QuestToastManager.ToastType.UNLOCKED);

                    if (mc.player != null && playSounds)
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
                } else if (newState == QuestState.COMPLETED) {
                    QuestToastManager.get().push(node, QuestToastManager.ToastType.COMPLETED);
                    if (mc.player != null && playSounds)
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f, 1.1f);
                }
            }
        });
    }
}

