package com.midnyte.patches.client.render;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.client.model.PatchesFaceModel;
import com.midnyte.patches.client.model.PatchesModel;
import com.midnyte.patches.entity.PatchesExpression;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Draws Patches' current face on a tiny head-local shell. */
public final class PatchesFaceLayer extends RenderLayer<PatchesRenderState, PatchesModel> {
    private final PatchesFaceModel faceModel;

    public PatchesFaceLayer(
            RenderLayerParent<PatchesRenderState, PatchesModel> renderer,
            PatchesFaceModel faceModel
    ) {
        super(renderer);
        this.faceModel = faceModel;
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
        Identifier texture = textureFor(state.expression);

        coloredCutoutModelCopyLayerRender(
                this.faceModel,
                texture,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state,
                -1,
                0
        );
    }

    private static Identifier textureFor(PatchesExpression expression) {
        String path = switch (expression) {
            case DEFAULT -> "textures/entity/face/default.png";
            case SURPRISED -> "textures/entity/face/surprised.png";
            case LAUGH -> "textures/entity/face/laugh.png";
            case MOUTH_OPEN -> "textures/entity/face/mouth_open.png";
            case HURT -> "textures/entity/face/hurt.png";
            case RESTING -> "textures/entity/face/resting.png";
        };

        return Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, path);
    }
}
