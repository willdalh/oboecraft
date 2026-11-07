package io.github.willdalh.oboecraft.tools.readingclips;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import java.util.List;
import java.util.Objects;

/**
 * The list of clips a run of the reading clip tool made, for a person to listen to. Clips that VOICEVOX didn't say as
 * the reading from any text come first, since they may be said wrong. Then the clips said from their reading, with
 * how VOICEVOX reads the name as it is written, since VOICEVOX guessed their pitch accent from the kana alone. It is a
 * Markdown file.
 */
final class ReviewList {
    /** A clip the tool made, and the text VOICEVOX was given for it. */
    record Made(NameListEntry entry, SpokenText spoken) {
        Made {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(spoken, "spoken");
        }

        /** Whether VOICEVOX was given the reading, or part of it, since it reads the name differently. */
        boolean fromReading() {
            return !spoken.isName(entry.japaneseName());
        }
    }

    private ReviewList() {
    }

    static String format(List<Made> made, ReadingClipList.Voice voice, String engineVersion) {
        List<Made> unchecked = made.stream().filter(clip -> !clip.spoken().checked()).toList();
        List<Made> fromReading = made.stream().filter(Made::fromReading).toList();
        StringBuilder text = new StringBuilder();
        text.append("# Reading clip review\n\n")
                .append("Made with VOICEVOX ").append(engineVersion).append(", in the voice ").append(voice.speaker())
                .append(" (").append(voice.style()).append(").\n\n")
                .append(made.size()).append(" clips were made. ").append(fromReading.size())
                .append(" of them were said from their reading, since VOICEVOX reads the name differently. ")
                .append(unchecked.size())
                .append(" of those don't say the reading as VOICEVOX splits it, and need a listen.\n");

        text.append("\n## Not said as the reading\n\n");
        for (Made clip : unchecked) {
            text.append("- `").append(clip.entry().id()).append("` ").append(clip.entry().japaneseName().text())
                    .append(": ").append(clip.spoken().text()).append('\n');
        }

        text.append("\n## Said from the reading\n\n");
        for (Made clip : fromReading) {
            text.append("- `").append(clip.entry().id()).append("` ").append(clip.entry().japaneseName().text())
                    .append(": ").append(clip.spoken().text()).append(", where VOICEVOX reads the name ")
                    .append(clip.spoken().nameSaid()).append('\n');
        }

        text.append("\n## Made clips\n\n");
        for (Made clip : made) {
            text.append("- `").append(clip.entry().id()).append("` ").append(clip.entry().japaneseName().text())
                    .append(": ").append(clip.spoken().text()).append('\n');
        }
        return text.toString();
    }
}
