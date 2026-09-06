package com.midnyte.patches.client.render;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.client.model.PatchesModel;
import com.midnyte.patches.entity.PatchesExpression;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Draws Patches' current non-default face just above the base head texture. */
public final class PatchesFaceLayer extends RenderLayer<PatchesRenderState, PatchesModel> {
    private static final float FACE_SCALE = 1.001F;

    public PatchesFaceLayer(RenderLayerParent<PatchesRenderState, PatchesModel> renderer) {
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
        Identifier texture = textureFor(state.expression);
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(FACE_SCALE, FACE_SCALE, FACE_SCALE);

        coloredCutoutModelCopyLayerRender(
                this.getParentModel(),
                texture,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state,
                -1,
                0
        );

        poseStack.popPose();
    }

    private static Identifier textureFor(PatchesExpression expression) {
        String path = switch (expression) {
            case SURPRISED -> "textures/entity/face/surprised.png";
            case LAUGH -> "textures/entity/face/laugh.png";
            case MOUTH_OPEN -> "textures/entity/face/mouth_open.png";
            case HURT -> "textures/entity/face/hurt.png";
            case RESTING -> "textures/entity/face/resting.png";
            case DEFAULT -> null;
        };

        return path == null ? null : Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, path);
    }
}
