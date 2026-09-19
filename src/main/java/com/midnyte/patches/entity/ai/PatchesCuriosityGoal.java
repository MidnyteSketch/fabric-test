package com.midnyte.patches.entity.ai;

import com.midnyte.patches.entity.PatchesEntity;
import com.midnyte.patches.entity.PatchesExpression;
import com.midnyte.patches.entity.PatchesMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
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
    private static final int DIAMOND_INSPECT_TICKS = 50;
    private static final int DIAMOND_BECKON_TICKS = 20 * 14;
    private static final int SHARE_REACTION_TICKS = 60;
    private static final double SCAN_RADIUS = 7.0;
    private static final double FLOWER_APPROACH_DISTANCE = 1.75;
    private static final double LOW_BLOCK_HORIZONTAL_APPROACH_DISTANCE = 1.75;
    private static final double LOW_BLOCK_MAX_VERTICAL_LOOK_DISTANCE = 4.0;
    private static final double DIAMOND_APPROACH_DISTANCE = 2.0;
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
    private Axolotl axolotlTarget;
    private WanderingTrader wanderingTraderTarget;
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int cooldownTicks;
    private int scanTicks;
    private int beckonCycleTicks;
    private int beckonHops;

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
            case WANDERING_TRADER -> tickWanderingTrader();
            case DIAMOND -> tickDiamond();
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

    private void tickDiamond() {
        Vec3 center = Vec3.atCenterOf(blockTarget);
        switch (phase) {
            case NOTICE -> { patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center); if (--phaseTicks <= 0) enterApproach(); }
            case APPROACH -> {
                patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (Math.sqrt(patches.distanceToSqr(center)) <= DIAMOND_APPROACH_DISTANCE) {
                    patches.getNavigation().stop(); phase = Phase.INSPECT; phaseTicks = DIAMOND_INSPECT_TICKS;
                    report("INSPECT", "Reached Diamond Ore; excitedly checking the find.");
                } else if (patches.getNavigation().isDone() || patches.tickCount % 10 == 0) patches.getNavigation().moveTo(center.x, center.y, center.z, APPROACH_SPEED);
            }
            case INSPECT -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); lookAt(center);
                if (--phaseTicks <= 0) { phase = Phase.BECKON; phaseTicks = DIAMOND_BECKON_TICKS; beckonCycleTicks = 0; beckonHops = 0; report("BECKON", "This is special; actively calling the player over."); }
            }
            case BECKON -> tickDiamondBeckon(center);
            case SHARE_REACTION -> {
                patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.LAUGH_TONGUE);
                // Own both body-facing rotations during the celebration so normal mob controls do not cancel the spin.
                float celebrationYaw = patches.getYRot() + 24.0F;
                patches.setYRot(celebrationYaw);
                patches.yBodyRot = celebrationYaw;
                patches.yHeadRot = celebrationYaw;
                patches.yBodyRotO = celebrationYaw;
                patches.yHeadRotO = celebrationYaw;
                if (--phaseTicks <= 0) { report("COMPLETE", "Finished celebrating the Diamond discovery."); finish(true); }
            }
            default -> { }
        }
    }

    private void tickDiamondBeckon(Vec3 diamondCenter) {
        patches.getNavigation().stop();
        patches.setActivityExpression(PatchesExpression.SURPRISED);
        Player player = relevantPlayer();
        if (player != null && patches.distanceTo(player) <= SHARE_PLAYER_DISTANCE) {
            phase = Phase.SHARE_REACTION; phaseTicks = SHARE_REACTION_TICKS;
            report("SHARE REACTION", "Player arrived; celebrating the Diamond find."); return;
        }

        // 60-tick loop: face player and make two short hops, pause, glance back at the diamond, repeat.
        int cycle = beckonCycleTicks++ % 60;
        if (cycle < 38 && player != null) {
            patches.getLookControl().setLookAt(player, 30.0F, patches.getMaxHeadXRot());
            if ((cycle == 4 || cycle == 18) && patches.onGround() && beckonHops < 2) {
                Vec3 motion = patches.getDeltaMovement();
                patches.setDeltaMovement(motion.x, 0.34, motion.z);
                beckonHops++;
            }
        } else {
            lookAt(diamondCenter);
            if (cycle == 59) beckonHops = 0;
        }

        if (--phaseTicks <= 0) {
            report("COMPLETE", "Player did not come over; remembered the important Diamond discovery.");
            finish(true);
        }
    }

    @Override public void stop() { if (phase != Phase.IDLE) finish(false); }

    private boolean chooseBestNearbyTarget() {
        BlockPos diamond = findNearbyDiamond();
        if (diamond != null) { selectDiamond(diamond); return true; }
        Axolotl axolotl = findNearbyAxolotl();
        if (axolotl != null) { selectAxolotl(axolotl); return true; }
        WanderingTrader trader = findNearbyWanderingTrader();
        if (trader != null) { selectWanderingTrader(trader); return true; }
        BlockPos flower = findNearbyFlower();
        if (flower != null) { selectFlower(flower); return true; }
        BlockPos lowBlock = findNearbyLowBlock();
        if (lowBlock != null) { selectLowBlock(lowBlock); return true; }
        return false;
    }

    private boolean tryUpgradeTarget() {
        if (targetPriority != PatchesCuriosityPriority.HIGH) {
            BlockPos diamond = findNearbyDiamond();
            if (diamond != null) { report("PRIORITY", "A Diamond discovery outranks the current curiosity; switching targets."); selectDiamond(diamond); beginNotice(); return true; }
        }
        if (targetPriority == PatchesCuriosityPriority.LOW) {
            Axolotl axolotl = findNearbyAxolotl();
            if (axolotl != null) { report("PRIORITY", "An Axolotl is more interesting than the current low curiosity; switching targets."); selectAxolotl(axolotl); beginNotice(); return true; }
            WanderingTrader trader = findNearbyWanderingTrader();
            if (trader != null) { report("PRIORITY", "A Wandering Trader is more interesting than the current low curiosity; switching targets."); selectWanderingTrader(trader); beginNotice(); return true; }
        }
        return false;
    }

    private void selectFlower(BlockPos flower) { targetKind = TargetKind.FLOWER; targetPriority = PatchesCuriosityPriority.LOW; blockTarget = flower.immutable(); axolotlTarget = null; }
    private void selectLowBlock(BlockPos pos) { targetKind = TargetKind.LOW_BLOCK; targetPriority = PatchesCuriosityPriority.LOW; blockTarget = pos.immutable(); axolotlTarget = null; }
    private void selectAxolotl(Axolotl axolotl) { targetKind = TargetKind.AXOLOTL; targetPriority = PatchesCuriosityPriority.MEDIUM; axolotlTarget = axolotl; wanderingTraderTarget = null; blockTarget = null; }
    private void selectWanderingTrader(WanderingTrader trader) { targetKind = TargetKind.WANDERING_TRADER; targetPriority = PatchesCuriosityPriority.MEDIUM; wanderingTraderTarget = trader; axolotlTarget = null; blockTarget = null; }
    private void selectDiamond(BlockPos diamond) { targetKind = TargetKind.DIAMOND; targetPriority = PatchesCuriosityPriority.HIGH; blockTarget = diamond.immutable(); axolotlTarget = null; }

    private void beginNotice() { phase = Phase.NOTICE; phaseTicks = 8; patches.getNavigation().stop(); patches.setActivityExpression(PatchesExpression.SURPRISED); report("NOTICE", "Spotted " + targetName() + " (" + targetPriority + ")."); }
    private void enterApproach() { phase = Phase.APPROACH; report("APPROACH", "Going over to investigate " + targetName() + "."); }

    private void finish(boolean remember) {
        if (remember) {
            if (targetKind == TargetKind.FLOWER && blockTarget != null) patches.rememberFlowerCuriosity(blockTarget);
            if (targetKind == TargetKind.LOW_BLOCK && blockTarget != null) patches.rememberLowBlockCuriosity(blockTarget);
            if (targetKind == TargetKind.AXOLOTL && axolotlTarget != null) patches.rememberAxolotlCuriosity(axolotlTarget.getUUID());
            if (targetKind == TargetKind.WANDERING_TRADER && wanderingTraderTarget != null) patches.rememberWanderingTraderCuriosity(wanderingTraderTarget.getUUID());
            if (targetKind == TargetKind.DIAMOND && blockTarget != null) patches.rememberDiamondCuriosity(blockTarget);
        }
        patches.getNavigation().stop(); patches.clearActivityExpression(); targetKind = null; targetPriority = null; blockTarget = null; axolotlTarget = null; wanderingTraderTarget = null;
        phase = Phase.IDLE; phaseTicks = 0; cooldownTicks = GENERAL_COOLDOWN_TICKS; beckonCycleTicks = 0; beckonHops = 0;
    }

    private boolean targetStillValid() {
        if (targetKind == TargetKind.FLOWER) return blockTarget != null && patches.level().getBlockState(blockTarget).is(BlockTags.FLOWERS);
        if (targetKind == TargetKind.LOW_BLOCK) return blockTarget != null && isLowCuriosityBlock(blockTarget);
        if (targetKind == TargetKind.DIAMOND) return blockTarget != null && isDiamondOre(blockTarget);
        if (targetKind == TargetKind.WANDERING_TRADER) return wanderingTraderTarget != null && wanderingTraderTarget.isAlive() && !wanderingTraderTarget.isRemoved() && wanderingTraderTarget.level() == patches.level();
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

    private Axolotl findNearbyAxolotl() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(Axolotl.class, search, axolotl -> axolotl.isAlive() && !patches.hasRememberedAxolotlCuriosity(axolotl.getUUID())).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private WanderingTrader findNearbyWanderingTrader() {
        AABB search = patches.getBoundingBox().inflate(SCAN_RADIUS, 3.0, SCAN_RADIUS);
        return patches.level().getEntitiesOfClass(WanderingTrader.class, search, trader -> trader.isAlive() && !patches.hasRememberedWanderingTraderCuriosity(trader.getUUID()) && patches.hasLineOfSight(trader)).stream().min(Comparator.comparingDouble(patches::distanceToSqr)).orElse(null);
    }

    private BlockPos findNearbyFlower() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!patches.level().getBlockState(pos).is(BlockTags.FLOWERS) || !canSeeBlock(pos)) continue;
            BlockPos curiosityPos = canonicalLowBlockPos(pos);
            if (patches.hasRememberedFlowerCuriosity(curiosityPos)) continue;
            double distance = curiosityPos.distSqr(origin); if (distance > SCAN_RADIUS * SCAN_RADIUS || distance >= bestDistance) continue;
            best = curiosityPos; bestDistance = distance;
        }
        return best;
    }

    private BlockPos findNearbyDiamond() {
        BlockPos origin = patches.blockPosition(); int radius = (int)Math.ceil(SCAN_RADIUS); BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius))) {
            if (!isDiamondOre(pos) || patches.hasRememberedDiamondCuriosityNear(pos) || !canSeeBlock(pos)) continue;
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

    private boolean isDiamondOre(BlockPos pos) { return patches.level().getBlockState(pos).is(Blocks.DIAMOND_ORE) || patches.level().getBlockState(pos).is(Blocks.DEEPSLATE_DIAMOND_ORE); }
    private boolean followDistanceIsUrgent() { if (patches.getMode() != PatchesMode.FOLLOWING) return false; Player player = patches.getFollowingPlayer(); return player != null && patches.distanceTo(player) >= HURRY_INTERRUPT_DISTANCE; }
    private Player relevantPlayer() { Player followed = patches.getFollowingPlayer(); if (followed != null) return followed; return patches.level().getNearestPlayer(patches, 12.0); }
    private void lookAt(Vec3 target) { patches.getLookControl().setLookAt(target.x, target.y, target.z, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtAxolotl() { patches.getLookControl().setLookAt(axolotlTarget, 20.0F, patches.getMaxHeadXRot()); }
    private void lookAtWanderingTrader() { patches.getLookControl().setLookAt(wanderingTraderTarget, 20.0F, patches.getMaxHeadXRot()); }
    private String targetName() {
        return switch (targetKind) {
            case AXOLOTL -> "an Axolotl";
            case WANDERING_TRADER -> "a Wandering Trader";
            case DIAMOND -> "Diamond Ore";
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
    private void report(String state, String detail) { if (!DEBUG_CURIOSITY) return; Player player = relevantPlayer(); if (player != null) player.sendSystemMessage(Component.literal("[Patches] CURIOSITY: " + state + " — " + detail)); }

    private enum TargetKind { FLOWER, LOW_BLOCK, AXOLOTL, WANDERING_TRADER, DIAMOND }
    private enum Phase { IDLE, NOTICE, APPROACH, INSPECT, SHARE_WAIT, PLAYER_INVITE, BECKON, SHARE_REACTION }
}
