package com.dwarfmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DwarfBannerActionRequest(int dwarfId, int action) implements CustomPacketPayload {
    public static final Type<DwarfBannerActionRequest> TYPE = new Type<>(DwarfMod.id("dwarf_banner_action"));
    public static final StreamCodec<FriendlyByteBuf, DwarfBannerActionRequest> CODEC = CustomPacketPayload.codec(
            DwarfBannerActionRequest::write,
            DwarfBannerActionRequest::new
    );

    public DwarfBannerActionRequest(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.dwarfId);
        buf.writeInt(this.action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
