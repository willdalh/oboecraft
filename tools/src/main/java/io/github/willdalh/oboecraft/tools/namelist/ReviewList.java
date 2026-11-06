package io.github.willdalh.oboecraft.tools.namelist;

import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NamePart;
import io.github.willdalh.oboecraft.core.text.JapaneseText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The list of entries the name list tool generated, for an agent to review: the words to check closely first, then
 * every generated entry. It is a Markdown file.
 */
final class ReviewList {
    private ReviewList() {
    }

    static void write(Path file, NameListGenerator.Result result, String sources) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, format(result, sources), StandardCharsets.UTF_8);
    }

    static String format(NameListGenerator.Result result, String sources) {
        List<NameListGenerator.GeneratedEntry> toCheck = result.generated().stream()
                .filter(entry -> !entry.notes().isEmpty())
                .toList();
        StringBuilder text = new StringBuilder();
        text.append("# Name list review\n\n")
                .append("Generated from ").append(sources).append(".\n\n")
                .append(result.entries().size()).append(" entries, of which ").append(result.generated().size())
                .append(" were generated and need review. ").append(toCheck.size())
                .append(" of them have words to check closely.\n");

        if (!result.notes().isEmpty()) {
            text.append("\n## Notes\n\n");
            result.notes().forEach(note -> text.append("- ").append(note).append('\n'));
        }

        text.append("\n## Words to check closely\n");
        for (NameListGenerator.GeneratedEntry entry : toCheck) {
            text.append("\n- `").append(entry.entry().id()).append("` ").append(describe(entry.entry().japaneseName()))
                    .append('\n');
            entry.notes().forEach(note -> text.append("  - ").append(note).append('\n'));
        }

        text.append("\n## Generated entries\n\n");
        for (NameListGenerator.GeneratedEntry entry : result.generated()) {
            text.append("- `").append(entry.entry().id()).append("` ")
                    .append(describe(entry.entry().japaneseName())).append('\n');
        }
        return text.toString();
    }

    /** A name with its parts, like {@code オークの原木: オーク | の | 原 げん | 木 ぼく}. */
    static String describe(JapaneseName name) {
        return name.text() + ": " + name.parts().stream().map(ReviewList::describe).collect(Collectors.joining(" | "));
    }

    private static String describe(NamePart part) {
        boolean readAsWritten = JapaneseText.normalize(part.text()).equals(part.reading());
        return readAsWritten ? part.text() : part.text() + " " + part.reading();
    }
}
