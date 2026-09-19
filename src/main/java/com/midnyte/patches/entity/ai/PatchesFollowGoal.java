package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class PatchesFollowGoal extends Goal {
    private static final boolean DEBUG_FOLLOW_STATE = false;
    private static final double RELAXED_DISTANCE = 6.0;
    private static final double CATCH_UP_DISTANCE = 12.0;
    private static final double HURRY_DISTANCE = 16.0;
    private static final double WARP_DISTANCE = 25.0;
    private static final double FOLLOW_RELEASE_DISTANCE = 3.0;
    private static final double HURRY_RELEASE_DISTANCE = 12.0;
    private static final double WALKING_AWAY_RATE = 0.035;
    private static final double ATTENTIVE_DISTANCE_CHANGE_EPSILON = 0.025;
    private static final int ATTENTIVE_PATIENCE_TICKS = 100;
    private static final double CATCH_UP_SPEED = 1.15;
    private static final double HURRY_SPEED = 1.35;
    private static final int PATH_RECALC_TICKS = 10;
    private static final int NO_PROGRESS_LIMIT = 10;
    private static final int RECALL_NO_PROGRESS_LIMIT = 4;
    private static final double MIN_PROGRESS_PER_CHECK = 0.05;
    private static final double PATH_FAILURE_WARP_DISTANCE = 12.0;
    private static final int WARP_ATTEMPTS = 10;

    private final PatchesEntity patches;
    private Player player;
    private int recalcTicks;
    private int failedProgressChecks;
    private int attentiveIdleTicks;
    private double lastProgressDistance = Double.NaN;
    private double lastAttentiveDistance = Double.NaN;
    private Vec3 lastObservedPlayerPosition;
    private double lastObservedDepartureRate;
    private PatchesFollowUrgency urgency = PatchesFollowUrgency.RELAXED;
    private boolean catchUpCommitted;
    private boolean attentiveTimedOut;
    private boolean recallActive;
    private float oldWaterCost;
    private boolean waterCostAdjusted;

    public PatchesFollowGoal(PatchesEntity patches) {
        this.patches = patches;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void requestRecall() {
        recallActive = true;
        catchUpCommitted = true;
        urgency = PatchesFollowUrgency.HURRY;
        attentiveTimedOut = false;
        recalcTicks = 0;
        failedProgressChecks = 0;
        lastProgressDistance = Double.NaN;
    }

    @Override
    public boolean canUse() {
        if (patches.getMode() != PatchesMode.FOLLOWING) {
            recallActive = false;
            resetPlayerObservation();
            return false;
        }

        Player candidate = patches.getFollowingPlayer();
        if (candidate == null || candidate.isSpectator() || !candidate.isAlive()) {
            recallActive = false;
            resetPlayerObservation();
            return false;
        }

        if (recallActive) {
            player = candidate;
            return patches.distanceTo(candidate) > FOLLOW_RELEASE_DISTANCE;
        }

        double distance = patches.distanceTo(candidate);
        double rate = observePlayerDeparture(candidate);
        if (distance < CATCH_UP_DISTANCE && rate < WALKING_AWAY_RATE) return false;
        if (distance < RELAXED_DISTANCE) return false;
        player = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (patches.getMode() != PatchesMode.FOLLOWING || player == null || !player.isAlive() || player.isSpectator()) return false;
        if (recallActive) return distanceToPlayer() > FOLLOW_RELEASE_DISTANCE;
        if (attentiveTimedOut && !catchUpCommitted) return false;
        if (catchUpCommitted) return distanceToPlayer() > FOLLOW_RELEASE_DISTANCE;
        return urgency == PatchesFollowUrgency.ATTENTIVE && distanceToPlayer() >= RELAXED_DISTANCE;
    }

    @Override
    public void start() {
        recalcTicks = 0;
        failedProgressChecks = 0;
        attentiveIdleTicks = 0;
        attentiveTimedOut = false;
        lastProgressDistance = Double.NaN;
        lastAttentiveDistance = distanceToPlayer();

        oldWaterCost = patches.getPathfindingMalus(PathType.WATER);
        patches.setPathfindingMalus(PathType.WATER, 0.0F);
        waterCostAdjusted = true;

        if (recallActive) {
            catchUpCommitted = true;
            urgency = PatchesFollowUrgency.HURRY;
            reportState("Goat Horn recall; hustling directly back to player.", distanceToPlayer(), lastObservedDepartureRate);
            return;
        }

        catchUpCommitted = false;
        urgency = determineUncommittedUrgency(distanceToPlayer(), lastObservedDepartureRate);
        if (urgency == PatchesFollowUrgency.CATCH_UP || urgency == PatchesFollowUrgency.HURRY || urgency == PatchesFollowUrgency.WARP) catchUpCommitted = true;
        reportState(reasonFor(urgency), distanceToPlayer(), lastObservedDepartureRate);
    }

    @Override
    public void stop() {
        if (waterCostAdjusted) {
            patches.setPathfindingMalus(PathType.WATER, oldWaterCost);
            waterCostAdjusted = false;
        }

        if (DEBUG_FOLLOW_STATE && player != null && patches.getMode() == PatchesMode.FOLLOWING) {
            urgency = PatchesFollowUrgency.RELAXED;
            reportState(recallActive ? "Recall complete; staying close to player." : attentiveTimedOut ? "Player lingered nearby; returning to own business." : "Rejoined player; free to wander.", distanceToPlayer(), lastObservedDepartureRate);
        }

        recallActive = false;
        player = null;
        patches.getNavigation().stop();
        failedProgressChecks = 0;
        attentiveIdleTicks = 0;
        attentiveTimedOut = false;
        lastProgressDistance = Double.NaN;
        lastAttentiveDistance = Double.NaN;
        catchUpCommitted = false;
        urgency = PatchesFollowUrgency.RELAXED;
    }

    @Override
    public void tick() {
        if (player == null) return;

        double distance = distanceToPlayer();
        double rate = observePlayerDeparture(player);

        if (recallActive) {
            tickRecall(distance, rate);
            return;
        }

        PatchesFollowUrgency desired = determineUrgency(distance, rate);
        if (desired != urgency) {
            urgency = desired;
            attentiveIdleTicks = 0;
            lastAttentiveDistance = distance;
            reportState(reasonFor(desired), distance, rate);
        }

        if (urgency == PatchesFollowUrgency.WARP) {
            if (tryWarpNearPlayer()) {
                failedProgressChecks = 0;
                lastProgressDistance = distanceToPlayer();
                catchUpCommitted = true;
                urgency = PatchesFollowUrgency.CATCH_UP;
                reportState("Recovery warp succeeded; finishing rejoin.", distanceToPlayer(), 0.0);
            }
            return;
        }

        patches.getLookControl().setLookAt(player, 10.0F, patches.getMaxHeadXRot());

        if (urgency == PatchesFollowUrgency.ATTENTIVE && !catchUpCommitted) {
            patches.getNavigation().stop();
            failedProgressChecks = 0;
            lastProgressDistance = distance;
            updateAttentivePatience(distance);
            return;
        }

        attentiveIdleTicks = 0;
        lastAttentiveDistance = Double.NaN;

        if (--recalcTicks <= 0) {
            recalcTicks = adjustedTickDelay(PATH_RECALC_TICKS);
            double speed = urgency == PatchesFollowUrgency.HURRY ? HURRY_SPEED : CATCH_UP_SPEED;
            boolean pathStarted = patches.getNavigation().moveTo(player, speed);
            updateProgress(distance, pathStarted);

            if (distance >= PATH_FAILURE_WARP_DISTANCE && failedProgressChecks >= NO_PROGRESS_LIMIT) {
                catchUpCommitted = true;
                urgency = PatchesFollowUrgency.WARP;
                reportState("Unable to make pathing progress; using recovery warp.", distance, rate);
            }
        }
    }

    private void tickRecall(double distance, double rate) {
        urgency = PatchesFollowUrgency.HURRY;
        catchUpCommitted = true;
        patches.getLookControl().setLookAt(player, 10.0F, patches.getMaxHeadXRot());

        if (--recalcTicks > 0) return;
        recalcTicks = adjustedTickDelay(PATH_RECALC_TICKS);

        boolean pathStarted = patches.getNavigation().moveTo(player, HURRY_SPEED);
        updateProgress(distance, pathStarted);

        if (failedProgressChecks >= RECALL_NO_PROGRESS_LIMIT && distance > FOLLOW_RELEASE_DISTANCE) {
            if (tryWarpNearPlayer()) {
                failedProgressChecks = 0;
                lastProgressDistance = distanceToPlayer();
                reportState("Recall path was blocked; used a safe recovery warp.", distanceToPlayer(), rate);
            }
        }
    }

    private void updateAttentivePatience(double distance) {
        if (Double.isNaN(lastAttentiveDistance)) {
            lastAttentiveDistance = distance;
            attentiveIdleTicks = 0;
            return;
        }
        double change = distance - lastAttentiveDistance;
        lastAttentiveDistance = distance;
        if (change > ATTENTIVE_DISTANCE_CHANGE_EPSILON) {
            attentiveIdleTicks = 0;
            return;
        }
        attentiveIdleTicks++;
        if (attentiveIdleTicks >= ATTENTIVE_PATIENCE_TICKS) attentiveTimedOut = true;
    }

    private PatchesFollowUrgency determineUrgency(double distance, double rate) {
        if (urgency == PatchesFollowUrgency.WARP) return PatchesFollowUrgency.WARP;
        if (distance >= WARP_DISTANCE) {
            catchUpCommitted = true;
            return PatchesFollowUrgency.WARP;
        }
        if (catchUpCommitted) {
            if (distance >= HURRY_DISTANCE) return PatchesFollowUrgency.HURRY;
            if (urgency == PatchesFollowUrgency.HURRY && distance > HURRY_RELEASE_DISTANCE) return PatchesFollowUrgency.HURRY;
            return PatchesFollowUrgency.CATCH_UP;
        }
        if (urgency == PatchesFollowUrgency.ATTENTIVE) {
            if (distance >= CATCH_UP_DISTANCE) {
                catchUpCommitted = true;
                return PatchesFollowUrgency.CATCH_UP;
            }
            if (distance >= RELAXED_DISTANCE) return PatchesFollowUrgency.ATTENTIVE;
            return PatchesFollowUrgency.RELAXED;
        }
        PatchesFollowUrgency state = determineUncommittedUrgency(distance, rate);
        if (state == PatchesFollowUrgency.CATCH_UP || state == PatchesFollowUrgency.HURRY || state == PatchesFollowUrgency.WARP) catchUpCommitted = true;
        return state;
    }

    private PatchesFollowUrgency determineUncommittedUrgency(double distance, double rate) {
        if (distance >= WARP_DISTANCE) return PatchesFollowUrgency.WARP;
        if (distance >= HURRY_DISTANCE) return PatchesFollowUrgency.HURRY;
        if (distance >= CATCH_UP_DISTANCE) return PatchesFollowUrgency.CATCH_UP;
        if (distance >= RELAXED_DISTANCE && rate >= WALKING_AWAY_RATE) return PatchesFollowUrgency.ATTENTIVE;
        return PatchesFollowUrgency.RELAXED;
    }

    private double observePlayerDeparture(Player target) {
        Vec3 current = target.position();
        if (lastObservedPlayerPosition == null) {
            lastObservedPlayerPosition = current;
            lastObservedDepartureRate = 0;
            return 0;
        }
        Vec3 movement = current.subtract(lastObservedPlayerPosition);
        lastObservedPlayerPosition = current;
        Vec3 fromPatches = current.subtract(patches.position());
        double horizontal = Math.sqrt(fromPatches.x * fromPatches.x + fromPatches.z * fromPatches.z);
        if (horizontal < 1e-4) {
            lastObservedDepartureRate = 0;
            return 0;
        }
        lastObservedDepartureRate = movement.x * (fromPatches.x / horizontal) + movement.z * (fromPatches.z / horizontal);
        return lastObservedDepartureRate;
    }

    private void resetPlayerObservation() {
        lastObservedPlayerPosition = null;
        lastObservedDepartureRate = 0;
    }

    private void updateProgress(double distance, boolean pathStarted) {
        if (!pathStarted) {
            failedProgressChecks++;
            lastProgressDistance = distance;
            return;
        }
        if (Double.isNaN(lastProgressDistance)) {
            lastProgressDistance = distance;
            failedProgressChecks = 0;
            return;
        }
        if (lastProgressDistance - distance >= MIN_PROGRESS_PER_CHECK) failedProgressChecks = 0;
        else failedProgressChecks++;
        lastProgressDistance = distance;
    }

    private boolean tryWarpNearPlayer() {
        if (player == null) return false;
        BlockPos base = player.blockPosition();

        for (int attempt = 0; attempt < WARP_ATTEMPTS; attempt++) {
            int xOffset = patches.getRandom().nextIntBetweenInclusive(-3, 3);
            int zOffset = patches.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(xOffset) < 2 && Math.abs(zOffset) < 2) continue;

            int yOffset = patches.getRandom().nextIntBetweenInclusive(-1, 1);
            BlockPos landing = base.offset(xOffset, yOffset, zOffset);
            if (!canWarpTo(landing)) continue;

            patches.snapTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, patches.getYRot(), patches.getXRot());
            patches.getNavigation().stop();
            patches.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        return false;
    }

    private boolean canWarpTo(BlockPos landing) {
        if (WalkNodeEvaluator.getPathTypeStatic(patches, landing) != PathType.WALKABLE) return false;
        if (patches.level().getBlockState(landing.below()).getBlock() instanceof LeavesBlock) return false;

        BlockPos delta = landing.subtract(patches.blockPosition());
        return patches.level().noCollision(patches, patches.getBoundingBox().move(delta));
    }

    private double distanceToPlayer() {
        return player == null ? 0 : patches.distanceTo(player);
    }

    private String reasonFor(PatchesFollowUrgency state) {
        return switch (state) {
            case RELAXED -> "Player nearby; free to wander.";
            case ATTENTIVE -> "Player may be leaving; watching to see what they do.";
            case CATCH_UP -> "Committed to rejoining player.";
            case HURRY -> "Player is well ahead; hurrying to rejoin.";
            case WARP -> "Player too distant; using emergency warp.";
        };
    }

    private void reportState(String reason, double distance, double rate) {
        if (!DEBUG_FOLLOW_STATE || player == null) return;
        String message = String.format("[Patches] FOLLOW: %s — %s (distance %.1f, player departure %+.3f/tick)", urgencyDisplayName(urgency), reason, distance, rate);
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
