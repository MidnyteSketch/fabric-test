package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesExpression;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

/** Shared ambient-curiosity controller. Target-specific behavior lives here instead of competing goals. */
public final class PatchesCuriosityGoal extends Goal {
    private static final boolean DEBUG_CURIOSITY = true;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int GENERAL_COOLDOWN_TICKS = 20 * 20;
    private static final int FLOWER_INSPECT_TICKS = 40;
    private static final int FLOWER_SHARE_WAIT_TICKS = 20 * 8;
    private static final int AXOLOTL_INSPECT_TICKS = 50;
    private static final int AXOLOTL_INVITE_TICKS = 20 * 8;
    private static final int SHARE_REACTION_TICKS = 60;
    private static final double SCAN_RADIUS = 7.0;
    private static final double FLOWER_APPROACH_DISTANCE = 1.75;
    private static final double AXOLOTL_COMFORT_DISTANCE = 3.0;
    private static final double AXOLOTL_REPOSITION_DISTANCE = 4.0;
    private static final double AXOLOTL_ABANDON_DISTANCE = 9.0;
    private static final double SHARE_PLAYER_DISTANCE = 4.0;
    private static final double HURRY_INTERRUPT_DISTANCE = 16.0;
    private static final double APPROACH_SPEED = 0.85;

    private final PatchesEntity patches;
    private TargetKind targetKind;
    private PatchesCuriosityPriority targetPriority;
    private BlockPos flowerTarget;
    private Axolotl axolotlTarget;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownTicks;
    private int scanTicks;

