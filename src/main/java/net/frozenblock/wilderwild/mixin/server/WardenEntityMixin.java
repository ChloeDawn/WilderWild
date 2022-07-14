package net.frozenblock.wilderwild.mixin.server;

import com.mojang.logging.LogUtils;
import net.frozenblock.wilderwild.WilderWild;
import net.frozenblock.wilderwild.entity.ai.WardenMoveControl;
import net.frozenblock.wilderwild.entity.ai.WardenNavigation;
import net.frozenblock.wilderwild.entity.render.animations.WardenAnimationInterface;
import net.frozenblock.wilderwild.registry.RegisterProperties;
import net.frozenblock.wilderwild.registry.RegisterSounds;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.Angriness;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WardenBrain;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.listener.GameEventListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WardenEntity.class)
public abstract class WardenEntityMixin extends HostileEntity implements WardenAnimationInterface {

    private final WardenEntity warden = WardenEntity.class.cast(this);

    /**
     * @author FrozenBlock
     * @reason custom death sound
     */
    @Overwrite
    public SoundEvent getDeathSound() {
        if (!this.isSubmergedInWaterOrLava()) {
            warden.playSound(RegisterSounds.ENTITY_WARDEN_DYING, 5.0F, 1.0F);
        } else if (this.isSubmergedInWaterOrLava()) {
            warden.playSound(RegisterSounds.ENTITY_WARDEN_UNDERWATER_DYING, 0.75F, 1.0F);
        }
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }

    @Shadow
    public abstract Brain<WardenEntity> getBrain();

    @Shadow
    protected abstract void addDigParticles(AnimationState animationState);

    @Shadow
    protected abstract boolean isDiggingOrEmerging();

    protected WardenEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    private final AnimationState dyingAnimationState = new AnimationState();

    private final AnimationState swimmingDyingAnimation = new AnimationState();

    @Override
    public AnimationState getDyingAnimationState() {
        return this.dyingAnimationState;
    }

    @Override
    public AnimationState getSwimmingDyingAnimation() {
        return this.swimmingDyingAnimation;
    }

    private float leaningPitch;
    private float lastLeaningPitch;

