package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tsukumogami, a menreiki that follows one player, its owner, while their overdue items pile up. It
 * follows and attacks only its owner, only its owner can hurt it, and other mobs ignore it. {@link ServerTsukumogami}
 * makes it and decides when it leaves. One that has no owner, like one made with {@code /summon}, removes itself.
 *
 * <p>Everyone who sees it is sent its orbiting items, whether it stands still, waits for safety or fades out, and is told
 * when an attack's wind-up starts. Their clients animate it from that with {@link TsukumogamiMotion}. Only its owner
 * sees its boss bar.
 */
public final class Tsukumogami extends PathfinderMob {
    /** The entity event that starts the attack's wind-up on the client. */
    public static final byte WIND_UP_EVENT = 4;
    /** How long the attack winds up before it hits, in ticks. */
    public static final int WIND_UP_TICKS = 20;
    /** How long it stands where it is after the hit, straightening up again, in ticks. */
    public static final int RECOVERY_TICKS = 20;
    /** How long the attack's animation runs, the wind-up and then the hit and straightening up again, in ticks. */
    public static final int ATTACK_ANIMATION_TICKS = WIND_UP_TICKS + RECOVERY_TICKS;
    /** How long it fades out before it is gone, in ticks. */
    public static final int FADE_TICKS = 40;
    /** The time between the hit of one attack and the start of the next, in ticks. */
    public static final int ATTACK_COOLDOWN_TICKS = 30;
    /** How far it walks between two steps, in blocks. Its walk on the client takes a step as often as it sounds. */
    public static final float STEP_BLOCKS = 1.1F;
    /**
     * How often it looks for a new path to its owner, in ticks. A search for an owner it can't reach is the most
     * expensive kind, so it waits this long also when it stands at the end of a path that falls short of its owner.
     */
    private static final int PATH_INTERVAL_TICKS = 10;
    /** How long it may make no progress towards its owner before it counts as stuck, in ticks. */
    private static final int STUCK_TICKS = 60;
    /** How far it must have moved in {@link #STUCK_TICKS} to not be stuck, squared. */
    private static final double STUCK_DISTANCE_SQR = 1.0;
    /** From how far away it teleports behind its owner, in blocks. */
    private static final double KEEP_UP_DISTANCE = 32;
    /** How long it waits before it looks again for a place behind its owner, after it found none, in ticks. */
    private static final int KEEP_UP_RETRY_TICKS = 20;
    /** How much of a block it walked counts towards its next step, as Minecraft counts it. */
    private static final float MOVE_DISTANCE_PER_BLOCK = 0.6F;
    /** How loud the bell is when it arrives, loud enough for its owner to hear from where it arrives. */
    private static final float ARRIVAL_BELL_VOLUME = 4.0F;

