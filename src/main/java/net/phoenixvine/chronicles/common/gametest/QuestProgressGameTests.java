package net.phoenixvine.chronicles.common.gametest;

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
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class QuestProgressGameTests {

    private static @NotNull QuestNode node(@NotNull String path) {
        return new QuestNode(ResourceLocation.fromNamespaceAndPath(PhoenixChronicles.MOD_ID, path),
                Component.literal(path),
                Component.literal(""));
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void completingAQuestUnlocksItsDependent(@NotNull GameTestHelper helper) {
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
    public static void questWithUnmetPrerequisiteStaysLocked(@NotNull GameTestHelper helper) {
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

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void flagSetForOnePlayerDoesNotLeakToAnother(@NotNull GameTestHelper helper) {
        Player alice = helper.makeMockPlayer();
        Player bob = helper.makeMockPlayer();
        String flagName = "gametest_has_nether_star";

        try {

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

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void guildMembersShareAScopedFlagButOutsidersDont(@NotNull GameTestHelper helper) {
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
