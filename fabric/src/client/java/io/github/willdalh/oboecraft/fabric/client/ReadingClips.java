package io.github.willdalh.oboecraft.fabric.client;

import io.github.willdalh.oboecraft.core.Oboecraft;
import io.github.willdalh.oboecraft.core.item.ItemId;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Plays an item's reading clip, after a pass or a fail and from the word list: the item's reading, said by VOICEVOX.
 * Each clip is its own sound event, {@code oboecraft:reading.<item id>}, listed in the mod's {@code sounds.json}, which
 * the reading clip tool writes. It plays in Minecraft's Voice/Speech category, so the player sets its volume there, and it isn't
 * placed in the world. A new clip stops the clip that is playing. Only used on the client thread.
 */
public final class ReadingClips {
    private static final String EVENT_PREFIX = "reading.";

    private @Nullable SoundInstance playing;

    /** The sound event of the item's clip, or none if the item id can't be a sound event's name. */
    public static Optional<Identifier> soundEvent(ItemId item) {
        return Optional.ofNullable(Identifier.tryBuild(Oboecraft.MOD_ID, EVENT_PREFIX + item.value()));
    }

    /** Plays the item's clip, if it has one, in place of the clip that is playing. */
    public void play(ItemId item) {
        stop();
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        soundEvent(item).filter(event -> sounds.getSoundEvent(event) != null).ifPresent(event -> {
            playing = new SimpleSoundInstance(event, SoundSource.VOICE, 1, 1, SoundInstance.createUnseededRandom(),
                    false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
            sounds.play(playing);
        });
    }

    /** Stops the clip that is playing, as when the player leaves the world or server. */
    public void stop() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }
}
