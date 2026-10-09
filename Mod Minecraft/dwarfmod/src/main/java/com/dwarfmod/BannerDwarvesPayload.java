package com.dwarfmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: the hired miners tied to the open banner. The client cannot see the banner link, the work tool
 * or the inventory of a dwarf, so the server lists them (answer to {@link DwarfBannerActionRequest} action 3).
 */
public record BannerDwarvesPayload(List<Entry> dwarves) implements CustomPacketPayload {
	public record Entry(int id, boolean working, boolean full, ItemStack tool) {
	}

	public static final CustomPacketPayload.Type<BannerDwarvesPayload> TYPE = new CustomPacketPayload.Type<>(DwarfMod.id("banner_dwarves"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BannerDwarvesPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeInt(p.dwarves.size());
			for (Entry e : p.dwarves) {
				buf.writeInt(e.id);
				buf.writeBoolean(e.working);
				buf.writeBoolean(e.full);
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.tool);
			}
		},
		buf -> {
			int n = buf.readInt();
			List<Entry> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				list.add(new Entry(buf.readInt(), buf.readBoolean(), buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
			}
			return new BannerDwarvesPayload(list);
		});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
