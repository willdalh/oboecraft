package io.github.willdalh.oboecraft.tools.namelist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The files of one Minecraft version that the name list comes from, downloaded from Mojang the way the launcher does:
 * the Japanese language file, which is one of the game's assets, and the server jar, which has the game's data
 * generator. Mojang gives a SHA-1 checksum for each file, and every download is checked against it.
 *
 * @param japaneseLanguageFile Minecraft's {@code ja_jp.json}
 * @param serverJar            the server jar of the version
 */
record MinecraftFiles(Path japaneseLanguageFile, Path serverJar) {
    private static final URI VERSION_MANIFEST = URI.create(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String RESOURCES = "https://resources.download.minecraft.net/";
    private static final String JAPANESE_LANGUAGE_ASSET = "minecraft/lang/ja_jp.json";

    /**
     * Downloads the files of a Minecraft version, or takes them from the cache.
     *
     * @throws IOException if a download fails, or Mojang has no such version
     */
    static MinecraftFiles download(String version, Downloads downloads) throws IOException {
        JsonObject versionInfo = versionInfo(version, downloads);
        String folder = "minecraft/" + version + "/";

        JsonObject assetIndexInfo = versionInfo.getAsJsonObject("assetIndex");
        JsonObject assetIndex = readJson(downloads.get(
                URI.create(assetIndexInfo.get("url").getAsString()),
                folder + "asset-index.json",
                Downloads.Checksum.sha1(assetIndexInfo.get("sha1").getAsString())));
        JsonObject japanese = assetIndex.getAsJsonObject("objects").getAsJsonObject(JAPANESE_LANGUAGE_ASSET);
        if (japanese == null) {
            throw new IOException("Minecraft " + version + " has no " + JAPANESE_LANGUAGE_ASSET);
        }
        String hash = japanese.get("hash").getAsString();
        Path japaneseLanguageFile = downloads.get(
                URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash),
                folder + "ja_jp.json",
                Downloads.Checksum.sha1(hash));

        JsonObject server = versionInfo.getAsJsonObject("downloads").getAsJsonObject("server");
        Path serverJar = downloads.get(
                URI.create(server.get("url").getAsString()),
                folder + "server.jar",
                Downloads.Checksum.sha1(server.get("sha1").getAsString()));
        return new MinecraftFiles(japaneseLanguageFile, serverJar);
    }

    /** The version's own file from Mojang's list of versions, which says where the other files are. */
    private static JsonObject versionInfo(String version, Downloads downloads) throws IOException {
        String manifestName = "minecraft/version_manifest_v2.json";
        JsonObject entry = findVersion(readJson(downloads.get(VERSION_MANIFEST, manifestName, null)), version);
        if (entry == null) {
            // The cached list may be older than the version.
            entry = findVersion(readJson(downloads.refresh(VERSION_MANIFEST, manifestName)), version);
        }
        if (entry == null) {
            throw new IOException("Mojang has no Minecraft version " + version);
        }
        return readJson(downloads.get(
                URI.create(entry.get("url").getAsString()),
                "minecraft/" + version + "/" + version + ".json",
                Downloads.Checksum.sha1(entry.get("sha1").getAsString())));
    }

    private static JsonObject findVersion(JsonObject manifest, String version) {
        for (JsonElement entry : manifest.getAsJsonArray("versions")) {
            if (entry.getAsJsonObject().get("id").getAsString().equals(version)) {
                return entry.getAsJsonObject();
            }
        }
        return null;
    }

    static JsonObject readJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
