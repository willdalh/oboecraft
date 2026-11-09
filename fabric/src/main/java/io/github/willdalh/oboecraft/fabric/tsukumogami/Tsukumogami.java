package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tsukumogami, a menreiki that follows one player, its owner, while their overdue items pile up at night. It
 * follows and attacks only its owner, only its owner can hurt it, and other mobs ignore it. {@link ServerTsukumogami}
 * makes it and decides when it leaves. One that has no owner, like one made with {@code /summon}, removes itself.
 *
 * <p>Everyone who sees it is sent its orbiting items, whether it stands still and whether it fades out. Only its owner
 * sees its boss bar.
 */
public final class Tsukumogami extends PathfinderMob {
    /** The entity event that starts the attack's wind-up on the client. */
    public static final byte WIND_UP_EVENT = 4;
    /** How long the attack winds up before it hits, in ticks. */
    public static final int WIND_UP_TICKS = 20;
    /** How long it fades out before it is gone, in ticks. */
    public static final int FADE_TICKS = 40;
    /** The time between the end of one attack and the start of the next, in ticks. */
    private static final int ATTACK_COOLDOWN_TICKS = 30;
    /** How often it looks for a new path to its owner, in ticks. */
    private static final int PATH_INTERVAL_TICKS = 10;
    /** How long it may make no progress towards its owner before it counts as stuck, in ticks. */
    private static final int STUCK_TICKS = 60;
    /** How far it must have moved in {@link #STUCK_TICKS} to not be stuck, squared. */
    private static final double STUCK_DISTANCE_SQR = 1.0;
    /** From how far away it teleports behind its owner, in blocks. */
    private static final double KEEP_UP_DISTANCE = 32;

    private static final EntityDataAccessor<List<ItemId>> DATA_ORBITING_ITEMS =
            SynchedEntityData.defineId(Tsukumogami.class, TsukumogamiType.ORBITING_ITEMS);
    private static final EntityDataAccessor<Boolean> DATA_STANDS_STILL =
            SynchedEntityData.defineId(Tsukumogami.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FADING =
            SynchedEntityData.defineId(Tsukumogami.class, EntityDataSerializers.BOOLEAN);

    /** The client's animations. */
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState windUpAnimationState = new AnimationState();
    public final AnimationState watchAnimationState = new AnimationState();
    public final AnimationState fadeAnimationState = new AnimationState();

    private final ServerBossEvent bossBar =
            new ServerBossEvent(getUUID(), Component.empty(), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
    private @Nullable ServerPlayer owner;
    private @Nullable ServerTsukumogami followers;
    private int windUpTicks;
    private int attackCooldownTicks;
    private int fadeTicks;
    private int ticksSinceProgress;
    private Vec3 progressCheckedAt = Vec3.ZERO;

    public Tsukumogami(EntityType<? extends Tsukumogami> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, TsukumogamiRules.BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                // Slower than a player walks, and it never runs.
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, KEEP_UP_DISTANCE + 16)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ORBITING_ITEMS, List.of());
        entityData.define(DATA_STANDS_STILL, false);
        entityData.define(DATA_FADING, false);
    }

