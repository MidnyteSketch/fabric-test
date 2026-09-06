package com.midnyte.patches.entity;

import com.midnyte.patches.entity.ai.PatchesFollowGoal;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class PatchesEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<ItemStack> BUNDLE =
            SynchedEntityData.defineId(PatchesEntity.class, EntityDataSerializers.ITEM_STACK);

    private PatchesMode modeBeforeSitting = PatchesMode.WANDERING;
    private @Nullable UUID followingPlayerUuid;

    public PatchesEntity(EntityType<? extends PatchesEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.TEMPT_RANGE, 10.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PatchesFollowGoal(this, 1.15, 4.0F, 2.5F));
        this.goalSelector.addGoal(
                2,
                new TemptGoal(
                        this,
                        1.0,
                        Ingredient.of(Items.COOKIE),
                        false
                )
        );
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, PatchesMode.WANDERING.id());
        builder.define(BUNDLE, ItemStack.EMPTY);
    }

    public PatchesMode getMode() {
        return PatchesMode.fromId(this.entityData.get(MODE));
    }

    public void setMode(PatchesMode mode) {
        this.entityData.set(MODE, mode.id());

        if (mode == PatchesMode.SITTING) {
            this.getNavigation().stop();
            this.setDeltaMovement(
                    0.0,
                    this.getDeltaMovement().y,
                    0.0
            );
        }
    }

    public ItemStack getBundleStack() {
        return this.entityData.get(BUNDLE);
    }

    public boolean hasBundle() {
        return !getBundleStack().isEmpty();
    }

    private void setBundleStack(ItemStack stack) {
        if (!stack.isEmpty() && !BundleSupport.isBundle(stack)) {
            throw new IllegalArgumentException("Patches can only equip a Bundle");
        }

        this.entityData.set(
                BUNDLE,
                stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)
        );
    }

    public @Nullable Player getFollowingPlayer() {
        if (followingPlayerUuid == null) {
            return null;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        return serverLevel.getPlayerByUUID(followingPlayerUuid);
    }

    private void setFollowingPlayer(Player player) {
        this.followingPlayerUuid = player.getUUID();
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide() && getMode() == PatchesMode.SITTING) {
            this.getNavigation().stop();
            this.setDeltaMovement(
                    0.0,
                    this.getDeltaMovement().y,
                    0.0
            );
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        /*
         * Bundle equipment is deliberately separate from Patches' inventory.
         * The exact vanilla ItemStack is stored, including all of its contents
         * and components, and is returned unchanged when removed. Every vanilla
         * dyed Bundle is accepted through the minecraft:bundles item tag.
         */
        if (BundleSupport.isBundle(stack)) {
            if (hasBundle()) {
                return InteractionResult.FAIL;
            }

            if (!level().isClientSide()) {
                ItemStack equippedBundle = stack.copyWithCount(1);
                setBundleStack(equippedBundle);

                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(1);
                }
            }

            return InteractionResult.SUCCESS;
        }

        /*
         * Sneak + empty hand removes the equipped Bundle. This check comes
         * before the normal empty-hand Sit/Stand interaction so the controls
         * do not conflict.
         */
        if (stack.isEmpty() && player.isShiftKeyDown() && hasBundle()) {
            if (!level().isClientSide()) {
                ItemStack equippedBundle = getBundleStack().copy();
                setBundleStack(ItemStack.EMPTY);

                if (!player.addItem(equippedBundle)) {
                    player.drop(equippedBundle, false);
                }
            }

            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.COOKIE)) {
            if (!level().isClientSide()) {
                setFollowingPlayer(player);

                if (getMode() == PatchesMode.WANDERING) {
                    setMode(PatchesMode.FOLLOWING);
                } else if (getMode() == PatchesMode.FOLLOWING) {
                    setMode(PatchesMode.WANDERING);
                }

                heal(2.0F);
                consumeOne(player, stack);

                playSound(
                        SoundEvents.GENERIC_EAT.value(),
                        0.7F,
                        1.15F
                );
            }

            return InteractionResult.SUCCESS;
        }

        if (stack.isEmpty()) {
            if (!level().isClientSide()) {
                if (getMode() == PatchesMode.SITTING) {
                    setMode(modeBeforeSitting);
                } else {
                    modeBeforeSitting = getMode();
                    setMode(PatchesMode.SITTING);
                }
            }

            return InteractionResult.SUCCESS;
        }

        if (isLikedFood(stack) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide()) {
                float healing;

                if (stack.is(Items.MUSHROOM_STEW)) {
                    healing = 6.0F;
                } else if (stack.is(Items.APPLE)) {
                    healing = 3.0F;
                } else {
                    healing = 2.0F;
                }

                heal(healing);

                boolean stew = stack.is(Items.MUSHROOM_STEW);

                consumeOne(player, stack);

                if (stew && !player.hasInfiniteMaterials()) {
                    ItemStack bowl = new ItemStack(Items.BOWL);

                    if (!player.addItem(bowl)) {
                        player.drop(bowl, false);
                    }
                }

                playSound(
                        SoundEvents.GENERIC_EAT.value(),
                        0.7F,
                        1.05F
                );
            }

            return InteractionResult.SUCCESS;
        }

        if (isRejectedFood(stack)) {
            return InteractionResult.FAIL;
        }

        return super.mobInteract(player, hand);
    }

    private static boolean isLikedFood(ItemStack stack) {
        return stack.is(Items.APPLE)
                || stack.is(Items.GLOW_BERRIES)
                || stack.is(Items.MUSHROOM_STEW);
    }

    private static boolean isRejectedFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.MUTTON)
                || stack.is(Items.RABBIT);
    }

    private static void consumeOne(Player player, ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        output.putInt(
                "PatchesMode",
                getMode().id()
        );

        output.putInt(
                "PatchesModeBeforeSitting",
                modeBeforeSitting.id()
        );

        output.storeNullable(
                "PatchesFollowingPlayer",
                UUIDUtil.CODEC,
                followingPlayerUuid
        );

        ItemStack bundle = getBundleStack();
        if (!bundle.isEmpty()) {
            output.store("PatchesBundle", ItemStack.CODEC, bundle);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        setMode(
                PatchesMode.fromId(
                        input.getIntOr(
                                "PatchesMode",
                                PatchesMode.WANDERING.id()
                        )
                )
        );

        modeBeforeSitting = PatchesMode.fromId(
                input.getIntOr(
                        "PatchesModeBeforeSitting",
                        PatchesMode.WANDERING.id()
                )
        );

        followingPlayerUuid = input
                .read("PatchesFollowingPlayer", UUIDUtil.CODEC)
                .orElse(null);

        ItemStack savedBundle = input
                .read("PatchesBundle", ItemStack.CODEC)
                .orElse(ItemStack.EMPTY);

        if (savedBundle.isEmpty() || BundleSupport.isBundle(savedBundle)) {
            setBundleStack(savedBundle);
        } else {
            setBundleStack(ItemStack.EMPTY);
        }
    }
}
