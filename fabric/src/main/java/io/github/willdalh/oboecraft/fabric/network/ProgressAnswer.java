package io.github.willdalh.oboecraft.fabric.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client's answer to {@link RequestProgress}: the player's progress, or why the client can't send it. */
public sealed interface ProgressAnswer extends CustomPacketPayload permits UploadProgress, ProgressUnavailable {
}
