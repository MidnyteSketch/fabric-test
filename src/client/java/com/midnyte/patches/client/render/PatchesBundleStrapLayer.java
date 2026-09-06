package com.midnyte.patches.client.render;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.client.model.PatchesModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/**
 * Renders the Bundle strap as a conditional texture overlay.
 *
 * The overlay reuses Patches' already-animated parent model, but is drawn at
 * a very slightly larger scale so its pixels sit just above the base skin
 * instead of fighting for the same depth. This mirrors the approach vanilla
 * uses for close-fitting outer/pattern render layers.
 */
public final class PatchesBundleStrapLayer extends RenderLayer<PatchesRenderState, PatchesModel> {
    private static final Identifier STRAP_TEXTURE = Identifier.fromNamespaceAndPath(
            PatchesMod.MOD_ID,
            "textures/entity/bundle_strap_overlay.png"
    );

    private static final float OVERLAY_SCALE = 1.003F;

    public PatchesBundleStrapLayer(RenderLayerParent<PatchesRenderState, PatchesModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            PatchesRenderState state,
            float yRot,
            float xRot
    ) {
        if (state.bundle.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(OVERLAY_SCALE, OVERLAY_SCALE, OVERLAY_SCALE);

        coloredCutoutModelCopyLayerRender(
                this.getParentModel(),
                STRAP_TEXTURE,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state,
                -1,
                0
        );

        poseStack.popPose();
    }
}
