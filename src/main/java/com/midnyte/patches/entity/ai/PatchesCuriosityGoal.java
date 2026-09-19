package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesExpression;
import com.midnyte.patches.entity.PatchesMode;
import com.midnyte.patches.mixin.BrushableBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

/** Shared ambient-curiosity controller. Individual interests select a priority and interaction style. */
public final class PatchesCuriosityGoal extends Goal {
    private static final boolean DEBUG_CURIOSITY = true;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int GENERAL_COOLDOWN_TICKS = 20 * 20;
    private static final int FLOWER_INSPECT_TICKS = 40;
    private static final int FLOWER_SHARE_WAIT_TICKS = 20 * 8;
    private static final int AXOLOTL_INSPECT_TICKS = 50;
    private static final int AXOLOTL_INVITE_TICKS = 20 * 8;
    private static final int VALUABLE_INSPECT_TICKS = 50;
    private static final int VALUABLE_BECKON_TICKS = 20 * 14;
    private static final int SHARE_REACTION_TICKS = 60;
    private static final double SCAN_RADIUS = 7.0;
    private static final double FLOWER_APPROACH_DISTANCE = 1.75;
    private static final double LOW_BLOCK_HORIZONTAL_APPROACH_DISTANCE = 1.75;
    private static final double LOW_BLOCK_MAX_VERTICAL_LOOK_DISTANCE = 4.0;
    private static final double VALUABLE_APPROACH_DISTANCE = 2.0;
    private static final double AXOLOTL_COMFORT_DISTANCE = 3.0;
    private static final double AXOLOTL_REPOSITION_DISTANCE = 4.0;
    private static final double AXOLOTL_ABANDON_DISTANCE = 9.0;
    private static final double SHARE_PLAYER_DISTANCE = 4.0;
    private static final double HURRY_INTERRUPT_DISTANCE = 16.0;
    private static final double APPROACH_SPEED = 0.85;

    private final PatchesEntity patches;
    private TargetKind targetKind;
    private PatchesCuriosityPriority targetPriority;
    private BlockPos blockTarget;
    private BlockPos blockMemoryTarget;
    private Axolotl axolotlTarget;
    private WanderingTrader wanderingTraderTarget;
    private Sniffer snifferTarget;
    private Entity lootVehicleTarget;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownTicks;
    private int scanTicks;
    private int beckonCycleTicks;
    private int beckonHops;

    public void resetCooldownForDebug() { cooldownTicks = 0; scanTicks = SCAN_INTERVAL_TICKS; }
    public void interruptForRecall() { if (phase != Phase.IDLE) finish(false); }

    public PatchesCuriosityGoal(PatchesEntity patches) {
        this.patches = patches;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (cooldownTicks > 0) { cooldownTicks--; return false; }
        if (patches.getMode() == PatchesMode.SITTING) return false;
        if (++scanTicks < SCAN_INTERVAL_TICKS) return false;
        scanTicks = 0;
        if (followDistanceIsUrgent()) return false;
        return chooseBestNearbyTarget();
    }

    @Override public boolean canContinueToUse() {
        return targetKind != null && phase != Phase.IDLE && patches.getMode() != PatchesMode.SITTING;
    }

    @Override public void start() { beginNotice(); }

    @Override public void tick() {
        if (targetKind == null) return;
        if (followDistanceIsUrgent()) {
            report("INTERRUPTED", "Player reached Hurry range; abandoning " + targetName() + ".");
            finish(false); return;
        }
        if (!targetStillValid()) {
            report("INTERRUPTED", targetName() + " is no longer available.");
            finish(false); return;
        }

        // Before a low/medium interaction becomes established, a more significant nearby find may supersede it.
        if (phase == Phase.NOTICE || phase == Phase.APPROACH) {
            if (patches.tickCount % SCAN_INTERVAL_TICKS == 0 && tryUpgradeTarget()) return;
        }

        switch (targetKind) {
            case FLOWER, LOW_BLOCK -> tickLowBlock();
            case AXOLOTL -> tickAxolotl();
            case BLUE_AXOLOTL -> tickBlueAxolotl();
            case SNIFFER -> tickSniffer();
            case ARCHAEOLOGY -> tickArchaeology();
            case LOOT_CONTAINER -> tickLootContainer();
            case LOOT_VEHICLE -> tickLootVehicle();
            case WANDERING_TRADER -> tickWanderingTrader();
            case VALUABLE_BLOCK -> tickValuableBlock();
        }
    }

