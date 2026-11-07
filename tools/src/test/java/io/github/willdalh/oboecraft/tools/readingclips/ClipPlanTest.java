package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.willdalh.oboecraft.core.item.ItemId;
import io.github.willdalh.oboecraft.core.item.JapaneseName;
import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.core.item.NamePart;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClipPlanTest {
    private static final ReadingClipList.Voice VOICE = new ReadingClipList.Voice("声", "uuid", "ノーマル");
    private static final String FORMAT = "format";

    private static final ItemId STONE = new ItemId("block.minecraft.stone");
    private static final ItemId BARREL = new ItemId("block.minecraft.barrel");
    private static final ItemId GONE = new ItemId("block.minecraft.gone");

    private static NameListEntry entry(ItemId id, String text, String reading) {
        return new NameListEntry(id, List.of(id.value()), new JapaneseName(List.of(new NamePart(text, reading))));
    }

    private static ReadingClipList.Clip clip(ItemId id, String text, String reading) {
        return new ReadingClipList.Clip(id, text, reading, text);
    }

    private static ReadingClipList list(ReadingClipList.Voice voice, ReadingClipList.Clip... clips) {
        return new ReadingClipList(voice, FORMAT, "0.26.1", List.of(clips));
    }

    @Test
    void everyEntryGetsAClipOnTheFirstRun() {
        List<NameListEntry> entries = List.of(entry(STONE, "石", "いし"), entry(BARREL, "樽", "たる"));

        ClipPlan plan = ClipPlan.of(entries, Optional.empty(), VOICE, FORMAT, Set.of());

        assertEquals(entries, plan.toMake());
        assertEquals(List.of(), plan.kept());
        assertEquals(Set.of(), plan.toDelete());
    }

    @Test
    void unchangedClipsAreKeptAndChangedOnesMadeAgain() {
        ReadingClipList old = list(VOICE, clip(STONE, "石", "いし"), clip(BARREL, "樽", "そん"));
        NameListEntry barrel = entry(BARREL, "樽", "たる");

        ClipPlan plan = ClipPlan.of(
                List.of(entry(STONE, "石", "いし"), barrel), Optional.of(old), VOICE, FORMAT, Set.of(STONE, BARREL));

        assertEquals(List.of(barrel), plan.toMake());
        assertEquals(List.of(clip(STONE, "石", "いし")), plan.kept());
    }

    @Test
    void aClipWhoseFileIsMissingIsMadeAgain() {
        NameListEntry stone = entry(STONE, "石", "いし");

        ClipPlan plan = ClipPlan.of(
                List.of(stone), Optional.of(list(VOICE, clip(STONE, "石", "いし"))), VOICE, FORMAT, Set.of());

        assertEquals(List.of(stone), plan.toMake());
    }

    @Test
    void anotherVoiceOrFormatMakesEveryClipAgain() {
        NameListEntry stone = entry(STONE, "石", "いし");
        ReadingClipList.Voice otherVoice = new ReadingClipList.Voice("声", "uuid", "ささやき");

        ClipPlan newVoice = ClipPlan.of(
                List.of(stone), Optional.of(list(otherVoice, clip(STONE, "石", "いし"))), VOICE, FORMAT, Set.of(STONE));
        ClipPlan newFormat = ClipPlan.of(List.of(stone), Optional.of(list(VOICE, clip(STONE, "石", "いし"))), VOICE,
                "other format", Set.of(STONE));

        assertEquals(List.of(stone), newVoice.toMake());
        assertEquals(List.of(stone), newFormat.toMake());
    }

    @Test
    void clipsOfItemsNoLongerInTheNameListAreDeleted() {
        ClipPlan plan = ClipPlan.of(List.of(entry(STONE, "石", "いし")),
                Optional.of(list(VOICE, clip(STONE, "石", "いし"), clip(GONE, "無", "む"))), VOICE, FORMAT,
                Set.of(STONE, GONE));

        assertEquals(Set.of(GONE), plan.toDelete());
        assertEquals(List.of(), plan.toMake());
    }
}
