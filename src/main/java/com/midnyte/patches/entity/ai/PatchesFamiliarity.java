package com.midnyte.patches.entity.ai;

import java.util.EnumMap;
import java.util.function.DoubleSupplier;

/** Short-term, bounded category saturation. Exact discovery memories live on PatchesEntity. */
public final class PatchesFamiliarity {
    public enum Category {
        FLOWER, LOW_BLOCK, GEODE, AXOLOTL, BLUE_AXOLOTL, PINK_SHEEP, TRAPPED_ALLAY,
        WANDERING_TRADER, SNIFFER, ARCHAEOLOGY, LOOT_CONTAINER, LOOT_VEHICLE,
        DIAMOND, EMERALD, ANCIENT_DEBRIS
    }

    private static final double MAX_SCORE = 8.0;
    private static final double GRACE = 3.0;
    private static final long DECAY_TICKS = 6000;
    private static final long DECISION_TICKS = 200;
    private final EnumMap<Category, Entry> entries = new EnumMap<>(Category.class);

    private static final class Entry {
        double score;
        long updated;
        long nextDecision;
        boolean allowed = true;
    }

    public record Decision(boolean allowed, boolean fresh, double score, double skipChance) {}

    private Entry entry(Category category, long now) {
        Entry entry = entries.computeIfAbsent(category, ignored -> { Entry e = new Entry(); e.updated = now; return e; });
        entry.score = Math.max(0, entry.score - Math.max(0, now - entry.updated) / (double) DECAY_TICKS);
        entry.updated = now;
        return entry;
    }

    public Decision evaluate(Category category, PatchesCuriosityPriority priority, long now, DoubleSupplier random) {
        Entry entry = entry(category, now);
        double cap = switch (priority) { case LOW -> 0.65; case MEDIUM -> 0.30; case HIGH -> 0.05; };
        double chance = cap * Math.max(0, entry.score - GRACE) / (MAX_SCORE - GRACE);
        boolean fresh = now >= entry.nextDecision;
        if (fresh) {
            entry.allowed = chance == 0 || random.getAsDouble() >= chance;
            entry.nextDecision = now + DECISION_TICKS;
        }
        return new Decision(entry.allowed, fresh, entry.score, chance);
    }

    public double record(Category category, long now) {
        Entry entry = entry(category, now);
        entry.score = Math.min(MAX_SCORE, entry.score + 1);
        entry.nextDecision = now;
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
        return result.isEmpty() ? "all categories fresh" : result.toString();
    }
}
