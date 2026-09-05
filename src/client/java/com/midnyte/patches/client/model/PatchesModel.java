package com.midnyte.patches.client.model;

import com.midnyte.patches.client.render.PatchesRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public final class PatchesModel extends EntityModel<PatchesRenderState> {
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
                CubeListBuilder.create().texOffs(1, 0).addBox(-4.75F, -8.75F, -4.0F, 9.5F, 9.5F, 8.0F),
                PartPose.offset(0.0F, 8.0F, 0.5F)
        );

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(16, 18).addBox(-3.75F, -10.0F, -1.5F, 7.5F, 10.0F, 4.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "front_left_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.25F, 0.0F, 0.0F, 4.25F, 6.0F, 3.25F),
                PartPose.offset(-2.0F, 18.0F, 2.25F)
        );
        root.addOrReplaceChild(
                "front_right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, 0.0F, 4.25F, 6.0F, 3.25F),
                PartPose.offset(2.0F, 18.0F, 2.25F)
        );
        root.addOrReplaceChild(
                "back_left_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.25F, 0.0F, -3.25F, 4.25F, 6.0F, 3.25F),
                PartPose.offset(-2.0F, 18.0F, -1.25F)
        );
        root.addOrReplaceChild(
                "back_right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -3.25F, 4.25F, 6.0F, 3.25F),
                PartPose.offset(2.0F, 18.0F, -1.25F)
        );

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(PatchesRenderState state) {
        super.setupAnim(state);

        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;

        float swing = state.walkAnimationPos * 0.6662F;
        float amount = Math.min(state.walkAnimationSpeed, 1.0F) * 1.25F;

        frontLeftLeg.xRot = Mth.cos(swing) * amount;
        backRightLeg.xRot = Mth.cos(swing) * amount;
        frontRightLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
        backLeftLeg.xRot = Mth.cos(swing + Mth.PI) * amount;

        if (state.mode == com.midnyte.patches.entity.PatchesMode.SITTING) {
            frontLeftLeg.xRot = 0.0F;
            frontRightLeg.xRot = 0.0F;
            backLeftLeg.xRot = 0.0F;
            backRightLeg.xRot = 0.0F;
        }
    }
}
