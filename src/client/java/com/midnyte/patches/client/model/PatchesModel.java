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
    /*
     * Patches' Blockbench model deliberately uses cuboids whose physical
     * dimensions differ slightly from the pixel dimensions of their UV maps.
     *
     * Minecraft's normal CubeListBuilder unwrap assumes those two dimensions
     * are identical. To preserve the original 64x32 Creeper-style texture,
     * each cuboid below is therefore built at its UV dimensions and the
     * ModelPart itself is non-uniformly scaled back to Patches' intended
     * physical proportions.
     */
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

        // Blockbench physical size: 9.5 x 9.5 x 8
        // Texture unwrap size:       8 x 8 x 8
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
                        0.0F, 8.0F, 0.5F,
                        0.0F, 0.0F, 0.0F,
                        HEAD_XY_SCALE, HEAD_XY_SCALE, 1.0F
                )
        );

        // Blockbench physical size: 7.5 x 10 x 4
        // Texture unwrap size:         7 x 10 x 3
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
                        0.0F, 18.0F, 0.0F,
                        0.0F, 0.0F, 0.0F,
                        BODY_X_SCALE, 1.0F, BODY_Z_SCALE
                )
        );

        // Each leg is physically 4.25 x 6 x 3.25, with a 4 x 6 x 3 UV unwrap.
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
                        -2.0F, 18.0F, 2.25F,
                        0.0F, 0.0F, 0.0F,
                        LEG_X_SCALE, 1.0F, LEG_Z_SCALE
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
                        2.0F, 18.0F, 2.25F,
                        0.0F, 0.0F, 0.0F,
                        LEG_X_SCALE, 1.0F, LEG_Z_SCALE
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
                        -2.0F, 18.0F, -1.25F,
                        0.0F, 0.0F, 0.0F,
                        LEG_X_SCALE, 1.0F, LEG_Z_SCALE
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
                        2.0F, 18.0F, -1.25F,
                        0.0F, 0.0F, 0.0F,
                        LEG_X_SCALE, 1.0F, LEG_Z_SCALE
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
        float amount = Math.min(state.walkAnimationSpeed, 1.0F) * 1.25F;

        frontLeftLeg.xRot = Mth.cos(swing) * amount;
        backRightLeg.xRot = Mth.cos(swing) * amount;
        frontRightLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
        backLeftLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
    }

    private void applySittingPose() {
        // Lower the torso while leaving the overall model grounded.
        head.y += 3.0F;
        body.y += 3.0F;

        // Fold all four legs forward into a compact Creeper-like seated pose.
        // This is intentionally a first-pass pose; the angles can be tuned
        // against an in-game screenshot without touching entity behaviour.
        frontLeftLeg.y += 3.25F;
        frontRightLeg.y += 3.25F;
        backLeftLeg.y += 3.0F;
        backRightLeg.y += 3.0F;

        frontLeftLeg.z -= 0.75F;
        frontRightLeg.z -= 0.75F;
        backLeftLeg.z += 1.25F;
        backRightLeg.z += 1.25F;

        frontLeftLeg.xRot = 1.10F;
        frontRightLeg.xRot = 1.10F;
        backLeftLeg.xRot = 1.35F;
        backRightLeg.xRot = 1.35F;
    }
}
