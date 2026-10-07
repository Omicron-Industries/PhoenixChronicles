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
import net.phoenixvine.chronicles.client.registry.QuestToastManager;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

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
        CompoundTag nbt = data.serializeNBT();
        net.minecraft.nbt.ListTag emergencyQuests = new net.minecraft.nbt.ListTag();
        for (QuestNode q : QuestTreeRegistry.getAllQuests().values()) {
            if (!q.getEffectiveEmergencyKit().hasRewards()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", q.getId().toString());
            entry.putBoolean("repeatable", q.isEmergencyRepeatable());
            entry.putInt("cooldown_seconds", q.getEmergencyCooldownSeconds());
            emergencyQuests.add(entry);
        }
        for (String chapter : net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems.chaptersWithKits()) {
            addEmergencyStation(emergencyQuests, net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems
                    .chapterStation(chapter),
                    net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems
                            .get(chapter));
        }
        net.phoenixvine.chronicles.common.model.EmergencyKit questbookKit = net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems
                .getQuestbook();
        if (questbookKit.hasRewards()) {
            addEmergencyStation(emergencyQuests,
                    net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems.QUESTBOOK_STATION, questbookKit);
        }
        nbt.put(net.phoenixvine.chronicles.client.util.ClientEmergencyState.QUESTS_KEY, emergencyQuests);
        nbt.putLong(net.phoenixvine.chronicles.client.util.ClientEmergencyState.SERVER_NOW_KEY,
                System.currentTimeMillis());
        this.progressNbt = nbt;
        this.initialSync = initialSync;
    }

    /** A station carries its own label and rewards, since the client has no quest to read them from. */
    private static void addEmergencyStation(net.minecraft.nbt.ListTag out, net.minecraft.resources.ResourceLocation id,
                                            net.phoenixvine.chronicles.common.model.EmergencyKit kit) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id.toString());
        entry.putBoolean("repeatable", kit.resolveRepeatable(
                net.phoenixvine.chronicles.common.model.EmergencyKit.Repeat.INHERIT));
        entry.putInt("cooldown_seconds", kit.resolveCooldownSeconds(
                net.phoenixvine.chronicles.common.model.EmergencyKit.INHERIT_COOLDOWN));
        entry.putString("label", net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems.stationLabel(id));
        entry.put("kit", kit.serializeNBT());
        out.add(entry);
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

            net.phoenixvine.chronicles.client.util.ClientEmergencyState.update(progressNbt);

            mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                if (initialSync) {
                    data.deserializeNBT(progressNbt);
                    version++;
                    return;
                }

                if (data.getAllStates().isEmpty()) {

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
                    boolean playSounds = QuestChroniclesSettings.get()
                            .isPlayToastSounds();
                    if (!shouldNotify(node, newState)) continue;
                    if (newState == QuestState.UNLOCKED) {

                        if (oldState == QuestState.COMPLETED) continue;
                        QuestToastManager.get().push(node, QuestToastManager.ToastType.UNLOCKED);

                        if (mc.player != null && playSounds) {
                            net.minecraft.sounds.SoundEvent sound = resolveSound(node.getUnlockSoundId(),
                                    node.getChapter(),
                                    net.phoenixvine.chronicles.client.util.ChapterConfig::getUnlockSoundId,
                                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP);
                            mc.player.playSound(sound, 0.5f, 1.4f);
                        }
                    } else if (newState == QuestState.COMPLETED) {
                        QuestToastManager.get().push(node, QuestToastManager.ToastType.COMPLETED);
                        if (mc.player != null && playSounds) {
                            net.minecraft.sounds.SoundEvent sound = resolveSound(node.getCompleteSoundId(),
                                    node.getChapter(),
                                    net.phoenixvine.chronicles.client.util.ChapterConfig::getCompleteSoundId,
                                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP);
                            mc.player.playSound(sound, 0.6f, 1.0f);
                        }
                    }
                }
            });
        }));
        ctx.get().setPacketHandled(true);
    }

    private static final Map<String, Long> LAST_NOTIFIED = new HashMap<>();
    private static final long NOTIFY_COOLDOWN_MS = 4000;

    private static boolean shouldNotify(QuestNode node, QuestState state) {
        if (state != QuestState.UNLOCKED && state != QuestState.COMPLETED) return true;
        long now = System.currentTimeMillis();
        String key = node.getId() + "|" + state;
        Long last = LAST_NOTIFIED.get(key);
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) return false;
        LAST_NOTIFIED.put(key, now);
        return true;
    }

    private static net.minecraft.sounds.SoundEvent resolveSound(String nodeSoundId, String chapter,
                                                                java.util.function.Function<net.phoenixvine.chronicles.client.util.ChapterConfig, String> chapterSoundGetter,
                                                                net.minecraft.sounds.SoundEvent fallback) {
        net.minecraft.sounds.SoundEvent resolved = lookupSound(nodeSoundId);
        if (resolved != null) return resolved;
        resolved = lookupSound(
                chapterSoundGetter.apply(net.phoenixvine.chronicles.client.util.ChapterConfig.get(chapter)));
        return resolved != null ? resolved : fallback;
    }

    @Nullable
    private static net.minecraft.sounds.SoundEvent lookupSound(@Nullable String id) {
        if (id == null || id.isBlank()) return null;
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id.trim());
        return rl != null ? net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(rl) : null;
    }
}
