package io.github.willdalh.oboecraft.fabric.gametest.mixin;

import io.github.willdalh.oboecraft.fabric.gametest.PlayedReadingClips;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes playback requests without replacing or cancelling Minecraft's sound playback. */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void recordReadingClip(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        PlayedReadingClips.record(sound);
    }
}
