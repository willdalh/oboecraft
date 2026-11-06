package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewResult;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewCancelled;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.ReviewStarted;
import io.github.willdalh.oboecraft.fabric.progress.ServerProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Each player's active review. The server runs every review: it reads the stack itself, checks answers and stores
 * the new state. A player has at most one review at a time. Each review has the id the client gave it, and every
 * message about it carries that id. Only used on the server thread.
 */
public final class ServerReviews {
    private final ItemMapping itemMapping;
    private final ServerProgress progress;
    private final Settings settings;
    private final Map<UUID, ActiveReview> active = new HashMap<>();

    /** A review that is going. Later milestones add the trigger here. */
    private record ActiveReview(int reviewId, NameListEntry entry) {
    }

    public ServerReviews(ItemMapping itemMapping, ServerProgress progress, Settings settings) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** The item the player is reviewing, if a review is going. */
    public Optional<ItemId> activeItem(ServerPlayer player) {
        return Optional.ofNullable(active.get(player.getUUID())).map(review -> review.entry().id());
    }

    /**
     * Starts the review {@code reviewId} of the stack in the player's hand, if it is a locked item. A client only asks
     * when it has no answer menu open, so a review that is still going is one the client has left: the new one
     * replaces it.
     */
    public void start(ServerPlayer player, int reviewId, InteractionHand hand) {
        active.remove(player.getUUID());
        if (!progress.isLoaded(player)) {
            refuse(player, reviewId, ReviewRefused.Reason.NO_PROGRESS);
            return;
        }
        Optional<NameListEntry> entry = itemMapping.entryOf(player.getItemInHand(hand));
        if (entry.isEmpty()) {
            refuse(player, reviewId, ReviewRefused.Reason.STACK_GONE);
            return;
        }
        ItemId item = entry.get().id();
        switch (ReviewRules.start(progress.state(player, item), progress.now(), settings)) {
            case ReviewStart.Started started -> {
                active.put(player.getUUID(), new ActiveReview(reviewId, entry.get()));
                ServerPlayNetworking.send(
                        player, new ReviewStarted(progress.now(), reviewId, item, started.level()));
            }
            case ReviewStart.NotLocked notLocked -> {
                progress.sendState(player, item);
                refuse(player, reviewId, ReviewRefused.Reason.NOT_LOCKED);
            }
        }
    }

    private void refuse(ServerPlayer player, int reviewId, ReviewRefused.Reason reason) {
        ServerPlayNetworking.send(player, new ReviewRefused(progress.now(), reviewId, reason));
    }

    /**
     * Checks an answer in the review {@code reviewId}. A pass unlocks the item and ends the review. Blank answers aren't
     * attempts and are ignored. An answer in a review that isn't going tells the client that review is cancelled.
     */
    public void answer(ServerPlayer player, int reviewId, String typed) {
        ActiveReview review = active.get(player.getUUID());
        if (review == null || review.reviewId() != reviewId) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now(), reviewId));
            return;
        }
        if (!ReviewRules.isAttempt(typed)) {
            return;
        }
        NameListEntry entry = review.entry();
        ReviewResult result = ReviewRules.answer(
                entry, progress.state(player, entry.id()), typed, progress.now(), settings);
        Optional<ReviewAnswered.Answer> shownAnswer = Optional.empty();
        if (result.outcome() == ReviewOutcome.PASS) {
            active.remove(player.getUUID());
            progress.update(player, entry.id(), result.newState());
            shownAnswer = Optional.of(new ReviewAnswered.Answer(entry.japaneseName(), entry.reading()));
        }
        ServerPlayNetworking.send(
                player, new ReviewAnswered(progress.now(), reviewId, result.outcome(), shownAnswer));
    }

    /** Cancels the review {@code reviewId} because the player closed its answer menu. Nothing changes. */
    public void close(ServerPlayer player, int reviewId) {
        ActiveReview review = active.get(player.getUUID());
        if (review != null && review.reviewId() == reviewId) {
            active.remove(player.getUUID());
        }
    }

    /** Cancels the player's review on the server's side, for example when they die, and tells the client. */
    public void cancel(ServerPlayer player) {
        ActiveReview review = active.remove(player.getUUID());
        if (review != null) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now(), review.reviewId()));
        }
    }

    /** Forgets the player's review when they leave. */
    public void forget(UUID player) {
        active.remove(player);
    }
}
