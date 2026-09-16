package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class PatchesFollowGoal extends Goal {
    private static final boolean DEBUG_FOLLOW_STATE = true;

    private static final double RELAXED_DISTANCE = 6.0;
    private static final double ATTENTIVE_DISTANCE = 9.0;
    private static final double CATCH_UP_DISTANCE = 12.0;
    private static final double HURRY_DISTANCE = 16.0;
    private static final double WARP_DISTANCE = 25.0;

    private static final double FOLLOW_RELEASE_DISTANCE = 3.0;
    private static final double HURRY_RELEASE_DISTANCE = 12.0;

    private static final double WALKING_AWAY_RATE = 0.035;
    private static final double SPRINTING_AWAY_RATE = 0.09;

    private static final double CATCH_UP_SPEED = 1.15;
    private static final double HURRY_SPEED = 1.35;

    private static final int PATH_RECALC_TICKS = 10;
    private static final int NO_PROGRESS_LIMIT = 10;
    private static final double MIN_PROGRESS_PER_CHECK = 0.05;
    private static final double PATH_FAILURE_WARP_DISTANCE = 16.0;
    private static final int WARP_GROUND_SEARCH_DEPTH = 12;

    private final PatchesEntity patches;
    private Player player;
    private int recalcTicks;
    private int failedProgressChecks;
    private double lastProgressDistance = Double.NaN;
    private PatchesFollowUrgency urgency = PatchesFollowUrgency.RELAXED;

    public PatchesFollowGoal(PatchesEntity patches) {
        this.patches = patches;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (patches.getMode() != PatchesMode.FOLLOWING) return false;

        Player candidate = patches.getFollowingPlayer();
        if (candidate == null || candidate.isSpectator() || !candidate.isAlive()) return false;

        double distance = patches.distanceTo(candidate);
        double separationRate = getSeparationRate(candidate);
        if (distance < RELAXED_DISTANCE || (distance < ATTENTIVE_DISTANCE && separationRate <= 0.0)) {
            return false;
        }

        this.player = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (patches.getMode() != PatchesMode.FOLLOWING
                || player == null
                || !player.isAlive()
                || player.isSpectator()) {
            return false;
        }

        if (urgency == PatchesFollowUrgency.ATTENTIVE) {
            return distanceToPlayer() >= RELAXED_DISTANCE;
        }
        return distanceToPlayer() > FOLLOW_RELEASE_DISTANCE;
    }

    @Override
    public void start() {
        recalcTicks = 0;
        failedProgressChecks = 0;
        lastProgressDistance = Double.NaN;
        urgency = determineUrgency(distanceToPlayer(), getSeparationRate());
        reportState(reasonFor(urgency, getSeparationRate()), distanceToPlayer(), getSeparationRate());
    }

    @Override
    public void stop() {
        if (DEBUG_FOLLOW_STATE && player != null && patches.getMode() == PatchesMode.FOLLOWING) {
            urgency = PatchesFollowUrgency.RELAXED;
            reportState("Rejoined player; free to wander.", distanceToPlayer(), getSeparationRate());
        }
        player = null;
        patches.getNavigation().stop();
        failedProgressChecks = 0;
        lastProgressDistance = Double.NaN;
        urgency = PatchesFollowUrgency.RELAXED;
    }

    @Override
    public void tick() {
        if (player == null) return;

        double distance = distanceToPlayer();
        double separationRate = getSeparationRate();
        PatchesFollowUrgency desired = determineUrgency(distance, separationRate);

        if (desired != urgency) {
            urgency = desired;
            reportState(reasonFor(desired, separationRate), distance, separationRate);
        }

        if (urgency == PatchesFollowUrgency.WARP) {
            if (tryWarpNearPlayer()) {
                failedProgressChecks = 0;
                lastProgressDistance = distanceToPlayer();
                reportState("Emergency warp succeeded; rejoined player.", distanceToPlayer(), 0.0);
            }
            return;
        }

        patches.getLookControl().setLookAt(player, 10.0F, patches.getMaxHeadXRot());

        if (urgency == PatchesFollowUrgency.ATTENTIVE) {
            patches.getNavigation().stop();
            failedProgressChecks = 0;
            lastProgressDistance = distance;
            return;
        }

        if (urgency == PatchesFollowUrgency.RELAXED) {
            urgency = PatchesFollowUrgency.CATCH_UP;
        }

        if (--recalcTicks <= 0) {
            recalcTicks = adjustedTickDelay(PATH_RECALC_TICKS);

            double speed = urgency == PatchesFollowUrgency.HURRY ? HURRY_SPEED : CATCH_UP_SPEED;
            boolean pathStarted = patches.getNavigation().moveTo(player, speed);

            if (!pathStarted) {
                // A rejected moveTo call by itself is not proof that Patches is
                // stuck. Navigation can already have a useful path, and moving
                // targets can make individual recalculations fail transiently.
                if (patches.getNavigation().isDone()) {
                    failedProgressChecks++;
                }
            } else {
                updateProgress(distance);
            }

            // Only call this a path-recovery failure when Patches is already in
            // the Hurry range, navigation has repeatedly been unable to make
            // meaningful progress for several seconds, and there is no active
            // path left to follow. Ordinary flat-ground pursuit should never
            // trip this merely because the player keeps moving.
            if (distance >= PATH_FAILURE_WARP_DISTANCE
                    && failedProgressChecks >= NO_PROGRESS_LIMIT
                    && patches.getNavigation().isDone()) {
                urgency = PatchesFollowUrgency.WARP;
                reportState("Unable to make pathing progress; using recovery warp.", distance, separationRate);
            }
        }
    }

    private PatchesFollowUrgency determineUrgency(double distance, double separationRate) {
        if (urgency == PatchesFollowUrgency.WARP) {
            return PatchesFollowUrgency.WARP;
        }

        if (distance >= WARP_DISTANCE) {
            return PatchesFollowUrgency.WARP;
        }

        if (urgency == PatchesFollowUrgency.HURRY && distance > HURRY_RELEASE_DISTANCE) {
            return PatchesFollowUrgency.HURRY;
        }

        if (urgency == PatchesFollowUrgency.CATCH_UP && distance > FOLLOW_RELEASE_DISTANCE) {
            if (distance >= HURRY_DISTANCE || (distance >= CATCH_UP_DISTANCE && separationRate >= SPRINTING_AWAY_RATE)) {
                return PatchesFollowUrgency.HURRY;
            }
            return PatchesFollowUrgency.CATCH_UP;
        }

        if (distance >= HURRY_DISTANCE || (distance >= CATCH_UP_DISTANCE && separationRate >= SPRINTING_AWAY_RATE)) {
            return PatchesFollowUrgency.HURRY;
        }

        if (distance >= CATCH_UP_DISTANCE) {
            return PatchesFollowUrgency.CATCH_UP;
        }

        if (distance >= ATTENTIVE_DISTANCE || (distance >= RELAXED_DISTANCE && separationRate >= WALKING_AWAY_RATE)) {
            return PatchesFollowUrgency.ATTENTIVE;
        }

        return PatchesFollowUrgency.RELAXED;
    }

    private double getSeparationRate() {
        return player == null ? 0.0 : getSeparationRate(player);
    }

    private double getSeparationRate(Player target) {
        Vec3 toPlayer = target.position().subtract(patches.position());
        double horizontalDistance = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
        if (horizontalDistance < 1.0e-4) return 0.0;

        Vec3 movement = target.getDeltaMovement();
        double awayX = -toPlayer.x / horizontalDistance;
        double awayZ = -toPlayer.z / horizontalDistance;
        return movement.x * awayX + movement.z * awayZ;
    }

    private void updateProgress(double distance) {
        if (Double.isNaN(lastProgressDistance)) {
            lastProgressDistance = distance;
            failedProgressChecks = 0;
            return;
        }

        if (lastProgressDistance - distance >= MIN_PROGRESS_PER_CHECK) {
            failedProgressChecks = 0;
        } else if (patches.getNavigation().isDone()) {
            // Lack of distance gain only counts against Patches when he also
            // has no active path. If he is still navigating, let the path play
            // out rather than interpreting pursuit of a moving player as stuck.
            failedProgressChecks++;
        } else {
            failedProgressChecks = 0;
        }

        lastProgressDistance = distance;
    }

    private boolean tryWarpNearPlayer() {
        if (player == null) return false;

        int baseX = player.getBlockX();
        int baseY = player.getBlockY();
        int baseZ = player.getBlockZ();

        int[][] horizontalOffsets = {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 2}, {2, -2}, {-2, 2}, {-2, -2},
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };

        for (int[] offset : horizontalOffsets) {
            BlockPos landing = findGroundedLanding(baseX + offset[0], baseY, baseZ + offset[1]);
            if (landing == null) continue;

            double x = landing.getX() + 0.5;
            double y = landing.getY();
            double z = landing.getZ() + 0.5;
            if (patches.randomTeleport(x, y, z, true, state -> true)) {
                patches.getNavigation().stop();
                patches.setDeltaMovement(Vec3.ZERO);
                return true;
            }
        }

        return false;
    }

    private BlockPos findGroundedLanding(int x, int startY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);

        for (int depth = 0; depth <= WARP_GROUND_SEARCH_DEPTH; depth++) {
            int y = startY - depth;
            cursor.set(x, y, z);
            BlockState feet = patches.level().getBlockState(cursor);
            BlockState head = patches.level().getBlockState(cursor.above());
            BlockState below = patches.level().getBlockState(cursor.below());

            if (feet.getCollisionShape(patches.level(), cursor).isEmpty()
                    && head.getCollisionShape(patches.level(), cursor.above()).isEmpty()
                    && !below.getCollisionShape(patches.level(), cursor.below()).isEmpty()) {
                return cursor.immutable();
            }
        }

        return null;
    }

    private double distanceToPlayer() {
        return player == null ? 0.0 : patches.distanceTo(player);
    }

    private String reasonFor(PatchesFollowUrgency state, double separationRate) {
        return switch (state) {
            case RELAXED -> separationRate < 0.0
                    ? "Player approaching; no need to chase."
                    : "Player nearby; free to wander.";
            case ATTENTIVE -> "Player is leaving; watching before committing to chase.";
            case CATCH_UP -> "Player is getting away; catching up.";
            case HURRY -> "Player is well ahead; hurrying to rejoin.";
            case WARP -> "Player too distant; using emergency warp.";
        };
    }

    private void reportState(String reason, double distance, double separationRate) {
        if (!DEBUG_FOLLOW_STATE || player == null) return;

        String message = String.format(
                "[Patches] FOLLOW: %s — %s (distance %.1f, separation %+.3f/tick)",
                urgencyDisplayName(urgency),
                reason,
                distance,
                separationRate
        );
        player.sendSystemMessage(Component.literal(message));
    }

    private static String urgencyDisplayName(PatchesFollowUrgency state) {
        return switch (state) {
            case RELAXED -> "Relaxed";
            case ATTENTIVE -> "Attentive";
            case CATCH_UP -> "Catch Up";
            case HURRY -> "Hurry";
            case WARP -> "Warp";
        };
    }
}
