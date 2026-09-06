package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Vanilla temptation behavior, except sitting Patches will not begin or
 * continue moving toward the tempting player.
 */
public final class PatchesTemptGoal extends TemptGoal {
    private final PatchesEntity patches;

    public PatchesTemptGoal(
            PatchesEntity patches,
            double speedModifier,
            Ingredient items,
            boolean canScare
    ) {
        super(patches, speedModifier, items, canScare);
        this.patches = patches;
    }

    @Override
    public boolean canUse() {
        return patches.getMode() != PatchesMode.SITTING && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return patches.getMode() != PatchesMode.SITTING && super.canContinueToUse();
    }
}
