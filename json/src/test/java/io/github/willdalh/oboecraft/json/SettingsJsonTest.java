package io.github.willdalh.oboecraft.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.settings.LevelSettings;
import io.github.willdalh.oboecraft.core.settings.Settings;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsJsonTest {
    private static final String CUSTOM = """
            {
              "version": 1,
              "levels": [
                {"interval": "1h 30m", "demotion": 1},
                {"interval": "2d", "demotion": 0}
              ],
              "banLength": "30s",
              "percentageFactor": 10,
              "dropCountsAsInteraction": true
            }
            """;

    private static Settings parse(String text) {
        return SettingsJson.parse(JsonParser.parseString(text));
    }

    @Test
    void readsEveryField() {
        Settings settings = parse(CUSTOM);

        assertEquals(List.of(new LevelSettings(Duration.ofMinutes(90), 1), new LevelSettings(Duration.ofDays(2), 0)),
                settings.levels());
        assertEquals(Duration.ofSeconds(30), settings.banLength());
        assertEquals(10, settings.percentageFactor());
        assertTrue(settings.dropCountsAsInteraction());
    }

    @Test
    void theDefaultsAreWrittenAndReadBackTheSame() {
        String text = SettingsJson.format(Settings.DEFAULTS);

        assertTrue(text.contains("\"interval\": \"10m\""), text);
        assertTrue(text.contains("\"banLength\": \"5m\""), text);
        assertEquals(Settings.DEFAULTS, parse(text));
        Settings custom = parse(CUSTOM);
        assertEquals(custom, parse(SettingsJson.format(custom)));
    }

    @Test
    void rejectsAFileThatIsntValidSettings() {
        String[] invalid = {
                "[]",
                CUSTOM.replace("\"version\": 1", "\"version\": 2"),
                CUSTOM.replace("\"banLength\": \"30s\",", ""),
                CUSTOM.replace("\"banLength\"", "\"banLenght\""),
                CUSTOM.replace("\"percentageFactor\": 10", "\"percentageFactor\": 10.5"),
                CUSTOM.replace("\"percentageFactor\": 10", "\"percentageFactor\": \"10\""),
                CUSTOM.replace("\"percentageFactor\": 10", "\"percentageFactor\": -1"),
                CUSTOM.replace("\"dropCountsAsInteraction\": true", "\"dropCountsAsInteraction\": 1"),
                CUSTOM.replace("\"1h 30m\"", "\"90 minutes\""),
                CUSTOM.replace("\"1h 30m\"", "\"0s\""),
                CUSTOM.replace("\"demotion\": 0", "\"demotion\": -1"),
                CUSTOM.replace("\"demotion\": 0", "\"demotion\": 0, \"extra\": 1"),
                "{\"version\": 1, \"levels\": \"10m\", \"banLength\": \"5m\", \"percentageFactor\": 5, "
                        + "\"dropCountsAsInteraction\": false}",
                CUSTOM.replaceAll("\\{\"interval\": \"1h 30m\", \"demotion\": 1},\\s*\\{\"interval\": \"2d\", "
                        + "\"demotion\": 0}", ""),
        };
        for (String text : invalid) {
            assertThrows(IllegalArgumentException.class, () -> parse(text), text);
        }
    }

    @Test
    void saysWhichLevelIsInvalid() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> parse(CUSTOM.replace("\"2d\"", "\"2 days\"")));

        assertTrue(error.getMessage().startsWith("Level 2 is invalid"), error.getMessage());
    }
}
