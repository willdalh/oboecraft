package io.github.willdalh.oboecraft.tools.namelist;

import java.util.List;

/** Splits Japanese text into words and guesses their readings. */
interface WordSplitter {
    /** The words of the text, in order. Their texts joined are the text. */
    List<Word> split(String text);
}
