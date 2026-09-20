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

                            var traderPos = player.blockPosition().offset(3, 0, 0);
                            WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(level, traderPos, EntitySpawnReason.COMMAND);
                            if (trader == null) return 0;
                            trader.setDespawnDelay(48000);

                            for (int i = 0; i < 2; i++) {
                                var llamaPos = traderPos.offset(i == 0 ? -2 : 2, 0, 2);
                                TraderLlama llama = EntityType.TRADER_LLAMA.spawn(level, llamaPos, EntitySpawnReason.COMMAND);
                                if (llama == null) continue;
                                llama.setDespawnDelay(48000);
                                llama.setLeashedTo(trader, true);
                            }

                            context.getSource().sendSuccess(() -> Component.literal("Spawned temporary Wandering Trader caravan for Patches testing."), false);
                            return 1;
                        })));

        LOGGER.info("Patches Test 1 initialized.");
    }
}
