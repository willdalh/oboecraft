package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.item.NameListEntry;
import io.github.willdalh.oboecraft.fabric.OboecraftMod;
import io.github.willdalh.oboecraft.fabric.client.ReadingClips;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/**
 * Every entry in the name list has a reading clip that Minecraft can play: its sound event is in the mod's
 * {@code sounds.json}, and the clip's file is in the mod's resources. It fails when the name list changed and the
 * reading clip tool wasn't run again.
 */
public class ReadingClipsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> missing = context.computeOnClient(client -> OboecraftMod.itemMapping().nameList().entries()
                .stream()
                .map(NameListEntry::id)
                .filter(item -> ReadingClips.soundEvent(item)
                        .map(event -> client.getSoundManager().getSoundEvent(event))
                        .filter(events -> clipExists(client.getResourceManager(), events))
                        .isEmpty())
                .map(Object::toString)
                .toList());
        if (!missing.isEmpty()) {
            throw new AssertionError(missing.size() + " name list entries have no reading clip, like "
                    + missing.subList(0, Math.min(5, missing.size())));
        }
    }

    private static boolean clipExists(ResourceManager resources, WeighedSoundEvents events) {
        Sound sound = events.getSound(RandomSource.create());
        return resources.getResource(sound.getPath()).isPresent();
    }
}
