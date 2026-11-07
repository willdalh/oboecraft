package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingClipListTest {
    private static final ReadingClipList.Voice VOICE =
            new ReadingClipList.Voice("玄野武宏", "c30dc15a-0992-4f8d-8bb8-ad3b314e6a6f", "ノーマル");

    @Test
    void theFileHasOneClipPerLineAndReadsBackTheSame() {
        ReadingClipList list = new ReadingClipList(VOICE, "Ogg Vorbis", "0.26.1", List.of(
                new ReadingClipList.Clip(new ItemId("block.minecraft.stone"), "石", "いし", "石"),
                new ReadingClipList.Clip(new ItemId("block.minecraft.trial_chamber"), "試練の間", "しれんのま", "シレンノマ")));

        String text = list.fileText();

        assertEquals("""
                {
                  "voice": {"speaker":"玄野武宏","speakerUuid":"c30dc15a-0992-4f8d-8bb8-ad3b314e6a6f","style":"ノーマル"},
                  "format": "Ogg Vorbis",
                  "engineVersion": "0.26.1",
                  "clips": [
                    {"id":"block.minecraft.stone","name":"石","reading":"いし","spoken":"石"},
                    {"id":"block.minecraft.trial_chamber","name":"試練の間","reading":"しれんのま","spoken":"シレンノマ"}
                  ]
                }
                """, text);
        assertEquals(list, ReadingClipList.parse(JsonParser.parseString(text)));
    }

    @Test
    void anEmptyListReadsBack() {
        ReadingClipList list = new ReadingClipList(VOICE, "Ogg Vorbis", "0.26.1", List.of());

        assertEquals(list, ReadingClipList.parse(JsonParser.parseString(list.fileText())));
    }

    @Test
    void twoClipsForOneItemAreRefused() {
        ReadingClipList.Clip stone = new ReadingClipList.Clip(new ItemId("block.minecraft.stone"), "石", "いし", "石");

        assertThrows(IllegalArgumentException.class,
                () -> new ReadingClipList(VOICE, "Ogg Vorbis", "0.26.1", List.of(stone, stone)));
    }

    @Test
    void aFileWithAMissingFieldIsRefused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ReadingClipList.parse(
                JsonParser.parseString("{\"voice\": {\"speaker\": \"a\", \"speakerUuid\": \"b\", \"style\": \"c\"},"
                        + " \"format\": \"f\", \"clips\": []}")));

        assertEquals("The field engineVersion is missing or isn't a string", error.getMessage());
    }
}
