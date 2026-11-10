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
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * The files of one Minecraft version that the name list comes from, downloaded from Mojang the way the launcher does:
 * the Japanese language file, which is one of the game's assets, and the server jar, which has the game's data
 * generator. Mojang gives a SHA-1 checksum for each file, and every download is checked against it.
 *
 * @param languageFiles the supported Minecraft translations
 * @param englishLanguageFile the English translation from the client jar
 * @param serverJar            the server jar of the version
 */
record MinecraftFiles(Map<LearningLanguage, Path> languageFiles, Path englishLanguageFile, Path serverJar) {
    private static final URI VERSION_MANIFEST = URI.create(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String RESOURCES = "https://resources.download.minecraft.net/";

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
        Map<LearningLanguage, Path> languages = new EnumMap<>(LearningLanguage.class);
        for (LearningLanguage language : LearningLanguage.values()) {
            String asset = "minecraft/lang/" + language.code() + ".json";
            JsonObject info = assetIndex.getAsJsonObject("objects").getAsJsonObject(asset);
            if (info == null) throw new IOException("Minecraft " + version + " has no " + asset);
            String hash = info.get("hash").getAsString();
            languages.put(language, downloads.get(URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash),
                    folder + language.code() + ".json", Downloads.Checksum.sha1(hash)));
        }
        JsonObject client = versionInfo.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = downloads.get(URI.create(client.get("url").getAsString()), folder + "client.jar",
                Downloads.Checksum.sha1(client.get("sha1").getAsString()));
        Path english = downloads.cacheFolder().resolve(folder + "en_us.json");
        try (ZipFile jar = new ZipFile(clientJar.toFile());
                var input = jar.getInputStream(jar.getEntry("assets/minecraft/lang/en_us.json"))) {
            Files.copy(input, english, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        JsonObject server = versionInfo.getAsJsonObject("downloads").getAsJsonObject("server");
        Path serverJar = downloads.get(
                URI.create(server.get("url").getAsString()),
                folder + "server.jar",
                Downloads.Checksum.sha1(server.get("sha1").getAsString()));
        return new MinecraftFiles(Map.copyOf(languages), english, serverJar);
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
