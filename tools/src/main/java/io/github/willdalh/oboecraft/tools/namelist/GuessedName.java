package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import java.util.List;
import java.util.Objects;

/**
 * A Japanese name split into parts with readings by the name list tool, for an agent to review.
 *
 * @param name  the name with its parts and readings
 * @param notes what a reviewer should check closely, empty if nothing stood out
 */
record GuessedName(JapaneseName name, List<String> notes) {
    GuessedName {
        Objects.requireNonNull(name, "name");
        notes = List.copyOf(notes);
    }
}
