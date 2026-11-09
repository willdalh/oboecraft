package io.github.willdalh.oboecraft.fabric.tsukumogami;

import io.github.willdalh.oboecraft.core.Oboecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The tsukumogami's sound events, {@code oboecraft:entity.tsukumogami.<name>}. Until it has sounds of its own, the
 * mod's {@code sounds.json} plays fitting vanilla sounds for them, so only that file changes when it gets its own.
 */
public final class TsukumogamiSounds {
    /** A temple bell, when it arrives. */
    public static final SoundEvent ARRIVE = register("arrive");
    public static final SoundEvent AMBIENT = register("ambient");
    /** Heavy geta steps. */
    public static final SoundEvent STEP = register("step");
    /** The wind-up of its attack. */
    public static final SoundEvent ATTACK = register("attack");
    public static final SoundEvent HURT = register("hurt");
    public static final SoundEvent DEATH = register("death");
    /** A dull sound when anyone but its owner hits it, so nobody thinks it is broken. */
    public static final SoundEvent NO_EFFECT = register("no_effect");
    /** A wind chime, when it fades away. */
    public static final SoundEvent FADE = register("fade");

    private TsukumogamiSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Oboecraft.MOD_ID, "entity.tsukumogami." + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Registers the sound events, when the mod starts. */
    static void register() {
        // Loading the class registers them.
    }
}
