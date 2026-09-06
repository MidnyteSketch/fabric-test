package com.midnyte.patches.client.render;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.client.model.PatchesGogglesModel;
import com.midnyte.patches.client.model.PatchesModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Renders the fixed strap and lowered Spyglass goggles while a Spyglass is equipped. */
public final class PatchesGogglesLayer extends RenderLayer<PatchesRenderState, PatchesModel> {
    private static final Identifier STRAP_TEXTURE = Identifier.fromNamespaceAndPath(
            PatchesMod.MOD_ID,
            "textures/entity/spyglass_strap_overlay.png"
    );

    private static final Identifier GOGGLES_TEXTURE = Identifier.fromNamespaceAndPath(
            PatchesMod.MOD_ID,
            "textures/entity/spyglass_goggles_overlay.png"
    );

    private final PatchesGogglesModel strapModel;
    private final PatchesGogglesModel gogglesModel;

    public PatchesGogglesLayer(
            RenderLayerParent<PatchesRenderState, PatchesModel> renderer,
            PatchesGogglesModel strapModel,
            PatchesGogglesModel gogglesModel
    ) {
        super(renderer);
        this.strapModel = strapModel;
        this.gogglesModel = gogglesModel;
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
        if (!state.hasSpyglass) {
            return;
        }

        coloredCutoutModelCopyLayerRender(
                this.strapModel,
                STRAP_TEXTURE,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state,
                -1,
                0
        );

        coloredCutoutModelCopyLayerRender(
                this.gogglesModel,
                GOGGLES_TEXTURE,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state,
                -1,
                0
        );
    }
}
