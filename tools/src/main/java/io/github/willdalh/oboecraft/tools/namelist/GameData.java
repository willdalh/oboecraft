package io.github.willdalh.oboecraft.tools.namelist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * What the game itself says about item names, read with Minecraft's own data generator and from the data in the server
 * jar.
 *
 * @param defaultNames every Minecraft item's default name, in the order the game registers the items
 * @param dataNames    item names that the game's data gives stacks, like the names of the maps found in abandoned
 *                     camps, in the order they are found
 */
record GameData(List<ItemNameKey> defaultNames, List<ItemNameKey> dataNames) {
    /** Air is the empty stack, which is never an item. */
    static final String AIR = "minecraft:air";

    /**
     * Runs Minecraft's data generator on a server jar, unless its reports are in the work folder already, and reads the
     * reports and the jar's data.
     *
     * @throws IOException if the data generator fails or its reports can't be read
     */
    static GameData read(Path serverJar, Path workFolder) throws IOException {
        Path reports = workFolder.resolve("generated/reports");
        if (!Files.exists(reports.resolve("registries.json"))) {
            runDataGenerator(serverJar, workFolder);
        }
        return new GameData(readDefaultNames(reports), readDataNames(serverJar));
    }

    /**
     * Runs Minecraft's data generator with {@code --reports}. It writes a report of every item's default components,
     * which hold the item's name, and of every registry, which gives the order of the items. The data generator is
     * part of the server jar and needs no EULA.
     */
    private static void runDataGenerator(Path serverJar, Path workFolder) throws IOException {
        Files.createDirectories(workFolder);
        String java = ProcessHandle.current().info().command().orElse("java");
        Path log = workFolder.resolve("data-generator.log");
        System.out.println("Running Minecraft's data generator, see " + log);
        Process process = new ProcessBuilder(
                java, "-DbundlerMainClass=net.minecraft.data.Main", "-jar", serverJar.toAbsolutePath().toString(),
                "--reports", "--output", "generated")
                .directory(workFolder.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Minecraft's data generator failed with exit code " + exitCode + ", see " + log);
            }
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("Minecraft's data generator was interrupted", e);
        }
    }

    /** Each item's default name from the item component reports, in the order the items are registered. */
    static List<ItemNameKey> readDefaultNames(Path reports) throws IOException {
        JsonObject items = MinecraftFiles.readJson(reports.resolve("registries.json"))
                .getAsJsonObject("minecraft:item")
                .getAsJsonObject("entries");
        List<Map.Entry<String, JsonElement>> inOrder = new ArrayList<>(items.entrySet());
        inOrder.sort(Comparator.comparingInt(
                entry -> entry.getValue().getAsJsonObject().get("protocol_id").getAsInt()));

        List<ItemNameKey> names = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : inOrder) {
            String item = entry.getKey();
            if (item.equals(AIR)) {
                continue;
            }
            String[] namespaceAndPath = item.split(":", 2);
            Path components =
                    reports.resolve(namespaceAndPath[0] + "/components/item/" + namespaceAndPath[1] + ".json");
            JsonElement name = MinecraftFiles.readJson(components)
                    .getAsJsonObject("components")
                    .get("minecraft:item_name");
            String key = translationKeyOf(name)
                    .orElseThrow(() -> new IOException("The item " + item + " has no translatable name"));
            names.add(new ItemNameKey(key, item));
        }
        return names;
    }

    /** The item names that the data in the server jar gives stacks, like loot tables that rename a map. */
    private static List<ItemNameKey> readDataNames(Path serverJar) throws IOException {
        SequencedSet<ItemNameKey> names = new LinkedHashSet<>();
        try (ZipFile bundler = new ZipFile(serverJar.toFile());
                ZipInputStream server = new ZipInputStream(openServerJar(bundler))) {
            for (ZipEntry entry = server.getNextEntry(); entry != null; entry = server.getNextEntry()) {
                if (entry.getName().startsWith("data/") && entry.getName().endsWith(".json")) {
                    JsonElement json = JsonParser.parseReader(new InputStreamReader(server, StandardCharsets.UTF_8));
                    collectItemNames(json, null, names);
                }
            }
        }
        return List.copyOf(names);
    }

    /**
     * The server jar from Mojang is a bundler with the real server jar inside it, which {@code versions.list}
     * names.
     */
    private static InputStream openServerJar(ZipFile bundler) throws IOException {
        ZipEntry versions = bundler.getEntry("META-INF/versions.list");
        if (versions == null) {
            throw new IOException("The server jar has no META-INF/versions.list");
        }
        String path;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(bundler.getInputStream(versions), StandardCharsets.UTF_8))) {
            path = reader.readLine().split("\t")[2];
        }
        ZipEntry server = bundler.getEntry("META-INF/versions/" + path);
        if (server == null) {
            throw new IOException("The server jar has no META-INF/versions/" + path);
        }
        return bundler.getInputStream(server);
    }

    /**
     * Finds the item names in a data file: a loot function that sets a stack's item name, like
     * {@code {"function": "minecraft:set_name", "target": "item_name", "name": {"translate": "..."}}}, and a stack's
     * {@code minecraft:item_name} component, like an advancement's icon. The item is the nearest stack or loot entry
     * around the name, if there is one.
     */
    static void collectItemNames(JsonElement json, String item, SequencedSet<ItemNameKey> names) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collectItemNames(element, item, names));
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        String itemHere = itemOf(object).orElse(item);
        if (isString(object.get("function"), "minecraft:set_name") && isString(object.get("target"), "item_name")) {
            translationKeyOf(object.get("name")).ifPresent(key -> names.add(new ItemNameKey(key, itemHere)));
        }
        translationKeyOf(object.get("minecraft:item_name"))
                .ifPresent(key -> names.add(new ItemNameKey(key, itemHere)));
        object.entrySet().forEach(entry -> collectItemNames(entry.getValue(), itemHere, names));
    }

    /** The item of a stack, which has an {@code id}, or of a loot entry, which has a {@code name}. */
    private static Optional<String> itemOf(JsonObject object) {
        if (object.get("id") instanceof JsonElement id && id.isJsonPrimitive()) {
            return Optional.of(id.getAsString());
        }
        if (isString(object.get("type"), "minecraft:item") && object.get("name") instanceof JsonElement name
                && name.isJsonPrimitive()) {
            return Optional.of(name.getAsString());
        }
        return Optional.empty();
    }

    /** The key of a translatable text, like {@code {"translate": "block.minecraft.oak_log"}}. */
    private static Optional<String> translationKeyOf(JsonElement text) {
        if (text != null && text.isJsonObject() && text.getAsJsonObject().get("translate") instanceof JsonElement key
                && key.isJsonPrimitive()) {
            return Optional.of(key.getAsString());
        }
        return Optional.empty();
    }

    private static boolean isString(JsonElement json, String value) {
        return json != null && json.isJsonPrimitive() && json.getAsString().equals(value);
    }
}
