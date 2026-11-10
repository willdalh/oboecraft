package io.github.willdalh.oboecraft.fabric.gametest;

import io.github.willdalh.oboecraft.core.Oboecraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/** Reading clips handed to Minecraft's sound manager, even when the test machine has no audio device. */
public final class PlayedReadingClips {
    private static final List<Identifier> played = new ArrayList<>();

    private PlayedReadingClips() { }

    /** Called by the test-only sound manager mixin. Only the client thread uses this class. */
    public static void record(SoundInstance sound) {
        Identifier event = sound.getIdentifier();
        if (event.getNamespace().equals(Oboecraft.MOD_ID) && event.getPath().startsWith("reading.")) {
            played.add(event);
        }
    }

    /** Returns the requests since the last call and clears them. */
    static List<Identifier> take() {
        List<Identifier> result = List.copyOf(played);
        played.clear();
        return result;
    }
}
