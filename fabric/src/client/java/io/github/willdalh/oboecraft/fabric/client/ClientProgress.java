package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.core.srs.SrsState;
import io.github.willdalh.oboecraft.fabric.lock.ProgressView;
import java.time.Instant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The local player's SRS states, as the server sent them, and the client's estimate of the server's time. It never
 * changes a state on its own: only messages from the server do.
 */
public final class ClientProgress implements ProgressView {
    /**
     * What the server sent on one connection. States from an earlier connection are never used, so the client's
     * locks are off on a new connection until its {@code ProgressSync} arrives.
     *
     * @param connection     the connection the states came over
     * @param progress       the states
     * @param serverTime     the server's time in the latest message
     * @param receivedNanos  {@link System#nanoTime} when that message arrived, which ignores changes to the
     *                       computer's clock
     */
    private record Snapshot(ClientPacketListener connection, Progress progress, Instant serverTime, long receivedNanos) {
        Snapshot withServerTime(Instant time) {
            return new Snapshot(connection, progress, time, System.nanoTime());
        }
    }

    private volatile @Nullable Snapshot snapshot;

    /** Replaces all states with the ones the server sent when the player joined. */
    public void sync(ClientPacketListener connection, Progress progress, Instant serverTime) {
        snapshot = new Snapshot(connection, progress, serverTime, System.nanoTime());
    }

    /** Stores a state the server sent. Ignored before the server sent all states for this connection. */
    public void update(ClientPacketListener connection, ItemId item, SrsState state, Instant serverTime) {
        Snapshot current = current(connection);
        if (current != null) {
            snapshot = new Snapshot(connection, current.progress().with(item, state), serverTime, System.nanoTime());
        }
    }

    /** Keeps the server's time from a message up to date. */
    public void updateServerTime(ClientPacketListener connection, Instant serverTime) {
        Snapshot current = current(connection);
        if (current != null) {
            snapshot = current.withServerTime(serverTime);
        }
    }

    /** Forgets everything, when the player leaves a world or server. */
    public void clear() {
        snapshot = null;
    }

    private @Nullable Snapshot current(@Nullable ClientPacketListener connection) {
        Snapshot current = snapshot;
        return current != null && connection != null && current.connection() == connection ? current : null;
    }

    private @Nullable Snapshot current() {
        return current(Minecraft.getInstance().getConnection());
    }

    /** The word list reads the latest immutable snapshot, never another connection's progress. */
    public Progress progress() {
        Snapshot current = current();
        return current == null ? Progress.EMPTY : current.progress();
    }

    /** One immutable read of the connection's progress and estimated time, reused for a whole display frame. */
    public ProgressView capture() {
        Snapshot current = current();
        return current == null ? Captured.EMPTY : new Captured(current.progress(),
                current.serverTime().plusNanos(System.nanoTime() - current.receivedNanos()));
    }

    private record Captured(@Nullable Progress progress, Instant now) implements ProgressView {
        private static final Captured EMPTY = new Captured(null, Instant.EPOCH);

        @Override
        public boolean hasProgress(Player player) {
            return progress != null;
        }

        @Override
        public SrsState state(Player player, ItemId item) {
            return progress == null ? SrsState.Leveled.NEW : progress.state(item);
        }
    }

    @Override
    public boolean hasProgress(Player player) {
        return current() != null;
    }

    @Override
    public SrsState state(Player player, ItemId item) {
        Snapshot current = current();
        return current == null ? SrsState.Leveled.NEW : current.progress().state(item);
    }

    /**
     * The server's time now: its time in the latest message, plus how long ago that message arrived. Before the server
     * sent anything on this connection, the epoch, which no lock check uses, since locks aren't active then.
     */
    @Override
    public Instant now() {
        Snapshot current = current();
        if (current == null) {
            return Instant.EPOCH;
        }
        return current.serverTime().plusNanos(System.nanoTime() - current.receivedNanos());
    }
}
