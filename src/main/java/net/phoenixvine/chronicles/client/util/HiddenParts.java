package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;

public final class HiddenParts {

    private HiddenParts() {}

    public static boolean questCompleted(QuestNode node) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || node == null) return false;
        return mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(d -> d.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED).orElse(false);
    }

    public static boolean taskDone(QuestNode node, QuestTask task) {
        Minecraft mc = Minecraft.getInstance();
        return questCompleted(node) || (mc.player != null && task.isCompletedFor(mc.player));
    }

    public static boolean taskTargetHidden(QuestNode node, QuestTask task) {
        return task.hidesTarget() && !taskDone(node, task);
    }

    public static boolean taskAmountHidden(QuestNode node, QuestTask task) {
        return task.hidesAmount() && !taskDone(node, task);
    }

    public static boolean rewardTargetHidden(QuestNode node, QuestReward reward) {
        return reward.hidesTarget() && !questCompleted(node);
    }

    public static boolean rewardAmountHidden(QuestNode node, QuestReward reward) {
        return reward.hidesAmount() && !questCompleted(node);
    }
}
