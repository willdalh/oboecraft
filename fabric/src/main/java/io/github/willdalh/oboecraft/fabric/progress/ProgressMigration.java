package io.github.willdalh.oboecraft.fabric.progress;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.files.AtomicFiles;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.UUID;

/** Migrates the old Japanese files while holding both generations' locks. Every replacement has a complete copy first. */
public final class ProgressMigration {
    /** Older mods reject the stub as too new, rather than silently starting empty progress after a downgrade. */
    public static final int STUB_VERSION = Integer.MAX_VALUE;

    private ProgressMigration() { }

    /** Runs on the store's file thread at startup, including accounts that aren't joining this time. */
    static void migrateAll(Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            var players = new HashSet<UUID>();
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.length() < 41 || !name.substring(36).startsWith(".json")) continue;
                try { players.add(UUID.fromString(name.substring(0, 36))); }
                catch (IllegalArgumentException ignored) { }
            }
            for (UUID player : players) {
                try { migrate(folder, player); }
                catch (IOException e) { OboecraftMod.LOGGER.error("Couldn't migrate progress for {}", player, e); }
            }
        } catch (IOException e) {
            OboecraftMod.LOGGER.error("Couldn't list legacy progress in {}", folder, e);
        }
    }

    /** False if a running game holds either file; a join retries startup work that couldn't finish. */
    public static boolean migrate(Path folder, UUID player) throws IOException {
        Path old = folder.resolve(player + ".json");
        if (!Files.isDirectory(folder)) return true;
        Path current = folder.resolve("ja_jp").resolve(old.getFileName());
        Files.createDirectories(current.getParent());
        try (FileChannel legacyChannel = channel(old); FileChannel currentChannel = channel(current)) {
            try (FileLock legacyLock = lock(legacyChannel); FileLock currentLock = lock(currentChannel)) {
                if (legacyLock == null || currentLock == null) return false;
                if (Files.exists(old) && !isStub(old, player)) {
                    if (Files.exists(current)) {
                        if (Files.getLastModifiedTime(old).compareTo(Files.getLastModifiedTime(current)) > 0) {
                            AtomicFiles.copy(current, unusedBroken(current));
                            copyKeepingTime(old, current);
                        } else {
                            AtomicFiles.copy(old, unusedBroken(current));
                        }
                    } else {
                        copyKeepingTime(old, current);
                    }
                    AtomicFiles.write(old, "{\"version\": " + STUB_VERSION + ", \"movedTo\": \"ja_jp/" + player + ".json\"}\n");
                }
                try (var files = Files.list(folder)) {
                    for (Path broken : files.filter(path -> path.getFileName().toString().matches(
                            java.util.regex.Pattern.quote(old.getFileName() + ".broken") + "(\\.\\d+)?")).toList()) {
                        Files.move(broken, unusedBroken(current));
                    }
                }
                return true;
            }
        }
    }

    /** A future or hand-edited file with the sentinel version alone must still be migrated and refused as too new. */
    private static boolean isStub(Path file, UUID player) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) return false;
            var root = json.getAsJsonObject();
            return root.size() == 2 && root.has("version") && root.has("movedTo")
                    && root.get("version").isJsonPrimitive() && root.get("version").getAsJsonPrimitive().isNumber()
                    && root.get("version").getAsString().equals(Integer.toString(STUB_VERSION))
                    && root.get("movedTo").isJsonPrimitive() && root.get("movedTo").getAsJsonPrimitive().isString()
                    && root.get("movedTo").getAsString().equals("ja_jp/" + player + ".json");
        } catch (java.nio.charset.CharacterCodingException | RuntimeException e) {
            return false; // Broken bytes are preserved by migration, then handled by the normal reader.
        }
    }

    private static void copyKeepingTime(Path source, Path target) throws IOException {
        var modified = Files.getLastModifiedTime(source);
        AtomicFiles.copy(source, target);
        Files.setLastModifiedTime(target, modified);
    }

    private static Path unusedBroken(Path file) {
        Path target = file.resolveSibling(file.getFileName() + ".broken");
        for (int n = 1; Files.exists(target); n++) target = file.resolveSibling(file.getFileName() + ".broken." + n);
        return target;
    }

    private static FileChannel channel(Path file) throws IOException {
        return FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    private static FileLock lock(FileChannel channel) throws IOException {
        try { return channel.tryLock(); }
        catch (OverlappingFileLockException e) { return null; }
    }
}
