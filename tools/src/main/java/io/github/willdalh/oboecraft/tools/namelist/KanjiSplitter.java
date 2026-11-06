package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits a word written with kanji into name parts, one per kanji where the reading can be split between them, using
 * the readings of each kanji in KANJIDIC2. For example, 原木 (げんぼく) becomes 原 (げん) and 木 (ぼく), and 焼き鳥
 * (やきとり) becomes 焼 (や), き and 鳥 (とり). Kanji whose readings don't add up to the word's reading, like 松明
 * (たいまつ), become one part.
 *
 * <p>A kanji's reading in a word can change from its own reading: the first kana can get a dakuten or handakuten
 * (rendaku, like ほん in 一本 read ぽん), and the last kana can become a small っ (like いち in 一本 read いっ). These are
 * allowed, but a split with fewer changes is preferred.
 */
final class KanjiSplitter {
    /** The counters ヶ and ヵ need their reading given like a kanji. */
    private static final Map<Character, List<String>> COUNTER_READINGS = Map.of(
            'ヶ', List.of("か", "が", "こ", "け"),
            'ヵ', List.of("か", "が", "こ", "け"));
    private static final char REPEAT_MARK = '々';
    private static final String VOICED = "かがきぎくぐけげこごさざしじすずせぜそぞただちぢちじつづつずてでとど"
            + "はばはぱひびひぴふぶふぷへべへぺほぼほぽ";
    private static final String GEMINATING = "つちくき";

    private static final int SOUND_CHANGE_COST = 1;
    /**
     * A kanji that is given a reading KANJIDIC2 doesn't have. It costs more than two kanji read together, since a
     * reading that doesn't come from the kanji usually belongs to the whole word, like 硫黄 (いおう), whose 硫 is never
     * read い on its own.
     */
    private static final int UNKNOWN_READING_COST = 25;
    /** Each kanji in a group of kanji read together. */
    private static final int READ_TOGETHER_COST_PER_KANJI = 10;

    private final Kanjidic kanjidic;

    KanjiSplitter(Kanjidic kanjidic) {
        this.kanjidic = Objects.requireNonNull(kanjidic, "kanjidic");
    }

    /**
     * The name parts of a word and what a reviewer should look at.
     *
     * @param parts the parts, with kana written in the word as parts of their own
     * @param notes what isn't sure about the split, empty if every kanji has a reading from KANJIDIC2
     */
    record Split(List<NamePart> parts, List<String> notes) {
    }

    /** One piece of the word: a kanji, which needs a reading, or kana, which is read as written. */
    private record Piece(String text, boolean kanji, String kana) {
    }

    /** The best way found to read the word up to a piece and a place in the reading. */
    private record Step(int cost, int ways, int fromPiece, int fromPosition, Reading part, String note) {
    }

    /** Some of the word's text and how it is read here. */
    private record Reading(String text, String reading) {
    }

    /**
     * Splits a word.
     *
     * @param word    the word as written, with at least one kanji
     * @param reading the word's reading in hiragana
     */
    Split split(String word, String reading) {
        List<Piece> pieces = piecesOf(word);
        int length = reading.length();
        // steps[piece][position]: the best way to read the first `piece` pieces as reading[0, position).
        Step[][] steps = new Step[pieces.size() + 1][length + 1];
        steps[0][0] = new Step(0, 1, -1, -1, null, null);
        for (int piece = 0; piece < pieces.size(); piece++) {
            for (int position = 0; position <= length; position++) {
                if (steps[piece][position] != null) {
                    extend(pieces, piece, reading, position, steps);
                }
            }
        }
        Step last = steps[pieces.size()][length];
        if (last == null) {
            return new Split(List.of(new NamePart(word, reading)),
                    List.of(word + " can't be split by its reading " + reading));
        }

        List<Reading> readings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (last.ways() > 1) {
            notes.add(word + " (" + reading + ") can be split between its kanji in more than one way");
        }
        int piece = pieces.size();
        int position = length;
        while (piece > 0) {
            Step step = steps[piece][position];
            readings.addFirst(step.part());
            if (step.note() != null) {
                notes.addFirst(step.note());
            }
            piece = step.fromPiece();
            position = step.fromPosition();
        }
        return new Split(partsOf(readings), List.copyOf(notes));
    }

