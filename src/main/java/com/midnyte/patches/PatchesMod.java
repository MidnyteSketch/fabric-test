package com.midnyte.patches;

import com.midnyte.patches.registry.ModEntities;
import com.midnyte.patches.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchesMod implements ModInitializer {
    public static final String MOD_ID = "patches";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.initialize();
        ModItems.initialize();
        LOGGER.info("Patches Test 1 initialized.");
    }
}
