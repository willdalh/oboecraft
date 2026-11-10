package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.Name;
import io.github.willdalh.oboecraft.core.item.PlainName;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the name list from the keys Minecraft names stacks with and their translated names. Keys with the same
 * name become one entry. An entry that is already in the name list keeps its item id, and keeps its parts as long as
 * its source name is the same, since they have been reviewed. Only new and changed names are guessed, and those need
 * review.
 */
final class NameListGenerator {
    /** A format argument, like {@code %s} or {@code %1$s}, which the game fills in, like a player's name. */
    private static final Pattern FORMAT_ARGUMENT = Pattern.compile("%(\\d+\\$)?[sd]");

    private NameListGenerator() {
    }

    /**
     * The new name list.
     *
     * @param entries   every entry, in the order of the keys
     * @param generated the entries whose names were guessed, with what to check closely, in the same order
     * @param notes     things about the whole list that a reviewer should know, like keys that were left out
     */
    record Result(List<NameListEntry> entries, List<GeneratedEntry> generated, List<String> notes) {
    }

    /**
     * An entry that needs review, with notes about its name or grouping.
     *
     * @param entry the entry
     * @param notes what to check closely, empty if nothing stood out
     */
    record GeneratedEntry(NameListEntry entry, List<String> notes) {
    }

    /** An entry while it is built, since keys named after arguments are added to it last. */
    private static final class Draft {
        final ItemId id;
        final List<String> translationKeys;
        final Name name;
        final List<String> notes;
        final boolean generated;

        Draft(ItemId id, List<String> translationKeys, Name name, List<String> notes,
                boolean generated) {
            this.id = id;
            this.translationKeys = new ArrayList<>(translationKeys);
            this.name = name;
            this.notes = notes;
            this.generated = generated;
        }
    }

    /**
     * Generates the name list.
     *
     * @param keys         the keys Minecraft names stacks with, in order
     * @param japanese     Minecraft's Japanese text for each key
     * @param existing     the name list as it is now, with reviewed parts
     * @param guesser      guesses the parts of new names
     * @throws IllegalStateException if two existing entries now have the same name, which needs former ids
     */
    static Result generate(List<ItemNameKey> keys, Map<String, String> japanese, List<NameListEntry> existing,
            NameGuesser guesser) {
        return generateNames(keys, japanese, existing, name -> {
            GuessedName guess = guesser.guess(name);
            return new GeneratedName(guess.name(), guess.notes());
        });
    }

    private record GeneratedName(Name name, List<String> notes) { }

    static Result generatePlain(List<ItemNameKey> keys, Map<String, String> translated, Map<String, String> english,
            List<NameListEntry> existing) {
        Result result = generateNames(keys, translated, existing, name -> new GeneratedName(new PlainName(name), List.of()));
        Map<ItemId, List<String>> flags = new LinkedHashMap<>();
        result.generated().forEach(entry -> flags.put(entry.entry().id(), new ArrayList<>(entry.notes())));
        for (NameListEntry entry : result.entries()) {
            for (String key : entry.translationKeys()) {
                String text = translated.get(key);
                if (text != null && text.equals(english.get(key))) {
                    flags.computeIfAbsent(entry.id(), unused -> new ArrayList<>()).add("Same as English: " + key);
                }
                if (text != null && FORMAT_ARGUMENT.matcher(text).find()) {
                    flags.computeIfAbsent(entry.id(), unused -> new ArrayList<>()).add("Format argument: " + key + " = " + text);
                }
            }
        }
        return new Result(result.entries(), result.entries().stream().filter(entry -> flags.containsKey(entry.id()))
                .map(entry -> new GeneratedEntry(entry, List.copyOf(flags.get(entry.id())))).toList(), result.notes());
    }

