package io.github.willdalh.oboecraft.core.item;

import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An item's name in Minecraft's Japanese translation, split into {@link NamePart}s so that each part has its reading.
 * For example, オークの原木 is オーク, の, 原 (げん) and 木 (ぼく).
 *
 * <p>A position in the name is a position in its whole {@linkplain #reading reading}. Everything about the positions is
 * worked out once, when the name is built, since answer checking asks for it at every step.
 */
public final class JapaneseName implements Name {
    private final List<NamePart> parts;
    private final String text;
    private final String reading;
    /** For each position in the reading, the part it belongs to. */
    private final PlacedPart[] partAt;
    /** For each position in the reading, the vowel just before it in the same part, or 0 if there is none. */
    private final char[] vowelBefore;

    /**
     * A part, with its text {@linkplain JapaneseText#normalize normalized}, and where its reading starts and ends in
     * the reading of the whole name.
     */
    public record PlacedPart(NamePart part, String normalizedText, int start, int end) {
    }

    /**
     * @throws IllegalArgumentException if there are no parts
     */
    public JapaneseName(List<NamePart> parts) {
        this.parts = List.copyOf(parts);
        if (this.parts.isEmpty()) {
            throw new IllegalArgumentException("A Japanese name needs at least one part");
        }
        this.text = this.parts.stream().map(NamePart::text).collect(Collectors.joining());
        this.reading = this.parts.stream().map(NamePart::reading).collect(Collectors.joining());

        partAt = new PlacedPart[reading.length()];
        vowelBefore = new char[reading.length()];
        int start = 0;
        for (NamePart part : this.parts) {
            int end = start + part.reading().length();
            PlacedPart placed = new PlacedPart(part, JapaneseText.normalize(part.text()), start, end);
            for (int position = start; position < end; position++) {
                partAt[position] = placed;
                if (position > start) {
                    char before = reading.charAt(position - 1);
                    boolean longVowelMark = before == JapaneseText.LONG_VOWEL_MARK;
                    vowelBefore[position] = longVowelMark ? vowelBefore[position - 1] : JapaneseText.vowelOf(before);
                }
            }
            start = end;
        }
    }

    /** The parts, in the order they are written. */
    public List<NamePart> parts() {
        return parts;
    }

    /** The name as Minecraft writes it, like オークの原木. */
    public String text() {
        return text;
    }

    /** How the whole name is said, in hiragana, like おーくのげんぼく. */
    public String reading() {
        return reading;
    }

    /**
     * The part that a position in the reading belongs to.
     *
     * @throws IndexOutOfBoundsException if the position isn't in the reading
     */
    public PlacedPart partAt(int position) {
        return partAt[position];
    }

    /**
     * The vowel that the reading ends in just before a position, as one of {@link JapaneseText#VOWELS}, or 0 at the
     * start of a part and after {@code ん} and {@code っ}. Vowels in different parts are said separately, so the vowel
     * at the end of one part is never the vowel before the next part.
     *
     * @throws IndexOutOfBoundsException if the position isn't in the reading
     */
    public char vowelBefore(int position) {
        return vowelBefore[position];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JapaneseName name && parts.equals(name.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return text + " (" + reading + ")";
    }
}