    /**
     * Makes it follow its owner, on the server, before it is added to the level. Its owner alone sees its boss bar.
     */
    void follow(ServerPlayer owner, ServerTsukumogami followers) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.followers = Objects.requireNonNull(followers, "followers");
        bossBar.addPlayer(owner);
    }

    /** Its owner, on the server. */
    public Optional<ServerPlayer> owner() {
        return Optional.ofNullable(owner);
    }

    /**
     * Shows the owner's overdue items and sets its max health for their number, keeping the same fraction of health,
     * so a pass weakens it at once. The boss bar shows its name and the overdue count.
     */
    void showOverdue(List<ItemId> overdue, int maxHealth) {
        entityData.set(DATA_ORBITING_ITEMS,
                List.copyOf(overdue.subList(0, Math.min(overdue.size(), TsukumogamiRules.MAX_ORBITING_ITEMS))));
        float oldMaxHealth = getMaxHealth();
        if (oldMaxHealth != maxHealth) {
            Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH)).setBaseValue(maxHealth);
            setHealth(TsukumogamiRules.scaledHealth(getHealth(), oldMaxHealth, maxHealth));
        }
        bossBar.setName(Component.translatableWithFallback("oboecraft.tsukumogami.boss_bar", "%s (%s)",
                getDisplayName(), overdue.size()));
        bossBar.setProgress(getHealth() / getMaxHealth());
    }

    /** The overdue items that orbit it, on both sides. */
    public List<ItemId> orbitingItems() {
        return entityData.get(DATA_ORBITING_ITEMS);
    }

    /** Whether it stands still and watches its owner during a review, on both sides. */
    public boolean standsStill() {
        return entityData.get(DATA_STANDS_STILL);
    }

    /** Whether it is fading out, on both sides. */
    public boolean isFading() {
        return entityData.get(DATA_FADING);
    }

    /**
     * Starts fading out on the spot, after its owner reviewed it away or at dawn. Its orbiting items fall away, and it
     * is gone after {@link #FADE_TICKS}. It can't be hurt while it fades.
     */
    void fadeOut() {
        if (isFading()) {
            return;
        }
        entityData.set(DATA_FADING, true);
        bossBar.removeAllPlayers();
        getNavigation().stop();
        playSound(TsukumogamiSounds.FADE, 2.0F, 1.0F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (owner == null || followers == null || owner.isRemoved() || owner.level() != level) {
            discard();
            return;
        }
        if (isFading()) {
            if (++fadeTicks >= FADE_TICKS) {
                discard();
            }
            return;
        }
        getLookControl().setLookAt(owner, 10.0F, 30.0F);
        boolean standsStill = followers.standsStill(owner);
        entityData.set(DATA_STANDS_STILL, standsStill);
        if (standsStill) {
            getNavigation().stop();
            windUpTicks = 0;
        } else {
            fightOwner(level, owner);
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
    }

    /** Walks to its owner and attacks with a slow, heavy hit, and keeps up if it falls behind or gets stuck. */
    private void fightOwner(ServerLevel level, ServerPlayer owner) {
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
        if (windUpTicks > 0) {
            getNavigation().stop();
            if (--windUpTicks == 0) {
                if (isWithinMeleeAttackRange(owner)) {
                    doHurtTarget(level, owner);
                }
                attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
            }
            return;
        }
        if (isWithinMeleeAttackRange(owner)) {
            ticksSinceProgress = 0;
            if (attackCooldownTicks == 0) {
                getNavigation().stop();
                windUpTicks = WIND_UP_TICKS;
                level.broadcastEntityEvent(this, WIND_UP_EVENT);
                playSound(TsukumogamiSounds.ATTACK, 1.5F, 1.0F);
            }
            return;
        }
        if (tickCount % PATH_INTERVAL_TICKS == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(owner, 1.0);
        }
        if (distanceToSqr(owner) > KEEP_UP_DISTANCE * KEEP_UP_DISTANCE || isStuck()) {
            TsukumogamiSpots.behind(level, owner, this).ifPresent(spot -> {
                teleportTo(spot.x, spot.y, spot.z);
                getNavigation().stop();
            });
            ticksSinceProgress = 0;
            progressCheckedAt = position();
        }
    }

    /** Whether it has moved less than a block in the last {@link #STUCK_TICKS} on its way to its owner. */
    private boolean isStuck() {
        if (++ticksSinceProgress < STUCK_TICKS) {
            return false;
        }
        boolean stuck = position().distanceToSqr(progressCheckedAt) < STUCK_DISTANCE_SQR;
        ticksSinceProgress = 0;
        progressCheckedAt = position();
        return stuck;
    }

    /**
     * Only damage its owner causes hurts it, besides {@code /kill} and the void. Other players' hits play a dull sound,
     * and everything else, like lava, falling or another mob, does nothing.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isFading()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        Entity attacker = source.getEntity();
        if (owner != null && attacker != null && attacker.getUUID().equals(owner.getUUID())) {
            return super.hurtServer(level, source, damage);
        }
        if (attacker instanceof Player) {
            playSound(TsukumogamiSounds.NO_EFFECT, 1.0F, 1.0F);
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (followers != null && owner != null) {
            followers.killed(owner);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            idleAnimationState.startIfStopped(tickCount);
            watchAnimationState.animateWhen(standsStill() && !isFading(), tickCount);
            fadeAnimationState.animateWhen(isFading(), tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == WIND_UP_EVENT) {
            windUpAnimationState.start(tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Other mobs never target it, and it never leads them to its owner. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /** Nobody can push it into lava or a trap. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 0;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return TsukumogamiSounds.AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TsukumogamiSounds.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TsukumogamiSounds.DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        playSound(TsukumogamiSounds.STEP, 0.6F, 1.0F);
    }
}
