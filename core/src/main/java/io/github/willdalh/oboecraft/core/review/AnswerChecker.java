package io.github.willdalh.oboecraft.core.review;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
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
     * Matches an answer against a Japanese name. A state is a position in the name's reading together with a position
     * in the answer. The search tries every way of reading the answer, since romaji can be split in more than one way,
     * like {@code onna} as お, ん, な or お, ん, あ. Everything about the name is worked out once by
     * {@link JapaneseName}, so a match only keeps what depends on the answer.
     */
    private static final class Match {
        private final JapaneseName name;
        private final String reading;
        private final String answer;
        private final boolean[] visited;
        private final Deque<Integer> pending = new ArrayDeque<>();

        Match(JapaneseName name, String answer) {
            this.name = name;
            this.reading = name.reading();
            this.answer = answer;
            visited = new boolean[(reading.length() + 1) * (answer.length() + 1)];
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
            if (position == reading.length()) {
                return;
            }
            JapaneseName.PlacedPart part = name.partAt(position);
            if (part.start() == position) {
                startPart(part, typed);
            }
            if (typed == answer.length()) {
                return;
            }
            char expected = reading.charAt(position);
            char next = answer.charAt(typed);
            char vowel = name.vowelBefore(position);
            if (next == expected || (isLongVowelOf(vowel, expected) && isLongVowelOf(vowel, next))) {
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
                if (end <= part.end()
                        && reading.startsWith(spelling.kana(), position)
                        && answer.startsWith(spelling.romaji(), typed)) {
                    visit(end, typed + spelling.romaji().length());
                }
            }
        }

        /** The ways to go on at the start of a part: its text, like a kanji, or leaving out the particle の. */
        private void startPart(JapaneseName.PlacedPart part, int typed) {
            String text = part.normalizedText();
            if (!text.equals(part.part().reading()) && answer.startsWith(text, typed)) {
                visit(part.end(), typed + text.length());
            }
            if (text.equals(OPTIONAL_PARTICLE)) {
                visit(part.end(), typed);
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
         * Whether a character, typed or in the reading, makes the vowel before it long: the long vowel mark, the same
         * vowel in kana or romaji, or う after an o and い after an e, as in さぎょう and えい. With no vowel before it,
         * at the start of a part or after ん, nothing is a long vowel.
         */
        private static boolean isLongVowelOf(char vowel, char c) {
            if (vowel == 0) {
                return false;
            }
            return c == JapaneseText.LONG_VOWEL_MARK
                    || c == vowel
                    || c == JapaneseText.kanaOfVowel(vowel)
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
            return JapaneseText.isVowel(next) || next == 'y' || next == '\'';
        }

        /** Whether the answer has a doubled consonant at this position, like {@code kk}, or {@code tch}, for っ. */
        private boolean startsDoubledConsonant(int typed) {
            if (typed + 1 >= answer.length()) {
                return false;
            }
            char first = answer.charAt(typed);
            char second = answer.charAt(typed + 1);
            boolean consonant = first >= 'a' && first <= 'z' && !JapaneseText.isVowel(first) && first != 'n';
            return consonant && (second == first || (first == 't' && answer.startsWith("ch", typed + 1)));
        }
    }
}
