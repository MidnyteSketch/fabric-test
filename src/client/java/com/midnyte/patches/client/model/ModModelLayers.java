package com.midnyte.patches.client.model;

import com.midnyte.patches.PatchesMod;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

public final class ModModelLayers {
    private ModModelLayers() {}

    public static final ModelLayerLocation PATCHES = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, "patches"),
            "main"
    );

    public static final ModelLayerLocation PATCHES_FACE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, "patches"),
            "face"
    );

    public static final ModelLayerLocation PATCHES_GOGGLES_STRAP = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, "patches"),
            "goggles_strap"
    );

    public static final ModelLayerLocation PATCHES_GOGGLES = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, "patches"),
            "goggles"
    );

    public static void register() {
        ModelLayerRegistry.registerModelLayer(PATCHES, PatchesModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(PATCHES_FACE, PatchesFaceModel::createFaceLayer);
        ModelLayerRegistry.registerModelLayer(PATCHES_GOGGLES_STRAP, PatchesGogglesModel::createStrapLayer);
        ModelLayerRegistry.registerModelLayer(PATCHES_GOGGLES, PatchesGogglesModel::createGogglesLayer);
    }
}
