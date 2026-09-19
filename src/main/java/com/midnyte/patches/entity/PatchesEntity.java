package com.midnyte.patches.entity;

import com.midnyte.patches.entity.ai.PatchesCuriosityGoal;
import com.midnyte.patches.entity.ai.PatchesFollowGoal;
import com.midnyte.patches.entity.ai.PatchesTemptGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PatchesEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> BUNDLE = SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> SPYGLASS = SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.INT);
    private static final int COOKIE_LAUGH_TICKS = 20;
    private static final int OTHER_FOOD_TICKS = 12;
    private static final int HURT_FACE_TICKS = 12;
    private static final double COOKIE_NOTICE_RANGE = 10.0;
    private static final int VALUABLE_MEMORY_RADIUS = 5;

    private PatchesMode modeBeforeSitting = PatchesMode.WANDERING;
    private @Nullable UUID followingPlayerUuid;
    private int expressionOverrideTicks;
    private final Set<BlockPos> rememberedFlowerCuriosities = new HashSet<>();
    private final Set<BlockPos> rememberedLowBlockCuriosities = new HashSet<>();
    private final Set<UUID> rememberedAxolotlCuriosities = new HashSet<>();
    private final Set<UUID> rememberedWanderingTraderCuriosities = new HashSet<>();
    private final Set<UUID> rememberedSnifferCuriosities = new HashSet<>();
    private final Set<BlockPos> rememberedArchaeologyCuriosities = new HashSet<>();
    private final Set<BlockPos> rememberedDiamondCuriosities = new HashSet<>();
    private final Set<BlockPos> rememberedEmeraldCuriosities = new HashSet<>();
    private final Set<BlockPos> rememberedAncientDebrisCuriosities = new HashSet<>();

    public PatchesEntity(EntityType<? extends PatchesEntity> entityType, Level level) { super(entityType, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.TEMPT_RANGE, 10.0).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PatchesCuriosityGoal(this));
        this.goalSelector.addGoal(2, new PatchesFollowGoal(this));
        this.goalSelector.addGoal(3, new PatchesTemptGoal(this, 1.0, Ingredient.of(Items.COOKIE), false));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(MODE, PatchesMode.WANDERING.id()); builder.define(BUNDLE, ItemStack.EMPTY); builder.define(SPYGLASS, ItemStack.EMPTY); builder.define(EXPRESSION, PatchesExpression.DEFAULT.id());
    }

    public PatchesMode getMode() { return PatchesMode.fromId(this.entityData.get(MODE)); }
    public void setMode(PatchesMode mode) { this.entityData.set(MODE, mode.id()); if (mode == PatchesMode.SITTING) { this.getNavigation().stop(); this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0); } }
    public PatchesExpression getExpression() { return PatchesExpression.fromId(this.entityData.get(EXPRESSION)); }
    private void setExpression(PatchesExpression expression) { this.entityData.set(EXPRESSION, expression.id()); }
    private void setTimedExpression(PatchesExpression expression, int ticks) { setExpression(expression); expressionOverrideTicks = ticks; }
    public void setActivityExpression(PatchesExpression expression) { setExpression(expression); expressionOverrideTicks = 2; }
    public void clearActivityExpression() { expressionOverrideTicks = 0; setExpression(PatchesExpression.DEFAULT); }

    public void rememberFlowerCuriosity(BlockPos pos) { rememberedFlowerCuriosities.add(pos.immutable()); }
    public boolean hasRememberedFlowerCuriosity(BlockPos pos) { return rememberedFlowerCuriosities.contains(pos); }
    public void rememberLowBlockCuriosity(BlockPos pos) { rememberedLowBlockCuriosities.add(pos.immutable()); }
    public boolean hasRememberedLowBlockCuriosity(BlockPos pos) { return rememberedLowBlockCuriosities.contains(pos); }
    public void rememberAxolotlCuriosity(UUID uuid) { rememberedAxolotlCuriosities.add(uuid); }
    public boolean hasRememberedAxolotlCuriosity(UUID uuid) { return rememberedAxolotlCuriosities.contains(uuid); }
    public void rememberWanderingTraderCuriosity(UUID uuid) { rememberedWanderingTraderCuriosities.add(uuid); }
    public boolean hasRememberedWanderingTraderCuriosity(UUID uuid) { return rememberedWanderingTraderCuriosities.contains(uuid); }
    public void rememberSnifferCuriosity(UUID uuid) { rememberedSnifferCuriosities.add(uuid); }
    public boolean hasRememberedSnifferCuriosity(UUID uuid) { return rememberedSnifferCuriosities.contains(uuid); }
    public void rememberArchaeologyCuriosity(BlockPos pos) { rememberedArchaeologyCuriosities.add(pos.immutable()); }
    public boolean hasRememberedArchaeologyCuriosity(BlockPos pos) { return rememberedArchaeologyCuriosities.contains(pos); }
    public void rememberValuableCuriosity(String kind, BlockPos pos) { valuableMemory(kind).add(pos.immutable()); }
    public boolean hasRememberedValuableCuriosityNear(String kind, BlockPos pos) {
        int radiusSq = VALUABLE_MEMORY_RADIUS * VALUABLE_MEMORY_RADIUS;
        for (BlockPos remembered : valuableMemory(kind)) if (remembered.distSqr(pos) <= radiusSq) return true;
        return false;
    }
    private Set<BlockPos> valuableMemory(String kind) {
        return switch (kind) {
            case "emerald" -> rememberedEmeraldCuriosities;
            case "ancient_debris" -> rememberedAncientDebrisCuriosities;
            default -> rememberedDiamondCuriosities;
        };
    }

    public ItemStack getBundleStack() { return this.entityData.get(BUNDLE); }
    public boolean hasBundle() { return !getBundleStack().isEmpty(); }
    private void setBundleStack(ItemStack stack) { if (!stack.isEmpty() && !BundleSupport.isBundle(stack)) throw new IllegalArgumentException("Patches can only equip a Bundle"); this.entityData.set(BUNDLE, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)); }
    public ItemStack getSpyglassStack() { return this.entityData.get(SPYGLASS); }
    public boolean hasSpyglass() { return !getSpyglassStack().isEmpty(); }
    private void setSpyglassStack(ItemStack stack) { if (!stack.isEmpty() && !stack.is(Items.SPYGLASS)) throw new IllegalArgumentException("Patches can only equip a Spyglass in his discovery-tool slot"); this.entityData.set(SPYGLASS, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)); }

    public @Nullable Player getFollowingPlayer() { if (followingPlayerUuid == null) return null; if (!(level() instanceof ServerLevel serverLevel)) return null; return serverLevel.getPlayerByUUID(followingPlayerUuid); }
    private void setFollowingPlayer(Player player) { this.followingPlayerUuid = player.getUUID(); }

    @Override public void tick() { super.tick(); if (!level().isClientSide()) { if (getMode() == PatchesMode.SITTING) { this.getNavigation().stop(); this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0); } updateExpression(); } }
    private void updateExpression() { if (expressionOverrideTicks > 0) { expressionOverrideTicks--; return; } if (getMode() == PatchesMode.SITTING) { setExpression(PatchesExpression.RESTING); return; } Player temptingPlayer = level().getNearestPlayer(this, COOKIE_NOTICE_RANGE); if (temptingPlayer != null && playerIsHoldingCookie(temptingPlayer)) { setExpression(PatchesExpression.SURPRISED); return; } setExpression(PatchesExpression.DEFAULT); }
    private static boolean playerIsHoldingCookie(Player player) { return player.getMainHandItem().is(Items.COOKIE) || player.getOffhandItem().is(Items.COOKIE); }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { boolean damaged = super.hurtServer(level, source, amount); if (damaged) setTimedExpression(PatchesExpression.HURT, HURT_FACE_TICKS); return damaged; }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) { super.dropCustomDeathLoot(level, source, killedByPlayer); ItemStack bundle = getBundleStack().copy(); if (!bundle.isEmpty()) { setBundleStack(ItemStack.EMPTY); spawnEquippedDrop(level, bundle); } ItemStack spyglass = getSpyglassStack().copy(); if (!spyglass.isEmpty()) { setSpyglassStack(ItemStack.EMPTY); spawnEquippedDrop(level, spyglass); } }
    private void spawnEquippedDrop(ServerLevel level, ItemStack stack) { ItemEntity dropped = new ItemEntity(level, getX(), getY() + 0.5, getZ(), stack); dropped.setDefaultPickUpDelay(); level.addFreshEntity(dropped); }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (BundleSupport.isBundle(stack)) { if (hasBundle()) return InteractionResult.FAIL; if (!level().isClientSide()) { setBundleStack(stack.copyWithCount(1)); if (!player.hasInfiniteMaterials()) stack.shrink(1); } return InteractionResult.SUCCESS; }
        if (stack.is(Items.SPYGLASS)) { if (hasSpyglass()) return InteractionResult.FAIL; if (!level().isClientSide()) { setSpyglassStack(stack.copyWithCount(1)); if (!player.hasInfiniteMaterials()) stack.shrink(1); } return InteractionResult.SUCCESS; }
        if (stack.isEmpty() && player.isShiftKeyDown()) { if (hasSpyglass()) { if (!level().isClientSide()) { ItemStack equipped = getSpyglassStack().copy(); setSpyglassStack(ItemStack.EMPTY); returnOrDrop(player, equipped); } return InteractionResult.SUCCESS; } if (hasBundle()) { if (!level().isClientSide()) { ItemStack equipped = getBundleStack().copy(); setBundleStack(ItemStack.EMPTY); returnOrDrop(player, equipped); } return InteractionResult.SUCCESS; } }
        if (stack.is(Items.COOKIE)) { if (!level().isClientSide()) { setFollowingPlayer(player); if (getMode() == PatchesMode.WANDERING) setMode(PatchesMode.FOLLOWING); else if (getMode() == PatchesMode.FOLLOWING) setMode(PatchesMode.WANDERING); heal(2.0F); consumeOne(player, stack); setTimedExpression(PatchesExpression.LAUGH, COOKIE_LAUGH_TICKS); playSound(SoundEvents.GENERIC_EAT.value(), 0.7F, 1.15F); } return InteractionResult.SUCCESS; }
        if (stack.isEmpty()) { if (!level().isClientSide()) { if (getMode() == PatchesMode.SITTING) setMode(modeBeforeSitting); else { modeBeforeSitting = getMode(); setMode(PatchesMode.SITTING); } } return InteractionResult.SUCCESS; }
        if (isLikedFood(stack) && getHealth() < getMaxHealth()) { if (!level().isClientSide()) { float healing = stack.is(Items.MUSHROOM_STEW) ? 6.0F : stack.is(Items.APPLE) ? 3.0F : 2.0F; heal(healing); boolean stew = stack.is(Items.MUSHROOM_STEW); consumeOne(player, stack); setTimedExpression(PatchesExpression.MOUTH_OPEN, OTHER_FOOD_TICKS); if (stew && !player.hasInfiniteMaterials()) returnOrDrop(player, new ItemStack(Items.BOWL)); playSound(SoundEvents.GENERIC_EAT.value(), 0.7F, 1.05F); } return InteractionResult.SUCCESS; }
        if (isRejectedFood(stack)) return InteractionResult.FAIL;
        return super.mobInteract(player, hand);
    }

    private void returnOrDrop(Player player, ItemStack stack) { if (player.addItem(stack)) return; ItemEntity dropped = new ItemEntity(level(), player.getX(), player.getY() + 0.5, player.getZ(), stack); dropped.setDefaultPickUpDelay(); level().addFreshEntity(dropped); }
    private static boolean isLikedFood(ItemStack stack) { return stack.is(Items.APPLE) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.MUSHROOM_STEW); }
    private static boolean isRejectedFood(ItemStack stack) { return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.CHICKEN) || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.RABBIT); }
    private static void consumeOne(Player player, ItemStack stack) { if (!player.hasInfiniteMaterials()) stack.shrink(1); }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    private String encodeBlockPositions(Set<BlockPos> positions) { StringBuilder encoded = new StringBuilder(); for (BlockPos pos : positions) { if (!encoded.isEmpty()) encoded.append(';'); encoded.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()); } return encoded.toString(); }
    private void decodeBlockPositions(String encoded, Set<BlockPos> destination) { destination.clear(); if (encoded.isEmpty()) return; for (String entry : encoded.split(";")) { String[] xyz = entry.split(","); if (xyz.length != 3) continue; try { destination.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]))); } catch (NumberFormatException ignored) { } } }
    private String encodeAxolotlMemory() { StringBuilder encoded = new StringBuilder(); for (UUID uuid : rememberedAxolotlCuriosities) { if (!encoded.isEmpty()) encoded.append(';'); encoded.append(uuid); } return encoded.toString(); }
    private void decodeAxolotlMemory(String encoded) { rememberedAxolotlCuriosities.clear(); if (encoded.isEmpty()) return; for (String entry : encoded.split(";")) { try { rememberedAxolotlCuriosities.add(UUID.fromString(entry)); } catch (IllegalArgumentException ignored) { } } }
    private String encodeWanderingTraderMemory() { StringBuilder encoded = new StringBuilder(); for (UUID uuid : rememberedWanderingTraderCuriosities) { if (!encoded.isEmpty()) encoded.append(';'); encoded.append(uuid); } return encoded.toString(); }
    private void decodeWanderingTraderMemory(String encoded) { rememberedWanderingTraderCuriosities.clear(); if (encoded.isEmpty()) return; for (String entry : encoded.split(";")) { try { rememberedWanderingTraderCuriosities.add(UUID.fromString(entry)); } catch (IllegalArgumentException ignored) { } } }
    private String encodeSnifferMemory() { StringBuilder encoded = new StringBuilder(); for (UUID uuid : rememberedSnifferCuriosities) { if (!encoded.isEmpty()) encoded.append(';'); encoded.append(uuid); } return encoded.toString(); }
    private void decodeSnifferMemory(String encoded) { rememberedSnifferCuriosities.clear(); if (encoded.isEmpty()) return; for (String entry : encoded.split(";")) { try { rememberedSnifferCuriosities.add(UUID.fromString(entry)); } catch (IllegalArgumentException ignored) { } } }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putInt("PatchesMode", getMode().id()); output.putInt("PatchesModeBeforeSitting", modeBeforeSitting.id()); output.storeNullable("PatchesFollowingPlayer", UUIDUtil.CODEC, followingPlayerUuid);
        ItemStack bundle = getBundleStack(); if (!bundle.isEmpty()) output.store("PatchesBundle", ItemStack.CODEC, bundle);
        ItemStack spyglass = getSpyglassStack(); if (!spyglass.isEmpty()) output.store("PatchesSpyglass", ItemStack.CODEC, spyglass);
        output.putString("PatchesRememberedFlowers", encodeBlockPositions(rememberedFlowerCuriosities));
        output.putString("PatchesRememberedLowBlocks", encodeBlockPositions(rememberedLowBlockCuriosities));
        output.putString("PatchesRememberedAxolotls", encodeAxolotlMemory());
        output.putString("PatchesRememberedWanderingTraders", encodeWanderingTraderMemory());
        output.putString("PatchesRememberedSniffers", encodeSnifferMemory());
        output.putString("PatchesRememberedArchaeology", encodeBlockPositions(rememberedArchaeologyCuriosities));
        output.putString("PatchesRememberedDiamonds", encodeBlockPositions(rememberedDiamondCuriosities));
        output.putString("PatchesRememberedEmeralds", encodeBlockPositions(rememberedEmeraldCuriosities));
        output.putString("PatchesRememberedAncientDebris", encodeBlockPositions(rememberedAncientDebrisCuriosities));
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); setMode(PatchesMode.fromId(input.getIntOr("PatchesMode", PatchesMode.WANDERING.id()))); modeBeforeSitting = PatchesMode.fromId(input.getIntOr("PatchesModeBeforeSitting", PatchesMode.WANDERING.id())); followingPlayerUuid = input.read("PatchesFollowingPlayer", UUIDUtil.CODEC).orElse(null);
        ItemStack savedBundle = input.read("PatchesBundle", ItemStack.CODEC).orElse(ItemStack.EMPTY); setBundleStack(savedBundle.isEmpty() || BundleSupport.isBundle(savedBundle) ? savedBundle : ItemStack.EMPTY);
        ItemStack savedSpyglass = input.read("PatchesSpyglass", ItemStack.CODEC).orElse(ItemStack.EMPTY); setSpyglassStack(savedSpyglass.isEmpty() || savedSpyglass.is(Items.SPYGLASS) ? savedSpyglass : ItemStack.EMPTY);
        decodeBlockPositions(input.getStringOr("PatchesRememberedFlowers", ""), rememberedFlowerCuriosities);
        decodeBlockPositions(input.getStringOr("PatchesRememberedLowBlocks", ""), rememberedLowBlockCuriosities);
        decodeAxolotlMemory(input.getStringOr("PatchesRememberedAxolotls", ""));
        decodeWanderingTraderMemory(input.getStringOr("PatchesRememberedWanderingTraders", ""));
        decodeSnifferMemory(input.getStringOr("PatchesRememberedSniffers", ""));
        decodeBlockPositions(input.getStringOr("PatchesRememberedArchaeology", ""), rememberedArchaeologyCuriosities);
        decodeBlockPositions(input.getStringOr("PatchesRememberedDiamonds", ""), rememberedDiamondCuriosities);
        decodeBlockPositions(input.getStringOr("PatchesRememberedEmeralds", ""), rememberedEmeraldCuriosities);
        decodeBlockPositions(input.getStringOr("PatchesRememberedAncientDebris", ""), rememberedAncientDebrisCuriosities);
        expressionOverrideTicks = 0; updateExpression();
    }
}
