package io.github.willdalh.oboecraft.fabric.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.Oboecraft;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Every sound event of the tsukumogami plays the mod's own sounds: it is in the mod's {@code sounds.json}, and each
 * sound it lists is a file of the mod's resources, not a vanilla sound event.
 */
public class TsukumogamiSoundsClientGameTest implements FabricClientGameTest {
    private static final String EVENT_PREFIX = "entity.tsukumogami.";

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> problems = context.computeOnClient(client -> {
            ResourceManager resources = client.getResourceManager();
            JsonObject soundsJson = soundsJson(resources);
            List<String> found = new ArrayList<>();
            List<Identifier> events = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                    .filter(id -> id.getNamespace().equals(Oboecraft.MOD_ID) && id.getPath().startsWith(EVENT_PREFIX))
                    .toList();
            if (events.isEmpty()) {
                found.add("no tsukumogami sound events are registered");
            }
            for (Identifier event : events) {
                JsonObject entry = soundsJson.getAsJsonObject(event.getPath());
                if (entry == null) {
                    found.add(event + " is not in sounds.json");
                    continue;
                }
                for (JsonElement sound : entry.getAsJsonArray("sounds")) {
                    JsonObject soundObject = sound.getAsJsonObject();
                    Identifier name = Identifier.parse(soundObject.get("name").getAsString());
                    if (soundObject.has("type")) {
                        found.add(event + " plays the sound event " + name + ", not a sound file");
                    } else if (resources.getResource(Sound.SOUND_LISTER.idToFile(name)).isEmpty()) {
                        found.add(event + " plays " + name + ", which has no file");
                    }
                }
            }
            return found;
        });
        if (!problems.isEmpty()) {
            throw new AssertionError("The tsukumogami's sounds are incomplete: " + problems);
        }
    }

    private static JsonObject soundsJson(ResourceManager resources) {
        Identifier id = Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "sounds.json");
        try (Reader reader = resources.getResourceOrThrow(id).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
