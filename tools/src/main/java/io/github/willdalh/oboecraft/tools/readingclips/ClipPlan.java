package io.github.willdalh.oboecraft.tools.readingclips;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which reading clips a run of the tool makes, keeps and deletes. A clip is kept when it is in the clip list with the
 * entry's name and reading, in the same voice and format, and its file is there. Every other entry gets a new clip.
 * Clip files of items that aren't in the name list any more are deleted.
 *
 * @param toMake   the entries that need a new clip, in the name list's order
 * @param kept     the clips that stay as they are
 * @param toDelete the items whose clip files are deleted
 */
record ClipPlan(List<NameListEntry> toMake, List<ReadingClipList.Clip> kept, Set<ItemId> toDelete) {
    ClipPlan {
        toMake = List.copyOf(toMake);
        kept = List.copyOf(kept);
        toDelete = Set.copyOf(toDelete);
    }

    /**
     * @param entries   the name list's entries
     * @param old       the clip list from the last run, if there is one
     * @param clipFiles the items that have a clip file now
     */
    static ClipPlan of(List<NameListEntry> entries, Optional<ReadingClipList> old, ReadingClipList.Voice voice,
            String format, Set<ItemId> clipFiles) {
        Map<ItemId, ReadingClipList.Clip> oldClips = old
                .filter(list -> list.voice().equals(voice) && list.format().equals(format))
                .map(list -> list.clips().stream()
                        .collect(Collectors.toMap(ReadingClipList.Clip::item, Function.identity())))
                .orElse(Map.of());
        List<NameListEntry> toMake = new ArrayList<>();
        List<ReadingClipList.Clip> kept = new ArrayList<>();
        for (NameListEntry entry : entries) {
            ReadingClipList.Clip clip = oldClips.get(entry.id());
            boolean unchanged = clip != null
                    && clip.name().equals(entry.japaneseName().text())
                    && clip.reading().equals(entry.japaneseName().reading())
                    && clipFiles.contains(entry.id());
            if (unchanged) {
                kept.add(clip);
            } else {
                toMake.add(entry);
            }
        }
        Set<ItemId> inNameList = entries.stream().map(NameListEntry::id).collect(Collectors.toSet());
        Set<ItemId> toDelete = clipFiles.stream().filter(item -> !inNameList.contains(item)).collect(Collectors.toSet());
        return new ClipPlan(toMake, kept, toDelete);
    }
}
