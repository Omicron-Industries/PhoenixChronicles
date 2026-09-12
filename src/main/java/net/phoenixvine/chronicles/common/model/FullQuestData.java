package net.phoenixvine.chronicles.common.model;

import net.minecraft.network.chat.Component;

import java.util.List;

public record FullQuestData(Component title, Component description, List<String> tasks, List<QuestTask> effectiveTasks,
                            List<QuestReward> effectiveRewards) {}
