package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The readings of words written with kanji in JMdict, the dictionary data from the Electronic Dictionary Research and
 * Development Group. It checks the readings the text splitter guesses.
 */
final class Jmdict {
    private final Map<String, Set<String>> readingsByWord;
    private final String created;

    private Jmdict(Map<String, Set<String>> readingsByWord, String created) {
        this.readingsByWord = readingsByWord;
        this.created = created;
    }

    /**
     * The readings JMdict gives a word as it is written, in hiragana, or none if JMdict doesn't have the word written
     * that way.
     */
    Set<String> readingsOf(String word) {
        return readingsByWord.getOrDefault(word, Set.of());
    }

    /** The date the JMdict file was made, like {@code 2026-11-06}, or {@code unknown}. */
    String created() {
        return created;
    }

    /**
     * Reads the JMdict XML file. Only the spellings ({@code keb}) and readings ({@code reb}) are kept. A reading
     * belongs to every spelling of its entry, unless it is limited to some of them ({@code re_restr}) or isn't read
     * from the kanji at all ({@code re_nokanji}).
     *
     * @throws IOException if the file isn't JMdict XML
     */
    static Jmdict read(InputStream xml) throws IOException {
        Map<String, Set<String>> readingsByWord = new HashMap<>();
        String created = "unknown";
        try {
            XMLStreamReader reader = DictionaryXml.factory().createXMLStreamReader(xml);
            List<String> spellings = new ArrayList<>();
            List<Reading> readings = new ArrayList<>();
            Reading reading = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.COMMENT && reader.getText().contains("JMdict created:")) {
                    created = reader.getText().replace("JMdict created:", "").strip();
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "entry" -> {
                            spellings.clear();
                            readings.clear();
                        }
                        case "keb" -> spellings.add(reader.getElementText());
                        case "r_ele" -> reading = new Reading();
                        case "reb" -> reading.text = JapaneseText.normalize(reader.getElementText());
                        case "re_nokanji" -> reading.noKanji = true;
                        case "re_restr" -> reading.onlyFor.add(reader.getElementText());
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "r_ele" -> readings.add(reading);
                        case "entry" -> addEntry(spellings, readings, readingsByWord);
                        default -> {
                        }
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            throw new IOException("Couldn't read JMdict", e);
        }
        return new Jmdict(readingsByWord, created);
    }

    private static void addEntry(List<String> spellings, List<Reading> readings, Map<String, Set<String>> into) {
        for (String spelling : spellings) {
            for (Reading reading : readings) {
                if (!reading.noKanji && (reading.onlyFor.isEmpty() || reading.onlyFor.contains(spelling))) {
                    into.computeIfAbsent(spelling, word -> new LinkedHashSet<>()).add(reading.text);
                }
            }
        }
    }

    private static final class Reading {
        String text;
        boolean noKanji;
        final List<String> onlyFor = new ArrayList<>();
    }
}
