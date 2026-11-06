package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.LockResult;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.StartReview;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of reviews: opening the answer menu, and showing what the server says. Each answer menu has its
 * own review id, and a message from the server only acts on the menu with the same id, so a late reply about an
 * earlier review never changes a newer one. Only used on the client thread.
 */
public final class ClientReviews {
    private final ItemMapping itemMapping;
    private final Locks locks;
    private final ClientProgress progress;
    private final Settings settings;
    private int lastReviewId;

    ClientReviews(ItemMapping itemMapping, Locks locks, ClientProgress progress, Settings settings) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Checks placing on the client, before the server does, so a locked block is never placed and then taken back.
     * When the item is locked, the answer menu opens at once.
     *
     * @return whether placing is stopped
     */
    public boolean stopsPlacing(LocalPlayer player, InteractionHand hand, ItemStack stack) {
        LockResult result = locks.check(progress, player, stack);
        if (result.decision() == LockDecision.ALLOW) {
            return false;
        }
        result.entry().ifPresent(entry -> openAnswerMenu(player, hand, entry, stack));
        return true;
    }

    /** The server stopped an action and asks for the answer menu for the stack in the hand. */
    void showReview(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        itemMapping.entryOf(stack).ifPresent(entry -> openAnswerMenu(player, hand, entry, stack));
    }

    /**
     * Opens the answer menu and asks the server to start the review. Only when no other screen is open: closing a
     * container's screen to open the menu over it would close the container, so the player tries again instead.
     */
    private void openAnswerMenu(LocalPlayer player, InteractionHand hand, NameListEntry entry, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            return;
        }
        // The level the server will start the review at, by the client's own state, until the server says.
        int level = switch (ReviewRules.start(progress.state(player, entry.id()), progress.now(), settings)) {
            case ReviewStart.Started started -> started.level();
            case ReviewStart.NotLocked notLocked -> 0;
        };
        int reviewId = ++lastReviewId;
        minecraft.gui.setScreen(new AnswerScreen(reviewId, entry.id(), stack.copy(), level));
        ClientPlayNetworking.send(new StartReview(reviewId, hand));
    }

    /**
     * The server started the review. If the server read another item from the hand than the menu shows, the client's
     * inventory was out of date: the menu closes, which cancels the review, and the player tries again.
     */
    void reviewStarted(int reviewId, ItemId item, int level) {
        AnswerScreen screen = answerScreen(reviewId);
        if (screen == null) {
            return;
        }
        if (screen.item().equals(item)) {
            screen.started(level);
        } else {
            screen.onClose();
        }
    }

    /** The server checked an answer. A pass closes the menu and shows the Japanese name and reading. */
    void reviewAnswered(int reviewId, ReviewOutcome outcome, Optional<ReviewAnswered.Answer> answer) {
        AnswerScreen screen = answerScreen(reviewId);
        if (screen == null) {
            return;
        }
        if (outcome == ReviewOutcome.WRONG) {
            screen.answeredWrong();
            return;
        }
        screen.end();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            answer.ifPresent(shown -> player.sendOverlayMessage(Component.translatable(
                    "oboecraft.review.passed", shown.japaneseName(), shown.reading())));
        }
    }

    /** The server refused or cancelled the review, so its answer menu closes. */
    void reviewEnded(int reviewId) {
        AnswerScreen screen = answerScreen(reviewId);
        if (screen != null) {
            screen.end();
        }
    }

    /** The open answer menu, if it belongs to the review {@code reviewId}. */
    private static @Nullable AnswerScreen answerScreen(int reviewId) {
        return Minecraft.getInstance().gui.screen() instanceof AnswerScreen screen && screen.reviewId() == reviewId
                ? screen
                : null;
    }
}
