package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.tsukumogami.OverdueItems;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import io.github.willdalh.oboecraft.fabric.item.ItemMappings;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import io.github.willdalh.oboecraft.fabric.review.ReviewListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Each player's tsukumogami: when it appears, what it shows and when it leaves, from the rules of core
 * {@link TsukumogamiRules}. It is never saved: whether it should exist is worked out again from the player's progress
 * and the world, and whether its owner is haunted and the tick it was killed at are only remembered while the server
 * runs. Only used on the server
 * thread.
 *
 * <p>It checks the players once a second while the tsukumogami is turned on, and a
 * player right after each of their reviews ends.
 */
public final class ServerTsukumogami implements ReviewListener {
    /** How often the players are checked, in ticks. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    private final ItemMappings itemMappings;
    private final ServerProgress progress;
    private final Map<UUID, OwnerState> owners = new HashMap<>();
    /** The tsukumogami that exist, by owner, so disabling the feature only looks at these. */
    private final Map<UUID, Tsukumogami> existing = new HashMap<>();

    /** What is known about one player, as the owner of their tsukumogami. */
    private static final class OwnerState {
        /** Whether the player is haunted, see {@link TsukumogamiRules#isHaunted}. */
        private boolean haunted;
        /** When it may appear, after it was reviewed away or the player arrived. */
        private Instant waitsUntil = Instant.MIN;
        private OptionalLong killedAtTick = OptionalLong.empty();
        private Optional<Instant> reviewStartedAt = Optional.empty();
        private Optional<Instant> reviewEndedAt = Optional.empty();
        private Optional<Instant> lastThreatAt = Optional.empty();
    }

