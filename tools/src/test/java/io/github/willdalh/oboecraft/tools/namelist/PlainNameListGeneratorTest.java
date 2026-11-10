package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.*;
import io.github.willdalh.oboecraft.core.item.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlainNameListGeneratorTest {
    @Test void namesAreGroupedAndFormatArgumentsJoinTheirDefaultItem() {
        var keys = List.of(new ItemNameKey("head", "head"), new ItemNameKey("named", "head"),
                new ItemNameKey("arrow1", "arrow"), new ItemNameKey("arrow2", "arrow"));
        var names = Map.of("head", "Spielerkopf", "named", "Kopf von %s", "arrow1", "Getränkter Pfeil", "arrow2", "Getränkter Pfeil");
        var result = NameListGenerator.generatePlain(keys, names, Map.of(), List.of());
        assertEquals(2, result.entries().size());
        assertEquals(List.of("head", "named"), result.entries().getFirst().translationKeys());
        assertTrue(result.generated().getFirst().notes().stream().anyMatch(note -> note.contains("Format argument")));
    }

    @Test void reviewedCorrectionsAndIdsSurviveUntilTheSourceChanges() {
        var keys = List.of(new ItemNameKey("item", "item"));
        var corrected = new NameListEntry(new ItemId("old-id"), List.of("item"), new PlainName("Correct", "Broken"));
        var result = NameListGenerator.generatePlain(keys, Map.of("item", "Broken"), Map.of(), List.of(corrected));
        assertEquals(List.of(corrected), result.entries());
        assertTrue(result.generated().isEmpty());
        var changed = NameListGenerator.generatePlain(keys, Map.of("item", "Changed"), Map.of(), List.of(corrected));
        assertEquals(corrected.id(), changed.entries().getFirst().id());
        assertEquals("Changed", changed.entries().getFirst().name().text());
        assertEquals(1, changed.generated().size());
    }

    @Test void untranslatedNamesAndSplitsAreFlaggedEvenWhenTheNameIsUnchanged() {
        var keys = List.of(new ItemNameKey("one", "one"), new ItemNameKey("two", "two"));
        var existing = new NameListEntry(new ItemId("one"), List.of("one", "two"), new PlainName("Sand"));
        var result = NameListGenerator.generatePlain(keys, Map.of("one", "Anders", "two", "Sand"),
                Map.of("two", "Sand"), List.of(existing));
        assertEquals(2, result.generated().size());
        var split = result.generated().getLast();
        assertTrue(split.notes().stream().anyMatch(note -> note.contains("Split from")));
        assertTrue(split.notes().stream().anyMatch(note -> note.contains("Same as English")));
    }

    @Test void mergingExistingItemsStillRequiresFormerIdsWithinThatLanguage() {
        var keys = List.of(new ItemNameKey("one", "one"), new ItemNameKey("two", "two"));
        var existing = List.of(new NameListEntry(new ItemId("one"), List.of("one"), new PlainName("Eins")),
                new NameListEntry(new ItemId("two"), List.of("two"), new PlainName("Zwei")));
        assertThrows(IllegalStateException.class, () -> NameListGenerator.generatePlain(keys,
                Map.of("one", "Gleich", "two", "Gleich"), Map.of(), existing));
    }
}
