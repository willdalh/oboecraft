package io.github.willdalh.oboecraft.fabric.item;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.NameList;
import io.github.willdalh.oboecraft.core.item.LearningLanguage;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.json.NameListJson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads the name list that ships in the mod's resources, in the format of {@link NameListJson}. It is read through
 * Fabric's mod container, so it works the same on a dedicated server, which has no client assets.
 */
public final class NameListFile {
    /** Where the name list is, relative to the root of the mod jar. */
    public static final String FOLDER = "oboecraft/name_lists/";

    private NameListFile() {
    }

    /**
     * Loads the name list from the mod's resources.
     *
     * @throws IllegalStateException if the file is missing or invalid. It ships with the mod, so this is a bug.
     */
    public static NameList load(LearningLanguage language) {
        String resource = FOLDER + language.code() + ".json";
        Path path = FabricLoader.getInstance()
                .getModContainer(Oboecraft.MOD_ID)
                .flatMap(mod -> mod.findPath(resource))
                .orElseThrow(() -> new IllegalStateException("The mod has no " + resource));
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            NameList list = NameListJson.parse(JsonParser.parseReader(reader));
            if (list.entries().stream().anyMatch(entry -> (entry.name() instanceof JapaneseName) != language.hasReadings())) {
                throw new IllegalArgumentException("The name format doesn't match " + language);
            }
            return list;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Couldn't load the name list from " + resource, e);
        }
    }
}
