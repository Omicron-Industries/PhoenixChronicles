package net.phoenixvine.chronicles.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class QuestProgressGameTests {

    private static QuestNode node(String path) {
        return new QuestNode(ResourceLocation.fromNamespaceAndPath(PhoenixChronicles.MOD_ID, path),
                Component.literal(path),
                Component.literal(""));
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void completingAQuestUnlocksItsDependent(GameTestHelper helper) {
        QuestTreeRegistry.clear();
        try {
            QuestNode gate = node("gametest_gate");
            QuestNode dependent = node("gametest_dependent");
            dependent.addPrerequisite(gate);

            QuestTreeRegistry.registerBareQuestNode(gate);
            QuestTreeRegistry.registerBareQuestNode(dependent);

            Player player = helper.makeMockPlayer();

            helper.assertTrue(QuestProgressTracker.getQuestState(player, gate) == QuestState.LOCKED,
                    "gate should start LOCKED");
            helper.assertTrue(QuestProgressTracker.getQuestState(player, dependent) == QuestState.LOCKED,
                    "dependent should start LOCKED before its prerequisite is met");

            QuestProgressTracker.changeQuestState(player, gate, QuestState.COMPLETED);

            helper.assertTrue(QuestProgressTracker.getQuestState(player, gate) == QuestState.COMPLETED,
                    "gate should be COMPLETED after changeQuestState");
            helper.assertTrue(QuestProgressTracker.getQuestState(player, dependent) == QuestState.UNLOCKED,
                    "completing the prerequisite should cascade-unlock the dependent quest " +
                            "(processChildCascades)");

            helper.succeed();
        } finally {
            QuestTreeRegistry.clear();
        }
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void questWithUnmetPrerequisiteStaysLocked(GameTestHelper helper) {
        QuestTreeRegistry.clear();
        try {
            QuestNode gate = node("gametest_gate2");
            QuestNode dependent = node("gametest_dependent2");
            dependent.addPrerequisite(gate);

            QuestTreeRegistry.registerBareQuestNode(gate);
            QuestTreeRegistry.registerBareQuestNode(dependent);

            Player player = helper.makeMockPlayer();

            helper.assertTrue(QuestProgressTracker.getQuestState(player, gate) == QuestState.LOCKED,
                    "unrelated registration must not affect the gate's own state");
            helper.assertTrue(QuestProgressTracker.getQuestState(player, dependent) == QuestState.LOCKED,
                    "dependent must remain LOCKED while its prerequisite is still LOCKED");

            helper.succeed();
        } finally {
            QuestTreeRegistry.clear();
        }
    }

    /**
     * Regression test for the "one team holding a nether star unlocks the quest for everyone" bug
     * class: setFlagForPlayer must scope the flag to the setting player alone (falling back to a
     * per-player key here, since GameTestHelper#makeMockPlayer's mock is a plain Player, not a
     * ServerPlayer -- TeamKeyResolver's guild/FTB-Team lookups never even run for it, so this
     * specifically exercises the no-team fallback path; see guildMembersShareAScopedFlagButOutsidersDont
     * below for the actual guild-sharing path), not leak it onto the global bucket every other
     * player's evaluate() also checks.
     */
    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void flagSetForOnePlayerDoesNotLeakToAnother(GameTestHelper helper) {
        Player alice = helper.makeMockPlayer();
        Player bob = helper.makeMockPlayer();
        String flagName = "gametest_has_nether_star";

        try {
            // A known global baseline first -- otherwise bob's lookup would hit the "unknown flag
            // defaults to true" fallback, which would mask a real leak as a false pass.
            PhoenixQuestFlags.setFlag(flagName, false);
            PhoenixQuestFlags.setFlagForPlayer(alice, flagName, true);

            helper.assertTrue(PhoenixQuestFlags.evaluate("flag:" + flagName, null, alice),
                    "the player the flag was set for should see it as true");
            helper.assertTrue(!PhoenixQuestFlags.evaluate("flag:" + flagName, null, bob),
                    "a different, unrelated player must NOT see a flag scoped to someone else's " +
                            "team/player key -- this is the exact leak the team-scoped flag storage exists to prevent");

            helper.succeed();
        } finally {
            PhoenixQuestFlags.clearFlagForPlayer(alice, flagName);
            PhoenixQuestFlags.clearFlag(flagName);
        }
    }

    /**
     * The actual guild-sharing path, which flagSetForOnePlayerDoesNotLeakToAnother above can't reach:
     * this needs real {@link ServerPlayer}s (Phoenix Guilds' GuildManager keys off player UUIDs it
     * looks up through a {@code ServerLevel}, and TeamKeyResolver only even attempts guild/FTB-Team
     * resolution for an {@code instanceof ServerPlayer}), so it uses Forge's FakePlayer -- a real
     * ServerPlayer subtype -- instead of GameTestHelper#makeMockPlayer's plain Player. Guild
     * creation/membership itself needs no login/network flow, just GuildManager calls against the
     * test's own ServerLevel, so this is fully self-contained and doesn't need a real multiplayer
     * session to verify.
     */
    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void guildMembersShareAScopedFlagButOutsidersDont(GameTestHelper helper) {
        if (!ModList.get().isLoaded("phoenix_guilds")) {
            helper.succeed();
            return;
        }

        ServerLevel overworld = helper.getLevel().getServer().overworld();
        GuildManager guilds = GuildManager.get(overworld);
        String flagName = "gametest_guild_researched_reactor";

        ServerPlayer alice = FakePlayerFactory.get(overworld, new GameProfile(UUID.randomUUID(), "gametest-alice"));
        ServerPlayer bob = FakePlayerFactory.get(overworld, new GameProfile(UUID.randomUUID(), "gametest-bob"));
        ServerPlayer outsider = FakePlayerFactory.get(overworld, new GameProfile(UUID.randomUUID(),
                "gametest-outsider"));

        Guild guild = guilds.createGuild("GameTestGuild-" + UUID.randomUUID(), alice.getUUID());
        guilds.addMember(guild.getId(), bob.getUUID());

        try {
            PhoenixQuestFlags.setFlag(flagName, false);
            PhoenixQuestFlags.setFlagForPlayer(alice, flagName, true);

            helper.assertTrue(PhoenixQuestFlags.evaluate("flag:" + flagName, null, alice),
                    "the player who set the flag should see it as true");
            helper.assertTrue(PhoenixQuestFlags.evaluate("flag:" + flagName, null, bob),
                    "a fellow guild member must see the SAME flag as true -- this is the actual " +
                            "guild-sharing behavior (e.g. one member's research unlocking a quest for the whole guild)");
            helper.assertTrue(!PhoenixQuestFlags.evaluate("flag:" + flagName, null, outsider),
                    "a player in no guild (or a different one) must NOT see a flag scoped to someone " +
                            "else's guild");

            helper.succeed();
        } finally {
            PhoenixQuestFlags.clearFlagForPlayer(alice, flagName);
            PhoenixQuestFlags.clearFlag(flagName);
            guilds.disbandGuild(guild.getId());
        }
    }
}
