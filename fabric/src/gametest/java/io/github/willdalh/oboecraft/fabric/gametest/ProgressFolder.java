package io.github.willdalh.oboecraft.fabric.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/** The client keeps progress in the game folder across worlds, so each client game test starts from no progress. */
final class ProgressFolder {
    private ProgressFolder() {
    }

    static void delete() {
        Path progress = FabricLoader.getInstance().getGameDir().resolve("oboecraft");
        if (!Files.exists(progress)) {
            return;
        }
        try (Stream<Path> files = Files.walk(progress)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
