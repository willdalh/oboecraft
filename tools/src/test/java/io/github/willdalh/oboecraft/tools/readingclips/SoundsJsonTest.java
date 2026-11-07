package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SoundsJsonTest {
    @Test
    void eachItemHasASoundEventPlayingItsClip() {
        String json = SoundsJson.format(List.of(new ItemId("block.minecraft.stone"), new ItemId("item.minecraft.apple")));

        assertEquals("""
                {
                  "reading.block.minecraft.stone": {"sounds":[{"name":"oboecraft:reading/block.minecraft.stone"}],\
                "subtitle":"subtitles.oboecraft.reading"},
                  "reading.item.minecraft.apple": {"sounds":[{"name":"oboecraft:reading/item.minecraft.apple"}],\
                "subtitle":"subtitles.oboecraft.reading"}
                }
                """, json);
    }

    @Test
    void anItemIdThatCantBeAFileNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SoundsJson.checkedFileName(new ItemId("block.Minecraft.x")));
        assertThrows(IllegalArgumentException.class, () -> SoundsJson.checkedFileName(new ItemId("block/x")));
    }
}
