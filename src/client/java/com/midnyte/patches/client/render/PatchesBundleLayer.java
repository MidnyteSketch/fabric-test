package com.midnyte.patches.client.render;

import com.midnyte.patches.client.model.PatchesModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders Patches' equipped vanilla Bundle on the left side of his torso.
 *
 * The Bundle remains a real ItemStack owned by the entity; this layer only
 * controls how that stack is presented on Patches' model.
 */
public final class PatchesBundleLayer extends RenderLayer<PatchesRenderState, PatchesModel> {

    public PatchesBundleLayer(RenderLayerParent<PatchesRenderState, PatchesModel> renderer) {
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

        /*
         * Position the vanilla Bundle item against Patches' left flank.
         * These are intentionally isolated here so the placement can be tuned
         * from screenshots without touching any entity behavior.
         */
        poseStack.translate(-0.33F, -0.68F, 0.02F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.58F, 0.58F, 0.58F);

        state.bundle.submit(
                poseStack,
                submitNodeCollector,
                lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
        );

        poseStack.popPose();
    }
}
