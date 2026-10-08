package com.dwarfmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: what the info screen shows. Order of values:
 * entityId, role, level, xp, nextXp(-1 max), friendship points, stage, nextPoints(-1 max), price %, canFollow, hire state
 * (0 none, 1 locked, 2 available, 3 active), hire minutes left, hire cost, following.
 */
public record DwarfInfoPayload(int[] v) implements CustomPacketPayload {
	public static final int SIZE = 14;
	public static final CustomPacketPayload.Type<DwarfInfoPayload> TYPE = new CustomPacketPayload.Type<>(DwarfMod.id("dwarf_info_data"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DwarfInfoPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			for (int i = 0; i < SIZE; i++) {
				buf.writeInt(p.v[i]);
			}
		},
		buf -> {
			int[] v = new int[SIZE];
			for (int i = 0; i < SIZE; i++) {
				v[i] = buf.readInt();
			}
			return new DwarfInfoPayload(v);
		});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
