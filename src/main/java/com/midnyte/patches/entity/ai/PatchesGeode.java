package com.midnyte.patches.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Structural inference only. The caller MUST establish ordinary sight of the seed first. */
public final class PatchesGeode {
    private static final int MAX_BLOCKS = 2048;
    private static final int MAX_SPAN = 24;

    public record Feature(BlockPos min, BlockPos max, List<BlockPos> interior) {
        public String memoryKey(String dimension) {
            return dimension + "|" + min.getX() + "," + min.getY() + "," + min.getZ()
                    + "," + max.getX() + "," + max.getY() + "," + max.getZ();
        }

        /** Heavy overlap tolerates a mined shell block without merging merely adjacent geodes. */
        public boolean matchesMemory(String dimension, String memory) {
            String[] parts = memory.split("\\|", -1);
            if (parts.length != 2 || !parts[0].equals(dimension)) return false;
            String[] xyz = parts[1].split(",");
            if (xyz.length != 6) return false;
            try {
                int x0 = Integer.parseInt(xyz[0]), y0 = Integer.parseInt(xyz[1]), z0 = Integer.parseInt(xyz[2]);
                int x1 = Integer.parseInt(xyz[3]), y1 = Integer.parseInt(xyz[4]), z1 = Integer.parseInt(xyz[5]);
                if (x1 < x0 || y1 < y0 || z1 < z0) return false;
                double overlap = (double) Math.max(0, Math.min(max.getX(), x1) - Math.max(min.getX(), x0) + 1)
                        * Math.max(0, Math.min(max.getY(), y1) - Math.max(min.getY(), y0) + 1)
                        * Math.max(0, Math.min(max.getZ(), z1) - Math.max(min.getZ(), z0) + 1);
                double oldVolume = ((double) x1 - x0 + 1) * ((double) y1 - y0 + 1) * ((double) z1 - z0 + 1);
                double volume = (double) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
                return overlap >= 0.65 * Math.max(volume, oldVolume);
            } catch (NumberFormatException ignored) { return false; }
        }
    }

    public static boolean isInterior(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    /** Null states denote unloaded blocks: incomplete features fail closed, never load chunks. */
    public static Feature recognize(BlockPos seed, Function<BlockPos, BlockState> stateAt) {
        BlockState seedState = stateAt.apply(seed);
        if (seedState == null || !isInterior(seedState)) return null;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> interior = new ArrayList<>();
        queue.add(seed.immutable());
        seen.add(seed.immutable());
        int minX = seed.getX(), minY = seed.getY(), minZ = seed.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        int layeredSamples = 0;
        Set<Direction> layerDirections = new HashSet<>();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            interior.add(pos);
            if (interior.size() > MAX_BLOCKS) return null;
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
            if (maxX - minX > MAX_SPAN || maxY - minY > MAX_SPAN || maxZ - minZ > MAX_SPAN) return null;
            boolean layered = false;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                BlockState state = stateAt.apply(next);
                if (state == null) return null;
                if (isInterior(state) && seen.add(next)) queue.addLast(next);
                if (!state.is(Blocks.CALCITE)) continue;
                // Ordered inner amethyst -> one/two calcite -> smooth basalt.
                for (int depth = 2; depth <= 3; depth++) {
                    BlockState outer = stateAt.apply(pos.relative(direction, depth));
                    if (outer == null) return null;
                    if (outer.is(Blocks.SMOOTH_BASALT)) { layered = true; layerDirections.add(direction); break; }
                    if (!outer.is(Blocks.CALCITE)) break;
                }
            }
            if (layered) layeredSamples++;
        }
        // Multiple walls/layer directions reject an isolated crystal or a single decorative panel.
        if (interior.size() < 24 || layeredSamples < 6 || layerDirections.size() < 3) return null;
        return new Feature(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), List.copyOf(interior));
    }

    private PatchesGeode() {}
}
