package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public final class PatchesFollowGoal extends Goal {
    private final PatchesEntity patches;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private Player player;
    private int recalcTicks;

    public PatchesFollowGoal(PatchesEntity patches, double speedModifier, float startDistance, float stopDistance) {
        this.patches = patches;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (patches.getMode() != PatchesMode.FOLLOWING) return false;

        Player candidate = patches.getFollowingPlayer();
        if (candidate == null || candidate.isSpectator() || !candidate.isAlive()) return false;
        if (patches.distanceToSqr(candidate) < startDistance * startDistance) return false;

        this.player = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return patches.getMode() == PatchesMode.FOLLOWING
                && player != null
                && player.isAlive()
                && !player.isSpectator()
                && patches.distanceToSqr(player) > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        recalcTicks = 0;
    }

    @Override
    public void stop() {
        player = null;
        patches.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (player == null) return;

        patches.getLookControl().setLookAt(player, 10.0F, patches.getMaxHeadXRot());

        if (--recalcTicks <= 0) {
            recalcTicks = adjustedTickDelay(10);
            patches.getNavigation().moveTo(player, speedModifier);
        }
    }
}