    public PatchesCuriosityGoal(PatchesEntity patches) {
        this.patches = patches;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }
        if (patches.getMode() == PatchesMode.SITTING) return false;
        if (++scanTicks < SCAN_INTERVAL_TICKS) return false;
        scanTicks = 0;
        if (followDistanceIsUrgent()) return false;
        return chooseBestNearbyTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return targetKind != null && phase != Phase.IDLE && patches.getMode() != PatchesMode.SITTING;
    }

    @Override
    public void start() {
        beginNotice();
    }

    @Override
    public void tick() {
        if (targetKind == null) return;
        if (followDistanceIsUrgent()) {
            report("INTERRUPTED", "Player reached Hurry range; abandoning " + targetName() + ".");
            finish(false);
            return;
        }
        if (!targetStillValid()) {
            report("INTERRUPTED", targetName() + " is no longer available.");
            finish(false);
            return;
        }

        // A medium curiosity can supersede a low one before Patches has settled into inspecting it.
        if (targetPriority == PatchesCuriosityPriority.LOW && (phase == Phase.NOTICE || phase == Phase.APPROACH) && patches.tickCount % SCAN_INTERVAL_TICKS == 0) {
            Axolotl better = findNearbyAxolotl();
            if (better != null) {
                report("PRIORITY", "An Axolotl is more interesting than the flower; switching targets.");
                selectAxolotl(better);
                beginNotice();
                return;
            }
        }

        if (targetKind == TargetKind.FLOWER) tickFlower();
        else tickAxolotl();
    }

    private void tickFlower() {
        Vec3 center = Vec3.atCenterOf(flowerTarget);
        switch (phase) {
            case NOTICE -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                lookAt(center);
                if (--phaseTicks <= 0) enterApproach();
            }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                lookAt(center);
                if (Math.sqrt(patches.distanceToSqr(center)) <= FLOWER_APPROACH_DISTANCE) {
                    patches.getNavigation().stop();
                    phase = Phase.INSPECT;
                    phaseTicks = FLOWER_INSPECT_TICKS;
                    report("INSPECT", "Reached flower; taking a closer look.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) {
                    patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
                }
            }
            case INSPECT -> {
                patches.getNavigation().stop();
                patches.setActivityExpression(PatchesExpression.DEFAULT);
                lookAt(center.add(0.0, 0.15, 0.0));
                if (--phaseTicks <= 0) {
                    phase = Phase.SHARE_WAIT;
                    phaseTicks = FLOWER_SHARE_WAIT_TICKS;
                    report("SHARE WAIT", "Finished inspecting; quietly waiting to see if player comes over.");
                }
            }
            case SHARE_WAIT -> {
                patches.getNavigation().stop();
                patches.setActivityExpression(PatchesExpression.DEFAULT);
                lookAt(center.add(0.0, 0.15, 0.0));
                Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
                    phase = Phase.SHARE_REACTION;
                    phaseTicks = SHARE_REACTION_TICKS;
                    report("SHARE REACTION", "Player came to see the flower; showing Joy.");
                } else if (--phaseTicks <= 0) {
                    report("COMPLETE", "Player did not join; finished enjoying the flower.");
                    finish(true);
                }
            }
            case SHARE_REACTION -> {
                patches.getNavigation().stop();
                patches.setActivityExpression(PatchesExpression.JOY);
                Player player = relevantPlayer();
                if (player != null) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot());
                if (--phaseTicks <= 0) {
                    report("COMPLETE", "Finished sharing the flower; returning to normal behavior.");
                    finish(true);
                }
            }
            default -> { }
        }
    }

    private void tickAxolotl() {
        double distance = patches.distanceTo(axolotlTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) {
            report("INTERRUPTED", "Axolotl moved too far away to keep following.");
            finish(false);
            return;
        }

        switch (phase) {
            case NOTICE -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                lookAtAxolotl();
                if (--phaseTicks <= 0) enterApproach();
            }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                lookAtAxolotl();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) {
                    patches.getNavigation().stop();
                    phase = Phase.INSPECT;
                    phaseTicks = AXOLOTL_INSPECT_TICKS;
                    report("INSPECT", "Reached Axolotl; watching it for a while.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) {
                    patches.getNavigation().moveTo(axolotlTarget, APPROACH_SPEED);
                }
            }
            case INSPECT -> {
                maintainAxolotlDistance();
                patches.setActivityExpression(PatchesExpression.DEFAULT);
                lookAtAxolotl();
                if (--phaseTicks <= 0) {
                    phase = Phase.PLAYER_INVITE;
                    phaseTicks = AXOLOTL_INVITE_TICKS;
                    report("PLAYER INVITE", "Looking to player to show off the Axolotl.");
                }
            }
            case PLAYER_INVITE -> {
                maintainAxolotlDistance();
                patches.setActivityExpression(PatchesExpression.DEFAULT);
                Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
                    phase = Phase.SHARE_REACTION;
                    phaseTicks = SHARE_REACTION_TICKS;
                    report("SHARE REACTION", "Player came over; contentedly sharing the Axolotl.");
                    return;
                }
                // Alternate attention between the player and Axolotl: "Are you seeing this?"
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot());
                else lookAtAxolotl();
                if (--phaseTicks <= 0) {
                    report("COMPLETE", "Player did not join; finished watching the Axolotl.");
                    finish(true);
                }
            }
            case SHARE_REACTION -> {
                maintainAxolotlDistance();
                patches.setActivityExpression(PatchesExpression.CONTENT);
                Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot());
                else lookAtAxolotl();
                if (--phaseTicks <= 0) {
                    report("COMPLETE", "Finished sharing the Axolotl; returning to normal behavior.");
                    finish(true);
                }
            }
            default -> { }
        }
    }

    @Override
    public void stop() {
        if (phase != Phase.IDLE) finish(false);
    }

    private boolean chooseBestNearbyTarget() {
        Axolotl axolotl = findNearbyAxolotl();
        if (axolotl != null) {
            selectAxolotl(axolotl);
            return true;
        }
        BlockPos flower = findNearbyFlower();
        if (flower != null) {
            selectFlower(flower);
            return true;
        }
        return false;
    }

    private void selectFlower(BlockPos flower) {
        targetKind = TargetKind.FLOWER;
        targetPriority = PatchesCuriosityPriority.LOW;
        flowerTarget = flower.immutable();
        axolotlTarget = null;
    }

    private void selectAxolotl(Axolotl axolotl) {
        targetKind = TargetKind.AXOLOTL;
        targetPriority = PatchesCuriosityPriority.MEDIUM;
        axolotlTarget = axolotl;
        flowerTarget = null;
    }

    private void beginNotice() {
        phase = Phase.NOTICE;
        phaseTicks = 8;
        patches.getNavigation().stop();
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        report("NOTICE", "Spotted " + targetName() + " (" + targetPriority + ").");
    }

    private void enterApproach() {
        phase = Phase.APPROACH;
        report("APPROACH", "Going over to investigate " + targetName() + ".");
    }

    private void finish(boolean remember) {
        if (remember) {
            if (targetKind == TargetKind.FLOWER && flowerTarget != null) patches.rememberFlowerCuriosity(flowerTarget);
            if (targetKind == TargetKind.AXOLOTL && axolotlTarget != null) patches.rememberAxolotlCuriosity(axolotlTarget.getUUID());
        }
        patches.getNavigation().stop();
        patches.clearActivityExpression();
        targetKind = null;
        targetPriority = null;
        flowerTarget = null;
        axolotlTarget = null;
        phase = Phase.IDLE;
        phaseTicks = 0;
        cooldownTicks = GENERAL_COOLDOWN_TICKS;
    }

    private boolean targetStillValid() {
        if (targetKind == TargetKind.FLOWER) return flowerTarget != null && patches.level().getBlockState(flowerTarget).is(BlockTags.FLOWERS);
        return axolotlTarget != null && axolotlTarget.isAlive() && !axolotlTarget.isRemoved() && axolotlTarget.level() == patches.level();
    }

    private void maintainAxolotlDistance() {
        double distance = patches.distanceTo(axolotlTarget);
        if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(axolotlTarget, APPROACH_SPEED);
        else patches.getNavigation().stop();
    }

    private Axolotl findNearbyAxolotl() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Axolotl.class, search, axolotl -> axolotl.isAlive() && !patches.hasRememberedAxolotlCuriosity(axolotl.getUUID()))
            .stream()
            .min(Comparator.comparingDouble(patches::distanceToSqr))
            .orElse(null);
    }

    private BlockPos findNearbyFlower() {
        BlockPos origin = patches.blockPosition();
        int radius = (int)Math.ceil(SCAN_RADIUS);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (patches.hasRememberedFlowerCuriosity(pos)) continue;
            if (!patches.level().getBlockState(pos).is(BlockTags.FLOWERS)) continue;
            double distance = pos.distSqr(origin);
            if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    private boolean followDistanceIsUrgent() {
        if (patches.getMode() != PatchesMode.FOLLOWING) return false;
        Player player = patches.getFollowingPlayer();
        return player != null && patches.distanceTo(player) >= HURRY_INTERRUPT_DISTANCE;
    }

    private Player relevantPlayer() {
        Player followed = patches.getFollowingPlayer();
        if (followed != null) return followed;
        return patches.level().getNearestPlayer(patches, 12.0);
    }

    private void lookAt(Vec3 target) {
        patches.getLookControl().setLookAt(target.x, target.y, target.z, 20.0F, patches.getMaxHeadXRot());
    }

    private void lookAtAxolotl() {
        patches.getLookControl().setLookAt(axolotlTarget, 20.0F, patches.getMaxHeadXRot());
    }

    private String targetName() {
        return targetKind == TargetKind.AXOLOTL ? "an Axolotl" : "a flower";
    }

    private void report(String state, String detail) {
        if (!DEBUG_CURIOSITY) return;
        Player player = relevantPlayer();
        if (player != null) player.sendSystemMessage(Component.literal("[Patches] CURIOSITY: " + state + " — " + detail));
    }

    private enum TargetKind { FLOWER, AXOLOTL }
    private enum Phase { IDLE, NOTICE, APPROACH, INSPECT, SHARE_WAIT, PLAYER_INVITE, SHARE_REACTION }
}