    private void tickLowBlock() {
        Vec3 center = Vec3.atCenterOf(blockTarget);
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (lowBlockReachedForInspection(center)) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = FLOWER_INSPECT_TICKS;
                    report("INSPECT", "Reached " + targetName() + "; taking a closer look.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAt(center.add(0.0, 0.15, 0.0));
                if (--phaseTicks <= 0) { phase = Phase.SHARE_WAIT; phaseTicks = FLOWER_SHARE_WAIT_TICKS; report("SHARE WAIT", "Finished inspecting; quietly enjoying " + targetName() + "."); }
            }
            case SHARE_WAIT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAt(center.add(0.0, 0.15, 0.0));
                Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over; sharing " + targetName() + " with Joy."); }
                else if (--phaseTicks <= 0) { report("COMPLETE", "Finished enjoying " + targetName() + "."); finish(true); }
            }
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.JOY);
                Player player = relevantPlayer(); if (player != null) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot());
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing " + targetName() + "; returning to normal behavior."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickAxolotl() {
        double distance = patches.distanceTo(axolotlTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Axolotl moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtAxolotl(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtAxolotl();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) { patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS; report("INSPECT", "Reached Axolotl; watching it for a while."); }
                else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(axolotlTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainAxolotlDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAtAxolotl();
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Looking to player to show off the Axolotl."); }
            }
            case PLAYER_INVITE -> {
                maintainAxolotlDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over; contentedly sharing the Axolotl."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtAxolotl();
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; finished watching the Axolotl."); finish(true); }
            }
            case SHARE_REACTION -> {
                maintainAxolotlDistance(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtAxolotl();
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the Axolotl; returning to normal behavior."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickBlueAxolotl() {
        double distance = patches.distanceTo(axolotlTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Blue Axolotl moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtAxolotl(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtAxolotl();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = VALUABLE_INSPECT_TICKS;
                    report("INSPECT", "Reached rare Blue Axolotl; excitedly watching it.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(axolotlTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainAxolotlDistance(); patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtAxolotl();
                if (--phaseTicks <= 0) { phase = Phase.BECKON; phaseTicks = VALUABLE_BECKON_TICKS; beckonCycleTicks = 0; beckonHops = 0; report("BECKON", "The Blue Axolotl is special; actively calling the player over."); }
            }
            case BECKON -> tickBlueAxolotlBeckon();
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.LAUGH_TONGUE);
                float celebrationYaw = patches.getYRot() + 24.0F;
                patches.setYRot(celebrationYaw);
                patches.yBodyRot = celebrationYaw;
                patches.yHeadRot = celebrationYaw;
                patches.yBodyRotO = celebrationYaw;
                patches.yHeadRotO = celebrationYaw;
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished celebrating the rare Blue Axolotl."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickBlueAxolotlBeckon() {
        patches.getNavigation().stop();
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        Player player = relevantPlayer();
        if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
            phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS;
            report("SHARE REACTION", "Player arrived; celebrating the Blue Axolotl."); return;
        }
        int cycle = beckonCycleTicks++ % 60;
        if (cycle < 38 && player != null) {
            patches.getLookControl().setLookAt(player, 30.0F, patches.getMaxHeadXRot());
            if ((cycle == 4 || cycle == 18) && patches.onGround() && beckonHops < 2) {
                Vec3 motion = patches.getDeltaMovement();
                patches.setDeltaMovement(motion.x, 0.34, motion.z);
                beckonHops++;
            }
        } else {
            lookAtAxolotl();
            if (cycle == 59) beckonHops = 0;
        }
        if (--phaseTicks <= 0) {
            report("COMPLETE", "Player did not come over; remembered the rare Blue Axolotl.");
            finish(true);
        }
    }

    private void tickSniffer() {
        double distance = patches.distanceTo(snifferTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Sniffer moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtSniffer(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtSniffer();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) { patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS; report("INSPECT", "Reached Sniffer; watching the unusual ancient creature."); }
                else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(snifferTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainSnifferDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAtSniffer();
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Looking to player to show off the Sniffer."); }
            }
            case PLAYER_INVITE -> {
                maintainSnifferDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over; sharing the Sniffer encounter."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtSniffer();
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; finished watching the Sniffer."); finish(true); }
            }
            case SHARE_REACTION -> {
                maintainSnifferDistance(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtSniffer();
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the Sniffer encounter."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickArchaeology() {
        Vec3 center = Vec3.atCenterOf(blockTarget);
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (lowBlockReachedForInspection(center)) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS;
                    report("INSPECT", "Reached " + targetName() + "; checking the unusual block closely.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAt(center);
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Calling the player's attention to " + targetName() + "."); }
            }
            case PLAYER_INVITE -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over to inspect " + targetName() + "."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAt(center);
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; remembered " + targetName() + "."); finish(true); }
            }
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAt(center);
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the archaeology find."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickLootContainer() {
        Vec3 center = Vec3.atCenterOf(blockTarget);
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (lowBlockReachedForInspection(center)) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS;
                    report("INSPECT", "Reached " + targetName() + "; checking the unopened find.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAt(center);
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Calling the player's attention to " + targetName() + "."); }
            }
            case PLAYER_INVITE -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over to check " + targetName() + "."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAt(center);
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; remembered " + targetName() + "."); finish(true); }
            }
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAt(center);
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the unopened container find."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickLootVehicle() {
        double distance = patches.distanceTo(lootVehicleTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Loot vehicle moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtLootVehicle(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtLootVehicle();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS;
                    report("INSPECT", "Reached " + targetName() + "; checking the unopened find.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(lootVehicleTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainLootVehicleDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAtLootVehicle();
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Calling the player's attention to " + targetName() + "."); }
            }
            case PLAYER_INVITE -> {
                maintainLootVehicleDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over to check " + targetName() + "."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtLootVehicle();
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; remembered " + targetName() + "."); finish(true); }
            }
            case SHARE_REACTION -> {
                maintainLootVehicleDistance(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtLootVehicle();
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the unopened vehicle-container find."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickWanderingTrader() {
        double distance = patches.distanceTo(wanderingTraderTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Wandering Trader moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtWanderingTrader(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtWanderingTrader();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) { patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS; report("INSPECT", "Reached Wandering Trader; watching the unusual visitor."); }
                else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(wanderingTraderTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainWanderingTraderDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAtWanderingTrader();
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Looking to player to show off the Wandering Trader."); }
            }
            case PLAYER_INVITE -> {
                maintainWanderingTraderDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over; sharing the Wandering Trader encounter."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtWanderingTrader();
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; finished watching the Wandering Trader."); finish(true); }
            }
            case SHARE_REACTION -> {
                maintainWanderingTraderDistance(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtWanderingTrader();
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the Wandering Trader encounter."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickValuableBlock() {
        Vec3 center = Vec3.atCenterOf(blockTarget);
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (Math.sqrt(patches.distanceToSqr(center)) <= VALUABLE_APPROACH_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = VALUABLE_INSPECT_TICKS;
                    report("INSPECT", "Reached " + targetName() + "; excitedly checking the find.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (--phaseTicks <= 0) { phase = Phase.BECKON; phaseTicks = VALUABLE_BECKON_TICKS; beckonCycleTicks = 0; beckonHops = 0; report("BECKON", "This is special; actively calling the player over."); }
            }
            case BECKON -> tickValuableBlockBeckon(center);
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.LAUGH_TONGUE);
                // Own both body-facing rotations during the celebration so normal mob controls do not cancel the spin.
                float celebrationYaw = patches.getYRot() + 24.0F;
                patches.setYRot(celebrationYaw);
                patches.yBodyRot = celebrationYaw;
                patches.yHeadRot = celebrationYaw;
                patches.yBodyRotO = celebrationYaw;
                patches.yHeadRotO = celebrationYaw;
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished celebrating the " + targetName() + " discovery."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickValuableBlockBeckon(Vec3 valuableCenter) {
        patches.getNavigation().stop();
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        Player player = relevantPlayer();
        if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
            phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS;
            report("SHARE REACTION", "Player arrived; celebrating the " + targetName() + " find."); return;
        }

        // 60-tick loop: face player and make two short hops, pause, glance back at the discovery, repeat.
        int cycle = beckonCycleTicks++ % 60;
        if (cycle < 38 && player != null) {
            patches.getLookControl().setLookAt(player, 30.0F, patches.getMaxHeadXRot());
            if ((cycle == 4 || cycle == 18) && patches.onGround() && beckonHops < 2) {
                Vec3 motion = patches.getDeltaMovement();
                patches.setDeltaMovement(motion.x, 0.34, motion.z);
                beckonHops++;
            }
        } else {
            lookAt(valuableCenter);
            if (cycle == 59) beckonHops = 0;
        }

        if (--phaseTicks <= 0) {
            report("COMPLETE", "Player did not come over; remembered the important " + targetName() + " discovery.");
            finish(true);
        }
    }

    @Override public void stop() { if (phase != Phase.IDLE) finish(false); }

    private boolean chooseBestNearbyTarget() {
        Axolotl blueAxolotl = findNearbyBlueAxolotl();
        if (blueAxolotl != null) { selectBlueAxolotl(blueAxolotl); return true; }
        BlockPos valuable = findNearbyValuableBlock();
        if (valuable != null) { selectValuableBlock(valuable); return true; }
        Axolotl axolotl = findNearbyAxolotl();
        if (axolotl != null) { selectAxolotl(axolotl); return true; }
        WanderingTrader trader = findNearbyWanderingTrader();
        if (trader != null) { selectWanderingTrader(trader); return true; }
        Sniffer sniffer = findNearbySniffer();
        if (sniffer != null) { selectSniffer(sniffer); return true; }
        BlockPos archaeology = findNearbyArchaeology();
        if (archaeology != null) { selectArchaeology(archaeology); return true; }
        BlockPos lootContainer = findNearbyLootContainer();
        if (lootContainer != null) { selectLootContainer(lootContainer); return true; }
        Entity lootVehicle = findNearbyLootVehicle();
        if (lootVehicle != null) { selectLootVehicle(lootVehicle); return true; }
        BlockPos flower = findNearbyFlower();
        if (flower != null) { selectFlower(flower); return true; }
        BlockPos lowBlock = findNearbyLowBlock();
        if (lowBlock != null) { selectLowBlock(lowBlock); return true; }
        return false;
    }

    private boolean tryUpgradeTarget() {
        if (targetPriority != PatchesCuriosityPriority.HIGH) {
            Axolotl blueAxolotl = findNearbyBlueAxolotl();
            if (blueAxolotl != null) { report("PRIORITY", "A rare Blue Axolotl outranks the current curiosity; switching targets."); selectBlueAxolotl(blueAxolotl); beginNotice(); return true; }
            BlockPos valuable = findNearbyValuableBlock();
            if (valuable != null) { report("PRIORITY", "A valuable discovery outranks the current curiosity; switching targets."); selectValuableBlock(valuable); beginNotice(); return true; }
        }
        if (targetPriority == PatchesCuriosityPriority.LOW) {
            Axolotl axolotl = findNearbyAxolotl();
            if (axolotl != null) { report("PRIORITY", "An Axolotl is more interesting than the current low curiosity; switching targets."); selectAxolotl(axolotl); beginNotice(); return true; }
            WanderingTrader trader = findNearbyWanderingTrader();
            if (trader != null) { report("PRIORITY", "A Wandering Trader is more interesting than the current low curiosity; switching targets."); selectWanderingTrader(trader); beginNotice(); return true; }
            Sniffer sniffer = findNearbySniffer();
            if (sniffer != null) { report("PRIORITY", "A Sniffer is more interesting than the current low curiosity; switching targets."); selectSniffer(sniffer); beginNotice(); return true; }
            BlockPos archaeology = findNearbyArchaeology();
            if (archaeology != null) { report("PRIORITY", "An archaeology find is more interesting than the current low curiosity; switching targets."); selectArchaeology(archaeology); beginNotice(); return true; }
            BlockPos lootContainer = findNearbyLootContainer();
            if (lootContainer != null) { report("PRIORITY", "An unopened generated container is more interesting than the current low curiosity; switching targets."); selectLootContainer(lootContainer); beginNotice(); return true; }
            Entity lootVehicle = findNearbyLootVehicle();
            if (lootVehicle != null) { report("PRIORITY", "An unopened loot vehicle is more interesting than the current low curiosity; switching targets."); selectLootVehicle(lootVehicle); beginNotice(); return true; }
        }
        return false;
    }

    private void selectFlower(BlockPos flower) { targetKind = TargetKind.FLOWER; targetPriority = PatchesCuriosityPriority.LOW; blockTarget = flower.immutable(); blockMemoryTarget = flower.immutable(); axolotlTarget = null; wanderingTraderTarget = null; }
    private void selectLowBlock(BlockPos pos) {
        targetKind = TargetKind.LOW_BLOCK; targetPriority = PatchesCuriosityPriority.LOW;
        blockTarget = lowBlockVisibleTarget(pos);
        blockMemoryTarget = canonicalLowBlockPos(blockTarget);
        axolotlTarget = null; wanderingTraderTarget = null;
    }
    private void selectBlueAxolotl(Axolotl axolotl) { targetKind = TargetKind.BLUE_AXOLOTL; targetPriority = PatchesCuriosityPriority.HIGH; axolotlTarget = axolotl; wanderingTraderTarget = null; snifferTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectAxolotl(Axolotl axolotl) { targetKind = TargetKind.AXOLOTL; targetPriority = PatchesCuriosityPriority.MEDIUM; axolotlTarget = axolotl; wanderingTraderTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectWanderingTrader(WanderingTrader trader) { targetKind = TargetKind.WANDERING_TRADER; targetPriority = PatchesCuriosityPriority.MEDIUM; wanderingTraderTarget = trader; axolotlTarget = null; snifferTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectSniffer(Sniffer sniffer) { targetKind = TargetKind.SNIFFER; targetPriority = PatchesCuriosityPriority.MEDIUM; snifferTarget = sniffer; axolotlTarget = null; wanderingTraderTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectArchaeology(BlockPos pos) { targetKind = TargetKind.ARCHAEOLOGY; targetPriority = PatchesCuriosityPriority.MEDIUM; blockTarget = pos.immutable(); blockMemoryTarget = pos.immutable(); axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; }
    private void selectLootContainer(BlockPos pos) { targetKind = TargetKind.LOOT_CONTAINER; targetPriority = PatchesCuriosityPriority.MEDIUM; blockTarget = pos.immutable(); blockMemoryTarget = canonicalLootContainerPos(pos); axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; lootVehicleTarget = null; }
    private void selectLootVehicle(Entity vehicle) { targetKind = TargetKind.LOOT_VEHICLE; targetPriority = PatchesCuriosityPriority.MEDIUM; lootVehicleTarget = vehicle; blockTarget = null; blockMemoryTarget = null; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; }
    private void selectValuableBlock(BlockPos valuable) { targetKind = TargetKind.VALUABLE_BLOCK; targetPriority = PatchesCuriosityPriority.HIGH; blockTarget = valuable.immutable(); blockMemoryTarget = valuable.immutable(); axolotlTarget = null; wanderingTraderTarget = null; }

    private void beginNotice() { phase = Phase.NOTICE; phaseTicks = 8; patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); report("NOTICE", "Spotted " + targetName() + " (" + targetPriority + ")."); }
    private void enterApproach() { phase = Phase.APPROACH; report("APPROACH", "Going over to investigate " + targetName() + "."); }

    private void finish(boolean remember) {
        if (remember) {
            if (targetKind == TargetKind.FLOWER && blockTarget != null) patches.rememberFlowerCuriosity(blockTarget);
            if (targetKind == TargetKind.LOW_BLOCK && blockMemoryTarget != null) patches.rememberLowBlockCuriosity(blockMemoryTarget);
            if ((targetKind == TargetKind.AXOLOTL || targetKind == TargetKind.BLUE_AXOLOTL) && axolotlTarget != null) patches.rememberAxolotlCuriosity(axolotlTarget.getUUID());
            if (targetKind == TargetKind.WANDERING_TRADER && wanderingTraderTarget != null) patches.rememberWanderingTraderCuriosity(wanderingTraderTarget.getUUID());
            if (targetKind == TargetKind.SNIFFER && snifferTarget != null) patches.rememberSnifferCuriosity(snifferTarget.getUUID());
            if (targetKind == TargetKind.ARCHAEOLOGY && blockTarget != null) patches.rememberArchaeologyCuriosity(blockTarget);
            if (targetKind == TargetKind.LOOT_CONTAINER && blockMemoryTarget != null) patches.rememberLootContainerCuriosity(blockMemoryTarget);
            if (targetKind == TargetKind.LOOT_VEHICLE && lootVehicleTarget != null) patches.rememberLootVehicleCuriosity(lootVehicleTarget.getUUID());
            if (targetKind == TargetKind.VALUABLE_BLOCK && blockTarget != null) patches.rememberValuableCuriosity(valuableKind(blockTarget), blockTarget);
        }
        patches.getNavigation().stop(); patches.clearActivityExpression(); targetKind = null; targetPriority = null; blockTarget = null; blockMemoryTarget = null; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; lootVehicleTarget = null;
        phase = Phase.IDLE; phaseTicks = 0; cooldownTicks = GENERAL_COOLDOWN_TICKS; beckonCycleTicks = 0; beckonHops = 0;
    }

    private boolean targetStillValid() {
        if (targetKind == TargetKind.FLOWER) return blockTarget != null && patches.level().getBlockState(blockTarget).is(BlockTags.FLOWERS);
        if (targetKind == TargetKind.LOW_BLOCK) return blockTarget != null && isLowCuriosityBlock(blockTarget);
        if (targetKind == TargetKind.VALUABLE_BLOCK) return blockTarget != null && isValuableBlock(blockTarget);
        if (targetKind == TargetKind.WANDERING_TRADER) return wanderingTraderTarget != null && wanderingTraderTarget.isAlive() && !wanderingTraderTarget.isRemoved() && wanderingTraderTarget.level() == patches.level();
        if (targetKind == TargetKind.SNIFFER) return snifferTarget != null && snifferTarget.isAlive() && !snifferTarget.isRemoved() && snifferTarget.level() == patches.level();
        if (targetKind == TargetKind.ARCHAEOLOGY) return blockTarget != null && isUnresolvedArchaeology(blockTarget);
        if (targetKind == TargetKind.LOOT_CONTAINER) return blockTarget != null && isUnresolvedLootContainer(blockTarget);
        if (targetKind == TargetKind.LOOT_VEHICLE) return lootVehicleTarget != null && lootVehicleTarget.isAlive() && !lootVehicleTarget.isRemoved() && lootVehicleTarget.level() == patches.level() && hasUnresolvedVehicleLoot(lootVehicleTarget);
        if (targetKind == TargetKind.BLUE_AXOLOTL) return axolotlTarget != null && axolotlTarget.isAlive() && !axolotlTarget.isRemoved() && axolotlTarget.level() == patches.level() && isBlueAxolotl(axolotlTarget);
        return axolotlTarget != null && axolotlTarget.isAlive() && !axolotlTarget.isRemoved() && axolotlTarget.level() == patches.level();
    }

    private boolean lowBlockReachedForInspection(Vec3 center) {
        if (targetKind == TargetKind.FLOWER) return Math.sqrt(patches.distanceToSqr(center)) <= FLOWER_APPROACH_DISTANCE;
        double dx = patches.getX() - center.x;
        double dz = patches.getZ() - center.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(patches.getEyeY() - center.y);
        return horizontalDistance <= LOW_BLOCK_HORIZONTAL_APPROACH_DISTANCE && verticalDistance <= LOW_BLOCK_MAX_VERTICAL_LOOK_DISTANCE;
    }

    private void maintainAxolotlDistance() { double distance = patches.distanceTo(axolotlTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(axolotlTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }
    private void maintainWanderingTraderDistance() { double distance = patches.distanceTo(wanderingTraderTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(wanderingTraderTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }
    private void maintainSnifferDistance() { double distance = patches.distanceTo(snifferTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(snifferTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }
    private void maintainLootVehicleDistance() { double distance = patches.distanceTo(lootVehicleTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(lootVehicleTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }

    private Axolotl findNearbyBlueAxolotl() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Axolotl.class, search, axolotl -> axolotl.isAlive() && isBlueAxolotl(axolotl) && !patches.hasRememberedAxolotlCuriosity(axolotl.getUUID()) && patches.hasLineOfSight(axolotl)).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private boolean isBlueAxolotl(Axolotl axolotl) { return axolotl.getVariant() == Axolotl.Variant.BLUE; }

    private Axolotl findNearbyAxolotl() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Axolotl.class, search, axolotl -> axolotl.isAlive() && !isBlueAxolotl(axolotl) && !patches.hasRememberedAxolotlCuriosity(axolotl.getUUID()) && patches.hasLineOfSight(axolotl)).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private WanderingTrader findNearbyWanderingTrader() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(WanderingTrader.class, search, trader -> trader.isAlive() && !patches.hasRememberedWanderingTraderCuriosity(trader.getUUID()) && patches.hasLineOfSight(trader)).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private Sniffer findNearbySniffer() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Sniffer.class, search, sniffer -> sniffer.isAlive() && !patches.hasRememberedSnifferCuriosity(sniffer.getUUID()) && patches.hasLineOfSight(sniffer)).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private Entity findNearbyLootVehicle() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Entity.class, search, entity -> entity != patches
                && entity.isAlive()
                && entity instanceof ContainerEntity
                && hasUnresolvedVehicleLoot(entity)
                && !patches.hasRememberedLootVehicleCuriosity(entity.getUUID())
                && patches.hasLineOfSight(entity))
                .stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private boolean hasUnresolvedVehicleLoot(Entity entity) {
        return entity instanceof ContainerEntity container && container.getContainerLootTable() != null;
    }

    private BlockPos findNearbyArchaeology() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius))) {
            if (!isUnresolvedArchaeology(pos) || patches.hasRememberedArchaeologyCuriosity(pos) || !canSeeBlock(pos)) continue;
            double distance = pos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable(); bestDistance = distance;
        }
        return best;
    }

    private boolean isUnresolvedArchaeology(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        if (!state.is(Blocks.SUSPICIOUS_SAND) && !state.is(Blocks.SUSPICIOUS_GRAVEL)) return false;
        if (!(patches.level().getBlockEntity(pos) instanceof BrushableBlockEntity brushable)) return false;
        return ((BrushableBlockEntityAccessor) brushable).patches$getLootTable() != null;
    }

    private BlockPos findNearbyLootContainer() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius))) {
            if (!isUnresolvedLootContainer(pos) || !canSeeBlock(pos)) continue;
            BlockPos memoryPos = canonicalLootContainerPos(pos);
            if (patches.hasRememberedLootContainerCuriosity(memoryPos)) continue;
            double distance = pos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable(); bestDistance = distance;
        }
        return best;
    }

    private boolean isUnresolvedLootContainer(BlockPos pos) {
        if (hasUnresolvedLootTable(pos)) return true;
        var state = patches.level().getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false;
        return hasUnresolvedLootTable(ChestBlock.getConnectedBlockPos(pos, state));
    }

    private boolean hasUnresolvedLootTable(BlockPos pos) {
        return patches.level().getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null;
    }

    private BlockPos canonicalLootContainerPos(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos.immutable();
        BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
        if (other.getX() < pos.getX()) return other.immutable();
        if (other.getX() > pos.getX()) return pos.immutable();
        if (other.getY() < pos.getY()) return other.immutable();
        if (other.getY() > pos.getY()) return pos.immutable();
        return other.getZ() < pos.getZ() ? other.immutable() : pos.immutable();
    }

    private BlockPos findNearbyFlower() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!patches.level().getBlockState(pos).is(BlockTags.FLOWERS) || !canSeeBlock(pos)) continue;
            BlockPos curiosityPos = canonicalLowBlockPos(pos);
            if (patches.hasRememberedFlowerCuriosity(curiosityPos)) continue;
            double distance = curiosityPos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable(); bestDistance = distance;
        }
        return best;
    }

    private BlockPos findNearbyValuableBlock() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius))) {
            if (!isValuableBlock(pos) || patches.hasRememberedValuableCuriosityNear(valuableKind(pos), pos) || !canSeeBlock(pos)) continue;
            double distance = pos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = pos.immutable(); bestDistance = distance;
        }
        return best;
    }

    private BlockPos findNearbyLowBlock() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!isLowCuriosityBlock(pos) || !canSeeBlock(pos)) continue;
            BlockPos curiosityPos = canonicalLowBlockPos(pos);
            if (patches.hasRememberedLowBlockCuriosity(curiosityPos)) continue;
            double distance = curiosityPos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = curiosityPos; bestDistance = distance;
        }
        return best;
    }

    /**
     * Treat vertically connected pieces of the same plant block as one curiosity.
     * Small Dripleaf and two-block flowers otherwise appear as separate block positions
     * even though Patches should understand them as one plant.
     */
    private BlockPos canonicalVerticalPlantPos(BlockPos pos) {
        var block = patches.level().getBlockState(pos).getBlock();
        BlockPos canonical = pos.immutable();
        while (patches.level().getBlockState(canonical.below()).getBlock() == block) canonical = canonical.below();
        return canonical;
    }

    private BlockPos lowBlockVisibleTarget(BlockPos pos) {
        if (patches.level().getBlockState(pos).is(Blocks.BIG_DRIPLEAF_STEM)) {
            BlockPos cursor = pos;
            while (patches.level().getBlockState(cursor).is(Blocks.BIG_DRIPLEAF_STEM)) cursor = cursor.above();
            if (patches.level().getBlockState(cursor).is(Blocks.BIG_DRIPLEAF)) return cursor.immutable();
        }
        return pos.immutable();
    }

    private BlockPos canonicalLowBlockPos(BlockPos pos) {
        if (patches.level().getBlockState(pos).is(Blocks.BIG_DRIPLEAF)) {
            BlockPos cursor = pos.below();
            BlockPos lowestStem = null;
            while (patches.level().getBlockState(cursor).is(Blocks.BIG_DRIPLEAF_STEM)) {
                lowestStem = cursor.immutable();
                cursor = cursor.below();
            }
            if (lowestStem != null) return lowestStem;
        }
        return canonicalVerticalPlantPos(pos);
    }

    private boolean isLowCuriosityBlock(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        if (state.is(Blocks.FIREFLY_BUSH)) return isExposedToAir(pos);
        return state.is(Blocks.SMALL_DRIPLEAF) || state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.FROGSPAWN)
                || state.is(Blocks.TURTLE_EGG) || state.is(Blocks.SNIFFER_EGG);
    }

    private boolean isExposedToAir(BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) if (patches.level().getBlockState(pos.relative(direction)).isAir()) return true;
        return false;
    }

    private boolean canSeeBlock(BlockPos pos) {
        Vec3 eye = patches.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hit = patches.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, patches));
        return hit.getBlockPos().equals(pos);
    }

    private boolean isValuableBlock(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        return state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }
    private String valuableKind(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return "emerald";
        if (state.is(Blocks.ANCIENT_DEBRIS)) return "ancient_debris";
        return "diamond";
    }
    private boolean followDistanceIsUrgent() { if (patches.getMode() != PatchesMode.FOLLOWING) return false; Player player = patches.getFollowingPlayer(); return player != null && patches.distanceTo(player) >= HURRY_INTERRUPT_DISTANCE; }
    private Player relevantPlayer() { Player followed = patches.getFollowingPlayer(); if (followed != null) return followed; return patches.level().getNearestPlayer(patches, 12.0); }
    private void lookAt(Vec3 target) { patches.getLookControl().setLookAt(target.x, target.y, target.z, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtAxolotl() { patches.getLookControl().setLookAt(axolotlTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtWanderingTrader() { patches.getLookControl().setLookAt(wanderingTraderTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtSniffer() { patches.getLookControl().setLookAt(snifferTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtLootVehicle() { patches.getLookControl().setLookAt(lootVehicleTarget, 20.0F, patches.getMaxHeadXRot()); }
    private String targetName() {
        return switch (targetKind) {
            case AXOLOTL -> "an Axolotl";
            case BLUE_AXOLOTL -> "a rare Blue Axolotl";
            case WANDERING_TRADER -> "a Wandering Trader";
            case SNIFFER -> "a Sniffer";
            case ARCHAEOLOGY -> patches.level().getBlockState(blockTarget).is(Blocks.SUSPICIOUS_SAND) ? "Suspicious Sand" : "Suspicious Gravel";
            case LOOT_CONTAINER -> "an unopened " + lootContainerName();
            case LOOT_VEHICLE -> "an unopened " + lootVehicleName();
            case VALUABLE_BLOCK -> {
                var state = patches.level().getBlockState(blockTarget);
                if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) yield "Emerald Ore";
                if (state.is(Blocks.ANCIENT_DEBRIS)) yield "Ancient Debris";
                yield "Diamond Ore";
            }
            case LOW_BLOCK -> {
                var state = patches.level().getBlockState(blockTarget);
                if (state.is(Blocks.FIREFLY_BUSH)) yield "a Firefly Bush";
                if (state.is(Blocks.SMALL_DRIPLEAF)) yield "Small Dripleaf";
                if (state.is(Blocks.BIG_DRIPLEAF)) yield "Big Dripleaf";
                if (state.is(Blocks.FROGSPAWN)) yield "Frogspawn";
                if (state.is(Blocks.TURTLE_EGG)) yield "Turtle Eggs";
                if (state.is(Blocks.SNIFFER_EGG)) yield "a Sniffer Egg";
                yield "something interesting";
            }
            default -> "a flower";
        };
    }
    private String lootVehicleName() {
        String name = lootVehicleTarget.getType().getDescription().getString();
        return name;
    }

    private String lootContainerName() {
        var state = patches.level().getBlockState(blockTarget);
        if (state.getBlock() instanceof ChestBlock) return state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? "Chest" : "Double Chest";
        if (state.is(Blocks.BARREL)) return "Barrel";
        if (state.is(Blocks.HOPPER)) return "Hopper";
        if (state.is(Blocks.DISPENSER)) return "Dispenser";
        if (state.is(Blocks.DROPPER)) return "Dropper";
        if (state.is(Blocks.SHULKER_BOX)) return "Shulker Box";
        if (state.is(Blocks.CRAFTER)) return "Crafter";
        return "loot container";
    }
    private void report(String state, String detail) { if (!DEBUG_CURIOSITY) return; Player player = relevantPlayer(); if (player != null) player.sendSystemMessage(Component.literal("[Patches] CURIOSITY: " + state + " — " + detail)); }

    private enum TargetKind { FLOWER, LOW_BLOCK, AXOLOTL, BLUE_AXOLOTL, WANDERING_TRADER, SNIFFER, ARCHAEOLOGY, LOOT_CONTAINER, LOOT_VEHICLE, VALUABLE_BLOCK }
    private enum Phase { IDLE, NOTICE, APPROACH, INSPECT, SHARE_WAIT, PLAYER_INVITE, BECKON, SHARE_REACTION }
}
