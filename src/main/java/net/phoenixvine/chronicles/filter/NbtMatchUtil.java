package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.*;

public final class NbtMatchUtil {

    private NbtMatchUtil() {}

    public static boolean matches(CompoundTag actual, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (actual == null) return false;

        return matchesCompound(actual, filter);
    }

    public static boolean matchesCompound(CompoundTag actual, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (actual == null) return false;

        for (String key : filter.getAllKeys()) {
            if (!actual.contains(key)) return false;
            Tag actualValue = actual.get(key);
            Tag filterValue = filter.get(key);
            if (!matchesTag(actualValue, filterValue)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesTag(Tag actual, Tag filter) {
        if (filter == null) return true;
        if (actual == null) return false;

        if (filter instanceof CompoundTag filterCompound) {
            if (actual instanceof CompoundTag actualCompound) {
                return matchesCompound(actualCompound, filterCompound);
            }
            return false;
        }

        if (filter instanceof NumericTag filterNum) {
            if (actual instanceof NumericTag actualNum) {
                return matchesNumber(actualNum, filterNum);
            }
            return false;
        }

        if (filter instanceof ListTag filterList) {
            if (actual instanceof ListTag actualList) {
                return matchesList(actualList, filterList);
            }
            return false;
        }

        if (filter instanceof StringTag filterStr && actual instanceof StringTag actualStr) {
            return actualStr.getAsString().equals(filterStr.getAsString());
        }

        return actual.equals(filter);
    }

    private static boolean matchesNumber(NumericTag actual, NumericTag filter) {
        if (filter instanceof FloatTag || filter instanceof DoubleTag || actual instanceof FloatTag ||
                actual instanceof DoubleTag) {
            return Math.abs(actual.getAsDouble() - filter.getAsDouble()) < 1e-6;
        }
        return actual.getAsLong() == filter.getAsLong();
    }

    private static boolean matchesList(ListTag actual, ListTag filter) {
        if (filter.isEmpty()) return true;
        if (actual.isEmpty()) return false;

        for (Tag filterItem : filter) {
            boolean foundMatch = false;
            for (Tag actualItem : actual) {
                if (matchesTag(actualItem, filterItem)) {
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) return false;
        }
        return true;
    }
}
