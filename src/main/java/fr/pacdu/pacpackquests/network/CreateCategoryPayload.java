package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CreateCategoryPayload(String category) implements CustomPayload {
    public static final Id<CreateCategoryPayload> ID = new Id<>(Identifier.of("pacpackquests", "create_category"));

    public static final PacketCodec<RegistryByteBuf, CreateCategoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CreateCategoryPayload::category,
            CreateCategoryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}