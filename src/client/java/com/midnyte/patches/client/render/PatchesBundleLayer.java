package com.midnyte.patches.client.render;

import com.midnyte.patches.client.model.PatchesModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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
         * Anchor to the body model part first. This makes the Bundle inherit
         * the body's animated position, including the vertical shift used by
         * the sitting pose, instead of floating in entity-local space.
         */
        this.getParentModel().translateToBody(poseStack);

        /*
         * The body's pivot is at its lower edge, so move upward to the middle
         * of the torso and outward onto Patches' left flank. These values are
         * intentionally isolated for visual tuning after an in-game screenshot.
         */
        poseStack.translate(-0.29F, -0.31F, 0.015F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.46F, 0.46F, 0.46F);

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
