package io.github.willdalh.oboecraft.core.item;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/** The supported languages of item names and progress. Game language variants are deliberately not aliases. */
public enum LearningLanguage {
    JAPANESE("ja_jp", Locale.JAPANESE),
    GERMAN("de_de", Locale.GERMAN),
    AUSTRIAN_GERMAN("de_at", Locale.forLanguageTag("de-AT")),
    NORWEGIAN("no_no", Locale.forLanguageTag("nb-NO")),
    DUTCH("nl_nl", Locale.forLanguageTag("nl-NL"));

    private final String code;
    private final Locale locale;

    LearningLanguage(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    public String code() { return code; }

    public boolean hasReadings() { return this == JAPANESE; }

    /** Strict parsing for untrusted uploads. Unknown languages are never silently replaced. */
    public static Optional<LearningLanguage> fromCode(String code) {
        for (LearningLanguage language : values()) {
            if (language.code.equals(code)) return Optional.of(language);
        }
        return Optional.empty();
    }

    public static LearningLanguage fromGameLanguage(String code) {
        return fromCode(code).orElse(JAPANESE);
    }

    /** A comparator owned by the caller: Collator is mutable and must not be shared between threads. */
    public Comparator<String> nameOrder() {
        if (this == JAPANESE) return Comparator.naturalOrder();
        Collator collator = Collator.getInstance(locale);
        return collator::compare;
    }
}
