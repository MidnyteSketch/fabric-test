package com.midnyte.patches.mixin;

import com.midnyte.patches.entity.PatchesEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InstrumentItem.class)
public abstract class InstrumentItemMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void patches$recallFollowingPatches(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!player.getItemInHand(hand).is(Items.GOAT_HORN)) return;

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof PatchesEntity patches) patches.requestRecallFromHorn(player);
        }
    }
}
