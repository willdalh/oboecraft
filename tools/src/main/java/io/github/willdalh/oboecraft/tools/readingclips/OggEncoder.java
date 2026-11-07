package io.github.willdalh.oboecraft.tools.readingclips;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns VOICEVOX's WAV files into Ogg Vorbis, the format Minecraft plays, with FFmpeg. FFmpeg is installed by hand and
 * found on the {@code PATH}. Speech needs little: the clips are mono at VOICEVOX's 24 kHz, at Vorbis quality 1. FFmpeg
 * runs bit-exact, so the same WAV file always gives the same clip, byte for byte.
 */
final class OggEncoder {
    /** Written to the clip list, so a change to the encoding makes every clip again. */
    static final String FORMAT = "Ogg Vorbis, quality 1, mono, 24000 Hz";

    private static final List<String> COMMAND = List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
            "-f", "wav", "-i", "pipe:0", "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
            "-c:a", "libvorbis", "-q:a", "1", "-ac", "1", "-ar", "24000", "-f", "ogg", "-y");

    private OggEncoder() {
    }

    /**
     * Checks that FFmpeg can be run.
     *
     * @throws IOException if it can't
     */
    static void check() throws IOException {
        run(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-version"), new byte[0]);
    }

    /** Writes {@code wav} to {@code file} as Ogg Vorbis. The file is replaced only once the new one is written. */
    static void encode(byte[] wav, Path file) throws IOException {
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        List<String> command = new ArrayList<>(COMMAND);
        command.add(partial.toString());
        run(command, wav);
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void run(List<String> command, byte[] input) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("Couldn't run FFmpeg. Install it and put it on the PATH", e);
        }
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(input);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IOException("FFmpeg failed: " + output.strip());
            }
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for FFmpeg", e);
        }
    }
}
