package io.github.willdalh.oboecraft.fabric.client.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The connection a configuration or play listener belongs to, which stays the same from configuration into the world,
 * so the progress store's session can belong to it.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {
    @Accessor("connection")
    Connection oboecraft$connection();
}
