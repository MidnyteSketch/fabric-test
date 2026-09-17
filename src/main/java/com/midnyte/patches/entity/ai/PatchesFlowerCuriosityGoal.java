package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesExpression;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** First vertical slice of Patches' ambient-curiosity system: flowers only. */
public final class PatchesFlowerCuriosityGoal extends Goal {
    private static final boolean DEBUG_CURIOSITY = true;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int GENERAL_COOLDOWN_TICKS = 20 * 20;
    private static final int INSPECT_TICKS = 40;
    private static final int SHARE_WAIT_TICKS = 20 * 8;
    private static final int SHARE_REACTION_TICKS = 60;
    private static final double SCAN_RADIUS = 7.0;
    private static final double APPROACH_DISTANCE = 1.75;
    private static final double SHARE_PLAYER_DISTANCE = 4.0;
    private static final double HURRY_INTERRUPT_DISTANCE = 16.0;
    private static final double APPROACH_SPEED = 0.85;

    private final PatchesEntity patches;
    private BlockPos target;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownTicks;
    private int scanTicks;

    public PatchesFlowerCuriosityGoal(PatchesEntity patches) {
        this.patches = patches;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (cooldownTicks > 0) { cooldownTicks--; return false; }
        if (patches.getMode() == PatchesMode.SITTING) return false;
        if (++scanTicks < SCAN_INTERVAL_TICKS) return false;
        scanTicks = 0;
        if (followDistanceIsUrgent()) return false;
        target = findNearbyFlower();
        return target != null;
    }

    @Override public boolean canContinueToUse() { return target != null && phase != Phase.IDLE && patches.getMode() != PatchesMode.SITTING; }

    @Override public void start() {
        phase = Phase.NOTICE;
        phaseTicks = 8;
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        report("NOTICE", "Spotted a new flower at " + target.toShortString() + ".");
    }

    @Override public void tick() {
        if (target == null) return;
        if (followDistanceIsUrgent()) { report("INTERRUPTED", "Player reached Hurry range; abandoning flower."); finish(false); return; }
        if (!patches.level().getBlockState(target).is(BlockTags.FLOWERS)) { report("INTERRUPTED", "Flower is gone."); finish(false); return; }

        Vec3 flowerCenter = Vec3.atCenterOf(target);
        switch (phase) {
            case NOTICE -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                patches.getLookControl().setLookAt(flowerCenter.x, flowerCenter.y, flowerCenter.z, 20.0F, patches.getMaxHeadXRot());
                if (--phaseTicks <= 0) enterApproach();
            }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED);
                patches.getLookControl().setLookAt(flowerCenter.x, flowerCenter.y, flowerCenter.z, 20.0F, patches.getMaxHeadXRot());
                double distance = Math.sqrt(patches.distanceToSqr(flowerCenter));
                if (distance <= APPROACH_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = INSPECT_TICKS;
                    report("INSPECT", "Reached flower; taking a closer look.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(flowerCenter.x, flowerCenter.y, flowerCenter.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT);
                patches.getLookControl().setLookAt(flowerCenter.x, flowerCenter.y + 0.15, flowerCenter.z, 20.0F, patches.getMaxHeadXRot());
                if (--phaseTicks <= 0) { phase = Phase.SHARE_WAIT; phaseTicks = SHARE_WAIT_TICKS; report("SHARE WAIT", "Finished inspecting; quietly waiting to see if player comes over."); }
            }
            case SHARE_WAIT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT);
                patches.getLookControl().setLookAt(flowerCenter.x, flowerCenter.y + 0.15, flowerCenter.z, 20.0F, patches.getMaxHeadXRot());
                Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
                    phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; patches.setActivityExpression(PatchesExpression.JOY);
                    report("SHARE REACTION", "Player came to see the flower; showing Joy.");
                } else if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; finished enjoying the flower."); finish(true); }
            }
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.JOY);
                Player player = relevantPlayer(); if (player != null) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot());
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the find; returning to normal behavior."); finish(true); }
            }
            case IDLE -> { }
        }
    }

    @Override public void stop() { if (phase != Phase.IDLE) finish(false); }
    private void enterApproach() { phase = Phase.APPROACH; report("APPROACH", "Going over to investigate the flower."); }
    private void finish(boolean remember) {
        if (remember && target != null) patches.rememberFlowerCuriosity(target);
        patches.getNavigation().stop(); patches.clearActivityExpression(); target = null; phase = Phase.IDLE; phaseTicks = 0; cooldownTicks = GENERAL_COOLDOWN_TICKS;
    }
    private boolean followDistanceIsUrgent() { if (patches.getMode() != PatchesMode.FOLLOWING) return false; Player player = patches.getFollowingPlayer(); return player != null && patches.distanceTo(player) >= HURRY_INTERRUPT_DISTANCE; }
    private Player relevantPlayer() { Player followed = patches.getFollowingPlayer(); if (followed != null) return followed; return patches.level().getNearestPlayer(patches, 12.0); }
    private BlockPos findNearbyFlower() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius,-2,-radius), origin.offset(radius,2,radius))) {
            if (patches.hasRememberedFlowerCuriosity(pos)) continue;
            if (!patches.level().getBlockState(pos).is(BlockTags.FLOWERS)) continue;
            double distance = pos.distSqr(origin); if (distance > SCAN_RADIUS*SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable(); bestDistance = distance;
        }
        return best;
    }
    private void report(String state,String detail) { if (!DEBUG_CURIOSITY) return; Player player = relevantPlayer(); if (player != null) player.sendSystemMessage(Component.literal("[Patches] CURIOSITY: "+state+" — "+detail)); }
    private enum Phase { IDLE, NOTICE, APPROACH, INSPECT, SHARE_WAIT, SHARE_REACTION }
}
