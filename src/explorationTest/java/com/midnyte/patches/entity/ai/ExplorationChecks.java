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

        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 1, 0).allowed(),
                "An isolated fresh flower should be interesting");

        for (int i = 0; i < 3; i++) f.record(flower, 0);
        var familiarIsolated = f.evaluate(flower, PatchesCuriosityPriority.LOW, 1, 0);
        check(familiarIsolated.allowed(), "Historical familiarity alone should not quickly suppress an isolated flower");
        check(Math.abs(familiarIsolated.pressure() - 2.5) < 0.0001,
                "Flower history is a weaker contribution than local density");

        var denseFlowers = f.evaluate(flower, PatchesCuriosityPriority.LOW, 3, 0);
        check(!denseFlowers.allowed(), "A locally dense flower scene should saturate quickly");
        check(Math.abs(denseFlowers.pressure() - 4.5) < 0.0001,
                "Local density is the primary flower familiarity signal");

        for (int i = 0; i < 100; i++) f.record(flower, 0);
        var historicallySaturated = f.evaluate(flower, PatchesCuriosityPriority.LOW, 1, 0);
        check(historicallySaturated.history() == 8.0 && !historicallySaturated.allowed(),
                "Very high historical flower familiarity can eventually suppress even an isolated example");

        check(f.evaluate(PatchesFamiliarity.Category.GEODE, PatchesCuriosityPriority.LOW, 1, 0).history() == 0.0,
                "Category history remains independent");

        check(f.evaluate(flower, PatchesCuriosityPriority.LOW, 1, 8L * 20L * 60L * 20L).allowed(),
                "Slow historical decay eventually restores willingness");

        var medium = new PatchesFamiliarity();
        check(medium.evaluate(PatchesFamiliarity.Category.AXOLOTL, PatchesCuriosityPriority.MEDIUM, 4, 0).allowed(),
                "Medium curiosities tolerate a moderately populated local scene");
        check(!medium.evaluate(PatchesFamiliarity.Category.AXOLOTL, PatchesCuriosityPriority.MEDIUM, 5, 0).allowed(),
                "A very dense Medium scene can still saturate");

        var valuables = new PatchesFamiliarity();
        for (int i = 0; i < 20; i++) valuables.record(PatchesFamiliarity.Category.DIAMOND, 0);
        var diamond = valuables.evaluate(PatchesFamiliarity.Category.DIAMOND, PatchesCuriosityPriority.HIGH, 8, 0);
        check(diamond.allowed() && Double.isInfinite(diamond.threshold()),
                "Diamond discoveries are exempt from familiarity suppression");

        var allays = new PatchesFamiliarity();
        for (int i = 0; i < 20; i++) allays.record(PatchesFamiliarity.Category.TRAPPED_ALLAY, 0);
        check(allays.evaluate(PatchesFamiliarity.Category.TRAPPED_ALLAY, PatchesCuriosityPriority.HIGH, 8, 0).allowed(),
                "Trapped Allays are never suppressed by familiarity");

        check(new PatchesFamiliarity().describe(0).equals("all categories historically fresh"),
                "Reload resets only session historical familiarity");
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
