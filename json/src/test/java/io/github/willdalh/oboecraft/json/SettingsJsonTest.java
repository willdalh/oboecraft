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
              "version": 3,
              "levels": [
                {"interval": "1h 30m", "demotion": 1},
                {"interval": "2d", "demotion": 0}
              ],
              "banLength": "30s",
              "percentageFactor": 10,
              "dropCountsAsInteraction": true,
              "tsukumogamiAppearThreshold": 8,
              "tsukumogamiFadeThreshold": 3,
              "tsukumogamiReviewFreeze": "1m 30s"
            }
            """;
    /** A file from before the fade threshold, with one tsukumogami threshold. */
    private static final String VERSION_2 = """
            {
              "version": 2,
              "levels": [
                {"interval": "1h 30m", "demotion": 1}
              ],
              "banLength": "30s",
              "percentageFactor": 10,
              "dropCountsAsInteraction": true,
              "tsukumogamiThreshold": 8,
              "tsukumogamiReviewFreeze": "1m 30s"
            }
            """;
    /** A file from before the tsukumogami. */
    private static final String VERSION_1 = """
            {
              "version": 1,
              "levels": [
                {"interval": "1h 30m", "demotion": 1}
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
        assertEquals(8, settings.tsukumogamiAppearThreshold());
        assertEquals(3, settings.tsukumogamiFadeThreshold());
        assertEquals(Duration.ofSeconds(90), settings.tsukumogamiReviewFreeze());
    }

    @Test
    void readsAVersion2FilesThresholdAsTheAppearThresholdWithTheDefaultFadeThreshold() {
        Settings settings = parse(VERSION_2);

        assertEquals(8, settings.tsukumogamiAppearThreshold());
        assertEquals(Settings.DEFAULTS.tsukumogamiFadeThreshold(), settings.tsukumogamiFadeThreshold());
        assertEquals(Duration.ofSeconds(90), settings.tsukumogamiReviewFreeze());
    }

    @Test
    void aVersion2FileWithAThresholdNotAboveTheDefaultFadeThresholdFadesOneBelowIt() {
        Settings settings = parse(VERSION_2.replace("\"tsukumogamiThreshold\": 8", "\"tsukumogamiThreshold\": 5"));

        assertEquals(5, settings.tsukumogamiAppearThreshold());
        assertEquals(4, settings.tsukumogamiFadeThreshold());
    }

    @Test
    void aVersion2FileWithTheTsukumogamiTurnedOffKeepsItOff() {
        Settings settings = parse(VERSION_2.replace("\"tsukumogamiThreshold\": 8", "\"tsukumogamiThreshold\": 0"));

        assertEquals(0, settings.tsukumogamiAppearThreshold());
        assertEquals(0, settings.tsukumogamiFadeThreshold());
    }

    @Test
    void readsAVersion1FileWithTheDefaultTsukumogamiSettings() {
        Settings settings = parse(VERSION_1);

        assertEquals(List.of(new LevelSettings(Duration.ofMinutes(90), 1)), settings.levels());
        assertTrue(settings.dropCountsAsInteraction());
        assertEquals(Settings.DEFAULTS.tsukumogamiAppearThreshold(), settings.tsukumogamiAppearThreshold());
        assertEquals(Settings.DEFAULTS.tsukumogamiFadeThreshold(), settings.tsukumogamiFadeThreshold());
        assertEquals(Settings.DEFAULTS.tsukumogamiReviewFreeze(), settings.tsukumogamiReviewFreeze());
    }

    @Test
    void writesVersion3() {
        String text = SettingsJson.format(Settings.DEFAULTS.withTsukumogamiReviewFreeze(Duration.ZERO));

        assertTrue(text.contains("\"version\": 3"), text);
        assertTrue(text.contains("\"tsukumogamiAppearThreshold\": 10"), text);
        assertTrue(text.contains("\"tsukumogamiFadeThreshold\": 5"), text);
        assertTrue(text.contains("\"tsukumogamiReviewFreeze\": \"0s\""), text);
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
                CUSTOM.replace("\"version\": 3", "\"version\": 4"),
                CUSTOM.replace("\"version\": 3", "\"version\": 0"),
                CUSTOM.replace("\"version\": 3", "\"version\": 2"),
                CUSTOM.replace("\"version\": 3", "\"version\": 1"),
                VERSION_1.replace("\"version\": 1", "\"version\": 2"),
                VERSION_2.replace("\"version\": 2", "\"version\": 3"),
                CUSTOM.replace("\"version\": 3,", ""),
                CUSTOM.replace("\"tsukumogamiAppearThreshold\": 8,", ""),
                CUSTOM.replace("\"tsukumogamiFadeThreshold\": 3,", ""),
                CUSTOM.replace("\"tsukumogamiAppearThreshold\": 8", "\"tsukumogamiAppearThreshold\": -1"),
                CUSTOM.replace("\"tsukumogamiAppearThreshold\": 8", "\"tsukumogamiAppearThreshold\": 1001"),
                CUSTOM.replace("\"tsukumogamiFadeThreshold\": 3", "\"tsukumogamiFadeThreshold\": 8"),
                CUSTOM.replace("\"tsukumogamiFadeThreshold\": 3", "\"tsukumogamiFadeThreshold\": -1"),
                VERSION_2.replace("\"tsukumogamiThreshold\": 8", "\"tsukumogamiThreshold\": -1"),
                VERSION_2.replace("\"tsukumogamiThreshold\": 8", "\"tsukumogamiThreshold\": 1001"),
                CUSTOM.replace("\"1m 30s\"", "\"2h\""),
                CUSTOM.replace("\"1m 30s\"", "90"),
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
