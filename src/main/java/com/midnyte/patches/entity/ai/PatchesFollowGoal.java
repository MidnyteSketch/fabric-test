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
    private static final double TOWARD_RATE = -0.02;
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
    private boolean catchUpCommitted;

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

        if (catchUpCommitted) {
            return distanceToPlayer() > FOLLOW_RELEASE_DISTANCE;
        }

        return urgency == PatchesFollowUrgency.ATTENTIVE && distanceToPlayer() >= RELAXED_DISTANCE;
    }

    @Override
    public void start() {
        recalcTicks = 0;
        failedProgressChecks = 0;
        lastProgressDistance = Double.NaN;
        catchUpCommitted = false;
        urgency = determineUncommittedUrgency(distanceToPlayer(), getSeparationRate());
        if (urgency == PatchesFollowUrgency.CATCH_UP
                || urgency == PatchesFollowUrgency.HURRY
                || urgency == PatchesFollowUrgency.WARP) {
            catchUpCommitted = true;
        }
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
        catchUpCommitted = false;
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
                catchUpCommitted = true;
                urgency = PatchesFollowUrgency.CATCH_UP;
                reportState("Emergency warp succeeded; finishing rejoin.", distanceToPlayer(), 0.0);
            }
            return;
        }

        patches.getLookControl().setLookAt(player, 10.0F, patches.getMaxHeadXRot());

        if (urgency == PatchesFollowUrgency.ATTENTIVE && !catchUpCommitted) {
            patches.getNavigation().stop();
            failedProgressChecks = 0;
            lastProgressDistance = distance;
            return;
        }

        if (--recalcTicks <= 0) {
            recalcTicks = adjustedTickDelay(PATH_RECALC_TICKS);

            double speed = urgency == PatchesFollowUrgency.HURRY ? HURRY_SPEED : CATCH_UP_SPEED;
            boolean pathStarted = patches.getNavigation().moveTo(player, speed);

            if (!pathStarted) {
                if (patches.getNavigation().isDone()) {
                    failedProgressChecks++;
                }
            } else {
                updateProgress(distance);
            }

            if (distance >= PATH_FAILURE_WARP_DISTANCE
                    && failedProgressChecks >= NO_PROGRESS_LIMIT
                    && patches.getNavigation().isDone()) {
                catchUpCommitted = true;
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
            catchUpCommitted = true;
            return PatchesFollowUrgency.WARP;
        }

        if (catchUpCommitted) {
            // Once Patches has actually started chasing, Attentive is no longer
            // available. Stopping or walking back toward him should let him
            // finish the rejoin all the way to three blocks.
            if (distance >= HURRY_DISTANCE) {
                return PatchesFollowUrgency.HURRY;
            }
            if (urgency == PatchesFollowUrgency.HURRY && distance > HURRY_RELEASE_DISTANCE) {
                return PatchesFollowUrgency.HURRY;
            }
            return PatchesFollowUrgency.CATCH_UP;
        }

        PatchesFollowUrgency state = determineUncommittedUrgency(distance, separationRate);
        if (state == PatchesFollowUrgency.CATCH_UP
                || state == PatchesFollowUrgency.HURRY
                || state == PatchesFollowUrgency.WARP) {
            catchUpCommitted = true;
        }
        return state;
    }

    private PatchesFollowUrgency determineUncommittedUrgency(double distance, double separationRate) {
        if (distance >= WARP_DISTANCE) return PatchesFollowUrgency.WARP;
        if (distance >= HURRY_DISTANCE) return PatchesFollowUrgency.HURRY;

        // Moving toward Patches must never turn Attentive into Catch Up. The
        // player is already closing the separation themselves.
        if (separationRate <= TOWARD_RATE) {
            return distance >= RELAXED_DISTANCE ? PatchesFollowUrgency.ATTENTIVE : PatchesFollowUrgency.RELAXED;
        }

        if (distance >= CATCH_UP_DISTANCE) return PatchesFollowUrgency.CATCH_UP;
        if (distance >= ATTENTIVE_DISTANCE
                || (distance >= RELAXED_DISTANCE && separationRate >= WALKING_AWAY_RATE)) {
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

            // We already validated a solid floor and clear feet/head space.
            // Directly place Patches on that grounded position rather than
            // asking randomTeleport to perform a second, stricter validation
            // that was rejecting otherwise valid superflat destinations.
            patches.teleportTo(x, y, z);
            patches.getNavigation().stop();
            patches.setDeltaMovement(Vec3.ZERO);
            return patches.distanceTo(player) < 6.0F;
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
            case ATTENTIVE -> separationRate < 0.0
                    ? "Player is coming back; watching without chasing."
                    : "Player is leaving; watching before committing to chase.";
            case CATCH_UP -> "Committed to rejoining player.";
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
