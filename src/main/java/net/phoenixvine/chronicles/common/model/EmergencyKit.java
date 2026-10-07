package net.phoenixvine.chronicles.common.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.common.registry.QuestEngineConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fallback rewards a player can claim while a quest is active (a lost required item, say), plus how often they can
 * be claimed. A quest has its own kit and each chapter has a default one; the timer settings fall back from the
 * quest, to its chapter, to the engine-wide defaults.
 */
public final class EmergencyKit {

    public enum Repeat {
        INHERIT,
        ONCE,
        REPEATABLE
    }

    public static final int INHERIT_COOLDOWN = -1;

    private final List<QuestReward> rewards = new ArrayList<>();
    private Repeat repeat = Repeat.INHERIT;
    private int cooldownSeconds = INHERIT_COOLDOWN;

    public List<QuestReward> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public void setRewards(List<QuestReward> next) {
        rewards.clear();
        for (QuestReward r : next) if (r != null) rewards.add(r);
    }

    public boolean hasRewards() {
        return !rewards.isEmpty();
    }

    public Repeat getRepeat() {
        return repeat;
    }

    public void setRepeat(Repeat repeat) {
        this.repeat = repeat != null ? repeat : Repeat.INHERIT;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int seconds) {
        this.cooldownSeconds = seconds < 0 ? INHERIT_COOLDOWN : seconds;
    }

    /** Nothing set at all: no rewards and both timer settings inherited, so there is nothing worth saving. */
    public boolean isDefault() {
        return rewards.isEmpty() && repeat == Repeat.INHERIT && cooldownSeconds == INHERIT_COOLDOWN;
    }

    public boolean resolveRepeatable(Repeat fallbackFromChapter) {
        Repeat resolved = repeat != Repeat.INHERIT ? repeat : fallbackFromChapter;
        return resolved == Repeat.INHERIT ? QuestEngineConfig.isEmergencyRepeatable() :
                resolved == Repeat.REPEATABLE;
    }

    public int resolveCooldownSeconds(int fallbackFromChapter) {
        int resolved = cooldownSeconds >= 0 ? cooldownSeconds : fallbackFromChapter;
        return resolved >= 0 ? resolved : QuestEngineConfig.getEmergencyCooldownSeconds();
    }

    public EmergencyKit copy() {
        EmergencyKit copy = new EmergencyKit();
        copy.rewards.addAll(rewards);
        copy.repeat = repeat;
        copy.cooldownSeconds = cooldownSeconds;
        return copy;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (QuestReward r : rewards) list.add(r.serializeNBT());
        if (!list.isEmpty()) tag.put("rewards", list);
        if (repeat != Repeat.INHERIT) tag.putString("repeat", repeat.name().toLowerCase());
        if (cooldownSeconds >= 0) tag.putInt("cooldown_seconds", cooldownSeconds);
        return tag;
    }

    public static EmergencyKit fromNBT(CompoundTag tag) {
        EmergencyKit kit = new EmergencyKit();
        if (tag == null) return kit;
        for (Tag t : tag.getList("rewards", Tag.TAG_COMPOUND)) {
            if (t instanceof CompoundTag ct) {
                QuestReward reward = QuestReward.deserializeNBT(ct);
                if (reward != null) kit.rewards.add(reward);
            }
        }
        if (tag.contains("repeat")) {
            try {
                kit.repeat = Repeat.valueOf(tag.getString("repeat").toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("cooldown_seconds")) kit.setCooldownSeconds(tag.getInt("cooldown_seconds"));
        return kit;
    }

    /** Kits used to be a plain list of item stacks; each becomes an item reward. */
    public static EmergencyKit fromLegacyItems(ListTag items) {
        EmergencyKit kit = new EmergencyKit();
        if (items == null) return kit;
        for (Tag t : items) {
            if (!(t instanceof CompoundTag ct)) continue;
            ItemStack stack = ItemStack.of(ct);
            if (stack.isEmpty()) continue;
            kit.rewards.add(new QuestReward.ItemReward(stack.getItem(), stack.getCount(),
                    stack.hasTag() ? stack.getTag().copy() : null, false));
        }
        return kit;
    }
}
