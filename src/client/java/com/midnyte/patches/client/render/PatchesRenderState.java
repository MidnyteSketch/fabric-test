package com.midnyte.patches.client.render;

import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class PatchesRenderState extends LivingEntityRenderState {
    public PatchesMode mode = PatchesMode.WANDERING;
}
