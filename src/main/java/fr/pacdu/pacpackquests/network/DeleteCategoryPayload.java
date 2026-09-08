package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeleteCategoryPayload(String category) implements CustomPayload {
    public static final Id<DeleteCategoryPayload> ID = new Id<>(Identifier.of("pacpackquests", "delete_category"));

    public static final PacketCodec<RegistryByteBuf, DeleteCategoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, DeleteCategoryPayload::category,
            DeleteCategoryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}