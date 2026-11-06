package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The readings of each kanji in KANJIDIC2, the kanji data from the Electronic Dictionary Research and Development
 * Group. It splits a word's reading between its kanji.
 */
final class Kanjidic {
    private final Map<Integer, Set<String>> readingsByKanji;
    private final String version;

    private Kanjidic(Map<Integer, Set<String>> readingsByKanji, String version) {
        this.readingsByKanji = readingsByKanji;
        this.version = version;
    }

    /** Builds the data from readings already in the form {@link #readingsOf} gives them, for tests. */
    static Kanjidic of(Map<String, Set<String>> readingsByKanji) {
        Map<Integer, Set<String>> byCodePoint = new HashMap<>();
        readingsByKanji.forEach((kanji, readings) -> byCodePoint.put(kanji.codePointAt(0), Set.copyOf(readings)));
        return new Kanjidic(byCodePoint, "test");
    }

    /**
     * The readings of a kanji in hiragana, or none if KANJIDIC2 doesn't have it. A kun reading with okurigana, like
     * {@code や.く} for 焼, gives its stem {@code や}, the whole word {@code やく} for when the okurigana isn't written,
     * and for a verb its stem form {@code やき}, as in 焼鳥.
     */
    Set<String> readingsOf(int kanji) {
        return readingsByKanji.getOrDefault(kanji, Set.of());
    }

    /** The version of the KANJIDIC2 file, like {@code 2026-310}, or {@code unknown}. */
    String version() {
        return version;
    }

    /**
     * Reads the KANJIDIC2 XML file. Only the Japanese on and kun readings are kept, not the readings used only in
     * names ({@code nanori}).
     *
     * @throws IOException if the file isn't KANJIDIC2 XML
     */
    static Kanjidic read(InputStream xml) throws IOException {
        Map<Integer, Set<String>> readingsByKanji = new HashMap<>();
        String version = "unknown";
        try {
            XMLStreamReader reader = DictionaryXml.factory().createXMLStreamReader(xml);
            Set<String> readings = null;
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (reader.getLocalName()) {
                    case "database_version" -> version = reader.getElementText().strip();
                    case "literal" -> {
                        readings = new LinkedHashSet<>();
                        readingsByKanji.put(reader.getElementText().codePointAt(0), readings);
                    }
                    case "reading" -> {
                        String type = reader.getAttributeValue(null, "r_type");
                        if (type.equals("ja_on") || type.equals("ja_kun")) {
                            addReading(reader.getElementText(), readings);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            throw new IOException("Couldn't read KANJIDIC2", e);
        }
        return new Kanjidic(readingsByKanji, version);
    }

    /**
     * Adds a reading as KANJIDIC2 writes it: on readings in katakana, and kun readings in hiragana with a dot before
     * the okurigana and a dash where the kanji is a prefix or suffix, like {@code -や.き}.
     */
    static void addReading(String written, Set<String> into) {
        String withoutDashes = written.replace("-", "");
        // The dot isn't a letter, so normalizing leaves it out.
        String reading = JapaneseText.normalize(withoutDashes);
        int dot = withoutDashes.indexOf('.');
        if (dot < 0) {
            into.add(reading);
            return;
        }
        String stem = reading.substring(0, dot);
        into.add(stem);
        into.add(reading);
        String stemForm = stemFormOf(reading);
        if (stemForm != null) {
            into.add(stemForm);
        }
    }

    /**
     * The stem form (連用形) of a verb, like {@code やき} for {@code やく}: the last kana moves from the u row to the i
     * row. Ichidan verbs, which end in {@code る}, also drop the {@code る}, but those are often godan too, so both are
     * tried: {@code いる} gives {@code いり} here and {@code い} as the stem. Null if the word doesn't end in the u row.
     */
    private static String stemFormOf(String verb) {
        char last = verb.charAt(verb.length() - 1);
        int row = "うくぐすつぬぶむる".indexOf(last);
        if (row < 0) {
            return null;
        }
        return verb.substring(0, verb.length() - 1) + "いきぎしちにびみり".charAt(row);
    }
}
