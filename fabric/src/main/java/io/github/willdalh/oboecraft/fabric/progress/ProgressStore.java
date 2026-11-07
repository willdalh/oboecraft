package io.github.willdalh.oboecraft.fabric.progress;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.srs.Progress;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.network.ProgressUnavailable;
import io.github.willdalh.oboecraft.fabric.network.UploadProgress;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * The player's progress on this computer, which the client keeps and sends to every server the player joins. It is one
 * progress file per account in the game folder, see {@code docs/architecture/progress-files.md}.
 *
 * <p>A session belongs to one connection, from the server's request for the progress until the player leaves. While it
 * lasts, the file is locked, so another game on the same computer can't use it at the same time, and the server's
 * changes on that connection are saved soon after they arrive. Anything another connection asks of the store is
 * ignored, so a message handled late never touches the current session. All file work runs in order on one background
 * thread, so the game never waits for the disk, and two saves of the file never race.
 */
public final class ProgressStore {
    /** How long after a change the progress is saved, so changes that arrive close together are saved once. */
    public static final Duration SAVE_DELAY = Duration.ofSeconds(2);
    /** How long the game waits for the last save when it stops. */
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private final Path folder;
    private final Duration saveDelay;
    private final Runnable saveFailed;
    private final ScheduledThreadPoolExecutor fileThread = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "Oboecraft progress store");
        thread.setDaemon(true);
        return thread;
    });
    /** The latest progress the server sent that isn't saved yet, and the connection it came over. */
    private final AtomicReference<@Nullable Change> unsaved = new AtomicReference<>();
    /** The name a broken file was kept under, until the player is told. */
    private final AtomicReference<@Nullable String> brokenFileKeptAs = new AtomicReference<>();
    /** The session's connection, file and lock. Only used on the file thread. */
    private @Nullable Session session;

    private record Change(Connection connection, Progress progress) {
    }

    private static final class Session {
        final Connection connection;
        final Path file;
        final FileChannel lockChannel;
        final FileLock lock;
        boolean saveFailed;

        Session(Connection connection, Path file, FileChannel lockChannel, FileLock lock) {
            this.connection = connection;
            this.file = file;
            this.lockChannel = lockChannel;
            this.lock = lock;
        }
    }

    /** What reading the player's file found: progress to use, or why there is none. */
    private sealed interface ReadResult {
        record Usable(Progress progress) implements ReadResult {
        }

        record Unusable(ProgressUnavailable.Reason reason) implements ReadResult {
        }
    }

    /**
     * @param folder     the folder of the progress files
     * @param saveDelay  how long after a change the progress is saved
     * @param saveFailed tells the player that their progress couldn't be saved, at most once a session. Called on the
     *                   file thread.
     */
    public ProgressStore(Path folder, Duration saveDelay, Runnable saveFailed) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.saveDelay = Objects.requireNonNull(saveDelay, "saveDelay");
        this.saveFailed = Objects.requireNonNull(saveFailed, "saveFailed");
        // When the game stops, the session's end saves the changes, so waiting saves can be dropped.
        fileThread.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /** The folder of the progress files in a game folder. */
    public static Path folder(Path gameDir) {
        return gameDir.resolve(Oboecraft.MOD_ID).resolve("progress");
    }

    /**
     * Starts a session for the connection when the server asks for the player's progress: locks and reads the
     * player's file, and sends the server {@link UploadProgress}, or {@link ProgressUnavailable} if it can't. A file
     * that isn't valid progress is kept as {@code .broken}, and the player starts with empty progress, see
     * {@link #takeBrokenFileKeptAs}. A session that is still going ends first, so its changes are read back, for
     * example when the server configures the player again.
     *
     * @param send sends a payload to the server. Called on the file thread.
     * @return when the answer has been sent
     */
    public CompletableFuture<Void> start(Connection connection, UUID player, Consumer<CustomPacketPayload> send) {
        return onFileThread(() -> send.accept(open(connection, player)));
    }

    /**
     * Saves the progress the server sent on the connection soon. Changes that arrive before the save are saved
     * together, as the latest progress. Ignored unless the connection has the session.
     */
    public void changed(Connection connection, Progress progress) {
        if (unsaved.getAndSet(new Change(connection, progress)) == null) {
            scheduleSave();
        }
    }

    private void scheduleSave() {
        if (!fileThread.isShutdown()) {
            fileThread.schedule(this::saveUnsaved, saveDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Ends the connection's session when the player leaves: saves the latest changes and unlocks the file. Ignored
     * unless the connection has the session.
     *
     * @return when the session has ended
     */
    public CompletableFuture<Void> end(Connection connection) {
        return onFileThread(() -> {
            if (session != null && session.connection == connection) {
                endSession();
            }
        });
    }

    /**
     * Ends the session when the game stops, and waits for the last save. The game stops before it leaves the world, so
     * the connection's own end comes later and is ignored.
     */
    public void close() {
        onFileThread(this::endSession);
        fileThread.shutdown();
        try {
            if (!fileThread.awaitTermination(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                OboecraftMod.LOGGER.error("The progress wasn't saved in time when the game stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The name a broken progress file was kept under, once, so the player can be told. */
    public Optional<String> takeBrokenFileKeptAs() {
        return Optional.ofNullable(brokenFileKeptAs.getAndSet(null));
    }

    /** Runs file work in order on the file thread. Once the game has stopped, there is none. Failures are logged. */
    private CompletableFuture<Void> onFileThread(Runnable work) {
        if (fileThread.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(work, fileThread).whenComplete((done, error) -> {
            if (error != null) {
                OboecraftMod.LOGGER.error("The progress store failed", error);
            }
        });
    }

    /** Opens the connection's session on the file thread, and says what to send the server. */
    private CustomPacketPayload open(Connection connection, UUID player) {
        endSession();
        // Changes that arrived outside a session belong to no file. The server only sends this session's after it.
        unsaved.set(null);
        Path file = folder.resolve(player + ".json");
        Optional<Session> locked;
        try {
            locked = lock(connection, file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't lock the progress file {}", file, e);
            return new ProgressUnavailable(ProgressUnavailable.Reason.UNREADABLE);
        }
        if (locked.isEmpty()) {
            OboecraftMod.LOGGER.warn("The progress file {} is in use by another running game", file);
            return new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE);
        }
        return switch (read(file)) {
            case ReadResult.Usable usable when !UploadProgress.fits(usable.progress()) -> {
                OboecraftMod.LOGGER.error("The progress in {} has {} states, too many or too large to send",
                        file, usable.progress().states().size());
                release(locked.get());
                yield new ProgressUnavailable(ProgressUnavailable.Reason.TOO_LARGE);
            }
            case ReadResult.Usable usable -> {
                session = locked.get();
                yield new UploadProgress(usable.progress());
            }
            case ReadResult.Unusable unusable -> {
                release(locked.get());
                yield new ProgressUnavailable(unusable.reason());
            }
        };
    }

    /**
     * Locks the file for this game, with a lock file next to it. Empty if another game holds the lock. Within one game,
     * a lock that is held already counts as another game's, which only tests can cause.
     */
    private static Optional<Session> lock(Connection connection, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        return Optional.of(new Session(connection, file, channel, lock));
    }

    /**
     * Reads the player's progress. A missing file is empty progress. A file that isn't valid progress is kept as
     * {@code .broken}, and is empty progress too. A file from a newer version of the mod, or one that can't be read, is
     * left untouched and can't be used.
     */
    private ReadResult read(Path file) {
        ProgressFiles.ReadResult result;
        try {
            result = ProgressFiles.read(file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't read the progress file {}", file, e);
            return new ReadResult.Unusable(ProgressUnavailable.Reason.UNREADABLE);
        }
        return switch (result) {
            case ProgressFiles.ReadResult.Loaded loaded -> new ReadResult.Usable(loaded.progress());
            case ProgressFiles.ReadResult.Missing missing -> new ReadResult.Usable(Progress.EMPTY);
            case ProgressFiles.ReadResult.TooNew tooNew -> {
                OboecraftMod.LOGGER.warn("The progress file {} has format version {}, newer than {}",
                        file, tooNew.version(), ProgressFiles.VERSION);
                yield new ReadResult.Unusable(ProgressUnavailable.Reason.TOO_NEW);
            }
            case ProgressFiles.ReadResult.Broken broken -> keepBroken(file, broken.reason());
        };
    }

    private ReadResult keepBroken(Path file, String reason) {
        OboecraftMod.LOGGER.error("The progress file {} isn't valid progress: {}", file, reason);
        try {
            brokenFileKeptAs.set(ProgressFiles.keepBroken(file).getFileName().toString());
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't move the broken progress file {}", file, e);
            return new ReadResult.Unusable(ProgressUnavailable.Reason.UNREADABLE);
        }
        return new ReadResult.Usable(Progress.EMPTY);
    }

    /**
     * Saves the latest change to the session's file, if it came over the session's connection. A failed save is logged,
     * the player is told the first time in a session, and it is tried again after the save delay, unless newer changes
     * have arrived, which are saved instead.
     */
    private void saveUnsaved() {
        Change change = unsaved.getAndSet(null);
        if (change == null || session == null || change.connection() != session.connection) {
            return;
        }
        try {
            ProgressFiles.write(session.file, change.progress());
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't save the progress file {}", session.file, e);
            if (!session.saveFailed) {
                session.saveFailed = true;
                saveFailed.run();
            }
            if (unsaved.compareAndSet(null, change)) {
                scheduleSave();
            }
        }
    }

    /** Saves the latest changes and unlocks the file, on the file thread. Does nothing without a session. */
    private void endSession() {
        if (session == null) {
            return;
        }
        saveUnsaved();
        Change failed = unsaved.get();
        if (failed != null && failed.connection() == session.connection && unsaved.compareAndSet(failed, null)) {
            OboecraftMod.LOGGER.error("The latest progress couldn't be saved to {} before the session ended",
                    session.file);
        }
        release(session);
        session = null;
    }

    private static void release(Session session) {
        try {
            session.lock.release();
            session.lockChannel.close();
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't unlock the progress file {}", session.file, e);
        }
    }
}
