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

        /*
         * HEAD
         *
         * Blockbench physical dimensions:
         * 9.5 x 9.5 x 8
         *
         * Texture unwrap dimensions:
         * 8 x 8 x 8
         */
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

        /*
         * BODY
         *
         * Blockbench physical dimensions:
         * 7.5 x 10 x 4
         *
         * Texture unwrap dimensions:
         * 7 x 10 x 3
         */
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

        /*
         * FRONT LEFT LEG
         *
         * Physical dimensions:
         * 4.25 x 6 x 3.25
         *
         * Texture unwrap:
         * 4 x 6 x 3
         */
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

        /*
         * FRONT RIGHT LEG
         */
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

        /*
         * BACK LEFT LEG
         */
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

        /*
         * BACK RIGHT LEG
         */
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

        /*
         * Head tracking remains active while standing, walking,
         * following, and sitting.
         */
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;

        /*
         * Sitting has its own pose and does not use the walking
         * animation.
         */
        if (state.mode == PatchesMode.SITTING) {
            applySittingPose();
            return;
        }

        /*
         * WALKING
         *
         * Patches' legs are substantially larger than a vanilla
         * Creeper's relative to his body. The earlier 1.25-radian
         * amplitude made the rear legs swing so far backward that
         * they could resemble a tail.
         *
         * Keep the alternating Creeper gait, but substantially
         * reduce its range.
         */
        float swing = state.walkAnimationPos * 0.6662F;
        float amount =
                Math.min(state.walkAnimationSpeed, 1.0F) * 0.55F;

        frontLeftLeg.xRot =
                Mth.cos(swing) * amount;

        backRightLeg.xRot =
                Mth.cos(swing) * amount;

        frontRightLeg.xRot =
                Mth.cos(swing + Mth.PI) * amount;

        backLeftLeg.xRot =
                Mth.cos(swing + Mth.PI) * amount;
    }

    private void applySittingPose() {

        /*
         * Lower Patches' upright body toward his seated legs.
         *
         * The head moves with the body so the neck connection
         * remains intact.
         */
        head.y += 3.25F;
        body.y += 3.25F;

        /*
         * FRONT LEGS
         *
         * These become the two large feet extending in front of
         * Patches, similar to his illustrated sitting pose.
         */
        frontLeftLeg.y += 4.65F;
        frontRightLeg.y += 4.65F;

        frontLeftLeg.z -= 0.35F;
        frontRightLeg.z -= 0.35F;

        frontLeftLeg.xRot = 1.42F;
        frontRightLeg.xRot = 1.42F;

        /*
         * REAR LEGS
         *
         * Keep these much closer to the body. They should read as
         * tucked rear legs rather than another pair of enormous
         * forward feet.
         */
        backLeftLeg.y += 4.35F;
        backRightLeg.y += 4.35F;

        backLeftLeg.z += 0.65F;
        backRightLeg.z += 0.65F;

        backLeftLeg.xRot = 0.48F;
        backRightLeg.xRot = 0.48F;
    }
}
