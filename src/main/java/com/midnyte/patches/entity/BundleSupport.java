package com.midnyte.patches.entity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

final class BundleSupport {
    private BundleSupport() {}

    static boolean isBundle(ItemStack stack) {
        return stack.is(ItemTags.BUNDLES);
    }
}
