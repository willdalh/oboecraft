package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.review.ReviewRules;
import io.github.willdalh.oboecraft.core.review.ReviewStart;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.client.display.PassNotification;
import io.github.willdalh.oboecraft.fabric.client.display.TimerText;
import io.github.willdalh.oboecraft.fabric.item.ItemMapping;
import io.github.willdalh.oboecraft.fabric.lock.CheckedStack;
import io.github.willdalh.oboecraft.fabric.lock.Locks;
import io.github.willdalh.oboecraft.fabric.lock.StackLocation;
import io.github.willdalh.oboecraft.fabric.lock.StoppedStack;
import io.github.willdalh.oboecraft.fabric.network.ReviewAnswered;
import io.github.willdalh.oboecraft.fabric.network.ReviewRefused;
import io.github.willdalh.oboecraft.fabric.network.StartReview;
import java.time.Duration;
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
    private final PassNotification notification;
    private int lastReviewId;

    ClientReviews(ItemMapping itemMapping, Locks locks, ClientProgress progress, PassNotification notification) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.notification = Objects.requireNonNull(notification, "notification");
    }

    /**
     * Checks an action on the client, before the server does, so a locked block is never placed and then taken back
     * and a locked stack never moves and then jumps back. When the lock check stops the action, the answer menu opens
     * at once for the stack it stopped on. When the item is banned, the player is told how long the ban has left.
     *
     * @return whether the action is stopped
     */
    public boolean stops(LocalPlayer player, List<CheckedStack> stacks) {
        Optional<StoppedStack> stopped = locks.firstStopped(progress, player, stacks);
        stopped.ifPresent(stop -> {
            switch (stop.decision()) {
                case REVIEW -> openAnswerMenu(player, stop.checked().location(), stop.entry(), stop.checked().stack());
                case BANNED -> showBan(player, stop.entry().id());
                case ALLOW, DENY -> {
                }
            }
        });
        return stopped.isPresent();
    }

    /**
     * Right-clicking a reviewable inventory stack must not split it first. A banned stack shows its ban instead.
     * Other inventory moves stay free.
     */
    public boolean reviewInventorySlot(LocalPlayer player, int slot) {
        if (!Locks.active(progress, player) || slot < 0 || slot >= player.containerMenu.slots.size()) return false;
        var clicked = player.containerMenu.getSlot(slot);
        if (clicked.container != player.getInventory() || !player.containerMenu.getCarried().isEmpty()) return false;
        var entry = itemMapping.entryOf(clicked.getItem());
        if (entry.isEmpty()) return false;
        switch (ReviewRules.start(progress.state(player, entry.get().id()), progress.now(), progress.settings())) {
            case ReviewStart.Started started -> openAnswerMenu(player,
                    new StackLocation.MenuSlot(player.containerMenu.containerId, slot), entry.get(), clicked.getItem());
            case ReviewStart.Banned banned -> showBan(player, entry.get().id());
            case ReviewStart.NotLocked notLocked -> {
                return false;
            }
        }
        return true;
    }

    /** Tells the player above the hotbar how long the item's ban has left, by the client's own state. */
    private void showBan(LocalPlayer player, ItemId item) {
        if (progress.state(player, item) instanceof SrsState.Leveled leveled) {
            leveled.bannedUntil().ifPresent(until -> player.sendOverlayMessage(Component.translatable(
                    "oboecraft.review.banned", TimerText.format(Duration.between(progress.now(), until)))));
        }
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
        // The level and attempts the server will start the review with, by the client's own state, until the server
        // says. The server's stop wins over the client's state, so the menu opens whatever the client thinks.
        ReviewStart.Started expected =
                ReviewRules.start(progress.state(player, entry.id()), progress.now(), progress.settings())
                        instanceof ReviewStart.Started started ? started : new ReviewStart.Started(0, ReviewRules.ATTEMPTS);
        int reviewId = ++lastReviewId;
        minecraft.gui.setScreen(new AnswerScreen(reviewId, entry.id(), stack.copy(), expected, menuScreen));
        ClientPlayNetworking.send(new StartReview(reviewId, location));
    }

    /**
     * The server started the review. If the server read another item at the location than the menu shows, the
     * client's inventory was out of date: the menu closes, which cancels the review, and the player tries again.
     */
    void reviewStarted(int reviewId, ItemId item, int level, int attemptsLeft) {
        AnswerScreen screen = answerScreen(reviewId);
        if (screen == null) {
            return;
        }
        if (screen.item().equals(item)) {
            screen.started(new ReviewStart.Started(level, attemptsLeft));
        } else {
            screen.onClose();
        }
    }

    /**
     * The server checked an answer. A pass closes the menu and shows the Japanese name in the notification. After a
     * fail, the menu stays open and shows the Japanese name and reading, until the player closes it.
     */
    void reviewAnswered(int reviewId, ReviewOutcome outcome, Optional<ReviewAnswered.Answer> answer) {
        // The server passed the item even if the player closed the menu before the reply came, so the name is shown.
        if (outcome == ReviewOutcome.PASS) {
            answer.ifPresent(shown -> notification.show(shown.japaneseName()));
        }
        AnswerScreen screen = answerScreen(reviewId);
        if (screen == null) {
            return;
        }
        switch (outcome) {
            case LESSON -> answer.ifPresent(screen::showAnswer);
            case WRONG -> screen.answeredWrong();
            case FAIL -> answer.ifPresent(screen::failed);
            case PASS -> screen.end();
        }
    }

    /**
     * The server refused the review, so its answer menu closes. A refusal because the item is banned comes after the
     * item's state, so the player is told how long the ban has left.
     */
    void reviewRefused(int reviewId, ReviewRefused.Reason reason) {
        AnswerScreen screen = answerScreen(reviewId);
        if (screen == null) {
            return;
        }
        screen.end();
        LocalPlayer player = Minecraft.getInstance().player;
        if (reason == ReviewRefused.Reason.BANNED && player != null) {
            showBan(player, screen.item());
        }
    }

    /** The server cancelled the review, so its answer menu closes. */
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
