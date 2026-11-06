package io.github.willdalh.oboecraft.tools.namelist;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.Morpheme;
import com.worksap.nlp.sudachi.Tokenizer;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Splits text into words with Sudachi, a Japanese text splitter, and its dictionary SudachiDict. Words are Sudachi's
 * long units (split mode C), so a compound like 作業台 is one word, which JMdict is more likely to have.
 */
final class SudachiWordSplitter implements WordSplitter, AutoCloseable {
    private static final String PARTICLE = "助詞";

    private final Dictionary dictionary;
    private final Tokenizer tokenizer;

    /**
     * @param systemDictionary SudachiDict's {@code system.dic}
     * @throws IOException if the dictionary can't be read
     */
    SudachiWordSplitter(Path systemDictionary) throws IOException {
        Config config = Config.defaultConfig().systemDictionary(systemDictionary);
        this.dictionary = Dictionary.load(config);
        this.tokenizer = dictionary.tokenizer();
    }

    @Override
    public List<Word> split(String text) {
        return tokenizer.tokenize(Tokenizer.SplitMode.C, text).stream().map(SudachiWordSplitter::wordOf).toList();
    }

    private static Word wordOf(Morpheme morpheme) {
        Morpheme dictionaryForm = morpheme.dictionaryFormMorpheme();
        return new Word(
                morpheme.surface(),
                JapaneseText.normalize(morpheme.readingForm()),
                dictionaryForm.surface(),
                JapaneseText.normalize(dictionaryForm.readingForm()),
                morpheme.partOfSpeech().getFirst().equals(PARTICLE),
                !morpheme.isOOV());
    }

    @Override
    public void close() throws IOException {
        dictionary.close();
    }
}
