package com.midnyte.patches;

import com.midnyte.patches.registry.ModEntities;
import com.midnyte.patches.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchesMod implements ModInitializer {
    public static final String MOD_ID = "patches";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.initialize();
        ModItems.initialize();

        // Temporary test helper for validating Patches' Wandering Trader caravan attention.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("patches_debug_trader")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var level = player.level();

                            WanderingTrader trader = EntityType.WANDERING_TRADER.create(level, EntitySpawnReason.COMMAND);
                            if (trader == null) return 0;
                            trader.moveTo(player.getX() + 3.0, player.getY(), player.getZ(), player.getYRot(), 0.0F);
                            trader.setDespawnDelay(48000);
                            level.addFreshEntity(trader);

                            for (int i = 0; i < 2; i++) {
                                TraderLlama llama = EntityType.TRADER_LLAMA.create(level, EntitySpawnReason.COMMAND);
                                if (llama == null) continue;
                                double side = i == 0 ? -1.5 : 1.5;
                                llama.moveTo(trader.getX() + side, trader.getY(), trader.getZ() + 1.5, trader.getYRot(), 0.0F);
                                llama.setDespawnDelay(48000);
                                level.addFreshEntity(llama);
                                llama.setLeashedTo(trader, true);
                            }

                            context.getSource().sendSuccess(() -> Component.literal("Spawned temporary Wandering Trader caravan for Patches testing."), false);
                            return 1;
                        })));

        LOGGER.info("Patches Test 1 initialized.");
    }
}
