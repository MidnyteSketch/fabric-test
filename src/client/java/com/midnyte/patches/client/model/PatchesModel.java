package com.midnyte.patches.client.model;

import com.midnyte.patches.client.render.PatchesRenderState;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public final class PatchesModel extends EntityModel<PatchesRenderState> {

    private static final float HEAD_XY_SCALE = 9.5F / 8.0F;

    private static final float BODY_X_SCALE = 7.5F / 7.0F;
    private static final float BODY_Z_SCALE = 4.0F / 3.0F;

    private static final float LEG_X_SCALE = 4.25F / 4.0F;
    private static final float LEG_Z_SCALE = 3.25F / 3.0F;

    private final ModelPart head;
    private final ModelPart body;

    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;

    public PatchesModel(ModelPart root) {
        super(root);

        this.head = root.getChild("head");
        this.body = root.getChild("body");

        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.backLeftLeg = root.getChild("back_left_leg");
        this.backRightLeg = root.getChild("back_right_leg");
    }

    public static LayerDefinition createBodyLayer() {
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
                                8.0F
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

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(16, 18)
                        .addBox(
                                -3.5F,
                                -10.0F,
                                -1.125F,
                                7.0F,
                                10.0F,
                                3.0F
                        ),
                new PartPose(
                        0.0F,
                        18.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        BODY_X_SCALE,
                        1.0F,
                        BODY_Z_SCALE
                )
        );

        root.addOrReplaceChild(
                "front_left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.117647F,
                                0.0F,
                                0.0F,
                                4.0F,
                                6.0F,
                                3.0F
                        ),
                new PartPose(
                        -2.0F,
                        18.0F,
                        2.25F,
                        0.0F,
                        0.0F,
                        0.0F,
                        LEG_X_SCALE,
                        1.0F,
                        LEG_Z_SCALE
                )
        );

        root.addOrReplaceChild(
                "front_right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -1.882353F,
                                0.0F,
                                0.0F,
                                4.0F,
                                6.0F,
                                3.0F
                        ),
                new PartPose(
                        2.0F,
                        18.0F,
                        2.25F,
                        0.0F,
                        0.0F,
                        0.0F,
                        LEG_X_SCALE,
                        1.0F,
                        LEG_Z_SCALE
                )
        );

        root.addOrReplaceChild(
                "back_left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.117647F,
                                0.0F,
                                -3.0F,
                                4.0F,
                                6.0F,
                                3.0F
                        ),
                new PartPose(
                        -2.0F,
                        18.0F,
                        -1.25F,
                        0.0F,
                        0.0F,
                        0.0F,
                        LEG_X_SCALE,
                        1.0F,
                        LEG_Z_SCALE
                )
        );

        root.addOrReplaceChild(
                "back_right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -1.882353F,
                                0.0F,
                                -3.0F,
                                4.0F,
                                6.0F,
                                3.0F
                        ),
                new PartPose(
                        2.0F,
                        18.0F,
                        -1.25F,
                        0.0F,
                        0.0F,
                        0.0F,
                        LEG_X_SCALE,
                        1.0F,
                        LEG_Z_SCALE
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
            applySittingPose();
            return;
        }

        float swing = state.walkAnimationPos * 0.6662F;
        float amount = Math.min(state.walkAnimationSpeed, 1.0F) * 0.55F;

        frontLeftLeg.xRot = Mth.cos(swing) * amount;
        backRightLeg.xRot = Mth.cos(swing) * amount;
        frontRightLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
        backLeftLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
    }

    private void applySittingPose() {
        // The lowered body/head placement from the previous pass was correct.
        head.y += 3.25F;
        body.y += 3.25F;

        /*
         * Put the front legs flat on the ground as the two large forward feet.
         * A 90-degree X rotation turns the six-pixel-tall leg into a six-pixel-long
         * foot. Moving the pivot to ground level prevents the diagonal/hanging
         * appearance produced by the previous 81-degree rotation.
         */
        frontLeftLeg.y += 6.0F;
        frontRightLeg.y += 6.0F;
        frontLeftLeg.z -= 2.25F;
        frontRightLeg.z -= 2.25F;
        frontLeftLeg.xRot = Mth.HALF_PI;
        frontRightLeg.xRot = Mth.HALF_PI;

        /*
         * Fold the rear legs backward and keep them close to the body. Their
         * ground-level pivots make them read as tucked haunches rather than
         * diagonal stilts. They sit slightly inboard in Z so the front feet
         * remain the dominant shape from the front/three-quarter view.
         */
        backLeftLeg.y += 6.0F;
        backRightLeg.y += 6.0F;
        backLeftLeg.z += 1.25F;
        backRightLeg.z += 1.25F;
        backLeftLeg.xRot = -Mth.HALF_PI;
        backRightLeg.xRot = -Mth.HALF_PI;
    }
}
