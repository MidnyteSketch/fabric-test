package com.midnyte.patches.registry;

import com.midnyte.patches.PatchesMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public final class ModItems {
    private ModItems() {}

    private static final ResourceKey<Item> PATCHES_SPAWN_EGG_KEY = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(PatchesMod.MOD_ID, "patches_spawn_egg")
    );

    public static final Item PATCHES_SPAWN_EGG = register(
            PATCHES_SPAWN_EGG_KEY,
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntities.PATCHES)
    );

    private static Item register(
            ResourceKey<Item> key,
            Function<Item.Properties, Item> factory,
            Item.Properties properties
    ) {
        Item item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(PATCHES_SPAWN_EGG));
    }
}