    @Inject(at = @At("HEAD"), method = "initialize")
    public void initialize(ServerWorldAccess serverWorldAccess, LocalDifficulty localDifficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound nbtCompound, CallbackInfoReturnable<EntityData> info) {
        warden.getBrain().remember(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
        warden.getBrain().remember(MemoryModuleType.TOUCH_COOLDOWN, Unit.INSTANCE, WardenBrain.EMERGE_DURATION);
        if (spawnReason == SpawnReason.SPAWN_EGG && !this.isTouchingWaterOrLava()) { //still emerges when touching a liquid for some reason??
            warden.setPose(EntityPose.EMERGING);
            warden.getBrain().remember(MemoryModuleType.IS_EMERGING, Unit.INSTANCE, WardenBrain.EMERGE_DURATION);
            this.playSound(SoundEvents.ENTITY_WARDEN_AGITATED, 5.0F, 1.0F);
        }
    }

    @Inject(at = @At("HEAD"), method = "pushAway")
    protected void pushAway(Entity entity, CallbackInfo info) {
        if (!warden.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_COOLING_DOWN) && !warden.getBrain().hasMemoryModule(MemoryModuleType.TOUCH_COOLDOWN) && !(entity instanceof WardenEntity) && !this.isDiggingOrEmerging() && !warden.isInPose(EntityPose.DYING) && !warden.isInPose(EntityPose.ROARING)) {
            if (!entity.isInvulnerable() && entity instanceof LivingEntity livingEntity) {
                if (!(entity instanceof PlayerEntity player)) {
                    warden.increaseAngerAt(entity, Angriness.ANGRY.getThreshold() + 20, false);

                    if (!livingEntity.isDead() && warden.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()) {
                        warden.updateAttackTarget(livingEntity);
                    }
                } else {
                    if (!player.isCreative()) {
                        warden.increaseAngerAt(entity, Angriness.ANGRY.getThreshold() + 20, false);

                        if (!player.isDead() && warden.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()) {
                            warden.updateAttackTarget(player);
                        }
                    }
                }
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "accept", cancellable = true)
    public void accept(ServerWorld world, GameEventListener listener, BlockPos pos, GameEvent event, @Nullable Entity entity, @Nullable Entity sourceEntity, float f, CallbackInfo info) {
        int additionalAnger = 0;
        if (world.getBlockState(pos).isOf(Blocks.SCULK_SENSOR)) {
            if (!world.getBlockState(pos).get(RegisterProperties.NOT_HICCUPPING)) {
                additionalAnger = 65;
            }
        }
        warden.getBrain().remember(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, 40L);
        world.sendEntityStatus(warden, (byte) 61);
        warden.playSound(SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, 5.0F, warden.getSoundPitch());
        BlockPos blockPos = pos;
        if (sourceEntity != null) {
            if (warden.isInRange(sourceEntity, 30.0D)) {
                if (warden.getBrain().hasMemoryModule(MemoryModuleType.RECENT_PROJECTILE)) {
                    if (warden.isValidTarget(sourceEntity)) {
                        blockPos = sourceEntity.getBlockPos();
                    }

                    warden.increaseAngerAt(sourceEntity);
                    warden.increaseAngerAt(sourceEntity, additionalAnger, false);
                } else {
                    warden.increaseAngerAt(sourceEntity, 10, true);
                    warden.increaseAngerAt(sourceEntity, additionalAnger, false);
                }
            }

            warden.getBrain().remember(MemoryModuleType.RECENT_PROJECTILE, Unit.INSTANCE, 100L);
        } else {
            warden.increaseAngerAt(entity);
            warden.increaseAngerAt(entity, additionalAnger, false);
        }

        if (warden.getAngriness() != Angriness.ANGRY && (sourceEntity != null || warden.getAngerManager().getPrimeSuspect().map((suspect) -> suspect == entity).orElse(true))) {
            WardenBrain.lookAtDisturbance(warden, blockPos);
        }
        info.cancel();
    }

    @Inject(method = "onTrackedDataSet", at = @At("HEAD"), cancellable = true)
    public void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        if (POSE.equals(data)) {
            if (warden.getPose() == EntityPose.DYING) {
                if (!this.isSubmergedInWaterOrLava()) {
                    this.getDyingAnimationState().start(warden.age);
                    ci.cancel();
                } else {
                    this.getSwimmingDyingAnimation().start(warden.age);
                    ci.cancel();
                }
            }
        }
    }

    private int deathTicks = 0;

    @Override
    public boolean isDead() {
        return super.isDead();
    }

    @Override
    public boolean isAlive() {
        return this.deathTicks < 70 && !this.isRemoved();
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (!warden.isRemoved() && !warden.dead) {

            Entity entity = damageSource.getAttacker();
            LivingEntity livingEntity = warden.getPrimeAdversary();
            if (this.scoreAmount >= 0 && livingEntity != null) {
                livingEntity.updateKilledAdvancementCriterion(warden, this.scoreAmount, damageSource);
            }

            if (this.isSleeping()) {
                this.wakeUp();
            }

            if (!warden.world.isClient && this.hasCustomName()) {
                WilderWild.LOGGER.info("Named entity {} died: {}", warden, warden.getDamageTracker().getDeathMessage().getString());
            }

            warden.dead = true;
            this.getDamageTracker().update();
            if (this.world instanceof ServerWorld) {
                if (entity == null || entity.onKilledOther((ServerWorld) warden.world, warden)) {
                    warden.emitGameEvent(GameEvent.ENTITY_DIE);
                    this.drop(damageSource);
                    this.onKilledBy(livingEntity);
                }

                warden.world.sendEntityStatus(warden, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
            }

            warden.setPose(EntityPose.DYING);
            warden.getBrain().clear();
            warden.clearGoalsAndTasks();
            warden.setAiDisabled(true);
        }
    }

    private void addAdditionalDeathParticles() {
        for (int i = 0; i < 20; ++i) {
            double d = this.random.nextGaussian() * 0.02;
            double e = this.random.nextGaussian() * 0.02;
            double f = this.random.nextGaussian() * 0.02;
            this.world.addParticle(ParticleTypes.SCULK_CHARGE_POP, this.getParticleX(1.0), this.getRandomBodyY(), this.getParticleZ(1.0), d, e, f);
            this.world.addParticle(ParticleTypes.SCULK_SOUL, this.getParticleX(1.0), this.getRandomBodyY(), this.getParticleZ(1.0), d, e, f);
        }

    }

    @Override
    protected void updatePostDeath() {
        ++this.deathTicks;
        if (this.deathTicks == 35 && !warden.world.isClient()) {
            warden.deathTime = 35;
        }

        if (this.deathTicks == 53 && !warden.world.isClient()) {
            warden.world.sendEntityStatus(warden, EntityStatuses.ADD_DEATH_PARTICLES);
            warden.world.sendEntityStatus(warden, (byte) 69420);
        }

        if (this.deathTicks == 70 && !warden.world.isClient()) {
            warden.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        this.updateLeaningPitch();
        if (warden.getPose() == EntityPose.DYING) {
            this.addDigParticles(this.getDyingAnimationState());
        }
    }

    private void updateLeaningPitch() {
        this.lastLeaningPitch = this.leaningPitch;
        if (this.isInSwimmingPose()) {
            this.leaningPitch = Math.min(1.0F, this.leaningPitch + 0.09F);
        } else {
            this.leaningPitch = Math.max(0.0F, this.leaningPitch - 0.09F);
        }

    }

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void handleStatus(byte status, CallbackInfo ci) {
        if (status == (byte) 69420) {
            this.addAdditionalDeathParticles();
        }
    }

    /**
     * @author FrozenBlock
     * @reason allows for further warden navigation customization
     */
    @Overwrite
    public EntityNavigation createNavigation(World world) {
        WardenEntity wardenEntity = WardenEntity.class.cast(this);
        // for some reason it needs a new one lol
        return new WardenNavigation(wardenEntity, world);
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.canMoveVoluntarily() && this.isTouchingWaterOrLava()) {
            this.updateVelocity(this.getMovementSpeed(), movementInput);
            this.move(MovementType.SELF, this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.9));
            if (this.isSubmergedInWaterOrLava() && this.getMovementSpeed() > 0F) {
                warden.setPose(EntityPose.SWIMMING);
            }
        } else {
            super.travel(movementInput);
            if (!this.isSubmergedInWaterOrLava() && this.getMovementSpeed() <= 0F && !this.isDiggingOrEmerging() && !warden.isInPose(EntityPose.SNIFFING) && !warden.isInPose(EntityPose.DYING) && !warden.isInPose(EntityPose.ROARING)) {
                warden.setPose(EntityPose.STANDING);
            }
        }

    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void WardenEntity(EntityType<? extends HostileEntity> entityType, World world, CallbackInfo ci) {
        WardenEntity wardenEntity = WardenEntity.class.cast(this);
        wardenEntity.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.moveControl = new WardenMoveControl(wardenEntity, 3, 26, 0.13F, 1.0F, true);
    }

    @Override
    public boolean canBreatheInWater() {
        return true;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public SoundEvent getSwimSound() {
        return RegisterSounds.ENTITY_WARDEN_SWIM;
    }

    @Override
    public void swimUpward(TagKey<Fluid> fluid) {
    }

    public float getLeaningPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastLeaningPitch, this.leaningPitch);
    }

    @Override
    protected boolean updateWaterState() {
        this.fluidHeight.clear();
        warden.checkWaterState();
        boolean bl = warden.updateMovementInFluid(FluidTags.LAVA, 0.1D);
        return this.isTouchingWaterOrLava() || bl;
    }

    private boolean isTouchingWaterOrLava() {
        return warden.isInsideWaterOrBubbleColumn() || warden.isInLava();
    }

    private boolean isSubmergedInWaterOrLava() {
        return warden.isSubmergedIn(FluidTags.WATER) || warden.isSubmergedIn(FluidTags.LAVA);
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void getDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> info) {
        if (this.isInSwimmingPose()) {
            info.setReturnValue(EntityDimensions.changing(warden.getType().getWidth(), 0.85F));
            info.cancel();
        }
    }
}