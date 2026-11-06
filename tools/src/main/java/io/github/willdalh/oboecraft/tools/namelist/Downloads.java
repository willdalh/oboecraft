package io.github.willdalh.oboecraft.tools.namelist;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Downloads files into a cache folder, so each file is only downloaded once. A file with a known checksum is checked
 * after it is downloaded and before a cached copy is used, so a broken or changed download is never used.
 */
final class Downloads {
    private final Path cacheFolder;
    private final HttpClient client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** A checksum a file must have, like SHA-1 for Mojang's files. */
    record Checksum(String algorithm, String hex) {
        static Checksum sha1(String hex) {
            return new Checksum("SHA-1", hex);
        }

        static Checksum sha256(String hex) {
            return new Checksum("SHA-256", hex);
        }
    }

    Downloads(Path cacheFolder) {
        this.cacheFolder = Objects.requireNonNull(cacheFolder, "cacheFolder");
    }

    Path cacheFolder() {
        return cacheFolder;
    }

    /**
     * The file at a URI, downloaded into the cache unless it is there already.
     *
     * @param fileName the file's name in the cache
     * @param checksum the checksum the file must have, or null if it isn't known. A file without a checksum is
     *     downloaded again only once it is deleted from the cache.
     * @throws IOException if the download fails or the file doesn't have its checksum
     */
    Path get(URI uri, String fileName, Checksum checksum) throws IOException {
        Path file = cacheFolder.resolve(fileName);
        if (Files.exists(file) && (checksum == null || hasChecksum(file, checksum))) {
            return file;
        }
        download(uri, file, checksum);
        return file;
    }

    /** Downloads a file again, even if it is in the cache. */
    Path refresh(URI uri, String fileName) throws IOException {
        Path file = cacheFolder.resolve(fileName);
        download(uri, file, null);
        return file;
    }

    private void download(URI uri, Path file, Checksum checksum) throws IOException {
        Files.createDirectories(file.getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        System.out.println("Downloading " + uri);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Downloading " + uri + " failed with HTTP " + response.statusCode());
                }
                Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Downloading " + uri + " was interrupted", e);
        }
        if (checksum != null && !hasChecksum(partial, checksum)) {
            Files.delete(partial);
            throw new IOException("The file downloaded from " + uri + " doesn't have the " + checksum.algorithm()
                    + " checksum " + checksum.hex());
        }
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static boolean hasChecksum(Path file, Checksum checksum) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(checksum.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(checksum.hex());
    }
}
