package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.core.srs.SrsRules;
import io.github.willdalh.oboecraft.fabric.network.ReviewAction;
import io.github.willdalh.oboecraft.fabric.lock.IgnoredItems;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.punishment.Punishment;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewResult;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewCancelled;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.ReviewStarted;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Each player's active review. The server runs every review: it reads the stack itself, checks answers and stores
 * the new state. A player has at most one review at a time. Each review has the id the client gave it, and every
 * message about it carries that id. Only used on the server thread.
 */
public final class ServerReviews {
    /**
     * The interaction a review that the client started from its own stop is checked again with. A location doesn't
     * say what the client tried to do there. The lock check treats every interaction the same, except dropping, which
     * the client only stops when the drop setting is on, and then like every other.
     */
    private static final Interaction RECHECKED_INTERACTION = Interaction.USE;

    private final ItemMapping itemMapping;
    private final Locks locks;
    private final ServerProgress progress;
    private final Settings settings;
    private final Map<UUID, ActiveReview> active = new HashMap<>();
    /** Where the server last stopped each player's action and asked for the answer menu, until a review starts. */
    private final Map<UUID, StackLocation> serverStops = new HashMap<>();

    /**
     * A review that is going.
     *
     * @param reviewId the id the client gave it
     * @param entry    the item under review
     * @param trigger  what the player was doing when it started
     */
    public record ActiveReview(int reviewId, NameListEntry entry, Trigger trigger) {
    }

    public ServerReviews(ItemMapping itemMapping, Locks locks, ServerProgress progress, Settings settings) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Whether the player has a review going. */
    public boolean isReviewing(ServerPlayer player) {
        return active.containsKey(player.getUUID());
    }

    /** The player's review, if one is going. Game tests read it. */
    public Optional<ActiveReview> activeReview(ServerPlayer player) {
        return Optional.ofNullable(active.get(player.getUUID()));
    }

    /**
     * The server stopped the player's action at {@code location} and asked the client for the answer menu there, so
     * the client's {@code StartReview} for it needs no second check. Only the latest stop is remembered.
     */
    public void serverStopped(ServerPlayer player, StackLocation location) {
        serverStops.put(player.getUUID(), location);
    }

    /**
     * Starts the review {@code reviewId} of the stack at {@code location}, if it is a locked item whose interaction the
     * lock check stops there. The server reads the stack and works out the trigger itself. At the location of the
     * server's latest stop, the lock check already stopped the stack. At any other location, the client stopped the
     * action itself and the server never saw it, so the lock check is asked again, and a stack it allows, for example
     * in creative mode, is refused. A client only asks when it has no answer menu open, so a review that is still
     * going is one the client has left: the new one replaces it.
     */
    public void start(ServerPlayer player, int reviewId, StackLocation location) {
        active.remove(player.getUUID());
        boolean serverStopped = location.equals(serverStops.remove(player.getUUID()));
        if (!progress.isLoaded(player)) {
            refuse(player, reviewId, ReviewRefused.Reason.NO_PROGRESS);
            return;
        }
        Optional<ItemStack> stack = location.stackIn(player);
        Optional<NameListEntry> entry = stack.flatMap(itemMapping::entryOf);
        if (entry.isEmpty()) {
            refuse(player, reviewId, ReviewRefused.Reason.STACK_GONE);
            return;
        }
        ItemId item = entry.get().id();
        if (!serverStopped && !lockCheckStops(player, stack.get(), location)) {
            refuseNotLocked(player, reviewId, item);
            return;
        }
        switch (ReviewRules.start(progress.state(player, item), progress.now(), settings)) {
            case ReviewStart.Started started -> {
                Trigger trigger = Trigger.of(player, location, itemMapping);
                active.put(player.getUUID(), new ActiveReview(reviewId, entry.get(), trigger));
                ServerPlayNetworking.send(player, new ReviewStarted(
                        progress.now(), reviewId, item, started.level(), started.attemptsLeft()));
            }
            case ReviewStart.NotLocked notLocked -> refuseNotLocked(player, reviewId, item);
            case ReviewStart.Banned banned -> {
                progress.sendState(player, item);
                refuse(player, reviewId, ReviewRefused.Reason.BANNED);
            }
        }
    }

    /** Whether the lock check stops an interaction with the stack at the location, see {@link #RECHECKED_INTERACTION}. */
    private boolean lockCheckStops(ServerPlayer player, ItemStack stack, StackLocation location) {
        List<CheckedStack> checked = List.of(new CheckedStack(stack, RECHECKED_INTERACTION, location));
        return locks.firstStopped(progress, player, checked).isPresent();
    }

    /** Refuses a review because the item isn't locked for the player, and sends its state so the client agrees. */
    private void refuseNotLocked(ServerPlayer player, int reviewId, ItemId item) {
        progress.sendState(player, item);
        refuse(player, reviewId, ReviewRefused.Reason.NOT_LOCKED);
    }

    private void refuse(ServerPlayer player, int reviewId, ReviewRefused.Reason reason) {
        ServerPlayNetworking.send(player, new ReviewRefused(progress.now(), reviewId, reason));
    }

