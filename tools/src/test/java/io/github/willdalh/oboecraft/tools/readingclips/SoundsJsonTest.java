package io.github.willdalh.oboecraft.tools.readingclips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SoundsJsonTest {
    @Test
    void eachItemHasASoundEventPlayingItsClip() {
        String json = SoundsJson.format(
                List.of(new ItemId("block.minecraft.stone"), new ItemId("item.minecraft.apple")), new JsonObject());

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
    void theOtherSoundEventsAreKeptAndOldClipsReplaced() {
        JsonObject existing = JsonParser.parseString("""
                {
                  "entity.tsukumogami.arrive": {"sounds": [{"name": "minecraft:block.bell.use", "type": "event"}]},
                  "reading.item.minecraft.gone": {"sounds": [{"name": "oboecraft:reading/item.minecraft.gone"}]},
                  "entity.tsukumogami.fade": {"sounds": [{"name": "minecraft:block.amethyst_block.chime", \
                "type": "event"}]}
                }
                """).getAsJsonObject();

        String json = SoundsJson.format(List.of(new ItemId("block.minecraft.stone")), existing);

        assertEquals("""
                {
                  "entity.tsukumogami.arrive": {"sounds":[{"name":"minecraft:block.bell.use","type":"event"}]},
                  "entity.tsukumogami.fade": {"sounds":[{"name":"minecraft:block.amethyst_block.chime","type":"event"}]},
                  "reading.block.minecraft.stone": {"sounds":[{"name":"oboecraft:reading/block.minecraft.stone"}],\
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
