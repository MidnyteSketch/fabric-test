package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesExpression;
import com.midnyte.patches.entity.PatchesMode;
import com.midnyte.patches.mixin.BrushableBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

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

    private static final double DISCOVERY_RADIUS = 18.0;
    private final PatchesFamiliarity familiarity = new PatchesFamiliarity();
    private PatchesGeode.Feature geode;
    private List<BlockPos> geodeLooks = List.of();
    private BlockPos observationPoint;
    private boolean discovery;
    private boolean leadWaiting;
    private Player discoveryPlayer;
    private long activityStarted, leadWaitStarted, nextProgressCheck;
    private int failedProgress;
    private double lastObservationDistance = Double.POSITIVE_INFINITY;
    private Vec3 lastPlayerPosition;
    private long nextDiscoveryScan;
    private long nextGeodeScan;
    private TraderLlama caravanLook;
    private long nextCaravanLook;
    private long caravanLookUntil;

    private final PatchesEntity patches;
    private TargetKind targetKind;
    private PatchesCuriosityPriority targetPriority;
    private BlockPos blockTarget;
    private BlockPos blockMemoryTarget;
    private Axolotl axolotlTarget;
    private WanderingTrader wanderingTraderTarget;
    private Sniffer snifferTarget;
    private Sheep pinkSheepTarget;
    private Allay trappedAllayTarget;
    private Entity lootVehicleTarget;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownTicks;
    private int scanTicks;
    private int beckonCycleTicks;
    private int beckonHops;

    public void resetCooldownForDebug() {
        cooldownTicks = 0; scanTicks = SCAN_INTERVAL_TICKS; nextDiscoveryScan = 0; nextGeodeScan = 0;
        report("FAMILIARITY", familiarity.describe(patches.level().getGameTime()) + "; decays 1/minute, resets on reload; exact memories unchanged.");
        report("DISCOVERY", discovery ? "Active lead: " + phase : discoveryReady() ? "Armed: Spyglass + Following; search radius 18." : "Inactive: requires Spyglass, Following, and player within 6 blocks.");
    }
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
        if (discovery && !validateDiscovery()) return;
        if (!targetStillValid()) {
            report("INTERRUPTED", targetName() + " is no longer available.");
            finish(false); return;
        }

        // Before a low/medium interaction becomes established, a more significant nearby find may supersede it.
        if (phase == Phase.NOTICE || phase == Phase.APPROACH) {
            if (patches.tickCount % SCAN_INTERVAL_TICKS == 0 && tryUpgradeTarget()) return;
        }

        if (discovery && phase == Phase.APPROACH) { tickDiscoveryLead(); return; }
        switch (targetKind) {
            case GEODE -> tickGeode();
            case FLOWER, LOW_BLOCK -> tickLowBlock();
            case AXOLOTL -> tickAxolotl();
            case BLUE_AXOLOTL -> tickBlueAxolotl();
            case TRAPPED_ALLAY -> tickTrappedAllay();
            case PINK_SHEEP -> tickPinkSheep();
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

    private void tickPinkSheep() {
        double distance = patches.distanceTo(pinkSheepTarget);
        if (distance > AXOLOTL_ABANDON_DISTANCE) { report("INTERRUPTED", "Pink Sheep moved too far away to keep following."); finish(false); return; }
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtPinkSheep(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtPinkSheep();
                if (distance <= AXOLOTL_COMFORT_DISTANCE) { patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = AXOLOTL_INSPECT_TICKS; report("INSPECT", "Reached Pink Sheep; admiring its unusual wool."); }
                else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(pinkSheepTarget, APPROACH_SPEED);
            }
            case INSPECT -> {
                maintainPinkSheepDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); lookAtPinkSheep();
                if (--phaseTicks <= 0) { phase = Phase.PLAYER_INVITE; phaseTicks = AXOLOTL_INVITE_TICKS; report("PLAYER INVITE", "Looking to player to show off the Pink Sheep."); }
            }
            case PLAYER_INVITE -> {
                maintainPinkSheepDistance(); patches.setActivityExpression(PatchesExpression.DEFAULT); Player player = relevantPlayer();
                if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) { phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS; report("SHARE REACTION", "Player came over; sharing the Pink Sheep encounter."); return; }
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtPinkSheep();
                if (--phaseTicks <= 0) { report("COMPLETE", "Player did not join; remembered the Pink Sheep."); finish(true); }
            }
            case SHARE_REACTION -> {
                maintainPinkSheepDistance(); patches.setActivityExpression(PatchesExpression.CONTENT); Player player = relevantPlayer();
                if (player != null && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 20.0F, patches.getMaxHeadXRot()); else lookAtPinkSheep();
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished sharing the Pink Sheep encounter."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickTrappedAllay() {
        if (!isAllayConfined(trappedAllayTarget) && phase != Phase.SHARE_REACTION) {
            patches.getNavigation().stop();
            phase = Phase.SHARE_REACTION;
            phaseTicks = SHARE_REACTION_TICKS;
            patches.setActivityExpression(PatchesExpression.JOY);
            report("SHARE REACTION", "The Allay is no longer confined; reacting happily to the rescue.");
        }

        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtTrappedAllay(); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtTrappedAllay();
                Vec3 observation = Vec3.atCenterOf(blockTarget);
                if (Math.sqrt(patches.distanceToSqr(observation)) <= VALUABLE_APPROACH_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = VALUABLE_INSPECT_TICKS;
                    report("INSPECT", "Reached the outside of the Allay's enclosure; checking on it.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) {
                    patches.getNavigation().moveTo(observation.x, observation.y, observation.z, APPROACH_SPEED);
                }
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); lookAtTrappedAllay();
                if (--phaseTicks <= 0) {
                    phase = Phase.BECKON; phaseTicks = VALUABLE_BECKON_TICKS; beckonCycleTicks = 0; beckonHops = 0;
                    report("BECKON", "The Allay is trapped; urgently calling the player over to help.");
                }
            }
            case BECKON -> tickTrappedAllayBeckon();
            case SHARE_REACTION -> {
                patches.getNavigation().stop();
                boolean rescued = !isAllayConfined(trappedAllayTarget);
                patches.setActivityExpression(rescued ? PatchesExpression.JOY : PatchesExpression.CONTENT);
                Player player = relevantPlayer();
                if (player != null && !rescued && (phaseTicks / 20) % 2 == 0) patches.getLookControl().setLookAt(player, 24.0F, patches.getMaxHeadXRot());
                else lookAtTrappedAllay();
                if (--phaseTicks <= 0) {
                    report("COMPLETE", rescued ? "Finished reacting to the freed Allay." : "Player came over; leaving the trapped Allay in their attention.");
                    finish(true);
                }
            }
            default -> { }
        }
    }

    private void tickTrappedAllayBeckon() {
        patches.getNavigation().stop();
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        Player player = relevantPlayer();
        if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
            phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS;
            report("SHARE REACTION", "Player arrived at the enclosure; keeping attention on the trapped Allay.");
            return;
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
            lookAtTrappedAllay();
            if (cycle == 59) beckonHops = 0;
        }

        if (--phaseTicks <= 0) {
            report("COMPLETE", "Player did not come over; remembered the trapped Allay.");
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
        Allay trappedAllay = findNearbyTrappedAllay();
        if (trappedAllay != null && allowFamiliarity(PatchesFamiliarity.Category.TRAPPED_ALLAY, PatchesCuriosityPriority.HIGH)) { selectTrappedAllay(trappedAllay); return true; }
        Axolotl blueAxolotl = findNearbyBlueAxolotl();
        if (blueAxolotl != null && allowFamiliarity(PatchesFamiliarity.Category.BLUE_AXOLOTL, PatchesCuriosityPriority.HIGH)) { selectBlueAxolotl(blueAxolotl); return true; }
        BlockPos valuable = findNearbyValuableBlock();
        if (valuable != null && allowFamiliarity(valuableCategory(valuable), PatchesCuriosityPriority.HIGH)) { selectValuableBlock(valuable); return true; }
        if (chooseDiscoveryTarget()) return true;
        Axolotl axolotl = findNearbyAxolotl();
        if (axolotl != null && allowFamiliarity(PatchesFamiliarity.Category.AXOLOTL, PatchesCuriosityPriority.MEDIUM)) { selectAxolotl(axolotl); return true; }
        WanderingTrader trader = findNearbyWanderingTrader();
        if (trader != null && allowFamiliarity(PatchesFamiliarity.Category.WANDERING_TRADER, PatchesCuriosityPriority.MEDIUM)) { selectWanderingTrader(trader); return true; }
        Sniffer sniffer = findNearbySniffer();
        if (sniffer != null && allowFamiliarity(PatchesFamiliarity.Category.SNIFFER, PatchesCuriosityPriority.MEDIUM)) { selectSniffer(sniffer); return true; }
        Sheep pinkSheep = findNearbyPinkSheep();
        if (pinkSheep != null && allowFamiliarity(PatchesFamiliarity.Category.PINK_SHEEP, PatchesCuriosityPriority.MEDIUM)) { selectPinkSheep(pinkSheep); return true; }
        BlockPos archaeology = findNearbyArchaeology();
        if (archaeology != null && allowFamiliarity(PatchesFamiliarity.Category.ARCHAEOLOGY, PatchesCuriosityPriority.MEDIUM)) { selectArchaeology(archaeology); return true; }
        BlockPos lootContainer = findNearbyLootContainer();
        if (lootContainer != null && allowFamiliarity(PatchesFamiliarity.Category.LOOT_CONTAINER, PatchesCuriosityPriority.MEDIUM)) { selectLootContainer(lootContainer); return true; }
        Entity lootVehicle = findNearbyLootVehicle();
        if (lootVehicle != null && allowFamiliarity(PatchesFamiliarity.Category.LOOT_VEHICLE, PatchesCuriosityPriority.MEDIUM)) { selectLootVehicle(lootVehicle); return true; }
        BlockPos flower = findNearbyFlower();
        if (flower != null && allowFamiliarity(PatchesFamiliarity.Category.FLOWER, PatchesCuriosityPriority.LOW)) { selectFlower(flower); return true; }
        BlockPos lowBlock = findNearbyLowBlock();
        if (lowBlock != null && allowFamiliarity(PatchesFamiliarity.Category.LOW_BLOCK, PatchesCuriosityPriority.LOW)) { selectLowBlock(lowBlock); return true; }
        return chooseGeode();
    }

    private boolean tryUpgradeTarget() {
        if (targetPriority != PatchesCuriosityPriority.HIGH) {
            Allay trappedAllay = findNearbyTrappedAllay();
            if (trappedAllay != null && allowFamiliarity(PatchesFamiliarity.Category.TRAPPED_ALLAY, PatchesCuriosityPriority.HIGH)) { report("PRIORITY", "A trapped Allay needs help; switching targets."); selectTrappedAllay(trappedAllay); clearExploration(); beginNotice(); return true; }
            Axolotl blueAxolotl = findNearbyBlueAxolotl();
            if (blueAxolotl != null && allowFamiliarity(PatchesFamiliarity.Category.BLUE_AXOLOTL, PatchesCuriosityPriority.HIGH)) { report("PRIORITY", "A rare Blue Axolotl outranks the current curiosity; switching targets."); selectBlueAxolotl(blueAxolotl); clearExploration(); beginNotice(); return true; }
            BlockPos valuable = findNearbyValuableBlock();
            if (valuable != null && allowFamiliarity(valuableCategory(valuable), PatchesCuriosityPriority.HIGH)) { report("PRIORITY", "A valuable discovery outranks the current curiosity; switching targets."); selectValuableBlock(valuable); clearExploration(); beginNotice(); return true; }
        }
        if (targetPriority == PatchesCuriosityPriority.LOW) {
            Axolotl axolotl = findNearbyAxolotl();
            if (axolotl != null && allowFamiliarity(PatchesFamiliarity.Category.AXOLOTL, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "An Axolotl is more interesting than the current low curiosity; switching targets."); selectAxolotl(axolotl); clearExploration(); beginNotice(); return true; }
            WanderingTrader trader = findNearbyWanderingTrader();
            if (trader != null && allowFamiliarity(PatchesFamiliarity.Category.WANDERING_TRADER, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "A Wandering Trader is more interesting than the current low curiosity; switching targets."); selectWanderingTrader(trader); clearExploration(); beginNotice(); return true; }
            Sniffer sniffer = findNearbySniffer();
            if (sniffer != null && allowFamiliarity(PatchesFamiliarity.Category.SNIFFER, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "A Sniffer is more interesting than the current low curiosity; switching targets."); selectSniffer(sniffer); clearExploration(); beginNotice(); return true; }
            Sheep pinkSheep = findNearbyPinkSheep();
            if (pinkSheep != null && allowFamiliarity(PatchesFamiliarity.Category.PINK_SHEEP, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "A Pink Sheep is more interesting than the current low curiosity; switching targets."); selectPinkSheep(pinkSheep); clearExploration(); beginNotice(); return true; }
            BlockPos archaeology = findNearbyArchaeology();
            if (archaeology != null && allowFamiliarity(PatchesFamiliarity.Category.ARCHAEOLOGY, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "An archaeology find is more interesting than the current low curiosity; switching targets."); selectArchaeology(archaeology); clearExploration(); beginNotice(); return true; }
            BlockPos lootContainer = findNearbyLootContainer();
            if (lootContainer != null && allowFamiliarity(PatchesFamiliarity.Category.LOOT_CONTAINER, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "An unopened generated container is more interesting than the current low curiosity; switching targets."); selectLootContainer(lootContainer); clearExploration(); beginNotice(); return true; }
            Entity lootVehicle = findNearbyLootVehicle();
            if (lootVehicle != null && allowFamiliarity(PatchesFamiliarity.Category.LOOT_VEHICLE, PatchesCuriosityPriority.MEDIUM)) { report("PRIORITY", "An unopened loot vehicle is more interesting than the current low curiosity; switching targets."); selectLootVehicle(lootVehicle); clearExploration(); beginNotice(); return true; }
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
    private void selectSniffer(Sniffer sniffer) { targetKind = TargetKind.SNIFFER; targetPriority = PatchesCuriosityPriority.MEDIUM; snifferTarget = sniffer; axolotlTarget = null; wanderingTraderTarget = null; pinkSheepTarget = null; trappedAllayTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectPinkSheep(Sheep sheep) { targetKind = TargetKind.PINK_SHEEP; targetPriority = PatchesCuriosityPriority.MEDIUM; pinkSheepTarget = sheep; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; trappedAllayTarget = null; lootVehicleTarget = null; blockTarget = null; blockMemoryTarget = null; }
    private void selectTrappedAllay(Allay allay) {
        BlockPos observation = findAllayObservationPoint(allay);
        if (observation == null) return;
        targetKind = TargetKind.TRAPPED_ALLAY; targetPriority = PatchesCuriosityPriority.HIGH; trappedAllayTarget = allay;
        blockTarget = observation; blockMemoryTarget = null; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; pinkSheepTarget = null; lootVehicleTarget = null;
    }
    private void selectArchaeology(BlockPos pos) { targetKind = TargetKind.ARCHAEOLOGY; targetPriority = PatchesCuriosityPriority.MEDIUM; blockTarget = pos.immutable(); blockMemoryTarget = pos.immutable(); axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; }
    private void selectLootContainer(BlockPos pos) { targetKind = TargetKind.LOOT_CONTAINER; targetPriority = PatchesCuriosityPriority.MEDIUM; blockTarget = pos.immutable(); blockMemoryTarget = canonicalLootContainerPos(pos); axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; lootVehicleTarget = null; }
    private void selectLootVehicle(Entity vehicle) { targetKind = TargetKind.LOOT_VEHICLE; targetPriority = PatchesCuriosityPriority.MEDIUM; lootVehicleTarget = vehicle; blockTarget = null; blockMemoryTarget = null; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; }
    private void selectValuableBlock(BlockPos valuable) { targetKind = TargetKind.VALUABLE_BLOCK; targetPriority = PatchesCuriosityPriority.HIGH; blockTarget = valuable.immutable(); blockMemoryTarget = valuable.immutable(); axolotlTarget = null; wanderingTraderTarget = null; }

    private void beginNotice() { phase = Phase.NOTICE; phaseTicks = 8; patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); report("NOTICE", "Spotted " + targetName() + " (" + targetPriority + ")."); }
    private void enterApproach() { phase = Phase.APPROACH; report("APPROACH", "Going over to investigate " + targetName() + "."); }

    private void finish(boolean remember) {
        if (remember) {
            if (targetKind == TargetKind.GEODE && geode != null) patches.rememberGeode(geode);
            double score = familiarity.record(currentCategory(), patches.level().getGameTime());
            report("FAMILIARITY", currentCategory() + " completed; score=" + String.format(java.util.Locale.ROOT, "%.2f", score) + "/8 (decays 1/minute).");
            if (targetKind == TargetKind.FLOWER && blockTarget != null) patches.rememberFlowerCuriosity(blockTarget);
            if (targetKind == TargetKind.LOW_BLOCK && blockMemoryTarget != null) patches.rememberLowBlockCuriosity(blockMemoryTarget);
            if ((targetKind == TargetKind.AXOLOTL || targetKind == TargetKind.BLUE_AXOLOTL) && axolotlTarget != null) patches.rememberAxolotlCuriosity(axolotlTarget.getUUID());
            if (targetKind == TargetKind.WANDERING_TRADER && wanderingTraderTarget != null) patches.rememberWanderingTraderCuriosity(wanderingTraderTarget.getUUID());
            if (targetKind == TargetKind.PINK_SHEEP && pinkSheepTarget != null) patches.rememberPinkSheepCuriosity(pinkSheepTarget.getUUID());
            if (targetKind == TargetKind.TRAPPED_ALLAY && trappedAllayTarget != null) patches.rememberTrappedAllayCuriosity(trappedAllayTarget.getUUID());
            if (targetKind == TargetKind.SNIFFER && snifferTarget != null) patches.rememberSnifferCuriosity(snifferTarget.getUUID());
            if (targetKind == TargetKind.ARCHAEOLOGY && blockTarget != null) patches.rememberArchaeologyCuriosity(blockTarget);
            if (targetKind == TargetKind.LOOT_CONTAINER && blockMemoryTarget != null) patches.rememberLootContainerCuriosity(blockMemoryTarget);
            if (targetKind == TargetKind.LOOT_VEHICLE && lootVehicleTarget != null) patches.rememberLootVehicleCuriosity(lootVehicleTarget.getUUID());
            if (targetKind == TargetKind.VALUABLE_BLOCK && blockTarget != null) patches.rememberValuableCuriosity(valuableKind(blockTarget), blockTarget);
        }
        clearExploration();
        patches.getNavigation().stop(); patches.clearActivityExpression(); targetKind = null; targetPriority = null; blockTarget = null; blockMemoryTarget = null; axolotlTarget = null; wanderingTraderTarget = null; snifferTarget = null; pinkSheepTarget = null; trappedAllayTarget = null; lootVehicleTarget = null;
        phase = Phase.IDLE; phaseTicks = 0; cooldownTicks = GENERAL_COOLDOWN_TICKS; beckonCycleTicks = 0; beckonHops = 0;
    }

    private boolean targetStillValid() {
        if (targetKind == TargetKind.GEODE) return geode != null && blockTarget != null && patches.level().hasChunkAt(blockTarget) && PatchesGeode.isInterior(patches.level().getBlockState(blockTarget));
        if (targetKind == TargetKind.FLOWER) return blockTarget != null && patches.level().getBlockState(blockTarget).is(BlockTags.FLOWERS);
        if (targetKind == TargetKind.LOW_BLOCK) return blockTarget != null && isLowCuriosityBlock(blockTarget);
        if (targetKind == TargetKind.VALUABLE_BLOCK) return blockTarget != null && isValuableBlock(blockTarget);
        if (targetKind == TargetKind.WANDERING_TRADER) return wanderingTraderTarget != null && wanderingTraderTarget.isAlive() && !wanderingTraderTarget.isRemoved() && wanderingTraderTarget.level() == patches.level();
        if (targetKind == TargetKind.SNIFFER) return snifferTarget != null && snifferTarget.isAlive() && !snifferTarget.isRemoved() && snifferTarget.level() == patches.level();
        if (targetKind == TargetKind.ARCHAEOLOGY) return blockTarget != null && isUnresolvedArchaeology(blockTarget);
        if (targetKind == TargetKind.LOOT_CONTAINER) return blockTarget != null && isUnresolvedLootContainer(blockTarget);
        if (targetKind == TargetKind.LOOT_VEHICLE) return lootVehicleTarget != null && lootVehicleTarget.isAlive() && !lootVehicleTarget.isRemoved() && lootVehicleTarget.level() == patches.level() && hasUnresolvedVehicleLoot(lootVehicleTarget);
        if (targetKind == TargetKind.PINK_SHEEP) return pinkSheepTarget != null && pinkSheepTarget.isAlive() && pinkSheepTarget.getColor() == DyeColor.PINK;
        if (targetKind == TargetKind.TRAPPED_ALLAY) return trappedAllayTarget != null && trappedAllayTarget.isAlive() && !trappedAllayTarget.isRemoved() && trappedAllayTarget.level() == patches.level();
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
    private void maintainPinkSheepDistance() { double distance = patches.distanceTo(pinkSheepTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(pinkSheepTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }
    private void maintainLootVehicleDistance() { double distance = patches.distanceTo(lootVehicleTarget); if (distance > AXOLOTL_REPOSITION_DISTANCE) patches.getNavigation().moveTo(lootVehicleTarget, APPROACH_SPEED); else patches.getNavigation().stop(); }

    private Sheep findNearbyPinkSheep() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Sheep.class, search, sheep -> sheep.isAlive()
                && sheep.getColor() == DyeColor.PINK
                && !patches.hasRememberedPinkSheepCuriosity(sheep.getUUID())
                && patches.hasLineOfSight(sheep))
                .stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private Allay findNearbyTrappedAllay() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Allay.class, search, allay -> allay.isAlive()
                && !allay.hasItemInHand()
                && !patches.hasRememberedTrappedAllayCuriosity(allay.getUUID())
                && isAllayConfined(allay)
                && canPerceiveAllayThroughCage(allay)
                && findAllayObservationPoint(allay) != null)
                .stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private boolean isAllayConfined(Allay allay) {
        if (!hasNearbyAllayCageMaterial(allay)) return false;
        Vec3 center = allay.position().add(0.0, allay.getBbHeight() * 0.5, 0.0);
        int blockedSides = 0;
        Vec3[] directions = { new Vec3(3.5, 0.0, 0.0), new Vec3(-3.5, 0.0, 0.0), new Vec3(0.0, 0.0, 3.5), new Vec3(0.0, 0.0, -3.5) };
        for (Vec3 direction : directions) {
            BlockHitResult hit = patches.level().clip(new ClipContext(center, center.add(direction), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, allay));
            if (hit.getType() != HitResult.Type.MISS) blockedSides++;
        }
        return blockedSides >= 3;
    }

    private boolean hasNearbyAllayCageMaterial(Allay allay) {
        BlockPos center = allay.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 3, 4))) {
            if (isAllayCageMaterial(pos)) return true;
        }
        return false;
    }

    private boolean isAllayCageMaterial(BlockPos pos) {
        var state = patches.level().getBlockState(pos);
        return state.is(BlockTags.FENCES) || state.is(Blocks.IRON_BARS);
    }

    private boolean canPerceiveAllayThroughCage(Allay allay) {
        Vec3 center = allay.position().add(0.0, allay.getBbHeight() * 0.5, 0.0);
        double halfWidth = Math.max(0.15, allay.getBbWidth() * 0.3);
        Vec3[] samples = {
                center,
                center.add(0.0, allay.getBbHeight() * 0.25, 0.0),
                center.add(halfWidth, 0.0, 0.0),
                center.add(-halfWidth, 0.0, 0.0),
                center.add(0.0, 0.0, halfWidth),
                center.add(0.0, 0.0, -halfWidth)
        };
        for (Vec3 sample : samples) if (rayCanPassAllayCage(patches.getEyePosition(), sample, allay)) return true;
        return false;
    }

    private boolean rayCanPassAllayCage(Vec3 start, Vec3 target, Entity context) {
        Vec3 direction = target.subtract(start);
        if (direction.lengthSqr() < 1.0e-6) return true;
        direction = direction.normalize();
        Vec3 cursor = start;
        for (int pass = 0; pass < 12; pass++) {
            BlockHitResult hit = patches.level().clip(new ClipContext(cursor, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() == HitResult.Type.MISS) return true;
            if (!isAllayCageMaterial(hit.getBlockPos())) return false;
            cursor = hit.getLocation().add(direction.scale(0.12));
            if (cursor.distanceToSqr(target) < 0.04) return true;
        }
        return false;
    }

    private BlockPos findAllayObservationPoint(Allay allay) {
        BlockPos center = allay.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos barrier : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 3, 4))) {
            if (!isAllayCageMaterial(barrier)) continue;
            BlockPos[] candidates = { barrier.north(), barrier.south(), barrier.east(), barrier.west() };
            for (BlockPos candidate : candidates) {
                if (!patches.getNavigation().isStableDestination(candidate)) continue;
                var path = patches.getNavigation().createPath(candidate, 0);
                if (path == null || !path.canReach()) continue;
                Vec3 eye = Vec3.atBottomCenterOf(candidate).add(0.0, patches.getEyeHeight(), 0.0);
                Vec3 target = allay.position().add(0.0, allay.getBbHeight() * 0.5, 0.0);
                if (!rayCanPassAllayCage(eye, target, allay)) continue;
                double distance = candidate.distSqr(patches.blockPosition());
                if (distance < bestDistance) { best = candidate.immutable(); bestDistance = distance; }
            }
        }
        return best;
    }

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
    private void lookAtWanderingTrader() {
        long now = patches.level().getGameTime();
        boolean groupPhase = phase == Phase.INSPECT || phase == Phase.PLAYER_INVITE || phase == Phase.SHARE_REACTION;
        if (groupPhase && now >= nextCaravanLook) {
            nextCaravanLook = now + 60 + patches.getRandom().nextInt(41);
            List<TraderLlama> llamas = patches.level().getEntitiesOfClass(TraderLlama.class,
                    wanderingTraderTarget.getBoundingBox().inflate(8.0), llama -> llama.isAlive()
                            && llama.getLeashHolder() == wanderingTraderTarget && patches.hasLineOfSight(llama));
            caravanLook = llamas.isEmpty() ? null : llamas.get(patches.getRandom().nextInt(llamas.size()));
            caravanLookUntil = now + 20;
            if (caravanLook != null) report("CARAVAN", "Glancing at this Trader's leashed llama; memory stays on Trader UUID.");
        }
        if (groupPhase && now < caravanLookUntil && caravanLook != null && caravanLook.isAlive()
                && caravanLook.getLeashHolder() == wanderingTraderTarget && caravanLook.distanceTo(wanderingTraderTarget) <= 8
                && patches.hasLineOfSight(caravanLook)) {
            patches.getLookControl().setLookAt(caravanLook, 20.0F, patches.getMaxHeadXRot());
        } else patches.getLookControl().setLookAt(wanderingTraderTarget, 20.0F, patches.getMaxHeadXRot());
    }
    private void lookAtSniffer() { patches.getLookControl().setLookAt(snifferTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtPinkSheep() { patches.getLookControl().setLookAt(pinkSheepTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtTrappedAllay() { patches.getLookControl().setLookAt(trappedAllayTarget, 24.0F, patches.getMaxHeadXRot()); }
    private void lookAtLootVehicle() { patches.getLookControl().setLookAt(lootVehicleTarget, 20.0F, patches.getMaxHeadXRot()); }
    private String targetName() {
        return switch (targetKind) {
            case GEODE -> "an Amethyst Geode";
            case AXOLOTL -> "an Axolotl";
            case BLUE_AXOLOTL -> "a rare Blue Axolotl";
            case WANDERING_TRADER -> "a Wandering Trader";
            case SNIFFER -> "a Sniffer";
            case ARCHAEOLOGY -> patches.level().getBlockState(blockTarget).is(Blocks.SUSPICIOUS_SAND) ? "Suspicious Sand" : "Suspicious Gravel";
            case LOOT_CONTAINER -> "an unopened " + lootContainerName();
            case LOOT_VEHICLE -> "an unopened " + lootVehicleName();
            case PINK_SHEEP -> "a Pink Sheep";
            case TRAPPED_ALLAY -> "a trapped Allay";
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

    private boolean allowFamiliarity(PatchesFamiliarity.Category category, PatchesCuriosityPriority priority) {
        var decision = familiarity.evaluate(category, priority, patches.level().getGameTime(), () -> patches.getRandom().nextDouble());
        if (decision.fresh() && decision.skipChance() > 0) report("FAMILIARITY", String.format(java.util.Locale.ROOT,
                "%s score=%.2f skip=%.1f%%: %s for this 10-second window", category, decision.score(),
                decision.skipChance() * 100, decision.allowed() ? "eligible" : "less eager"));
        return decision.allowed();
    }

    private PatchesFamiliarity.Category valuableCategory(BlockPos pos) {
        return switch (valuableKind(pos)) {
            case "emerald" -> PatchesFamiliarity.Category.EMERALD;
            case "ancient_debris" -> PatchesFamiliarity.Category.ANCIENT_DEBRIS;
            default -> PatchesFamiliarity.Category.DIAMOND;
        };
    }

    private PatchesFamiliarity.Category currentCategory() {
        return targetKind == TargetKind.VALUABLE_BLOCK ? valuableCategory(blockTarget)
                : PatchesFamiliarity.Category.valueOf(targetKind.name());
    }

    private void clearExploration() {
        discovery = false; discoveryPlayer = null; leadWaiting = false;
        geode = null; geodeLooks = List.of(); observationPoint = null;
        failedProgress = 0; lastObservationDistance = Double.POSITIVE_INFINITY;
        lastPlayerPosition = null; nextProgressCheck = 0;
        caravanLook = null; nextCaravanLook = 0; caravanLookUntil = 0;
    }

    private boolean chooseGeode() {
        long now = patches.level().getGameTime();
        if (now < nextGeodeScan) return false;
        nextGeodeScan = now + 100;
        BlockPos origin = patches.blockPosition();
        Set<BlockPos> checked = new HashSet<>();
        int structuralChecks = 0;
        for (BlockPos seed : BlockPos.betweenClosed(origin.offset(-7, -3, -7), origin.offset(7, 3, 7))) {
            if (seed.distSqr(origin) > SCAN_RADIUS * SCAN_RADIUS || checked.contains(seed)
                    || !patches.level().hasChunkAt(seed) || !PatchesGeode.isInterior(patches.level().getBlockState(seed))
                    || !canSeeBlock(seed)) continue;
            if (++structuralChecks > 2) break;
            PatchesGeode.Feature feature = PatchesGeode.recognize(seed,
                    pos -> patches.level().hasChunkAt(pos) ? patches.level().getBlockState(pos) : null);
            if (feature == null) continue;
            checked.addAll(feature.interior());
            if (patches.hasRememberedGeode(feature)) continue;
            if (!allowFamiliarity(PatchesFamiliarity.Category.GEODE, PatchesCuriosityPriority.LOW)) return false;
            BlockPos observation = findObservationPoint(seed, 5.0, true);
            if (observation == null) { report("GEODE", "Visible layered feature; no reachable observation point with multiple interior views."); continue; }
            List<BlockPos> looks = geodeVisiblePoints(feature, observation);
            if (looks.size() < 2) continue;
            clearExploration();
            geode = feature; geodeLooks = looks; observationPoint = observation;
            targetKind = TargetKind.GEODE; targetPriority = PatchesCuriosityPriority.LOW;
            blockTarget = seed.immutable(); blockMemoryTarget = feature.min();
            activityStarted = now; nextProgressCheck = now + 20;
            report("GEODE", "One layered feature, " + feature.interior().size() + " inner blocks; "
                    + looks.size() + " look points, observation=" + observation.toShortString());
            return true;
        }
        return false;
    }

    private List<BlockPos> geodeVisiblePoints(PatchesGeode.Feature feature, BlockPos observation) {
        Vec3 eye = Vec3.atBottomCenterOf(observation).add(0, patches.getEyeHeight(), 0);
        List<BlockPos> points = new ArrayList<>();
        for (BlockPos pos : feature.interior()) {
            if (pos.distSqr(observation) > 100 || !canSeeBlockFrom(eye, pos)) continue;
            if (points.stream().anyMatch(other -> other.distSqr(pos) < 4)) continue;
            points.add(pos);
            if (points.size() == 6) break;
        }
        return List.copyOf(points);
    }

    private void tickGeode() {
        long now = patches.level().getGameTime();
        if (now - activityStarted > 900) { report("GEODE", "Observation timed out."); finish(false); return; }
        if (patches.getMode() == PatchesMode.FOLLOWING) {
            Player player = patches.getFollowingPlayer();
            if (player == null || patches.distanceTo(player) >= 8) { report("GEODE", "Player left this Low interest behind."); finish(false); return; }
        }
        if (phase == Phase.APPROACH) {
            lookAt(Vec3.atCenterOf(blockTarget));
            if (patches.distanceToSqr(Vec3.atBottomCenterOf(observationPoint)) <= 1.0) {
                patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = FLOWER_INSPECT_TICKS;
                report("INSPECT", "Looking around the geode interior.");
            } else moveToObservation(now);
            return;
        }
        // Keep the tested Low cadence; only replace its subject gaze with distinct interior points.
        if (phase == Phase.INSPECT || phase == Phase.SHARE_WAIT) {
            List<BlockPos> visible = geodeLooks.stream().filter(pos -> patches.level().hasChunkAt(pos)
                    && PatchesGeode.isInterior(patches.level().getBlockState(pos)) && canSeeBlock(pos)).toList();
            if (visible.size() < 2) { report("GEODE", "Lost the interior view."); finish(false); return; }
            blockTarget = visible.get((int) ((now - activityStarted) / 16 % visible.size()));
        }
        tickLowBlock();
    }

    /** Actual walkable feet position, collision box, reachable path, and sight from that position. */
    private BlockPos findObservationPoint(BlockPos subject, double range, boolean multipleGeodeViews) {
        List<BlockPos> candidates = new ArrayList<>();
        int radius = (int) Math.ceil(range);
        for (BlockPos pos : BlockPos.betweenClosed(subject.offset(-radius, -3, -radius), subject.offset(radius, 3, radius))) {
            Vec3 feet = Vec3.atBottomCenterOf(pos);
            if (feet.distanceToSqr(Vec3.atCenterOf(subject)) > range * range || !patches.level().hasChunkAt(pos)) continue;
            if (WalkNodeEvaluator.getPathTypeStatic(patches, pos) != PathType.WALKABLE) continue;
            if (!patches.level().noCollision(patches, patches.getBoundingBox().move(feet.subtract(patches.position())))) continue;
            if (!canSeeBlockFrom(feet.add(0, patches.getEyeHeight(), 0), subject)) continue;
            candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> patches.distanceToSqr(Vec3.atBottomCenterOf(pos))));
        int attempts = 0;
        for (BlockPos pos : candidates) {
            if (++attempts > 24) break;
            if (multipleGeodeViews) {
                int points = 0;
                Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, patches.getEyeHeight(), 0);
                for (BlockPos nearby : BlockPos.betweenClosed(subject.offset(-3, -3, -3), subject.offset(3, 3, 3))) {
                    if (nearby.distSqr(subject) < 4 || !patches.level().hasChunkAt(nearby)) continue;
                    if (PatchesGeode.isInterior(patches.level().getBlockState(nearby)) && canSeeBlockFrom(eye, nearby)) { points++; break; }
                }
                if (points == 0) continue;
            }
            var path = patches.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return pos;
        }
        return null;
    }

    private boolean canSeeBlockFrom(Vec3 eye, BlockPos pos) {
        BlockHitResult hit = patches.level().clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, patches));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private boolean discoveryReady() {
        Player player = patches.getFollowingPlayer();
        return patches.hasSpyglass() && patches.getMode() == PatchesMode.FOLLOWING && !patches.isSleeping()
                && player != null && player.isAlive() && !player.isSpectator() && patches.distanceTo(player) <= 6.0;
    }

    private record DiscoveryCandidate(BlockPos pos, TargetKind kind, PatchesCuriosityPriority priority) {}

    private boolean chooseDiscoveryTarget() {
        long now = patches.level().getGameTime();
        if (!discoveryReady() || now < nextDiscoveryScan) return false;
        nextDiscoveryScan = now + 100;
        BlockPos origin = patches.blockPosition();
        List<DiscoveryCandidate> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-18, -4, -18), origin.offset(18, 4, 18))) {
            double distance = pos.distSqr(origin);
            if (distance > DISCOVERY_RADIUS * DISCOVERY_RADIUS || !patches.level().hasChunkAt(pos)) continue;
            TargetKind kind; PatchesCuriosityPriority priority;
            if (isValuableBlock(pos) && !patches.hasRememberedValuableCuriosityNear(valuableKind(pos), pos)) {
                kind = TargetKind.VALUABLE_BLOCK; priority = PatchesCuriosityPriority.HIGH;
            } else if (isUnresolvedArchaeology(pos) && !patches.hasRememberedArchaeologyCuriosity(pos)) {
                kind = TargetKind.ARCHAEOLOGY; priority = PatchesCuriosityPriority.MEDIUM;
            } else if (isUnresolvedLootContainer(pos) && !patches.hasRememberedLootContainerCuriosity(canonicalLootContainerPos(pos))) {
                kind = TargetKind.LOOT_CONTAINER; priority = PatchesCuriosityPriority.MEDIUM;
            } else continue;
            if (!canSeeBlock(pos)) continue;
            candidates.add(new DiscoveryCandidate(pos.immutable(), kind, priority));
        }
        candidates.sort(Comparator.<DiscoveryCandidate>comparingInt(candidate -> -candidate.priority().ordinal())
                .thenComparingDouble(candidate -> candidate.pos().distSqr(origin)));
        int attempts = 0;
        for (DiscoveryCandidate candidate : candidates) {
            var category = candidate.kind() == TargetKind.VALUABLE_BLOCK ? valuableCategory(candidate.pos())
                    : PatchesFamiliarity.Category.valueOf(candidate.kind().name());
            if (!allowFamiliarity(category, candidate.priority())) continue;
            if (++attempts > 6) break;
            BlockPos observation = findObservationPoint(candidate.pos(), 1.9, false);
            if (observation == null) continue;
            switch (candidate.kind()) {
                case VALUABLE_BLOCK -> selectValuableBlock(candidate.pos());
                case ARCHAEOLOGY -> selectArchaeology(candidate.pos());
                case LOOT_CONTAINER -> selectLootContainer(candidate.pos());
                default -> throw new IllegalStateException("Unapproved Discovery target");
            }
            startDiscovery(observation, now);
            return true;
        }
        List<Entity> vehicles = patches.level().getEntitiesOfClass(Entity.class,
                patches.getBoundingBox().inflate(DISCOVERY_RADIUS, 4, DISCOVERY_RADIUS), entity -> entity.isAlive()
                        && hasUnresolvedVehicleLoot(entity) && !patches.hasRememberedLootVehicleCuriosity(entity.getUUID())
                        && patches.distanceToSqr(entity) <= DISCOVERY_RADIUS * DISCOVERY_RADIUS && patches.hasLineOfSight(entity));
        vehicles.sort(Comparator.comparingDouble(patches::distanceToSqr));
        attempts = 0;
        for (Entity vehicle : vehicles) {
            if (++attempts > 4) break;
            if (!allowFamiliarity(PatchesFamiliarity.Category.LOOT_VEHICLE, PatchesCuriosityPriority.MEDIUM)) break;
            var path = patches.getNavigation().createPath(vehicle, 1);
            if (path == null || !path.canReach()) continue;
            selectLootVehicle(vehicle);
            startDiscovery(vehicle.blockPosition(), now);
            return true;
        }
        return false;
    }

    private void startDiscovery(BlockPos observation, long now) {
        clearExploration();
        discovery = true; discoveryPlayer = patches.getFollowingPlayer(); observationPoint = observation;
        activityStarted = now; nextProgressCheck = now + 20;
        lastPlayerPosition = discoveryPlayer.position();
        report("DISCOVERY", "Leading to " + targetName() + "; pause at 8 blocks, resume within 4.5; Follow safety unchanged.");
    }

    private boolean validateDiscovery() {
        long now = patches.level().getGameTime();
        String reason = null;
        if (!patches.hasSpyglass() || patches.getMode() != PatchesMode.FOLLOWING || patches.isSleeping()) reason = "equipment or command changed";
        else if (discoveryPlayer == null || patches.getFollowingPlayer() != discoveryPlayer || !discoveryPlayer.isAlive()
                || discoveryPlayer.isSpectator() || discoveryPlayer.level() != patches.level()) reason = "player unavailable";
        else if (patches.hurtTime > 0) reason = "hurt interrupts exploration";
        else if (patches.distanceTo(discoveryPlayer) >= 12) reason = "Follow catch-up distance reached";
        else if (now - activityStarted > 1200) reason = "exploration time budget exhausted";
        else if (patches.tickCount % 20 == 0) {
            boolean visible = targetKind == TargetKind.LOOT_VEHICLE ? lootVehicleTarget != null && patches.hasLineOfSight(lootVehicleTarget)
                    : blockTarget != null && patches.level().hasChunkAt(blockTarget) && canSeeBlock(blockTarget);
            if (!visible) reason = "discovery no longer visible";
            if (lastPlayerPosition != null && patches.distanceTo(discoveryPlayer) > 6) {
                Vec3 movement = discoveryPlayer.position().subtract(lastPlayerPosition);
                Vec3 away = discoveryPlayer.position().subtract(patches.position()).normalize();
                if (movement.dot(away) > 0.7) reason = "player is leaving the lead";
            }
            lastPlayerPosition = discoveryPlayer.position();
        }
        if (reason != null) { report("DISCOVERY STOP", reason + "; target remains unremembered."); finish(false); return false; }
        return true;
    }

    private void tickDiscoveryLead() {
        long now = patches.level().getGameTime();
        double separation = patches.distanceTo(discoveryPlayer);
        if (!leadWaiting && separation >= 8) {
            leadWaiting = true; leadWaitStarted = now; beckonCycleTicks = 0;
            patches.getNavigation().stop(); report("DISCOVERY WAIT", "Waiting for player to join the lead.");
        }
        if (leadWaiting) {
            patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED);
            if (separation <= 4.5) {
                leadWaiting = false; failedProgress = 0; lastObservationDistance = Double.POSITIVE_INFINITY;
                nextProgressCheck = now + 20; report("DISCOVERY LEAD", "Player joined; continuing.");
            } else {
                int cycle = beckonCycleTicks++ % 60;
                if (cycle < 38) {
                    patches.getLookControl().setLookAt(discoveryPlayer, 30, patches.getMaxHeadXRot());
                    if ((cycle == 4 || cycle == 18) && patches.onGround()) {
                        Vec3 motion = patches.getDeltaMovement(); patches.setDeltaMovement(motion.x, 0.34, motion.z);
                    }
                } else lookAt(discoverySubject());
                if (now - leadWaitStarted >= 200) { report("DISCOVERY STOP", "Player did not join after 10 seconds."); finish(false); }
                return;
            }
        }
        patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(discoverySubject());
        boolean reached = targetKind == TargetKind.LOOT_VEHICLE ? patches.distanceTo(lootVehicleTarget) <= AXOLOTL_COMFORT_DISTANCE
                : patches.distanceToSqr(Vec3.atBottomCenterOf(observationPoint)) <= 0.64;
        if (reached) {
            patches.getNavigation().stop(); phase = Phase.INSPECT;
            phaseTicks = targetPriority == PatchesCuriosityPriority.HIGH ? VALUABLE_INSPECT_TICKS : AXOLOTL_INSPECT_TICKS;
            report("DISCOVERY INSPECT", "Arrived; using the existing " + targetPriority + " inspection/share routine.");
            return;
        }
        if (targetKind == TargetKind.LOOT_VEHICLE) observationPoint = lootVehicleTarget.blockPosition();
        moveToObservation(now);
    }

    private Vec3 discoverySubject() {
        return targetKind == TargetKind.LOOT_VEHICLE ? lootVehicleTarget.position().add(0, lootVehicleTarget.getBbHeight() * 0.5, 0)
                : Vec3.atCenterOf(blockTarget);
    }

    private void moveToObservation(long now) {
        if (now < nextProgressCheck) return;
        nextProgressCheck = now + 20;
        Vec3 feet = Vec3.atBottomCenterOf(observationPoint);
        double distance = patches.position().distanceTo(feet);
        boolean started = patches.getNavigation().moveTo(feet.x, feet.y, feet.z, APPROACH_SPEED);
        if (!started || lastObservationDistance - distance < 0.1) failedProgress++;
        else failedProgress = 0;
        lastObservationDistance = distance;
        if (failedProgress >= 4) { report(discovery ? "DISCOVERY STOP" : "GEODE", "Four failed/no-progress path checks; giving up cleanly."); finish(false); }
    }

    private enum TargetKind { GEODE, FLOWER, LOW_BLOCK, AXOLOTL, BLUE_AXOLOTL, PINK_SHEEP, TRAPPED_ALLAY, WANDERING_TRADER, SNIFFER, ARCHAEOLOGY, LOOT_CONTAINER, LOOT_VEHICLE, VALUABLE_BLOCK }
    private enum Phase { IDLE, NOTICE, APPROACH, INSPECT, SHARE_WAIT, PLAYER_INVITE, BECKON, SHARE_REACTION }
}
