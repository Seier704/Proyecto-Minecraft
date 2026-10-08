package com.dwarfmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the "follow me" button of the dwarf info screen. */
public record DwarfFollowRequest(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DwarfFollowRequest> TYPE = new CustomPacketPayload.Type<>(DwarfMod.id("dwarf_follow"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DwarfFollowRequest> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, DwarfFollowRequest::entityId, DwarfFollowRequest::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
