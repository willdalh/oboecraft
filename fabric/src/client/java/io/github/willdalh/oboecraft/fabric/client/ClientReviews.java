package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.settings.Settings;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.lock.StoppedStack;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.StartReview;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
     * Checks an action on the client, before the server does, so a locked block is never placed and then taken back
     * and a locked stack never moves and then jumps back. When the lock check stops the action, the answer menu opens
     * at once for the stack it stopped on.
     *
     * @return whether the action is stopped
     */
    public boolean stops(LocalPlayer player, List<CheckedStack> stacks) {
        Optional<StoppedStack> stopped = locks.firstStopped(progress, player, stacks);
        stopped.ifPresent(stop ->
                openAnswerMenu(player, stop.checked().location(), stop.entry(), stop.checked().stack()));
        return stopped.isPresent();
    }

    /** The server stopped an action and asks for the answer menu for the stack at {@code location}. */
    void showReview(StackLocation location) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        location.stackIn(player).ifPresent(stack -> itemMapping.entryOf(stack).ifPresent(entry ->
                openAnswerMenu(player, location, entry, stack)));
    }

    /**
     * Opens the answer menu and asks the server to start the review. Only over the game or over the screen of the
     * player's open menu, which stays open behind it. Over any other screen, like another answer menu or the chat,
     * nothing opens, and the player tries again.
     */
    private void openAnswerMenu(LocalPlayer player, StackLocation location, NameListEntry entry, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        @Nullable AbstractContainerScreen<?> menuScreen =
                current instanceof AbstractContainerScreen<?> screen && screen.getMenu() == player.containerMenu
                        ? screen
                        : null;
        if (current != null && menuScreen == null) {
            return;
        }
        // The level the server will start the review at, by the client's own state, until the server says.
        int level = switch (ReviewRules.start(progress.state(player, entry.id()), progress.now(), settings)) {
            case ReviewStart.Started started -> started.level();
            case ReviewStart.NotLocked notLocked -> 0;
        };
        int reviewId = ++lastReviewId;
        minecraft.gui.setScreen(new AnswerScreen(reviewId, entry.id(), stack.copy(), level, menuScreen));
        ClientPlayNetworking.send(new StartReview(reviewId, location));
    }

    /**
     * The server started the review. If the server read another item at the location than the menu shows, the
     * client's inventory was out of date: the menu closes, which cancels the review, and the player tries again.
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
