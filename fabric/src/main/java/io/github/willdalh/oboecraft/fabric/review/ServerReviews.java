package io.github.willdalh.oboecraft.fabric.review;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewResult;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.core.srs.SrsState;
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
 * the new state. A player has at most one review at a time. Only used on the server thread.
 */
public final class ServerReviews {
    private final ItemMapping itemMapping;
    private final ServerProgress progress;
    private final Settings settings;
    private final Map<UUID, ActiveReview> active = new HashMap<>();

    /** A review that is going. Later milestones add the trigger here. */
    private record ActiveReview(NameListEntry entry) {
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
     * Starts a review of the stack in the player's hand, if it is a locked item. If a review is already going, the
     * player is told about that one instead, so the client's answer menu shows the review the server runs.
     */
    public void start(ServerPlayer player, InteractionHand hand) {
        if (!progress.isLoaded(player)) {
            return;
        }
        ActiveReview current = active.get(player.getUUID());
        if (current != null) {
            sendStarted(player, current.entry().id());
            return;
        }
        Optional<NameListEntry> entry = itemMapping.entryOf(player.getItemInHand(hand));
        if (entry.isEmpty()) {
            ServerPlayNetworking.send(player, new ReviewRefused(progress.now(), ReviewRefused.Reason.STACK_GONE));
            return;
        }
        ItemId item = entry.get().id();
        switch (ReviewRules.start(progress.state(player, item), progress.now(), settings)) {
            case ReviewStart.Started started -> {
                active.put(player.getUUID(), new ActiveReview(entry.get()));
                ServerPlayNetworking.send(player, new ReviewStarted(progress.now(), item, started.level()));
            }
            case ReviewStart.NotLocked notLocked -> {
                progress.sendState(player, item);
                ServerPlayNetworking.send(player, new ReviewRefused(progress.now(), ReviewRefused.Reason.NOT_LOCKED));
            }
        }
    }

    private void sendStarted(ServerPlayer player, ItemId item) {
        int level = switch (progress.state(player, item)) {
            case SrsState.Leveled leveled -> leveled.level();
        };
        ServerPlayNetworking.send(player, new ReviewStarted(progress.now(), item, level));
    }

    /**
     * Checks an answer in the player's review. A pass unlocks the item and ends the review. Blank answers aren't
     * attempts and are ignored. An answer with no review going tells the client its review is cancelled.
     */
    public void answer(ServerPlayer player, String typed) {
        ActiveReview review = active.get(player.getUUID());
        if (review == null) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now()));
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
        ServerPlayNetworking.send(player, new ReviewAnswered(progress.now(), result.outcome(), shownAnswer));
    }

    /** Cancels the player's review because they closed the answer menu. Nothing changes. */
    public void close(ServerPlayer player) {
        active.remove(player.getUUID());
    }

    /** Cancels the player's review on the server's side, for example when they die, and tells the client. */
    public void cancel(ServerPlayer player) {
        if (active.remove(player.getUUID()) != null) {
            ServerPlayNetworking.send(player, new ReviewCancelled(progress.now()));
        }
    }

    /** Forgets the player's review when they leave. */
    public void forget(UUID player) {
        active.remove(player);
    }
}
