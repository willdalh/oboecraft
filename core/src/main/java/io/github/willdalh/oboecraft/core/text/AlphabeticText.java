package io.github.willdalh.oboecraft.core.text;

import java.text.Normalizer;
import java.util.Locale;

/** The shared spelling rules for German, Norwegian Bokmål and Dutch. No typo or Japanese sound rules apply. */
public final class AlphabeticText {
    private AlphabeticText() { }

    public static String normalize(String text) {
        String composed = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        composed.codePoints().forEach(c -> {
            switch (c) {
                case 'ä', 'æ' -> result.append("ae");
                case 'ö', 'ø' -> result.append("oe");
                case 'ü' -> result.append("ue");
                case 'ß' -> result.append("ss");
                case 'å' -> result.append("aa");
                case 'é', 'è', 'ê', 'ë' -> result.append('e');
                case 'ï' -> result.append('i');
                default -> {
                    int type = Character.getType(c);
                    // Only spacing and punctuation are ignored. Other letters, accents and symbols still differ.
                    boolean punctuation = type == Character.CONNECTOR_PUNCTUATION
                            || type == Character.DASH_PUNCTUATION || type == Character.START_PUNCTUATION
                            || type == Character.END_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION
                            || type == Character.FINAL_QUOTE_PUNCTUATION || type == Character.OTHER_PUNCTUATION;
                    if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && !punctuation) result.appendCodePoint(c);
                }
            }
        });
        return result.toString();
    }
}
