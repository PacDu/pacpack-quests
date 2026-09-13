package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EditCategoryPayload(String categoryOld, String categoryNew) implements CustomPayload {
    public static final CustomPayload.Id<EditCategoryPayload> ID = new CustomPayload.Id<>(Identifier.of("pacpackquests", "edit_category"));

    public static final PacketCodec<RegistryByteBuf, EditCategoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, EditCategoryPayload::categoryOld,
            PacketCodecs.STRING, EditCategoryPayload::categoryNew,
            EditCategoryPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
