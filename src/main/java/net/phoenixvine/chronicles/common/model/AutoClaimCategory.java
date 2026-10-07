package net.phoenixvine.chronicles.common.model;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of reward a player can choose to have claimed for them the moment a quest completes. Only rewards in an
 * enabled category are granted; the rest of the quest's rewards stay waiting in the claim screen, so nothing a
 * player turned off is ever granted behind their back.
 */
public enum AutoClaimCategory {

    ITEMS("Items & XP", "Item, fluid and XP rewards"),
    COMMANDS("Commands & events", "Commands, script events, external rewards and quest actions"),
    LOOT_TABLES("Loot tables", "Loot tables, reward tables and loot crates"),
    LOOT_BOXES("Loot boxes", "Choice boxes in Lootbox mode - opened for you, granting a random option"),
    CHOICE_BOXES("Choice boxes", "Menu and All boxes. Ones you pick from stay waiting for you to open");

    private final String label;
    private final String description;

    AutoClaimCategory(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** The category a reward belongs to, or null for rewards that are never auto-claimed (they open a screen). */
    @Nullable
    public static AutoClaimCategory of(QuestReward reward) {
        return switch (reward.getType()) {
            case ITEM, XP, FLUID -> ITEMS;
            case COMMAND, SCRIPT_EVENT, EXTERNAL, QUEST_ACTION -> COMMANDS;
            case LOOT_TABLE, REWARD_TABLE, LOOT_CRATE, CONFLUX_UNLOCK -> LOOT_TABLES;
            case CHOICE_BOX -> reward instanceof QuestReward.ChoiceBoxReward box &&
                    box.getMode() == QuestReward.ChoiceBoxReward.Mode.LOOTBOX ? LOOT_BOXES : CHOICE_BOXES;
            case OPEN_SCREEN -> null;
        };
    }

    public static int toMask(Set<AutoClaimCategory> categories) {
        int mask = 0;
        for (AutoClaimCategory c : categories) mask |= 1 << c.ordinal();
        return mask;
    }

    public static EnumSet<AutoClaimCategory> fromMask(int mask) {
        EnumSet<AutoClaimCategory> set = EnumSet.noneOf(AutoClaimCategory.class);
        for (AutoClaimCategory c : values()) if ((mask & (1 << c.ordinal())) != 0) set.add(c);
        return set;
    }
}
