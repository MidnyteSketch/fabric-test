package com.midnyte.patches.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic tests of grouping and saturation; movement/expressions still need in-game testing. */
public final class ExplorationChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        familiarity();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        geodes();
        System.out.println("Exploration checks passed: " + assertions + " assertions");
    }

    private static void familiarity() {
        var f = new PatchesFamiliarity();
        var flower = PatchesFamiliarity.Category.FLOWER;
        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 0, () -> 0).allowed(), "Fresh category must trigger");
        for (int i = 0; i < 3; i++) f.record(flower, 0);
        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 0, () -> 0).allowed(), "Three encounters retain grace");
        for (int i = 0; i < 100; i++) f.record(flower, 0);
        AtomicInteger rolls = new AtomicInteger();
        var saturated = f.evaluate(flower, PatchesCuriosityPriority.LOW, 0, () -> { rolls.incrementAndGet(); return 0.1; });
        check(saturated.score() == 8 && !saturated.allowed(), "Repeated Low encounters saturate and can defer");
        check(Math.abs(saturated.skipChance() - 0.65) < 0.0001, "Low maximum suppression");
        for (int i = 1; i < 200; i++) check(!f.evaluate(flower, PatchesCuriosityPriority.LOW, i, () -> { rolls.incrementAndGet(); return 0.99; }).allowed(), "No scan/individual reroll in decision window");
        check(rolls.get() == 1, "One roll per category/window");
        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 200, () -> 0.99).allowed(), "Next window can trigger");
        check(f.evaluate(PatchesFamiliarity.Category.GEODE, PatchesCuriosityPriority.LOW, 200, () -> 0).score() == 0, "Category independence");
        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 8 * 6000, () -> 0).allowed(), "Decay fully restores willingness");
        for (var priority : PatchesCuriosityPriority.values()) {
            var category = PatchesFamiliarity.Category.DIAMOND;
            var fresh = new PatchesFamiliarity();
            for (int i = 0; i < 20; i++) fresh.record(category, 0);
            double expected = switch (priority) { case LOW -> 0.65; case MEDIUM -> 0.30; case HIGH -> 0.05; };
            check(Math.abs(fresh.evaluate(category, priority, 0, () -> 1).skipChance() - expected) < 0.0001, "Tier retains intended protection: " + priority);
        }
        check(new PatchesFamiliarity().describe(0).equals("all categories fresh"), "Reload resets only short-term familiarity");
    }

    private static Map<BlockPos, BlockState> shell(int offset) {
        Map<BlockPos, BlockState> world = new HashMap<>();
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            int r = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
            if (r < 2) continue;
            world.put(new BlockPos(x + offset, y, z), (r == 2 ? Blocks.AMETHYST_BLOCK : r == 3 ? Blocks.CALCITE : Blocks.SMOOTH_BASALT).defaultBlockState());
        }
        world.put(new BlockPos(offset + 2, 0, 0), Blocks.BUDDING_AMETHYST.defaultBlockState());
        return world;
    }

    private static PatchesGeode.Feature recognize(Map<BlockPos, BlockState> world, BlockPos seed) {
        return PatchesGeode.recognize(seed, pos -> world.getOrDefault(pos, Blocks.AIR.defaultBlockState()));
    }

    private static void geodes() {
        Map<BlockPos, BlockState> world = shell(0);
        BlockPos seed = new BlockPos(2, 0, 0);
        var first = recognize(world, seed);
        check(first != null, "Layered feature qualifies without provenance");
        String key = first.memoryKey("minecraft:overworld");
        for (BlockPos alternate : first.interior()) {
            var same = recognize(world, alternate);
            check(same != null && same.memoryKey("minecraft:overworld").equals(key), "Every inner block resolves to one feature");
        }
        check(!first.matchesMemory("minecraft:the_nether", key), "Memory is dimension-scoped");
        check(!first.matchesMemory("minecraft:overworld", "bad data"), "Malformed memory ignored");
        world.remove(new BlockPos(-2, -2, -2));
        check(recognize(world, seed).matchesMemory("minecraft:overworld", key), "Mining one inner block does not rediscover geode");
        world.putAll(shell(12));
        check(!recognize(world, new BlockPos(14, 0, 0)).matchesMemory("minecraft:overworld", key), "Separate nearby geode remains new");
        var bare = shell(0);
        bare.entrySet().removeIf(entry -> entry.getValue().is(Blocks.CALCITE));
        check(recognize(bare, seed) == null, "No calcite layer: reject");
        bare = shell(0);
        bare.entrySet().removeIf(entry -> entry.getValue().is(Blocks.SMOOTH_BASALT));
        check(recognize(bare, seed) == null, "No basalt layer: reject");
        check(recognize(Map.of(seed, Blocks.AMETHYST_BLOCK.defaultBlockState()), seed) == null, "Isolated amethyst: reject");
        check(recognize(world, new BlockPos(4, 0, 0)) == null, "Basalt is never a detection seed");
        AtomicInteger visits = new AtomicInteger();
        check(PatchesGeode.recognize(seed, pos -> { visits.incrementAndGet(); return Blocks.AMETHYST_BLOCK.defaultBlockState(); }) == null, "Oversized connected structure fails closed");
        check(visits.get() < 13000, "Traversal has a hard work bound");
        check(PatchesGeode.recognize(seed, pos -> pos.equals(seed) ? Blocks.AMETHYST_BLOCK.defaultBlockState() : null) == null, "Unloaded boundary fails closed");
        var layerPanel = new HashMap<BlockPos, BlockState>();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            layerPanel.put(new BlockPos(x, 0, z), Blocks.AMETHYST_BLOCK.defaultBlockState());
            layerPanel.put(new BlockPos(x, -1, z), Blocks.CALCITE.defaultBlockState());
            layerPanel.put(new BlockPos(x, -2, z), Blocks.SMOOTH_BASALT.defaultBlockState());
        }
        check(recognize(layerPanel, BlockPos.ZERO) == null, "One layered decorative panel is not a geode enclosure");
    }
}
