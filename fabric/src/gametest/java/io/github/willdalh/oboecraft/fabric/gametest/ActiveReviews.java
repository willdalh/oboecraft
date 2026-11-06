package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.review.ServerReviews;
import io.github.willdalh.oboecraft.fabric.review.Trigger;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** What the server knows about a test player's review that is going. */
final class ActiveReviews {
    private ActiveReviews() {
    }

    /** The item under review, if a review is going. */
    static Optional<ItemId> item(ServerPlayer player) {
        return OboecraftMod.serverReviews().activeReview(player).map(review -> review.entry().id());
    }

    /** The review's trigger, if a review is going. */
    static Optional<Trigger> trigger(ServerPlayer player) {
        return OboecraftMod.serverReviews().activeReview(player).map(ServerReviews.ActiveReview::trigger);
    }
}