    private static Result generateNames(List<ItemNameKey> keys, Map<String, String> japanese,
            List<NameListEntry> existing, Function<String, GeneratedName> guesser) {
        List<String> notes = new ArrayList<>();
        SequencedMap<String, List<String>> keysByName = new LinkedHashMap<>();
        List<ItemNameKey> namedAfterArguments = new ArrayList<>();
        Map<String, String> defaultKeyByItem = new HashMap<>();
        for (ItemNameKey key : keys) {
            if (key.item() != null) {
                defaultKeyByItem.putIfAbsent(key.item(), key.translationKey());
            }
            String name = japanese.get(key.translationKey());
            if (name == null) {
                notes.add(key.translationKey() + " has no name, so it is left out");
            } else if (FORMAT_ARGUMENT.matcher(name).find()) {
                namedAfterArguments.add(key);
            } else {
                keysByName.computeIfAbsent(name, unused -> new ArrayList<>()).add(key.translationKey());
            }
        }

        Map<String, NameListEntry> existingByKey = new HashMap<>();
        existing.forEach(entry -> entry.translationKeys().forEach(key -> existingByKey.put(key, entry)));
        Map<ItemId, String> keeperOf = keepers(existing, keysByName);
        Set<ItemId> existingIds = new HashSet<>();
        existing.forEach(entry -> existingIds.add(entry.id()));

        List<Draft> drafts = new ArrayList<>();
        Map<String, Draft> draftByKey = new HashMap<>();
        for (Map.Entry<String, List<String>> group : keysByName.entrySet()) {
            Draft draft = draftFor(
                    group.getKey(), group.getValue(), existingByKey, keeperOf, existingIds, guesser, notes);
            drafts.add(draft);
            group.getValue().forEach(key -> draftByKey.put(key, draft));
        }

        for (ItemNameKey key : namedAfterArguments) {
            String text = japanese.get(key.translationKey());
            String itemKey = defaultKeyByItem.get(key.item());
            Draft draft = itemKey == null || itemKey.equals(key.translationKey()) ? null : draftByKey.get(itemKey);
            if (draft == null) {
                notes.add(key.translationKey() + " (" + text + ") names no item with an entry, so it is left out");
            } else {
                draft.translationKeys.add(key.translationKey());
                notes.add(key.translationKey() + " (" + text + ") is added to " + draft.id + ", the item it names");
            }
        }

        for (NameListEntry entry : existing) {
            if (!keeperOf.containsKey(entry.id())) {
                drafts.add(new Draft(entry.id(), entry.translationKeys(), entry.name(), List.of(), false));
                notes.add(entry.id() + " (" + entry.name().text() + ") is no longer in the game. It is kept, "
                        + "so saved progress for it isn't lost");
            }
        }

        List<NameListEntry> entries = new ArrayList<>();
        List<GeneratedEntry> generated = new ArrayList<>();
        for (Draft draft : drafts) {
            NameListEntry entry = new NameListEntry(draft.id, draft.translationKeys, draft.name);
            entries.add(entry);
            if (draft.generated) {
                generated.add(new GeneratedEntry(entry, draft.notes));
            }
        }
        return new Result(List.copyOf(entries), List.copyOf(generated), List.copyOf(notes));
    }

    /**
     * For each existing entry, the name whose entry keeps its item id: the name of its item id's key, or if the entry
     * no longer has that key, because Minecraft dropped it, the first name of its other keys. A dropped key that comes
     * back belongs to a new item, not to the entry, since the entry's item lives on in its other keys. When an entry's
     * keys get different names, the other names become new items.
     */
    private static Map<ItemId, String> keepers(
            List<NameListEntry> existing, SequencedMap<String, List<String>> keysByName) {
        Map<String, String> nameByKey = new HashMap<>();
        keysByName.forEach((name, keys) -> keys.forEach(key -> nameByKey.put(key, name)));
        Map<ItemId, String> keeperOf = new HashMap<>();
        for (NameListEntry entry : existing) {
            String keeper = entry.translationKeys().contains(entry.id().value())
                    ? nameByKey.get(entry.id().value())
                    : null;
            for (int i = 0; keeper == null && i < entry.translationKeys().size(); i++) {
                keeper = nameByKey.get(entry.translationKeys().get(i));
            }
            if (keeper != null) {
                keeperOf.put(entry.id(), keeper);
            }
        }
        return keeperOf;
    }

    /**
     * The entry for a group of keys with the same name. A name that is unchanged keeps its reviewed parts, even when it
     * split off from its entry and becomes a new item. Only a new or changed name is guessed.
     */
    private static Draft draftFor(String name, List<String> keys, Map<String, NameListEntry> existingByKey,
            Map<ItemId, String> keeperOf, Set<ItemId> existingIds, Function<String, GeneratedName> guesser, List<String> listNotes) {
        List<NameListEntry> touched = keys.stream()
                .map(existingByKey::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (touched.size() > 1) {
            throw new IllegalStateException("The entries " + touched.stream().map(NameListEntry::id).toList()
                    + " now have the same name " + name + ". Merging entries needs former ids, which the "
                    + "name list doesn't have yet. See docs/design/items.md.");
        }
        NameListEntry previous = touched.isEmpty() ? null : touched.getFirst();
        boolean keepsId = previous != null && name.equals(keeperOf.get(previous.id()));
        boolean nameUnchanged = previous != null && (previous.name() instanceof PlainName plain
                ? plain.sourceText() : previous.name().text()).equals(name);
        ItemId id = keepsId ? previous.id() : newId(name, keys, existingIds);
        String splitFrom = previous != null && !keepsId
                ? "Split from " + previous.id() + " (" + previous.name().text() + "), so it is a new item"
                : null;

        if (nameUnchanged) {
            if (splitFrom != null) {
                listNotes.add(id + " (" + name + "): " + splitFrom + ". Its name is unchanged, so it keeps its parts");
            }
            return new Draft(id, keys, previous.name(), splitFrom == null ? List.of() : List.of(splitFrom), splitFrom != null);
        }
        GeneratedName guess = guesser.apply(name);
        List<String> notes = new ArrayList<>();
        if (keepsId) {
            notes.add("The name changed from " + previous.name().text());
        } else if (splitFrom != null) {
            notes.add(splitFrom);
        }
        notes.addAll(guess.notes());
        return new Draft(id, keys, guess.name(), notes, true);
    }

    /**
     * The id of a new item: its first key that isn't already an item id. An item id is never given to another item,
     * even when Minecraft brings back a key it dropped.
     *
     * @throws IllegalStateException if every key is already an item id
     */
    private static ItemId newId(String name, List<String> keys, Set<ItemId> existingIds) {
        return keys.stream()
                .map(ItemId::new)
                .filter(id -> !existingIds.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The new item " + name + " has only the keys " + keys
                        + ", which are already item ids of other entries, so it has no id it can take"));
    }
}
