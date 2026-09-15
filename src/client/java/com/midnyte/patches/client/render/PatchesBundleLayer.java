package com.midnyte.patches.client.render;

import com.midnyte.patches.client.model.PatchesModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

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
         * Anchor to the body model part first so the Bundle follows any body
         * animation, including Patches' lowered sitting pose.
         */
        this.getParentModel().translateToBody(poseStack);

        /*
         * Patches' body uses non-uniform X/Z scaling to preserve his custom
         * Blockbench proportions while keeping the original texture unwrap.
         * That scale should affect the attachment point, but not distort the
         * vanilla Bundle item itself. Cancel it before rendering the item.
         */
        poseStack.scale(7.0F / 7.5F, 1.0F, 3.0F / 4.0F);

        /*
         * Sit the Bundle close against the left flank and slightly lower than
         * the previous pass. The bottom of the item should now finish just
         * above the first visible body pixel rather than hovering outward.
         *
         * Minecraft 26.3's PoseStack no longer accepts Quaternionf directly,
         * so compose the same Y-then-Z rotations into a Matrix4f.
         */
        poseStack.translate(-0.245F, -0.255F, 0.015F);
        poseStack.mulPose(
                new Matrix4f()
                        .rotateY((float) Math.toRadians(90.0F))
                        .rotateZ((float) Math.toRadians(180.0F))
        );
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
