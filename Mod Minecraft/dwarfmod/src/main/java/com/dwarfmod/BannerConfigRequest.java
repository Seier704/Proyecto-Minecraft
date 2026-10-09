package com.dwarfmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: tunnel size (1..3) and depth (5..64) chosen in the miner banner screen. */
public record BannerConfigRequest(int mode, int depth) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BannerConfigRequest> TYPE = new CustomPacketPayload.Type<>(DwarfMod.id("banner_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BannerConfigRequest> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeInt(p.mode);
			buf.writeInt(p.depth);
		},
		buf -> new BannerConfigRequest(buf.readInt(), buf.readInt()));

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
