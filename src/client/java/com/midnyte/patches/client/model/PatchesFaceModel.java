package com.midnyte.patches.client.model;

import com.midnyte.patches.client.render.PatchesRenderState;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Head-only shell used for expression textures.
 *
 * The shell is inflated locally around the head cube instead of scaling the
 * entire entity in world/model space. This keeps the face a constant tiny
 * distance above Patches' head even while the head pitches up or down.
 */
public final class PatchesFaceModel extends EntityModel<PatchesRenderState> {
    private static final float HEAD_XY_SCALE = 9.5F / 8.0F;
    private static final float FACE_INFLATION = 0.008F;

    private final ModelPart head;

    public PatchesFaceModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }

    public static LayerDefinition createFaceLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(1, 0)
                        .addBox(
                                -4.0F,
                                -7.368421F,
                                -4.0F,
                                8.0F,
                                8.0F,
                                8.0F,
                                new CubeDeformation(FACE_INFLATION)
                        ),
                new PartPose(
                        0.0F,
                        8.0F,
                        0.5F,
                        0.0F,
                        0.0F,
                        0.0F,
                        HEAD_XY_SCALE,
                        HEAD_XY_SCALE,
                        1.0F
                )
        );

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(PatchesRenderState state) {
        super.setupAnim(state);

        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;

        if (state.mode == PatchesMode.SITTING) {
            head.y += 2.25F;
        }
    }
}
