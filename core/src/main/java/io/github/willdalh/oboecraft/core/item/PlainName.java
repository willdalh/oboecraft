package io.github.willdalh.oboecraft.core.item;

import io.github.willdalh.oboecraft.core.text.AlphabeticText;
import java.util.Objects;

/** A name without readings. Its answer key is computed once, when the list is loaded. */
public final class PlainName implements Name {
    private final String text;
    private final String sourceText;
    private final String answer;

    public PlainName(String text) {
        this(text, text);
    }

    /** The source spelling lets the tool retain a reviewed correction until Minecraft changes the translation. */
    public PlainName(String text, String sourceText) {
        this.text = Objects.requireNonNull(text, "text");
        this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
        if (sourceText.isBlank()) throw new IllegalArgumentException("A source name can't be blank");
        answer = AlphabeticText.normalize(text);
        if (text.codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("A name needs letters or digits");
        }
    }

    @Override public String text() { return text; }

    public String sourceText() { return sourceText; }

    public boolean matches(String typed) { return answer.equals(AlphabeticText.normalize(typed)); }

    @Override public boolean equals(Object other) {
        return other instanceof PlainName name && text.equals(name.text) && sourceText.equals(name.sourceText);
    }
    @Override public int hashCode() { return Objects.hash(text, sourceText); }
    @Override public String toString() { return text; }
}