    /**
     * A rough reading for a word that the text splitter has no reading for: each kanji's first reading in KANJIDIC2,
     * which is an on reading if it has one, and kana as written.
     *
     * @throws IllegalArgumentException if KANJIDIC2 has no reading for one of the kanji
     */
    String guessReading(String word) {
        StringBuilder reading = new StringBuilder();
        List<Piece> pieces = piecesOf(word);
        for (int piece = 0; piece < pieces.size(); piece++) {
            Piece current = pieces.get(piece);
            if (current.kanji()) {
                String first = candidatesFor(pieces, piece).keySet().stream().findFirst().orElseThrow(
                        () -> new IllegalArgumentException("KANJIDIC2 has no reading for " + current.text()));
                reading.append(first);
            } else {
                reading.append(current.kana());
            }
        }
        return reading.toString();
    }

    /** Tries every way to read the piece at a place in the reading. */
    private void extend(List<Piece> pieces, int piece, String reading, int position, Step[][] steps) {
        Piece current = pieces.get(piece);
        Step from = steps[piece][position];
        if (!current.kanji()) {
            if (reading.startsWith(current.kana(), position)) {
                record(steps, piece + 1, position + current.kana().length(),
                        from, piece, position, new Reading(current.text(), current.kana()), 0, null);
            }
            return;
        }

        // A part's reading can't start with a kana that belongs to the part before it.
        if (!canStartAPart(reading, position)) {
            return;
        }
        for (Map.Entry<String, Integer> candidate : candidatesFor(pieces, piece).entrySet()) {
            String kanjiReading = candidate.getKey();
            if (reading.startsWith(kanjiReading, position)) {
                record(steps, piece + 1, position + kanjiReading.length(), from, piece, position,
                        new Reading(current.text(), kanjiReading), candidate.getValue(), null);
            }
        }
        // The kanji, or it and the kanji after it, read in a way KANJIDIC2 doesn't know.
        for (int end = piece + 1; end <= pieces.size() && pieces.get(end - 1).kanji(); end++) {
            int kanjiCount = end - piece;
            StringBuilder text = new StringBuilder();
            pieces.subList(piece, end).forEach(each -> text.append(each.text()));
            int cost = kanjiCount == 1 ? UNKNOWN_READING_COST : READ_TOGETHER_COST_PER_KANJI * kanjiCount;
            for (int readingEnd = position + kanjiCount; readingEnd <= reading.length(); readingEnd++) {
                String partReading = reading.substring(position, readingEnd);
                String note = kanjiCount == 1
                        ? text + " is read " + partReading + ", which KANJIDIC2 doesn't have"
                        : text + " is read together as " + partReading;
                record(steps, end, readingEnd, from, piece, position,
                        new Reading(text.toString(), partReading), cost, note);
            }
        }
    }

    /**
     * Turns the readings into parts. Kana that can't start a part, like the ん in 赤ん坊, joins the part before it, so
     * that becomes 赤ん (あかん) and 坊 (ぼう).
     */
    private static List<NamePart> partsOf(List<Reading> readings) {
        List<Reading> joined = new ArrayList<>();
        for (Reading reading : readings) {
            if (!joined.isEmpty() && !canStartAPart(reading.reading(), 0)) {
                Reading before = joined.removeLast();
                joined.add(new Reading(before.text() + reading.text(), before.reading() + reading.reading()));
            } else {
                joined.add(reading);
            }
        }
        return joined.stream().map(reading -> new NamePart(reading.text(), reading.reading())).toList();
    }

    private static boolean canStartAPart(String reading, int position) {
        if (position >= reading.length()) {
            return false;
        }
        char first = reading.charAt(position);
        return !JapaneseText.isSmallKana(first) && first != JapaneseText.LONG_VOWEL_MARK && first != 'ん';
    }

