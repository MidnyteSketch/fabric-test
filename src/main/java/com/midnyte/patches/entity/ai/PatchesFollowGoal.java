package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class PatchesFollowGoal extends Goal {
    private static final boolean DEBUG_FOLLOW_STATE = true;

    private static final double RELAXED_DISTANCE = 6.0;
    private static final double ATTENTIVE_DISTANCE = 9.0;
    private static final double CATCH_UP_DISTANCE = 12.0;
    private static final double HURRY_DISTANCE = 16.0;
    private static final double WARP_DISTANCE = 24.0;

    // Once Patches commits to catching up, come properly back to the player
    // before releasing the follow goal. This is deliberately much closer than
    // the distance at which he first becomes concerned.
    private static final double FOLLOW_RELEASE_DISTANCE = 3.0;
    private static final double HURRY_RELEASE_DISTANCE = 9.0;

    private static final double WALKING_AWAY_RATE = 0.035;
    private static final double SPRINTING_AWAY_RATE = 0.09;

    private static final double CATCH_UP_SPEED = 1.15;
    private static final double HURRY_SPEED = 1.35;

    private static final int PATH_RECALC_TICKS = 10;
    private static final int NO_PROGRESS_LIMIT = 4;
    private static final double MIN_PROGRESS_PER_CHECK = 0.20;

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

        // Do not occupy MOVE/LOOK while Follow is relaxed. This is important:
        // RandomStrollGoal and the ordinary look goals are then free to give
        // Patches his normal idle wandering around the nearby player.
        if (distance < RELAXED_DISTANCE && separationRate < WALKING_AWAY_RATE) {
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

        // Hysteresis: after Follow actually engages, stay committed until
        // Patches has returned to a comfortable close distance.
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
            // Stay in WARP until a teleport actually succeeds. Previously a
            // failed attempt could immediately fall back to HURRY and produce
            // noisy Hurry/Warp oscillation while the player kept moving.
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

        // If hysteresis is keeping the goal alive below the normal trigger,
        // continue the committed catch-up rather than becoming inert at six
        // blocks. The goal will release at FOLLOW_RELEASE_DISTANCE.
        if (urgency == PatchesFollowUrgency.RELAXED) {
            urgency = PatchesFollowUrgency.CATCH_UP;
        }

        if (--recalcTicks <= 0) {
            recalcTicks = adjustedTickDelay(PATH_RECALC_TICKS);

            double speed = urgency == PatchesFollowUrgency.HURRY ? HURRY_SPEED : CATCH_UP_SPEED;
            boolean pathStarted = patches.getNavigation().moveTo(player, speed);

            if (!pathStarted) {
                failedProgressChecks++;
            } else {
                updateProgress(distance);
            }

            if (distance >= HURRY_DISTANCE && failedProgressChecks >= NO_PROGRESS_LIMIT) {
                urgency = PatchesFollowUrgency.WARP;
                reportState("Path recovery failed; using emergency warp.", distance, separationRate);
            }
        }
    }

    private PatchesFollowUrgency determineUrgency(double distance, double separationRate) {
        // Warp is sticky while the goal is active. It should only end when the
        // teleport succeeds or the Follow goal itself ends.
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
            if (distance >= HURRY_DISTANCE || separationRate >= SPRINTING_AWAY_RATE) {
                return PatchesFollowUrgency.HURRY;
            }
            return PatchesFollowUrgency.CATCH_UP;
        }

        if (distance >= HURRY_DISTANCE || (distance >= ATTENTIVE_DISTANCE && separationRate >= SPRINTING_AWAY_RATE)) {
            return PatchesFollowUrgency.HURRY;
        }

        if (distance >= CATCH_UP_DISTANCE || (distance >= RELAXED_DISTANCE && separationRate >= WALKING_AWAY_RATE)) {
            return PatchesFollowUrgency.CATCH_UP;
        }

        if (distance >= ATTENTIVE_DISTANCE || (distance >= RELAXED_DISTANCE && separationRate > 0.0)) {
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
        } else {
            failedProgressChecks++;
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
        int[] verticalOffsets = {0, 1, -1, 2, -2};

        // randomTeleport performs Minecraft's own collision/landing checks.
        // Try a small vertical range as well as horizontal offsets because the
        // player may have arrived on stairs, slopes, ledges, or after a large
        // vertical displacement.
        for (int yOffset : verticalOffsets) {
            for (int[] offset : horizontalOffsets) {
                double x = baseX + offset[0] + 0.5;
                double y = baseY + yOffset;
                double z = baseZ + offset[1] + 0.5;

                if (patches.randomTeleport(x, y, z, true, state -> true)) {
                    patches.getNavigation().stop();
                    return true;
                }
            }
        }

        // The checked attempts above can all fail in cramped or unusual
        // terrain. For Follow recovery, prefer rejoining Patches to leaving him
        // permanently stranded: use a conservative one-block offset as the
        // final fallback. The next normal movement tick can resolve the exact
        // standing position.
        patches.teleportTo(player.getX() + 1.0, player.getY(), player.getZ());
        patches.getNavigation().stop();
        return patches.distanceTo(player) < 4.0F;
    }

    private double distanceToPlayer() {
        return player == null ? 0.0 : patches.distanceTo(player);
    }

    private String reasonFor(PatchesFollowUrgency state, double separationRate) {
        return switch (state) {
            case RELAXED -> separationRate < 0.0
                    ? "Player approaching; no need to chase."
                    : "Player nearby; free to wander.";
            case ATTENTIVE -> "Player drifting away; staying aware.";
            case CATCH_UP -> "Player moving away; catching up.";
            case HURRY -> "Player rapidly getting farther away; hurrying.";
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