    private static final EntityDataAccessor<List<ItemId>> DATA_ORBITING_ITEMS =
            SynchedEntityData.defineId(Tsukumogami.class, TsukumogamiType.ORBITING_ITEMS);
    private static final EntityDataAccessor<Boolean> DATA_STANDS_STILL =
            SynchedEntityData.defineId(Tsukumogami.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAITS_FOR_SAFETY =
            SynchedEntityData.defineId(Tsukumogami.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FADING =
            SynchedEntityData.defineId(Tsukumogami.class, EntityDataSerializers.BOOLEAN);

    /** How it moves on the client. */
    private final TsukumogamiMotion motion = new TsukumogamiMotion();

    /** Its boss bar, on the server, made when it starts following its owner. */
    private @Nullable ServerBossEvent bossBar;
    private @Nullable ServerPlayer owner;
    private @Nullable ServerTsukumogami serverTsukumogami;
    private int overdueCount;
    private int windUpTicks;
    private int keepUpRetryTicks;
    private int attackCooldownTicks;
    private int fadeTicks;
    /** How long until it looks for a new path to its owner, in ticks. */
    private int pathSearchTicks;
    /** Whether the path it follows leads to its owner, so it looks for a new one as soon as it walked it. */
    private boolean followsPathToOwner;
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
        entityData.define(DATA_WAITS_FOR_SAFETY, false);
        entityData.define(DATA_FADING, false);
    }

    /**
     * Makes it follow its owner, on the server, before it is added to the level. Its owner alone sees its boss bar.
     */
    void follow(ServerPlayer owner, ServerTsukumogami serverTsukumogami) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.serverTsukumogami = Objects.requireNonNull(serverTsukumogami, "serverTsukumogami");
        bossBar = new ServerBossEvent(
                getUUID(), getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.addPlayer(owner);
    }

    /** The players who see its boss bar, on the server. Game tests read it. */
    public List<ServerPlayer> bossBarViewers() {
        return bossBar == null ? List.of() : List.copyOf(bossBar.getPlayers());
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
        overdueCount = overdue.size();
        updateBossBarName();
        if (bossBar != null) {
            bossBar.setProgress(getHealth() / getMaxHealth());
        }
    }

    private void updateBossBarName() {
        if (bossBar != null) {
            bossBar.setName(waitsForSafety()
                    ? Component.translatableWithFallback("oboecraft.tsukumogami.boss_bar_waiting",
                            "%s (%s) — Waiting until you’re safe", getDisplayName(), overdueCount)
                    : Component.translatableWithFallback("oboecraft.tsukumogami.boss_bar", "%s (%s)",
                            getDisplayName(), overdueCount));
        }
    }

    /** Whether attacks are suspended for danger or its grace period, on both sides. */
    public boolean waitsForSafety() {
        return entityData.get(DATA_WAITS_FOR_SAFETY);
    }

    /** Its current boss bar title, for server game tests. */
    public Component bossBarName() {
        return bossBar == null ? getDisplayName() : bossBar.getName();
    }

    /** The overdue items that orbit it, on both sides. */
    public List<ItemId> orbitingItems() {
        return entityData.get(DATA_ORBITING_ITEMS);
    }

    /** Whether it stands still and watches its owner during a review, on both sides. */
    public boolean standsStill() {
        return entityData.get(DATA_STANDS_STILL);
    }

    /**
     * Rings the temple bell where it arrived, for its owner only, since it follows nobody else. The bell comes from its
     * direction, so the owner can tell where it is.
     */
    void ringArrivalBell() {
        if (owner == null) {
            return;
        }
        owner.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                TsukumogamiSounds.ARRIVE), getSoundSource(), getX(), getY(), getZ(), ARRIVAL_BELL_VOLUME, 1.0F,
                random.nextLong()));
    }

    /** Whether it is fading out, on both sides. */
    public boolean isFading() {
        return entityData.get(DATA_FADING);
    }

    /** How it moves, on the client. */
    public TsukumogamiMotion motion() {
        return motion;
    }

    /**
     * Starts fading out on the spot, after its owner reviewed it away. Its orbiting items fall away, and it
     * is gone after {@link #FADE_TICKS}. It can't be hurt while it fades.
     */
    void fadeOut() {
        if (isFading()) {
            return;
        }
        entityData.set(DATA_FADING, true);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
        getNavigation().stop();
        playSound(TsukumogamiSounds.FADE, 2.0F, 1.0F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (owner == null || serverTsukumogami == null || bossBar == null || owner.isRemoved()
                || owner.level() != level) {
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
        boolean waiting = serverTsukumogami.waitsForSafety(owner, this);
        if (waiting != waitsForSafety()) {
            entityData.set(DATA_WAITS_FOR_SAFETY, waiting);
            updateBossBarName();
        }
        if (waiting) {
            windUpTicks = 0;
        }
        boolean standsStill = serverTsukumogami.standsStill(owner);
        entityData.set(DATA_STANDS_STILL, standsStill);
        if (standsStill) {
            getNavigation().stop();
            windUpTicks = 0;
        } else {
            fightOwner(level, owner);
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
    }

    /**
     * Walks to its owner and attacks with a slow, heavy hit, after which it stands where it is while it straightens
     * up, and keeps up if it falls behind or gets stuck.
     */
    private void fightOwner(ServerLevel level, ServerPlayer owner) {
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
        if (pathSearchTicks > 0) {
            pathSearchTicks--;
        }
        if (windUpTicks > 0) {
            getNavigation().stop();
            if (--windUpTicks == 0) {
                playSound(TsukumogamiSounds.HIT, 1.5F, 1.0F);
                if (isWithinMeleeAttackRange(owner)) {
                    doHurtTarget(level, owner);
                }
                attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
            }
            return;
        }
        if (!waitsForSafety() && attackCooldownTicks > ATTACK_COOLDOWN_TICKS - RECOVERY_TICKS) {
            getNavigation().stop();
            return;
        }
        if (isWithinMeleeAttackRange(owner)) {
            ticksSinceProgress = 0;
            if (!waitsForSafety() && attackCooldownTicks == 0) {
                getNavigation().stop();
                windUpTicks = WIND_UP_TICKS;
                level.broadcastEntityEvent(this, WIND_UP_EVENT);
                playSound(TsukumogamiSounds.ATTACK, 1.5F, 1.0F);
            }
            return;
        }
        if (pathSearchTicks == 0 || followsPathToOwner && getNavigation().isDone()) {
            searchPathTo(owner);
        }
        if (keepUpRetryTicks > 0) {
            keepUpRetryTicks--;
        } else if (distanceToSqr(owner) > KEEP_UP_DISTANCE * KEEP_UP_DISTANCE || isStuck()) {
            keepUp(level, owner);
        }
    }

    /**
     * Looks for a path to its owner and follows it, or the part of it that gets it as close as it can. It looks again
     * after {@link #PATH_INTERVAL_TICKS}, or as soon as it walked a path that leads to its owner.
     */
    private void searchPathTo(ServerPlayer owner) {
        PathNavigation navigation = getNavigation();
        navigation.moveTo(owner, 1.0);
        Path path = navigation.getPath();
        followsPathToOwner = navigation.isInProgress() && path != null && path.canReach();
        pathSearchTicks = PATH_INTERVAL_TICKS;
    }

    /**
     * Teleports to a place behind its owner. Where there is none, like over open water, it looks again a second
     * later, not on every tick.
     */
    private void keepUp(ServerLevel level, ServerPlayer owner) {
        Optional<Vec3> spot = TsukumogamiSpots.behind(level, owner, this);
        if (spot.isPresent()) {
            teleportTo(spot.get().x, spot.get().y, spot.get().z);
            getNavigation().stop();
            pathSearchTicks = 0;
        } else {
            keepUpRetryTicks = KEEP_UP_RETRY_TICKS;
        }
        ticksSinceProgress = 0;
        progressCheckedAt = position();
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
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        if (isFading()) {
            return false;
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
        if (serverTsukumogami != null && owner != null) {
            serverTsukumogami.killed(owner);
        }
    }

    /** Every way it is removed ends here, being unloaded with its chunk included, so its boss bar always goes. */
    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            motion.tick(standsStill(), isFading(), waitsForSafety());
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == WIND_UP_EVENT) {
            motion.startAttack();
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** It flinches when it is hurt, on the client, in place of the flailing walk of Minecraft's own mobs. */
    @Override
    public void handleDamageEvent(DamageSource source) {
        float walkSpeed = walkAnimation.speed();
        super.handleDamageEvent(source);
        walkAnimation.setSpeed(walkSpeed);
        motion.startFlinch();
    }

    /** A step sounds every {@link #STEP_BLOCKS}. */
    @Override
    protected float nextStep() {
        return moveDist + STEP_BLOCKS * MOVE_DISTANCE_PER_BLOCK;
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
        playSound(TsukumogamiSounds.STEP, 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }
}