    /**
     * Checks an answer in the review {@code reviewId}. A pass unlocks the item and ends the review, a wrong first
     * answer is saved as a wrong attempt, and a wrong second answer is a fail. Blank answers aren't attempts and are
     * ignored. An answer in a review that isn't going tells the client that review is cancelled.
     */
    public void answer(ServerPlayer player, int reviewId, String typed) {
        ActiveReview review = active.get(player.getUUID());
        if (review == null || review.reviewId() != reviewId || !progress.isLoaded(player)) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now(), reviewId));
            return;
        }
        if (!ReviewRules.isAttempt(typed)) {
            return;
        }
        NameListEntry entry = review.entry();
        finish(player, review, ReviewRules.answer(
                entry, progress.state(player, entry.id()), typed, progress.now(), settings));
    }

    /** Applies only to the matching active review, never a client-supplied item. */
    public void action(ServerPlayer player, int reviewId, ReviewAction.Action action) {
        ActiveReview review = active.get(player.getUUID());
        if (review == null || review.reviewId() != reviewId || !progress.isLoaded(player)) return;
        ItemId item = review.entry().id();
        switch (action) {
            case SHOW_ANSWER -> {
                if (ReviewRules.isLesson(progress.state(player, item))) {
                    sendAnswered(player, review, ReviewOutcome.LESSON);
                }
            }
            case DONT_KNOW -> finish(player, review,
                    ReviewRules.dontKnow(progress.state(player, item), progress.now(), settings));
            case KNOWN, IGNORE -> {
                progress.update(player, item, action == ReviewAction.Action.KNOWN
                        ? new SrsState.Known() : new SrsState.Ignored());
                active.remove(player.getUUID());
                if (action == ReviewAction.Action.IGNORE) IgnoredItems.drop(player, item);
                ServerPlayNetworking.send(player, new ReviewCancelled(progress.now(), reviewId));
            }
        }
    }

    /**
     * Carries out the result of an answer or Don't know. A pass or a fail ends the review. A fail first removes part
     * of the current view, at the level the item had before the fail demoted it, and then stores the new state.
     */
    private void finish(ServerPlayer player, ActiveReview review, ReviewResult result) {
        ItemId item = review.entry().id();
        switch (result.outcome()) {
            case PASS -> {
                active.remove(player.getUUID());
                progress.update(player, item, result.newState());
            }
            case FAIL -> {
                active.remove(player.getUUID());
                punish(player, review, result.levelBefore());
                progress.update(player, item, result.newState());
            }
            case WRONG -> progress.update(player, item, result.newState());
            case LESSON -> {
            }
        }
        sendAnswered(player, review, result.outcome());
    }

    /**
     * Removes part of the current view for a fail at {@code level}: the reviewed item, or for a crafting trigger the
     * recipe's ingredients, whatever is in the input slots by now.
     */
    private void punish(ServerPlayer player, ActiveReview review, int level) {
        CurrentView view = CurrentView.of(player, review.trigger(), itemMapping);
        Map<ItemId, Integer> inView = view.counts();
        ItemId item = review.entry().id();
        Map<ItemId, Integer> removed = switch (review.trigger()) {
            case Trigger.Crafting crafting -> Punishment.removedIngredients(crafting.recipe(), inView, level, settings);
            case Trigger.Inventory _, Trigger.Container _ -> removedOfItem(item, inView, level);
        };
        view.remove(removed);
    }

    private Map<ItemId, Integer> removedOfItem(ItemId item, Map<ItemId, Integer> inView, int level) {
        int count = Punishment.removedCount(item, inView.getOrDefault(item, 0), level, settings);
        return count > 0 ? Map.of(item, count) : Map.of();
    }

    /** Tells the client how the answer turned out, with the Japanese name when the client shows it. */
    private void sendAnswered(ServerPlayer player, ActiveReview review, ReviewOutcome outcome) {
        Optional<ReviewAnswered.Answer> shownAnswer = outcome == ReviewOutcome.WRONG
                ? Optional.empty()
                : Optional.of(new ReviewAnswered.Answer(
                        review.entry().japaneseName().text(), review.entry().japaneseName().reading()));
        ServerPlayNetworking.send(
                player, new ReviewAnswered(progress.now(), review.reviewId(), outcome, shownAnswer));
    }

    /** Only known, ignored and mastered entries belonging to the sender can be reverted. */
    public void revert(ServerPlayer player, ItemId item) {
        if (!progress.isLoaded(player) || isReviewing(player) || itemMapping.nameList().byId(item).isEmpty()) return;
        if (SrsRules.canRevert(progress.state(player, item), settings)) {
            progress.update(player, item, SrsState.Leveled.NEW);
        }
    }

    /** Cancels the review {@code reviewId} because the player closed its answer menu. Nothing changes. */
    public void close(ServerPlayer player, int reviewId) {
        ActiveReview review = active.get(player.getUUID());
        if (review != null && review.reviewId() == reviewId) {
            active.remove(player.getUUID());
        }
    }

    /**
     * The player's open menu is closing on the server. A review that needs that menu, since its current view is in
     * it, is cancelled.
     */
    public void menuClosing(ServerPlayer player) {
        ActiveReview review = active.get(player.getUUID());
        if (review != null && review.trigger().needsMenu(player.containerMenu.containerId)) {
            cancel(player);
        }
    }

    /** Cancels the player's review on the server's side, for example when they die, and tells the client. */
    public void cancel(ServerPlayer player) {
        ActiveReview review = active.remove(player.getUUID());
        if (review != null) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now(), review.reviewId()));
        }
    }

    /** Forgets the player's review and the server's latest stop when they leave. */
    public void forget(UUID player) {
        active.remove(player);
        serverStops.remove(player);
    }
}
