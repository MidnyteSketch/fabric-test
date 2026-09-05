package com.midnyte.patches.registry;

import com.midnyte.patches.PatchesMod;
import com.midnyte.patches.entity.PatchesEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<PatchesEntity> PATCHES = register(
            "patches",
            EntityType.Builder.<PatchesEntity>of(PatchesEntity::new, MobCategory.CREATURE)
                    .sized(0.56F, 1.50F)
                    .clientTrackingRange(10)
    );

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, name);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(PATCHES, PatchesEntity.createAttributes());
    }
}
