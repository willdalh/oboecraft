package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import io.github.willdalh.oboecraft.core.text.Romaji;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Whether a typed answer is correct for an item. The general rule: the same sound passes, a different sound fails.
 * Each part of the Japanese name may be written as its text, like a kanji, or as its reading in hiragana, katakana or
 * romaji, so that kanji must be the ones in the name and kana must be the right ones. See the design doc on answers.
 */
public final class AnswerChecker {
    /** The particle that an answer may leave out. */
    private static final String OPTIONAL_PARTICLE = "の";
    private static final String ROMAJI_VOWELS = "aiueo";
    private static final String KANA_VOWELS = "あいうえお";

    private AnswerChecker() {
    }

    /**
     * Whether the typed answer is the item's Japanese name. Both are {@linkplain JapaneseText#normalize normalized}
     * first, so spaces, case and punctuation don't matter. Then the answer is matched part by part.
     */
    public static boolean isCorrect(NameListEntry entry, String typed) {
        return new Match(entry.japaneseName(), JapaneseText.normalize(typed)).succeeds();
    }

    /**
     * Matches an answer against a Japanese name. A position in the name is a position in its whole reading, and a
     * state is a position in the name together with a position in the answer. The search tries every way of reading
     * the answer, since romaji can be split in more than one way, like {@code onna} as お, ん, な or お, ん, あ.
     */
    private static final class Match {
        private final String reading;
        private final String answer;
        /** For each position in the reading, where the part it belongs to ends. */
        private final int[] partEnd;
        /** For each position where a part starts, the part's normalized text if it differs from its reading. */
        private final String[] textAt;
        /** For each position where a part starts, whether the answer may leave the part out. */
        private final boolean[] optionalAt;
        /** For each position in the reading, the vowel of the kana before it, or 0 if it has none. */
        private final char[] vowelBefore;
        private final boolean[] visited;
        private final Deque<Integer> pending = new ArrayDeque<>();

        Match(JapaneseName name, String answer) {
            this.reading = name.reading();
            this.answer = answer;
            int length = reading.length();
            partEnd = new int[length];
            textAt = new String[length + 1];
            optionalAt = new boolean[length + 1];
            vowelBefore = new char[length + 1];
            visited = new boolean[(length + 1) * (answer.length() + 1)];

            int start = 0;
            for (NamePart part : name.parts()) {
                int end = start + part.reading().length();
                for (int position = start; position < end; position++) {
                    partEnd[position] = end;
                }
                String text = JapaneseText.normalize(part.text());
                textAt[start] = text.equals(part.reading()) ? null : text;
                optionalAt[start] = part.text().equals(OPTIONAL_PARTICLE);
                start = end;
            }
            for (int position = 1; position <= length; position++) {
                char before = reading.charAt(position - 1);
                boolean longVowelMark = before == JapaneseText.LONG_VOWEL_MARK;
                vowelBefore[position] = longVowelMark ? vowelBefore[position - 1] : JapaneseText.vowelOf(before);
            }
        }

        boolean succeeds() {
            visit(0, 0);
            while (!pending.isEmpty()) {
                int state = pending.pop();
                int position = state / (answer.length() + 1);
                int typed = state % (answer.length() + 1);
                if (position == reading.length() && typed == answer.length()) {
                    return true;
                }
                step(position, typed);
            }
            return false;
        }

        /** Every way to go on from a position in the reading and a position in the answer. */
        private void step(int position, int typed) {
            String text = textAt[position];
            if (text != null && answer.startsWith(text, typed)) {
                visit(partEnd[position], typed + text.length());
            }
            if (optionalAt[position]) {
                visit(partEnd[position], typed);
            }
            if (position == reading.length() || typed == answer.length()) {
                return;
            }
            char expected = reading.charAt(position);
            char next = answer.charAt(typed);
            if (next == expected || (isLongVowel(position) && isLongVowelOf(vowelBefore[position], next))) {
                visit(position + 1, typed + 1);
            }
            if (expected == 'ん' && next == 'n' && !startsSyllable(typed + 1)) {
                visit(position + 1, typed + 1);
            }
            if (expected == 'っ' && startsDoubledConsonant(typed)) {
                visit(position + 1, typed + 1);
            }
            for (Romaji.Spelling spelling : Romaji.spellingsStartingWith(next)) {
                int end = position + spelling.kana().length();
                if (end <= partEnd[position]
                        && reading.startsWith(spelling.kana(), position)
                        && answer.startsWith(spelling.romaji(), typed)) {
                    visit(end, typed + spelling.romaji().length());
                }
            }
        }

        private void visit(int position, int typed) {
            int state = position * (answer.length() + 1) + typed;
            if (!visited[state]) {
                visited[state] = true;
                pending.push(state);
            }
        }

        /**
         * Whether the kana at this position makes the vowel before it long: the long vowel mark, the same vowel again,
         * or う after an o and い after an e, as in さぎょう and えい.
         */
        private boolean isLongVowel(int position) {
            return isLongVowelOf(vowelBefore[position], reading.charAt(position));
        }

        /**
         * Whether a character, typed or in the reading, makes the vowel before it long: the long vowel mark, the same
         * vowel in kana or romaji, or う after an o and い after an e.
         */
        private static boolean isLongVowelOf(char vowel, char c) {
            if (vowel == 0) {
                return false;
            }
            char vowelKana = KANA_VOWELS.charAt(ROMAJI_VOWELS.indexOf(vowel));
            return c == JapaneseText.LONG_VOWEL_MARK
                    || c == vowel
                    || c == vowelKana
                    || (vowel == 'o' && (c == 'う' || c == 'u'))
                    || (vowel == 'e' && (c == 'い' || c == 'i'));
        }

        /**
         * Whether the answer starts a syllable at this position with a vowel or a {@code y}, so that an {@code n}
         * before it belongs to that syllable instead of being {@code ん}, as when typing on a Japanese keyboard.
         * {@code n'} is {@code ん} and is matched as romaji.
         */
        private boolean startsSyllable(int typed) {
            if (typed == answer.length()) {
                return false;
            }
            char next = answer.charAt(typed);
            return ROMAJI_VOWELS.indexOf(next) >= 0 || next == 'y' || next == '\'';
        }

        /** Whether the answer has a doubled consonant at this position, like {@code kk}, or {@code tch}, for っ. */
        private boolean startsDoubledConsonant(int typed) {
            if (typed + 1 >= answer.length()) {
                return false;
            }
            char first = answer.charAt(typed);
            char second = answer.charAt(typed + 1);
            boolean consonant = first >= 'a' && first <= 'z' && ROMAJI_VOWELS.indexOf(first) < 0 && first != 'n';
            return consonant && (second == first || (first == 't' && answer.startsWith("ch", typed + 1)));
        }
    }
}