    public ServerTsukumogami(ItemMappings itemMappings, ServerProgress progress) {
        this.itemMappings = Objects.requireNonNull(itemMappings, "itemMappings");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    /** Checks every player once each {@link #CHECK_INTERVAL_TICKS}, at the end of a server tick. */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            checkPlayers(server);
        }
    }

    /**
     * Checks every player now. With the tsukumogami turned off, it only makes any tsukumogami that
     * still exists fade out.
     */
    public void checkPlayers(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (!TsukumogamiRules.isOn(progress.settings())) {
            if (!existing.isEmpty()) {
                List.copyOf(existing.values()).forEach(this::fadeOut);
            }
            owners.values().forEach(owner -> owner.haunted = false);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            check(player, gameTime);
        }
    }

    /**
     * Works out whether the player's tsukumogami should exist now, and makes it appear, shows their overdue items on
     * it, or makes it fade out.
     */
    private void check(ServerPlayer player, long gameTime) {
        Optional<Tsukumogami> tsukumogami = existingOf(player);
        if (!canBeFollowed(player)) {
            tsukumogami.ifPresent(this::discard);
            return;
        }
        Settings settings = progress.settings();
        Instant now = progress.now();
        OwnerState owner = owner(player);
        List<ItemId> overdue = overdueItems(player, now, settings);
        owner.haunted = TsukumogamiRules.isHaunted(owner.haunted, overdue.size(), settings);
        if (tsukumogami.isPresent()) {
            // A wait only holds back one that doesn't exist yet.
            if (TsukumogamiRules.shouldExist(owner.haunted, gameTime, owner.killedAtTick, now, Instant.MIN)) {
                tsukumogami.get().showOverdue(overdue, TsukumogamiRules.maxHealth(overdue.size(), settings));
            } else {
                fadeOut(tsukumogami.get());
                owner.waitsUntil = now.plus(TsukumogamiRules.RETURN_DELAY);
            }
        } else if (TsukumogamiRules.shouldExist(owner.haunted, gameTime, owner.killedAtTick, now, owner.waitsUntil)) {
            appear(player, overdue, TsukumogamiRules.maxHealth(overdue.size(), settings));
        }
    }

    /**
     * Whether a tsukumogami can follow the player at all: alive in the Overworld, in survival or adventure mode, not
     * on Peaceful, and with their progress held.
     */
    private boolean canBeFollowed(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && player.level().dimension() == Level.OVERWORLD
                && player.gameMode().isSurvival() && player.level().getDifficulty() != Difficulty.PEACEFUL
                && progress.hasSession(player);
    }

    /**
     * The overdue items the player carries: in the inventory, the hotbar, the offhand and on the cursor, but not their
     * worn armor or a container.
     */
    private List<ItemId> overdueItems(ServerPlayer player, Instant now, Settings settings) {
        Inventory inventory = player.getInventory();
        List<ItemId> carried = new ArrayList<>(Inventory.INVENTORY_SIZE + 2);
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            addItemOf(player, stack, carried);
        }
        addItemOf(player, inventory.getItem(Inventory.SLOT_OFFHAND), carried);
        addItemOf(player, player.containerMenu.getCarried(), carried);
        return OverdueItems.of(carried, item -> progress.state(player, item), now, settings);
    }

    private void addItemOf(ServerPlayer player, ItemStack stack, List<ItemId> items) {
        if (!stack.isEmpty()) {
            itemMappings.get(progress.language(player)).entryOf(stack).ifPresent(entry -> items.add(entry.id()));
        }
    }

    /** Makes the player's tsukumogami arrive near them, if there is a place for it. */
    private void appear(ServerPlayer player, List<ItemId> overdue, int maxHealth) {
        ServerLevel level = player.level();
        Optional<Vec3> spot = TsukumogamiSpots.around(level, player, TsukumogamiType.TYPE);
        if (spot.isEmpty()) {
            return;
        }
        Tsukumogami tsukumogami = TsukumogamiType.TYPE.create(level, EntitySpawnReason.EVENT);
        if (tsukumogami == null) {
            return;
        }
        Vec3 at = spot.get();
        tsukumogami.snapTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        tsukumogami.follow(player, this);
        tsukumogami.showOverdue(overdue, maxHealth);
        tsukumogami.setHealth(tsukumogami.getMaxHealth());
        if (!level.addFreshEntity(tsukumogami)) {
            tsukumogami.discard();
            return;
        }
        existing.put(player.getUUID(), tsukumogami);
        tsukumogami.ringArrivalBell();
        // An owner who got into bed before it arrived is woken, so its owner cannot sleep through an encounter.
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        player.sendOverlayMessage(Component.translatableWithFallback("oboecraft.tsukumogami.arrived",
                "A tsukumogami is following you. Review your overdue items to drive it away."));
    }

    /** The player's tsukumogami, if it exists. One that is gone, like one that was unloaded, is forgotten. */
    public Optional<Tsukumogami> existingOf(ServerPlayer player) {
        Tsukumogami tsukumogami = existing.get(player.getUUID());
        if (tsukumogami != null && tsukumogami.isRemoved()) {
            existing.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.ofNullable(tsukumogami);
    }

    private void fadeOut(Tsukumogami tsukumogami) {
        tsukumogami.owner().ifPresent(owner -> existing.remove(owner.getUUID()));
        tsukumogami.fadeOut();
    }

    private void discard(Tsukumogami tsukumogami) {
        tsukumogami.owner().ifPresent(owner -> existing.remove(owner.getUUID()));
        tsukumogami.discard();
    }

    /** Whether the player's tsukumogami stands still for a review, see {@link TsukumogamiRules#standsStill}. */
    public boolean standsStill(ServerPlayer player) {
        OwnerState owner = owner(player);
        return TsukumogamiRules.standsStill(
                owner.reviewStartedAt, owner.reviewEndedAt, progress.now(), progress.settings());
    }

    /** Checks threats each AI tick, before any pending strike can land. No sight or path check is needed. */
    boolean waitsForSafety(ServerPlayer player, Tsukumogami tsukumogami) {
        double range = TsukumogamiRules.THREAT_RANGE;
        boolean threatened = !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                mob -> mob != tsukumogami && mob.isAlive() && mob.getTarget() == player
                        && mob.distanceToSqr(player) <= range * range).isEmpty();
        OwnerState owner = owner(player);
        Instant now = progress.now();
        if (threatened) {
            owner.lastThreatAt = Optional.of(now);
        }
        return TsukumogamiRules.waitsForSafety(owner.lastThreatAt, now);
    }

    /** The player killed their tsukumogami, so it is gone for 12,000 elapsed game ticks. */
    void killed(ServerPlayer player) {
        existing.remove(player.getUUID());
        owner(player).killedAtTick =
                OptionalLong.of(player.level().getServer().overworld().getGameTime());
    }

    @Override
    public void reviewStarted(ServerPlayer player) {
        owner(player).reviewStartedAt = Optional.of(progress.now());
    }

    /** Checks the player at once, so a pass that drops the count to the fade threshold makes it fade out. */
    @Override
    public void reviewEnded(ServerPlayer player) {
        OwnerState owner = owner(player);
        owner.reviewStartedAt = Optional.empty();
        owner.reviewEndedAt = Optional.of(progress.now());
        long gameTime = player.level().getServer().overworld().getGameTime();
        if (TsukumogamiRules.isOn(progress.settings())) {
            check(player, gameTime);
        }
    }

    /**
     * The player joined, respawned or changed dimension. A tsukumogami that can't follow them now is removed. One that
     * doesn't exist then waits at least {@link TsukumogamiRules#ARRIVAL_DELAY}, so it never appears on top of a
     * player who just arrived, and a longer wait, after it was reviewed away, is kept.
     */
    public void arrived(ServerPlayer player) {
        Optional<Tsukumogami> tsukumogami = existingOf(player);
        if (tsukumogami.isPresent() && canBeFollowed(player)) {
            return;
        }
        tsukumogami.ifPresent(this::discard);
        OwnerState owner = owner(player);
        Instant arrivalWait = progress.now().plus(TsukumogamiRules.ARRIVAL_DELAY);
        if (arrivalWait.isAfter(owner.waitsUntil)) {
            owner.waitsUntil = arrivalWait;
        }
    }

    /**
     * The player's game mode changed. Changing between survival and adventure mode changes nothing, and any other
     * change is like arriving: creative and spectator mode remove it, and returning from them makes it wait.
     */
    public void gameModeChanged(ServerPlayer player) {
        GameType previous = player.gameMode.getPreviousGameModeForPlayer();
        if (previous == null || !previous.isSurvival() || !player.gameMode().isSurvival()) {
            arrived(player);
        }
    }

    /**
     * The player left or died, so their tsukumogami is removed. Whether they are haunted, the tick it was killed at,
     * and a wait after it was reviewed away are kept, so leaving and joining again never gets rid of it or brings it
     * back sooner. A player with none of them is forgotten.
     */
    public void left(ServerPlayer player) {
        existingOf(player).ifPresent(this::discard);
        OwnerState owner = owner(player);
        owner.reviewStartedAt = Optional.empty();
        owner.reviewEndedAt = Optional.empty();
        owner.lastThreatAt = Optional.empty();
        if (!owner.haunted && owner.killedAtTick.isEmpty() && !owner.waitsUntil.isAfter(progress.now())) {
            owners.remove(player.getUUID());
        }
    }

    /**
     * Whether the player may not sleep: while their tsukumogami exists, wherever it is, and while it should exist but
     * waits or has found no place to arrive yet, so its owner cannot sleep through an encounter.
     */
    public boolean preventsSleep(ServerPlayer player) {
        if (existingOf(player).isPresent()) {
            return true;
        }
        if (!canBeFollowed(player)) {
            return false;
        }
        long gameTime = player.level().getServer().overworld().getGameTime();
        Settings settings = progress.settings();
        Instant now = progress.now();
        OwnerState owner = owner(player);
        int overdueCount = overdueItems(player, now, settings).size();
        boolean haunted = TsukumogamiRules.isHaunted(owner.haunted, overdueCount, settings);
        return TsukumogamiRules.shouldExist(haunted, gameTime, owner.killedAtTick, now, Instant.MIN);
    }

    /** Forgets everything when the server stops, so another world in the same game starts afresh. */
    public void clear() {
        existing.clear();
        owners.clear();
    }

    private OwnerState owner(ServerPlayer player) {
        return owners.computeIfAbsent(player.getUUID(), id -> new OwnerState());
    }
}
