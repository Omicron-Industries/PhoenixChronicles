package net.phoenixvine.chronicles.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

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
}
