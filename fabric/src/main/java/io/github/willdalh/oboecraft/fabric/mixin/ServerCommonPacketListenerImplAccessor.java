package io.github.willdalh.oboecraft.fabric.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The connection a configuration or play listener belongs to, which stays the same from configuration into the world,
 * so the progress a player sent while configured is only used by the same connection in the world.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {
    @Accessor("connection")
    Connection oboecraft$connection();
}
