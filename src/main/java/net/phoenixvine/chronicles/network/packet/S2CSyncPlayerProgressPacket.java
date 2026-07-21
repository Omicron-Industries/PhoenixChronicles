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

/**
 * Server → Client: sends the full PlayerQuestData blob so the client capability
 * mirrors the server. Sent on login and after every server-side state change.
 */
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

    /**
     * True until the first sync of the current session has been applied. That first packet is
     * always this player's FULL prior progress arriving into a fresh, empty client capability -
     * every already-completed quest reads as a LOCKED→COMPLETED "transition" against that empty
     * baseline, which used to replay a toast for every quest ever completed, on every login.
     * Reset on logout/disconnect (see ChronicleClientEvents.onPlayerLogout) so the next session
     * suppresses correctly again; real completions during an active session still fire normally
     * since only the very first sync of the session is skipped.
     */
    private static boolean receivedFirstSync = false;

    /**
     * Wall-clock time the MOST RECENT sync of this session (after the first) was applied - a
     * fresh join's prereq cascade (see ChronicleEvents#onPlayerLogin/
     * QuestProgressTracker#processChildCascades) doesn't necessarily finish within that single
     * first packet; it can keep unlocking and auto-completing quests across the next several
     * server ticks as the cascade ripples through the tree, each firing its own follow-up sync.
     * Those still read as genuine LOCKED→COMPLETED transitions against the (already-applied)
     * baseline, so skipping only the very first sync isn't enough on its own.
     *
     * This used to be a FIXED window measured from the first sync alone (3s, then never
     * extended) - fine for a short pack, but on a large one a long cascade chain (complete A →
     * unlock/auto-complete B → unlock/auto-complete C → ...) can keep producing genuine
     * login-cascade syncs well past a fixed cutoff, especially under server lag, which is what
     * made this "not every relog, but often enough": whether the toast flood happened depended
     * on how deep this particular player's pending cascade was and how fast the server was
     * ticking that session. Now a SLIDING window instead: every sync seen while still inside the
     * grace period pushes the deadline back out, so suppression only ends once syncs actually
     * stop arriving for a full grace period - i.e. the cascade has genuinely gone quiet - rather
     * than at a fixed offset from the first packet regardless of whether more are still incoming.
     *
     * Capped by MAX_GRACE_MS measured from the FIRST sync (firstSyncTimeMs) so this can't extend
     * forever - a player who genuinely starts completing quests back-to-back right after joining
     * still gets real toasts eventually instead of the window sliding indefinitely.
     */
    private static long graceDeadlineMs = 0;
    private static long firstSyncTimeMs = 0;
    private static final long LOGIN_GRACE_MS = 3000;
    private static final long MAX_GRACE_MS = 15000;

    /**
     * Bumped every time a sync is applied, so screens holding per-category progress caches
     * (e.g. ChronicleOverviewScreen) can invalidate only when progress actually changed instead
     * of unconditionally every client tick.
     */
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

            // Snapshot old states before overwrite so we can detect transitions
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
                return; // establishing baseline, not new completions - see javadoc above
            }
            if (now < graceDeadlineMs) {
                // Still inside the login-cascade grace window - push the deadline back out since
                // a sync just landed, meaning the cascade is still actively producing them (see
                // graceDeadlineMs's javadoc for why this needs to slide instead of being fixed),
                // but never past MAX_GRACE_MS from the very first sync.
                graceDeadlineMs = Math.min(now + LOGIN_GRACE_MS, firstSyncTimeMs + MAX_GRACE_MS);
                return;
            }

            // Fire toasts for any quests that newly became UNLOCKED or COMPLETED
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState oldState = oldStates.getOrDefault(node.getId(), QuestState.LOCKED);
                QuestState newState = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (oldState == newState) continue;
                boolean playSounds = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isPlayToastSounds();
                if (newState == QuestState.UNLOCKED) {
                    QuestToastManager.get().push(node, QuestToastManager.ToastType.UNLOCKED);
                    // Completion already had a sound (PLAYER_LEVELUP below); unlock had none at
                    // all, so the toast for a newly-available quest was silent. Lighter/quieter
                    // than the completion fanfare so the two stay distinguishable by ear.
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
