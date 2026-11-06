package io.github.willdalh.oboecraft.core.item;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An item's name in Minecraft's Japanese translation, split into {@link NamePart}s so that each part has its reading.
 * For example, オークの原木 is オーク, の, 原 (げん) and 木 (ぼく).
 */
public final class JapaneseName {
    private final List<NamePart> parts;
    private final String text;
    private final String reading;

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
