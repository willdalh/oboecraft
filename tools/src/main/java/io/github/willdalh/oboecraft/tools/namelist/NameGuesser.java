package io.github.willdalh.oboecraft.tools.namelist;

/** Splits a Japanese name into name parts and guesses their readings. */
interface NameGuesser {
    /**
     * @param japaneseName the name as Minecraft's Japanese translation writes it, like オークの原木
     */
    GuessedName guess(String japaneseName);
}
