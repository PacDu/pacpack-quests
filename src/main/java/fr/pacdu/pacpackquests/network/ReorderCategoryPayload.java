package fr.pacdu.pacpackquests.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ReorderCategoryPayload(List<String> categories) implements CustomPayload {
    public static final Id<ReorderCategoryPayload> ID = new Id<>(Identifier.of("pacpackquests", "reorder_category"));

    public static final PacketCodec<RegistryByteBuf, ReorderCategoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), ReorderCategoryPayload::categories,
            ReorderCategoryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}