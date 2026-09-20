package com.midnyte.patches.entity.ai;

import java.util.EnumMap;

/**
 * Bounded category familiarity. Exact discovery memories live on PatchesEntity.
 *
 * Local visible saturation is the primary signal; completed encounters add a weaker,
 * slowly decaying historical familiarity. Decisions are deterministic so tuning is
 * observable in-game instead of depending on random rolls.
 */
public final class PatchesFamiliarity {
    public enum Category {
        FLOWER, LOW_BLOCK, GEODE, AXOLOTL, BLUE_AXOLOTL, PINK_SHEEP, TRAPPED_ALLAY,
        WANDERING_TRADER, SNIFFER, ARCHAEOLOGY, LOOT_CONTAINER, LOOT_VEHICLE,
        DIAMOND, EMERALD, ANCIENT_DEBRIS
    }

    private static final double MAX_SCORE = 8.0;
    // One completed encounter fades over roughly twenty minutes of play.
    private static final long DECAY_TICKS = 20L * 60L * 20L;
    private final EnumMap<Category, Entry> entries = new EnumMap<>(Category.class);

    private static final class Entry {
        double score;
        long updated;
    }

    public record Decision(boolean allowed, double history, int localCount, double pressure, double threshold) {}

    private Entry entry(Category category, long now) {
        Entry entry = entries.computeIfAbsent(category, ignored -> {
            Entry e = new Entry();
            e.updated = now;
            return e;
        });
        entry.score = Math.max(0.0, entry.score - Math.max(0L, now - entry.updated) / (double) DECAY_TICKS);
        entry.updated = now;
        return entry;
    }

    public Decision evaluate(Category category, PatchesCuriosityPriority priority, int localCount, long now) {
        Entry entry = entry(category, now);

        // Important discoveries should remain dependable rather than becoming "old news".
        if (category == Category.TRAPPED_ALLAY
                || category == Category.DIAMOND
                || category == Category.EMERALD
                || category == Category.ANCIENT_DEBRIS) {
            return new Decision(true, entry.score, localCount, 0.0, Double.POSITIVE_INFINITY);
        }

        double historyWeight;
        double threshold;
        switch (category) {
            case FLOWER -> { historyWeight = 0.50; threshold = 4.0; }
            case LOW_BLOCK -> { historyWeight = 0.35; threshold = 4.5; }
            case GEODE -> { historyWeight = 0.20; threshold = 4.5; }
            case AXOLOTL, PINK_SHEEP, WANDERING_TRADER, SNIFFER -> {
                historyWeight = 0.25; threshold = 4.5;
            }
            case ARCHAEOLOGY, LOOT_CONTAINER, LOOT_VEHICLE -> {
                historyWeight = 0.20; threshold = 5.0;
            }
            case BLUE_AXOLOTL -> {
                historyWeight = 0.10; threshold = 6.0;
            }
            default -> {
                historyWeight = switch (priority) {
                    case LOW -> 0.35;
                    case MEDIUM -> 0.20;
                    case HIGH -> 0.10;
                };
                threshold = switch (priority) {
                    case LOW -> 4.5;
                    case MEDIUM -> 5.0;
                    case HIGH -> 6.0;
                };
            }
        }

        int boundedLocal = Math.max(1, Math.min(8, localCount));
        double pressure = boundedLocal + entry.score * historyWeight;
        return new Decision(pressure < threshold, entry.score, localCount, pressure, threshold);
    }

    public double record(Category category, long now) {
        Entry entry = entry(category, now);
        entry.score = Math.min(MAX_SCORE, entry.score + 1.0);
        return entry.score;
    }

    public String describe(long now) {
        StringBuilder result = new StringBuilder();
        for (Category category : Category.values()) {
            if (!entries.containsKey(category)) continue;
            double score = entry(category, now).score;
            if (score < 0.01) continue;
            if (!result.isEmpty()) result.append(", ");
            result.append(category).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", score));
        }
        return result.isEmpty() ? "all categories historically fresh" : result.toString();
    }
}
