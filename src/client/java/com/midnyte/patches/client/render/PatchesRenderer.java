package com.midnyte.patches.client.render;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.client.model.ModModelLayers;
import com.midnyte.patches.client.model.PatchesModel;
import com.midnyte.patches.entity.PatchesEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PatchesRenderer extends MobRenderer<PatchesEntity, PatchesRenderState, PatchesModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            PatchesMod.MOD_ID,
            "textures/entity/patches.png"
    );

    public PatchesRenderer(EntityRendererProvider.Context context) {
        super(context, new PatchesModel(context.bakeLayer(ModModelLayers.PATCHES)), 0.32F);
        this.addLayer(new PatchesFaceLayer(this));
        this.addLayer(new PatchesBundleStrapLayer(this));
        this.addLayer(new PatchesBundleLayer(this));
    }

    @Override
    public PatchesRenderState createRenderState() {
        return new PatchesRenderState();
    }

    @Override
    public void extractRenderState(PatchesEntity entity, PatchesRenderState state, float tickProgress) {
        super.extractRenderState(entity, state, tickProgress);
        state.mode = entity.getMode();
        state.expression = entity.getExpression();

        ItemStack bundle = entity.getBundleStack();
        if (bundle.isEmpty()) {
            state.bundle.clear();
        } else {
            this.itemModelResolver.updateForLiving(
                    state.bundle,
                    bundle,
                    ItemDisplayContext.FIXED,
                    entity
            );
        }
    }

    @Override
    public Identifier getTextureLocation(PatchesRenderState state) {
        return TEXTURE;
    }
}
