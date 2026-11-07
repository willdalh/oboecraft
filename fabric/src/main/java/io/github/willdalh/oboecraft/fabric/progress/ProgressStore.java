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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * The player's progress on this computer, which the client keeps and sends to every server the player joins. It is one
 * progress file per account in the game folder, see {@code docs/architecture/progress-files.md}.
 *
 * <p>A session lasts from the server's request for the progress until the player leaves. While it lasts, the file is
 * locked, so another game on the same computer can't use it at the same time, and the server's changes are saved soon
 * after they arrive.
 * All file work runs in order on one background thread, so the game never waits for the disk, and two saves of the
 * file never race.
 */
public final class ProgressStore {
    /** How long after a change the progress is saved, so changes that arrive close together are saved once. */
    public static final Duration SAVE_DELAY = Duration.ofSeconds(2);
    /** How long the game waits for the last save when it stops. */
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private final Path folder;
    private final Duration saveDelay;
    private final ScheduledThreadPoolExecutor fileThread = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "Oboecraft progress store");
        thread.setDaemon(true);
        return thread;
    });
    /** The latest progress the server sent that isn't saved yet. */
    private final AtomicReference<@Nullable Progress> unsaved = new AtomicReference<>();
    /** The name a broken file was kept under, until the player is told. */
    private final AtomicReference<@Nullable String> brokenFileKeptAs = new AtomicReference<>();
    /** Whether a session is going, so changes are saved. */
    private volatile boolean active;
    /** The session's file and lock. Only used on the file thread. */
    private @Nullable Session session;

    private record Session(Path file, FileChannel lockChannel, FileLock lock) {
    }

    /** What reading the player's file found: progress to use, or why there is none. */
    private sealed interface ReadResult {
        record Usable(Progress progress) implements ReadResult {
        }

        record Unusable(ProgressUnavailable.Reason reason) implements ReadResult {
        }
    }

    /**
     * @param folder    the folder of the progress files
     * @param saveDelay how long after a change the progress is saved
     */
    public ProgressStore(Path folder, Duration saveDelay) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.saveDelay = Objects.requireNonNull(saveDelay, "saveDelay");
        // When the game stops, the session's end saves the changes, so waiting saves can be dropped.
        fileThread.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /** The folder of the progress files in a game folder. */
    public static Path folder(Path gameDir) {
        return gameDir.resolve(Oboecraft.MOD_ID).resolve("progress");
    }

    /**
     * Starts a session when the server asks for the player's progress: locks and reads the player's file, and sends the
     * server {@link UploadProgress}, or {@link ProgressUnavailable} if it can't. A file that isn't valid progress is
     * kept as {@code .broken}, and the player starts with empty progress, see {@link #takeBrokenFileKeptAs}. A session
     * that is still going, when the server configures the player again, ends first, so its changes are read back.
     *
     * @param send sends a payload to the server. Called on the file thread.
     * @return when the answer has been sent
     */
    public CompletableFuture<Void> start(UUID player, Consumer<CustomPacketPayload> send) {
        return CompletableFuture.runAsync(() -> send.accept(open(player)), fileThread);
    }

    /**
     * Saves the progress the server sent soon, if a session is going. Changes that arrive before the save are saved
     * together, as the latest progress.
     */
    public void changed(Progress progress) {
        if (active && unsaved.getAndSet(progress) == null) {
            scheduleSave();
        }
    }

    private void scheduleSave() {
        fileThread.schedule(this::saveUnsaved, saveDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Ends the session when the player leaves: saves the latest changes and unlocks the file. Does nothing if no
     * session is going.
     *
     * @return when the session has ended
     */
    public CompletableFuture<Void> end() {
        active = false;
        return CompletableFuture.runAsync(this::endSession, fileThread);
    }

    /** Ends the session when the game stops, and waits for the last save. */
    public void close() {
        end();
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

    /** Opens the session on the file thread, and says what to send the server. */
    private CustomPacketPayload open(UUID player) {
        // A session that never ended, which shouldn't happen, ends first, so its changes are saved.
        endSession();
        Path file = folder.resolve(player + ".json");
        Optional<Session> locked;
        try {
            locked = lock(file);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't lock the progress file {}", file, e);
            return new ProgressUnavailable(ProgressUnavailable.Reason.UNREADABLE);
        }
        if (locked.isEmpty()) {
            OboecraftMod.LOGGER.warn("The progress file {} is in use by another running game", file);
            return new ProgressUnavailable(ProgressUnavailable.Reason.IN_USE);
        }
        return switch (read(file)) {
            case ReadResult.Usable usable -> {
                session = locked.get();
                active = true;
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
    private static Optional<Session> lock(Path file) throws IOException {
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
        return Optional.of(new Session(file, channel, lock));
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
     * Saves the latest changes to the session's file. A failed save is logged and tried again after the save delay, or
     * when the session ends, unless newer changes have arrived, which are saved instead.
     */
    private void saveUnsaved() {
        Progress progress = unsaved.getAndSet(null);
        if (progress == null || session == null) {
            return;
        }
        try {
            ProgressFiles.write(session.file(), progress);
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't save the progress file {}", session.file(), e);
            if (unsaved.compareAndSet(null, progress) && active) {
                scheduleSave();
            }
        }
    }

    /** Saves the latest changes and unlocks the file, on the file thread. */
    private void endSession() {
        saveUnsaved();
        if (session != null) {
            release(session);
            session = null;
        }
        // Changes whose save failed can't be saved without the session. The failure was logged.
        unsaved.set(null);
    }

    private static void release(Session session) {
        try {
            session.lock().release();
            session.lockChannel().close();
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't unlock the progress file {}", session.file(), e);
        }
    }
}
