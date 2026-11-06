package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.lock.Interaction;
import io.github.willdalh.oboecraft.core.lock.LockDecision;
import io.github.willdalh.oboecraft.core.review.ReviewOutcome;
import io.github.willdalh.oboecraft.core.srs.SrsState;
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

/** The client's side of reviews: opening the answer menu, and showing what the server says. */
public final class ClientReviews {
    private final ItemMapping itemMapping;
    private final Locks locks;
    private final ClientProgress progress;

    ClientReviews(ItemMapping itemMapping, Locks locks, ClientProgress progress) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    /**
     * Checks placing on the client, before the server does, so a locked block is never placed and then taken back.
     * When the item is locked, the answer menu opens at once.
     *
     * @return whether placing is stopped
     */
    public boolean stopsPlacing(LocalPlayer player, InteractionHand hand, ItemStack stack) {
        LockResult result = locks.check(progress, player, stack, Interaction.PLACE);
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

    /** Opens the answer menu and asks the server to start the review, unless an answer menu is already open. */
    private void openAnswerMenu(LocalPlayer player, InteractionHand hand, NameListEntry entry, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof AnswerScreen) {
            return;
        }
        int level = switch (progress.state(player, entry.id())) {
            case SrsState.Leveled leveled -> leveled.level();
        };
        minecraft.gui.setScreen(new AnswerScreen(minecraft.gui.screen(), stack.copy(), level));
        ClientPlayNetworking.send(new StartReview(hand));
    }

    /** The server started the review. If the player already closed the answer menu, the server knows by now. */
    void reviewStarted(int level) {
        AnswerScreen screen = answerScreen();
        if (screen != null) {
            screen.started(level);
        }
    }

    /** The server checked an answer. A pass closes the menu and shows the Japanese name and reading. */
    void reviewAnswered(ReviewOutcome outcome, Optional<ReviewAnswered.Answer> answer) {
        AnswerScreen screen = answerScreen();
        if (outcome == ReviewOutcome.WRONG) {
            if (screen != null) {
                screen.answeredWrong();
            }
            return;
        }
        if (screen != null) {
            screen.end();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            answer.ifPresent(shown -> player.sendOverlayMessage(Component.translatable(
                    "oboecraft.review.passed", shown.japaneseName(), shown.reading())));
        }
    }

    /** The server refused or cancelled the review, so the answer menu closes. */
    void reviewEnded() {
        AnswerScreen screen = answerScreen();
        if (screen != null) {
            screen.end();
        }
    }

    private static @Nullable AnswerScreen answerScreen() {
        return Minecraft.getInstance().gui.screen() instanceof AnswerScreen screen ? screen : null;
    }
}
