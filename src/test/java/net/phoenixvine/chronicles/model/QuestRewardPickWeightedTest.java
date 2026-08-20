package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRewardPickWeightedTest {

    private static final class StubReward extends QuestReward {

        final String name;

        StubReward(String name) {
            this.name = name;
        }

        @Override
        public RewardType getType() {
            return RewardType.SCRIPT_EVENT;
        }

        @Override
        public Component getSummary() {
            return Component.literal(name);
        }

        @Override
        public void grant(ServerPlayer player) {}

        @Override
        public CompoundTag serializeNBT() {
            return new CompoundTag();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Test
    void emptyOrNullPoolReturnsEmptyList() {
        assertTrue(QuestReward.pickWeighted(null, 3, new Random()).isEmpty());
        assertTrue(QuestReward.pickWeighted(List.of(), 3, new Random()).isEmpty());
    }

    @Test
    void nonPositiveCountReturnsEmptyList() {
        List<QuestReward.WeightedReward> pool = List.of(new QuestReward.WeightedReward(new StubReward("a"), 1));
        assertTrue(QuestReward.pickWeighted(pool, 0, new Random()).isEmpty());
        assertTrue(QuestReward.pickWeighted(pool, -1, new Random()).isEmpty());
    }

    @Test
    void requestingMoreThanPoolSizeReturnsEveryEntryExactlyOnce() {
        QuestReward a = new StubReward("a");
        QuestReward b = new StubReward("b");
        List<QuestReward.WeightedReward> pool = List.of(new QuestReward.WeightedReward(a, 1),
                new QuestReward.WeightedReward(b, 1));

        List<QuestReward> picked = QuestReward.pickWeighted(pool, 10, new Random(42));

        assertEquals(2, picked.size());
        assertTrue(picked.contains(a));
        assertTrue(picked.contains(b));
    }

    @Test
    void neverPicksTheSameEntryTwice() {
        QuestReward a = new StubReward("a");
        QuestReward b = new StubReward("b");
        QuestReward c = new StubReward("c");
        List<QuestReward.WeightedReward> pool = List.of(new QuestReward.WeightedReward(a, 5),
                new QuestReward.WeightedReward(b, 5), new QuestReward.WeightedReward(c, 5));

        List<QuestReward> picked = QuestReward.pickWeighted(pool, 2, new Random(7));

        assertEquals(2, picked.size());
        assertEquals(picked.size(), java.util.Set.copyOf(picked).size(), "no duplicates expected");
    }

    @Test
    void zeroOrNegativeWeightIsClampedToOneRatherThanExcludingTheEntry() {
        QuestReward heavy = new StubReward("heavy");
        QuestReward weightless = new StubReward("weightless");
        QuestReward.WeightedReward zeroWeight = new QuestReward.WeightedReward(weightless, 0);

        assertEquals(1, zeroWeight.weight());

        boolean everPicked = false;
        for (long seed = 0; seed < 200 && !everPicked; seed++) {
            List<QuestReward.WeightedReward> pool = List.of(new QuestReward.WeightedReward(heavy, 1000), zeroWeight);
            List<QuestReward> picked = QuestReward.pickWeighted(pool, 1, new Random(seed));
            everPicked = picked.contains(weightless);
        }
        assertTrue(everPicked, "a weight-0 entry clamped to 1 must be reachable, just rare");
    }

    @Test
    void deterministicForAFixedSeed() {
        QuestReward a = new StubReward("a");
        QuestReward b = new StubReward("b");
        List<QuestReward.WeightedReward> pool = List.of(new QuestReward.WeightedReward(a, 3),
                new QuestReward.WeightedReward(b, 1));

        List<QuestReward> first = QuestReward.pickWeighted(pool, 1, new Random(123));
        List<QuestReward> second = QuestReward.pickWeighted(pool, 1, new Random(123));

        assertEquals(first, second);
    }
}
