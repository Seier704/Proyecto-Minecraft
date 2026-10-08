package com.dwarfmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by the client when the player Alt+right-clicks a dwarf; the server answers with a chat summary. */
public record DwarfInfoRequest(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DwarfInfoRequest> TYPE = new CustomPacketPayload.Type<>(DwarfMod.id("dwarf_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DwarfInfoRequest> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, DwarfInfoRequest::entityId, DwarfInfoRequest::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
