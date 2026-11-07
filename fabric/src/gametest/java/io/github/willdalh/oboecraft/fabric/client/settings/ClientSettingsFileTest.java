package io.github.willdalh.oboecraft.fabric.client.settings;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Client settings stay compatible with old files, and invalid sizes never replace a player's stored settings. */
public final class ClientSettingsFileTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String legacy = "{\"version\":1,\"clickableButtons\":true}";
        check(ClientSettingsFile.parse(JsonParser.parseString(legacy)).equals(new ClientSettings(true, 100)),
                "Old files must preserve clickable buttons and use the original image size");

        for (int size : new int[]{50, 100, 300, 400}) {
            check(ClientSettingsFile.parse(JsonParser.parseString(settingsJson(Integer.toString(size))))
                    .equals(new ClientSettings(true, size)), "A supported image size must load");
        }
        for (String size : new String[]{"49", "401", "100.5", "1e100", "true", "\"200\"", "null"}) {
            expectInvalid(settingsJson(size));
        }
        expectInvalid("{\"version\":2,\"clickableButtons\":true}");
        expectInvalid(settingsJson("100").replace("\"version\":2", "\"version\":3"));
        expectInvalid(settingsJson("100").replace("\"clickableButtons\":true", "\"clickableButtons\":\"true\""));
        expectInvalid(settingsJson("100").replace("}", ",\"misspelledSetting\":true}"));

        try {
            Path directory = Files.createTempDirectory("oboecraft-client-settings");
            Path file = directory.resolve("client.json");
            try {
                check(ClientSettingsFile.read(file).equals(ClientSettings.DEFAULTS), "Missing files use defaults");
                Files.writeString(file, legacy);
                check(ClientSettingsFile.read(file).equals(new ClientSettings(true, 100)), "Legacy file on disk");
                check(Files.readString(file).equals(legacy), "Reading must not rewrite a legacy file");

                ClientSettings changed = new ClientSettings(true, 300);
                ClientSettingsFile.write(file, changed);
                check(ClientSettingsFile.read(file).equals(changed), "Size and buttons must survive saving and loading");
                check(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("version").getAsInt() == 2,
                        "Saving upgrades the client file to version 2");

                String invalid = settingsJson("999");
                Files.writeString(file, invalid);
                check(ClientSettingsFile.read(file).equals(ClientSettings.DEFAULTS), "Invalid files use defaults");
                check(Files.readString(file).equals(invalid), "Invalid settings must remain untouched");
            } finally {
                Files.deleteIfExists(file);
                Files.deleteIfExists(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String settingsJson(String size) {
        return "{\"version\":2,\"clickableButtons\":true,\"reviewItemScalePercent\":" + size + "}";
    }

    private static void expectInvalid(String json) {
        try {
            ClientSettingsFile.parse(JsonParser.parseString(json));
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("Invalid client settings were accepted: " + json);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
