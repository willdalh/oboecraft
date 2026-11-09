package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.tsukumogami.Night;
import io.github.willdalh.oboecraft.core.tsukumogami.OverdueItems;
import io.github.willdalh.oboecraft.core.tsukumogami.TsukumogamiRules;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Each player's tsukumogami: when it appears, what it shows and when it leaves, from the rules of core
 * {@link TsukumogamiRules}. It is never saved: whether it should exist is worked out again from the player's progress
 * and the world, and that it was killed tonight is only remembered while the server runs. Only used on the server
 * thread.
 *
 * <p>It checks the players once a second while it is night in the Overworld and the tsukumogami is turned on, and a
 * player right after each of their reviews ends. During the day, a check is one comparison of the time of day.
 */
public final class ServerTsukumogami implements ReviewListener {
    /** How often the players are checked, in ticks. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    private final ItemMapping itemMapping;
    private final ServerProgress progress;
    private final Map<UUID, OwnerState> owners = new HashMap<>();
    /** The tsukumogami that exist, by owner, so the day's check only looks at these. */
    private final Map<UUID, Tsukumogami> existing = new HashMap<>();

    /** What is known about one player, as the owner of their tsukumogami. */
    private static final class OwnerState {
        /** When it may appear, after it was reviewed away or the player arrived. */
        private Instant waitsUntil = Instant.MIN;
        private OptionalLong killedOnNight = OptionalLong.empty();
        private Optional<Instant> reviewStartedAt = Optional.empty();
        private Optional<Instant> reviewEndedAt = Optional.empty();
    }

    public ServerTsukumogami(ItemMapping itemMapping, ServerProgress progress) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    /** Checks every player once each {@link #CHECK_INTERVAL_TICKS}, at the end of a server tick. */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL_TICKS == 0) {
            checkPlayers(server);
        }
    }

    /**
     * Checks every player now. During the day, or with the tsukumogami turned off, it only makes any tsukumogami that
     * still exists fade out.
     */
    public void checkPlayers(MinecraftServer server) {
        long dayTime = server.overworld().getOverworldClockTime();
        if (!TsukumogamiRules.isOn(progress.settings()) || !Night.isNight(dayTime)) {
            if (!existing.isEmpty()) {
                List.copyOf(existing.values()).forEach(this::fadeOut);
            }
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            check(player, dayTime);
        }
    }

    /**
     * Works out whether the player's tsukumogami should exist now, and makes it appear, shows their overdue items on
     * it, or makes it fade out.
     */
    private void check(ServerPlayer player, long dayTime) {
        Optional<Tsukumogami> tsukumogami = existingOf(player);
        if (!canBeFollowed(player)) {
            tsukumogami.ifPresent(this::discard);
            return;
        }
        Settings settings = progress.settings();
        Instant now = progress.now();
        OwnerState owner = owner(player);
        List<ItemId> overdue = overdueItems(player, now, settings);
        if (tsukumogami.isPresent()) {
            // A wait only holds back one that doesn't exist yet.
            if (TsukumogamiRules.shouldExist(overdue.size(), dayTime, owner.killedOnNight, now, Instant.MIN, settings)) {
                tsukumogami.get().showOverdue(overdue, TsukumogamiRules.maxHealth(overdue.size(), settings));
            } else {
                fadeOut(tsukumogami.get());
                owner.waitsUntil = now.plus(TsukumogamiRules.RETURN_DELAY);
            }
        } else if (TsukumogamiRules.shouldExist(
                overdue.size(), dayTime, owner.killedOnNight, now, owner.waitsUntil, settings)) {
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
            addItemOf(stack, carried);
        }
        addItemOf(inventory.getItem(Inventory.SLOT_OFFHAND), carried);
        addItemOf(player.containerMenu.getCarried(), carried);
        return OverdueItems.of(carried, item -> progress.state(player, item), now, settings);
    }

    private void addItemOf(ItemStack stack, List<ItemId> items) {
        if (!stack.isEmpty()) {
            itemMapping.entryOf(stack).ifPresent(entry -> items.add(entry.id()));
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
        tsukumogami.ringArrivalBell(player);
        // An owner who got into bed before it arrived is woken, so sleeping never skips the night it came for.
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

    /** The player killed their tsukumogami, so it is gone for the rest of this night. */
    void killed(ServerPlayer player) {
        existing.remove(player.getUUID());
        owner(player).killedOnNight =
                OptionalLong.of(Night.nightOf(player.level().getServer().overworld().getOverworldClockTime()));
    }

    @Override
    public void reviewStarted(ServerPlayer player) {
        owner(player).reviewStartedAt = Optional.of(progress.now());
    }

    /** Checks the player at once, so a pass that drops the count below the threshold makes it fade out. */
    @Override
    public void reviewEnded(ServerPlayer player) {
        OwnerState owner = owner(player);
        owner.reviewStartedAt = Optional.empty();
        owner.reviewEndedAt = Optional.of(progress.now());
        long dayTime = player.level().getServer().overworld().getOverworldClockTime();
        if (TsukumogamiRules.isOn(progress.settings()) && Night.isNight(dayTime)) {
            check(player, dayTime);
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
     * The player left or died, so their tsukumogami is removed. Whether it was killed tonight, and a wait after it was
     * reviewed away, are kept, so leaving and joining again never brings it back early or sooner. A player with
     * neither is forgotten.
     */
    public void left(ServerPlayer player) {
        existingOf(player).ifPresent(this::discard);
        OwnerState owner = owner(player);
        owner.reviewStartedAt = Optional.empty();
        owner.reviewEndedAt = Optional.empty();
        if (owner.killedOnNight.isEmpty() && !owner.waitsUntil.isAfter(progress.now())) {
            owners.remove(player.getUUID());
        }
    }

    /**
     * Whether the player may not sleep: while their tsukumogami exists, wherever it is, and while it should exist but
     * waits or has found no place to arrive yet, so sleeping never skips the night it is coming for.
     */
    public boolean preventsSleep(ServerPlayer player) {
        if (existingOf(player).isPresent()) {
            return true;
        }
        long dayTime = player.level().getServer().overworld().getOverworldClockTime();
        Settings settings = progress.settings();
        Instant now = progress.now();
        return canBeFollowed(player) && TsukumogamiRules.shouldExist(overdueItems(player, now, settings).size(),
                dayTime, owner(player).killedOnNight, now, Instant.MIN, settings);
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
