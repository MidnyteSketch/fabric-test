package com.midnyte.patches.client.render;

import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class PatchesRenderState extends LivingEntityRenderState {
    public PatchesMode mode = PatchesMode.WANDERING;
    public final ItemStackRenderState bundle = new ItemStackRenderState();
}