    /**
     * Records a way to read up to a piece and position if it is the best so far. Each call is a different way, so an
     * equally good one only adds to the count of best ways, which is capped at 2 since it only matters whether there
     * is more than one.
     */
    private static void record(Step[][] steps, int piece, int position, Step from, int fromPiece, int fromPosition,
            Reading part, int cost, String note) {
        int total = from.cost() + cost;
        Step best = steps[piece][position];
        if (best == null || total < best.cost()) {
            steps[piece][position] = new Step(total, from.ways(), fromPiece, fromPosition, part, note);
        } else if (total == best.cost()) {
            steps[piece][position] = new Step(best.cost(), Math.min(best.ways() + from.ways(), 2), best.fromPiece(),
                    best.fromPosition(), best.part(), best.note());
        }
    }

    /** The readings a kanji may have here, each with the cost of the sound changes it needs. */
    private Map<String, Integer> candidatesFor(List<Piece> pieces, int piece) {
        String text = pieces.get(piece).text();
        Map<String, Integer> candidates = new LinkedHashMap<>();
        List<String> counter = COUNTER_READINGS.get(text.charAt(0));
        if (counter != null) {
            counter.forEach(reading -> candidates.put(reading, 0));
            return candidates;
        }
        int kanji = text.codePointAt(0);
        if (kanji == REPEAT_MARK && piece > 0 && pieces.get(piece - 1).kanji()) {
            kanji = pieces.get(piece - 1).text().codePointAt(0);
        }
        boolean first = piece == 0;
        for (String reading : kanjidic.readingsOf(kanji)) {
            if (reading.isEmpty()) {
                continue;
            }
            addCandidate(candidates, reading, 0);
            if (!first) {
                voicedOf(reading).forEach(voiced -> addCandidate(candidates, voiced, SOUND_CHANGE_COST));
            }
            String geminated = geminatedOf(reading);
            if (geminated != null) {
                addCandidate(candidates, geminated, SOUND_CHANGE_COST);
                if (!first) {
                    voicedOf(geminated).forEach(both -> addCandidate(candidates, both, 2 * SOUND_CHANGE_COST));
                }
            }
        }
        return candidates;
    }

    private static void addCandidate(Map<String, Integer> candidates, String reading, int cost) {
        candidates.merge(reading, cost, Math::min);
    }

    /** The reading with a dakuten or handakuten on its first kana, as in rendaku. */
    private static List<String> voicedOf(String reading) {
        List<String> voiced = new ArrayList<>();
        char first = reading.charAt(0);
        for (int i = 0; i < VOICED.length(); i += 2) {
            if (VOICED.charAt(i) == first) {
                voiced.add(VOICED.charAt(i + 1) + reading.substring(1));
            }
        }
        return voiced;
    }

    /** The reading with its last kana as a small っ, as in いち read いっ, or null if it doesn't end in つちくき. */
    private static String geminatedOf(String reading) {
        if (reading.length() < 2 || GEMINATING.indexOf(reading.charAt(reading.length() - 1)) < 0) {
            return null;
        }
        return reading.substring(0, reading.length() - 1) + "っ";
    }

    /** Splits a word into kanji, each its own piece, and runs of kana with any punctuation in them. */
    private static List<Piece> piecesOf(String word) {
        List<Piece> pieces = new ArrayList<>();
        StringBuilder kanaText = new StringBuilder();
        word.codePoints().forEach(codePoint -> {
            if (needsReading(codePoint)) {
                if (!kanaText.isEmpty()) {
                    pieces.add(kanaPiece(kanaText.toString()));
                    kanaText.setLength(0);
                }
                pieces.add(new Piece(Character.toString(codePoint), true, null));
            } else {
                kanaText.appendCodePoint(codePoint);
            }
        });
        if (!kanaText.isEmpty()) {
            pieces.add(kanaPiece(kanaText.toString()));
        }
        return pieces;
    }

    private static Piece kanaPiece(String text) {
        return new Piece(text, false, JapaneseText.normalize(text));
    }

    /** Whether a character needs a reading given: a kanji, the repeat mark 々, or the counters ヶ and ヵ. */
    static boolean needsReading(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
                || COUNTER_READINGS.containsKey((char) codePoint);
    }
}
