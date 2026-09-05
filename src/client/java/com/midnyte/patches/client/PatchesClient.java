package com.midnyte.patches.client;

import com.midnyte.patches.client.model.ModModelLayers;
import com.midnyte.patches.client.render.PatchesRenderer;
import com.midnyte.patches.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class PatchesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModModelLayers.register();
        EntityRenderers.register(ModEntities.PATCHES, PatchesRenderer::new);
    }
}
